import SwiftUI
import ScaScanKit

/// The evening recap — the day's books, closed.
///
/// No Android counterpart. It exists because the daily target on the Log tab is
/// deliberately hypothetical: it never moves with the Watch's active-energy
/// syncing, so it's a stable line to aim at all day. That leaves the real
/// arithmetic — activity burned, and the balance yesterday left behind — to be
/// settled once, here, as deductions from what was eaten. See `DailyRecap`.
///
/// It unlocks at `NotificationHelper.recapHour` (21:00) for the current day; the
/// 21:00 notification deep-links straight into it. Earlier days stay reachable
/// from the calendar button.
///
/// The reveal is sequenced rather than dumped on screen at once: meals build up
/// one by one, water fills, the deductions land, and only then does the verdict
/// ring draw itself. That ordering is the point — it reads as a result being
/// worked out, not a table.
struct DailyRecapView: View {
    @Environment(AppContainer.self) private var container
    @State private var state: DailyRecapState?

    /// The day the screen opens on — 0 from the notification, or whatever the
    /// Log tab asked for.
    let initialOffsetDays: Int

    // Reveal choreography.
    @State private var phase: Phase = .idle
    @State private var revealedMeals = 0
    @State private var eatenShown: Double = 0
    @State private var burnedShown: Double = 0
    @State private var carryShown: Double = 0
    @State private var netShown: Double = 0
    @State private var waterFill: Double = 0
    @State private var ringFill: Double = 0
    @State private var verdictBump = 0
    @State private var replayCount = 0

    private enum Phase: Int, Comparable {
        case idle, meals, water, ledger, verdict
        static func < (lhs: Self, rhs: Self) -> Bool { lhs.rawValue < rhs.rawValue }
    }

    var body: some View {
        Group {
            if let state {
                content(state)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Daily Recap")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if state == nil {
                state = DailyRecapState(repository: container.logRepository, offsetDays: initialOffsetDays)
            }
        }
    }

    @ViewBuilder
    private func content(_ state: DailyRecapState) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header(state)

                    if !state.isUnlocked {
                        LockedRecapCard(unlockDate: state.unlockDate) { state.select(offsetDays: -1) }
                    } else if let recap = state.recap {
                        mealsCard(recap)

                        if phase >= .water {
                            waterCard(recap).transition(.recapCard)
                        }
                        if phase >= .ledger {
                            ledgerCard(recap).transition(.recapCard)
                        }
                        if phase >= .verdict {
                            // A day with nothing logged has no verdict to give.
                            // Rendering the ring anyway would state a confident
                            // conclusion ("well under target") drawn entirely
                            // from the absence of data.
                            if recap.meals.isEmpty {
                                NoDataCard()
                                    .id(Self.verdictAnchor)
                                    .transition(.recapCard)
                            } else {
                                VerdictHero(recap: recap, ringFill: ringFill)
                                    .id(Self.verdictAnchor)
                                    .transition(.recapCard)
                            }
                        }
                    } else {
                        ProgressView().frame(maxWidth: .infinity).padding(.vertical, 40)
                    }
                }
                .padding(20)
                .padding(.bottom, 24)
            }
            .toolbar { toolbar(state) }
            // Re-runs whenever the day changes or Replay is tapped: loads that
            // day's ledger, then plays the reveal from the top.
            .task(id: "\(state.offsetDays).\(replayCount)") {
                resetChoreography()
                await state.load()
                guard let recap = state.recap else { return }
                try? await play(recap, proxy: proxy)
            }
            // 21:00 is exactly when someone is most likely to already be sitting
            // on this screen (the notification just fired). Without this it
            // would stay locked, counting down past zero, until they navigated
            // away and back.
            .task(id: state.offsetDays) {
                let delay = state.unlockDate.timeIntervalSinceNow
                guard !state.isUnlocked, delay > 0, delay < Self.dayInSeconds else { return }
                try? await Task.sleep(for: .seconds(delay + 1))
                guard !Task.isCancelled else { return }
                replayCount += 1
            }
            .sensoryFeedback(trigger: verdictBump) { _, _ in
                guard let verdict = state.recap?.verdict else { return nil }
                return verdict == .onTarget ? .success : .warning
            }
        }
    }

    // MARK: - Header & chrome

    private func header(_ state: DailyRecapState) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            dayText(state.dayLabel, dateStyle: .dateTime.weekday(.wide))
                .font(.largeTitle.bold())
            Text(state.daySubtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    /// Renders a `DayLabel`: the relative words come from the string catalog,
    /// the dates from `Text`'s own locale-aware formatting.
    @ViewBuilder
    private func dayText(_ label: DailyRecapState.DayLabel, dateStyle: Date.FormatStyle) -> some View {
        switch label {
        case .today: Text("Today")
        case .yesterday: Text("Yesterday")
        case .other(let date): Text(date, format: dateStyle)
        }
    }

    @ToolbarContentBuilder
    private func toolbar(_ state: DailyRecapState) -> some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Picker("Day", selection: Binding(
                    get: { state.offsetDays },
                    set: { state.select(offsetDays: $0) }
                )) {
                    // Always deep enough to include the day actually on screen —
                    // the Log tab can walk back further than the picker's
                    // default reach, and a selection with no matching tag would
                    // render as an empty picker.
                    ForEach(state.pickerOffsets, id: \.self) { offset in
                        dayText(
                            state.label(forOffset: offset),
                            dateStyle: .dateTime.weekday(.abbreviated).day().month(.abbreviated)
                        )
                        .tag(offset)
                    }
                }
            } label: {
                Label("Choose day", systemImage: "calendar")
            }
        }
        if state.isUnlocked, state.recap != nil {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    replayCount += 1
                } label: {
                    Label("Replay", systemImage: "arrow.counterclockwise")
                }
            }
        }
    }

    // MARK: - Cards

    private func mealsCard(_ recap: DailyRecap) -> some View {
        RecapCard(title: "What you ate", icon: "fork.knife", tint: .scascanBrand) {
            if recap.meals.isEmpty {
                Text("Nothing logged for this day.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                VStack(spacing: 10) {
                    ForEach(Array(recap.meals.enumerated()), id: \.element.id) { index, meal in
                        mealRow(meal, revealed: index < revealedMeals)
                    }
                }
            }

            Divider().padding(.vertical, 2)

            HStack {
                Text("Total eaten")
                    .font(.subheadline.weight(.medium))
                Spacer()
                CountingNumber(value: eatenShown)
                    .font(.title3.weight(.bold))
                    .monospacedDigit()
                Text("kcal")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if recap.proteinG + recap.carbsG + recap.fatG > 0 {
                Text("P \(Int(recap.proteinG.rounded()))g · C \(Int(recap.carbsG.rounded()))g · F \(Int(recap.fatG.rounded()))g")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func mealRow(_ meal: DailyRecap.Meal, revealed: Bool) -> some View {
        HStack(spacing: 12) {
            Text(meal.loggedAt, format: .dateTime.hour().minute())
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
                .monospacedDigit()
                // Wide enough for a 12-hour "10:15 AM" on one line; 24-hour
                // locales just leave a little slack.
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(width: 58, alignment: .leading)

            VStack(alignment: .leading, spacing: 1) {
                Text(meal.name)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                if !meal.servingSize.isEmpty {
                    Text(meal.servingSize)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            Text("\(Int(meal.kcal.rounded())) kcal")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .monospacedDigit()
        }
        // Each row eases in on its own beat — see `play(_:proxy:)`.
        .opacity(revealed ? 1 : 0)
        .offset(y: revealed ? 0 : 12)
        .blur(radius: revealed ? 0 : 5)
    }

    private func waterCard(_ recap: DailyRecap) -> some View {
        RecapCard(title: "Water", icon: "drop.fill", tint: .blue) {
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                CountingNumber(value: Double(recap.waterMl) * waterFillProgress(recap))
                    .font(.title2.weight(.bold))
                    .monospacedDigit()
                Text("/ \(recap.waterTargetMl) ml")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(Int((waterFill * 100).rounded()))%")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.blue)
                    .monospacedDigit()
            }

            WaterBar(fill: waterFill)

            Text(recap.waterFraction >= 1
                 ? "Goal reached — nicely done."
                 : "\(max(recap.waterTargetMl - recap.waterMl, 0)) ml short of the daily goal.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    /// The counting water number rides the same 0→1 progress as the bar, so the
    /// digits and the fill land together.
    private func waterFillProgress(_ recap: DailyRecap) -> Double {
        guard recap.waterFraction > 0 else { return 0 }
        return min(waterFill / recap.waterFraction, 1)
    }

    private func ledgerCard(_ recap: DailyRecap) -> some View {
        RecapCard(title: "The maths", icon: "equal.square.fill", tint: .indigo) {
            ledgerRow(
                label: "Eaten",
                icon: "fork.knife",
                sign: "",
                value: eatenShown,
                tint: .primary
            )
            ledgerRow(
                label: "Burned by activity",
                icon: "flame.fill",
                sign: "−",
                value: burnedShown,
                tint: .orange
            )
            ledgerRow(
                label: recap.carryOverKcal >= 0 ? "Credit from the day before" : "Owed from the day before",
                icon: recap.carryOverKcal >= 0 ? "arrow.down.left.circle.fill" : "arrow.up.right.circle.fill",
                sign: recap.carryOverKcal >= 0 ? "−" : "+",
                value: abs(carryShown),
                tint: .teal
            )

            Divider().padding(.vertical, 2)

            HStack {
                Text("What the day cost")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                CountingNumber(value: netShown)
                    .font(.title2.weight(.bold))
                    .monospacedDigit()
                Text("kcal")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            // Two separate Texts rather than one assembled String: a string
            // built at runtime is never extracted for translation.
            Text("Activity and yesterday's balance come off your intake here, not off the target you saw during the day.")
                .font(.caption)
                .foregroundStyle(.secondary)
            if recap.trendKcal != 0 {
                Text("Target includes a \(recap.trendKcal > 0 ? "+" : "")\(recap.trendKcal) kcal weight-trend correction.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func ledgerRow(
        label: LocalizedStringResource, icon: String, sign: String, value: Double, tint: Color
    ) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.footnote)
                .foregroundStyle(tint)
                .frame(width: 20)
            Text(label)
                .font(.subheadline)
            Spacer(minLength: 8)
            HStack(spacing: 1) {
                Text(sign)
                CountingNumber(value: value)
            }
            .font(.subheadline.weight(.medium))
            .monospacedDigit()
            .foregroundStyle(sign.isEmpty ? .primary : tint)
        }
    }

    // MARK: - Choreography

    private static let verdictAnchor = "recap.verdict"
    private static let dayInSeconds: TimeInterval = 24 * 60 * 60

    private func resetChoreography() {
        phase = .idle
        revealedMeals = 0
        eatenShown = 0
        burnedShown = 0
        carryShown = 0
        netShown = 0
        waterFill = 0
        ringFill = 0
    }

    /// The reveal, in the order the numbers actually make sense in: meals, then
    /// water, then the two deductions, then the verdict. `Task.sleep` throws on
    /// cancellation, so switching days mid-play unwinds this cleanly.
    private func play(_ recap: DailyRecap, proxy: ScrollViewProxy) async throws {
        try await Task.sleep(for: .seconds(0.15))
        withAnimation(.smooth) { phase = .meals }

        // Long days shouldn't take longer to reveal — the whole stagger fits
        // into ~0.9s no matter how many meals there are.
        let beat = min(0.09, 0.9 / Double(max(recap.meals.count, 1)))
        for index in recap.meals.indices {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.75)) { revealedMeals = index + 1 }
            try await Task.sleep(for: .seconds(beat))
        }
        withAnimation(.easeOut(duration: 0.7)) { eatenShown = recap.consumedKcal }
        try await Task.sleep(for: .seconds(0.5))

        withAnimation(.smooth) { phase = .water }
        // Eased rather than sprung on purpose: a spring overshoots its target,
        // and an overshooting *number* would flash a millilitre total the user
        // never actually drank.
        withAnimation(.easeOut(duration: 0.9).delay(0.1)) {
            waterFill = recap.waterFraction
        }
        try await Task.sleep(for: .seconds(0.85))

        withAnimation(.smooth) { phase = .ledger }
        withAnimation(.easeOut(duration: 0.6).delay(0.2)) {
            burnedShown = recap.burnedKcal
            carryShown = Double(recap.carryOverKcal)
        }
        withAnimation(.easeOut(duration: 0.8).delay(0.55)) { netShown = recap.netKcal }
        try await Task.sleep(for: .seconds(1.3))

        withAnimation(.smooth) { phase = .verdict }
        withAnimation(.spring(response: 1.2, dampingFraction: 0.9).delay(0.15)) {
            ringFill = recap.targetFraction
        }
        try await Task.sleep(for: .seconds(0.2))
        withAnimation(.easeInOut(duration: 0.6)) {
            proxy.scrollTo(Self.verdictAnchor, anchor: .center)
        }
        verdictBump += 1
    }
}

// MARK: - Verdict

private struct VerdictHero: View {
    let recap: DailyRecap
    let ringFill: Double

    private var tint: Color {
        switch recap.verdict {
        case .onTarget: return .scascanBrand
        case .over: return .orange
        case .under: return .cyan
        }
    }

    private var symbol: String {
        switch recap.verdict {
        case .onTarget: return "checkmark"
        case .over: return "exclamationmark"
        case .under: return "arrow.down"
        }
    }

    private var headline: LocalizedStringResource {
        switch recap.verdict {
        case .onTarget: return "Right on target"
        case .over: return "Over target"
        case .under: return "Well under target"
        }
    }

    private var detail: LocalizedStringResource {
        let delta = Int(abs(recap.deltaKcal).rounded())
        switch recap.verdict {
        case .onTarget:
            return recap.deltaKcal <= 0
                ? "\(delta) kcal of room left — a good day."
                : "\(delta) kcal over, close enough to call it even."
        case .over:
            return "\(delta) kcal above your \(recap.targetKcal) kcal target."
        case .under:
            return "\(delta) kcal below your \(recap.targetKcal) kcal target — eating too little counts too."
        }
    }

    /// How far past the target the day went, as a fraction of the target —
    /// drawn as a second, thinner arc so overshoot stays visible once the main
    /// ring is pinned at full.
    private var overshoot: Double {
        guard recap.verdict == .over, recap.targetKcal > 0 else { return 0 }
        return min(recap.deltaKcal / Double(recap.targetKcal), 1)
    }

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(.quaternary, style: StrokeStyle(lineWidth: 20, lineCap: .round))

                Circle()
                    .trim(from: 0, to: ringFill)
                    .stroke(
                        AngularGradient(
                            colors: [tint.opacity(0.55), tint],
                            center: .center,
                            startAngle: .degrees(-90),
                            endAngle: .degrees(270)
                        ),
                        style: StrokeStyle(lineWidth: 20, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))

                if overshoot > 0 {
                    Circle()
                        .trim(from: 0, to: overshoot * ringFill)
                        .stroke(tint.opacity(0.9), style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .padding(19)
                }

                VStack(spacing: 2) {
                    Image(systemName: symbol)
                        .font(.title3.weight(.black))
                        .foregroundStyle(tint)
                        .padding(.bottom, 2)
                    Text("\(Int(recap.netKcal.rounded()))")
                        .font(.system(size: 44, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                    Text("of \(recap.targetKcal) kcal")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 216, height: 216)
            .frame(maxWidth: .infinity)

            VStack(spacing: 4) {
                Text(headline)
                    .font(.title3.bold())
                    .foregroundStyle(tint)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.vertical, 20)
        .frame(maxWidth: .infinity)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
}

/// Stands in for the verdict on a day with no meals logged.
private struct NoDataCard: View {
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "tray")
                .font(.system(size: 30))
                .foregroundStyle(.secondary)
                .frame(width: 70, height: 70)
                .background(.quaternary, in: Circle())
            Text("No verdict for this day")
                .font(.title3.bold())
            Text("Nothing was logged, so there's nothing to weigh up against your target.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
}

// MARK: - Locked state

private struct LockedRecapCard: View {
    let unlockDate: Date
    let onShowYesterday: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "moon.stars.fill")
                .font(.system(size: 34))
                .foregroundStyle(.indigo)
                .frame(width: 78, height: 78)
                .background(.indigo.opacity(0.14), in: Circle())

            Text("Today's recap opens at 21:00")
                .font(.title3.bold())
                .multilineTextAlignment(.center)

            // Ticks on its own without the view needing to reload.
            TimelineView(.periodic(from: .now, by: 60)) { context in
                Text(countdown(from: context.date))
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                    .contentTransition(.numericText())
            }

            Text("The evening recap closes the day's books: everything you ate, minus what you burned and whatever balance yesterday left behind. Until then the Log tab's target stays a clean, unmoving line to aim at.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            Button("See yesterday's recap", action: onShowYesterday)
                .buttonStyle(.borderedProminent)
                .padding(.top, 2)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    private func countdown(from now: Date) -> LocalizedStringResource {
        let seconds = max(unlockDate.timeIntervalSince(now), 0)
        let hours = Int(seconds) / 3_600
        let minutes = (Int(seconds) % 3_600) / 60
        if hours > 0 { return "Unlocks in \(hours)h \(minutes)m" }
        return "Unlocks in \(minutes)m"
    }
}

// MARK: - Building blocks

/// The recap's card shell: same rounded surface the rest of the app uses, plus
/// a tinted icon chip so each step of the story is identifiable at a glance.
private struct RecapCard<Content: View>: View {
    let title: LocalizedStringResource
    let icon: String
    let tint: Color
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(tint.opacity(0.15), in: Circle())
                Text(title)
                    .font(.headline)
            }
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

/// A number that counts up when animated, rather than snapping to its new
/// value: `Animatable` hands us every interpolated step of `value`, so the
/// digits roll through the whole range under whatever animation is in effect.
private struct CountingNumber: View, Animatable {
    var value: Double

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        Text("\(Int(value.rounded()))")
    }
}

private struct WaterBar: View {
    let fill: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(.quaternary)
                Capsule()
                    .fill(
                        LinearGradient(
                            colors: [.cyan, .blue],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: min(max(geo.size.width * fill, fill > 0 ? 14 : 0), geo.size.width))
            }
        }
        .frame(height: 14)
    }
}

private extension AnyTransition {
    /// Cards don't fade in flat — they rise and settle, which is what makes the
    /// sequence read as one continuous reveal.
    static var recapCard: AnyTransition {
        .opacity
        .combined(with: .offset(y: 22))
        .combined(with: .scale(scale: 0.97, anchor: .top))
    }
}
