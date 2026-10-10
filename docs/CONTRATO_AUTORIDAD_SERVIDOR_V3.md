# Autoridad de servidor: contrato y orden de migración

Actualizado el 6 de octubre de 2026. Documento para revisión conjunta con Claude.
Las decisiones de `ios/docs/CASOS_PARIDAD_SERVIDOR.md`, sección 0, son vinculantes.

## Resultado buscado

El creador administra el lobby y pide iniciar. Después, ningún teléfono reparte secretos,
resuelve acciones, cambia de fase, expulsa por AFK ni declara al ganador. El servidor conserva
el motor completo; cada teléfono recibe el estado público y únicamente sus datos privados.
Si el creador se desconecta, la partida sigue por sus plazos, sin traspaso de autoridad.

La migración es un protocolo nuevo (`protocolVersion=3`, `authorityMode=server`). No se
activa en una sala con clientes antiguos ni a mitad de una partida. Las salas actuales siguen
usando su protocolo hasta que terminen. Publicar Functions por sí solo no migra los clientes.

## Reglas cerradas

- Mercenario: `ultimaRondaSilenciado` se escribe cuando el silencio se aplica. La noche
  siguiente ese objetivo es ilegal y su carta está deshabilitada. Un objetivo protegido
  por Médico o muerto esa noche no consume el cooldown.
- Alcalde silenciado: no habla, vota, se revela, decide ni activa corrupción en el segundo
  empate. Una revelación anterior permanece visible. Si está silenciado o muerto en secreto, se
  abre igualmente `ALCALDE_DESEMPATE`, por el mismo plazo, y vence sin expulsar a nadie.
  Solo se saltea si su incapacidad ya es pública: revelado y silenciado, o muerte con
  rol público. Esto evita revelar su rol por la duración de la fase.
- Oráculo: una invitación válida se resuelve usando quienes estaban vivos al empezar la
  noche. Si el Oráculo muere esa noche, el invitado conserva el derecho a hablar en el debate.
  No vota ni actúa, y el permiso vence al terminar ese debate.
- Desertor sin elección inicial: al vencer `REPARTO`, el servidor elige `Pueblo` o
  `Traidores` con `crypto.randomInt(2)` y persiste la elección en la misma transacción
  que abre la primera noche. No usa código de sala, nombres ni un hash calculable por
  clientes. Los reintentos de esa transacción reutilizan la misma extracción privada.
  Una elección explícita anterior se conserva; después, no responder conserva el bando.
- Desertor: antes de cerrar una victoria de Traidores, si está vivo, eligió bando, no usó
  su reconsideración y la ronda es `>= 4`, se abre una ventana obligatoria de resolución.
  También puede revisar durante `DIA_DEBATE` desde la ronda 4 (tres días completos).
  El umbral de supervivientes anterior queda eliminado. Estar silenciado no impide
  esta decisión privada. Puede mantener o cambiar; ambas consumen el único uso. El plazo es
  `votacionSeg` de esa sala. Vencer equivale a mantener y no suma AFK. Se reevalúa el ganador
  inmediatamente. Una victoria de Pueblo nunca espera esa ventana.
  La ventana guarda internamente su fase de retorno. Si un abandono rompe la paridad,
  se cierra sin consumir el cambio ni modificar el bando: desde `AMANECER` abre debate;
  desde `RESULTADO` abre la noche de la ronda siguiente. Otras fases interrumpidas por
  abandono recuperan su tiempo restante y las intenciones de quienes siguen en la sala.
  No repite resolución nocturna, recuento, expulsión ni AFK. Una salida que mantiene la
  paridad no reabre la ventana ni extiende su plazo.
- Salida voluntaria durante la partida: eliminación inmediata con causa `ABANDONO`;
  no concede victoria de bando ni especial y se registra como derrota en historial y
  estadísticas. Se aplica también a quien ya murió o al Bufón previamente expulsado:
  salir antes del final no permite esquivar una derrota. Perder conexión permite volver y no es
  una salida voluntaria. Salir después del resultado no reescribe la victoria ya obtenida.
- Una victoria legítima por abandono masivo mediante AFK cuenta para historial y
  estadísticas; la cancelación cuando todos son expulsados por AFK no cuenta.

## Motor y mensajes

Motor puro Node.js, sin Firebase ni reloj del teléfono. Recibe estado, intención autenticada
y hora del servidor; devuelve el estado siguiente o un rechazo. Las pruebas usan el mismo
motor de producción y las semillas deterministas de Android para los desempates.

Solicitud de acción:

```json
{
  "roomId": "...",
  "matchId": "...",
  "phaseIndex": 12,
  "requestId": "UUID generado una sola vez y reutilizado al reintentar",
  "action": "votar",
  "targetUid": "..."
}
```

`uid` del actor sale de Firebase Auth. App Check se exige en las callables. No se aceptan
roles, resultados, votos ajenos, fechas ni duración elegidos por el cliente. Se valida
identidad, pertenencia, fase, plazo, vida, silencio, rol, objetivo, poder y cooldown.

Un reintento idéntico conserva el resultado mientras permanezca en la ventana de los
últimos 180 recibos del motor; fuera de ella una solicitud de una fase vieja se rechaza. Reutilizar `requestId` con otro contenido se
rechaza. Otra partida/fase no puede reutilizar una acción vieja. Se limita a 12 intenciones aceptadas por jugador y fase. Además, un bucket compartido
por UID permite una ráfaga de 8 solicitudes y repone una cada 2 segundos (30/minuto),
entre todas las callables V3. Un rechazo de juego consume el bucket; Auth ausente o
envelope inválido se rechazan antes de leer Firestore. No hay un documento por toque.
`onlineRequestLimits/{sha256(uid)}` es exclusivo de Admin: un documento por cuenta,
borrado al eliminar Auth. `expiresAt` está preparado, pero la política TTL todavía
debe configurarse en Google Cloud; el campo solo no lo borra.

La noche nueva tiene un plazo común y acciones privadas por rol. El dispositivo presenta
su propia habilidad; saber si otro jugador ya actuó no se publica. No termina antes por
acciones secretas, para no revelar quién tiene un poder. Votos y habilidades del día se
resuelven por el servidor. La UI debe representar explícitamente `NOCHE` y
`DESERTOR_RECONSIDERACION`; no convertirlas a fases desconocidas con un fallback local.

## Persistencia, privacidad y plazos

### Presentación del resultado diurno (8/10)

La eliminación por voto se aplica al **entrar en RESULTADO**, tanto por mayoría
como por decisión/corrupción del Alcalde. Una publicación coherente trae muerte,
causa `VOTE`, `DAY_EXPULSION`, rol público si corresponde y victoria especial del
Bufón. No se elimina a nadie durante el recuento. El ganador se evalúa al vencer
RESULTADO; un abandono durante esa fase ya considera al expulsado muerto.

Con expulsión, el plazo es `max(transitionSeconds, 8)` segundos; con Bufón,
`max(transitionSeconds, 12)`. Sin expulsión conserva `transitionSeconds`.
`victoriasEspeciales` es público desde la expulsión del Bufón, incluso con roles
ocultos; el bando del Desertor conserva su privacidad hasta el final.
El recién expulsado no puede enviar al chat de muertos durante RESULTADO.

El cliente presenta el resultado al recibirlo: mantiene la carta del expulsado y
los textos anteriores hasta el impacto de la bota. El rol público aparece dentro
de la ceremonia, y el Bufón después de la expulsión. Esta retención solo afecta
presentación; permisos e intenciones usan siempre el snapshot real. La publicación
es atómica, pero la recepción entre teléfonos depende de la red y no es simultánea
al milisegundo. No hay nuevas publicaciones ni sincronización por animación.

Reingreso con evento visto: cuadro final sin repetir sonido ni ceremonia; con
evento nuevo y poco tiempo: versión comprimida o cuadro final. Las ceremonias del
amanecer pueden seguir durante el debate, con continuación automática y un máximo
de seis segundos por ceremonia. No se pausa el reloj ni se tapa un plazo vencido.
Recuento agregado, roles y ventanas públicas conservan las restricciones de V3.

- Firestore: `partidas/{roomId}/servidor/current`, estado completo exclusivo de Admin;
  `partidas/{roomId}/serverOutbox/current`, publicación y vencimiento durables. Reglas
  `false` para todo cliente, incluido el creador. Una transacción serializa las decisiones.
- RTDB: namespace nuevo `onlineV3/{roomId}`. `public` contiene estado público; `private/{uid}`
  es legible solamente por ese UID. Permisos de chat y presencia los decide el servidor.
  No reutilizar `salas/{roomId}` para secretos: su regla de lectura del anfitrión concede
  acceso a todo el árbol y una regla más profunda no puede revocarlo.
- Cloud Tasks: vencimiento de fase identificado por `roomId + matchId + phaseIndex + deadline`.
  Tarea duplicada, antigua o entregada tarde es inocua. Un cambio de voto puede aumentar la
  versión de estado; **no** debe invalidar la tarea del plazo de esa fase.
  La cola y su worker se ubicarán en `southamerica-east1`: la lista oficial de Cloud Tasks
  incluye São Paulo y no incluye Santiago. Las callables y el trigger de outbox pueden
  permanecer junto a Firestore en Santiago. [Regiones oficiales](https://docs.cloud.google.com/tasks/docs/locations).
  Tasks admite entregas duplicadas y retrasos, incluso de minutos; no es un reloj exacto.
  Se requiere recuperación autenticada de una fase vencida desde cualquier participante,
  con espera escalonada y reintentos limitados; el mismo motor decide si corresponde avanzar.
  El cliente muestra que se está resolviendo al llegar a cero y bloquea acciones tardías.
  [Limitaciones de ejecución](https://docs.cloud.google.com/tasks/docs/common-pitfalls),
  [configuración de Tasks con Firebase](https://firebase.google.com/docs/functions/task-functions).
- Reloj: `limiteFaseEpochMs` inmutable durante la fase. Cuenta regresiva en el teléfono con
  offset de servidor; ninguna escritura de reloj por segundo. Una acción exactamente en
  el vencimiento es tardía. El servidor avanza aunque todos los teléfonos se cierren.
- Firestore, RTDB y Tasks no son atómicos juntos. La transacción escribe un outbox durable;
  su consumidor reintenta publicación/encolado y nunca reemplaza una proyección nueva por
  una vieja. La función de vencimiento no confirma éxito si falla el commit.
- Final: resultado calculado por servidor, mismo `matchId` para historial idempotente;
  cancelación por AFK no genera victoria ni historial. La limpieza debe incluir V3 y
  proteger partidas activas, outbox pendiente y resultados aún sin archivar.

Los siete endpoints ya se exportan desde `functions/src/index.js`, con `minInstances=0`:

| Endpoint | Región | Responsabilidad |
| --- | --- | --- |
| `iniciarPartidaV3` | Santiago | Solo `hostId`, todos los participantes compatibles y listos |
| `accionPartidaV3` | Santiago | Intención autenticada, validación y recibo idempotente |
| `recuperarFaseV3` | Santiago | Cualquier participante puede pedir recuperación, tras 5 s de gracia |
| `prepararRevanchaV3` | Santiago | Solo creador, resultado archivado y última publicación entregada |
| `abandonarPartidaV3` | Santiago | Salida voluntaria y revocación de acceso |
| `publicarPartidaV3` | Santiago | Trigger Firestore con reintentos sobre el outbox actual |
| `resolverFaseV3` | São Paulo | Worker privado de Tasks; no endpoint abierto a jugadores |

Inicio recibe `{roomId}`; revancha/salida `{roomId, matchId}`; recuperación
`{roomId, matchId, phaseIndex}`. Región callable `southamerica-west1`; Tasks
`southamerica-east1`.

Las tareas se programan a `deadline + 1500 ms`. Si llegan temprano, el worker devuelve
error reintentable, nunca éxito. Si el commit ya ocurrió pero falló publicar/encolar,
el siguiente intento publica el outbox actual sin resolver dos veces. La cola limita
20 entregas concurrentes y 20/s, hasta 8 intentos por tarea; worker máximo 2 instancias.
Si se agotan todos esos intentos, la recuperación de un participante sigue disponible;
antes de habilitar V3 deben quedar configuradas la alerta y la reparación operativa
de tareas agotadas, incluyendo el caso sin participantes conectados.
Recuperar devuelve `waiting` con `retryAfterMs`, `advanced` o `current`. El cliente
Android ya escalona y limita la recuperación: 5 s de gracia, dispersión por UID
de hasta 2 s y hasta tres intentos por fase. iOS todavía debe integrar esta lógica.

`onlineMaintenance/serverAuthority.enabled=true` es una llave exclusiva de Admin
para permitir inicios nuevos. Si falta o vale false, el modo no se inicia. Deshabilitarla
no detiene partidas existentes ni rompe sus reintentos. Las listas Admin opcionales
`allowedHostUids` y `allowedRoomIds` restringen los inicios a cuentas y salas de QA;
una lista vacía o mal formada no autoriza ningún inicio. Se verificaron en Cloud el
7/10 con una sala temporal; al terminar se restauró el gate cerrado. No hay apertura
general de V3. Ver `docs/DESPLIEGUE_V3_CLOUD_2026-10-07.md`.

La revancha crea otro `matchId` y una generación nueva, limpia poderes/acciones,
marca a todos como no listos y conserva el historial de la partida anterior. El lobby
permanece bloqueado hasta que RTDB quite secretos y permisos anteriores; entonces
`authorityMode=lobby` permite las escrituras de lobby validadas. Las tareas viejas
no pueden afectar la siguiente partida. Durante el juego, ni el creador puede
modificar estado, membresía o indicador de autoridad directamente.

## Orden y criterios de aceptación

1. Paridad de los cuatro cambios en Android/iOS locales y motor servidor con pruebas.
2. Servicio transaccional, outbox, Tasks, reglas y pruebas contra emuladores reales.
   Probar acción/vencimiento concurrentes, duplicados, reconexión y caída de publicación.
   El chat V3 ya valida identidad, canal, fase, permiso, tamaño, timestamp,
   frecuencia y retención; la prueba nativa se ejecuta separadamente de las reglas.
3. Cliente Android V3: reutilizar presentación, retirar resolución local y promoción de
   anfitrión para este modo, leer proyección pública/privada y enviar intenciones.
4. Cliente iOS V3: mismo contrato, sin ejecutar `ClassicGame` como árbitro online. Activar
   salas reales solo cuando el adaptador y gameplay estén integrados.
5. Ensayo nativo con dos plataformas, creador desconectado y recuperación. Revancha con
   nuevo `matchId` y todos los poderes, cooldowns y contadores reiniciados.
6. Despliegue y habilitación V3 graduales después de esos criterios. Mantener el protocolo
   actual mientras la migración no cumpla su aceptación completa.

## Consumo y tarea de revisión para Claude

Son **tres listeners por participante**: `snapshot/public`, `snapshot/private/{uid}` y
`snapshot/permissions/{uid}`; presencia/chat tienen sus propios listeners acotados en RTDB. Los secretos
de noche no se retransmiten a todos. Transacciones cortas, `minInstances=0`, límites de
instancias y reintentos. Las lecturas del motor no crecen como un listener por cada rol.

Una intención válida lee normalmente dos documentos del motor (sala y estado) y
escribe estado + outbox. El bucket añade una lectura/escritura de Firestore. Cada
publicación lee sala/outbox, confirma el marcador con otra transacción y consulta
en RTDB únicamente `queuedTaskId` antes de la transacción del snapshot completo.
Esta transacción todavía descarga y escribe un snapshot con todas las proyecciones
privadas en el servidor: no se confunde con los bytes que cada jugador recibe.
Los reintentos de transacción y la entrega del trigger que actualiza el marcador
también deben entrar en la medición.

La revisión pública solo aumenta con cambios públicos; cada revisión privada
aumenta únicamente si cambian los datos de ese UID. Los recibos de las callables
usan la revisión pública, nunca el contador secreto del motor. Una acción privada
no provoca entrega del listener público ni otra tarea para el mismo plazo.
Los anuncios usan `eventosPublicos: [{seq, codigo, ronda, jugadores, texto}]`. `seq`
es entero positivo, estrictamente creciente por partida; no se reinicia al recortar
el anillo interno de 60 eventos. La proyección transmite solamente la ronda actual.
Los códigos incluyen `AFK_EXPULSION` (con UID), `NIGHT_START`, `COUNTERPOINT_OPEN`
(con participantes), `DESERTER_WINDOW`, `DESERTER_WINDOW_CLOSED`, `VICTORY`,
`MATCH_CANCELLED` y `MAYOR_NO_DECISION`, además de los anuncios de votos/Oráculo.
No se crean eventos por intenciones secretas. Android/iOS presentan esos eventos sin
inventar resultados; efectos una sola vez por `matchId + seq`, cambios de fase por
`matchId + phaseIndex`. Guardar el último `seq` recibido al reconectar/recrear la
pantalla. Un nuevo `matchId` descarta ese cursor y todas las intenciones anteriores.

Los logs `online_v3_operation` incluyen tiempo, intentos de transacción, cambios
públicos/privados y `projectionBytes`. Este último mide JSON del snapshot final,
**no consumo facturado**. No se registran roles, intenciones, nombres ni tokens.

Medir partidas completas de 5, 10 y 15 jugadores: lecturas/escrituras Firestore incluyendo
reintentos, bytes RTDB por receptor, descargas de foto con/sin caché, invocaciones/tiempo
Functions, operaciones Tasks, p50/p95 y errores. Contar también lobby, revancha y
desconexiones. Convertir a costo con tarifas vigentes después de medir; no prometer un
precio por jugador con el agregado mensual actual. El presupuesto de USD 5 es una alerta.

Claude puede revisar este contrato y ejecutar los casos de paridad con énfasis en DES-09,
DES-12 a DES-14, ALC-08, ORA-06, AFK-06 y revancha. Entregar discrepancia, reproducción y
resultado esperado. La implementación de backend, reglas y adaptación online la conduce
Codex. Para esta revisión, evitar cambios en esos archivos compartidos; las propuestas
de presentación de iOS pueden quedar como comentario/documento hasta fijar los DTO V3.

## Estado de esta entrega (revisión del 5 de octubre)

Implementado en el árbol de trabajo, pendiente de commit/despliegue:

- Motor V3 y paridad local Android/iOS para Mercenario, Oráculo, Alcalde y Desertor.
  El temporizador local de iOS también vence la decisión opcional del Alcalde.
- Callables, trigger de outbox, worker Tasks, recuperación autenticada y gate Admin.
- Salida voluntaria, revancha segura y protección de historial antes de resetear.
- Contadores públicos/privados separados y anuncios estructurados.
- Límite distribuido por UID, aislamiento de secretos y limpieza V3 que conserva
  partidas vivas sin depender de presencia del creador.

Verificación: 203 pruebas de motor Android y APK Debug; 90 pruebas del núcleo iOS
y build Debug de simulador; 52 unitarias backend; 15 integraciones V3 y 11 de
limpieza anterior; reglas anteriores Firestore/RTDB; 51 verificaciones específicas
de aislamiento V3 y desbloqueo de revancha.

La prueba HTTP utiliza Auth, App Check de emulador, Functions, Firestore, RTDB y
Tasks locales. Comprueba gate cerrado, rechazos de Auth/App Check, inicio/reintento,
recibo duplicado, aislamiento, recuperación temprana y revocación al salir. La tarea
avanzó REPARTO → NOCHE sin ninguna llamada de cliente durante la espera. El emulador
entregó antes del plazo y el worker devolvió error reintentable hasta vencer: cubre R-02.
**No certifica IAM ni entrega de Tasks en Google Cloud, ni gameplay nativo V3.**

Comprobaciones reproducibles (Node 22, JDK de Android Studio):

```sh
npm --prefix functions run test:unit
npm --prefix functions run check
npm run test:authority
npm run test:authority-http
sh ./gradlew :app:testDebugUnitTest :app:assembleDebug
bash ios/Scripts/test_core.sh
```

El archivo `firebase.authority-qa.json` usa puertos separados de los emuladores de
Claude. No ejecutar los tests que inyectan cola junto al trigger Functions real:
son dos suites distintas para evitar interferencia en las publicaciones.

**Siguiente bloque:** integrar la presentación habitual de Android, adaptar gameplay
iOS, ensayar ambas plataformas, medir partidas completas con 5/10/15 jugadores y
probar Tasks/IAM real. Luego despliegue y habilitación gradual del gate. La
infraestructura productiva no se cambió: el online publicado conserva la autoridad
anterior; Android solicita V3 únicamente en una compilación Debug explícita de QA.

Para Claude: revisar nuevamente R-01/R-02/R-03/R-04/R-05/R-09/R-10 con estos
endpoints y DTO; actualizar la sonda de Desertor a ronda 4. DES-12 ahora prueba
creador desconectado durante la ventana, sin traspaso de autoridad. Revisar UI
propuesta para NOCHE, reconsideración, abandono y revancha; no modificar backend
ni reglas en paralelo. No hace falta volver a decidir las reglas confirmadas.


## Cliente Android y chat: avance del 6 de octubre

**Estado posterior, 8 de octubre:** Android ya reutiliza el layout y los animadores
de la mesa habitual. La descripción de pantalla experimental de este apartado
documenta el estado del día 6, no el actual. Ver `VENTANAS_V3_ANDROID_2026-10-08.md`
y `PARIDAD_MESA_V3_ANDROID_2026-10-08.md`. iOS también cuenta con un adaptador V3
en desarrollo; la prueba mixta y la habilitación Release siguen pendientes.

Esta entrega añade una pantalla **experimental separada**, `ServerGameplayActivity`.
Es una presentación funcional del protocolo, con Firebase SDK real, no un motor local:
recibe tres proyecciones coherentes y envía intenciones. Aún falta integrar la presentación
habitual del gameplay; no representa una migración visual terminada ni habilita la beta.

- `ServerGameContract`: DTO estrictos, fases explícitas, ensamblado de los tres listeners,
  acciones disponibles, recibos y recuperación. Se rechazan protocolos, partidas y fases
  incoherentes. `permissions/{uid}` incluye `phaseIndex`; una fase nueva se presenta solo
  cuando público, privado y permisos corresponden a esa fase. Un rol ajeno nunca sale
  de un fallback local. El resultado final viene del servidor y no lo escribe el teléfono.
- `ServerGameClient`: callables V3, listeners, presencia por conexión/desconexión y
  reintentos con el mismo UUID. Detiene listeners al salir de primer plano. No hay
  heartbeat por segundo. La pérdida de acceso elimina de la pantalla el estado privado.
- Lobby/recuperación: reconoce `authorityMode=server`, evita reparto local, traspaso de
  autoridad, consulta de roles Firestore anteriores y reset local de revancha. Espera
  la publicación limpia del nuevo `matchId` para volver al lobby. Al recuperar V3, lee
  la autoridad de la sala antes de intentar escribir presencia del protocolo anterior.
- `FirebaseEmulatorConfig.database`: todos los consumidores Android reutilizan la
  misma instancia de Realtime Database configurada; las pruebas nativas detectaron que
  volver a obtener una instancia podía perder el ruteo al emulador.
- `SERVER_ONLINE_V3`: deshabilitado por defecto; solo Debug admite la propiedad
  `-PtraidoresServerOnlineV3=true`. Release fuerza `false`. La llave Admin productiva
  sigue deshabilitada. La actividad de QA y su proveedor sintético App Check solo
  existen en Debug y exigen proyecto `traidores-local` y emuladores explícitos.

### Chat acotado y permisos

**Actualización del 8/10 — Android:** el chat público permite escribir durante
`DIA_DEBATE`, `VOTACION` y `DESEMPATE_VOTACION` a miembros vivos y no silenciados.
En `CONTRAPUNTO`, solamente a sus participantes. El muerto invitado por el Oráculo
conserva voz solo en el debate. Recuento, decisión del Alcalde y resultado son de
solo lectura en el canal público; los canales privados siguen sus permisos propios.

`onlineV3/{roomId}/chat/{publico|traidores|muertos}/{uid}_{slot}` conserva hasta
16 posiciones por UID y canal. Se sobrescribe una posición propia; nunca se permite
sobrescribir la de otro jugador. Para 15 jugadores son como máximo 240 mensajes por
canal. La pantalla escucha solo el canal elegido, con `limitToLast(60)`.

Cada envío actualiza atómicamente mensaje y `chatRate/{uid}`. El servidor valida
2 segundos entre mensajes, compartidos entre los tres canales, texto de 1–300 caracteres,
UID, `matchId`, `phaseIndex`, permiso y plazo vigente. Una escritura del ledger sin su
mensaje, dos mensajes para un mismo ledger o un envío de una fase anterior se rechazan.
El cliente lee su pequeño ledger para elegir la siguiente posición. No hay callable,
escritura Firestore ni documento nuevo por mensaje. Los permisos de silenciados, muertos,
Traidores e invitación del Oráculo los fija el servidor; la UI solo los refleja.

La publicación de `REPARTO` o `LOBBY` con nuevo `matchId` limpia chat, ledger y presencia
anterior **en la misma transacción RTDB que publica las nuevas proyecciones**. Así, un
jugador que pasa a Traidores en la revancha no recibe el chat secreto de la partida previa.
Las publicaciones normales siguen usando la transacción del snapshot, sin descargar chat.

### Emotes V3: protocolo del 8/10

`permissions/{uid}.reactions` habilita a miembros vivos y no silenciados durante
debate, Contrapunto, votación, recuento, segunda votación y desempate del Alcalde.
El usuario confirmó que **el silencio también bloquea emotes**, incluida la mesa común.
No se permiten al finalizar la partida. Un miembro puede recibir aunque no pueda enviar.

`onlineV3/{room}/reactions/{uid}_{slot}` contiene exclusivamente
`{actorUid, matchId, phaseIndex, slot, emoteId, ts}`. Hay ocho slots por UID.
Cada envío actualiza atómicamente esa posición y `reactionRate/{uid}`; las reglas
validan propietario, pertenencia, permiso, partida, fase, plazo, timestamp del servidor,
catálogo cerrado de doce emotes base, diez segundos entre envíos y dos usos por ronda.
La modificación aislada de mensaje o ledger se rechaza. Premium y «6 7» quedan fuera.

Android reutiliza la paleta y las burbujas de la mesa común. Añade un listener RTDB
indexado por `ts`, limitado a 40 elementos, solamente en primer plano y fases de
reacción. Al reconectar escucha desde la hora del servidor actual, sin animar el
anillo antiguo. Deduplica por `matchId:actorUid:slot:ts` y absorbe la corrección
del timestamp optimista del SDK del mismo slot dentro del intervalo mínimo.
El nuevo matchId limpia reacciones y ledger junto al chat y la presencia.

No hay callable ni escritura Firestore por emote: una lectura del ledger y una
actualización RTDB de dos rutas por envío. El tráfico se distribuye entre miembros.
La medición con 5/10/15 receptores está en
`AJUSTES_MESA_EMOTES_V3_ANDROID_2026-10-08.md`: es JSON de SDK en emulador por
ronda de máxima participación, no la factura ni los bytes de una partida completa.

### Pruebas y reproducción Android

Se generan 53 snapshots del motor Node para 5/10/15 jugadores y los tres mapas,
incluyendo elecciones privadas, silencios, cooldown, Oráculo, resultado y abandono.
Nueve pruebas Kotlin consumen ese contrato real, ordenan listeners, rechazan revisiones
viejas, validan acciones y prueban recibos/reintentos. Las semillas son material exclusivo
de tests y no se empaquetan en el APK.

Verificación de cierre: 709 pruebas Android sin fallos (incluidas las 9 de contrato),
APK Debug y compilación Kotlin Release; 52 unitarias backend sin fallos; 15 integraciones
V3 y 62 verificaciones específicas de reglas. El ensayo nativo completo terminó con
código 0: inicio, tres proyecciones, intención privada, avance con app cerrada,
reconexión pasando por el lobby, chat, resultado, historial por cuenta y revancha limpia.

El ensayo nativo usa un Android emulado y cinco usuarios de Auth Emulator. Los otros
cuatro participantes se manejan por callables autenticadas para verificar el resultado.
Esto no sustituye una partida completa con cinco teléfonos ni certifica Tasks/IAM real.

Preparación temporal de Debug y ejecución (Node 22, JDK de Android Studio, Android SDK):

```sh
node scripts/prepare-server-client-android.cjs prepare
sh ./gradlew :app:assembleDebug -PtraidoresServerOnlineV3=true -PtraidoresOnlineAuthorityEmulator=true
adb -s emulator-5558 install -r app/build/outputs/apk/debug/app-debug.apk
TRAIDORES_QA_ADB=/ruta/al/Android/sdk/platform-tools/adb TRAIDORES_QA_DEVICE=emulator-5558 node scripts/with-jdk.cjs firebase emulators:exec --config firebase.authority-qa.json --project traidores-local --only auth,firestore,database,functions 'node scripts/test-server-client-android.cjs'
```

Después, también si el ensayo falla, quitar la configuración temporal y reconstruir:

```sh
node scripts/prepare-server-client-android.cjs cleanup
sh ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin
```

El helper nunca cambia `app/google-services.json`. No subir el archivo temporal
`app/src/debug/google-services.json`. Los puertos QA están separados de los de Claude.

### Pendiente antes de habilitar V3

1. Completar integración visual Android y revisar accesibilidad de la pantalla habitual.
2. Para la beta iOS (no bloquea publicar Android): completar la validación del
   adaptador V3, permisos/fase, reconsideración, salida, recuperación y revancha;
   probar Android + iOS juntos.
3. Repetir la medición Cloud de sesiones completas y reconexiones de 5/10/15 con
   esta entrega, incluidos chats y fotos. Ver `MEDICION_V3_BETA_ANDROID.md` para la
   medición anterior; los tamaños de fixture/logs no equivalen al consumo facturado.
4. Ensayo controlado de Tasks/IAM y alertas/reparación cuando se agotan los reintentos.
5. Despliegue gradual y gate, una vez que estas pruebas estén aprobadas.

Claude puede revisar el contrato de cliente, permisos por fase y limpieza de chat de la
revancha. No hace falta volver a decidir las reglas ya confirmadas por el usuario.


## Correcciones de la revisión del 6 de octubre

La revancha `authorityMode=lobby` permite configuración, presencia y «listo», pero
el cliente no puede cambiar/eliminar `authorityMode`, `protocolVersion`, `estado`,
`partidaInicialCreada`, `partidaInicial`, `estadoPartida`, `preparedMatchId`,
`serverGeneration`, `rematchGeneration` ni `rematchOf`, ni borrar y recrear la sala
como legacy. El backend conserva inicio, generación y reset. Las reglas también
impiden crear una sala con marcadores de autoridad o revancha falsificados. El
cliente que ya reconoció V3 rechaza una sala degradada, sin arrancar el motor local.

En toda sala con `protocolVersion=3`, cada alta o actualización de
`jugadores/{uid}` debe conservar `protocolVersion=3` y `puedeArbitrar=false`.
Ningún participante ni el creador pueden quitar la versión al editar o sustituir
el documento. Se comprueba la sala con `getAfter`, también cuando sala y creador
se crean juntos. Los documentos legacy conservan la versión opcional. Esto no
bloquea «listo», presencia ni expulsión desde el lobby.

Todo vencimiento que devuelve `changed:true` aumenta `phaseIndex` o fija ganador,
y una fase activa nueva tiene un plazo futuro. El motor y la transacción verifican
esta invariante antes de escribir. Si un estado corrupto no puede avanzar, el cron
registra `online_v3_recovery_stuck` y no escribe estado/outbox ni lo publica; solamente
puede guardar el cursor del lote. Requiere alerta y reparación operativa antes de
habilitar producción: el registro por sí solo no crea una política de alerta.

Android conserva el ensamblado de las tres proyecciones y su piso de fase/revisión
entre reconexiones. Guarda el cursor de presentación en el estado de la Activity;
no repite anuncios de accesibilidad ya vistos. En la entrega del día 6 todavía
faltaba la integración visual habitual; los avances Android del día 8 están
documentados en `PARIDAD_MESA_V3_ANDROID_2026-10-08.md`.

Al reconectar, la mesa se reconstruye desde el snapshot coherente actual
(jugadores, fase, roles visibles, condición propia y resultado). Los eventos de
la ronda actual sirven para presentación; no son un registro necesario para
reconstruir fases o eliminaciones. No se reproducen eventos de rondas que el
cliente no presenció. El adaptador iOS debe seguir el mismo criterio.
