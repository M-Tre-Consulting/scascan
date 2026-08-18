import Foundation
import UserNotifications
import ScaScanKit

/// Routes notification taps into the app: the 21:00 recap opens that day's
/// recap, a hydration reminder opens the Log tab.
///
/// `UNUserNotificationCenterDelegate` makes no promise about which thread it
/// calls back on, and this target compiles with `SWIFT_DEFAULT_ACTOR_ISOLATION
/// = MainActor` — so the delegate methods are explicitly `nonisolated`, pull
/// the plain values they need out of the (non-`Sendable`) response right there,
/// and hop to the main actor themselves. Letting the compiler infer main-actor
/// isolation on a callback the framework invokes from its own queue is exactly
/// how you get a `_dispatch_assert_queue_fail` crash.
@MainActor
final class NotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    private weak var container: AppContainer?

    /// Must be constructed during launch: a tap that *launched* the app is only
    /// delivered if a delegate is already set by the time launching finishes.
    init(container: AppContainer) {
        self.container = container
        super.init()
        UNUserNotificationCenter.current().delegate = self
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let identifier = response.notification.request.identifier
        let deliveredAt = response.notification.date
        completionHandler()

        Task { @MainActor [weak self] in
            self?.route(identifier: identifier, deliveredAt: deliveredAt)
        }
    }

    /// Recaps and hydration pings are worth showing even with the app open —
    /// without this, a foreground notification is silently dropped.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    private func route(identifier: String, deliveredAt: Date) {
        guard let container else { return }

        if identifier == NotificationHelper.dailyRecapIdentifier {
            // The recap notification repeats daily, so it can't carry the day it
            // belongs to — derive it from when this one was actually delivered.
            // Tapping last night's notification over breakfast should still open
            // *last night's* recap, not an empty one for the day just started.
            container.deepLinkTab = 1
            container.deepLinkRoute = .recap(Self.dayOffset(of: deliveredAt))
            return
        }

        if identifier.hasPrefix(NotificationHelper.hydrationReminderIdentifierPrefix) {
            container.deepLinkTab = 1
        }
    }

    /// Whole calendar days from today back to `date` (0 = today, -1 = yesterday).
    private static func dayOffset(of date: Date) -> Int {
        let calendar = Calendar.current
        let from = calendar.startOfDay(for: .now)
        let to = calendar.startOfDay(for: date)
        return min(calendar.dateComponents([.day], from: from, to: to).day ?? 0, 0)
    }
}
