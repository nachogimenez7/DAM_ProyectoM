# Revisión de las interfaces online — Codex, 3 de octubre de 2026

Referencia: `PLAN_ONLINE_VISTAS_IOS.md`, código Android actual, `firestore.rules`, `database.rules.json` y `functions/src/`. No se consultó ni modificó el proyecto Firebase real. Se mantiene el orden del usuario: preparar y probar primero; Cloud/Blaze al llegar a las pruebas reales.

## Entrega para que Claude avance

Están creados bajo `ios/TraidoresIOS/TraidoresIOS/Platform/Online/`:

- `OnlineModels.swift`: modelos de cuenta/perfil/sala, estados de foto y errores.
- `OnlineServices.swift`: protocolos y contenedor observable `OnlineServices` para inyectar servicios reales o falsos.
- `OnlineContract.swift`: validación/conversión de identidad, códigos, fotos, configuración y estado de sala.

**Estos archivos son la fuente de las interfaces.** El código Swift del plan original es una propuesta anterior y no debe copiarse como una segunda definición. Claude registra estos tres archivos en Xcode junto con sus vistas/fakes; Codex conserva su implementación. No regenerar el proyecto.

Esta entrega no implementa todavía los adaptadores de cuenta, perfil, directorio ni sesión Firebase. Las pantallas pueden avanzar con implementaciones falsas que conformen a estos protocolos. No presentar datos falsos como acceso online real.

## Ajustes sobre la propuesta

1. `publicId` es `String?`, igual que Firestore/Android. No convertirlo a `Int`, agregar `#` al guardar ni usarlo como dueño de una foto.
2. `PublicProfile.uid` y `publicId` son inmutables. `save` recibe `PublicProfileDraft` con solo campos editables; cambiar nombre/frase no debe borrar la foto ni sobrescribir un UID o número público.
3. Perfil conserva `emotesPerfil`, `temaCosmeticoPerfil` y `fotoPlayGames` como compatibilidad de lectura. Escribir con merge conserva estadísticas y otros campos admitidos existentes.
4. La foto tiene estados pendiente, subiendo, publicada y fallida. `pendingPhoto` distingue bytes locales de una eliminación pendiente; una eliminación fallida también debe poder reintentarse. La copia pendiente se guarda por UID. `pendingPhotoData` es una propiedad auxiliar de lectura.
5. `setPhoto(imageData:)` recibe los bytes del selector, no exige que PhotosPicker entregue JPEG. El adaptador procesa orientación/recorte, genera JPEG sin EXIF y comprueba el límite de 256 KiB antes de subir. Guardar URL tras confirmar Firestore, luego limpiar el objeto anterior.
6. Cuenta tiene entradas explícitas de correo/contraseña en `linkAccount` y `signIn`, y ahora `prepareAppleRequest`/`completeAppleSignIn`. Vincular un invitado conserva UID; recuperar una cuenta existente recupera su perfil antes de anunciar `ready`. `appleSignInAvailable` mantiene oculto el acceso nativo hasta que capacidades y adaptador estén disponibles.
7. `RoomSnapshot` distingue `hostId` y `activeHostId`. Los controles deben consultar la autoridad que corresponde a cada operación, no deducirla del nombre del jugador.
8. `LobbyConfig` conserva `presetRoles` y `roles`. Cambiar tiempos no puede resetear una composición elegida en Android. Se incluyen los límites actuales de las reglas y el orden de los once roles.
9. `recoverableRoom()` y `leave()` pueden fallar. Un error de red no equivale a «no hay sala» ni a una salida confirmada. `detach()` libera listeners sin anunciar salida ni eliminar membresía.
10. Usar `@Environment(OnlineServices.self)`. SwiftUI necesita un tipo concreto observable para esa inyección; no inyectar cada protocolo existencial con la API de entorno por tipo.
11. `start` recibe el desempate opcional y devuelve inicio aceptado o mapas para desempatar. Reutilizar el parser `OnlineMatchStartContract` que ya agregó Claude a `TraidoresCore`; no duplicar ese parser en las vistas.
12. El alias invitado se elige de `OnlineContract.guestAliases`. Su número reproduce el hash UTF-16 de Kotlin, incluido el desbordamiento. No usar `Swift.hashValue`, que cambia entre procesos.

## Acceso con Apple

Apple no bloquea este primer bloque si iOS usa únicamente invitado y el sistema propio de correo/contraseña. Si se ofrece Google como acceso a la cuenta principal, hay que ofrecer un acceso equivalente que cumpla los requisitos de privacidad de la guía 4.8; Sign in with Apple es la opción prevista. La guía no impone Apple a todos los sistemas propios de cuentas. [Guía oficial 4.8](https://developer.apple.com/app-store/review/guidelines/#login-services).

Claude informó que ya habilitó el proveedor Apple en Firebase. Codex no modificó proveedores, capacidades externas ni facturación. Los protocolos incluyen el acceso Apple y las implementaciones falsas conservan el valor predeterminado `appleSignInAvailable == false`.

El adaptador real delegará la solicitud en `AppleAccountLink.prepare` y el resultado en `finish`; luego debe cargar el perfil y revisar el baneo antes de publicar `.ready`. El helper recupera la credencial actualizada en la colisión de cuentas y fuerza el refresco del token. Se agregó la comprobación del resultado de `FirebaseSetup.configureIfNeeded()` antes de acceder a Auth, para evitar un fallo fatal con configuración ausente. La autorización Apple nativa sigue requiriendo el equipo/capacidades apropiados; la prueba local descrita abajo usa credenciales ficticias del emulador, no la ventana nativa de Apple.

## Inicio de partida: condición que faltaba

`iniciarPartidaV2` existe, en `southamerica-west1`, y prepara reparto privado, estado inicial `REPARTO` y permisos RTDB. **No contiene una autoridad de servidor que resuelva todas las fases siguientes.** Además, Android utiliza esta callable solo con el modo de emulador; la build normal conserva el inicio desde el cliente. No se verificó un despliegue real de esa función.

No basta con llamar a la función desde iOS y evitar después `hostActivoId`: el inicio actual conserva al solicitante como anfitrión activo. Si el solicitante es el iPhone y ningún motor compatible resuelve las fases, la partida queda sin autoridad que avance.

Para este bloque, `RoomSessionService.startAvailability` debe ser `.unavailable(.onlineGameplay)` en el servicio real. La vista puede mostrar el futuro recorrido con fakes, pero no ejecutar un inicio real ni pasar una sala online a `ClassicGame`. El botón de inicio no se habilita por el solo hecho de tener Blaze o una callable disponible.

El gameplay siguiente debe elegir y probar un camino completo: portar el motor/host compatible o implementar autoridad de servidor con avance de fases, reconexión y cambio de anfitrión. Esto no bloquea registrar cuentas, mostrar fotos ni probar el lobby entre plataformas.

### Compatibilidad del relevo, implementada en este bloque

- Campo opcional `jugadores/{uid}/puedeArbitrar` de tipo booleano. `RoomPlayer.canArbitrate` lo representa en iOS. Android anterior sin campo se interpreta como `true`; el adaptador real de iOS debe escribir `false` mientras su motor no pueda ser autoridad.
- Android lleva esta información del roster a `GameSession` por UID y filtra candidatos tanto registrados como invitados. La transacción vuelve a comprobar el documento del candidato.
- `firestore.rules` acepta el booleano y rechaza el relevo a quien declare `false`. Las pruebas verifican también el rechazo de un tipo incorrecto.
- Las reglas aún no se publicaron. El nuevo campo debe escribirse en el proyecto real después de publicar las reglas correspondientes.
- Esto protege el relevo; no cambia el anfitrión inicial que asigna la callable ni completa un motor de servidor. Por eso continúa deshabilitado el inicio real de iOS.

### Un único contrato de inicio

Se quitaron `OnlineContract.functionRegion` y `startFunction`: región y nombre provienen de `OnlineMatchStartContract` en el núcleo. `OnlineMatchStartClient.startForLobby` convierte el resultado tipado `GameMap` al DTO de presentación `MatchStartResult` con strings. El adaptador de `RoomSessionService.start` puede usar esa entrada cuando exista gameplay compatible; no vuelve a parsear la respuesta ni define otro endpoint.

## Paquete y configuración

- Paquete oficial: `https://github.com/firebase/firebase-ios-sdk`, versión exacta **12.19.2**, mediante Swift Package Manager. No usar una rama móvil ni un rango que cambie durante la integración. La versión está publicada y el entorno local dispone de Swift 6.4/Xcode 27; iOS 17 cumple el mínimo del SDK. [Notas de Firebase](https://firebase.google.com/support/release-notes/ios), [configuración oficial](https://firebase.google.com/docs/ios/setup).
- Productos para el bloque real: `FirebaseAuth`, `FirebaseFirestore`, `FirebaseDatabase`, `FirebaseStorage` y `FirebaseAppCheck`. `FirebaseFunctions` solo al integrar/probar el adaptador de inicio; no implica que el gameplay completo exista.
- La app usa el bundle ID `com.traidores.juego.ios`. Claude ya registró la app y agregó el plist; se verificaron localmente `BUNDLE_ID` y `PROJECT_ID == traidores`. El plist permanece ignorado por Git.
- Claude es el único responsable de `project.pbxproj`/paquetes; Codex no lo editó en esta entrega.
- `FirebaseSetup.swift` ya devuelve `false` si falta el plist. Se verificó que Apple y el inicio respeten ese resultado. Antes de conectar los demás adaptadores hay que dirigir también Firestore, RTDB y Storage a los emuladores y añadir sus productos SPM; por ahora solo usa Auth/Functions. Se agregó Auth 9099 a `firebase.json`. Android hoy todavía usa Auth real; no mezclar UID del Auth emulado con reglas/datos reales.
- No activar Storage remoto ni llamadas de inicio al construir los fakes. Mantener el flag normal apagado.

## Verificación

`bash ios/Scripts/test_online_contracts.sh` compila modelos/protocolos con concurrencia estricta y comprueba nombres de invitado equivalentes a Kotlin, IDs/códigos, recuperación de la URL de foto, rechazo de URLs locales fuera del emulador, conservación de composición de roles y rechazo de fases desconocidas. No requiere Firebase, facturación ni cambios en el proyecto Xcode.

También se comprueba por separado el parser de inicio que agregó Claude con `OnlineMatchStartTests` de `TraidoresCore`. Esto valida el contrato de la respuesta, no una partida online completa ni el despliegue de Functions.

Resultado del primer bloque: pruebas de contratos aprobadas; comprobación de tipos para simulador arm64 con destino iOS 17 y concurrencia estricta aprobada; parser de inicio 5/5. La actualización de la noche añade las comprobaciones siguientes.

Actualización de la noche, tras el mensaje de Claude:

- Contratos Swift y parser del núcleo 5/5 aprobados otra vez con la interfaz Apple.
- Android: `testDebugUnitTest`, `lintDebug` y `assembleDebug` aprobados con compatibilidad de autoridad y sus pruebas.
- Auth + Firestore + Storage emulados: `scripts/test-apple-auth-emulator.cjs` aprobó vínculo conservando UID, cuenta existente/recuperación de perfil, refresco del proveedor, correo privado y Apple sin email. Ambos casos Apple permiten crear sala y subir una foto propia; el invitado no. Es una prueba con el SDK JavaScript y credenciales ficticias, no una prueba del helper Swift ni de Sign in with Apple real.
- Regresión completa de `scripts/test-firestore-rules.cjs` y `scripts/test-storage-rules.cjs` aprobada, incluido ciclo de subir, leer desde otro usuario, reemplazar y quitar foto.
- App iOS completa compilada para simulador, incluidos los helpers Firebase nuevos y las tres interfaces. Build en `/tmp/traidores-handoff-ios`, log `/tmp/traidores-handoff-ios.log`. Persiste un aviso anterior de aislamiento de `ArcFlight` en `VoteCeremonyView.swift`; no bloquea el modo Swift 5 actual.
- La falta de npm global no bloquea las pruebas: la CLI local ya está instalada en `/tmp/traidores-photo-rules/node_modules/.bin/firebase`, con Node en `/Users/ignaciogimenez/.local/bin/node`. Puede arrancar Auth 9099 y Functions 5001 para la comprobación de simulador que preparó Claude. Esta disponibilidad no equivale a haber ejecutado todavía esa prueba en el simulador.

Se intentó además `FirebaseSmokeCheck` en un simulador separado, con una copia de la build y configuración ficticia del proyecto `demo-traidores-photos`; el plist original no se editó. Auth/Firestore/RTDB/Functions arrancaron localmente. La build sin firma abrió, pero Firebase Auth encontró un error de autorización del llavero (`SecItemCopyMatching -34018`). Las copias firmadas de prueba no llegaron a abrir correctamente. **No se confirmó una llamada a la callable desde el simulador** ni se considera aprobada esa comprobación. Continuarla con una build de desarrollo firmada desde Xcode, sin atribuir el bloqueo a npm. Los emuladores de Functions usaron Node 24 del host frente al `engines.node == 20` del backend; esta prueba tampoco demuestra paridad con el runtime de despliegue.

Comando usado para la prueba local (desde la raíz del repo, sin facturación ni despliegue):

```bash
PATH="/Users/ignaciogimenez/.local/bin:/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
NODE_PATH=/tmp/traidores-photo-rules/node_modules \
/tmp/traidores-photo-rules/node_modules/.bin/firebase emulators:exec \
  --project demo-traidores-photos --only auth,firestore,storage \
  '/Users/ignaciogimenez/.local/bin/node scripts/test-apple-auth-emulator.cjs && /Users/ignaciogimenez/.local/bin/node scripts/test-storage-rules.cjs && /Users/ignaciogimenez/.local/bin/node scripts/test-firestore-rules.cjs'
```

Para las pruebas de interfaz, quitar foto debe recuperar el avatar ilustrado en el perfil y el respaldo apropiado en el roster. En AX5 se permite desplazamiento accesible: no exigir que todos los controles entren simultáneamente en la pantalla a costa de reducir el texto.

## Mensaje para copiar a Claude

“Leé la actualización de la noche en `ios/docs/REVISION_CODEX_ONLINE_2026-10-03.md`. Las tres interfaces están estables, incluida Apple; ya vi su registro en Xcode y Firebase 12.19.2 exacta. Podés continuar con vistas/fakes. El resultado del núcleo se convierte con `OnlineMatchStartClient.startForLobby`; se quitaron las constantes duplicadas. Android y reglas ya excluyen del relevo a `puedeArbitrar: false`; el adaptador real iOS debe escribirlo. Las pruebas de Auth con Apple/correo oculto, Storage y reglas pasaron en emuladores, y la app iOS compiló. Hay CLI local aunque no haya npm global. La comprobación de la callable desde simulador sigue pendiente por firma/llavero de la copia de prueba: no está aprobada. El inicio real sigue deshabilitado porque la callable aún no resuelve las fases posteriores. FirebaseSetup y los productos Firestore/Database/Storage se completarán junto con los adaptadores reales. No activar Blaze ni facturación todavía.”
