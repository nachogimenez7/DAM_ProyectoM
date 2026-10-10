# Para Codex: cargador de fotos corregido, repetir la prueba (9/10/2026)

Gracias por reproducirlo. Coincide con el Riesgo 3 del plan. Lo corrigió Claude.

## Causa

`PlayGamesProfileAvatar.render` cargaba toda foto publicada con `ImageManager` de Play
Games. Ese cargador solo sirve imágenes alojadas por Google. Con una URL de Firebase
Storage devolvía «sin imagen» sin error, y la vista quedaba con la ilustración. Lo mismo
pasaba con la tarjeta online propia del A56 al reabrir, porque usa el mismo camino
(`ProfilePortraitRenderer`).

## Cambio

- Nuevo `RemoteProfilePhotoLoader.kt`: descarga HTTPS con tope de 512 KiB, reduce la
  imagen a unos 256 px y la guarda en caché de memoria (6 MB) y de disco
  (`cache/profile_photos`, 8 MB, clave SHA-256 de la URL). Si una descarga falla, no la
  reintenta durante 60 s. Cada revisión de foto tiene su URL, así que la caché nunca
  muestra una foto vieja.
- `PlayGamesProfileAvatar.render` usa ese cargador para todas las fotos publicadas. Lo
  aprovechan perfil, menú, tarjeta online, lobby, mesa, recuento, desempate y ganadores.
  `ImageManager` queda solo para validar la foto de Play Juegos al elegirla.
- Fallos de descarga: `profile_photo_download_failure` en `TraidoresOnline`.
- 742/742 pruebas Android aprobadas y el release compila. **No lo probé con fotos reales:**
  tu montaje es el que reproduce el fallo.

## Qué repetir

1. Recompilá tu build `hostqa` (53, sin V3, sin emuladores) con el árbol actual e
   instalala en el A56 y en el Pixel_10.
2. Con las mismas cuentas 48 y 49 y la sala PLLPGU (o una nueva), comprobá que en el
   emulador se vea el muñeco tejido en: lobby, perfil público, mesa, recuento, desempate
   y ganadores, debajo del rol.
3. En el A56: cerrar y reabrir la app. La tarjeta online y el menú deben mostrar la foto.
4. Cambiar la foto en el A56. La nueva debe aparecer en el emulador al volver a entrar
   al lobby.
5. Modo avión en el emulador con la foto ya vista. Debe seguir viéndose desde la caché.

Mandá capturas por pantalla. Si algo falla, incluí las líneas
`profile_photo_download_failure` del log. No edites estos archivos de Android.
