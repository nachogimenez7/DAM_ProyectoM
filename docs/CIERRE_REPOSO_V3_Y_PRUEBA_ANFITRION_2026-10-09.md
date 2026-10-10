# Cierre del reposo V3 y prueba con anfitrión — 9/10/2026

## Información para Claude

El cierre del gate por sí solo NO eliminaba el trabajo en reposo: accionPartidaV3 y resolverFaseV3 conservaban minInstances=1; repararPartidasV3 ejecutaba una consulta cada minuto incluso sin partidas. La medición previa de 15 minutos (07:25–07:40 UTC) mostró 15 reparaciones sin salas, 16 lecturas Firestore y cero escrituras; el agregado de segundos de instancia no es un precio ni costo por partida. Ver docs/CHEQUEO_COSTOS_BETA_2026-10-09.md.

Por pedido explícito del usuario de cerrar V3, se realizó SOLO configuración remota:

- onlineMaintenance/serverAuthority: enabled=false, allowlists vacías.
- config/onlineV3: enabled=false.
- Job repararPartidasV3: PAUSED.
- Cloud Run accionpartidav3 y resolverfasev3: mínimo de la revisión activa = 0; máximos 4/2 conservados, misma imagen de contenedor. Revisiones anteriores inactivas.
- Worker conserva invoker exclusivo del service account 99323018581-compute@developer.gserviceaccount.com.
- Ninguna partida pendiente en serverOutbox antes del cierre.

No se desplegó fuente, no se deshabilitó App Check y no se modificaron Storage, historial ni limpiarSalasAbandonadasV1. La facturación/Monitoring puede tardar en reflejar la reducción. Esto elimina las reservas mínimas y el cron V3; no promete costo cero para todo el proyecto.

Respaldo y verificación: output/pausa-v3-2026-10-09/{before,after,operations,revisions}.json. La configuración de Functions puede conservar el mínimo antiguo como metadato aunque la revisión efectiva en Run esté en cero: un despliegue futuro debe revisar esos valores y mantener pausado el job.

## Preparado sin desplegar

functions/src/onlineGameFunctions.js: mínimos cero para acción/worker, manteniendo CPU/concurrencia/IAM. functions/src/onlineGameRecovery.js: fallback cada 15 minutos. Pruebas de configuración actualizadas: 78/78 unitarias del backend. Un despliegue futuro podría reactivar Scheduler: requiere autorización y verificar/reaplicar la pausa. V3 se prueba localmente por ahora.

## Medición

Snapshot inicial: output/firebase-capacity/snapshot-*.json, tomado a las 08:03 UTC. Últimas 24 horas: 2.918 lecturas y 599 escrituras Firestore; incluyen prácticas, NO son reposo puro. No sumar métricas legacy duplicadas a las nuevas. La ventana de 24 h sin partidas aún queda pendiente: las pruebas que siguen interrumpen el reposo.

## Dispositivos y build

Debug 53/0.1.52, SERVER_ONLINE_V3=false, USE_ONLINE_AUTHORITY_EMULATOR=false, Storage habilitado. La app existente del A56 usa otra firma: NO fue desinstalada ni se borraron sus datos. Se creó una variante separada com.traidores.juego.hostqa mediante un init script externo (sin editar app/build.gradle), con la misma lógica normal y Firebase real. APK: output/beta-anfitrion-cloud-2026-10-09/traidores-hostqa-53.apk. Se instaló también en Pixel_10. No se usa CloudQaLaunchActivity ni el motor V3.

Credenciales QA privadas en output, nunca para publicar. Tokens debug temporales específicos de estos dispositivos, sin reducir enforcement. Las pruebas visuales de fotos, partida, historial y relevo se documentarán a continuación con resultados reales; no reemplazar una partida por un resultado sembrado mediante Admin.

## Avance de fotos e historial

La foto elegida por el usuario se subió a Storage (JPEG de 68.859 bytes). La URL se propagó al perfil público y al jugador de la sala. La representación visual todavía falló en el cliente remoto y en la tarjeta propia: ver FALLO_FOTOS_BETA_ANFITRION_2026-10-09.md para Claude, responsable del cargador. No se editó ese archivo.

La partida real con anfitrión ed1be97c-34b6-40d2-b105-72610635b276 terminó y la sala volvió a esperando. Se consultaron las dos cuentas QA en Firestore: ambas tienen un registro de historial con ese mismo matchId y won=true. Evidencia privada: output/beta-anfitrion-cloud-2026-10-09/history-check.json. Esto verifica la persistencia en las dos cuentas; no equivale a aprobar todas las acciones ni la presentación del historial en ambos teléfonos.

El A56 dejó de estar disponible por ADB al continuar la revisión; se pidió reconectarlo. Quedan pendientes reemplazo/quitar foto, interrupción de subida, recuperación en otro dispositivo, regresión completa y relevo. No se sembraron resultados mediante Admin.
