# Fotos de perfil para la beta — 3 de octubre de 2026

Decisión del usuario: galería y avatares ilustrados; sin SafeSearch ni revisión manual en esta etapa. Prioridad: que la foto aparezca durante todo el juego. No se activó facturación, no se creó un bucket ni se desplegaron reglas.

## Android

- Quitada la opción «Usar mi foto de Google» y la adopción automática de Play Games. Se conservan enlaces antiguos para compatibilidad; Play Games sigue disponible para acceso/progreso.
- Las fotos se recortan a cuadrado de hasta 512 px, se recodifican como JPEG sin EXIF y tienen límite de 256 KiB para publicar.
- Storage: `profilePhotos/{firebaseAuthUid}/avatar_{sha256}.jpg`. Cada revisión tiene su propia URL; se publica antes de retirar el archivo anterior. Si falla la limpieza puede quedar un archivo antiguo; debe revisarse antes de ampliar la beta.
- Firestore: campo opcional `fotoPerfil` en `perfiles_publicos/{uid}` y jugadores de sala; se conserva `fotoPlayGames` como respaldo para clientes/perfiles anteriores. UID de Auth identifica propiedad; el nombre del jugador no identifica archivos.
- La sala transmite la referencia y el resolver la conserva en la partida. Perfil, sala, cartas de mesa, votaciones/desempates y resultados usan la misma foto. La carta del rol sigue visible en resultados; debajo del nombre y la etiqueta del rol aparece la foto circular de 24 dp/pt.
- Avatar de respaldo mientras se descarga; caché en memoria y protección contra callbacks de una foto anterior en una vista reutilizada. No se llama a Vision por visualizar fotos.
- Subida/reintento al guardar/volver al perfil. La copia local sigue disponible si falla la red. Cambiar nombre/frase no vuelve a subir una foto recuperada de la nube. Las fotos existentes del dispositivo se publican al entrar al perfil cuando se habilita Storage.

La integración remota está deshabilitada por defecto para conservar las reglas desplegadas y evitar peticiones al bucket antes de prepararlo. Construir con `-PtraidoresProfileStorage=true` solo después de conectar el bucket y desplegar reglas. Para emuladores: combinar con `-PtraidoresOnlineAuthorityEmulator=true`; Storage en puerto 9199. El proyecto existente aún usa Auth real; las pruebas de reglas usan identidades simuladas y no requieren una cuenta ni facturación.

## iOS

El port actual no incluye Firebase, cuentas ni salas online. La foto local ya elegida se muestra en lobby, ficha propia, mesa/chat, recuento/desempate y resultados. La identidad humana se decide por ID, nunca por coincidencia de nombre con un bot. Se agregó un componente reutilizable con soporte de URL remota y un campo opcional en el perfil local; no se presenta esto como subida online implementada.

Pendiente para compartir Android ↔ iOS: integrar Firebase Auth/Firestore/Storage y recuperación del perfil en el port. Adaptar su DTO público al campo `fotoPerfil`, usando el mismo UID y el mismo bucket; no copiar el estado privado de `ClassicGame` al servidor.

## Activación y aceptación

1. Preparar Android y acordar el contrato de cuenta/perfil/lobby para iOS con Claude. Primero ejecutar las pruebas de emuladores. El usuario decidió posponer la comprobación de Brubank y la prueba Cloud hasta tener listas las pruebas reales; no es necesario completar todo el online de iOS para empezar con dos cuentas Android.
2. Activar la prueba Cloud, vincular la cuenta de facturación al proyecto Firebase existente y crear/conectar su bucket. Revisar su región y `google-services.json`; iOS requerirá su configuración propia.
3. Desplegar `storage.rules` y la actualización de `firestore.rules` antes de habilitar la opción de compilación. No desplegar todas las funciones o reglas de otros servicios como parte de este cambio.
4. Probar con dos cuentas: seleccionar, reemplazar y quitar; sala → partida → votación → resultado; recuperar perfil en otro dispositivo; red cortada; cambios rápidos y cambio de cuenta durante subida.
5. Medir subidas/descargas y bytes durante la beta. El límite de frecuencia por usuario y la limpieza automática de archivos huérfanos aún no están implementados.

## Verificación local

Android: pruebas unitarias, ensamblado y lint. Se amplió la prueba del resolver para asegurar que conserva la foto del jugador remoto. Storage: pruebas de propiedad, lecturas autenticadas, rechazo de invitados, tamaño/tipo, ruta y eliminación; Firestore: perfil con foto y rechazo de escrituras ajenas y URL excesiva, además de suites de reglas existentes.

`scripts/test-storage-rules.cjs` también comprueba con los SDK y emuladores el circuito subir → publicar perfil → recuperar desde otra sesión → copiar al roster → descargar desde otra cuenta, más reemplazo, eliminación y una publicación rechazada que conserva la referencia anterior. Usa bytes de prueba con metadata JPEG: verifica transporte y permisos, no decodificación de una foto ni moderación. Estas pruebas no ejecutan el gestor Android ni los servicios iOS; siguen pendientes las pruebas de red cortada, cambios rápidos y cambio de cuenta desde las apps.

iOS: compilación de simulador y pruebas de perfil, votación y victoria. Las iniciales de respaldo son arte decorativo dibujado en Canvas; los contenedores anuncian el nombre completo al lector de pantalla. Las pruebas seleccionadas de perfil, votación y victoria pasaron; la prueba final de victoria incluyó la auditoría de accesibilidad. No se ejecutó la suite UI completa ni se probaron fotos entre dispositivos reales.

Las URLs de descarga de Firebase incluyen un token compartible. Las reglas impiden escrituras ajenas y limitan las lecturas mediante SDK; no convierten una URL de descarga compartida en una foto privada. Aquí se usan como fotos públicas de perfil, según la decisión de la beta.
