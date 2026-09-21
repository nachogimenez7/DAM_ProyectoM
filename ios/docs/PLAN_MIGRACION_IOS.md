# Plan técnico: Traidores Android → iOS nativo

Preparado el 18 de septiembre de 2026. Actualizado tras aprobación de la etapa 1: **menú primero, gameplay local contra IA después, online al final**. El proyecto SwiftUI ya tiene estructura y menú; la compilación iOS y la validación visual esperan Xcode. Las secciones de protocolo/Firebase se conservan como requisitos del online futuro.

## 1. Diagnóstico y alcance comprobado

Se clonó `https://github.com/nachogimenez7/DAM_ProyectoM.git` en la carpeta del proyecto. Base auditada: `main`, commit `32e4fc09dfb726d5264b27804bb4f8c1f5ae1a21`. Android declara `versionName = 0.1.34`, `versionCode = 35`, paquete `com.traidores.juego`. El árbol estaba limpio antes de agregar estos documentos.

La inspección fue estática: código Kotlin, recursos XML, configuración Gradle, reglas e índices Firebase, backend JavaScript y pruebas existentes. No se accedió a Firebase Console ni se consultaron usuarios o salas reales. Por eso no se afirma que el commit coincida con el APK publicado ni que las reglas del repositorio estén desplegadas. Ese contraste es requisito para probar en producción.

| Área | Evidencia y consecuencia |
|---|---|
| Aplicación | Un módulo Android `app`; 186 archivos Kotlin en `main` y 75 archivos de pruebas Kotlin. Activities/XML, orientación vertical. |
| Dominio | `GameModels.kt` tiene 1.002 líneas; `GameEngine.kt`, 2.488. Hay políticas y resolutores pequeños que se pueden portar y probar por separado. |
| Acoplamiento | `GameplayMockActivity.kt` tiene 13.098 líneas y `LobbyActivity.kt`, 7.631. Pese al nombre «Mock», la primera contiene gameplay online real, publicaciones, votos y relevo. No basta con traducir `GameEngine`. |
| Identidad | Firebase Auth anónima, correo/contraseña, Google y vinculación de Play Games. `uidTemporal` actualmente transporta el UID autenticado; no es un UUID intercambiable. |
| Persistencia | Firestore guarda salas, jugadores, repartos privados, acciones, perfiles y checkpoint durable. RTDB distribuye estado vivo, presencia, chats y confirmaciones. |
| Seguridad | App Check usa Debug en debug y Play Integrity en release. Las reglas actuales comprueban membresía, autoría, ventanas de voto y acceso a canales. El anfitrión sigue siendo una autoridad de confianza. |
| Observabilidad | Crashlytics y diagnósticos online existentes. Android usa caché Firestore en memoria explícitamente. |
| Backend | `functions/` contiene `iniciarPartidaV2` y funciones de limpieza. El cliente Android utiliza la callable de inicio solamente cuando `FirebaseEmulatorConfig.usesAuthoritativeOnlineStart` está activo; release conserva inicio desde el cliente. No se verificó su despliegue. |
| Plataforma | Play Games incluye identidad, logros/progreso, amigos, avatar y guardado en la nube. También existe Firebase Messaging; no es requisito para una partida en primer plano. |
| Recursos | En `app/src/main/res`: 97 WebP, 24 PNG, 6 TTF, 23 MP3, 6 OGG, 2 WAV, 1 MPEG y 195 XML. Hay originales adicionales en `roles_gauchos`, `roles_griegos` y `roles_medievales`. |
| Entorno local | En el diagnóstico inicial no había Xcode ni herramientas de desarrollo. Ahora están instaladas Command Line Tools (Swift 6.3.3) y permiten compilar/probar Core; sigue faltando Xcode/SDK iOS. El clon se realizó con Git del runtime. No hay compilación iOS validada. |

Referencias principales: [Gradle](../../app/build.gradle), [modelos](../../app/src/main/java/com/traidores/juego/GameModels.kt), [motor](../../app/src/main/java/com/traidores/juego/GameEngine.kt), [lobby](../../app/src/main/java/com/traidores/juego/LobbyActivity.kt), [gameplay](../../app/src/main/java/com/traidores/juego/GameplayMockActivity.kt), [reglas Firestore](../../firestore.rules), [reglas RTDB](../../database.rules.json), [backend](../../functions/src/index.js).

### Documentación que requiere interpretación

El README describe una versión anterior sin Auth ni App Check. `docs/firebase-online-schema.md` ya menciona Auth, pero conserva secciones antiguas: atribuye a todos los chats claves `push()`, describe lecturas de canales por confianza y selección del mapa por votos. En este commit:

- `LobbyChatController` escribe `chat_lobby/{uid}/{0|1}`; los chats de gameplay sí usan mensajes con claves generadas.
- RTDB restringe canales mediante `miembros`, vida, rol y `enLobby`, aunque el anfitrión controla esos permisos.
- `OnlineMatchStartPolicy` toma el mapa seleccionado por el anfitrión e ignora votos de mapa antiguos.
- Los repartos admiten una actualización controlada de `pistaInvestigacion`; no son completamente inmutables.

Las fuentes para compatibilidad serán código, reglas, pruebas y capturas de una build Android identificada. La documentación sirve como contexto, no sustituye el contrato ejecutable.

`docs/arquitectura-ios-kmp.md` propone KMP y evitar duplicar el motor. Para esta solicitud se propone Swift nativo porque extraer KMP implicaría modificar Android, expresamente fuera de alcance. No se modifica ese documento. La duplicación se controla mediante fixtures y pruebas de equivalencia; KMP queda como decisión futura independiente.

## 2. Decisión de arquitectura

SwiftUI para la interfaz, Swift para dominio y adaptadores de los SDK oficiales Firebase Apple. Propuesta inicial: iPhone, vertical, iOS 17+ para Observation; evaluar iPad como alcance posterior. iOS 17 es una elección de producto, no el mínimo de Firebase. La guía vigente de Firebase pide Xcode 26.2+ y soporta iOS 15+; se fijará una versión concreta compatible del SDK y su `Package.resolved` al incorporar Firebase en la etapa 4. [Configuración oficial](https://firebase.google.com/docs/ios/setup).

Dependencia actual: únicamente el paquete Swift local `TraidoresCore`. **Firebase se difiere hasta la etapa online**; el menú y la IA local deben funcionar sin red ni plist. En esa etapa se agregarán mediante Swift Package Manager `FirebaseCore`, `FirebaseAuth`, `FirebaseFirestore`, `FirebaseDatabase`, `FirebaseAppCheck`, `FirebaseCrashlytics` y `GoogleSignIn` al implementar cuentas. Functions solo si se incorpora la ruta de emulador; Messaging y Game Center quedan para una decisión posterior.

Flujo de dependencias: vistas SwiftUI → stores de presentación → casos de uso/dominio → interfaces de repositorios. Los adaptadores Firebase implementan esas interfaces. El dominio no importa SwiftUI, UIKit ni Firebase.

- `@MainActor @Observable` para el estado que consume la UI; un coordinador serializa eventos de red, acciones y cambios de sesión.
- `async/await` y secuencias de eventos con cancelación para listeners; propietarios definidos por sesión/sala, no por cada render de una vista.
- DTO específicos de Firestore/RTDB, separados del modelo de dominio. `Codable` con claves explícitas donde corresponda y decodificación controlada de números, timestamps, campos ausentes y nodos vacíos.
- Reloj y aleatoriedad inyectables; almacenamiento local para preferencias y referencia de recuperación. Firebase Auth conserva la sesión; no se copian contraseñas ni tokens a `UserDefaults`.
- Ninguna animación determina una transición del juego. La UI confirma las presentaciones requeridas por el protocolo y aplica el estado autoritativo recibido.

### Estructura objetivo (base y menú implementados; resto futuro)

```text
ios/
├── README.md
├── docs/
│   ├── PLAN_MIGRACION_IOS.md
│   ├── ONLINE_CONTRACT.md             # reserva de contrato; se completa con online
│   └── ASSET_MANIFEST.md              # origen, hash, destino y conversión
├── .gitignore                        # exclusivo de iOS
├── Scripts/                          # comprobación de recursos/pruebas
├── FirebaseTests/
│   ├── firebase.emulators.json        # apunta a reglas raíz sin modificarlas
│   └── fixtures/
├── Packages/
│   └── TraidoresCore/
│       ├── Package.swift
│       ├── Sources/TraidoresCore/
│       │   ├── Models/
│       │   ├── Rules/
│       │   ├── OnlineContract/
│       │   └── Ports/
│       └── Tests/TraidoresCoreTests/
└── TraidoresIOS/
    ├── TraidoresIOS.xcodeproj
    ├── Configuration/
    │   ├── Debug.xcconfig
    │   ├── Release.xcconfig
    │   ├── Local.xcconfig.example
    │   └── Firebase/                  # plist local por entorno
    ├── TraidoresIOS/
    │   ├── App/                       # App, AppDelegate, composición, rutas
    │   ├── Features/
    │   │   ├── Access/
    │   │   ├── Home/
    │   │   ├── JoinRoom/
    │   │   ├── Lobby/
    │   │   ├── RoleReveal/
    │   │   ├── Match/
    │   │   ├── Results/
    │   │   └── Recovery/
    │   ├── Data/Firebase/             # Auth, Room, Roles, Actions, RTDB, perfil
    │   ├── DesignSystem/
    │   ├── Platform/                  # audio, hápticos, ciclo de vida
    │   ├── Resources/                 # Assets.xcassets, fuentes, audio
    │   ├── Info.plist
    │   ├── PrivacyInfo.xcprivacy
    │   └── TraidoresIOS.entitlements
    ├── TraidoresIOSTests/
    └── TraidoresIOSUITests/
```

Solo `ios/` recibirá cambios. No se tocarán `app/`, Gradle, recursos originales, reglas raíz, `functions/` ni archivos sincronizados del proyecto ChatGPT. El `.xcodeproj` se versionará para abrirlo directamente; no será una carpeta vacía presentada como proyecto compilable.

## 3. Tabla Android → iOS

| Android actual | iOS propuesto | Condición de equivalencia |
|---|---|---|
| `GameSession`, `GamePlayer`, `GameRole`, enums Kotlin | Structs Swift, enums con valores de protocolo, roles opcionales | Conservar campos, valores, orden y semántica; un rol desconocido no es aldeano. |
| `GameEngine`, `GameRules`, resolutores y gates | `TraidoresCore`, funciones puras y coordinador | Portar primero lo necesario para el cliente; motor de autoridad después. |
| `RoleCatalog`, `RoleCompositionBalance` | Catálogo y reglas Swift | Mantener las 11 claves, equipos, composición y exclusividad por mapa. |
| Activities, XML y adapters | Vistas SwiftUI, navegación y stores | Reproducir flujos y jerarquía visual, no clases ni coordenadas Android. |
| Firebase Auth / Credential Manager | FirebaseAuth + GoogleSignIn + AuthenticationServices | Mismo proyecto y UID, vinculación de proveedores y recuperación del perfil. |
| Firestore Android | FirebaseFirestore Apple | Mismas rutas, transacciones, índices, permisos y caché en memoria. |
| Realtime Database Android | FirebaseDatabase Apple | Mismos nodos, permisos, orden de presencia y codecs. |
| Play Integrity / proveedor Debug | App Attest, eventual fallback DeviceCheck, Debug | Registrar la app Apple antes de probar servicios con enforcement. |
| Crashlytics Gradle | FirebaseCrashlytics + subida de dSYM | Crash de prueba simbolizado, sin roles privados ni credenciales en logs. |
| SharedPreferences / serialización JVM | UserDefaults y persistencia Swift versionada | Preferencias locales; reconstruir la partida desde Firebase. |
| SoundPool / música / vibración | AVAudioPlayer/AVAudioSession y hápticos UIKit | Control de volumen, interrupciones y regreso desde segundo plano. |
| PNG/WebP, TTF, OGG/MP3, nine-patch y drawables | Asset Catalog, fuentes, audio Apple, shapes e imágenes redimensionables | Copias o derivados solo en `ios/`; conservar claves remotas como `rolImagen`. |
| Google Play Games | Fuera del primer hito; GameKit opcional después | Game Center no reemplaza Firebase ni importa automáticamente logros/guardados. |
| Firebase Messaging | Más adelante: FirebaseMessaging + APNs | No bloquear las partidas por no disponer aún de push. |

## 4. Portar modelos y reglas sin desviaciones

El primer modelo Swift representará sala, jugador, configuración, reparto privado, estado público, acción y confirmaciones. No se portarán todavía la IA conversacional, `TableMemory` ni los modos locales. Se conservarán las claves `aldeano`, `policia`, `medico`, `alcalde`, `asesino`, `espia`, `mercenario`, `desertor`, `payador`, `bufon`, `oraculo`; mapas `pampa`, `grecia`, `medieval`; equipos `Pueblo`, `Traidores`, `Neutral` y resultado `Cancelada`.

Las fases externas conservarán exactamente los valores de `GamePhase`: `REPARTO`, `NOCHE_ASESINO`, `NOCHE_MERCENARIO`, `NOCHE_POLICIA`, `NOCHE_MEDICO`, `NOCHE_ORACULO`, `AMANECER`, `DIA_DEBATE`, `CONTRAPUNTO`, `VOTACION`, `RECUENTO_VOTOS`, `DESEMPATE_VOTACION`, `ALCALDE_DESEMPATE`, `RESULTADO`. No se deducirá el recorrido online recorriendo el enum: las políticas de noche y los resolutores determinan qué sucede.

Hay dos responsabilidades distintas:

1. **Participante:** reconstruir la partida con información parcial, mostrar su rol, validar opciones visibles, emitir acciones y aplicar resultados del anfitrión. No calcular victoria usando roles que no conoce ni repartir cartas al reconectar.
2. **Anfitrión:** recuperar todos los repartos autorizados, resolver acciones/votos, aplicar AFK, temporizadores, desempates, habilidades especiales, victoria, publicación y transferencia de autoridad. Se habilita solo después de equivalencia probada.

Pruebas de reglas a trasladar desde los casos Android: protección frente a asesinato, Espía inocente ante investigación, silencio y repetición, Alcalde revelado y voto doble, empate/sin expulsión, Payador y sus dos objetivos, Oráculo y permisos del invitado, victoria especial del Bufón, elección/cambio/supervivencia del Desertor, ausencia de killers y paridad. Con roles incompletos `GameRules.winnerFor` no declara ganador; esa guarda es obligatoria.

Detalles de implementación que requieren pruebas específicas:

- Kotlin `Long` de tiempo → `Int64` en milisegundos; conservar enteros en Firestore, no segundos o strings.
- Kotlin/JVM recorre UTF-16 y tiene overflow de 32 bits. Swift `String.hashValue` no reproduce `hashCode`, `stableNoise` ni `stableVoteNoise`. Implementar las fórmulas explícitamente, con aritmética modular y casos Unicode; el desempate de asesinos usa semilla inicial 0 y el otro helper 17.
- Los nombres intervienen en mapas y desempates. Mantener nombre canónico, orden y UID por separado; no sustituir `nombre` por el rótulo visual `nombreSala`.
- Longitud, ordenación, minúsculas y truncado no siempre coinciden entre Swift y JVM. Fixtures de tildes, emoji y nombres repetidos, cotejados también contra las reglas.
- El reparto es una decisión única del anfitrión. Los participantes nunca intentan reproducirlo con una semilla propia.
- Preservar los tiempos y sus límites de `GameTimingConfig`; valores normales 4/40/120/20 segundos. Copiar pruebas de bordes, no solo el caso feliz.

## 5. Contrato online que debe conservarse

Proyecto de referencia: `traidores`. RTDB configurada: `https://traidores-default-rtdb.firebaseio.com`. La app Apple de producción debe registrarse en ese mismo proyecto y usar las mismas bases. Un proyecto de staging independiente solo permite juego cruzado con Android apuntando también a staging.

| Superficie | Rutas y conducta |
|---|---|
| Acceso a sala | Firestore `codigosSala/{codigo}` → `partidas/{roomId}`; código de seis caracteres con alfabeto permitido. No confundir código, roomId y matchId. |
| Lobby | `partidas/{roomId}/jugadores/{uid}`; alta/contador transaccionales, ready, orden, identidad y `configLobby`. Invitados pueden entrar pero no crear sala; `soloCuentas` puede impedir su alta. |
| Arranque | `partidaInicial`, `partidaInicialCreada`, `entradaLiberadaMatchId`; recibir roster y reparto antes de confirmar entrada. |
| Información privada | `repartos/{uid}`: rol propio/aliados permitidos; `pistaInvestigacion` privada actualizada por autoridad. No enumerar repartos como participante. |
| Checkpoint durable | `partidas/{roomId}/runtime/authoritative`: recuperación puntual y validación de votos; no listener permanente de todos los clientes. |
| Estado vivo | RTDB `salas/{roomId}/estado_partida`: envoltorio `matchId`, `phaseIndex`, `actualizadaPor`, `actualizadaEn`, `estadoPartida`. |
| Acciones | Firestore `partidas/{roomId}/acciones/{actionId}`. El participante escucha solo sus acciones con filtros autorizados; el anfitrión recibe las necesarias para resolver. |
| Acceso RTDB | `control` y `miembros/{uid}`. Esperar la membresía que publica el anfitrión; unirse en Firestore no da acceso inmediato a RTDB. |
| Presencia | `presencia/{uid}` con `estado` y `ts`; observar `.info/connected`, armar `onDisconnect` y después publicar conectado. Repetir en cada reconexión. |
| Confirmaciones | `sincronizacion/clientes/{uid}` y `sincronizacion/listosVotacion/{uid}`: match/fase/ronda, lectura de carta, presentación, regreso y listo. No omitir porque sean invisibles en la UI. |
| Chat de lobby | `chat_lobby/{uid}/{0|1}`, dos slots alternados, texto 1.200 ms/emote 4.000 ms de cooldown; lector compatible con mensajes antiguos. |
| Chat de partida | `chat`, `chat_traidores`, `chat_espectadores` con claves de mensaje, `ts`, `matchId`, fase, ronda, tipo y autor. Suscripciones según permisos; notices del plan tienen tratamiento de anfitrión específico. |
| Perfil y moderación | `perfiles_publicos/{uid}`, `meta/public_ids`, `bans`, baneos por sala, `reportes`; no inventar IDs públicos o alias invitados. |

Puntos obligatorios del codec y la secuencia:

- `versionEstado == 2` es el esquema actual que acepta `OnlineMatchSessionBuilder`; no confundirlo con `protocoloVoto` ni con el nombre de la callable `V2`. Valores desconocidos deben mostrar incompatibilidad, sin adivinar una fase.
- El estado incluye `authorityEpoch` y `stateSequence`. Portar por separado `OnlineStateOrder.isNewer` e `isSameOrNewer`: tienen criterios diferentes, y contienen fallback legacy. No reemplazarlos por ordenar timestamps del teléfono.
- El anfitrión confirma un checkpoint Firestore en transacción, verifica `hostActivoId` e incrementa la secuencia; solo entonces publica RTDB. Hay fallback legacy. No existe una transacción atómica entre ambas bases: reintento y reconciliación son parte del protocolo.
- Un voto con protocolo 2 debe usar literalmente `${matchId}_${uid}_r${ronda}_p${phaseIndex}_votar_s1`. Mantiene un documento por jugador/fase; cambiar voto actualiza objetivo y contador atómico, hasta 24 cambios. La regla verifica fase abierta, vida, silencio, objetivo y deadline con 1.500 ms de gracia.
- Otras acciones y voto legacy usan `UUID.nameUUIDFromBytes` sobre UTF-8 de `matchId|uid|ronda|phaseIndex|grupo|slot`: MD5, bits de UUID versión 3 y variante RFC, sin agregar namespace. `invitar_muerto` y `guardar_poder` comparten `accion_oraculo`. Un UUID aleatorio o un UUID v5 no es compatible.
- El payload conserva `tipo`, `actorId`, `actorNombre`, `actorEsHost`, `objetivoNombre`, `fase`, `ronda`, `phaseIndex`, `detalles`, `creadaEn`, `creadaEnLocal`. `detalles` lleva orden de actor/objetivo. Las acciones nocturnas usan `matar`, `silenciar`, `investigar`, `salvar`, `invitar_muerto`, `guardar_poder`. Las reglas permiten identificar `modoCliente = ios`; no hace falta fingir Android.
- `configLobby.roles` es un CSV con el orden de `LocalGameFactory.editableRoleKeys()` y `presetRoles`; `partidaInicial.config.roles` es un mapa. No serializar ambos como el mismo tipo.
- Firestore guarda `votos` como mapa; RTDB lo convierte a lista de objetos `{jugador, objetivo}` ordenada por jugador porque los nombres pueden contener caracteres no válidos en claves RTDB. Conservar este codec.
- Firestore usa timestamps del servidor; RTDB usa `ServerValue.timestamp()`. Campos vacíos/listas vacías pueden desaparecer en RTDB y `null` puede borrar un nodo: probar el tratamiento de ausencia y no enviar `NSNull` indiscriminadamente.
- El reloj de presentación se ancla a `.info/serverTimeOffset` y a un reloj monotónico; al volver a primer plano se resincroniza. No avanzar el juego por el timer SwiftUI.
- Evitar confirmaciones optimistas definitivas: distinguir escritura pendiente, aceptada y rechazada. No reproducir automáticamente una acción caducada después de reconectar.
- Cancelar listeners al cambiar cuenta, sala o match; filtrar `matchId` también en reintentos para no mezclar la revancha con la partida anterior.

Referencias específicas: [identidad de acciones](../../app/src/main/java/com/traidores/juego/OnlineActionIdentity.kt), [orden de estado](../../app/src/main/java/com/traidores/juego/OnlineStateInbox.kt), [transporte](../../app/src/main/java/com/traidores/juego/OnlineAuthoritativeStateTransport.kt), [configuración de lobby](../../app/src/main/java/com/traidores/juego/OnlineLobbyConfig.kt), [contrato de inicio](../../app/src/main/java/com/traidores/juego/OnlineMatchStartContract.kt).

## 6. Firebase y credenciales

| Servicio | Implementación y comprobación |
|---|---|
| Auth | Invitado con `signInAnonymously`; Google/correo y luego Apple. Vincular credenciales al usuario actual cuando corresponda, refrescar token y recuperar `publicId`. Si la credencial ya pertenece a otra cuenta, autenticar esa cuenta y cargar su perfil; no fusionar por email automáticamente. |
| Firestore | Mismos documentos, filtros e índices; configurar caché en memoria antes de usar la instancia. En Apple la persistencia offline viene activada por defecto: no aceptar un snapshot en caché como permiso para arrancar una partida. [Offline Firestore](https://firebase.google.com/docs/firestore/manage-data/enable-offline). |
| RTDB | Mismas referencias y URL. Manejar membresía, desconexión, listeners, errores y escritura de tiempo de servidor; decidir explícitamente no persistir comandos de partida entre lanzamientos. [Presencia y offline en Apple](https://firebase.google.com/docs/database/ios/offline-capabilities). |
| Crashlytics | Inicialización Apple, build phase de dSYM e inputs correspondientes; validar un crash de prueba fuera del debugger, reabrir y comprobar simbolicación. [Guía oficial](https://firebase.google.com/docs/crashlytics/ios/get-started). |
| App Check | Registrar app Apple; factory antes de `FirebaseApp.configure()`. App Attest en dispositivo, Debug solo para desarrollo/simulador y eventual DeviceCheck en dispositivos no compatibles. Firebase requiere entorno App Attest `production`. No activar/desactivar enforcement global durante este trabajo. [App Attest](https://firebase.google.com/docs/app-check/ios/app-attest-provider). |
| Reglas | Son del backend común, no existe una traducción a Swift ni reglas «para iOS». Reutilizarlas y probar payloads Apple. Comparar exportación desplegada con el repositorio antes de probar salas reales; no desplegar cambios para sortear errores de permisos. |

### Qué preparar en Firebase Console

1. **Agregar una aplicación Apple al proyecto existente `traidores`.** Propuesta de Bundle ID: `com.traidores.juego.ios`, pendiente de disponibilidad y elección definitiva. Debe coincidir exactamente con Apple Developer y Xcode. No crear un proyecto Firebase nuevo para las salas compartidas.
2. **Descargar `GoogleService-Info.plist` de esa app Apple.** El `google-services.json` Android ya está en el clon, pero no sirve como configuración de iOS. El plist irá localmente en `ios/TraidoresIOS/Configuration/Firebase/` y la build copiará el archivo del entorno elegido al bundle. Contiene identificadores de cliente, no una clave administrativa; aun así proponemos ignorar las copias por entorno para evitar conexiones accidentales. [Registro y archivo](https://firebase.google.com/docs/ios/setup).
3. **Auth → proveedores:** comprobar Anónimo, Correo/Contraseña y Google ya usados; habilitar Apple cuando se implemente. No se exportan usuarios ni se descarga una base de contraseñas.
4. **Google:** obtener/verificar el OAuth client ID iOS vinculado al Bundle ID y el `REVERSED_CLIENT_ID`; agregar su URL scheme y gestionar el callback SwiftUI. Re-descargar el plist si cambia la configuración. No reutilizar un client ID Android ni su SHA como configuración iOS. [Google Sign-In y Firebase](https://firebase.google.com/docs/auth/ios/google-signin).
5. **App Check:** registrar el proveedor Apple, Team ID y tokens Debug de las instalaciones de desarrollo/CI. El token Debug es confidencial: no versionarlo. Si se elige DeviceCheck, configurar su clave Apple y Key ID donde la consola lo solicite. Verificar qué servicios tienen enforcement activo y que Android siga registrado.
6. **Firestore/RTDB:** conservar una copia de las reglas e índices efectivamente desplegados, confirmar database ID/ubicación y URL RTDB. Las reglas existentes y `firestore.indexes.json` ya están en el repo; no hay otra credencial de base de datos que deba incorporarse a la app.
7. **Crashlytics:** habilitar/comprobar la app Apple en la consola. No requiere descargar una clave privada adicional; los dSYM los produce Xcode.
8. **Verificar Functions y plan:** confirmar si hay funciones desplegadas y qué versión usa el APK de referencia. No habilitar Blaze ni desplegar backend para esta migración inicial.

### Qué se crea fuera de Firebase

- Apple Developer: App ID/Bundle ID, Team ID, firma y perfiles de aprovisionamiento; cuenta adecuada para dispositivo, capacidades y TestFlight. Instalar Xcode y simuladores en esta Mac.
- Para Apple Login: habilitar Sign in with Apple, crear/configurar Service ID y return URL de Firebase según el flujo, clave `.p8` y Key ID, y registrar esos datos en el proveedor Apple de Firebase. Configurar relay de correo si se usan correos de Firebase. La `.p8` no entra en la app ni en Git. [Configuración oficial de Apple en Firebase](https://firebase.google.com/docs/auth/ios/apple).
- APNs `.p8`, Push Notifications y su alta en Firebase se necesitan solo cuando se agreguen notificaciones. Game Center se configura en Apple/App Store Connect cuando se decida incorporarlo.
- No hace falta descargar una service account, usar Firebase Admin SDK en iOS ni entregar contraseñas administrativas. Ninguna clave privada del servidor debe empaquetarse con la app.

## 7. Google, Apple y Game Center

Si Google autentica la cuenta principal en iOS, la regla 4.8 pide una opción equivalente con sus garantías de privacidad, salvo excepciones específicas. La solución recomendada para Traidores es **Sign in with Apple junto a Google**. Ofrecer solo un invitado o email/contraseña adicional no equivale automáticamente a cumplir 4.8. El requisito depende del acceso ofrecido en iOS; que Android tenga Google por sí solo no obliga a mostrarlo en una build interna Apple. [Regla 4.8](https://developer.apple.com/app-store/review/guidelines/#login-services).

La primera prueba interna puede usar Firebase anónimo y un anfitrión Android registrado. Antes de distribución pública se implementan Apple/Google con vinculación segura, reautenticación, nonce SHA-256 para Apple, nombre entregado en la primera autorización y manejo de correo privado. Apple y Google no garantizan el mismo UID por tener correos relacionados: la continuidad depende del proveedor ya vinculado o de una vinculación explícita.

Si la app permite crear cuentas, debe permitir iniciar su eliminación desde la app. Hay que eliminar datos asociados y revocar los tokens de Apple cuando aplique. El borrado Android actual elimina perfil, respaldo Play Games y Auth, pero no demuestra limpieza completa de todas las referencias en salas, chats y reportes. Hace falta diseñar esa política y resolver cuentas con respaldo Play Games que iOS no puede borrar mediante su SDK. Es un requisito de lanzamiento, con posible trabajo de backend separado. [Eliminación de cuentas](https://developer.apple.com/support/offering-account-deletion-in-your-app/).

El chat también exige tratar contenido de usuarios: reportar, bloquear/silenciar, moderación y contacto de soporte; revisar las restricciones de Apple sobre experiencias centradas en chat aleatorio/anónimo. El lobby con invitación y gameplay no debe presentarse como un chat anónimo independiente. Preparar política de privacidad, declaraciones App Privacy, manifiestos y motivos de APIs que correspondan al binario real. No añadir ATT por la sola existencia de Firebase: se evalúa si hay tracking efectivo. [Guías de revisión](https://developer.apple.com/app-store/review/guidelines/).

**Game Center queda fuera de la primera versión funcional online.** Después puede aportar logros y clasificaciones con GameKit. Las salas seguirán en Firebase: cambiar el multijugador a Game Center rompería el objetivo común. Game Center no importa los logros, amigos ni `traidores_profile_v1` de Play Games. Un usuario autenticado únicamente mediante Play Games necesitará vincular antes un proveedor accesible desde iOS para reutilizar su cuenta; Google Sign-In y Play Games son proveedores distintos. El progreso que solo existe en el snapshot Play Games requiere una migración adicional, no se deduce de `perfiles_publicos`. [Capacidades de Game Center](https://developer.apple.com/game-center/).

## 8. Interfaz y recursos

Mantener la identidad: fondos por mapa, cartas de roles, colores, marcos, sonidos y fuentes. Crear tokens de diseño y componentes SwiftUI para panel, botón, carta, avatar, indicador de fase, contador y chat. La pantalla de partida tendrá cabecera, mesa adaptable, panel de acción propio y acceso al chat; overlays para carta y revelaciones. `NavigationStack`, sheets y presentaciones de pantalla completa reemplazan navegación entre Activities.

Usar tamaño disponible, safe areas, teclado y texto dinámico; evitar copiar anchos fijos XML de 344 dp o reducir texto para que entre. Probar iPhone pequeño y grande, VoiceOver, reducir movimiento, contraste y apertura del teclado. En background iOS puede suspender la app: marcar/recuperar presencia y resincronizar al volver, sin prometer timers continuos. El relevo debe funcionar sin servicios permanentes.

Se copiarán únicamente los recursos necesarios para cada etapa. Para el Asset Catalog, generar PNG/PDF apropiados desde los originales cuando corresponda y validar transparencia/calidad; mantener una tabla que traduzca `rolImagen` a assets. WebP se tratará como formato fuente, no se asumirá importación directa correcta en todos los caminos de Xcode. Convertir OGG a un formato soportado por el reproductor elegido, preservar MP3/WAV donde sea apropiado y recrear nine-patch/XML con insets/shapes SwiftUI. No se renombra ni borra ningún recurso Android. Mantener manifiesto con origen y hash, y confirmar derechos de fuentes/audio al preparar la distribución.

## 9. Riesgos de compatibilidad

| Riesgo | Impacto | Mitigación / criterio de salida |
|---|---|---|
| Documentación, APK y reglas desplegadas diferentes | App conectada que recibe denegaciones o interpreta mal el juego | Fijar commit/APK/ruleset de referencia; verificar Console y fixtures antes de salas reales. |
| iOS sin motor elegido como host | Partida congelada o resultado incorrecto | Primera demo controlada con host Android conectado. El protocolo actual no anuncia capacidad de host: ocultar «crear sala» no garantiza evitar relevo. No publicar compatibilidad general hasta etapa 4. |
| Autoridad en cliente | Un anfitrión manipulado puede fabricar estados y permisos | Mantener modelo actual para interoperar; no presentar App Check como antitrampas. Servidor autoritativo es un proyecto posterior. |
| Dos bases, permisos asincrónicos | Denegaciones al entrar, estado viejo o fases no confirmadas | Esperar membresía, confirmar checkpoint, serializar publicaciones y probar fallos parciales. |
| ID de acción o tipos distintos | Duplicados, votos rechazados o acciones no resueltas | Vectores Android/Swift, UUID v3 exacto, ID voto v2, enteros/timestamps y CSV de roles. |
| Rol desconocido tratado como inocente | Filtración, botones incorrectos o victoria falsa | Modelo parcial explícito; reparto y pistas privados; no calcular ganador en participante. |
| Hash, Unicode, orden y RNG | Desempate diferente entre plataformas | Fixtures UTF-16, overflow, nombres duplicados y resultados deterministas. |
| Background, red y caché | Acciones fuera de fase, host ausente, doble listener | Reloj monotónico, caché explícita, restauración, cancelación y reconciliación. |
| Auth y progreso Play Games | Nuevo UID o progreso perdido al cambiar de dispositivo | Vincular proveedores, recuperar perfil por UID y separar migración de guardado. |
| Enforcement App Check | iOS bloqueado aunque Auth funcione | Alta Apple previa, Debug autorizado, prueba en dispositivo con App Attest y métricas. |
| Eliminación de cuenta/moderación incompletas | Bloqueo de lanzamiento | Flujo verificable con datos y proveedores reales; resolver limpieza antes de App Store. |

## 10. Etapas vigentes: menú → IA local → online

Orden cambiado por el usuario tras aprobar la base. No se implementa gameplay completo en la etapa 1.

| Etapa | Entregable | Pruebas y aceptación |
|---|---|---|
| 0 — diagnóstico terminado | Clon y análisis de Android/Firebase | Solo cambios bajo `ios/`; referencia Android fijada. |
| 1 — base y menú, aprobada | Proyecto Xcode, paquete local Swift, fondo/logo/fuente/música originales, navegación, guía de roles, ayuda y opciones. Perfil y modos todavía pendientes, sin partidas simuladas presentadas como reales. | Catálogo comparado con fixtures extraídos de Kotlin; sintaxis, plists, recursos y referencias comprobados. Falta compilar iOS y revisar en simulador/iPhone 13 cuando se instale Xcode. |
| 2 — partida local clásica | Lobby local de 5 jugadores en Pampa, reparto, 1 humano + 4 bots, noche, debate, votos, empate, eliminación, resultado y reinicio. Motor puro sin Firebase; bots con decisiones locales. | Equivalencia con casos Kotlin de protección, investigación, asesinato, votos y victoria; RNG y reloj inyectables. Partida completa offline y recuperación al background. No fingir IA conversacional completa en este paso. |
| 3 — IA y reglas ampliadas | Portar gradualmente comportamiento conversacional local, memoria y dificultad desde `LocalBotAi` y colaboradores; roles especiales, 3 mapas, configuraciones y tamaños admitidos. | Simulaciones repetibles, bots sin conocimiento privado indebido, tests de roles especiales/AFK/empates, rendimiento y memoria en iPhone 13. IA del repo es local: no necesita una API de IA ni un servicio pago. |
| 4 — online Firebase | Registro Apple, Auth/App Check/Crashlytics, lobby mixto y contratos; primera partida con host Android, después host Swift y relevo. | Checklist de protocolo de este documento, emuladores, reglas y dispositivos mixtos. No dar por terminada la compatibilidad mientras iOS no pueda asumir autoridad. |
| 5 — distribución | Google/Apple y vinculación, perfil remoto, eliminación, privacidad/moderación, accesibilidad y TestFlight | Mismo UID y perfil donde estén vinculados, reautenticación, eliminación/revocación, App Attest y crash simbolizado. Game Center y notificaciones por separado. |

La etapa 4 conserva el ensayo inicial de 5 jugadores/composición clásica/host Android antes de abrir compatibilidad a cualquier sala; cuando llegue ese momento ya existirá el motor local Swift. El motor debe seguir separado de la UI para reutilizarlo como autoridad online sin reescribirlo.

La primera revisión en dispositivo de la etapa 1 incluye: arranque, safe areas, botones y regreso, texto grande/VoiceOver, música, silencio físico, cambio de preferencia, bloqueo/reapertura y modo avión. No se necesita Firebase para ninguno de esos pasos. El ícono final, la animación de introducción y el perfil completo se dejan para pulido posterior.

### Estrategia de pruebas

1. **Equivalencia sin red:** guardar fixtures anonimizados de Kotlin bajo `ios/FirebaseTests/fixtures`, con commit y escenario. Comparar DTO, decisiones de reglas, IDs, codec, orden, límites y campos emitidos. Reutilizar los casos de `GameEngineTest`, `OnlineActionIdentityTest`, `OnlineMatchSessionBuilderTest`, `OnlineAuthoritativeStateTransportTest`, `OnlineStateInboxTest`, `RoleCatalogTest` y gates de arranque/voto/recuperación. No generar esperados con el mismo algoritmo Swift que se prueba.
2. **Permisos y fallos con Emulator Suite:** configuración exclusiva de `ios/`, referenciando reglas raíz en lectura. Auth emulator adicional para iOS; Firestore 8081, RTDB 9000, Functions 5001 según repo. Los tests cliente deben pasar por reglas; Admin solo para preparar fixtures. Casos negativos: rol ajeno, canales indebidos, UID falso, voto tardío, acción de match anterior, edición de orden, escritura sin membresía y sala en limpieza.
3. **Cuidado con Android emulator:** `FirebaseEmulatorConfig` actual redirige Firestore/RTDB/Functions pero no Auth. No asumir aislamiento total ni que iOS con tokens del Auth emulator interoperará automáticamente con esa configuración. La primera matriz mixta puede usar un proyecto de prueba con ambas apps bien configuradas, o una sala privada controlada del proyecto común tras revisar credenciales/reglas; documentar qué Auth se usa. En simulador iOS usar host local y en iPhone IP LAN; no copiar `10.0.2.2` de Android.
4. **Dispositivos mixtos:** cuenta y UID distintos por jugador; forzar cierre, modo avión, reconexión, bloqueo de pantalla, cambio de reloj, pérdida de host y revancha. En cada paso comparar `matchId`, fase, `phaseIndex`, secuencia, vivos, muteados y resultado, sin registrar roles secretos innecesariamente.
5. **Verificación de recursos/UI y release:** resolución, memoria, audio, teclado, accesibilidad, firma y pruebas reales de App Check/Crashlytics; no validar attestation únicamente en emuladores.

Las suites existentes de reglas y backend sirven de referencia para la etapa online. No se ejecutan en esta etapa de menú sin backend. Algunos scripts npm invocan rutas Windows; los nuevos comandos de prueba macOS estarán bajo `ios/`, sin reescribir los scripts Android.

## 11. Estado y siguiente paso

La etapa 1 está aprobada. Se crearon el proyecto Xcode y el menú sin incorporar gameplay o dependencias Firebase. No se alteraron Android, reglas ni backend. El README iOS registra qué verificaciones se pudieron ejecutar y cuáles necesitan Xcode.

Mientras se actualiza macOS, se puede compilar/probar el paquete puro con Command Line Tools, que ahora están instaladas. Para la app completa se necesita Xcode con SDK iOS. El iPhone 13 será el dispositivo de prueba: **compila la Mac; el iPhone ejecuta la app**. El proyecto apunta a iOS 17 o posterior.

Firebase se prepara al comenzar la etapa 4. No es necesario descargar credenciales ni habilitar servicios ahora. Primero validar el menú en dispositivo y continuar con una partida local clásica. La guía [PRIMERA_PRUEBA_IPHONE.md](PRIMERA_PRUEBA_IPHONE.md) contiene los pasos de firma y ejecución.
