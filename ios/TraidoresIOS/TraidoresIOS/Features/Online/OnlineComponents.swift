import SwiftUI
import TraidoresCore

extension OnlineError {
    /// Same wording as Android's `OnlineErrorMessages` and `AccountLink`, with accents.
    var message: String {
        switch self {
        case .offline: "No hay conexión estable con el servidor. Probá otra vez."
        case .sessionExpired: "Tu sesión venció. Volvé a entrar al modo online."
        case .permissionDenied: "El servidor rechazó la acción. Probá otra vez."
        case .accountRequired: "Esta sala es solo para cuentas. Creá tu cuenta o entrá con ella para unirte."
        case .cancelled: "La operación se interrumpió. Probá otra vez."
        case .invalidCredentials: "El correo o la contraseña no son correctos."
        case .emailInUse: "Ese correo ya pertenece a otra cuenta. Entrá con él para recuperarla."
        case .weakPassword: "Esa contraseña es muy débil. Probá con una más larga."
        case .invalidCode: "El código debe tener 6 caracteres, sin I, O, 0 ni 1."
        case .invalidProfile: "El perfil tiene datos inválidos. Revisá el nombre y la frase."
        case .invalidImage: "No se pudo leer esa imagen. Elegí otra."
        case .imageTooLarge: "La foto es demasiado grande, incluso después de comprimirla. Elegí otra."
        case .invalidRoomConfiguration: "La configuración de la sala no es válida."
        case .roomNotFound: "No existe una sala con ese código."
        case .roomFull: "La sala está llena."
        case .roomAlreadyStarted: "La partida de esa sala ya empezó."
        case .roomChanged: "La sala cambió mientras se aplicaba la acción. Probá otra vez."
        case .roomNotReady: "La sala todavía no está lista."
        case .incompatibleRoom: "Esa sala usa una versión del juego que no es compatible."
        case .suspended(let reason): reason
        case .featureUnavailable(let feature): feature.unavailableMessage
        case .server(let detail): detail ?? "El servidor no pudo completar la acción. Probá otra vez."
        }
    }

    /// Retrying the same request can help; a wrong code or a full room needs a different one.
    var isTransient: Bool {
        switch self {
        case .offline, .cancelled, .roomChanged, .server, .sessionExpired: true
        default: false
        }
    }
}

extension OnlineFeature {
    var unavailableMessage: String {
        switch self {
        case .configuration: "El modo online no está configurado en esta versión."
        case .profileStorage: "Las fotos online todavía no están habilitadas."
        case .onlineGameplay: "La partida online todavía no está disponible en esta versión. Podés armar la sala y esperar a los demás."
        case .appleSignIn: "El acceso con Apple todavía no está disponible en esta versión."
        case .googleSignIn: "El acceso con Google todavía no está disponible en esta versión."
        }
    }
}

enum OnlineAvatarArt {
    static func key(for asset: String) -> String {
        if AnimalAvatarCatalog.keys.contains(asset) { return asset }
        let parts = asset.split(separator: "_").map(String.init)
        guard parts.count == 3, parts[0] == "rol" else { return "pampa_aldeano" }
        let map = switch parts[2] { case "griego": "grecia"; case "medieval": "medieval"; default: "pampa" }
        let role = parts[1] == "detective" ? "policia" : parts[1]
        guard OnlineContract.roleKeys.contains(role) else { return "pampa_aldeano" }
        return "\(map)_\(role)"
    }
    private static let legacy = ["aldeana": "pampa_aldeano", "detective": "pampa_policia", "medica": "pampa_medico",
                                 "alcalde": "pampa_alcalde", "asesino": "pampa_asesino", "espia": "pampa_espia",
                                 "mercenario": "pampa_mercenario", "desertora": "pampa_desertor",
                                 "payador": "pampa_payador", "bufon": "medieval_bufon", "oraculo": "grecia_oraculo"]

    /// Android's `ProfileRoleCatalog` key (`pampa_policia`) to the role artwork in the catalog.
    static func asset(for key: String) -> String {
        if AnimalAvatarCatalog.keys.contains(key) { return key }
        let normalized = legacy[key] ?? key
        let parts = normalized.split(separator: "_", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return "rol_aldeano_gaucho" }
        let suffix = switch parts[0] { case "grecia": "griego"; case "medieval": "medieval"; default: "gaucho" }
        let role = parts[1] == "policia" ? "detective" : parts[1]
        let name = "rol_\(role)_\(suffix)"
        return UIImage(named: name) == nil ? "rol_aldeano_gaucho" : name
    }
}

/// Online portrait: published photo first, then the illustrated avatar. Local gallery bytes
/// only appear when the owner passes them explicitly, so an account switch never shows the
/// previous owner's photo.
struct OnlinePortrait: View {
    let photoURL: URL?
    let avatarKey: String
    let size: CGFloat
    var localPhoto: Data? = nil

    var body: some View {
        ProfilePortrait(image: AnimalAvatarCatalog.normalize(avatarKey), photoData: localPhoto, photoURL: photoURL)
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(TraidoresTheme.border, lineWidth: 1))
        .accessibilityHidden(true)
    }

}

struct OnlinePanel: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            // Opaque: translucent panels read as low contrast in the accessibility audit.
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
            .overlay { RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border) }
    }
}

extension View {
    func onlinePanel() -> some View { modifier(OnlinePanel()) }

    /// Keep readable text at the scroll boundary when large type puts controls there.
    @ViewBuilder func onlineScrollEdges() -> some View {
        if #available(iOS 26.0, *) { scrollEdgeEffectHidden() }
        else { self }
    }
}

struct OnlineSectionLabel: View {
    let title: String
    init(_ title: String) { self.title = title }

    var body: some View {
        Text(title)
            .font(.caption.bold())
            .tracking(1.2)
            .foregroundStyle(TraidoresTheme.secondary)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Loading, error and retry in one place so every online screen behaves the same.
struct OnlineStatusCard: View {
    enum Status: Equatable {
        case loading(String)
        case failed(OnlineError)
    }

    let status: Status
    var retry: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 12) {
            switch status {
            case .loading(let text):
                ProgressView().tint(TraidoresTheme.gold)
                Text(text).font(.body).foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(.center)
            case .failed(let error):
                Image(systemName: error == .offline ? "wifi.exclamationmark" : "exclamationmark.triangle.fill")
                    .font(.title2).foregroundStyle(TraidoresTheme.gold)
                    .accessibilityHidden(true)
                Text(error.message).font(.body).foregroundStyle(TraidoresTheme.text)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("online.status.message")
                if let retry {
                    Button("REINTENTAR", action: retry)
                        .buttonStyle(TraidoresButtonStyle())
                        .accessibilityIdentifier("online.status.retry")
                }
            }
        }
        .frame(maxWidth: .infinity)
        .onlinePanel()
        .accessibilityElement(children: .contain)
    }
}

/// Short inline error under a form or action, announced to VoiceOver when it appears.
struct OnlineInlineError: View {
    static let color = Color(red: 1, green: 0.62, blue: 0.55)
    let error: OnlineError

    var body: some View {
        Label(error.message, systemImage: "exclamationmark.circle.fill")
            .font(.callout)
            .foregroundStyle(Self.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("online.inlineError")
            .onAppear { AccessibilityNotification.Announcement(error.message).post() }
    }
}

/// Dark rounded field used by the online forms.
struct OnlineTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .font(.body)
            .padding(.horizontal, 12)
            .frame(minHeight: 46)
            .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 10))
            .overlay { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border) }
    }
}

extension GameMap {
    init(onlineKey: String) { self = GameMap(rawValue: onlineKey) ?? .pampa }
}
