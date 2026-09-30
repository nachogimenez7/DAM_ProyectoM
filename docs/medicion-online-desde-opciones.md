# Medición copiable en Android 0.1.41 y posteriores

En el menú: Opciones → Pruebas online.

- **Reiniciar medición** pone en cero únicamente las estimaciones locales de
  Firestore. No borra cuentas, estadísticas, salas ni datos de Firebase.
- **Copiar reporte beta** copia el reporte de estabilidad y el desglose de consumo
  observado. También se incluye desde el engranaje del lobby y de la partida.

El desglose agrupa lobby, partida y buscador de salas. Conserva operaciones entre
pantallas y entre partidas mientras siga vivo el proceso de la aplicación.
Identifica lecturas estimadas, lecturas dependientes de reglas estimadas,
escrituras intentadas y aperturas de listeners. En las escuchas excluye callbacks provenientes de
caché y cambios locales pendientes. Solo observa instrumentación existente y el
listener del buscador: copiar o reiniciar no hace consultas adicionales.

**Es una estimación parcial de un dispositivo, no la factura ni la cuota restante
de todo el proyecto.** Hay operaciones no instrumentadas, posibles lecturas de
reglas y reintentos internos del SDK que no se pueden atribuir con estos
contadores. No sumar el contador de escrituras intentadas como escrituras
facturadas. Las lecturas dependientes se muestran aparte para no ocultar esa
incertidumbre. Los campos técnicos identifican flujos de código, nunca rutas de
documentos, nombres, mensajes ni UID.

La ventana de Firestore empieza al activar el contador o reiniciar su medición.
La medición de red del reporte conserva su ventana propia por partida; los bytes
del dispositivo incluyen servicios ajenos a RTDB. No dividir ambos totales como
si midieran siempre el mismo intervalo. Los datos de sala/eventos pueden ser de
la última sesión guardada, mientras los contadores son de la ejecución actual.

## Prueba de consumo de lobby

1. Instalar la última versión en los dispositivos elegidos. Reiniciar la medición antes de
   entrar a la sala y registrar la hora.
2. Entrar a una sala nueva y copiar un reporte inicial desde el engranaje.
3. Dejar el lobby quieto cinco minutos, con las pantallas encendidas, sin chat ni
   cambios, y copiar otro reporte. Bloquear/minimizar se mide en otra prueba.
4. Mandar ambos del anfitrión y de un invitado, indicando cantidad de jugadores.
   Comparar diferencias por apartado: no sumar dos reportes acumulativos.

## Prueba de partida

Reiniciar antes de crear/entrar a una sala nueva, jugar y copiar desde el lobby
al terminar. Enviar reportes del anfitrión y de un invitado. Si cambia la
coordinación, incluir el nuevo anfitrión e indicar el momento. Si se mata/reinicia
la app, los contadores empiezan de cero: anotarlo, porque no es una medición
completa de la partida.

Para confirmar costo/cuota, contrastar con Cloud Monitoring o consola Firebase
en un intervalo aislado y después de que consolide las métricas. No extrapolar
las lecturas del anfitrión a todos los jugadores: sus operaciones son distintas.

## Prueba del 30 de septiembre: cinco jugadores, 0.1.41

Se reinició únicamente la medición del anfitrión A56 y de un invitado emulado.
Los intervalos entre reportes no fueron exactamente cinco minutos:

| Dispositivo | Intervalo | Lecturas observadas adicionales | Lecturas de reglas estimadas adicionales | Escrituras intentadas adicionales |
| --- | ---: | ---: | ---: | ---: |
| Invitado | 416 s | 15 | 15 | 0 |
| Anfitrión | 436 s | 37 | 14 | 15 |

En el invitado, todas las lecturas adicionales pertenecen a `lobby/players`.
El registro del anfitrión muestra renovaciones de presencia cada treinta segundos.
El anfitrión acumuló once escrituras `host_lease` y cuatro escrituras de presencia
entre ambos reportes. Sus escuchas de lobby pasaron de una a cuatro aperturas
acumuladas: el campo `escuchas_abiertas` no representa escuchas simultáneas.

El A56 se bloqueó automáticamente y abrió de nuevo las escuchas al despertar.
A las 01:39:26, una respuesta de caché sin documento de sala disparó
`lobby_room_missing`; el código la trató como una eliminación y cerró el lobby.
Las comprobaciones del menú fallaron momentáneamente con «client is offline».
A las 01:39:32, el servidor devolvió la misma sala en estado de espera, con el
mismo anfitrión y cinco jugadores. El proceso de Android no había reiniciado.
Esto identifica una salida incorrecta del cliente; las mediciones no permiten
atribuir saturación al servicio ni calcular la factura total del proyecto.

La versión 0.1.42 solo considera eliminada una sala cuando su ausencia llega
del servidor sin escrituras pendientes. Ignora callbacks de una sesión de
escucha anterior o de un lobby detenido. También limpia partida/fase/ronda
anteriores al abrir una sala nueva; volver al mismo lobby conserva el resultado
para copiar el reporte. Hay pruebas de regresión para estos casos.

Siguiente paso: comprobar bloqueo/desbloqueo del anfitrión durante treinta
segundos en un lobby, antes de repetir la medición quieta con pantalla encendida.

## Recuperación del anfitrión del lobby: 0.1.43

La prueba de treinta segundos con 0.1.42 pasó. Al prolongar el bloqueo, el
emulador registrado reclamó la sala y la transacción desactivó al anfitrión
anterior. En el registro del A56, la escucha de jugadores terminó con
`PERMISSION_DENIED`. La recuperación de membresía llegó a completarse, pero
callbacks anteriores todavía podían decidir una salida y la escucha terminada
no se reabría.

En 0.1.43:

- El lobby conserva al anfitrión desconectado durante tres minutos. La política
  se usa en la decisión local, la comprobación transaccional y el temporizador.
  No cambia el relevo rápido de coordinación durante gameplay.
- El relevo automático conserva la membresía activa y el cupo del anfitrión
  anterior. Solo cambia su autoridad y deja su botón Listo sin confirmar.
- Al volver después del relevo, el jugador conserva acceso y recibe un aviso de
  cambio de anfitrión. La pérdida de un cupo sin baneo confirmado ya no se
  presenta como una expulsión del anfitrión.
- La reparación de membresía invalida comprobaciones anteriores, ignora cambios
  locales pendientes y vuelve a abrir la escucha de jugadores tras recuperarla.
- Una prueba con el emulador local de reglas comprueba el relevo por una cuenta
  registrada, la conservación del cupo/membresía y que el anterior anfitrión
  puede recuperar su presencia y leer jugadores. No cambia reglas desplegadas.

Siguiente prueba, con los cinco dispositivos actualizados y una sala nueva:
bloquear el anfitrión dos minutos, volver y confirmar que sigue siendo anfitrión
y que aparecen los cinco jugadores. Después comprobar una ausencia superior a
tres minutos: debe haber relevo y el anterior anfitrión debe poder volver como
participante. Aún no usar estos bloqueos como medición de consumo quieto.

El usuario confirmó recuperación correcta tras dos minutos con 0.1.43. Queda
pendiente la validación en dispositivos del relevo tras tres minutos y las
pruebas de coordinación durante partidas; no confundirla con la prueba ya pasada.

## Actualizar el buscador: 0.1.44

El encabezado de Buscar partida incorpora una flecha circular. Al tocarla,
`Source.SERVER` vuelve a consultar las salas públicas que están esperando y
dentro de la ventana de vigencia, con el límite actual de resultados. Muestra
actualización en curso, éxito o fallo de conexión. Impide consultas simultáneas
y limita los pedidos a uno cada cinco segundos. Un fallo manual mantiene las
filas existentes. Se conserva la escucha automática de salas.

El contador atribuye la consulta a `buscador/salas_actualizar`; las lecturas
manuales son adicionales y no deben mezclarse con el consumo quieto. Una respuesta
que llega después de salir de la pantalla se descarta; tampoco reemplaza cambios
más recientes recibidos de la escucha ni una página más amplia solicitada después.

Para el siguiente paso de medición, actualizar los mismos cinco dispositivos,
comprobar brevemente el botón y luego reiniciar la medición solo en anfitrión e
invitado. Copiar antes y después de cinco minutos, con todas las pantallas
encendidas y sin iniciar partida, cambiar mapas, chat o usar Actualizar.

## Medición del lobby de cinco jugadores, 0.1.44

Reportes enviados por el usuario para la sala `***HS7`:

| Dispositivo | Intervalo entre reportes | Lecturas observadas adicionales | Reglas estimadas adicionales | Escrituras intentadas adicionales |
| --- | ---: | ---: | ---: | ---: |
| Invitado | 375 − 11 = 364 s | 20 − 8 = 12 | 13 − 1 = 12 | 1 − 1 = 0 |
| Anfitrión A56 | 467 − 48 = 419 s | 56 − 21 = 35 | 30 − 13 = 17 | 15 − 1 = 14 |

En el invitado, todo el incremento pertenece a `lobby/players`, con una sola
apertura acumulada de cada escucha. En el anfitrión, el desglose adicional es:
jugadores 17, membresía propia 14, sala 3 y baneo propio 1. Escribió trece veces
`host_lease` y una vez presencia; sus cuatro escuchas pasaron de una a dos
aperturas acumuladas. Esto muestra una reapertura y no cuatro escuchas duplicadas
simultáneas. El A56 no estaba disponible por ADB durante este análisis, por lo
que no se atribuye una causa concreta a su transición de Activity.

Los registros de los emuladores confirman que, tras terminar los movimientos
iniciales de entrada/reingreso, desde las 02:39:20 hasta las 02:45:20 se recibieron
actualizaciones de jugadores aproximadamente cada treinta segundos. No aparece
un relevo de anfitrión en este intervalo; el creador permaneció como anfitrión.
Esto concuerda con la renovación periódica de presencia del anfitrión. Los
reportes nuevos ya muestran LOBBY, ronda cero y sin partida medida, sin heredar
el resultado de una sala anterior.

La muestra sirve como referencia de consumo observado. Los intervalos son
distintos e incluyen movimientos iniciales/reapertura: no extrapolarla como una
factura exacta, no sumar lecturas de reglas como si estuvieran verificadas por
facturación y no tratar dos dispositivos como el total del proyecto. El `-/5`
del encabezado significa que el reporte aún no registró el conteo del lobby;
no prueba que los cinco estuvieran desconectados.

No hace falta repetir la espera quieta para avanzar. Siguiente paso: reiniciar
medición en anfitrión y el mismo invitado antes de entrar a una sala nueva de
cinco, jugar una partida completa a ritmo normal sin forzar desconexiones y
copiar ambos reportes al regresar al lobby. Registrar duración aproximada y
cualquier anomalía. Después medir las recuperaciones durante una partida de
quince y la revancha.

## Partida completa de cinco jugadores, 0.1.44

Sala `***U5R`, partida recortada `ad3ee6a3`, dos rondas. Según el usuario, el
asesino mató dos jugadores y el pueblo expulsó a un inocente. Reportes tomados
desde el lobby después de la partida:

| Dispositivo | Ventana Firestore | Lecturas observadas | Reglas estimadas | Escrituras intentadas | Bytes recibidos/enviados |
| --- | ---: | ---: | ---: | ---: | ---: |
| Anfitrión A56 | 183 s | 78 | 48 | 20 | 290212 / 138725 |
| Invitado | 146 s | 37 | 18 | 4 | 167202 / 37585 |

Los bytes pertenecen a ventanas propias de 131 s y 112 s respectivamente y
abarcan todos los servicios de la aplicación. Firestore incluye entrada,
preparación e inicio, juego y vuelta al lobby. No son del mismo intervalo que los
bytes y las dos ventanas entre dispositivos tampoco coinciden exactamente.

Diez publicaciones confirmadas del anfitrión: mediana observada 518 ms y p95
estimado por el reporte 561 ms. La muestra no establece un límite de usuarios,
facturación total ni la carga de salas simultáneas. No multiplicar el invitado
por cuatro como si cada rol tuviera idénticas operaciones; tampoco sumar las
reglas estimadas como lecturas verificadas de facturación.

Los emuladores confirman `estado=finalizada` aproximadamente a las 02:53:27 y
vuelta a `esperando` con cinco miembros a las 02:53:28. El reporte conservó
`DIA_DEBATE:17` porque la sesión puede decidir un ganador desde esa fase. No fue
una salida prematura de la partida. En 0.1.45, el reporte muestra RESULTADO cuando
la sesión ya tiene ganador y registra el conteo del lobby a partir de una lista
de jugadores confirmada. Este ajuste es diagnóstico y no cambia fases del juego.

Hubo tres rechazos transitorios del chat de espectadores inmediatamente tras
la muerte de un jugador, antes de que el registro RTDB de permisos reflejara su
estado. Los tres clientes recuperaron automáticamente la escucha: a las
02:52:17.711, 02:52:58.138 y 02:53:15.953 llegaron snapshots autorizados. El caso
más lento duró aproximadamente 1.6 s. No se probó envío de mensajes de muertos
en esta partida; sigue pendiente comprobarlo junto a recuperación en quince.

No repetir esta partida de cinco solo por el encabezado del reporte. Próximo
paso: preparar quince dispositivos con la última versión, anfitrión A56 y al
menos otra cuenta registrada para relevo. Reiniciar medición en anfitrión e
invitado antes de entrar a una sala nueva de quince a ritmo normal. Guiar primero
recuperación de invitado y luego recuperación/cambio de coordinador durante
partida, tomando también reporte del nuevo coordinador si hay relevo.

### Preparación de quince, 30 de septiembre

A56 anfitrión y emulador 5555 como invitado de medición. Ambos en 0.1.45;
sus snapshots de servidor coinciden en quince miembros activos y conectados.
Primera prueba solicitada: invitado en segundo plano treinta segundos durante
discusión, anfitrión abierto; comprobar fase vigente y controles al regresar.

El buscador ahora descarta salas cuyo documento informa cero participantes,
incluso si es reciente. Cambio preparado en fuente después de instalar 0.1.45;
no reemplazar APK durante esta prueba. Actualizar consulta el servidor, pero
`jugadoresActuales` cuenta lugares reservados, no conexiones RTDB. Por tanto esta
corrección no detecta por sí sola una sala con lugares reservados y todos ausentes.
La presencia RTDB es privada a miembros: el buscador no puede consultar ni borrar
salas ajenas. Sigue pendiente un resumen público de disponibilidad verificado por
el backend o la limpieza central, conservando tres minutos para reconectar al
anfitrión. No anunciar esa detección como implementada ni agregar consultas de
todos los jugadores por cada sala, por su costo y por los permisos necesarios.

El usuario confirmó recuperación inmediata de 5555 tras treinta segundos en
segundo plano. Al pausar A56, 5635 asumió coordinación: solicitud 03:32:47.816,
confirmación 03:32:48.534 y roles completos 03:32:48.769. El usuario estimó unos
cinco segundos de recuperación del A56, con tarjetas de todos «reconectando» al
principio. Registro disponible: Activity retorna a las 03:33:46.085, reconoce
relevo y reinicia escucha a las 03:33:46.498; aplica estado vigente de debate a
las 03:33:57.005. El intervalo del registro no es una medición controlada de la
latencia visual informada por el usuario. Hubo rechazo transitorio de escucha
de acciones del anterior anfitrión antes de reconocer el relevo; siguió jugando.
Ganó el pueblo en ronda cinco. A56 y 5555 aplicaron RESULTADO a las 03:35:14.

5555 y 5635 confirman vuelta a la misma sala: quince activos/conectados, A56
otra vez anfitrión y LISTO restablecido a cero. Se pidieron reportes finales de
A56, 5555 y 5635 antes de revancha. ADB inalámbrico del A56 dejó de estar disponible
después del bloqueo; registros previos guardados en output privado. Pendientes:
analizar aviso transitorio de todos reconectando, revancha completa y recuperación
del coordinador sustituto durante recuento/noche con acciones habilitadas.

Se preparó texto breve para eliminado («Observá la partida»), preservando
«El Oráculo te dio voz» cuando corresponde, y tamaño mayor en su tarjeta.
Se evita que `privateHintText()` reintroduzca la descripción larga después de
`renderPersonalStatus()`. Compilación Kotlin y recursos correctos; pendiente
verificación visual en próximo APK. No reinstalar durante prueba actual.

### Reportes finales de quince en 0.1.45

Sala ***HM4, partida 350db332, RESULTADO:50, ronda cinco, victoria del pueblo.
Los reportes del usuario corresponden a A56 (creador), 5555 (invitado) y 5635
(invitado que coordinó el final; volvió a invitado tras devolver control al lobby).

| Dispositivo | Ventana Firestore | Lecturas observadas | Reglas estimadas | Escrituras intentadas | Ventana de red | Recibidos | Enviados |
|---|---:|---:|---:|---:|---:|---:|---:|
| A56 | 1651 s | 616 | 460 | 86 | 1019 s | 2248038 B | 659457 B |
| 5555 | 1663 s | 200 | 149 | 30 | 1040 s | 1211533 B | 188949 B |
| 5635 | 1686 s | 285 | 142 | 42 | 1104 s | 1335528 B | 256955 B |

No son quince dispositivos ni el total facturado. Firestore incluye la espera
previa, preparación, partida, recuperación y operaciones posteriores; los bytes
tienen una ventana más corta y abarcan servicios de toda la app. Las reglas son
estimaciones, y las escrituras, intentos instrumentados. No extrapolar un único
rol invitado a los otros doce ni determinar capacidad concurrente con esta prueba.

A56: 29 publicaciones confirmadas, p50 549 ms y p95 estimado 600 ms. 5635: cinco,
p50 587 ms y p95 estimado 625 ms. Los eventos cambian de `host_publish` a
`guest_apply` en A56, y de invitado a publicaciones de host en 5635. Confirman el
relevo observado en logs y llegada al mismo resultado en ambos roles.

A56 atribuye 317 de 616 lecturas observadas a `partida/actions`, 160 a
`lobby/players` y 38 a resolución de votos. 5555: 129 a jugadores del lobby y 28
a acciones. 5635: 131 a jugadores, 93 a acciones y 15 a recuperar roles de host.
Los invitados registraron 21 y 22 intentos de cambiar voto; el reporte por sí solo
no determina si hubo cambios deliberados o reintentos. Emotes/chat siguen RTDB.

Los reportes de invitados conservaron 14/15, pero snapshots posteriores confirman
15 activos y conectados en la misma sala. El usuario no quiere dedicar pruebas al
aviso visual transitorio «todos reconectando»: no convertirlo en requisito de salida.
Sí siguen pendientes revancha completa y relevo del coordinador sustituto cerca
de recuento/noche. A56 ya volvió a estar disponible por ADB inalámbrico.

Cancelar LISTO ya existe en `toggleCurrentOnlineReady()`, con transacción de dos
documentos y una escritura del miembro. La presentación bloquea invitados cuando
todos están listos; el host usa ese mismo botón para iniciar. Habilitar cancelación
no agregaría escuchas ni sondeo periódico, pero cada pulsación sí implica lecturas
de transacción, cambios recibidos en listeners y reglas/reintentos. El usuario
prefiere no añadirla si aumenta lecturas: no se cambió ese bloqueo en esta revisión.

### Revancha de quince, cierre de pruebas del usuario

Misma sala ***HM4, partida nueva 3dcdebf6, RESULTADO:30, ronda tres. Ambos reportes
indican 15/15. Logs de 5555 y 5635 confirman regreso a la misma sala a las
03:54:03, creador A56, quince miembros activos y LISTO a cero. Snapshot A56 de
servidor confirma quince conectados tras reentrada. No equiparar este resultado
con la prueba pendiente de relevo del coordinador sustituto.

| Dispositivo | Ventana Firestore anterior → nueva | Incremento lecturas observadas | Incremento reglas estimadas | Incremento escrituras intentadas |
|---|---:|---:|---:|---:|
| A56 | 1651 → 2498 s | 200 | 112 | 42 |
| 5555 | 1663 → 2533 s | 54 | 47 | 6 |

El incremento abarca 847/870 segundos incluyendo espera entre partidas, nueva
preparación, juego y regreso. No son lecturas exclusivamente de la revancha ni
de toda la sala. A56 publicó catorce estados, p50 532 ms y p95 estimado 574 ms.
Su red de aplicación: 1156196 recibidos / 340052 enviados en 481 s; invitado:
768932 / 107701 en 526 s. Estas ventanas tampoco coinciden con Firestore.

Hubo otro relevo al 5635: 03:51:37.192 solicitud, 03:51:37.489 confirmación,
03:51:37.655 recuperación de quince roles en NOCHE_ASESINO, ronda tres.
A las 03:51:41.147 confirmó cinco de cinco acciones nocturnas y avanzó al
amanecer. El usuario no indicó una interrupción deliberada en esta revancha;
no atribuir el relevo al bloqueo automático sin evidencia adicional.

En el relevo se rechazaron transitoriamente cuatro intentos de avisos del plan
de asesinos, a las 03:51:37–38, mientras cambiaba autoridad RTDB. La resolución
nocturna sí funcionó. Revisar secuencia de confirmación de permisos/publicación
antes de liberar; no afirmar que esos avisos finalmente se entregaron. El log del
A56 ya no conserva ese intervalo. El usuario terminó pruebas por hoy. Plan de
cierre y carga real documentado en `cierre-beta-2026-09-30.md`.
