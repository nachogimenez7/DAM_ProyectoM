# Entrega de APK y reglas — 6 de septiembre de 2026

## Entregado

- APK: `output/apk/Traidores-online-2026-09-06.apk`.
- Aplicación: `com.traidores.juego`, versión 0.1.18, código 19.
- Variante debug, firmada con el certificado Android Debug; firma APK v2 verificada.
- Tamaño: 116.616.477 bytes, aproximadamente 111,2 MiB.
- SHA-256: `1904F28E154F6CE2AE14B54CBB23088AA55E074120680AD311E6F8454372E6C0`.
- Firebase real: proyecto `traidores`; `USE_ONLINE_AUTHORITY_EMULATOR=false`. No requiere emuladores locales de Firebase.

Con la autorización del usuario se desplegaron las reglas Firestore y Realtime Database. El CLI confirmó ambas publicaciones. Se descargaron las reglas resultantes y se compararon con los archivos locales. No se desplegaron Functions, índices, hosting ni una publicación en Play Store. Las Functions de limpieza nuevas siguen fuera de producción.

Respaldos previos, copias posteriores y registro de despliegue: `output/deployment-2026-09-06/`.

## Correcciones de esta revisión

1. El título de expulsión admitía una línea aunque contenía un nombre y «FUE EXPULSADO» separados por salto de línea. Ahora admite dos, con espacio de fuente y separación adecuados.
2. El buscador rechazaba fechas de actualización por delante del reloj local. Una actualización con timestamp del servidor al entrar otro jugador podía hacer desaparecer una sala de un emulador atrasado. Se acepta esa fecha futura; se mantienen los filtros de sala llena, antigua o en limpieza.
3. La transición día/noche espera al dibujo de la vista en lugar de finalizar cuando aún mide cero. Los callbacks de una ejecución cancelada no pueden iniciar otra transición.
4. Día/noche y el anuncio sin víctimas mantienen un tiempo mínimo de presentación medido con reloj monotónico. La muerte no habilita continuar antes de su tiempo mínimo. Una escala acelerada de Animator no equivale a haber leído el anuncio. Los tiempos de lectura de expulsión ya se programan con Handler y se conservan.
5. Al pausar, la transición interrumpida queda para volver a presentarse. Los anuncios activos de muerte, silencio y ausencia de víctimas vuelven a su cola; no se consideran leídos por cancelar sus efectos.
6. Se retiró la cubierta opaca añadida al anfitrión. Se conserva la espera de publicación, manteniendo la presentación anterior; los refrescos de cartas, perfil y chat tampoco muestran el estado pendiente. Esto reduce el adelanto de presentación del anfitrión, pero no promete simultaneidad exacta de dibujo entre dispositivos.
7. El chat invita con «Escribí tu primera sospecha…» y un cursor que parpadea cada 650 ms. El toque abre el editor; se detiene al abrir el chat, tener un borrador o salir de la pantalla. Reducir efectos deja el cursor estático; no desactiva los anuncios principales.

La cuenta registrada no aparece como condición para omitir estas animaciones. No se atribuye el incidente a la cuenta sin evidencia de ejecución en el dispositivo.

## Verificación

- `testDebugUnitTest`: **632 pruebas, cero fallos y errores**.
- `assembleDebug`: correcto; APK firmado y verificado.
- `lintDebug`: **cero errores, 1243 warnings**. Ya no están los errores de indentación del informe intermedio; se normalizaron los finales de línea antes de ejecutar y no se editaron fuentes durante el análisis.
- Seis suites Firebase pasaron en la ejecución conjunta: reglas Firestore, invitados, votos, reglas RTDB, integración backend y simulación online.
- La simulación de inicio falló inicialmente porque el emulador de Functions no descubrió las funciones en 10 segundos. Se repitió sola con `FUNCTIONS_DISCOVERY_TIMEOUT=60` y pasó, incluidas llamadas callable y concurrencia. No se modificó el backend para conseguir ese resultado.
- `git diff --check`: correcto.

Logs: `output/entrega-cinematicas-android.log`, `output/entrega-cinematicas-firebase-tests.log`, `output/entrega-cinematicas-start-retry.log`, `output/apk-signature-verification.log` y `output/deployment-2026-09-06/deploy.log`.

No se ejecutó una partida visual en los emuladores del usuario. La validación automática no certifica por sí sola que no haya cortes o diferencias de presentación bajo carga.

## Prueba recomendada con este APK

Instalar el mismo archivo en todos los emuladores, actualizando la instalación existente sin borrar datos, y crear una sala nueva.

1. Probar primero cinco jugadores. Observar inicio, amanecer y expulsión en anfitrión e invitados; comprobar el título completo de expulsión.
2. Mantener abierto Buscar partida en dos emuladores mientras entra un tercero. La sala debe permanecer mientras tenga cupo.
3. Probar VS IA en el emulador que saltaba animaciones. Repetir pasando brevemente la app a segundo plano durante una presentación y regresando.
4. Probar el cursor y su apertura del editor; comprobar que reducir decoraciones no omite los anuncios principales.
5. Después repetir con doce jugadores. Si persiste un adelanto, guardar el reporte de estabilidad del anfitrión y de un invitado de esa misma partida, indicando la fase y el momento del corte.

La migración del motor online a autoridad del servidor sigue pendiente; este despliegue no elimina por sí solo las facultades de un anfitrión modificado.
