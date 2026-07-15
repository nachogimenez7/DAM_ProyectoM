# SPEC — Rediseño del lobby online (opciones, chat de lobby, votación de mapa)

> Para Codex. Español, `archivo:línea`. **No compilar** (el usuario valida en Android Studio). **Ciclo nuevo de features** (post-estabilización): tres partes independientes que rediseñan el lobby. El diseño ya se validó con mockups; este spec baja esos mockups a implementación. Priorizar teléfonos (portrait) y conservar la identidad medieval/dorada. **Textos claros y entendibles**: español, sentence case, sin jerga, con una línea de ayuda breve donde haga falta.
>
> **Decisiones ya tomadas con el usuario (no re-preguntar):**
> 1. **Chat de lobby y votación de mapa: SOLO online.** En local (vs bots) no van (los bots no votan ni chatean). En local, `GONE`.
> 2. **Reorg de opciones:** online = **un** botón "Opciones avanzadas" con los tiempos adentro. Local = **dos** botones: "Opciones avanzadas" (lo real + tiempos) y "Opciones de testeo" (Modo test rápido siempre + switches de debug + forzar rol, estos solo en builds debug).
> 3. **Votación de mapa: voto en vivo.** Cada jugador toca su mapa, el líder queda preseleccionado, se **fija al iniciar**; **empate lo decide el anfitrión**.
> 4. **Chat: visible por default y ocultable.** Vista previa abajo + hoja expandible; avisos de sistema (dorados) + emotes rápidos.
> 5. **Tiempos personalizados se conservan:** el modo "Personalizado" + los +/− por cada tiempo siguen existiendo, ahora dentro de "Opciones avanzadas".
>
> **Ya implementado, NO tocar:** la cantidad flexible de jugadores (selector `onlinePlayerTargetPanel` con `btnDecreaseExpectedPlayers`/`btnIncreaseExpectedPlayers`/`onlineExpectedPlayersText` y botón `btnPlayWithPresent`, `activity_lobby.xml:137-198`) ya está hecha de la tanda anterior.
>
> **Fuera de este spec (backlog):** rediseño de la pantalla de asignación de roles (`AssigningRolesActivity`), que quedó vieja; se retoca más adelante.

---

## Parte 1 — Reorganización de opciones (online + local)

### Diagnóstico
- Hoy hay dos botones de configuración: `btnTimingOptions` ("TIEMPOS") y `btnAdvancedOptions`, uno al lado del otro (`activity_lobby.xml:457` y `:475`). Ambos se muestran al host en online (`updateOnlineControlState`, `LobbyActivity.kt:1942`).
- Online — `showOnlineAdvancedOptionsDialog` (`LobbyActivity.kt:2615`) tiene **solo 2 switches**: "Mostrar roles al morir" y "Mostrar votos individuales". No incluye tiempos.
- Local — `showAdvancedOptionsDialog` (`LobbyActivity.kt:2166`) mezcla **real** (roles al morir, votos individuales, lectura inicial del rol, composición de roles) con **testeo** (Modo test rápido + 4 switches solo en builds debug: obedecer votos, forzar empates, bots no matan al humano, bots no votan al humano).
- Tiempos — `showTimingDialog` (`LobbyActivity.kt:1986`): presets `GameTimingPreset` + botón "Personalizado" + un +/− por cada `TimingField` (transición, noche, discusión, votación).
- Forzar rol (debug) — `debugRoleSection`/`btnDebugRole` (`activity_lobby.xml:412` y `:430`), cicla `debugRoles` (`LobbyActivity.kt:95`) y se aplica al iniciar en local (`LobbyActivity.kt:220`).

### Arreglo

**1.1 — Extraer la sección de tiempos a un helper reutilizable.** Sacar el cuerpo de `showTimingDialog` a un helper que devuelva la vista de tiempos (presets Rápido/Normal/Lento + "Personalizado" + steppers por tiempo), p. ej. `buildTimingSection(draftHolder): View`, para poder incrustarlo dentro de "Opciones avanzadas" sin duplicar. **Conservar el modo Personalizado y los steppers idénticos a hoy** (ejemplo de uso real: noche corta para que actúen rápido + debate largo para que discutan).

**1.2 — Online: un solo botón "Opciones avanzadas".**
- Ocultar `btnTimingOptions` en online (queda solo `btnAdvancedOptions`, con texto "Opciones avanzadas").
- `showOnlineAdvancedOptionsDialog` pasa a incluir, además de los 2 switches actuales, la **sección de tiempos** (helper de 1.1). Al aplicar, guardar `revealRolesOnDeath`, `showIndividualVotes` **y** `timingConfig` (como hace hoy el timing dialog).
- Sin nada de testeo en online.

**1.3 — Local: dos botones "Opciones avanzadas" + "Opciones de testeo".**
- En la fila de config, dejar `btnAdvancedOptions` = "Opciones avanzadas" y convertir `btnTimingOptions` en el botón "Opciones de testeo" (reusar el botón existente para no tocar el layout de más; el diálogo de tiempos suelto deja de existir). 
- `showAdvancedOptionsDialog` (local) queda con **lo real**: roles al morir, votos individuales, lectura inicial del rol, composición de roles, y la **sección de tiempos** (helper de 1.1). **Sacar de acá** el Modo test rápido y los switches de debug.
- Nuevo `showTestOptionsDialog` (local): "Modo test rápido" (siempre visible en local) y —solo si `isDebugBuild`— "IA obedece votos del chat", "Forzar empates", "Bots no te matan de noche", "Bots no te votan", y **"Forzar tu rol"** (un selector que reemplaza al `btnDebugRole` suelto; ocultar `debugRoleSection`). Si no es build debug, el diálogo muestra únicamente "Modo test rápido".
- Copys claros, una línea de ayuda por opción. Ejemplos: "Modo test rápido — saltea fases sin acción humana y acelera votaciones."; "Forzar tu rol — elegís con qué carta arrancás (solo pruebas)."

### Verificación
- Online: el host ve **un solo** botón "Opciones avanzadas" que incluye roles al morir, votos individuales y tiempos (con Personalizado). No hay botón "TIEMPOS" ni testeo.
- Local: **dos** botones. Avanzadas = real + tiempos (con Personalizado); Testeo = Modo test rápido (+ los de debug si corresponde). El forzar-rol vive dentro de Testeo, no suelto.
- En release (no debug), el diálogo de Testeo local muestra solo "Modo test rápido".

---

## Parte 2 — Chat de lobby (solo online)

### Diagnóstico / diseño
- No existe hoy. El chat del gameplay (`GameplayChatController`, ~2111 líneas) arrastra roles, canal de traidores, bots y el God-director: **no reusar**, es demasiado peso para el lobby.
- Patrón a imitar (solo lo visual): vista previa ambiente → hoja expandible, como el ambient feed del gameplay.
- Layout: el lobby es un `ScrollView lobbyBodyScroll` (`activity_lobby.xml:87`) dentro del `RelativeLayout` raíz (`activity_lobby.xml:2`). El chat va **anclado al fondo del RelativeLayout, fuera del scroll**, para quedar siempre visible; sumar padding-bottom al scroll para que el contenido no quede tapado.

### Arreglo

**2.1 — Nuevo componente liviano `LobbyChatController`.** Render de la barra ambiente + hoja expandible, envío/lectura de mensajes y avisos de sistema. Sin roles, canales, bots ni director. Se instancia solo en lobby online.

**2.2 — Firestore: subcolección nueva `partidas/{id}/chat_lobby/{mensajeId}`.** Campos espejando el chat de partida: `actorId` (== `request.auth.uid`), `speaker` (≤18), `mensaje` (1-140), `creadaEn` (serverTimestamp), `creadaEnLocal`. Listener **descendente** + `limit(~40)` y revertir para mostrar (mismo criterio ya aplicado al chat de partida, para no cortar en partidas largas). Regla nueva en `firestore.rules` espejando la de `chat` (validar autoría y tamaños). Documentar la subcolección en `docs/firebase-online-schema.md`.

**2.3 — Avisos de sistema (dorados, estilo cronista).** Generados **localmente** por cada cliente a partir de los snapshots que el lobby ya observa (presencia, listos, estado de sala, votos de mapa) — **no se escriben en Firestore** (evita costos y duplicados; todos ven los mismos eventos porque observan el mismo estado). Tipos:
- Jugador se unió / salió / fue expulsado.
- "Sala completa (6/6)" · "Faltan N listos".
- Cambio de anfitrión.
- Votación de mapa: cambio de líder ("Medieval va ganando", con throttle para no spamear) y al iniciar ("Se jugará en Medieval").
- Al reabrir para revancha: recap del resultado ("Ganó el pueblo — ¿revancha?").
Render inline en dorado con ícono, intercalados con los mensajes de jugadores por su timestamp local.

**2.4 — UI / comportamiento.**
- Barra ambiente dockeada abajo, **visible por default**, con los últimos 2-3 mensajes + hint "Tocá para hablar".
- Tocar la barra → **hoja expandible** con los mensajes, el input y una fila de **emotes rápidos**. Cerrar con la flecha o tocando afuera.
- **Ocultable:** un control (ícono ojo/flecha) colapsa la barra a una pestañita fina "Chat del feudo"; tocarla la restaura. Recordar la preferencia (SharedPreferences, namespace `TraidoresPrefs`).
- Emotes rápidos: set chico que postea un mensaje corto (reusar `EmoteCatalog` si encaja, o textos fijos).
- Solo online: en local el chat está `GONE`.

### Verificación
- Dos celulares en la misma sala ven los mensajes en tiempo real.
- Aparecen los avisos de sistema (unirse, listo, líder de mapa) en dorado, intercalados.
- Se puede ocultar y restaurar el chat; por default está visible.
- En local no aparece nada de chat.

---

## Parte 3 — Votación de mapa (solo online)

### Diagnóstico
- Hoy el mapa lo elige **solo el host**: `setupMapSelector` (`LobbyActivity.kt:1603`) bloquea a los invitados (`isOnlineGuest() || isFirestoreOnlineLobby()`). El área de mapa en el layout: `selectedMapCard` (preview: `selectedMapImage`, `selectedMapName`, `selectedMapRole`) + miniaturas `mapPampa`/`mapGrecia`/`mapMedieval` (`activity_lobby.xml:299-397`).

### Arreglo

**3.1 — Voto en vivo por jugador.** Cada jugador escribe su voto en su doc de presencia: campo nuevo `votoMapa` (clave de mapa: `pampa`/`grecia`/`medieval`). Tocar una miniatura = emitir/cambiar tu voto (habilitar el tap **para todos** en online; quitar el bloqueo de invitado para este fin puntual). Regla en `firestore.rules`: permitir `votoMapa` (string) en el doc propio del jugador.

**3.2 — Agregación y líder.** El lobby ya escucha la colección de jugadores; sumar los `votoMapa`, contar por mapa y determinar el líder (más votado). En cada miniatura: mostrar el conteo + avatarcitos de quién votó; resaltar el líder (borde dorado + etiqueta "Va ganando"). El `selectedMapCard`/`selectedMapName`/`selectedMapRole` reflejan el **líder en vivo**, incluyendo tema + "Rol exclusivo: X".
- Mapa → rol exclusivo (informativo): Pampa · Payador, Grecia · Oráculo, Medieval · Bufón (ya lo sabe `roleMeta`, `LobbyActivity.kt:2673`).

**3.3 — Se fija al iniciar; empate lo decide el anfitrión.** El campo `mapa`/`mapaNombre` del room **no** se reescribe en cada cambio de líder: se resuelve solo **al iniciar**. Al tocar iniciar, el host resuelve el ganador (líder de votos) y lo escribe en el room + `partidaInicial`. Si hay **empate**, el host elige entre los mapas empatados (diálogo chico "Empate: elegí el mapa"); si el host votó, su voto puede desempatar. Aviso en el chat: "Se jugará en Medieval".
- Default de voto: al unirse, sin voto. Mostrar "sin voto" para quien no votó. La partida puede iniciar aunque no todos hayan votado (los que no votaron no cuentan; si **nadie** votó, queda el mapa actual del room).

**3.4 — Solo online.** En local, `setupMapSelector` queda como hoy (host elige, sin votación).

### Verificación
- Cada jugador puede votar su mapa; conteos y líder se actualizan en vivo en todos los celulares.
- El preview (tema + rol exclusivo) sigue al líder.
- Al iniciar se juega el mapa más votado; en empate el host elige; sale el aviso en el chat.
- En local no hay votación (host elige como antes).

---

## Orden de entrega

1. **Parte 1 (reorg de opciones)** — independiente y chica; buen arranque, no depende de las otras.
2. **Parte 3 (votación de mapa)** — reusa el área de mapa y los docs de jugador.
3. **Parte 2 (chat de lobby)** — el componente más nuevo; sus avisos de sistema consumen el estado de la votación (Parte 3), así que conviene último.

Recordatorio: la cantidad flexible (`onlinePlayerTargetPanel` + `btnPlayWithPresent`) **ya está**; no se toca.
