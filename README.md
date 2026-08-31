# App Traidores

Proyecto Android/Kotlin del juego **Traidores**, un juego mobile de deduccion social con lobby, asignacion de roles, fases de noche/dia, votacion, expulsion y resultados.

Tiene dos modos:

- **Local (vs IA):** completo y offline. Jugas contra bots conversacionales (`LocalBotAi`).
- **Online (experimental):** real, sobre **Firebase Firestore + Realtime Database**, con Firebase Auth, App Check, presencia, sincronizacion por fases y recuperacion. La autoridad de partida sigue en el dispositivo anfitrion; ver [`docs/firebase-online-schema.md`](docs/firebase-online-schema.md).

Version actual: `0.1.16` (versionCode 17).

## Como abrir el proyecto

1. Abrir **Android Studio**.
2. Seleccionar **Open**.
3. Elegir esta carpeta del proyecto: `App Traidores`.
4. Esperar la sincronizacion de Gradle.
5. Ejecutar la app en un emulador o dispositivo Android.

> Para compilar se necesita `app/google-services.json` (Firebase, gitignored). El modo local funciona offline; el online requiere internet y Firebase configurado.

## Pantallas principales

- **Menu principal:** acceso a jugar, roles, ayuda, opciones y perfil.
- **Lobby:** configuracion de jugadores, mapa, tiempos, roles y opciones avanzadas.
- **Asignacion de roles:** muestra la carta del jugador antes de iniciar la partida.
- **Gameplay:** fases de noche, amanecer, debate, votacion, expulsion y resultado.
- **Roles y ayuda:** material de consulta para explicar reglas y personajes.
- **Opciones/perfil:** configuracion de sonido, nombre y datos del jugador.

## Modo presentacion y test rapido

El gameplay usa por defecto un ritmo de presentacion: respeta los tiempos configurados en el lobby y no permite saltear fases temporizadas.

En **Lobby > Opciones avanzadas** existe `MODO TEST RAPIDO`. Al activarlo, la partida recupera el comportamiento rapido para probar flujos: fases sin accion humana avanzan velozmente y la votacion/expulsion se acelera.

## Estructura rapida

- `app/src/main/java/com/traidores/juego/`: codigo principal de la app.
- `app/src/main/res/`: layouts, drawables, fuentes, sonidos y recursos Android.
- `app/src/test/`: tests unitarios de reglas y motor.
- `docs/`: notas tecnicas y documentacion del proyecto.
- `roles_gauchos/`, `roles_griegos/`, `roles_medievales/`: recursos visuales de roles por mapa/tematica.
- `tmp/`: archivos temporales o previews locales, no necesarios para compilar.

## Documentacion

Toda la documentacion del proyecto esta en [`docs/`](docs/README.md):

- **General:** [vision/objetivos/alcance](docs/general/01-vision-objetivos-alcance.md), [mecanicas](docs/general/02-mecanicas.md), [arquitectura](docs/general/03-arquitectura.md), [tecnologias](docs/general/04-tecnologias.md), [estructura](docs/general/05-estructura-proyecto.md), [convenciones](docs/general/06-convenciones-codigo.md), [flujo](docs/general/07-flujo-funcionamiento.md).
- **Desarrollo:** [guia para nuevos devs](docs/desarrollo/guia-nuevos-desarrolladores.md), [backlog](docs/desarrollo/backlog.md), [decisiones de arquitectura](docs/desarrollo/decisiones-arquitectura.md).
- **Online:** [contrato Firestore](docs/firebase-online-schema.md).

> La documentacion para la facultad (Analisis de Sistemas / Bases de Datos) se movio fuera del repo a `Facultad/Objetos/App Traidores - Analisis y BD/`.

> Nota: [`docs/project-structure.md`](docs/project-structure.md) quedo parcialmente desactualizado; la version vigente es [`docs/general/05-estructura-proyecto.md`](docs/general/05-estructura-proyecto.md).
