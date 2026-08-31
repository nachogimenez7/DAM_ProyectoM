---
quick_id: 260831-rtd
status: complete
---

# Segunda optimizacion: estado online caliente en RTDB

## Delivered

- El host publica fases, jugadores publicos, votos y presentaciones compartidas en
  `salas/{roomId}/estado_partida` de Realtime Database.
- Todos los tamaños de mesa usan el mismo transporte; 14 jugadores fue solo el escenario de
  medicion que motivo la optimizacion.
- El listener Firestore del documento de sala queda para control: abandono, final, host activo
  y compatibilidad legacy. Ya no recibe una escritura por cada fase normal.
- Cada estado conserva un checkpoint durable en
  `partidas/{partidaId}/runtime/authoritative`, sin listener durante gameplay.
- El reingreso tras cerrar la app lee una sola vez el checkpoint y lo combina con la copia
  legacy sin depender del reloj de otro celular.
- Si RTDB rechaza una publicacion, el host vuelve automaticamente al campo Firestore legacy.
- La escucha RTDB se reintenta cuando la membresia/presencia queda lista.
- Rotar o recrear la pantalla conserva la version aplicada e ignora snapshots de sala viejos.
- Revancha y limpieza eliminan tanto el checkpoint como el estado caliente anterior.

## Expected impact

- Cada cambio autoritativo normal deja de producir una lectura Firestore del documento de sala
  en cada jugador conectado.
- Para `N` jugadores y `S` cambios de estado se evitan aproximadamente `N × S` lecturas
  visibles de Firestore, además de parte de las lecturas dependientes de reglas. Quedan una
  escritura de checkpoint y sus validaciones por cambio.
- La mejora es general para partidas de 5 a 15 jugadores y crece con el tamaño y la duración.
- El flujo normal no agrega esperas. Los invitados suelen recibir el estado con menor latencia;
  sólo recuperar después de cerrar la app agrega una lectura puntual de checkpoint.

## Verification and deployment

- `npm run test:database-rules`: OK, incluido host, miembro, intruso y `matchId` incorrecto.
- `npm run test:firestore-rules`: OK, incluido checkpoint, autorización y no retroceso de fase.
- `database.rules.json`, `firestore.indexes.json`, `firebase.json` y `.firebaserc`: JSON válido.
- `git diff --check`: sin errores.
- Índice `matchId + phaseIndex` desplegado previamente al proyecto `traidores`.
- Reglas Firestore y Realtime Database compiladas y desplegadas al proyecto `traidores` el
  31 de agosto de 2026.
- No se ejecutó Gradle ni compilación Android por instrucción del proyecto.

## Next measurement

- Instalar desde Android Studio y repetir la prueba comparable con 14 dispositivos.
- Probar específicamente una partida normal, rotación de pantalla, cierre/reingreso, caída del
  host y revancha.
- Comparar lecturas Firestore y revisar las líneas `firestore_usage` del tag
  `TraidoresOnline` antes de elegir la tercera fase de optimización.
