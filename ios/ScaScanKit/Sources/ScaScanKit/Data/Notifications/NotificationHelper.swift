import Foundation
import UserNotifications

/// Mirrors Android's `ui.util.NotificationHelper`, with one real architectural
/// simplification: Android schedules hydration reminders through a WorkManager
/// `PeriodicWorkRequest` because it has no OS-level "repeat this notification"
/// primitive — a worker has to wake up and post each one. iOS does have that
/// primitive (`UNTimeIntervalNotificationTrigger(repeats: true)`), so the
/// reminder is scheduled once and the system fires it forever on its own —
/// no background wake-up, no BGTaskScheduler, and it survives app termination
/// and relaunch without any extra bookkeeping.
public enum NotificationHelper {
    public static let hydrationReminderIdentifier = "scascan.hydration_reminder"
    public static let analysisCompleteIdentifier = "scascan.analysis_complete"

    @discardableResult
    public static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// `interval` must be at least 60 seconds (`UNTimeIntervalNotificationTrigger`'s minimum).
    public static func scheduleHydrationReminder(interval: TimeInterval) {
        let content = UNMutableNotificationContent()
        content.title = "Time to hydrate!"
        content.body = "Stay on track with your water goal. Log a glass now."
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(interval, 60), repeats: true)
        let request = UNNotificationRequest(identifier: hydrationReminderIdentifier, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    public static func cancelHydrationReminder() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [hydrationReminderIdentifier])
    }

    public static func postAnalysisComplete(foodName: String, calories: Int) {
        let content = UNMutableNotificationContent()
        content.title = "Analysis ready"
        content.body = "\(foodName) · \(calories) kcal — tap to add to log"
        content.sound = .default

        let request = UNNotificationRequest(identifier: analysisCompleteIdentifier, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
