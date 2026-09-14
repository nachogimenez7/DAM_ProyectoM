# Lectura de uso de Firebase — 13 de septiembre de 2026

Consulta a las 16:57 ART en la consola del proyecto `traidores`, plan Spark.
La serie diaria de septiembre muestra para el día 13 (datos disponibles al consultar):

| Servicio | Consumo del día | Cuota Spark | Alcance del dato |
|---|---:|---:|---|
| RTDB, descargas | 8,175 MiB | 10 GB/mes | Todo el proyecto, no sólo la última sala |
| Firestore, lecturas | 1.489 | 50.000/día | Todas las sesiones y operaciones del día |
| Firestore, escrituras | 179 | 20.000/día | Todas las sesiones y operaciones del día |

RTDB muestra 26,5 MB descargados desde el inicio del mes (0,3 % de la cuota).
El costo del proyecto figura «Sin costo» en Spark. Las series pueden actualizarse
con retraso; el día 13 todavía no había terminado. La partida de unos 13 participantes
se interrumpió y usaba APK 0.1.24. No corresponde dividir el consumo diario entre
jugadores ni atribuirlo sólo a esa partida.

Para obtener MB por participación: tomar dos lecturas RTDB con marca horaria antes
y después de una única partida aislada, esperar a que la consola refleje ambas,
anotar participantes y duración, y dividir el incremento total entre las
participaciones. Contrastar con «COPIAR REPORTE BETA» de al menos anfitrión e
invitado; esa telemetría local de red no es idéntica a los bytes facturables RTDB.

## Segunda lectura tras las pruebas de tres partidas de cinco

Consulta a las 18:18 ART del mismo día, todavía en Spark:

| Servicio | Total visible del día o mes | Variación frente a 16:57 ART |
|---|---:|---:|
| RTDB, descargas del día 13 | 9,522 MiB | +1,347 MiB |
| RTDB, descargas de septiembre | 27,8 MB | +1,3 MB aproximadamente |
| Firestore, lecturas del día 13 | 3.337 | +1.848 |
| Firestore, escrituras del día 13 | 418 | +239 |

Son variaciones de los tableros de **todo el proyecto** durante aproximadamente
81 minutos. Incluyen otras sesiones, limpieza y retrasos de consolidación; no
representan una medición aislada de esas tres partidas ni MB por participación.
El uso visible sigue por debajo de las cuotas Spark: 6,7 % de lecturas diarias,
2,1 % de escrituras diarias y 0,3 % de descargas RTDB mensuales.

## Lectura tras la prueba de 15 jugadores

Consulta posterior a dos partidas de 15 participantes, aproximadamente a las
18:53 ART, todavía en Spark:

| Servicio | Total visible | Variación frente a 18:18 ART |
|---|---:|---:|
| RTDB, descargas del día 13 | 21,121 MiB | +11,599 MiB |
| RTDB, descargas de septiembre | 39,4 MB | +11,6 MB aproximadamente |
| Firestore, lecturas del día 13 | 7.103 | +3.766 |
| Firestore, escrituras del día 13 | 708 | +290 |

El usuario reportó dos partidas terminadas (victoria del Bufón y luego del Pueblo),
emotes y vuelta al lobby. El intervalo entre consultas también puede incluir otras
operaciones y datos demorados del panel. Por eso estas diferencias no deben
atribuirse exactamente a las 30 participaciones ni usarse como costo por persona.
Lecturas: 14,2 % de la cuota diaria de 50.000; escrituras: 3,5 % de 20.000;
RTDB mensual: 0,4 % de 10 GB. Para proyectar una beta abierta aún falta una
partida aislada con lecturas antes y después y sin otras sesiones activas.
