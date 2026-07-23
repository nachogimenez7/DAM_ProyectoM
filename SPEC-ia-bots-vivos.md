# SPEC — IA de bots viva: ciclo 2 (clímax, noche, variedad)

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). **Continuación** de `SPEC-ia-bots-conversacion.md`: sus 4 fases (lenguaje, memoria persistente, conversación encadenada, vida extra) ya están entregadas y confirmadas en el código actual — no tocar esa base, este spec construye sobre ella.
>
> **Motivo del ciclo:** el usuario quiere mejorar la IA en general (no solo para un trailer puntual), pero hay un trailer próximo que sirve de ocasión para mostrarla — por eso la Fase 1 prioriza lo que más se nota en cámara y es barato/seguro, y la Fase 2 deja documentado lo más ambicioso para después, sin descartarlo.
>
> **Decisiones tomadas con el usuario:**
> 1. La noche sí importa para el trailer — hay tomas de noche/transición día-noche.
> 2. El clímax (revelación de ganador) se resuelve con líneas de texto en el chat, no con animación/arte nuevo.
> 3. La reacción a votos en vivo (la más cara, toca la resolución de la votación) se documenta para un ciclo posterior, no bloquea este.

---

## Diagnóstico (verificado en código, jul 2026)

Tras el overhaul de `SPEC-ia-bots-conversacion.md`, lo que YA funciona bien y no hay que tocar: últimas palabras al ser expulsado (`LocalBotAi.kt:802-805`, `BotDialogueLines.kt:930-955`), interpelar al humano callado (`BotConversationDirector.kt:123-146`), referencias a rondas pasadas / "seguir el hilo" (`BotDialogueLines.kt:873-928`), y el ritmo del chat con typing indicator (`BotConversationDirector.kt:112-117`, `GameplayChatController.kt:2306-2308`).

Tres huecos concretos quedan:

1. **Los bots se apagan en el clímax.** `reactionsToEvent` corta en seco en cuanto hay ganador: `if (session.winner.isNotBlank() || recentBotStreak(session) >= 3) return emptyList()` (`LocalBotAi.kt:521`). Lo mismo `cachedConversationBatch`: `if (session.winner.isNotBlank()) { conversationBatchCache = null; return emptyList() }` (`LocalBotAi.kt:744`). El momento de mayor tensión del juego —y el cierre natural de cualquier trailer— pasa en silencio total de la IA.
2. **Muletilla única por personalidad, sin variedad.** `applyPersonalitySignature` (`BotDialogueLines.kt:1773-1793`) inyecta con baja probabilidad (`seed % 6 != 0` descarta 5/6 veces, línea 1778) pero cada personalidad tiene **una sola frase fija** (TRANQUI→"tranqui,", PICANTE→"sin vueltas,", JODON→sufijo "jaja", DESCONFIADO→"mmm,", IMPULSIVO→"dale,", ANALITICO→"van dos cosas:"). No usa el mecanismo anti-repetición `chooseFreshLine` (`BotDialogueLines.kt:974-987`) que sí usan otras líneas. En una partida de varios minutos (o un trailer grabando de punta a punta) la muletilla se repite textual.
3. **La noche es una pantalla estática para el humano no-traidor.** `BotConversationDirector.canRun` (`BotConversationDirector.kt:17-19`) solo corre en `chatPhases = {DIA_DEBATE, CONTRAPUNTO, VOTACION, DESEMPATE_VOTACION}` (`BotConversationDirector.kt:10-15`) — ninguna de las 5 sub-fases nocturnas (`NOCHE_ASESINO/MERCENARIO/POLICIA/MEDICO/ORACULO`, `GameModels.kt:411-417`) está incluida. `nightSubtitle()` (`GameplayMockActivity.kt:5543-5570`) muestra **un único texto fijo por fase** ("Los Traidores se mueven en silencio.", etc.) y el resto es el botón "ESPERAR". Ya existe chat de traidores en la noche (`nextTraitorNightBeat`, `BotConversationDirector.kt:96-110`), pero solo lo ve el humano si juega de traidor (`canRunVisibleTraitorNight`, `GameplayChatController.kt:2613-2619`). Para aldeano/médico/policía —la mayoría de las partidas— la noche no tiene ninguna señal de actividad. Ya hay música ambiente de noche (`night_phase_music.mpeg`) sonando de fondo, así que el hueco no es de audio: es de que en pantalla no pasa nada.

---

## Fase 1 — Prioridad trailer (bajo riesgo, sin assets nuevos)

### 1.1 Reacción de los bots al ganador/revelación

**Objetivo:** cuando se determina el ganador, 2-3 bots tiran una línea de reacción (sorpresa, bronca, festejo, ironía si el bot traidor gana) antes de que se muestre el resultado.

**Dónde engancharlo:** `GameSession.withWinnerCheck()` (`GameEngine.kt:2317-2327`) es el único lugar donde `winner` pasa de `""` a un valor. Ahí, justo antes de fijar `winner`, generar y commitear las líneas de reacción (mismo patrón que `withBotMessages`, `GameEngine.kt:2300-2308`, que ya usan otros flujos para insertar mensajes de bot al historial):

```kotlin
private fun GameSession.withWinnerCheck(): GameSession {
    val prepared = autoResolveBotDesertorChoice(this)
    val winner = GameRules.winnerFor(prepared)
    if (winner == GameRules.TOWN_WINNER) {
        return prepared.withVictoryReactions(winner).copy(winner = winner)
    }
    if (canDesertorReconsider(prepared)) {
        return prepared.copy(winner = "")
    }
    return prepared.withVictoryReactions(winner).copy(winner = winner)
}
```
Solo generar reacciones si `this.winner.isBlank() && winner.isNotBlank()` (evitar reinyectar en recomputes). `withVictoryReactions` nueva, delega a `LocalBotAi.victoryReactionMessages(session, winner)` y aplica `withBotMessages` igual que los demás flujos.

**Nueva función en `LocalBotAi.kt`** (cerca de `reactionsToEvent`, `LocalBotAi.kt:516-538`, mismo estilo): `victoryReactionMessages(session: GameSession, winner: String, limit: Int = 3): List<Pair<String, String>>`. No reusar `reactionsToEvent` tal cual (ese sigue bloqueado por diseño una vez hay ganador para no seguir opinando de eventos viejos — dejarlo así); esta es una función nueva y específica para el momento de victoria, así no hace falta debilitar el guard existente.

**Tono por caso** (nuevas líneas en `BotDialogueLines.kt`, agrupadas cerca de `eliminationLastWordsLine`, `BotDialogueLines.kt:930-955`, para reusar el mismo patrón de seed por sesión+ronda+bot):
- Bot de pueblo, ganó el pueblo: alivio/festejo ("por fin, sabía que algo raro pasaba con vos" a un traidor descubierto).
- Bot traidor, ganó el pueblo (perdió): frustración con picardía, sin romper personaje si el juego no revela roles a todos ("bueno, esta vez no salió").
- Bot traidor, ganaron los traidores: triunfo contenido o directamente burlón según personalidad (PICANTE se victoriosa fuerte; TRANQUI apenas un "y bueno, así es esto").
- Bufón: si ganó por su condición especial, línea de victoria distinta (ya hay precedente de tono especial para el Bufón en `BotDialogueLines.kt:1763-1770`).
- Usar `finishSpeech` para aplicar personalidad/sanitización como el resto de las líneas.

**Verificación:** jugar hasta el final (pueblo gana y traidores ganan, probar los dos) y confirmar que aparecen 2-3 líneas de bots en el chat justo antes/durante la pantalla de resultado, sin romper el flujo de `showWinnerReveal` (`GameplayMockActivity.kt:7903-7908`).

### 1.2 Muletillas variadas por personalidad

En `applyPersonalitySignature` (`BotDialogueLines.kt:1773-1793`), reemplazar la frase fija por 2-3 variantes por personalidad con rotación anti-repetición (mismo criterio que `chooseFreshLine`, `BotDialogueLines.kt:974-987`, que ya evita que un bot repita literalmente su última línea):

- TRANQUI: "tranqui,", "igual,", "che, tranqui pero...".
- PICANTE: "sin vueltas,", "te lo digo derecho,", "a las claras,".
- JODON: sufijo "jaja", "posta jaja", "en serio (mentira) jaja" — mantener la lógica de no duplicar si `containsLaugh(text)` ya es cierto (línea 1785).
- DESCONFIADO: "mmm,", "ojo,", "no sé eh,".
- IMPULSIVO: "dale,", "va,", "ya está,".
- ANALITICO: "van dos cosas:", "pensándolo bien:", "mirá:".

Mantener el gate de probabilidad (`seed % 6 != 0`, línea 1778) y el límite de largo (110 caracteres) tal cual. Solo cambia la selección de variante dentro de cada personalidad, usando el mismo `seed` que ya recibe la función para elegir determinísticamente sin repetir la anterior usada por ese bot (se puede pasar el contexto/seed de forma análoga a `chooseFreshLine`).

**Test:** iterar `applyPersonalitySignature` con varios seeds consecutivos por personalidad y asertar que no devuelve la misma variante dos veces seguidas.

### 1.3 Noche viva (solo para el humano que no es traidor)

**Restricción de diseño importante:** no se puede mostrar qué jugador específico está actuando de noche — revelaría roles (ej. si se marca a un jugador como "actuando" durante `NOCHE_ASESINO`, eso lo delata como traidor). La solución tiene que ser ambiental, no por-jugador.

**Cambio propuesto — variar `nightSubtitle()`** (`GameplayMockActivity.kt:5543-5570`): hoy devuelve un único string fijo por fase. Cambiar a una lista de 3-4 variantes por fase, rotando cada pocos segundos mientras se espera (reutilizar el mismo `Handler`/patrón de rotación que ya use la pantalla, o un timer simple con `postDelayed`), por ejemplo para `NOCHE_ASESINO`: "Los Traidores se mueven en silencio.", "Se escuchan pasos apagados en la oscuridad.", "Algo se decide lejos de las antorchas.". Esto no requiere assets nuevos (ya hay `night_phase_music.mpeg` de fondo, confirmado en `app/src/main/res/raw/`) — es puramente rotación de texto para que la pantalla no se sienta congelada.

Alcance acotado a esto en Fase 1 (sin tocar `BotConversationDirector.canRun` ni intentar meter chat de bots visible en fases donde el humano no debería ver nada — eso es más riesgoso y no aporta si compromete el secreto de roles). Si más adelante se quiere ir más lejos (ej. un ticker de eventos ambiente tipo "alguien parece nervioso"), documentarlo como Fase 2.

**Verificación:** jugar una noche completa como aldeano/médico/policía y confirmar que el texto cambia un par de veces durante la espera, sin romper el flujo hacia el amanecer.

---

## Fase 2 — Backlog general (no bloquea el trailer, documentado para después)

### 2.1 Reacción a votos en vivo
La votación se resuelve toda de una sola vez — no hay ningún punto intermedio donde "a un bot le caen 2 votos" antes del resultado final: `resolveVotingInternal` (`GameEngine.kt:674-725`) arma el mapa completo de votos en un paso y recién ahí llama `recordVotes` (`GameEngine.kt:2119-2132`); mismo patrón en `resolveVotingWithRecordedVotes`/`resolveTieVotingWithRecordedVotes` (`GameEngine.kt:602-672`). Para que un bot se defienda al acumular votos hace falta un cambio estructural: resolver votos incrementalmente (o simular un conteo parcial visible) en vez de todo de un paso. Es la escena de mayor tensión dramática del juego, pero también la más cara — evaluar en un ciclo separado, con su propio diseño de cómo exponer el conteo parcial sin romper el resto del flujo de votación.

### 2.2 Bots que usan emotes
`EmoteCatalog` (`EmoteCatalog.kt`) hoy solo lo usa el humano (perfil, loadout, reacciones online humano-a-humano en `GameplayChatController.kt:282-318`). Cero uso desde `LocalBotAi.kt`/`BotDialogueLines.kt`/`BotConversationDirector.kt`. Diseñar: qué emotes le quedan bien a cada personalidad, con qué frecuencia (barato, no debe saturar), y si reemplazan o acompañan una línea de texto.

### 2.3 Pares de afinidad estables entre bots
Hoy lo único parecido es `allies` (compañeros de equipo traidor) en `chooseVoteTarget` (`LocalBotAi.kt:436-440`) — no hay afinidad social persistente entre dos bots de pueblo cualesquiera. Requeriría un nuevo estado derivado (ej. semilla por partida que fije 1-2 pares "que se banquen") y líneas que referencien esa relación.

### 2.4 Reacción a emotes que manda el humano
No existe ningún `onEmoteReceived` ni lógica de bot reaccionando al `emoteId` del humano. Depende de 2.2 (una vez que los bots "hablan" en emotes, tiene sentido que también los lean).

### 2.5 `silentHumanPrompt` sensible a dificultad
El spec original (`SPEC-ia-bots-conversacion.md`, ítem 4.4) pedía que la presión al humano callado sea configurable por dificultad ("en HARD lo presionan más"). Hoy `pauseAfterBotStreak` (`BotConversationDirector.kt:40-42`) no lee `session.botDifficulty` en absoluto. Menor, pendiente.

---

## Orden de entrega
1. **Fase 1** completa (1.1 + 1.2 + 1.3) — impacto directo en el trailer, riesgo bajo, sin dependencias de assets.
2. **Fase 2** — ítems independientes entre sí, sin apuro, para seguir profundizando la IA después del trailer. 2.1 (votos) es la de mayor impacto restante; 2.2-2.4 son una cadena (emotes de bots habilita reacción a emotes del humano).
