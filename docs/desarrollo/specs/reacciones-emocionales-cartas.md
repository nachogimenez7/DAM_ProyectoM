# Spec — Reacciones emocionales sobre la carta (burbuja + sonido + chat)

> ⚠️ **TODAVÍA EN PLANIFICACIÓN — NO ENVIAR A CODEX TODAVÍA.** El usuario pidió explícitamente terminar de planear esto antes de encargarlo. Este documento es la base de esa planificación, se sigue completando.

> Handoff Claude (diseño/review) → Codex (implementación), cuando esté listo. Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.

**⚠️ Nota de alcance:** esto es una **función nueva**, distinta del resto de las specs de esta serie (que eran estabilización/pulido visual). El usuario lo pidió explícitamente como el próximo bloque de trabajo, después de cerrar el modo local — no mezclar con el resto de los ajustes de gameplay en curso.

**Referencia visual:** el usuario mostró como referencia el estilo de reacciones de Clash Royale (burbuja de diálogo con un personaje ilustrado y expresivo adentro, apareciendo sobre la unidad). La idea NO es una burbuja de diálogo clásica (forma de nube de texto) sino algo *similar en espíritu* — una forma que sobresale de la carta con la cara adentro, más chica y simple que la referencia.

**Contexto:** el jugador humano (y también los bots) pueden mostrar una reacción emocional sobre su propia carta — 4 estados: **enojado, triste, contento, sospechoso**. Al activarse: se ve un ícono sobre la carta, suena un efecto de audio, y queda una línea en el chat anunciándolo. Decisiones ya tomadas con el usuario:

- Solo reaccionás con **tu propio** estado — no se apunta a otro jugador (más simple, sin necesitar elegir objetivo).
- Los **bots también reaccionan**, no es exclusivo del humano.
- Cada reacción **también genera una línea en el chat** (no es solo ícono+sonido flotante).

---

## 1. Disparador y UI — ícono sobre la propia carta

### Dónde engancha
`SidePlayerCardHolder` (`GameplayMockActivity.kt:346-357`) ya tiene el patrón exacto a seguir — es un `data class` con varios badges superpuestos sobre la carta (`mutedBadge`, `actionBadge`, ambos `TextView` anclados sobre `cardFace`). Agregar un campo más:

```kotlin
private data class SidePlayerCardHolder(
    val root: LinearLayout,
    val cardFace: FrameLayout,
    val cardBack: ImageView,
    val avatar: TextView,
    val mutedBadge: TextView,
    val actionBadge: TextView,
    val reactionBadge: ImageView,   // nuevo
    val name: TextView,
    ...
)
```

### Cómo se activa (solo para la carta del jugador humano)
Sobre la carta propia del humano (en el panel inferior, `currentPlayerCard`/similar, no las cartas laterales de los demás — revisar cuál es la carta "propia" vs las de compañía), agregar un control chico (botón o el propio ícono, tocable) que abra un selector de 4 opciones (enojado/triste/contento/sospechoso) — puede ser 4 iconitos en fila que aparecen al tocar un botón "reaccionar", similar en espíritu al selector de emoji de un juego de mesa online. No hace falta un diálogo grande — un popup/fila chica alcanza.

### Presentación visual — burbuja sobre la carta, no un badge chico
A diferencia del planteo original (un ícono chico tipo badge), la reacción se muestra como una **burbuja que sobresale de la carta hacia arriba**, con la cara/ilustración adentro — inspirado en el estilo de referencia (Clash Royale) pero más chico y simple, y con identidad propia (borde dorado, fondo oscuro/pergamino, "colita" apuntando hacia la carta), no una nube de diálogo genérica. Tanto para la carta del humano como para las de los bots (todas las cartas laterales pueden mostrar su propia burbuja cuando reaccionan).

Al elegir/disparar una reacción:
1. Aparece la burbuja sobre la carta correspondiente (entrada + `hold` de un par de segundos + salida — mismo patrón de `AnimatorSet` ya usado en el resto del código para elementos temporales sobre las cartas).
2. Se reproduce el sonido correspondiente (ver sección 2).
3. Se agrega una línea al chat (ver sección 3).

### Assets visuales — Fase 1 (ahora)
4 caras (enojado/triste/contento/sospechoso), chicas y simples de leer a ese tamaño — el usuario ya confirmó que puede generarlas con Codex (que tiene acceso a generación de imágenes vía ChatGPT); esa conversación de arte queda entre el usuario y Codex al momento de implementar, no es algo a resolver en esta spec. Para la Fase 1, un solo set de 4 alcanza para los 3 mapas (no hace falta variar por tema todavía — eso es la Fase 2).

---

## 2. Sonido — assets nuevos a conseguir

`GameSound` (`GameplayAudioDirector.kt:16-31`) es el catálogo actual de efectos — no tiene nada parecido a estas 4 emociones. Hacen falta **4 sonidos nuevos**, uno por reacción. Mismo patrón que se usó para el resto del audio del proyecto (ver `docs/desarrollo/specs/audio-director.md`): **el usuario consigue los archivos** (banco libre/CC0, ej. freesound.org, Pixabay Sound Effects, mixkit), Codex los integra al catálogo:

```kotlin
ANGRY(R.raw.sfx_reaction_angry, HapticLevel.LIGHT, 0.8f),
SAD(R.raw.sfx_reaction_sad, HapticLevel.LIGHT, 0.8f),
HAPPY(R.raw.sfx_reaction_happy, HapticLevel.LIGHT, 0.8f),
SUSPICIOUS(R.raw.sfx_reaction_suspicious, HapticLevel.LIGHT, 0.8f),
```

Sonidos cortos y sutiles (tipo "ping" de reacción de juego de mesa online, no efectos largos) — nombres de archivo válidos para Android raw (`a-z 0-9 _`, sin espacios/tildes/paréntesis, mismo problema que ya se resolvió una vez con el resto del audio).

---

## 3. Integración con el chat

`GameChatMessage` (`GameModels.kt:284-288`) ya es la estructura que se usa para toda línea de chat (`speaker`, `message`, `isGod`). Una reacción no es un mensaje de "Dios" (`isGod = false`), es del jugador que reacciona — reusar el mismo mecanismo que ya usan las líneas normales de chat, con un texto tipo:

```
"$nombreJugador reacciono con enojo/tristeza/alegria/sospecha."
```

(el ✦ visual delante ya lo aplica el renderer para mensajes de Dios — para una reacción, capaz conviene un ícono distinto, a definir en el detalle visual del chat, no bloqueante para esta spec).

No hace falta un tipo de mensaje nuevo en el modelo de datos — alcanza con generar el texto de la reacción y agregarlo al `chatHistory` con el mecanismo existente.

---

## 4. Bots reaccionando a sucesos

Los bots ya tienen un mecanismo de reacción a sucesos narrativos: `LocalBotAi.reactionsToEvent()` (`LocalBotAi.kt:935`, agregado en una ronda anterior — ver `docs/desarrollo/specs/bots-ai.md` Fase 1) genera comentarios de chat cuando pasa algo (muerte, expulsión, silencio). Extender esa misma función (o una hermana) para que, además del comentario de texto, el bot también dispare una reacción emocional coherente con el suceso y su personalidad — ejemplos orientativos:

- Un bot cuyo aliado murió → triste o enojado (según personalidad).
- Un bot que fue acusado/votado en su contra → enojado o sospechoso.
- Un bot cuyo bando parece ir ganando → contento.
- Un bot con personalidad DESCONFIADO reaccionando a una muerte → sospechoso, casi siempre.

No hace falta una IA nueva — es una capa más sobre la lógica de reacciones a sucesos que ya existe, mapeando el tipo de suceso + personalidad a una de las 4 emociones. Mantener el determinismo existente (`stableNoise`, no `Math.random()`) igual que el resto de `LocalBotAi.kt`.

**Sugerencia de límite (a confirmar, no bloqueante):** para que no se sienta spam, considerar que un bot no reaccione más de una vez por suceso público, y no reaccione en cada ronda si no pasó nada relevante — mismo criterio de moderación que ya se aplica a las líneas de chat de los bots.

---

## 5. Fase 2 (futuro, no implementar todavía) — cada emoción con cara de un rol, por mapa

Aclaración importante del usuario: la Fase 2 **no** es "las mismas 4 caras genéricas con piel distinta por mapa" — es asignarle cada una de las 4 emociones a un **personaje/rol específico** del juego. Ejemplo dado por el usuario:

- Enojado → Policía/Detective
- Triste → Médico
- Contento → Aldeano
- Sospechoso → Alcalde (o similar)

Y esto se repetiría **por mapa** (griego/medieval/pampa), dando 4 emociones × 3 mapas = **12 combinaciones en total** — cada mapa ya tiene su propia ilustración de esos roles (`RoleCatalog`/`roleImageFor()` y el arte de cartas de rol que ya existe en el proyecto). **Antes de encargar arte nuevo para esta fase, revisar si se puede reusar/recortar el arte de rol que YA existe por mapa** (las ilustraciones de Policía, Médico, Aldeano, Alcalde de cada tema ya están en el proyecto para las cartas de rol) — podría ahorrar tener que generar 12 ilustraciones nuevas desde cero.

Esto queda anotado para planear en detalle más adelante, no forma parte de la primera entrega (Fase 1, 4 caras genéricas).

## 6. Fase 3 (idea de producto, no técnica) — transacciones para emotes

El usuario mencionó, a modo de idea a futuro, la posibilidad de vender/monetizar emotes adicionales. **Esto es una decisión de producto/negocio, no un tema técnico de esta spec** — no se planea ni se toca nada de esto ahora. Queda anotado únicamente para que quede registro de que la idea existe, para cuando llegue el momento de conversarla en serio (monetización, tienda in-app, etc. — todo eso implica decisiones de producto y posiblemente de infraestructura que hoy no están sobre la mesa).

---

## Constraints del ciclo

- Es función nueva — no forma parte de "estabilización visual", tratarlo como su propio bloque de trabajo.
- Mantener identidad medieval/dorada en la burbuja y su animación (no una nube de diálogo genérica).
- No compilar — el usuario valida en Android Studio.
- Los 4 sonidos son responsabilidad del usuario conseguirlos (igual que el resto del audio del proyecto). Las 4 caras de la Fase 1 se resuelven en conversación aparte entre el usuario y Codex (generación de imágenes vía ChatGPT) al momento de implementar.
- Fase 2 (12 combinaciones rol × mapa) y Fase 3 (monetización) quedan fuera de esta primera entrega — anotadas para más adelante, no implementar todavía.

## Resumen de archivos a tocar/crear (Fase 1)

- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — `SidePlayerCardHolder` (nuevo campo para la burbuja de reacción), UI del selector de reacción, animación de entrada/salida de la burbuja, disparo del sonido y de la línea de chat.
- `app/src/main/java/com/traidores/juego/GameplayAudioDirector.kt` — 4 nuevas entradas en `GameSound`.
- `app/src/main/res/raw/` — 4 archivos de sonido nuevos (a conseguir).
- `app/src/main/res/drawable/` — 4 caras nuevas (enojado/triste/contento/sospechoso) — arte a resolver con Codex al momento de implementar.
- `app/src/main/java/com/traidores/juego/LocalBotAi.kt` — extender `reactionsToEvent()` (o función hermana) para que los bots también disparen una reacción emocional coherente con el suceso y su personalidad.
