---
quick_id: 260831-oaf
status: complete
---

# Backend local para iniciar partidas online

## Goal

Implementar y probar en Firebase Emulator una funcion callable que valide el inicio, resuelva
el mapa, reparta roles y escriba estado publico/repartos privados sin confiar en el cliente.

## Tasks

1. [x] Portar composicion, normalizacion y reparto a un nucleo Node puro y probado.
2. [x] Implementar `iniciarPartidaV2` con Auth obligatorio y App Check preparado.
3. [x] Escribir la transaccion Firestore mediante Admin SDK con idempotencia.
4. [x] Configurar Functions Emulator sin desplegar ni modificar reglas reales.
5. [x] Probar el nucleo, el callable y la privacidad del payload.
6. [x] Documentar ejecucion local, limites y conexion Android futura.

## Estado de verificacion

- `npm --prefix functions test`: 12 pruebas, 0 fallos.
- `npm run test:functions`: 3 pruebas de integracion, 0 fallos; callable cargada.
- `npm run test:firestore-rules`: correcto.
- `npm run test:database-rules`: correcto.
- `npm audit --omit=dev` en `functions/`: 0 vulnerabilidades conocidas.
- `git diff --check`: correcto; solo advertencias esperadas de LF/CRLF.

## Must Haves

- Cero despliegues y cero cambios de facturacion.
- Ningun rol en el documento publico de la partida.
- Solo host estable o activo; sala esperando, sin limpieza y con todos listos.
- El backend vuelve a leer todos los datos y no confia en conteos ni jugadores enviados.
- Reintentos idempotentes por sala/partida.
- Node 20, compatible con el runtime local y soportado por Cloud Functions.
