# SPEC — Emotes online + verificar 12 jugadores

> Para Codex. Español, `archivo:línea`. **No compilar** (el usuario valida en Android Studio). Dos frentes independientes del modo **online**: (1) sincronizar los emotes durante el gameplay, (2) confirmar que 12 jugadores funciona (ya está soportado, es verificación + pulido responsive puntual).
>
> **Secuencia**: esta spec toca `GameplayMockActivity` y `GameplayChatController`, igual que `SPEC-gameplay-fixes-e-historial.md`. **No implementar hasta que esa primera spec esté compilada y validada por el usuario**, para no encimar dos tandas sin compilar sobre los mismos archivos.

## Contexto verificado

- **Emotes: infra completa, gateada off online.** El cooldown y el límite por ronda ya existen (`GameplayReactionLimiter`: `DEFAULT_MAX_PER_ROUND = 2`, `DEFAULT_COOLDOWN_MS = 10_000L`). La paleta, la burbuja (`showReactionBubble`, `GameplayMockActivity.kt:3823`) y el gating por fase (`isPublicReactionPhase`) andan en local. El online está bloqueado a propósito en `reactionBaseUnavailableMessage` (`GameplayMockActivity.kt:3759`: `isOnlineGameplay() -> "Los emotes online todavía no están sincronizados."`).
- **Cada emote tiene un `id` estable** (`EmoteCatalog`, ej. `"griego_enojado"`) — ese es el identificador que viaja por la red.
- **El chat online ya usa RTDB** en `salas/{roomId}/chat` (público), `chat_traidores`, `chat_espectadores` (`GameplayChatController.kt:1516-1788`, constantes `RTDB_*_CHAT_NODE` en `:2490-2492`). Cada mensaje lleva `matchId`, `speaker`, `mensaje`, `isGod`. **Este es el patrón exacto a copiar para emotes.**
- **12 jugadores ya soportado**: `MAX_PLAYERS = 15` (`GameModels.kt:473`); el online acepta hasta 15. El `DEFAULT_MAX_PLAYERS = 10` de `LobbyBrowserActivity.kt:333` es solo el default de display cuando la sala no declara `maxJugadores` — **no** es un tope.

---

## Parte 1 — Emotes online (sincronizar por RTDB)

**Objetivo**: que al tirar un emote en una partida online, lo vean todos los jugadores, con el mismo cooldown/límite que en local.

1. **Habilitar el envío online**: quitar el branch `isOnlineGameplay() -> "..."` de `reactionBaseUnavailableMessage` (`GameplayMockActivity.kt:3759`) para que online use las mismas reglas de fase/vivo que local (debate/votación, vivo, sin evento bloqueante).
2. **Al enviar un emote en online** (donde hoy se hace `showReactionBubble(human.name, spec)` + `limiter.record`, `GameplayMockActivity.kt:3752-3754`): además de mostrarlo localmente y registrar el cooldown, **empujar a RTDB** un hijo nuevo en `salas/{onlineRoomId}/emotes` con:
   - `matchId` (el `onlineMatchId` de la sesión, igual que el chat),
   - `player` (nombre del humano),
   - `emoteId` (el `id` del `EmoteCatalog`),
   - `ts` (`ServerValue.TIMESTAMP`).
   Usar `push()` como el chat. El cooldown/límite se mantiene **client-side** en el emisor (`GameplayReactionLimiter`), igual que el chat es client-trusted; suficiente para este alcance (no validar en servidor).
3. **Escuchar el nodo** (en `GameplayChatController`, al lado de los listeners de chat, `:1674` en adelante, o en los listeners online de la Activity): query con `limitToLast(N)` (ej. 30), filtrar por el `matchId` actual, y **deduplicar** por push-key (guardar las keys ya mostradas, o ignorar las que llegan con `ts` anterior al momento de suscribirse — mismo criterio que el chat para no re-mostrar historial viejo). Para cada emote **de otro jugador** (saltear el propio, ya se mostró local):
   - buscar el spec por `emoteId` en `EmoteCatalog` para el tema actual,
   - llamar `showReactionBubble(player, spec)` (reusar el existente).
4. **Gating de recepción**: mostrar la burbuja solo si la UI no está en un evento bloqueante (reusar el criterio de `reactionUiBlocked()` para el timing, o encolar y mostrar al salir del overlay). Las burbujas se auto-descartan solas, así que no hace falta más.
5. **Reglas de seguridad RTDB**: agregar reglas para `salas/{roomId}/emotes` calcando las de los nodos de chat en `database.rules.json` (`auth != null`, shape básico, y el `.write` de borrado del padre que ya permite el teardown de sala). **El usuario debe desplegar las reglas** (`firebase deploy --only database`) — dejarlo anotado en la entrega.
6. **Limpieza**: confirmar que el borrado de sala vacía (que ya hace `removeValue()` sobre `salas/{roomId}`) arrastra el nodo `emotes` (al ser hijo, sí, pero verificar que las reglas nuevas no bloqueen el borrado del padre).
7. **No romper local**: el camino local (mostrar burbuja + limiter) queda igual; solo se agrega la rama de red cuando `isOnlineGameplay()`.

**Verificación**: 2 clientes online (emuladores) en la misma partida; al tirar un emote en uno, aparece la burbuja sobre ese jugador en el otro, respetando cooldown (10s) y límite (2/ronda) en el emisor; muertos/fuera de fase no pueden; no aparecen emotes viejos al entrar tarde.

## Parte 2 — 12 jugadores (verificación + pulido puntual)

12 ya está permitido; **no cambiar límites**. El trabajo es confirmar que se ve y sincroniza bien, extendiendo lo que ya se hizo para 10 (`SPEC-online-10-jugadores.md`).

> **ACLARACIÓN DE ALCANCE (importante)**: el reparto online usa `onlineSafeRoleComposition` (`GameModels.kt:705-716`), que es **deliberadamente reducido**: 1 policía + 1 médico + 1 asesino + (1 mercenario si son 7+) + aldeanos. **NO** incluye alcalde, desertor, espía ni los exclusivos de mapa (payador/oráculo/bufón) a **ninguna** cantidad. Por lo tanto "12 online" hoy = esos 4 roles + 8 aldeanos. Esto es **intencional** y **fuera de alcance de esta spec**: los roles especiales en online son un plan aparte, por fases (Fase 1: Alcalde + Desertor; Fase 2: exclusivos de mapa; Fase 3: Espía = 2do killer), a encarar **después** de cerrar la estabilidad. No agregar roles al preset online acá.

1. **Local con 12 bots**: recorrer una partida completa — mesa (cartas legibles, sin solaparse), chat, desempate, reveals, pantalla final — nada cortado ni desbordado. (Barato, un solo dispositivo; valida casi todo lo que rompe por cantidad. Ojo: el local **sí** trae roles especiales a 12, así que esta prueba también valida la UI de esos roles a esa cantidad.)
2. **Reparto online con 12**: confirmar que `onlineSafeRoleComposition` reparte coherente con 12 (1 asesino + 1 mercenario + 1 policía + 1 médico + 8 aldeanos). **No** esperar roles especiales online — su ausencia es esperada, no un bug.
3. **Browser de salas**: una sala creada para 12 debe mostrar `X/12` correcto (el default 10 de `LobbyBrowserActivity.kt:333` es solo fallback; verificar que una sala con `maxJugadores`/`jugadoresEsperados = 12` muestre 12, no 10).
4. **Online real 8-12**: arranque (`FALTAN X` hasta completar), timers, handoff de host, y ahora los **emotes** de la Parte 1 — todos con 12 activos. Anotar cualquier cosa lenta o cortada.

## Fuera de alcance
- No validar emotes en el servidor (client-side, como el chat).
- No tocar el modelo de sincronización de fases ni el reparto de roles.
- No subir el límite de jugadores (ya es 15).

## Nota para Codex
Si al cablear los emotes ves que conviene un mecanismo distinto (p. ej. un solo listener unificado con el chat, o mostrar el emote también en el feed de chat como línea especial), proponelo aparte. Y si detectás que 12 rompe algo puntual de layout (mesa/desempate), marcalo con el mismo criterio responsive que ya venimos usando (autosize, sin anchos fijos).
