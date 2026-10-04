# Plan iOS: acceso, perfil público y sala — 3 de octubre de 2026

Responde al encargo de `docs/reparto-codex-claude-fotos-online.md`. Claude se encarga de las vistas SwiftUI en `Features/` y de registrar archivos y paquetes en `project.pbxproj`. Codex se encarga de los servicios en `Platform/Online/`, de Android, de las reglas y de Firebase. Este documento propone lo que las vistas necesitan consumir; no hay código implementado todavía.

Se conservan sin cambios los ajustes de fotos locales sin commit: `MenuDestinations.swift`, `LocalGameView.swift`, `VoteCeremonyView.swift` y `MatchResultView.swift`. En resultados queda carta → nombre → rol → foto de 24 pt. No vuelve la foto de Google Play Games.

## 1. Alcance de este bloque

Entra en este bloque:

1. **Acceso:** entrar como invitado, vincular cuenta, ver el estado de la cuenta y la pantalla de cuenta suspendida (`bans/{uid}`).
2. **Perfil público:** mostrar y editar `perfiles_publicos/{uid}` con `fotoPerfil`. Incluye subir, reemplazar y quitar la foto, con estados de subida y error. La copia local sigue disponible sin red.
3. **Pantalla online** con los mismos botones que Android: «REINGRESAR A PARTIDA», «BUSCAR PARTIDA», «UNIRSE POR CÓDIGO» y «CREAR PARTIDA».
4. **Salas:** buscar salas públicas, unirse por código de 6 caracteres y crear una sala con nombre, mapa, cantidad, visibilidad y «solo cuentas».
5. **Lobby:** lista de jugadores con foto o inicial, estado listo, voto de mapa, configuración (editable solo por el anfitrión), presencia y salida segura.

Queda fuera de este bloque: el gameplay online, el chat del lobby, expulsar o banear desde la interfaz y las notificaciones. Mientras el recorrido no se pruebe entre plataformas, el modo online queda detrás de un flag de compilación o de un argumento de lanzamiento. Los builds normales siguen mostrando «PRÓXIMAMENTE».

## 2. Interfaces que necesitan las vistas

> Codex ya implementó esta propuesta en `Platform/Online/OnlineModels.swift`, `OnlineServices.swift` y `OnlineContract.swift`, con cuenta por correo y contraseña, alias de invitado y estados de foto. Esos archivos son la referencia; el bosquejo de abajo queda como antecedente.

Propuesta para que Codex la ajuste. Los nombres siguen el esquema de Firestore, y las vistas nunca importan Firebase.

```swift
// Platform/Online/OnlineModels.swift (Codex)
struct OnlineIdentity: Equatable, Sendable {
    let uid: String
    let publicId: Int?          // nil para invitados
    let isRegistered: Bool
    let displayName: String     // alias de invitado o nombrePerfil
}

enum OnlineAccessState: Equatable, Sendable {
    case signedOut, connecting
    case ready(OnlineIdentity)
    case suspended(reason: String)
    case failed(OnlineError)
}

struct PublicProfile: Equatable, Sendable {
    var uid: String, publicId: Int?
    var nombrePerfil: String, nombreSala: String, bioPerfil: String
    var avatarPerfil: String, bannerPerfil: String, rolFavoritoPerfil: String
    var fotoPerfil: URL?
}

enum PhotoSyncState: Equatable, Sendable { case idle, uploading, failed(OnlineError) }

struct RoomSummary: Identifiable, Equatable, Sendable {
    let id: String, code: String, name: String, mapKey: String, hostName: String
    let current: Int, expected: Int, accountsOnly: Bool
}

struct RoomDraft: Equatable, Sendable {
    var name: String, mapKey: String, expected: Int   // 5...15
    var isPublic: Bool, accountsOnly: Bool
}

struct LobbyConfig: Equatable, Sendable {
    var transicionSeg: Int, nocheSeg: Int, discusionSeg: Int, votacionSeg: Int
    var revelarRolesAlMorir: Bool, votosIndividuales: Bool
}

struct RoomPlayer: Identifiable, Equatable, Sendable {
    let id: String               // uid; nunca el nombre
    let order: Int, nombreSala: String, publicId: Int?
    let fotoPerfil: URL?, avatarPerfil: String
    let isHost: Bool, isReady: Bool, isConnected: Bool, mapVote: String?
}

struct RoomSnapshot: Equatable, Sendable {
    let id: String, code: String, name: String, mapKey: String
    let expected: Int, isPublic: Bool, accountsOnly: Bool
    let hostId: String, config: LobbyConfig
    let players: [RoomPlayer]
    let phase: RoomPhase         // esperando, iniciando(matchId), enJuego, cerrada
}

enum ConnectionState: Equatable, Sendable { case live, reconnecting, lost }

enum OnlineError: Error, Equatable, Sendable {
    case offline, sessionExpired, permissionDenied, accountRequired
    case roomNotFound, roomFull, roomAlreadyStarted, ambiguousCode
    case suspended(String), server(String?)
}
```

```swift
// Platform/Online/OnlineServices.swift (Codex)
@MainActor protocol OnlineAccountService: AnyObject, Observable {
    var access: OnlineAccessState { get }
    func enterAsGuest() async
    func linkAccount() async throws         // ver decisión 3.1
    func signOut() async
}

@MainActor protocol PublicProfileService: AnyObject, Observable {
    var profile: PublicProfile? { get }
    var photoSync: PhotoSyncState { get }
    func refresh() async throws
    func save(_ profile: PublicProfile) async throws
    func setPhoto(jpegData: Data) async throws   // el servicio recorta, recodifica y sube
    func removePhoto() async throws
}

@MainActor protocol RoomDirectoryService: AnyObject {
    func publicRooms() async throws -> [RoomSummary]
    func create(_ draft: RoomDraft) async throws -> String    // roomId
    func join(code: String) async throws -> String
    func recoverableRoom() async -> RoomSummary?
}

@MainActor protocol RoomSessionService: AnyObject, Observable {
    var snapshot: RoomSnapshot? { get }
    var connection: ConnectionState { get }
    func attach(roomId: String) async throws
    func setReady(_ ready: Bool) async throws
    func voteMap(_ key: String) async throws
    func updateConfig(_ config: LobbyConfig) async throws     // solo anfitrión
    func start() async throws                                  // solo anfitrión
    func leave() async
}
```

Se inyectan con `@Environment` desde `TraidoresApp`. Para las pruebas de interfaz, Claude escribe `OnlineFakeServices` en `Features/Online/Testing/`. La implementación falsa es en memoria y se elige con `-ui-testing-online <escenario>`. Así las vistas se prueban sin red y sin emulador, y las reglas siguen siendo de Codex.

## 3. Decisiones tomadas (3/10, noche)

Las tres primeras se resolvieron e implementaron con el usuario. El online de iOS sigue sin pantallas y el gameplay de iOS todavía se está puliendo.

1. **Iniciar sesión con Apple.**
   - El proveedor Apple está habilitado en Firebase Auth. Para iOS no lleva ID de servicios.
   - `Platform/Online/AppleAccountLink.swift` está pensado para `SignInWithAppleButton` y hace lo siguiente:
     - prepara el nonce;
     - si hay un invitado, vincula su UID;
     - si el Apple ID ya tiene cuenta, la recupera;
     - fuerza la actualización del token, como `AccountLink.refreshClaims`.
   - **Hace falta el Apple Developer Program pago (USD 99 por año).** El equipo actual (`NPXYQ28D3M`) es gratuito, con perfiles que vencen a los 7 días, y no puede firmar esta capacidad ni App Attest. Por eso las capacidades están en `TraidoresIOS-Online.entitlements` y se activan desde `Local.xcconfig` cuando exista el equipo pago. Mientras tanto la app sigue instalándose en el iPhone.
   - Antes de publicar una cuenta, la App Store también exige eliminarla desde la app, y para Apple eso incluye revocar el token.
2. **App iOS registrada en Firebase.**
   - Bundle `com.traidores.juego.ios`, app ID `1:99323018581:ios:87bfb0250d63c3138083dc`, en el proyecto `traidores`, con el plan Spark sin cambios.
   - `GoogleService-Info.plist` se agregó al target, con Analytics y anuncios desactivados.
   - Firebase iOS SDK 12.19.2 por SPM, con FirebaseCore, Auth, Functions y AppCheck. El archivo `Package.resolved` está en `project.xcworkspace`.
   - `FirebaseSetup` configura Firebase recién en el primer uso online, nunca al abrir la app.
   - App Check usa el proveedor de depuración en Debug y App Attest en Release.
   - `-firebase-emulator-host <host>` (solo Debug) apunta Auth y Functions a los emuladores, en los puertos 9099 y 5001.
3. **Cómo empieza la partida.**
   - Un dispositivo iOS solamente llama a `iniciarPartidaV2`; nunca reparte roles.
   - El contrato vive en TraidoresCore (`OnlineMatchStart.swift`) y cubre la solicitud, la respuesta (`started`, `already_started` y `tie_break_required`) y los errores con los textos de `OnlineErrorMessages.kt`. Tiene 5 pruebas.
   - `Platform/Online/OnlineMatchStartClient.swift` hace la llamada.
   - **Corrección según la revisión de Codex:** la función reparte, pero deja al que la llamó como `hostActivoId`, y nada en el servidor resuelve las fases siguientes. Si un iPhone inicia, la partida queda sin autoridad. Por eso el inicio real queda desactivado (`startAvailability == .unavailable(.onlineGameplay)`) hasta portar un anfitrión compatible o crear la autoridad de servidor.
   - La función usa el mapa elegido por el anfitrión e ignora los votos (`resolveMap`), aunque `firebase-online-schema.md` todavía describe la votación.
4. **Alias de invitado.** La lista cerrada vive en `GuestIdentity.aliases` y en las reglas. El servicio iOS debe tomarla de una única fuente; la vista nunca permite editar el nombre de un invitado.
5. **Cierre del voto online.** Hay diferencia entre los 3 s de Android y los 5 s pedidos (`ONLINE_CONTRACT.md`). No afecta este bloque, pero debe quedar fijado antes del gameplay.

**Verificado contra emuladores (3/10).** El chequeo `FirebaseSmokeCheck` del simulador, en Debug, recorrió Auth emulado → `iniciarPartidaV2`:
- una sala inexistente devolvió `roomNotFound`;
- una sala de 5 jugadores listos devolvió `accepted(matchId, pampa, alreadyStarted: false)`;
- el segundo llamado devolvió la misma partida con `alreadyStarted: true`.

Para repetirlo:
1. Node 24 está instalado en `~/.local/node/bin` (verificado con SHA-256 de nodejs.org); correr `npm install` en la raíz y en `functions/`.
2. Como `firebase.json` no declara el emulador de Auth, usar una copia de la configuración con `"auth": {"port": 9099}`.
3. Levantar `firebase emulators:start --project traidores --only auth,firestore,database,functions` con `JAVA_HOME` del JBR de Android Studio.
4. Crear con firebase-admin un usuario en el Auth emulado y una sala lista.
5. Abrir la app con `-firebase-emulator-host 127.0.0.1 -firebase-smoke-room <id> -firebase-smoke-email <correo> -firebase-smoke-password <clave>`.

En modo emulador no se configura App Check, así que no sale ningún pedido al proyecto real.

## 4. Archivos

**Nuevos (Claude), en `Features/Online/`:**

| Archivo | Contenido |
|---|---|
| `OnlineModeView.swift` | Pantalla online: estado de acceso y los cuatro botones; reingreso si `recoverableRoom` existe. |
| `OnlineAccessView.swift` | Invitado o cuenta; vínculo con progreso, cancelación y error; cuenta suspendida con motivo. |
| `RoomBrowserView.swift` | Lista de salas, vacío, cargando, sin conexión y «REINTENTAR». |
| `JoinRoomSheet.swift` | Código de 6 caracteres con el formato `A-HJ-NP-Z2-9`, validación local y errores del servidor. |
| `CreateRoomSheet.swift` | Nombre, mapa, cantidad 5–15, pública/privada y solo cuentas; deshabilitado para invitados con explicación. |
| `OnlineLobbyView.swift` | Jugadores con foto/inicial, listo, voto de mapa, configuración, banner de reconexión y salir. |
| `OnlineStatusViews.swift` | Componentes compartidos de carga, error con reintento y aviso de desconexión. |
| `OnlineErrorCopy.swift` | Textos en español equivalentes a `OnlineErrorMessages.kt`. |
| `Testing/OnlineFakeServices.swift` | Escenarios falsos para pruebas y vistas previas. |

**Modificados (Claude):**
- `Features/Menu/MenuDestinations.swift`:
  - la tarjeta «JUGAR ONLINE» navega a `OnlineModeView` cuando el flag está activo;
  - `ProfileView` suma la sección de cuenta, el ID público `#N` y el estado de subida de foto;
  - `GamePlayerAvatar` y `ProfilePortrait` ya aceptan URL remota y se reutilizan;
  - se quitan los textos «todavía no disponible» solo cuando corresponda.
- `App/TraidoresApp.swift`: inyección de servicios reales o falsos.
- `TraidoresIOS.entitlements`: Sign in with Apple, si se decide en 3.1.
- `project.pbxproj`: archivos nuevos de Claude y de Codex, y el paquete Firebase. Se valida con `plutil -lint`.

**Fuera de mi alcance:** `Platform/Online/**` (Codex), `app/**`, `firebase.json`, `firestore.rules`, `storage.rules` y la facturación.

## 5. Pruebas de interfaz

El archivo nuevo es `TraidoresIOSUITests/OnlineFlowUITests.swift` y usa los escenarios falsos.

- **Acceso:**
  - entrar como invitado;
  - vínculo cancelado y vínculo fallido con reintento;
  - cuenta suspendida, que muestra el motivo y no permite entrar.
- **Perfil:**
  - foto subiendo → publicada;
  - subida fallida conserva la foto local y ofrece reintentar;
  - quitar foto vuelve a la inicial;
  - el ID `#N` es visible y no editable;
  - el invitado no edita nombre ni frase.
- **Buscar:** lista, vacío, sin conexión → «REINTENTAR» → lista.
- **Unirse por código:**
  - formato inválido (sin llamar al servicio);
  - sala inexistente, llena o ya iniciada;
  - solo cuentas siendo invitado;
  - código válido → lobby.
- **Crear:**
  - el invitado ve la explicación y no puede crear;
  - el registrado crea → lobby como anfitrión.
- **Lobby:**
  - foto y respaldo por jugador;
  - un bot o jugador con el mismo nombre no hereda la foto (identidad por uid);
  - listo/no listo;
  - controles de anfitrión ocultos para los demás;
  - reconexión → banner → recuperado;
  - conexión perdida → salir seguro.
- **Accesibilidad:**
  - `performAccessibilityAudit` en cada pantalla con el tamaño de texto del sistema y con el más grande;
  - todos los botones accesibles sin desplazarse en iPhone 17;
  - VoiceOver anuncia nombre y estado de cada jugador.
- **Texto visible:** ninguna pantalla menciona plataformas.

Las pruebas contra el emulador de Firebase y la prueba con dos cuentas reales Android ↔ iOS corresponden al paso 3 de la secuencia conjunta, cuando los servicios de Codex existan.

## 6. Orden propuesto

1. Codex confirma o ajusta la sección 2 y se toman las decisiones 3.1–3.3.
2. Claude implementa las vistas contra `OnlineFakeServices` y sus pruebas, sin depender del SDK.
3. Codex entrega `Platform/Online/`; Claude registra archivos y paquete y conecta la inyección real.
4. Recorrido conjunto contra el emulador, después con dos cuentas reales cuando se active la prueba Cloud.

## 7. Estado de las vistas (4/10)

Implementadas en `Features/Online/` contra las interfaces de Codex (`OnlineServices`), con servicios falsos en memoria (`OnlineFakeServices.swift`, solo Debug). No hay datos ni llamadas reales a Firebase.

**Pantallas, siguiendo Android a pedido del usuario:**
- **«JUGAR EN LÍNEA»** (`OnlineModeView`): título dorado centrado, línea de estado y los botones de Android. Reintenta el acceso por sí sola («El servidor no responde. Reintentando…»). Suma una tarjeta de identidad compacta: retrato, nombre, «INVITADO» o «CUENTA», número y estado de la foto.
- **Cuenta** como Android (`OnlineAccountFlow`):
  - tarjeta «CUENTA» en el Perfil;
  - ventana «Tu cuenta», con correo, contraseña y un solo CONTINUAR (`linkAccount`, que vincula o recupera), y la misma validación local que `AccountCredentials`;
  - aviso «Cuenta vinculada exitosamente».
  - No hay cierre de sesión, como en Android. El botón de Apple aparece solo si `appleSignInAvailable`.
- **Ventanas `GameDialog`** (`OnlineDialogs.swift`): «UNIRSE POR CÓDIGO», «CREAR SALA ONLINE» (jugadores, nombre, pública o privada), «Solo con cuenta», «Acceso online suspendido» y «¿Salir de la sala?». Se presentan aparte, como el panel de opciones de la mesa, para que VoiceOver no lea la pantalla de abajo. Si se pide una mientras otra se cierra, la nueva espera a que termine de cerrarse.
- **«BUSCAR PARTIDA»** (`RoomBrowserView`): lista, vacío, error con reintento. Entrar abre el lobby desde la lista.
- **Lobby** (`OnlineLobbyView`):
  - código con COPIAR y COMPARTIR;
  - jugadores con foto o avatar por UID, ANFITRIÓN/LISTO/ESPERANDO/DESCONECTADO y ESTOY LISTO;
  - reglas editables solo por el anfitrión y votación de mapa;
  - aviso de reconexión; una salida fallida no saca al jugador de la sala.
  - «INICIAR PARTIDA» se ve bloqueado mientras `startAvailability` no sea `.ready`.

**Inyección:** `OnlineBootstrap.services()` en la raíz de la app.
- En Release devuelve `nil` y la tarjeta conserva «PRÓXIMAMENTE»; el binario no incluye los servicios falsos.
- En Debug se activa con `-ui-testing-online <guest|registered|offline|suspended|empty>` o con `-online-preview`. Este último queda guardado en ese dispositivo y se apaga con `-online-preview-off`.
- Los adaptadores reales de Codex reemplazan a `FakeOnlineScenario.makeServices()` en ese único punto.

**Pruebas:** `OnlineFlowUITests`, 9 recorridos con auditoría de accesibilidad en cada pantalla y ventana; Release compila sin los servicios falsos. Excepciones documentadas en la prueba:
- «Text clipped» queda fuera de la auditoría: marcaba textos distintos y visibles en cada corrida. El recorte se revisa con capturas, incluida AX5.
- Detrás de una ventana, la pantalla de abajo se ve oscurecida pero VoiceOver la omite, lo correcto. El auditor lo reporta como texto inaccesible.
- En el Perfil desplazado se ignora el contraste del texto que queda bajo el título.
