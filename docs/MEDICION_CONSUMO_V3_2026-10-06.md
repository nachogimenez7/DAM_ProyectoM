# Consumo y estabilidad de V3: medición local

6 de octubre de 2026. Codex trabajó en backend, índices y medición mientras Claude implementaba el desempate de iOS. No se modificó gameplay ni se desplegó/habilitó V3 en producción.

## Partidas completas

18 recorridos con servicios reales y Firestore/RTDB emulados, más 3 recorridos con listeners SDK bajo las reglas reales. Todos terminaron con ganador, sin eliminaciones por AFK y exactamente un historial por cuenta registrada. Son recorridos concretos, no máximos por partida ni una prueba de muchas salas simultáneas.

| Jugadores | Corta Pampa: lecturas / escrituras | Larga Pampa: lecturas / escrituras | Rondas larga |
|---:|---:|---:|---:|
| 5 | 213 / 134 | 573 / 350 | 5 |
| 10 | 353 / 234 | 917 / 583 | 5 |
| 15 | 613 / 408 | 1.399 / 905 | 6 |

Las cortas terminaron en ronda 2/2/3. Las largas protegieron al Médico durante tres noches y provocaron tres dobles empates mediante votos legales. En una segunda votación con electorado impar hubo una abstención, sin alcanzar el umbral de AFK. Desde ronda 4 los bots volvieron a votar para cerrar la partida. Las salas de 10/15 abrieron la ventana del Alcalde; las de 15 alcanzaron la reconsideración obligatoria del Desertor y consumieron la elección de mantener.

| Jugadores | Larga Grecia: lecturas / escrituras | Larga Medieval: lecturas / escrituras | Cada acción corta reenviada una vez: lecturas / escrituras |
|---:|---:|---:|---:|
| 5 | 573 / 350 | 573 / 350 | 264 / 151 |
| 10 | 911 / 579 | 833 / 532 | 464 / 271 |
| 15 | 1.393 / 901 | 1.297 / 842 | 826 / 479 |

El reenvío agrega **3 lecturas y 1 escritura**, sin repetir la acción. Una acción original consume **3 lecturas y 3 escrituras antes de publicación**; cerrar la partida puede agregar una escritura de sala. Guardar N cuentas consume **3N lecturas y 3N escrituras**.

Como extrapolación aritmética, 100 partidas iguales a la larga de 15 jugadores en Pampa representarían 139.900 lecturas y 90.500 escrituras de los servicios incluidos. Eso no calcula una factura ni acredita capacidad para 100 salas simultáneas.

## Ahorro comprobado

La publicación normal sigue consumiendo 3 lecturas y 1 escritura Firestore, además de RTDB. Un evento cuyo outbox ya está entregado ahora necesita **1 lectura**, antes necesitaba 2. No accede a Tasks/RTDB. La publicación pendiente conserva la verificación de autoridad y limpieza.

En las mismas partidas cortas, las lecturas bajaron de 226/366/632 a 213/353/613. Las escrituras no cambiaron. La actualización del marcador sigue descartándose en el trigger sin leer Firestore.

Se prepararon exenciones de índices para servidor y serverOutbox: contienen estructuras privadas leídas por ruta, no búsquedas por roles/recibos/proyecciones. Se conserva el índice de grupo ascendente de serverOutbox.recoveryAtMs. Esto reduce trabajo y almacenamiento de índices; no reduce por sí mismo documentos leídos. [Prácticas de Firebase](https://firebase.google.com/docs/firestore/best-practices), [configuración de índices](https://firebase.google.com/docs/reference/firestore/indexes).

## Concurrencia: hallazgo abierto

Se enviaron acciones de todos los participantes simultáneamente por fase. Todas confirmaron correctamente; el outbox agrupó la publicación de cada ráfaga. Esto prueba contención dentro de una sala, no muchas salas ejecutándose juntas.

| Jugadores | Lecturas primera corrida / repetición | Escrituras | Intentos de transacción de acciones, primera / repetición |
|---:|---:|---:|---:|
| 5 | 215 / 217 | 122 | 47 / 48 |
| 10 | 361 / 359 | 203 | 109 / 108 |
| 15 | 709 / 659 | 346 | 252 / 227 |

Los intentos incluyen limitador y motor. El contador incluye lecturas de cada reintento y solo escrituras confirmadas.

Se ordenaron las lecturas: sala → estado → outbox cuando corresponde; el marcador de lobby respeta sala → outbox. Esto evita tomar esos documentos en órdenes distintos. Agrega secuencialidad a lecturas que antes corrían en paralelo; se debe contrastar ese intercambio en Cloud.

El p95 local de acciones simultáneas en la primera corrida fue aproximadamente 3,5 / 7,0 / 11,6 segundos. En la repetición con orden consistente fue 3,5 / 3,5 / 7,5 segundos. **Dos corridas no demuestran una mejora estable ni predicen la latencia productiva.** El documento compartido del motor sigue siendo un punto de contención por sala. Se requiere un ensayo pequeño en Cloud antes de abrir V3. Las duraciones son de limitador + servicio, sin transporte callable ni publicación.

## Entregas observadas en SDK cliente

Se conectaron 3 listeners por participante con identidad simulada de emulador y reglas de RTDB: público, privado propio y permisos propios. Los valores convergieron al servidor en cada publicación. Las publicaciones exclusivamente privadas no generaron eventos nuevos del listener público.

| Jugadores | Eventos públicos | Eventos privados propios, total | Eventos de permisos, total | JSON entregado, total aproximado |
|---:|---:|---:|---:|---:|
| 5 | 70 | 87 | 70 | 134 KiB |
| 10 | 150 | 177 | 140 | 429 KiB |
| 15 | 315 | 371 | 300 | 1.178 KiB |

Son callbacks value y JSON reconstruido por SDK web/Node. **No son bytes de red ni descargas facturadas**: protocolo, deltas, caché y conexiones tienen otro tráfico. No sustituyen la prueba Android/iOS. No convertir estos valores a dólares.

## Recuperación sin jugadores conectados

Se agregó repararPartidasV3: comprobación cada minuto en São Paulo, una instancia máxima y ninguna ociosa. El outbox indica recoveryAtMs: deadline + 30 s durante juego; momento del cambio + 30 s para resultado/revancha/salida de lobby pendientes sin deadline. Una publicación terminal entregada limpia ese indicador.

Consulta solo documentos vencidos por índice: páginas de 25, hasta 2 páginas por corrida; presupuesto de 40 s antes de iniciar otra operación. Guarda cursor si debe continuar: una sala con error no bloquea permanentemente las siguientes. Cada sala usa reloj actualizado al procesarla para conservar la duración completa de la fase siguiente.

Reutiliza motor y outbox. Una carrera con Tasks no avanza dos veces. Funciona aunque el gate de nuevos inicios esté apagado. Es un respaldo: Tasks sigue como camino normal; recuperación del cliente conserva su gracia de 5 s.

En reposo se midieron **2 lecturas de documentos y 0 escrituras por corrida**: cursor y consulta vacía. Cada minuto equivaldría a 2.880 lecturas de documentos por día, más Scheduler/Functions según tarifas/cuotas. No hay escritura por segundo. Con trabajo se puede escribir el cursor una vez y se agregan las reparaciones. Una consulta no vacía también puede generar lecturas de entradas de índices, no capturadas aquí.

Una fase aislada elegible podría recuperarse unos 30–90 s después de su deadline si Scheduler/Firebase están disponibles y no hay backlog. No es garantía ante fallos de infraestructura. Fallos y backlog producen logs para alertas.

## Reproducción y límites

Desde la raíz, Node 22 y JDK compatible:

    npm run test:authority-meter
    npm run measure:authority
    npm run measure:authority-clients
    npm run test:authority
    npm run test:authority-http
    npm --prefix functions run test:unit
    npm --prefix functions run check

Las mediciones exigen GCLOUD_PROJECT=traidores-local, Firestore 127.0.0.1:18081 y RTDB 127.0.0.1:19000. Fixtures propios limpiados al finalizar. Usar emuladores separados de Claude. No ejecutar la cola simulada junto con triggers Functions reales.

Informes regenerables: output/server-v3-consumption.json, output/server-v3-concurrent.json y output/server-v3-client-delivery.json. La opción --concurrent-only repite ráfagas sin reemplazar el informe completo.

Incluido: limitador, inicio, acciones, plazos, publicación, eventos de outbox ya publicado, encolado de limpieza por cambios de sala e historial de cuentas registradas. Preparación/inspección/borrado y sus triggers iniciales están excluidos. El modelo ordena entregas; Eventarc puede agrupar/repetir/reordenar en producción. El costo fijo de recuperación se mide aparte.

Pendiente: factura/tarifas/regiones reales, lecturas de entradas de índices, almacenamiento, CPU/memoria Functions, Scheduler/Tasks reales, IAM, múltiples salas simultáneas, chat, lobby, reconexiones y fotos/caché móviles. El emulador no acredita el despliegue ni aplica índices como producción.

Siguiente paso: cerrar clientes, desplegar índices/Functions con gate cerrado, verificar IAM/alertas y hacer un ensayo reducido Android+iOS junto con métricas Cloud. No se aplicó una tarifa de otra región ni se habilitó V3 para el público.


## Revisión de eventos y recuperación (6–7 de octubre)

Se repitieron las tres partidas cortas con SDK cliente después de la revisión N-1/N-7.
Se conserva `seq` monótono por partida, pero la proyección envía solo eventos de la
ronda actual. El anillo de 60 permanece interno. Las intenciones secretas siguen sin
producir entregas públicas ni eventos nuevos.

| Jugadores | Lecturas / escrituras | JSON SDK anterior, aprox. | JSON SDK actual | Callbacks público / privado / permisos |
|---:|---:|---:|---:|---:|
| 5 | 213 / 134 | 134 KiB | 119 KiB | 70 / 87 / 70 |
| 10 | 353 / 234 | 429 KiB | 392 KiB | 150 / 177 / 140 |
| 15 | 613 / 408 | 1.178 KiB | 1.050 KiB | 315 / 371 / 300 |

Son aproximadamente 9–11 % menos JSON reconstruido en esos recorridos, con los
mismos documentos Firestore y callbacks. No mide tráfico facturado ni garantiza
ese ahorro en otras partidas. Informe actualizado: `output/server-v3-client-delivery.json`;
ejecución aprobada en `output/v3-review-sdk-final.log`.

N-1: al romperse la paridad por abandono, la ventana vuelve al debate o a la noche
siguiente sin consumir la reconsideración. Dos consultas de recuperación posteriores
al cierre correcto no realizaron escrituras. Un estado corrupto que no puede avanzar
produce `online_v3_recovery_stuck`, sin escribir estado/outbox ni publicar/encolar;
el cursor del lote sí puede escribirse una vez. Requiere alerta y reparación: no se
presenta el log como una corrección automática de cualquier corrupción.
