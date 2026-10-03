# Primera prueba en el iPhone 13

La Mac compila con Xcode; el iPhone ejecuta la app. El proyecto requiere iOS 17 o posterior. Elegir una versión de Xcode que soporte la versión iOS instalada en el teléfono.

**Estado al 3/10/2026:** compilación Debug, instalación y lanzamiento aprobados en el iPhone 13 de Ignacio / iOS 27 con Xcode 27.0. Firma local configurada y modo desarrollador activado. Ícono de Traidores agregado. No hace falta repetir la configuración siguiente si se conserva este entorno. Ver `CONTINUIDAD_CODEX_2026-10-03.md` para los comandos y pendientes.

1. Terminar la actualización de macOS e instalar Xcode. Abrirlo y completar instalación inicial de componentes/SDK iOS.
2. Abrir `ios/TraidoresIOS/TraidoresIOS.xcodeproj`.
3. En Xcode → Settings → Apple Accounts, agregar tu cuenta Apple. Para esta prueba del menú se puede usar una cuenta personal; no hace falta contratar el programa pago para comenzar a ejecutar la app en tu propio dispositivo. TestFlight/distribución y capacidades futuras se revisan después. [Cuenta y ejecución en dispositivos](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices).
4. Seleccionar target TraidoresIOS → Signing & Capabilities → Automatically manage signing → tu Team. Si el Bundle ID provisional no está disponible, elegir uno propio. También se puede copiar `Configuration/Local.xcconfig.example` a `Local.xcconfig` y definir Team/Bundle ID allí; no versionar el archivo local.
5. Conectar el iPhone por cable, desbloquearlo y aceptar «Confiar» si aparece. Seleccionarlo como destino en Xcode.
6. Activar Developer Mode cuando lo solicite el dispositivo y seguir el reinicio/confirmación indicados por Apple. La opción puede aparecer después de vincularlo con Xcode. [Developer Mode](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device/).
7. Seleccionar scheme TraidoresIOS y ejecutar con ▶ o Cmd+R.

No se necesita Firebase, GoogleService-Info.plist, cuenta de Google, App Check ni una API de IA para esta versión.

## Qué revisar juntos

- El logo y los cuatro botones entran en pantalla sin invadir el notch ni el indicador inferior.
- Jugar contra IA permite elegir dificultad, configurar el lobby, repartir el rol y completar la partida local.
- Para una primera partida breve: NORMAL → Pampa con 5–7 jugadores → OPCIONES DE PARTIDA → Partida rápida → GUARDAR. Al terminar, revisar la ceremonia con marco de corona de Android, equipo ganador y «VER CRÓNICA»; volver al lobby y abrir «VER ÚLTIMO RESULTADO».
- Roles, ayuda, opciones y regreso funcionan.
- Música se silencia desde el menú y opciones, y la preferencia sobrevive al cierre.
- Como en Android, la intro y la música usan el volumen multimedia (suenan con el interruptor de silencio activado); la música se pausa al salir. Revisar también una interrupción de audio real.
- Texto grande y VoiceOver permiten desplazarse y llegar a todos los botones.
- La app abre y funciona en modo avión.

La última compilación e instalación (rediseño de ganadores y Roboto) están comprobadas; el intento final de lanzamiento terminó con código 1. Abrir Traidores desde su icono. Falta corregir la auditoría de Dynamic Type de «COMISARIO» en el cierre. La revisión visual y la escucha del audio en el iPhone siguen pendientes. Si aparece un error de firma en una futura instalación, registrar el mensaje exacto antes de cambiar la configuración del proyecto.
