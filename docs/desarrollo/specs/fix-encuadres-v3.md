# Spec — Fix encuadres v3: etiquetas del cronista + paneles centrados

> Handoff Claude (diagnóstico) → Codex. Código = fuente de verdad.
> Diff acotado a esta spec. No mezclar cambios no pedidos.
> Resultado esperado: etiquetas del feed legibles, ventana de acción compacta y centrada, voto encuadrado con texto legible.

---

## Contexto visual

**Screenshot 1 (chat El Cronista, mapa Pampa):**
Los banners del feed muestran "* SUCESO", "N SUCESO", "X SUCESO" — letras de código internas, no texto temático.

**Screenshot 3 (RECUENTO DE VOTOS, mapa Grecia):**
El frame griego está aplicado, se ve lindo. Pero el subtítulo "Cada sella muestra quien emitió el voto." aparece en blanco sobre las columnas de mármol en vez de estar dentro del centro oscuro. El `paddingHorizontal=22dp` no es suficiente para el frame con columnas gruesas.

**Screenshot 4 (INFORMACION PRIVADA, mapa Grecia):**
El `privateFeedbackPanel` invade las columnas laterales de cartas. La raya roja (`privateFeedbackTone`) choca visualmente con el frame de mármol. El usuario quiere "solo el centro de la pantalla".

---

## TAREA 1 — Etiquetas del feed: de letras de código a nombres temáticos

**Archivo:** `app/src/main/java/com/traidores/juego/GameplayChatController.kt`

### 1a. Agregar campo `label` a `EventPresentation`

```kotlin
// ANTES:
private data class EventPresentation(
    val icon: String,
    val backgroundColor: Int,
    val strokeColor: Int,
    val iconColor: Int
)

// DESPUÉS — agregar label:
private data class EventPresentation(
    val icon: String,
    val label: String,
    val backgroundColor: Int,
    val strokeColor: Int,
    val iconColor: Int
)
```

### 1b. Asignar `label` en `eventPresentationFor()`

Reemplazar cada `EventPresentation(...)` con `label` correspondiente:

| Condición | label |
|---|---|
| "murio" o "asesin" en texto | `"MUERTE"` |
| "expuls", "votacion" o "voto" | `"EXPULSIÓN"` |
| "noche" | `"NOCHE"` |
| "amanec" o "dia" | `"AMANECER"` |
| "silenci" o "mudo" | `"SILENCIO"` |
| "empate" | `"EMPATE"` |
| else (default) | `"SUCESO"` |

Ejemplo para MUERTE:
```kotlin
"murio" in lower || "asesin" in lower -> EventPresentation(
    icon = "X",
    label = "MUERTE",
    backgroundColor = Color.parseColor("#7A2A22"),
    strokeColor = Color.parseColor("#B46A72"),
    iconColor = Color.parseColor("#F0B2A8")
)
```
Aplicar el mismo patrón a todos los casos.

### 1c. Usar `event.label` en el banner (línea ~821)

```kotlin
// ANTES:
text = "${event.icon} SUCESO"

// DESPUÉS:
text = event.label
```

El campo `icon` puede conservarse si se usa en otro lado; de lo contrario, puede quedar como interno sin display.

---

## TAREA 2 — privateFeedbackPanel: ancho centrado + quitar raya conflictiva

### 2a. Layout XML — ancho por márgenes, no fijo

**Archivo:** `app/src/main/res/layout/activity_gameplay_mock.xml`

El panel con ancho fijo (300-320dp) invade las columnas de cartas laterales (~62dp cada una).
Usar `match_parent` con `layout_marginHorizontal` para confinar al centro:

```xml
<FrameLayout
    android:id="@+id/privateFeedbackPanel"
    android:layout_width="match_parent"
    android:layout_height="180dp"
    android:layout_gravity="center"
    android:layout_marginHorizontal="72dp"
    android:background="@drawable/bg_translucent_game_panel"
    android:elevation="14dp">
```

El margen de 72dp ≈ ancho de la columna lateral de cartas (~62dp) + buffer. El panel queda exactamente en la franja central del gameplay en cualquier teléfono.

### 2b. Quitar `privateFeedbackTone`

La raya coloreada a la izquierda (privateFeedbackTone) choca visualmente con el frame de mármol/madera. El frame ya provee identidad visual. Ocultarla:

**En el XML**, agregar `android:visibility="gone"` al View privateFeedbackTone:
```xml
<View
    android:id="@+id/privateFeedbackTone"
    android:layout_width="5dp"
    android:layout_height="match_parent"
    android:layout_gravity="start"
    android:background="@color/accent_gold"
    android:visibility="gone" />
```

**En código** (`GameplayMockActivity.kt`), si hay alguna línea que lo hace visible (`setBackgroundColor` / `visibility = VISIBLE`), comentarla o eliminarla.

### 2c. Ajustar contenido interno (LinearLayout)

Con el panel usando el frame de evento como background (9-patch), las columnas decorativas del frame consumen ~40dp en cada lado. El padding del LinearLayout interno debe superar ese valor:

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:paddingHorizontal="16dp"
    android:paddingVertical="10dp">
```

Y en código, después de aplicar el background:
```kotlin
privateFeedbackPanel.setBackgroundResource(compactRevealPanelBackgroundForMap(session.mapKey))
// Forzar padding para que el contenido quede dentro del centro oscuro del frame
val ph = (resources.displayMetrics.density * 16).toInt()
val pv = (resources.displayMetrics.density * 10).toInt()
privateFeedbackPanel.setPadding(ph, pv, ph, pv)
```

### 2d. Reducir textSize del título para que entre en el panel más angosto

```xml
<TextView
    android:id="@+id/privateFeedbackTitle"
    ...
    android:textSize="17sp"
    android:maxLines="1"
    app:autoSizeMaxTextSize="17sp"
    app:autoSizeMinTextSize="12sp" />
```

---

## TAREA 3 — voteResultPanel: padding suficiente para texto dentro del frame

**Archivo:** `app/src/main/res/layout/activity_gameplay_mock.xml`

El panel tiene `paddingHorizontal="22dp"` pero el frame griego tiene columnas de ~45dp. El subtítulo aparece sobre las columnas.

### 3a. Aumentar padding horizontal del panel

```xml
<LinearLayout
    android:id="@+id/voteResultPanel"
    android:layout_width="500dp"
    android:layout_height="wrap_content"
    android:layout_gravity="center"
    android:layout_marginHorizontal="32dp"
    android:background="@drawable/bg_tie_vote_panel"
    android:gravity="center_horizontal"
    android:orientation="vertical"
    android:paddingHorizontal="48dp"
    android:paddingTop="18dp"
    android:paddingBottom="14dp">
```

### 3b. En código, forzar el padding después de aplicar el frame

En `GameplayMockActivity.kt`, donde se aplica `dramaticBackground` a `voteResultPanel`:
```kotlin
voteResultPanel.setBackgroundResource(dramaticBackground)
val ph = (resources.displayMetrics.density * 48).toInt()
val pvTop = (resources.displayMetrics.density * 18).toInt()
val pvBot = (resources.displayMetrics.density * 14).toInt()
voteResultPanel.setPadding(ph, pvTop, ph, pvBot)
```

Esto garantiza que el "RECUENTO DE VOTOS" y el subtítulo queden DENTRO del centro oscuro del frame, sin superponerse con las columnas decorativas.

---

## Verificación esperada

| Elemento | Antes | Después |
|---|---|---|
| Feed cronista — tipo de evento | "N SUCESO", "X SUCESO", "* SUCESO" | "NOCHE", "MUERTE", "AMANECER", "SUCESO"... |
| privateFeedbackPanel — ancho | Invade columnas de cartas | Confinado al centro de pantalla (márgenes 72dp) |
| privateFeedbackPanel — raya roja | Visible, choca con el frame | Oculta (gone) |
| RECUENTO DE VOTOS — subtítulo | Texto sobre columnas de mármol | Texto dentro del centro oscuro |

## Archivos a modificar

1. `app/src/main/java/com/traidores/juego/GameplayChatController.kt` — Tarea 1
2. `app/src/main/res/layout/activity_gameplay_mock.xml` — Tareas 2a, 2b, 2c, 3a
3. `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — Tareas 2c (padding en código) y 3b
