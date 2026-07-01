# Spec — Correcciones: botón "Revelarme" del Alcalde queda activo a la vez que "Votar"

> Handoff Claude (diseño/review) → Codex (implementación). Continuación de `revelar-vs-votar-y-ritmo-noche.md`, ya implementado. Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.

**Contexto:** se hizo un code review completo (8 ángulos, verificación directa en código) sobre la implementación del split "VOTAR" (principal) / "Revelarme y duplicar mi voto" (secundario) del Alcalde. La mayoría quedó bien — el botón está en ambos layouts, el click llega correctamente hasta `alcaldeRevealed = true`, el color `accent_red` se aplicó tal cual se pidió. Quedaron 2 correcciones puntuales y 2 opcionales de bajo riesgo.

---

## 1. 🔴 Prioridad — "Revelarme" sigue habilitado después de elegir a quién votar

### Diagnóstico confirmado

`canOfferMayorReveal()` (`GameplayMockActivity.kt:1081-1091`):

```kotlin
private fun canOfferMayorReveal(): Boolean {
    val human = GameEngine.humanPlayer(session)
    return human.alive &&
        human.role?.key == RoleCatalog.ALCALDE &&
        !session.alcaldeRevealed &&
        (
            session.phase == GamePhase.DIA_DEBATE ||
                session.phase == GamePhase.VOTACION ||
                session.phase == GamePhase.ALCALDE_DESEMPATE
            )
}
```

Esta función controla la visibilidad/habilitación del botón secundario (`renderMayorRevealSecondaryButton()`), pero **no considera si ya hay un objetivo de voto seleccionado** (`selectedTarget`, campo de clase en `GameplayMockActivity.kt:62`). Durante `VOTACION`, cuando el Alcalde toca la carta de un sospechoso, el botón principal cambia a `"VOTAR"` (vía `selectedAction != null -> selectedAction`), pero el botón secundario "Revelarme" sigue visible y habilitado al mismo tiempo — sin ningún indicio de que tocarlo ahora expondría el rol en vez de solo confirmar el voto elegido.

### Fix

Agregar `selectedTarget.isBlank()` a la condición de `canOfferMayorReveal()`:

```kotlin
private fun canOfferMayorReveal(): Boolean {
    val human = GameEngine.humanPlayer(session)
    return human.alive &&
        human.role?.key == RoleCatalog.ALCALDE &&
        !session.alcaldeRevealed &&
        selectedTarget.isBlank() &&
        (
            session.phase == GamePhase.DIA_DEBATE ||
                session.phase == GamePhase.VOTACION ||
                session.phase == GamePhase.ALCALDE_DESEMPATE
            )
}
```

Con esto, apenas el Alcalde selecciona un objetivo para votar, el botón "Revelarme" desaparece automáticamente (ya que `renderMayorRevealSecondaryButton()` ya llama a `canOfferMayorReveal()` para decidir su visibilidad) — para volver a ofrecer la opción de revelarse, alcanza con que el jugador deseleccione el objetivo (lo cual ya limpia `selectedTarget` por el camino existente de `clearSelection()`). No hace falta tocar ninguna otra función ni agregar estado nuevo.

---

## 2. 🟡 Consolidar el chequeo de "¿puede revelarse?" (opcional, bajo riesgo)

### Diagnóstico confirmado

Existe una segunda condición independiente, calcada a mano, adentro del overlay de desempate de votación (`GameplayMockActivity.kt:4904-4906`):

```kotlin
val hiddenHumanMayor =
    human.alive && human.role?.key == "alcalde" && !session.alcaldeRevealed
btnTieRevealMayor.visibility = if (hiddenHumanMayor) View.VISIBLE else View.GONE
```

Esto controla un botón distinto (`btnTieRevealMayor`, en el panel de desempate) que cubre la fase `DESEMPATE_VOTACION` — la cual `canOfferMayorReveal()` deliberadamente **no** incluye en su lista de fases, porque durante el desempate la pantalla principal se reemplaza por el overlay de votación de desempate, así que el botón secundario del panel principal no tiene sentido ahí. Hoy **no hay ningún hueco de juego** (verificado: sí se puede revelar durante el desempate, vía este otro botón), pero son dos copias independientes de la misma condición base ("¿el humano es Alcalde vivo y sin revelar?") — si una se edita a futuro (por ejemplo, para excluir el modo online), la otra puede quedar desactualizada sin que se note.

### Fix sugerido
Extraer la condición base compartida a una función chica, y que ambos sitios la usen con su propio filtro de fase encima:

```kotlin
private fun isUnrevealedHumanMayor(): Boolean {
    val human = GameEngine.humanPlayer(session)
    return human.alive && human.role?.key == RoleCatalog.ALCALDE && !session.alcaldeRevealed
}

private fun canOfferMayorReveal(): Boolean {
    return isUnrevealedHumanMayor() &&
        selectedTarget.isBlank() &&
        (
            session.phase == GamePhase.DIA_DEBATE ||
                session.phase == GamePhase.VOTACION ||
                session.phase == GamePhase.ALCALDE_DESEMPATE
            )
}
```

Y en el overlay de desempate (línea ~4904), reemplazar el cálculo inline por `val hiddenHumanMayor = isUnrevealedHumanMayor()`. Es un refactor chico, sin cambio de comportamiento.

---

## 3. 🟡 Confirmar visualmente — botón principal apagado durante el debate (no requiere código, solo revisión)

Durante `DIA_DEBATE`, con el Alcalde sin revelar, el botón principal ahora muestra `"ESPERAR"` deshabilitado, y la única acción disponible es el botón secundario más chico. Antes, `"REVELARME"` era la etiqueta prominente del botón principal en esa misma fase. **No es necesariamente un bug** — es el diseño que se pidió (acción segura por defecto, revelar como opción secundaria) — pero antes de darlo por cerrado, confirmar en Android Studio que el botón secundario se note lo suficiente como para no parecer que el juego "se trabó" esperando algo. Si no se nota bien, la solución sería puramente visual (tamaño/contraste del botón secundario), no de lógica.

---

## 4. 🟢 Opcional, bajo riesgo — limpieza menor (no bloqueante)

- `GameplayMockActivity.kt:2813` — el estilo del botón secundario (`GradientDrawable` con relleno + borde `accent_red` + radio de esquina) se arma a mano, casi idéntico al que ya arma el botón principal unas líneas antes. Se podría extraer un helper chico compartido (`stylePillButton(view, fillColor, strokeColor)` o similar), pero no es urgente.
- `activity_gameplay_mock.xml` — el nuevo ícono del sol sumó 4 dimensiones fijas más al archivo que `CLAUDE.md` ya marca como sobrecargado (de 92 a 96). No rompe nada; si en algún momento se retoma ese archivo para reducir dimensiones fijas, tenerlo en cuenta.

---

## Resumen de archivos a tocar

- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — `canOfferMayorReveal()` (fix 1, obligatorio), `isUnrevealedHumanMayor()` + refactor del overlay de desempate (fix 2, opcional).

## Orden sugerido
1. **Fix 1 primero** — es el único con impacto real en el juego.
2. Fix 2 si hay tiempo — reduce riesgo de una futura desincronización, pero hoy no rompe nada.
3. Punto 3 — solo mirar en pantalla, no es un cambio de código garantizado.
4. Punto 4 — dejarlo para cuando se retome esa parte del código, no es prioritario ahora.
