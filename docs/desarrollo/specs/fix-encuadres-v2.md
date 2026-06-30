# Spec — Fix de encuadres (frames) + ventana de acción compacta

> Handoff Claude (diseño/diagnóstico) → Codex. Código = fuente de verdad.
> Diff acotado: solo tocar lo descrito. No mezclar cambios no pedidos.
> El usuario valida en Android Studio (BlueStacks).

---

## Contexto

Codex generó frames ornamentales como `.9.png` por mapa (cuero/pampa, mármol/grecia, hierro-madera/medieval).
Están bien diseñados y se ven lindos en los overlays dramáticos (death reveal).
El problema es que están MAL COLOCADOS en tres lugares:

1. El `chatAmbientFeed` (feed flotante en gameplay normal) tiene el frame gigante como fondo → tapa el centro del gameplay.
2. El `chatPanel` (chat expandido "El Cronista") tiene el frame como fondo pero los frames tienen **centro blanco opaco** → luce como fondo blancuzco, no como encuadre.
3. El `privateFeedbackPanel` (ventana "INFORMACION PRIVADA") ocupa TODA la pantalla de alto → técnicamente está llena porque un hijo con `match_parent` dentro de un `wrap_content` FrameLayout expande al padre.

**Principio guía:** Los frames ornamentales son para overlays dramáticos (pausa, muerte, acción elegida), no para UI interactiva/ambiental.

---

## TAREA 1 — Quitar el frame del chatAmbientFeed

**Archivo:** `app/src/main/java/com/traidores/juego/GameplayChatController.kt`

Función `renderChatBackgrounds()` (aprox línea 1248):

```kotlin
// ANTES:
private fun renderChatBackgrounds() {
    val background = chatBoxBackgroundFor(host.currentSession.mapKey)
    chatAmbientFeed.setBackgroundResource(background)   // ← ELIMINAR esta línea
    chatPanel.setBackgroundResource(background)
}

// DESPUÉS:
private fun renderChatBackgrounds() {
    val background = chatBoxBackgroundFor(host.currentSession.mapKey)
    chatPanel.setBackgroundResource(background)
    // chatAmbientFeed conserva su background del XML: bg_chat_ambient_feed (no tocar)
}
```

El `chatAmbientFeed` ya tiene en el XML `android:background="@drawable/bg_chat_ambient_feed"` que es correcto: un shape oscuro sutil. No sobrescribir.

---

## TAREA 2 — Simplificar fondo del chatPanel (sin frame de centro blanco)

Los drawables `bg_chat_box_pampa.xml`, `bg_chat_box_grecia.xml`, `bg_chat_box_medieval.xml` actualmente incluyen el frame `ui_frame_feed_*` como segunda capa. Ese frame tiene **centro blanco opaco** → luce como fondo en vez de encuadre.

Reemplazar los tres drawables. Formato: shape oscuro + stroke fino coloreado por mapa.

**`app/src/main/res/drawable/bg_chat_box_pampa.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="14dp" />
            <solid android:color="#D824190F" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="14dp" />
            <stroke
                android:width="2dp"
                android:color="#BF7A3A1A" />
        </shape>
    </item>
</layer-list>
```

**`app/src/main/res/drawable/bg_chat_box_grecia.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="14dp" />
            <solid android:color="#D51E2428" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="14dp" />
            <stroke
                android:width="2dp"
                android:color="#BFC4A84A" />
        </shape>
    </item>
</layer-list>
```

**`app/src/main/res/drawable/bg_chat_box_medieval.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="14dp" />
            <solid android:color="#DE18120D" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="14dp" />
            <stroke
                android:width="2dp"
                android:color="#BF5C4835" />
        </shape>
    </item>
</layer-list>
```

Resultado: chat limpio y legible, con tinte de color sutil por mapa. Sin frame blanco.

---

## TAREA 3 — privateFeedbackPanel compacto con frame de evento

### 3a. Layout XML — ventana de tamaño fijo centrada

**Archivo:** `app/src/main/res/layout/activity_gameplay_mock.xml`

El `privateFeedbackPanel` tiene `layout_height="wrap_content"` pero su hijo `privateFeedbackTone` tiene `layout_height="match_parent"`. Dentro de un FrameLayout cuyo padre es `match_parent`, esto fuerza al panel a pantalla completa.

**Fix:** Cambiar a altura fija `180dp` y ajustar contenido:

```xml
<FrameLayout
    android:id="@+id/privateFeedbackPanel"
    android:layout_width="300dp"
    android:layout_height="180dp"
    android:layout_gravity="center"
    android:background="@drawable/bg_translucent_game_panel"
    android:elevation="14dp">

    <View
        android:id="@+id/privateFeedbackTone"
        android:layout_width="5dp"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:background="@color/accent_gold" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:orientation="vertical"
        android:paddingHorizontal="20dp"
        android:paddingVertical="12dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif"
            android:includeFontPadding="false"
            android:text="INFORMACION PRIVADA"
            android:textColor="@color/text_muted"
            android:textSize="9sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/privateFeedbackTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:fontFamily="sans-serif"
            android:gravity="center"
            android:includeFontPadding="false"
            android:maxLines="1"
            android:text="RESPUESTA PRIVADA"
            android:textColor="@color/accent_gold"
            android:textSize="18sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/privateFeedbackMessage"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:fontFamily="sans-serif"
            android:gravity="center"
            android:includeFontPadding="false"
            android:maxLines="2"
            android:text="Martina parece SOSPECHOSA."
            android:textColor="@color/text_primary"
            android:textSize="13sp"
            android:textStyle="bold"
            app:autoSizeMaxTextSize="13sp"
            app:autoSizeMinTextSize="10sp"
            app:autoSizeStepGranularity="1sp"
            app:autoSizeTextType="uniform" />

        <Button
            android:id="@+id/btnContinuePrivateFeedback"
            style="@style/BtnDark"
            android:layout_width="200dp"
            android:layout_height="32dp"
            android:layout_marginTop="10dp"
            android:background="@drawable/bg_tie_vote_button"
            android:minHeight="0dp"
            android:minWidth="0dp"
            android:padding="0dp"
            android:text="CONTINUAR"
            android:textSize="10sp" />
    </LinearLayout>
</FrameLayout>
```

### 3b. Código — aplicar frame de evento por mapa

**Archivo:** `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt`

Encontrar `compactRevealPanelBackgroundForMap()` (aprox línea 2707):

```kotlin
// ANTES — shape simple
private fun compactRevealPanelBackgroundForMap(mapKey: String): Int {
    return when (mapKey.lowercase()) {
        "grecia" -> R.drawable.bg_reveal_compact_grecia
        "medieval" -> R.drawable.bg_reveal_compact_medieval
        "pampa" -> R.drawable.bg_reveal_compact_pampa
        else -> R.drawable.bg_translucent_game_panel
    }
}

// DESPUÉS — frame ornamental con centro oscuro (igual que death reveal)
private fun compactRevealPanelBackgroundForMap(mapKey: String): Int {
    return when (mapKey.lowercase()) {
        "grecia" -> R.drawable.ui_frame_event_grecia
        "medieval" -> R.drawable.ui_frame_event_medieval
        "pampa" -> R.drawable.ui_frame_event_pampa
        else -> R.drawable.bg_translucent_game_panel
    }
}
```

**Importante:** Después de aplicar el background en `applyThematicBackgrounds()` (aprox línea 2694), forzar el padding del panel para que el contenido no quede bajo las decoraciones del frame:

```kotlin
// Inmediatamente después de setBackgroundResource:
privateFeedbackPanel.setBackgroundResource(compactRevealPanelBackgroundForMap(session.mapKey))
privateFeedbackPanel.setPadding(dp(22), dp(14), dp(22), dp(14))
```

Donde `dp(n)` = `(n * resources.displayMetrics.density).toInt()` (usar el helper existente o definirlo localmente).

---

## Verificación esperada

| Pantalla | Antes | Después |
|---|---|---|
| Gameplay normal (NOCHE/DIA) | Frame de cuero gigante en el centro | Feed ambiental sutil, sin frame ornamental |
| Chat expandido (El Cronista) | Frame de cuero como fondo con centro visible | Panel oscuro limpio con borde fino marrón/dorado/hierro |
| Ventana de acción (INFORMACION PRIVADA) | Panel full-screen de alto, fondo simple | Card compacta 300×180dp centrada, con frame ornamental del mapa |

## Archivos a modificar

1. `app/src/main/java/com/traidores/juego/GameplayChatController.kt` — Tarea 1
2. `app/src/main/res/drawable/bg_chat_box_pampa.xml` — Tarea 2
3. `app/src/main/res/drawable/bg_chat_box_grecia.xml` — Tarea 2
4. `app/src/main/res/drawable/bg_chat_box_medieval.xml` — Tarea 2
5. `app/src/main/res/layout/activity_gameplay_mock.xml` — Tarea 3a
6. `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — Tarea 3b
