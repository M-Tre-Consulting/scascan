import Foundation

/// One day's closed-out ledger, as shown by the evening recap screen.
///
/// This is where burned calories finally enter the arithmetic. During the day
/// the app deliberately shows a *hypothetical* target — base allowance,
/// yesterday's carry-over, the weight-trend nudge — and nothing else, so the
/// number stops drifting up and down every time Fitness syncs a few more
/// active kilocalories. The burn (and the carry-over) is instead settled once,
/// at the end of the day, as a deduction from what was eaten:
///
///     net = eaten − burned − carryOver
///
/// which is then measured against `targetKcal`. Presenting it as a deduction
/// from intake rather than an addition to the target is the whole point: "you
/// ate 2340, you burned 620, so 1720 counts" reads as a result, whereas a
/// target that quietly grows all afternoon reads as a moving goalpost.
public struct DailyRecap: Sendable, Equatable {
    /// A single logged meal, flattened to just what the recap draws.
    public struct Meal: Sendable, Equatable, Identifiable {
        public let id: UUID
        public let name: String
        public let servingSize: String
        public let kcal: Double
        public let loggedAt: Date

        public init(id: UUID, name: String, servingSize: String, kcal: Double, loggedAt: Date) {
            self.id = id
            self.name = name
            self.servingSize = servingSize
            self.kcal = kcal
            self.loggedAt = loggedAt
        }
    }

    public enum Verdict: Sendable, Equatable {
        /// Landed inside the healthy band around the target.
        case onTarget
        /// Meaningfully above the target once activity was credited.
        case over
        /// Far enough below the target to be worth flagging — under-eating is
        /// a failure mode too, not a bonus.
        case under
    }

    public var dayStart: Date
    /// 0 = today, -1 = yesterday, … Matches `LogRepository`'s day-offset convention.
    public var offsetDays: Int

    public var meals: [Meal]
    public var consumedKcal: Double
    public var burnedKcal: Double
    /// Carry-over from the previous day: positive = unused allowance credited
    /// forward, negative = an overspend still owed. Subtracted from intake, so
    /// a credit lowers the net and a debt raises it.
    public var carryOverKcal: Int
    /// The day's allowance to measure `netKcal` against: the base target plus
    /// the weight-trend correction, floored the same way the live target is.
    /// Deliberately excludes both the burn and the carry-over — those are the
    /// two deductions on the intake side of the ledger.
    public var targetKcal: Int
    /// The weight-trend correction already folded into `targetKcal`, kept
    /// separately only so the UI can explain where the number came from.
    public var trendKcal: Int

    public var waterMl: Int
    public var waterTargetMl: Int

    public var proteinG: Double
    public var carbsG: Double
    public var fatG: Double

    public init(
        dayStart: Date,
        offsetDays: Int,
        meals: [Meal],
        consumedKcal: Double,
        burnedKcal: Double,
        carryOverKcal: Int,
        targetKcal: Int,
        trendKcal: Int,
        waterMl: Int,
        waterTargetMl: Int,
        proteinG: Double,
        carbsG: Double,
        fatG: Double
    ) {
        self.dayStart = dayStart
        self.offsetDays = offsetDays
        self.meals = meals
        self.consumedKcal = consumedKcal
        self.burnedKcal = burnedKcal
        self.carryOverKcal = carryOverKcal
        self.targetKcal = targetKcal
        self.trendKcal = trendKcal
        self.waterMl = waterMl
        self.waterTargetMl = waterTargetMl
        self.proteinG = proteinG
        self.carbsG = carbsG
        self.fatG = fatG
    }

    /// What the day actually cost, after activity and the previous day's balance.
    public var netKcal: Double {
        consumedKcal - burnedKcal - Double(carryOverKcal)
    }

    /// Net minus target: positive = over, negative = room left.
    public var deltaKcal: Double { netKcal - Double(targetKcal) }

    public var isEmpty: Bool { meals.isEmpty && waterMl == 0 }

    /// A 100 kcal grace band above the target (roughly one piece of fruit —
    /// below the precision anyone's food log actually has), and everything
    /// under 75% of it counted as under-eating rather than a win.
    public var verdict: Verdict {
        guard targetKcal > 0 else { return .onTarget }
        if netKcal > Double(targetKcal) + 100 { return .over }
        if netKcal < Double(targetKcal) * 0.75 { return .under }
        return .onTarget
    }

    /// How full the target is, clamped for ring/bar drawing.
    public var targetFraction: Double {
        guard targetKcal > 0 else { return 0 }
        return min(max(netKcal / Double(targetKcal), 0), 1)
    }

    public var waterFraction: Double {
        guard waterTargetMl > 0 else { return 0 }
        return min(max(Double(waterMl) / Double(waterTargetMl), 0), 1)
    }
}
