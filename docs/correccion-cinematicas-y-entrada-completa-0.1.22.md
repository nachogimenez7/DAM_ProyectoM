# Corrección de cinemáticas y entrada completa — 0.1.22

## Evidencia de la prueba

Los cinco emuladores BlueStacks tenían en cero `animator_duration_scale`,
`transition_animation_scale` y `window_animation_scale`. La pantalla de reparto todavía usaba
un `AnimatorSet` normal: Android lo terminaba inmediatamente y abría el gameplay sin mostrar la
cinemática. La alternativa basada en animaciones clásicas tampoco resultó suficiente en esa
configuración de BlueStacks.

En la partida fotografiada, el registro del anfitrión confirmó que liberó el ingreso con cuatro
de cinco participantes preparados. Faltaba la confirmación de `Forastero 9676`, que permaneció
en el lobby mientras los demás entraron al reparto.

## Cambios

- Las presentaciones esenciales usan un motor cuadro a cuadro basado en reloj monotónico cuando
  Android deshabilita las animaciones. No depende de `Animator`, de transiciones de ventana ni de
  las escalas globales del emulador.
- El reparto inicial también usa ese motor y conserva su duración antes de abrir el gameplay.
- Día/noche, muerte, amanecer sin muerte, silencio, revelación de traidores, recuento/expulsión y
  victoria mantienen su presentación aunque las escalas estén en cero.
- La preferencia de efectos reducidos ya no elimina la ceremonia esencial de victoria.
- El anfitrión sólo libera el ingreso cuando cada jugador de la lista inicial publicó su
  confirmación para el mismo `matchId`. Se eliminó la liberación parcial por mayoría o timeout.
- Mientras falta una confirmación, anfitrión e invitados reintentan la barrera de entrada cada
  1,5 segundos. Así una falla queda visible como espera conjunta y no divide la sala.
- Se agregaron registros `essential_presentation_start` y `essential_presentation_finish` para
  comprobar qué motor y qué presentación ejecutó cada dispositivo.

Todo este cambio funciona con Firebase Spark. No requiere Functions desplegadas ni Blaze.

## Validación

- 642 pruebas unitarias: 0 fallos.
- Lint Android: 0 errores (1.247 advertencias preexistentes).
- `assembleDebug` y `assembleRelease`: correctos.
- APK `0.1.22` (`versionCode 23`) verificado con APK Signature Scheme v2 y la misma firma de
  desarrollo usada por `0.1.21`.
- Archivo: `output/apk/Traidores-online-0.1.22.apk`.
- Tamaño: `116.649.249 bytes`.
- SHA-256: `182005D38B3A4E27F6A6D99286D2D220112191D71BC889EF953E2EB30BB534A6`.

## Prueba manual que falta

La comprobación decisiva es una partida nueva con cinco emuladores usando todos el APK 0.1.22.
Debe verse el reparto en los cinco y ninguno debe entrar a gameplay hasta que estén confirmados
los cinco. Luego deben verse las transiciones de noche/día y la resolución de muerte o ausencia
de muerte. Esta validación necesita la ejecución visual en BlueStacks.
