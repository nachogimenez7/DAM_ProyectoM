# Prueba online con 1 teléfono y 4 instancias de BlueStacks

Objetivo: completar una partida online normal de cinco jugadores y comprobar desconexión,
reconexión, chat y continuidad del anfitrión antes de invitar testers externos.

## Preparación de identidades

Cada dispositivo necesita un `uid` Firebase diferente.

| Puesto | Dispositivo | Identidad | Función inicial |
|---|---|---|---|
| D1 | BlueStacks 1 | Cuenta Bandido Games | Crea la sala y es anfitrión |
| D2 | BlueStacks 2 | Invitado nuevo | Entra por código |
| D3 | BlueStacks 3 | Invitado nuevo | Entra por código |
| D4 | BlueStacks 4 | Invitado nuevo | Entra por código |
| D5 | Teléfono real | Invitado nuevo | Entra por código |

- Crear instancias nuevas de BlueStacks antes de instalar Traidores, o borrar los datos de la
  aplicación en cada clon. No clonar una instancia que ya tenga una sesión de Traidores.
- Iniciar Play Games únicamente en D1. Las otras instancias deben quedar sin vincular para
  que Firebase cree identidades anónimas diferentes.
- Instalar exactamente el mismo APK en los cinco dispositivos.
- Mantener App Check sin aplicar en la consola durante estas pruebas de debug. Cada instancia
  debug genera su propio token; Play Integrity se valida más adelante con el AAB de Play.

## Ronda 1 — Lobby y comunicaciones

1. D1 crea una sala normal de 5 jugadores y comparte el código.
2. D2–D5 entran por código.
3. Comprobar que aparecen cinco nombres distintos y cinco documentos distintos en
   `partidas/{roomId}/jugadores`.
4. Enviar un mensaje de lobby desde cada dispositivo.
5. Marcar LISTO uno por uno y confirmar que el contador cambia en todos.
6. D1 inicia únicamente cuando muestra 5/5 y todos están listos.

Resultado esperado: nadie duplica a otro jugador, el chat aparece en los cinco dispositivos
y el reparto comienza una sola vez.

## Ronda 2 — Partida completa

1. Cada dispositivo anota su rol sin mostrarlo a los demás.
2. Todos confirman la carta.
3. Completar al menos dos noches y dos votaciones.
4. Durante cada fase enviar acciones desde dispositivos diferentes.
5. Continuar hasta que aparezca un ganador.
6. Confirmar que los cinco muestran el mismo ganador, ronda, eliminados y resultado.

## Ronda 3 — Reconexión de invitado

1. Crear una sala nueva.
2. Con la partida iniciada, cerrar Traidores por completo en D3.
3. Esperar a que los demás lo vean desconectado.
4. Abrir Traidores en D3 y usar REINGRESAR.
5. Confirmar que conserva rol, estado de vida y fase; no debe crear un sexto jugador.

## Ronda 4 — Caída del anfitrión

1. Durante una fase activa, cerrar Traidores por completo en D1.
2. Confirmar que otro jugador conectado toma `hostActivoId`.
3. Completar la fase sin D1.
4. Reabrir D1 y reingresar.
5. Comprobar que la partida continúa y no retrocede de fase.

## Ronda 5 — RTDB y modo avión

1. En el teléfono real, abrir y usar el chat.
2. Activar modo avión durante al menos diez segundos.
3. Desactivar modo avión y esperar la reconexión.
4. Enviar otro mensaje.

Resultado esperado: los listeners se detienen mientras no hay presencia y se reenganchan al
publicar `conectado`; el chat no queda mudo.

## Ronda 6 — Seguridad visible

1. Expulsar a un jugador y confirmar que no puede volver a entrar con el mismo código.
2. Eliminar a un jugador durante la partida: debe perder el chat público y, si era Traidor, el
   canal de Traidores; en cambio debe poder usar el chat de espectadores.
3. Proponer un silencio de mesa. El resultado solo debe aplicarse al alcanzar la mayoría y no por
   el voto aislado de un participante.
4. Cerrar al anfitrión original después de transferir el control. La sala debe seguir funcionando
   y el anfitrión anterior ya no debe poder cerrarla ni administrar su código.

## Ronda 7 — Ocho jugadores y roles de mapa

Con ocho instalaciones, crear una partida corta en cada mapa y llegar a la revelación final:

- Pampa: debe aparecer el Payador.
- Grecia: debe aparecer el Oráculo.
- Medieval: debe aparecer el Bufón.

En cada caso comprobar también que la acción especial se muestra y que la partida continúa en
todos los dispositivos después de usarla.

## Evidencia mínima

Por cada error anotar:

- dispositivo;
- host o invitado;
- fase y ronda;
- acción anterior;
- texto visible;
- captura;
- líneas de Logcat filtradas por `TraidoresOnline`.

No avanzar a la prueba cerrada de Google Play hasta completar una partida, una reconexión de
invitado, una transferencia de anfitrión y al menos una partida de ocho jugadores sin corrupción
de sala.
