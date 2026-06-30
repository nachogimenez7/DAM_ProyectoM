# Spec — Bots IA: corrección de bugs (ronda 2)

> Handoff Claude (review/diseño) → Codex. Código = fuente de verdad. Diff acotado; mantener **determinismo** (`stableNoise`, no `Math.random`) y no romper tests. Bots = solo singleplayer.

## Contexto
La ronda anterior de mejoras de bots ya está implementada (`reactionsToEvent`, `rankedPublicSuspects`, `coordinationLine`, etc.). Quedaron **bugs de coherencia** visibles en partida.

## Diagnóstico (con evidencia)
Captura real:
- THIAGO: "eso ya es una punta con mora, no la dejemos colgada"
- MORA: "dale, eso ya es una punta con mora, no la dejemos colgada"  ← **Mora copió la línea de Thiago y acuerda una punta contra sí misma**

Causas confirmadas leyendo el código:
1. **`rankedPublicSuspects` (`:3001`) y `speechTarget` (`:3016`) SÍ excluyen al propio bot** → la generación base no es el problema.
2. **Falta dedup de la tanda:** `openingDebateMessages` (`:955`) y `votingIntentMessages` (`:1037`) devuelven el `mapIndexed` **sin** `.distinctBy` (a diferencia de `reactionsToEvent` `:952`, que sí deduplica) → líneas repetidas textual entre bots.
3. **Eco en "seguir/acordar":** la rama `botToBotLine` (`:1017-1018`) y/o las líneas tipo "dale, …" reproducen el texto del hablante anterior **sin** verificar que el target mencionado no sea el propio bot → un bot termina acordando sospecha contra sí mismo.

## Fixes

### F1 — Dedup de tanda (rápido, alto impacto)
- En `openingDebateMessages` y `votingIntentMessages`, agregar al final el mismo patrón que `reactionsToEvent`:
  `.distinctBy { normalizedForParsing(it.second).take(42) }`
- Además, evitar que dos bots en la misma tanda usen exactamente el mismo `target`+plantilla (variar índice/semilla si colisiona).

### F2 — Guardia anti-auto-incriminación (robusto)
- Ningún bot debe emitir una línea cuyo **target acusatorio sea él mismo**. Implementar un guard final en `finishSpeech`/`sanitizeBotSpeech`:
  - Si la línea, tras conectores acusatorios ("a", "con", "voto a", "punta con", "nombro a", "sospecho de"), nombra al **propio hablante** → regenerar con un target válido (≠ self) o reemplazar por una línea neutral ("prefiero escuchar una respuesta más antes de cerrar").
- Aplica a TODAS las fuentes de línea (opening, voting, botToBot, agree).

### F3 — "Acordar" sin copiar textual
- La línea de seguir/acordar (`botToBotLine` y "dale,…") debe **parafrasear**, no reproducir el texto anterior; y **saltearse** si el target de la línea previa es el propio bot (no podés "acordar" tu propia punta).
- Si el bot quiere apoyar a quien lo acusa a él, debe **defenderse** en vez de acordar (reusar Intent.DEFEND).

### F4 — Coherencia básica
- Verificar que en cada línea el `$target` nombrado sea el sospechoso real (no el hablante, no un muerto).

## Comportamiento esperado (lo que pidió el usuario)
- **Si el jugador habla:** los bots responden en base a su mensaje (`reactionsToHumanMessage` ya existe — confirmar que el `GameplayChatController` la dispara al enviar el humano).
- **Si el jugador no habla:** los bots siguen su rumbo y **discuten entre ellos** de forma coherente (opening/voting/botToBot ya existen; quedan coherentes tras F1-F4).
- Nada de líneas sin sentido ni auto-acusaciones ni repeticiones textuales.

## Tests (agregar)
- Una tanda de `openingDebateMessages`/`votingIntentMessages` **no tiene líneas duplicadas**.
- Ningún bot emite una línea que lo nombre a sí mismo como sospechoso/target.
- (Si es viable) un bot acusado responde defendiéndose, no "acordando".

## Nota suelta (no es bug)
- Separador "DIA N": el código ya hace `"DIA ${round}"` (`GameplayChatController:714`) → si se ve "DIA N" es build viejo; verificar al recompilar.

## Documentación a actualizar al cerrar (Claude)
- `docs/desarrollo/decisiones-arquitectura.md` (ADR-05) y `docs/desarrollo/backlog.md` (IA de bots: coherencia).
