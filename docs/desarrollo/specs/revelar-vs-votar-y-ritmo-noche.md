# Spec — Ritmo del salto de noche + acción "revelarse" separada de votar (Alcalde y Payador)

> Handoff Claude (diseño/review) → Codex (implementación). Continuación de la serie `saltar-noche-y-reveal-sin-muertes*.md`. Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.

**Contexto:** el usuario jugó una partida de Payador y volvió a sentir que "se pierde la noche" — no es un bug nuevo, es que el arreglo anterior (botón de salto que se arma solo tras un delay) usa un delay demasiado corto para un rol sin ninguna acción nocturna, como el Payador (su Contrapunto es 100% diurno). Además, planteó que roles con una acción "de revelarse" (Alcalde, y a futuro el Payador) no tienen forma clara de actuar sin exponerse. Se investigó el código a fondo antes de escribir esta spec — hay hallazgos concretos, no solo hipótesis.

---

## 1. Alargar el margen antes de habilitar "SALTAR NOCHE"

### Diagnóstico confirmado
`GameplayMockActivity.kt:5755`: `NIGHT_SKIP_ARM_DELAY_MS = 650L`. El Payador, igual que el Aldeano, no tiene ninguna acción en las 5 sub-fases nocturnas (`NOCHE_ASESINO/MERCENARIO/POLICIA/MEDICO/ORACULO`) — su Contrapunto es exclusivamente de `DIA_DEBATE`/`CONTRAPUNTO` (confirmado en `GameEngine.kt`, no hay ningún `NOCHE_*` que lo involucre). Esto significa que `canSkipRemainingNight()` es `true` desde el primer instante de la noche, y con solo 650ms de espera, el botón "SALTAR NOCHE" queda disponible casi al mismo tiempo que arranca la fase — sin margen real para percibir que la noche empezó.

### Fix
Cambiar `NIGHT_SKIP_ARM_DELAY_MS` de `650L` a **`3500L`** (3.5 segundos), para que coincida aproximadamente con la duración del cartel "NOCHE N" que ya se muestra al iniciar la noche (`DayNightTransitionAnimator`, transición de ~4s) — así el jugador ve pasar un momento de atmósfera antes de poder saltar, sin hacerlo esperar de más si genuinamente no tiene nada que hacer.

No hace falta tocar ninguna otra lógica: `isNightSkipButtonReady()`/`canSkipRemainingNight()` ya están bien guardados (confirmado en la ronda de review anterior), es un cambio de una sola constante.

---

## 2. Separar "votar" de "revelarme" — Alcalde

### Diagnóstico confirmado (importante: esto NO es un bug de lógica)

Se rastreó `GameEngine.canActOnTarget()` (`GameEngine.kt:1039`): `GamePhase.VOTACION -> isValidVoteTarget(session, targetName, human)` — **no depende de `session.alcaldeRevealed`**. Un Alcalde sin revelar ya puede tocar la carta de un jugador durante `VOTACION` y votar normalmente sin revelarse; `targetActionLabel()` devuelve `"VOTAR"` para ese caso, y en `renderAdvanceButton()` (`GameplayMockActivity.kt:2706`) `selectedAction != null -> selectedAction` tiene prioridad sobre el label `"REVELARME"`.

**El problema es puramente de interfaz**: antes de tocar cualquier carta, el botón principal (`btnAction`, mapeado a la vista `btnVote`) siempre muestra `"REVELARME"` como acción por defecto durante `DIA_DEBATE`/`VOTACION`/`ALCALDE_DESEMPATE` mientras no esté revelado (`GameplayMockActivity.kt:2716`), sin ningún indicio de que también se puede votar en silencio tocando una carta directamente. El jugador nunca se entera de que la opción silenciosa ya existe.

### Fix — invertir la jerarquía visual, no la lógica

1. **Durante `VOTACION`/`ALCALDE_DESEMPATE`** (fases donde ya se puede votar sin revelarse): el botón principal (`btnVote`/`btnAction`) debería mostrar por defecto `"VOTAR"` en vez de `"REVELARME"` cuando no hay carta seleccionada — sigue funcionando como acción principal, dorado estándar (`@style/BtnDark`, igual que las demás acciones), y NO expone el rol.
2. **Agregar un botón/control secundario, más chico y con acento visual propio**, para la acción de revelarse a propósito ("Revelarme y duplicar mi voto"), visible solo cuando el Alcalde sigue sin revelar y está en una fase donde tiene sentido ofrecerlo (`DIA_DEBATE`/`VOTACION`/`ALCALDE_DESEMPATE`). Al tocarlo, dispara exactamente el mismo camino que hoy dispara "REVELARME" (`GameEngine.kt` — la resolución que marca `alcaldeRevealed = true`), sin cambiar esa lógica.
3. **Durante `DIA_DEBATE` específicamente**: el Alcalde no tiene ningún target que votar todavía (`canActOnTarget` no cubre Alcalde en `DIA_DEBATE`), así que ahí el botón secundario de "Revelarme" puede ser la única opción visible además de esperar — está bien, es coherente (revelarse temprano en el debate es una jugada social válida).

### Estilo visual — usar la paleta ya definida, no inventar colores
- Botón principal "VOTAR": mismo estilo que ya usan las demás acciones (`@style/BtnDark`, fondo `@drawable/bg_btn_dark`, texto `@color/text_primary`).
- Botón secundario "Revelarme": usar `@color/accent_red` (`#8F2633`, ya definido en `colors.xml` y ya usado en contexto de gameplay para acciones de riesgo/agresivas) como color de acento — por ejemplo, borde delgado en `accent_red` sobre fondo oscuro, texto en un tono derivado de ese rojo (no texto blanco puro sobre el borde, para mantener coherencia con el resto de la paleta dorada/marrón). Tamaño notablemente más chico que el botón principal, para que se lea como "opción secundaria, con costo" y no compita visualmente con "VOTAR".
- No usar `@color/accent_gold` para el botón de revelar — el dorado ya está asociado a "acción principal segura" en toda la UI; usar un color distinto (el rojo) es lo que comunica "esto tiene un costo" sin necesidad de texto adicional.

### Dónde meterlo en el layout (atención, hay una restricción real)
`actionControls` (`gameplay_table_section.xml:467-508`) es un `LinearLayout` horizontal con `weightSum="5"` ya completamente ocupado por `btnRevealCard` (peso 2, "REVELAR" — esto es otro botón, no relacionado: alterna si tu propia carta está visible en tu panel de jugador) y `btnVote`/`btnAction` (peso 3). No hay espacio libre en esa fila sin reestructurar. Evaluar:
- Agregar una fila nueva, chica, encima o debajo de `actionControls`, con el botón secundario — visible solo cuando corresponde (`visibility = GONE` el resto del tiempo), para no ocupar espacio permanentemente.
- Ojo con el archivo `activity_gameplay_mock.xml`/`gameplay_table_section.xml`: ya se marcó en la ronda anterior que `activity_gameplay_mock.xml` tiene demasiadas dimensiones fijas (regla explícita en `CLAUDE.md:166`); si el nuevo botón va en `gameplay_table_section.xml` es otro archivo y no aplica esa regla puntual, pero igual conviene usar `wrap_content`/pesos relativos donde se pueda, no un nuevo alto fijo más.

---

## 3. El mismo patrón para el Payador — y por qué no es exactamente igual

### Diagnóstico confirmado
`GameEngine.resolveContrapunto()` (`GameEngine.kt:535-552`) genera el mensaje público **incondicionalmente**: `"El Contrapunto termino. El Payador senalo a $selected como mas sospechoso."`, propagado a todos vía `withPublicHistory(message)`. A diferencia del Alcalde, acá **no existe ningún camino silencioso** — usar el Contrapunto siempre delata el rol, sin excepción.

Aclaración importante: el botón principal del Payador **ya** por defecto muestra `"VOTAR SIN USAR"` durante `DIA_DEBATE` cuando no se seleccionó a nadie (`GameplayMockActivity.kt:2721-2723`) — o sea, el patrón de "acción principal = votar normal, sin exponerte" **ya existe en la interfaz** para el Payador. El problema real no es de interfaz sino del **mensaje que genera el motor** cuando se usa el poder: nombra al rol explícitamente, sin alternativa.

### Fix — decisión de diseño ya tomada: el Payador puede usar su poder sin ser nombrado
Cambiar el mensaje público en `resolveContrapunto()` para que **no mencione la palabra "Payador"**, mantiene el efecto del voto extra (`contrapuntoSuspicion`) intacto. Por ejemplo, reemplazar:

```kotlin
val message = "El Contrapunto termino. El Payador senalo a $selected como mas sospechoso."
```

por algo que comunique el resultado sin nombrar el rol responsable, por ejemplo:

```kotlin
val message = "El Contrapunto termino. $selected quedo senalado como mas sospechoso."
```

Esto preserva toda la lógica existente (`contrapuntoSuspicion`, el bonus de voto, `payadorUsed`) — es un cambio de texto, no de reglas. El botón "SEÑALAR"/"CONTRAPUNTO" para elegir a los 2 jugadores del debate puede seguir llamándose igual; lo único que cambia es que el resultado público ya no delata quién lo usó.

### Convención para roles futuros con mecánica similar
Para cualquier rol futuro que tenga una acción "poderosa pero identificable" (como Alcalde revelarse) versus una acción "segura pero anónima" (votar normal), el patrón a seguir es:

1. **La acción por defecto/principal siempre es la que no expone el rol** (votar normal, o lo que sea el equivalente "seguro" de ese rol) — nunca la acción de mayor riesgo/impacto por defecto.
2. **La acción que sí expone o cuesta algo va en un control secundario**, visualmente distinto (más chico, acento en `accent_red` en vez de `accent_gold`), nunca como la opción primaria.
3. **Decidir, caso por caso, si el mensaje público de esa acción debe nombrar el rol o no** — depende de si "revelar quién sos" es parte del efecto narrativo de la habilidad (Alcalde: sí, literalmente se llama "revelarse") o si es un efecto secundario evitable del texto (Payador: el Contrapunto no necesita nombrar al Payador para tener sentido narrativo, así que no debería hacerlo).

Esto no requiere una abstracción nueva en el motor — cada rol sigue teniendo su propia función de resolución (`resolveContrapunto`, la lógica de Alcalde, etc.); el patrón es una convención de diseño a seguir manualmente en cada una, no un framework a construir ahora.

---

## Resumen de archivos a tocar

- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — `NIGHT_SKIP_ARM_DELAY_MS` (línea 5755, sección 1); label por defecto de `btnVote` durante `VOTACION`/`ALCALDE_DESEMPATE` para Alcalde (sección 2); wiring del nuevo botón secundario de revelar.
- `app/src/main/res/layout/gameplay_table_section.xml` y `app/src/main/res/layout-land/gameplay_table_section.xml` — nuevo botón secundario "Revelarme" (recordar tocar **ambos** archivos, no solo uno — ya nos pasó antes que un cambio quedara solo en un layout y no en el otro).
- `app/src/main/java/com/traidores/juego/GameEngine.kt` — mensaje público de `resolveContrapunto()` (línea ~546, sección 3).

## Orden sugerido
1. Sección 1 (una constante, cero riesgo) — resuelve la queja más urgente de inmediato.
2. Sección 3 (una línea de texto en `GameEngine.kt`, cero riesgo) — resuelve el Payador.
3. Sección 2 (la más grande: nuevo botón, layout en 2 archivos, estilo) — la parte visual, con más superficie de cambio.
