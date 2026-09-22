import Foundation

/// Swift port of Android's `GameplayTableUi.companionCardMetrics`.
/// Values are expressed in density-independent points so the table keeps the
/// same proportions on iPhone while adapting from 5 through 15 players.
public struct ClassicCompanionMetrics: Equatable, Sendable {
    public var columnWidth: Int
    public var minimumCardWidth: Int
    public var itemHeight: Int
    public var itemGap: Int
    public var avatarSize: Int
    public var cardWidth: Int
    public var cardHeight: Int
    public var nameHeight: Int
    public var nameTextSize: Double
    public var scrollEnabled: Bool

    public static func androidCompatible(
        totalPlayers: Int,
        availableHeight: Int = 376,
        availableWidth: Int? = nil
    ) -> Self {
        let playersPerSide = (max(totalPlayers, 2) - 1 + 1) / 2
        let scrollEnabled = totalPlayers >= 13
        let base: Self = switch playersPerSide {
        case 0...2:
            .init(columnWidth: 112, minimumCardWidth: 104, itemHeight: 106, itemGap: 4,
                  avatarSize: 22, cardWidth: 54, cardHeight: 86, nameHeight: 18,
                  nameTextSize: 10, scrollEnabled: false)
        case 3:
            .init(columnWidth: 104, minimumCardWidth: 96, itemHeight: 89, itemGap: 3,
                  avatarSize: 20, cardWidth: 45, cardHeight: 72, nameHeight: 17,
                  nameTextSize: 9.5, scrollEnabled: false)
        case 4:
            .init(columnWidth: 94, minimumCardWidth: 86, itemHeight: 74, itemGap: 2,
                  avatarSize: 18, cardWidth: 36, cardHeight: 58, nameHeight: 16,
                  nameTextSize: 9, scrollEnabled: false)
        case 5:
            .init(columnWidth: 86, minimumCardWidth: 78, itemHeight: 65, itemGap: 2,
                  avatarSize: 16, cardWidth: 31, cardHeight: 50, nameHeight: 15,
                  nameTextSize: 8.5, scrollEnabled: false)
        default:
            .init(columnWidth: 78, minimumCardWidth: 70, itemHeight: 62, itemGap: 2,
                  avatarSize: 15, cardWidth: 29, cardHeight: 46, nameHeight: 14,
                  nameTextSize: 8, scrollEnabled: scrollEnabled)
        }

        var fitted = base
        if availableHeight > 0 {
            let usableHeight = max(availableHeight - base.itemGap * (playersPerSide - 1), playersPerSide)
            let idealItemHeight = usableHeight / playersPerSide
            if idealItemHeight > base.itemHeight, availableWidth != nil {
                let grownItemHeight = min(idealItemHeight, 132)
                let grownCardHeight = max(grownItemHeight - base.nameHeight, base.cardHeight)
                fitted = fitted.scaled(
                    by: Double(grownCardHeight) / Double(base.cardHeight),
                    itemHeight: grownItemHeight,
                    allowGrowth: true
                )
            } else if idealItemHeight < base.itemHeight, !scrollEnabled {
                let fittedCardHeight = max(idealItemHeight - base.nameHeight, 24)
                let fittedCardWidth = max(Int(Double(base.cardWidth) * Double(fittedCardHeight) /
                                              Double(base.cardHeight)), 22)
                fitted.itemHeight = idealItemHeight
                fitted.avatarSize = min(base.avatarSize, max(Int(Double(fittedCardWidth) * 0.42), 12))
                fitted.cardWidth = fittedCardWidth
                fitted.cardHeight = fittedCardHeight
                fitted.nameTextSize = min(base.nameTextSize, idealItemHeight < 70 ? 6.5 : base.nameTextSize)
            }
        }

        if let availableWidth, availableWidth > 0, availableWidth < fitted.columnWidth {
            fitted = fitted.fitted(toWidth: availableWidth)
        }

        if availableWidth != nil, availableHeight > 0, !scrollEnabled, playersPerSide <= 3 {
            fitted = fitted.withPortraitSpacing(playersPerSide: playersPerSide, availableHeight: availableHeight)
        }
        return fitted
    }

    /// Keeps Android's responsive breakpoints while avoiding oversized cards on
    /// tall, narrow iPhones when only two to four companions occupy each side.
    public func cappedCardWidth(_ maximumWidth: Int) -> Self {
        let safeMaximum = max(maximumWidth, 20)
        guard cardWidth > safeMaximum else { return self }
        let scale = Double(safeMaximum) / Double(cardWidth)
        var copy = self
        copy.cardWidth = safeMaximum
        copy.cardHeight = max(Int(Double(cardHeight) * scale), 32)
        copy.avatarSize = min(avatarSize, max(Int(Double(safeMaximum) * 0.52), 12))
        return copy
    }

    private func scaled(by scale: Double, itemHeight: Int?, allowGrowth: Bool) -> Self {
        let safeScale = allowGrowth ? min(max(scale, 1), 1.9) : min(max(scale, 0.35), 1)
        var copy = self
        copy.columnWidth = max(Int(Double(columnWidth) * safeScale), 48)
        copy.minimumCardWidth = max(Int(Double(minimumCardWidth) * safeScale), 40)
        copy.itemHeight = itemHeight ?? max(Int(Double(self.itemHeight) * safeScale), 40)
        copy.avatarSize = max(Int(Double(avatarSize) * safeScale), 12)
        copy.cardWidth = max(Int(Double(cardWidth) * safeScale), 20)
        copy.cardHeight = max(Int(Double(cardHeight) * safeScale), 32)
        copy.nameTextSize = min(max(nameTextSize * safeScale, 6.5), 15)
        return copy
    }

    private func fitted(toWidth availableWidth: Int) -> Self {
        let targetWidth = max(availableWidth, 48)
        let fittedCardWidth = min(cardWidth, max(targetWidth - 8, 20))
        let scale = Double(fittedCardWidth) / Double(max(cardWidth, 1))
        var copy = self
        copy.columnWidth = targetWidth
        copy.minimumCardWidth = targetWidth
        copy.cardWidth = fittedCardWidth
        copy.cardHeight = max(Int(Double(cardHeight) * scale), 32)
        copy.avatarSize = min(avatarSize, max(Int(Double(fittedCardWidth) * 0.52), 12))
        copy.nameTextSize = min(max(nameTextSize * scale, 6.5), nameTextSize)
        return copy
    }

    private func withPortraitSpacing(playersPerSide: Int, availableHeight: Int) -> Self {
        guard playersPerSide > 1 else { return self }
        let freeHeight = availableHeight - itemHeight * playersPerSide
        guard freeHeight > itemGap else { return self }
        var copy = self
        copy.itemGap = if playersPerSide == 2 {
            min(max(Int(Double(freeHeight) * 0.08), itemGap), 28)
        } else {
            min(max(Int(Double(freeHeight) * 0.05), itemGap), 16)
        }
        return copy
    }
}
