# Entrega de estabilización online — 0.1.19

Fecha local: 6 de septiembre de 2026. Esta entrega cierra la implementación y las comprobaciones automáticas de la primera etapa. La aceptación visual con cinco y doce jugadores sigue pendiente. No representa la migración completa a autoridad del servidor.

## APK

- Archivo: `output/apk/Traidores-online-0.1.19.apk`.
- Aplicación: `com.traidores.juego`; versión 0.1.19, código 20.
- Tamaño: 118.158.548 bytes.
- SHA-256: `B8F1C88503FAD7C5A14990415A639B89EACAE5BAF4448FBE5B9736BBFCD4FA65`.
- Variante debug; certificado Android Debug, firma v2 verificada.
- `USE_ONLINE_AUTHORITY_EMULATOR=false`: conecta al Firebase real y funciona con Spark.

Se conserva el APK anterior. Instalar esta versión en todos los participantes y crear una sala nueva; no mezclar versiones durante la prueba, especialmente al cambiar de anfitrión.

## Qué cambió

### Presentación y anfitrión

La expulsión online tenía un camino que iniciaba su animación directamente en el anfitrión. Ahora espera la confirmación de publicación y distingue cada presentación, incluso dentro de la misma fase. Tampoco se publica el resumen de un resultado pendiente a través del pulso del jugador.

Los invitados conservan en cola los cambios recibidos durante una presentación esencial. Las actualizaciones de una misma presentación se agrupan, mientras que las fases y expulsiones recibidas se conservan para mostrarlas en orden. Pausar y reanudar recupera el recuento o la expulsión interrumpidos. El recuento debe terminar antes de confirmar su presentación.

La barrera cuenta también a los espectadores conectados, confirma automáticamente cuando termina la presentación y mantiene tres segundos mínimos de lectura. El límite de espera por otros participantes pasa de seis a treinta segundos; ese límite no autoriza a cortar una animación esencial del coordinador. Los invitados lentos conservan las presentaciones recibidas aunque llegue un estado posterior.

Esto corrige caminos concretos de adelanto y cancelación. La confirmación de publicación sigue sin demostrar que todos los dispositivos dibujaron el resultado a la vez. No se certifica simultaneidad ni resolución del incidente observado hasta realizar las pruebas visuales.

### Orden, reconexión y búsqueda

- Cada publicación obtiene una secuencia mediante una transacción Firestore y conserva la versión de autoridad de la sala. La recepción deja de depender del reloj del nuevo anfitrión para ordenar estados del protocolo nuevo.
- La secuencia se conserva al recrear la pantalla. Un anfitrión promovido espera a presentar los estados que ya tiene pendientes antes de tomar el control.
- El buscador deja de usar la hora del teléfono como límite de la consulta. Utiliza la referencia temporal de Firebase al estar disponible.
- Amplía automáticamente la ventana de búsqueda de treinta en treinta hasta 150 documentos cuando faltan salas disponibles; permite solicitar más. Ampliar la consulta puede generar nuevas lecturas.

### Tráfico y estadísticas

- Cuando nada cambió, el pulso actualiza solamente el identificador de partida y la fecha de conexión, conservando el resto del estado.
- «COPIAR REPORTE BETA», en las opciones de la partida online, incluye bytes recibidos/enviados por la aplicación, contadores y tiempos de publicación. Los bytes incluyen todos los servicios de la app: no equivalen al consumo facturable de RTDB. La medición vive en memoria y no sobrevive al cierre del proceso.
- El perfil propio publica sus contadores mediante una actualización separada en el lobby. Un fallo de esa actualización no participa en la creación ni en la entrada a la sala.
- Estos contadores proceden del historial de ese dispositivo, incluyendo sus partidas locales y online. No son estadísticas verificadas por servidor, un historial compartido entre dispositivos ni una base fiable para rankings competitivos.

### Preparación local para Blaze

La limpieza utiliza una cola de vencimientos pendientes, un llenado inicial acotado y reanudable y reintentos para salas retenidas o errores. Los marcadores de salas ya limpiadas quedan fuera del barrido recurrente. Se añadió el disparador que agenda las salas y pruebas de idempotencia.

Las Functions siguen sin desplegarse. No se activó facturación. Las correcciones del APK usan las reglas ya desplegadas; esta entrega no necesitó una nueva publicación de reglas o índices.

## Verificación

- Kotlin: 638 pruebas, cero fallos, errores o pruebas omitidas.
- Firebase: siete suites completas aprobadas, incluyendo reglas, invitados, votos, integración backend y simulaciones de juego e inicio. Resultados: `output/online-audit/results.json`.
- Regresión adicional del pulso RTDB: confirma que conserva los campos previos y rechaza cambios de otro jugador o partida. Registro: `output/online-stabilization-heartbeat.log`.
- Backend: comprobación de sintaxis y 17 pruebas unitarias aprobadas; 15 pruebas en la suite de integración.
- Firma APK: verificada en `output/online-stabilization-apk-signature.log`.
- `assembleDebug`, `testDebugUnitTest` y `lintDebug`: ejecución final correcta. Lint: cero errores y 1244 advertencias; estas advertencias no se resolvieron como parte de esta entrega. Registro: `output/online-stabilization-delivery-android.log`.

No se ejecutaron partidas visuales en los emuladores del usuario. Las simulaciones Firebase no sustituyen esas pruebas ni miden el rendimiento gráfico Android.

## Prueba de aceptación

1. Instalar 0.1.19 en todos los emuladores y abrir una sala nueva de cinco jugadores.
2. Comparar inicio, amanecer, recuento y expulsión del anfitrión con los invitados. Confirmar que las presentaciones terminan y los textos se ven completos.
3. Pasar un invitado a segundo plano durante un anuncio y volver. Repetir con el anfitrión. Comprobar recuperación y continuidad.
4. Desconectar al anfitrión y comprobar el relevo; volver a conectar. Repetir con relojes de dispositivo desajustados.
5. Mantener el buscador abierto mientras otros entran; la sala debe seguir visible mientras conserve cupos. La prueba con más de treinta salas requiere preparar ese conjunto de datos.
6. Repetir con doce jugadores, incluyendo un dispositivo lento. Guardar el reporte beta del anfitrión y de un invitado al terminar o ante un fallo, junto con la fase y lo observado.
7. Comprobar el perfil al volver al lobby después de una partida y revisar también las presentaciones del modo VS IA.

## Etapas todavía pendientes

La autoridad de roles, votos, noche y ganador continúa en el cliente anfitrión. Migrarla requiere implementar y validar el protocolo del servidor y activar los servicios correspondientes con Blaze. Tampoco se completaron estadísticas verificadas con resultados registrados una sola vez, recuperación íntegra desde servidor ni verificación remota de enforcement de App Check.

Antes de avanzar, cerrar la prueba visual de esta versión y contrastar los reportes de tráfico con Firebase. Las estimaciones de costo anteriores siguen siendo escenarios, no una medición del consumo de este APK.
