import Foundation
import HealthKit
import UIKit
import Observation

/// Mirrors Android's `data.health.HealthConnectManager`, backed by HealthKit
/// instead of Health Connect. Conforms to `HealthProviding` for `LogRepository`,
/// and exposes a richer surface (connect/disconnect/sync) for `AppSettingsView`,
/// matching how Android injects the concrete `HealthConnectManager` directly
/// into `AppSettingsFragment`/`ProfileViewModel` rather than hiding it behind
/// an interface there.
///
/// One real platform difference, documented rather than glossed over: Health
/// Connect lets an app query exactly which permissions were granted.
/// HealthKit deliberately does not — for read-only types, `authorizationStatus`
/// stays `.notDetermined`-ish from the app's point of view even after the
/// user grants access, so an app can't infer whether someone *has* health
/// data (e.g. to detect pregnancy). So "connected" here means "the user has
/// been through the request sheet at least once" (persisted locally), not a
/// live read of granted scopes — reads simply return empty/zero for whatever
/// was actually denied, which is how HealthKit is designed to degrade anyway.
///
/// Two-way sync, both directions explicit here since it's easy to lose track
/// of what actually crosses the Health boundary:
///   - READ: steps, active energy burned, height, weight, and workouts
///     (Apple Watch / Fitness app sessions) — the active-energy sum already
///     folds in whatever a workout contributed, since that's the same
///     HealthKit `activeEnergyBurned` type both write into; `readWorkoutsToday`
///     additionally exposes the per-workout breakdown so that's visible
///     rather than just a single opaque number.
///   - WRITE: every logged meal (as an `HKCorrelation` of type `.food`,
///     bundling energy + all six macros so Health's Nutrition tab shows a
///     normal food entry) and every water log (`dietaryWater`), tagged with
///     ScaScan's own id so edits/deletes in the app replace or remove the
///     matching HealthKit object instead of leaving orphaned duplicates.
@MainActor
@Observable
public final class HealthKitManager: HealthProviding {
    public static let shared = HealthKitManager()

    private let store = HKHealthStore()
    private let profileStore: UserProfileStore
    private let defaults: UserDefaults
    private static let requestedKey = "healthkit_did_request_access"

    private static let stepsType = HKQuantityType(.stepCount)
    private static let activeEnergyType = HKQuantityType(.activeEnergyBurned)
    private static let heightType = HKQuantityType(.height)
    private static let weightType = HKQuantityType(.bodyMass)
    private static let workoutType = HKObjectType.workoutType()

    private static let dietaryEnergyType = HKQuantityType(.dietaryEnergyConsumed)
    private static let dietaryProteinType = HKQuantityType(.dietaryProtein)
    private static let dietaryCarbsType = HKQuantityType(.dietaryCarbohydrates)
    private static let dietaryFatType = HKQuantityType(.dietaryFatTotal)
    private static let dietaryFiberType = HKQuantityType(.dietaryFiber)
    private static let dietarySugarType = HKQuantityType(.dietarySugar)
    private static let dietarySodiumType = HKQuantityType(.dietarySodium)
    private static let dietaryWaterType = HKQuantityType(.dietaryWater)

    /// Metadata key tagging every sample/correlation ScaScan writes with the
    /// originating `LogEntry`/`WaterLog`'s own `id`, so it can be found again
    /// for deletion or replacement — HealthKit has no "update in place".
    private static let entryIDMetadataKey = "com.nicoloperri.Scascan.logEntryID"
    private static let waterIDMetadataKey = "com.nicoloperri.Scascan.waterLogID"

    private static let readTypes: Set<HKObjectType> = [
        stepsType, activeEnergyType, heightType, weightType, workoutType
    ]
    private static let shareTypes: Set<HKSampleType> = [
        dietaryEnergyType, dietaryProteinType, dietaryCarbsType, dietaryFatType,
        dietaryFiberType, dietarySugarType, dietarySodiumType, dietaryWaterType
    ]

    public var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    public init(profileStore: UserProfileStore = .shared, defaults: UserDefaults = .standard) {
        self.profileStore = profileStore
        self.defaults = defaults
    }

    // MARK: - Connection

    public func requestAuthorization() async throws {
        try await store.requestAuthorization(toShare: Self.shareTypes, read: Self.readTypes)
        defaults.set(true, forKey: Self.requestedKey)
    }

    public func hasPermissions() async -> Bool {
        guard isAvailable else { return false }
        return defaults.bool(forKey: Self.requestedKey)
    }

    /// HealthKit provides no API to programmatically revoke read access — the
    /// user must do that in Settings ▸ Health ▸ Data Access & Devices. This
    /// only resets ScaScan's local "connected" flag so the UI reflects
    /// disconnection; call `openHealthSettings()` alongside it in the UI.
    ///
    /// Also clears the shared cache `LogRepository` leaves behind for the
    /// widget (`isHealthConnected` / cached active-calorie readings) —
    /// otherwise the widget, which has no direct Health access of its own,
    /// would keep computing the adaptive target from a frozen, increasingly
    /// stale snapshot forever after the user disconnects here.
    public func disconnect() {
        defaults.set(false, forKey: Self.requestedKey)
        profileStore.isHealthConnected = false
        profileStore.lastActiveCalories = 0
        profileStore.lastYesterdayActiveCaloriesDayStart = 0
    }

    public func openHealthSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    // MARK: - HealthProviding (reads)

    public func readActiveCalories(offsetDays: Int) async -> Double {
        let (start, end) = Self.dayRange(offsetDays: offsetDays)
        return await readActiveCaloriesRange(start: start, end: end)
    }

    public func readActiveCaloriesRange(start: Date, end: Date) async -> Double {
        let kcal = await sumQuantity(type: Self.activeEnergyType, unit: .kilocalorie(), start: start, end: end)
        if kcal > 1.0 { return kcal }

        // Fallback: estimate from steps (~0.04 kcal/step), matching Android's
        // last-resort tier when no active-energy samples exist yet.
        let steps = await readSteps(start: start, end: end)
        return steps > 0 ? Double(steps) * 0.04 : 0
    }

    public func readWeightHistory(pastDays: Int) async -> [(Date, Double)] {
        let end = Date()
        let start = end.addingTimeInterval(-Double(pastDays) * 86_400)
        let samples = await quantitySamples(type: Self.weightType, start: start, end: end, ascending: true)
        return samples.map { ($0.startDate, $0.quantity.doubleValue(for: .gramUnit(with: .kilo))) }
    }

    // MARK: - Additional reads (mirrors HealthConnectManager's fuller surface)

    public func readSteps(offsetDays: Int) async -> Int {
        let (start, end) = Self.dayRange(offsetDays: offsetDays)
        return await readSteps(start: start, end: end)
    }

    public func readSteps(start: Date, end: Date) async -> Int {
        Int(await sumQuantity(type: Self.stepsType, unit: .count(), start: start, end: end))
    }

    public func readLatestWeightKg() async -> Double? {
        let end = Date()
        let start = end.addingTimeInterval(-30 * 86_400)
        let samples = await quantitySamples(type: Self.weightType, start: start, end: end, ascending: false, limit: 1)
        return samples.first?.quantity.doubleValue(for: .gramUnit(with: .kilo))
    }

    public func readLatestHeightCm() async -> Double? {
        let end = Date()
        let start = end.addingTimeInterval(-365 * 86_400)
        let samples = await quantitySamples(type: Self.heightType, start: start, end: end, ascending: false, limit: 1)
        return samples.first?.quantity.doubleValue(for: .meterUnit(with: .centi))
    }

    /// Today's Apple Watch / Fitness app workout sessions — purely informational
    /// (their calories are already folded into `readActiveCalories` above,
    /// since both draw from the same `activeEnergyBurned` HealthKit type).
    public func readWorkoutsToday() async -> [WorkoutSummary] {
        guard isAvailable else { return [] }
        let (start, end) = Self.dayRange(offsetDays: 0)
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let samples = (try? await querySamples(type: Self.workoutType, predicate: predicate)) ?? []
        return samples.compactMap { sample in
            guard let workout = sample as? HKWorkout else { return nil }
            let kcal = workout.statistics(for: Self.activeEnergyType)?.sumQuantity()?.doubleValue(for: .kilocalorie()) ?? 0
            return WorkoutSummary(
                id: workout.uuid.uuidString,
                activityName: workout.workoutActivityType.displayName,
                start: workout.startDate,
                duration: workout.duration,
                kcal: kcal
            )
        }
        .sorted { $0.start > $1.start }
    }

    // MARK: - HealthProviding (write-back)

    /// Writes a logged meal to Health as an `HKCorrelation` of type `.food`
    /// bundling energy + all six macros — the same shape Health's own
    /// Nutrition tab and other food-tracking apps expect, so ScaScan's log
    /// actually shows up there instead of just sitting in ScaScan's own
    /// SwiftData store. Best-effort: a write failure (no permission, Health
    /// unavailable) never blocks or fails the local log.
    public func writeDietaryEntry(_ entry: LogEntry) async {
        guard isAvailable, await hasPermissions() else { return }
        let date = entry.timestamp
        let idString = entry.id.uuidString
        let baseMetadata: [String: Any] = [HKMetadataKeyFoodType: entry.foodName, Self.entryIDMetadataKey: idString]

        func sample(_ type: HKQuantityType, _ unit: HKUnit, _ value: Double) -> HKQuantitySample {
            HKQuantitySample(
                type: type, quantity: HKQuantity(unit: unit, doubleValue: value),
                start: date, end: date, metadata: baseMetadata
            )
        }

        let samples: Set<HKSample> = [
            sample(Self.dietaryEnergyType, .kilocalorie(), entry.calories),
            sample(Self.dietaryProteinType, .gram(), entry.protein),
            sample(Self.dietaryCarbsType, .gram(), entry.carbohydrates),
            sample(Self.dietaryFatType, .gram(), entry.fat),
            sample(Self.dietaryFiberType, .gram(), entry.fiber),
            sample(Self.dietarySugarType, .gram(), entry.sugar),
            sample(Self.dietarySodiumType, .gramUnit(with: .milli), entry.sodium)
        ]

        let correlation = HKCorrelation(
            type: HKCorrelationType(.food), start: date, end: date, objects: samples, metadata: baseMetadata
        )
        try? await store.save(correlation)
    }

    /// Removes the HealthKit food correlation previously written for this
    /// entry's id (found via the metadata tag, since HealthKit objects can't
    /// be looked up by ScaScan's own SwiftData id directly). Called before
    /// re-writing on edit, and standalone on delete.
    public func deleteDietaryEntry(id: UUID) async {
        guard isAvailable else { return }
        let predicate = HKQuery.predicateForObjects(withMetadataKey: Self.entryIDMetadataKey, allowedValues: [id.uuidString])
        guard let objects = try? await querySamples(type: HKCorrelationType(.food), predicate: predicate), !objects.isEmpty else { return }
        try? await store.delete(objects)
    }

    public func writeWater(_ log: WaterLog) async {
        guard isAvailable, await hasPermissions() else { return }
        let sample = HKQuantitySample(
            type: Self.dietaryWaterType,
            quantity: HKQuantity(unit: .literUnit(with: .milli), doubleValue: Double(log.amountMl)),
            start: log.timestamp, end: log.timestamp,
            metadata: [Self.waterIDMetadataKey: log.id.uuidString]
        )
        try? await store.save(sample)
    }

    public func deleteWater(id: UUID) async {
        guard isAvailable else { return }
        let predicate = HKQuery.predicateForObjects(withMetadataKey: Self.waterIDMetadataKey, allowedValues: [id.uuidString])
        guard let objects = try? await querySamples(type: Self.dietaryWaterType, predicate: predicate), !objects.isEmpty else { return }
        try? await store.delete(objects)
    }

    // MARK: - Query plumbing

    private func sumQuantity(type: HKQuantityType, unit: HKUnit, start: Date, end: Date) async -> Double {
        guard isAvailable else { return 0 }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        return await withCheckedContinuation { continuation in
            let query = HKStatisticsQuery(quantityType: type, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, statistics, _ in
                let value = statistics?.sumQuantity()?.doubleValue(for: unit) ?? 0
                continuation.resume(returning: value)
            }
            store.execute(query)
        }
    }

    private func quantitySamples(
        type: HKSampleType,
        start: Date,
        end: Date,
        ascending: Bool,
        limit: Int = HKObjectQueryNoLimit
    ) async -> [HKQuantitySample] {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let samples = (try? await querySamples(type: type, predicate: predicate, ascending: ascending, limit: limit)) ?? []
        return samples.compactMap { $0 as? HKQuantitySample }
    }

    private func querySamples(
        type: HKSampleType,
        predicate: NSPredicate,
        ascending: Bool = false,
        limit: Int = HKObjectQueryNoLimit
    ) async throws -> [HKSample] {
        guard isAvailable else { return [] }
        let sort = [NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: ascending)]
        return try await withCheckedThrowingContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: limit, sortDescriptors: sort) { _, samples, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: samples ?? [])
                }
            }
            store.execute(query)
        }
    }

    private static func dayRange(offsetDays: Int) -> (start: Date, end: Date) {
        let start = Calendar.current.startOfDay(for: Date().addingTimeInterval(Double(offsetDays) * 86_400))
        let end = offsetDays == 0 ? Date() : start.addingTimeInterval(86_400)
        return (start, end)
    }
}
