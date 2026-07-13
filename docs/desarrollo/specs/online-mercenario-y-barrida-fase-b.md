# Spec — Mercenario en online (7+) + cierre de la barrida responsive (Fase B)

Dos partes independientes, pensadas para compilar juntas. **Parte A** suma el Mercenario al preset online (aparece con 7+ jugadores). **Parte B** cierra lo que faltaba de la barrida responsive de gameplay.

**Contexto:** el online v1 usa un preset fijo (1 asesino, 1 médico, 1 detective, resto aldeanos). El Mercenario estaba excluido a propósito (no probado), pero el motor y el chat **ya lo soportan** (ver §A0). Restricción general: no tocar el flujo de sincronización online (gates/watchdog/handoff). Cambios mínimos y localizados.

---

# PARTE A — Mercenario en el online (a partir de 7 jugadores)

## A0. Estado actual (verificado en código, para que Codex confíe)
El Mercenario ya está integrado en la lógica general; **solo falta sumarlo al preset online y probarlo**:
- `GameRules.traitorRoleKeys = {asesino, mercenario, espia}` → el mercenario **es traidor**, así que `canSeeTraitorChat` lo suscribe automáticamente al chat de traidores online (`GameplayChatController.startOnlineTraitorChatListener`). No hay que tocar el chat.
- `GameRules.killerRoleKeys = {asesino, espia}` → el mercenario **no es killer**: no participa de la resolución de víctima nocturna. Correcto: su acción es silenciar, no matar.
- La noche online es una sola ventana: `resolveOnlineNightWindow()` (`GameplayMockActivity` ~4863) ya resuelve `NOCHE_MERCENARIO` vía `GameEngine.resolveMercenary(...)`, leyendo la acción registrada.
- El registro de acción nocturna (`recordOnlinePlayerAction`) ya mapea `NOCHE_MERCENARIO -> "silenciar"`.
- El estado del silencio (`muted`, `nightSilenceTarget`, `lastSilencedRound`) se serializa en `estadoPartida` y se aplica en `OnlineMatchSessionBuilder.applyState` / `playersFromAuthoritativeState`.

## A1. Cambio del preset (el único cambio de lógica requerido)
En `LocalGameFactory.onlineSafeRoleComposition(playerCount)` (`GameModels.kt` ~688), sumar el mercenario desde 7 jugadores, igual que hace el preset RECOMENDADO:

```kotlin
fun onlineSafeRoleComposition(playerCount: Int): RoleCompositionConfig {
    val count = playerCount.coerceIn(TEST_MIN_PLAYERS, MAX_PLAYERS)
    val counts = linkedMapOf(
        RoleCatalog.POLICIA to 1,
        RoleCatalog.MEDICO to 1,
        RoleCatalog.ASESINO to 1
    )
    if (count >= 7) counts[RoleCatalog.MERCENARIO] = 1   // <-- NUEVO
    val specialCount = counts.values.sum()
    counts[RoleCatalog.ALDEANO] = (count - specialCount).coerceAtLeast(0)
    return RoleCompositionConfig(counts = counts, customized = true)
}
```

Resultado por cantidad: 5-6 jugadores = igual que hoy; **7 jugadores = 1 asesino + 1 mercenario + 1 médico + 1 detective + 3 aldeanos** (2 traidores vs 5 pueblo, mejor balance). 8+ sigue sumando solo mercenario por ahora (los demás roles especiales quedan para una fase futura; no agregar alcalde/espía/etc.).

## A2. Verificación de la fase del mercenario en online — **CRÍTICO (nunca se probó)**
No hay bug conocido, pero como nunca corrió en online, hay que probarlo end-to-end. Antes, revisar en código que estos puntos no tengan una guarda que asuma "sin mercenario":
1. Que el cliente del **mercenario** (guest) pueda seleccionar objetivo y registrar la acción durante la ventana nocturna (mismo camino que el detective/médico, que ya andan).
2. Que el host, al cerrar la noche, aplique el silencio y lo publique en `estadoPartida`.
3. Que **todos** los clientes reciban el `muted` y disparen el reveal de silencio (jaula) en el amanecer — revisar que `collectNewlyMutedPlayers()` + `maybeShowNextSilenceReveal()` se ejecuten en el camino de `applyAuthoritativeOnlineState` igual que los reveals de muerte (bug hermano del que ya arreglamos: si el reveal de silencio no llega a los invitados, aplicar el mismo criterio que a los de muerte/amanecer).
4. Que el jugador silenciado (si es un humano) quede **bloqueado de escribir en el chat y de votar** el día siguiente (`human.muted` ya lo maneja el motor; confirmar que en online el estado `muted` llega y se respeta).

## A3. Documentación
- `docs/firebase-online-schema.md` (sección "Reparto online"): actualizar de "1 asesino, 1 médico, 1 comisario y el resto aldeanos" a incluir "**+ 1 mercenario desde 7 jugadores**".
- `docs/prueba-online-8-celulares.md` y `docs/prueba-online-8-celulares` (roles online): idem.

## A4. Criterios de aceptación (Parte A)
- Sala online de **7** (con instancias): reparto = 1 asesino + 1 mercenario + 1 médico + 1 detective + 3 aldeanos.
- El mercenario ve y puede escribir en el chat de traidores (junto con el asesino).
- El mercenario silencia a un jugador de noche; al amanecer, **todos** los celulares ven el cartel de silencio y el jugador queda silenciado (no puede escribir/votar ese día).
- La partida sigue su curso normal (noche → día → votación → victoria) sin `*_failure` en Logcat.
- Salas de 5-6 no cambian (siguen sin mercenario).

---

# PARTE B — Cierre de la barrida responsive (menos urgente)

> B1 (scroll del reveal de traidores) ya lo implementó Codex. Queda B2 y B3.

## B2. HUD del gameplay: auto-ajuste preventivo + banner central angosto
No hay bug reportado del HUD (el usuario solo notó cartas y carteles, ya resueltos), así que esto es **preventivo y de bajo riesgo**: aplicar el patrón responsive a los textos del HUD que todavía tengan tamaño fijo, sin re-maquetar nada.

1. **Banner central de eventos** (`activity_gameplay_mock.xml`, `centralPublicEventBanner` ~línea 80): tiene `layout_marginHorizontal="82dp"`. En un teléfono de 360dp eso deja solo ~196dp de ancho útil — sospechoso de quedar apretado. Bajar el margen a algo relativo/menor (p. ej. `48dp`) y verificar en preview que el texto del evento entre cómodo. Si sus textos internos no tienen `autoSize`, agregarlo.
2. **Textos del `bottomPlayerPanel`** (`gameplay_table_section.xml`): revisar que `currentPlayerName`, `currentPlayerStatus`, `roleName`, `currentPlayerHint` y los textos del `eliminatedStatePanel` (`textSize` 19sp/11.5sp fijos, ~607-629) tengan `autoSize` + `maxLines`. Agregar donde falte.
3. **`topStatus`** (título de fase, subtítulo, objetivo): confirmar `autoSize` en los textos que puedan variar de largo según la fase/idioma.
No convertir a `@dimen` ni ConstraintLayout; solo `autoSize` + `maxLines` sobre lo que hoy es texto fijo. Verificar cada cambio en el **preview de Android Studio** a 360dp y 411dp, con la escala de fuente del sistema en grande.

## B3. Validar la jaula del reveal de silencio (ya reducida ~15% a ciegas)
Las 5 piezas superpuestas del reveal de silencio (`silenceRevealCard`, `silenceRevealCageLeft/Right/Door/Lock`, ~499-559) se redujeron proporcionalmente sin preview. Verificar —en el preview con `tools:visibility="visible"` o forzando un silencio del Mercenario en local— que sigan alineadas: puerta centrada sobre la carta, candado centrado sobre la puerta, rejas a los lados. Si alguna quedó corrida, recalibrar manteniendo las proporciones relativas. (Esto se vuelve fácil de probar en cuanto la Parte A meta al mercenario en el online.)

## B4. Criterios de aceptación (Parte B)
- El banner central de eventos entra cómodo en un teléfono de 360dp.
- Ningún texto del HUD se corta con nombres largos o con la fuente del sistema en grande.
- El reveal de silencio se ve con las piezas de la jaula alineadas.

---

# Cómo probar (general)
- Parte A: sala online de 7 con instancias (BlueStacks multi-instancia + emuladores); el mercenario silencia y se verifica en todos los clientes.
- Parte B: preview de Android Studio (360dp y 411dp, fuente grande) + un chequeo en el A56 real.
