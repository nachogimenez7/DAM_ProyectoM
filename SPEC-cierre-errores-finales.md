# SPEC — Cierre de errores finales (Payador/Bufón bot, muerte del humano, AFK, Desertor, docs)

> Para Codex. Español, `archivo:línea`. **No compilar** (el usuario valida en Android Studio). Pasada de cierre: seis arreglos chicos e independientes para terminar de pulir el modo local. Cada parte compila y es jugable por sí sola; respetar el orden de entrega del final.
>
> **Decisiones ya tomadas con el usuario (no re-preguntar):**
> 1. **Bufón bot: balanceado** — provoca y se hace el raro, pero no gana siempre; a veces lo matan de noche o zafa. No debe expulsarse en todas las partidas.
> 2. **AFK: se desactiva solo en local** — ausencia = abstención/sin acción, nunca expulsión. (Ver nota técnica en la Parte 4: el online ni siquiera pasa por ese código.)
> 3. **Muerte del humano / victoria del Bufón: cartel + elección** — "Seguir mirando" (acelera hasta el final) o "Volver al menú".
> 4. **Payador bot: solo con buen material** — abre el Contrapunto únicamente si hay un conflicto/contradicción real entre dos jugadores; si no aparece, no lo malgasta (puede no usarlo en toda la partida).
>
> **Aclaración de reglas (la Espía NO hereda nada):** la Espía es una killer normal desde la ronda 1 — elige la víctima **junto a los asesinos todas las noches** (`GameRules.killerRoleKeys = {asesino, espia}`, `GameModels.kt:414`) y lo único especial es que `investigationResult` la marca **inocente** ante el detective (`GameEngine.kt:1982-1990`). **No hay "sucesión automática" ni "co-ejecutor estilo Padrino"**: eso es narrativa vieja del doc que describe con palabras engañosas algo que es puramente emergente (siempre fue killer). La Parte 6 corrige esa redacción en la documentación.

---

## Parte 1 — Payador bot: Contrapunto solo con material real

### Diagnóstico
Hoy en `GameEngine.resolveDayDebate` (`GameEngine.kt:423-451`) un Payador bot dispara el Contrapunto **el primer día que llega a `DIA_DEBATE`**, eligiendo a los dos participantes **por orden alfabético** (`sortedBy { it.name }.take(2)`), y en `resolveContrapunto` (`GameEngine.kt:557-561`) el bot señala siempre al **primero** (`session.contrapuntoPlayers.firstOrNull()`). Resultado: toda partida en Pampa con Payador bot repite el mismo guion (Contrapunto entre los dos primeros alfabéticos, +1 de voto casi arbitrario). Además, saltear el debate con "listos para votar" también pasa por `resolveDayDebate` (`GameplayMockActivity.kt:3648`), así que el botón de acelerar te mete igual en el Contrapunto.

### Arreglo
El Payador bot debe abrir el Contrapunto **solo cuando hay tensión real** entre dos jugadores, elegir a esos dos, y señalar al que su lectura marca más sospechoso.

**1.1 — Nuevo helper en `LocalBotAi` (fachada pública).** Agregar:
```kotlin
// Devuelve el par de jugadores para el Contrapunto, o null si no hay material suficiente.
fun chooseBotContrapuntoPair(session: GameSession, payador: GamePlayer): Pair<String, String>?
```
Lógica sugerida (reusar helpers existentes, todos ya disponibles en el mismo paquete):
- Candidatos = `GameEngine.alivePlayers(session).filter { it.name != payador.name }`. Si hay menos de 2, devolver `null`.
- **Preferencia A (conflicto directo):** buscar un jugador con `publicContradiction(session, name) != null` (`BotConversationMemory.kt:371`). Emparejarlo con su antagonista natural: alguien que lo acusó (`conversationMemory(session)[name]?.accusedBy`) o a quien él acusó (`...accusedTargets`), que esté vivo y sea != payador. Si existe el par, devolverlo.
- **Preferencia B (dos sospechosos calientes):** tomar `rankedPublicSuspects(session, payador)` (`LocalBotAi.kt:1561`); si los **dos primeros** tienen `score >= UMBRAL` (sugerido 6, alineado con `isWeakSuspicion`, `BotConversationMemory.kt:81`), devolver ese par.
- Si no se cumple ni A ni B, devolver `null` (el Payador **no** usa el Contrapunto esta ronda; está bien que no lo use nunca si nunca hay material).

**1.2 — Usar el helper en `resolveDayDebate`** (`GameEngine.kt:426-438`). Reemplazar el bloque alfabético por:
```kotlin
val botPayador = alivePlayers(session).firstOrNull { it.role?.key == "payador" && !it.isHuman }
if (botPayador != null && !session.payadorUsed) {
    LocalBotAi.chooseBotContrapuntoPair(session, botPayador)?.let { (first, second) ->
        val afterFirst = chooseContrapuntoPlayer(session, first)
        return chooseContrapuntoPlayer(afterFirst, second)
    }
}
```
Si el helper devuelve `null`, sigue el flujo normal (transición a `VOTACION`), que es lo que ya hace el resto de la función.

**1.3 — Señalamiento del bot en `resolveContrapunto`** (`GameEngine.kt:557-561`). Reemplazar `session.contrapuntoPlayers.firstOrNull().orEmpty()` por una elección por sospecha:
```kotlin
val selected = if (payador.isHuman) {
    suspiciousPlayer.takeIf { it in session.contrapuntoPlayers }.orEmpty()
} else {
    LocalBotAi.chooseBotContrapuntoSuspect(session, payador, session.contrapuntoPlayers)
}
```
Nuevo helper:
```kotlin
// De los dos participantes, el que la lectura del payador marca más sospechoso.
fun chooseBotContrapuntoSuspect(session: GameSession, payador: GamePlayer, participants: List<String>): String
```
Implementación: de `rankedPublicSuspects(session, payador)` tomar el primero cuyo `player.name in participants`; fallback `participants.firstOrNull().orEmpty()`.

### Verificación
- Test (`GameEngineTest`): con una mesa sin contradicciones ni sospechas fuertes, `resolveDayDebate` de un Payador bot transiciona directo a `VOTACION` (no entra en `CONTRAPUNTO`).
- Test: sembrando una contradicción de rol en el `claimLedger` de un jugador (y un acusador vivo), `resolveDayDebate` abre `CONTRAPUNTO` con ese par.
- Test: `chooseBotContrapuntoSuspect` devuelve el participante con mayor score y no el primero alfabético.
- Manual (Pampa, Payador bot): varias partidas seguidas ya **no** repiten el mismo Contrapunto; a veces no hay Contrapunto.

---

## Parte 2 — Bufón bot: hacerse expulsar (balanceado)

### Diagnóstico
No hay **ninguna** lógica de Bufón en la IA (grep de `bufon`/`BUFON` en `LocalBotAi.kt` y `Bot*.kt` = 0 resultados). Un bot con rol `bufon` juega como aldeano: no molesta, no se hace odiar y nunca persigue su única condición de victoria (que lo expulsen). En Medieval, el rol exclusivo del mapa es un asiento muerto cuando lo tiene un bot. Además, el guard `isSelfAccusatoryLine` en `finishSpeech` (`BotDialogueLines.kt:1723-1727`) **reemplaza** cualquier línea auto-incriminatoria por un fallback neutro — justo lo contrario de lo que el Bufón necesita.

### Arreglo (balanceado — que NO gane siempre)
El Bufón es `Neutral`, así que `isTraitor(bufon)` es `false` y ya pasa por los caminos de diálogo del pueblo. Se le agrega una veta provocadora y se lo deja auto-incriminarse, con frecuencia moderada.

**2.1 — Permitir la auto-incriminación del Bufón en `finishSpeech`** (`BotDialogueLines.kt:1723`). Cambiar:
```kotlin
val guarded = if (isSelfAccusatoryLine(safe, session, bot)) {
    neutralSelfAccusationFallback(session, bot, context)
} else {
    safe
}
```
por:
```kotlin
val guarded = if (bot.role?.key != RoleCatalog.BUFON && isSelfAccusatoryLine(safe, session, bot)) {
    neutralSelfAccusationFallback(session, bot, context)
} else {
    safe
}
```
(El Bufón es el único rol al que se le permite tirarse tierra encima.)

**2.2 — Líneas del Bufón.** En `BotDialogueLines.kt`, agregar una lista/generador de líneas provocadoras, molestas y auto-incriminatorias, en la voz argentina informal del juego (sin emojis — ver `SPEC-mejoras-post-playtest.md` §1). Dos registros:
- **Provocación/molestia** (para el debate): interrumpir, exagerar, mandar fruta, picar a otros sin cerrar nada.
- **Auto-incriminación** (para cuando lo acusan o para levantar la mano): "sáquenme a mí total no aporto nada", "yo sería el traidor perfecto y nadie se da cuenta", "voten al más raro… ah, ese soy yo", etc.

Mantener el tono del Bufón de `RoleCatalog` (`RoleCatalog.kt:100-101`, `331`: "molesta, interrumpe y se esfuerza por caer mal").

**2.3 — Inyectar en `roleDrivenLine`** (`BotDialogueLines.kt:1126-1164`, dispatch en `:1145-1156`). Agregar una rama:
```kotlin
roleKey == RoleCatalog.BUFON -> jesterProvocationLines(session, target, pressure, seed)
```
y ajustar el `shouldSpeak` (`:1158-1163`) para que el Bufón hable un poco más seguido que un rol normal pero **no siempre** (balanceado): p. ej. `roleKey == BUFON -> pressure || seed % 3 == 0`. **No** subirlo a "cada ronda" (eso lo haría ganar siempre).

**2.4 — Cuando lo acusan, que redoble en vez de defenderse.** En `LocalBotAi.reactionsToHumanMessage` (`LocalBotAi.kt:852-877`), las ramas `messageIntent == ACCUSE && focusNames.contains(bot.name)` y `focusNames.contains(bot.name)` hoy llaman `defensiveLine(...)`. Anteponer una rama para el Bufón que devuelva una línea de auto-incriminación/agradecimiento ("gracias, justo quería que me miren") en vez de defenderse. Idea de guard:
```kotlin
focusNames.contains(bot.name) && bot.role?.key == RoleCatalog.BUFON -> jesterEmbraceAccusationLine(session, bot, seed)
```
colocada **antes** de las ramas `defensiveLine`.

**2.5 — Calibración (importante para "no gana siempre").** El scoring de los demás bots ya castiga esquivar/contradecir/acusar, así que con 2.1–2.4 el Bufón sube su propio score solo. Para que quede balanceado y no se vuelva imparable:
- No hacer que el Bufón se auto-incrimine en **todas** las intervenciones (respetar el `seed % 3` y el gate de `pressure`).
- **No** tocar la selección de víctima nocturna de los traidores: si los traidores lo matan de noche, el Bufón **pierde** (es parte del balance; su victoria es solo por expulsión — `GameEngine.kt:959-978`). No agregar ninguna protección para el Bufón.
- El voto propio del Bufón: dejarlo con la lógica actual (`chooseVoteTarget`), no hace falta tocarlo.

### Verificación
- Test (`BotDialogueLines`/nuevo): una línea auto-incriminatoria de un bot **Bufón** sobrevive `finishSpeech` (no la reemplaza el fallback), y la misma línea en un bot **no-Bufón** sí se reemplaza.
- Manual (Medieval, Bufón bot, sin que sea el humano): en varias partidas el Bufón **a veces** se hace expulsar y a veces no (muere de noche o zafa). Que no gane en todas.
- Manual: el Bufón bot molesta/provoca en el debate sin volverse un muro de texto (respeta anti-racha `recentBotStreak`).

---

## Parte 3 — Muerte del humano / victoria del Bufón: cartel + elección

### Diagnóstico
Cuando el humano muere (de noche, por expulsión o por AFK) o **gana como Bufón** (victoria especial que **no** termina la partida — `GameEngine.kt:959-987`, decisión intencional), la partida sigue en **tiempo real**: el humano mira debates de 120s y votaciones de 20s hasta el final, sin poder chatear (`canHumanChat` exige estar vivo) y sin salida rápida. Falta un cartel con dos opciones.

### Arreglo
Un único punto de decisión que se dispara una sola vez cuando el humano ya no está vivo y la partida no terminó (solo **local**; en online el invitado ya espera el estado del host).

**3.1 — Flag de una sola vez.** En `GameplayMockActivity`, agregar `private var spectatorChoiceOffered = false` (persistir en `onSaveInstanceState`/restore junto a los otros flags booleanos, `GameplayMockActivity.kt:1028-1076` y el bloque de restore correspondiente).

**3.2 — Detección y cartel.** Nueva función:
```kotlin
private fun maybeOfferSpectatorChoice() {
    if (spectatorChoiceOffered) return
    if (isOnlineGameplay()) return                     // online: el invitado ya espera al host
    if (session.winner.isNotBlank()) return            // el fin de partida tiene su propia pantalla
    if (GameEngine.humanPlayer(session).alive) return
    if (isBlockingGameplayUiActive()) return           // no encimar reveals/overlays (GameplayMockActivity.kt:5489)
    spectatorChoiceOffered = true
    // AlertDialog no cancelable con dos acciones:
    //  - "SEGUIR MIRANDO" -> enterSpectatorFastForward()
    //  - "VOLVER AL MENÚ"  -> returnToLobby()  (ya existe, GameplayMockActivity.kt:6537)
}
```
Copy del cartel: si el humano tiene una `GameSpecialVictory` con `roleKey == RoleCatalog.BUFON` (`session.specialVictories.any { it.playerName == humano && it.roleKey == BUFON }`) → título "¡Ganaste como Bufón!" y cuerpo "El pueblo te expulsó. Podés quedarte a ver cómo termina o volver al menú."; si no → título "Te eliminaron" y cuerpo "Podés quedarte a mirar la partida o volver al menú." Usar el estilo de diálogo del juego (medieval/dorado) coherente con los `AlertDialog` existentes (p. ej. el del Desertor, `GameplayMockActivity.kt:6775`+).

**3.3 — Fast-forward de espectador.**
```kotlin
private fun enterSpectatorFastForward() {
    session = session.copy(quickTestMode = true)
    scheduleAutoAdvanceIfNeeded()
    renderGame()
}
```
Reusar `quickTestMode` es el lever mínimo y correcto: `GameEngine.shouldAutoAdvance` (`GameEngine.kt:1333`) exige `quickTestMode && !requiresHumanInput`, y con el humano muerto `requiresHumanInput` es `false`, así que las fases autoavanzan con los delays de transición. Los reveals de muerte/silencio/victoria **siguen mostrándose** (los dispara `renderGame`, y `scheduleAutoAdvanceIfNeeded` ya pausa el auto-avance mientras hay un overlay, `GameplayMockActivity.kt:4544-4563`), así que el espectador no se pierde los momentos.
- Verificar que activar `quickTestMode` a mitad de partida no tenga efectos colaterales: el sesgo "matar al humano" de `chooseAssassinTargetWithoutPlan` (`LocalBotAi.kt:278-281`) es inocuo porque el humano ya está muerto (no es candidato válido). No toca los flags de debug.

**3.4 — Llamar `maybeOfferSpectatorChoice()`** desde:
- `resumeGameFlowAfterBlockingUi()` (`GameplayMockActivity.kt:5724`) — después de que se cierran los reveals (cubre muerte nocturna y expulsión del humano).
- `dismissJesterVictory()` (`GameplayMockActivity.kt:6357-6363`), después del `hide()` — cubre la victoria del Bufón humano (que además está expulsado, así que la condición "no vivo" se cumple).
- Al final de `renderGame()` como red de seguridad (el `spectatorChoiceOffered` evita duplicados).

### Verificación
- Manual: morir de noche como humano → aparece el cartel; "Seguir mirando" acelera y se ven los reveals; "Volver al menú" sale limpio.
- Manual: ganar como Bufón humano (Medieval, hacerse expulsar) → cartel con copy de victoria; el juego no queda clavado en tiempo real.
- Manual: rotar el teléfono con el cartel resuelto no lo vuelve a mostrar (flag persistido).

---

## Parte 4 — AFK: no expulsar en local

### Diagnóstico
`registerHumanAfkMiss` (`GameEngine.kt:1471-1517`) expulsa al humano tras **2 ausencias consecutivas** (`val expelled = nextStreak >= 2`, `:1478`) y le avisa "serás expulsado por AFK". En una partida single-player contra bots, un jugador que se queda leyendo el chat puede quedar eliminado de su propia partida.

**Nota técnica (verificada):** este código es **solo local**. En online, `onCountdownExpired` (`GameplayMockActivity.kt:4671-4699`) resuelve por `resolveOnlineNightWindowFromFirestore` / `resolveOnlineVotingFromFirestore` (host) o espera al host (invitado), y **nunca** llama a `GameEngine.resolveHumanTimeout` → `registerHumanAfkMiss`. O sea: el online ya trata la ausencia como abstención (coincide con `docs/firebase-online-schema.md:150`). Desactivar la expulsión acá afecta **solo al local**, que es exactamente lo pedido.

### Arreglo
**4.1 — Flag explícito en `GameSession`** (`GameModels.kt:5-60`, junto a los otros booleanos): `val afkExpulsionEnabled: Boolean = false`. Default `false` = local seguro. (Autodocumenta la intención y deja la puerta abierta si alguna vez se cablea AFK server-side.)

**4.2 — Respetar el flag en `registerHumanAfkMiss`** (`GameEngine.kt:1478`):
```kotlin
val expelled = session.afkExpulsionEnabled && nextStreak >= 2
```

**4.3 — Hint sin amenaza cuando no se expulsa.** En la rama `if (!expelled)` (`GameEngine.kt:1496-1507`), si `!session.afkExpulsionEnabled` el mensaje **no** debe amenazar con AFK. Sugerido:
```kotlin
privateHint = if (session.afkExpulsionEnabled) {
    "Perdiste tu $action. Si vuelves a ausentarte en tu $nextOpportunity, serás expulsado por AFK."
} else {
    "Perdiste tu $action de esta ronda."
}
```
El resto del flujo (la fase avanza tratando al humano como abstención/sin acción) ya funciona con `expelled = false` y no necesita cambios.

**4.4 — No hace falta cablear `afkExpulsionEnabled = true` en ningún lado** (el online no pasa por este código, ver nota). Dejarlo en `false` en todos los constructores.

### Verificación
- Test (`GameEngineTest`): con `afkExpulsionEnabled = false`, dos timeouts nocturnos consecutivos del humano **no** lo matan (`players` sigue con el humano `alive = true`) y la fase igual avanza.
- Test: el `privateHint` tras un miss en local no contiene "AFK".
- Manual (local): quedarse quieto dos votaciones seguidas ya no te expulsa de tu propia partida.

---

## Parte 5 — Desertor bot: sacarlo del determinismo

### Diagnóstico
El bando inicial del Desertor bot sale de `sessionCode.hashCode() and 1` (`initialDesertorTeam`, `GameModels.kt:841-845`) y el código de las partidas locales es **siempre** `"SALA-01"` (`GameModels.kt:496`). Resultado: el Desertor bot arranca en el **mismo bando en todas las partidas locales**. Y su reconsideración (`autoResolveBotDesertorChoice`, `GameEngine.kt:2244-2264`) es puro oportunismo (siempre el bando que va ganando), también sin variación. Nadie lo "ve", pero sesga todas las partidas igual.

### Arreglo
**5.1 — Semilla variable por partida (lo importante).** En `initialDesertorTeam` (`GameModels.kt:841-845`), reemplazar la semilla por una que cambie entre partidas. El reparto ya barajó los roles (`roles.shuffled()`, `GameModels.kt:578`), así que el **orden de roles asignados** es aleatorio por partida. Usar eso como semilla:
```kotlin
private fun initialDesertorTeam(players: List<GamePlayer>, sessionCode: String): String {
    val desertor = players.firstOrNull { it.role?.key == "desertor" } ?: return ""
    if (desertor.isHuman) return ""
    val seed = players.joinToString("|") { "${it.name}:${it.role?.key.orEmpty()}" }.hashCode()
    return if (seed and 1 == 0) GameRules.TOWN_WINNER else GameRules.TRAITOR_WINNER
}
```
(El mismo cambio conviene en la copia online `OnlineMatchSessionBuilder.initialOnlineDesertorTeam`, `OnlineMatchSessionBuilder.kt:186-190`, por consistencia — aunque el online no reparte Desertor hoy.)

**5.2 — Reconsideración con un poco de lealtad (polish opcional).** En `autoResolveBotDesertorChoice` (`GameEngine.kt:2244-2264`), hoy el bot siempre se pasa al bando ganador. Para que no sea 100% mecánico, agregar una chance chica de **mantener su bando inicial** por "lealtad", con semilla estable:
```kotlin
val loyaltyStay = stableNoise("${session.code}:${session.round}:desertor-loyalty") % 4 == 0
val selectedTeam = if (loyaltyStay && session.desertorTeam.isNotBlank()) {
    session.desertorTeam
} else if (traitors >= town) GameRules.TRAITOR_WINNER else GameRules.TOWN_WINNER
```
No cambiar nada más (sigue marcando `desertorChangedTeam = true` para no reconsiderar de nuevo).

**Importante:** **no** filtrar el bando del Desertor bot en el chat ni en anuncios públicos (sería un leak). El arreglo es interno; la "visibilidad" que se busca es que deje de sesgar todas las partidas igual, no exponer su bando.

### Verificación
- Test: `initialDesertorTeam` da bandos distintos para dos listas de jugadores con distinto orden de roles (aunque el `sessionCode` sea el mismo `"SALA-01"`).
- Manual: en varias partidas locales con Desertor bot (9+ jugadores), el resultado del Desertor deja de ser idéntico partida tras partida.

---

## Parte 6 — Documentación que no contradiga al código (el código manda)

Corregir las discrepancias donde la doc quedó vieja. **En todos los casos el código es la fuente de verdad.**

**6.1 — Firebase Auth (existe auth anónima; las reglas la exigen).** Hoy `OnlineTempIdentity.ensureAuthenticated` hace `signInAnonymously` (`OnlineTempIdentity.kt:28-41`) y `firestore.rules` exige `request.auth.uid` en cada escritura (`firestore.rules:286,319,352`). Corregir el "sin Firebase Auth":
- `CLAUDE.md` (sección Project, la frase "sin Firebase Auth, App Check ni Cloud Functions todavía") → "…con **Firebase Anonymous Auth** para identidad, pero sin App Check ni Cloud Functions todavía".
- `docs/firebase-online-schema.md:3` ("todavia no hay Firebase Auth…") → aclarar que **sí** hay auth **anónima** (las reglas usan `request.auth.uid`); lo que falta es Auth con cuentas reales, App Check y Cloud Functions.
- `docs/general/01-vision-objetivos-alcance.md:45` (bullet "…hoy se usa `uidTemporal` sin login real") → matizar: hay login **anónimo** (el `uidTemporal` = uid anónimo de Firebase); falta login con cuenta.
- `ESTADO_ACTUAL.md` §7 (las líneas que dicen "no usan `request.auth.uid`" / "sin Auth fuerte") → corregir a "auth **anónima** presente; las reglas atan escrituras a `request.auth.uid`; falta Auth con cuentas + App Check + Cloud Functions".
- `docs/desarrollo/backlog.md` F3 y N1: aclarar que N1 es "Auth **con cuentas reales**" (hoy ya hay anónima), no "Auth desde cero".

**6.2 — Espía (mata como asesina; sin sucesión/herencia).** Reescribir donde diga "sucesión automática", "co-ejecutor estilo Padrino" o que "sigue matando cuando caen los asesinos" como si fuera algo especial. Redacción correcta: **"Traidora killer: elige la víctima junto a los asesinos todas las noches desde el inicio; lo único distinto es que ante la investigación del detective aparece inocente."**
- `docs/general/02-mecanicas.md:44` (fila Espía "co-ejecutor, comparte la fase del Asesino") y `:58` (la oración "Si caen todos los Asesinos, sigue matando por sí mismo (la sucesión es automática…)") → sacar la idea de sucesión; queda "mata como asesina + inocente ante el detective".
- `docs/general/01-vision-objetivos-alcance.md:52` → misma corrección.
- `docs/desarrollo/backlog.md` F1 → misma corrección (sacar "sucesión automática" / "estilo Padrino").
- `ESTADO_ACTUAL.md` §3 (fila Espía "co-ejecutor") → alinear.
- (La `function` del `RoleCatalog.kt:75` y el `privateRoleHint` del `GameEngine.kt:993-997` ya están bien: no los toques.)

**6.3 — Paridad del Desertor (siempre cuenta como oposición viva).** `docs/general/02-mecanicas.md:18` dice "sumando al Desertor si eligió Pueblo". El código lo suma **siempre**, aunque haya elegido Traidores: `townForParity = alive.count { it.role?.team == TOWN_WINNER || it.role?.key == "desertor" }` (`GameModels.kt:429`). Corregir el doc a: **"El Desertor vivo siempre cuenta como oposición viva para la paridad (aun si eligió Traidores); por eso apoyar a los traidores no acelera su victoria por número."** Referenciar el test `desertorSupportingTraitorsStillCountsAsLivingOppositionForParity` (`GameEngineTest.kt:2918`).

**6.4 — Contrapunto: habla solo el par señalado.** `docs/general/02-mecanicas.md:61` dice "Solo ellos y el Payador pueden hablar". El fix pre-trailer sacó al Payador (`GameEngine.kt:1244-1247`, `canParticipateInChat`). Corregir a: **"Durante el Contrapunto solo pueden hablar los dos jugadores señalados; el Payador escucha y al final marca a uno."**

**6.5 — `quickTestMode` default en local.** `docs/general/02-mecanicas.md:121` dice que las partidas locales arrancan con `quickTestMode = true`. El código lo pone en **`false`** (`LocalGameFactory.createSession`, `GameModels.kt:500`). Corregir el doc a `false` (el Modo Test Rápido es opt-in desde Opciones avanzadas del lobby).

**6.6 — (tras implementar las Partes 1–5) actualizar el snapshot.** En `ESTADO_ACTUAL.md` y `docs/desarrollo/backlog.md`:
- Payador bot: ya no elige alfabético (Parte 1).
- Bufón (backlog F2): el bot ahora persigue su victoria social (Parte 2); sigue sin acción **nocturna** (eso es correcto y por diseño).
- AFK: en local ya no expulsa (Parte 4).
- Desertor bot: bando inicial ya no es determinista (Parte 5).

### Verificación
- Lectura cruzada: ninguna de las líneas citadas arriba sigue afirmando lo viejo. Ningún doc dice "sin Firebase Auth" a secas ni "sucesión automática" del Espía.

---

## Orden de entrega sugerido
1. **Parte 4 (AFK)** y **Parte 5 (Desertor)** — cambios chicos y de riesgo casi cero; dejan el motor ordenado.
2. **Parte 1 (Payador bot)** — helper nuevo + dos call sites; con tests.
3. **Parte 3 (muerte/espectador)** — UI + fast-forward; probar los tres caminos de muerte + victoria Bufón.
4. **Parte 2 (Bufón bot)** — la más "de sensación"; calibrar que no gane siempre.
5. **Parte 6 (docs)** — al final, para reflejar 1–5 ya hechos y cerrar las contradicciones históricas.

## Recordatorios
- No compilar (valida el usuario en Android Studio).
- Mantener la API pública de `LocalBotAi` como fachada (los helpers nuevos son `fun` en `LocalBotAi`; el resto, `internal fun` de nivel de archivo en el mismo paquete).
- Sin emojis en las líneas de bots (`SPEC-mejoras-post-playtest.md` §1).
- Conservar identidad medieval/dorada en el cartel de la Parte 3.
