# Arquitectura del sistema

## Visión general

App Android nativa, **un único módulo `:app`**, un único paquete `com.traidores.juego`. No usa Navigation Component: la navegación es por **`Intent` explícitos** entre Activities. El estado de partida es un objeto **`Serializable` inmutable** (`GameSession`) que viaja por `Intent`/`Bundle` y, en online, se reconstruye desde Firestore.

No hay framework de inyección de dependencias, ni patrón MVVM/MVI formal, ni capa de repositorio genérica. La arquitectura es **Activities + objetos/clases helper** (muchos `object` Kotlin sin estado).

## Capas lógicas

Aunque no hay módulos físicos separados, se distinguen estas capas por responsabilidad:

### 1. Capa de presentación (Activities)
Inflan XML, enlazan vistas, manejan clicks, muestran diálogos y renderizan estado.
- `MainActivity`, `JugarActivity`, `LocalModeActivity`, `OnlineModeActivity`, `LobbyBrowserActivity`, `LobbyActivity`, `AssigningRolesActivity`, `GameplayMockActivity`, `RolesActivity`, `AyudaActivity`, `OpcionesActivity`, `ProfileActivity`, `ProfileSelectionActivity`.
- `BaseActivity` reporta el ciclo de vida a `MusicManager`; todas las Activities están fijadas en vertical desde el manifest.
- **Riesgo conocido:** `GameplayMockActivity.kt` (~6.300 líneas) y `LobbyActivity.kt` (~2.400 líneas) concentran navegación, render, animación, timers, chat, sincronización online y mutación de estado.

### 2. Capa de presentación de datos / renderizado
Convierte la sesión en datos listos para UI y aísla animaciones.
- `GameplayTableUi.kt`, `GameTableLayout.kt`, `WinnerResultsRenderer.kt`.
- `GameplayChatController.kt` maneja el chat de gameplay: feed ambiental central, panel expandido, teclado, mensajes locales/online y reacciones de bots.
- Animadores: `DayNightTransitionAnimator`, `DeathRevealAnimator`, `SilenceRevealAnimator`, `TraitorRevealAnimator`, `JesterVictoryAnimator`, `VoteResultAnimator`, `RolePreviewAnimator`.
- Estado de feedback/timers: `GameplayFeedbackState`, `GameplayCountdown`, `GameplayEffects`, `GameplaySoundEffects`.

### 3. Capa de dominio / reglas
Modela jugadores, fases, acciones, roles y resuelve las reglas.
- `GameModels.kt` — modelos serializables + `GameRules` + `LocalGameFactory`.
- `GameEngine.kt` — resolvedor autoritativo de fases, votos, acciones nocturnas, desempates y condiciones de victoria.
- `RoleCatalog.kt` — catálogo central de roles (nombres, equipos, mínimos, mapa, descripciones, historias).
- `LocalBotAi.kt` — IA conversacional de bots (~3.300 líneas): personalidades, memoria de conversación, planes de voto, lectura de relaciones, detección de intención.

### 4. Capa online (Firebase Firestore)
Coordina salas, presencia y sincronización autoritativa entre dispositivos.
- `OnlineRoomFirestore` (operaciones CRUD de sala/jugador), `OnlineModeActivity`, `LobbyBrowserActivity`.
- Gates y resolvedores: `OnlineStartupGate`, `OnlinePhaseGate`, `OnlineRecoveryGate`, `OnlineActionResolver`, `OnlineAuthoritativeStateMapper`, `OnlineSyncWatchdog`, `OnlineMatchSessionBuilder`, `OnlineLobbyRules`.
- Identidad/recuperación: `OnlineTempIdentity`, `PlayerPublicIdentity`, `OnlineRoomRecovery`, `RoomDisplayNames`, `PlayerPublicIdentity`.
- Mensajería/depuración: `OnlineErrorMessages`, `OnlineDebugLog`.

### 5. Capa de plataforma/servicios
- `MusicManager`, `AudioPreferences` (música/efectos), `SharedPreferences` (preferencias y perfil), recursos empaquetados.

## Flujo de datos

- `GameSession` es **inmutable**: cada acción produce una copia vía `copy()`.
- `GameEngine` recibe una sesión y devuelve una nueva sesión (estilo funcional/reductor).
- En **local**, el host de la lógica es el propio dispositivo: `GameEngine` resuelve y la Activity renderiza.
- En **online**, el **host activo** resuelve fases y publica el estado autoritativo en `estadoPartida`; los invitados solo registran acciones/votos y aplican el estado recibido. Detalle en [`firebase-online-schema.md`](../firebase-online-schema.md).
- Persistencia de estado de Activity vía `onSaveInstanceState` (sobre todo en gameplay y perfil).

## Modelo de persistencia

Dos mecanismos, **ninguno relacional**:

1. **Firestore (NoSQL documental)** — salas online, jugadores, acciones, chat, IDs públicos y perfiles públicos. Colección raíz: `partidas`. Ver diccionario y modelo en [`facultad/08-der-modelo-relacional.md`](../facultad/08-der-modelo-relacional.md).
2. **SharedPreferences (clave-valor local)** — namespace `TraidoresPrefs`: preferencias de audio/idioma/vibración, perfil del jugador, ID público local, datos de recuperación de sala online.

> Implicación para la facultad: el proyecto **no usa una base de datos relacional**. El DER/Modelo Relacional se construye como un **diseño propuesto y normalizado** derivado del dominio, separado del modelo NoSQL real. Ver [08-der-modelo-relacional.md](../facultad/08-der-modelo-relacional.md).

## Manejo de errores

- Guard clauses (early returns) ante estado o acción inválidos.
- `Toast` para feedback recuperable; `AlertDialog` para confirmaciones y valores editables.
- Fallbacks seguros cuando falta un extra de `Intent` o un recurso drawable (`resources.getIdentifier()` con placeholder).
- Reglas de Firestore (`firestore.rules`) validan forma y tamaño de los documentos.
- No hay manejador global de excepciones, pantalla de error ni logging estructurado (existe `OnlineDebugLog` para depuración puntual).

## Diagrama de componentes (alto nivel)

```
┌─────────────────────────────────────────────────────────────┐
│                       Activities (UI)                        │
│  Main · Jugar · LocalMode · OnlineMode · LobbyBrowser        │
│  Lobby · AssigningRoles · GameplayMock · Roles · Ayuda       │
│  Opciones · Profile · ProfileSelection                       │
└───────────────┬───────────────────────────┬─────────────────┘
                │                            │
   ┌────────────▼───────────┐    ┌───────────▼────────────────┐
   │  Render / Animación     │    │  Online (Firestore)         │
   │  GameplayTableUi        │    │  OnlineRoomFirestore        │
   │  *Animator · Countdown  │    │  *Gate · *Resolver          │
   │  WinnerResultsRenderer  │    │  SyncWatchdog · SessionBld  │
   └────────────┬───────────┘    └───────────┬────────────────┘
                │                            │
   ┌────────────▼────────────────────────────▼────────────────┐
   │                   Dominio / Reglas                        │
   │   GameModels · GameRules · LocalGameFactory               │
   │   GameEngine · RoleCatalog · LocalBotAi                   │
   └────────────┬─────────────────────────────┬───────────────┘
                │                             │
   ┌────────────▼──────────┐     ┌────────────▼────────────────┐
   │  SharedPreferences     │     │   Firebase Firestore        │
   │  (TraidoresPrefs)      │     │   (colección `partidas`)    │
   └────────────────────────┘     └─────────────────────────────┘
```
