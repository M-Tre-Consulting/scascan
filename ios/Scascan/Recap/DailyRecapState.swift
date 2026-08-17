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

    /// A day's name, keeping translated words and locale-formatted dates apart:
    /// "Today" needs to reach the string catalog (a `String` handed to `Text` is
    /// never extracted, so it would ship untranslated), while a date needs
    /// `Text`'s own formatting — smuggling one through the other gives you
    /// either an English word or a junk `"%@"` catalog key.
    enum DayLabel: Equatable {
        case today
        case yesterday
        case other(Date)
    }

    var dayLabel: DayLabel { label(forOffset: offsetDays) }

    var daySubtitle: String {
        date.formatted(.dateTime.weekday(.wide).day().month(.wide))
    }

    var date: Date {
        Calendar.current.date(byAdding: .day, value: offsetDays, to: .now) ?? .now
    }

    /// Day offsets the picker offers, newest first — always deep enough to
    /// include whatever day is currently selected.
    var pickerOffsets: [Int] {
        Array(stride(from: 0, through: min(-(Self.historyDays - 1), offsetDays), by: -1))
    }

    /// Labels for the day picker, newest first.
    func label(forOffset offset: Int) -> DayLabel {
        switch offset {
        case 0: return .today
        case -1: return .yesterday
        default: return .other(Calendar.current.date(byAdding: .day, value: offset, to: .now) ?? .now)
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
