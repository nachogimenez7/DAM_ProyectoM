# Prueba online con varios celulares

Objetivo: probar el online como modo experimental jugable. No es el online final.

## Antes de probar

1. En Firebase Console > Firestore > Reglas, copiar el contenido de `firestore.rules` y publicar.
2. En Firestore > Datos, borrar salas viejas de `partidas` que ya no sirvan para la prueba.
3. En Android Studio, abrir Logcat y filtrar por:

```text
TraidoresOnline
```

4. Instalar el APK en el celular host y en los invitados.
5. En Opciones, confirmar que `Modo vertical de gameplay` este activado si el celular venia de una instalacion vieja.

## Flujo recomendado

1. Host: abrir app > Jugar > Online > Crear partida.
2. Host comparte el codigo de 6 caracteres.
3. Invitados: Jugar > Online > Unirse por codigo.
4. Todos marcan `Listo`.
5. Host inicia la partida.

## Que mirar en Firebase

- `partidas/{partidaId}`:
  - `codigoSala`
  - `estado`
  - `jugadoresActuales`
  - `estadoPartida`
- `partidas/{partidaId}/jugadores/{uidTemporal}`:
  - cada jugador conectado o desconectado.
- `partidas/{partidaId}/acciones/{accionId}`:
  - acciones nocturnas y votos enviados.
- `partidas/{partidaId}/chat/{mensajeId}`:
  - mensajes de chat.

## Que mirar en Logcat

Eventos utiles:

- `create_room_success`
- `join_room_success`
- `lobby_players_snapshot`
- `online_start_requested`
- `online_match_enter`
- `gameplay_enter`
- `action_record_success`
- `night_resolve_*`
- `vote_resolve_*`
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
- `No se pudo registrar la accion online`: la accion nocturna o voto no llego a `partidas/{id}/acciones`.
- `No se pudo enviar el mensaje`: el chat no llego a `partidas/{id}/chat`.

## Roles online para esta prueba

El inicio online usa roles basicos seguros:

- 1 Asesino
- 1 Medico
- 1 Detective/Comisario
- 1 Alcalde si hay 8 o mas jugadores
- resto Aldeanos

No se usan por defecto Mercenario, Desertor, Espia, Bufon, Oraculo ni Payador en online durante esta prueba.
