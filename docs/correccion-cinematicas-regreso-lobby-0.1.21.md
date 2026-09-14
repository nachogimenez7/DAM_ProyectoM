# Corrección de cinemáticas y regreso al lobby — 0.1.21

## Diagnóstico comprobado

Los cinco emuladores BlueStacks usados en la prueba tenían en cero las tres escalas globales
de animación de Android. Las presentaciones conservaban parte de sus esperas de seguridad,
pero `ObjectAnimator` llevaba los elementos a su estado final inmediatamente. Por eso la
partida parecía estar en modo rápido aun sin una opción del juego que lo solicitara.

El regreso final tenía otra causa: al llegar a 5/5, el anfitrión actualizaba el plazo de vuelta
sin generar una nueva secuencia autoritativa. Los invitados descartaban esa actualización como
duplicada y el anfitrión regresaba solo; luego su lobby limpiaba el estado de la partida.

## Cambios

- Las presentaciones esenciales tienen una animación alternativa basada en el reloj de dibujo
  cuando Android desactiva `Animator`: reparto de rol, día/noche, muerte, amanecer sin muerte,
  silencio, revelación de traidores, recuento/expulsión y victoria.
- Con escalas normales se conserva la animación original.
- La orden de volver al lobby se publica como una nueva secuencia, se confirma en RTDB y recién
  después se marca la sala como finalizada.
- Todos reciben el mismo plazo de regreso y el lobby anfitrión espera 1,5 segundos antes de
  limpiar la partida anterior.
- Las tarjetas del recuento son más angostas para mantener ambas columnas dentro del marco en
  pantallas estrechas.

Todo funciona con el plan Spark. No se agregaron Functions ni servicios que requieran Blaze.

## APK verificada

- Archivo: `output/apk/Traidores-online-0.1.21.apk`
- Versión: `0.1.21` (`versionCode 22`)
- Tamaño: `116.632.865 bytes`
- SHA-256: `DC4CEA8B81BA85E92A2F80B628D48FA3002A644CCAEBA8CB6C95A2042895A666`
- Firma APK Signature Scheme v2 verificada.
