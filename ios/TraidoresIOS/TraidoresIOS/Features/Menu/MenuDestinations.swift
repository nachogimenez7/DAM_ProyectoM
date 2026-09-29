import SwiftUI
import PhotosUI
import TraidoresCore

struct PlayModesView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            MenuBackground()

            VStack(spacing: 0) {
                Text("SELECCIONAR MODO")
                    .font(TraidoresTheme.title(23))
                    .foregroundStyle(TraidoresTheme.gold)
                    .padding(.top, 28)
                    .padding(.bottom, 12)

                Spacer(minLength: 8)

                VStack(spacing: 40) {
                    NavigationLink {
                        LocalModeView()
                    } label: {
                        modeCard(title: "JUGAR CONTRA IA", image: "modo_juego_local_pampa_v3")
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("play.local")

                    modeCard(title: "JUGAR ONLINE", image: "modo_jugar_online", badge: "PRÓXIMAMENTE")
                        .accessibilityElement(children: .combine)
                }
                .frame(maxWidth: 560)
                .padding(.horizontal, 16)

                Spacer(minLength: 24)
            }

            Button(action: dismiss.callAsFunction) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .frame(width: 44, height: 44)
                    .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 10))
                    .overlay { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border) }
            }
            .foregroundStyle(TraidoresTheme.secondary)
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .accessibilityLabel("Volver")
        }
        .foregroundStyle(TraidoresTheme.text)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func modeCard(title: String, image: String, badge: String? = nil) -> some View {
        ZStack(alignment: .bottom) {
            LinearGradient(colors: [.clear, .black.opacity(0.88)],
                           startPoint: .center, endPoint: .bottom)
                .allowsHitTesting(false)
            Text(title)
                .font(TraidoresTheme.title(22))
                .foregroundStyle(TraidoresTheme.text)
                .multilineTextAlignment(.center)
                .shadow(color: .black, radius: 3, y: 2)
                .padding(.horizontal, 20)
                .padding(.bottom, 12)

            if let badge {
                Text(badge)
                    .font(.caption2.bold())
                    .tracking(1)
                    .foregroundStyle(TraidoresTheme.gold)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(TraidoresTheme.ink.opacity(0.92), in: Capsule())
                    .padding(10)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
        }
        .aspectRatio(3 / 2, contentMode: .fit)
        .background {
            Image(image)
                .resizable()
                .scaledToFill()
                .accessibilityHidden(true)
        }
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

struct LocalMenuProfile: Codable, Equatable {
    var name = "Jugador"
    var bio = "No fui yo. Esta vez."
    var avatar = "rol_aldeano_gaucho"
    var banner = "pampa"
    var favorite = "rol_detective_gaucho"
    var photoData: Data?
    static func load(_ data: Data) -> Self {
        (try? JSONDecoder().decode(Self.self, from: data)) ?? Self()
    }
}

struct ProfileView: View {
    @AppStorage("menu.localProfile.v1") private var storedProfile = Data()
    @AppStorage("menu.profileTheme") private var profileTheme = "classic"
    @AppStorage("menu.profileEmotes") private var emoteIDs = "griego_enojado,griego_triste,griego_contento,griego_sospechoso"
    @State private var draft = LocalMenuProfile()
    @State private var saved = LocalMenuProfile()
    @State private var selection: ProfileSelection?
    @State private var initialized = false
    @State private var saveError = false
    @State private var isEditing = false
    @State private var enlargedAvatar = false
    @State private var roleDetail = false
    @State private var photoItem: PhotosPickerItem?
    @State private var photoLoadError = false
    @State private var loadingPhoto = false
    @FocusState private var editingText: Bool
    private let banners: [(key: String, title: String)] = [
        ("pampa", "Pampa"), ("grecia", "Grecia"), ("medieval", "Medieval"),
        ("asesino_medieval", "Asesino medieval"), ("medico_pampeano", "Médica pampeana"),
        ("oraculo_griego", "Oráculo griego")
    ]
    private var favorite: GuideRole? {
        AndroidMenuReference.content.maps.flatMap(\.roles).first { $0.image == draft.favorite }
    }
    private var validName: Bool { !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var changed: Bool { draft != saved }
    private var accent: Color {
        switch profileTheme {
        case "sea": Color(red: 61/255, green: 230/255, blue: 224/255)
        case "fire": Color(red: 1, green: 106/255, blue: 50/255)
        case "space": Color(red: 98/255, green: 233/255, blue: 1)
        default: TraidoresTheme.gold
        }
    }
    private var surface: Color {
        switch profileTheme {
        case "sea": Color(red: 7/255, green: 26/255, blue: 36/255)
        case "fire": Color(red: 30/255, green: 10/255, blue: 7/255)
        case "space": Color(red: 12/255, green: 19/255, blue: 43/255)
        default: TraidoresTheme.panel
        }
    }
    private var themeTitle: String {
        switch profileTheme { case "sea": "Abismo Real"; case "fire": "Forja Infernal"; case "space": "Espacial"; default: "Clásico" }
    }

    private func openSelection(_ value: ProfileSelection) {
        editingText = false
        selection = value
    }

    private func saveProfile() {
        guard validName else { return }
        editingText = false
        draft.name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        draft.bio = draft.bio.trimmingCharacters(in: .whitespacesAndNewlines)
        do { storedProfile = try JSONEncoder().encode(draft); saved = draft; isEditing = false }
        catch { saveError = true }
    }

    var body: some View {
        MenuPage(title: "PERFIL", backgroundAsset: profileTheme == "classic" ? nil : "profile_background_\(profileTheme)", headerTint: accent, headerSurface: surface) {
          VStack(spacing: 16) {
            VStack(spacing: 6) {
              ZStack(alignment: .bottom) {
                VStack {
                GeometryReader { geometry in
                    Image("profile_banner_\(draft.banner)")
                        .resizable().scaledToFill().frame(width: geometry.size.width, height: 112).clipped()
                }.frame(height: 112)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .onTapGesture { if isEditing { openSelection(.banner) } }
                    .accessibilityHidden(true)
                  Spacer().frame(height: 62)
                }
                Button { if isEditing { openSelection(.avatar) } else { enlargedAvatar = true } } label: {
                    ProfilePortrait(image: draft.avatar, photoData: draft.photoData).frame(width: 112, height: 112)
                        .overlay(Circle().stroke(accent, lineWidth: 4))
                        .shadow(color: .black.opacity(0.5), radius: 8, y: 4)
                        .overlay(alignment: .bottomTrailing) {
                            if isEditing { Image(systemName: "pencil").padding(8).background(surface, in: Circle()) }
                        }
                }.buttonStyle(.plain).accessibilityIdentifier("profile.avatar")
                    .accessibilityLabel(isEditing ? "Editar foto de perfil" : "Ampliar foto de perfil")
                if isEditing {
                    Button { openSelection(.banner) } label: {
                        Image(systemName: "pencil").frame(width: 38, height: 38).background(surface, in: Circle())
                    }.accessibilityIdentifier("profile.banner").accessibilityLabel("Editar banner del perfil")
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing).padding(8)
                }
              }.frame(height: 174)
                Text(draft.name.isEmpty ? "Tu nombre" : draft.name)
                    .font(.system(size: 32, weight: .bold)).lineLimit(1).minimumScaleFactor(0.65)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    .accessibilityIdentifier("profile.displayName")
                Text("PERFIL LOCAL").font(.subheadline.bold()).foregroundStyle(accent)
                Text("Sin cuenta vinculada").font(.caption).foregroundStyle(TraidoresTheme.secondary)
            }
            if isEditing {
            VStack(alignment: .leading, spacing: 12) {
                Text("NOMBRE VISIBLE").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                TextField("Tu nombre", text: $draft.name)
                    .textContentType(.nickname).submitLabel(.done).focused($editingText)
                    .accessibilityIdentifier("profile.name")
                    .onChange(of: draft.name) { _, value in
                        if value.count > 20 { draft.name = String(value.prefix(20)) }
                    }
                Divider()
                Text("TU FRASE").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                TextField("Una frase sobre vos", text: $draft.bio, axis: .vertical)
                    .lineLimit(2...3).focused($editingText)
                    .accessibilityIdentifier("profile.bio")
                    .onChange(of: draft.bio) { _, value in
                        if value.count > 40 { draft.bio = String(value.prefix(40)) }
                    }
                Text("Nombre: hasta 20 caracteres. Frase: hasta 40, como en Android.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
            .padding(20).background(surface, in: RoundedRectangle(cornerRadius: 14))
            Button { openSelection(.style) } label: {
                HStack { Text("ESTILO DEL PERFIL · \(themeTitle.uppercased())").font(.caption.bold()); Spacer(); Image(systemName: "paintpalette") }
            }.buttonStyle(TraidoresButtonStyle()).accessibilityIdentifier("profile.style")
            } else {
                Text(draft.bio.isEmpty ? "Tu frase, tu manera de jugar." : "“\(draft.bio)”")
                    .font(.title3.italic()).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity).padding(16)
                    .background(surface.opacity(0.95), in: RoundedRectangle(cornerRadius: 10))
            }
            profileHeading("ESTADÍSTICAS")
            HStack(spacing: 8) {
                stat("Partidas"); stat("Victorias"); stat("Porcentaje")
            }
            Text("Las estadísticas se mostrarán cuando este perfil tenga progreso registrado.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            Button {
                if isEditing { openSelection(.favorite) } else { roleDetail = true }
            } label: {
                HStack {
                    Image(draft.favorite).resizable().scaledToFit().frame(width: 70, height: 108)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("ROL FAVORITO").font(.caption.bold())
                        Text(favorite?.title ?? "Comisario").font(.headline)
                        Text(isEditing ? "Elegir personaje y mapa" : "Tocá para ver la ficha").font(.footnote)
                    }
                    Spacer()
                    Image(systemName: isEditing ? "pencil" : "chevron.right")
                }
            }
            .padding(14).frame(maxWidth: .infinity)
            .background(surface.opacity(0.95), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(accent.opacity(0.5)))
            .foregroundStyle(TraidoresTheme.text).buttonStyle(.plain).accessibilityIdentifier("profile.favorite")
            Text("El rol favorito personaliza tu perfil; no cambia el rol que recibís en una partida.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            profileHeading("EMOTES")
            HStack(spacing: 8) {
                ForEach(emoteIDs.split(separator: ",").map(String.init), id: \.self) { id in
                    if let emote = AndroidMenuReference.content.emotes.first(where: { $0.id == id }) {
                        Button { if isEditing { openSelection(.emotes) } } label: {
                            ProfileEmoteImage(emote: emote).frame(height: 66)
                                .frame(maxWidth: .infinity).padding(5)
                                .background(surface, in: RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent.opacity(0.6)))
                        }.buttonStyle(.plain).accessibilityLabel(emote.title)
                    }
                }
            }
            if isEditing {
                Button("EDITAR EMOTES · 4 DE 4") { openSelection(.emotes) }
                    .buttonStyle(TraidoresButtonStyle()).accessibilityIdentifier("profile.emotes")
            }
            profileHeading("LOGROS DESTACADOS")
            Text("Todavía no hay logros obtenidos en este perfil iOS.").font(.subheadline)
            Button("VER TODOS LOS LOGROS") { selection = .achievements }
                .buttonStyle(TraidoresButtonStyle()).accessibilityIdentifier("profile.achievements")
            profileHeading("ÚLTIMA PARTIDA")
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "clock.arrow.circlepath").font(.title2).foregroundStyle(accent)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Sin historial conectado").font(.headline)
                    Text("Las partidas finalizadas aparecerán aquí cuando se integre el historial de iOS.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                }
            }.frame(maxWidth: .infinity, alignment: .leading).padding(16)
                .background(surface, in: RoundedRectangle(cornerRadius: 10))
            profileHeading("CUENTA")
            Text("Guardado en este dispositivo. El acceso, la recuperación y la sincronización online todavía no están disponibles en esta versión.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            if isEditing {
            Button("GUARDAR PERFIL", action: saveProfile)
            .buttonStyle(TraidoresButtonStyle(prominent: true))
            .disabled(!validName || !changed).accessibilityIdentifier("profile.save")
            Text(!validName ? "Escribí un nombre para guardar." : changed ? "Tenés cambios sin guardar." : "Perfil guardado en este dispositivo.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                .accessibilityIdentifier("profile.status")
            Button("DESCARTAR CAMBIOS") { draft = saved; isEditing = false; editingText = false }
                .frame(maxWidth: .infinity).accessibilityIdentifier("profile.discard")
            }
          }
          .padding(16).frame(maxWidth: .infinity)
          .background(surface.opacity(profileTheme == "classic" ? 0.96 : 0.86), in: RoundedRectangle(cornerRadius: 16))
          .overlay(RoundedRectangle(cornerRadius: 16).stroke(accent.opacity(0.65), lineWidth: 1))
        }
        .overlay(alignment: .topTrailing) {
            Button {
                if isEditing { saveProfile() } else { isEditing = true }
            } label: {
                Image(systemName: isEditing ? "checkmark" : "pencil")
                    .font(.headline).frame(width: 44, height: 44).background(surface, in: Circle())
                    .overlay(Circle().stroke(accent.opacity(0.6)))
            }.foregroundStyle(accent).padding(.trailing, 16)
                .accessibilityLabel(isEditing ? "Terminar de editar el perfil" : "Editar perfil")
                .accessibilityIdentifier("profile.edit")
        }
        .onSubmit { editingText = false }
        .onAppear {
            guard !initialized else { return }
            draft = LocalMenuProfile.load(storedProfile)
            saved = draft
            initialized = true
        }
        .sheet(item: $selection) { selected in
            MenuPage(title: selected.title) {
                Text("Tocá una opción para elegirla. Después guardá el perfil.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                if selected == .style {
                    ProfileStyleSelector(theme: profileTheme, name: draft.name, bio: draft.bio, avatar: draft.avatar, photo: draft.photoData) { theme in
                        profileTheme = theme; selection = nil
                    }
                    Text("El estilo se equipa en este perfil; el gameplay permanece sin cambios.").font(.footnote)
                } else if selected == .emotes {
                    ProfileEmoteSelector(ids: emoteIDs.split(separator: ",").map(String.init)) { ids in
                        emoteIDs = ids.joined(separator: ","); selection = nil
                    }
                } else if selected == .achievements {
                    Text("Estos son los diez logros de Android. Se desbloquearán con progreso real; todavía no se pueden equipar en iOS.")
                        .font(.footnote)
                    ProfileAchievementCatalogView()
                } else if selected == .banner {
                    ForEach(banners, id: \.key) { banner in
                        Button {
                            draft.banner = banner.key; selection = nil
                        } label: {
                            VStack(alignment: .leading) {
                                ProfileStripImage(image: "profile_banner_\(banner.key)", height: 95)
                                Text(banner.title + (draft.banner == banner.key ? " ✓" : ""))
                                    .font(.headline).padding(12)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }.buttonStyle(.plain).accessibilityIdentifier("profile.banner.\(banner.key)")
                            .accessibilityValue(draft.banner == banner.key ? "Seleccionado" : "")
                    }
                } else {
                    if selected == .avatar {
                        PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                            Label("ELEGIR FOTO DEL IPHONE", systemImage: "photo")
                        }.buttonStyle(TraidoresButtonStyle()).disabled(loadingPhoto)
                            .accessibilityIdentifier("profile.photoPicker")
                        Text("La foto se recorta al círculo del avatar. Solo se guarda al guardar el perfil; no se sube a ningún servidor.")
                            .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                        if loadingPhoto { ProgressView("Cargando foto…") }
                    }
                    ProfileRoleSelector(currentImage: selected == .avatar && draft.photoData != nil ? "" : (selected == .avatar ? draft.avatar : draft.favorite)) { role in
                        if selected == .avatar { draft.avatar = role.image; draft.photoData = nil }
                        else { draft.favorite = role.image }
                        selection = nil
                    }
                }
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $enlargedAvatar) {
            MenuPage(title: "FOTO DE PERFIL") {
                ProfilePortrait(image: draft.avatar, photoData: draft.photoData).frame(width: 260, height: 260).frame(maxWidth: .infinity)
                Button("CERRAR") { enlargedAvatar = false }.buttonStyle(TraidoresButtonStyle())
            }
        }
        .sheet(isPresented: $roleDetail) {
            if let favorite {
                MenuPage(title: favorite.title) {
                    Image(favorite.image).resizable().scaledToFit().frame(maxHeight: 300).frame(maxWidth: .infinity)
                    InformationCard(title: "Su historia", message: favorite.story)
                    InformationCard(title: "Su función", message: favorite.function)
                }
            }
        }
        .alert("No se pudo guardar el perfil", isPresented: $saveError) {
            Button("ENTENDIDO", role: .cancel) {}
        }
        .alert("No se pudo cargar la foto", isPresented: $photoLoadError) {
            Button("ENTENDIDO", role: .cancel) {}
        } message: { Text("Elegí una imagen válida de menos de 15 MB. Tu avatar anterior no se modificó.") }
        .task(id: photoItem) {
            guard let photoItem else { return }
            loadingPhoto = true
            defer { loadingPhoto = false; self.photoItem = nil }
            do {
                guard let data = try await photoItem.loadTransferable(type: Data.self),
                      data.count <= 15_000_000, let image = UIImage(data: data) else {
                    photoLoadError = true; return
                }
                guard !Task.isCancelled else { return }
                let factor = min(1, 512 / max(image.size.width, image.size.height))
                let size = CGSize(width: image.size.width * factor, height: image.size.height * factor)
                let format = UIGraphicsImageRendererFormat(); format.scale = 1
                let thumbnail = UIGraphicsImageRenderer(size: size, format: format).image { _ in
                    image.draw(in: CGRect(origin: .zero, size: size))
                }
                guard let jpeg = thumbnail.jpegData(compressionQuality: 0.85) else { photoLoadError = true; return }
                draft.photoData = jpeg
                selection = nil
            } catch {
                if !Task.isCancelled { photoLoadError = true }
            }
        }
    }

    private func profileHeading(_ title: String) -> some View {
        HStack { Text(title).font(.caption.bold()).tracking(1); Spacer() }.foregroundStyle(accent)
    }
    private func stat(_ label: String) -> some View {
        VStack(spacing: 6) { Text(label == "Porcentaje" ? "—%" : "—").font(.title2.bold()); Text(label).font(.caption) }
            .frame(maxWidth: .infinity).padding(.vertical, 16)
            .background(surface, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent.opacity(0.3)))
    }
}

private struct ProfileStyleSelector: View {
    @State private var preview: String
    let name, bio, avatar: String
    let photo: Data?
    let equip: (String) -> Void
    private let themes = [("classic", "Clásico"), ("space", "Espacial"), ("sea", "Abismo Real"), ("fire", "Forja Infernal")]
    init(theme: String, name: String, bio: String, avatar: String, photo: Data?, equip: @escaping (String) -> Void) {
        _preview = State(initialValue: theme)
        self.name = name; self.bio = bio; self.avatar = avatar; self.photo = photo; self.equip = equip
    }
    private var color: Color {
        switch preview { case "sea": .cyan; case "fire": .orange; case "space": Color(red: 98/255, green: 233/255, blue: 1); default: TraidoresTheme.gold }
    }
    var body: some View {
        VStack(spacing: 14) {
            ZStack {
                ProfileStripImage(image: preview == "classic" ? "fondo_menu" : "profile_background_\(preview)", height: 250)
                VStack(spacing: 10) {
                    ProfilePortrait(image: avatar, photoData: photo).frame(width: 92, height: 92)
                        .overlay(Circle().stroke(color, lineWidth: 3))
                    Text(name).font(.title2.bold())
                    Text(themes.first { $0.0 == preview }!.1.uppercased()).font(.caption.bold()).foregroundStyle(color)
                    Text(bio.isEmpty ? "Tu frase" : bio).font(.subheadline).padding(12)
                        .background(.black.opacity(0.7), in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(color.opacity(0.7)))
                }.padding(18)
            }.frame(height: 250).clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(color))
            ForEach(themes, id: \.0) { theme in
                Button { preview = theme.0 } label: {
                    HStack { Text(theme.1).font(.headline); Spacer(); if preview == theme.0 { Image(systemName: "checkmark.circle.fill") } }
                        .frame(maxWidth: .infinity, minHeight: 44).padding(.horizontal, 14)
                        .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(preview == theme.0 ? color : TraidoresTheme.border))
                        .contentShape(Rectangle())
                }.buttonStyle(.plain).accessibilityIdentifier("profile.style.\(theme.0)")
                    .accessibilityValue(preview == theme.0 ? "Seleccionado" : "")
            }
            Button("EQUIPAR") { equip(preview) }.buttonStyle(TraidoresButtonStyle(prominent: true))
                .accessibilityIdentifier("profile.style.equip")
        }
    }
}

private struct ProfileEmoteSelector: View {
    @State private var selectedEmotes: [String]
    @State private var selectedCategory = "CLASSIC"
    let apply: ([String]) -> Void
    init(ids: [String], apply: @escaping ([String]) -> Void) {
        _selectedEmotes = State(initialValue: ids)
        self.apply = apply
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Elegí exactamente 4 emotes · \(selectedEmotes.count)/4").font(.headline)
                .accessibilityIdentifier("profile.emotes.count")
            HStack {
                ForEach([("CLASSIC", "Clásicos"), ("MEME", "Memes"), ("LEGENDARY", "Legendarios")], id: \.0) { category in
                    Button(category.1) { selectedCategory = category.0 }
                        .font(.caption.bold()).padding(10)
                        .background(selectedCategory == category.0 ? TraidoresTheme.gold : TraidoresTheme.panel, in: Capsule())
                        .foregroundStyle(selectedCategory == category.0 ? TraidoresTheme.ink : TraidoresTheme.text)
                }
            }
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(AndroidMenuReference.content.emotes.filter { $0.category == selectedCategory }) { emote in
                    Button {
                        if selectedEmotes.contains(emote.id) { selectedEmotes.removeAll { $0 == emote.id } }
                        else if selectedEmotes.count < 4 { selectedEmotes.append(emote.id) }
                    } label: {
                        VStack(spacing: 8) {
                            ProfileEmoteImage(emote: emote).frame(height: 92)
                            Text(emote.title).font(.subheadline.bold())
                            Text(emote.theme).font(.caption)
                            Text(emote.description).font(.caption).foregroundStyle(TraidoresTheme.secondary)
                            if emote.premium { Label("Premium", systemImage: "star.fill").font(.caption) }
                            else if selectedEmotes.contains(emote.id) { Image(systemName: "checkmark.circle.fill").foregroundStyle(TraidoresTheme.gold) }
                        }.frame(maxWidth: .infinity).padding(10)
                            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain).accessibilityIdentifier("profile.emote.\(emote.id)")
                        .accessibilityValue(selectedEmotes.contains(emote.id) ? "Seleccionado" : "")
                }
            }
            Button("APLICAR EMOTES") { apply(selectedEmotes) }
                .buttonStyle(TraidoresButtonStyle(prominent: true)).disabled(selectedEmotes.count != 4)
                .accessibilityIdentifier("profile.emotes.apply")
            Text("Los 20 emotes del catálogo de Android están disponibles para este perfil local. El envío en partidas se integrará al retomar el gameplay.").font(.footnote)
        }
    }
}

private struct ProfileAchievementCatalogView: View {
    @State private var selected: ProfileAchievementContent?
    var body: some View {
        VStack(spacing: 12) {
            ForEach(AndroidMenuReference.content.achievements) { item in
                Button { selected = item } label: {
                    HStack {
                        Image(systemName: "medal.fill").font(.title2)
                            .foregroundStyle(item.rarity == "GOLD" ? TraidoresTheme.gold : item.rarity == "SILVER" ? .gray : .brown)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(item.title).font(.headline)
                            Text("Pendiente · \(item.rarity == "GOLD" ? "Oro" : item.rarity == "SILVER" ? "Plata" : "Bronce")").font(.caption)
                        }
                        Spacer(); Image(systemName: "lock.fill")
                    }.padding(12).background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                }.buttonStyle(.plain).accessibilityIdentifier("profile.achievement.\(item.id)")
            }
        }
        .alert(selected?.title ?? "Logro", isPresented: Binding(get: { selected != nil }, set: { if !$0 { selected = nil } })) {
            Button("ENTENDIDO") { selected = nil }
        } message: { Text(selected?.description ?? "") }
    }
}

private struct ProfileRoleSelector: View {
    let currentImage: String
    let choose: (GuideRole) -> Void
    @State private var selectedMap: String
    init(currentImage: String, choose: @escaping (GuideRole) -> Void) {
        self.currentImage = currentImage
        self.choose = choose
        _selectedMap = State(initialValue: AndroidMenuReference.content.maps.first {
            $0.roles.contains { $0.image == currentImage }
        }?.id ?? "pampa")
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 6) {
                ForEach(AndroidMenuReference.content.maps.reversed()) { map in
                    Button(map.id == "medieval" ? "Medieval" : map.shortTitle) { selectedMap = map.id }
                        .font(.subheadline.bold()).lineLimit(1).minimumScaleFactor(0.75)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(selectedMap == map.id ? TraidoresTheme.gold : TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(selectedMap == map.id ? TraidoresTheme.ink : TraidoresTheme.text)
                        .buttonStyle(.plain).accessibilityIdentifier("profile.map.\(map.id)")
                }
            }
            ForEach(AndroidMenuReference.content.maps.filter { $0.id == selectedMap }) { map in
                Text(map.title).font(TraidoresTheme.title(20)).foregroundStyle(TraidoresTheme.gold)
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    ForEach(map.roles) { role in
                        Button { choose(role) } label: {
                            VStack(spacing: 8) {
                                Image(role.image).resizable().scaledToFit().frame(height: 150).accessibilityHidden(true)
                                Text(role.title).font(.subheadline.bold()).multilineTextAlignment(.center)
                                if currentImage == role.image { Image(systemName: "checkmark.circle.fill").foregroundStyle(TraidoresTheme.gold) }
                            }.frame(maxWidth: .infinity).padding(10)
                                .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                        }.buttonStyle(.plain).accessibilityIdentifier("profile.choice.\(map.id).\(role.id)")
                            .accessibilityValue(currentImage == role.image ? "Seleccionado" : "")
                    }
                }
            }
        }
    }
}

private struct ProfileStripImage: View {
    let image: String
    let height: CGFloat
    var body: some View {
        GeometryReader { geometry in
            Image(image).resizable().scaledToFill()
                .frame(width: geometry.size.width, height: height).clipped()
        }.frame(height: height).accessibilityHidden(true)
    }
}

struct ProfilePortrait: View {
    let image: String
    var photoData: Data? = nil
    private var focus: CGFloat {
        if image.contains("bufon") { return 0.24 }
        if image.contains("oraculo") { return 0.27 }
        if image.contains("asesino") || image.contains("mercenario") || image.contains("espia") { return 0.28 }
        return image == "rol_aldeano_gaucho" ? 0.32 : 0.30
    }
    var body: some View {
        GeometryReader { geometry in
            if let artwork = photoData.flatMap(UIImage.init(data:)) ?? UIImage(named: image) {
                let width = max(geometry.size.width, geometry.size.height * artwork.size.width / artwork.size.height)
                let height = width * artwork.size.height / artwork.size.width
                Image(uiImage: artwork).resizable()
                    .frame(width: width, height: height)
                    .position(x: geometry.size.width / 2,
                              y: geometry.size.height / 2 + height * (0.5 - (photoData == nil ? focus : 0.5)))
            }
        }.clipShape(Circle()).accessibilityHidden(true)
    }
}

private struct ProfileEmoteImage: View {
    let emote: ProfileEmoteContent
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var frame = "a"
    var body: some View {
        if emote.animated {
            Image("\(emote.image)_\(frame)").resizable().scaledToFit()
                .task(id: reduceMotion) {
                    frame = "a"
                    guard !reduceMotion else { return }
                    for next in ["a", "b", "a", "b"] {
                        frame = next
                        do { try await Task.sleep(for: .milliseconds(110)) } catch { return }
                    }
                }.accessibilityHidden(true)
        } else {
            Image(emote.image).resizable().scaledToFit().accessibilityHidden(true)
        }
    }
}

private enum ProfileSelection: String, Identifiable {
    case avatar, banner, favorite, style, emotes, achievements
    var id: String { rawValue }
    var title: String {
        switch self {
        case .avatar: "ELEGIR AVATAR"
        case .banner: "ELEGIR BANNER"
        case .favorite: "ROL FAVORITO"
        case .style: "ESTILO DEL PERFIL"
        case .emotes: "TUS EMOTES"
        case .achievements: "TODOS LOS LOGROS"
        }
    }
}

struct OptionsView: View {
    @Environment(MenuPreferences.self) private var preferences
    @State private var confirmingReset = false

    var body: some View {
        @Bindable var preferences = preferences
        MenuPage(title: "Opciones") {
            Text("Ajustá el menú para que sea cómodo de leer y escuchar.")
                .foregroundStyle(TraidoresTheme.secondary)
            Text("SONIDO Y RESPUESTA").font(.caption.bold()).tracking(1)
                .foregroundStyle(TraidoresTheme.gold)
            VStack(alignment: .leading, spacing: 12) {
                Toggle("Música del menú", isOn: $preferences.musicEnabled)
                    .font(.headline).tint(TraidoresTheme.gold)
                    .accessibilityIdentifier("options.music")
                Text("Música: \(Int((preferences.musicVolume * 100).rounded()))%")
                    .font(.subheadline.bold()).monospacedDigit()
                    .accessibilityIdentifier("options.volumeLabel")
                Slider(value: $preferences.musicVolume, in: 0...1, step: 0.05)
                    .tint(TraidoresTheme.gold)
                    .disabled(!preferences.musicEnabled)
                    .accessibilityLabel("Volumen de música")
                    .accessibilityIdentifier("options.volume")
                Text("La música respeta el modo silencio del iPhone y se pausa al salir de la app.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
            .padding(20)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
            Text("LECTURA Y ACCESIBILIDAD").font(.caption.bold()).tracking(1)
                .foregroundStyle(TraidoresTheme.gold)
            VStack(alignment: .leading, spacing: 12) {
                Picker("Texto del menú y las guías", selection: $preferences.textSize) {
                    ForEach(MenuTextSize.allCases) { size in Text(size.title).tag(size) }
                }
                .tint(TraidoresTheme.gold)
                .accessibilityIdentifier("options.textSize")
                Text("Cada carta esconde una intención. Leé, preguntá y descubrí en quién confiar.")
                    .font(.body).accessibilityIdentifier("options.textPreview")
                Text("Según el iPhone respeta el tamaño configurado en Accesibilidad. Este ajuste todavía no modifica el gameplay.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
            .padding(20)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
            NavigationLink { AboutView() } label: { Text("ACERCA DE TRAIDORES") }
                .buttonStyle(TraidoresButtonStyle())
                .accessibilityIdentifier("options.about")
            Button("RESTABLECER OPCIONES") { confirmingReset = true }
                .buttonStyle(TraidoresButtonStyle())
                .accessibilityIdentifier("options.reset")
            Text("Efectos, vibración de partida, notificaciones e idiomas se integrarán en sus respectivas etapas.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
        }
        .alert("¿Restablecer las opciones del menú?", isPresented: $confirmingReset) {
            Button("CANCELAR", role: .cancel) {}
            Button("RESTABLECER") { preferences.resetMenuOptions() }
        } message: {
            Text("Se restauran música, volumen y lectura. No se borran perfiles ni partidas.")
        }
    }
}

struct AboutView: View {
    private var version: String {
        let info = Bundle.main.infoDictionary ?? [:]
        return "Versión iOS \(info["CFBundleShortVersionString"] as? String ?? "—") · build \(info["CFBundleVersion"] as? String ?? "—")"
    }
    var body: some View {
        MenuPage(title: "ACERCA DE TRAIDORES") {
            Image("bandido_menu_medallion").resizable().scaledToFit()
                .frame(height: 130).frame(maxWidth: .infinity).accessibilityHidden(true)
            InformationCard(title: "Traidores", message: "Un juego de deducción social, engaño y debate donde cada decisión puede cambiar el destino del pueblo.")
            InformationCard(title: "Creado por Bandido Games", message: "Bandido Games es el estudio independiente detrás de Traidores.")
            Text(version).font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                .accessibilityIdentifier("about.version")
            NavigationLink { SupportMessageView(feedback: false) } label: {
                Text("CONTACTAR A BANDIDO GAMES")
            }
            .buttonStyle(TraidoresButtonStyle(prominent: true))
            Link("POLÍTICA DE PRIVACIDAD", destination: URL(string: "https://www.traidores.me/privacidad")!)
                .buttonStyle(TraidoresButtonStyle())
                .accessibilityIdentifier("about.privacy")
            Link("INFORMACIÓN SOBRE ELIMINACIÓN", destination: URL(string: "https://www.traidores.me/eliminar-cuenta")!)
                .buttonStyle(TraidoresButtonStyle())
            Text("La gestión y eliminación de cuentas se habilitará cuando se integre el acceso a perfiles online.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
        }
    }
}

struct SupportMessageView: View {
    let feedback: Bool
    @Environment(\.openURL) private var openURL
    @State private var message = ""
    @State private var mailUnavailable = false
    private let email = "bandidogamesestudio@gmail.com"
    var body: some View {
        MenuPage(title: feedback ? "COMENTARIOS / ERRORES" : "CONTACTO") {
            Text("Contanos qué pasó o qué te gustaría mejorar.")
                .foregroundStyle(TraidoresTheme.secondary)
            TextEditor(text: $message)
                .frame(minHeight: 180).scrollContentBackground(.hidden)
                .padding(12)
                .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border))
                .accessibilityIdentifier("support.message")
            Text("Se abrirá tu aplicación de correo. Nada se envía automáticamente y podés revisar el mensaje antes de enviarlo.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            Button("ABRIR CORREO") {
                var components = URLComponents()
                components.scheme = "mailto"
                components.path = email
                components.queryItems = [
                    .init(name: "subject", value: feedback ? "Comentarios y errores de Traidores — iOS" : "Soporte de Traidores — iOS"),
                    .init(name: "body", value: message)
                ]
                if let url = components.url {
                    openURL(url) { accepted in mailUnavailable = !accepted }
                }
            }
            .buttonStyle(TraidoresButtonStyle(prominent: true))
            .disabled(message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .accessibilityIdentifier("support.openMail")
            Text(email).font(.footnote).textSelection(.enabled)
        }
        .alert("No se pudo abrir el correo", isPresented: $mailUnavailable) {
            Button("ENTENDIDO", role: .cancel) {}
        } message: { Text("Podés escribirnos a \(email).") }
    }
}
