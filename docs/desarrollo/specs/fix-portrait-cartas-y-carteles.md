# Spec — Fix portrait: cartas tapadas por el panel, orden del cartel de eliminado y overlays cortados

Bugs detectados probando en un Samsung A56 (portrait, config de sistema default) con 9 jugadores en modo local. Tres bugs urgentes (FASE A) + cierre de la barrida responsive (FASE B).

**Decisiones ya tomadas con el usuario:**
- Bug de cartas tapadas: **achicar cartas para que entren todas** (sin scroll hasta 13+, como hoy). No activar scroll más temprano.
- Cartel de eliminado: **diálogo estilo juego** (panel oscuro + borde dorado + botones del juego), no overlay con marco de mapa.
- El gameplay es **portrait-only** (manifest); la rama landscape de los métodos tocados se deja intacta.

**Restricciones:** no tocar el flujo online (gates/watchdog/handoff, recién estabilizado). No rediseñar overlays que ya se ven bien (noDeath, voteResult, privateFeedback, winner, death ya corregido). Cambios mínimos y localizados.

---

# FASE A — Bugs urgentes

## A1. (GRAVE) Cartas de la mesa tapadas por el panel inferior con 9-12 jugadores

### Causa raíz (verificada en código)
1. En portrait, `applyAdaptiveGameplayLayout()` (`GameplayMockActivity.kt` ~4250) ensancha `bottomPlayerPanel` a casi todo el ancho de pantalla: `width = dp((screenWidthDp - 24).coerceIn(244, 372))`, centrado con `gravity = BOTTOM|CENTER_HORIZONTAL`. Como el panel es hijo de `centerColumn` (FrameLayout con `clipChildren=false`), desborda sobre la base de **ambas** columnas laterales de cartas.
2. Las columnas (`leftPlayersScroll`/`rightPlayersScroll`, `gameplay_table_section.xml`) son `match_parent` de alto. El inset inferior que les reserva el espacio del panel (`bottomScrollInset = BOTTOM_PLAYER_PANEL_HEIGHT_DP + 12`, ~línea 4247) **solo se aplica cuando `metrics.scrollEnabled`**, o sea con 13+ jugadores.
3. `renderPlayerColumns()` (~3910) mide `availableHeightDp` desde `leftPlayersScroll.height` **completo**, sin descontar el panel. Con 9-12 jugadores (sin scroll), las cartas se centran en la altura total y las de abajo quedan detrás del panel (caso reportado: carta de "Rami" 100% oculta, "Toto" parcialmente tapada).

Dato clave: `GameplayTableUi.companionCardMetrics()` (líneas 792-836) **ya tiene lógica de encoger/crecer las cartas para caber en `availableHeightDp`**. Alimentándole la altura correcta, el tamaño se auto-ajusta sin tocar esa función.

### Fix
1. **`applyAdaptiveGameplayLayout()`** — rama portrait (~4247): aplicar el inset **siempre**, no solo con scroll:
   ```kotlin
   val bottomScrollInset = BOTTOM_PLAYER_PANEL_HEIGHT_DP + 12
   ```
   (eliminar el condicional `if (metrics.scrollEnabled) ... else 0`). La rama landscape queda como está.
2. **`renderPlayerColumns()`** (~3905-3915): descontar el área del panel de la altura disponible, en portrait:
   - Camino medido: tras calcular `availableHeightDp` desde `measuredHeightPx`, restar `BOTTOM_PLAYER_PANEL_HEIGHT_DP + 12` cuando `isPortrait()`.
   - Camino fallback (cuando `measuredHeightPx == null`): `screenHeightDp - 16` pasa a `screenHeightDp - 16 - (BOTTOM_PLAYER_PANEL_HEIGHT_DP + 12)` en portrait.
   - **Restar la constante, no `paddingBottom` del view**: en el primer render el padding todavía no está aplicado (se setea en `applyAdaptiveGameplayLayout`, que corre después de medir) y `view.height` no cambia con el padding, así que restar la constante una sola vez es correcto en ambos casos y determinístico desde el primer frame. No restar dos veces.
3. **No tocar `companionCardMetrics`**: el encogimiento (rama `idealItemHeight < base.itemHeightDp`, línea 807) y el crecimiento acotado ya resuelven el tamaño. Con la altura corregida, en un teléfono típico (~640dp útiles / 5 cartas por lado) las cartas siguen siendo grandes.

### Criterios de aceptación A1
- Partidas locales de **9, 12 y 15** jugadores en portrait (bots): **ninguna** carta queda tapada por el panel inferior, ni parcial ni totalmente.
- Con 9-12: todas las cartas visibles sin scroll, centradas en el área **por encima** del panel.
- Con 13+: el scroll sigue funcionando como hoy (top-aligned + inset).
- El botón "LISTOS PARA VOTAR" (marginBottom 152dp) no queda tapado ni tapa cartas.
- La rama landscape de `applyAdaptiveGameplayLayout` no cambió.

## A2. Cartel "Te eliminaron": aparece ANTES del reveal de muerte y es un AlertDialog default de Android

### Causa raíz (verificada)
- Orden: `renderGame()` llama `maybeOfferSpectatorChoice()` (~línea 1491) **antes** de `resumeGameFlowAfterBlockingUi()` (que es quien muestra los reveals pendientes). La guarda de `maybeOfferSpectatorChoice()` (~7151) usa `isBlockingGameplayUiActive()`, que solo detecta reveals **corriendo** (`isDeathRevealRunning`, etc.), no los **pendientes en cola** (`pendingDeathReveals`). Al morir el humano: la cola se llena en `collectNewlyDeadPlayers()`, pero el diálogo se ofrece antes de que el primer reveal arranque.
- Estética: `showSpectatorChoiceDialog()` (~7163) usa `AlertDialog.Builder().setTitle().setMessage().setPositiveButton()...` pelado → look Android default.

### Fix
1. **Orden** — en `maybeOfferSpectatorChoice()` agregar una guarda con la función existente `hasPendingDawnRevealSequence()` (~6216):
   ```kotlin
   if (hasPendingDawnRevealSequence()) return false
   ```
   Con eso el llamado temprano de `renderGame()` no dispara nada; el reveal de muerte corre; al terminar, `resumeGameFlowAfterBlockingUi()` vuelve a llamar `maybeOfferSpectatorChoice()` (ya existe ese llamado, ~5773) y el diálogo aparece **después** del cartel de quién murió. No mover llamados; solo la guarda.
2. **Estética** — reescribir `showSpectatorChoiceDialog()` con vista custom estilo juego, replicando el patrón de los diálogos de `OnlineModeActivity` (`showCreateRoomDialog` + `dialogTitle` + `dialogButton`):
   - Contenedor `LinearLayout` vertical con `bg_dialog_game_panel`, padding ~24/22/24/18dp.
   - Título dorado (`accent_gold`, 24sp, bold, centrado, MAYÚSCULAS): `"TE ELIMINARON"` / variante Bufón `"¡GANASTE COMO BUFÓN!"`.
   - Mensaje (`text_secondary`, ~16sp, centrado): conservar los textos actuales de ambas variantes.
   - Botonera horizontal centrada: **SEGUIR MIRANDO** (dorado, `bg_btn_gold`, texto `bg_dark`) y **VOLVER AL MENÚ** (oscuro, `bg_btn_dark`, texto `text_primary`), ~138x44dp c/u.
   - `AlertDialog` con `setView(content)`, **no cancelable**, window: fondo transparente, `setLayout(minOf(screenWidth - 32dp, 430dp), WRAP_CONTENT)`, dim 0.58.
   - Conservar el comportamiento actual: `pauseCountdown()` al mostrar, `enterSpectatorFastForward()` en SEGUIR MIRANDO, `returnToLobby()` en VOLVER AL MENÚ, y el flag `spectatorChoiceOffered` (incluida su persistencia en `onSaveInstanceState`).

### Criterios de aceptación A2
- Al morir el jugador humano de noche: primero se ve el cartel de muerte con su nombre (y el de "sin muertos"/silenciado si aplica), y **recién al cerrarse** aparece el diálogo de eliminado.
- Al ser expulsado por votación: primero el recuento/expulsión, después el diálogo.
- El diálogo se ve con la estética del juego (panel oscuro, borde/título dorado, botones del juego), no como AlertDialog default.
- La variante del Bufón conserva su título y mensaje.
- Rotación/recreación del Activity no re-ofrece el diálogo si ya se ofreció (flag persistido, comportamiento actual).

## A3. Panel del Oráculo cortado abajo

### Causa raíz
El ajuste responsive anterior fijó `oracleRevealPanel` en `336x193dp` (`@dimen/reveal_oracle_panel_width/height`), pero el contenido interno suma ~233dp (badge 24 + título 38 + subtítulo 42 + nombre 46 + texto 44 + botón 38 + márgenes/paddings) → el texto final y el botón "ESCUCHAR AL REGRESADO" quedan cortados.

### Fix
En `activity_gameplay_mock.xml` (~1005):
1. `oracleRevealPanel` (FrameLayout): `layout_height` → `wrap_content` (el ancho queda `@dimen/reveal_oracle_panel_width` = 336dp).
2. El `LinearLayout` interno de contenido: `layout_height` `match_parent` → `wrap_content`.
3. El `ImageView` del portal (`centerCrop`) y el `View` de sombra (`bg_oracle_text_shade`) quedan `match_parent`: en FrameLayout adoptan la altura final del panel definida por el contenido.
4. En `values/dimens.xml`: eliminar `reveal_oracle_panel_height` (queda sin uso).

### Criterios de aceptación A3
- El overlay del Oráculo muestra completos: badge, "UNA VOZ REGRESA", subtítulo, nombre del jugador, texto descriptivo y el botón, con el borde dorado cerrado abajo.
- Entra completo en un teléfono de 360dp de ancho sin desbordar a los costados.

---

# FASE B — Cierre de la barrida responsive (menos urgente)

## B1. Reveal de compañeros traidores (`traitorRevealCards`, ~896)
Fila horizontal de cartas generada por código (no XML). Con 3+ cartas puede desbordar el ancho en teléfono. Fix propuesto: envolver `traitorRevealCards` en un `HorizontalScrollView` (con `clipChildren=false` y la fila centrada cuando entra), **sin** cambiar el look del caso común de 1-2 cartas. Alternativa si el código que crea las cartas lo permite fácil: achicar el tamaño de carta cuando `count >= 3`. Elegir la opción de menor riesgo al ver el código generador.

## B2. Verificación del HUD con preview (no tocar a ciegas)
Checklist para revisar en el preview de Android Studio (dispositivos de 360dp y 411dp + fuente de sistema en grande), usando `tools:visibility="visible"` para los paneles ocultos:
- `topStatus` (título de fase + subtítulo + countdown), `eventLogPanel`, `chatAmbientFeed` (feed "QUE SE DICE..."), textos del `bottomPlayerPanel` (`currentPlayerName/Status/Hint`, `roleName`), botonera `actionControls` (REVELAR / ELEGIR OBJETIVO), `chatPanel` completo, `actionFeedbackBanner` (344dp fijos) y `centralPublicEventBanner` (marginHorizontal 82dp — sospechoso en 360dp: verificar que no quede demasiado angosto).
- Solo corregir lo que se vea mal, con el patrón ya montado: `@dimen` en `values/dimens.xml` + autosize (`app:autoSize*`) + `maxLines`. No re-maquetar nada que se vea bien.

## B3. Validar la jaula del reveal de silencio
Fue reducida ~15% a ciegas (5 piezas superpuestas animadas: card/cageLeft/cageRight/cageDoor/cageLock, ~499-559). Verificar en preview o forzando un silencio del Mercenario en local que las piezas sigan alineadas (puerta centrada sobre la carta, candado sobre la puerta). Si algo quedó corrido, recalibrar manteniendo las proporciones relativas entre piezas.

---

# Cómo probar (general)
- Partida local con bots: 9 y 12 jugadores (bug A1), morir de noche y por votación (bug A2), partida con Oráculo en mapa Grecia (bug A3).
- Preview de Android Studio en Pixel (411dp) y un device de 360dp para FASE B.
- Verificación final en teléfono real (A56) además de BlueStacks.
