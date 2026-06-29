# Spec — Feed unificado (cronista): chat + eventos en una ventana central

> Handoff Claude (diseño/review) → Codex (implementación). El código es la fuente de verdad. Codex arranca en frío: leer todo.

## Objetivo
Unificar el **chat de jugadores** y los **eventos de "Dios"** (muertes, votos, resultados de fase) en **una sola ventana central temática por mapa**, con un **stream cronológico único**: las líneas de Dios aparecen como **banners destacados** intercaladas con el chat de los jugadores (cada uno con su color). Un toggle **"Todo / Solo sucesos"** permite el vistazo rápido del historial.

Esto logra tres cosas a la vez:
1. La ventana central crece (al quitar el panel EVENTOS de arriba se libera espacio).
2. Inmersión: la muerte/expulsión aparece dentro de la conversación, donde ya están mirando.
3. Avanza el punto 2: **extrae el event log** del monolito hacia el controller del chat (que pasa a ser el "cronista/feed").

Decisión de diseño tomada: **stream único + filtro** (no pestañas).

## Estado actual relevante (no romper)
- El chat ya está en `GameplayChatController` (extraído). Esta tarea lo **extiende** para que también maneje los eventos. (Rename opcional a `GameplayFeedController` — NO obligatorio; evitar churn si genera riesgo.)
- Event log hoy en `GameplayMockActivity`: vistas `eventLog*` (panel superior `eventLogPanel`, `eventLogSummary`, `eventLogContainer`, etc.), métodos `renderEventLog`, `renderEventLogPanel`, `toggleEventLog`, alimentado por `session.publicHistory`.
- **Modelo de datos actual (clave):**
  - `GameEngine.withPublicHistory(msg)` → `publicHistory` (cap 8) + `godHistory` (cap 32). Eventos como **strings**. NO van a `chatHistory`.
  - `GameEngine.withChatMessage(speaker, msg, isGod=false)` → `chatHistory` (cap 40, `List<GameChatMessage>`). Hoy nunca se usa `isGod=true`.
  - `GameChatMessage(speaker, message, isGod)` ya tiene el flag `isGod`.
  - Online: el chat del jugador se reconstruye desde Firestore `partidas/{id}/chat`; los eventos llegan por `estadoPartida` (el mapper setea `publicHistory`/`godHistory` desde el estado autoritativo, ver `publicHistoryFromAuthoritativeState`).

---

## Diseño: `chatHistory` como fuente canónica del feed

**`chatHistory` pasa a ser el stream unificado** (jugadores + Dios). El feed se renderiza desde `chatHistory`: `isGod=true` → banner de suceso; `isGod=false` → línea de jugador con su color (`PlayerChatColor`). Filtro "Solo sucesos" = `chatHistory.filter { it.isGod }`.

### Cambios por capa

**1. GameEngine (local) — rutear eventos a `chatHistory`**
- En `withPublicHistory(message)`, además de `publicHistory`/`godHistory`, **agregar también** un `GameChatMessage("Dios", message, isGod = true)` a `chatHistory` (reutilizando `withChatMessage(..., isGod = true)`).
- Subir el cap de `chatHistory` (p. ej. 40 → 60) para que los eventos no expulsen el chat reciente.
- Mantener `publicHistory`/`godHistory` por compatibilidad (online y posibles tests) por ahora.
- Es un cambio centralizado y testeable. **Correr y ajustar los tests** de `GameEngineTest` que asuman tamaños de `chatHistory`/`publicHistory`.

**2. Online — eventos en el stream mostrado**
- Al aplicar estado autoritativo, las **líneas nuevas** de Dios (de `publicHistory`/`godHistory` del estado) deben **aparecer en el `chatHistory` mostrado** como `GameChatMessage("Dios", ..., isGod=true)`, **deduplicadas** (no re-insertar líneas ya presentes).
- Ordenamiento online: el chat de jugadores trae timestamp (`creadaEnLocal`/`creadaEn`); los eventos llegan en transiciones de fase. Es aceptable **insertar el evento cuando llega** (orden "cuando sucede"). No hace falta interleave perfecto por reloj en esta iteración.
- ⚠️ **Punto delicado y de revisión:** la deduplicación de eventos online y que no se dupliquen al reaplicar estados. Mantener el patrón existente (los invitados aplican estados nuevos e ignoran viejos/duplicados).

**3. `GameplayChatController` — render del feed unificado**
- Renderizar el feed desde `chatHistory` (ya no `filterNot { isGod }`): 
  - **Dios (`isGod`)**: línea tipo **banner** ancho, estilo destacado (dorado/pergamino, con "✦" o ícono). Opcional: tono según el suceso (muerte → rojizo) reutilizando la paleta de tonos.
  - **Jugador**: línea con nombre en su color (`PlayerChatColor`) + texto.
- **Feed ambiental (colapsado):** últimas N entradas del stream **mezclado** (eventos + chat) → una muerte "flashea" aunque el chat esté colapsado.
- **Expandido:** scroll completo + composer + **toggle "Todo / Solo sucesos"** (default "Todo"; estado del toggle se conserva mientras dure la pantalla). 
- Conservar todo lo ya logrado (apertura/cierre, teclado/IME, no-leídos, cooldown online, eliminado/silenciado solo-lectura).

**4. Layout — quitar EVENTOS de arriba y agrandar el centro**
- Eliminar/retirar el panel `eventLogPanel` (y su lógica `renderEventLog*`/`toggleEventLog` del Activity; su rol lo cumple ahora el feed).
- La ventana central sube: reducir `layout_marginTop` del contenedor central (hoy ~128dp pensado para dejar lugar al EVENTOS) a quedar justo bajo la **barra superior slim** (fase + timer + botones chat/ajustes). Aprovechar el espacio liberado hacia arriba (lo que pediste).
- La **barra superior** (DIA 1 + subtítulo + timer + botones) **se queda** como HUD; NO se fusiona al feed. (La idea de "consejos/tips" en esa barra es una mejora futura aparte.)
- Fondo temático por mapa: cuadro programático (sin PNG) aplicado a esta ventana más grande:
  - Grecia: panel claro tipo **mármol** con borde fino dorado.
  - Medieval: panel **madera** oscura, borde, y acento de **sello de cera roja** (círculo rojo pequeño en una esquina, con shape).
  - Pampa: panel **cuero/marrón cálido**.
  - Selección por `session.mapKey`; translúcido (el mapa se ve atenuado) pero con contraste para legibilidad.

**5. Color de identidad único (incluye nombres de cartas)**
- `PlayerChatColor` es la **única fuente de color de identidad**: aplicarlo al nombre de jugador en el feed (líneas de chat) **y** al **nombre debajo de cada carta lateral** (`holder.name` en `GameplayMockActivity`), para que el color sea consistente carta ↔ feed.
- Cuidar legibilidad del nombre sobre la carta (sombra/contraste si hace falta).

### Estados a cuidar (verificación manual en Android Studio)
- Local: muertes/votos/resultados aparecen como banners en el feed, en orden, intercalados con el chat de bots.
- Online: eventos aparecen en el feed sin duplicarse al reaplicar estados; chat sigue llegando.
- Toggle "Solo sucesos" muestra solo banners de Dios; "Todo" vuelve al stream completo.
- Feed ambiental muestra mezcla (una muerte reciente se ve colapsada).
- Chat expandido + teclado (regresión histórica).
- Eliminado/silenciado: solo-lectura con hint; igual ve el feed.
- Filtro y scroll: si estás leyendo arriba y llega algo, no saltar bruscamente al fondo (reusar la lógica de "mensajes nuevos").

---

## Fuera de alcance (futuro)
- "Consejos/tips" contextuales en la barra superior (por fase/rol).
- Reveals sincronizados entre teléfonos; sonido/háptica en los banners de muerte/expulsión (gran oportunidad de inmersión, pero aparte).
- Arte PNG real para la ventana temática (hoy programático).
- Iteración B: selector de color en lobby (`GamePlayer.color`, paleta, sync Firestore).

## Regla de proceso
- Diff acotado a esta tarea. No mezclar cambios visuales no pedidos.
- Refactor pequeño/justificado; el usuario valida compilación y apariencia en Android Studio.

## Documentación a actualizar al cerrar (lo hace Claude tras revisar)
- `docs/general/05-estructura-proyecto.md`, `docs/general/03-arquitectura.md`, `docs/general/07-flujo-funcionamiento.md`.
- `docs/desarrollo/decisiones-arquitectura.md` (ADR: feed unificado / `chatHistory` como stream canónico + extracción event log).
- `docs/desarrollo/backlog.md` (avance D1; el EVENTOS dejó de ser panel propio).
