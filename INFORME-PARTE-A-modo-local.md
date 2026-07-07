# INFORME — Revisión narrativa y de texto · App Traidores (Parte A: modo local)

> Auditoría de solo lectura (jul 2026). Entregable para implementación por Codex: cada propuesta incluye `archivo:línea` y "antes → después". No se editó ningún archivo del repo.

> **Regla de oro para el ejecutor:** varios textos públicos del cronista **son parseados por regex/`contains` en otros archivos**. Antes de tocar cualquier anuncio, respetar esto:
> - `GameplayTableUi.kt:613-640` extrae ronda/muertos/silenciados/expulsados con regex sobre `"Noche N"`, `"D[ií]a N"`, `"murio X"`, `"X no puede hablar ni votar hoy"`, `"X fue expulsado"`.
> - `ChronicleFeedPresenter.kt:78-88` clasifica por keywords (`"amanec"`, `"murio"`, `"expuls"`, `"votacion"`, `"empate"`, `"bufon"`…).
> - `LocalBotAi.kt:919-938` y `:2870-2876` reaccionan a `"murio"`, `"no murio nadie"`, `"no puede hablar"`, `"expulso a"`.
> - `GameplayTableUi.kt:368-373` (`isImportantPublicEvent`) matchea `"muteados:"`, `"oraculo"`, etc.
>
> **Receta segura:** la primera oración del anuncio queda idéntica (marcador + nombre); el color se agrega en oraciones nuevas al final, sin nombres de jugadores ni palabras clave (`murio`, `expulsado`, `no puede hablar`, `empate`).

---

## A.1 — Auditoría de la voz del cronista (`GameEngine.kt`)

**Diagnóstico general:** los mensajes de noche/día (`nightStartMessage`, `nightContinuesMessage`, `dayDebateMessage`, `nextNightMessage`) tienen buena atmósfera y variante por mapa. El problema es que **los picos dramáticos —amanecer con muerte, votación, expulsión— son los más administrativos**: sin sabor de mapa, sin variación, en tono de acta notarial. Además, varios mensajes fijos se repiten idénticos todas las rondas.

### Mensajes que están bien (no tocar salvo detalle)
- `nightStartMessage` (1524-1533) — las tres variantes tienen carácter. ✔
- `dayDebateMessage` medieval y pampa (1547, 1549) — ✔. La de grecia (1548) tiene `"engaño"` con ñ, único acentuado del bloque (ver A.3).
- Mensajes del Alcalde corrupto ("Corrupcion en el pueblo: …", 463-465, 820, 836, 854) — de lo mejor del cronista. ✔
- Contrapunto (521-522, 548) — funcional y con identidad. ✔

### Planos / repetidos — propuestas de reescritura

**1. Amanecer sin muerte — `GameEngine.kt:363`**
- Antes: `"Amanecer: no murio nadie."`
- Después (por mapa, primera oración intacta):
  - medieval: `"Amanecer: no murio nadie. Las puertas se abren despacio, entre el alivio y la desconfianza."`
  - grecia: `"Amanecer: no murio nadie. Los dioses concedieron una noche de tregua."`
  - pampa: `"Amanecer: no murio nadie. El pueblo despierta entero, pero nadie durmio tranquilo."`
- Requiere pasar `session` (ya está en scope en `resolveDawn`).

**2. Amanecer con muerte — `GameEngine.kt:368`**
- Antes: `"Amanecer: murio $victim."`
- Después (⚠ mantener `"murio $victim."` como primera oración exacta; la regex `murio\s+([^.\s]+)` de `GameplayTableUi.kt:618` captura la palabra siguiente):
  - medieval: `"Amanecer: murio $victim. El pueblo se reune en silencio alrededor del cuerpo."`
  - grecia: `"Amanecer: murio $victim. La plaza amanece de luto y de sospechas."`
  - pampa: `"Amanecer: murio $victim. Lo encontraron al alba; nadie vio nada, como siempre."`

**3. Fin de votación — `GameEngine.kt:581` y `:673` (y desempate `:605`, `:624`, `:706`, `:750`)**
- Antes: `"La votacion termino. Comienza el recuento."` (idéntico en 2 lugares; el de desempate en 4).
- Después: extraer a `private fun votingEndedMessage(session)` / `tieEndedMessage(session)`:
  - medieval: `"La votacion termino. El pregonero cuenta los votos ante todo el feudo."`
  - grecia: `"La votacion termino. Se cuentan las piedras en el agora."`
  - pampa: `"La votacion termino. Se cuentan las manos alzadas en la plaza."`
- Mantiene la keyword `"votacion"` que usa `ChronicleFeedPresenter.kt:85`.

**4. Expulsión — `GameEngine.kt:933`**
- Antes: `"Dia ${session.round}: $target fue expulsado."`
- Después (⚠ `"Dia N:"` y `"$target fue expulsado."` intactos; regex en `GameplayTableUi.kt:623-626`):
  - medieval: `"Dia N: $target fue expulsado. Cruza las puertas del feudo para no volver."`
  - grecia: `"Dia N: $target fue expulsado. El ostracismo queda sellado ante la polis."`
  - pampa: `"Dia N: $target fue expulsado. Se va del pueblo con lo puesto y sin despedidas."`

**5. Próxima noche — `GameEngine.kt:1558-1564` — acá hay un bug además de estilo (ver A.4.2):**
- Antes: `"La oscuridad vuelve a caer y el pueblo cierra sus puertas."` (idéntico todas las rondas).
- Después — **incluir el número de noche**, que además arregla la atribución de rondas del resumen final:
  - medieval: `"Noche ${prepared.round + 1}: la oscuridad vuelve a caer y el feudo atranca sus puertas."`
  - grecia: `"Noche ${prepared.round + 1}: la oscuridad vuelve a caer sobre la polis y sus dudas."`
  - pampa: `"Noche ${prepared.round + 1}: se apagan los faroles y el pueblo vuelve a quedar a oscuras."`

**6. "El amanecer se acerca." — `GameEngine.kt:1543`** — string fijo, sin mapa, y se muestra varias veces por noche. Propuesta (mantener `"amanec"`):
- medieval: `"El amanecer se acerca. El feudo contiene la respiracion."`
- grecia: `"El amanecer se acerca. La primera luz toca las columnas."`
- pampa: `"El amanecer se acerca. Ya clarea detras de los ranchos."`

**7. "Muteados: X." — `GameEngine.kt:1554`** — anglicismo gamer que rompe la ambientación; el resto del juego dice "silenciado".
- Antes: `"Dia N: $opening Muteados: $muted."` → Después: `"Dia N: $opening Silenciados: $muted."`
- ⚠ **Cambio acoplado obligatorio:** `GameplayTableUi.kt:371` matchea `"muteados:"` — actualizar en el mismo commit.

**8. Oráculo — `GameEngine.kt:387`** (sin parser dependiente; se puede reescribir libre):
- Antes: `"El Oraculo ha permitido que $invited regrese para discutir durante este dia."`
- Después: `"El Oraculo abre la puerta de las sombras: $invited regresa para hablar durante este dia."`
- ⚠ Si se cambia, revisar `GameplayTableUi.kt:373` que matchea `"regrese para discutir"` (pasarlo a `"regresa para hablar"` o mantener el verbo).

**`ChronicleFeedPresenter.kt` / `GameplayFeedMessages.kt`:** el presenter está bien estructurado, pero tiene un bug de clasificación (A.4.1). `appendGodEvents` deduplica mensajes idénticos (`GameplayFeedMessages.kt:12-18`) — otra razón para que los anuncios repetitivos lleven número de ronda: hoy dos amaneceres iguales en el flujo online se colapsan en uno.

---

## A.2 — Líneas de bots (`LocalBotAi.kt`)

**Diagnóstico:** el sistema es muy sólido (personalidades, agendas, memoria conversacional, `chooseFreshLine` anti-repetición, `sanitizeBotSpeech`, guard de auto-acusación con fallback en 3352-3361). La voz "jugador argentino de chat" es una decisión de diseño coherente y no la tocaría de fondo. Hallazgos:

### Bug funcional que mata 4 comportamientos (prioridad máxima)
`latestExpelledTarget` (**LocalBotAi.kt:2870-2876**) y `publicEventFromAnnouncement` (**:921**) buscan el marcador `"expulso a"`, pero **el motor nunca emite ese texto**: la expulsión se anuncia como `"$target fue expulsado"` (`GameEngine.kt:933`) o `"decidio expulsar a"` (`:463`, `:836`). El único `"se expulso a"` del repo está en el resumen de fin de partida (`GameplayTableUi.kt:586`), que no pasa por ahí. Consecuencia — todo esto es **código muerto en la práctica**:
1. Reacciones de bots a expulsiones (`BotEventType.EXPULSION`, las 18 líneas de 1436-1466 nunca suenan).
2. Apertura `"ayer sacamos a $expelled y seguimos igual, no votemos por inercia"` (:1023-1024).
3. El social read `failedPush` → `"ayer me pude haber equivocado con X"` (:2475-2482, :1019-1020, :1111-1116).
4. La razón de voto `"empujo mal ayer"` (:562-565).

**Fix propuesto (sin tocar firmas):** en `eventTarget`/`latestExpelledTarget`, usar el marcador `"fue expulsado"` (y opcionalmente `"expulsar a"` para los caminos del Alcalde). `eventTarget` ya resuelve el nombre por `mentionsName`, así que funciona igual.

### Texto roto visible en chat
- **:1077** `"acompanio lo de $target por ahora"` — "acompanio" no es ninguna palabra. → `"acompaño lo de $target por ahora"` (o, si se quiere evitar la ñ como en el resto: `"banco lo de $target por ahora"`).
- **:1114** `"si votamos mal de nuevo maniana revisen quien empujo esto"` → `"mañana"`.
- **:1480** `"igual los demas no safan"` → `"no zafan"` (ortografía).

### Frases que rompen inmersión (anacronismos evitables, mismo largo)
- **:1481** `"a $target le apagaron el microfono, pero al resto no"` → `"a $target le taparon la boca, pero al resto no"`.
- **:2232** `"$target estas jugando para el clip"` → `"$target estas actuando para la tribuna"`.
- (Opcional) **:1480** `"modo estatua"` es jerga moderna pero inofensiva; se puede dejar.
- Los emojis 🤔👀😰 (`withOccasionalEmoji`, :3012-3032) son modernos pero funcionan como "chat de jugadores"; decisión de producto, no bug.

### Variantes con más color medieval (agregar a las listas existentes, ≤140, sin tocar lógica)
Agregar (no reemplazar) en `linesFor` (**:2183-2256**):
- `Intent.ACCUSE`: `"yo a $spokenTarget no le fio ni una moneda, $reason"` · `"$spokenTarget jura mucho y explica poco"`
- `Intent.TEASE`: `"$spokenTarget esa mentira no sobrevive ni al primer gallo"` · `"jajaja $spokenTarget vendehumo de feria"`
- `Intent.DEFEND`: `"no quememos a $spokenTarget en la plaza sin escucharlo"`
- `Intent.CALM_DOWN`: `"no armemos la horca antes del juicio, escuchemos a $spokenTarget"`
- `Intent.ADMIT_DOUBT`: `"capaz estoy viendo brujas donde no hay"`

En `eventReactionLine` MUERTE_NOCTURNA (**:1404-1434**), sumar 1 variante por personalidad, p. ej.: PICANTE `"a $target lo callaron de noche, de dia nadie se anima"`; JODON `"$target ya esta criando malvas, los raros siguen aca"`.

**Auto-acusación / repetición:** el guard `isSelfAccusatoryLine` + `neutralSelfAccusationFallback` cubre el caso; no se encontraron caminos que lo esquiven. La repetición está bien mitigada (`recentLines`, `dedupeBotMessages`, rotación por seed). Único punto: `finishSpeech` (:2975) pasa todo a `lowercase()`, así que **los nombres de jugadores aparecen en minúscula en el chat de bots** — coherente con el estilo "chat informal"; si molesta, la solución barata es re-capitalizar los nombres después del lowercase.

---

## A.3 — Bug-hunt de texto

### Mojibake (encontrado 1)
- **`GameplayMockActivity.kt:6353`**: `"Â¿Quieres cambiar de bando?"` → `"¿Quieres cambiar de bando?"`. Es el único mojibake en `app/src/main` (verificado con grep de `Ã|Â|�`).

### ñ perdida en texto visible
El copy evita tildes de forma bastante consistente (estándar de facto), pero la **ñ** quedó a mitad de camino: algunos strings la tienen (`engaño` GameEngine:1548, `dueños` LocalBotAi:1445, `BUFÓN`/`Consiguió` GameplayMockActivity:5911-5913, `DÍA`/`votación` GameplayTableUi:146/535-537) y otros la perdieron:
- `RoleCatalog.kt:221` y `:334` `"polis pequena"`, `:337` `"hizo pequena"` → `"pequeña"`.
- `RoleCatalog.kt:329` `"peleado por senores"` → `"señores"`.
- `GameplayMockActivity.kt:4043` `"vuelve a discutir manana"` → `"mañana"`.
- `LocalBotAi.kt:1077/1114` (ya listados en A.2).
- **Cluster SEÑALAR** (cambiar todo junto o nada): `GameEngine.kt:529/548/1064/1118`, `GameplayTableUi.kt:173/190/256/257/436`, `GameplayMockActivity.kt:2339/3320/3335/4094/4959/5303`, `VoteResultAnimator.kt:487`, `RoleCatalog.kt:93`. ⚠ `"SENALAR"` se usa como **valor comparado** (`GameplayTableUi.kt:190` hace `normalized == "SENALAR"`); cambiar solo el display rompe el tono del botón. Tratarlo como cambio coordinado único (los 14 sitios en un commit) o dejarlo para el "cambio mayor" de acentuación.

**Recomendación de estándar:** para este ciclo, mantener "sin tildes" en los strings que parsean regex (anuncios del motor) y arreglar solo las ñ (que no participan de ningún parser, salvo el cluster SENALAR). La migración completa a ortografía correcta es cambio mayor (ver A.5).

### Terminología
- **Detective vs Comisario:** en pampa el rol se muestra "Comisario" (`RoleCatalog.kt:301/316`), pero el hint del Espía dice siempre `"el Detective te ve como inocente"` (`GameEngine.kt:960`) y la descripción `"El Detective te vera como inocente"` (`RoleCatalog.kt:153`). Fix barato: `"el investigador te ve como inocente"` o resolver el nombre por mapa.
- **Traidor vs Asesino:** consistente a nivel jugador (equipo "Traidores", rol "Asesino/Asesina"); `GameModels.kt:343` mantiene un check legacy `team == "Asesino"` — no visible, solo deuda.
- **Voseo vs tuteo mezclados en la voz del sistema** (los bots son voseo puro y está bien):
  - Voseo: `GameEngine.kt:982` `"Podes hablar"`, `:984` `"No podes hablar ni votar"`, `GameplayMockActivity.kt:1229` `"No podes actuar"`, `:4959/5303` `"Elegi…"`.
  - Tuteo: `GameEngine.kt:863` `"Puedes revelarte"`, `:291-293` `"Te protegiste… se anunciara"`, `GameplayMockActivity.kt:6353-6357` `"¿Quieres cambiar…? / puedes"`, `RoleCatalog` (todo tuteo).
  - Propuesta: **sistema/cronista en tuteo neutro** (es lo mayoritario) y bots en voseo. Normalizar las 4-5 líneas voseadas del sistema: `"No podes actuar sobre ese jugador"` → `"No puedes actuar sobre ese jugador"`, `"Podes hablar, pero no votar"` → `"Puedes hablar, pero no votar"`, `"Elegi…"` → `"Elige…"`, etc.

### Strings con riesgo de recorte en pantallas chicas
- `privateRoleHint` (`GameEngine.kt:956-988`) concatena rol+equipo+estado+hasta 4 hints extra; con Espía+silenciado supera fácil los 120 caracteres. Igual `assassinVotePrivateHint` (:168-182) lista todos los votos asesinos. Verificar que el contenedor del hint tenga `maxLines`+`ellipsize` o scroll; si no, acortar: el resumen de votos puede ser solo `"Victima elegida: X (2 votos)"`.
- `"Se abre un Contrapunto entre ${A} y ${B}. La conversacion queda restringida hasta que termine."` (:521-522) — con dos nombres largos ronda 100+ caracteres en banner landscape; alternativa: `"Contrapunto: $A contra $B. Solo ellos y el Payador hablan."`

---

## A.4 — Chequeo general del gameplay local

1. **Muertes pintadas como "amanecer" en la crónica** — `ChronicleFeedPresenter.kt:79-81`: `"nadie murio" in lower || "amanec" in lower → DAWN` se evalúa **antes** que `"murio" → DEATH`, y el mensaje real es `"Amanecer: murio X."` → toda muerte nocturna queda con kind/tono `DAWN`, nunca `DEATH`. Fix: evaluar muerte antes: `"murio" in lower && "no murio" !in lower -> DEATH`, y dejar DAWN después. (Análogo: `SILENCE` casi nunca matchea porque el texto de silencio viene embebido en el mensaje de amanecer y no contiene `"silenci"`; menor, pero si se quiere el tono, agregar `"no puede hablar" in lower -> SILENCE` como oración separada.)
2. **Muertes de rondas ≥2 mal atribuidas en el resumen final** — `GameplayTableUi.roundOutcomes` (613-640) trackea la ronda con `"Noche N"`/`"Dia N"`, pero `nextNightMessage` (`GameEngine.kt:1558`) no lleva número: el `"Amanecer: murio X."` de la noche 2 se procesa con `currentRound` todavía en 1 → `daySummaries`/`keyMoments` cargan la muerte al día anterior. El fix es el mismo de A.1.5 (poner `"Noche ${round+1}:"` en `nextNightMessage`), que además arregla la dedupe del feed online.
3. **Acoplamiento texto↔lógica (deuda técnica central):** bots, crónica y resumen final parsean el copy en español con regex/`contains`. El bug de `"expulso a"` (A.2) y los dos de arriba son síntomas del mismo problema: cualquier edición de copy puede romper comportamiento en silencio. Mitigación barata para este ciclo: **extraer los marcadores a constantes compartidas** (p. ej. `GameplayTextMarkers`) usadas por productor y parsers. Solución real (cambio mayor): que `GameSession` lleve eventos estructurados y el texto se genere solo para render.
4. **Monolitos:** `GameplayMockActivity.kt` = **6.074 líneas**, `LobbyActivity.kt` = 2.463. El patrón de extracción a `*Controller`/`*Animator`/`GameplayTableUi` ya existe y funciona; seguir por ahí (candidatos: diálogo del Desertor + diálogos programáticos, bloque online-authoritative). Fuera de alcance ahora; solo no sumarle responsabilidades nuevas.
5. **Flujo de fases y feedback:** el motor está prolijo (guards + `copy()`, `enterUnifiedNight` con tope de 8 iteraciones, AFK con aviso previo claro en `GameEngine.kt:1409-1413`). Los `targetActionLabel` (MATAR/SILENCIAR/INVESTIGAR/SALVAR/…) son claros. No se encontraron rutas rotas de fase en la lectura.
6. **Toasts genéricos duplicados:** `"Espera a que comience la fase."` aparece 2 veces (`GameplayMockActivity.kt:1156/1222`) — candidato natural a `strings.xml` cuando se toque esa pantalla (convención de CLAUDE.md).

---

## A.5 — Propuestas priorizadas

### Quick wins (bajo riesgo, alto impacto)
1. **Marcador `"expulso a"` → `"fue expulsado"`** en `LocalBotAi.kt:921` y `:2873-2875`. Revive reacciones a expulsiones + 3 comportamientos de debate. 2 líneas.
2. **`nextNightMessage` con número de noche** (`GameEngine.kt:1558-1564`). Arregla atribución del resumen final + dedupe online + repetición literal. 3 líneas.
3. **Orden de clasificación en `ChronicleFeedPresenter.kt:79-81`** (muerte antes que amanecer). Las muertes recuperan su tono visual.
4. **Mojibake `GameplayMockActivity.kt:6353`** + ortografía visible: `acompanio`, `maniana`, `safan` (LocalBotAi 1077/1114/1480), `pequena`/`senores` (RoleCatalog 221/329/334/337), `manana` (GameplayMockActivity 4043).
5. **`"Muteados:"` → `"Silenciados:"`** (`GameEngine.kt:1554` **junto con** `GameplayTableUi.kt:371`).
6. **Reescrituras del cronista de A.1** (amanecer, votación, expulsión, "El amanecer se acerca") respetando la regla de oro. Es el mayor salto de calidad narrativa del ciclo.
7. **Anacronismos de bots** (micrófono → boca, clip → tribuna) + variantes medievales nuevas de A.2.
8. **Voseo→tuteo en la voz del sistema** (4-5 líneas listadas en A.3) y `"el Detective"` → `"el investigador"` en el hint del Espía (`GameEngine.kt:960`).

### Cambios mayores (planificar, no meter en este ciclo)
1. **Eventos públicos estructurados** en `GameSession` (o, versión intermedia, constantes compartidas de marcadores) para desacoplar copy de lógica.
2. **Migración ortográfica completa** (tildes + cluster SEÑALAR, 14 sitios coordinados) — recién después del punto 1, o con los parsers normalizando acentos.
3. **Seguir desarmando `GameplayMockActivity`** (diálogos programáticos → layouts XML, bloque online a un coordinador).
4. **Mover copy repetido a `strings.xml`** pantalla por pantalla, a medida que se toquen.
