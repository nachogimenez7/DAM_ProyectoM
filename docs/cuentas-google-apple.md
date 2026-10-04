# Cuenta con Google, Apple y correo — 4/10/2026

## Estado

Android ya tiene acceso con Google mediante Credential Manager. iOS ahora ofrece Google, Apple y correo en la misma ventana de cuenta, desde Perfil (accesible por el retrato del menú) y desde Online. Google usa GoogleSignIn-iOS 10.0.0 y el cliente OAuth de `GoogleService-Info.plist`; el retorno usa su `REVERSED_CLIENT_ID`. Firebase permanece en 12.19.2, ahora también con Firestore.

Los botones usan fondo oscuro y un marco exterior dorado suave para combinar con el juego. Apple conserva su control oficial negro. Google usa el fondo `#131314`, borde `#8E918F`, texto `#E3E3E3`, Google Sans Medium y la G original de las [guías oficiales actuales](https://developers.google.com/identity/branding-guidelines); el preset oscuro de GoogleSignInSwift 10.0.0 todavía es azul. La fuente incluye su licencia OFL y se distribuye en el bundle. La ventana es compartida por ambos accesos. Captura: `ios/docs/evidence/codex-cuenta-google-apple.png`.

`OnlineBootstrap.services()` entrega una cuenta y un perfil reales en Debug normal y Release. Los escenarios falsos solo se seleccionan explícitamente con `-ui-testing-online`, dentro de Debug. Los tests de interfaz sin escenario ni emulador no inicializan Firebase. No se necesitan Blaze ni Storage para este bloque.

Las salas y el gameplay online iOS todavía no están conectados. El hub permite gestionar la cuenta, pero deshabilita buscar/unirse/crear y explica que las partidas siguen en preparación (`roomsAvailable = false`). No devuelve salas inventadas. Al conectar estos adaptadores, cada jugador iOS debe escribir `puedeArbitrar = false`; tampoco debe asumir `hostActivoId` para ejecutar ClassicGame como árbitro de Android.

## Identidad y perfil reales

- Los tres proveedores vinculan la identidad del invitado y conservan su Firebase UID.
- Si la credencial ya pertenece a otra cuenta, un invitado entra en esa cuenta y recupera su perfil. Una cuenta registrada no se reemplaza automáticamente por otra: la colisión se rechaza.
- Apple usa nonce SHA-256 y, al recuperar una cuenta, la credencial actualizada del SDK para respetar el uso único del nonce. La condición de cuenta registrada depende de `isAnonymous`, incluyendo correos privados o ausentes.
- Se actualiza el token y se consulta `bans/{uid}` en el servidor antes de permitir el acceso.
- `perfiles_publicos/{uid}` es la fuente del nombre, personalización, fotos publicadas y número. Las cuentas nuevas reservan su ID mediante una transacción sobre `meta/public_ids`, igual que Android. Si otro dispositivo ya lo asignó, se conserva. No se genera un número local al fallar el servidor.
- Los cambios del perfil vinculado se envían a Firestore al terminar de editar. El caché del menú se actualiza después de la confirmación y se limpia al cambiar de cuenta. Guardar texto/personalización conserva la foto remota y los demás campos.
- La recuperación requiere una lectura del servidor; el caché por sí solo no anuncia una cuenta lista. El error permite reintentar.

La subida y retirada de fotos en iOS siguen pendientes de Storage. Las fotos ya publicadas se recuperan; las cuentas vinculadas no presentan una selección local como una subida completada. El historial por cuenta será el próximo bloque solicitado, después de terminar las integraciones online; este cambio no lo implementa ni afirma que esté sincronizado.

## Apple: configuración externa pendiente

Firebase ya tiene habilitado el proveedor Apple, según el handoff. La firma usa actualmente un equipo gratuito. Por eso el botón Apple está visible pero deshabilitado y explica que aún no está habilitado; no simula un acceso.

Para usarlo en iOS se necesita un equipo del Apple Developer Program con Sign in with Apple habilitado para el identificador de la app. En el archivo ignorado `Configuration/Local.xcconfig` deben configurarse el equipo, `CODE_SIGN_ENTITLEMENTS = TraidoresIOS/TraidoresIOS-Online.entitlements` y `TRAIDORES_APPLE_SIGN_IN_ENABLED = YES`. No habilitar la bandera con el equipo gratuito. Después se prueba la autorización nativa y su retorno a Firebase con una cuenta Apple real.

El acceso Apple web/Android requiere además Service ID y la configuración OAuth (Team ID, Key ID y clave privada) en Firebase; habilitar solamente el proveedor nativo iOS no completa esa configuración. No se añadió un flujo Apple en Android en este bloque.

Referencias oficiales: [Google en iOS](https://firebase.google.com/docs/auth/ios/google-signin), [Apple en iOS](https://firebase.google.com/docs/auth/ios/apple), [Apple en Android y configuración web](https://firebase.google.com/docs/auth/android/apple).

## Verificación

Compilación Debug firmada para simulador aprobada. El chequeo nativo usa el SDK Firebase real y Auth/Firestore emulados: vinculación y recuperación Google, vinculación y recuperación por correo, Apple con correo privado, rechazo de sustitución de una cuenta registrada, persistencia de perfil/ID y rechazo del caché con Firestore desconectado. Esto valida el adaptador, no la selección de una cuenta real en la interfaz de Google ni la autorización nativa de Apple.

Para repetirlo, levantar los emuladores Auth (9099) y Firestore (8081), instalar la app Debug firmada y lanzarla con `-firebase-emulator-host 127.0.0.1 -firebase-account-smoke YES`. Debe registrar `FIREBASE SMOKE: ACCOUNT PASS`. El modo emulado usa un proveedor App Check local para no intercambiar tokens con el proyecto real. El código del chequeo solo existe en Debug y exige un host de emuladores.

Los contratos se verifican con `sh ios/Scripts/test_online_contracts.sh`. `OnlineFlowUITests` incluye una prueba de que Perfil y Online ofrecen los mismos botones Google y Apple; sus escenarios son solo pruebas de presentación. La validación de la cuenta contra los emuladores usa el adaptador real y se ejecuta por separado.

La validación de cuentas aprobó 6/6 pruebas: cuenta y perfil reales contra emuladores, botones desde Perfil/Online, vinculación en la presentación y tres regresiones del perfil local (nombre/avatar/banner/rol, estilos/emotes y nombre vacío). La prueba real registra por correo desde la pantalla, guarda el nombre en Firestore, reinicia la app y comprueba el mismo perfil y número. Se habilita para el runner con `TEST_RUNNER_TRAIDORES_AUTH_EMULATOR_TEST=1`; el primer arranque usa `-firebase-ui-reset-auth YES` exclusivamente contra los emuladores, y el reinicio conserva la sesión para comprobar la recuperación. La corrección del botón Apple incluye su estado deshabilitado en VoiceOver y pasó la auditoría de accesibilidad.

Debug y Release para simulador compilan. La selección de Google real y la autorización nativa de Apple siguen siendo verificaciones externas: no deben confundirse con los tokens de prueba locales. Para recuperar una cuenta se utiliza su proveedor; no se unen perfiles distintos automáticamente por nombre o correo.

El cambio de colores se verificó de nuevo en el simulador: botones compartidos desde Perfil/Online con auditoría de accesibilidad aprobada, y cuenta/perfil reales contra Auth/Firestore emulados aprobados. La prueba espera y descarta el aviso de Autofill de iOS antes de seguir con la edición.
