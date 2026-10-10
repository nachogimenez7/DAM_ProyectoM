# Encargo para Codex: que la mesa V3 se vea como el online común

Fecha: 8/10/2026. Lo redactó Claude a pedido del usuario. Prioridad máxima, junto
con la activación (`BRIEF_CODEX_ACTIVACION_BETA_V3.md`). Las dos tareas tocan
archivos distintos y pueden avanzar en paralelo.

## Por qué existe este encargo

El usuario vio las pruebas y la mesa V3 **no se ve como el online común**. Es la
**segunda vez**: la presentación del 7/10 ya había sido rechazada en el A56
(`ENTREGA_MESA_ANDROID_V3_2026-10-07.md:11`). Las animaciones nuevas están bien,
pero la mesa en sí sigue siendo otra.

**Causa raíz.** `ServerGameTableRenderer` reutiliza el XML, pero **reimplementa** la
mesa: roster, chat, HUD y panel inferior. El online común los arma con su propio
código (`GameplayMockActivity` + `GameplayChatController`). Una reimplementación
termina pareciéndose, pero no es igual, y cada parche agrega otra diferencia.
**Cambio de estrategia:** dejar de reimplementar. Hacer que V3 use las mismas
funciones que dibujan la mesa común, alimentadas con datos de la proyección.

## Diferencias comprobadas (capturas y código)

Referencias: `docs/evidence/2026-10-05/android-ios-parity/mayor.png` (mesa común) y
`output/server-v3-a56-chat.png`, `server-v3-table-recount.png` y
`server-v3-table-real-*.png` (V3).

1. **Falta el centro de la mesa.** La mesa común tiene, siempre visible en la columna
   central, el panel «CHAT DEL PUEBLO»:
   - recuadro de ronda («RECUENTO · DÍA 2 / El pueblo espera el resultado»);
   - feed con crónica y mensajes;
   - fila de entrada («Solo lectura» o la invitación a escribir).

   Son las vistas `chatAmbientFeed`, `chatAmbientRoundCard`, `chatAmbientTitle` y
   siguientes de `gameplay_table_section.xml:320+`, que maneja
   `GameplayChatController.renderAmbientChatFeed()` (`:1113`).
   **V3 no toca ninguna de esas vistas:** deja el centro vacío (se ve el paisaje), pone
   una barra «EVENTOS» y abre el chat como una hoja inferior de 360 dp que tapa las
   cartas y el panel propio. *Es la diferencia más visible.*
2. **Mensajes de chat.** La mesa común usa `renderChatMessages` (`:2100`): estilos,
   colores por jugador, pestañas de canal (`renderChannelTabs`), respuestas rápidas,
   avisos de no leídos e insignia. V3 dibuja `TextView` planos «Nombre: texto» y oculta
   `chatMetaRow` y `chatChannelTabs`.
3. **Roster.** La mesa común usa `renderPlayerColumns` (`GameplayMockActivity.kt:7398`),
   `applyAdaptiveGameplayLayout` (`:7438`), `createSidePlayerCard` (`:7617`) y
   `animatePlayerDeath` (`:7580`). V3 tiene su propio `Card` (`renderRoster`), sin
   animación de muerte en la carta y con nombres de poco contraste («QA1» casi
   invisible en `a56-chat`).
4. **Encabezado.** En la mesa común aparece el botón de emotes (máscara) y el título de
   fase sin «· N». V3 cambia los emotes por un botón de chat, agrega el número de
   ronda al título y oculta `phaseProgressTrack`.
5. **Panel inferior.** En la mesa común: «VER CARTA» / «CONTINUAR» / «CARTA OCULTA».
   En V3: «VER ROL» / «ESPERANDO…», y `bind()` fuerza ancho y alto propios
   (`ServerGameTableRenderer` cambia `layoutParams` de `bottomPlayerPanel`).
6. **Evidencia inútil.** `output/server-v3-table-current.png` es una captura de la
   pantalla de inicio de Android, no de la mesa.

## Qué hacer, en orden

### Paso 0 — Comparación lado a lado (antes de tocar código, unas 2 h)

En el **A56**, con el mismo mapa (Pampa), la misma cantidad de jugadores (8) y la
misma fase, capturar la **mesa común online** (o la local si el online común no se
puede levantar) y la **mesa V3**. Fases: noche, amanecer, debate con chat cerrado,
debate con chat abierto, votación, recuento y resultado. Guardar los pares como
`output/paridad-visual/<fase>-comun.png` y `<fase>-v3.png`, más una lista de
diferencias por par. **Esa comparación es el criterio de aceptación**, no la
descripción de las capturas.

### Paso 1 — Centro de la mesa y chat: reutilizar `GameplayChatController`

`GameplayChatController` ya depende de una interfaz (`ChatHost`), pero
**arranca sus propios listeners del online anterior** (`startOnlineChatListener`,
`:3332`, y los de traidores y espectadores). Hay que agregar una costura mínima:

- Una interfaz `ChatDataSource` (o similar) con: mensajes por canal, envío y canales
  permitidos. En el online común sigue la implementación actual. En V3, la
  implementación delega en `ServerGameChat` (RTDB V3, permisos de la proyección).
- En modo V3 el controlador **no arranca** sus listeners anteriores ni lee la sala
  anterior. Tampoco agrega listeners: usa los del chat V3 que ya existen.
- `ChatHost.currentSession` = un `GameSession` **de solo lectura**, construido desde
  la proyección. Hoy `ServerGameTableRenderer` ya arma `identity.copy(players=…)`;
  extraer esa conversión a un adaptador único (`ServerGameSessionAdapter`) y usar
  el mismo en roster, chat, recuento y animadores.
  **Nunca** se llama a `GameEngine` para avanzar ni resolver nada.
- Reacciones y emotes siguen deshabilitados (requieren protocolo V3). La costura
  no debe dejar el botón visible si no funciona.
- Eliminar la barra «EVENTOS» y la hoja inferior propia si el feed central ya muestra
  la crónica de la ronda (`ChronicleFeedPresenter`), como en la mesa común.

Riesgo: el controlador tiene unas 4.500 líneas. Si la costura se complica, la
alternativa aceptable es **llamar desde V3 a las funciones de dibujo existentes**
(`renderAmbientChatFeed`, `renderChatMessages`, `renderChannelTabs`) separándolas
de la fuente de datos. **No** se acepta un tercer chat reimplementado.

### Paso 2 — Roster y panel inferior: mismas funciones

Extraer `renderPlayerColumns` / `createSidePlayerCard` / `applyAdaptiveGameplayLayout` /
`animatePlayerDeath` a un componente compartido que reciba `List<GamePlayer>` y los
flags de presentación, y usarlo desde las dos mesas. Quitar las anulaciones de
tamaño de `bottomPlayerPanel` en `bind()` y usar los textos de la mesa común.
La retención contra anticipos (§3.6 de la revisión) se aplica sobre el
`GameSession` adaptado, antes de entregarlo al componente.

### Paso 3 — Encabezado

Título de fase, cuenta regresiva y barra de progreso como en la mesa común. El botón
de emotes queda oculto mientras no exista protocolo V3; no reemplazarlo por otro icono.

## Reglas que no cambian

- Autoridad del servidor: nada de `GameEngine` como árbitro, nada de pausar el reloj.
- Sin listeners, lecturas ni callables nuevos. El chat V3 sigue con sus permisos.
- Sin arte nuevo ni rediseño: el objetivo es **igualar** la mesa común.
- La mesa común (local y online anterior) no debe cambiar su aspecto. Correr sus
  pruebas y comparar una captura antes y después.
- No tocar `ios/` ni `sources/`.

## Criterio de aceptación

1. Los pares de captura del Paso 0, rehechos después de los cambios, no muestran
   diferencias de estructura: centro, chat, roster, encabezado y panel inferior.
   Solo se aceptan las diferencias justificadas por V3 (sin emotes, sin votantes
   individuales), anotadas.
2. Una ronda completa grabada en el A56 con plazos reales.
3. **El usuario la aprueba en el A56.** Sin esa aprobación, la beta no se activa
   (es la condición de entrada de `BRIEF_CODEX_ACTIVACION_BETA_V3.md`).

## Ajuste pendiente de la entrega anterior

Incluir el ajuste del umbral FULL de la expulsión: elegir FULL cuando el tiempo
restante cubra hasta el vuelo de la carta (~5,7 s con revelación + 500 ms), dejar
que el plazo corte la lectura final y no redibujar en STATIC después del impacto.
Registrar en el log de debug el modo y el tiempo restante.
