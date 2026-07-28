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

## Evidencia mínima

Por cada error anotar:

- dispositivo;
- host o invitado;
- fase y ronda;
- acción anterior;
- texto visible;
- captura;
- líneas de Logcat filtradas por `TraidoresOnline`.

No avanzar a la prueba cerrada de Google Play hasta completar una partida y una reconexión de
invitado sin corrupción de sala.
