# Firebase online experimental

El modo online de Traidores sigue siendo experimental. Esta documentacion define el contrato actual para poder probar sin perder de vista los limites: **sí hay Firebase Auth anónima** (`OnlineTempIdentity.ensureAuthenticated` hace `signInAnonymously`; el `uidTemporal` es el uid anónimo y las reglas exigen `request.auth.uid`), pero todavia no hay Auth con cuentas reales, Cloud Functions, App Check ni backend autoritativo completo.

## Objetivo actual

- Crear y buscar salas online desde Android.
- Unirse a una sala por codigo de 6 caracteres.
- Mantener presencia en RTDB con deteccion de desconexion y reconexion.
- Iniciar partidas online solo con cantidad esperada completa.
- Reconstruir partidas desde `partidaInicial` y `estadoPartida` al reingresar.
- Registrar acciones de gameplay y mensajes de chat.
- Reservar un ID publico numerico fijo para perfil y futuros amigos.
- Evitar datos obviamente invalidos desde reglas y desde el cliente.

El modo confiable para demo sigue siendo `Jugar vs IA` mientras el online madura.

## Configuracion del repo

- `firebase.json`: apunta a `firestore.rules` y `database.rules.json`.
- `firestore.rules`: reglas de Firestore para pruebas online.
- `database.rules.json`: reglas de Realtime Database para chat y presencia.
- `app/google-services.json`: configuracion local de la app Android.

Para publicar reglas desde una maquina con Firebase CLI:

```bash
firebase deploy --only firestore:rules
firebase deploy --only database
```

## Rutas

### `pruebas/{docId}`

Ruta temporal para verificar conexion Firebase desde la app. Puede borrarse cuando el online ya no necesite el boton de prueba.

Campos usados:

- `nombre`
- `mensaje`
- `origen`
- `fechaLocal`
- `fechaServidor`

### `partidas/{partidaId}`

Documento principal de sala.

Campos base:

- `nombre`: nombre visible de sala, maximo 60 caracteres.
- `codigoSala`: codigo publico de 6 caracteres, formato `A-HJ-NP-Z2-9`.
- `estado`: `esperando`, `en_juego`, `abandonada` o `finalizada`.
- `mapa`: clave interna del mapa.
- `mapaNombre`: nombre visible del mapa.
- `hostId`: id temporal del anfitrion.
- `hostNombre`: nombre visible del anfitrion, maximo 18 caracteres.
- `hostActivoId`: jugador que resuelve fases en ese momento.
- `hostVersion`: contador de handoff de host.
- `partidaInicialCreada`: `true` cuando ya se repartieron roles una vez.
- `jugadoresEsperados`: cantidad fija definida por el host, de 5 a 15; en una sala de prueba, de 3 a 15.
- `maxJugadores`: mismo limite visible que `jugadoresEsperados`.
- `jugadoresActuales`: contador visible de jugadores registrados en la sala.
- `modoPrueba`: habilita explicitamente salas experimentales con un minimo de 3 jugadores. El cliente lo controla, por lo que este limite es solo para pruebas entre amigos; App Check debera protegerlo antes de produccion.
- `configLobby`: configuracion visible y sincronizada antes de iniciar (`transicionSeg`, `nocheSeg`, `discusionSeg`, `votacionSeg`, `revelarRolesAlMorir` y `votosIndividuales`). Sobrevive al traspaso de anfitrion.
- `origen`: origen tecnico, por ejemplo `android-online-create`.
- `creadaEn`: timestamp de servidor.
- `actualizadaEn`: timestamp de servidor.

Campos agregados durante la partida:

- `partidaInicial`: snapshot inicial generado una sola vez por el host. Cada jugador queda ligado por `uidTemporal`.
- `estadoPartida`: estado autoritativo publicado por el host.
- `estadoClientes`: estado resumido publicado por cada cliente.
- `entradaLiberadaMatchId`: `matchId` que el host habilito para abandonar el lobby. Se escribe solo despues de que todos los clientes confirmaron haber recibido el reparto, o tras el timeout de seguridad.
- `ultimaActividadOnline`: timestamp de actividad reciente.
- `ultimoResultado`: resumen durable de la ultima partida (`ganador`, `ronda`, `mapa`, `matchId` y `finalizadaEnLocal`). No se elimina al preparar la revancha y permite mostrar el resumen en el lobby.

Dentro de `estadoPartida`, ademas de fase, ronda, jugadores y votos, se publican dos
datos de presentacion compartida:

- `desertorBando`: bando elegido por el desertor (`Pueblo`, `Traidores` o vacio si todavia no eligio). Sin este campo el resto de la mesa no puede mostrar el resultado final del desertor.
- `desertorCambioBando`: `true` cuando ya uso su unica reconsideracion.
- `nocheSinVictima`: permite que todos los clientes muestren la revelacion de amanecer sin muertes.
- `presentacionVotacion`: identificador durable de `expulsion` o `sin_expulsion`; evita que un invitado pierda la ventana si el host avanza de fase despues de presentarla.

`partidaInicial.config` conserva la configuracion elegida por el host para que todos los
celulares, incluido un reingreso, reconstruyan la misma partida:

- `transicionSeg`: duracion de las transiciones de dia y noche.
- `nocheSeg`: duracion de cada fase nocturna.
- `discusionSeg`: duracion del debate diurno.
- `votacionSeg`: duracion de la votacion.
- `revelarRolesAlMorir`: muestra u oculta la carta de un jugador eliminado.
- `votosIndividuales`: muestra quien emitio cada voto o solamente los totales.

Las salas creadas antes de incorporar `config` siguen siendo compatibles: usan los
tiempos predeterminados y las reglas locales recibidas como fallback.

### `partidas/{partidaId}/jugadores/{uidTemporal}`

Documento de presencia por jugador.

Campos:

- `nombre`: nombre de perfil normalizado, maximo 18 caracteres.
- `nombrePerfil`: copia del nombre de perfil para futuras pantallas publicas.
- `nombreSala`: nombre visible dentro de esa sala, maximo 32 caracteres. Incluye el `#` publico para distinguir nombres repetidos, por ejemplo `Federico #2`.
- `publicId`: ID publico numerico fijo del jugador, sin el simbolo `#`.
- `bioPerfil`: frase publica del perfil, maximo 40 caracteres.
- `avatarPerfil`: clave de foto/avatar elegida para el perfil.
- `bannerPerfil`: clave de banner publico del perfil.
- `rolFavoritoPerfil`: clave del rol favorito elegido.
- `uidTemporal`: id local del dispositivo. Debe coincidir con el id del documento.
- `estado`: campo legacy para clientes anteriores. La fuente de verdad de conectado/desconectado vive en RTDB.
- `esHost`: booleano opcional.
- `listo`: booleano opcional.
- `orden`: posicion estable del jugador dentro de la sala.
- `activoEnPartida`: booleano para distinguir presencia de jugador activo.
- `unidoEn`: timestamp de servidor.
- `ultimaConexion`: timestamp de servidor.
- `ultimaConexionLocal`: timestamp local del dispositivo.
- `votoMapa`: voto opcional del jugador (`pampa`, `grecia` o `medieval`). Se limpia al preparar una revancha.

La eleccion de mapa online se resuelve dentro de la misma transaccion que crea
`partidaInicial`: sin votos se conserva el mapa actual, con un lider unico gana ese mapa y,
si hay empate, el anfitrion elige solamente entre los empatados. Su voto previo cuenta como
un voto normal, sin peso extra.

### `meta/public_ids`

Documento contador para reservar IDs publicos numericos.

Campos:

- `nextId`: proximo numero disponible. Si el documento no existe, la app empieza en `1`.
- `actualizadaEn`: timestamp de servidor.

Este contador es experimental y funciona sin Auth para pruebas. En produccion deberia moverse a una Cloud Function o protegerse con Auth/App Check.

### `perfiles_publicos/{uidTemporal}`

Documento publico minimo del jugador mientras no exista login real.

Campos:

- `uidTemporal`: id local del dispositivo. Debe coincidir con el id del documento.
- `publicId`: ID publico numerico fijo del jugador.
- `nombrePerfil`: nombre de perfil visible.
- `nombreSala`: nombre visible dentro de sala, maximo 32 caracteres, con `#publicId` cuando corresponde.
- `bioPerfil`: frase publica del perfil, maximo 40 caracteres.
- `avatarPerfil`: clave de foto/avatar elegida para el perfil.
- `bannerPerfil`: clave de banner publico del perfil.
- `rolFavoritoPerfil`: clave del rol favorito elegido.
- `actualizadaEn`: timestamp de servidor.

Regla de producto actual:

- El `publicId` no se edita desde el perfil.
- El perfil muestra el ID como `#1`, `#2`, etc.
- Para futuros amigos se deberia buscar/agregar por `#`.
- Si varios jugadores tienen el mismo nombre, dentro de una sala se distinguen por `nombreSala`.

## Reparto online

El online de prueba usa un preset seguro:

- 3 jugadores de prueba: 1 asesino, 1 medico y 1 comisario/detective.
- 4 jugadores de prueba: la composicion anterior mas 1 aldeano.
- 5 y 6 jugadores: 1 asesino, 1 medico, 1 comisario/detective y el resto aldeanos.
- 7 jugadores: suma 1 mercenario.
- 8 jugadores: suma 1 alcalde.
- 9 jugadores: suma 1 desertor.
- 10 a 15 jugadores: suma 1 espia (segundo killer) y completa con aldeanos.

Los umbrales son los mismos `minimumPlayers` del catalogo de roles, para que el online se
sienta como el local a medida que crece la mesa.

Faltan todavia el Bufon, el Oraculo y el Payador, que son exclusivos de mapa y quedan para
una fase posterior. Siguen disponibles para local/IA.

Reglas importantes:

- La sala no inicia hasta que `jugadoresActuales == jugadoresEsperados` y todos esos jugadores esten `listo`.
- Si un jugador se desconecta antes de iniciar, RTDB actualiza su presencia con `onDisconnect()` y el host puede liberar su cupo marcando `activoEnPartida = false`; el documento Firestore queda como historial y `jugadoresActuales` se recalcula.
- `partidaInicial` se crea una sola vez.
- El inicio online debe escribirse por transaccion: si `partidaInicialCreada` ya es `true`, no se reparten roles de nuevo.
- Antes de salir del lobby, cada cliente confirma el `matchId` recibido en `estadoClientes.{uidTemporal}` con `entradaLobbyLista = true`. El host publica ese mismo id en `entradaLiberadaMatchId` cuando todos confirmaron; los clientes ignoran el snapshot local pendiente del host y navegan al recibir la liberacion confirmada. A los 10 segundos el host puede liberar con las confirmaciones disponibles para evitar que una escritura perdida congele la sala.
- Al crear sala, la app verifica que el `codigoSala` generado no exista ya en Firestore; si colisiona, reintenta con otro codigo.
- Al unirse por codigo, la app usa solo salas `esperando`; si hubiera mas de una sala activa con el mismo codigo, bloquea el ingreso y pide crear una sala nueva.
- Si un cliente reingresa, reconstruye desde `partidaInicial` y `estadoPartida`.
- Si la sala esta `esperando`, el reingreso vuelve al lobby.
- Si la sala esta `en_juego`, el reingreso abre gameplay directo con la misma carta, fase y estado vivo/muerto.
- El reingreso nunca debe llamar al reparto local como fallback. Si faltan `partidaInicial`, `estadoPartida`, `fase` o el jugador por `uidTemporal`, se muestra error y se limpia la recuperacion local.
- Si un jugador esta desconectado durante noche o votacion, su accion cuenta como ausente.
- Si el host activo se desconecta o su personaje muere, el primer jugador vivo y conectado segun `orden` puede tomar `hostActivoId`. El cambio es tecnico e invisible; `hostId` sigue identificando al creador de la sala.
- En gameplay online, los clientes solo registran acciones/votos; el host activo publica el resultado en `estadoPartida`.
- Los carteles compartidos de amanecer y votacion no dependen de un boton exclusivo del host. Cada jugador vivo publica `presentacionConfirmada`; el coordinador avanza cuando todos confirmaron despues de 3 segundos o al cumplirse un maximo de 10 segundos.
- Los eliminados siguen viendo carteles y chat publico en modo solo lectura, pero no cuentan en `LISTOS n/total`.
- La pantalla ganadora vuelve al lobby al terminar la musica de victoria; 45 segundos quedan como respaldo si el audio esta desactivado o falla.
- La noche y la votacion esperan el timer completo. No hay avance temprano aunque todos hayan actuado.
- En noche, si un jugador envia mas de una accion valida para la misma ronda, se toma la **primera** por `creadaEnLocal`: la eleccion nocturna no se puede cambiar una vez confirmada (`OnlineActionResolver`, pineado en tests).
- El alcalde se revela registrando una accion `accion_jugador` con `detalles.accion = "revelar_alcalde"` y sin objetivo. El anfitrion activo la ve por el listener de `acciones`, aplica la revelacion y la publica; el resto la recibe por `alcaldeRevelado` y el anuncio publico. El desempate del alcalde ya se resolvia asi desde antes, con una accion `votar` en fase `ALCALDE_DESEMPATE`.
- El desertor manda su bando en `objetivoNombre` (`Pueblo` o `Traidores`) con dos acciones distintas: `elegir_bando` la primera vez y `reconsiderar_bando` en la ventana de cambio. **Tienen que ser distintas**: con una sola accion, al abrirse la ventana de reconsideracion el anfitrion volveria a leer la eleccion inicial y le quemaria el cambio al jugador sin que lo pidiera. El anfitrion deduce cual espera del propio estado (`desertorBando` en blanco o no), asi que un traspaso de host no pierde el pedido.
- En online el bando del desertor **nunca se preasigna**: lo elige el jugador. Preasignarlo en el cliente dependia de `isHuman`, que es distinto en cada celular, y cada dispositivo reconstruia un bando diferente.
- Si el desertor no elige nunca, `GameRules.winnerFor` no puede declarar ganadores a los traidores y la partida se queda sin final posible. Por eso, a partir de la ronda 2, el anfitrion le asigna un bando estable por sala (`OnlineDesertorGate`). La victoria del pueblo no depende de esto: se evalua antes que el guard del desertor.
- En votacion/desempate, si un jugador vota mas de una vez en la misma fase, se toma el ultimo voto por `creadaEnLocal`.
- Las acciones o votos ausentes no bloquean la fase; cuentan como sin accion o abstencion.
- Durante `REPARTO`, cada cliente publica en `estadoClientes.{uidTemporal}` si entro al gameplay, cuantos jugadores ve y si ya toco `EMPEZAR`.
- El host activo no inicia la primera noche hasta que todos los jugadores esperados esten en gameplay, vean la cantidad correcta de cartas y hayan tocado `EMPEZAR`.
- Si pasan 30 segundos y falta un celular, el host puede usar `FORZAR NOCHE`. No reparte roles de nuevo; solo publica el primer estado nocturno desde la `partidaInicial` existente.

Campos actuales dentro de `estadoClientes.{uidTemporal}`:

- `fase`, `ronda`, `phaseIndex`: fase local que ve ese cliente.
- `enGameplay`: `true` cuando el cliente ya entro a gameplay.
- `jugadoresVistos`: cantidad de cartas/jugadores que ese cliente reconstruyo.
- `jugadoresEsperados`: cantidad esperada para esa partida.
- `uidTemporal`: id temporal del cliente.
- `orden`: indice del jugador humano dentro de la partida reconstruida.
- `rolLeido`: `true` cuando el jugador toco `EMPEZAR` en la lectura inicial.
- `estadoArranque`: `sincronizando`, `leyendo_rol`, `rol_leido` o `en_partida`.
- `aplicoEstadoPartida`: `true` cuando el cliente ya aplico al menos un estado autoritativo o es el host activo.
- `sincronizando`: `true` cuando el cliente esta esperando una fase publicada por el host.
- `presentacionConfirmada`: identificador de la ultima presentacion compartida cuyo boton `CONTINUAR` pulso ese cliente.
- `ultimaFaseAplicadaEnLocal`: fase e indice que el cliente tiene aplicada localmente, por ejemplo `NOCHE_ASESINO:1`.
- `jugador`, `rolKey`: datos de depuracion para Logcat/Firebase Console.

Regla de sincronizacion por fase:

- En online, los invitados no avanzan fases localmente.
- Si el timer de un invitado termina antes de recibir `estadoPartida`, queda sincronizando.
- El host activo publica `estadoPartida`; los invitados aplican estados nuevos e ignoran estados viejos o duplicados.

Regla de cierre y revancha:

- Al terminar una partida, la sala pasa a `finalizada`.
- Cuando el host vuelve al lobby, la misma sala regresa a `esperando`, elimina `partidaInicial`, `estadoPartida`, `estadoClientes`, los cuatro chats de RTDB y las acciones; tambien pone a todos los jugadores en no listos y limpia `votoMapa`.
- `ultimoResultado` sobrevive a la revancha. `chat`, `chat_traidores`, `chat_espectadores` y `chat_lobby` se vacian para no conservar basura de partidas anteriores.
- El navegador oculta salas `esperando` cuya `actualizadaEn` tenga mas de 30 minutos, para no mostrar salas huerfanas tras el cierre abrupto de un emulador o proceso.
- Una sala llena permite intentar reingreso; la transaccion valida si el UID ya pertenecia a ella antes de rechazar por falta de cupo.

### `partidas/{partidaId}/acciones/{accionId}`

Registro de acciones enviadas durante gameplay.

Campos:

- `tipo`: `accion_jugador` o `fase_avanzada`.
- `actorId`: id temporal del actor.
- `actorNombre`: nombre visible del actor.
- `actorEsHost`: booleano.
- `objetivoNombre`: nombre del objetivo, opcional y maximo 18 caracteres.
- `fase`: fase actual.
- `ronda`: numero de ronda, 0 a 30.
- `phaseIndex`: indice interno de fase, 0 a 500.
- `modoCliente`: por ahora `android`.
- `detalles`: mapa con datos especificos de la accion. Para robustez incluye `actorOrden` y, si hay objetivo, `objetivoOrden`; los nombres siguen guardandose para lectura humana.
- `creadaEn`: timestamp de servidor.
- `creadaEnLocal`: timestamp local.

### `partidas/{partidaId}/chat/{mensajeId}` (legacy)

Ruta Firestore conservada solamente para compatibilidad con APK anteriores. La aplicacion actual no escribe ni escucha mensajes aqui.

Campos:

- `actorId`: id temporal del jugador.
- `speaker`: nombre visible, maximo 18 caracteres.
- `mensaje`: texto entre 1 y 140 caracteres.
- `fase`: fase en la que se envio.
- `ronda`: numero de ronda, 0 a 30.
- `isGod`: booleano.
- `creadaEn`: timestamp de servidor.
- `creadaEnLocal`: timestamp local.

### `partidas/{partidaId}/chat_lobby/{mensajeId}` (legacy)

Ruta Firestore conservada solamente para compatibilidad con APK anteriores. La aplicacion actual usa `salas/{roomId}/chat_lobby` en RTDB.

Campos:

- `actorId`: id temporal del autor; debe coincidir con `request.auth.uid`.
- `speaker`: nombre visible, maximo 18 caracteres.
- `mensaje`: texto o etiqueta legible del emote, entre 1 y 140 caracteres.
- `tipo`: `texto` o `emote`.
- `emoteId`: id del emote del perfil; obligatorio solo cuando `tipo == emote`.
- `creadaEn`: timestamp de servidor.
- `creadaEnLocal`: timestamp local de fallback. El orden principal usa `creadaEn` del servidor.

Los avisos de sistema derivados de snapshots (entradas, salidas, listos, cambios de mapa y
host) son locales y no crean documentos. Los resultados importantes salen de
`ultimoResultado`, por lo que sobreviven a cierres y reingresos.

### `partidas/{partidaId}/chat_traidores/{mensajeId}` (legacy)

Ruta Firestore conservada solamente para compatibilidad con APK anteriores. La aplicacion actual usa `salas/{roomId}/chat_traidores` en RTDB y el cliente solo se suscribe si su rol es traidor.

Campos:

- `actorId`: id temporal del jugador (debe coincidir con `request.auth.uid`).
- `speaker`: nombre visible, maximo 18 caracteres.
- `mensaje`: texto entre 1 y 140 caracteres.
- `fase`: fase en la que se envio.
- `ronda`: numero de ronda, 0 a 30.
- `isGod`: booleano. Hoy los mensajes escritos por jugadores usan `false`; en una fase futura el host activo podra publicar lineas de sistema del plan con `true`.
- `canal`: constante `"traidores"`.
- `creadaEn`: timestamp de servidor.
- `creadaEnLocal`: timestamp local.

2+ killers online (activo desde 10 jugadores, con la entrada del espia):

- Con menos de 10 jugadores hay un solo asesino: elige y listo, sin votacion.
- **Ya funciona**: cada killer registra su accion `matar` en `acciones`; el host resuelve como victima la mas elegida y, si hay empate, sortea entre las empatadas con semilla estable por sala/ronda (`GameEngine.assassinVoteWinner`). Si nadie eligio, no muere nadie.
- Solo cuentan roles killer. El mercenario no participa de esta votacion porque su accion es silenciar.
- Cada killer ve la eleccion del otro como una linea de sistema en el canal (`"Nacho eligio a Mora como victima."`). Esas lineas **no se escriben en Realtime Database**: cada celular las deriva de las acciones que ya recibe por el listener de `acciones`, igual que los eventos de Dios se derivan de `estadoPartida`. Asi ningun cliente puede inyectar avisos falsos y no hizo falta tocar las reglas ni permitir `isGod = true` en el canal.
- Solo se avisa la **primera** eleccion confirmada de cada killer, que es la que despues cuenta en la resolucion.
- La decision final no se anuncia aparte: la victima aparece igual en el amanecer.

Seguridad actual del canal:

- **Integridad de autoria garantizada** por RTDB: `actorId == auth.uid`, o sea nadie puede escribir en nombre de otro.
- **Secreto de lectura honor-system**: RTDB no puede consultar los documentos de Firestore donde hoy viven membresia y roles. Por eso cualquier usuario autenticado que conozca el id de sala podria leer el nodo.
- Cerrar de verdad el canal exige reflejar membresia y rol en RTDB mediante un backend confiable, o repartir los mensajes desde Cloud Functions a nodos privados por jugador.
- El secreto general de roles tambien requiere un backend autoritativo que no exponga el reparto completo a los clientes.

### `salas/{roomId}/chat_espectadores/{pushId}`

Canal RTDB del Chat de los Muertos, disponible en la interfaz solo para jugadores eliminados de una
partida online. Los eliminados pueden leer el chat publico y escribir en este canal durante
cualquier fase mientras no haya ganador. Los jugadores vivos no crean el listener ni ven el
boton para acceder al canal.

Usa los mismos campos que `chat_traidores`, con `canal = "espectadores"`. RTDB garantiza la
integridad de autoria mediante `actorId == auth.uid`, pero la lectura sigue siendo
**honor-system**: un cliente modificado y autenticado podria leer el nodo aun estando vivo.
Cerrar esa lectura requiere el mismo backend autoritativo pendiente que el canal traidor.

## Realtime Database: datos vivos

El chat online y la presencia usan la base `traidores-default-rtdb`:

```text
/salas/{roomId}
    /chat/{pushId}
    /chat_traidores/{pushId}
    /chat_espectadores/{pushId}
    /chat_lobby/{pushId}
    /presencia/{uid}
```

Los cuatro chats usan claves generadas por `push()`, timestamp de servidor `ts` y listeners
limitados a los mensajes recientes. `chat`, `chat_traidores` y `chat_espectadores` conservan
tambien `matchId`, `fase` y `ronda`; `chat_lobby` permite `tipo = texto|emote` y `emoteId`
opcional.
Los eventos de Dios se siguen derivando de `estadoPartida` y no se duplican en RTDB.

`presencia/{uid}` contiene:

- `estado`: `conectado` o `desconectado`.
- `ts`: timestamp del servidor del ultimo cambio.

Cada cliente escucha `/.info/connected`. Primero registra el `onDisconnect()` que publicara
`desconectado` y despues publica `conectado`; el procedimiento se rearma automaticamente en
cada reconexion. Lobby, listos, acciones nocturnas, presentaciones y handoff de host consumen
esta presencia. Los campos `estado` y `ultimaConexion*` de Firestore quedan como fallback
legacy y no reciben heartbeats repetidos.

Las reglas garantizan autenticacion, autoria y tamanos. La lectura del canal de traidores y
el borrado completo de chats durante una revancha siguen siendo controles de confianza entre
clientes autenticados. Para cerrar esos dos puntos en produccion hace falta un backend que
refleje membresia, roles y autoridad del host.

## Limites actuales

Las reglas validan forma y tamanos, pero no pueden garantizar frecuencia fuerte de escritura. El cooldown local evita spam accidental, pero un cliente modificado podria seguir abusando.

Lectura: desde el barrido de reglas, `partidas`, `jugadores`, `acciones`, `perfiles_publicos`
y `meta/public_ids` exigen sesion iniciada. Ya no se puede leer una sala sin autenticarse,
pero **cualquier usuario autenticado sigue pudiendo leer cualquier sala**, y `partidaInicial`
incluye el rol de cada jugador: el secreto de roles sigue siendo honor-system.

Borrado en RTDB: vaciar un canal, borrar un mensaje o eliminar el nodo completo de una sala
exige tener nodo de presencia en esa sala. Antes bastaba con estar autenticado, asi que
cualquiera podia vaciar el chat de una partida ajena.

Pendiente para produccion:

- Firebase Auth con **cuentas reales** (hoy ya hay Auth anónima; falta login persistente que reemplace el `uidTemporal` anónimo).
- App Check para reducir clientes no autorizados.
- Cloud Functions para validar frecuencia, resolver sala llena de forma centralizada y limpiar salas.
- Reglas mas estrictas por rol, host y estado real de partida.

## Limpieza de salas

Por ahora no se borra automaticamente. La app puede marcar una sala como:

- `abandonada`: el host salio antes de terminar.
- `finalizada`: la partida termino.

Limpieza manual recomendada durante pruebas:

1. Ir a Firestore Console.
2. Abrir `partidas`.
3. Borrar documentos viejos con `estado` `abandonada` o `finalizada`.
4. Si quedan subcolecciones visibles, borrarlas desde el mismo documento.
5. En Realtime Database, borrar tambien `salas/{partidaId}` si quedo un nodo huerfano de una prueba interrumpida.

Limpieza futura:

- Agregar TTL basado en `actualizadaEn` o `ultimaActividadOnline`.
- O usar Cloud Function programada que elimine salas viejas.

## Criterios de prueba

- Crear sala desde Android y ver `partidas/{partidaId}`.
- Unirse por codigo y ver `jugadores/{uidTemporal}`.
- Enviar chat y ver `Realtime Database/salas/{partidaId}/chat/{pushId}`.
- Forzar el cierre de un cliente y ver `presencia/{uid}/estado = desconectado`.
- Hacer una accion nocturna o voto y ver `acciones/{accionId}`.
- Intentar escribir un mensaje vacio o demasiado largo desde consola y verificar que falle al desplegar reglas.
