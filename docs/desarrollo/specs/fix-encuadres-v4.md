# Spec — Fix encuadres v4 (definitiva): overlays compactos, ordenados y consistentes

> Handoff Claude → Codex. Código = fuente de verdad. Diff acotado a esta spec; no mezclar cambios no pedidos.
> El usuario valida en Android Studio/BlueStacks. **Supersede** las partes visuales pendientes de specs anteriores (v2/v3) sobre estos overlays.

## ⚠️ Ya hecho por Claude (NO tocar)
Ya reescribí los marcadores 9-patch de los 3 marcos de evento:
- `app/src/main/res/drawable-nodpi/ui_frame_event_grecia.9.png`
- `app/src/main/res/drawable-nodpi/ui_frame_event_medieval.9.png`
- `app/src/main/res/drawable-nodpi/ui_frame_event_pampa.9.png`

**Causa raíz del espacio vacío:** la región estirable era solo el 18% central (px 419–604), así que el centro oscuro no podía encogerse y el panel quedaba gigante. Ahora la banda estirable es 200–824 (caja de contenido 170–855 intacta) → el marco **abraza el contenido**. **Codex no debe regenerar ni modificar estos PNG.**

Con los marcos arreglados, el resto es Kotlin/XML. Tareas:

---

## T1 — Chat (cronista): etiquetas legibles, no códigos
**Archivo:** `app/src/main/java/com/traidores/juego/GameplayChatController.kt`
Hoy el banner del feed muestra `"* SUCESO"`, `"N SUCESO"`, `"X SUCESO"` (la letra del `icon`). Cambiar a nombre temático.

### 1a. Agregar `label` a `EventPresentation` (data class ~656)
```kotlin
private data class EventPresentation(
    val icon: String,
    val label: String,      // ← nuevo
    val backgroundColor: Int,
    val strokeColor: Int,
    val iconColor: Int
)
```
### 1b. En `eventPresentationFor()` (~663) asignar `label` por tipo
| Condición | label |
|---|---|
| "murio"/"asesin" | `"MUERTE"` |
| "expuls"/"votacion"/"voto" | `"EXPULSIÓN"` |
| "noche" | `"NOCHE"` |
| "amanec"/"dia" | `"AMANECER"` |
| "silenci"/"mudo" | `"SILENCIO"` |
| "empate" | `"EMPATE"` |
| else | `"SUCESO"` |

### 1c. En el banner (~821)
```kotlin
text = event.label    // antes: "${event.icon} SUCESO"
```

---

## T2 — `applyRevealOverlayTheme()`: padding correcto + incluir el SILENCIO
**Archivo:** `GameplayMockActivity.kt` (~2691)

Hoy el método pone padding manual que **pisa la caja de contenido del 9-patch** y descoloca el texto respecto del marco. Ahora que el marco abraza el contenido, hay que **dejar que la caja de contenido del 9-patch posicione el texto** (no forzar padding chico) y **sumar el overlay de silencio** (hoy no recibe marco → captura 5).

Reemplazar el cuerpo por:
```kotlin
private fun applyRevealOverlayTheme() {
    val frame = revealPanelBackgroundForMap(session.mapKey)
    // Quitar padding explícito: el 9-patch (caja 170–855) ya inseta el contenido al centro oscuro.
    deathRevealContent.setBackgroundResource(frame)
    deathRevealContent.setPadding(0, 0, 0, 0)
    silenceRevealContent.setBackgroundResource(frame)   // ← ahora el silencio también lleva marco del mapa
    silenceRevealContent.setPadding(0, 0, 0, 0)
    voteResultPanel.setBackgroundResource(frame)
    voteResultPanel.setPadding(0, 0, 0, 0)
    privateFeedbackPanel.setBackgroundResource(frame)
    privateFeedbackPanel.setPadding(0, 0, 0, 0)
}
```
- Hay que tener `silenceRevealContent` como campo (lateinit + `findViewById(R.id.silenceRevealContent)`). Si no existe aún, agregarlo.
- **Importante:** también quitar el `android:padding*` del XML de estos contenedores (T3/T6) para que NO pisen la caja del 9-patch. El inset del marco lo da el propio 9-patch.
- `compactRevealPanelBackgroundForMap` puede eliminarse (ya no hace falta una variante "compacta": el marco se encoge solo). Si se elimina, actualizar sus usos para usar `revealPanelBackgroundForMap`.

> Nota: si tras la prueba el texto queda muy pegado al borde interno en algún mapa, agregar un padding chico **uniforme** (ej. `dp(6)` en los 4 lados) — pero NO valores grandes ni asimétricos, eso reintroduce el desfasaje.

---

## T3 — `privateFeedbackPanel`: ventana compacta, centrada, sin invadir laterales
**Archivo:** `activity_gameplay_mock.xml` (~206) + `GameplayMockActivity.kt`

El usuario quiere esta ventana **chica, solo en el centro**, sin tocar columnas de cartas ni bordes.

### 3a. Layout
```xml
<FrameLayout
    android:id="@+id/privateFeedbackPanel"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="center"
    android:layout_marginHorizontal="64dp"
    android:elevation="14dp">
    <!-- el background lo pone applyRevealOverlayTheme(); quitar android:background y android:padding* del XML -->
```
- `match_parent` + `marginHorizontal="64dp"` confina la ventana a la franja central (las columnas de cartas son ~62dp). Ya no invade laterales.
- Quitar `android:background` y `android:padding*` del FrameLayout (los maneja el 9-patch).
- El `LinearLayout` interno: quitar su `paddingHorizontal/paddingVertical` o dejarlos chicos (ej. 4dp); el inset real lo da el marco.

### 3b. Ocultar la barra de tono (choca con el marco)
En el XML del `privateFeedbackTone` agregar `android:visibility="gone"`. Si en código hay algo que lo vuelve visible (`privateFeedbackTone.setBackgroundColor(...)` ~4379), quitarlo o dejar el View oculto.

### 3c. Título con autosize (la ventana es más angosta)
```xml
android:textSize="17sp"
app:autoSizeMaxTextSize="17sp"
app:autoSizeMinTextSize="12sp"
app:autoSizeTextType="uniform"
```

---

## T4 — Matar el texto fantasma detrás del overlay (captura 3)
El banner central ("Dios preparó… ocultos") se ve a través del scrim. Existe `hideCentralPublicEventBanner(immediate = true)`.
- Llamarlo **al mostrar** cada overlay dramático: en `showPrivateFeedback(...)` (~4370), y en los show de death reveal / vote result / silencio (donde se hace `overlay.visibility = View.VISIBLE`).
- Punto único recomendado: justo antes de poner cada overlay `VISIBLE`, `hideCentralPublicEventBanner(immediate = true)`.

---

## T5 — RECUENTO DE VOTOS: cuadrícula compacta (varios votados entran todos)
**Archivo:** `VoteResultAnimator.kt` + `activity_gameplay_mock.xml`

Hoy las cartas (`dp(136)×dp(168)`) van en un `HorizontalScrollView` → con 4+ candidatos se van de pantalla (captura 4). Pasar a **cuadrícula** que acomoda todos dentro del marco.

### 5a. XML — reemplazar el scroll horizontal por una grilla
Cambiar `voteResultScroll` (HorizontalScrollView) + `voteResultCards` (LinearLayout horizontal) por un `GridLayout` (o un `ScrollView` vertical conteniendo el `GridLayout` por si hay muchísimos):
```xml
<GridLayout
    android:id="@+id/voteResultCards"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="center"
    android:useDefaultMargins="false" />
```
(Si se mantiene un contenedor scroll, que sea vertical y con `fillViewport`.)

### 5b. Código — columnas y tamaño según cantidad
En `show(session)` (~68) donde se agregan las cartas (~113-122):
- Calcular `val cols = candidateNames.size.coerceAtMost(3)` y `voteResultCards.columnCount = cols`.
- Tamaño de carta según cantidad (para que entren en el ancho del centro del marco):
  - `≤3` candidatos: `dp(112) × dp(150)`
  - `4-6`: `dp(96) × dp(126)`
  - `7+`: `dp(84) × dp(112)`
- Agregar cada carta con `GridLayout.LayoutParams` (margen `dp(6)`), dejando que GridLayout las acomode en filas (row-major automático con `columnCount`).
- Centrar la grilla (`layout_gravity="center"`, y el panel con gravity center).
- **Mantener** la animación de fichas de voto por carta (tokens) y el camino de la expulsión (`kickExpulsionCard`). La expulsión (1 sola carta) puede seguir como está; la grilla aplica al recuento multi-candidato.
- Quitar/!usar el alto fijo `RECOUNT_SCROLL_HEIGHT_DP` que forzaba 170dp (causaba hueco). El contenido define el alto y el marco lo abraza.

### 5c. Ancho del panel responsivo (portrait)
En `applyPanelMode` (~287): hoy fuerza `RECOUNT_PANEL_WIDTH_DP=500` / `EXPULSION=520`. En portrait angosto se desborda. Cambiar a ancho responsivo: `match_parent` con `marginHorizontal` (ej. 18dp) y un tope máximo, en vez de 500/520dp fijos. El marco es ~cuadrado; con la grilla centrada queda ordenado.

---

## T6 — Centrado vertical del contenido en todos los overlays
- `voteResultPanel`: `android:gravity="center"` (hoy `center_horizontal`) para que el contenido quede centrado en el marco (con el marco abrazando, no debe quedar pegado arriba).
- Verificar que `deathRevealContent`, `silenceRevealContent`, `privateFeedbackPanel` tengan el contenido con `gravity="center"` y `layout_gravity="center"`.

---

## Verificación (los 3 mapas: pampa, grecia, medieval)
- [ ] Chat: el feed dice "NOCHE", "MUERTE", "AMANECER"… (no "N SUCESO").
- [ ] RECUENTO: sin hueco vacío; cartas centradas; con muchos votados entran todos en cuadrícula.
- [ ] EXPULSIÓN: marco abraza el contenido, centrado, sin hueco.
- [ ] VÍCTIMA/INFO PRIVADA: ventana chica, solo en el centro (no toca laterales), con marco del mapa, sin barra de tono, sin texto fantasma detrás.
- [ ] SILENCIO: ahora con el marco del mapa (igual que la muerte).
- [ ] Texto siempre dentro del centro oscuro, legible, sin superponerse a columnas/laureles.

## Archivos a tocar (Codex)
1. `GameplayChatController.kt` — T1
2. `GameplayMockActivity.kt` — T2, T3b, T4, T6
3. `activity_gameplay_mock.xml` — T3a/3c, T5a, T6
4. `VoteResultAnimator.kt` — T5b/5c
> Los `.9.png` de los marcos: **ya corregidos por Claude, no tocar.**

## Doc a actualizar al cerrar (Claude)
- `docs/general/05-estructura-proyecto.md` (marcos 9-patch por mapa).
- `docs/general/07-flujo-funcionamiento.md` (overlays unificados: muerte/expulsión/silencio/info con marco temático).
- `docs/desarrollo/decisiones-arquitectura.md` (ADR: 9-patch stretch fix; overlays consistentes).
