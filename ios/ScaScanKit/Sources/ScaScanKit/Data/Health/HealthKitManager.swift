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

    private static let readTypes: Set<HKObjectType> = [stepsType, activeEnergyType, heightType, weightType]

    public var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    public init(profileStore: UserProfileStore = .shared, defaults: UserDefaults = .standard) {
        self.profileStore = profileStore
        self.defaults = defaults
    }

    // MARK: - Connection

    public func requestAuthorization() async throws {
        try await store.requestAuthorization(toShare: [], read: Self.readTypes)
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
    public func disconnect() {
        defaults.set(false, forKey: Self.requestedKey)
    }

    public func openHealthSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    // MARK: - HealthProviding

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
        let samples = await samples(type: Self.weightType, start: start, end: end, ascending: true)
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
        let samples = await samples(type: Self.weightType, start: start, end: end, ascending: false, limit: 1)
        return samples.first?.quantity.doubleValue(for: .gramUnit(with: .kilo))
    }

    public func readLatestHeightCm() async -> Double? {
        let end = Date()
        let start = end.addingTimeInterval(-365 * 86_400)
        let samples = await samples(type: Self.heightType, start: start, end: end, ascending: false, limit: 1)
        return samples.first?.quantity.doubleValue(for: .meterUnit(with: .centi))
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

    private func samples(
        type: HKSampleType,
        start: Date,
        end: Date,
        ascending: Bool,
        limit: Int = HKObjectQueryNoLimit
    ) async -> [HKQuantitySample] {
        guard isAvailable else { return [] }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let sort = [NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: ascending)]
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: limit, sortDescriptors: sort) { _, samples, _ in
                continuation.resume(returning: (samples as? [HKQuantitySample]) ?? [])
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
