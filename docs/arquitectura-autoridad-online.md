# Arquitectura de autoridad online

## Estado actual

Firestore y Realtime Database validan identidad, membresia, forma y limites de los datos. Sin
embargo, el anfitrion activo todavia ejecuta `GameEngine`, reparte roles y publica el estado que
los demas clientes aceptan. Un APK de anfitrion modificado sigue pudiendo fabricar un resultado
valido para las reglas.

La primera frontera extraida en Android vive en `OnlineMatchStartContract.kt`:

- `OnlineMatchStartPolicy` decide si una sala puede empezar, normaliza jugadores y resuelve el
  mapa sin depender de Android ni del SDK de Firebase.
- `OnlineMatchStartPayloadFactory` define el estado publico, los repartos privados y el acceso
  de miembros a RTDB.
- `LobbyActivity` conserva por ahora la transaccion y la interaccion visual, pero ya no contiene
  las reglas ni el formato sensible del reparto.

El siguiente paso ya existe como backend local en `functions/`:

- `iniciarPartidaV2` es una callable de segunda generacion, ubicada en
  `southamerica-west1`, que exige Firebase Auth y tiene enforcement de App Check preparado.
- La funcion vuelve a leer la sala y toda la coleccion de jugadores; no acepta jugadores,
  conteos, roles ni resultados calculados por el telefono.
- Una transaccion de Firestore valida al anfitrion, estado, limpieza, cantidad, listos y votos;
  despues asigna roles con aleatoriedad criptografica y separa el payload publico de los
  repartos privados.
- La sincronizacion posterior con RTDB configura membresia y permisos de canales. Si esa segunda
  escritura falla, un reintento autorizado reconstruye RTDB desde el reparto ya confirmado en
  Firestore, sin crear otra partida.

La funcion se ejecuta y prueba solo con Firebase Emulator. No esta desplegada, no cambia las
reglas de produccion y Android todavia usa el flujo compatible del anfitrion. Por lo tanto, esta
etapa mejora la base y permite probar la migracion, pero por si sola todavia no elimina la trampa
en la version publicada.

## Limite de datos

El inicio separa tres superficies:

| Superficie | Contenido | Lectores |
|---|---|---|
| Estado publico | `matchId`, mapa, configuracion, nombres, uid y orden | miembros de la sala |
| Reparto privado | rol propio; para un traidor, tambien sus aliados visibles | dueño y autoridad |
| Acceso RTDB | membresia, vida y permisos de canales | reglas de RTDB |

El estado publico no debe contener `rolKey`, `rolNombre`, `rolEquipo`, `rolImagen` ni
`rolesVisibles`. Esta propiedad queda cubierta por una prueba recursiva del payload.

## Objetivo de produccion

El flujo objetivo es:

1. El anfitrion solicita `iniciarPartida(roomId, eleccionDesempate)`.
2. Un backend autenticado vuelve a leer sala y jugadores dentro de una operacion consistente.
3. El backend valida anfitrion, estado, limpieza, cantidad, jugadores listos y votos de mapa.
4. El backend genera el `matchId`, asigna roles y escribe estado publico y repartos privados.
5. Las reglas dejan de permitir que el cliente escriba `partidaInicial`, `estadoPartida`,
   `repartos`, `hostActivoId` y los permisos sensibles de RTDB.
6. Los clientes solamente observan la confirmacion y entran a la partida como hoy.

Luego se aplica el mismo patron, por orden de riesgo, a resolucion de acciones nocturnas,
votacion, eliminaciones, victoria y traspaso de autoridad.

## Pruebas locales

Requisitos: Node 20 y Java disponible para los emuladores. Desde la raiz del repositorio:

```powershell
npm install
npm --prefix functions install
npm run test:functions-unit
npm run test:functions
npm run simulate:functions-local
```

`test:functions-unit` cubre politica, composicion, privacidad, autenticacion e idempotencia.
`test:functions` levanta Functions, Firestore y RTDB Emulator y verifica las escrituras completas,
el rechazo de intrusos, el desempate sin efectos y la reparacion de RTDB tras un fallo parcial.
`simulate:functions-local` mide inicio, reintentos, llamada callable y concurrencia despues de un
calentamiento. Los valores son comparativos: no incluyen Internet y el emulador no representa el
escalado de Google Cloud.

No ejecutar `firebase deploy --only functions` en esta etapa. El proyecto sigue en Spark y el
despliegue de Cloud Functions requiere una decision explicita sobre Blaze.

## Migracion compatible

- No endurecer reglas mientras una version publicada todavia necesite autoridad de cliente.
- Agregar una version de protocolo/autoridad a cada partida nueva.
- Implementar el backend y probarlo primero en salas de prueba, sin cambiar las salas actuales.
- Publicar un AAB compatible y observar errores antes de exigir autoridad de servidor.
- Recien entonces cerrar las escrituras antiguas en Firestore y RTDB.

## Proximo cambio

Conectar Android a `iniciarPartidaV2` mediante una version de protocolo y una ruta de prueba que
apunte al emulador. Mientras no haya backend desplegado, produccion debe conservar el flujo actual.
Cuando se decida habilitar Blaze, el orden seguro es: desplegar la funcion, publicar un AAB que la
use, observar errores y adopcion, y solo despues cerrar las escrituras autoritativas antiguas.
