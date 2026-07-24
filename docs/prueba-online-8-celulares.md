# Prueba online con varios celulares

Objetivo: probar el online como modo experimental jugable. No es el online final.

## Antes de probar

1. Publicar **las dos** reglas, que viven en pantallas distintas de la consola. Si publicas
   una sola, la otra queda con la version vieja:
   - Firebase Console > Firestore Database > Reglas: pegar `firestore.rules` y publicar.
   - Firebase Console > Realtime Database > Reglas: pegar `database.rules.json` y publicar.
2. En Firestore > Datos, borrar salas viejas de `partidas` que ya no sirvan para la prueba.
3. En Android Studio, abrir Logcat y filtrar por:

```text
TraidoresOnline
```

4. Instalar el APK en el celular host y en los invitados.
5. Abrir una vez `Perfil` u `Online` en cada celular para que reserve su `#` publico si todavia no lo tiene.

## Flujo recomendado

1. Host: abrir app > Jugar > Online > Crear partida.
2. Host elige `jugadoresEsperados` entre 5 y 15. Para la prueba de 5, dejar 5.
3. Host comparte el codigo de 6 caracteres.
4. Invitados: Jugar > Online > Unirse por codigo.
5. Todos marcan `Listo`.
6. Host inicia la partida solo cuando no aparezca `FALTAN X`, `FALTAN LISTOS` o `SINCRONIZANDO`.
7. Al entrar a gameplay, todos leen su carta y tocan `EMPEZAR`.
8. La primera noche debe arrancar recien cuando todos tocaron `EMPEZAR`.

Durante la lectura inicial:

- Si un jugador ve menos cartas que el total esperado, debe quedar en “Sincronizando cartas...”.
- Si un invitado toca `EMPEZAR`, no debe avanzar solo a noche; debe esperar al host.
- Si pasan 30 segundos y un celular quedo trabado, el host puede tocar `FORZAR NOCHE`.
- Forzar noche no debe repartir cartas de nuevo ni cambiar roles.

Si alguien entra y se desconecta antes de iniciar:

1. El host debe ver `LIBERAR X DESCONECTADO`.
2. Tocar ese boton.
3. Verificar que el cupo queda libre y que otro jugador puede entrar con el mismo codigo.

Si alguien cierra la app o crashea:

1. Abrir la app de nuevo.
2. Ir a Jugar > Online.
3. Tocar `Reingresar ...` si aparece.
4. Si la sala estaba esperando, debe volver al lobby.
5. Si la sala ya estaba en juego, debe entrar directo al gameplay.
6. Verificar que mantiene la misma carta, la misma fase y no crea otra sala.

## Que mirar en Firebase

- `partidas/{partidaId}`:
  - `codigoSala`
  - `estado`
  - `jugadoresEsperados`
  - `jugadoresActuales`
  - `hostActivoId`
  - `hostVersion`
  - `partidaInicialCreada`
  - `partidaInicial`
  - `estadoPartida`
  - `estadoClientes`
- `estadoClientes.{uidTemporal}`:
  - `enGameplay`
  - `jugadoresVistos`
  - `jugadoresEsperados`
  - `orden`
  - `rolLeido`
  - `estadoArranque`
  - `aplicoEstadoPartida`
  - `sincronizando`
  - `ultimaFaseAplicadaEnLocal`
- `partidas/{partidaId}/jugadores/{uidTemporal}`:
  - cada jugador conectado o desconectado.
  - `publicId`
  - `nombrePerfil`
  - `nombreSala`
  - `orden`
  - `listo`
  - `activoEnPartida`
  - si fue liberado antes de iniciar, debe quedar `activoEnPartida: false`.
- `partidas/{partidaId}/acciones/{accionId}`:
  - acciones nocturnas y votos enviados.
- `partidas/{partidaId}/chat/{mensajeId}`:
  - mensajes de chat.
- `meta/public_ids`:
  - `nextId`
- `perfiles_publicos/{uidTemporal}`:
  - `publicId`
  - `nombrePerfil`

## Que mirar en Logcat

Eventos utiles:

- `create_room_success`
- `join_room_success`
- `lobby_players_snapshot`
- `online_start_requested`
- `online_start_success`
- `online_match_enter`
- `gameplay_enter`
- `client_state_publish_requested`
- `startup_gate`
- `startup_role_read`
- `startup_first_night_start`
- `startup_first_night_received`
- `phase_host_publish`
- `phase_apply_authoritative`
- `phase_gate_wait`
- `phase_client_syncing`
- `phase_ignore_old`
- `sync_watchdog`
- `action_record_success`
- `host_handoff_*`
- `night_resolve_*`
- `vote_resolve_*`
- `alcalde_resolve_*`
- `chat_send_failure`
- cualquier linea `*_failure`

Si crashea, anotar:

- fase exacta;
- rol del jugador;
- si era host o invitado;
- ultima accion tocada;
- bloque rojo de Logcat.

## Mensajes de error comunes

- `Firebase rechazo la accion`: casi siempre significa reglas sin publicar o una ruta nueva no cubierta por `firestore.rules`.
- `No hay conexion estable con Firebase`: revisar internet del celular y volver a intentar.
- `La sala ya no existe o fue borrada`: crear una sala nueva y compartir otro codigo.
- `La sala perdio datos de partida`: borrar esa sala y crear otra; no deberia repartir roles nuevos encima.
- `Hay mas de una sala con ese codigo`: colision de salas activas; crear una sala nueva.
- `No se pudo generar un codigo libre`: reintentar crear sala; la app ya intento varios codigos.
- `No se pudo registrar la accion online`: la accion nocturna o voto no llego a `partidas/{id}/acciones`.
- `No se pudo enviar el mensaje`: el chat no llego a `partidas/{id}/chat`.

## Guia de emergencia para la demo

Si el host no puede iniciar:

1. Revisar el boton: si dice `FALTAN X`, falta gente.
2. Si dice `FALTAN LISTOS`, alguien no marco listo.
3. Si dice `SINCRONIZANDO`, esperar unos segundos o liberar desconectados.
4. Si sigue igual, crear sala nueva antes de perder tiempo.

Si un invitado queda en sincronizando al entrar:

1. Esperar 10 segundos.
2. Revisar Logcat: buscar `sync_watchdog` y `phase_apply_authoritative`.
3. Si no aparece `phase_apply_authoritative`, cerrar app y usar `Reingresar`.
4. Si reingresar no funciona, crear sala nueva.

Si un celular crashea durante la partida:

1. No reiniciar la sala inmediatamente.
2. Que ese jugador abra la app > Jugar > Online > `Reingresar`.
3. Si era el host, mirar Logcat en otro celular por `host_handoff_*`.
4. La partida debe seguir aunque el jugador no vuelva.

Si Firebase se ve raro:

1. Confirmar que `partidas/{id}/estado` este `en_juego`.
2. Confirmar que exista `partidaInicial` y `estadoPartida`.
3. Confirmar que `estadoClientes` tenga entradas de los jugadores.
4. Si falta `partidaInicial`, la sala esta corrupta: crear sala nueva.

Durante noche y votacion:

- Si un jugador toca dos veces o cambia objetivo antes de que termine el timer, debe valer su ultima accion/voto.
- Si alguien no toca nada, la fase debe avanzar igual al terminar el timer.
- En Firebase, cada accion debe guardar `detalles.actorOrden` y, cuando corresponda, `detalles.objetivoOrden`.
- En Logcat, `night_resolve_actions_loaded` y `vote_resolve_votes_loaded` muestran cuantas acciones/votos se leyeron.

## Roles online para esta prueba

El inicio online usa roles basicos seguros:

- 1 Asesino
- 1 Medico
- 1 Detective/Comisario
- 1 Mercenario desde 7 jugadores
- resto Aldeanos

Con 5-6 jugadores no hay Mercenario. Desde 7 jugadores se agrega uno y los demas cupos siguen siendo Aldeanos. No se usan por defecto Alcalde, Desertor, Espia, Bufon, Oraculo ni Payador en online durante esta prueba.

Para validar al Mercenario desde 7 jugadores:

1. Confirmar que comparte el chat de Traidores con el Asesino.
2. Durante la noche, elegir un objetivo y verificar `action_record_success` con `accion=silenciar`.
3. Al amanecer, confirmar que todos los dispositivos muestran el reveal de silencio.
4. Verificar que el jugador silenciado puede leer el chat, pero no escribir ni votar durante ese dia.
