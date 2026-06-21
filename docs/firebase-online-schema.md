# Firebase online experimental

El modo online de Traidores sigue siendo experimental. Esta documentacion define el contrato actual para poder probar sin perder de vista los limites: todavia no hay Firebase Auth, Cloud Functions, App Check ni backend autoritativo completo.

## Objetivo actual

- Crear y buscar salas online desde Android.
- Unirse a una sala por codigo de 6 caracteres.
- Mantener presencia basica de jugadores.
- Registrar acciones de gameplay y mensajes de chat.
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
- `maxJugadores`: 1 a 15.
- `jugadoresActuales`: contador visible de jugadores.
- `modoPrueba`: booleano para marcar salas experimentales.
- `origen`: origen tecnico, por ejemplo `android-online-create`.
- `creadaEn`: timestamp de servidor.
- `actualizadaEn`: timestamp de servidor.

Campos agregados durante la partida:

- `partidaInicial`: snapshot inicial que usa el host para iniciar gameplay.
- `estadoPartida`: estado autoritativo publicado por el host.
- `estadoClientes`: estado resumido publicado por cada cliente.
- `ultimaActividadOnline`: timestamp de actividad reciente.

### `partidas/{partidaId}/jugadores/{uidTemporal}`

Documento de presencia por jugador.

Campos:

- `nombre`: nombre visible, maximo 18 caracteres.
- `uidTemporal`: id local del dispositivo. Debe coincidir con el id del documento.
- `estado`: `conectado`, `desconectado` o `listo`.
- `esHost`: booleano opcional.
- `listo`: booleano opcional.
- `unidoEn`: timestamp de servidor.
- `ultimaConexion`: timestamp de servidor.

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
- `detalles`: mapa con datos especificos de la accion.
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

## Limites actuales

Las reglas validan forma y tamanos, pero no pueden garantizar frecuencia fuerte de escritura. El cooldown local evita spam accidental, pero un cliente modificado podria seguir abusando.

Pendiente para produccion:

- Firebase Auth para reemplazar `uidTemporal`.
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
