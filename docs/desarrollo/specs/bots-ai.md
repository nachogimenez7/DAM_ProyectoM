# Spec — Mejoras de IA de bots (+ volumen relativo de audio)

> Handoff Claude (diseño/review) → Codex (implementación). Código = fuente de verdad. Codex arranca en frío: leer todo. Diff acotado por fase; el usuario valida en Android Studio.

**Contexto:** los bots son **solo de singleplayer** (en online juegan personas) → no afecta la sincronización online. La IA ya es rica: 6 personalidades (`Personality`: TRANQUI/PICANTE/JODON/DESCONFIADO/IMPULSIVO/ANALITICO), agendas, intents, líneas con sabor, decisiones de noche/voto, y reacciones a los mensajes del humano. Es **determinístico** (usa `stableNoise`, no `Math.random`) — **mantener determinismo** (no romper tests existentes en `GameEngineTest`).

**Objetivo (tono elegido: entretenido/caótico):** que el chat sea divertido de mirar y genere caos, sin volverse difícil.

### Anclas de código
- `LocalBotAi`: `chooseVoteTarget` (`:288`), `openingDebateMessages` (`:899`), `votingIntentMessages` (`:981`), `reactionsToHumanMessage` (`:1081`), `lineForIntent` (`:1978`), `personalityFor` (`:2093`), agendas/intents (`:1313+`, `:1531+`).
- Estado: `GameSession.claimLedger: Map<String, List<ClaimRecord>>`; `ClaimRecord(round, phase, roleKey, statementType, target)` (statementType: PROTECTED/INVESTIGATED/REFUSED_ROLE/TRUST/ACCUSE/VOTE).
- Dificultad: `GameSession.botDifficulty` (NORMAL/HARD); ya influye `votePlanSnapshot`/reacciones (tests `:2040`, `:2065`) → **extender, no romper**.
- Inyección de líneas en el chat: `GameplayChatController.stageBotBurstForCurrentPhase()` / `cancelPendingBotChat()`, disparado desde `GameplayMockActivity` al avanzar de fase.

---

## FASE 1 — Reacciones a los sucesos (mayor ROI de inmersión)
Que los bots **comenten los sucesos** en el feed (muerte, expulsión, silencio), justo después del banner de "Dios" del cronista.

- Nueva API: `LocalBotAi.reactionsToEvent(session, event): List<Pair<String,String>>` (speaker → texto), donde `event` describe el suceso (tipo: MUERTE_NOCTURNA / EXPULSION / SILENCIO, y el nombre afectado). Bounded (2-3 reacciones).
- Sabor por personalidad: TRANQUI lamenta/calma, PICANTE acusa al toque, JODÓN bromea, DESCONFIADO sospecha, IMPULSIVO salta, ANALÍTICO deduce ("si lo protegían y murió igual, el médico no está vivo o falló").
- Hook: tras revelar el suceso (AMANECER → muerte/silencio; RECUENTO/expulsión), el `GameplayChatController` **stagea** estas reacciones como un burst corto en el feed, después del banner. Reusar el mecanismo de burst existente.
- No floodear: límite + no reaccionar a cada cosa si ya hubo mucho chat.

**➡️ Revisión de Claude** antes de seguir.

---

## FASE 2 — Acusar con fundamento (usar el claim ledger)
Que las acusaciones/votos se apoyen en lo que **realmente pasó**, no al azar.

- Mejorar `chooseVoteTarget` y las líneas de acusación para considerar el `claimLedger`: contradicciones (defendió y luego acusó / cambió de rol declarado), quién acusó a quién, resultados de investigación públicos, quién quedó callado.
- Líneas que **citan evidencia**: "vos defendiste a Bruno y ahora lo acusás", "nadie le preguntó a Santi todavía", "Mili cambió la historia".
- HARD: deducción más sólida y consistente; NORMAL: usa evidencia a veces, más errático.
- Mantener consistencia con la lógica de voto existente (no romper tests de `chooseVoteTarget`).

**➡️ Revisión de Claude** antes de seguir.

---

## FASE 3 — Variedad / menos repetición
- Variar la semilla de generación por `round`/`phaseIndex` (además de `session.code`+nombre) → el fraseo cambia ronda a ronda y entre partidas. **Sin `Math.random`** (seguir con `stableNoise`).
- Ampliar los pools de plantillas de los intents más usados (ACCUSE/ASK/TEASE/DEFEND/CALM_DOWN) con varias variantes.
- Evitar que dos bots digan casi lo mismo en el mismo burst (deduplicar por similitud simple).

---

## FASE 4 — Personalidad / humor (tono caótico)
- Voces más marcadas y argentinas; el **JODÓN** más gracioso, el **PICANTE** más filoso, el **IMPULSIVO** más impredecible.
- Más interjecciones/risas/muletillas por personalidad (ya existe el flavoring en `:2765+`; enriquecerlo).
- Que el caos sea legible (no insultos gratuitos; humor de mesa de amigos).

---

## CROSS-CUTTING — Dificultad NORMAL/HARD que se note
Extender el efecto ya existente de `botDifficulty`:
- **HARD:** mejor deducción (Fase 2), **traidores coordinan** (no se acusan entre ellos, se cubren), mentiras consistentes, reacciones más afiladas.
- **NORMAL:** más errático y charlatán, deducción más débil, más bardo.
- Mantener los tests de diferencia NORMAL/HARD y agregar cobertura nueva donde aplique.

---

## TAREA EXTRA (audio) — Volumen relativo por sonido
(Acordado aparte; incluido acá para mandar un solo doc.)
- Agregar `relativeVolume: Float = 1f` a `GameSound`; `GameplaySoundEffects.play(context, res, volumeScale = 1f)` aplica `player.setVolume(volume * scale, …)`; el director pasa `sound.relativeVolume`.
- **Solo atenúa** (MediaPlayer no amplifica > original). Los bajos (búho/cartas) se suben re-normalizando el archivo. Valores **a oído** (empezar en 1f, bajar los que molesten).

---

## Reglas de proceso
- Mantener **determinismo** (no `Math.random`) y **no romper** los tests de bots en `GameEngineTest`; agregar tests nuevos por fase.
- Diff acotado por fase. Burst de bots con límites (no saturar el chat / Firestore no aplica acá, es local).

## Documentación a actualizar al cerrar (lo hace Claude tras revisar)
- `docs/desarrollo/decisiones-arquitectura.md` (ADR-05 ampliada: reacciones a sucesos, evidencia, dificultad).
- `docs/general/07-flujo-funcionamiento.md` (bots reaccionan a sucesos / dificultad).
- `docs/desarrollo/backlog.md` (avance IA de bots).
