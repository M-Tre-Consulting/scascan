import SwiftUI
import ScaScanKit

/// Mirrors Android's `ProfileFragment` — personal info (feeding the
/// Mifflin-St Jeor calculation), AI target status, and the settings entry point.
struct ProfileView: View {
    @Environment(AppContainer.self) private var container
    @State private var state: ProfileViewState?

    @State private var name = ""
    @State private var age = ""
    @State private var height = ""
    @State private var weight = ""
    @State private var isMale = true
    @State private var activityIndex = 2
    @State private var goalIndex = 1
    @State private var fieldError: String?

    private static let activityLabels = ["Sedentary", "Lightly active", "Moderately active", "Very active", "Extra active"]
    private static let goalLabels = ["Lose weight", "Maintain weight", "Build muscle"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if container.profileStore.aiCalorieTarget == 0 {
                    setupReminderCard
                }
                personalInfoCard
                if let state {
                    aiTargetCard(state)
                }
            }
            .padding(20)
        }
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: Route.self) { route in
            if case .settings = route { AppSettingsView() }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: Route.settings) {
                    Image(systemName: "gearshape")
                }
            }
        }
        .onAppear {
            if state == nil {
                state = ProfileViewState(repository: container.nutritionRepository, profileStore: container.profileStore)
            }
            loadProfile()
        }
    }

    private var setupReminderCard: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 6) {
                Text("Personalized targets not set")
                    .font(.headline)
                Text("Your daily calorie goal is currently estimated. Select a fitness goal below and save your profile to get an AI-computed personalized target.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var personalInfoCard: some View {
        SectionCard(title: "Personal Details", subtitle: "Used to calculate your daily calorie goal.") {
            VStack(spacing: 12) {
                TextField("Name", text: $name)
                    .textFieldStyle(.roundedBorder)

                HStack(spacing: 12) {
                    TextField("Age", text: $age)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                    TextField("Height (cm)", text: $height)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                    TextField("Weight (kg)", text: $weight)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder)
                }

                Picker("Sex", selection: $isMale) {
                    Text("Male").tag(true)
                    Text("Female").tag(false)
                }
                .pickerStyle(.segmented)

                Picker("Activity level", selection: $activityIndex) {
                    ForEach(Array(Self.activityLabels.enumerated()), id: \.offset) { index, label in
                        Text(label).tag(index)
                    }
                }
                .pickerStyle(.menu)

                Picker("Fitness goal", selection: $goalIndex) {
                    ForEach(Array(Self.goalLabels.enumerated()), id: \.offset) { index, label in
                        Text(label).tag(index)
                    }
                }
                .pickerStyle(.menu)

                if let fieldError {
                    Text(fieldError)
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                Button("Save profile", action: saveProfile)
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    @ViewBuilder
    private func aiTargetCard(_ state: ProfileViewState) -> some View {
        if state.targetState != .idle || container.profileStore.aiCalorieTarget > 0 {
            SectionCard(title: "AI-computed target") {
                switch state.targetState {
                case .idle:
                    HStack {
                        Text("Daily target: \(container.profileStore.aiCalorieTarget) kcal (AI-computed)")
                        Spacer()
                        Button("Recompute") { state.computeTargets() }
                            .font(.subheadline)
                    }
                case .computing:
                    HStack {
                        ProgressView()
                        Text("Computing your personalized targets…")
                            .foregroundStyle(.secondary)
                    }
                case .done(let calories):
                    Text("Daily target: \(calories) kcal (AI-computed)")
                case .error(let message):
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private func loadProfile() {
        let profile = container.profileStore
        guard profile.hasProfile() else { return }
        name = profile.name
        age = String(profile.age)
        height = String(profile.heightCm)
        weight = String(profile.weightKg)
        isMale = profile.isMale
        activityIndex = profile.activityIndex
        goalIndex = profile.goalIndex
    }

    private func saveProfile() {
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        guard !trimmedName.isEmpty else { fieldError = "Name is required"; return }
        guard let ageValue = Int(age), ageValue > 0 else { fieldError = "Age is required"; return }
        guard let heightValue = Int(height), heightValue > 0 else { fieldError = "Height is required"; return }
        guard let weightValue = Double(weight), weightValue > 0 else { fieldError = "Weight is required"; return }
        fieldError = nil

        let profile = container.profileStore
        profile.name = trimmedName
        profile.age = ageValue
        profile.heightCm = heightValue
        profile.weightKg = weightValue
        profile.isMale = isMale
        profile.activityIndex = activityIndex
        profile.goalIndex = goalIndex

        if container.keyStore.hasKey() {
            state?.computeTargets()
        }
    }
}
