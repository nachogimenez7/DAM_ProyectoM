# SPEC — Lobby online: más claro y sin redundancia

> Para Codex. Español, referencias `archivo:línea`. **No compilar** (el usuario valida en Android Studio). Alcance: **claridad visual, copy y reordenamiento** del lobby online. **Sin features nuevas ni cambios de lógica de negocio** (nada de tocar arranque, transacciones, presencia, votación real de mapa). Todo es layout + textos + un par de flags de UI.
>
> **Contexto**: el usuario se pierde en el lobby online. Diagnóstico: información repetida (el código de sala y el "faltan N" aparecen 2-3 veces) y bloques oscuros apilados sin jerarquía. Hay un mockup aprobado del layout objetivo (bloques: ARRANQUE → INVITAR → EN LA SALA → CONFIGURACIÓN → chat).
>
> **Archivos**: `app/src/main/res/layout/activity_lobby.xml` y `app/src/main/java/com/traidores/juego/LobbyActivity.kt`. El lobby online y el local **comparten** `lobbyConfigurationPanel`; la visibilidad por modo se decide en `renderLobbyStructure` (`LobbyActivity.kt:529`). **Todo cambio debe dejar el modo local funcionando** (probar ambos).

## Estado actual (verificado)

- Header (`lobbyHeader`, `activity_lobby.xml:32`): `lobbyTitle` + `playerCount`. `playerCount.text` se arma en `LobbyActivity.kt:401-414` → `"X/Y jugadores - faltan N"` / `"- faltan N listos"` / `"- todos listos"`.
- Botón de inicio: `btnStartGame` (`activity_lobby.xml:112`, estilo `BtnGold`, fondo `bg_btn_gold_ripple`). Su texto/estado lo maneja `renderStartButtonState()` (`LobbyActivity.kt:1957`): host con faltantes → `"FALTAN $missingPlayers"` (botón dorado, deshabilitado). `canStart` → `"INICIAR ONLINE"`.
- Hint: `lobbyModeHint` (`activity_lobby.xml:127`), texto online desde `onlineLobbyHint()` (`LobbyActivity.kt:1670`) → `"Esperando N jugadores mas. Codigo XXXXX."` (repite código y "faltan").
- Panel de código: `onlineCodePanel` (`activity_lobby.xml:219`) con "CODIGO DE SALA" + `onlineRoomCodeText` + Copiar/Compartir.
- Stepper cantidad: `onlinePlayerTargetPanel` (`activity_lobby.xml:138`).
- Votación de mapa: `onlineMapVoteHeader` + `mapVoteCardsRow` (3 cartas) + `mapVoteResultHint`. Render en `renderOnlineMapVoting()` (`LobbyActivity.kt:550`): `views.count.text = "$count votos"` (`:572`), hint "Sin votos: al iniciar se mantiene X" (`:578`).
- Jugadores online: chips horizontales en `onlinePlayersContainer` (`onlinePlayersScroll`), creados en `renderLobby` (`LobbyActivity.kt:479-487`) con `createOnlinePlayerChip`. (El modo local usa `playersContainer` bajo `lobbyPlayersLabel`.)

**Orden actual de hijos de `lobbyConfigurationPanel`**: botón inicio → hint → stepper → liberar desconectados → código → header mapa → cartas mapa → hint mapa → descripción mapa → jugadores online → "CONFIGURACION" + opciones avanzadas → "JUGADORES" (local) + lista local.

---

## Parte A — Matar la información repetida

1. **Hint** (`onlineLobbyHint()`, `LobbyActivity.kt:1670-1689`): sacar el código y el "faltan N". Que sea una llamada a la acción útil:
   - `ONLINE_ROOM_STATE_IN_GAME` → `"Partida online iniciada."` (sin código).
   - faltan jugadores (`missing > 0`) → `"Compartí el código para que se sumen."`
   - sala completa, faltan listos → `"Sala completa. Toquen LISTO para arrancar."`
   - Eliminar por completo la variable `codeText` (el código ya está grande en `onlineCodePanel`).
2. **Header** (`playerCount.text`, `LobbyActivity.kt:401-414`): dejar solo `"X/Y jugadores"` para el online (quitar el sufijo `" - faltan N"`; el botón/progreso y el header "EN LA SALA" muestran el resto). Mantener el modo local como está (`:416`).
   - Opcional aceptable: conservar `" - todos listos"` cuando `missingPlayers == 0 && missingReady == 0`, porque no duplica nada. Pero **nunca** el "faltan N" de jugadores faltantes (ese vive en el botón).

Resultado: el código aparece **una sola vez** (panel INVITAR) y el "faltan N" **una sola vez** (botón/progreso).

## Parte B — Botón de inicio que comunique el estado (progreso)

Hoy, mientras faltan jugadores, `btnStartGame` es **dorado pleno** (invita a tocarlo y no arranca). Cambiar para que el dorado signifique "ya se puede iniciar":

1. **Agregar una barra de progreso online**, debajo de `btnStartGame`, visible solo cuando el host está esperando (faltan jugadores o faltan listos). Implementación sugerida (elegí la que te resulte más limpia):
   - **Opción simple**: un `FrameLayout` (id `onlineStartProgress`, `layout_height="10dp"`, fondo oscuro redondeado tipo `bg_btn_dark`) con un `View` hijo de relleno dorado (`#D9A43A`) cuyo ancho se setea por código a `actuales/esperados` (usar `layoutParams` con weight o porcentaje). Debajo, un `TextView` chico centrado con `"n de m jugadores"`.
   - **Alternativa**: un `ProgressBar` horizontal determinado con `progressDrawable` custom (track oscuro + progress dorado), `max = onlineExpectedPlayers`, `progress = activePlayers.size`.
   - Default `visibility="gone"`; se muestra/actualiza en `renderStartButtonState()`.
2. En `renderStartButtonState()` (`LobbyActivity.kt:1957-2011`), para el online:
   - Cuando **NO** se puede iniciar todavía (host esperando): `btnStartGame` con fondo **oscuro** (`bg_btn_dark_ripple`, no el dorado) y texto `"ESPERANDO ${activePlayers.size}/${onlineExpectedPlayers}"` (reemplaza el `"FALTAN N"`); mostrar y actualizar `onlineStartProgress`.
   - Cuando `canStart`: fondo **dorado** (`bg_btn_gold_ripple`) + `"INICIAR ONLINE"`; ocultar `onlineStartProgress`.
   - `canStartWithPresent` y demás estados: se mantienen; el dorado solo para estados accionables.
   - El invitado (guest) y los estados de sincronización: sin progreso, como están.
3. Mantener toda la **lógica** de `startButton.isEnabled` y los `contentDescription` tal cual (solo cambian estilo/copia/visibilidad del progreso).

## Parte C — Reordenar en bloques con encabezados

Objetivo (solo online): agrupar como el mockup aprobado. Reordenar los hijos de `lobbyConfigurationPanel` y agregar **encabezados de sección** (TextViews nuevos, estilo del "CONFIGURACION" existente en `activity_lobby.xml:668`: 11sp, `letterSpacing`, `text_muted`), visibles solo online.

**Orden objetivo (online), de arriba a abajo:**
1. **Arranque**: `btnStartGame` → `onlineStartProgress` (nuevo, Parte B) → `lobbyModeHint`.
2. `btnReleaseDisconnected` (queda acá, casi siempre oculto).
3. **Header `"INVITAR"`** (nuevo, online-only) → `onlineCodePanel`.
4. **Header `"EN LA SALA"`** (nuevo, online-only) → `onlinePlayersScroll` (**moverlo acá arriba**, hoy está después del mapa).
5. **Header `"CONFIGURACIÓN"`** (reusar el label existente de `:668`, hacerlo visible también online) → `onlinePlayerTargetPanel` (stepper, **moverlo acá abajo**) → `onlineMapVoteHeader` + `mapVoteCardsRow` + `mapVoteResultHint` → `btnAdvancedOptions` ("Opciones avanzadas").
6. Bloques **local-only** (`mapDescription`, `lobbyPlayersLabel` + `playersListPanel`, `selectedMapCard`): dejarlos donde no molesten; siguen ocultándose en online por `renderLobbyStructure` (`:529-537`).
7. Chat dock (`lobbyChatDock`) al final, igual.

**Cuidado (crítico)**: `lobbyConfigurationPanel` es compartido local+online. Al reordenar:
- Los bloques online-only (`onlineCodePanel`, `onlinePlayersScroll`, `onlinePlayerTargetPanel`, headers nuevos) están `gone` en local → no afectan el local.
- Los headers nuevos (`INVITAR`, `EN LA SALA`) deben setear `visibility` online-only en `renderLobbyStructure`.
- **Verificar el lobby local** después del reorden: la selección de mapa (`mapVoteCardsRow` seleccionable), `mapDescription` y la lista `JUGADORES` (`playersListPanel`) deben seguir viéndose bien y en orden lógico.

**Casilleros "Libre"** (parte de EN LA SALA): en `renderLobby` (`LobbyActivity.kt:479-525`), después de agregar los chips reales online, agregar chips placeholder `"Libre"` para las plazas vacías (`onlineExpectedPlayers - visiblePlayers`, tope al `onlineExpectedPlayers`). Crear un helper `createEmptyLobbySlotChip()` con el mismo ancho (`onlineChipWidth`) y estilo tenue (borde punteado / `text_muted`, texto "Libre"). Así se ve la sala llenándose en vez de un vacío. (No agregan jugadores reales; son solo visuales.)

## Parte D — Votación de mapa más entendible

En `renderOnlineMapVoting()` (`LobbyActivity.kt:550-586`):
1. **Cero votos** (`views.count.text`, `:572`): cuando `count == 0`, mostrar `"Toca para votar"` en vez de `"0 votos"`. Con `count > 0`, dejar `"$count voto(s)"`.
2. **Sello "POR DEFECTO"**: agregar a cada carta de mapa un badge chico (TextView nuevo en el overlay de la carta, en `activity_lobby.xml`, `gone` por default, fondo dorado, texto "POR DEFECTO", 9sp). Mostrarlo **solo en la carta del mapa por defecto** (`currentMap().key`) cuando `summary.totalVotes == 0`; ocultarlo en cuanto haya algún voto o en las otras cartas. Refleja visualmente la frase "al iniciar se mantiene X".
3. El `mapVoteResultHint` (`:578`) puede quedar como está (o acortarse a "Sin votos aún" ya que el sello lo comunica) — a criterio, sin romper los otros dos estados (líder/empate).

## Fuera de alcance (explícito)
- No tocar `renderStartButtonState` más allá de estilo/copy/progreso; **nada** de la lógica de `isEnabled`, `canStart`, transacciones ni presencia.
- No cambiar el modelo de votación de mapa (`OnlineMapVoteResolver`), ni el reparto, ni tiempos.
- No agregar jugadores/roles nuevos. Los casilleros "Libre" son puramente visuales.
- No rediseñar el modo local; solo **no romperlo** con el reorden.

## Verificación
1. **Lobby online recién creado (1/5)**: el código aparece una sola vez; el header dice "1/5 jugadores"; el botón muestra "ESPERANDO 1/5" oscuro con la barra de progreso; el hint dice "Compartí el código…"; se ven 1 chip real + 4 "Libre"; Grecia (o el mapa por defecto) con sello "POR DEFECTO" y las 3 con "Toca para votar".
2. **Sala llenándose**: el progreso avanza; al completarse y estar todos listos, el botón se pone dorado "INICIAR ONLINE" y desaparece la barra.
3. **Con votos de mapa**: el sello "POR DEFECTO" desaparece; las cartas muestran "$n voto(s)" y el líder se resalta como hoy.
4. **Lobby local**: recorrerlo completo — selección de mapa, descripción, lista "JUGADORES", iniciar — todo funcional y ordenado (no lo rompió el reorden).
5. **Invitado (guest) online**: ve su estado ("ESPERANDO AL ANFITRION" o el que corresponda) sin barra de progreso rota.
