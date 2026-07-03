# Spec — Correcciones de la primera tanda de testeo manual

> Handoff Claude (diseño/review) → Codex (implementación). Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.

**Contexto:** el usuario jugó la matriz de testeo (`docs/desarrollo/guia-testeo-manual-modo-local.md`) y reportó varios bugs con capturas. Acá están diagnosticados con archivo:línea. Algunos tienen causa confirmada leyendo el código; los que no, están marcados como "reproducir" con hipótesis. **Un fix (el texto mojibake del Bufón) ya está hecho y commiteado por Claude** (commit `b1777d5`).

**Tip de reproducción:** en el Lobby (build debug) hay un toggle **"FORZAR EMPATES"** (`debugForceVoteTies`) y el selector **"ROL: X"** — usalos para reproducir los bugs de votación/desempate y de roles específicos de forma confiable.

---

## 1. 🔴 Noche: doble cuenta regresiva para roles sin acción

**Reporte:** con Payador/Bufón/Oráculo (sin acción nocturna), aparece una cuenta de ~15s con "SALTAR NOCHE", y si no la tocás, aparece **otra** cuenta de ~10s. El usuario quiere **una sola** ventana corta de noche y listo, no varias.

**Causa confirmada:** `onCountdownExpired()` (`GameplayMockActivity.kt` ~3632-3645) resuelve la noche **una sub-fase por vez**. Para un humano sin input en modo local, la rama es:
```kotlin
GamePhase.NOCHE_ASESINO, ... NOCHE_ORACULO -> {
    if (isOnlineGameplay()) resolveOnlineNightWindow()
    else if (GameEngine.requiresHumanInput(session)) GameEngine.resolveHumanTimeout(session)
    else advanceSessionWithoutRendering()   // ← avanza UNA sub-fase, luego re-renderiza otra cuenta
}
```
La noche tiene 5 sub-fases (asesino/mercenario/policía/médico/oráculo); cada una que el humano no controla dispara su propio countdown, aunque el humano no tenga nada que hacer. De ahí las cuentas encadenadas.

**Fix:** cuando el timer expira en una noche local sin input humano, colapsar **todas** las sub-fases nocturnas restantes sin input en un solo salto hasta el amanecer — el mismo loop que ya hace `skipRemainingNight()` (avanzar mientras `!requiresHumanInput(advanced) && isNightPhase(advanced)`), en vez de `advanceSessionWithoutRendering()` una sola vez. Así hay **una sola** cuenta de noche. Si en una sub-fase posterior el humano SÍ tiene acción, el loop se detiene ahí (igual que hoy `skipRemainingNight`), así que es seguro.

**Además (pedido explícito del usuario):** que esa única ventana sea **corta** para roles sin acción — no los 40s (`DEFAULT_NIGHT_SECONDS`) ni 15s. Para un humano sin ninguna acción esa noche, considerar una duración fija corta (~10s, o directamente el arm-delay de 3.5s + un pequeño margen) en vez de `timing.nightSeconds`. Ubicación: `activePhaseSeconds()` (`GameplayMockActivity.kt` ~4034-4058), rama de las NOCHE_*: hoy devuelve `timing.nightSeconds`; para el caso `!requiresHumanInput` en local, devolver una duración corta fija.

---

## 2. 🔴 Desempate: no deja cambiar el voto (reproducir)

**Reporte:** en el desempate (captura 1), no podía votar/cambiar de voto — "solo me dejaba votar al que voté primero". Sospecha que como no votó en la ronda previa, el sistema le auto-asignó un voto.

**Lo que veo en el código (sin causa 100% confirmada):** el tap de cada carta (`createTieVoteCard`, `GameplayMockActivity.kt:5468-5476`) hace `selectedTarget = player.name; renderTieVoteWindow()`, y `actionable = canActOnTarget(player.name)` → `isValidTieVoteTarget` (`GameEngine.kt`) = `isValidVoteTarget(...) && target in tieVoteCandidates`. Con dos candidatos válidos que no son el humano, **ambos deberían ser tocables y re-tocables** — o sea, por lectura estática cambiar el voto debería funcionar.

**Hipótesis a verificar reproduciendo** (con `debugForceVoteTies` activado):
- ¿La cuenta regresiva del desempate expira antes de que llegue a tocar la segunda carta (auto-resuelve)? Si el timer es muy corto, se siente como "no me deja".
- ¿`canActOnTarget` empieza a devolver `false` para el otro candidato después de seleccionar uno? Revisar si algún estado cambia al seleccionar.
- ¿Hay un paso de "confirmar" (botón "ELEGIR CARTA") que bloquea re-selección una vez confirmado?

**Acción:** reproducir con empates forzados, tocar candidato A y luego B en el desempate, y confirmar si el resaltado se mueve. Corregir según lo que aparezca (probablemente: permitir re-selección libre hasta que se confirme/expire, y/o alargar el timer del desempate).

---

## 3. 🟡 Victoria: el Bufón aparece agrupado con los traidores

**Reporte (captura 4):** en la pantalla de VICTORIA ("LOS TRAIDORES HAN GANADO"), el Bufón (que ganó por victoria especial) aparece al lado de Valen (Mercenario) y Rami (Asesino), como si fuera del bando traidor.

**Causa confirmada:** `GameplayTableUi.winnerPresentation()` fusiona los ganadores especiales dentro de la lista de ganadores de facción:
```kotlin
val winningPlayers = (factionWinningPlayers + specialWinningPlayers).distinctBy { it.name }
```
El Bufón entra en `winningPlayers` junto a los traidores, y el renderer los muestra todos bajo el mismo título de facción.

**Dato clave:** el `WinnerResultsRenderer` **ya tiene** una sección separada para victorias especiales (`renderSpecialVictories`, `WinnerResultsRenderer.kt:73`, con encabezado "VICTORIA ESPECIAL - BUFON" y sus cartas). El problema es que el Bufón aparece **duplicado**: en la sección especial Y también fusionado con los traidores, porque `winnerPresentation` lo mete en `winningPlayers`.

**Fix (solo en `winnerPresentation`):** NO fusionar los ganadores especiales en `winningPlayers` — dejarlos solo en `specialVictories` (que ya se pasa aparte). Así el Bufón aparece únicamente en su sección especial, no con los traidores. **Cuidado:** `humanWon` hoy se calcula como `winningPlayers.any { it.isHuman }`; al sacar al Bufón de `winningPlayers` hay que mantener `humanWon = true` cuando el humano ganó por victoria especial (chequear también `specialVictories`), para que el resultado personal siga diciendo "VICTORIA". El renderer no necesita cambios funcionales para esto (el rediseño visual va aparte, ver mockup de la ventana de victoria).

---

## 4. 🟡 Victoria: no cierra el chat (ni otras ventanas)

**Reporte (captura 4):** el usuario tenía el chat abierto y al aparecer la ventana de victoria, el chat quedó abierto encima/detrás. La ventana de victoria debería cerrar todas las demás.

**Causa confirmada:** `showWinnerReveal()` (`GameplayMockActivity.kt:5830`) pausa el countdown y limpia banners, pero **no cierra el panel de chat**. 

**Fix:** en `showWinnerReveal()`, cerrar/colapsar el chat (el `chatController` tiene el método para cerrar el panel expandido — usar ese) y cualquier otro overlay abierto antes de mostrar la victoria, para que la ventana de victoria quede sola.

---

## 5. 🟡 Chat: anuncios de expulsión redundantes

**Reporte (captura 3):** el chat muestra la expulsión dos veces: "Día 2: Lautaro fue expulsado." y "Día 2: Lautaro fue expulsado. La oscuridad vuelve a caer y el pueblo cierra sus puertas."

**Causa confirmada:** doble push al historial:
- `resolveResult()` (`GameEngine.kt:934`) hace `.withPublicHistory(message)` con "Día N: X fue expulsado."
- Luego `startNextRound(resolved, message)` (`GameEngine.kt:937` → def. ~línea con `startNextRound`) hace `val message = "$previousMessage ${nextNightMessage(prepared)}"` y **vuelve a** `.withPublicHistory(message)` — re-incluye la línea de expulsión + el texto de noche.

**Fix:** `startNextRound()` debería pushear al historial **solo** la parte nueva (`nextNightMessage(prepared)`, el texto de transición a la noche), no re-incluir `previousMessage` (que el caller ya agregó al historial). El `publicAnnouncement` (banner del momento) puede seguir siendo el combinado, pero el **historial/chat** no debe duplicar. **Cuidado:** revisar todos los callers de `startNextRound` — confirmar que cada uno ya agregó su `previousMessage` al historial antes de llamar (si alguno no lo hace, ese mensaje se perdería del chat). Los tests de `GameEngineTest` deberían cubrir que no se pierda ni se duplique.

---

## 6. 🟡 Marco griego: cuadro negro sobre el borde superior

**Reporte (captura 5):** en el reveal de expulsión de Grecia ("Jugador FUE EXPULSADO"), hay un cuadro negro que sobresale por encima del marco ornamental superior.

**Causa probable:** el contenido oscuro (o su fondo `bg_reveal_text_shade`) del `voteResultPanel` no queda contenido dentro del centro oscuro del marco griego — el inset superior no alcanza para el ornamento del marco griego (que es distinto/más alto que el de los otros mapas). Insets actuales: `voteResultPanel.setPadding(dp(46), dp(60), dp(46), dp(42))` (`GameplayMockActivity.kt:3310`). Es la misma clase de problema que arreglamos en el reveal de muerte.

**Fix:** ajustar el inset superior del `voteResultPanel` (y/o acotar el fondo oscuro interno) para que nada sobresalga por encima del borde superior del marco, verificándolo específicamente en el mapa **grecia** (el mármol tiene proporciones distintas). Ajustar a ojo en Android Studio.

---

## 7. 🟡 Música de la victoria del Bufón

**Reporte (captura 2):** la música que suena en la ventana de victoria del Bufón no corresponde.

**Causa confirmada:** `showJesterVictory()` (`GameplayMockActivity.kt:5790`) llama `MusicManager.playVictoryMusic(this)` — la música de **fin de partida** — aunque la victoria del Bufón es especial y **la partida continúa**. Suena la fanfarria de victoria final cuando en realidad el juego sigue.

**Fix:** para la victoria especial del Bufón (que NO termina la partida), no reproducir la música de victoria final. Usar solo el efecto del Bufón (`GameSound.JESTER`, que ya se reproduce en la línea siguiente) y mantener la música de fase, o una señal corta propia. La música de victoria final queda reservada para `showWinnerReveal` (fin real de partida).

---

## 8. 🟢 Sonido de silenciado (asset del usuario)

**Reporte:** cambiar el sonido de cuando alguien es silenciado/muteado.

**Acción:** el efecto actual es `GameSound.SILENCE` (`R.raw.sfx_silence`). El usuario consigue un archivo nuevo (banco libre/CC0, nombre válido para raw: `a-z 0-9 _`) y Codex lo reemplaza. Igual que el resto del audio del proyecto.

---

## 9. 🟢 Ventana de victoria: decorarla más

**Reporte (captura 4):** la ventana de victoria es "todo negro", se puede decorar más.

**Acción (diseño, no bloqueante):** darle más ambientación al fondo de la pantalla de ganador — hoy usa `logDrawableFor(themeKey)` como fondo (`showWinnerReveal:5851`) pero el resto es negro. Ideas: aprovechar el marco ornamental por mapa, un fondo temático más rico, o separar visualmente las secciones (bando ganador vs victoria especial, que se ata con el punto 3). Esto conviene planearlo aparte con mockups antes de implementar.

---

## 10. 🟠 Bots demasiado enfocados en el humano (quickTestMode confirmado OFF)

**Reporte:** con el Oráculo no pudo pasar de la primera noche — o lo mataban, o lo votaban. **El usuario confirmó que "MODO TEST RÁPIDO" estaba DESACTIVADO**, así que el ayudante de testeo (`LocalBotAi.kt:232`) NO es la causa.

**Sesgo confirmado en el código (matar):** `nightPressureScore()` (`LocalBotAi.kt:3188`) — la función que decide a quién apunta el asesino — le suma **+2 al humano** a partir de la ronda 2:
```kotlin
return (if (candidate.isHuman && session.round > 1) 2 else 0) + spokeCount * 3 + ...
```
Esto hace que, a medida que avanza la partida, el asesino tienda a cazar al humano. Probablemente es intencional (subir la dificultad), pero es **demasiado agresivo** dado que el usuario se siente el blanco constante.

**Lo que NO explica el bug:** en la ronda 1 ese +2 no aplica (`session.round > 1` es falso), así que la muerte de la **primera** noche no viene de ahí — es variancia (el humano es un objetivo válido más en un pool chico) o hay otra causa a reproducir.

**Fix / investigación para Codex:**
1. Suavizar o quitar el `+2` anti-humano en `nightPressureScore` (`LocalBotAi.kt:3188`) — que el humano no sea perseguido de forma desproporcionada.
2. Investigar el targeting de **votos** (`rankedPublicSuspects` / `chooseVoteTarget` en `LocalBotAi.kt`): no encontré un sesgo `isHuman` explícito ahí, así que la sospecha es **emergente** — probablemente un humano que **no chatea** queda como el más sospechoso por descarte. Revisar si la falta de participación del humano infla su sospecha, y suavizarlo para que un jugador pasivo no sea foco automático el día 1.
3. Reproducir varias partidas seguidas (rol forzado) contando cuántas veces el humano muere noche 1 / es votado día 1, para medir si es sistemático o variancia.

---

## 11. 🟡 Alcalde: el botón "REVELARME - VOTO DOBLE" queda cortado abajo

**Reporte (captura nueva):** en la partida del Alcalde, el botón secundario **"REVELARME - VOTO DOBLE"** queda cortado por el borde inferior de la pantalla — se ve solo la parte de arriba.

**Causa confirmada:** el `bottomPlayerPanel` (`gameplay_table_section.xml:376-378`) tiene **altura fija `118dp`**. Cuando el `btnRevealMayorSecondary` (`:544-562`, 24dp + `marginTop 2dp`) se hace visible (solo para el Alcalde sin revelar), el contenido total supera los 118dp y el botón se desborda fuera del panel → lo corta el borde de la pantalla.

**Fix:** que el `bottomPlayerPanel` acomode ese botón extra cuando está visible. Opciones:
- Cambiar la altura del panel a `wrap_content` (con un mínimo) para que crezca hacia arriba cuando aparece el botón — verificar que no tape las columnas de cartas laterales ni el feed de chat.
- O reducir la altura de las otras filas (la fila `actionControls` REVELAR/ESPERAR) cuando el botón secundario está visible.
- O aumentar la altura fija del panel lo suficiente para contener el botón (menos elegante, suma dimensión fija).

Recordar: el cambio va en **ambos** layouts (`gameplay_table_section.xml` y `layout-land/gameplay_table_section.xml`).

---

## 12. ✅ Texto del Bufón mal codificado — YA CORREGIDO

Hecho y commiteado por Claude (`b1777d5`): "BUFÃN"/"ConsiguiÃ³" → "BUFÓN"/"Consiguió" en `GameplayMockActivity.kt` (victoria especial del Bufón). No requiere acción de Codex.

---

## Resumen de prioridad
1. **Bug 1** (doble cuenta de noche) y **Bug 2** (desempate no deja votar) — son los que rompen la jugabilidad, van primero.
2. **Bugs 3, 4, 5, 6, 7** — correcciones de presentación/coherencia, riesgo medio.
3. **Bug 10** — confirmar quickTestMode antes de invertir tiempo.
4. **Bugs 8, 9, 11** — assets/diseño/captura pendiente, no bloqueantes.
