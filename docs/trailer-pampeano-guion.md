# Tráiler cinematográfico — Mapa Pampeano

**Duración:** ~65 s · **Formato principal:** 16:9 (después se puede hacer una versión 9:16 para Reels/TikTok)
**Idea:** un asesinato en un pueblo de la pampa, una investigación donde la espía "ayuda" al detective, y un giro final: ella es la asesina.

**Cómo está pensada la historia (4 actos, que además calzan con el ciclo día/noche del juego):**

| Acto | Qué pasa | Clima |
|---|---|---|
| 1. Calma | Pueblo tranquilo, el payador canta | Atardecer |
| 2. Crimen | Se apaga la luz, disparo, huye un jinete de poncho rojo | Noche |
| 3. Investigación | El detective llega, el pueblo se acusa entre sí, la espía le da una "pista" | Día / atardecer |
| 4. Giro | El detective descubre la verdad: la espía es la asesina | Noche |

**La pista del giro:** en el plano 6B se ve un **aro dorado** en la mano de la víctima (el espectador no sabe de quién es). En el plano 13 aparece la espía con **un solo aro**. Sin diálogo, el público lo entiende solo.
**El engaño:** el hombre del poncho rojo es el culpable aparente. En realidad la espía lo incriminó dejando su poncho en un rancho abandonado.

---

## 0. Antes de generar (importante)

1. **Referencias:** adjuntá en cada generación la carta del rol correspondiente (`roles_gauchos/`). Lo ideal es recortar al personaje y pasarlo con fondo limpio, para que el modelo no copie el fondo de la carta.
2. **Planos que podés animar directo desde tus cartas** (imagen a video, usando la carta como primer cuadro): 2 (payador), 5 (asesino a caballo, de noche) y 7 (detective). Ya están compuestas y el personaje sale idéntico. Las cartas son verticales (2:3), así que hay que extenderlas a 16:9 con un generador de imágenes antes de animarlas.
3. **Estilo:** tus cartas son pintura al óleo. Mantené ese estilo ("pintura viva"). Ayuda a disimular pequeñas diferencias de rostro entre planos.
4. **Generá 2 o 3 versiones de cada plano** y elegí la mejor. Cada plano es un clip aparte de 3 a 5 s; se unen en el editor.
5. **Texto, logo y diálogos van aparte.** La IA escribe mal. Los textos y el logo se ponen en el editor, y las frases ("voz en off") se graban o generan aparte.

### Bloque de estilo (pegalo al final de CADA prompt)

```
Cinematic painterly look, like a living oil painting. 19th-century Argentine pampas, western atmosphere. Rich earthy palette, subtle film grain, anamorphic lens, shallow depth of field, slow deliberate camera movement, natural human motion and facial expressions. No text, no subtitles, no logos, no modern objects, no morphing faces, no cartoon style, no cuts inside the clip.
```

### Descripciones fijas de personajes (copiá la que corresponda dentro del prompt)

- **DETECTIVE:** man in his 40s, mustache and short beard, dark curly hair, wide-brim black hat with a navy blue band, navy blue frock coat, black waistcoat with pocket-watch chain, navy neckerchief, white-and-blue striped poncho over one shoulder, silver star badge on chest, leather gunbelt with revolver, baggy navy bombachas.
- **ESPÍA:** woman around 38, wavy black hair in a loose braid, ONE small gold hoop earring visible, brown wide-brim hat, dark poncho with red geometric trim and fringe, red neckerchief, cream blouse, leather belt with bullet loops, revolver. Warm, trustworthy face with a slight sly half-smile.
- **SOSPECHOSO (poncho rojo):** man around 40, black curly hair, stubble, brown hat, deep red poncho with fringe and geometric trim, red neckerchief, yellow sash, double-barrel shotgun, black horse with a white diamond blaze on the forehead.
- **PAYADOR:** cheerful man with curly black hair and mustache, tan hat, red patterned poncho over cream shirt, red neckerchief, classical guitar.
- **MÉDICA:** woman around 45, plum headscarf, maroon fringed shawl, cream blouse and apron, brown skirt, leather medical bag with glass bottles.
- **ALCALDESA:** stout older woman, dark curly hair in an updo, dark navy dress, brown fringed shawl, wooden gavel in one hand and a large leather book under her arm. Stern expression.
- **ALDEANO:** heavyset man, brown hat, cream shirt, red neckerchief, baggy brown bombachas, mate gourd, sheepdog nearby.

---

## 1. Guion plano por plano

> **Música (en el editor):** guitarra criolla cálida → silencio total en el crimen → cuerdas graves y **bombo legüero** que va creciendo desde el plano 9 → corte seco en el plano 15.

### ACTO 1 — CALMA

**Plano 1 · 5 s · Establecimiento** (sin referencias)
```
Wide aerial drone shot gliding slowly over endless golden pampas grass at sunset. A small gaucho village with whitewashed adobe houses and terracotta roofs, a metal windmill turning slowly, dust floating in the warm light, a lone rider far in the distance. The camera moves forward and descends gently toward the village. Golden-hour light.
```
Sonido: viento, molino rechinando, guitarra lejana. En el editor: texto "Un pueblo en la pampa…"

**Plano 2 · 4 s · El payador** (referencia: `rol_payador_gaucho.png`, imagen a video)
```
The cheerful gaucho singer from the reference image plays guitar and sings, laughing, in a candlelit pulpería at dusk. His head moves with the rhythm and his fingers strum. Blurred townsfolk in hats clap and smile in the background. Lantern light flickers warmly. Slow push-in toward his face.
```

**Plano 3 · 3 s · Alguien mira** (referencia: espía)
```
Seen from outside through a dusty window: the woman in dark poncho and brown hat watches the singer inside, lantern light reflecting on her face. She gives a slight, ambiguous smile, then lowers her eyes. Slow rack focus from the window glass to her face. [ESPÍA description]
```
Nota: tiene que verse amable y ambigua, no malvada.

### ACTO 2 — CRIMEN

**Plano 4 · 3 s · Se apaga la luz** (referencia: payador, opcional)
```
Inside the pulpería, extreme close-up of the guitar strings vibrating; one string snaps. The oil lantern flame flickers and dies, and the room falls into complete darkness.
```
Sonido: acorde que se corta, cuerda que salta, silencio de 1 segundo, **disparo**.

**Plano 5 · 4 s · El jinete de poncho rojo** (referencia: `rol_asesino_gaucho.png`, imagen a video)
```
Night, full moon. The rider in the deep red poncho on a black horse gallops away along a dirt road past a windmill, shotgun in hand, poncho flying, dust rising. The camera tracks alongside at low height. He glances back over his shoulder.
```
Sonido: galope, respiración del caballo.

**Plano 6A · 4 s · El hallazgo** (referencias: médica, aldeano)
```
Cold bluish dawn. In front of the pulpería, villagers gather in a circle. The heavyset farmer drops his mate gourd in shock. The healer woman in the plum headscarf kneels beside a body covered with a poncho and gently lifts its hand. [MÉDICA description] [ALDEANO description]
```

**Plano 6B · 2 s · La pista (insert)**
```
Extreme macro close-up of a dirt-stained hand slowly opening to reveal a small gold hoop earring in the palm. Shallow depth of field, cold morning light.
```
Sonido: un solo golpe grave (sting).

### ACTO 3 — INVESTIGACIÓN

**Plano 7 · 4 s · Llega el detective** (referencia: `rol_detective_gaucho.png`, imagen a video)
```
Bright midday. The detective from the reference image walks slowly through the village street toward the camera, villagers stepping aside. The silver star badge catches the sun and a windmill turns behind him. He stops and scans the faces with narrowed eyes. Low-angle hero shot, camera tracking backward.
```

**Plano 8 · 5 s · El pueblo se acusa** (referencias: alcaldesa, aldeano, detective)
```
Interior of a rustic general store used as town hall, harsh side light from a window. The stern older woman mayor slams a wooden gavel on the counter. Villagers argue and point fingers at each other; the heavyset farmer raises his hands in protest. In the background the detective stands silent, watching everyone. Handheld camera whipping between faces.
```
Voz en off (alcaldesa): "¡Uno de ustedes lo mató!"

**Plano 9 · 5 s · La espía "ayuda"** (referencias: espía + detective)
```
Over-the-shoulder two-shot behind the stables, golden afternoon light. The woman in the dark poncho leans close to the detective and whispers urgently, glancing around. She hands him a folded paper and points toward the distant hills. He studies her face, then nods, trusting her. Tight framing, shallow depth of field.
```
Voz en off (espía, susurrando): "Fue el del poncho rojo. Lo vi huir hacia el monte." Entra el bombo.

**Plano 10 · 4 s · Cabalgan juntos** (referencias: espía + detective)
```
Dusk, wide side-tracking shot: the detective on a gray horse and the woman on a brown horse gallop side by side across the pampas under an orange and purple sky, ponchos fluttering, tall grass waving, long shadows. They exchange a brief glance.
```

**Plano 11 · 4 s · Trampa (el poncho plantado)**
```
Twilight at an abandoned rancho with a broken fence. A deep red poncho hangs from a fence post, flapping in the wind; a shotgun lies in the grass. The detective dismounts and reaches for the poncho. In the background, out of focus, the woman stands watching him, perfectly still. Slow push-in.
```

### ACTO 4 — GIRO

**Plano 12 · 4 s · El detective entiende** (referencias: médica, detective)
```
Night inside the empty pulpería, a single lamp lit, the singer's guitar resting on a table. The detective's weathered hand holds a small gold hoop earring in the lamplight. Slow push-in from the earring to his face as his eyes slowly widen with realization.
```
Voz en off (médica, bajito): "Lo tenía agarrado en la mano."

**Plano 13 · 4 s · Ella aparece** (referencia: espía)
```
A shadow falls across the room. The woman stands in the doorway, backlit by moonlight, one hand resting on her holstered revolver. She slowly turns her head in profile toward the camera: one gold hoop earring on her visible ear. Slow dolly in. Tense atmosphere.
```
Nota: asegurate de que se vea el aro. Si el modelo no lo muestra bien, hacé un plano extra solo de la oreja.

**Plano 14 · 3 s · La máscara cae** (referencia: espía)
```
Extreme close-up of the woman's face in moonlight. Her warm, helpful expression slowly fades into a cold, calm half-smile. She tilts her head, eyes locked on the camera.
```
Voz en off (espía): "Tardaste en darte cuenta, detective." La música se corta.

**Plano 15 · 3 s · Duelo** (referencias: espía + detective)
```
Wide shot from outside through the open doorway: two silhouettes face each other inside the pulpería, moonlight on the floor, hands near their holsters. Wind blows dust through the door. Hold still, then cut to black.
```
Sonido: viento, **clic de un revólver**, negro.

### CIERRE

**Plano 16 · 5 s · Título** (en el editor, NO con IA)
Fondo: pintura de la pampa de noche (podés usar un fotograma del plano 1 oscurecido) o negro.
Textos: **"Hay un traidor entre nosotros."** → logo de TRAIDORES → "Disponible en Google Play / App Store".

---

## 2. Cómo recortar si querés un tráiler más corto (30–40 s)
Quedate con los planos 1, 2, 4, 5, 7, 9, 12, 13, 14 y 16.

## 3. Tené en cuenta
- En el juego, **Espía y Asesino son roles distintos**. En el tráiler los fusionamos para el giro; es licencia narrativa, pero conviene que no parezca que explica reglas del juego.
- Si algún modelo cambia la ropa entre planos, volvé a generar ese plano con más referencias (hoja de personaje con frente, perfil y espalda).
