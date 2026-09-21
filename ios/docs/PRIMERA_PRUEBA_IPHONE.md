# Primera prueba en el iPhone 13

La Mac compila con Xcode; el iPhone ejecuta la app. El proyecto requiere iOS 17 o posterior. Elegir una versión de Xcode que soporte la versión iOS instalada en el teléfono.

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
- Jugar abre dos tarjetas que indican «Próximamente», sin simular una partida.
- Roles, ayuda, opciones y regreso funcionan.
- Música se silencia desde el menú y opciones, y la preferencia sobrevive al cierre.
- El interruptor físico de silencio se respeta; la música se pausa al salir. Revisar también una interrupción de audio real.
- Texto grande y VoiceOver permiten desplazarse y llegar a todos los botones.
- La app abre y funciona en modo avión.

La primera compilación y revisión en iPhone todavía están pendientes. Si aparece un error de firma, registrar el mensaje exacto antes de cambiar la configuración del proyecto.
