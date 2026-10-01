# Recuperación nocturna · Android 0.1.46

## Corrección y comprobaciones automáticas

Los avisos del Plan esperan la confirmación de sincronización de permisos RTDB. Se crean mediante una transacción que conserva los avisos existentes, evitando sobrescribir mensajes inmutables tras un relevo. No se modificaron las reglas desplegadas.

Pasaron las pruebas unitarias Android, la compilación debug y las pruebas locales de reglas RTDB, incluido un nuevo caso de relevo que conserva el aviso del host anterior, crea el pendiente y evita duplicados.

## Primera partida de cinco

Sala enmascarada ***V46, partida 2ba07670. A56 anfitrión inicial y aldeano; emulador 5635 comisario. El usuario bloqueó el teléfono 30 segundos durante la noche.

- El 5635 asumió a las 02:32:42, recuperó los cinco roles, publicó el resultado de su investigación a las 02:32:50 y resolvió la noche con tres acciones válidas a las 02:33:07.
- El usuario confirmó recuperación del A56 en aproximadamente cinco segundos, avance de la partida y regreso de todos al lobby con LISTO disponible.
- Ambos reportes coinciden en RESULTADO:10, ronda 1 y 5/5 conectados en el lobby.
- En el registro capturado del 5635 no aparecen fallos de publicación de avisos ni de sincronización de acceso RTDB. Esto no demuestra por sí solo que todos los mensajes fueran entregados.
- 5635: ocho publicaciones confirmadas, p50 548 ms, p95 estimado 886 ms; 73 lecturas observadas estimadas, 36 dependientes de reglas y 18 escrituras intentadas.
- A56: una publicación confirmada antes del relevo; 82 lecturas observadas estimadas, 46 dependientes de reglas y 12 escrituras intentadas.

Los contadores son parciales, tienen ventanas distintas y abarcan entrada y regreso al lobby; no representan consumo total de la sala ni facturación real.

## Revancha de cinco

Misma sala ***V46, partida e657ffdb. A56 médico y 5635 asesino. El usuario minimizó la app y bloqueó el teléfono, luego volvió y pudo proteger antes de terminar la noche. Recuperación percibida de diez segundos o más y un fondo de día transitorio con título nocturno.

- Ambos reportes terminan en RESULTADO:10, ronda 1, con 5/5 conectados. Todos regresaron al lobby y quedó disponible otra partida.
- A56 registró `guest_host_advance_timeout` a las 02:40:47.915; aplicó el estado nocturno a las 02:40:59.049. Es un intervalo entre aviso y aplicación, no una medida exacta desde el desbloqueo.
- Reinicio de sala solicitado a las 02:42:52.211, confirmado a las 02:42:52.561 y limpieza terminada a las 02:43:22.686. El usuario percibió unos quince segundos de cartel; la secuencia completa del registro dura aproximadamente treinta segundos.
- Deltas desde los reportes de la primera partida: 5635, 53 lecturas observadas estimadas, 32 de reglas y 18 escrituras intentadas; A56, 65 lecturas, 38 de reglas y 14 escrituras. Incluyen espera y revancha, con ventanas distintas.

La capacidad del médico de actuar tras recuperar y el regreso al lobby pasan la prueba funcional. Quedan por revisar la latencia de recuperación, la presentación del fondo al reanudar y la duración de la limpieza. No hace falta repetir otra partida completa antes de investigar esos tramos.

## Pendiente original (cubierto por la revancha)

Iniciar una revancha en la misma sala. Si el A56 recibe un rol con acción nocturna, comprobar que pueda actuar después de volver de una desconexión, antes de que termine la noche. El aldeano de la primera prueba no permite validar ese caso.

## Candidata 0.1.47: revisión posterior

- La sincronización de miembros RTDB se dispara también al cambiar el estado de la sala o el anfitrión. Antes solo dependía de recibir jugadores: si estos llegaban primero, el reinicio podía esperar otro evento (en el registro, aproximadamente 28 segundos hasta pedir permisos).
- La recuperación pide el estado RTDB mediante `get()` inmediatamente al reanudar como invitado y en los reintentos existentes. Mantiene la escucha, evita solicitudes simultáneas y descarta respuestas de una generación detenida. No agrega lecturas Firestore ni reinicia globalmente la conexión Firebase. La red real sigue condicionando los tiempos; `get()` puede usar caché cuando no hay servidor accesible.
- Al saltar presentaciones históricas, se aplica explícitamente el fondo de la fase vigente junto con su periodo. Antes actualizaba el periodo guardado sin cambiar necesariamente la imagen visible.

Pendiente de validación manual en cinco dispositivos: tiempo de vuelta del A56, posibilidad de actuar y duración del cartel de limpieza al terminar. No afirmar mejora medida antes de esta prueba.

## Resultado manual 0.1.47

Sala ***593, partida e844cba2: A56 asesino, 5635 aldeano. Tras minimizar y bloquear el teléfono treinta segundos, el usuario informó vuelta en unos diez segundos, selección de víctima correcta y partida terminada. Ambos reportes coinciden en RESULTADO:10, ronda 1 y 5/5 conectados; lobby habilitado otra vez.

La limpieza duró unos ocho segundos según el usuario. Los registros confirman reinicio solicitado a las 02:59:37.914 y limpieza terminada a las 02:59:45.890: aproximadamente ocho segundos, frente a treinta de la secuencia anterior. Un fallo inicial de sincronización RTDB a las 02:59:39.026 se recuperó en el reintento de cinco segundos, con sincronización exitosa a las 02:59:44.783. Queda por revisar la carrera de devolución de autoridad que puede causar ese reintento; no afirmar ausencia de errores.

5635 tomó el relevo con cinco roles recuperados y resolvió tres acciones nocturnas válidas. Su registro capturado no muestra fallos de avisos del Plan. Reportes parciales: A56 75 lecturas observadas, 45 dependientes estimadas y 11 escrituras intentadas; 5635 74, 37 y 17 respectivamente. No equivalen al consumo de todos los dispositivos ni de Firebase.

La recuperación funcional y la limpieza pasan esta prueba. La espera de diez segundos al volver sigue siendo una limitación observada; no se demostró una reducción frente a la prueba anterior. El usuario no confirmó explícitamente el aspecto del fondo, por lo que la corrección visual sigue pendiente de confirmación.

## Revisión 0.1.48: permisos y salas abandonadas

- El chat de espectadores requiere tanto la muerte en el estado del juego como la confirmación del registro propio RTDB (`activo`, fuera del lobby y `vivo=false`). Se reutiliza la escucha de membresía, sin lecturas Firestore adicionales. Al confirmarse el cambio, el callback de presencia reabre el contenido.
- Durante la limpieza de revancha, el lobby comprueba la autoridad RTDB antes de sincronizar miembros y conserva el reintento existente si el relevo sigue pendiente. No intenta reclamar autoridad mediante una escritura anticipada. La toma de anfitrión en un lobby normal mantiene su funcionamiento anterior.
- El botón de reingreso ya valida sala y membresía contra Firestore, antigüedad con reloj de servidor y presencia RTDB. Salas en espera/finalizadas vencen a los treinta minutos de actividad y partidas a las veinticuatro horas. Cuando todos figuran desconectados, ahora la gracia parte de la última desconexión y no de una actualización administrativa de la sala; dura tres minutos, igual que la protección del anfitrión.
- No se borran salas activas ni se modifica Firebase desplegado. El buscador sigue filtrando por resumen público y antigüedad; esto no demuestra detección inmediata de todos los AFK en salas ajenas. Una lectura de presencia fallida oculta el reingreso y conserva el recuerdo para un nuevo intento.

Pendiente manual: un ciclo de cinco con relevo, eliminación/chat de espectadores y regreso al lobby; aparte, dejar todos fuera de una sala de prueba más de tres minutos y verificar que el menú no ofrece reingreso. No hace falta una partida de quince.

## Bloqueo de recuento 0.1.48 y corrección 0.1.49

El usuario reportó recuperación inmediata del A56 comisario tras bloquear treinta segundos. Durante la votación posterior, el 5635 (asesino y coordinador activo) quedó negro y los demás mostraron EMPATE, CONTINUANDO 5/5.

El logcat confirma cierre fatal a las 03:18:20.312: `GridLayout.setColumnCount` desde `VoteResultAnimator.applyPanelMode`, llamado por `hide()` al continuar el recuento. La grilla conservaba tarjetas con índices de una tercera columna cuando se intentó reducir a dos columnas. El coordinador había cargado cinco acciones y recibió las cinco confirmaciones; el bloqueo fue posterior, en la presentación.

Los cinco registros de acciones muestran destinos diferentes formando un ciclo; el resultado de un voto por candidato coincide con esas acciones. No se constató pérdida de una votación unánime en esta ejecución.

Corrección: `hide()` solo cancela animaciones y oculta el panel, sin reconfigurar las columnas. `show()` y `showExpulsion()` ya vacían la grilla antes de configurar la siguiente presentación. Pendiente comprobación real del cierre de empate con cinco candidatos, paso al desempate y también votación unánime.

## Validación manual del empate 0.1.49

El usuario creó otra sala y repitió el empate: informó que avanzó correctamente, que el chat de muertos envía y permite leer mensajes y que la limpieza fue muy breve sin bloquear dispositivos. No solicitamos otra partida para repetir esos casos.

Ajuste posterior de texto: la mención a intervención del Alcalde en el segundo empate solo se muestra con un Alcalde vivo y revelado. Si su cargo todavía no es público, se usa un texto neutral, evitando anunciar roles ocultos o prometer una expulsión. Este cambio de texto es posterior al APK 0.1.49 instalado.

No se añade abstención explícita en esta revisión: el empate funciona como vía para no expulsar; una opción de saltar voto requiere definir mayoría, abstenciones y relación con AFK antes de cambiar el protocolo.

Sigue pendiente comprobar una sala sin participantes y la instalación desde Google Play. No consta una prueba de votación unánime en la validación descrita por el usuario.

## Sala vacía: prueba realizada por ADB

Se cerró Traidores mediante `am force-stop` en los cinco dispositivos, conservando sus datos y el recuerdo de recuperación. Se confirmó previamente que el A56 tenía guardada la sala ***R28.

Lectura administrativa de presencia RTDB, sin alterar Firebase: cinco participantes desconectados, cero conectados. A las 03:42:45 de Argentina habían transcurrido 206 segundos desde la última desconexión. Se abrió el juego por su entrada normal en el A56 y se navegó a Jugar en línea.

El menú mostró Buscar partida, Unirse por código y Crear partida, sin botón Reingresar. Las preferencias del A56 ya no contenían el identificador ni el código de recuperación. La prueba de vencimiento del reingreso a una sala vacía pasa en Android 0.1.49.

Esto verifica ocultamiento y limpieza del recuerdo local, no eliminación física de la sala ni desaparición inmediata del buscador. El cambio posterior del texto del Alcalde no estaba incluido en ese APK. Sigue pendiente la comprobación de la distribución final desde Google Play.
