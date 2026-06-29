# Estructura del proyecto

> Corrige y reemplaza a [`../project-structure.md`](../project-structure.md), que menciona archivos inexistentes (`PlayerProfileStore.kt`, `OnlineLobbyModels.kt`, `OnlineLobbyStore.kt`) y describe el online como simulado.

## Raíz del repositorio

```
App Traidores/
├── app/                       Módulo Android principal
├── docs/                      Documentación (este árbol)
├── gradle/ · gradlew · gradlew.bat   Wrapper de Gradle
├── build.gradle · settings.gradle · gradle.properties  Configuración
├── firebase.json              Apunta a firestore.rules
├── firestore.rules            Reglas de seguridad de Firestore
├── CLAUDE.md                  Instrucciones de proyecto para asistentes IA
├── ESTADO_ACTUAL.md           Auditoría técnica basada en código
├── README.md                  Entrada rápida
├── roles_gauchos/ · roles_griegos/ · roles_medievales/   Arte fuente de roles
├── .planning/                 Artefactos del flujo de trabajo GSD
└── tmp/ · *.png · *.xml (raíz)  Previews y archivos temporales (no requeridos para compilar)
```

> `app/google-services.json` es requerido en tiempo de compilación/ejecución por el plugin de Google Services y está gitignored.

## Código principal

Ruta: `app/src/main/java/com/traidores/juego/` (~60 archivos Kotlin).

### Activities (13)
| Archivo | Responsabilidad | Orientación (manifest) |
|---|---|---|
| `MainActivity.kt` | Menú principal | portrait |
| `JugarActivity.kt` | Selector local/online | portrait |
| `LocalModeActivity.kt` | Crea sesión local → lobby | portrait |
| `OnlineModeActivity.kt` | Crea/busca salas Firestore | portrait |
| `LobbyBrowserActivity.kt` | Lista salas online | unspecified |
| `LobbyActivity.kt` | Configuración de sala, jugadores, mapa, tiempos, roles | unspecified |
| `AssigningRolesActivity.kt` | Pantalla intermedia de reparto/lectura | unspecified |
| `GameplayMockActivity.kt` | Pantalla principal de partida (local y online) | unspecified, `adjustResize` |
| `RolesActivity.kt` | Guía/listado de roles | portrait |
| `AyudaActivity.kt` | Ayuda/tutorial | portrait |
| `OpcionesActivity.kt` | Preferencias (audio, idioma, vibración, texto, smoke test Firebase) | portrait |
| `ProfileActivity.kt` | Perfil del jugador (nombre, avatar, banner, rol favorito, bio) | portrait |
| `ProfileSelectionActivity.kt` | Selección de avatar/banner/rol | portrait |

> Las pantallas de juego declaran `screenOrientation="unspecified"`; `BaseActivity` aplica una preferencia de orientación (vertical/horizontal) sólo a Lobby, LobbyBrowser, AssigningRoles y Gameplay. **No están fijadas a landscape en el manifest.**

### Dominio y reglas
- `GameModels.kt` — `GameSession`, `GamePlayer`, `GameRole`, `GamePhase`, enums, configs (`GameTimingConfig`, `RoleRevealConfig`, `RoleCompositionConfig`), `RoleRevealGate`, `GameRules`, `LocalGameFactory`, `GameMap`.
- `GameEngine.kt` — resolución de fases, votos, acciones nocturnas, desempates, victorias.
- `RoleCatalog.kt` — catálogo de roles, `RoleMap`, `RoleDefinition`, nombres/historias por mapa.
- `LocalBotAi.kt` — IA conversacional de bots.
- `Role.kt`, `RoleListItem.kt`, `MapInfo.kt` — modelos auxiliares de presentación.

### Presentación / render
- `GameplayTableUi.kt`, `GameTableLayout.kt`, `WinnerResultsRenderer.kt`.
- `GameplayChatController.kt` - controlador del chat de gameplay (feed ambiental, panel expandido, teclado, mensajes online/locales).
- `PlayerChatColor.kt` - color deterministico por jugador para chat y usos visuales futuros.
- Animadores: `DayNightTransitionAnimator.kt`, `DeathRevealAnimator.kt`, `SilenceRevealAnimator.kt`, `TraitorRevealAnimator.kt`, `JesterVictoryAnimator.kt`, `VoteResultAnimator.kt`, `RolePreviewAnimator.kt`.
- Estado/efectos: `GameplayFeedbackState.kt`, `GameplayCountdown.kt`, `GameplayEffects.kt`, `GameplaySoundEffects.kt`.

### Online (Firestore)
- `OnlineRoomFirestore.kt`, `OnlineMatchSessionBuilder.kt`, `OnlineLobbyRules.kt`.
- Gates: `OnlineStartupGate.kt`, `OnlinePhaseGate.kt`, `OnlineRecoveryGate.kt`.
- Sincronización: `OnlineActionResolver.kt`, `OnlineAuthoritativeStateMapper.kt`, `OnlineSyncWatchdog.kt`.
- Identidad/recuperación: `OnlineTempIdentity.kt`, `PlayerPublicIdentity.kt`, `OnlineRoomRecovery.kt`, `RoomDisplayNames.kt`, `PlayerPublicIdentity.kt`.
- Soporte: `OnlineErrorMessages.kt`, `OnlineDebugLog.kt`.

### Perfil / adapters / UI común
- `ProfileCustomizationCatalog.kt`, `ProfileRoleCatalog.kt`, `ProfileSelectionAdapter.kt`.
- `RoleAdapter.kt`, `RoleDetailDialog.kt`, `TraidoresSwitchStyle.kt`.

### Plataforma
- `BaseActivity.kt`, `MusicManager.kt`, `AudioPreferences.kt`.

## Recursos Android

Ruta: `app/src/main/res/`
- `drawable/` y `drawable-nodpi/` — fondos día/noche por mapa, botones, cartas, iconos, efectos (silencio, sangre).
- `layout/` — ~19 layouts de Activities + diálogos + items de listas.
- `font/` — tipografías personalizadas.
- `raw/` — música y efectos de sonido.
- `values/` — `colors.xml`, `strings.xml` (subconjunto pequeño), `themes.xml` (estilos `BtnGold`, `BtnDark`), dimensiones.
- `mipmap/` — iconos de launcher.

## Tests

Ruta: `app/src/test/java/com/traidores/juego/` (~15 clases): motor, countdown, feedback state, table UI/layout, role catalog y la mayoría de la infraestructura online (action resolver, state mapper, lobby rules, session builder, gates, sync watchdog, room firestore).

## Criterio de reorganización futura

Para después de la presentación, separar por paquetes de responsabilidad (`engine/`, `model/`, `gameplay/`, `lobby/`, `audio/`, `profile/`, `online/`, `ui/common/`). Conviene hacerlo con tests y compilación porque mover clases Kotlin cambia paquetes e imports.
