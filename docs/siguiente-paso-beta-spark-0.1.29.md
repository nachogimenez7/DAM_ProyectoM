# Siguiente paso para la beta online en Spark (0.1.29)

Fecha: 13 de septiembre de 2026. La APK local 0.1.29 es de depuración; la versión
publicada en la prueba cerrada de Google Play sigue siendo 0.1.18.

## 1. Prueba funcional breve: cinco jugadores

Instalar 0.1.29 en los cinco dispositivos. Incluir un teléfono real, una cuenta con
el estilo espacial y otra con un estilo diferente. Jugar una partida completa y una
revancha. Comprobar el avatar y el marco en el lobby, durante la partida, en el
resultado y al volver al lobby. No debe aparecer un avatar azul transitorio ni
perderse el estilo. Comprobar además reparto, noche, amanecer, votación, expulsión,
victoria y regreso conjunto. El anfitrión no debe ver un anuncio ni poder actuar
antes que los invitados.

Probar una votación después de que termine su tiempo: debe mostrar un mensaje claro
sin cambiar el voto. Con el Asesino, seleccionar una víctima y confirmar con MATAR;
la selección sola todavía no ejecuta la acción. Copiar el REPORTE BETA del anfitrión
y de dos invitados antes de iniciar la revancha, porque la medición local se reinicia
por partida y se pierde si se cierra el proceso.

## 2. Medida de consumo por participación

La primera partida sirve para probar el aspecto; no usarla como cifra de consumo.
Para medir, reservar una franja sin otras salas ni pruebas y usar la misma versión en
todos los dispositivos:

1. Anotar hora, RTDB Downloads o el contador de Cloud Monitoring
   `firebasedatabase.googleapis.com/network/sent_bytes_count`, y lecturas/escrituras
   de Firestore. Anotar también si los paneles ya actualizaron sus datos.
2. Mantener los mismos dispositivos en el lobby diez minutos sin jugar. Registrar
   el aumento como línea base de tráfico de fondo.
3. Jugar tres partidas completas de cinco con duración y número de rondas anotados.
   Registrar emotes, chat y reconexiones; copiar el REPORTE BETA de anfitrión y dos
   invitados en cada una.
4. Releer los contadores cuando la consola haya incorporado toda la ventana.
   Restar la línea base proporcional al tiempo. Una partida de cinco terminada
   aporta cinco participaciones; si alguien abandona, guardar además sus minutos
   conectados y no tratarla como una partida comparable.
5. Repetir el lote con 10 y 15 jugadores. Usar la plantilla
   `docs/plantilla-medicion-online.csv`. La primera tanda da una estimación; para
   presupuestar una beta más amplia usar varias partidas y reportar mediana y peor
   caso, no sólo la media.

```text
MB RTDB por participación =
  (bytes RTDB del lote - bytes de base ajustados) / 1.048.576 / participaciones

MB RTDB por jugador-hora =
  MB RTDB netos / (suma de minutos conectados / 60)

Lecturas Firestore por participación = lecturas netas del lote / participaciones
Escrituras Firestore por participación = escrituras netas del lote / participaciones
```

El REPORTE BETA usa bytes de red del proceso Android: incluye RTDB, Firestore,
autenticación y otras descargas. Sirve para comparar anfitrión e invitados, no para
calcular directamente los MB facturables de RTDB. Los paneles Firebase agregan el
proyecto completo y pueden tener retraso. Las diferencias observadas hoy
(21,121 MiB RTDB, 7.103 lecturas y 708 escrituras para todo el día al último corte)
no son una medida por participación.

## 3. Puerta para una beta abierta limitada

- Completar sin errores graves al menos tres partidas consecutivas con 5, 10 y 15
  jugadores en la versión candidata. Incluir un teléfono real, un invitado lento,
  una reconexión y una revancha con cambio de mapa.
- Confirmar que nadie pierde una cinemática esencial, queda sin actuar o ve un
  resultado antes que el resto; que todos vuelven al lobby; y que una sala abandonada
  desaparece de la búsqueda.
- Medir MB RTDB y operaciones Firestore por participación. Mantener margen respecto
  de Spark: 10 GB/mes de descargas RTDB, 100 conexiones RTDB simultáneas, 50.000
  lecturas y 20.000 escrituras Firestore por día.
- Actualizar la prueba cerrada de Play con un AAB release firmado y repetir una ronda
  en teléfonos reales. La APK local 0.1.29 no equivale a ese paquete publicable.
- Resolver el acceso a producción de Play: la página de prueba abierta indica que
  hace falta ese acceso. El panel muestra cumplidos los hitos de probadores de la
  prueba cerrada, pero el botón de solicitud aparece deshabilitado; aún no está
  identificada la causa.

Spark permite una beta funcional limitada, pero el anfitrión todavía puede conocer
o fabricar parte del estado desde un cliente modificado. No ofrecer rankings o
recompensas competitivas como resultados verificados hasta mover esas decisiones
a un backend de confianza. Para una beta pública más amplia, completar una tanda
de al menos 30 partidas en la misma versión sin defectos graves sin resolver.

Referencias: [uso de RTDB](https://firebase.google.com/docs/database/usage/monitor-usage),
[cuotas Firebase](https://firebase.google.com/pricing),
[cuotas Firestore](https://firebase.google.com/docs/firestore/quotas),
[requisitos de prueba de Google Play](https://support.google.com/googleplay/android-developer/answer/14151465).
