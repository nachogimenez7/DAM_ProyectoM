# Spec — Correcciones ronda 3: ayuda de testing + hallazgos del code review

> Handoff Claude (diseño/review) → Codex (implementación). Continuación de `saltar-noche-y-reveal-sin-muertes.md` y `saltar-noche-y-reveal-sin-muertes-correcciones.md`. Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.

**Contexto:** el bug del salto de noche sin tap quedó resuelto (mecanismo de armado de 650ms confirmado en el código, con limpieza correcta en `onPause`/`onDestroy`). Se hizo un code review completo de todo el trabajo de las últimas rondas (saltar noche, reveal "nadie murió", tests del motor, chat rediseñado) con 8 ángulos de revisión + verificación directa en código. Esta spec junta dos cosas: (1) una ayuda de testing temporal que pidió el usuario, (2) los hallazgos confirmados del review.

---

## 1. Ayuda de testing — asesinos apuntan siempre al jugador humano (solo en Modo Test Rápido)

### Motivo
El usuario quiere poder ver el reveal "nadie murió" de forma confiable sin tener que adivinar a quién van a atacar los bots asesinos cada noche (hoy el objetivo se calcula con `nightPressureScore` + ruido determinístico, distinto en cada partida). Si el Médico puede saber con certeza que el asesino apunta al jugador humano, alcanza con auto-protegerse siempre para forzar "nadie murió" de forma repetible.

### Dónde y cómo
`LocalBotAi.chooseAssassinTarget(session, assassin)` (`LocalBotAi.kt:229-241`) ya recibe `session: GameSession` como parámetro, y `GameSession` ya tiene un campo `quickTestMode: Boolean` (`GameModels.kt:15`) — el toggle "MODO TEST RAPIDO" que ya existe en Lobby → Opciones avanzadas.

**Cambio propuesto:** al principio de `chooseAssassinTarget`, si `session.quickTestMode` es `true`, intentar devolver directamente el nombre del jugador humano (`GameEngine.humanPlayer(session).name`), **siempre que siga siendo un objetivo válido** según el guard que ya existe (`GameEngine.isValidKillTarget(session, humanName, assassin)` — para no romper la regla de que un asesino no puede matar a otro traidor/compañero, ni a un jugador ya muerto). Si el humano no es un objetivo válido en ese momento (por ejemplo, si el propio humano es el Asesino), caer al comportamiento normal existente (el `sortedWith(...)` que ya está).

```kotlin
fun chooseAssassinTarget(session: GameSession, assassin: GamePlayer): String {
    val candidates = GameEngine.alivePlayers(session)
        .filter { GameEngine.isValidKillTarget(session, it.name, assassin) }
    if (session.quickTestMode) {
        val human = GameEngine.humanPlayer(session)
        if (candidates.any { it.name == human.name }) return human.name
    }
    return candidates
        .sortedWith(
            compareByDescending<GamePlayer> { nightPressureScore(session, it) }
                .thenBy { stableNoise("${session.code}:${session.round}:${assassin.name}:${it.name}:kill") }
                .thenBy { it.name }
        )
        .firstOrNull()
        ?.name
        .orEmpty()
}
```

### Por qué esta forma y no otra
- **Cero riesgo para partidas reales**: `quickTestMode` es `false` por defecto (`GameModels.kt:423,447`) y solo se activa a propósito desde un menú de opciones avanzadas ya existente — no hay forma de que esto se filtre a una partida normal sin que el usuario lo prenda deliberadamente.
- **Reversible en una línea**: si más adelante se quiere sacar esta ayuda, alcanza con borrar el bloque `if (session.quickTestMode) { ... }`.
- **No es una regla de juego nueva** en el sentido que preocupa al CLAUDE.md ("no agregar funciones nuevas") — es una ayuda de testing acotada a un modo de desarrollo que ya existe, no un cambio de comportamiento del juego real.
- Respeta el guard existente (`isValidKillTarget`), así que no puede violar ninguna invariante (matar compañero de equipo, objetivo muerto, etc.).

---

## 2. Hallazgos del code review — 7 confirmados

### 🔴 2.1 — El anillo y los rayos del reveal "nadie murió" nunca se ven (prioridad máxima)

`app/src/main/java/com/traidores/juego/NoDeathRevealAnimator.kt` — `sunRing`, `rayTop`, `rayBottom`, `rayLeft`, `rayRight` tienen `android:visibility="gone"` en `activity_gameplay_mock.xml` (líneas 640-686), pero el animador (`start()`/`resetViews()`) solo anima su `alpha`/escala/traslación — nunca los pasa a `View.VISIBLE`. Una vista `GONE` no se mide ni se dibuja sin importar su alpha, así que hoy solo se ve el círculo del sol apareciendo y "respirando"; el anillo y los cuatro rayos (que sí están coreografiados con su propio movimiento de entrada) quedan invisibles siempre.

**Fix:** en `resetViews()` (o al principio de `start()`), agregar `sunRing.visibility = View.VISIBLE` y lo mismo para los 4 rayos, antes de animarles el alpha desde 0.

### 🟡 2.2 — El pulido fino del chat no llega al layout landscape real

Confirmado con un diff directo entre `app/src/main/res/layout/gameplay_table_section.xml` y `app/src/main/res/layout-land/gameplay_table_section.xml`: el tamaño del panel y el fondo sí se aplican en cualquier orientación (se fijan por código Kotlin en `GameplayChatController.applyChatPanelDimensions()`/`renderChatBackgrounds()`, independientes del XML). Pero el ajuste de padding del header (`9dp/7dp` en el layout por defecto vs `11dp/9dp` en `-land`) y un cambio de orientación de una fila interna (vertical→horizontal) solo se editaron en el archivo por defecto (portrait). Gameplay es landscape-only, así que Android infla `-land`, y ese pulido específico no se ve en la pantalla real de juego.

**Fix:** replicar los mismos valores de padding/orientación que se ajustaron en `gameplay_table_section.xml` (líneas ~9-17, zona del header del chat) también en `gameplay_table_section.xml` de `layout-land/`.

### 🟡 2.3 — Nuevas dimensiones fijas violan una regla ya escrita del proyecto

`CLAUDE.md:166` dice textualmente: *"Avoid adding fixed widths/heights to `activity_gameplay_mock.xml`; it already contains 99 fixed dimensions."* El nuevo bloque del reveal "nadie murió" (`activity_gameplay_mock.xml`, desde línea 622) suma ~14 dimensiones fijas nuevas (`214dp`/`164dp`, `158dp`/`158dp`, `112dp`/`112dp`, rayos de `5dp`/`42dp`, etc.) a un archivo que el proyecto ya marcó como sobrecargado de esto.

**Fix:** no es necesario reescribir todo a `wrap_content`/constraints (el panel es un overlay estático, no necesita ser tan flexible), pero si es razonable, evaluar si algunas de esas dimensiones pueden expresarse de forma relativa entre sí (por ejemplo, el ancho/alto del `FrameLayout` contenedor en función del tamaño del sol) en vez de sumar todas como valores fijos independientes. Si no es práctico, al menos dejarlo anotado como excepción consciente, no silenciosa.

### 🟡 2.4 — Detección de "nadie murió" depende de un texto exacto

`GameplayTableUi.kt:469-471`: `wasNoDeathAtDawn()` busca el substring `"amanecer: no murio nadie"` dentro de `session.publicAnnouncement`, en vez de depender de un dato de estado estructurado. Funciona hoy, pero es frágil: si en el futuro se reformula ese mensaje (puntuación, tildes, orden), el reveal deja de dispararse en silencio, sin error de compilación ni test que lo detecte fuera del único assert que verifica ese string exacto.

**Fix sugerido (no urgente, pero documentarlo):** agregar un campo explícito a `GameSession` (por ejemplo `nightHadNoVictim: Boolean`) seteado por `GameEngine.resolveDawn()` junto con el texto narrativo, y que `wasNoDeathAtDawn()` lea ese campo en vez de parsear el string. Es un cambio más prolijo pero no bloqueante — se puede dejar para una ronda futura si se prefiere no tocar `GameEngine.kt` ahora.

### 🟡 2.5 — Código duplicado: resolución de noche online no usa el helper nuevo

`GameplayMockActivity.kt:3736-3749`: `resolveOnlineNightWindow()` mantiene su propio `when` copiado a mano para resolver las 5 sub-fases nocturnas, en vez de reusar `advanceNightSessionWithoutRendering()` (el helper que se creó específicamente para el salto de noche, en la ronda anterior). Ahora hay dos copias independientes de la misma lógica en el mismo archivo — un cambio futuro en las sub-fases nocturnas tiene que aplicarse en los dos lugares.

**Fix:** reemplazar el cuerpo del `while` en `resolveOnlineNightWindow()` para que llame a `advanceNightSessionWithoutRendering(resolved)` en vez de repetir el `when`.

### 🟡 2.6 — Se perdió la diferenciación visual del chat por mapa (fuera de lo pedido)

`GameplayChatController.kt:1267-1272`: `renderChatBackgrounds()` ahora usa siempre `bg_chat_frame_thin` sin importar `session.mapKey`. Confirmado que `bg_chat_box_grecia.xml`, `bg_chat_box_medieval.xml` y `bg_chat_box_pampa.xml` quedaron completamente huérfanos (sin ninguna referencia en el repo). El pedido original era "un marco nuevo, más fino" — no se pidió explícitamente perder la identidad visual distinta por mapa que ya existía para el chat.

**Decisión a confirmar con el usuario antes de tocar esto:** ¿el marco fino nuevo debería tener una variante de color/detalle sutil por mapa (como los demás paneles), o está bien que sea uno solo genérico? Si se decide que sí debe variar por mapa, extender `bg_chat_frame_thin` a 3 variantes de color y volver a ramificar por `mapKey`. Si se decide que un solo marco genérico está bien, borrar los 3 drawables huérfanos (`bg_chat_box_*`) para no dejar recursos muertos.

### 🟢 2.7 — Detalle menor de eficiencia (opcional, no urgente)

`GameplayMockActivity.kt:4394`: `isNightSkipButtonReady()` reprograma el `Runnable` de armado (`removeCallbacks` + `postDelayed`) en cada render mientras el delay de 650ms no venció, incluso si nada cambió desde el render anterior. No es grave, pero es trabajo de `Handler` evitable. Si hay tiempo, agregar un chequeo de "ya está programado, no reprogramar" antes de repetir `postDelayed`.

---

## Resumen de archivos a tocar

- `app/src/main/java/com/traidores/juego/LocalBotAi.kt` — ayuda de testing en `chooseAssassinTarget` (sección 1).
- `app/src/main/java/com/traidores/juego/NoDeathRevealAnimator.kt` — fix de visibilidad del anillo/rayos (2.1, **prioridad máxima**).
- `app/src/main/res/layout-land/gameplay_table_section.xml` — replicar padding/orientación del header de chat (2.2).
- `app/src/main/res/layout/activity_gameplay_mock.xml` — revisar dimensiones fijas del bloque nuevo (2.3, opcional/best-effort).
- `app/src/main/java/com/traidores/juego/GameplayTableUi.kt` — nota documentada, fix opcional para más adelante (2.4).
- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — consolidar `resolveOnlineNightWindow()` (2.5), y opcionalmente el detalle de 2.7.
- `app/src/main/java/com/traidores/juego/GameplayChatController.kt` — decisión pendiente sobre variantes por mapa (2.6, confirmar antes de tocar).

## Orden sugerido
1. **2.1 primero** (el reveal se ve roto hoy, es lo más visible).
2. 1 (ayuda de testing) — para que el usuario pueda seguir probando cómodo mientras se hace el resto.
3. 2.2, 2.3, 2.5 (correcciones técnicas, bajo riesgo).
4. 2.6 — preguntar antes de tocar (hay una decisión de producto pendiente).
5. 2.4, 2.7 — quedan para cuando haya tiempo, no son bloqueantes.
