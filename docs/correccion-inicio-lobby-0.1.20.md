# Corrección del inicio del lobby — 0.1.20

Fecha: 7 de septiembre de 2026.

## Incidente observado

En la sala `uQiHiFz0cOrN70thuxty`, los cinco jugadores llegaban a estar conectados y listos. Al pulsar «INICIAR PARTIDA», un invitado se desmarcaba y el inicio fallaba. Cada nuevo intento repetía el ciclo.

Los registros de los cinco emuladores muestran la secuencia exacta:

- 01:27:02.392: el anfitrión recibe `ready=5`.
- 01:27:03.002: el invitado `LtT86...` solicita explícitamente `ready=false`.
- 01:27:03.499: el preflight del anfitrión todavía había capturado `ready=5`.
- 01:27:03.563: la transacción vuelve a leer el servidor y encuentra un jugador sin confirmar.
- 01:27:05.209: el inicio termina con `Todavia faltan jugadores listos.`

La misma secuencia se repite en los cuatro intentos. Firestore no cambió el jugador equivocado y la transacción no aplicó una escritura parcial. El emulador del invitado ejecutó el botón «NO LISTO» al mismo tiempo que el anfitrión pulsaba el botón situado en la misma zona. Esto es compatible con una pulsación replicada por la sincronización de instancias de BlueStacks.

## Corrección

- Cuando la sala completa alcanza todos los «listos», el botón de los invitados pasa a «ESPERANDO AL ANFITRIÓN» y queda desactivado.
- La comprobación también se repite dentro del manejador del toque para cubrir una vista que todavía no haya terminado de redibujarse.
- Mientras todavía falta un jugador, cada invitado conserva la posibilidad de marcarse o desmarcarse.
- Si el estado cambia durante el breve intervalo entre el preflight y la transacción, el anfitrión recibe el motivo concreto del rechazo. Ya no aparece «El servidor devolvió un error inesperado» para ese caso válido.

No cambiaron las reglas de Firebase ni las Functions. La corrección funciona en Spark.

## APK y verificación

- APK: `output/apk/Traidores-online-0.1.20.apk`.
- Versión: 0.1.20, código 21.
- Tamaño: 116.632.861 bytes.
- SHA-256: `B6B85484131BBC454A1CC106E063228841FA8BBB4313D9F9218B44493C159015`.
- Firma APK v2 verificada con certificado Android Debug.
- 640 pruebas Kotlin aprobadas, sin fallos, errores ni omisiones.
- `assembleDebug` y `lintDebug` aprobados; lint tiene cero errores y 1244 advertencias.
- Registro de compilación: `output/online-start-ready-race-android.log`.
- Registro de firma: `output/online-start-ready-race-signature.log`.

## Repetición de la prueba

Instalar el mismo APK 0.1.20 en las cinco instancias y crear una sala nueva. Se puede dejar activa la sincronización de BlueStacks para comprobar la protección: al llegar a 5/5 listos, los invitados deben mostrar «ESPERANDO AL ANFITRIÓN», y el toque del anfitrión no debe desmarcar a ninguno.

Después de confirmar el inicio con cinco jugadores, continuar con las comprobaciones de cinematografía y repetir con doce jugadores.
