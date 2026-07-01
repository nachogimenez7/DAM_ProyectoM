# Spec — Chat central: pergamino translúcido por mapa + emojis ocasionales en bots

> Handoff Claude (diseño/review) → Codex (implementación). Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.

**Contexto:** el chat central en gameplay (`chatAmbientFeed`, la vista chica que aparece arriba a la izquierda con las últimas líneas) hoy usa un marco genérico plano (`bg_chat_frame_thin`) que no llama la atención ni se siente "de mapa". El usuario quiere que use las mismas texturas por mapa que **ya existen y ya se usan en otras pantallas** (mármol griego, madera medieval, símbolos patrios en la pampa), en versión translúcida — sin tapar demasiado el fondo del mapa detrás. También quiere que los bots usen algún emoji ocasional en sus líneas. Las reacciones tocables sobre mensajes quedan **explícitamente fuera de este ciclo** (es una función nueva, no un retoque visual — el usuario decidió no abrirla ahora).

**Actualización — confirmado con capturas:** el tratamiento en `chatAmbientFeed` (sección 1) ya se implementó y el usuario lo validó en pantalla (se ve la textura por mapa sin tapar demasiado el fondo). **Se confirma también aplicar el mismo tratamiento a la ventana expandida (`chatPanel`, "EL CRONISTA DE LA POLIS")** — ya no es opcional, ver sección 1 más abajo, apartado "Aplicar lo mismo a la ventana expandida".

---

## 1. Fondo de pergamino translúcido, reusando el asset por mapa que ya existe

### Lo que ya existe (no hay que crear arte nuevo)

`logDrawableFor(theme: String)` (`GameplayMockActivity.kt:5660-5666`) ya devuelve la textura correcta por mapa:

```kotlin
private fun logDrawableFor(theme: String): Int {
    return when (theme) {
        "medieval" -> R.drawable.log_medieval
        "griego" -> R.drawable.log_griego
        else -> R.drawable.log_gaucho
    }
}
```

Los archivos (`app/src/main/res/drawable-nodpi/log_griego.webp`, `log_medieval.webp`, `log_gaucho.webp`, ~500-600KB cada uno) ya están en el repo y ya se usan como fondo opaco en el panel de bitácora de eventos (`eventLogBackground`, `gameplay_table_section.xml:268-281`) y en la vista previa de rol. **No hace falta encargar ni generar ningún asset nuevo** — es la misma textura, aplicada distinto (translúcida, en un lugar nuevo).

### Cómo aplicarlo al chat sin tapar el mapa

Seguir el mismo patrón estructural que ya usa `eventLogContent`/`eventLogBackground` (`gameplay_table_section.xml:268-281`): un contenedor `FrameLayout` con un `ImageView` de fondo (la textura del mapa) detrás del contenido, en vez de un `LinearLayout` con `android:background` plano como está hoy `chatAmbientFeed`.

**Restructurar `chatAmbientFeed`** (`gameplay_table_section.xml:303-339`) de `LinearLayout` a `FrameLayout`, con:
1. `ImageView` de fondo, `src` = `logDrawableFor(themeKey)` (seteado por código, igual que `eventLogBackground`), `scaleType="fitXY"`, y **`android:alpha` reducido (probar valores entre 0.5 y 0.7)** para que se note la textura del mapa pero el fondo de detrás (el mapa real de la pantalla) siga transparentando. Ajustar el valor exacto mirándolo en pantalla — no hay un número "correcto" de antemano, depende de cómo se vea con las 3 texturas.
2. Encima de esa imagen, una capa oscura semi-transparente (reusar `bg_reveal_text_shade`, el mismo recurso que ya usan los paneles de reveal para que el texto se lea sobre cualquier fondo) — esto es importante porque el mármol griego es claro y el texto actual (dorado/verde/naranja) está pensado para leerse sobre fondo oscuro. Sin esta capa, el mármol podría comerse la legibilidad.
3. Encima de todo eso, el contenido actual sin cambios: `chatAmbientMessages` (LinearLayout con las líneas) y `chatAmbientHint` ("Toca para hablar").

**En código** (`GameplayMockActivity.kt` o `GameplayChatController.kt`, donde se maneje el theming del chat): agregar una línea que setee el nuevo `ImageView` de fondo del `chatAmbientFeed` con `logDrawableFor(themeKey)`, en el mismo lugar donde ya se llama `renderChatBackgrounds()`.

### Aplicar lo mismo a la ventana expandida (confirmado)

La ventana de chat expandida (`chatPanel`, título "EL CRONISTA DE LA POLIS") hoy sigue con el marco fino plano (`bg_chat_frame_thin`) sin la textura por mapa — confirmado en captura por el usuario tras validar el resultado en `chatAmbientFeed`. **Aplicar el mismo tratamiento acá también**: mismo patrón (`ImageView` de fondo con `logDrawableFor(themeKey)` + capa oscura semi-transparente tipo `bg_reveal_text_shade` + contenido existente sin cambios), reestructurando `chatPanel` de la misma forma que `chatAmbientFeed`.

Único ajuste a tener en cuenta acá: esta ventana tiene scroll de mensajes + input de texto (más contenido y más tiempo de lectura que el feed chico), así que probablemente necesite **un poco más de opacidad en la capa oscura** que la usada en `chatAmbientFeed`, para que leer varios mensajes seguidos no se sienta incómodo contra la textura de mármol/madera. Ajustar a ojo en Android Studio — no hay un valor fijo correcto de antemano.

---

## 2. Emojis ocasionales en las líneas de los bots

El usuario quiere que los bots usen algún emoji puntual en sus líneas de chat — no como feature nueva de interacción, solo como contenido de texto (ya existe una plantilla de líneas con "sabor" por personalidad en `LocalBotAi.kt`, ver specs previas `bots-ai.md`/`bots-ai-fixes.md`).

**Alcance:** sumar emojis a algunas de las plantillas de línea existentes (`lineForIntent`, `reactionsToEvent`, u otras funciones de generación de texto en `LocalBotAi.kt`), en momentos puntuales de tensión/sospecha (ej. 🤔, 👀, 😰) — **con moderación**: no en cada línea, para no perder el tono serio/medieval del juego. Mantener el determinismo existente (`stableNoise`, no `Math.random()`) si el emoji se elige entre variantes.

**No tocar:** esto es solo contenido de texto en las plantillas ya existentes — no requiere UI nueva, no requiere lógica de reacciones tocables (eso queda fuera, ver sección 3).

---

## 3. Explícitamente fuera de alcance — reacciones tocables

El usuario decidió **no** sumar reacciones tocables sobre mensajes en este ciclo (tipo apretar 👍/😨 sobre una línea de otro jugador) — es una función nueva de interacción, no un retoque visual, y el ciclo actual es de estabilización. Queda anotado acá como idea a futuro, **no implementar en este pase**.

---

## Constraints del ciclo

- Reusar los assets `log_griego`/`log_medieval`/`log_gaucho` ya existentes — no encargar ni generar arte nuevo.
- Mantener identidad medieval/dorada y legibilidad del texto sobre cualquiera de los 3 fondos.
- No agregar funciones nuevas de interacción (reacciones quedan afuera).
- No compilar — el usuario valida en Android Studio, ajustando a ojo el valor de alpha si hace falta.

## Resumen de archivos a tocar

- `app/src/main/res/layout/gameplay_table_section.xml` y `app/src/main/res/layout-land/gameplay_table_section.xml` — restructurar `chatAmbientFeed` de `LinearLayout` a `FrameLayout` con el `ImageView` de textura + capa oscura + contenido existente (**recordar tocar ambos archivos**, no solo uno).
- `app/src/main/java/com/traidores/juego/GameplayChatController.kt` o `GameplayMockActivity.kt` — setear el nuevo `ImageView` de fondo con `logDrawableFor(themeKey)` junto al resto del theming del chat.
- `app/src/main/java/com/traidores/juego/LocalBotAi.kt` — sumar emojis ocasionales a algunas plantillas de línea existentes.
