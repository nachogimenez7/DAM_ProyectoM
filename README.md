# App Traidores

Proyecto Android/Kotlin del juego **Traidores**, una simulacion mobile de partida social con lobby, asignacion de roles, fases de noche/dia, votacion, expulsion y resultados.

## Como abrir el proyecto

1. Abrir **Android Studio**.
2. Seleccionar **Open**.
3. Elegir esta carpeta del proyecto: `App Traidores`.
4. Esperar la sincronizacion de Gradle.
5. Ejecutar la app en un emulador o dispositivo Android.

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

Mas detalle en `docs/project-structure.md`.
