# Arquitectura de autoridad online

## Estado actual

Firestore y Realtime Database validan identidad, membresia, forma y limites de los datos. Sin
embargo, el anfitrion activo todavia ejecuta `GameEngine`, reparte roles y publica el estado que
los demas clientes aceptan. Un APK de anfitrion modificado sigue pudiendo fabricar un resultado
valido para las reglas.

La primera frontera extraida vive en `OnlineMatchStartContract.kt`:

- `OnlineMatchStartPolicy` decide si una sala puede empezar, normaliza jugadores y resuelve el
  mapa sin depender de Android ni del SDK de Firebase.
- `OnlineMatchStartPayloadFactory` define el estado publico, los repartos privados y el acceso
  de miembros a RTDB.
- `LobbyActivity` conserva por ahora la transaccion y la interaccion visual, pero ya no contiene
  las reglas ni el formato sensible del reparto.

Esta extraccion no vuelve confiable al anfitrion. Su objetivo es fijar un contrato probado antes
de mover la autoridad y evitar que esa migracion cambie la jugabilidad por accidente.

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

## Migracion compatible

- No endurecer reglas mientras una version publicada todavia necesite autoridad de cliente.
- Agregar una version de protocolo/autoridad a cada partida nueva.
- Implementar el backend y probarlo primero en salas de prueba, sin cambiar las salas actuales.
- Publicar un AAB compatible y observar errores antes de exigir autoridad de servidor.
- Recien entonces cerrar las escrituras antiguas en Firestore y RTDB.

## Proximo cambio

Definir el contrato remoto de `iniciarPartida`, su respuesta y errores, y preparar una
implementacion de servidor separada. Asociar facturacion o desplegar infraestructura queda fuera
de esta extraccion y requiere una decision explicita antes de generar costos o cambiar reglas.
