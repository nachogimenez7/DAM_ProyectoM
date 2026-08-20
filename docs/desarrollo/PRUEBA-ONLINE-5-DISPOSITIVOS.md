# Prueba online con cinco dispositivos

Usar un teléfono real y cuatro instancias de BlueStacks, todas con una instalación limpia del
mismo APK debug. Comprobar Internet, fecha/hora automáticas y nombres distintos.

## Preparación

1. Vincular una cuenta de prueba en el dispositivo anfitrión.
2. Dejar al menos dos dispositivos como invitados y vincular cuentas en los demás.
3. Crear una sala privada normal de 5 jugadores, compartir el código y entrar con los otros cuatro.
4. Comprobar nombre, código, mapa, 5/5 jugadores, presencia y botón Listo en todas las pantallas.

## Partida completa

1. Marcar los cinco como listos e iniciar.
2. Confirmar que cada dispositivo recibe un solo rol, nadie ve roles ajenos y los traidores sí
   ven a sus compañeros.
3. Jugar al menos una noche, debate y votación. Comprobar acciones, cronómetro, chat, emotes,
   muertes y cambios de fase.
4. Cerrar por completo un invitado, abrirlo y usar Recuperar partida. Debe volver al mismo rol y
   estado.
5. Cerrar el anfitrión y comprobar que otro jugador activo toma la autoridad sin trabar la mesa.
6. Terminar la partida: resultado y revelación final deben coincidir en los cinco.
7. Jugar una revancha. Los roles se reparten de nuevo y ningún estado temporal de la mesa
   anterior debe bloquear la nueva partida.

## Moderación

1. En un dispositivo usar **Silenciar para mí**. Solo allí deben desaparecer el chat y los emotes
   del objetivo. Revertirlo con **Volver a escuchar**.
2. En Debate o Votación, con cinco vivos, proponer silencio de mesa y obtener tres votos. Al
   silenciado se le bloquea texto libre, incluso en Firebase, pero conserva respuestas rápidas.
3. Reportar desde el perfil y repetir: en Firebase debe quedar un solo documento con id
   `matchId_reportanteUid_reportadoUid`.
4. En una sala nueva usar **Expulsar de la sala**. El afectado debe salir de inmediato, ver un
   único botón para volver al online y poder reingresar si el cupo sigue disponible.

## Red y sanciones

1. Cortar Wi-Fi diez segundos en un invitado y reconectar. Presencia y listeners deben recuperarse.
2. Agregar temporalmente una cuenta a `bans/{uid}`: el menú online debe explicar la suspensión.
3. Revisar Logcat: un build release no debe emitir detalles de sala, uid, roles o chat.

Por cada fallo registrar dispositivo, hora, resultado esperado/real, captura y Logcat.
