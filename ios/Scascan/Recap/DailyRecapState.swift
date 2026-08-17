import Foundation
import Observation
import ScaScanKit

/// Drives `DailyRecapView`: which day is being looked at, its closed-out
/// ledger, and whether that day's books are closed yet.
@MainActor
@Observable
final class DailyRecapState {
    /// 0 = today, -1 = yesterday, … Never positive.
    private(set) var offsetDays: Int
    private(set) var recap: DailyRecap?
    private(set) var isLoading = false

    /// How far back the day picker offers to go. A month is plenty — beyond
    /// that a recap is history, not feedback.
    static let historyDays = 30

    private let repository: LogRepository

    init(repository: LogRepository, offsetDays: Int = 0) {
        self.repository = repository
        self.offsetDays = min(offsetDays, 0)
    }

    var isUnlocked: Bool { NotificationHelper.isRecapUnlocked(offsetDays: offsetDays) }
    var unlockDate: Date { NotificationHelper.recapUnlockDate() }

    var isToday: Bool { offsetDays == 0 }

    var dayTitle: String {
        switch offsetDays {
        case 0: return "Today"
        case -1: return "Yesterday"
        default: return date.formatted(.dateTime.weekday(.wide))
        }
    }

    var daySubtitle: String {
        date.formatted(.dateTime.weekday(.wide).day().month(.wide))
    }

    var date: Date {
        Calendar.current.date(byAdding: .day, value: offsetDays, to: .now) ?? .now
    }

    /// Labels for the day picker, newest first.
    func label(forOffset offset: Int) -> String {
        switch offset {
        case 0: return "Today"
        case -1: return "Yesterday"
        default:
            let day = Calendar.current.date(byAdding: .day, value: offset, to: .now) ?? .now
            return day.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
        }
    }

    func select(offsetDays offset: Int) {
        guard offset != offsetDays else { return }
        offsetDays = min(offset, 0)
        recap = nil
    }

    func load() async {
        guard isUnlocked else { return }
        isLoading = true
        recap = try? await repository.dailyRecap(forDateOffset: offsetDays)
        isLoading = false
    }
}
