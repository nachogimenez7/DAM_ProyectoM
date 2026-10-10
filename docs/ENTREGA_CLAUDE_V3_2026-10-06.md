# Entrega a Claude: consumo y recuperación V3

La revisión posterior de A/B y N-1/N-3/N-4/N-7 está implementada y documentada en
`docs/RESPUESTA_REVISION_V3_2026-10-07.md`. Ese documento y el contrato actualizado
mandan sobre la sección de DTO de esta entrega inicial.

6 de octubre de 2026. Codex avanzó en backend/medición mientras Claude trabajaba en el desempate local de iOS. No se tocaron archivos iOS ni presentación Android.

## Qué cambió

- `functions/src/onlineGameService.js`: outbox con `recoveryAtMs`; publicación ya entregada consume 1 lectura en lugar de 2; orden consistente sala → estado → outbox, incluido el desbloqueo del lobby.
- `functions/src/onlineGameRecovery.js`: servicio y función programada `repararPartidasV3`, exportada desde `index.js`. Rescata fases vencidas y publicaciones pendientes de resultado/revancha/salida desde lobby, sin clientes. Reutiliza motor, validaciones y publicación V3.
- `firestore.indexes.json`: exenciones para datos privados e índice de grupo de recuperación. Debe desplegarse y estar listo antes de depender del scheduler en Cloud.
- Mediciones reproducibles de servicios, contadores que admiten concurrencia/reintentos y listeners SDK bajo reglas por UID. Informe en `docs/MEDICION_CONSUMO_V3_2026-10-06.md`.
- Scripts npm incorporados a suites existentes. Reglas originales conservadas; se agregaron pruebas que niegan lectura/escritura cliente del cursor de recuperación.

## Contrato para clientes

No cambian nombres/payloads de callables, DTO públicos/privados, permisos, roles ni decisiones cerradas. `recoveryAtMs` y `onlineMaintenance/serverRecovery` son internos de Admin: el cliente no los lee/escribe.

Tasks mantiene deadline + 1.500 ms; recuperación cliente conserva 5 s de gracia, dispersión por UID y máximo de reintentos por fase. El respaldo servidor revisa después de 30 s y corre cada minuto con lote acotado. Los clientes muestran que el servidor está resolviendo y bloquean intenciones tardías; no implementan otro reloj ni transición local.

La revancha sigue bloqueada hasta que la publicación quite secretos/chat anteriores. El respaldo también termina ese desbloqueo si no hay clientes conectados. Salir durante juego sigue siendo `ABANDONO`; perder conexión permite volver.

## Resultado y riesgo abierto

18 recorridos de servicios y 3 de SDK: 5/10/15 jugadores; cortas; reenvíos; largas de 5–6 rondas en los tres mapas con tres dobles empates; ventanas Alcalde/Desertor; ráfagas simultáneas. Historial correcto y sin eliminaciones AFK.

Pampa: 213/353/613 lecturas en cortas y 573/917/1.399 en largas. No son facturas ni máximos. Acciones privadas no generaron eventos del listener público.

Concurrencia local elevó reintentos/latencia. La repetición con orden consistente mejoró algunos tiempos, pero no demuestra rendimiento productivo. Mantener este hallazgo abierto: validar en Cloud y con ambos clientes antes del acceso público. El tráfico interno completo de RTDB y chat/fotos todavía necesitan medición.

## Revisión al terminar el desempate

No hace falta interrumpir el trabajo actual. Cuando esté listo, revisar presentación y contrato de:

1. `DESEMPATE_VOTACION` → segundo recuento → `ALCALDE_DESEMPATE` o `RESULTADO`, según capacidad pública del Alcalde. Incapacidad secreta conserva ventana/plazo sin exponer rol.
2. Cero con publicación tardía: UI espera al servidor; `ClassicGame` no resuelve online.
3. Reconexión con fase avanzada: aplicar la proyección correspondiente más nueva, sin repetir ventanas/sonidos. Comparar generaciones/fases/revisiones dentro del mismo `matchId`.
4. Resultado y revancha: otro `matchId`, secretos/chat borrados y poderes reiniciados. No tratar partidas distintas como actualizaciones de la misma.

El desempate local de Claude no convierte automáticamente el gameplay iOS en cliente V3. Salas/lobby y gameplay reales de iOS siguen pendientes de integración con el protocolo. Android conserva presentación V3 experimental: falta incorporar la habitual.

## Producción y siguiente bloque

Este bloque está en el árbol local. No se desplegaron Functions/índices ni se activó el gate; el online publicado conserva el protocolo anterior. No se subieron las modificaciones actuales de Claude durante esta tarea.

Orden restante:

1. Cerrar presentación Android y adaptadores/gameplay V3 iOS; sesión mixta con creador desconectado.
2. Desplegar índices, comprobar estado listo y luego Functions con gate cerrado.
3. Validar IAM/invocación privada de Tasks y Scheduler. Configurar alertas del worker, `online_v3_recovery_failed`, `online_v3_recovery_backlog` y errores de la función programada. Los logs implementados no crean políticas de alerta por sí solos.
4. Ensayo reducido con cuentas de prueba: concurrencia/latencia/métricas, chat/lobby/reconexiones y fotos/caché. Contrastar contra contadores locales.
5. Revisar presupuesto y abrir gradualmente; impedir nuevos inicios debe conservar partidas existentes.

## Comprobaciones de este bloque

- 55 pruebas unitarias backend y comprobación de sintaxis.
- 4 pruebas del contador, incluyendo separación de contextos concurrentes e intentos abortados.
- 37 integraciones V3/recuperación/limpieza, sin fallos; 64 comprobaciones específicas de seguridad. Las reglas anteriores Firestore/RTDB también pasaron.
- 1 ensayo HTTP Auth/App Check/Functions/Tasks emulados aprobado: avance sin solicitudes del anfitrión, con autenticación y reintentos reales del emulador.

Los logs locales están en `output/server-v3-final-validation.log`, `output/server-v3-validation.log` y `output/server-v3-http.log`. El cron no se ejecutó automáticamente en el emulador: su servicio se ensayó por invocación directa y se verificó su configuración. Estas comprobaciones no certifican IAM, cron ni capacidad productivos. Los comandos reproducibles están en el informe de medición.
