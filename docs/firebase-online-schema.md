# Firebase online experimental

El modo online de Traidores sigue siendo experimental. Esta documentacion define el contrato actual para poder probar sin perder de vista los limites: **sí hay Firebase Auth anónima** (`OnlineTempIdentity.ensureAuthenticated` hace `signInAnonymously`; el `uidTemporal` es el uid anónimo y las reglas exigen `request.auth.uid`), pero todavia no hay Auth con cuentas reales, Cloud Functions, App Check ni backend autoritativo completo.

## Objetivo actual

- Crear y buscar salas online desde Android.
- Unirse a una sala por codigo de 6 caracteres.
- Mantener presencia basica de jugadores.
- Iniciar partidas online solo con cantidad esperada completa.
- Reconstruir partidas desde `partidaInicial` y `estadoPartida` al reingresar.
- Registrar acciones de gameplay y mensajes de chat.
- Reservar un ID publico numerico fijo para perfil y futuros amigos.
- Evitar datos obviamente invalidos desde reglas y desde el cliente.

El modo confiable para demo sigue siendo `Jugar vs IA` mientras el online madura.

## Configuracion del repo

- `firebase.json`: apunta a `firestore.rules`.
- `firestore.rules`: reglas de Firestore para pruebas online.
- `app/google-services.json`: configuracion local de la app Android.

Para publicar reglas desde una maquina con Firebase CLI:

```bash
firebase deploy --only firestore:rules
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
- `origen`: origen tecnico, por ejemplo `android-online-create`.
- `creadaEn`: timestamp de servidor.
- `actualizadaEn`: timestamp de servidor.

Campos agregados durante la partida:

- `partidaInicial`: snapshot inicial generado una sola vez por el host. Cada jugador queda ligado por `uidTemporal`.
- `estadoPartida`: estado autoritativo publicado por el host.
- `estadoClientes`: estado resumido publicado por cada cliente.
- `ultimaActividadOnline`: timestamp de actividad reciente.

Dentro de `estadoPartida`, ademas de fase, ronda, jugadores y votos, se publican dos
datos de presentacion compartida:

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
- `estado`: `conectado`, `desconectado` o `listo`.
- `esHost`: booleano opcional.
- `listo`: booleano opcional.
- `orden`: posicion estable del jugador dentro de la sala.
- `activoEnPartida`: booleano para distinguir presencia de jugador activo.
- `unidoEn`: timestamp de servidor.
- `ultimaConexion`: timestamp de servidor.
- `ultimaConexionLocal`: timestamp local del dispositivo.

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
- 5 a 15 jugadores: 1 asesino, 1 medico, 1 comisario/detective y el resto aldeanos.

No se agregan por defecto Alcalde, Mercenario, Desertor, Espia, Bufon, Oraculo ni Payador en online. Siguen disponibles para local/IA y fases futuras.

Reglas importantes:

- La sala no inicia hasta que `jugadoresActuales == jugadoresEsperados` y todos esos jugadores esten `listo`.
- Si un jugador se desconecta antes de iniciar, el host puede liberar su cupo marcando `activoEnPartida = false`; el documento queda como historial y `jugadoresActuales` se recalcula.
- `partidaInicial` se crea una sola vez.
- El inicio online debe escribirse por transaccion: si `partidaInicialCreada` ya es `true`, no se reparten roles de nuevo.
- Al crear sala, la app verifica que el `codigoSala` generado no exista ya en Firestore; si colisiona, reintenta con otro codigo.
- Al unirse por codigo, la app usa solo salas `esperando`; si hubiera mas de una sala activa con el mismo codigo, bloquea el ingreso y pide crear una sala nueva.
- Si un cliente reingresa, reconstruye desde `partidaInicial` y `estadoPartida`.
- Si la sala esta `esperando`, el reingreso vuelve al lobby.
- Si la sala esta `en_juego`, el reingreso abre gameplay directo con la misma carta, fase y estado vivo/muerto.
- El reingreso nunca debe llamar al reparto local como fallback. Si faltan `partidaInicial`, `estadoPartida`, `fase` o el jugador por `uidTemporal`, se muestra error y se limpia la recuperacion local.
- Si un jugador esta desconectado durante noche o votacion, su accion cuenta como ausente.
- Si el host activo cae, el primer jugador conectado segun `orden` puede tomar `hostActivoId`.
- En gameplay online, los clientes solo registran acciones/votos; el host activo publica el resultado en `estadoPartida`.
- La noche y la votacion esperan el timer completo. No hay avance temprano aunque todos hayan actuado.
- En noche, si un jugador envia mas de una accion valida para la misma ronda, se toma la ultima por `creadaEnLocal`.
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
- `ultimaFaseAplicadaEnLocal`: fase e indice que el cliente tiene aplicada localmente, por ejemplo `NOCHE_ASESINO:1`.
- `jugador`, `rolKey`: datos de depuracion para Logcat/Firebase Console.

Regla de sincronizacion por fase:

- En online, los invitados no avanzan fases localmente.
- Si el timer de un invitado termina antes de recibir `estadoPartida`, queda sincronizando.
- El host activo publica `estadoPartida`; los invitados aplican estados nuevos e ignoran estados viejos o duplicados.

Regla de cierre y revancha:

- Al terminar una partida, la sala pasa a `finalizada`.
- Cuando el host vuelve al lobby, la misma sala regresa a `esperando`, elimina `partidaInicial`, `estadoPartida` y `estadoClientes`, y pone a todos los jugadores en no listos.
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

### `partidas/{partidaId}/chat/{mensajeId}`

Mensajes de chat de gameplay.

Campos:

- `actorId`: id temporal del jugador.
- `speaker`: nombre visible, maximo 18 caracteres.
- `mensaje`: texto entre 1 y 140 caracteres.
- `fase`: fase en la que se envio.
- `ronda`: numero de ronda, 0 a 30.
- `isGod`: booleano.
- `creadaEn`: timestamp de servidor.
- `creadaEnLocal`: timestamp local.

### `partidas/{partidaId}/chat_traidores/{mensajeId}`

Canal secreto del equipo asesino ("Plan de los Asesinos"). En online es humano contra humano: no hay bots, asi que solo transporta mensajes escritos por jugadores traidores. La UI (piel roja, toggle PUEBLO/PLAN) es la misma que en local; solo cambia la fuente de los mensajes (Firestore en vez del motor local). El cliente solo se suscribe si su rol es traidor.

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

Diseno futuro para 2+ killers online:

- Mientras el preset online tenga 1 solo asesino, no hay votacion nocturna de asesinos: elige y listo.
- Cuando el preset sume mas killers (por ejemplo asesino + espia, o 2 asesinos), cada killer registra su accion nocturna normal en `acciones`.
- El host activo lee la ultima accion valida de cada killer para la ronda/fase y publica en `chat_traidores` una linea de sistema por eleccion: `"Nacho eligio a Mora como su victima"`.
- Al cerrar la noche por timer, el host activo resuelve como victima a la mas elegida. Si hay empate, sortea entre las empatadas con semilla estable por sala/ronda; si nadie eligio, no muere nadie.
- El host activo publica la decision final en `chat_traidores` antes de aplicar la muerte en `estadoPartida`.
- Solo cuentan roles killer. El mercenario no participa de esta votacion porque su accion es silenciar.

Seguridad actual del canal:

- **Integridad de autoria garantizada** por reglas: `actorId == request.auth.uid`, o sea nadie puede escribir en nombre de otro.
- **Secreto de lectura honor-system**, igual que el secreto de roles en online: como `partidas/{id}` y `partidaInicial` son `allow read: if true`, un cliente puede leer los roles de todos con o sin este chat. El chat de traidores no agrega una clase nueva de vulnerabilidad; hereda la que ya existe.
- Para cerrar la lectura del canal a nivel servidor: reemplazar `allow read: if true` en `chat_traidores` por una funcion que haga `get()` sobre `partidaInicial` y verifique que el `request.auth.uid` tiene rol traidor. Requiere que el reparto guarde el rol por uid en un shape navegable por reglas. Como todos los documentos del canal exigen la misma condicion, el listener de la subcoleccion pasa entero para un traidor y es rechazado entero para un no-traidor.
- Cierre real del secreto general de roles requiere backend autoritativo (Cloud Functions) que reparta y guarde roles sin exponerlos world-readable, o payloads de rol cifrados por jugador.

## Limites actuales

Las reglas validan forma y tamanos, pero no pueden garantizar frecuencia fuerte de escritura. El cooldown local evita spam accidental, pero un cliente modificado podria seguir abusando.

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

Limpieza futura:

- Agregar TTL basado en `actualizadaEn` o `ultimaActividadOnline`.
- O usar Cloud Function programada que elimine salas viejas.

## Criterios de prueba

- Crear sala desde Android y ver `partidas/{partidaId}`.
- Unirse por codigo y ver `jugadores/{uidTemporal}`.
- Enviar chat y ver `chat/{mensajeId}`.
- Hacer una accion nocturna o voto y ver `acciones/{accionId}`.
- Intentar escribir un mensaje vacio o demasiado largo desde consola y verificar que falle al desplegar reglas.
