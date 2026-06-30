# Spec — Legibilidad de títulos en overlays (death / silencio / recuento)

> Para Codex. **Primero sincronizá** con lo que Claude ya aplicó (ver `fix-encuadres-coordinacion.md`): marcos en `drawable-xxhdpi`, `applyRevealOverlayTheme` con padding explícito, ventana de víctima compacta, recuento en grilla de 2 columnas. Esta spec es un ajuste chico ENCIMA de eso. No rehagas lo anterior.
> Diff acotado. El usuario valida en Android Studio.

## Problema
Los marcos ya quedan bien, pero los **títulos** caen sobre el borde decorado del marco (zona clara/transparente), no sobre el centro oscuro difuminado → se leen mal. Afecta:
- Death reveal: "AL AMANECER..." (queda arriba, sobre el borde/mapa).
- Recuento: "RECUENTO DE VOTOS" + subtítulo "Cada sello muestra quien emitio el voto." (sobre el borde superior).
- (Por consistencia) Silencio: "UNA VOZ FUE SILENCIADA".

Objetivo: que esas palabras queden **sobre el centro oscuro** y con un **respaldo difuminado** detrás para máxima legibilidad.

---

## T1 — Nuevo drawable: respaldo difuminado para texto
Crear `app/src/main/res/drawable/bg_reveal_text_shade.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#A60B0B0E" />
    <corners android:radius="12dp" />
</shape>
```
(Negro/marrón muy oscuro al ~65% — actúa como "fondo difuminado" detrás del texto sin tapar el marco.)

---

## T2 — Death reveal: título sobre el centro oscuro
En `activity_gameplay_mock.xml`, el **primer TextView** de `deathRevealContent` (texto `"AL AMANECER..."`, ~líneas 315-324):
- Agregarle id `android:id="@+id/deathRevealHeadline"`.
- Agregar respaldo + padding:
```xml
android:background="@drawable/bg_reveal_text_shade"
android:paddingHorizontal="14dp"
android:paddingVertical="5dp"
```
- Que el `layout_width` sea `wrap_content` y `layout_gravity="center_horizontal"` (que el chip abrace el texto, centrado), no `match_parent`.

---

## T3 — Silencio: mismo tratamiento (consistencia)
El TextView `"UNA VOZ FUE SILENCIADA"` de `silenceRevealContent` (~líneas 459-465):
- id `android:id="@+id/silenceRevealHeadline"`, mismo `background` + paddings + `wrap_content` + `center_horizontal` que T2.

---

## T4 — Recuento: título y subtítulo sobre el centro oscuro
En `voteResultPanel`:
- `voteResultTitle` (id ya existe): agregar `background` + paddings de T2; cambiar `layout_width` a `wrap_content` y `layout_gravity="center_horizontal"` (chip centrado, no a lo ancho).
- `voteResultSubtitle` (id ya existe): mismo tratamiento (puede ir con su propio chip, justo debajo del título).
- Mantener el `autoSizeTextType` del título.

> Si visualmente prefieres un solo chip que envuelva título+subtítulo: envolver ambos en un `LinearLayout` vertical con el `background=bg_reveal_text_shade` y padding, en vez de un chip por TextView. Cualquiera de las dos sirve; elegí la que se vea más limpia.

---

## T5 — Bajar los títulos al centro oscuro (padding superior)
En `GameplayMockActivity.applyRevealOverlayTheme()` (lo aplicó Claude), los paneles grandes tienen hoy:
```kotlin
deathRevealContent.setPadding(dp(50), dp(48), dp(50), dp(48))
silenceRevealContent.setPadding(dp(50), dp(48), dp(50), dp(48))
voteResultPanel.setPadding(dp(46), dp(46), dp(46), dp(42))
```
El padding superior (~48/46dp) deja el título sobre el borde. Subir el **top** para que el primer texto caiga dentro del centro oscuro:
```kotlin
deathRevealContent.setPadding(dp(50), dp(60), dp(50), dp(48))
silenceRevealContent.setPadding(dp(50), dp(60), dp(50), dp(48))
voteResultPanel.setPadding(dp(46), dp(60), dp(46), dp(42))
```
(Valores tentativos; el centro oscuro arranca ~57dp del borde. Ajustar ±6dp si hace falta tras probar.)

---

## Verificación (3 mapas)
- [ ] "AL AMANECER...", "RECUENTO DE VOTOS"+subtítulo y "UNA VOZ FUE SILENCIADA" quedan **sobre el centro oscuro**, con su respaldo difuminado, perfectamente legibles.
- [ ] Los chips de texto quedan **centrados** y abrazan el texto (no ocupan todo el ancho).
- [ ] No se superponen con las columnas/laureles del marco.
- [ ] El resto (cartas, botón CONTINUAR) sigue igual y centrado.

## Archivos a tocar
1. `app/src/main/res/drawable/bg_reveal_text_shade.xml` (nuevo) — T1
2. `app/src/main/res/layout/activity_gameplay_mock.xml` — T2, T3, T4
3. `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — T5
