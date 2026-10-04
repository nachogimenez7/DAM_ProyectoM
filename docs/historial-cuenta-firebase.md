# Historial por cuenta — 4 de octubre de 2026

## Estado

Implementación Android y backend preparada y probada contra Firebase Auth, Firestore y Functions emulados. El proyecto remoto sigue en Spark: estas reglas y funciones todavía no están desplegadas. La lectura del historial en iOS es el siguiente bloque; su pantalla sigue indicando que no está conectado.

El menú Android muestra el retrato propio y un altavoz con estados de música activa/silenciada. El bloque online está centrado como en iOS. Ambas tarjetas encuadran las ilustraciones por la cara, con el mismo criterio del Perfil; Android coloca el retrato arriba del nombre con texto grande.

La tarjeta de identidad de Android usa el perfil público confirmado por el servidor. Una cuenta muestra nombre, número, ilustración o foto publicada y su descripción `bioPerfil` (si tiene texto); un invitado abre el diálogo real de cuenta del Perfil. La subida de fotos sigue dependiendo de configurar Storage y activar el flag existente.

## Contrato compartido

- Auth UID es la identidad de la cuenta.
- Resumen privado: `cuentas/{uid}`, con `schemaVersion: 1`, `partidas`, `victorias`, `ultimaPartidaEn` y `actualizadaEn`.
- Registros privados: `cuentas/{uid}/historial/{recordId}`.
- ID: `online_` o `local_` seguido del SHA-256 hexadecimal UTF-8 de `matchKey`.
- Online: `matchKey = online:{partidaInicial.matchId}`. Local: `local:{codigo}:{startedAtEpochMs}:{initialPlayerCount}`.
- Campos: `schemaVersion`, `uid`, `matchKey`, `origen` (local/online), `roomId`, `matchId`, `fechaLocalMs`, `mapKey`, `mapName`, `roleKey`, `roleName`, `won`, `participantCount`, `winner`, `finalizadaEn` (Timestamp), `contabilizada` (solo servidor).
- Lectura: ordenar por `finalizadaEn` descendente y limitar a 50. Los contadores abarcan todo el historial, no solamente esos 50 registros. No requiere índice compuesto.
- El resumen público `perfiles_publicos/{uid}.estadisticasPerfil` lo actualiza el backend. Editar el perfil conserva esos contadores.

## Guardado y límites de confianza

El anfitrión Android confirma el checkpoint final y el resultado en el documento de sala en una sola transacción. `guardarHistorialOnlineV1` usa la instantánea final del evento, no el estado actual de una sala que pudo reiniciarse. Guarda también a participantes desconectados con perfil de cuenta; no depende de que abran la pantalla de victoria. Cancela resultados incompletos, cancelados o ambiguos y excluye jugadores simulados. La transacción por UID crea un registro y actualiza sus contadores una sola vez, incluso con eventos repetidos o concurrentes.

La autoridad de gameplay sigue siendo el anfitrión actual. Persistir desde el backend no convierte el motor en un servidor antitrampas: valida el cierre y las identidades, pero no vuelve a simular la partida.

Las partidas locales iniciadas con una cuenta pueden subirse a su mismo UID. Son resultados declarados por ese cliente; no prueban una victoria competitiva. `contarPartidaLocalV1` valida el ID canónico y contabiliza cada documento una vez. La cuenta al iniciar la partida queda fijada y se conserva al rotar la pantalla. Los previews de interfaz no se envían.

El cliente conserva una cola de envíos pendientes por UID, para sobrevivir a una desconexión o cierre. No la presenta como historial confirmado. Reintenta al iniciar/restaurar sesión, abrir el Perfil o tocar la tarjeta de reintento. Nunca transfiere esa cola a otra cuenta.

No se importan los contadores históricos del dispositivo: no tienen propietario verificable. Los invitados conservan únicamente su historial local existente. Las cuentas muestran registros leídos de Firebase, con estados de carga, error, envío pendiente y estadísticas en proceso.

`borrarHistorialCuentaV1` elimina primero el perfil público y luego el historial privado al eliminar Firebase Auth. Esto impide que un evento atrasado recree la cuenta borrada.

## Activación remota

1. Activar Blaze desde Firebase cuando se vaya a hacer esta prueba real. Configurar presupuesto y alertas; las alertas no son un límite automático de gasto.
2. Comprobar el destino con `node scripts/prepare-firebase-release.cjs`. Es una consulta de solo lectura: valida el proyecto, la región y la facturación. El 04/10 se confirmó `billingEnabled: false`; el inventario remoto de Functions devolvió 403, por lo que no se presume que haya funciones desplegadas.
3. Con Blaze activo y los clientes actualizados, ejecutar `node scripts/prepare-firebase-release.cjs --apply`. Despliega explícitamente inicio, limpieza e historial del repositorio, espera su confirmación y después publica las reglas e índices de Firestore. Ante un fallo se detiene antes de la siguiente etapa. No activa facturación ni crea recursos de Storage. Para publicar también sus reglas, añadir `--storage` una vez que exista el bucket: verifica su propietario antes del despliegue.
4. Instalar una compilación normal, sin `traidoresOnlineAuthorityEmulator`.
5. Terminar una partida online con dos cuentas, revisar los dos historiales y recuperar uno en otra sesión. Comprobar una local, un reintento y una partida cancelada.
6. Conectar iOS a los mismos documentos y UID; no crear otro contador ni importar preferencias del teléfono. La subida de fotos de iOS también continúa pendiente: sus métodos reales actualmente devuelven una función no disponible.

Los cambios Android y las reglas deben publicarse de forma coordinada. Las nuevas reglas reservan estadísticas del perfil al servidor; un cliente antiguo que intente cambiarlas recibirá un rechazo. Los payloads actuales de perfil de Android/iOS omiten esas estadísticas.

## Pruebas reproducibles

- Functions: `node --test functions/test/accountHistory.unit.test.js`.
- Con emuladores Auth 9099, Firestore 8081 y Functions 5001 del proyecto `traidores-local`:
  `FIRESTORE_EMULATOR_HOST=127.0.0.1:8081 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 node --test functions/test/accountHistory.integration.test.js`.
  Si el emulador se inició con `--project traidores`, añadir `HISTORY_TEST_PROJECT=traidores`.
- Reglas: `node scripts/test-account-history-rules.cjs` (dependencias Firebase de pruebas instaladas). Usa un namespace demo separado.
- Android: `:app:testDebugUnitTest` con `AccountHistoryContractTest`, `OnlineLobbyRulesTest` y `OnlineMatchProfileResolverTest`.

Las pruebas de integración comprueban concurrencia, contadores, invitados, resultados atrasados, ejecución real de los triggers, supervivencia al borrado de sala y purga al borrar Auth. Las reglas verifican aislamiento por UID, recuperación desde otra sesión, inmutabilidad, imposibilidad de escribir resultados online/contadores desde el cliente y publicación final atómica del anfitrión.

## Revisión visual

Compilación Android e iOS aprobadas. En un emulador Android separado se comprobó el menú, acceso de invitado contra Auth/Firestore reales emulados y tarjeta con escala de fuente 1 y 2. Las pruebas iOS `testRegisteredAccountShowsItsIdentity` y `testLargestTextKeepsOnlineScreensUsable` pasaron, incluyendo las auditorías de accesibilidad existentes. Los servicios falsos iOS se usan solo con los argumentos explícitos de estas pruebas, nunca en una ejecución normal.

## Sincronización del perfil y banner

La tarjeta de ambas plataformas muestra también el banner publicado. Android guarda cada edición en una cola por UID y la envía a `perfiles_publicos`, con confirmación del servidor y reintento desde Perfil/Online. Antes se actualizaba solamente el respaldo de Play Juegos, lo que dejaba nombre y avatar locales diferentes de la tarjeta pública. La primera migración conserva las ediciones del teléfono únicamente si Firebase confirma el mismo número de cuenta. La tarjeta espera esa sincronización y el Perfil recupera los datos confirmados; una frase vacía también se propaga. Borrar la cuenta limpia su cola.

En iOS el guardado ya espera confirmación de Firestore. Se añadió escucha del perfil público, limitada al UID actual, para reflejar cambios confirmados en las vistas y en el almacenamiento del menú. Se comprobó guardar y recuperar un perfil real contra Auth/Firestore emulados (`testRealAccountAndProfileAgainstEmulators`, activado con `TEST_RUNNER_TRAIDORES_AUTH_EMULATOR_TEST=1`). En Android se verificó que el #1 conservó una partida y una victoria al publicar el nombre, avatar y banner elegidos.

## Consumo de las tarjetas y ediciones

El banner y los avatares ilustrados son recursos incluidos en cada aplicación. Mostrarlos no requiere descargar esos archivos de Firebase. La tarjeta lee el documento propio del perfil; la descripción y las claves del catálogo son campos de ese mismo documento. Una foto publicada sí puede consumir descargas de Storage.

No se impuso un cooldown largo a las ediciones de la beta. Android conserva por UID la última edición confirmada y no vuelve a enviarla si no cambió; fusiona las ediciones pendientes mientras hay un envío en curso. Comprobar un número existente dejó de publicar incondicionalmente el perfil del teléfono. Reservar un número necesita confirmación de Firestore: un fallo de red ya no inventa un número local. iOS compara el borrador normalizado con el perfil confirmado y evita una escritura y su lectura posterior cuando son iguales. Las comprobaciones de acceso siguen leyendo del servidor.

Esto elimina operaciones repetidas en los clientes normales, pero no es una protección contra clientes modificados. Si la beta muestra abuso, un límite de frecuencia debe aplicarse en reglas o backend, distinguiendo ediciones de texto y subida de fotos. Las funciones tienen máximos de instancias, incluyendo limpieza de salas huérfanas y borrado de cuentas; esos máximos no son un tope de gasto. El coste total incluye las lecturas de las escuchas y los eventos de las salas, aunque el historial se guarde solo al terminar.

Verificación de esta optimización: Android ensamblado y 691 pruebas unitarias sin fallos; backend 21 pruebas sin fallos; iOS `testRealAccountAndProfileAgainstEmulators` aprobado, con una nueva comprobación de que repetir el mismo borrador conserva `actualizadaEn` en el documento remoto emulado. La consulta de preparación y el intento con `--apply` en Spark terminaron sin desplegar: la comprobación de facturación detuvo este último antes de ejecutar Firebase deploy.

La preparación y el despliegue son estados diferentes. No puede completarse el despliegue de Functions/Storage en Spark. Tampoco debe presentarse como terminado todo el online de iOS: cuenta y perfil están conectados; historial, fotos y gameplay aún requieren trabajo y aceptación real.
