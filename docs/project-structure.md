# Estructura del proyecto

> ⚠️ **Documento parcialmente desactualizado.** Menciona archivos que ya no existen (`PlayerProfileStore.kt`, `OnlineLobbyModels.kt`, `OnlineLobbyStore.kt`) y describe el online como "simulado/local" cuando hoy es real sobre Firestore. La versión vigente es [`general/05-estructura-proyecto.md`](general/05-estructura-proyecto.md). Se conserva como referencia histórica.

Esta guia resume donde esta cada parte importante sin mover paquetes Kotlin antes de la presentacion.

## Raiz

- `app/`: modulo Android principal.
- `gradle/`, `gradlew`, `gradlew.bat`: wrapper de Gradle para compilar y testear.
- `build.gradle`, `settings.gradle`, `gradle.properties`: configuracion del proyecto.
- `README.md`: entrada rapida para abrir y entender la app.
- `docs/`: documentacion tecnica, notas de diseno y material auxiliar.
- `roles_gauchos/`, `roles_griegos/`, `roles_medievales/`: imagenes fuente de roles por ambientacion.
- `tmp/`: previews y archivos temporales locales.

## Codigo principal

Ruta: `app/src/main/java/com/traidores/juego/`

- `MainActivity.kt`, `JugarActivity.kt`, `LocalModeActivity.kt`, `OnlineModeActivity.kt`: navegacion inicial.
- `LobbyActivity.kt`: armado de sala, jugadores, mapa, tiempos y opciones avanzadas.
- `AssigningRolesActivity.kt`: lectura inicial de carta antes de empezar la partida.
- `GameplayMockActivity.kt`: pantalla principal de partida. Orquesta UI, fases, timers, chat, overlays y animaciones.
- `GameEngine.kt`: reglas de partida, resolucion de fases, votos, muertes, desempates y condiciones de victoria.
- `GameModels.kt`: modelos de sesion, jugadores, roles, fases y configuraciones; tambien contiene `LocalGameFactory`, que crea partidas locales, bots, mapas y distribucion de roles.
- `LocalBotAi.kt`: decisiones automaticas de bots.
- `GameplayTableUi.kt`: textos, targets, estados y helpers de UI del gameplay.
- `VoteResultAnimator.kt`, `DayNightTransitionAnimator.kt`, `WinnerResultsRenderer.kt`: animaciones y presentacion de momentos especiales.
- `MusicManager.kt`, `GameplaySoundEffects.kt`: musica y efectos.
- `ProfileActivity.kt`, `PlayerProfileStore.kt`: perfil del jugador.
- `LobbyBrowserActivity.kt`, `OnlineLobbyModels.kt`, `OnlineLobbyStore.kt`: flujo online simulado/local.

## Recursos Android

Ruta: `app/src/main/res/`

- `drawable/`: fondos, botones, cartas, imagenes y estilos visuales.
- `layout/`: pantallas XML cuando aplica.
- `font/`: tipografias.
- `raw/`: audio.
- `values/`: colores, textos, temas y dimensiones.
- `mipmap/`: iconos de launcher.

## Tests

Ruta: `app/src/test/java/com/traidores/juego/`

- `GameEngineTest.kt`: pruebas principales del motor, roles, fases, votos y condiciones de victoria.

## Documentacion existente

- `docs/lan-role-readiness.md`: notas de preparacion para lectura/flujo de roles.
- `docs/map-exclusive-roles.md`: reglas de roles por mapa.
- `docs/discord/`: material para integracion o presentacion de Discord.

## Criterio de orden para despues de presentar

Cuando no haya presion de entrega, el orden real deberia separar paquetes por responsabilidad:

- `engine/`: reglas puras y bots.
- `model/`: modelos serializables.
- `gameplay/`: pantalla de partida, coordinadores y animadores.
- `lobby/`: sala, configuracion y asignacion.
- `audio/`: musica y efectos.
- `profile/`: perfil del jugador.
- `online/`: simulacion online y almacenamiento local.
- `ui/common/`: helpers visuales compartidos.

Ese refactor conviene hacerlo con tests y compilacion porque mover clases Kotlin cambia paquetes e imports.
