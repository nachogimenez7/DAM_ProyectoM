# Auditoría del online de Traidores — 5 de septiembre de 2026

## Alcance y conclusión

Auditoría e implementación **local**, sobre `b9d2981` y los cambios sin commit existentes. Se conservaron las correcciones previas de voto ciego, contador nocturno, ACK de entrada y autoridad del lobby. No se ejecutó deploy, publicación de APK, push ni commit. **Excepción de carga externa:** una compilación release ejecutó la tarea automática `uploadCrashlyticsMappingFileRelease` del plugin existente y terminó con éxito; no se verificó remotamente su recepción. Se trata como posible subida de símbolos R8 y no se afirma que la sesión estuvo libre de toda subida. La verificación posterior y el comando `test:android-audit` excluyen explícitamente esa tarea. No se usaron cuentas de jugadores ni bases de producción para pruebas.

Se corrigieron fallos reales de pérdida de estado, validación de votos, reconexión y limpieza. **No se considera el online resistente a un anfitrión malicioso**: el motor, los objetivos, las transiciones y los resultados continúan bajo su autoridad. Compilar y pasar emuladores no demuestra ausencia de jank ni comportamiento correcto del SDK Android en dispositivos.

## Correcciones implementadas

1. **Recuento sin pérdida por error de lectura.** Votos, noche y decisión del alcalde usan una lectura común `Source.SERVER`. Un error conserva la fase y reintenta con 1, 2, 4 y luego 5 segundos entre intentos. Se eliminó la resolución con `emptyMap()` o acciones locales al fallar el servidor. Un ticket liga la respuesta a match, ronda, fase, índice, autoridad y vida de la Activity; respuestas tardías no resuelven otra ventana. Los timers duplicados no crean múltiples lectores.
2. **Votación protocolo 2.** El checkpoint publica `protocoloVoto=2`; las reglas comprueban match, ronda, fase, actor habilitado, objetivo vivo, orden/nombre, candidatos de desempate, deadline y un ID único verificable por UID/ventana. Se mantiene la gracia de recepción existente de 1500 ms. El cierre confirmado de `votacionCerrada` precede a la consulta de votos y no puede reabrirse en el mismo índice. No se cambia la prohibición funcional preexistente de autovoto ni quién puede votar.
3. **Primer voto y reintentos.** Se preserva creación ciega → actualización atómica → lectura idempotente del propio documento. Esa última lectura ahora exige servidor. El anfitrión espera su checkpoint antes de escribir su primer voto. Cambiar de anfitrión no invalida el campo histórico e inmutable `actorEsHost` de un voto ya creado.
4. **Checkpoint antes de estado caliente.** Las publicaciones Firestore/RTDB se serializan. Un checkpoint fallido no libera la nueva fase por RTDB; se reintenta. Se conserva el fallback Firestore si falla RTDB después del checkpoint. Los resultados no dependen de leer la copia caliente como si fuera durable.
5. **Caché y callbacks.** La autorización para sincronizar el roster RTDB depende del snapshot concreto confirmado, no de haber recibido alguna vez un snapshot del servidor. Se invalidan pendientes al detener el lobby; ACKs de una generación o match anteriores se descartan. El gameplay no toma decisiones de autoridad desde snapshots de caché/pending; el reparto para promover al anfitrión exige servidor. Se protegen callbacks de presencia, sincronización, estado y chat al detenerlos.
6. **Presencia.** `OnlinePresencePublisher` arma `onDisconnect` antes de anunciar conexión. El offline se encola inmediatamente y conserva la protección del servidor: ya no espera la cancelación de `onDisconnect`, cuyo callback podía pisar una reconexión posterior. Las respuestas de otra generación no habilitan listeners.
7. **Reglas.** Se impide crear una nueva membresía para entrar directamente en una partida en curso, autoelevar `esHost`, reactivar una membresía retirada durante el juego y leer repartos tras un baneo. RTDB rechaza regresión de `phaseIndex` del mismo match, evasión de cooldown mediante timestamps atrasados/multipath y votos de silencio tras su vencimiento. El marcador de limpieza bloquea escrituras de host e invitados y no puede ser creado ni eliminado por ellos.
8. **Reingreso y backend de inicio.** Las preferencias locales y `hostId` original ya no otorgan autoridad si existe otro `hostActivoId`. La callable también aplica esa prioridad. Un reintento de inicio conserva vida, permisos y miembros del mismo match RTDB. Si una partida avanzada perdió completamente el espejo, no se reconstruye falsamente con todos vivos: se requiere recuperación del estado actual.
9. **Salas antiguas.** El buscador filtra salas llenas, antiguas o en limpieza y retira las que caducan mientras está abierto, sin agregar lecturas por ese refresco visual. Se retiró el janitor destructivo del cliente. La antigüedad deja de habilitar al creador anterior para borrar una sala migrada.
10. **Revancha.** El borrado durable de acciones/repartos/runtime exige servidor. Un fallo ya no baja `limpiezaPendiente` como si todo hubiese terminado: se reintenta para impedir que el checkpoint viejo bloquee la partida nueva. La limpieza de chat conserva su carácter de mejor esfuerzo.

## Compatibilidad y condiciones antes de un eventual despliegue

**No instalar este APK contra reglas antiguas ni mezclar sin planificación APKs viejos con un anfitrión de protocolo 2.** El ID de voto nuevo y la ventana verificable requieren las reglas incluidas. Un invitado actualizado conserva el ID legado cuando observa protocolo 1; un APK viejo no sabe producir los IDs exigidos por un anfitrión nuevo. La ruta legacy permanece para migración y sigue teniendo deuda de anti-replay. Esto es una limitación de compatibilidad documentada, no una protección contra el anfitrión.

El despliegue futuro debe coordinar reglas, versiones de clientes y backend, probar con todos los dispositivos actualizados, y definir adopción mínima antes de eliminar legacy. No se cambiaron `versionCode`, firma ni canales de distribución. Las Functions de inicio siguen en la ruta de emulador Android; el inicio habitual de release conserva el motor del anfitrión. Las nuevas Functions de limpieza existen localmente y **no están ejecutándose en producción**.

## Mapa del flujo real

Abreviaturas: FS = Firestore; DB = RTDB; H = anfitrión activo; U = usuario autenticado. En todas las filas Auth y reglas constituyen el límite de identidad del SDK; un test de reglas usa claims simulados, no Play Integrity real.

| Transición | Fuente de verdad / escritor | Precondiciones y atomicidad | Reintento, carrera y offline | Timeout, recuperación y diagnóstico |
|---|---|---|---|---|
| Crear sala | FS `partidas`, `jugadores`, `codigosSala`; U | Batch de sala, dueño y código; identificadores y campos validados | Batch todo o nada; colisión de código no debe pisar otro. Confirmación offline pendiente | Callback éxito/error; logs de creación y error traducido |
| Unirse / buscar / código | FS sala y membresía propia; U | Transacción vuelve a leer estado/cupo; create sólo en lobby; no baneo | Transacción reintenta ante cupo concurrente; reglas no aceptan ingreso directo en juego | Error de cupo/conexión; consulta acotada de 30 salas y corte 30 min |
| Presencia | DB `presencia/uid`; propio U, H puede retirar | Membresía activa; `onDisconnect` registrado antes del online | Generaciones descartan callbacks viejos; conexión SDK no equivale a una concesión temporal con heartbeat | Desconexión servidor y watchdog; logs/reporte de reconexión |
| Comenzar | FS sala/roster/repartos; H o callable local | Transacción valida listo, cantidad, mapa y reparto completo | Inicio idempotente por match; callable no acepta roster calculado por cliente | Callable 30 s; reintento repara inicio parcial sin resucitar jugadores |
| Reparto privado | FS `repartos/uid`; H/backend | Dueño activo ve reparto propio; H ve todos porque ejecuta motor | Lectura servidor para recuperación; baneo retira acceso; información ya descargada no puede revocarse de memoria | Listener/generación y reintentos de carga; fallo mantiene sincronización |
| Entrada a partida | FS match + DB acceso/ACKs | Invitado espera miembro activo fuera del lobby antes del ACK; barrera por match | FS y DB no son atómicos; ACK repetido es idempotente; callback de match anterior descartado | Jitter/reintento de entrada y timeout de barrera ya existentes; logs ACK/acceso |
| Acciones de noche | FS `acciones`; propio U | Identidad y ventana en payload; ID determinista por acción/slot; H resuelve | Listener distingue pendiente/caché; lectura final servidor; fallo no inventa acciones ni expulsa AFK | Gate nocturno, piso secreto y reloj existente; backoff común de lectura |
| Sincronización | FS `runtime/authoritative` durable; DB `estado_partida` caliente; H | Checkpoint confirmado antes de liberar DB; publicaciones ordenadas | Falla FS conserva fase remota; falla DB usa fallback FS; índices no retroceden | Watchdog y reintento; eventos de fase, fallo de publicación y recuperación |
| Debate / listos | DB clientes/listos; cada U escribe su entrada, H avanza | Match/ronda/índice en readiness; motor define elegibilidad | Entrada propia reemplazable; datos viejos no satisfacen quorum del match nuevo | Reloj host y quorum existentes; no se cambió la duración de debate |
| Votar / cambiar | FS único documento por UID/ventana; propio U | V2 exige ventana abierta, target y propietario; cambio atómico acotado | Create ciego evita get prohibido inexistente; doble toque/idempotencia; offline no confirma éxito desde caché | Deadline + gracia 1500 ms y cierre explícito; logs create/update/idempotent/failure |
| Cierre / recuento | FS cierre y votos confirmados; H | Transacción cierra checkpoint antes de query; regla impide reapertura | Separa voto aceptado antes del cierre de voto tardío; error mantiene ventana cerrada y reintenta | Todos listos conservan gracia corta; timeout usa votos efectivamente confirmados |
| Cambio de fase | Motor H → checkpoint FS → estado DB | Índice creciente y mismo match; invitados aplican estado | Gate impide avances duplicados por respuestas tardías; host aún puede fabricar un estado legal en forma | Relojes y animaciones cliente; logs fase y watchdog |
| Finalización | Ganador en estado y metadatos FS; H | Motor calcula resultado; publicación durable antes de espejo | Fallo parcial de metadatos se registra; ganador remoto sigue dependiendo del H | Barrera/plazo de vuelta al lobby existentes; Crashlytics fase y retraso |
| Reconexión | FS sala, membresía, reparto y checkpoint; DB presencia | UID existente, acceso activo; prioridad host activo sobre preferencias | Recompone sesión desde checkpoint; no reingresa expulsado como nueva membresía en juego | Reintentos y aviso de recuperación; guion de dispositivos abajo |
| Abandono / expulsión | FS slot/baneo; DB permisos; propio U/H según operación | Transacciones de salida; baneo/membresía bloquean nuevas acciones | Revocación entre FS/DB aún depende de sincronización H; no hay atomicidad entre servicios | Grace/handoff actuales; errores no se presentan como un éxito |
| Migración H | FS `hostActivoId`/`hostVersion`; candidato mediante transacción; DB control | Candidato observado no concede autoridad antes del commit; recuperar reparto completo | Caché no promueve; creador previo no recupera autoridad por preferencias; permiso FS/DB puede desfasarse | Grace para desconectado y ventana de invitado existentes; logs promoción/democión |
| Revancha | FS `limpiezaPendiente`, roles/acciones/runtime; H | Sala esperando, cleanup confirmado antes de admitir nuevo inicio | No declara listo ante error durable; paginación de borrado existente | Reintento de cleanup; error de chat se registra pero no altera reglas del juego |
| Limpieza terminal | Functions Admin sobre FS/DB | Política conservadora, claim FS y lock DB revalidado, purga recursiva | Reconexión antes del lock aborta; después se rechaza. Fallo parcial conserva locks y es reintentable | Barrido cada 15 min, 100 registros por colección/página; eventos done/failed/sweep |

## Política de retención implementada

| Caso | Descubrimiento y retención |
|---|---|
| Lobby abierto abandonado o lleno nunca iniciado | Oculto al llenarse o superar 30 min sin actualización; elegible para purga tras 24 h sin actividad confirmada |
| Finalizada / abandonada | 24 h; presencia activa o actividad reciente conserva datos |
| Partida en juego, host desconectado o sala sin presencia | 7 días de inactividad; la desconexión de un solo host no basta para borrar |
| Presencia `conectado` con timestamp viejo | Se conserva: ese timestamp no representa un lease. No se mata una conexión larga legítima |
| Timestamp inválido/futuro, estado desconocido o lectura fallida | Se conserva por incertidumbre; fallo se registra |
| Huérfana observada al borrar padre | Trigger agrega cola idempotente; se revalida antes de limpiar descendientes y espejo tras retención |
| Código reasignado | Se borra sólo si aún apunta a esa sala |
| Eliminación parcial | Locks persistentes y reintento; no se abre el registro entre borrar hijos y terminar |

`cleanupRoom` elimina recursivamente **cada colección hija**, incluyendo descendientes anidados, antes de reducir padre y espejo a marcas mínimas de cierre. No deja roles/chat/acciones en esas marcas. Se conservan tombstones para bloquear recreación maliciosa de la ruta RTDB cuyo bootstrap aún es cliente. No se configuró TTL para eliminarlos: borrarlos reabriría ese riesgo.

Limitaciones: los candidatos antiguos requieren leer su espejo DB completo (pendiente acotar por canales); las salas recientes se descartan con los metadatos de la página antes de esa lectura. Los tombstones crecen; el barrido paginado tiene demora proporcional al total de salas; un cliente capaz de conservar una presencia conectada puede retrasar limpieza. Las huérfanas anteriores a la instalación del trigger no se descubren automáticamente desde una consulta a padres: requieren inventario Admin adicional (colecciones/grupos/export), revalidación y una tarea de backfill controlada. No se fingió resolverlas con TTL del padre. Firebase confirma que [borrar un documento](https://firebase.google.com/docs/firestore/manage-data/delete-data) y [TTL](https://firebase.google.com/docs/firestore/ttl) no borran sus subcolecciones.

## Riesgos ordenados por severidad

| Severidad | Hallazgo y situación final |
|---|---|
| Crítica | **H controla reglas y resultado real. Abierto.** Puede elegir roles, modificar vivos, deadlines, victorias y publicar checkpoints fabricados. V2 valida votos contra el estado de H, no demuestra que H diga la verdad |
| Alta | **Bootstrap y migración RTDB no consultan FS. Abierto.** El primer dueño de una ruta vacía puede reclamar `hostUid`; FS/DB pueden discrepar en handoff/revocación. Tombstone protege las salas purgadas, no resuelve la vinculación inicial |
| Alta | **Pérdida de votos/acciones por fallback vacío. Corregido localmente.** Lectura de servidor, conservación de fase, reintento y tickets |
| Alta | **Voto fuera de fase/replay/objetivo inválido. Corregido en V2; legacy conserva deuda.** Reglas y simulaciones nuevas lo comprueban. Noche y decisión del alcalde aún necesitan una ventana autoritativa equivalente en backend |
| Alta | **Membresía nueva en juego / reactivación retirada / reparto tras baneo. Corregido en reglas locales.** Es indispensable desplegarlas coordinadamente en una etapa autorizada |
| Alta | **Reintento de inicio podía resucitar jugadores. Corregido en Functions locales.** Pérdida completa del espejo de un juego avanzado se rechaza conservadoramente y sigue necesitando recuperador autoritativo |
| Alta | **Enforcement remoto App Check/Play Integrity no verificado.** Código release usa Play Integrity, debug usa provider debug y callable exige App Check. No se inspeccionó consola ni se cambiaron políticas remotas |
| Media | **Código de sala enumerable por get. Abierto.** Prohibir listar códigos no limita automáticamente intentos por UID ni ataques con múltiples anónimos; requiere endpoint con límites y métricas |
| Media | **Timestamps de H y orden de estados dentro de una fase. Deuda.** Deadline v2 se evalúa con `request.time`, pero H origina el límite; reloj local sigue interviniendo en orden/UX/handoff legado. Migrar a epoch/sequence emitidos por backend |
| Media | **Purgas cliente por antigüedad y carreras de reconexión. Corregido localmente.** Backend conserva datos ante incertidumbre; quedan tombstones e inventario de huérfanas históricas |
| Media | **Chat/eventos/acciones acumulados. Parcial.** Hay listeners acotados de chat y lotes de cleanup; limitar lecturas no limita almacenamiento. Partidas/lobbies mantenidos activos indefinidamente necesitan retención por canal en backend |
| Media | **Callbacks/timers en Activity grande. Parcial.** Tickets y guards cubren puntos modificados; no se declaró verificado todo callback visual, navegación ni rendimiento Android sin dispositivos |
| Media | **Observabilidad de votos en release insuficiente. Abierto.** Logs detallados son debug; Crashlytics/reporte de estabilidad cubren fases/retrasos. Falta medir denegaciones y latencia de cada operación sin datos privados |
| Baja | **Costo/latencia. Compensación explícita.** Confirmar checkpoint antes de fase agrega dependencia FS; evita pérdida de estado. Se preservan deduplicación y listeners existentes; no se promete ahorro global sin medición |

Seguridad APK: inspección de 666 rutas versionadas de app/functions/scripts no encontró patrones de clave privada PEM ni cuentas de servicio JSON. No es un escáner exhaustivo de secretos. La configuración pública Firebase no sustituye Auth/Rules. El manifiesto principal conserva `allowBackup=false` y `usesCleartextTraffic=false`; release no usa la configuración de emulador. Los errores online usan “servidor”/“conexión”.

Tener un provider instalado no prueba enforcement. La activación es por servicio en consola y requiere observar métricas y compatibilidad de dispositivos: [documentación oficial de App Check](https://firebase.google.com/docs/app-check/enable-enforcement), [Play Integrity en Android](https://firebase.google.com/docs/app-check/android/play-integrity-provider).

## Pruebas y evidencia

La evidencia ejecutable está en `output/online-audit/results.json` y logs junto a ese archivo; el manifiesto de verificación final detalla builds y conteos. Los mensajes `PERMISSION_DENIED` dentro de tests negativos son esperados; se determina éxito por aserciones y exit code, no por ausencia de esos logs.

Comandos usados (PowerShell, raíz del proyecto):

```powershell
git status --short
git diff --stat
git diff -- <archivos online>
git log -6 --oneline
node scripts/with-jdk.cjs C:\Windows\System32\cmd.exe /d /c gradlew.bat testDebugUnitTest assembleDebug --console=plain
npm run test:functions-unit
npm --prefix functions run check
npm run test:online-audit
node scripts/with-jdk.cjs C:\Windows\System32\cmd.exe /d /c gradlew.bat testDebugUnitTest assembleDebug assembleRelease lintDebug -x :app:uploadCrashlyticsMappingFileRelease --console=plain
git diff --check
```

El Node del PATH era 20.16.0; se probó backend unitario y primera integración con él. Para la ejecución final de Emulator Suite se usó el Node 24.19.0 incluido en el entorno, respetando Node >=22 del proyecto raíz. Functions declara runtime 20 y el emulador advierte la diferencia cuando corre con Node 24; el backend también pasó integración bajo Node 20. El JDK del wrapper es Android Studio/JBR 21. No se actualizó ninguna dependencia para hacer pasar las pruebas.

Demostrado automáticamente:

- Primer voto H/3 invitados concurrentes; claims anónimos y registrados simulados; cambios atómicos, doble toque, reintentos idempotentes y límite de cambios.
- Rechazo de suplantación, sobrescritura ajena, UID externo, objetivo inexistente/muerto/inválido en desempate, actor muerto/silenciado, fase/ronda/match atrasados o futuros, cierre y deadline con gracia.
- Reconexión de SDK de pruebas con ventana abierta/cerrada, invariancia del número de documentos, migración de H y cierre monotónico.
- 10 simulaciones de 3/6/9/12/15 usuarios: roles privados, presencia, ACKs, readiness, publicación FS→DB, todos los votos y cambios, cierre y lectura. Incluye rechazo deliberado del ACK antes de conceder acceso DB.
- Simulación de inicio: 5 iteraciones por 5/10/15 usuarios, reintentos, callable, cinco solicitudes a la misma sala (un inicio y cuatro idempotentes), y ocho salas simultáneas.
- 13 pruebas de integración backend: purga anidada, locks y códigos, reconexión justo antes del lock, fallo de borrado inyectado y recuperación, dos trabajadores, huérfana, paginación, inicio privado y recuperación segura.
- Tests Kotlin deterministas de presencia y de tickets de resolución: caché/pending no se convierten en resultado vacío, respuesta duplicada no vuelve a resolver, stop/handoff/rematch/fase distinta invalidan la respuesta. Las suites existentes de motor/quorum/voto/timeout y simulación de protocolo siguen ejecutándose.

No demostrado: Auth real ni vinculación de anónimo en red móvil, tokens Integrity/enforcement remoto, UI Android y memoria/jank, push/Play Games, latencia Internet, índices efectivamente desplegados y scheduler de producción. La Function programada se prueba llamando su servicio real en emuladores; Emulator Suite no ejecutó un reloj de Cloud Scheduler. Las cifras de milisegundos de las simulaciones son locales, no SLOs de producción.

## Prueba manual corta — 3 a 5 dispositivos

Usar la misma versión actualizada y un entorno con estas reglas. Cinco permite partida normal; con tres/cuatro usar únicamente el modo de prueba que ya existe. Mezclar cuenta registrada y dos invitados. Guardar el reporte de estabilidad al finalizar.

1. H crea sala pública; los demás entran uno a uno y a la vez. Verificar cupo, roster y que nadie adopte H temporalmente. Llenar la sala y comprobar que desaparece del buscador; el reingreso por la sesión guardada sigue disponible.
2. Iniciar; un invitado pasa brevemente a segundo plano durante reparto. Todos ven sólo su rol/aliados permitidos y entran sin errores de ACK. Ejecutar una noche con acciones de todos los roles presentes.
3. En votación, primero votan los invitados y al final H. Cambiar un voto, hacer doble toque y votar simultáneamente. Mostrar votos sólo según configuración habitual. El recuento debe incluir exactamente un voto por jugador y el último cambio aceptado.
4. Repetir dejando un jugador sin votar hasta vencer el tiempo. En otro intento quitarle la red antes de votar y devolverla antes del cierre; después repetir devolviéndola después del cierre. No deben aparecer votos fantasma ni éxito local de una acción rechazada.
5. Cortar la red de H al cerrar/recontar. La mesa debe esperar o migrar; al volver la red, un único recuento con datos confirmados. Reabrir H desde reciente/cerrado y comprobar que no se restituye autoridad si ya migró.
6. Expulsar a un usuario en lobby y probar reingreso; durante partida probar retorno válido de un desconectado. Probar empate/alcalde si ocurre. Finalizar y revancha; confirmar roles nuevos y ausencia de votos/checkpoint anteriores.
7. Salir de todos los dispositivos y comprobar que no queda una sala llena en búsqueda. La purga no es inmediata: verificar por Admin el vencimiento aplicable en un entorno de prueba, descendientes eliminados y marcas de cierre en ambas bases.

## Plan incremental para retirar autoridad de H

1. **Contrato y adopción.** Negociar versión/capacidades y epoch de autoridad desde backend; telemetría por protocolo. No cerrar legacy hasta adopción medida. Mantener tests de clientes mixtos.
2. **Inicio y membresía.** Publicar bajo autorización `iniciarPartidaV2`, migrar clientes y luego cerrar reparto/roster/control sensible al cliente. Crear/unirse/handoff/revocar mediante backend; FS es fuente y un outbox reintenta el espejo DB.
3. **Ventanas y votos.** Backend crea/vence/cierra ventanas con reloj servidor y resuelve votos en transacción idempotente por match/phaseIndex. Guardar resolución/hash/sequence; H pide resolver pero no entrega conteo ni ganador. Retirar flag controlado por H y ruta legacy.
4. **Noche y efectos.** Portar reglas puras del motor con pruebas de paridad y semillas deterministas; backend aplica acciones válidas una vez, calcula vida/silencio/pistas y publica sólo resultados públicos. Roles privados nunca se entregan completos a H.
5. **Resultados, migración y retención.** Backend emite ganador y vuelta al lobby, asigna coordinador por lease servidor y realiza limpieza/retención por canal. Entonces cerrar escrituras cliente de `runtime`, `estadoPartida`, permisos DB y resultados.

Preferencia: evolución por contratos, paridad de motor y outbox entre bases; no duplicar ahora el motor entero en Functions ni presentar esta etapa como eliminación de trampas.

## Observabilidad propuesta

Extender `OnlineDiagnostics`/reporte local con eventos acotados y sin roles, nombres, mensajes ni tokens: `vote_write` (modo, código SDK, intento, protocolo), `window_close`, `resolution_read_retry`, `checkpoint_commit`, `hot_publish`, `entry_ack`, `membership_revoked`, `host_handoff`, `reconnect`, `cleanup_done/failed`. Para correlación remota usar un identificador efímero o hash no reversible de sesión; no subir UIDs/códigos crudos de debug.

Medir monotónicamente duración de envío→ACK, cierre→lectura, checkpoint→DB, entrada y recuperación; distribuir p50/p95/p99 por app/protocolo/red. Contar denegaciones por operación/regla sin interpretar toda denegación como ataque. Muestrear jank con FrameMetrics/JankStats y ANR al render/cerrar voto; trazar inicio/fin del bloque de resolución. Contabilizar listeners vivos, reconexiones y tamaño de payload. El contador Firestore actual es una estimación local, no facturación. Backend ya agrega contadores de barrido y fallos por código; alertas sobre repetición de locks pendientes y atraso del cursor.

## Estado por área

| Área | Estado | Motivo |
|---|---|---|
| Voto V2 / reglas | Bueno | Cobertura adversarial local; rollout mixto y dispositivos pendientes |
| Concurrencia / recuperación | Bueno | Gates, servidor y fallos parciales cubiertos; dos bases siguen sin transacción conjunta |
| Autoridad y anti-trampa | Riesgo | H todavía conoce y modifica estado crítico |
| Privacidad de invitados | Bueno | Lectura propia y baneo; H conserva reparto completo |
| App Check / Integrity | Deuda | Integración presente; enforcement/Play real no verificado |
| Salas abandonadas | Bueno | Servicio conservador probado; requiere despliegue y backfill de huérfanas históricas |
| Arquitectura | Deuda | Helpers extraídos y backend incremental; Activities grandes y motor cliente |
| Observabilidad | Deuda | Diagnóstico de fase existente; métricas operativas propuestas |
| Consumo y latencia | Deuda | Menos trabajo inválido; checkpoint seriado agrega dependencia y falta medición real |
| Validación automatizada | Bueno | Reglas, simulaciones, motor y backend; no equivale a prueba de dispositivos |

No se asigna “excelente” a un sistema cuyo riesgo arquitectónico principal sigue abierto.

La lista final de archivos modificados y nuevos, conteos de pruebas y resultados de build se guarda en `output/online-audit/verificacion-final.md`.
