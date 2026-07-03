# Spec — Segunda tanda para Codex: opción de debug + rediseño de la ventana de victoria

> Handoff Claude (diseño/review) → Codex (implementación). Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.

Este archivo agrupa **todo lo pendiente de enviar** (el brief `correcciones-testeo-ronda1.md` ya se envió por separado). Tres partes: (1) una opción de debug para poder testear roles que dependen de sobrevivir, (2) los ítems del checklist que quedaron sin poder probar, y (3) el rediseño de la ventana de victoria.

---

# PARTE 1 — Opción de debug: los bots no atacan al humano

**Motivo:** el usuario no puede testear roles que dependen de **sobrevivir** (Alcalde, Oráculo, Payador) ni observar el comportamiento de los bots a lo largo de la partida, porque el humano muere de noche o es votado de día muy temprano. Pidió una opción para que "los bots nunca me voten". Para que sirva de verdad (el Oráculo/Payador necesitan aguantar varias noches), la protección debe cubrir **también** la muerte nocturna, no solo el voto.

**Fix — nuevo toggle en Opciones Avanzadas** (mismo patrón que `quickTestMode`/`debugForceVoteTies`), ej. `debugBotsNeverTargetHuman` ("LOS BOTS NO ME ATACAN"):
- Cuando está activo, los bots asesinos **no eligen al humano** como víctima nocturna (excluirlo de candidatos en `LocalBotAi.chooseAssassinTarget`, `:229`).
- Los bots **no votan al humano** de día (excluirlo en `LocalBotAi.chooseVoteTarget` `:303` / `rankedPublicSuspects` `:3049`, y en el equivalente del desempate).
- Solo debug, `false` por defecto, solo modo local.

**Wiring (idéntico al patrón existente):**
- `GameModels.kt` — nuevo campo `val debugBotsNeverTargetHuman: Boolean = false` en `GameSession` (junto a `debugForceVoteTies`/`debugBotsObeyVoteCommands`, ~línea 18). Actualizar los factory que setean los otros debug flags (`GameModels.kt:424, 448`).
- `LobbyActivity.showAdvancedOptionsDialog()` — un `SwitchCompat` nuevo (mismo estilo que "FORZAR EMPATES") y agregarlo al `session.copy(...)` del commit (`LobbyActivity.kt:~2068`).
- `LocalBotAi` — consumir el flag en la elección de víctima (`chooseAssassinTarget`) y de voto (`chooseVoteTarget`/`rankedPublicSuspects` + desempate): si está activo, filtrar al humano de los candidatos. **Cuidado:** que al excluir al humano siempre quede al menos un candidato válido (si por reglas no quedara otro, caer al comportamiento normal para no romper la resolución).

---

# PARTE 2 — Ítems del checklist sin poder probar todavía

Estos dependen de que el humano sobreviva varias rondas / observe a los bots, así que **con la Parte 1 recién se van a poder testear**. No son bugs confirmados todavía — quedan pendientes de verificación:
- Los bots comentan los sucesos de forma coherente (sin repetir líneas idénticas, sin acusarse a sí mismos).
- Los bots reaccionan a los sucesos con **emotes** de forma coherente.

Si al observarlos con la protección activa aparece algo incoherente, se reporta como bug aparte. (La ronda anterior de IA de bots ya cubrió dedup de líneas y no-auto-acusación — ver `bots-ai-fixes.md` — así que esto es una re-verificación, no necesariamente hay bug.)

---

# PARTE 3 — Fondo oscuro que se sale del marco (TODAS las ventanas de anuncio)

**Reporte (captura):** en varias ventanas que anuncian algo (ej. "Juli FUE EXPULSADO" en Grecia), el fondo oscuro interno **se sale del marco** — sobresale por encima del ornamento superior y por los costados, sobre las columnas. El usuario lo quiere corregido en **TODAS** las ventanas de anuncio. (Esto **generaliza** el #6 de `correcciones-testeo-ronda1.md`, que era solo Grecia, a todas las ventanas.)

**Causa confirmada:** todas las ventanas de reveal (muerte, silencio, resultado de votación/expulsión, "nadie murió", info privada) usan el marco ornamental por mapa (`revealPanelBackgroundForMap`) como fondo del contenedor + un rectángulo oscuro interno `bg_reveal_text_shade` (`#A60B0B0E`, `match_parent`) para legibilidad del texto. Los insets (padding) que empujan el contenido adentro se setean **iguales para los 3 mapas** en `applyRevealOverlayTheme` (`GameplayMockActivity.kt:~3304-3310`; ej. `voteResultPanel.setPadding(dp(46), dp(60), dp(46), dp(42))`). El problema: el marco **griego** tiene columnas laterales anchas + un laurel superior, así que su "centro oscuro" visible es **más chico** que el de medieval/pampa — pero comparte los mismos insets. Entonces el rectángulo oscuro interno se extiende por encima de las columnas/laurel = "el fondo negro supera al marco".

**Fix:** hacer los insets **específicos por mapa** para las ventanas de reveal (el marco griego necesita insets más grandes —arriba y a los lados— que medieval/pampa), de modo que el fondo oscuro interno quede contenido dentro del centro visible de cada marco. Aplica a TODOS los paneles que usan `revealPanelBackgroundForMap` + `bg_reveal_text_shade`: `deathRevealContent`, `silenceRevealContent`, `voteResultPanel`, `noDeathRevealContent`, `privateFeedbackPanel`. Conviene centralizarlo (ej. un helper `revealInsetsForMap(mapKey)` en vez de repetir valores) y verificar los 3 mapas en Android Studio. Complemento posible: darle al `bg_reveal_text_shade` un margen propio para que nunca toque el borde decorativo, sin importar el mapa.

---

# PARTE 4 — Oráculo: sale "la voz vuelve" justo antes de la victoria

**Reporte:** probando el Oráculo, cuando estaba por usar el poder al final, justo ganaron los traidores — y aparecieron **dos ventanas seguidas**: primero la del Oráculo ("una voz vuelve...") y después la de victoria de los traidores. No debería mostrarse el evento intermedio cuando la partida ya terminó.

**Causa confirmada:** en `resumeGameFlowAfterBlockingUi()` (`GameplayMockActivity.kt`), el reveal de victoria (`maybeShowWinnerReveal()`) se chequea **último**, después de los reveals intermedios:
```
if (maybeShowNextDeathReveal()) return
if (maybeShowNoDeathReveal()) return
if (maybeShowNextSilenceReveal()) return
if (maybeShowOracleReveal()) return    // ← el evento del Oráculo sale acá
if (maybeShowTieVote()) return
if (maybeShowVoteResult()) return
if (maybeShowJesterVictory()) return
if (maybeShowWinnerReveal()) return    // ← la victoria, recién después
```
Cuando la partida se decide en el mismo amanecer que un evento del Oráculo, primero sale el evento y después la victoria.

**Fix:** cuando `session.winner.isNotBlank()`, priorizar la ventana de victoria y **saltear los reveals intermedios que ya no tienen sentido** (oráculo, "nadie murió", silencio, empate, resultado de votación). El reveal de **muerte** del golpe final se puede mantener antes de la victoria (para mostrar quién murió) o saltearlo también — a criterio; lo importante es que el evento del Oráculo ("la voz vuelve") NO aparezca con la partida ya terminada. Implementación simple: gatear los `maybeShow*` intermedios con `session.winner.isBlank()`, o mover `maybeShowWinnerReveal()` más arriba (justo después del death reveal).

---

# PARTE 5 — Frases fuera de época según el mapa (mate/gauchescas en Grecia y Medieval)

**Reporte:** aparecen frases sobre el mate (gauchescas) en los mapas griego y medieval. Deberían ser frases típicas de la época **según el mapa**, no genéricas.

**Causa confirmada:** `passiveNightMessage()` (`GameplayMockActivity.kt:4902-4911`) devuelve una de 5 frases nocturnas fijas, una gauchesca: *"Alguien se mueve en secreto. El mate queda frío y las sospechas calientes."* — usada **igual en los 3 mapas**.

**Fix:** hacer las frases de ambientación **por mapa**. Que `passiveNightMessage()` (y cualquier banco de frases de sabor) elija de una lista específica del mapa: gauchesca para la Pampa (mate, pulpería, etc.), griega para Grecia (ágora, oráculos, dioses), medieval para Medieval (castillo, juglares, tabernas). Auditar además otras fuentes de texto de sabor por si hay más regionalismos fuera de lugar: la narración central (`GameplayTableUi.centralPhaseMessage`) y las líneas con sabor de los bots en `LocalBotAi`. Nota menor: hay una descripción de logro con mate en `ProfileCustomizationCatalog.kt:118` (no es texto de gameplay por mapa, no urgente, pero queda anotada).

---

# PARTE 6 — Rediseño de la ventana de victoria

**Contexto:** la ventana de victoria actual (captura del usuario) tiene dos problemas de fondo (el Bufón aparece agrupado con los traidores, y la pantalla es "todo negro") más un bug de flujo (queda el chat abierto detrás). Esta parte cubre el **rediseño visual** y se apoya en los fixes ya diagnosticados en `correcciones-testeo-ronda1.md` (#3 Bufón separado, #4 cerrar chat, #9 decoración). El diseño fue validado con el usuario sobre un mockup: **sección especial en violeta, fondo con degradé oscuro + marco dorado ornamental** (NO la textura del mapa).

## Estructura actual (para ubicarse)

- Layout: `activity_gameplay_mock.xml` — `winnerRevealOverlay` (raíz) → `winnerRevealPanel` → `winnerRevealBackground` (ImageView, hoy `logDrawableFor(themeKey)`) + `winnerRevealShine` + `winnerRevealScroll` → `winnerRevealContent` → { `winnerRevealTitle`, `winnerRevealPersonalResult`, `winnerRevealCards`, `winnerSummaryPanel` (`winnerSummaryStatsRow` con rounds/duration/players, `winnerSummaryHighlight`, `winnerSummaryTimeline`) }.
- Renderer: `WinnerResultsRenderer.render(players, summary, specialVictories, specialWinners, themeKey)` (`WinnerResultsRenderer.kt:32`). Renderiza las cartas de facción en `winnerRevealCards` (`renderCards`), y **ya** tiene una sección de victorias especiales (`renderSpecialVictories`, `:73`) que agrega un header "VICTORIA ESPECIAL - BUFON" + las cartas especiales al mismo contenedor.
- Show: `showWinnerReveal()` (`GameplayMockActivity.kt:5830`) — setea `winnerRevealBackground` con `logDrawableFor(themeKey)`, llama al renderer, y reproduce `MusicManager.playVictoryMusic`.

## Cambio base (dato) — el Bufón duplicado

El renderer **ya separa** las victorias especiales. El problema es que `GameplayTableUi.winnerPresentation()` fusiona los ganadores especiales dentro de `winningPlayers` (ver `correcciones-testeo-ronda1.md` #3), así que el Bufón aparece en la fila de facción **y** en la especial → duplicado + confundido con los traidores. **Requisito para este rediseño:** aplicar primero el fix de `winnerPresentation` (no fusionar; `humanWon` cuenta también las victorias especiales). Con eso, la fila de facción queda solo con los ganadores reales del bando.

---

## Rediseño (basado en el mockup aprobado)

### 1. Sección de facción con encabezado propio
Hoy las cartas de facción aparecen sin título (directo debajo del resultado). Agregar un **encabezado "BANDO GANADOR"** arriba de la fila de facción, con divisores y color según el bando:
- Traidores → rojo (`@color/accent_red`, `#8F2633`).
- Pueblo → verde (`#8FCB91`, el que ya se usa para el equipo pueblo).
- Las cartas de facción llevan un borde fino del color del bando (rojo/verde).

### 2. Sección de victoria especial en violeta
`renderSpecialVictories` (`WinnerResultsRenderer.kt:73-115`) hoy pinta el header en dorado (`#F4C45F`). Cambiarlo a **violeta** y darle un contenedor propio, claramente distinto de la sección de facción:
- Header "VICTORIA ESPECIAL — BUFÓN": color violeta claro `#B79AE0`, con divisores violetas a los lados.
- La(s) carta(s) especial(es) dentro de una **caja con fondo violeta oscuro** (`#1C1630`) y borde violeta (`#8A6FC0`), con el nombre + rol ("BUFÓN") + una línea corta ("Engañó al pueblo y ganó al ser expulsado."). El borde de la carta en violeta claro (`#B79AE0`).
- Objetivo: que a simple vista el Bufón NO se confunda con el bando ganador. El violeta es el color neutral de los roles especiales (distinto del rojo traidores y del verde pueblo).

### 3. Decoración del panel (arreglar "todo negro")
En `winnerRevealPanel` / `winnerRevealBackground` / `winnerRevealContent`:
- **Fondo:** degradé oscuro (`#1A1207` → `#120D06`) en vez de negro plano. (Se decidió NO usar la textura del mapa acá, para mantener el foco en los ganadores.) El `winnerRevealBackground` con `logDrawableFor(themeKey)` puede quedar muy atenuado o reemplazarse por el degradé — a criterio, priorizando legibilidad.
- **Marco dorado ornamental** alrededor del panel: borde dorado (`#B8923F`, ~2dp) con esquinas acentuadas (como los reveals / el chat), radio ~14dp.
- **Estadísticas** (`winnerSummaryStatsRow`): cada stat (rondas/tiempo/elim.) en una cajita con fondo dorado oscuro (`#241809`), número en dorado claro (`#F3D488`) y etiqueta chica en `#B9AD92`.
- **Resumen** (`winnerSummaryHighlight`/`winnerSummaryTimeline`): en un bloque con borde izquierdo dorado (`#6B5528`), fondo `#0F0B06`, texto `#B9AD92`.
- **Botón "VOLVER A LA SALA":** el dorado prominente que ya se usa (estilo `BtnGold`).

### 4. Título / resultado personal
Mantener el resultado personal grande arriba ("VICTORIA" / "DERROTA", con fuente `bree_serif`/voice, dorado) y el resultado de facción como subtítulo, coloreado según el bando ganador (rojo/verde). Esto ya existe (`winnerRevealTitle` / `winnerRevealPersonalResult`), solo ajustar color del subtítulo por bando.

### 5. Cerrar el chat al mostrar la victoria (fix #4)
En `showWinnerReveal()` (`GameplayMockActivity.kt:5830`), cerrar/colapsar el panel de chat (y cualquier overlay abierto) antes de mostrar la victoria, para que la ventana quede sola. Usar el método de cierre del `chatController`.

---

## Consideraciones

- **Orientación:** el renderer ya ramifica portrait/landscape (`WinnerResultsRenderer.kt:39, 43-51`). El rediseño debe verse bien en ambas — en apaisado las secciones quedan más compactas (cartas en fila, stats en una línea). Verificar los dos.
- **Colores nuevos:** conviene sumar los violetas a `colors.xml` como recursos con nombre (ej. `special_victory_accent`, `special_victory_bg`) en vez de hardcodear hex, para consistencia. El rojo (`accent_red`) y el dorado (`accent_gold`) ya existen.
- **No romper el caso normal:** cuando NO hay victoria especial, la sección violeta simplemente no aparece (`renderSpecialVictories` ya retorna vacío si no hay especiales) — verificar que el layout no deje un hueco.
- **Legibilidad:** el texto del resumen es denso; mantener tamaños legibles y el contraste alto sobre el degradé.
- No compilar — el usuario valida en Android Studio y ajusta a ojo los valores finos (atenuación del fondo, tamaños).

## Resumen de archivos a tocar

- `app/src/main/java/com/traidores/juego/GameplayTableUi.kt` — `winnerPresentation` (no fusionar especiales en `winningPlayers`; `humanWon` cuenta especiales) — es el fix base #3.
- `app/src/main/java/com/traidores/juego/WinnerResultsRenderer.kt` — header "BANDO GANADOR" para la facción (con color por bando), restyle violeta de la sección especial (`renderSpecialVictories`), cajas de stats, bloque de resumen, bordes de carta por bando.
- `app/src/main/res/layout/activity_gameplay_mock.xml` — decoración del `winnerRevealPanel`/`winnerRevealBackground` (marco dorado + degradé), estilos de `winnerSummaryPanel`.
- `app/src/main/res/values/colors.xml` — colores violetas nombrados para la victoria especial.
- `app/src/main/res/drawable/` — si hace falta, un drawable para el marco/degradé del panel.
- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — `showWinnerReveal` cierra el chat (fix #4).
