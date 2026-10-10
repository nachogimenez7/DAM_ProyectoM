# Preparación de beta V3 — 8/10/2026

Backend actualizado en traidores. **La beta pública todavía no se abrió.**

## Despliegue verificado

Reglas Firestore y RTDB e índices desplegados. Los seis índices de partidas están
READY. Functions actualizadas y verificadas ACTIVE después del despliegue:

- Santiago: iniciarPartidaV3, accionPartidaV3, recuperarFaseV3,
  prepararRevanchaV3, abandonarPartidaV3, publicarPartidaV3, guardarHistorialOnlineV1.
- São Paulo: resolverFaseV3 y repararPartidasV3, conservando sus regiones de Tasks/cron.

Actualización completada aproximadamente a las 21:25 del 8/10, hora argentina.
Todas tienen minInstances=0; los máximos vigentes se registraron en
output/v3-cloud/2026-10-08/deployment-verified.json. Esto acota instancias, no fija un
techo de factura. El cron de recuperación sigue siendo un recurso con uso propio.

Backups previos de reglas y configuración en output/v3-cloud/2026-10-08/.
Logs: output/paridad-visual/ajustes-cloud-rules.log y ajustes-cloud-functions.log.
App Check permanece habilitado; no se abrió Storage ni se cambiaron sus reglas.

Estado confirmado después del despliegue:

```json
// onlineMaintenance/serverAuthority
{"enabled": false, "allowedHostUids": [], "allowedRoomIds": []}
// config/onlineV3
{"enabled": false, "minVersionCode": 52}
```

## Build y distribución

Versión preparada: versionCode 52, versionName 0.1.51. Release R8 minificada con
-PtraidoresServerOnlineV3=true. Bundle en app/build/outputs/bundle/release/app-release.aab.
**Sin firma de subida, sin subida a Play, sin pista asignada.** El APK aislado Debug
sirve para QA y no demuestra Play Integrity ni comportamiento de una entrega Play.

## Qué se probó y qué sigue pendiente

733 unitarias Android, 73 backend, 39 integración, 107 checks de reglas de autoridad
y 35 de emotes aprobados. Prueba nativa con dos Android emulados: paleta común,
burbuja una vez, reconexión sin repetir y chat en votación. Revancha limpia comprobada.
Regresión nativa de mesa también aprobada: roles, desempates, permisos, transición,
expulsión, Bufón y reconexión en resultado, contra backend emulado.
Detalles, capturas y medición parcial en AJUSTES_MESA_EMOTES_V3_ANDROID_2026-10-08.md.

El interruptor se probó como política de cliente y reglas (apagado, versión mínima,
solo lectura). **No se probó todavía un apagado desde una build instalada por Play.**
El aislamiento de versiones se comprobó en reglas y código; falta un APK 0.1.50 real.
No se verificó esta entrega visual en el A56 ni una partida Cloud de cinco teléfonos,
sus p50/p95, factura completa, historial final y revancha desde Play.

## Orden de la próxima prueba

La firma queda para la otra computadora del usuario, después de entregar los cambios.
Guía completa: [PRUEBA_INTERNA_PLAY_V3_52.md](PRUEBA_INTERNA_PLAY_V3_52.md).
La preparación adicional del piloto incorpora `allowedUids` en el menú, con tres
pruebas unitarias aprobadas; el permiso se recalcula si cambia el UID de la cuenta.

1. Firmar el AAB con la clave de subida existente y distribuir por prueba interna.
2. Registrar/verificar SHA-256 de firma Play en Firebase y Play Integrity real.
3. Habilitar el menú solo para UID de prueba mediante config/onlineV3.allowedUids.
4. Crear la sala privada y habilitar solo su creador y su ID en el gate del servidor.
   Aprobar la mesa en A56 con el usuario antes de la partida completa.
5. Partida real completa: acciones, expulsión/Bufón, reconexión, abandono, resultado,
   historial y revancha. Medir lecturas/escrituras, tráfico RTDB, invocaciones y latencia.
6. Probar apagado, revisar logs y aislamiento con APK antiguo; decidir apertura.

No abrir a todos ni promover a beta hasta completar estas condiciones.

## Apagado desde la consola, sin la Mac

El propietario/admin con permisos del proyecto puede hacerlo en Firebase → Firestore:

1. En onlineMaintenance/serverAuthority, poner enabled=false y dejar allowedHostUids
   y allowedRoomIds vacíos. No borrar el documento.
2. En config/onlineV3, poner enabled=false; conservar minVersionCode=52 y poner
   allowedUids=[].
3. Entrar de nuevo al menú online en la build beta: debe mostrar «Online en mantenimiento».
   Las partidas que ya comenzaron siguen su plazo y pueden recuperarse.
4. Registrar la hora y revisar logs de Functions y consumo en Google Cloud.

No deshabilitar facturación ni borrar salas para hacer mantenimiento. Las alertas
de presupuesto avisan; no cortan el gasto. Confirmar destinatarios en Facturación.

Cuando se apruebe apertura general, el gate será enabled=true **sin campos de listas**;
listas presentes pero vacías bloquean inicios. Config cliente enabled=true, mínimo52,
sin allowedUids para apertura general; durante el piloto conservar los UID concretos.
Este procedimiento está documentado; la apertura y promoción no se ejecutaron.
