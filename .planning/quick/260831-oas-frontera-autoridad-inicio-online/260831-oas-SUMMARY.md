---
quick_id: 260831-oas
status: complete
---

# Frontera de autoridad para inicio y reparto online

## Delivered

- `OnlineMatchStartPolicy` concentra autorizacion, idempotencia, estado de sala, cantidad,
  jugadores listos, orden estable y resolucion de mapa.
- `OnlineMatchStartPayloadFactory` concentra el contrato de estado publico, repartos privados y
  acceso RTDB.
- `LobbyActivity` conserva la transaccion Firestore y la UI, pero deja de construir o decidir
  directamente el reparto sensible.
- El esquema, las rutas Firebase y la jugabilidad permanecen compatibles con la version actual.
- La arquitectura objetivo y el despliegue gradual quedaron documentados en
  `docs/arquitectura-autoridad-online.md`.

## Security properties covered

- Solo el anfitrion estable o activo puede solicitar el inicio.
- Reintentar una partida ya creada es idempotente.
- Una sala en limpieza, fuera de espera, incompleta o con jugadores no listos no inicia.
- El estado publico no contiene campos de asignacion de roles.
- Un jugador normal recibe solo su rol; un traidor recibe su rol y los aliados traidores.

## Verification

- `testDebugUnitTest`: 589 pruebas, 0 fallos, 0 errores y 0 omitidas.
- `assembleDebug`: correcto con Java 17 de Android Studio.
- `git diff --check`: correcto; solo advertencias esperadas de normalizacion LF/CRLF.

## Next

Definir el contrato remoto de `iniciarPartida` y preparar una implementacion de backend sin
desplegarla ni cerrar escrituras antiguas hasta tener versionado de protocolo y un AAB probado.
