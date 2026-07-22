# SPEC — Correcciones de gameplay + Historial de partidas

> Para Codex. Español, `archivo:línea`. **No compilar** (el usuario valida en Android Studio). Todo en el modo **local** salvo donde diga. Las partes 1-6 son fixes/pulido; la parte 7 es la feature nueva (historial).
>
> **Pedido explícito del usuario**: donde marco "error → solución", si al reproducir encontrás una causa distinta a la que sospecho, seguí la evidencia y explicá qué cambiaste. Y si ves algo relacionado que falta, proponelo.

---

## Parte 1 — Táctil de votar en la mesa (a veces hay que tocar dos veces)

**Síntoma** (reportado por el usuario, en la **votación normal de la mesa**, no es congelamiento): al tocar una carta para votar, a veces el primer toque no registra y hay que tocar de nuevo.

**Diagnóstico y sospechas** (verificar reproduciendo durante el día con bots chateando):
- El click de carta está en `holder.root.setOnClickListener` (`GameplayMockActivity.kt:5123-5139`). Tiene 3 ramas: `isActionable` → selecciona; `!isAlive` → toast; **`else` → `showMiniPlayerProfile(player)`**. `isActionable` es un `val` capturado en el bind. **Sospecha principal**: si una carta viva y votable quedó bindeada con `isActionable = false` (bind previo a que la fase pasara a `VOTACION`, o `canActOnTarget` transitoriamente false), el primer toque cae en el `else` y **abre el mini-perfil** en vez de seleccionar; el usuario lo cierra y toca otra vez → "dos toques".
- `bindSidePlayerCard` ya evita re-binds innecesarios con `renderKey` (`GameplayMockActivity.kt:4975-4989`), así que **no** es el re-render comiéndose el tap. Pero conviene confirmar que `renderKey` incluye lo que hace a una carta actionable (fase, `selectedTarget`, vivo/mudo) para que al entrar a `VOTACION` las cartas se re-bindeen y queden actionable **antes** del primer toque.
- Rama secundaria: el botón de confirmar voto (`btnAction` → `handleCurrentPhase`) sale temprano si `countdown.isTransitionLocked` (`GameplayMockActivity.kt:~1174`), mostrando un toast; si el usuario confirma en el primer segundo de la fase, el primer toque se pierde.

**Solución sugerida**:
1. En el `else` del onClick (`:5137`), **no** abrir el mini-perfil con un tap simple sobre una carta viva durante una fase de acción/voto: si `isAlive` y la fase admite acción (`requiresHumanInput`/votación), tratar el tap como intento de selección (recomputar `canActOnTarget(player.name)` en el momento del click en vez de confiar en el `val` capturado). El mini-perfil ya está disponible con **long-press** (`:5140`), así que no se pierde.
2. Recomputar `isActionable` en tiempo de click (llamar `canActOnTarget(player.name)` dentro del listener) para no depender del estado del último bind.
3. Verificar que `renderKey` (`:4975-4988`) invalide la carta cuando cambia la fase o `selectedTarget`, así entra a `VOTACION` ya actionable.

Verificación: entrar a la votación del día y tocar cartas **mientras los bots chatean**; cada primer toque debe seleccionar (o cambiar el voto), nunca abrir el perfil ni requerir segundo toque.

## Parte 2 — El bot invocado por el Oráculo no habla

**Síntoma**: cuando el Oráculo invoca a un muerto, ese jugador (bot) no dice nada en el debate del día.

**Diagnóstico (verificado)**: el motor **sí** lo habilita. `GameEngine.canSpeak(session, player)` contempla al invitado (`GameEngine.kt:1187-1192`: `DIA_DEBATE && oracleInvitedPlayer == player.name`), y `canParticipateInChat` (`:1250-1258`) devuelve `true` para él. `messageBots` (`BotConversationMemory.kt:508-516`) filtra por `canParticipateInChat`, así que **debería** entrar como orador. El bug está **río abajo**, en la generación de la línea o en el scheduling del chat:
- Rastrear `LocalBotAi.nextConversationLine` → `cachedConversationBatch` → `openingDebateMessages` (`LocalBotAi.kt:537`): confirmar que a un orador **muerto-pero-invitado** se le genera una línea. Muchas helpers de la IA (perception, sospechas, `speechTarget`, `rankedPublicSuspects`) filtran a **vivos** y pueden devolver vacío/`null` para un muerto → el batch no produce mensaje para ese bot.
- Confirmar también que `BotConversationDirector.eligibleSpeakers` y `chooseSpeakers` (`BotConversationDirector.kt`) no excluyan al muerto por otra vía, y que el `chatController` efectivamente agende su turno en `DIA_DEBATE`.

**Solución**: que el invitado (dead + `oracleInvitedPlayer`) produzca al menos una línea de conversación durante `DIA_DEBATE` (una intervención breve alcanza; no hace falta que participe como un vivo pleno). Donde las helpers de IA cortan por `alive`, permitir explícitamente al `oracleInvitedPlayer`. Verificación: partida local forzando Oráculo (Parte 7 de la spec de modo práctica, o los flags de debug), invocar a un muerto y confirmar que habla en el día siguiente.

## Parte 3 — Al revelarse el Alcalde, dar vuelta su carta en la mesa

**Estado actual**: las cartas de los no-humanos muestran el dorso oculto; solo el humano ve su rol. Cuando el Alcalde se revela (`session.alcaldeRevealed = true`), hoy solo hay un mensaje de texto.

**Pedido**: cuando el Alcalde se revela (bot o humano), **dar vuelta su carta** en la mesa con animación y mostrar la imagen del rol Alcalde, visible para todos.

**Solución**:
- En `bindSidePlayerCard` (`GameplayMockActivity.kt:4962`), para la carta cuyo `role?.key == "alcalde"` cuando `session.alcaldeRevealed`, mostrar la imagen del rol Alcalde (usar `role.imageResName` vía `resources.getIdentifier`, mismo patrón que `RoleAdapter.kt:96-97`) en vez del dorso. Incluir `alcaldeRevealed` + la identidad del alcalde en el `renderKey` para que se re-bindee al revelarse.
- Animación de flip: reusar el patrón de flip que ya exista (buscar animaciones de carta en el reparto / `RolePreviewAnimator`); si no hay uno reusable simple, un flip con `rotationY` 0→90 (cambiar imagen)→180, o un scaleX 1→0→1 cambiando el drawable en el medio. Disparar el flip una sola vez, cuando `alcaldeRevealed` pasa a true (no en cada bind).
- Triggers existentes: bot se revela en `autoRevealBotAlcalde` (`GameEngine.kt:2251`); humano vía el botón "REVELARME". El flip se dispara desde el render cuando detecta el cambio de estado, no hay que tocar la lógica del engine.

## Parte 4 — Ventana de DESEMPATE: texto cortado y botones chicos (responsive)

**Síntoma** (capturas): dentro de cada carta el nombre se corta ("Thiago"→"Thia") y "TOCAR PARA VOTAR" se corta; abajo los botones (CHAT / REVELARME / ELEGIR CARTA) están apretados y "REVELARME" parte en dos líneas.

**Causa**: en `createTieVoteCard` (`GameplayMockActivity.kt:6986-7008`) el nombre y el status usan **tamaño fijo** (`textSize = 13f` y `10f`) con `ellipsize = END` → se cortan en cartas angostas (~106dp tras el fix responsive previo). Los botones de abajo son un row de ancho fijo con labels largos.

**Solución**:
1. **Nombre** (`:6986-6993`): reemplazar `textSize = 13f` por **autosize** (`TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration`, min ~8sp, max 13sp), igual que se hizo con el badge de las cartas de mesa. Así "Thiago" se achica en vez de cortarse.
2. **Status** (`:6998-7004`): autosize (min ~7sp, max 10sp) y/o acortar el texto a **"VOTAR"** o **"TOCÁ PARA VOTAR"** más corto; hoy "TOCAR PARA VOTAR" no entra.
3. **Botones inferiores** del panel de desempate (en `activity_gameplay_mock.xml`, la fila con `btnTieVoteChat`, `btnTieRevealMayor` y el de confirmar): darles autosize, acortar "REVELARME"→**"REVELAR"** (o ícono), y asegurar que la fila entre en una pantalla angosta apaisada/vertical — si tres botones no entran, permitir 2 filas o achicar el chat a ícono. `singleLine` + autosize en cada uno.
4. Revisar que el fix aplique tanto en vertical como apaisado.

## Parte 5 — Sangre en la carta del muerto, bota en la del expulsado

**Estado**: la carta del muerto en la mesa se muestra atenuada/oscura (`holder.root.alpha = 0.4f`, `GameplayMockActivity.kt:5122`) con la "carta oscura" ya existente. `GamePlayer` **no** guarda **cómo** murió (`GameModels.kt:177-187`: solo `alive`).

**Pedido**: al muerto por la noche (asesino) → gotas de **sangre**; al **expulsado** por votación → **bota**. Encima de la carta oscura actual. Reusar assets existentes: `death_blood_splatter.xml` (vector) y `boot_recoil.png`/`boot_windup.png`/`ic_kicking_boot.png`/`expulsion_seal.png`.

**Solución**:
1. **Guardar la causa de muerte**: agregar a `GamePlayer` un campo `deathCause: DeathCause = DeathCause.NONE` (enum nuevo: `NONE`, `NIGHT`, `VOTE`). Setearlo donde el jugador muere:
   - Muerte nocturna: en `resolveDawn` (`GameEngine.kt:379-381`), al marcar la víctima `alive = false`, también `deathCause = NIGHT`.
   - Expulsión por voto: donde se aplica `dayEliminationTarget` como muerto (buscar en la resolución del recuento / `resolveResult` / donde el expulsado pasa a `alive = false`), setear `deathCause = VOTE`.
   - (El Bufón "gana al ser expulsado" también es VOTE.) Serializable ya está en la data class.
2. **Overlay en la carta** (`bindSidePlayerCard`): cuando `!isAlive`, agregar sobre la `cardFace` un `ImageView` overlay chico según `deathCause`: `NIGHT` → `death_blood_splatter` (sangre), `VOTE` → bota (`ic_kicking_boot`/`expulsion_seal`). Incluir `deathCause` en el `renderKey`. Mantener la atenuación actual.
3. Que sea sutil y no tape el avatar/nombre (esquina o marca de agua sobre la carta).

## Parte 6 — Ventana de expulsión: reducir de 3 colores a 1-2

**Ubicación**: `VoteResultAnimator` — el panel de expulsión arma `title` ("$name FUE EXPULSADO"), `subtitle` ("Su carta permanece oculta") y `setNotice(...)` ("El poder deja su marca en la jornada" / "El pueblo continua") (`VoteResultAnimator.kt:638-648`). Hoy los tres textos usan colores distintos (dorado / crema / un tercer color) → se ve desprolijo.

**Solución**: unificar a **máximo 2 colores**: el `title` en dorado (acento) y `subtitle` + `notice` en un mismo crema/blanco tenue. Buscar dónde se asignan los `setTextColor` de esos tres TextViews (en el layout del panel de resultado, `activity_gameplay_mock.xml`, o en el propio `VoteResultAnimator`) y dejar título=dorado, resto=un solo color. Aplicar el mismo criterio al panel de **muerte nocturna** si comparte el patrón, para consistencia.

---

## Parte 7 — Historial de partidas (en el perfil) — feature nueva

**Decisiones del usuario**: nivel **básico**, **solo local** por ahora, arrancar "de a poco".

**Datos por partida (básico)**: fecha, mapa (nombre), tu rol (nombre), resultado (ganaste/perdiste). Guardar también el epoch para ordenar.

**Flujo de UI**: en el **Perfil** se ve la **última partida** jugada (una tarjeta resumen); al tocarla, se abren las **últimas 5** en una lista/diálogo.

**Implementación sugerida** (espejar el estilo de `AchievementTracker`, que ya persiste en `SharedPreferences` y ya recibe la sesión al terminar):
1. **Modelo + store nuevos**: `MatchRecord(dateEpochMs, mapKey, mapName, roleKey, roleName, won: Boolean)` y `object MatchHistoryStore` con:
   - `record(context, session)`: construir el record del humano y **anteponerlo** a una lista guardada en `SharedPreferences` (namespace `TraidoresPrefs`), tope ~10 registros. Serializar con `org.json` (`JSONArray` de `JSONObject`), que ya viene en Android — no agregar dependencias.
   - Dedup por el mismo `matchKey` que usa `AchievementTracker` (`code:startedAtEpochMs:initialPlayerCount`, `AchievementTracker.kt:238-244`) para no duplicar si `record` se llama más de una vez por partida.
   - `lastMatch(context): MatchRecord?` y `lastMatches(context, n = 5): List<MatchRecord>`.
   - Determinar `won` reusando la lógica de `AchievementTracker.didHumanWin(session, human)` (`AchievementTracker.kt:150-159`) — extraerla a un helper compartido o replicarla (es corta).
2. **Hook al terminar**: donde hoy se llama `AchievementTracker.recordMatchIfNeeded(this, session)` (`GameplayMockActivity.kt:~1883`, dentro de `renderGame` cuando hay `winner` y **no** es online), agregar al lado `MatchHistoryStore.record(this, session)`. **Solo local** (mismo gate `!isOnlineGameplay()`). No tocar el online.
3. **UI en el Perfil** (`ProfileActivity` + su layout): agregar una sección "ÚLTIMA PARTIDA" con la tarjeta resumen (mapa + rol + resultado + fecha, con color según ganó/perdió). Al tocarla, abrir un diálogo (mismo estilo de diálogos del proyecto) titulado "ÚLTIMAS PARTIDAS" con las últimas 5 en filas compactas. Si no hay partidas, estado vacío ("Todavía no jugaste ninguna partida."). Ubicarla donde tenga sentido en el perfil (cerca de los logros, que es data de progreso similar).
4. **Sin online, sin red, sin cloud**: todo local en `SharedPreferences`, como los logros. (La sync a la nube / Google Play Games queda para una spec futura.)

**Verificación**: jugar 6 partidas locales variadas → el perfil muestra la última correcta (mapa/rol/resultado/fecha); al tocar, muestra las últimas 5 (no 6) ordenadas de más nueva a más vieja; ganar/perder se refleja bien; una partida abandonada a mitad **no** se guarda (solo con `winner`); el online no genera registros.

---

## Nota para Codex (pensá vos también)
El usuario quiere que, además de esto, propongas lo que se te ocurra que falte o que mejore estos puntos (por ejemplo: si al reproducir el doble-toque encontrás otra causa; si el flip del Alcalde conviene acompañarlo con sonido/aviso; si el historial básico pide algún dato más que sea gratis de guardar). Dejá tus sugerencias marcadas aparte al entregar, sin implementarlas salvo las que sean parte del pedido.

## Fuera de alcance
- Online (nada de historial online, ni tocar el lobby/gameplay online).
- Google Play Games / cloud save (spec futura).
- Assets nuevos: reusar los existentes (sangre, bota).
