import Foundation
import Observation
import ScaScanKit

/// Mirrors the AI-target-computation slice of Android's `ProfileViewModel`.
@MainActor
@Observable
final class ProfileViewState {
    enum TargetState: Equatable {
        case idle
        case computing
        case done(Int)
        case error(String)
    }

    private(set) var targetState: TargetState = .idle

    private let repository: NutritionRepository
    private let profileStore: UserProfileStore
    private let keyStore: GeminiKeyStore

    init(
        repository: NutritionRepository,
        profileStore: UserProfileStore = .shared,
        keyStore: GeminiKeyStore = .shared
    ) {
        self.repository = repository
        self.profileStore = profileStore
        self.keyStore = keyStore
    }

    func computeTargets() {
        guard profileStore.hasProfile(), keyStore.hasKey() else { return }
        targetState = .computing
        Task {
            do {
                let targets = try await repository.computeTargets(profile: profileStore)
                profileStore.aiCalorieTarget = targets.dailyCalories
                profileStore.aiProteinTarget = targets.proteinGrams
                profileStore.aiCarbsTarget = targets.carbsGrams
                profileStore.aiFatTarget = targets.fatGrams
                targetState = .done(targets.dailyCalories)
            } catch {
                targetState = .error(error.localizedDescription)
            }
        }
    }

    func resetTargetState() { targetState = .idle }
}
