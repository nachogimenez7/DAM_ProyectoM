# Intro de Bandido Games

## Comportamiento implementado

- La versión completa dura aproximadamente dos segundos.
- Se reproduce una vez después de instalar la app y una vez después de cada actualización.
- En aperturas posteriores se muestra una firma visual silenciosa de menos de un segundo.
- Un toque en cualquier parte salta la presentación.
- Play Games inicia la vinculación en segundo plano mientras la intro está visible.
- La música del menú comienza después de cerrar la presentación.
- El ladrido respeta la opción y el volumen de efectos de sonido.

## Audio

El ladrido real de Bandido está guardado en:

`app/src/main/res/raw/sfx_bandido_bark.wav`

Se extrajo de una grabación de cachorro, se limpió suavemente y se normalizó sin cambiar
su tono. La animación funciona sin sonido si el recurso se elimina.

Tratamiento aplicado:

- un único ladrido de 0,62 segundos;
- mezcla mono a 44,1 kHz;
- filtrado suave de graves y agudos;
- compresión ligera y normalización con pico máximo de -1 dB;
- fundidos breves para evitar cortes audibles.

## Assets

- estado normal: `app/src/main/res/drawable-nodpi/bandido_games_intro_idle.webp`
- apertura intermedia: `app/src/main/res/drawable-nodpi/bandido_games_intro_bark_1.webp`
- apertura avanzada: `app/src/main/res/drawable-nodpi/bandido_games_intro_bark_2.webp`
- estado ladrando: `app/src/main/res/drawable-nodpi/bandido_games_intro_bark.webp`
- secuencia de fotogramas: `app/src/main/res/drawable/bandido_games_intro_bark_animation.xml`
- maestro normal con la O corregida: `branding/bandido-games-logo-corrected.png`
- maestro ladrando con la O corregida: `branding/bandido-games-logo-bark-v3.png`
- previsualización con audio: `branding/bandido-games-intro-fluid-preview.mp4`
