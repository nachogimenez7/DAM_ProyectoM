# Revisión del contrato V3 — segunda vuelta

5 de octubre de 2026. Revisión de la entrega de Codex: motor, servicio, callables, worker,
límite por UID, reglas e historial. **No toqué backend, reglas ni motores.** La sonda
`REVISION_CONTRATO_V3_sonda.cjs` solo lee el motor; ya está actualizada a la regla del
Desertor en ronda 4 y agrega abandono y revancha.

Reproducir: `node ios/docs/REVISION_CONTRATO_V3_sonda.cjs`. Base: `npm --prefix functions run test:unit`
→ 52/52. No corrí las suites con emuladores (`test:authority`, `test:authority-http`).

## 1. Estado de los hallazgos anteriores

| ID | Estado | Evidencia |
|---|---|---|
| R-01 revancha/abandono | **Resuelto**, con una salvedad (N-3) | `prepararRevanchaV3`, `abandonarPartidaV3`, desbloqueo `authorityMode=lobby` tras publicar |
| R-02 Tasks temprano | **Resuelto** | worker lanza `unavailable` si `retryAfterMs`; programa a `deadline + 1500 ms` |
| R-03 revisión pública | **Resuelto** | sonda: noche 1 → tras dos acciones secretas 1 → amanecer 2; recibos usan `publicRevision` |
| R-04 Alcalde | **Resuelto** | sonda: oculto capaz / silenciado oculto / muerto en secreto → misma ventana y mismo texto al vencer; revelado y silenciado → `TIE_NO_EXPULSION` |
| R-05 anuncios | **Parcial** (N-4) | Ya existen `ORACLE_INVITATION`, empates, mayoría, corrupción y «nadie fue expulsado» |
| R-09 inicio | **Resuelto** | `startServerMatch` exige `hostId` |
| R-10 rechazos | **Resuelto** | bucket por UID (8 de ráfaga, 1 cada 2 s) que consumen también los rechazos |
| Desertor ronda 4 | **Pasa** | sonda: ventana en ronda 4; en ronda 3 ganan Traidores sin ventana; en el debate se rechaza en ronda 3 y se acepta en ronda 4; silenciado puede elegir |
| DES-12 | No lo ejecuté | Codex lo cubre en la integración; no corrí emuladores |
| ALC-08, ORA-06, AFK-06 | **Pasan** | sonda |

## 2. Hallazgos nuevos

### Alta

**N-1 · Un abandono durante la ventana del Desertor puede trabar la partida para siempre.**
Si un traidor sale mientras `DESERTOR_RECONSIDERACION` está abierta y la paridad se rompe,
`winnerFor` pasa a `null`. Como `evaluateWinner` devuelve `true` solo por estar en esa fase:
- la elección del Desertor se acepta, consume el uso y la fase no cambia;
- cada vencimiento devuelve `changed: true` sin avanzar `phaseIndex` ni el plazo. La tarea no
  se reencola (mismo token) y cada recuperación «avanza» sin avanzar.

Reproducción (sonda, «ABANDONO en ventana»): ronda 4, 3 Aldeanos + 2 Asesinos + Mercenario +
Desertor. Ventana abierta, sale el Mercenario → `[DESERTOR_RECONSIDERACION, null, null]`. Al
vencer dos veces queda en `DESERTOR_RECONSIDERACION` con `phaseIndex` 3.

Esperado: si al cerrar la ventana ya no hay ganador, la partida sigue desde donde se abrió la
ventana. Guardar la fase de retorno al abrirla: amanecer → `DIA_DEBATE`, resultado → noche
siguiente. Hace falta decidir si ese cierre consume el uso del Desertor; sugiero que no, porque
no llegó a ser una decisión final. Agregar una invariante a las pruebas: todo
`expirePhase` con `changed: true` sube `phaseIndex` o fija `winner`.

**N-2 · Un jugador ya muerto que sale pierde su victoria.** *(regla a confirmar con el usuario)*
`leaveServerGame` reemplaza la causa por `ABANDONO` aunque el jugador ya estuviera muerto, y el
historial excluye `ABANDONO`. Un Aldeano muerto que cierra la partida antes del final pierde la
victoria de Pueblo. Un Bufón expulsado que se va pierde su victoria especial. Salir después de
morir es lo más común.
Reproducción (sonda): Aldeano muerto de noche sale → causa `ABANDONO`; gana Pueblo → `won: false`.
**Decidido por el usuario:** salir antes del final, vivo o muerto, nunca suma victoria. Se
mantiene el comportamiento actual: queda registrado como derrota, para que no sirva para
esquivar una derrota. Solo hay que escribirlo en el contrato y fijarlo con pruebas (Aldeano
muerto y Bufón expulsado que salen → `won: false`). Sanciones: más adelante.

### Media

**N-3 · En el lobby de revancha el anfitrión puede degradar la sala.** Con `authorityMode=lobby`,
`activeHostCanUpdateRoom` deja cambiar cualquier campo fuera de su lista: `authorityMode`,
`protocolVersion`, `estado`, `partidaInicialCreada`, `preparedMatchId`, `serverGeneration`,
`rematchOf`. `test-server-authority-rules.cjs` solo prueba la denegación con `server`.
Así el anfitrión puede pasar la revancha al protocolo anterior, donde lee secretos.
Esperado: las reglas impiden que el cliente cambie esos campos cuando existen, y el cliente V3
rechaza una sala cuyo protocolo bajó. Agregar el caso a las pruebas de reglas.

**N-4 · Siguen eventos con código genérico `INFO`.** Expulsión por AFK (además sin
`jugadores`), «Noche N», apertura del Contrapunto, ventana del Desertor, victoria, cancelación
por AFK y «El Alcalde no decidió». Sin código, el cliente tiene que interpretar el texto.
Sugerencia: `AFK_EXPULSION`, `NIGHT_START`, `COUNTERPOINT_OPEN`, `DESERTER_WINDOW`, `VICTORY`,
`MATCH_CANCELLED`, `MAYOR_NO_DECISION`.
Consumo: `eventosPublicos` (hasta 60) viaja completo en cada cambio público. Conviene acotarlo
a la ronda actual o publicarlo como nodo de solo agregado, y medir los bytes por receptor.

### Baja

- **N-5:** salir del lobby vuelve a bloquear la sala (`authorityMode=server`) hasta que se
  publique. Mientras tanto fallan las escrituras de los demás («listo»). El cliente debe
  reintentarlas sin mostrar error.
- **N-6:** quien entra al lobby después de la revancha no figura en `permissions` V3 hasta el
  inicio. Está bien si el lobby usa sus canales actuales; hay que dejarlo escrito para los clientes.

## 3. Propuesta de presentación iOS (documento, sin código hasta fijar los DTO)

**NOCHE**
- Todos ven la misma pantalla y la misma cuenta regresiva (`limiteFaseEpochMs` + offset del
  servidor), tengan poder o no. Quien no tiene acción ve el mismo marco con «La noche sigue…»;
  así ni el tiempo en pantalla ni la vibración delatan un rol.
- Cada uno ve su carta de poder. Al confirmar, la marca sale de `accionesConfirmadas`, nunca de
  un estado local optimista. No hay «esperando a los demás».
- Cartas no elegibles atenuadas: `objetivosBloqueados` (Mercenario) y objetivos que el motor
  rechazaría. Sonido y háptica solo para la acción propia.
- El chat de traidores aparece solo si `permissions.traitorChat`.
- En cero: «Resolviendo…», con entradas bloqueadas hasta que llegue un `phaseIndex` nuevo.
  `recuperarFaseV3` tras la gracia de 5 s, respetando `retryAfterMs` con espera creciente y un tope.

**DESERTOR_RECONSIDERACION**
- Mesa: aviso público con cuenta regresiva («El Desertor decide antes de cerrar la partida»).
  No muestra quién es.
- Desertor: hoja no descartable con «Mantener <bando>» y «Pasarme a <otro>», y la aclaración
  «Es tu única decisión». También la ve estando silenciado.
- En el debate, desde la ronda 4: botón «Revisar bando» en su carta privada, con confirmación de
  uso único. Desaparece cuando `desertorCambioBando` vale `true`.

**Abandono**
- Confirmación según el momento:
  - vivo: «Si salís ahora quedás eliminado y no ganás esta partida»;
  - muerto: depende de N-2;
  - con resultado: «Tu resultado ya quedó registrado».
- Pasar la app a segundo plano o perder la red no es abandono: se muestra reconexión y se vuelve
  a escuchar `public` / `private` / `permissions`.
- La mesa ve el evento `PLAYER_LEFT` con la carta tachada.

**Revancha**
- Solo el creador ve «Revancha» en el resultado.
- Con `publication-pending`, el botón muestra «Preparando la sala…» y reintenta solo. Los demás
  ven la fase `LOBBY` y vuelven al lobby con «listo» desmarcado.
- Quien abandonó no aparece. El resultado anterior queda visible hasta salir de esa pantalla.

## 4. No verificado

Emuladores, Tasks e IAM reales, clientes V3 y partidas entre dispositivos. Las constantes de
ronda 4 de Android/iOS local las leí; no corrí sus pruebas.
