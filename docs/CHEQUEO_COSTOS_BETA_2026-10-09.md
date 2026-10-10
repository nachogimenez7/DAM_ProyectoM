# Chequeo de costos y recursos en reposo — 9/10/2026

Consulta de solo lectura solicitada por el usuario en Chrome. Se usó la sesión de Bandido Games en Google Cloud Billing y se complementó con las APIs de configuración y Monitoring del proyecto `traidores`. No se cambió facturación, instancias, cron, permisos ni gates.

## Lo registrado por facturación

Período visible: **1–9 de octubre de 2026**, mes actual por fecha de uso. El desglose inicial incluía todos los proyectos (1), servicios (10) y SKU (34). Se consultó el informe de uso sin ahorros y se agrupó por SKU, mostrando sus 24 filas. Los importes y cantidades siguientes son los valores redondeados visibles de la consola, no datos exactos de un CSV; el intento de exportación no devolvió un archivo utilizable.

| Concepto | Uso registrado | Costo visible antes de ahorros |
|---|---:|---:|
| CPU Functions, São Paulo | 1.465,75 segundos | USD 0,05 |
| CPU Functions, Santiago | 413,16 segundos | USD 0,01 |
| Memoria Functions, São Paulo | 359,75 GiB-segundo | USD 0,00 |
| Memoria Functions, Santiago | 81,42 GiB-segundo | USD 0,00 |
| Invocaciones Functions generación 2 | 3.953 | USD 0,00 |
| Invocaciones generación 1 | 57 | USD 0,00 |
| Lecturas Firestore | 11.396 | USD 0,00 |
| Escrituras Firestore | 1.511 | USD 0,00 |
| Borrados Firestore | 953 | USD 0,00 |
| Transferencia saliente RTDB | 0,02 GiB | USD 0,00 |
| Operaciones Tasks | 257 | USD 0,00 |
| Cloud Build | 7,87 minutos | USD 0,00 |

El total bruto visible era USD 0,06. El desglose inicial mostraba USD −0,06 en descuentos basados en inversión y USD 0,00 en créditos promocionales, quedando total USD 0,00. La consola seguía mostrando USD 300 de crédito de prueba y 87 días restantes. No se identificó el origen exacto de ese descuento ni se atribuyó a una compra de compromisos.

Las cantidades son de todo el proyecto y del período: incluyen pruebas, preparación, limpieza, consultas y trabajo programado. **No son el consumo de una partida ni prueban que los últimos ensayos ya estén íntegramente facturados.** Google advierte que los datos pueden tardar más de 24 horas en aparecer. USD 0,00 puede representar cuota gratuita o redondeo; no equivale a recurso sin uso.

## Consumo sin partidas V3 de práctica

Configuración comprobada aproximadamente a las 04:42 de Argentina:

- `accionPartidaV3`: mínimo 1 instancia, máximo 4, CPU 1, memoria 256 MiB.
- `resolverFaseV3`: mínimo 1 instancia, máximo 2, CPU 1, memoria 256 MiB.
- `repararPartidasV3`: mínimo 0; cron habilitado cada minuto.
- Limpieza del online anterior: cron separado cada 15 minutos.
- Gate servidor y `config/onlineV3`: apagados, listas de prueba vacías.

Monitoring de 04:25–04:40 de Argentina, después de la limpieza de nuestra práctica:

- 15 solicitudes a `repararPartidasV3`; sus 15 registros indicaron `scanned:0`, `published:0`, `failed:0`.
- Una solicitud de limpieza de salas abandonadas.
- 16 operaciones de lectura Firestore y cero escrituras en los contadores consultados.
- Cero solicitudes a `accionPartidaV3` y `resolverFaseV3`.
- Aproximadamente 1.811,7 segundos de instancia facturable agregados en el proyecto. No son CPU-segundos de juego ni un precio: incluyen tiempo de permanencia de instancias, y la métrica por sí sola no permite convertirlo a factura.

La agenda de recuperación implica unas 1.440 ejecuciones por día si sigue habilitada. Es trabajo por tiempo transcurrido, no por cantidad de jugadores. Las instancias mínimas pueden generar costo en reposo aunque el gate esté cerrado.

Evidencia: `output/costos-2026-10-09/runtime-audit.json` y `output/costos-2026-10-09/reposo-proyecto.json`. Los contadores son de proyecto, con posibles solapamientos de muestras y retrasos; no afirmar que toda su lectura es atribuible al cron.

## Qué significa para la beta con anfitrión

El importe observado es bajo. No permite calcular un costo por jugador o proyectar rentabilidad. La beta usará otro camino de juego, por lo que no corresponde multiplicar los USD 0,06 por jugadores o partidas.

Prioridades concretas:

1. Al pausar Cloud V3, comprobar que no quedan partidas V3 activas y reducir a cero los mínimos de sus dos Functions. Pausar solamente el cron de recuperación V3 cuando ya no haya partidas que dependan de él. Conservar historial, limpieza y servicios del online con anfitrión. **Esto se propone; no se ejecutó en este chequeo.** También actualizar los mínimos en código para que un despliegue futuro no los restablezca accidentalmente.
2. Medir una partida del camino con anfitrión incluyendo lobby, chat, historial y reconexión. Separar el uso de QA y el consumo base. Usar jugadores por partida, duración, mensajes y reconexiones, no solo descargas/usuarios registrados, para estimar actividad.
3. Medir las fotos aparte: tamaños comprimidos, miniaturas, caché y descargas reales por sesión. Cada receptor y descarga repetida puede aumentar la transferencia.
4. Crear escenarios de 100, 1.000 y 10.000 partidas diarias solo después de contar operaciones/bytes del camino que se publicará. Aplicar cuotas diarias, almacenamiento y transferencia por servicio; no multiplicar el costo neto de una prueba que quedó compensada por descuentos.

Firestore Standard publica 50.000 lecturas, 20.000 escrituras y 20.000 borrados gratuitos por día para la base que califica. RTDB publica actualmente 360 MB/día sin costo en Blaze, aproximadamente 10 GB/mes, y USD 1/GB adicional. Las fotos usan tarifas y condiciones de Cloud Storage según bucket/región; no aplicarles las de RTDB ni asumir cuota gratuita de una región distinta.

Fuentes oficiales consultadas:

- [Informes y retraso de datos de facturación](https://docs.cloud.google.com/billing/docs/how-to/cost-table).
- [Costo de instancias mínimas](https://docs.cloud.google.com/run/docs/configuring/min-instances).
- [Cuota gratuita de Firestore](https://firebase.google.com/docs/firestore/quotas).
- [Precios de Firebase, RTDB y referencias de Storage](https://firebase.google.com/pricing).

No se certificó capacidad bajo concurrencia ni se fijó una factura mensual. No se hizo commit ni push.
