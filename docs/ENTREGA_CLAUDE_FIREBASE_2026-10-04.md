# Entrega para revisión de Claude — 4 de octubre de 2026

## Pedido actual y estado

El usuario pide que Claude analice lo implementado, cómo quedó conectado y qué falta. Este documento entrega contexto para esa revisión; no asigna un nuevo bloque de programación a Claude.

Codex implementó y probó la conexión de historial por cuenta y publicación de fotos antes de activar Blaze. El usuario está preparando la activación desde Firebase. La última comprobación remota confirmó Spark; no asumir que ese dato sigue vigente durante la lectura. Consultar nuevamente antes del despliegue.

**Código implementado y pruebas locales aprobadas no equivalen a despliegue en producción.** Esta entrega sube Git, no publica reglas/funciones, no crea el bucket y no cambia la facturación. Las fotos remotas siguen deshabilitadas por defecto en las compilaciones.

### Commits de implementación

| Rama | Commit | Contenido |
| --- | --- | --- |
| `main` | `f850ea5` | Android, backend, scripts y documentación compartida. |
| `ios-port` | `81bd418` | El mismo cambio compartido, antes de trasladarlo a `main`. |
| `ios-port` | `7a9e5fe` | Servicios de historial/fotos iOS, integración UI, registro Xcode, pruebas nativas y actualización de `ios/CLAUDE.md`. |

La documentación de esta entrega va en un commit posterior en ambas ramas. No fusionar todo `ios-port` sobre `main` para trasladar Android. Los cambios de sonido del cierre anterior ya estaban publicados: `feaabd7` en `main` y `a4dc3f5` en `ios-port`; esta entrega los conserva.

## Qué funciona y qué sigue pendiente

| Bloque | Android | iOS | Aceptación remota |
| --- | --- | --- | --- |
| Cuenta y perfil público | Servicios Firebase existentes, perfil confirmado por UID. | Servicios reales de Auth/Firestore, recuperación de cuenta y perfil. | Revalidar con dispositivos y configuración remota actual. |
| Historial de cuenta | Lector Firestore, cola de resultados locales y cierre online en el backend. | Lector Firestore, estadísticas, última partida, historial y cola de resultados locales. | Desplegar funciones/reglas y confirmar resultados en el proyecto real. |
| Fotos publicadas | Cola persistida por UID, reemplazo, reintento y eliminación. | Publicador real Storage/Firestore con el mismo contrato. | Bucket, reglas, flags de compilación y prueba entre dispositivos. |
| Fotos durante el juego | Referencia conservada en sala/roster, mesa, votos y ganadores. | Foto propia en partida local y componentes de presentación preparados. | Android requiere prueba completa real; iOS multiplayer todavía no está conectado. |
| Salas y gameplay online | Motor y autoridad Android existentes. | `UnavailableIOSRooms`, `roomsAvailable: false`; no hay adaptador real de salas/gameplay. | No habilitar botones ni anunciar Android ↔ iOS completo por tener Auth/Storage. |
| Acceso con Apple | No corresponde al acceso nativo iOS. | Código existente; flag de capacidad permanece desactivado. | Equipo/capacidades de Apple y prueba real pendientes. |

Los servicios falsos iOS están limitados a Debug y a argumentos explícitos de pruebas. El bootstrap normal usa los servicios reales de cuenta, perfil e historial. No se agregó historial demostrativo a una ejecución normal.

## Contratos que deben conservarse

### Identidad y perfil

- Firebase Auth UID identifica al dueño. `publicId` es su número visible, no la clave de propiedad. Nombre y correo tampoco identifican archivos o registros.
- Perfil público: `perfiles_publicos/{uid}`, con `uidTemporal`, `publicId`, `nombrePerfil`, `nombreSala`, `bioPerfil`, `avatarPerfil`, `bannerPerfil`, `rolFavoritoPerfil`, emotes/tema y `fotoPerfil` opcional.
- `fotoPlayGames` sigue como compatibilidad de lectura. No reintroducir la opción de elegir la foto de Play Games. Al publicar/quitar una foto de galería se vacía ese respaldo.
- Perfil, menú y tarjeta online usan el perfil confirmado. El banner y los avatares ilustrados son recursos de la app; no se descargan de Storage.
- Las ediciones de nombre, descripción y cosméticos omiten los campos de foto y las estadísticas del servidor. Evitar volver a enviar un borrador sin cambios.

### Historial privado

- Resumen: `cuentas/{uid}`: `schemaVersion: 1`, `partidas`, `victorias`, `ultimaPartidaEn`, `actualizadaEn`.
- Registros: `cuentas/{uid}/historial/{recordId}`. Leer por `finalizadaEn` descendente, máximo 50. Los contadores abarcan todo el historial, no solo esa ventana.
- `recordId`: prefijo `online_` o `local_` más SHA-256 hexadecimal UTF-8 del `matchKey`.
- Online: `online:{matchId}`. Android local conserva su clave existente. iOS local usa `local:ios:{UUID}`, fijada y guardada al comenzar.
- El cliente puede crear un resultado **local** validado de su UID; nunca escribir resultados online ni contadores. El backend marca `contabilizada` y actualiza el resumen privado y `estadisticasPerfil` público en una transacción.
- La partida local fija su UID al comenzar. Si cambia la cuenta antes del final, el resultado espera a la cuenta inicial. Un guardado recuperado usa el mismo ID y no se cuenta dos veces.
- No importar preferencias/contadores históricos sin dueño comprobable. Las estadísticas de cuenta proceden de snapshots confirmados por Firestore.

### Fotos

- Objeto: `profilePhotos/{uid}/avatar_{sha256}.jpg`. JPEG de hasta 512 px por lado, máximo 256 KiB; iOS genera 512 × 512. Recodificación sin metadatos del original.
- Subir los bytes, obtener la URL y confirmar `fotoPerfil` en Firestore antes de borrar la edición pendiente.
- Los bytes pendientes se persisten por UID y revisión, no como una ruta mutable al último archivo elegido. Un ACK antiguo no borra una selección nueva.
- Cambiar de cuenta invalida la presentación/operación anterior y conserva su cola para un reintento bajo el UID correcto.
- Reemplazar conserva versiones anteriores: una partida activa puede seguir usando la URL de su roster. Quitar la foto borra todas las versiones del UID después de vaciar la referencia pública.
- Borrar Auth ejecuta la limpieza de perfil, historial privado y todas las versiones de fotos desde el backend.
- Las URLs de descarga incluyen un token compartible; son fotos públicas de perfil. Las reglas de lectura autenticada mediante SDK no convierten una URL compartida en privada.
- Sin SafeSearch ni revisión manual, según la decisión del usuario para esta beta. La validación de tamaño/tipo no es moderación de contenido.

## Archivos nuevos y cambios principales

Las rutas de esta sección son relativas a la raíz del repositorio.

### Android y backend

- `app/src/main/java/com/traidores/juego/ProfilePhotoStorage.kt`: publicación serializada, cola de JPEG por UID/revisión, ACK condicionado, conservación de versiones y eliminación completa al quitar.
- `AccountProfileSync.kt`: los payloads de cosméticos ya no pisan `fotoPerfil`; también preservan el respaldo de foto mientras Storage está habilitado.
- `AccountDeletion.kt`: elimina la cola de fotos al completar la eliminación de cuenta.
- `app/src/debug/java/com/traidores/juego/ProfileMediaSmokeActivity.kt` y manifest Debug: driver con SDK reales, bloqueado si no están habilitados ambos flags de emuladores/Storage. No se incluye en Release.
- `functions/src/index.js`: `borrarHistorialCuentaV1` ahora elimina también el prefijo de fotos del UID. En emuladores, si falta el endpoint de Storage, no intenta acceder al bucket remoto.
- `functions/test/accountHistory.integration.test.js`: prueba de eliminación de versiones de una cuenta conservando las de otra.
- `scripts/test-storage-rules.cjs`: transporte, recuperación, reemplazo conservando versiones del roster, publicación rechazada y eliminación.
- `scripts/seed-media-emulators.cjs`: crea dos cuentas ficticias y resultados mediante el backend. Exige endpoints locales exactos antes de inicializar Admin; guarda las credenciales ficticias en un archivo con permisos 600.

### iOS

Salvo las rutas de configuración, proyecto y pruebas, las rutas siguientes parten de `ios/TraidoresIOS/TraidoresIOS/`.

- `Platform/Online/FirebaseAccountHistory.swift` **nuevo**: `AccountHistoryService` real, dos escuchas por UID, solo snapshots confirmados, timeout/reintento, cancelación por Auth y cola duradera de resultados locales.
- `Platform/Online/FirebaseProfilePhotos.swift` **nuevo**: codec ImageIO/UIKit, publicación Storage/Firestore, cola duradera por UID y serialización de selecciones.
- `FirebaseAccountService.swift`: integra el publicador en `FirebasePublicProfileService`, confirma el perfil después de publicar y reintenta la cola del UID recuperado.
- `FirebaseSetup.swift`: Storage en 9199 para emuladores y flag remoto. HTTP solo se admite para el origen del emulador explícito; producción conserva HTTPS.
- `OnlineModels.swift` / `OnlineServices.swift`: tipos y protocolo de historial, servicio opcional en la inyección y disponibilidad de fotos. Los contratos siguen compilando sin SDK Firebase.
- `Features/Online/OnlineModeView.swift`: bootstrap real inyecta `FirebaseAccountHistory`; mantiene las salas no disponibles.
- `Features/Menu/MenuDestinations.swift`: estadísticas reales, última partida, hoja de historial, carga/error/reintento, selector/quitar foto y estado de publicación. Escuchas del historial ligadas a la visibilidad del Perfil.
- `Features/Menu/MenuView.swift`: URL publicada del retrato y soporte del origen del emulador en Debug.
- `Features/LocalGame/LocalGameStore.swift`: UID/ID persistidos al comenzar, encolado al finalizar, restauración idempotente y recuperación de Auth al iniciar una partida con una cuenta previamente confirmada.
- `Platform/Online/FirebaseSmokeCheck.swift` / `App/TraidoresApp.swift`: drivers y reporte de pruebas exclusivamente Debug, con endpoint de emuladores explícito.
- `TraidoresIOSUITests/OnlineFlowUITests.swift`: pruebas nativas de historial/fotos y resultados reales del motor local.
- `project.pbxproj`: ambos archivos nuevos ya están registrados, y `FirebaseStorage` agregado a Firebase iOS SDK **12.19.2**. No registrar duplicados ni regenerar el proyecto con el generador antiguo.
- `Configuration/Shared.xcconfig`, ejemplo local e `Info.plist`: `TRAIDORES_PROFILE_STORAGE_ENABLED = NO` compartido; habilitación posterior mediante configuración local.

## Pruebas realizadas

| Verificación | Resultado y alcance |
| --- | --- |
| Android | Ensamblado Debug y 691 pruebas unitarias aprobadas. |
| Backend | 21 pruebas unitarias y 5 integraciones con Auth/Firestore/Functions/Storage emulados aprobadas. Incluyen triggers reales, idempotencia, cuentas borradas y limpieza de fotos por UID. |
| Reglas | Suites de historial y Storage aprobadas: aislamiento, restricciones de escritura, validaciones y ciclo de fotos. Los rechazos de permisos en las pruebas negativas son esperados. |
| Contratos iOS | `bash ios/Scripts/test_online_contracts.sh` aprobado. |
| SDK Android nativo | Subida, JPEG sin GPS, Firestore sin red, selecciones rápidas, dos cuentas, descarga ajena permitida, escritura ajena rechazada, reintento tras cierre de proceso y eliminación de todas las versiones. |
| SDK iOS nativo | `testNativeHistoryAndPhotosAgainstEmulators` aprobado: historial, red cortada/reintento, compresión, aislamiento, descarga de otra cuenta, reemplazo/eliminación y reapertura con foto pendiente. Verificación visual de foto remota y auditoría de accesibilidad del historial. |
| Motor local iOS + Firebase | `testNativeLocalResultsReachFirebase` aprobado: partidas reales del motor, trigger de contadores, restauración sin duplicado y resultado esperando al UID de inicio. Repetido después del ajuste de arranque. |
| Compilaciones iOS | Debug y Release de simulador aprobadas. Se comprobó que los marcadores/drivers de prueba no existen en el binario Release. |

No se ejecutó toda la suite UI de iOS en este bloque ni se hizo todavía la aceptación remota entre teléfonos reales. Las fotos de color sólido y las cuentas `Media QA` existen únicamente en las pruebas; no son contenido de la beta ni datos demostrativos inyectados en la app normal.

### Repetición acotada

Iniciar Auth 9099, Firestore 8081, Functions 5001 y Storage 9199 con `--project traidores`. Para el fixture, definir `FIRESTORE_EMULATOR_HOST=127.0.0.1:8081`, `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099`, `FIREBASE_STORAGE_EMULATOR_HOST=127.0.0.1:9199` y ejecutar `node scripts/seed-media-emulators.cjs /tmp/media-fixtures.json`. Usar fixtures distintas para cada plataforma y un emulador/simulador de QA separado del que usa el usuario.

iOS: pasar las credenciales ficticias del JSON como `TEST_RUNNER_TRAIDORES_MEDIA_EMAIL`, `TEST_RUNNER_TRAIDORES_MEDIA_OTHER_EMAIL`, `TEST_RUNNER_TRAIDORES_MEDIA_PASSWORD` a xcodebuild y seleccionar las dos pruebas nativas mencionadas. Para repetir la prueba local de forma independiente, generar un fixture nuevo.

Android: Debug con `-PtraidoresOnlineAuthorityEmulator=true -PtraidoresProfileStorage=true`. Lanzar `.ProfileMediaSmokeActivity` con `am start -W`, extras `email`, `other`, `password`, `stage=prepare`. Esperar `ANDROID MEDIA PREPARED` en `cache/media_smoke_result.txt` mediante `run-as`; cerrar proceso y relanzar con `stage=resume`. Debe terminar en `ANDROID MEDIA PASS`.

Integración backend: ejecutar `functions/test/accountHistory.integration.test.js` con los endpoints locales anteriores y `HISTORY_TEST_PROJECT=traidores`. No apuntar los scripts de fixtures a producción.

## Próximo bloque: aceptación con Blaze

1. El usuario activa Blaze en el proyecto existente `traidores`. No crear otro proyecto para iOS. La región de Firestore existente y de Functions es `southamerica-west1`.
2. Consultar `node scripts/prepare-firebase-release.cjs`. Sin flags es solo lectura: valida proyecto, región y facturación. Antes, el inventario remoto de Functions devolvió 403; no presumir que esté vacío.
3. Configurar presupuesto/alertas y revisar los topes específicos de Functions si están disponibles para la cuenta. Las alertas no detienen servicios; esos topes no abarcan Storage/Firestore. [Documentación oficial de costes](https://firebase.google.com/docs/projects/billing/avoid-surprise-bills).
4. Crear/conectar el bucket indicado por la configuración: `traidores.firebasestorage.app`. Confirmar dueño y región antes de publicar o cambiar configuraciones. [Requisito oficial de Blaze para Storage](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024).
5. Desplegar con clientes compatibles. El script `--apply` publica explícitamente siete funciones, espera confirmación y después publica reglas/índices de Firestore; `--storage` añade validación del bucket y publicación de sus reglas. Se detiene ante un fallo. No activa facturación ni crea el bucket.
6. Las funciones explícitas son `iniciarPartidaV2`, `limpiarSalasAbandonadasV1`, `registrarSalaHuerfanaV1`, `programarLimpiezaSalaV2`, `guardarHistorialOnlineV1`, `contarPartidaLocalV1`, `borrarHistorialCuentaV1`. No desplegar indiscriminadamente otros servicios.
7. Después del despliegue, compilar sin emuladores y habilitar fotos: Android `-PtraidoresProfileStorage=true`; iOS `TRAIDORES_PROFILE_STORAGE_ENABLED = YES`. Mantener `TRAIDORES_APPLE_SIGN_IN_ENABLED = NO` hasta preparar sus capacidades.
8. Aceptación: dos cuentas Android terminan una partida online y ambas reciben historial; una cuenta termina una local, reinicia y conserva su resultado. Probar foto en el perfil de otra cuenta, sala, mesa, votación y ganadores, reemplazo, eliminación, red cortada y recuperación desde otro dispositivo. iOS puede verificar cuenta/fotos/historial y partidas locales sin tener aún multiplayer.
9. Medir lecturas/escrituras, bytes almacenados/descargados, errores y ejecuciones de Functions. Registrar fallos concretos antes de ampliar la beta.

Las nuevas reglas reservan estadísticas al servidor: un cliente antiguo que todavía intente escribirlas puede recibir rechazo. Coordinar publicación de reglas y compilaciones actualizadas.

## Mejoras propuestas después de esa aceptación

1. **Salas y gameplay online iOS:** implementar directorio/sesión/lobby contra los DTOs compartidos y después sincronización de partida. `ClassicGame` no es intercambiable con el motor Android. La callable reparte roles, pero no arbitra por sí sola todas las fases: no habilitar inicio iOS ni `hostActivoId` sin una autoridad compatible. El jugador iOS debe publicar `puedeArbitrar=false` mientras no pueda ejercer ese papel.
2. **Recuperación de partidas:** verificar reconexión, cierre de proceso y traspaso de autoridad elegible. No deducir autoridad de que exista una conexión o del nombre del jugador.
3. **Retención de fotos:** limpieza automática de revisiones antiguas/orfandad respetando URLs usadas por partidas activas. Actualmente reemplazar conserva versiones; no hay limpieza periódica implementada.
4. **Consumo y abuso:** decidir límites por usuario a partir de mediciones de la beta. La deduplicación de cliente y `maxInstances` no son límites globales de gasto ni protección contra clientes modificados. No se agregó un cooldown largo.

Amigos/presencia y monetización todavía no se implementaron en este bloque. El pack de apoyo previo conserva su condición de prueba; esta entrega no habilita compras ni convierte cosméticos de prueba en derechos pagados.

## Foco sugerido para la revisión de Claude

Revisar aislamiento de UID, cancelación/ACK al cambiar Auth, recuperación de cola al reiniciar, ciclo de escuchas del Perfil, posibles carreras entre texto y foto y paridad de los DTOs Android/iOS. Verificar que un estado de caché no se anuncie como confirmado y que botones online iOS sigan reflejando servicios no disponibles.

Entregar hallazgos concretos con archivo/línea, consecuencia observable y prioridad. Separar errores del código de pendientes deliberados de despliegue o gameplay. Indicar qué pruebas nuevas justificaría cada hallazgo; las verificaciones ya aprobadas no requieren repetir suites completas para una lectura inicial.

Fuentes complementarias: `docs/historial-cuenta-firebase.md`, `docs/fotos-perfil-storage-beta.md`, `docs/firebase-online-schema.md`, `ios/docs/PLAN_ONLINE_VISTAS_IOS.md`. Los planes antiguos son contexto histórico: este documento y el código de los commits indicados describen el estado de esta entrega.
