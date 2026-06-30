# Coordinación encuadres — Claude ya aplicó cambios (NO reimplementar)

> Para Codex. Claude aplicó estos cambios **directamente** en el repo (el usuario lo pidió para cortar el ida y vuelta). **Sincronizá/pulleá y NO reimplementes** estos archivos: si los volvés a tocar con tu propia versión vamos a duplicar/romper. Tu análisis fue correcto; abajo está cómo quedó y cuál es el paso siguiente acordado si hace falta.

## Ya hecho por Claude (no tocar)

**Causa raíz confirmada (coincide con tu análisis):** el problema no era solo el padding, sino el **tamaño mínimo/intrínseco del 9-patch usado como background de un panel `wrap_content`**. Los `ui_frame_event_*.9.png` son 1026×1026 y estaban en `drawable-nodpi` → no escalaban → mínimo intrínseco enorme y borde grueso → centro angosto y paneles inflados.

Cambios aplicados:
1. **Marcos movidos `drawable-nodpi` → `drawable-xxhdpi`** (`ui_frame_event_{grecia,medieval,pampa}.9.png`). Ahora escalan con densidad: mínimo intrínseco ~133dp y borde ~56dp consistente. (Los stretch markers ya estaban corregidos a 200–824 por Claude.)
2. **`GameplayMockActivity.applyRevealOverlayTheme()`**: se sacó el `setPadding(0,0,0,0)` (anulaba la caja del 9-patch) → padding explícito ~dp(50/48) para death/silence/vote. **La ventana de info privada (víctima) ya NO usa el marco ornamental grande** → usa `bg_reveal_compact_*` (fino, temático), con `privateFeedbackPanel` a **300dp centrado** en el XML.
3. **`VoteResultAnimator`** (recuento): grilla **2 columnas que envuelven**, `recountCardSize` ajustado, última carta impar centrada (`GridLayout.spec(0,2,CENTER)`). Higiene de `GridLayout`: `removeAllViews()` ANTES de cambiar `columnCount` en `show()` y `playExpulsion()`.

Archivos tocados por Claude: `drawable-xxhdpi/ui_frame_event_*.9.png`, `GameplayMockActivity.kt`, `activity_gameplay_mock.xml`, `VoteResultAnimator.kt`.

## Crash "VER EXPULSIÓN"
- Tu sospecha de excepción de layout por GridLayout es la más probable → Claude la blindó (orden de `removeAllViews`/`columnCount`).
- `targetPlayer == null` **ya está manejado** (early return con `setContinueReady` + `onFinished`), no es el crash.
- **Falta el Logcat** (`FATAL EXCEPTION`) para confirmar. Si tras compilar sigue, el usuario lo pega.

## Paso siguiente ACORDADO (solo si al probar el mínimo intrínseco sigue molestando)
Si después de compilar los reveals grandes (death/vote/silence) todavía muestran hueco por tamaño mínimo, adoptamos **tu idea** (es mejor y más robusta), en una spec aparte:
- Contenedor `FrameLayout` de **tamaño controlado** por overlay.
- El marco como **ImageView de fondo** (dimensión explícita razonable), desacoplado de la medición del contenido.
- Contenido **centrado encima**.
- Tamaños **separados** por tipo (private / vote / death-silence) porque no todos necesitan el mismo.
- Recuento: grilla dentro de una **caja fija/limitada**, sin depender del tamaño intrínseco del PNG.

> Orden: el usuario compila y prueba lo que ya aplicó Claude → si queda bien, listo; si el mínimo sigue molestando, Claude escribe la spec con tu enfoque de ImageView+contenedor controlado y ahí sí lo implementás.
