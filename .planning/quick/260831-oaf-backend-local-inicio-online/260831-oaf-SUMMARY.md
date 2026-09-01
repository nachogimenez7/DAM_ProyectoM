---
quick_id: 260831-oaf
status: complete
---

# Backend local autoritativo para el inicio online

## Delivered

- `iniciarPartidaV2`, callable gen2 en `southamerica-west1`, exige Firebase Auth y prepara
  enforcement de App Check.
- El backend vuelve a leer sala y jugadores, valida el inicio, resuelve mapa, genera un `matchId`
  y asigna roles con aleatoriedad criptografica.
- Firestore recibe un estado publico sin roles y un reparto privado por jugador dentro de una
  transaccion idempotente.
- RTDB recibe membresia y permisos despues del commit; un reintento autorizado repara esa
  sincronizacion sin crear otra partida ni cambiar roles.
- Functions Emulator y comandos de prueba quedaron integrados al proyecto, sin despliegues,
  facturacion ni cambios de reglas.

## Security properties covered

- Solo el anfitrion estable o activo puede iniciar o reintentar.
- El servidor no confia en listas, conteos, votos ni roles enviados por Android.
- El payload publico no contiene campos privados de roles.
- Un ciudadano recibe solo su rol; un traidor recibe tambien sus aliados traidores.
- Empates no escriben estado y los reintentos conservan el `matchId` y reparto originales.
- Las dependencias del backend no tienen vulnerabilidades conocidas segun `npm audit`.

## Verification

- Unitarias: 12 aprobadas.
- Integracion Firestore + RTDB + Functions Emulator: 3 aprobadas.
- Reglas Firestore: aprobadas.
- Reglas Realtime Database: aprobadas.
- Definicion cargada por el emulador: `iniciarPartidaV2`.

## Next

Conectar Android al emulador mediante una version de protocolo sin tocar el flujo productivo.
Para proteger partidas reales hara falta desplegar la funcion, publicar primero un AAB compatible
y cerrar las escrituras autoritativas del cliente solo despues de comprobar adopcion y estabilidad.
