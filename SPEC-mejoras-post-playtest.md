# SPEC — Pulido post-playtest (4 mejoras)

> Para Codex. Español, `archivo:línea` donde aplica. No compilar (el usuario valida en Android Studio). Cuatro mejoras salidas de un playtest de un amigo, **independientes entre sí**. Las secciones están en el **orden de implementación sugerido** (de arriba a abajo): las secciones 1 y 2 son quick wins de alto impacto visual; la 4 es la más grande y su parte online conviene dejarla al final y probarla con varios dispositivos.
>
> Complementa `SPEC-dificultad-ia-bots.md` (presión/tono de bots), cuyo split de `LocalBotAi.kt` (Parte 1) **ya está hecho** — por eso se citan `BotDialogueLines.kt` y `BotConversationMemory.kt` como archivos existentes.

## Contenido
1. Chat de bots: sacar emojis/mojibake + arreglar el eco
2. Ventana de rol: timing inmediato + layout sin scroll
3. Opciones de accesibilidad: overlay landscape en lobby y partida
4. Listos para votar: saltar el debate estilo Among Us

---

# 1) Chat de bots — sacar emojis/mojibake + arreglar el eco

Feedback: en la conversación de bots aparece "algo inentendible" (emojis y acentos corruptos) y el diálogo "se traba" (los bots repiten la frase del anterior).

## 1.1 Eliminar los emojis de los bots
- Borrar `withOccasionalEmoji` (`BotDialogueLines.kt:1372-1392`) y `canUseOccasionalEmoji` (`BotDialogueLines.kt:1394-1399`).
- Único call site: `finishSpeech` (`BotDialogueLines.kt:1369`). Cambiar
  ```kotlin
  return withOccasionalEmoji(guarded, session, bot, context)
  ```
  por
  ```kotlin
  return guarded
  ```
- Verificar con grep que no queda ningún otro uso de `withOccasionalEmoji` / `canUseOccasionalEmoji` en `app/src`.

Con esto desaparecen los literales corruptos `ðŸ¤"`, `ðŸ‘€`, `ðŸ˜°` de `BotDialogueLines.kt:1385-1388`. Encaja con el tono más serio del spec de dificultad.

## 1.2 Barrer el mojibake del resto del repo
La corrupción **no es solo de emojis**: hay acentos rotos por la misma codificación no-UTF-8. Ejemplo confirmado: `"...no te escondas atrÃ¡s de $speaker"` (debería ser "atrás") en `BotDialogueLines.kt:983`.
- Grepear todo `app/src/main` por secuencias mojibake típicas: `Ã`, `ð`, `Ÿ`, `˜`, `¤`, `‘`, `’`, `œ`, `€`, `»` pegadas dentro de palabras/strings.
- Corregir a UTF-8 correcto (`á é í ó ú ñ ¿ ¡`) **o**, para mantener el estilo del archivo (que a propósito escribe sin acentos: "murio", "aca", "q"…), reescribir la palabra sin acento. Criterio: que no quede ningún carácter raro en pantalla.
- Asegurar que los archivos queden **guardados en UTF-8** (si el editor los reabre en Windows-1252, vuelve a romper).

*(Nota: `normalizedForParsing` ya limpia emojis y acentos para el parsing — `BotConversationMemory.kt:576-581` deja solo `[a-z0-9ñ ]` — así que esto es puramente visual y no cambia ninguna lógica de decisión de los bots.)*

## 1.3 Eco entre bots — causas (confirmadas en código)
1. **Dedup débil:** `dedupeBotMessages` (`BotDialogueLines.kt:1401-1403`) hace `distinctBy { normalizedForParsing(it.second).take(42) }`. Compara solo los **primeros 42 caracteres**, así que un prefijo corto ("dale, ", "bien, ") esquiva el filtro: `"dale, X"`, `"bien, X"` y `"X"` sobreviven las tres.
2. **Solo dentro del lote:** se aplica por lista generada, no contra lo ya dicho. Se llama en `openingDebateMessages` (`LocalBotAi.kt:525`) y `votingIntentMessages` (`LocalBotAi.kt:625`).
3. **Reacciones sin dedup:** `reactionsToHumanMessage` (`LocalBotAi.kt:628-741`) **no** llama `dedupeBotMessages` (return sin dedup en `LocalBotAi.kt:740`). Ahí es donde más se ve el eco de la captura.
4. **Sin memoria de historial:** ninguna generadora compara contra los últimos mensajes públicos, así que un bot repite lo que otro dijo hace segundos.

## 1.4 Eco — fixes

**Núcleo normalizado sin filler.** Nuevo helper en `BotDialogueLines.kt`:
```kotlin
private val LEADING_FILLERS = listOf(
    "dale", "bien", "ok", "oka", "okey", "che", "eh", "mira", "mira vos",
    "ya", "bueno", "obvio", "claro", "tal cual", "y si", "nada", "posta", "igual"
)
internal fun botMessageCore(text: String): String {
    var core = normalizedForParsing(text)
    var changed = true
    while (changed) {
        changed = false
        for (f in LEADING_FILLERS) {
            if (core == f) return ""
            if (core.startsWith("$f ")) { core = core.removePrefix("$f ").trim(); changed = true }
        }
    }
    return core
}
```

**`dedupeBotMessages` compara el núcleo completo (no 42 chars):**
```kotlin
internal fun List<Pair<String, String>>.dedupeBotMessages(): List<Pair<String, String>> =
    distinctBy { botMessageCore(it.second) }
```

**Filtro contra el historial reciente** (descarta lo que ya apareció en los últimos ~6 mensajes públicos):
```kotlin
internal fun List<Pair<String, String>>.dropEchoesOfRecentChat(session: GameSession): List<Pair<String, String>> {
    val recentCores = recentPublicMessages(session).takeLast(6).map { botMessageCore(it.message) }.filter { it.isNotBlank() }
    return filterNot { (_, text) ->
        val core = botMessageCore(text)
        core.isNotBlank() && recentCores.any { it == core || it.contains(core) || core.contains(it) }
    }
}
```
(`recentPublicMessages` ya existe en `BotConversationMemory`.)

**Aplicar en las tres generadoras:**
- `openingDebateMessages` (`LocalBotAi.kt:525`): `...}.dropEchoesOfRecentChat(session).dedupeBotMessages()`.
- `votingIntentMessages` (`LocalBotAi.kt:625`): igual.
- `reactionsToHumanMessage` (`LocalBotAi.kt:740`): encadenar `.dropEchoesOfRecentChat(session).dedupeBotMessages()` al `return` (hoy no tiene ninguno).

**Preferir menos mensajes antes que repetir:** si tras filtrar la lista queda más corta, está bien — que se emitan menos líneas en vez de rellenar con repeticiones. No re-generar para "completar" el cupo.

## 1.5 Verificación (sección 1)
- En el chat, **ningún bot repite textualmente** la frase del anterior (ni con "dale,"/"bien," delante).
- No aparecen emojis ni caracteres raros (`ðŸ...`, `Ã...`).
- La conversación sigue con volumen razonable (no queda muda por filtrar de más).
- Correr la suite de tests de bots si existe; el cambio no debe alterar decisiones (voto/target), solo el texto mostrado.

---

# 2) Ventana de rol — timing inmediato + layout sin scroll

Feedback: la lectura inicial "se siente sin sentido" y la imagen/texto quedan cortados al scrollear. Decisión: EMPEZAR inmediato por defecto, con opción hasta 10s para nuevos, cuenta regresiva **visible** cuando hay espera, y agrandar el panel para no scrollear (como la ventana de ganadores).

**Contexto:** la ventana se arma en `showRolePreview(initialReveal)` (`GameplayMockActivity.kt:4820`). En el arranque (líneas 4866-4880) el botón EMPEZAR se **oculta** (`View.GONE`) y aparece cuando corre `enableInitialRoleReadyRunnable` (`GameplayMockActivity.kt:190-197`) tras `initialRoleReadingDelayMs()` (`:6155-6158`), que lee el pref `role_reading_seconds` con default **6** (`DEFAULT_ROLE_READING_SECONDS = 6`, `:6520`). Si se toca antes, `closeRolePreview` (`:4900-4906`) da sonido de ERROR + Toast — sin mostrar cuenta regresiva. El lobby ya tiene el control 0–10s (`LobbyActivity.kt:1700`, dibujo `1790`/`1804`, guardado `2113`, default `2574`).

## 2.1 Default a 0 (inmediato)
- `DEFAULT_ROLE_READING_SECONDS = 0` en `GameplayMockActivity.kt:6520` y en `LobbyActivity.kt:2574`.
- Con esto EMPEZAR queda disponible al instante (ya existe la rama `readingDelayMs == 0L` → corre el runnable de inmediato, `GameplayMockActivity.kt:4873-4874`).
- Verificar si `RoleRevealConfig.normalized()` (`GameModels.kt:122-133`, con `minimumReadingSeconds`) define otro default de lectura que también deba bajar a 0; si no alimenta al `showRolePreview` local, solo anotarlo.

## 2.2 Cuenta regresiva visible cuando hay espera (> 0s)
Botón **visible pero deshabilitado** que muestre el conteo (en vez de oculto). En `showRolePreview`, rama `initialReveal` (`:4866-4880`):
- Botón `View.VISIBLE`, `isEnabled = false` durante la espera.
- Reemplazar `enableInitialRoleReadyRunnable` (`:190-197`) por un runnable que **tickee cada segundo**:
```kotlin
private var roleReadingSecondsLeft = 0
private val roleReadingTickRunnable = object : Runnable {
    override fun run() {
        if (!initialRoleReadingActive || !::btnContinueRolePreview.isInitialized) return
        if (roleReadingSecondsLeft <= 0) {
            btnContinueRolePreview.isEnabled = true
            btnContinueRolePreview.text = "EMPEZAR"
        } else {
            btnContinueRolePreview.text = "EMPEZAR (${roleReadingSecondsLeft})"
            roleReadingSecondsLeft--
            autoAdvanceHandler.postDelayed(this, 1000L)
        }
    }
}
```
En `showRolePreview` (rama initialReveal): `roleReadingSecondsLeft = initialRoleReadingDelayMs() / 1000` (Int), botón VISIBLE + `isEnabled = (segundos == 0)`, `removeCallbacks(roleReadingTickRunnable)` y `roleReadingTickRunnable.run()`. Cancelar el runnable donde hoy se cancela `enableInitialRoleReadyRunnable` (`:852`).

## 2.3 Sacar el toast punitivo
Con el botón mostrando el conteo, en `closeRolePreview` (`:4895-4907`) quitar el bloque del Toast "Toma unos segundos..." + sonido ERROR. El `return` temprano puede quedar (si está deshabilitado no cierra), pero **sin** feedback negativo.

## 2.4 Lobby: etiquetar 0 como "Inmediato"
En el control de segundos del lobby (`LobbyActivity.kt:1790` en adelante), mostrar el valor 0 como **"Inmediato"** en vez de "0 s"; el resto (1–10) igual. Es la opción "para usuarios nuevos": suben el número y aparece la cuenta regresiva de 2.2.

## 2.5 Layout: agrandar el contenedor
`rolePreviewContent` es un cuadrado **fijo 330dp × 330dp** (`activity_gameplay_mock.xml:701-702`) con `ScrollView` interno (`rolePreviewScroll`, `:770-781`) y carta a `128×146dp` (`:791-797`) → en landscape obliga a scrollear. La ventana de ganadores llena la pantalla: `winnerRevealPanel` es `match_parent` + `layout_marginHorizontal="36dp"` (`:1602-1606`) con `winnerRevealScroll` `fillViewport="true"` (`:1627-1631`).

Cambiar `rolePreviewContent` (`:699-708`):
- `layout_width="match_parent"` con `layout_marginHorizontal="48dp"` (o `maxWidth` ~600dp).
- `layout_height="wrap_content"` con `layout_marginVertical="24dp"`.

Recordatorio del proyecto: `activity_gameplay_mock.xml` ya tiene 99 dims fijas — **modificar/quitar**, no sumar fijas; preferir `wrap_content` + márgenes.

## 2.6 Layout: pasar el contenido a horizontal (landscape)
El `LinearLayout` interno (`:723-727`) hoy es vertical. Pasarlo a **dos columnas**:
- **Izquierda:** la carta `rolePreviewImage` más grande (p. ej. `150×172dp`), centrada vertical.
- **Derecha:** "TU ROL" + `rolePreviewName` + `rolePreviewTeam` arriba, y debajo QUE HACE (`rolePreviewFunction`) y CONSEJO (`rolePreviewAdvice`).

Con la carta al costado, el texto usa toda la altura y no necesita scroll. **Mantener** el `ScrollView` (`:770-781`) envolviendo solo la columna de texto como salvaguarda para "Grande" (con `fillViewport="true"`), pero en Normal no debería activarse. Mantener los ids existentes (`rolePreviewImage`, `rolePreviewName`, `rolePreviewTeam`, `rolePreviewFunction`, `rolePreviewAdvice`, `rolePreviewMapBackground`, `rolePreviewScroll`) para no tocar el binding (`GameplayMockActivity.kt:4848-4863`).

## 2.7 Verificación (sección 2)
- Partida local nueva: EMPEZAR **al instante**, sin espera ni toast.
- Subiendo a 5–10s en el lobby: el botón muestra "EMPEZAR (5)"… "EMPEZAR (1)" → "EMPEZAR", sin toast de error al tocar antes.
- La carta + QUE HACE + CONSEJO entran **sin scrollear** en un teléfono en landscape con texto Normal; en "Grande", si aparece scroll, la imagen no se corta (queda fija al costado).
- Online: el arranque sigue OK (`isOnlineStartupPhase` / `markOnlineInitialRoleRead`, `:4908-4924`) — EMPEZAR cierra y marca `rolLeido`.

---

# 3) Opciones de accesibilidad — overlay landscape en lobby y partida

Decisión: mantener el menú de Opciones completo en el menú principal, y agregar en **lobby** y **partida** un panel liviano **solo de accesibilidad** (sin Firebase/cuenta/idioma), que **respete la orientación** (overlay landscape, sin rotar).

**Problema:** el engranaje in-game abre la `OpcionesActivity` **completa** — `btnSettings` (`GameplayMockActivity.kt:499`) hace `startActivity(Intent(this, OpcionesActivity::class.java))` (`:776-778`). `OpcionesActivity` es **portrait**, así que desde el gameplay/lobby (landscape) gira la orientación y muestra Firebase/cuenta/idioma, fuera de lugar. El **lobby no tiene** acceso a opciones.

**Alcance del overlay** (reusar las mismas prefs, namespace `TraidoresPrefs`):
- Música on/off + volumen → `AudioPreferences.MUSIC_ENABLED` y `"music_volume"`.
- Efectos on/off + volumen → `AudioPreferences.EFFECTS_ENABLED` y `"voice_volume"`.
- Vibración → `"vibration_on"`.
- Tamaño de texto (Compacto/Normal/Grande, 0/1/2) → `"gameplay_text_size"`.

Fuera del overlay (queda solo en el menú principal): prueba de Firebase, tarjeta de cuenta, idioma, "restablecer opciones".

## 3.1 Layout reutilizable
Nuevo `res/layout/view_accessibility_options.xml`: panel landscape con la estética medieval (paneles marrón oscuro, borde dorado, `BtnGold`/`BtnDark`), con los controles del alcance. Seguir el patrón de los overlays de `activity_gameplay_mock.xml` (p. ej. `rolePreviewOverlay`, `:690-708`). Título "ACCESIBILIDAD" + botón CERRAR.

## 3.2 Controlador compartido
Nueva clase `AccessibilityOptionsOverlay` que recibe la `View` inflada + `Context` y bindea **controles ↔ prefs**, replicando `OpcionesActivity` sin la parte online:
- Switches/seekbars como `OpcionesActivity.kt:148-169` (incluye `MusicManager.refresh(context)` y `GameplayEffects.play(...)` en los cambios).
- Tamaño de texto como `OpcionesActivity.kt:132-143`.
- Guardar en las mismas keys → menú principal y overlay quedan sincronizados.

## 3.3 Puntos de entrada (overlay, sin cambiar de Activity → no rota)
- **Gameplay:** cambiar `btnSettings` (`:776-778`) para **mostrar el overlay** en vez de `startActivity(OpcionesActivity)`. Incluir el layout como `FrameLayout` overlay oculto en `activity_gameplay_mock.xml` (patrón `rolePreviewOverlay`) y togglearlo. Que el back lo cierre: engancharlo en el `onBackPressed` que ya maneja overlays (`:1018` en adelante).
- **Lobby:** agregar un botón de engranaje que muestre el mismo overlay (incluido en el layout del lobby). El lobby ya es landscape.

## 3.4 Tamaño de texto en vivo
El gameplay lee `"gameplay_text_size"` en `GameplayMockActivity.kt:6141`. Al cambiarlo desde el overlay, **re-aplicar** (re-render de tabla/paneles/mensajes) para que se note sin salir. Si el re-apply en caliente es complejo, mínimo: aplicarlo **al cerrar** el overlay (`renderGame()`/refresh de textos). Anotar cuál de las dos se hizo.

## 3.5 No tocar el menú principal
`OpcionesActivity` (la del menú) queda **igual**, completa. Este overlay es adicional.

**Notas:** el overlay no crea Activity → respeta la orientación (constraint del proyecto). Si se repiten etiquetas ("Música", "Efectos", "Tamaño del texto"), moverlas a `strings.xml` y usarlas en ambos lugares (convención: mover texto repetido a recursos al tocar una pantalla).

## 3.6 Verificación (sección 3)
- Desde la **partida** (landscape): el engranaje abre el panel **sin rotar** y **sin** Firebase/cuenta/idioma; cambiar música/efectos/vibración/tamaño aplica.
- Desde el **lobby**: el engranaje abre el mismo panel.
- Cambiar el tamaño de texto y confirmar que el gameplay lo refleja (en vivo o al cerrar).
- Lo elegido persiste y coincide con el menú principal (misma pref).
- El back cierra el overlay sin salir de la partida/lobby.

---

# 4) Listos para votar — saltar el debate estilo Among Us

Decisión: durante el debate, botón **"LISTOS PARA VOTAR"**; cada jugador vivo se marca listo, se muestra **progreso X/N**, y con **unanimidad** se salta directo a la votación. En **local** los bots se auto-marcan escalonados (**opción A**, con efecto de contador). En **online** se sincroniza reusando el patrón del gate de arranque. Implementar **4.1 (local) antes que 4.2 (online)**.

**Contexto de fases:** `DIA_DEBATE` (`GameModels.kt:325`) → `VOTACION` (`:327`). Avance normal: al expirar el timer, `onCountdownExpired` (`GameplayMockActivity.kt:4302`) resuelve la fase; para el debate local termina en `GameEngine.resolveDayDebate` (`GameEngine.kt:409`) → `transitionTo(GamePhase.VOTACION, …)` (`:431-437`). El botón **fuerza ese mismo avance antes de tiempo**. El timer sale de `GameTimingConfig.discussionSeconds` (`GameModels.kt:213-220`) y queda como **fallback**.

**Reglas comunes (local y online):**
- **Denominador N = jugadores vivos que pueden votar.** Excluye muertos **y silenciados por el Mercenario** (el silenciado "no podrá hablar ni votar", no puede bloquear la unanimidad → no cuenta en N).
- **Unánime** (todos los que cuentan), no mayoría.
- **Toggle:** se puede cancelar antes de completar.
- **Progreso visible X/N** (a todos, en online).
- Solo en `DIA_DEBATE`. **No** en `CONTRAPUNTO` (`:326`, charla restringida) ni otras fases.

## 4.1 Local (opción A: bots escalonados)

**Estado y UI:** botón durante `DIA_DEBATE` tipo **"LISTOS 1/10"** (X = listos, N = vivos-que-votan), en la zona de controles del día (patrón de botón contextual alrededor de `GameplayMockActivity.kt:3269-3319`). Set en memoria: `private val readyToVote = mutableSetOf<String>()`.

**Flujo:**
1. El humano toca → se agrega a `readyToVote` (1/N); el botón pasa a "cancelar".
2. Programar a **cada bot vivo-que-vota** para marcarse listo escalonado:
   ```kotlin
   val delay = 800L + (stableNoise("${session.code}:${session.round}:${bot.name}:ready") % 2700).toLong() // ~0.8–3.5s
   autoAdvanceHandler.postDelayed({ markBotReady(bot.name) }, delay)
   ```
   Cada `markBotReady` agrega al set, refresca X/N y, si `readyToVote.size >= aliveVoters`, dispara el avance.
3. Cuando **listos == N** → llamar al **mismo** camino que usa el debate al expirar (resolver `DIA_DEBATE` → `VOTACION`; reutilizar la función que hoy invoca `onCountdownExpired` para el debate local, no duplicar la transición). Cancelar el countdown de debate para no doble-disparar.

**Cancelar:** si el humano destogglea antes de completar: limpiar `readyToVote`, `autoAdvanceHandler.removeCallbacks(...)` de los bots pendientes, contador a 0/N.

**Ciclo de vida:** guardar `readyToVote` + flag de cascada en `onSaveInstanceState` (junto a los `STATE_*`) y limpiar runnables en `onPause`/`onDestroy` (donde hoy se limpian los del countdown, `:852-882`) para que una rotación no deje bots a medias. Si el humano está muerto, no mostrar el botón (timer + bots resuelven).

## 4.2 Online (reusar el gate de arranque)

**Precedente a copiar:** la lectura de rol inicial ya sincroniza readiness por Firestore: cada cliente publica `"rolLeido"`/`"estadoArranque"` en su doc (ver el `set(...)` de estado del cliente en `GameplayMockActivity.kt:1458-1475`) y `OnlineStartupGate.evaluate(...)` (`OnlineStartupGate.kt:41-78`) cuenta listos y decide `canStart`; el host avanza. **Replicar ese patrón.**
1. **Publicar readiness:** agregar `"listoParaVotar"` (bool) al estado del cliente que ya se escribe (`:1458-1475`), según el toggle, solo en `DIA_DEBATE`.
2. **Gate:** nuevo `OnlineVoteReadyGate` (espejo de `OnlineStartupGate`): dado `expectedAliveVoters` y los estados, calcular `readyCount` y `canSkip = readyCount >= expectedAliveVoters` (solo vivos-que-votan, excluyendo silenciados). Exponer `readyCount`/`total` para el X/N.
3. **Avance:** el **host** avanza `DIA_DEBATE → VOTACION` cuando `canSkip`, por el mismo mecanismo con que hoy avanza fases; los guests siguen (`onlineAwaitingHostAdvance` y manejo autoritativo en `:4320-4336`).
4. **Progreso:** mostrar `readyCount/total` a todos.

**Alcance/riesgo:** toca el flujo **online experimental**. Hacerlo **después** de 4.1 (que funciona offline y es el modo principal) y probar con 2–3 dispositivos. Reutilizar el gate acota el riesgo.

## 4.3 Verificación (sección 4)
- **Local:** tocar "LISTOS" → el contador sube escalonado (1/10 → … → 10/10) y salta a votación. Cancelar antes frena la cascada y vuelve a 0/N. Si nadie toca, el timer sigue avanzando igual.
- **Local, rotando** a mitad de la cascada: no quedan bots colgados ni doble avance.
- **Online (2–3 celus):** cada uno marca "listo", todos ven X/N; con todos los vivos listos, salta para todos.
- **Silenciado:** no bloquea la unanimidad (no cuenta en N).
- En `CONTRAPUNTO` el botón **no** aparece.

---

## Orden de entrega sugerido
1. Sección 1 (chat de bots) — commit propio.
2. Sección 2 (ventana de rol) — commit propio.
3. Sección 3 (overlay de accesibilidad) — commit propio.
4. Sección 4 (listos para votar): 4.1 local primero (commit), luego 4.2 online (commit aparte, probar con varios dispositivos).
