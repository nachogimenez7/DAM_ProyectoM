# Correcciones tras las pruebas de 5 y 12 emuladores

## Corrección posterior: rechazo al crear sala

El resumen `estadisticasPerfil` se había agregado al payload común usado para crear salas,
unirse y publicar presencia. Con las reglas anteriores, que admiten una lista cerrada de
campos, ese dato adicional rechaza la operación completa. Se retiró su envío del payload
común, conservando el historial y las estadísticas propias locales, también en el lobby.
La lectura de resúmenes remotos existentes se conserva, pero no se publican resúmenes nuevos.
La publicación automática de estadísticas descrita más abajo queda aplazada hasta una
migración compatible. Esta corrección no requiere desplegar reglas nuevas para ese campo.
No resuelve ni modifica los requisitos separados del protocolo de voto V2.

Se agregó una regresión para verificar que invitados y cuentas envían exactamente el mismo
payload de identidad tengan o no partidas registradas. Por instrucción del usuario, no se
compiló ni ejecutó esta nueva prueba; tampoco se desplegaron reglas. La causa remota se infiere
del cambio de esquema y el mensaje mostrado; no se inspeccionaron las reglas de producción.

## Entrega local

Se conservaron los cambios anteriores y los cambios de Gradle presentes al comenzar. No hubo deploy, instalación de APK, push ni commit. El usuario compilará y ejecutará la revisión final.

## Causas encontradas y cambios

- **Presentación adelantada del anfitrión.** `renderGame` presentaba el estado resuelto antes de completar su publicación. Ahora una barrera por match, fase, índice, ronda y ganador mantiene una pantalla de sincronización y detiene los avances mientras espera confirmación de checkpoint y publicación RTDB, o su alternativa durable. Un error conserva la espera y el reintento. Confirmaciones de otra fase no liberan la actual; una confirmación recibida con la pantalla detenida no impide reintentar al volver. Esto elimina ese adelanto local deliberado; no garantiza que todos los teléfonos dibujen en el mismo milisegundo ni elimina la autoridad del anfitrión sobre el motor.
- **Noche cerrada con presencia incompleta.** Los actores requeridos se calculaban desde la presencia, por lo que un dispositivo aún no reflejado podía quedar fuera. Ahora se calculan desde el roster de la partida y sus roles. Un médico lento sigue siendo requerido para el cierre anticipado; una identidad incompleta o duplicada lo bloquea. El timeout normal sigue permitiendo resolver una noche cuando alguien realmente no responde.
- **Dispositivos dejados atrás al comenzar.** El watchdog podía iniciar con aproximadamente tres cuartos de confirmaciones. Ahora necesita que todos hayan reportado una mesa completa y estén conectados. Se mantiene tolerancia a una confirmación de lectura de rol tardía después de cargar la mesa. Si un dispositivo nunca carga, se espera: el anfitrión debe recuperar la conexión o rehacer la sala sin ese dispositivo, no comenzar silenciosamente con una mesa incompleta.
- **Lobby.** Número y palabra «JUGADORES» en dos líneas con altura adaptable; separación antes del nombre de sala; texto introductorio corregido y pista de nombre opcional.
- **Victoria.** Título adaptable hasta dos líneas y tamaño mínimo menor; encabezado «EQUIPO GANADOR».
- **Estadísticas.** El perfil propio y el del lobby usaban valores vacíos, y el historial excluía partidas online. Ahora el historial registra finales online y deduplica por matchId, incluso si cambia el timestamp de inicio tras reconectar. El perfil propio lee los contadores guardados y el perfil compartido incluye un resumen opcional de partidas y victorias. Al volver al lobby se publica el perfil actualizado mediante el flujo de presencia existente. Perfiles antiguos sin resumen siguen indicando datos no disponibles. No se reconstruyen partidas antiguas que nunca se guardaron.

Las estadísticas son historial informativo del dispositivo, compartido por su dueño; mezclan las partidas locales y online registradas allí. No constituyen ranking certificado ni sincronización de historial entre dispositivos. Las reglas validan estructura, enteros, límites y que las victorias no excedan las partidas; no certifican la veracidad de un cliente modificado.

## Verificación y límites

Antes de la indicación de dejar la compilación al usuario, Android compiló y pasaron **628 pruebas**, incluidas seis nuevas regresiones de roster, carga de 12 jugadores, publicación, reconexión y estadísticas. Pasó la suite principal de reglas Firestore, ampliada con aceptación del resumen y rechazos por valores inválidos o escritura ajena.

El lint de una revisión intermedia terminó con 13 avisos de severidad error `SuspiciousIndentation` y 1247 warnings. Sus ubicaciones quedaron desfasadas durante los últimos ajustes y el archivo mezclaba finales de línea. Se normalizaron sus finales de línea y se revisaron los fragmentos señalados; **no se declara lint aprobado**. Los últimos guards de callbacks y esa normalización quedan pendientes de compilación, pruebas y lint sobre el código final, como pidió el usuario.

Logs: `output/audit-online-device-fixes.log` y `output/audit-online-device-rules.log`. El primer log incluye el fallo de lint; las pruebas unitarias y el ensamblado anteriores dentro de esa ejecución sí terminaron. No se reprodujo la UI en 12 emuladores: ADB solo detectó un emulador offline. No se determinó cuánto contribuyeron CPU, memoria o BlueStacks al incidente original.

## Próxima prueba

Usar la misma versión en todos los dispositivos y un entorno con las reglas correspondientes. El nuevo campo de estadísticas requiere estas reglas, además de los requisitos de voto V2 documentados en la auditoría anterior; no instalar contra reglas antiguas sin coordinar la actualización. Las reglas permanecen locales.

1. **Cinco jugadores:** completar noche, votación y final. Verificar que el anfitrión no muestra resultados mientras los publica, que se lee todo el título y que dice «EQUIPO GANADOR».
2. **Jugador lento:** demorar un dispositivo al cargar la mesa. La primera noche debe esperar. Durante la noche, demorar la acción del médico: el cierre anticipado debe esperarlo hasta el timeout normal.
3. **Reconexión del anfitrión:** cortar red al resolver y volver. Debe conservar la espera, publicar o recuperar autoridad, y presentar una sola resolución. Repetir pasando la app a segundo plano.
4. **Estadísticas:** completar una partida y volver al lobby. Debe sumar una partida y la victoria cuando corresponda. Reabrir ese mismo final no debe sumarlo dos veces. Comprobar que otros dispositivos reciben el perfil actualizado.
5. **Doce jugadores:** repetir con todos cargados y luego con uno demorado. Registrar qué dispositivo era anfitrión, fase/ronda y momento de la diferencia. Si reaparece, guardar el reporte de estabilidad de anfitrión y afectado de esa misma partida para correlacionar recepción, publicación y presentación.

Este cierre entrega correcciones a causas concretas. La aceptación final requiere esas pruebas; no se afirma que todas las trabas observadas en la sesión original hayan quedado reproducidas o eliminadas.
