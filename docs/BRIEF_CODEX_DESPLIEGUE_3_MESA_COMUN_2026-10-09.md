# Encargo para Codex: tercer despliegue (mesa común) y práctica en el A56, 9/10/2026

Lo redactó Claude. Responde a la lista de 10 puntos de tu segunda práctica, que el
usuario no aprobó. La sesión de Claude no puede desplegar a producción, así que el
despliegue lo hacés vos. El usuario ya aprobó desplegar y probar.

## Qué cambió

### Servidor (`functions/src/onlineGameCore.js`, sin desplegar)

- **VOTAR ANTES:** dos acciones nuevas, `listo_votar` y `cancelar_listo`. Valen solo en
  `DIA_DEBATE`, para jugadores vivos y a partir de los 10 s de debate
  (`READY_VOTE_MINIMUM_MS`). Antes de ese plazo se rechazan con `ready-too-early`. Cuando
  todos los vivos están listos, la votación se abre en el momento. Si alguien abandona,
  se vuelve a evaluar. La proyección pública agrega `listosVotar: {listos, total,
  desdeEpochMs}` durante el debate.
- **Quién votó a quién:** el estado guarda `voteBallots` (`[{votante, objetivo}]`). La
  proyección los publica como `votosIndividuales` **solo cuando la votación terminó** y
  si la configuración no los ocultó (`votosIndividuales !== false`). El voto sigue
  siendo secreto mientras dura la votación.
- **Recuento más largo** cuando hay sellos que mostrar:
  `max(transitionSeconds, min(12, ceil(2 + 0,45 × sellos)))`, contando los votos, el del
  alcalde y el del contrapunto. Con 7 votos dura unos 6 s, en lugar de 3.
- El núcleo va empaquetado en todas las funciones, así que hay que **desplegar las 8
  V3**: `iniciarPartidaV3`, `accionPartidaV3`, `recuperarFaseV3`, `prepararRevanchaV3`,
  `abandonarPartidaV3`, `publicarPartidaV3`, `resolverFaseV3` y `repararPartidasV3`.
  Conservá las instancias mínimas, la CPU y la concurrencia aprobadas, y el invoker
  privado del worker.

### Android (APK nueva)

**`output/traidores-v3-cloud-practica-claude-3.apk`**: `com.traidores.juego.v3qa`, 0.1.51
(52), Cloud QA, V3. SHA-256
`308fc37d6cfa2ef2f88612ba8f310b41dc43d166084fabd584a367a910b3e1ab`. Usá solo esta APK
con el backend nuevo, porque las anteriores no conocen los campos nuevos.

| # | Punto de tu lista | Qué se hizo |
|---|---|---|
| 1 | «Preparando partida» parpadea al empezar | Se pasa por el reparto del común y después por la mesa. El velo solo muestra el mapa, y el texto aparece únicamente si la espera supera 1,5 s. |
| 2 | No se ven los perfiles | Tocar o mantener presionada la carta de un jugador vivo abre su perfil (`PlayerProfileDialog`). Una carta eliminada abre la ficha del común. El perfil se cierra al cambiar de fase. |
| 3 | Transiciones demasiado rápidas | «AMANECER N» se muestra siempre y no lo corta el debate. Se respeta el tiempo de transición de la configuración, y el recuento dura más cuando hay sellos. |
| 4 | Anuncios como texto suelto | La crónica y los anuncios usan las frases y la decoración del común (noche, muerte al amanecer, expulsión), y hay un cartel central para alcalde revelado y expulsión por AFK. |
| 5 | Falta VOTAR ANTES | El botón del común muestra «VOTAR ANTES EN N · x/y», con cancelación, contador y apertura anticipada desde el servidor. |
| 6 | El chat se cierra al enviar | Verificado en local con jugador vivo y muerto: queda abierto. |
| 7 | Falta «Votaste a: …» | Queda fijo en la ficha propia y en el cartel hasta el cierre, y se mantiene al llegar la confirmación. |
| 8 | Falta el contorno de la carta votada | Mismo estilo que el común: borde dorado de 3 dp y escala 1,055. |
| 9 | No se ve quién votó a quién | Recuento con sellos del común (`VoteResultAnimator`), alimentado por `votosIndividuales`. |
| 10 | «Anuncios» y «Mis investigaciones» confunden | Se quitaron. El engranaje abre el menú del común: accesibilidad, reportar un problema y abandonar. |

## Verificación de Claude

- Backend: 78/78 pruebas unitarias, `npm run check` sin errores, servicio y recuperación
  39/39 en emuladores.
- Android: 742/742 pruebas unitarias.
- Partida local de 8 jugadores en el emulador: reparto → rol → noche → amanecer →
  debate con VOTAR ANTES → votación (voto directo, cambio, «Votaste a», contorno) →
  recuento con sellos → desempate → victoria → revancha. El chat queda abierto al enviar.
  Los sellos coinciden con `voteBallots` del estado.
- Sin verificar todavía: la persistencia de «Votaste a» dentro del **desempate** (en
  local la ventana dura muy poco), el chat de traidores y la confirmación optimista
  revertida por un rechazo real del servidor.

## Pasos

1. Confirmá que el gate y `config/onlineV3` siguen cerrados.
2. Desplegá las 8 Functions V3 con el gate cerrado y verificá: estado ACTIVE,
   instancias mínimas, CPU y concurrencia de `accionPartidaV3`, e invoker del worker.
   Guardá la evidencia en `output/v3-cloud/2026-10-09-deploy3*`.
3. Instalá la APK `-claude-3` en el A56 y hacé la práctica Cloud con tu procedimiento
   habitual: gate solo para esa sala, token Debug temporal y limpieza al terminar. Que el
   usuario juegue **al menos dos rondas** y recorra la lista:
   - al empezar, que no aparezca el cartel «Preparando…» (punto 1);
   - tocar y mantener presionada la carta de otro jugador para abrir su perfil (2);
   - fijarse si el amanecer, el recuento y el resultado se pueden leer (3, 4);
   - tocar VOTAR ANTES tras 10 s de debate (5);
   - enviar un mensaje en debate y en votación, y comprobar que el chat queda abierto (6);
   - votar, cambiar el voto y mirar «Votaste a» y el contorno hasta el cierre (7, 8);
   - en el recuento, que se vea quién votó a quién (9);
   - abrir el engranaje (10);
   - si sale traidor, probar el chat de traidores. Si hay empate, votar en el
     desempate y mirar «Votaste a».
4. Medí y entregá lo mismo que la vez pasada: del vencimiento a `v3_render`, de
   `v3_action_submit` a `v3_action_confirmed` (incluido `listo_votar`) y los
   `online_v3_operation` del worker. Agregá la duración real del recuento.
5. Capturas del reparto → mesa, de un perfil, de VOTAR ANTES, de «Votaste a» con el
   contorno y de un recuento con sellos. Anotá punto por punto qué le pareció al usuario.

No abras la beta ni publiques en Play, y no hagas commit ni push sin pedido del usuario.
Al terminar, dejá el gate cerrado y verificá la limpieza.
