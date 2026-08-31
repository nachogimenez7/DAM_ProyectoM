---
quick_id: 260830-fco
status: complete
---

# Primera optimizacion de costos online en Firebase

## Delivered

- Gameplay deja de escuchar la coleccion completa de jugadores: usa el roster inmutable de la
  sesion y la presencia de Realtime Database.
- Solo el Comisario abre el listener de su pista privada durante la partida.
- Los invitados ya no duplican su presencia de gameplay en Firestore; el anfitrion conserva el
  espejo minimo que exigen las reglas actuales para el handoff.
- Los listeners de lobby se cierran antes de abrir gameplay, evitando solapamiento entre ambas
  pantallas.
- Cada cambio de `listo` deja de disparar una consulta completa diferida de todos los jugadores;
  el anfitrion conserva una unica verificacion de servidor antes de iniciar.
- Las consultas autoritativas de resolucion leen solo las acciones del `phaseIndex` actual, con
  un indice compuesto declarado para `matchId + phaseIndex`.
- Se agrego telemetria local por canal para listeners, documentos visibles, lecturas dependientes
  estimadas, consultas forzadas y escrituras.
- Se agregaron pruebas unitarias para la politica de listeners/presencia, el contador y la
  conservacion de jugadores registrados en el roster online.

## Expected impact

- En una partida de 14 jugadores, los listeners Firestore base de gameplay bajan de 56 a 29:
  14 de estado, 14 de acciones y 1 pista privada como maximo.
- Desaparecen 14 listeners de la coleccion de jugadores y hasta 13 listeners privados inutiles.
- Una secuencia completa de 14 jugadores marcandose listos evita aproximadamente 196 lecturas
  visibles de consultas redundantes, mas las lecturas dependientes de reglas.
- Las partidas largas dejan de releer el historial completo de acciones al resolver cada fase.

## Verification

- `firestore.indexes.json` validado como JSON.
- Referencias eliminadas y ciclos de vida revisados estaticamente.
- `git diff --check` sin errores.
- No se ejecuto Gradle, compilacion ni pruebas por instruccion del proyecto; el usuario validara
  desde Android Studio.

## Next measurement

- Repetir la misma prueba de 14 dispositivos y comparar lecturas, escrituras, eliminaciones y
  maximo de listeners.
- Recoger las lineas `firestore_usage lobby_transition`, `firestore_usage lobby` y
  `firestore_usage gameplay` del tag `TraidoresOnline` para atribuir cualquier consumo restante.
- Con esa medicion, la siguiente migracion es el estado autoritativo compartido de Firestore a
  Realtime Database, manteniendo un checkpoint durable para recuperacion.
