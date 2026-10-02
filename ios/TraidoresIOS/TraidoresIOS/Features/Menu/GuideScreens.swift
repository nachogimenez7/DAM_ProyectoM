import SwiftUI

private func guideColor(_ team: String) -> Color {
    switch team {
    case "Pueblo": Color(red: 0.56, green: 0.80, blue: 0.57)
    case "Traidores": Color(red: 0.88, green: 0.45, blue: 0.43)
    default: TraidoresTheme.gold
    }
}

struct RolesGuideView: View {
    @State private var selectedMap = "medieval"
    @State private var selectedRole: GuideRole?
    private var map: GuideMap {
        AndroidMenuReference.content.maps.first { $0.id == selectedMap }!
    }

    var body: some View {
        MenuPage(title: "ROLES") {
            ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(AndroidMenuReference.content.maps) { item in
                        Button {
                            selectedRole = nil
                            selectedMap = item.id
                        } label: {
                            VStack(spacing: 8) {
                                Image(item.image).resizable().scaledToFill()
                                    .frame(width: 126, height: 82).clipped()
                                Text(item.shortTitle).font(.subheadline.bold())
                            }
                            .padding(8)
                            .selectionFrame(selectedMap == item.id, cornerRadius: 10)
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("roles.map.\(item.id)")
                        .id(item.id)
                    }
                }
            }
            .onChange(of: selectedMap) { _, value in
                proxy.scrollTo(value, anchor: .center)
            }
            }
            VStack(alignment: .leading, spacing: 8) {
                Text(map.title).font(TraidoresTheme.title(24)).foregroundStyle(TraidoresTheme.gold)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("roles.mapTitle")
                Text(map.era).font(.caption.bold()).foregroundStyle(TraidoresTheme.secondary)
                Text(map.description).font(.subheadline)
                Text("Rol exclusivo: \(map.exclusive)").font(.subheadline.bold())
                    .foregroundStyle(TraidoresTheme.gold)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border))
            Text("ROLES DEL MAPA").font(TraidoresTheme.title(17, relativeTo: .headline))
                .foregroundStyle(TraidoresTheme.gold)
                .accessibilityAddTraits(.isHeader)
                .readableOnArtwork()
            ForEach(map.roles) { role in
                Button { selectedRole = role } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(role.image).resizable().scaledToFit().frame(width: 92)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        VStack(alignment: .leading, spacing: 7) {
                            Text(role.title).font(TraidoresTheme.title(22)).foregroundStyle(TraidoresTheme.gold)
                            Text(role.team.uppercased()).font(.caption.bold()).foregroundStyle(guideColor(role.team))
                            Text(role.story).font(.subheadline).lineLimit(4)
                            Text("Desde \(role.minimum) jugadores · Tocá para ver la ficha")
                                .font(.caption).foregroundStyle(TraidoresTheme.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(12)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("roles.card.\(role.id)")
            }
        }
        .sheet(item: $selectedRole) { role in
            MenuPage(title: role.title.uppercased()) {
                Image(role.image).resizable().scaledToFit().frame(maxHeight: 300)
                    .frame(maxWidth: .infinity)
                Text(role.team.uppercased()).font(.headline).foregroundStyle(guideColor(role.team))
                InformationCard(title: "Su historia", message: role.story)
                InformationCard(title: "Su función", message: role.function)
                Text("Desde \(role.minimum) jugadores").foregroundStyle(TraidoresTheme.secondary)
                Button("CERRAR") { selectedRole = nil }
                    .buttonStyle(TraidoresButtonStyle(prominent: false))
                    .accessibilityIdentifier("roles.close")
            }
            .presentationDragIndicator(.visible)
        }
    }
}

struct HelpView: View {
    @State private var expanded: String?
    @State private var expandedRole: String?
    @State private var showingTutorial = false

    var body: some View {
        MenuPage(title: "AYUDA") {
            Text("Todo lo necesario para entrar al pueblo y sobrevivir a sus sospechas.")
                .foregroundStyle(TraidoresTheme.text).readableOnArtwork()
            Button("VER TUTORIAL") { showingTutorial = true }
                .buttonStyle(TraidoresButtonStyle(prominent: true))
                .accessibilityIdentifier("help.tutorial")
            Text("El modo online y algunos roles todavía no están disponibles en esta versión.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary).readableOnArtwork()
            ForEach(AndroidMenuReference.content.help) { section in
                VStack(alignment: .leading, spacing: 12) {
                    Button {
                        expanded = expanded == section.id ? nil : section.id
                    } label: {
                        HStack {
                            Text(section.title).font(TraidoresTheme.title(17, relativeTo: .headline))
                            Spacer()
                            Image(systemName: expanded == section.id ? "minus" : "plus")
                        }
                        .foregroundStyle(TraidoresTheme.gold)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("help.section.\(section.id)")
                    .accessibilityValue(expanded == section.id ? "Expandida" : "Contraída")
                    if expanded == section.id {
                        if section.id == "Roles" {
                            roleAdvice
                        } else {
                            Text(section.body).font(.subheadline).fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("help.body.\(section.id)")
                        }
                    }
                }
                .padding(14)
                .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border))
            }
        }
        .sheet(isPresented: $showingTutorial) { MenuTutorialView() }
    }

    private var roleAdvice: some View {
        let roles = AndroidMenuReference.content.maps.flatMap(\.roles)
        let unique = roles.reduce(into: [GuideRole]()) { result, role in
            if !result.contains(where: { $0.id == role.id }) { result.append(role) }
        }
        return VStack(alignment: .leading, spacing: 10) {
            ForEach(unique) { role in
                VStack(alignment: .leading, spacing: 8) {
                    Button {
                        expandedRole = expandedRole == role.id ? nil : role.id
                    } label: {
                        HStack {
                            Text(role.id == "policia" ? "Detective / Comisario" : role.title)
                            Spacer()
                            Image(systemName: expandedRole == role.id ? "minus" : "plus")
                        }
                        .font(.subheadline.bold()).foregroundStyle(guideColor(role.team))
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("help.role.\(role.id)")
                    if expandedRole == role.id {
                        Text(role.function).font(.subheadline)
                        Text(role.advice).font(.subheadline).foregroundStyle(TraidoresTheme.secondary)
                    }
                }
                Divider().overlay(TraidoresTheme.border)
            }
        }
    }
}

private struct MenuTutorialView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var index = 0
    private let icons = ["person.text.rectangle", "moon.fill", "person.wave.2.fill", "hand.tap.fill"]
    var body: some View {
        let pages = AndroidMenuReference.content.tutorial
        let page = pages[index]
        MenuPage(title: "TUTORIAL") {
            Text("\(index + 1) DE \(pages.count)").font(.caption.bold())
                .frame(maxWidth: .infinity).accessibilityIdentifier("tutorial.progress")
            Image(systemName: icons[index]).font(.system(size: 54)).foregroundStyle(TraidoresTheme.gold)
                .frame(maxWidth: .infinity).padding(.vertical, 18)
            InformationCard(title: page.title, message: page.body)
            Text(page.hint).foregroundStyle(TraidoresTheme.text).readableOnArtwork()
            HStack {
                Button("ANTERIOR") { index -= 1 }.disabled(index == 0)
                    .buttonStyle(TraidoresButtonStyle(prominent: false))
                Button(index == pages.count - 1 ? "ENTENDIDO" : "SIGUIENTE") {
                    if index == pages.count - 1 { dismiss() } else { index += 1 }
                }
                .buttonStyle(TraidoresButtonStyle(prominent: true))
                .accessibilityIdentifier("tutorial.next")
            }
            Button("SALTAR") { dismiss() }.font(.headline).frame(maxWidth: .infinity, minHeight: 44)
        }
    }
}
