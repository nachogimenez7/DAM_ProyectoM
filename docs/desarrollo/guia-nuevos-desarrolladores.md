# Guía para nuevos desarrolladores

Bienvenido a **App Traidores**. Esta guía te pone a producir rápido.

## 1. Requisitos

- **Android Studio** (versión reciente) con Android SDK 34 y JDK 17 (compila a bytecode Java 8).
- **Windows** es el entorno activo del proyecto (también funciona en otros SO).
- `app/google-services.json` válido (Firebase). Sin este archivo el build falla por el plugin `com.google.gms.google-services`. Pedilo a quien administre el proyecto Firebase.

## 2. Abrir y correr

1. Android Studio → **Open** → elegí la carpeta `App Traidores`.
2. Esperá la sincronización de Gradle (wrapper 8.5, AGP 8.1.4).
3. Ejecutá en emulador o dispositivo (minSdk 24).

> El validador de apariencia/compilación es el desarrollador en Android Studio: según `CLAUDE.md`, **no se ejecutan compilaciones automáticas** desde el flujo asistido.

## 3. Modelo mental del proyecto

- Un módulo `:app`, un paquete `com.traidores.juego`.
- Navegación por `Intent` explícitos entre Activities (no Navigation Component).
- El estado de partida es **`GameSession` inmutable** (`Serializable`) que viaja por `Intent`.
- Las reglas viven en `GameEngine`; los modelos en `GameModels.kt`; los roles en `RoleCatalog.kt`.
- La IA de bots es `LocalBotAi` (conversacional, ~3.300 líneas).
- El online usa Firebase Firestore (colección `partidas`) y es **experimental**.

Leé en este orden:
1. [general/03-arquitectura.md](../general/03-arquitectura.md)
2. [general/02-mecanicas.md](../general/02-mecanicas.md)
3. [general/07-flujo-funcionamiento.md](../general/07-flujo-funcionamiento.md)
4. [`../../ESTADO_ACTUAL.md`](../../ESTADO_ACTUAL.md) (auditoría por subsistema con archivo:línea)

## 4. Archivos clave y su tamaño (cuidado)

| Archivo | Aprox. | Por qué importa |
|---|---|---|
| `GameplayMockActivity.kt` | ~6.300 líneas | Pantalla de partida: UI, fases, timers, chat, online, animaciones. **El cambio más riesgoso.** |
| `LocalBotAi.kt` | ~3.300 líneas | IA conversacional de bots. |
| `LobbyActivity.kt` | ~2.400 líneas | Configuración de sala + flujo online. |
| `GameEngine.kt` | ~2.000 líneas | Reglas y resolución de fases. |
| `GameModels.kt` | ~770 líneas | Modelos + `LocalGameFactory` + `GameRules`. |
| `RoleCatalog.kt` | ~360 líneas | Catálogo de roles. |

Antes de tocar `GameplayMockActivity` o `LobbyActivity`, identificá la sección exacta y preferí refactorizaciones pequeñas y justificadas (es una restricción del proyecto: estabilizar, no reescribir).

## 5. Cómo correr los tests

```bash
./gradlew test          # Linux/macOS
gradlew.bat test        # Windows
```

Los tests JVM cubren `GameEngine`, countdown, feedback state, table UI/layout, role catalog y la infraestructura online (gates, resolver, mapper, sync watchdog, etc.). No hay tests de UI/instrumentación.

## 6. Tareas comunes

### Agregar/ajustar un rol
1. Definir clave y `RoleDefinition` en `RoleCatalog.kt` (equipo, mínimo, mapa exclusivo, función).
2. Si tiene acción nocturna, agregar fase en `GamePhase` y resolución en `GameEngine`.
3. Agregar a `LocalGameFactory.editableRoleKeys()` y a la composición si corresponde.
4. Agregar assets `rol_<key>_<suffix>` por mapa.
5. Cubrir con tests en `GameEngineTest`/`RoleCatalogTest`.

### Cambiar tiempos o composición por defecto
- Tiempos: `GameTimingConfig`/`GameTimingPreset` en `GameModels.kt`.
- Composición: `LocalGameFactory.roleCompositionPreset` / `normalizedRoleComposition`.

### Trabajar en el online
- Leé primero [`../firebase-online-schema.md`](../firebase-online-schema.md) (contrato Firestore vigente).
- Los nombres de campo son constantes en `OnlineRoomFirestore` (Firestore usa español: `codigoSala`, `jugadoresEsperados`...).
- Si tocás la forma de un documento, actualizá también `firestore.rules` (validan `hasOnly`/`hasAll` y tamaños).

## 7. Persistencia y preferencias

- Namespace `SharedPreferences`: `TraidoresPrefs`.
- Constantes de preferencias en `OpcionesActivity`, `ProfileActivity`, `AudioPreferences`, `BaseActivity`, `PlayerPublicIdentity`, `OnlineRoomRecovery`, `OnlineTempIdentity`.
- Ver [facultad/09-diccionario-datos.md](../facultad/09-diccionario-datos.md) para el listado completo de claves.

## 8. Flujo de trabajo (GSD)

`CLAUDE.md` define un flujo GSD: para cambios pequeños `/gsd-quick`, investigación `/gsd-debug`, trabajo planificado `/gsd-execute-phase`. Los artefactos viven en `.planning/`.

## 9. Convenciones

Resumen en [general/06-convenciones-codigo.md](../general/06-convenciones-codigo.md). Lo esencial: Kotlin official style, 4 espacios, trailing commas, estado inmutable con `copy()`, guard clauses, español en el dominio, y mover texto a recursos cuando toques una pantalla.
