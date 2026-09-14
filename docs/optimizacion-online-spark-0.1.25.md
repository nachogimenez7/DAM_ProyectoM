# Online 0.1.25: inicio, presentación y consumo

## Cambios de esta versión

- La lectura inicial del rol se confirma automáticamente 20 segundos después de que el contenido completo de la carta queda visible en un cliente activo. Pulsar «EMPEZAR» antes sigue funcionando. El contador se conserva al pausar o recrear la actividad. Un cliente que no cargó la carta no se confirma por tiempo.
- El chat se actualiza con la fase nueva después de terminar la transición de día/noche. Evita que el panel renderice mensajes o canal de la fase siguiente bajo la animación.
- El mapa elegido al crear la sala se muestra como mapa fijo del anfitrión. La app nueva no escribe votos de mapa y el inicio local y el backend ignoran votos de clientes anteriores. No hay desempate.
- El chat del lobby deja de duplicar avisos locales de «está listo». Los indicadores de listo de los jugadores y el botón de inicio permanecen.
- La suscripción RTDB de estados de clientes usa eventos por hijo y mantiene una copia local, en vez de reconstruir toda la lista desde un `ValueEventListener` en cada callback. La reducción real de bytes facturados debe medirse; no se infiere de la API de callbacks.

## Paneo de consumo

| Ruta | Coste probable | Decisión |
|---|---|---|
| Voto de mapa | Una escritura de jugador por voto, difundida como cambio a los listeners de la sala | Eliminado en el cliente nuevo; el backend ignora votos viejos |
| Aviso «está listo» | Sólo memoria local | Eliminado para limpiar el chat; ahorro Firebase: cero |
| Cambios de LISTO | Una escritura de documento por cambio; la escuchan los clientes del lobby | Mantener: protege el inicio consistente |
| Lista de jugadores en el lobby | Una lectura inicial por documento y lecturas por documentos modificados para cada cliente conectado | Mantener por ahora; una migración a RTDB exige rediseñar membresía, reglas y expulsión |
| Documento propio de membresía | Listener adicional al documento del mismo jugador | Mantener: permite detectar expulsión si el listener de la lista pierde acceso |
| Presencia y sincronización durante partida | RTDB, con pulsos parciales; host conserva espejo de presencia en Firestore para cambio de autoridad | Medir bytes y frecuencia antes de reducir más |
| Inicio y reparto | Transacción Firestore y documentos privados por jugador | Mantener: consistencia y privacidad |

Firestore cobra lecturas de documentos, no sólo llamadas de código. En un lobby de 15, cada cambio de un documento de jugador puede llegar a los demás clientes y aumentar el total de lecturas. El contador interno y el panel de Firebase deben comprobar cuánto ocurre de verdad; el valor mensual agregado no permite atribuir tráfico a una partida.

## Medición siguiente

Usar una sesión controlada de 5 y otra de 15, sin otras salas simultáneas. Guardar el contador de Firestore y las descargas RTDB antes y después, esperar a que el panel procese el día y comparar con «COPIAR REPORTE BETA» de anfitrión e invitado. El reporte de Android incluye todo el tráfico de la app; las descargas facturables de RTDB incluyen también sobrecarga de protocolo. No igualar ambas cifras.

## Pendiente funcional

Si un cliente no llega a cargar la carta, la barrera de inicio continúa esperando. Diseñar un cierre sincronizado por tiempo para volver a todos al lobby, en lugar de iniciar con un participante atrasado. Probar la transición y el auto inicio en celular físico antes de distribuir la beta abierta.
