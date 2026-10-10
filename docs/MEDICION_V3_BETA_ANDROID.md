# V3: funcionamiento, consumo y próximos ajustes

Medición para decidir la apertura de la beta Android. La presentación de la mesa
queda pausada por pedido del usuario mientras se comprueba el servidor. Esta
entrega cambia herramientas de QA y documentación; no cambia la interfaz ni
despliega Functions/reglas nuevas.

## Qué se midió

- 18 recorridos locales completos del servicio y 3 con listeners SDK: partidas
  cortas, largas, reenvíos y acciones simultáneas de 5, 10 y 15 jugadores.
- Tres partidas completas contra Firebase real con cuentas temporales autenticadas,
  App Check, tres listeners por participante, acciones simultáneas por fase,
  chat e historial por cuenta. Resultados Cloud, abajo.
- Las fases Cloud vencen con Cloud Tasks y el reloj real. Los clientes no llaman
  a recuperarFaseV3 ni arbitran. No se aceleran los vencimientos desde Admin.
- Lecturas/escrituras instrumentadas locales, callbacks JSON del SDK, métricas
  de tráfico/ejecución/carga Cloud y logs de operaciones, diferenciados.

QA prepara participantes/salas y limpia sus datos. El juego utiliza exclusivamente
las proyecciones autorizadas de cada cuenta, sin consultar roles ajenos ni el
documento privado del motor. El gate se habilita solo para la sala y su creador
temporales y se restaura al terminar. No se desactiva App Check.

Los tiempos de QA son breves: noche 20 s, debate 30 s, voto 25 s y transición 1 s.
Son útiles para medir acciones/plazos, pero no sirven para aprobar animaciones
o duración de anuncios. Se usa Pampa y participantes automáticos, no personas
jugando en Android. No hay fotos en este ensayo ni prueba de red móvil.

## Operaciones locales del motor e historial

Los 21 recorridos terminaron con ganador, sin AFK y con un historial por cuenta.
Las cifras coinciden con la medición anterior para los recorridos secuenciales.

| Jugadores | Corta: lecturas / escrituras | Larga: lecturas / escrituras | Rondas larga | Cada acción corta reenviada una vez |
|---:|---:|---:|---:|---:|
| 5 | 213 / 134 | 573 / 350 | 5 | 264 / 151 |
| 10 | 353 / 234 | 917 / 583 | 5 | 464 / 271 |
| 15 | 613 / 408 | 1.399 / 905 | 6 | 826 / 479 |

Son recorridos concretos, no un máximo por partida. Incluyen limitador por UID,
motor, inicio, plazos, publicación, eventos duplicados de outbox, encolado de
limpieza e historial. Excluyen preparación/inspección/borrado de fixtures, entradas
de índices y tráfico de lobby, fotos y reconexiones. Eventarc real puede agrupar
o repetir entregas de manera diferente.

Con ráfagas simultáneas se registraron 225/122, 361/203 y 639/346 lecturas/escrituras
para 5/10/15. Las transacciones del motor compiten por el estado de su sala;
la agrupación del outbox reduce publicaciones, pero los reintentos agregan lecturas.
La latencia local de esas ráfagas no predice la productiva.

El SDK local reconstruyó aproximadamente 119, 392 y 1.050 KiB de JSON para las
partidas cortas de 5/10/15. Esto **no es tráfico facturado**: la red usa deltas,
protocolo, conexiones y cifrado. No se convierte ese JSON a dólares.

## Partidas Cloud

Las tres partidas terminaron con ganador. Se aceptaron 168/168 acciones, se
enviaron 80 mensajes y se verificaron historiales legibles por las treinta cuentas.
Ninguna eliminación por AFK, ningún rechazo ni error de chat. El recorrido de 10
incluyó doble empate + Alcalde; el de 15, desempates y cinco rondas. La ventana final
del Desertor sigue cubierta localmente; estos recorridos Cloud no la ejercitaron.

| Jugadores | Rondas | Acciones aceptadas | Confirmación HTTP p50 / p95 | Proyección confirmada p50 / p95 |
|---:|---:|---:|---:|---:|
| 5 | 2 | 14 / 14 | 0,937 / 2,887 s | 1,471 / 4,268 s |
| 10 | 3 | 44 / 44 | 0,910 / 1,788 s | 1,480 / 2,274 s |
| 15 | 5 | 110 / 110 | 1,501 / 3,464 s | 2,019 / 4,036 s |

La confirmación HTTP mide desde enviar la intención hasta el recibo de la callable,
incluyendo transporte/limitador. La segunda columna de tiempos mide hasta observar
esa acción en la proyección privada coherente de su fase. No son el mismo momento.
Los percentiles se calculan sobre acciones aceptadas de una sola partida por tamaño;
no representan una garantía ni una comparación controlada entre tamaños.

En los logs, las partidas tuvieron 21, 68 y 206 intentos de la transacción del
motor para 14, 44 y 110 acciones. El log de operación no incluye la transacción del
limitador; el medidor local sí la cuenta. Las fases normalmente llegaron al cliente
unos 2,5–3,3 s después del deadline anterior; el primer arranque de Tasks llegó a
6,6 s. Hay demoras que mejorar aunque la base de datos tenga poca carga.

En la limpieza del ensayo de 10, la eliminación cliente de las cuentas falló por
requires-recent-login después de una partida larga. El juego/historial sí terminaron.
Se borraron después las diez cuentas propias con la API administrativa, se confirmó
el gate cerrado y se corrigió la herramienta para usar esa limpieza. Se conserva
la evidencia original junto a cleanup-10-recovered.json; no se oculta el fallo de QA.

## Métricas Cloud y reposo

Consultar los JSON en output/v3-cloud. Se recogerán ventanas completas de un minuto,
incluyendo setup, juego, verificación y limpieza; no son contadores facturados por
sala. Las métricas pueden demorar cuatro minutos o más en llegar. No sumar gauges
como conexiones/carga: se informa su pico por instante, sumando sus componentes.

La ventana de reposo 02:37–03:07 UTC del 8/10 mostró 30 corridas del respaldo de
recuperación, todas sin salas vencidas ni fallos. No hubo un bucle de reparación.
Las métricas de documentos del proyecto incluyen además inspecciones y otras tareas;
una consulta vacía tiene un mínimo facturado que no aparece como documento devuelto.

El modelo local del respaldo sigue siendo 2 lecturas y 0 escrituras por corrida
vacía: 2.880 lecturas/día si corre cada minuto, más Scheduler/compute. Es costo fijo
del respaldo aunque no juegue nadie. Tasks es la vía normal y el cron actúa de red
de recuperación. No se cambió su frecuencia en esta entrega.

Ubicaciones verificadas: Firestore y callables en Santiago; worker Tasks en São
Paulo; RTDB existente en us-central1. Esa separación puede influir en publicación
y transferencia; es una hipótesis a contrastar, no una causa demostrada de toda
la latencia. No se migra la base existente durante este ensayo.

Lectura final de Monitoring, luego del retraso de ingestión:

| Clientes de QA | Ventana UTC 8/10 | Documentos leídos / escritos | RTDB salida (incluye overhead) | Pico carga RTDB | Requests Cloud Run | Segundos de instancia facturables Run |
|---:|---|---:|---:|---:|---:|---:|
| 5 | 03:08–03:12 | 238 / 122 | 375.875 bytes | 0,075 % | 81 | 49,32 |
| 10 | 03:14–03:20 | 537 / 272 | 2.445.793 bytes | 0,100 % | 185 | 77,62 |
| 15 | 03:20–03:30 | 1.254 / 577 | 10.790.014 bytes | 0,293 % | 379 | 153,90 |

Los tres juegos duraron unos 2:18, 4:41 y 7:39 desde conectar los listeners; los
tamaños recorrieron fases diferentes. Estos totales son de **todo el proyecto
durante sus ventanas**, no lecturas facturadas del motor ni medición aislada por
cuenta. Incluyen fixtures, verificaciones, limpieza principal, invocaciones de
triggers sin trabajo, tráfico administrativo y cron. Parte de la limpieza adicional
de Auth/los marcadores de mantenimiento ocurrió fuera de esas ventanas.
La métrica de almacenamiento no devolvió muestras: no se interpreta como cero.
Run tampoco captura la función Auth de primera generación ejecutada por la
limpieza de QA. No convertir estos segundos agregados a dinero asumiendo que
todos los servicios tienen el mismo CPU/memoria.

La baja carga de RTDB **no acredita capacidad para muchas salas simultáneas** ni
excluye contención Firestore. La confirmación p95 de 15 fue 3,46 s, y la proyección
4,04 s; falta mejorar esa respuesta. La salida de la ventana de 15 fue unos 10,29
MiB, frente a 3,80 MiB de JSON reconstruido por los clientes con chat. No usar
callbacks para predecir la factura. Hace falta perfilar qué parte corresponde a
clientes, transacciones del publicador, reintentos, conexiones y observación
administrativa. No se atribuye todo el tráfico a un jugador ni a una causa única.

Como sensibilidad, copiar exactamente 100 ventanas QA de 15 por día durante 30
días daría unos USD 19,60 de **solo salida RTDB**, usando la franquicia diaria
disponible y la tarifa del catálogo. No es una previsión de la beta: repite también
tráfico de pruebas y mezcla cargas que deben separarse. Sí muestra por qué no
basta el cálculo de lecturas Firestore para estimar el gasto total.

Se ejecutó además el smoke Cloud después de corregir limpieza: privacidad de
roles/historial, reenvío idempotente, Tasks con host desconectado, abandono como
derrota, revancha con matchId nuevo y listo de lobby. Pasó y salió con código 0.
La herramienta elimina ahora también sus marcadores externos de mantenimiento,
cierra sesión SDK y termina después de persistir la evidencia/limpiar recursos.
No se cambia el borrado de cuentas de los jugadores del producto.

## Tarifas verificadas y estimación parcial

Catálogo oficial Cloud Billing consultado en USD, con vigencia 7/10/2026. Para
Firestore Standard en **Santiago**, después de las cuotas gratuitas:

| Operación | USD por 100.000 | SKU |
|---|---:|---|
| Lecturas | 0,043 | 302D-5C80-B088 |
| Escrituras | 0,129 | 08D7-9F49-231D |
| Borrados | 0,014 | 0989-F449-2673 |

La cuota gratuita es 50.000 lecturas y 20.000 escrituras diarias para la base elegible,
compartida con el resto del proyecto. Se usa la tarifa regional del catálogo, no la
tabla inicial de Iowa de la página. Fuentes: [Firestore](https://cloud.google.com/firestore/pricing)
y [Catálogo de precios](https://cloud.google.com/billing/docs/how-to/catalog-api).
Evidencia exacta en output/v3-cloud/pricing-selected.json.

Extrapolación de los recorridos locales de 15, repitiendo la misma cantidad cada
día durante 30 días, añadiendo 2.880 lecturas/día del respaldo y suponiendo que la
cuota gratuita no la usa otro consumo:

| Partidas por día | Solo lecturas/escrituras modeladas: corta | Solo lecturas/escrituras modeladas: larga |
|---:|---:|---:|
| 10 | USD 0 | USD 0 |
| 100 | USD 0,99 / 30 días | USD 3,93 / 30 días |
| 1.000 | USD 22,32 / 30 días | USD 51,69 / 30 días |

Fórmula diaria: max(0, partidas × lecturas + 2.880 − 50.000) × 0,043 / 100.000
+ max(0, partidas × escrituras − 20.000) × 0,129 / 100.000.

**Estos importes no son la factura del servidor.** Faltan Functions/Cloud Run,
RTDB, transferencia entre regiones, índices, almacenamiento, borrados/retención,
Eventarc, Scheduler, Tasks, lobby, fotos, reconexiones y otros servicios. Tampoco
prueban que mil salas simultáneas funcionen. El crédito de prueba cubre consumos
elegibles; no modifica las tarifas para cuando venza.

RTDB se cobra por almacenamiento y descarga, incluidos protocolo/cifrado; no por
cada callback. El catálogo muestra USD 1/GiB de salida luego de 0,3515625 GiB/día
gratuitos (la página lo expresa como 360 MB/día), y USD 5/GiB-mes de almacenamiento
por encima de su cuota. [Tarifas Firebase](https://firebase.google.com/pricing),
[cómo se factura RTDB](https://firebase.google.com/docs/database/usage/billing).

Cloud Run request-based Tier 2: USD 0,0000336 por vCPU-s y USD 0,0000035 por
GiB-s, antes de descuentos/cuotas. No sumar duración de requests concurrentes
como si cada uno tuviera una instancia dedicada. Cuotas compartidas por cuenta,
con descuento calculado a tarifa Tier 1. [Cloud Run](https://cloud.google.com/run/pricing).
Tasks: millón de operaciones mensuales gratuito por cuenta, luego USD 0,40 por
millón; crear y entregar/reintentar son operaciones distintas. [Tasks](https://cloud.google.com/tasks/pricing).

## Qué falta antes de abrir la beta

1. Perfilar primero publicarPartidaV3/worker y su tráfico RTDB; separar descargas
   de clientes y trabajo interno, revisar repetición/agrupación de publicaciones.
   Revisar la latencia de confirmación/publicación y los 2–3 s de resolución al
   vencer el reloj. Medir cualquier ajuste con el mismo ensayo y conservar
   autoridad/privacidad/idempotencia. No activar instancias ociosas sin medir costo.
2. Probar pocas salas simultáneas en escalones acotados. Esta entrega solo prueba
   contención entre jugadores de una misma sala, no capacidad global.
3. Medir Android físico con chat/fotos/lobby/reconexión y consumo fuera del gameplay.
4. Después cerrar la presentación habitual de la mesa sobre las proyecciones V3,
   siguiendo el diseño existente, y aprobarla con el usuario en el A56.
5. AAB/Release, App Check de Play y apertura gradual con alertas. Release mantiene
   SERVER_ONLINE_V3=false hasta terminar esa validación.

## Reproducción

Local, emuladores aislados de Claude: test:authority-meter, measure:authority y
measure:authority-clients del package.json. Node/JDK como indica el informe local
docs/MEDICION_CONSUMO_V3_2026-10-06.md.

Cloud es una prueba explícita sobre el proyecto real, cerrada y limitada a fixtures:

    TRAIDORES_REAL_FIREBASE_CONFIRM=traidores node scripts/test-server-v3-cloud.cjs --players 5 --full-match

Solo continuar con 10 y 15 después de limpieza/gate cerrado del ensayo anterior.
Límite de 12 minutos de gameplay por ejecución; SIGINT/SIGTERM provoca limpieza.
No ejecutar otras pruebas de gate a la vez ni reutilizar cuentas reales.

Lectura de métricas sin cambiar configuración, después del retraso de ingestión:

    node scripts/read-server-v3-cloud-metrics.cjs --from 2026-10-08T03:08:00Z --to 2026-10-08T03:12:00Z --room qa-v3-cloud-1791428899963 --out output/v3-cloud/monitoring-5-full.json

La herramienta pagina Monitoring/Logging, conserva métricas sin procesar y guarda
logs resumidos sin intenciones, roles, nombres ni tokens. Las evidencias están en
output/ ignorado por git; el documento contiene los resultados revisables.

Verificación: medidor 4/4; 21 recorridos locales; tres partidas completas Cloud;
smoke Cloud; sintaxis Node de las herramientas y git diff --check. Las cuentas y
salas son desechables. V3 sigue cerrado al público después de las pruebas.
