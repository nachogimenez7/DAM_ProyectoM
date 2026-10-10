import SwiftUI
import TraidoresCore

/// Everything the profile window of one player shows (Android's `PlayerProfile`). It never
/// carries the role dealt in the match: «rol favorito» is a profile choice, independent of it.
struct PlayerProfileSnapshot: Identifiable, Equatable {
    enum Kind: Equatable { case me, bot, player }

    var id: String
    var name: String
    var kind: Kind
    var publicId: String?
    var bio: String?
    /// Animal avatar key (`avatar_puma`).
    var avatarKey: String
    var photoURL: URL?
    var photoData: Data?
    var bannerKey: String?
    /// Android's role key (`pampa_policia`), shown with the role artwork.
    var favoriteRoleKey: String?
    var achievementIDs: [String] = []
    var emoteIDs: [String] = []
    var matches: Int?
    var wins: Int?
    var styleRaw = "classic"
    /// Only meaningful for other online players (see `ProfileModerationActions`).
    var isMuted = false
}

/// Actions on another online player, shown at the bottom of their profile window.
struct ProfileModerationActions {
    /// The caller updates the store and the snapshot (`isMuted`); the window stays open.
    let toggleMute: () -> Void
    /// The caller closes the window and opens its report dialog.
    let report: () -> Void
}

extension PlayerProfileSnapshot {
    /// Your own window: the local profile plus, when there is an account, its public number.
    @MainActor
    static func own(name: String, storedProfile: Data, theme: String, emoteIDs: String, publicId: String?) -> Self {
        let profile = LocalMenuProfile.load(storedProfile)
        return PlayerProfileSnapshot(
            id: "me", name: name, kind: .me, publicId: publicId, bio: profile.bio,
            avatarKey: profile.avatar,
            photoURL: OnlineContract.photoURL(profile.profilePhotoURL, emulatorOrigin: FirebaseSetup.storageEmulatorOrigin),
            photoData: profile.photoData, bannerKey: profile.banner,
            favoriteRoleKey: OnlineAvatarArt.key(for: profile.favorite),
            emoteIDs: emoteIDs.split(separator: ",").map(String.init), styleRaw: theme)
    }
}

/// Port of Android's `BotProfileFactory`. A bot belongs to the fixed squad (see
/// AVATARES_ANIMALES.md), so its profile follows its animal and survives renames; the numbers use
/// the same seed as Android, so a bot shows the same figures on both platforms.
enum BotProfileCatalog {
    private struct Entry {
        let bio: String
        let banner: String
        let role: String
        var achievements: [String]? = nil
        var emotes: [String]? = nil
    }

    private static let squad: [String: Entry] = [
        "Thiago": Entry(bio: "Siempre habla primero y después revisa si tenía razón.", banner: "pampa", role: "pampa_policia",
                        achievements: ["profile_created", "expel_all_killers"],
                        emotes: ["gaucho_sospechoso", "gaucho_enojado", "premium_mate", "griego_contento"]),
        "Mora": Entry(bio: "Escucha más de lo que dice. Si te mira raro, algo vio.", banner: "grecia", role: "grecia_oraculo",
                      achievements: ["profile_created", "total_wins_50"],
                      emotes: ["griego_sospechoso", "griego_triste", "premium_dormida", "griego_enojado"]),
        "Lautaro": Entry(bio: "No acusa fuerte: deja frases cortas y espera que el pueblo se prenda fuego solo.",
                         banner: "pampa", role: "pampa_asesino",
                         achievements: ["assassin_kills_25", "profile_created"],
                         emotes: ["gaucho_enojado", "gaucho_sospechoso", "premium_mate", "griego_contento"]),
        "Valen": Entry(bio: "Suele votar tarde, pero pocas veces vota sin motivo.", banner: "grecia", role: "grecia_medico"),
        "Rami": Entry(bio: "Tiene cara de inocente y estadísticas que no ayudan a creerle.", banner: "medieval", role: "medieval_espia"),
        "Juli": Entry(bio: "Defiende al que nadie defiende y después pregunta por qué sospechan.", banner: "grecia", role: "grecia_alcalde"),
        "Santi": Entry(bio: "Cuando todos gritan, él cuenta votos.", banner: "medieval", role: "medieval_policia"),
        "Mili": Entry(bio: "Le gusta cambiar de opinión justo antes de votar.", banner: "pampa", role: "pampa_mercenario"),
        "Toto": Entry(bio: "Juega como si supiera algo. A veces es verdad.", banner: "pampa", role: "pampa_payador"),
        "Agus": Entry(bio: "Se ríe en los momentos equivocados.", banner: "medieval", role: "medieval_bufon"),
        "Bruno": Entry(bio: "Pide pruebas, recibe pruebas y pide pruebas mejores.", banner: "medieval", role: "medieval_aldeano"),
        "Lola": Entry(bio: "Nunca parece apurada, ni cuando la acusan tres a la vez.", banner: "grecia", role: "grecia_desertor"),
        "Fede": Entry(bio: "Vota con seguridad incluso cuando no tiene ninguna.", banner: "medieval", role: "medieval_asesino"),
        "Cata": Entry(bio: "Tiene memoria para cada contradicción del pueblo.", banner: "pampa", role: "pampa_alcalde")
    ]

    private static let defaultEmotes = ["griego_enojado", "griego_triste", "griego_contento", "griego_sospechoso"]

    static func profile(for player: ClassicPlayer) -> PlayerProfileSnapshot {
        profile(name: player.name, avatarKey: player.avatarKey, seat: player.id, id: "bot:\(player.id)")
    }

    /// `seat` is the 1-based position at the table or lobby; it only decides the squad member
    /// when the bot has no animal of its own.
    static func profile(name: String, avatarKey: String?, seat: Int, id: String) -> PlayerProfileSnapshot {
        let names = ClassicGame.defaultBotNames
        let key = AnimalAvatarCatalog.normalize(avatarKey ?? AnimalAvatarCatalog.keys[max(0, seat - 1) % 14])
        let slot = AnimalAvatarCatalog.keys.firstIndex(of: key).flatMap { $0 < names.count ? $0 : nil }
            ?? max(0, seat - 1) % names.count
        let squadName = names[slot]
        let entry = squad[squadName] ?? squad["Thiago"]!
        let seed = stableSeed(squadName)
        let allAchievements = AndroidMenuReference.content.achievements.map(\.id)
        let allEmotes = AndroidMenuReference.content.emotes.map(\.id)
        let achievements = entry.achievements ?? stableSlice(allAchievements, seed: seed, count: 2)
        let emotes = entry.emotes ?? stableSlice(defaultEmotes + allEmotes, seed: seed, count: 4)
        let matches = 20 + seed % 381
        let rate = 35 + (seed / 13) % 31
        let wins = min(max(Int((Double(matches) * Double(rate) / 100).rounded()), 0), matches)
        return PlayerProfileSnapshot(
            id: id, name: name, kind: .bot,
            publicId: String(70_000 + seed % 30_000), bio: entry.bio, avatarKey: key,
            bannerKey: entry.banner, favoriteRoleKey: entry.role,
            achievementIDs: Array(NSOrderedSet(array: achievements).compactMap { $0 as? String }.prefix(3)),
            emoteIDs: emotes, matches: matches, wins: wins)
    }

    /// Android's `stableSeed`: 32-bit hash over the lowercased name, kept non-negative.
    static func stableSeed(_ value: String) -> Int {
        var hash: Int32 = 23
        for unit in value.lowercased().utf16 { hash = 31 &* hash &+ Int32(unit) }
        return Int(hash & Int32.max)
    }

    private static func stableSlice(_ source: [String], seed: Int, count: Int) -> [String] {
        guard !source.isEmpty else { return [] }
        var selected: [String] = []
        var cursor = seed
        while selected.count < count, selected.count < source.count {
            let item = source[cursor % source.count]
            if !selected.contains(item) { selected.append(item) }
            cursor = cursor / 3 + 7
        }
        return selected
    }
}

/// The profile window opened by long-pressing (or tapping) a player's card, or by tapping your
/// own name. One card for everyone: header, favourite role, featured achievements, emotes and
/// figures. Sections without data are left out instead of showing placeholders.
struct PlayerProfileCard: View {
    let profile: PlayerProfileSnapshot
    var moderation: ProfileModerationActions? = nil
    let onClose: () -> Void
    @Environment(\.reduceAnimations) private var reduceMotion
    @State private var shown = false

    private var style: ProfileStyle { ProfileStyle(rawValue: profile.styleRaw) ?? .classic }
    private var accent: Color { style.primary }
    private var favoriteRole: GuideRole? {
        guard let key = profile.favoriteRoleKey else { return nil }
        let asset = OnlineAvatarArt.asset(for: key)
        return AndroidMenuReference.content.maps.flatMap(\.roles).first { $0.image == asset }
    }
    private var achievements: [ProfileAchievementContent] {
        profile.achievementIDs.compactMap { id in AndroidMenuReference.content.achievements.first { $0.id == id } }
    }
    private var emotes: [ProfileEmoteContent] {
        profile.emoteIDs.compactMap { id in AndroidMenuReference.content.emotes.first { $0.id == id } }
    }

    var body: some View {
        ZStack {
            Color.black.opacity(shown ? 0.62 : 0.01).ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: onClose)
                .accessibilityHidden(true)
            GeometryReader { geometry in
                ScrollView {
                    card
                        .padding(.vertical, 16)
                        .frame(maxWidth: .infinity, minHeight: geometry.size.height)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
            .padding(.horizontal, 20)
            .opacity(shown ? 1 : 0)
            .scaleEffect(shown || reduceMotion ? 1 : 0.95)
        }
        .onAppear { withAnimation(.easeOut(duration: 0.2)) { shown = true } }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier("table.playerProfile")
    }

    private var card: some View {
        VStack(spacing: 12) {
            header
            if let bio = profile.bio, !bio.isEmpty {
                Text("“\(bio)”").font(.subheadline.italic()).foregroundStyle(TraidoresTheme.text)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            }
            if let role = favoriteRole { favoriteRoleRow(role) }
            if !achievements.isEmpty { achievementsRow }
            if !emotes.isEmpty { emotesRow }
            statsRow
            if let moderation { moderationRow(moderation) }
            Button("CERRAR", action: onClose)
                .buttonStyle(GameDialogButtonStyle(strong: true))
                .accessibilityIdentifier("table.playerProfile.close")
                .padding(.top, 2)
        }
        .padding(EdgeInsets(top: 12, leading: 14, bottom: 14, trailing: 14))
        .frame(maxWidth: 400)
        // The style's surface is translucent in some styles and the screen below showed through,
        // so an opaque base goes behind it (the later `.background` sits behind the earlier one).
        .background(style.surface, in: RoundedRectangle(cornerRadius: 18))
        .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(accent, lineWidth: 1.5))
    }

    private var header: some View {
        VStack(spacing: 6) {
            ZStack(alignment: .bottom) {
                VStack(spacing: 0) {
                    Group {
                        if let banner = profile.bannerKey, UIImage(named: "profile_banner_\(banner)") != nil {
                            Image("profile_banner_\(banner)").resizable().scaledToFill()
                        } else {
                            LinearGradient(colors: [accent.opacity(0.35), style.surface],
                                           startPoint: .leading, endPoint: .trailing)
                        }
                    }
                    .frame(height: 84).frame(maxWidth: .infinity).clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .accessibilityHidden(true)
                    Spacer().frame(height: 40)
                }
                ProfilePortrait(image: AnimalAvatarCatalog.normalize(profile.avatarKey),
                                photoData: profile.photoData, photoURL: profile.photoURL)
                    .frame(width: 84, height: 84)
                    .overlay(Circle().stroke(accent, lineWidth: 3))
                    .shadow(color: .black.opacity(0.5), radius: 6, y: 3)
            }
            .frame(height: 124)
            Text(profile.name).font(TraidoresTheme.title(24, relativeTo: .title2)).foregroundStyle(TraidoresTheme.gold)
                .lineLimit(2).minimumScaleFactor(0.7).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: 8) {
                Text(subtitle).font(.caption.bold()).tracking(1).foregroundStyle(accent)
                if let id = profile.publicId {
                    Text("#\(id)").font(.caption).foregroundStyle(TraidoresTheme.secondary)
                }
            }
        }
    }

    private var subtitle: String {
        switch profile.kind {
        case .me: "TU PERFIL"
        case .bot: "JUGADOR DE LA MESA"
        case .player: "JUGADOR"
        }
    }

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption.bold()).tracking(1).foregroundStyle(accent)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(accent.opacity(0.07), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent.opacity(0.3)))
    }

    private func favoriteRoleRow(_ role: GuideRole) -> some View {
        section("ROL FAVORITO") {
            HStack(spacing: 10) {
                ProfilePortrait(image: role.image).frame(width: 44, height: 44)
                    .overlay(Circle().stroke(accent.opacity(0.6), lineWidth: 1.5))
                Text(role.title).font(.subheadline.bold()).foregroundStyle(TraidoresTheme.text)
                Spacer(minLength: 0)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Rol favorito: \(role.title)")
        }
    }

    private var achievementsRow: some View {
        section("LOGROS DESTACADOS") {
            VStack(alignment: .leading, spacing: 6) {
                ForEach(achievements) { item in
                    let tone = rarityColor(item.rarity)
                    HStack(spacing: 8) {
                        Image(systemName: "medal.fill").foregroundStyle(tone).frame(width: 24)
                            .accessibilityHidden(true)
                        Text(item.title).font(.footnote.bold()).foregroundStyle(TraidoresTheme.text)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }

    private func rarityColor(_ rarity: String) -> Color {
        switch rarity {
        case "GOLD": Color(hex: "#FFF2A3")
        case "SILVER": Color(hex: "#DCEAFF")
        default: Color(hex: "#F0A35A")
        }
    }

    private var emotesRow: some View {
        section("EMOTES") {
            HStack(spacing: 8) {
                ForEach(emotes) { emote in
                    ProfileEmoteImage(emote: emote).frame(width: 52, height: 52)
                        .padding(3)
                        .background(Color(hex: "#2A2318"), in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: emote.tone), lineWidth: 1.5))
                        .accessibilityLabel(emote.title)
                }
                Spacer(minLength: 0)
            }
        }
    }

    private var statsRow: some View {
        section("ESTADÍSTICAS") {
            if let matches = profile.matches, let wins = profile.wins {
                HStack(spacing: 8) {
                    stat("PARTIDAS", "\(matches)")
                    stat("VICTORIAS", "\(wins)")
                    stat("EFECTIVIDAD", matches > 0 ? "\(wins * 100 / matches)%" : "0%")
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(matches) partidas, \(wins) victorias")
            } else {
                Text("Sin datos todavía").font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
        }
    }

    private func moderationRow(_ actions: ProfileModerationActions) -> some View {
        section("ACCIONES SOBRE ESTE JUGADOR") {
            VStack(spacing: 8) {
                Button(action: actions.toggleMute) {
                    Text(profile.isMuted ? "VOLVER A ESCUCHAR" : "SILENCIAR PARA MÍ")
                }
                .buttonStyle(GameDialogButtonStyle(strong: false))
                .accessibilityIdentifier("table.playerProfile.mute")
                Text(profile.isMuted ? "No ves su chat. Solo lo ves vos."
                                     : "Deja de mostrarte su chat. Solo lo ves vos.")
                    .font(.caption2).foregroundStyle(TraidoresTheme.secondary)
                    .frame(maxWidth: .infinity, alignment: .center).multilineTextAlignment(.center)
                Button(action: actions.report) {
                    Text("REPORTAR")
                        .font(.subheadline.bold()).tracking(0.6)
                        .foregroundStyle(Color(hex: "#FFB4AB"))
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(Color(hex: "#351616"), in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: "#8F2633")))
                        .contentShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("table.playerProfile.report")
            }
        }
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.title3.bold()).foregroundStyle(TraidoresTheme.text)
            Text(label).font(.system(size: 9, weight: .bold)).tracking(0.6).foregroundStyle(TraidoresTheme.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
