import Foundation
import UserNotifications

/// Mirrors Android's `ui.util.NotificationHelper`, with one real behavioral
/// upgrade: Android just fires a reminder every fixed 3 hours around the
/// clock. Here, reminders are confined to a waking-hours window and spaced
/// out further whenever the user actually logs water — see
/// `delayHydrationReminders(afterAddingMl:)`.
///
/// That dynamic behavior needs real bookkeeping (today's 3 reminder times are
/// persisted and can be nudged later), which rules out a single "fire forever
/// on its own" repeating trigger. Each of today's reminders is instead
/// scheduled as a one-shot `UNCalendarNotificationTrigger`, recomputed fresh
/// for the day on every foreground (`refreshHydrationScheduleIfNeeded`) — so
/// the trade-off is that the schedule only extends as the app gets opened,
/// rather than running unattended for days. Fine for an app opened to log
/// meals throughout the day; a multi-day-ahead schedule could be added later
/// if that turns out to matter.
///
/// The 21:00 evening-recap notification below has no Android counterpart at all,
/// and — since its time never moves — can be a single repeating trigger that
/// keeps firing whether or not the app gets opened.
public enum NotificationHelper {
    public static let hydrationReminderIdentifierPrefix = "scascan.hydration_reminder"
    public static let analysisCompleteIdentifier = "scascan.analysis_complete"
    public static let dailyRecapIdentifier = "scascan.daily_recap"

    /// The hour the day's books close: when the recap notification fires, and
    /// the hour before which today's recap stays locked. Both the scheduler and
    /// the UI's lock read this, so they can't drift apart.
    public static let recapHour = 21

    /// Reminders are spread across this daily window — nobody wants a
    /// hydration ping at 3am.
    private static let windowStartHour = 10
    private static let windowEndHour = 20
    private static let reminderCount = 3

    @discardableResult
    public static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// Whether notifications are actually allowed right now — for reflecting the
    /// real state back into a settings toggle after a refused prompt.
    public static func hasAuthorization() async -> Bool {
        switch await UNUserNotificationCenter.current().notificationSettings().authorizationStatus {
        case .authorized, .provisional, .ephemeral: return true
        default: return false
        }
    }

    /// Turns the hydration schedule on (or resets it): 3 reminders spread
    /// evenly across the 10:00–20:00 window, discarding any stale schedule
    /// from a previous session.
    public static func scheduleHydrationReminders(profileStore: UserProfileStore = .shared) {
        reschedule(addedMl: nil, forceFreshBaseline: true, profileStore: profileStore)
    }

    /// Call whenever water gets logged: pushes today's remaining reminder(s)
    /// later by an amount proportional to how much was just drunk relative to
    /// the user's typical quick-add amount — a small sip barely moves the
    /// next ping, a full bottle pushes it back close to a full reminder-gap's
    /// worth of time. Clamped so nothing gets pushed past the window's end.
    public static func delayHydrationReminders(afterAddingMl amount: Int, profileStore: UserProfileStore = .shared) {
        guard profileStore.waterRemindersEnabled, amount > 0 else { return }
        reschedule(addedMl: amount, forceFreshBaseline: false, profileStore: profileStore)
    }

    /// Call on every foreground. If today's schedule is stale (a new
    /// calendar day, or nothing has been computed yet), recomputes the plain
    /// equidistant baseline for today; otherwise leaves an already-applied
    /// water-delay alone.
    public static func refreshHydrationScheduleIfNeeded(profileStore: UserProfileStore = .shared) {
        guard profileStore.waterRemindersEnabled else { return }
        guard profileStore.hydrationScheduleDayKey != dayKey(for: .now) else { return }
        reschedule(addedMl: nil, forceFreshBaseline: true, profileStore: profileStore)
    }

    public static func cancelHydrationReminders() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: reminderIdentifiers)
    }

    // MARK: - Evening recap

    /// Keeps the 21:00 recap notification in sync with the user's preference.
    /// Safe (and cheap) to call on every launch/foreground: re-adding a request
    /// with the same identifier replaces it rather than stacking duplicates.
    ///
    /// Unlike the hydration reminders, this one *can* be a single repeating
    /// trigger — the time never moves — so the schedule survives indefinitely
    /// without the app being opened. The trade-off is that a repeating
    /// notification's text is fixed at scheduling time, so the body can't quote
    /// the day's actual numbers; it invites the user in instead.
    public static func refreshDailyRecap(profileStore: UserProfileStore = .shared) async {
        guard profileStore.dailyRecapEnabled else {
            cancelDailyRecap()
            return
        }
        guard await requestAuthorization() else { return }

        let content = UNMutableNotificationContent()
        content.title = "Your day, wrapped"
        content.body = "See what you ate, what you burned, and how you landed against your target."
        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: DateComponents(hour: recapHour, minute: 0), repeats: true
        )
        try? await UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: dailyRecapIdentifier, content: content, trigger: trigger)
        )
    }

    public static func cancelDailyRecap() {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [dailyRecapIdentifier])
    }

    /// Whether the recap for a given day offset (0 = today) is unlocked yet.
    /// Past days always are; today's opens at `recapHour`, since a "recap"
    /// assembled at lunchtime would just be the Log tab with extra steps.
    public static func isRecapUnlocked(offsetDays: Int, now: Date = .now) -> Bool {
        guard offsetDays == 0 else { return offsetDays < 0 }
        return Calendar.current.component(.hour, from: now) >= recapHour
    }

    /// When today's recap unlocks — used for the countdown on the locked screen.
    public static func recapUnlockDate(now: Date = .now) -> Date {
        let calendar = Calendar.current
        return calendar.date(
            bySettingHour: recapHour, minute: 0, second: 0, of: now, matchingPolicy: .nextTime
        ) ?? calendar.startOfDay(for: now).addingTimeInterval(Double(recapHour) * 3_600)
    }

    public static func postAnalysisComplete(foodName: String, calories: Int) {
        let content = UNMutableNotificationContent()
        content.title = "Analysis ready"
        content.body = "\(foodName) · \(calories) kcal — tap to add to log"
        content.sound = .default

        let request = UNNotificationRequest(identifier: analysisCompleteIdentifier, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    // MARK: - Scheduling internals

    private static var reminderIdentifiers: [String] {
        (0..<reminderCount).map { "\(hydrationReminderIdentifierPrefix).\($0)" }
    }

    private static func reschedule(addedMl: Int?, forceFreshBaseline: Bool, profileStore: UserProfileStore) {
        let now = Date()
        let calendar = Calendar.current
        let startOfToday = calendar.startOfDay(for: now)
        guard
            let windowStart = calendar.date(byAdding: .hour, value: windowStartHour, to: startOfToday),
            let windowEnd = calendar.date(byAdding: .hour, value: windowEndHour, to: startOfToday)
        else { return }

        let gap = windowEnd.timeIntervalSince(windowStart) / Double(reminderCount)
        let todayKey = dayKey(for: now)

        var times: [Date]
        if !forceFreshBaseline,
           profileStore.hydrationScheduleDayKey == todayKey,
           let stored = profileStore.hydrationScheduleTimes,
           stored.count == reminderCount {
            times = stored.map { Date(timeIntervalSince1970: $0) }
        } else {
            // Fresh baseline: `reminderCount` anchors, equidistant, starting
            // right at the window's open.
            times = (0..<reminderCount).map { windowStart.addingTimeInterval(gap * Double($0)) }
        }

        if let addedMl {
            // The user's typical serving stands in for "one full reminder
            // gap's worth of hydration" — drinking about that much pushes the
            // next ping back by roughly one gap; a smaller sip, proportionally less.
            let quickAdds = profileStore.waterQuickAddAmountsMl
            let referenceMl = quickAdds.isEmpty ? 250 : quickAdds.reduce(0, +) / quickAdds.count
            let delay = gap * (Double(addedMl) / Double(max(referenceMl, 1)))
            times = times.map { time in
                guard time > now else { return time }
                return min(time.addingTimeInterval(delay), windowEnd)
            }
        }

        profileStore.hydrationScheduleDayKey = todayKey
        profileStore.hydrationScheduleTimes = times.map(\.timeIntervalSince1970)

        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: reminderIdentifiers)

        for (index, time) in times.enumerated() where time > now {
            let content = UNMutableNotificationContent()
            content.title = "Time to hydrate!"
            content.body = "Stay on track with your water goal. Log a glass now."
            content.sound = .default

            let comps = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: time)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            let request = UNNotificationRequest(identifier: reminderIdentifiers[index], content: content, trigger: trigger)
            center.add(request)
        }
    }

    private static func dayKey(for date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return "\(c.year ?? 0)-\(c.month ?? 0)-\(c.day ?? 0)"
    }
}
