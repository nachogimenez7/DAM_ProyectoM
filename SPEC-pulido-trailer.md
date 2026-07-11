# SPEC — Pulido para el trailer (performance, online asesinos, decoración)

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). Pasada de pulido antes de grabar el trailer. **Los arreglos del payador (Contrapunto) y de la animación post-patada ya los hizo Claude** (ver "Ya hecho" abajo); este spec cubre los tres puntos restantes.
>
> Estado de decisiones con el usuario:
> - Performance: **spec** (este documento). Es lo primordial.
> - Online asesinos: **documentar para después** (hoy el online tiene 1 solo asesino; la votación recién aplica cuando el preset online sume más killers).
> - Decoración del chat: **spec** + necesita un asset de marco fino (lo provee el usuario/diseñador).

## Ya hecho por Claude (no rehacer)
- **Bug del payador**: `canParticipateInChat` y `canHumanChat` para `CONTRAPUNTO` (`GameEngine.kt:1244` y `:1210`) ahora habilitan a hablar **solo** a `session.contrapuntoPlayers` (se quitó el payador). Durante el Contrapunto hablan únicamente los dos señalados.
- **Animación post-patada**: en `VoteResultAnimator.kt`, el vuelo de la carta (`launchExpelledCard`) pasó de `EXPULSION_LAUNCH_MS = 720L` a `1_050L` y el interpolador de `AccelerateInterpolator(1.5f)` a `1.15f`, para un arco de salida más fluido.

---

## 1. Performance: el gameplay se traba y "muestra todo de golpe"

### Diagnóstico
El síntoma (freeze de 2-3 s y después vuelca todo junto) es bloqueo del hilo principal por **generación de diálogo de bots sincrónica**, agravada por el overhaul de memoria de la IA (que entró en el mismo batch que los perfiles — de ahí la correlación con "desde que metimos perfiles"; los perfiles en sí se generan una sola vez en `PlayerProfileStore.withProfiles`, `PlayerProfile.kt:89`, no son el freeze recurrente).

Causa concreta: `LocalBotAi.nextConversationLine(session, speaker)` (`LocalBotAi.kt:636`) genera los mensajes de **todos** los bots (llama a `openingDebateMessages`/`votingIntentMessages` con `limit = cantidad de bots`) para devolver **la línea de uno solo**. Y el director lo invoca **por cada beat** y, dentro de un beat, **por cada candidato a hablar** (`BotConversationDirector.chooseSpeakers` devuelve una lista y `nextIdleBeat` prueba `lineForSpeaker` uno por uno hasta obtener no-nulo, `BotConversationDirector.kt:54-57`). Cada una de esas llamadas recalcula la tabla entera de sospechas, que **ahora lee todo el `claimLedger` y la `TableMemory` de todas las rondas**. Es trabajo O(candidatos × bots × costo-por-mensaje) repetido para el mismo estado.

### Arreglo (memoización por estado de conversación)
Cachear el batch generado de la fase y servir cada línea desde ahí, invalidando cuando cambia el estado del chat:
- En `LocalBotAi`, agregar un cache de una entrada: `(claveEstado) -> List<Pair<String,String>>` para el batch de la fase (`openingDebateMessages` o `votingIntentMessages` según fase).
- Clave de estado estable que capture "esta foto de la conversación": p. ej. `"${session.phaseIndex}:${session.phase}:${socialChatSize(session)}"` (o incluir el hash del último mensaje). Mientras la clave no cambia, `nextConversationLine` para cualquier speaker reusa el batch cacheado en vez de regenerarlo.
- Efecto: dentro de un mismo beat, probar varios candidatos son **cache hits** (1 generación en vez de N). Cuando se commitea un mensaje nuevo, `socialChatSize` cambia → la clave cambia → se regenera **una** vez. Se preserva la frescura (cada línea sigue viéndose contra el estado actual) pero se elimina la regeneración redundante.
- Invalidar/limpiar el cache al cambiar de fase y en `winner` (para no servir líneas viejas).

### Optimización secundaria (opcional)
`session.playerProfiles` (14 perfiles) viaja dentro de `GameSession`, que es `Serializable` y se guarda en `onSaveInstanceState`. Como los perfiles son **deterministas** (`BotProfileFactory` es estable por nombre; el humano sale de prefs), no hace falta serializarlos: se pueden re-derivar en el restore con `PlayerProfileStore.withProfiles`. Excluirlos del Bundle aliviana el guardado/restauración (rotación, segundo plano). Menor que lo de arriba, pero suma para la fluidez percibida.

### Verificación
- Confirmar con el **profiler de Android Studio** o mirando en Logcat los `Choreographer: Skipped N frames` / `Davey! duration=…` durante el debate y la votación. Antes del arreglo deberían aparecer picos al caer los mensajes de bots; después, no.
- Jugar una partida completa: el debate y la votación deben caer fluidos, sin el "tilt y volcado" de 2-3 s.

---

## 2. Online: cómo deciden los asesinos (DISEÑO — no implementar aún)

**Por qué "no aún":** el preset online reparte **1 solo asesino** (`docs/firebase-online-schema.md:128`: 1 asesino, 1 médico, 1 detective, resto aldeanos). Con un solo killer no hay votación: elige y listo. Este diseño se activa **cuando el preset online sume más killers** (asesino+espía, o 2 asesinos, como el preset local RECOMMENDED en `GameModels.kt:601`). Dejarlo documentado para engancharlo entonces.

### Flujo objetivo (con 2+ killers)
1. **Cada killer elige su víctima** con la acción nocturna normal (tocar carta → MATAR); esa acción ya se registra en `partidas/{sala}/acciones` en online.
2. **Reflejo en el chat de asesinos**: por cada elección, una línea de sistema en el canal `TRAIDORES`: *"Nacho eligió a Mora como su víctima"*, *"Thiago eligió a Fede como su víctima"*, *"Valen eligió a Mora como su víctima"*.
3. **Resolución al cerrarse la noche** (el timer completo, como ya espera el online): la víctima es la **más elegida** entre los killers. Línea final: *"Decisión final: Mora será la elegida para morir esta noche"*.
4. **Empate** entre las más elegidas → **sorteo** entre las empatadas (semilla estable por ronda para reproducibilidad): *"Hubo empate; la suerte eligió a Mora"*.
5. **Nadie eligió** → **no muere nadie** esa noche.
6. Solo cuentan **killers** (asesino, espía); el **mercenario** (silencia) no vota la víctima.

### Quién publica (autoridad)
El **host activo** ya es quien resuelve la noche y publica `estadoPartida`. Es él quien debe:
- Leer las acciones de kill de los killers (de `acciones`, filtradas por ronda/fase).
- Publicar las líneas de reflejo + la decisión final en `partidas/{sala}/chat_traidores` (como mensajes `isGod` de sistema, autor `"Plan"`), **antes** de aplicar la muerte y del amanecer público.
- Aplicar la muerte resultante en `estadoPartida`.

Esto mantiene una sola fuente de verdad (evita que cada cliente calcule un resultado distinto). Encaja con la subcolección `chat_traidores` ya creada en la Fase D.

### Dependencia
Requiere habilitar >1 killer en el preset online (`onlineSafeRoleComposition`, `GameModels.kt:628`). Mientras el preset tenga 1 asesino, implementar esto no cambia nada visible: con 1 killer, el paso 2 muestra una sola línea y el 3 la confirma.

---

## 3. Decoración del chat con marcos

### Objetivo
Enmarcar el chat para que se sienta más "de época". Aplica al **chat de asesinos** (marco rojo/sangre) y al **chat del centro del mapa** (marco dorado), en sus dos formas: colapsada (feed ambiente) y expandida (panel).

### Cableado
Reusar el sistema de **marcos por mapa** que ya existe en las ventanas de evento (`applyRevealOverlayTheme`). En `GameplayChatController`:
- `renderChatBackgrounds` (`GameplayChatController.kt:1779`) ya elige fondo por canal; agregar detrás del panel y del feed un `ImageView`/`background` con el drawable de marco correspondiente (dorado para `PUBLICO`, rojo para `TRAIDORES`).
- El canal traidor ya dibuja un borde rojo redondeado programático (`renderTraitorChatBackgrounds`, `:1812`); el marco ornamental va **encima/en lugar** de ese borde simple.

### El asset fino (lo provee el usuario/diseñador)
El marco de las ventanas de evento es **ancho y ornamentado**; en el chat chico taparía contenido. Hace falta una variante **fina**, y **dos proporciones** distintas:
- **Feed colapsado** (ambiente): marco fino para un recuadro chico.
- **Panel expandido**: marco fino para un recuadro grande (relación de aspecto distinta).
- Y la **variante roja** de ambos para el canal de asesinos.

Consideración técnica para quien haga el arte: los marcos que se estiran deforman las esquinas. Entregar como **9-patch** (`.9.png`, esquinas fijas + bordes estirables) o como piezas separadas (4 esquinas + 4 lados), para que escalen sin romperse. Si el asset todavía no está, se puede dejar el borde programático actual como provisorio y enchufar el marco cuando llegue.

### Verificación
- Chat del centro (colapsado y abierto): marco dorado fino, sin tapar texto ni romper esquinas.
- Chat de asesinos (colapsado y abierto): marco rojo fino, coherente con la piel sangrienta.
- Fuentes grandes / pantallas chicas: el marco no recorta el contenido (respeta el padding interno).

## Orden de entrega
1. **Performance** (memoización) — primordial, es lo que más se nota en el trailer.
2. **Decoración** — cuando esté el asset del marco fino (cableado listo antes).
3. **Online asesinos** — cuando el preset online sume más killers.
