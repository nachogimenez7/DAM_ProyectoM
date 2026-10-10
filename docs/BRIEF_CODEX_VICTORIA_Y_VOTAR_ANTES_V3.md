# Encargo para Codex: pantalla de victoria y «VOTAR ANTES» en V3

Fecha: 8/10/2026. Lo redactó Claude después de revisar la entrega «Mesa habitual,
chat y emotes V3» (permisos, reglas RTDB y los nueve pares de
`output/paridad-visual/ajustes/`).

## Resultado de la revisión

**Aprobado:**
- **Chat público.** Coincide fase por fase con el común (`GameEngine.canHumanChat`,
  `GameEngine.kt:1315`): debate, votación y segunda votación; en Contrapunto, solo
  sus dos participantes; nunca en el desempate del Alcalde. El invitado del Oráculo
  habla solo en el debate.
- **Reglas de emotes.** Validadas:
  - la reacción y su `reactionRate` deben escribirse juntas;
  - no se pueden borrar ni pisar las ajenas;
  - 10 s entre envíos y 2 por ronda, igual que `GameplayReactionLimiter`;
  - catálogo cerrado;
  - limpieza en la revancha (`onlineGameService.js:339`).
- **Silenciados sin emotes en ambos modos:** el usuario confirmó que lo pidió.
- **Pares visuales.** Noche, amanecer, debate, chat ampliado, votación, recuento,
  resultado y expulsión quedaron prácticamente iguales a la mesa común.

**Nota menor, sin cambio obligatorio:** las reglas aceptan emotes de cualquier mapa en
cualquier sala. Es solo cosmético; se puede ajustar después.

## 1. Pantalla de victoria (pendiente visible)

Es la única pantalla que sigue siendo propia de V3. Ver el par `victoria-*`:

| Mesa común | V3 actual |
|---|---|
| «VICTORIA DEL PUEBLO» / «La plaza vuelve a respirar.» | «GANÓ PUEBLO» / «VICTORIA» |
| Panel más bajo, con el título de la partida visible arriba | Panel alto, casi de pantalla completa |
| «VER CRÓNICA» / «VOLVER AL LOBBY» | «ANUNCIOS DE LA RONDA» / «SALIR DE LA SALA» |

- Usar `GameplayTableUi.winnerPresentation(session)` (lo que usa
  `GameplayMockActivity.kt:10860`) sobre el `GameSession` adaptado: títulos, frase y
  resultado personal. Medidas y orden del panel, iguales a la mesa común.
- La semántica de los botones de V3 se mantiene: el creador prepara la revancha y
  los demás salen. Los textos se toman de la mesa común cuando la acción es la
  misma. El botón de crónica abre los anuncios disponibles con el nombre que usa la
  mesa común.
- Repetir el par `victoria`, más uno de derrota y uno de partida cancelada.

## 2. «VOTAR ANTES» (decisión del usuario: entra en la beta)

### Comportamiento de la mesa común (a igualar)

Fuente: `GameplayMockActivity.renderReadyToVoteButton` (`:6459`),
`eligibleReadyVoters` (`:6551`), `OnlineVoteReadyGate` y
`READY_VOTE_MINIMUM_DEBATE_MS`.

- Solo en `DIA_DEBATE`, para jugadores **vivos**. Los silenciados también cuentan.
  El invitado del Oráculo, que está muerto, no cuenta.
- Se habilita después de **10 s** de debate.
- Botón: «VOTAR ANTES EN N · x/y» (deshabilitado hasta los 10 s),
  «LISTOS PARA VOTAR · x/y», «CANCELAR · x/y» si ya marcaste. Colores y pulso
  propios (`readyToVoteBackground`).
- Cada jugador marca o cancela. Cuando **todos** los vivos marcaron, se abre la
  votación enseguida.

### Diseño V3 (servidor arbitra, sin campos nuevos en las acciones)

- **Acciones nuevas:** `listo_votar` y `cancelar_listo` en `ACTIONS`, sin `targetUid`
  ni `team`. No hace falta tocar `ACTION_FIELDS`.
- **Validación:**
  - `phaseIs(DIA_DEBATE)`;
  - actor vivo;
  - `nowMs >= phaseStartedAtMs + 10000` (el reloj es del servidor, no del teléfono).
- **Estado:** el slot `${order}:listo_votar` de `state.actions`. `cancelar_listo` lo
  borra. Se limpia solo al cambiar de fase, porque `transition` vacía las acciones.
  El tope `MAX_ACTIONS_PER_PHASE` (12) frena el spam de marcar y desmarcar.
- **Cierre anticipado:** si todos los vivos tienen el slot, `beginVote(state, nowMs, false)`
  dentro de la misma acción, como hace hoy `contrapunto`. Si un abandono deja a todos
  los restantes listos, `leaveServerGame` también tiene que abrir la votación.
- **Proyección pública:** solo agregados. `listosVotar: {listos, total}` en `DIA_DEBATE`;
  `null` en las demás fases. **No publicar quién marcó.** El común en V3 muestra solo
  «x/y» y el estado propio sale de `accionesConfirmadas` privadas.
  Como el contador es público, cada marca y cada cancelación incrementa
  `publicRevision`, así que cada toque provoca una publicación a todos.
- **Medición obligatoria:** son publicaciones nuevas, del orden de 1 a 2 por jugador y
  por debate (unas 50 a 100 por partida de 10 jugadores y 5 rondas). Medirlas e
  informarlas junto con chat y emotes.
- **Cliente:**
  - `ServerGameActionPolicy` ofrece la acción correspondiente;
  - el botón principal reutiliza el dibujo de la mesa común (textos, colores,
    deshabilitado hasta los 10 s según `phaseStartedAtMs` del servidor);
  - la confirmación pasa por el `ServerGameActionSender` habitual, con `requestId`
    y reintento.
  - Reemplaza al «ESPERAR» del debate.
- **Interacción con otras acciones del debate:** revelar Alcalde, elegir Contrapunto y
  revisión del Desertor siguen disponibles en el menú habitual. Si el Contrapunto se
  abre, las marcas se pierden con el cambio de fase, igual que en el común.
- **Pruebas:**
  - motor: rechazo antes de 10 s, muerto, fase incorrecta; marcar y cancelar;
    todos listos → `VOTACION`; abandono que completa → `VOTACION`; tope de 12;
  - proyección: sin identidades, solo agregados;
  - contrato: regenerar fixtures;
  - nativa: dos clientes ven «x/y» actualizarse y la votación se abre al completar.
- **Contrato:** documentar acciones, campo público y efecto en `publicRevision` en
  `CONTRATO_AUTORIDAD_SERVIDOR_V3.md`. Requiere un nuevo despliegue de Functions
  con el gate cerrado, como el anterior.

## Orden y línea de corte

1. Pantalla de victoria (chico).
2. «VOTAR ANTES».
3. Seguir con la prueba de Play/A56 según el mensaje del usuario (prueba interna,
   gate solo para su UID y sala, partida real, apagado y APK 0.1.50).

**Línea de corte:** si «VOTAR ANTES» no pasa las pruebas de motor y nativas antes de
la prueba con personas, **avisar al usuario** con el estado real. No desplegarlo a
medias ni ocultarlo por cuenta propia.

Sin arte nuevo, sin cambios en iOS ni `sources/`, sin desactivar App Check, sin
abrir la beta ni hacer commit/push sin pedido explícito del usuario.
