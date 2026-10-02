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
    @State private var nameBeforeEditing = "Jugador"
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
    @Environment(\.dynamicTypeSize) private var systemTextSize
    private let banners: [(key: String, title: String)] = [
        ("pampa", "Pampa"), ("grecia", "Grecia"), ("medieval", "Medieval"),
        ("asesino_medieval", "Asesino medieval"), ("medico_pampeano", "Médica pampeana"),
        ("oraculo_griego", "Oráculo griego")
    ]
    private var favorite: GuideRole? {
        AndroidMenuReference.content.maps.flatMap(\.roles).first { $0.image == draft.favorite }
    }
    private var validName: Bool { !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var style: ProfileStyle { ProfileStyle(rawValue: profileTheme) ?? .classic }
    private var accent: Color { style.primary }
    private var surface: Color { style.surface }
    private var themeTitle: String { style.name }

    private func openSelection(_ value: ProfileSelection) {
        editingText = false
        selection = value
    }

    private func saveProfile() {
        guard initialized else { return }
        var profile = draft
        profile.name = profile.name.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.bio = profile.bio.trimmingCharacters(in: .whitespacesAndNewlines)
        // Keep the last valid name while the player clears the field to type another.
        if profile.name.isEmpty { profile.name = nameBeforeEditing }
        guard profile != saved else { return }
        do { storedProfile = try JSONEncoder().encode(profile); saved = profile }
        catch { saveError = true }
    }

    var body: some View {
        MenuPage(title: "PERFIL", backgroundAsset: style.backgroundAsset, headerTint: accent, headerSurface: surface) {
          VStack(alignment: .leading, spacing: 16) {
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
                            // Fixed badge: at accessibility sizes it must not cover the portrait.
                            if isEditing { editBadge(size: 34).offset(x: 4, y: 4) }
                        }
                }.buttonStyle(.plain).accessibilityIdentifier("profile.avatar")
                    .accessibilityLabel(isEditing ? "Editar foto de perfil" : "Ampliar foto de perfil")
                if isEditing {
                    Button { openSelection(.banner) } label: {
                        editBadge(size: 36).frame(width: 44, height: 44).contentShape(Rectangle())
                    }.accessibilityIdentifier("profile.banner").accessibilityLabel("Editar banner del perfil")
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing).padding(4)
                }
              }.frame(height: 174)
                Text(draft.name.isEmpty ? "Tu nombre" : draft.name)
                    .font(.largeTitle.bold()).lineLimit(2).minimumScaleFactor(0.8)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("profile.displayName")
                Text("PERFIL LOCAL").font(.subheadline.bold()).tracking(1).foregroundStyle(accent)
                Text("Sin cuenta vinculada").font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
            .frame(maxWidth: .infinity)
            if isEditing {
            VStack(alignment: .leading, spacing: 14) {
                editField(title: "NOMBRE VISIBLE", count: draft.name.count, limit: 20) {
                    TextField("Tu nombre", text: $draft.name)
                        .textContentType(.nickname).submitLabel(.done).focused($editingText)
                        .accessibilityIdentifier("profile.name")
                        .onChange(of: draft.name) { _, value in
                            if value.count > 20 { draft.name = String(value.prefix(20)) }
                        }
                }
                editField(title: "TU FRASE", count: draft.bio.count, limit: 40) {
                    TextField("Una frase sobre vos", text: $draft.bio, axis: .vertical)
                        .lineLimit(2...3).focused($editingText)
                        .accessibilityIdentifier("profile.bio")
                        .onChange(of: draft.bio) { _, value in
                            if value.count > 40 { draft.bio = String(value.prefix(40)) }
                        }
                }
            }
            .profileCard(accent: accent, surface: surface)
            Button { openSelection(.style) } label: {
                HStack { Text("ESTILO DEL PERFIL · \(themeTitle.uppercased())").font(.caption.bold()); Spacer(); Image(systemName: "paintpalette") }
            }.buttonStyle(TraidoresButtonStyle(accent: accent, surface: surface)).accessibilityIdentifier("profile.style")
            } else {
                Text(draft.bio.isEmpty ? "Tu frase, tu manera de jugar." : "“\(draft.bio)”")
                    .font(.title3.italic()).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .profileCard(accent: accent, surface: surface)
            }
            profileHeading("ESTADÍSTICAS")
            // Side by side normally; stacked with accessibility text sizes.
            let statsLayout = systemTextSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(spacing: 8)) : AnyLayout(HStackLayout(spacing: 8))
            statsLayout { stat("Partidas"); stat("Victorias"); stat("Porcentaje") }
            note("Las estadísticas se mostrarán cuando este perfil tenga progreso registrado.")
            Button {
                if isEditing { openSelection(.favorite) } else { roleDetail = true }
            } label: {
                HStack(spacing: 14) {
                    Image(draft.favorite).resizable().scaledToFit().frame(width: 70, height: 108)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    VStack(alignment: .leading, spacing: 4) {
                        Text("ROL FAVORITO").font(.caption.bold()).foregroundStyle(accent)
                        Text(favorite?.title ?? "Comisario").font(TraidoresTheme.title(22, relativeTo: .title2))
                        Text(isEditing ? "Elegir personaje y mapa" : "Tocá para ver la ficha")
                            .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: isEditing ? "pencil" : "chevron.right").foregroundStyle(accent)
                }
                .profileCard(accent: accent, surface: surface, padding: 12)
                .contentShape(Rectangle())
            }
            .foregroundStyle(TraidoresTheme.text).buttonStyle(.plain).accessibilityIdentifier("profile.favorite")
            note("El rol favorito personaliza tu perfil; no cambia el rol que recibís en una partida.")
            profileHeading("EMOTES")
            HStack(spacing: 8) {
                ForEach(emoteIDs.split(separator: ",").map(String.init), id: \.self) { id in
                    if let emote = AndroidMenuReference.content.emotes.first(where: { $0.id == id }) {
                        // Outside edit mode the emotes are only a showcase, not buttons.
                        if isEditing {
                            Button { openSelection(.emotes) } label: { emoteTile(emote) }
                                .buttonStyle(.plain).accessibilityLabel(emote.title)
                                .accessibilityHint("Cambia tus emotes")
                        } else {
                            emoteTile(emote)
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel(emote.title)
                        }
                    }
                }
            }
            if isEditing {
                Button("EDITAR EMOTES · 4 DE 4") { openSelection(.emotes) }
                    .buttonStyle(TraidoresButtonStyle(accent: accent, surface: surface))
                    .accessibilityIdentifier("profile.emotes")
            }
            profileHeading("LOGROS DESTACADOS")
            note("Todavía no obtuviste logros en este perfil.", primary: true)
            Button("VER TODOS LOS LOGROS") { selection = .achievements }
                .buttonStyle(TraidoresButtonStyle(accent: accent, surface: surface))
                .accessibilityIdentifier("profile.achievements")
            profileHeading("ÚLTIMA PARTIDA")
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "clock.arrow.circlepath").font(.title2).foregroundStyle(accent)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Sin historial conectado").font(.headline)
                    Text("Tus partidas terminadas aparecerán aquí cuando el historial esté disponible.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                }
                Spacer(minLength: 0)
            }
            .profileCard(accent: accent, surface: surface)
            .accessibilityElement(children: .combine)
            profileHeading("CUENTA")
            note("Guardado en este dispositivo. El acceso, la recuperación y la sincronización online todavía no están disponibles en esta versión.")
            if isEditing {
                note(!validName ? "Escribí tu nombre. Si lo dejás vacío, se conserva el anterior." : "Tus cambios se guardan automáticamente.")
                    .accessibilityIdentifier("profile.status")
            }
          }
          .padding(16).frame(maxWidth: .infinity)
          .background(surface, in: RoundedRectangle(cornerRadius: 16))
          .overlay(RoundedRectangle(cornerRadius: 16).stroke(accent.opacity(0.65), lineWidth: 1))
        }
        .overlay(alignment: .topTrailing) {
            Button {
                if isEditing {
                    saveProfile(); draft = saved; editingText = false
                } else {
                    nameBeforeEditing = saved.name
                }
                isEditing.toggle()
            } label: {
                Image(systemName: isEditing ? "checkmark" : "pencil")
                    .font(.system(size: 17, weight: .semibold)).frame(width: 44, height: 44).background(surface, in: Circle())
                    .overlay(Circle().stroke(accent.opacity(0.6)))
            }.foregroundStyle(accent).padding(.trailing, 16)
                .accessibilityLabel(isEditing ? "Terminar de editar el perfil" : "Editar perfil")
                .accessibilityShowsLargeContentViewer()
                .accessibilityIdentifier("profile.edit")
        }
        .onSubmit { editingText = false }
        .onAppear {
            guard !initialized else { return }
            draft = LocalMenuProfile.load(storedProfile)
            saved = draft
            nameBeforeEditing = draft.name
            initialized = true
        }
        .onChange(of: draft) { _, _ in saveProfile() }
        .onDisappear { saveProfile() }
        .sheet(item: $selection) { selected in
            MenuPage(title: selected.title) {
                Text("Tus elecciones se guardan automáticamente.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    .readableOnArtwork()
                if selected == .style {
                    ProfileStyleSelector(theme: profileTheme, name: draft.name, bio: draft.bio, avatar: draft.avatar, photo: draft.photoData) { theme in
                        profileTheme = theme
                    }
                    Text("El estilo cambia el aspecto de tu perfil; no afecta a las partidas.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary).readableOnArtwork()
                } else if selected == .emotes {
                    ProfileEmoteSelector(ids: emoteIDs.split(separator: ",").map(String.init)) { ids in
                        emoteIDs = ids.joined(separator: ",")
                    }
                } else if selected == .achievements {
                    Text("Estos son los diez logros del juego. Se desbloquean con progreso real; todavía no se pueden destacar en esta versión.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary).readableOnArtwork()
                    ProfileAchievementCatalogView()
                } else if selected == .banner {
                    ForEach(banners, id: \.key) { banner in
                        Button {
                            draft.banner = banner.key; selection = nil
                        } label: {
                            VStack(alignment: .leading, spacing: 0) {
                                ProfileStripImage(image: "profile_banner_\(banner.key)", height: 95)
                                    .clipShape(UnevenRoundedRectangle(topLeadingRadius: 12, topTrailingRadius: 12))
                                HStack {
                                    Text(banner.title).font(.headline)
                                    Spacer()
                                    if draft.banner == banner.key {
                                        Text("EQUIPADO").font(.caption2.bold()).tracking(1).foregroundStyle(TraidoresTheme.gold)
                                        SelectionBadge()
                                    }
                                }.padding(12)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .selectionFrame(draft.banner == banner.key)
                            .contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityIdentifier("profile.banner.\(banner.key)")
                            .accessibilityValue(draft.banner == banner.key ? "Seleccionado" : "")
                    }
                } else {
                    if selected == .avatar {
                        PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                            Label("ELEGIR FOTO DEL IPHONE", systemImage: "photo")
                        }.buttonStyle(TraidoresButtonStyle()).disabled(loadingPhoto)
                            .accessibilityIdentifier("profile.photoPicker")
                        Text("La foto se recorta al círculo del avatar y se guarda automáticamente en este iPhone.")
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
        HStack(spacing: 10) {
            // The rule only fills leftover space; the title never wraps to make room for it.
            Text(title).font(TraidoresTheme.title(19, relativeTo: .title3)).tracking(0.5)
                .layoutPriority(1)
                .accessibilityAddTraits(.isHeader)
            Rectangle().fill(accent.opacity(0.35)).frame(height: 1).accessibilityHidden(true)
        }
        .foregroundStyle(accent).padding(.top, 6)
    }
    private func note(_ text: String, primary: Bool = false) -> some View {
        Text(text).font(primary ? .subheadline : .footnote)
            .foregroundStyle(primary ? TraidoresTheme.text : TraidoresTheme.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
    private func stat(_ label: String) -> some View {
        VStack(spacing: 6) {
            Text(label == "Porcentaje" ? "--%" : "--").font(.title2.bold())
            Text(label).font(.caption).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 16).padding(.horizontal, 4)
        .background(surface, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent.opacity(0.3)))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label): sin datos")
    }
    private func editBadge(size: CGFloat) -> some View {
        Image(systemName: "pencil").font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(accent)
            .frame(width: size, height: size)
            .background(surface, in: Circle())
            .overlay(Circle().stroke(accent.opacity(0.7)))
    }
    private func emoteTile(_ emote: ProfileEmoteContent) -> some View {
        ProfileEmoteImage(emote: emote).frame(height: 66)
            .frame(maxWidth: .infinity).padding(5)
            .background(surface, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent.opacity(0.45)))
    }
    private func editField<Field: View>(title: String, count: Int, limit: Int,
                                        @ViewBuilder field: () -> Field) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            let label = Text(title).font(.caption.bold()).tracking(1).foregroundStyle(accent)
            let counter = Text("\(count)/\(limit)").font(.caption.monospacedDigit())
                .foregroundStyle(TraidoresTheme.secondary)
                .accessibilityLabel("\(count) de \(limit) caracteres")
            // With large text the counter moves below the label instead of hyphenating it.
            ViewThatFits(in: .horizontal) {
                HStack { label.fixedSize(); Spacer(); counter.fixedSize() }
                VStack(alignment: .leading, spacing: 2) { label; counter }
            }
            field()
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(Color.black.opacity(0.28), in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(accent.opacity(0.45)))
        }
    }
}

private extension View {
    /// Inner profile card: a faint accent wash and border so it reads against the panel in every style.
    func profileCard(accent: Color, surface: Color, padding: CGFloat = 16) -> some View {
        self.padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: 12).fill(surface)
                    .overlay(RoundedRectangle(cornerRadius: 12).fill(accent.opacity(0.07)))
            }
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(accent.opacity(0.35)))
    }
}

private struct ProfileStyleSelector: View {
    @State private var preview: ProfileStyle
    let name, bio, avatar: String
    let photo: Data?
    let equip: (String) -> Void
    init(theme: String, name: String, bio: String, avatar: String, photo: Data?, equip: @escaping (String) -> Void) {
        _preview = State(initialValue: ProfileStyle(rawValue: theme) ?? .classic)
        self.name = name; self.bio = bio; self.avatar = avatar; self.photo = photo; self.equip = equip
    }
    var body: some View {
        VStack(spacing: 12) {
            ZStack {
                ProfileStripImage(image: preview.backgroundAsset ?? "fondo_menu", height: 250)
                VStack(spacing: 10) {
                    ProfilePortrait(image: avatar, photoData: photo).frame(width: 92, height: 92)
                        .overlay(Circle().strokeBorder(LinearGradient(colors: preview.frame, startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 3))
                    Text(name).font(.title2.bold()).foregroundStyle(preview.text)
                    Text(preview.name.uppercased()).font(.caption.bold()).tracking(1).foregroundStyle(preview.primary)
                    Text(bio.isEmpty ? "Tu frase" : bio).font(.subheadline).foregroundStyle(preview.text).padding(12)
                        .background(LinearGradient(colors: preview.fill, startPoint: .topLeading, endPoint: .bottomTrailing),
                                    in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(preview.primary.opacity(0.7)))
                }.padding(18)
            }.frame(height: 250).clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(LinearGradient(colors: preview.frame, startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 2))
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Vista previa: \(preview.name)")
            Text("TOCÁ UN ESTILO PARA EQUIPARLO").font(.caption.bold()).tracking(1)
                .foregroundStyle(TraidoresTheme.secondary).frame(maxWidth: .infinity)
            ForEach(ProfileStyle.allCases) { style in
                let selected = preview == style
                Button { preview = style; equip(style.rawValue) } label: {
                    HStack(spacing: 12) {
                        // Each row wears its own palette so the styles can be told apart at a glance.
                        Circle().fill(LinearGradient(colors: style.frame, startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 22, height: 22)
                            .overlay(Circle().stroke(TraidoresTheme.ink, lineWidth: 1))
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(style.pickerTitle).font(.headline).foregroundStyle(style == .classic ? TraidoresTheme.gold : style.text)
                            if selected {
                                Text("EQUIPADO").font(.caption2.bold()).tracking(1).foregroundStyle(style.primary)
                            }
                        }
                        Spacer(minLength: 0)
                        if selected { SelectionBadge(tone: style.primary) }
                    }
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(LinearGradient(colors: style.fill, startPoint: .leading, endPoint: .trailing),
                                in: RoundedRectangle(cornerRadius: 12))
                    .overlay {
                        if !selected {
                            RoundedRectangle(cornerRadius: 12)
                                .strokeBorder(LinearGradient(colors: style.frame, startPoint: .leading, endPoint: .trailing).opacity(0.55), lineWidth: 1)
                        }
                    }
                    .selectionFrame(selected, tone: style.primary, accent: style.secondary, fill: .clear)
                    .contentShape(Rectangle())
                }.buttonStyle(.plain).accessibilityIdentifier("profile.style.\(style.rawValue)")
                    .accessibilityLabel(style.name)
                    .accessibilityValue(selected ? "Seleccionado" : "")
            }
        }
    }
}

private struct ProfileEmoteSelector: View {
    @State private var selectedEmotes: [String]
    @State private var limitReached = false
    let apply: ([String]) -> Void
    private let categories: [(id: String, title: String, subtitle: String)] = [
        ("CLASSIC", "CLÁSICOS", "Las reacciones originales de Traidores."),
        ("MEME", "MEMES", "Momentos absurdos para responder sin escribir."),
        ("LEGENDARY", "LEGENDARIOS", "Los emotes más especiales del juego.")
    ]
    init(ids: [String], apply: @escaping ([String]) -> Void) {
        _selectedEmotes = State(initialValue: ids)
        self.apply = apply
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Elegí exactamente 4 emotes · \(selectedEmotes.count)/4").font(.headline)
                    .accessibilityIdentifier("profile.emotes.count")
                Text(limitReached ? "Ya elegiste 4 emotes. Quitá uno para cambiarlo."
                     : selectedEmotes.count == 4 ? "Selección guardada automáticamente. Mantené pulsado un emote para leer su descripción."
                     : "Completá los 4 para guardar. Hasta entonces se conserva tu selección anterior.")
                    .font(.footnote).foregroundStyle(limitReached ? TraidoresTheme.gold : TraidoresTheme.secondary)
            }
            .readableOnArtwork()
            ForEach(categories, id: \.id) { category in
                VStack(alignment: .leading, spacing: 2) {
                    Text(category.title).font(TraidoresTheme.title(19, relativeTo: .title3)).foregroundStyle(TraidoresTheme.gold)
                        .accessibilityAddTraits(.isHeader)
                    Text(category.subtitle).font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                }
                .readableOnArtwork()
                // Three equal columns and fixed card content keep every row aligned, as in Android.
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 18) {
                    ForEach(AndroidMenuReference.content.emotes.filter { $0.category == category.id }) { emote in
                        emoteCard(emote)
                    }
                }
            }
        }
        .onChange(of: selectedEmotes) { _, ids in
            if ids.count == 4 { apply(ids) }
        }
    }

    private func emoteCard(_ emote: ProfileEmoteContent) -> some View {
        let order = selectedEmotes.firstIndex(of: emote.id).map { $0 + 1 }
        let tone = Color(hex: emote.tone)
        return Button {
            if let index = selectedEmotes.firstIndex(of: emote.id) { selectedEmotes.remove(at: index); limitReached = false }
            else if selectedEmotes.count < 4 { selectedEmotes.append(emote.id) }
            else { limitReached = true }
        } label: {
            VStack(spacing: 6) {
                ProfileEmoteImage(emote: emote).frame(height: 70)
                Text(emote.title).font(.footnote.bold()).lineLimit(1).minimumScaleFactor(0.75)
                Text(emote.theme).font(.caption2).foregroundStyle(TraidoresTheme.secondary)
                    .lineLimit(2, reservesSpace: true).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity).padding(.horizontal, 6).padding(.vertical, 10)
            .selectionFrame(order != nil, tone: tone, accent: TraidoresTheme.gold)
            .overlay(alignment: .topTrailing) { if let order { SelectionBadge(number: order).offset(x: 6, y: -6) } }
            .overlay(alignment: .topLeading) {
                if emote.animated || emote.premium {
                    Text(emote.animated ? "ANIMADO" : "PREMIUM").font(.system(size: 9, weight: .heavy))
                        .foregroundStyle(TraidoresTheme.ink).padding(.horizontal, 5).padding(.vertical, 2)
                        .background(TraidoresTheme.gold, in: Capsule()).padding(6)
                        .accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu { Text(emote.description) }
        .accessibilityIdentifier("profile.emote.\(emote.id)")
        .accessibilityLabel("\(emote.title), \(emote.theme)")
        .accessibilityValue(order.map { "Seleccionado, \($0) de 4" } ?? "")
        .accessibilityHint(emote.description)
    }
}

private struct ProfileAchievementCatalogView: View {
    @State private var selected: ProfileAchievementContent?
    var body: some View {
        VStack(spacing: 12) {
            ForEach(AndroidMenuReference.content.achievements) { item in
                let rarity = Rarity(item.rarity)
                Button { selected = item } label: {
                    HStack(spacing: 14) {
                        // Medal in a ringed frame (Android `bg_achievement_medal_frame`), dimmed while locked.
                        Image(systemName: "medal.fill").font(.system(size: 22))
                            .foregroundStyle(rarity.border.opacity(0.75))
                            .frame(width: 48, height: 48)
                            .background(TraidoresTheme.panel, in: Circle())
                            .overlay(Circle().strokeBorder(rarity.border, lineWidth: 1.5))
                            .overlay(alignment: .bottomTrailing) {
                                Image(systemName: "lock.fill").font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(TraidoresTheme.ink).frame(width: 18, height: 18)
                                    .background(TraidoresTheme.secondary, in: Circle())
                            }
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title).font(.headline).foregroundStyle(rarity.title)
                            Text("Bloqueado · \(rarity.name)").font(.caption.bold()).foregroundStyle(rarity.border)
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").foregroundStyle(rarity.border.opacity(0.8)).accessibilityHidden(true)
                    }
                    .padding(12)
                    .background(rarity.background, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(rarity.border.opacity(0.7), lineWidth: 1.5))
                    .contentShape(Rectangle())
                }.buttonStyle(.plain).accessibilityIdentifier("profile.achievement.\(item.id)")
                    .accessibilityLabel("\(item.title), logro de \(rarity.name.lowercased()), bloqueado")
            }
        }
        .alert(selected?.title ?? "Logro", isPresented: Binding(get: { selected != nil }, set: { if !$0 { selected = nil } })) {
            Button("ENTENDIDO") { selected = nil }
        } message: { Text(selected?.description ?? "") }
    }

    /// Colors of Android's `achievementVisualStyle`.
    private struct Rarity {
        let name: String, border: Color, title: Color, background: Color
        init(_ raw: String) {
            switch raw {
            case "GOLD": name = "Oro"; border = Color(hex: "#FFF2A3"); title = Color(hex: "#FFF7B2"); background = Color(hex: "#F2140F05")
            case "SILVER": name = "Plata"; border = Color(hex: "#DCEAFF"); title = Color(hex: "#E7F1FF"); background = Color(hex: "#E6202830")
            default: name = "Bronce"; border = Color(hex: "#F0A35A"); title = Color(hex: "#FFB56E"); background = Color(hex: "#E6332017")
            }
        }
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
                        .accessibilityAddTraits(selectedMap == map.id ? .isSelected : [])
                }
            }
            ForEach(AndroidMenuReference.content.maps.filter { $0.id == selectedMap }) { map in
                Text(map.title).font(TraidoresTheme.title(20)).foregroundStyle(TraidoresTheme.gold)
                    .readableOnArtwork()
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                    ForEach(map.roles) { role in
                        let selected = currentImage == role.image
                        Button { choose(role) } label: {
                            VStack(spacing: 8) {
                                Image(role.image).resizable().scaledToFit().frame(height: 150)
                                    .clipShape(RoundedRectangle(cornerRadius: 8)).accessibilityHidden(true)
                                Text(role.title).font(.subheadline.bold()).multilineTextAlignment(.center)
                                    .lineLimit(2, reservesSpace: true)
                            }.frame(maxWidth: .infinity).padding(10)
                                .selectionFrame(selected)
                                .overlay(alignment: .topTrailing) { if selected { SelectionBadge().offset(x: 6, y: -6) } }
                                .contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityIdentifier("profile.choice.\(map.id).\(role.id)")
                            .accessibilityValue(selected ? "Seleccionado" : "")
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
    @Environment(\.reduceAnimations) private var reduceMotion
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

/// Same sections and copy as Android's `OpcionesActivity`. Notifications and the online
/// measurement need the online stage and are added with it; the language picker is hidden
/// on Android too.
struct OptionsView: View {
    @Environment(MenuPreferences.self) private var preferences
    @State private var resetDone = false

    var body: some View {
        @Bindable var preferences = preferences
        MenuPage(title: "OPCIONES") {
            Text("Ajusta el juego para que sea cómodo de leer y escuchar.")
                .foregroundStyle(TraidoresTheme.text).readableOnArtwork()
            VStack(alignment: .leading, spacing: 12) {
                optionsHeading("SONIDO Y RESPUESTA")
                Toggle("Música", isOn: $preferences.musicEnabled)
                    .font(.headline).tint(TraidoresTheme.gold)
                    .accessibilityIdentifier("options.music")
                Text("Controla por separado la música y los efectos del juego.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                Text("Música: \(Int((preferences.musicVolume * 100).rounded()))%")
                    .font(.subheadline.bold()).monospacedDigit()
                    .accessibilityIdentifier("options.volumeLabel")
                Slider(value: $preferences.musicVolume, in: 0...1, step: 0.05)
                    .tint(TraidoresTheme.gold)
                    .disabled(!preferences.musicEnabled)
                    .accessibilityLabel("Volumen de música")
                    .accessibilityIdentifier("options.volume")
                Divider()
                Toggle("Efectos de sonido", isOn: $preferences.effectsEnabled)
                    .font(.headline).tint(TraidoresTheme.gold)
                    .accessibilityIdentifier("options.effects")
                Text("Efectos: \(Int((preferences.effectsVolume * 100).rounded()))%")
                    .font(.subheadline.bold()).monospacedDigit()
                Slider(value: $preferences.effectsVolume, in: 0...1, step: 0.05)
                    .tint(TraidoresTheme.gold).disabled(!preferences.effectsEnabled)
                    .accessibilityLabel("Volumen de efectos")
                    .accessibilityIdentifier("options.effectsVolume")
                Divider()
                Toggle("Vibración al interactuar", isOn: $preferences.vibrationEnabled)
                    .font(.headline).tint(TraidoresTheme.gold)
                    .accessibilityIdentifier("options.vibration")
            }
            .padding(20)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
            .sensoryFeedback(.success, trigger: preferences.vibrationEnabled) { _, enabled in enabled }
            VStack(alignment: .leading, spacing: 12) {
                optionsHeading("LECTURA Y ACCESIBILIDAD")
                HStack {
                    Text("Tamaño del texto").font(.headline)
                    Spacer(minLength: 8)
                    Picker("Tamaño del texto", selection: $preferences.textSize) {
                        ForEach(MenuTextSize.allCases) { size in Text(size.title).tag(size) }
                    }
                    .labelsHidden()
                    .tint(TraidoresTheme.gold)
                    .accessibilityIdentifier("options.textSize")
                }
                Text("Se aplica a mensajes, botones y datos durante la partida.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                Text("El pueblo despierta. Escucha, debate y decide.")
                    .font(.body).accessibilityIdentifier("options.textPreview")
            }
            .padding(20)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 12) {
                optionsHeading("EFECTOS VISUALES")
                Toggle("Reducir animaciones", isOn: $preferences.reduceAnimations)
                    .font(.headline).tint(TraidoresTheme.gold)
                    .accessibilityIdentifier("options.reduceAnimations")
                Text("Quita partículas y simplifica transiciones para una experiencia más tranquila.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
            .padding(20)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
            NavigationLink { AboutView() } label: { Text("ACERCA DE TRAIDORES") }
                .buttonStyle(TraidoresButtonStyle())
                .accessibilityIdentifier("options.about")
            // Like Android: resets at once and confirms with a brief message.
            Button("RESTABLECER OPCIONES") {
                preferences.resetMenuOptions()
                resetDone = true
            }
                .buttonStyle(TraidoresButtonStyle())
                .accessibilityIdentifier("options.reset")
        }
        .overlay(alignment: .bottom) {
            if resetDone {
                Text("Opciones restablecidas.")
                    .font(.subheadline.bold()).foregroundStyle(TraidoresTheme.text)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(TraidoresTheme.ink, in: Capsule())
                    .overlay(Capsule().stroke(TraidoresTheme.gold.opacity(0.7)))
                    .padding(.bottom, 24)
                    .transition(.opacity)
                    .accessibilityIdentifier("options.resetDone")
                    .task {
                        try? await Task.sleep(for: .seconds(2))
                        withAnimation { resetDone = false }
                    }
            }
        }
        .animation(.easeOut(duration: 0.2), value: resetDone)
    }
}

private func optionsHeading(_ title: String) -> some View {
    Text(title).font(TraidoresTheme.title(17, relativeTo: .headline)).tracking(0.5)
        .foregroundStyle(TraidoresTheme.gold)
        .accessibilityAddTraits(.isHeader)
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
