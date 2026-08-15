import HealthKit

/// HealthKit gives every workout a `workoutActivityType` enum case but no
/// human-readable name — apps are expected to supply their own (Apple's own
/// Fitness app ships a much larger private table). Covers the activity types
/// people actually log from Apple Watch / the Fitness app; anything else
/// falls back to "Workout" rather than a raw enum case number.
///
/// `String(localized:)` rather than a plain literal: this runs outside a
/// SwiftUI view body, so it can't rely on `Text` to pick the string up into
/// the app's `Localizable.xcstrings` catalog automatically — this makes the
/// same lookup explicit.
extension HKWorkoutActivityType {
    var displayName: String {
        switch self {
        case .americanFootball: return String(localized: "American Football")
        case .archery: return String(localized: "Archery")
        case .badminton: return String(localized: "Badminton")
        case .baseball: return String(localized: "Baseball")
        case .basketball: return String(localized: "Basketball")
        case .boxing: return String(localized: "Boxing")
        case .climbing: return String(localized: "Climbing")
        case .cricket: return String(localized: "Cricket")
        case .crossTraining: return String(localized: "Cross Training")
        case .cycling: return String(localized: "Cycling")
        case .dance: return String(localized: "Dance")
        case .elliptical: return String(localized: "Elliptical")
        case .fencing: return String(localized: "Fencing")
        case .functionalStrengthTraining: return String(localized: "Functional Strength Training")
        case .golf: return String(localized: "Golf")
        case .gymnastics: return String(localized: "Gymnastics")
        case .hiking: return String(localized: "Hiking")
        case .hockey: return String(localized: "Hockey")
        case .highIntensityIntervalTraining: return String(localized: "HIIT")
        case .martialArts: return String(localized: "Martial Arts")
        case .mixedCardio: return String(localized: "Mixed Cardio")
        case .paddleSports: return String(localized: "Paddle Sports")
        case .pilates: return String(localized: "Pilates")
        case .rowing: return String(localized: "Rowing")
        case .rugby: return String(localized: "Rugby")
        case .running: return String(localized: "Running")
        case .sailing: return String(localized: "Sailing")
        case .skatingSports: return String(localized: "Skating")
        case .snowSports: return String(localized: "Snow Sports")
        case .soccer: return String(localized: "Soccer")
        case .softball: return String(localized: "Softball")
        case .squash: return String(localized: "Squash")
        case .stairClimbing: return String(localized: "Stair Climbing")
        case .surfingSports: return String(localized: "Surfing")
        case .swimming: return String(localized: "Swimming")
        case .tableTennis: return String(localized: "Table Tennis")
        case .tennis: return String(localized: "Tennis")
        case .trackAndField: return String(localized: "Track & Field")
        case .traditionalStrengthTraining: return String(localized: "Strength Training")
        case .volleyball: return String(localized: "Volleyball")
        case .walking: return String(localized: "Walking")
        case .waterFitness: return String(localized: "Water Fitness")
        case .wrestling: return String(localized: "Wrestling")
        case .yoga: return String(localized: "Yoga")
        case .coreTraining: return String(localized: "Core Training")
        case .flexibility: return String(localized: "Flexibility")
        case .cooldown: return String(localized: "Cooldown")
        case .other: return String(localized: "Workout")
        default: return String(localized: "Workout")
        }
    }
}
