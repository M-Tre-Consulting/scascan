import Foundation

/// Mirrors Android's `data.model.MacroTargets`.
public struct MacroTargets: Codable, Hashable, Sendable {
    public var proteinG: Int
    public var carbsG: Int
    public var fatG: Int

    public init(proteinG: Int, carbsG: Int, fatG: Int) {
        self.proteinG = proteinG
        self.carbsG = carbsG
        self.fatG = fatG
    }
}
