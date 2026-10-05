# Fotos de perfil para la beta — actualizado el 4 de octubre de 2026

Decisión del usuario: galería y avatares ilustrados; sin SafeSearch ni revisión manual en esta etapa. Prioridad: que la foto aparezca durante todo el juego. No se activó facturación, no se creó un bucket ni se desplegaron reglas.

## Android

- Quitada la opción «Usar mi foto de Google» y la adopción automática de Play Games. Se conservan enlaces antiguos para compatibilidad; Play Games sigue disponible para acceso/progreso.
- Las fotos se recortan a cuadrado de hasta 512 px, se recodifican como JPEG sin EXIF y tienen límite de 256 KiB para publicar.
- Storage: `profilePhotos/{firebaseAuthUid}/avatar_{sha256}.jpg`. Cada revisión tiene su propia URL. Un reemplazo conserva las anteriores para no romper el roster de una partida activa. Quitar la foto elimina todas las versiones del UID; borrar Auth también las elimina desde el backend. La limpieza periódica de versiones antiguas queda pendiente.
- Firestore: campo opcional `fotoPerfil` en `perfiles_publicos/{uid}` y jugadores de sala; se conserva `fotoPlayGames` como respaldo para clientes/perfiles anteriores. UID de Auth identifica propiedad; el nombre del jugador no identifica archivos.
- La sala transmite la referencia y el resolver la conserva en la partida. Perfil, sala, cartas de mesa, votaciones/desempates y resultados usan la misma foto. La carta del rol sigue visible en resultados; debajo del nombre y la etiqueta del rol aparece la foto circular de 24 dp/pt.
- Avatar de respaldo mientras se descarga; caché en memoria y protección contra callbacks de una foto anterior en una vista reutilizada. No se llama a Vision por visualizar fotos.
- Subida/reintento al guardar/volver al perfil. La copia local sigue disponible si falla la red. Cambiar nombre/frase no vuelve a subir una foto recuperada de la nube. Las fotos existentes del dispositivo se publican al entrar al perfil cuando se habilita Storage.

La integración remota está deshabilitada por defecto para conservar las reglas desplegadas y evitar peticiones al bucket antes de prepararlo. Construir con `-PtraidoresProfileStorage=true` solo después de conectar el bucket y desplegar reglas. Para emuladores: combinar con `-PtraidoresOnlineAuthorityEmulator=true`; Storage en puerto 9199. Con esa opción de emuladores, Auth también se enruta al puerto 9099 (actualizado el 4/10). Las pruebas de reglas usan identidades de prueba y no requieren facturación.

## iOS

Firebase Auth, Firestore y Storage están integrados. `FirebaseProfilePhotos` publica, reemplaza, elimina y reintenta con bytes JPEG persistidos por UID. `FirebasePublicProfileService` recupera el campo compartido `fotoPerfil`; Perfil y menú usan esa URL, y las tarjetas usan el mismo perfil confirmado. Las ediciones de texto no escriben ni borran la foto.

La foto propia se conserva en las vistas de partida local, votación y ganadores. El gameplay online de iOS sigue pendiente: no se presenta una partida Android ↔ iOS completa como implementada. La subida usa el mismo UID y bucket que Android; nunca se sube el estado privado de `ClassicGame`.

Producción conserva `TRAIDORES_PROFILE_STORAGE_ENABLED = NO`. Cambiar a `YES` en la configuración local únicamente después de preparar bucket y reglas. Debug con `-firebase-emulator-host 127.0.0.1` enruta Auth, Firestore, Functions y Storage a localhost y habilita la prueba de fotos. HTTP solo se acepta para el origen explícito de ese emulador; las fotos remotas requieren HTTPS.

## Activación y aceptación

1. La integración de cuenta, historial y fotos está preparada en ambas plataformas. Ejecutar las pruebas de emuladores. El usuario decidió posponer la comprobación de Brubank y la prueba Cloud hasta tener listas las pruebas reales; no es necesario completar todo el online de iOS para empezar con dos cuentas Android.
2. Activar la prueba Cloud, vincular la cuenta de facturación al proyecto Firebase existente y crear/conectar su bucket. Revisar su región y `google-services.json`; iOS ya tiene su configuración Firebase propia.
3. Desplegar `storage.rules` y la actualización de `firestore.rules` antes de habilitar la opción de compilación. No desplegar todas las funciones o reglas de otros servicios como parte de este cambio.
4. Probar con dos cuentas: seleccionar, reemplazar y quitar; sala → partida → votación → resultado; recuperar perfil en otro dispositivo; red cortada; cambios rápidos y cambio de cuenta durante subida.
5. Medir subidas/descargas y bytes durante la beta. El límite de frecuencia por usuario y la limpieza automática de archivos huérfanos aún no están implementados.

## Verificación local

Android: pruebas unitarias, ensamblado y lint. Se amplió la prueba del resolver para asegurar que conserva la foto del jugador remoto. Storage: pruebas de propiedad, lecturas autenticadas, rechazo de invitados, tamaño/tipo, ruta y eliminación; Firestore: perfil con foto y rechazo de escrituras ajenas y URL excesiva, además de suites de reglas existentes.

`scripts/test-storage-rules.cjs` también comprueba con los SDK y emuladores el circuito subir → publicar perfil → recuperar desde otra sesión → copiar al roster → descargar desde otra cuenta, más reemplazo, eliminación y una publicación rechazada que conserva la referencia anterior. Usa bytes de prueba con metadata JPEG: verifica transporte y permisos, no decodificación de una foto ni moderación. Se complementan con drivers nativos Debug que sí ejecutan los publicadores Android/iOS: red cortada, cambios rápidos, cambio de cuenta y cierre/reapertura. No están incluidos en Release.

iOS: compilación de simulador y pruebas de perfil, votación y victoria. Las iniciales de respaldo son arte decorativo dibujado en Canvas; los contenedores anuncian el nombre completo al lector de pantalla. Las pruebas seleccionadas de perfil, votación y victoria pasaron; la prueba final de victoria incluyó la auditoría de accesibilidad. No se ejecutó la suite UI completa ni se probaron fotos entre dispositivos reales.

Las URLs de descarga de Firebase incluyen un token compartible. Las reglas impiden escrituras ajenas y limitan las lecturas mediante SDK; no convierten una URL de descarga compartida en una foto privada. Aquí se usan como fotos públicas de perfil, según la decisión de la beta.

## Repetir las pruebas nativas

Iniciar los emuladores con `firebase emulators:start --project traidores --only auth,firestore,functions,storage`. Preparar dos cuentas y resultados usando `scripts/seed-media-emulators.cjs /tmp/media-fixtures.json` con `FIRESTORE_EMULATOR_HOST=127.0.0.1:8081`, `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099` y `FIREBASE_STORAGE_EMULATOR_HOST=127.0.0.1:9199`. El script se niega a acceder a producción; escribe credenciales ficticias en un archivo de permisos 600. Usar fixtures distintas para cada plataforma.

iOS: pasar los valores del JSON como `TEST_RUNNER_TRAIDORES_MEDIA_EMAIL`, `TEST_RUNNER_TRAIDORES_MEDIA_OTHER_EMAIL`, `TEST_RUNNER_TRAIDORES_MEDIA_PASSWORD` al ejecutar `OnlineFlowUITests/testNativeHistoryAndPhotosAgainstEmulators` y `testNativeLocalResultsReachFirebase` con xcodebuild. La primera prueba termina la app con bytes pendientes y la abre de nuevo; también verifica la pantalla real de historial y su accesibilidad. La segunda hace terminar partidas reales del motor local y espera los contadores del trigger.

Android: compilar Debug con ambos flags de emuladores/Storage y lanzar `.ProfileMediaSmokeActivity` con `am start -W`, extras `email`, `other`, `password` y `stage=prepare`. Leer `run-as com.traidores.juego cat cache/media_smoke_result.txt`. Tras `ANDROID MEDIA PREPARED`, hacer `am force-stop` y relanzar con `stage=resume`; debe indicar `ANDROID MEDIA PASS`. El driver se niega a ejecutar contra producción.

La prueba entre teléfonos reales y la aceptación del bucket remoto siguen pendientes de Blaze. No se implementó moderación ni un límite de gasto automático.
