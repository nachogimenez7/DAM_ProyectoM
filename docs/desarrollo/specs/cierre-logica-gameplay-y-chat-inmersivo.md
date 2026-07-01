# Spec — Cierre de lógica de gameplay (tests puntuales) + chat central más grande e inmersivo

> Handoff Claude (diseño/review) → Codex (implementación). Código = fuente de verdad. Codex arranca en frío: leer todo antes de tocar nada. Diff acotado por fase; el usuario valida en Android Studio (no se compila en este handoff).

**Contexto:** el usuario quiere dar por cerrada la lógica de gameplay antes de sumar roles nuevos, y admite no haber jugado mucho con los roles exclusivos por mapa (Payador/Pampa, Bufón/Medieval, Oráculo/Grecia). También quiere retomar el chat central: ya quedó bien tras el pase anterior, pero lo quiere más grande, con marco propio y más inmersivo (tanto la ventana expandida como el feed colapsado).

Son dos partes independientes — pueden implementarse y mergearse por separado. La Parte 1 es solo tests (motor de reglas, sin UI). La Parte 2 es solo layout/drawables (UI, sin motor de reglas).

---

## PARTE 1 — Tests puntuales para cerrar huecos de cobertura

### Diagnóstico general

El motor de reglas (`GameEngine.kt`, `GameModels.kt`) está en buen estado: 137 tests en `GameEngineTest.kt`, sin ningún `TODO`/`FIXME` pendiente en `app/src/main`. Los sistemas más complejos (empates de votación, corrupción del Alcalde) son los mejor probados de todo el código — no hace falta reescribir nada del motor.

Lo que falta no son bugs confirmados, sino **interacciones sin verificar**: casos donde dos mecánicas se cruzan (rol + votación, rol + condición de victoria) y cada una está probada por separado, pero nunca juntas. Se agregan como tests nuevos que no deberían requerir cambiar el motor — **si algún test revela que el comportamiento actual está mal, no corregirlo en silencio: reportarlo antes de tocar `GameEngine.kt`**, porque un cambio ahí es más riesgoso que uno de UI.

### Tests a agregar, en orden de prioridad

**🔴 Alto — Espía como único asesino, de punta a punta**
El test existente `onlySpyInheritsKillWhenAssassinIsDead` (`GameEngineTest.kt:778-797`) solo verifica `requiresHumanInput`/`targetActionLabel`; nunca llama a `resolveAssassin` con el Espía como único traidor vivo para confirmar que la muerte realmente se resuelve en `resolveDawn`. El Espía no tiene resolver propio — depende enteramente de `activeKillers()` (`GameEngine.kt:145-149`). Agregar un test que arme una partida con Asesino muerto y Espía vivo, ejecute la noche completa, y confirme que la víctima elegida por el Espía aparece muerta al amanecer.

**🟡 Medio — Payador: el bonus de +1 voto se aplica de verdad**
`payadorSuspicionAddsOneVote` (`GameEngineTest.kt:1123-1134`) solo confirma que se guarda `contrapuntoSuspicion` y que la fase pasa a `VOTACION`. Ningún test hace correr la votación completa (`resolveVoting`/`weightedVoteLeaders`, `GameEngine.kt:1777-1790`) para confirmar que el jugador señalado por el Payador termina con un voto extra en el conteo real. Agregar un test que arme el Contrapunto, señale a un jugador, y verifique el conteo final de votos.

**🟡 Medio — Alcalde muerto nunca debería llegar a ALCALDE_DESEMPATE**
Ya está anotado en `docs/desarrollo/backlog.md:45` como pendiente de verificar. Agregar un test que mate al Alcalde antes de un empate y confirme que la fase `ALCALDE_DESEMPATE` no se alcanza (debería caer en el camino de "empate sin Alcalde" que ya está probado en `repeatedTieWithoutMayorEndsTheDayWithoutExpulsion`).

**🟡 Medio — Oráculo: el muerto invocado no debe contar como vivo para ganar**
`docs/map-exclusive-roles.md` dice explícitamente que el jugador invocado por el Oráculo no debe "contar como vivo para las condiciones de victoria", pero ningún test corre `GameRules.winnerFor` con un invocado activo en el debate. Agregar un test que invoque a un muerto, deje vivo el balance justo de traidores/pueblo, y confirme que el ganador se calcula igual que si el invocado no estuviera "hablando".

**🔴 Alto — Empate total: todos muertos a la vez**
`GameModels.kt:343-355`: `winnerFor` devuelve "gana el Pueblo" si no queda ningún jugador vivo con rol asesino — por vacuidad lógica, si **no queda nadie vivo de ningún bando**, esa condición también se cumple y el motor declararía ganador al Pueblo en silencio, sin que haya sobrevivido nadie. Hoy es difícil de alcanzar (se elimina de a un jugador por vez), pero no hay ningún test ni guarda explícita. Agregar un test que fuerce ese estado (todos los jugadores `alive = false`) y decidir junto con el usuario si el resultado correcto es "empate/nadie gana" en vez del actual "gana Pueblo" — **esto sí puede requerir un cambio mínimo en `GameModels.kt` si el comportamiento actual se considera incorrecto; confirmarlo con el usuario antes de cambiarlo**.

**🟡 Medio — Desertor vivo con equipo sin elegir al momento de calcular el ganador**
`GameModels.kt:347-350`: si el Desertor sigue vivo y `desertorTeam` está en blanco cuando se evalúa `winnerFor`, no suma ni para Pueblo ni para Traidores. Hay tests de Desertor apoyando bando (`desertorSupportingTraitorsDoesNotAccelerateParityWin`, `GameEngineTest.kt:2597-2619`) pero ninguno con el equipo todavía sin elegir. Agregar un test que llegue a `winnerFor` con el Desertor vivo y sin equipo elegido, y confirmar que el resultado es el esperado (probablemente excluirlo del conteo de paridad es correcto — verificar con el diseño actual, no asumir).

**🟡 Medio — Alcalde revelado + bonus de Payador combinados en la misma votación**
Cada uno está probado por separado (`revealedAlcaldeVoteCountsDouble`, `payadorSuspicionAddsOneVote`) pero nunca juntos en la misma llamada a `weightedVoteLeaders` (`GameEngine.kt:1777-1790`). Agregar un test con ambos activos a la vez y confirmar que los pesos se combinan correctamente (doble voto del Alcalde + voto fantasma del Payador en el mismo tally).

**🟢 Bajo — Mercenario silencia al mismo jugador que el Asesino mata esa noche**
El orden de resolución en `resolveDawn` (`GameEngine.kt:354-401`) aplica la muerte antes de chequear el silencio contra `.alive`, así que por inspección el código luce correcto (un jugador matado-y-silenciado debería terminar muerto, no mudo). No hay test dedicado. Agregar uno que confirme explícitamente ese orden.

**🟢 Bajo — Oráculo: intentar usar el poder una segunda vez**
Los tests existentes verifican que `oracleUsed` se marca en `true` tras usarlo, pero ninguno intenta invocar una segunda vez en la misma partida para confirmar que se rechaza. Agregar ese caso.

**🟢 Bajo — Bufón expulsado por desempate del Alcalde (no por voto normal)**
Los tests de victoria especial de Bufón (`GameEngineTest.kt:715-776`) cubren voto normal y muerte nocturna, pero no el camino alternativo de expulsión vía `chooseAlcaldeTie`. Agregar ese caso para confirmar que la victoria especial también se dispara ahí.

**🟢 Bajo — Médico protegiéndose a sí mismo**
`allowSelf = true` está en el código (`GameEngine.kt:283`) pero no hay test explícito de auto-protección. Agregar uno simple.

**🟢 Bajo — Rechazo de auto-voto explícito en la votación principal (no en el desempate)**
El guard existe estructuralmente (`isValidVoteTarget`, `GameEngine.kt:1647-1651`) y hay cobertura indirecta en el desempate, pero ningún test nombra explícitamente "no podés votarte a vos mismo" en la fase de votación normal. Agregar un test directo, más por documentación que por riesgo real.

### Nota aparte — documentación

`docs/map-exclusive-roles.md` documenta el comportamiento esperado de Bufón y Oráculo en detalle, pero **no menciona a Payador en absoluto**, a pesar de que su lógica ya existe en `GameEngine.kt` (`resolveContrapunto`, ~línea 501-546). Sumar una sección para Payador a ese documento (qué pasa si solo hay 1 candidato válido para el Contrapunto, qué pasa si el Payador muere antes de usar su poder, etc.) — es barato y deja una referencia escrita contra la cual escribir/verificar tests futuros.

### Recomendación de playtesting manual (no es tarea de Codex, es para el usuario)

Dado que ya hay buena cobertura automática en Bufón y Oráculo, el playtest manual pendiente debería priorizar:
1. **Payador** — es el rol con el hueco de test más grande de los tres exclusivos (el bonus de voto).
2. **El camino de expulsión de Bufón vía desempate del Alcalde** — ninguna prueba automática lo toca todavía.

---

## PARTE 2 — Chat central más grande, con marco propio, feed colapsado incluido

### Estado actual (hechos, confirmados leyendo el código)

El chat tiene dos vistas distintas, ambas manejadas desde `GameplayChatController.kt`:

**Feed colapsado (`chatAmbientFeed`)** — `gameplay_table_section.xml:303-339`. Vista chica flotante arriba del panel inferior de jugadores, sin marco (fondo plano `bg_chat_ambient_feed`, el mismo para los 3 mapas), hasta 3 líneas de mensaje truncadas + "Toca para hablar".

**Ventana expandida (`chatPanel`)** — `gameplay_table_section.xml:582-744`. El tamaño real **se calcula por código, no por XML** (`GameplayChatController.applyChatPanelDimensions`, ~línea 454-500):
- Ancho: 46% del ancho de `centerColumn`, con tope entre `320dp` y `420dp`.
- Alto: **fijo en 300dp** — no proporcional a la pantalla disponible.
- Fondo: se reasigna en runtime por mapa (`renderChatBackgrounds()` → `bg_chat_box_grecia/medieval/pampa`), pero son formas planas (rectángulo redondeado + borde de 1-2dp), sin ilustración ornamental.

Los marcos ornamentales que ya usamos en los reveals (`ui_frame_event_grecia/medieval/pampa`) son PNGs 9-patch pesados (~1-1.2MB cada uno) con bordes decorativos gruesos (~50-60dp por lado) — pensados para paneles estáticos de contenido corto, no para un área con scroll + input de texto. Reusarlos tal cual comería demasiado espacio útil de mensajes.

**Decisión ya tomada con el usuario:** en vez de reusar esos marcos pesados, se construye un **marco nuevo, más fino, pensado específicamente para el chat** (bordes angostos, no los ~56dp de los reveals), y **se aplica tanto al feed colapsado como a la ventana expandida**.

### Qué construir

1. **Nuevo asset de marco fino** — recomendación: hacerlo como **`drawable` vectorial** (no PNG 9-patch pesado), en la misma línea que `bg_reveal_event_panel.xml` (el fallback liviano que ya existe para reveals sin marco de mapa específico): un borde dorado delgado (2-3dp), esquinas con un detalle ornamental simple (no fotorrealista), por mapa o con una única versión neutra si no se justifica variarla por mapa. Esto evita depender de arte nuevo generado fuera del repo (a diferencia del reveal de "nadie murió", que si necesitaba arte ilustrado) y Codex puede construirlo íntegramente como shape/vector drawable.
   - Si más adelante el usuario quiere el mismo nivel fotorrealista/grabado que los marcos de reveal, ahí sí haría falta encargar arte bitmap nueva — dejar esa puerta abierta pero no bloquear esta entrega por eso.
2. **Ventana expandida (`chatPanel`)** — en `GameplayChatController.kt`:
   - Subir el ancho: tope actual `420dp` → agrandar el rango (ej. hacia el ancho real de `centerColumn`, ya que el panel es un overlay centrado en `root`, no está limitado por sus vecinos).
   - Cambiar el alto de fijo `300dp` a un cálculo proporcional a `centerColumn.height`, siguiendo el mismo patrón que ya usa `applyChatSheetDimensionsPortrait` (ratio con clamp) en vez de un valor fijo.
   - Cambiar el fondo (`chatBoxBackgroundFor`, ~línea 1256-1268) para usar el nuevo marco fino en vez de `bg_chat_box_*`.
   - Reajustar el padding interior (`chatHeader`, `chatComposer`, `chatMessagesScroll`) proporcionalmente al nuevo tamaño, para que no quede con espacio vacío desbalanceado.
3. **Feed colapsado (`chatAmbientFeed`)** — aplicar el mismo marco (versión más chica/liviana) en vez de `bg_chat_ambient_feed`. No hace falta agrandarlo tanto como la ventana expandida (sigue siendo una vista previa, no el chat completo), pero si el marco nuevo necesita más aire interno, ajustar el padding/margen en `gameplay_table_section.xml:303-339` en consecuencia.
4. **No tapar `topStatus` ni `bottomPlayerPanel` de forma brusca** cuando el panel crece, y mantener siempre alcanzable el botón de cerrar (`btnCloseChat`).
5. **Cuidado con el peso del asset:** a diferencia de los marcos de reveal (se muestran una vez por evento), el marco del chat se ve constantemente y se anima al abrir/cerrar — si se termina optando por un PNG 9-patch en vez de vector, mantenerlo liviano.

### Constraints del ciclo

- Gameplay es landscape-only — el camino de `applyChatSheetDimensionsPortrait` (portrait) es código muerto en la práctica, no es necesario tocarlo.
- Mantener identidad medieval/dorada.
- Refactor chico y justificado — esto es tuning de constantes + un asset nuevo, no una reescritura de `GameplayChatController.kt`.
- No compilar — el usuario valida en Android Studio.

---

## Resumen de archivos a tocar/crear

**Parte 1 (tests):**
- `app/src/test/java/com/traidores/juego/GameEngineTest.kt` — ~11 tests nuevos (ver lista arriba).
- `app/src/main/java/com/traidores/juego/GameModels.kt` — **solo si** el test de empate total confirma que el resultado actual es incorrecto (confirmar con el usuario antes de tocar).
- `docs/map-exclusive-roles.md` — sección nueva para Payador.

**Parte 2 (chat):**
- `app/src/main/res/drawable/` — nuevo marco vectorial fino para el chat (feed colapsado + ventana expandida).
- `app/src/main/java/com/traidores/juego/GameplayChatController.kt` — constantes de tamaño (`applyChatPanelDimensions`), `chatBoxBackgroundFor`/`renderChatBackgrounds`, paddings interiores.
- `app/src/main/res/layout/gameplay_table_section.xml` — ajustes de margen/padding del feed colapsado si el marco nuevo lo requiere.
