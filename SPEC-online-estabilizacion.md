# SPEC — Estabilización online post-playtest (host, chat, cantidad flexible, salto de noche, botones de continuar, responsive)

> Para Codex. Español, `archivo:línea`. **No compilar** (el usuario valida compilación y apariencia en Android Studio). Tanda de estabilización del modo online después de un playtest real con 6-7 personas. Seis partes; respetar el **orden de entrega** del final (la Parte 4 depende de la Parte 1).
>
> **Decisiones ya tomadas con el usuario (no re-preguntar):**
> 1. **Cantidad flexible: AMBAS mecánicas** — selector de cantidad en el lobby **y** botón "jugar con los presentes". No hace falta salir y recrear la sala para jugar con menos gente.
> 2. **Salto de noche: solo online.** En local ya existe el botón de saltar fase (`nightSkipEnabledAtMs`, arma a ~3.5s). En online, **la propia acción nocturna es la señal de "listo"** — cuando el actor de cada sub-fase ya eligió, el host adelanta, sin botón nuevo. **Entregar después de la Parte 1** (toca el mismo camino de avance de fase que hoy está frágil).
> 3. **Botones de continuar online: bajar el máximo a 6s** (hoy 10s), manteniendo el acelerar-por-votos (LISTOS n/total). Además **colapsar el doble botón del amanecer** (hay dos "continuar" seguidos).
> 4. **Responsive: auditoría completa** de todas las pantallas, priorizando teléfonos (Samsung se ve cortado). Solo correcciones chicas y justificadas, sin reescribir layouts.
>
> **Invariante que persigue la Parte 1 (leer antes de tocar host):** en el **lobby** (sala `esperando`/`finalizada`) el anfitrión es SIEMPRE el creador original (`hostId`) mientras siga presente y activo en la sala. `hostActivoId` es un concepto **solo de gameplay** (quién resuelve fases en el momento) y al volver al lobby se restaura a `hostId`. Únicamente si el creador abandonó la sala se transfiere el rol de anfitrión al primer jugador activo+conectado por orden.

---

## Parte 1 — Host estable en el lobby + reinicio robusto de sala (bugs "anfitrión cambia" y "no se puede reiniciar")

### Diagnóstico
Son el mismo problema de raíz: el rol de anfitrión al volver a la sala depende de `hostActivoId`, que es volátil.

- En gameplay, cada publicación de estado del host escribe `hostActivoId = quien publica` (`GameplayMockActivity.kt:1815`), incluido el estado final, que además marca la sala `finalizada` (`GameplayMockActivity.kt:1818-1819`). Si el creador murió o se desconectó a mitad, el "bastón" de host activo ya había pasado a otro jugador (handoff de gameplay, `GameplayMockActivity.kt:2524-2585`), y ese otro queda como `hostActivoId` al terminar.
- En el lobby, `currentUserIsOnlineHost()` decide "quién es host" por `hostActivoId`, no por el creador (`LobbyActivity.kt:912-916`).
- La revancha **no restaura** `hostActivoId = hostId`: la transacción de `maybeResetFinishedOnlineRoomForRematch` (`LobbyActivity.kt:1514-1525`) borra `partidaInicial`/`estadoPartida`/`estadoClientes` y resetea "listos", pero deja `hostActivoId` como estaba.
- El handoff de lobby deja que **el primero por orden que esté conectado** agarre el host si el host activo aparece "desconectado" un instante durante la transición gameplay→lobby (`LobbyActivity.kt:931-1019`). Eso es literalmente "el primero que volvió se queda de anfitrión".
- La reapertura para revancha **solo** dispara desde estado `finalizada` (`LobbyActivity.kt:1507`). La 1ª captura del usuario muestra el cartel "Partida online iniciada", que es el texto del estado **`en_juego`** (`LobbyActivity.kt:892`): la sala quedó **trabada en `en_juego`** sin volver a `esperando`. Con la sala fuera de `esperando`, `OnlineLobbyRules.canStart` nunca se cumple (`OnlineLobbyRules.kt:49`) y nadie puede reiniciar. `returnToLobby()` solo hace `finish()` (`GameplayMockActivity.kt:6917-6922`), no marca ni destraba nada.

### Arreglo

**1.1 — En el lobby, el host es el creador.** En `currentUserIsOnlineHost()` (`LobbyActivity.kt:912-916`), cuando el estado de sala es de lobby (`esperando` o `finalizada`), basar la identidad en `onlineHostId` (creador), no en `hostActivoId`:
```kotlin
private fun currentUserIsOnlineHost(): Boolean {
    // En el lobby el anfitrión es el creador; hostActivoId es solo de gameplay.
    val creatorPresent = activeOnlinePlayers().any { it.id == onlineHostId }
    return when {
        onlineHostId == onlineTempUid -> true
        // Fallback: si el creador ya no está en la sala, el primer activo+conectado por orden.
        !creatorPresent && onlineLobbyHostFallbackId() == onlineTempUid -> true
        onlineHostId.isBlank() && lobbyMode == MODE_ONLINE_CREATE -> true
        else -> false
    }
}
```
Nuevo helper `onlineLobbyHostFallbackId()`: primer `activeOnlinePlayers()` con `status == PLAYER_STATE_CONNECTED`, ordenado por `order` y luego `id`; o `""`.

**1.2 — La revancha la prepara el creador y restaura el host.** En `maybeResetFinishedOnlineRoomForRematch` (`LobbyActivity.kt:1486-1568`):
- Cambiar el gate de disparo de `!currentUserIsOnlineHost()` para que solo dispare el **creador presente** (o el fallback si el creador se fue) — ya cubierto por 1.1, así que dejar `!currentUserIsOnlineHost()` funciona una vez arreglado 1.1.
- En la transacción, reemplazar el chequeo `activeHostId != onlineTempUid` (`LobbyActivity.kt:1510-1513`) por: el que resetea debe ser `hostId` (creador) **o**, si el creador no figura entre los jugadores del room, el fallback por orden.
- Agregar al `update` del room (`LobbyActivity.kt:1514-1525`): `FIELD_ACTIVE_HOST_ID to <hostId del room>` (restaurar el bastón al creador). Si el creador se fue, setear `hostActivoId` **y** `hostId` al fallback, para que la identidad quede estable de acá en adelante (una sola fuente de verdad).

**1.3 — Cubrir la sala trabada en `en_juego`.** La reapertura a `esperando` debe disparar también cuando el room quedó `en_juego` pero la partida ya no está viva:
- En `applyOnlineRoomSnapshot` (`LobbyActivity.kt:731-783`), tratar como "reabrible" tanto `finalizada` como `en_juego` **sin gameplay vivo**. Definir "sin gameplay vivo" como: no hay `estadoPartida`, **o** `estadoPartida.ganador` no está en blanco (la partida terminó). Solo el host (1.1) reabre.
- **Ojo con el re-entry loop:** hoy, si `onlineRoomState == en_juego && !onlineStartedNoticeShown`, el snapshot llama `startOnlineMatch()` (`LobbyActivity.kt:777-780`) y te reinyecta al gameplay terminado. Reordenar: **primero** intentar la reapertura (1.3); solo re-entrar al gameplay si realmente hay partida viva (`partidaInicialCreada && estadoPartida presente && ganador en blanco`). Si la partida terminó, no re-entrar: reabrir a `esperando`.
- Marcar `finalizada` de forma más confiable al terminar: en `returnToLobby()` (`GameplayMockActivity.kt:6917`), si `isOnlineGameplay()` y `session.winner` no está en blanco y el room sigue `en_juego`, publicar `estado = finalizada` antes de `finish()` (idempotente; si ya está `finalizada`, no pasa nada).

**1.4 — El handoff de lobby solo si el creador se fue de verdad.** En `maybeClaimOnlineLobbyHostHandoff` (`LobbyActivity.kt:931-945`), no reclamar el host solo porque el host activo aparece desconectado un instante: reclamar únicamente cuando el **creador** (`hostId`) ya no es un jugador activo+presente en la sala. Priorizar restaurar/mantener al creador antes que "primero por orden".

### Verificación
- El creador crea sala, muere en la partida, todos vuelven a la sala: el **creador** sigue siendo el anfitrión (ve las opciones de host y el botón de iniciar); nadie más se queda de host por volver primero.
- Terminar una partida y volver a la sala: el estado pasa a `esperando`, aparecen todos en "no listo", y el host puede volver a iniciar (no queda trabada en "Partida online iniciada").
- Forzar una sala que quedó en `en_juego` con partida terminada: al volver al lobby, el host la reabre a `esperando`; ningún cliente re-entra al gameplay terminado.
- Si el creador abandona la sala del todo: el primer jugador activo por orden pasa a ser anfitrión (y `hostId` se actualiza), sin quedar sin host.

---

## Parte 2 — El chat no debe arrastrar la partida anterior (bug "chat de la partida vieja")

### Diagnóstico
Dos causas que se suman:
- La revancha (`LobbyActivity.kt:1514-1525`) borra campos del documento pero **nunca borra las subcolecciones `chat` ni `chat_traidores`**. En Firestore, borrar campos no borra subcolecciones, así que los mensajes de la partida previa quedan vivos en la sala.
- El listener del chat lee `.collection("chat").orderBy("creadaEnLocal").limit(40)` **sin filtrar por la partida actual** (`GameplayChatController.kt:1461-1487`; el de traidores igual, `:1510-1521`). Encima es **ascendente + limit(40)**, así que muestra los **40 mensajes más viejos** = los de la primera partida. Por eso "estaba el chat de la partida anterior".

### Arreglo

**2.1 — Borrar el chat al preparar la revancha (arreglo principal).** En el flujo de reinicio (Parte 1, `maybeResetFinishedOnlineRoomForRematch`), borrar en lote los docs de `chat` y `chat_traidores` del room. Una transacción no puede borrar subcolecciones desconocidas, así que hacerlo **fuera de la transacción** (después de que la reapertura a `esperando` tuvo éxito): por cada subcolección, `get()` de los docs y `WriteBatch.delete(...)` en tandas de ≤500. Si el borrado falla, loguear (`OnlineDebugLog`) pero **no** bloquear el reinicio; la Parte 2.2 es el respaldo.

**2.2 — Listener descendente (arreglo del bug latente + defensa).** Cambiar en ambos listeners (`GameplayChatController.kt:1468` y `:1520`) `orderBy("creadaEnLocal")` por `orderBy("creadaEnLocal", Query.Direction.DESCENDING)` + `limit(40)`, y **revertir la lista** antes de mostrarla (para que quede cronológica). Esto arregla también un bug latente aparte: hoy, en una partida larga de **más de 40 mensajes**, se dejan de ver los nuevos (mostrás los 40 más viejos). Con descendente ves siempre los últimos 40.

**2.3 — (Opcional, defensa en profundidad) Filtro por partida.** Si se quiere blindar aún más ante un borrado fallido: que `partidaInicial` guarde un `chatEpoch` (p. ej. `startedAtEpochMs` del arranque), que cada mensaje de chat incluya ese `epoch`, y que el listener filtre `whereEqualTo("epoch", chatEpoch)`. Requiere tocar el envío (`GameplayChatController.kt:1366-1376` y `:1425-1436`) y las reglas (`firestore.rules`) para permitir el campo. Dejar como mejora posterior si 2.1 + 2.2 ya resuelven el caso en pruebas.

### Verificación
- Jugar una partida en una sala, volver y jugar otra: el chat de la 2ª arranca **vacío**, sin mensajes de la 1ª (ni en público ni en el canal de traidores).
- Partida con más de 40 mensajes: se siguen viendo los últimos, no se congela en los primeros 40.

---

## Parte 3 — Empezar con los presentes sin recrear la sala (selector + botón)

### Diagnóstico
Lo único que traba jugar con menos gente es la cantidad fija:
- `jugadoresEsperados` se fija al crear la sala (`OnlineRoomFirestore.kt:97-98`) y no se puede cambiar desde el lobby.
- `OnlineLobbyRules.canStart` exige `activePlayers.size == expectedPlayers` **exacto** (`OnlineLobbyRules.kt:40-52`).

**Buena noticia (no hace falta tocar el reparto):** la composición de roles **ya se adapta** a la cantidad presente. `normalizedRoleComposition` (`GameModels.kt:721-725`) arma la composición según cuántos hay (5-6: asesino+médico+comisario+aldeanos; 7+: suma mercenario — ver `docs/firebase-online-schema.md`), y `assignRoles` reparte sobre los jugadores reales. Cambiar de 7 a 6 recalcula solo.

### Arreglo

**3.1 — Selector de cantidad en el lobby (solo host).** Agregar un control −/+ (o stepper) visible únicamente para el host (`currentUserIsOnlineHost()`, ya arreglado en la Parte 1) que ajuste `jugadoresEsperados`:
- Rango permitido: `[mínimo, maxJugadores]`, donde `mínimo = LocalGameFactory.MIN_PLAYERS` (o `TEST_MIN_PLAYERS` si `modoPrueba`). No permitir por debajo del mínimo ni por encima de `MAX_PLAYERS`.
- Escribe `FIELD_EXPECTED_PLAYERS` (y `FIELD_MAX_PLAYERS` para que el cartel visible coincida) en el doc del room. Todos re-sincronizan solos: el snapshot ya relee `onlineExpectedPlayers` (`LobbyActivity.kt:746-749`) y `renderLobby()` actualiza el "n/total".
- Solo editable con la sala `esperando` y sin `partidaInicialCreada`.

**3.2 — Botón "Jugar con los presentes" (solo host).** Azúcar sobre 3.1 + inicio: setea `jugadoresEsperados = activeOnlinePlayers().size` y dispara el inicio normal (`startOnlineRoomForEveryone`, `LobbyActivity.kt:1177`). Guardas:
- Si `activeOnlinePlayers().size < mínimo` → Toast "Faltan jugadores para el mínimo" y no hacer nada.
- Si no todos los presentes están `listo` → Toast (reusar `onlineStartPreflightMessage`, `LobbyActivity.kt:1367`) y no iniciar.
- Escribir `jugadoresEsperados` y **esperar la confirmación del snapshot** (que `onlineExpectedPlayers` ya sea el nuevo valor) antes de llamar al inicio, para que todos los gates usen el número correcto.

**3.3 — Que todos los gates usen el nuevo `expectedPlayers` antes de iniciar.** El inicio ya lee `onlineExpectedPlayers` post-snapshot y lo pasa al `partidaInicial`, a `OnlineMatchSessionBuilder` y a `OnlineStartupGate`. Verificar que no quede ningún uso del valor viejo cacheado entre "cambié la cantidad" y "toqué iniciar" (revisar `buildOnlineBaseSession`, `initialMatchPayload`, y el gate de arranque `OnlineStartupGate.evaluate`, `OnlineStartupGate.kt:41`). Al bajar la cantidad, revalidar `allOnlinePlayersReady()` (`LobbyActivity.kt:1570`) sobre los presentes.

**3.4 — No tocar el reparto.** El preset de `normalizedRoleComposition` ya cubre 5-15 (y 3-4 en `modoPrueba`). No agregar ni cambiar roles.

### Verificación
- Sala creada para 7, llegan 6: el host baja el selector a 6 (o toca "jugar con los presentes"), todos listos, y la partida arranca con 6 y la composición correcta (asesino+médico+comisario+aldeanos), sin mercenario.
- El host no puede bajar por debajo del mínimo ni iniciar sin que todos los presentes estén listos.
- Un invitado ve actualizarse el "n/total" cuando el host cambia la cantidad.

---

## Parte 4 — Salto de noche en online (solo online). **ENTREGAR DESPUÉS DE LA PARTE 1.**

### Diagnóstico
- La noche online avanza **sub-fase por sub-fase, una por rol** (`GamePhase`: `NOCHE_ASESINO`, `NOCHE_MERCENARIO`, `NOCHE_POLICIA`, `NOCHE_MEDICO`, `NOCHE_ORACULO`; `GameModels.kt:394-406`), esperando el **timer completo** de cada una (doc: "La noche y la votacion esperan el timer completo. No hay avance temprano aunque todos hayan actuado"). En el preset online cada sub-fase activa tiene un solo actor.
- El patrón "todos listos → el host adelanta" **ya existe** para el debate: `maybeAdvanceOnlineReadyVote` evalúa `OnlineVoteReadyGate` y, si `canSkip`, salta a votación, respetando un piso de tiempo mínimo (`readyVoteUnlockRemainingMs`) — `GameplayMockActivity.kt:3904-3923`. Reusar esa idea.

### Arreglo

**4.1 — La acción es la señal de "listo" (sin botón nuevo).** En cada sub-fase nocturna, el host adelanta apenas el actor de esa sub-fase mandó una **acción válida** para la ronda/fase actual (las acciones ya se registran en `partidas/{id}/acciones`), respetando un **piso mínimo** de 3-4s (para que se lea el cartel y el actor pueda cambiar su elección antes de que cierre). Mantener el **timer como tope**: si el actor nunca manda, la sub-fase cae igual por timer como hoy.

**4.2 — Actor ausente/muerto/desconectado no traba.** Si en la sub-fase no hay un actor vivo+conectado que deba actuar (rol no presente, muerto o desconectado), adelantar apenas cumplido el piso, sin esperar el timer completo. La acción de un desconectado cuenta como ausente (regla ya vigente).

**4.3 — Implementación sugerida.** Un helper análogo a `OnlineVoteReadyGate`, p. ej. `object OnlineNightReadyGate` con:
```kotlin
fun shouldAdvance(
    isHost: Boolean,
    expectedActorPresent: Boolean,   // hay un actor vivo+conectado que debe actuar en esta sub-fase
    actorActed: Boolean,             // ese actor ya mandó acción válida para (ronda, phaseIndex)
    elapsedMs: Long,
    floorMs: Long = 3_500L
): Boolean
```
Regla: `isHost && elapsedMs >= floorMs && (!expectedActorPresent || actorActed)`. El host lo evalúa en su listener de `acciones` (o en el tick de fase) para la sub-fase nocturna actual y, si da `true`, **publica el próximo estado** (adelanta) en vez de esperar el timer. Encadenadas las sub-fases, la noche entera colapsa a "lo que tarden en actuar" y cae en `DIA_DEBATE`.

**4.4 — Alcance acotado.** Solo la noche. No tocar `DIA_DEBATE` (ya tiene su "listos", `:3904`) ni `VOTACION`/`DESEMPATE_VOTACION` (ya tienen su gate). No tocar el modo local (ya tiene su botón de saltar).

**4.5 — Fuga de info: aceptable.** Que una sub-fase corte rápido revela "ya actuó ese rol", no *quién* es. Para una partida entre amigos es aceptable y el piso de 3-4s lo suaviza.

### Verificación
- Partida online de 6: la noche se resuelve a los pocos segundos cuando asesino/policía/médico ya eligieron; nadie queda mirando timers vacíos.
- Un actor nocturno desconectado no cuelga la noche: cae por piso/timer.
- El debate y la votación siguen funcionando igual que antes.

---

## Parte 5 — Ventanas de "continuar" online: máximo 6s + colapsar el doble botón del amanecer

### Diagnóstico
- El gate compartido "CONTINUAR · LISTOS n/total" cubre dos ventanas: **AMANECER** (en `btnAction`) y **RESULTADO/expulsión** (en `btnContinueVoteResult`) — `GameplayMockActivity.kt:1371-1416`. Hoy el mínimo es 3s y el **máximo 10s** (`OnlinePresentationGate.kt:19-20`): cualquiera puede tocar CONTINUAR pasados 3s; si todos tocan, salta; si no, cae solo a los 10s.
- **El doble botón que viste es real, y es en el amanecer.** Cuando alguien muere, primero aparece `btnContinueDeathReveal` = "CONTINUAR" (auto-timeout a **9s**, `REVEAL_CONTINUE_TIMEOUT_MS`, `GameplayMockActivity.kt:7403`; flujo `showDeathRevealContinue`/`continueDeathReveal`, `:6126-6151`) para cerrar la animación de muerte, y **recién después** aparece el gate compartido "CONTINUAR · LISTOS n/total" en `btnAction`. Son hasta ~19s y dos confirmaciones para un amanecer con muerte. La **expulsión** ya usa un solo botón (el gate compartido en `btnContinueVoteResult`).

### Arreglo

**5.1 — Bajar el máximo del gate compartido a 6s.** En `OnlinePresentationGate.kt:20`, `MAXIMUM_DISPLAY_MS = 6_000L`. Mantener `MINIMUM_DISPLAY_MS = 3_000L` y el acelerar-por-votos (LISTOS n/total). Resultado: si todos confirman, salta entre 3-6s; si no, cae solo a los 6s. Aplica a AMANECER y a RESULTADO (los dos casos que mencionaste: expulsión y amanecer, "no hay mucho para leer").

**5.2 — Colapsar el doble botón del amanecer (solo online).** Que la revelación de muerte **no exija su propio tap** en online: cuando la animación termina, auto-continuar sin dejar `btnContinueDeathReveal` como paso obligatorio, cayendo directo al gate compartido (que ya muestra el amanecer; la muerte queda en la crónica/mesa). Concretamente, en `showDeathRevealContinue()` (`GameplayMockActivity.kt:6126`): si `isOnlineGameplay()`, invocar `continueDeathReveal()` tras un beat corto (p. ej. 800-1000ms) en vez de mostrar el botón como obligatorio. Así en online queda **una sola** confirmación acelerable (el gate de máx 6s). En local, dejar el botón como está.

**5.3 — No tocar las ventanas privadas.** El feedback privado del detective/oráculo (`btnContinuePrivateFeedback`, `btnContinueOracleReveal`) contiene info que el jugador necesita leer (p. ej. "X es traidor"). **No** auto-saltarlas: se quedan como están.

### Verificación
- Online, amanecer con muerte: **una sola** confirmación (máx 6s), no dos botones seguidos.
- Online, amanecer sin muerte y expulsión: el gate cae solo a los 6s si nadie acelera, y antes si todos tocan CONTINUAR.
- El detective sigue viendo su resultado de investigación sin que se lo salteen.

---

## Parte 6 — Auditoría completa de responsive (todas las pantallas, priorizando teléfonos)

### Diagnóstico
En teléfonos Samsung (relaciones de aspecto y densidades distintas) se cortan carteles. Ejemplo confirmado por el usuario: en el **buscador de salas** la fila de sala corta el botón "ENTRAR" a la derecha (2ª captura). El proyecto tiene muchas dimensiones fijas (CLAUDE.md: `activity_gameplay_mock.xml` con 99 dimensiones fijas), que es la causa típica.

### Arreglo (auditoría, **no** reescritura)
**6.1 — Recorrer todas las pantallas** y listar textos/controles que se cortan o se salen en teléfonos angostos y anchos:
- Buscador de salas (`LobbyBrowserActivity`): fila de sala + botón "ENTRAR" (empezar por acá, es el bug visible).
- Lobby (`activity_lobby` / `LobbyActivity`): título, código de sala, botones COPIAR/COMPARTIR, LISTO/NO LISTO, filas de jugadores.
- Gameplay apaisado (`activity_gameplay_mock`): cartel de rol, controles de acción, botones de continuar, ventanas de evento (amanecer/expulsión/desempate).
- Menú, perfil, selección de perfil, roles, ayuda, opciones.

**6.2 — Reemplazar anchos/altos fijos por responsivos donde se corta:** `wrap_content` + `maxWidth`, `layout_weight`, migrar a `ConstraintLayout` con `0dp`/chains donde convenga, `TextView` con `ellipsize`/`autoSizeTextType`, y `minWidth`/`minHeight` de **48dp** en touch targets (CLAUDE.md). Priorizar el botón "ENTRAR" de la fila del buscador primero.

**6.3 — Respetar las restricciones del proyecto** (CLAUDE.md): mantener identidad medieval/dorada, no cambiar la orientación de cada pantalla, cambios chicos y justificados por pantalla, mover texto duplicado/accesibilidad a `strings.xml` cuando se toque una pantalla.

**6.4 — Verificar** con previews/Layout Inspector a **320dp, 360dp (típico Samsung), 411dp** y densidades altas. Entregable: lista de cada cartel corregido con `archivo:línea`; el usuario valida en Android Studio.

### Verificación
- La fila del buscador muestra el botón "ENTRAR" completo en 360dp.
- Ningún cartel de lobby/gameplay queda cortado en 320-411dp.
- Los touch targets llegan a 48dp donde el layout lo permite.

---

## Orden de entrega

Cada parte compila y es probable por sí sola. Orden recomendado:

1. **Parte 1 (host + reinicio de sala)** — base; desbloquea revanchas y reinicios.
2. **Parte 2 (chat limpio)** — engancha con la revancha de la Parte 1.
3. **Parte 3 (cantidad flexible)** — lobby/inicio.
4. **Parte 5 (botones de continuar: 6s + doble botón)** — chico e independiente, se puede colar acá.
5. **Parte 4 (salto de noche online)** — **después de la Parte 1 sí o sí** (mismo camino de avance de fase del host).
6. **Parte 6 (responsive)** — independiente; en paralelo o al final.
