# Encargo para Codex: últimos ajustes de mesa, chat en votación y emotes V3

Fecha: 8/10/2026. Lo redactó Claude después de revisar la entrega «Mesa habitual
Android conectada a V3» (código y los siete pares de `output/paridad-visual/despues/`).

## Resultado de la revisión de la entrega anterior

**Aprobada en lo estructural.** Comparadas lado a lado, la mesa común y la V3 ahora
son la misma mesa: centro «CHAT DEL PUEBLO», recuadro de ronda, crónica, chat
ampliado con pestañas y respuestas rápidas, cartas y nombres con colores, y panel
inferior. Los tres puntos que pidió revisar están bien:

1. `ServerGameSessionAdapter` arma un `GameSession` nuevo desde la proyección y no
   copia roles, votos ni objetivos del lobby. Los roles salen de
   `ServerGameTablePresentation.player`, es decir, lo público más lo que ese jugador
   ya puede ver. No resuelve nada.
2. La costura del chat (`ChatDataSource`) corta los cuatro listeners del protocolo
   anterior (`:3203`, `:3370`, `:3431`, `:3520`), el envío de reacciones (`:522`) y el
   arranque (`:394`, `:412`). El envío pasa por `dataSource.send`. Los canales y el
   permiso de escritura salen de `allowedChannels`/`canSend`, que respetan la
   proyección.
3. Las dos mesas usan `GameplayPlayerColumns` y el mismo controlador de chat.

Nota menor: `revealRolesOnDeath` se deduce con
`any { !alive && publicRoleKey != null }` (`ServerGameSessionAdapter.kt`). Con roles
ocultos al morir, un Alcalde revelado y muerto lo vuelve `true`. Mejor tomarlo de
la configuración de la sala (`identity.roleRevealConfig` o el campo equivalente).

## Decisiones del usuario (8/10, nuevas)

- **Chat público durante la votación, como en el online común.** Cambia permisos
  en el servidor.
- **Emotes en la beta del sábado.** Requiere el protocolo de reacciones V3.

## A. Diferencias visibles que quedan (comparación lado a lado)

1. **Subtítulo del encabezado.** La mesa común muestra el objetivo de la fase
   («Objetivo: Elige a quién proteger y confirma PROTEGER.», «Objetivo: Tocá una carta
   para votar…», «El pueblo cuenta los votos recibidos.»). V3 muestra el último
   `anuncioPublico`, que queda viejo: «Amanecer: esta vez nadie murió.» aparece en
   Noche, Votación y Recuento. Hay que usar la misma lógica de la mesa común
   (`GameplayMockActivity.kt:9756` → `GameplayPhasePresentation.phaseAdvice(session)`
   y `GameplayTableUi.centralPhaseMessage`) sobre el `GameSession` adaptado. Los
   anuncios siguen yendo a la crónica, como hoy.
2. **Panel inferior: textos y estados.**
   - Votación: la mesa común muestra «SELECCIONÁ A UN JUGADOR» deshabilitado hasta
     elegir carta. V3 muestra «VOTAR» dorado y activo antes de elegir.
   - Noche: la mesa común dice «SALVARME» y las cartas «SALVAR». V3 dice «PROTEGER»
     en los dos.
   - Debate: la mesa común muestra «VOTAR ANTES EN 7 · 0/8» deshabilitado. V3 muestra
     «CONTINUAR», que **parece tocable y no hace nada**. Eso no puede quedar: usar la
     etiqueta de espera de la mesa común con el mismo estilo deshabilitado.
   - Pista: «Tocá la carta del objetivo.» frente a los textos contextuales de la mesa
     común («Alguien intenta proteger a un jugador.», etc.).

   Tomar etiquetas, estados y pistas de las mismas funciones que usa la mesa común.
   Los nombres de acción de `ServerGameActionPolicy` quedan solo como identificadores.
3. **Franja superior.** En todas las capturas V3 hay una banda de cielo con un corte
   recto bajo la barra de estado. En Recuento y Resultado, esa banda queda **sin
   oscurecer** mientras el resto de la pantalla está tapado por el overlay. La mesa
   común dibuja de borde a borde y el overlay cubre la barra de estado. Igualar
   insets/edge-to-edge de `ServerGameplayActivity` con `GameplayMockActivity`.
4. **«2 MENSAJES NUEVOS» al abrir el chat V3**, aunque esos mensajes ya se veían en
   el centro. La mesa común no lo muestra. Marcar como leídos los mensajes visibles
   en el feed central, igual que la mesa común.
5. **Subtítulo del recuento.** V3 pasa `anuncioPublico` (en el fixture, el texto del
   amanecer). Usar el mensaje de la mesa común o el del evento de votación.
6. **El par `resultado` no sirve.** La captura común quedó en medio de la transición
   «NOCHE 3». Hay que repetirla en el mismo momento que la V3. Hacer también un par
   con expulsión y otro con victoria.

Criterio: rehacer los siete pares (más los nuevos de resultado) y que las únicas
diferencias que queden sean el contador del fixture y las propias de V3, anotadas.

## B. Chat público durante la votación (servidor)

- En `projectServerGame`, `publicChat` pasa a ser verdadero también en `VOTACION` y
  `DESEMPATE_VOTACION` para jugadores vivos y no silenciados. La voz del Oráculo no
  aplica: `beginVote` ya la limpia.
- **Antes de tocarlo, verificar en el controlador común en qué fases se puede
  escribir** (incluidas `CONTRAPUNTO`, `RECUENTO_VOTOS`, `ALCALDE_DESEMPATE` y
  `RESULTADO`) y copiar exactamente esa tabla. Si alguna fase difiere de lo pedido,
  informarla en lugar de inventarla.
- Las reglas RTDB del chat no cambian: ya leen `permissions.publicChat` y el plazo.
- Actualizar `CONTRATO_AUTORIDAD_SERVIDOR_V3.md`, las pruebas del motor y las
  proyecciones regeneradas. Se despliega junto con el bloque pendiente de Functions.
- Consumo: más mensajes por partida. Anotarlo para la medición.

## C. Emotes V3 (protocolo nuevo)

Mismo diseño que el chat V3, que ya está probado: escritura directa en RTDB
validada por reglas contra la proyección. No pasa por callables ni por el motor.

- **Permiso.** Nuevo campo `reactions` en `permissions/{uid}`, calculado por el
  servidor. Las fases tienen que ser exactamente las de la mesa común
  (`GameplayMockActivity.isPublicReactionPhase`, `:6288`: debate, contrapunto,
  votación, recuento, segunda votación y desempate del Alcalde), con las mismas
  condiciones de jugador: vivo, silenciado, etc. Copiarlas de
  `reactionBaseUnavailableMessage` (`:6281`).
- **Nodo.** `onlineV3/{room}/reactions/{uid}_{slot}` con
  `{actorUid, matchId, phaseIndex, slot, emoteId, ts}` y un anillo chico por jugador,
  por ejemplo 8 slots. Hay un nodo de ritmo `reactionRate/{uid}` como `chatRate`.
  Las reglas validan miembro, `reactions === true`, `matchId`/`phaseIndex` actuales,
  plazo vigente, `ts === now`, intervalo mínimo y `emoteId` dentro de una lista
  cerrada. Lectura solo para miembros.
- **Límites.** Los mismos que `GameplayReactionLimiter` (cooldown y máximo por ronda)
  en el cliente. En las reglas, al menos el intervalo mínimo.
- **Catálogo.** En la beta, solo los emotes base. Los emotes de cosméticos/pack no se
  pueden validar como propiedad en las reglas y el pack todavía no es una compra
  real. Dejarlos fuera e informarlo. Incluir el «6 7» solo si es un emote base.
- **Cliente.** Reemplazar el corte de `ChatDataSource` para reacciones por una
  fuente V3: enviar y recibir. Mostrar con `showReactionBubble` y el
  `btnToggleEmotes` de la mesa común; no hacer una UI nueva. Deduplicar por
  `matchId:actorUid:slot:ts`. No reproducir reacciones viejas al reconectar.
- **Listener.** Es un **listener RTDB nuevo** (`limitToLast` chico), activo solo en
  fases de reacción y con la app en primer plano. Medir bytes por partida de
  5/10/15 jugadores e informarlo.
- **Pruebas.** Reglas: emulador RTDB, con escritura permitida y rechazada por fase,
  silencio, muerte, `matchId` viejo, ritmo y `emoteId` inválido. Nativa: dos
  clientes ven la misma burbuja una vez. La reconexión no repite.
- Limpieza de sala y revancha: el nodo de reacciones se borra con el resto de
  `onlineV3/{room}`. Verificar que la revancha empieza vacía.

## Orden y línea de corte

1. A (ajustes visuales) y B (chat en votación): chicos, primero.
2. Lo pendiente de `BRIEF_CODEX_ACTIVACION_BETA_V3.md` (despliegue, build de Play,
   clientes viejos, apagado).
3. C (emotes).

**Línea de corte:** si el viernes a la noche los emotes no pasan las pruebas de
reglas y la prueba de dos clientes, **avisar al usuario** con el estado real. Él
decide entre salir sin emotes o mover la beta. No ocultar el botón por cuenta
propia ni publicarlos a medio probar.

## Fuera de este encargo

Sin arte nuevo, sin cambios en iOS ni `sources/`, sin desactivar App Check, sin
commit/push sin pedido del usuario. La aprobación final en el A56 sigue siendo del
usuario.
