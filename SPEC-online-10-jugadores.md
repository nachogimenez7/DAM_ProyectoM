# SPEC — Online con 10 jugadores: verificación y pulido

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). Objetivo: que una sala online de **10 jugadores** sea jugable y fluida **hoy**. Alcance acordado con el usuario: **pulir lo existente, sin roles nuevos ni segundo asesino** (eso va en una spec futura; el diseño de 2+ killers online ya está documentado en `docs/firebase-online-schema.md:297-305`).
>
> **Aclaración clave**: el "límite de 7" **no existe en el código**. `LocalGameFactory.MAX_PLAYERS = 15` (`GameModels.kt:459`), las salas online aceptan `jugadoresEsperados` de 5 a 15 y el stepper del host llega a 15 (`OnlineModeActivity.kt:203,216`). Lo que ocurre en 7 es que el preset online suma el **Mercenario** (`onlineSafeRoleComposition`, `GameModels.kt:690-701`). Lo que nunca se validó es que la partida con 8-10 se vea bien y fluya — eso es esta spec.
>
> **Reparto online actual con 10** (no tocar en esta spec): 1 asesino + 1 mercenario + 1 policía + 1 médico + 6 aldeanos. Partida cargada al pueblo y más larga de lo ideal; aceptado para esta etapa.

## Contexto técnico

- **Mesa**: `GameplayTableUi.companionCardMetrics` (`GameplayTableUi.kt:721`) calcula métricas por `playersPerSide`. Con 10 jugadores hay 9 acompañantes → 5 en la columna izquierda y 4 en la derecha (`GameTableLayout.companionSlots`, `GameTableLayout.kt:33`). El branch de 5 por lado (`GameplayTableUi.kt:765-776`) da cartas de 31×50dp con nombre a 8.5sp; hay lógica de ajuste a la altura disponible (`:790-838`) con pisos de 24dp de alto y 6.5sp. `scrollEnabled` recién a partir de 13 jugadores (`:727`).
- **Desempate**: `renderTieVoteWindow` (`GameplayMockActivity.kt:6685`) arma un `GridLayout` con `columnCount = candidates.size.coerceIn(1, 4)` y **`rowCount = 1` fijo** (`:6689-6690`).
- **Gates online** (todos agnósticos de cantidad, solo verificar): arranque cuando `jugadoresActuales == jugadoresEsperados` y todos listos (`OnlineLobbyRules.canStart`, `OnlineLobbyRules.kt:40`), `estadoClientes` por cliente, `FORZAR NOCHE` a los 30s, presentaciones compartidas 3-10s, handoff de host por `orden`.
- **Chat online**: listeners con `limitToLast(ONLINE_CHAT_MAX_MESSAGES = 40)` (`GameplayChatController.kt:2173`).

---

## Parte 1 — Fix real: desempate con 5+ empatados

Con 10 jugadores un empate de 5 candidatos es posible (2 votos cada uno). Hoy `renderTieVoteWindow` fija `rowCount = 1` con `columnCount` tope 4: con 5+ cartas `GridLayout` revienta o apila mal.

- `GameplayMockActivity.kt:6689-6690`: calcular filas — `columnCount = min(size, 4)`, `rowCount = ceil(size / 4f)`.
- Con 2 filas, achicar cartas (`:6691-6696`): agregar rama `size >= 5 -> ancho 92 / alto 124` (ajustable a ojo) para que el panel no desborde en un teléfono apaisado.
- Verificar que `tieVotePanel` tolere dos filas sin cortar el botón de confirmación ni el aviso "SI EL EMPATE SE REPITE…" (`:6731`); si el alto no da, envolver el grid en un `ScrollView` vertical.
- Si existe una ventana análoga para `ALCALDE_DESEMPATE` (`alcaldeTieCandidates`), aplicarle el mismo criterio.
- Cubrir con test JVM si hay lógica extraíble (cálculo filas/columnas), estilo de los tests existentes en `app/src/test/java/com/traidores/juego/`.

## Parte 2 — Mesa y pantallas con 10 (verificar en LOCAL, misma UI)

Truco de verificación: el gameplay local comparte pantalla con el online → una partida **local con 10 bots** en un teléfono valida toda la UI sin juntar 10 personas. Revisar y corregir solo lo que se vea roto (restricción del proyecto: correcciones chicas, sin rediseños):

1. **Mesa con 10 y con 15**: cartas legibles, sin solaparse con el feed de chat colapsado ni el panel inferior; muertos/atenuados distinguibles a 31×50dp.
2. **Escala de texto de accesibilidad** (overlay existente): con `gameplayTextScale` al máximo, los nombres a 8.5sp × escala no deben cortar filas; si se rompe, capear la escala en las cartas de mesa, no en el chat.
3. **Votación normal** (tap en cartas de la mesa): seleccionar a los 9 objetivos posibles es cómodo; el resaltado se ve en cartas chicas.
4. **Ventana de resultados de votación** con `votosIndividuales` activado y 10 votantes: la lista entra o scrollea.
5. **`WinnerResultsRenderer`**: pantalla final con 10 filas de jugadores.
6. **`AssigningRolesActivity`**: reparto/reveal con 10.
7. **Reveals compartidos** (amanecer, expulsión, desempate, silencio): nombres largos (`nombreSala` hasta 32 chars con `#`) no desbordan los carteles.
8. **Lobby online con 10**: lista de jugadores, listos, votación de mapa y chat de lobby en un teléfono (el rediseño nuevo ya contempla cantidad flexible; solo confirmar 10 filas + scroll).
9. **`LobbyBrowserActivity`**: tarjeta de sala mostrando `X/10` correcto.

## Parte 3 — Fluidez online con 10 (smoke con emuladores + prueba real)

Nada de esto debería requerir código nuevo; es checklist de humo antes de la partida real (2-3 dispositivos + `modoPrueba` no alcanza para 10, así que es revisión de lógica + la prueba real de esta noche):

1. Arranque: con 10 esperados, el botón del host muestra `FALTAN X` hasta que estén todos listos; `estadoClientes` registra 10 entradas y la noche 1 no arranca hasta los 10 `EMPEZAR` (o `FORZAR NOCHE` a los 30s).
2. Timers: noche y votación esperan el timer completo con 10 acciones/votos registrados; última acción por `creadaEnLocal` sigue mandando.
3. Handoff: si muere/se desconecta el host activo, toma el siguiente vivo conectado por `orden` — con 10 hay más candidatos, no cambia la regla.
4. Presentaciones compartidas: `LISTOS n/total` excluye a los muertos; con 10 el avance sigue en 3-10s.
5. Muertos: siguen viendo carteles y chat (y con `SPEC-chat-espectadores.md` implementada, su canal) sin bloquear fases.

## Parte 4 — Ajustes menores recomendados

- `ONLINE_CHAT_MAX_MESSAGES` 40 → **60** (`GameplayChatController.kt:2173`), para igualar el cap local por canal (`GameplayFeedMessages.MAX_FEED_MESSAGES = 60`): con 10 personas el feed churnea mucho más rápido.
- Nada más. Explícitamente **fuera de alcance**: cambios al preset de roles online, segundo asesino, roles de mapa online, tiempos por defecto.

## Nota para el host (no es código)

Con 10 jugadores conviene subir en la config de sala la discusión y la votación (p. ej. discusión 120s, votación 45-60s): hay más gente hablando por chat y más candidatos que mirar. La config del lobby ya lo permite (`configLobby`).

## Verificación final
1. Local con 10 bots: recorrer una partida completa mirando los puntos de la Parte 2.
2. Local con 15 bots: la mesa activa scroll (≥13) y nada desborda.
3. Desempate forzado con 5 empatados (partida local, comandos debug de voto si están disponibles): la ventana muestra 2 filas sin crash.
4. Online real de 10 esta noche: los puntos de la Parte 3 + anotar cualquier cosa lenta o cortada para la próxima iteración.
