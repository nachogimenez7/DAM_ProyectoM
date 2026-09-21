# Traidores iOS

App nativa SwiftUI, iPhone, iOS 17+. Orden acordado: **menú → partida local contra IA → online compatible con Android**.

Abrir `TraidoresIOS/TraidoresIOS.xcodeproj` con Xcode. Scheme: **TraidoresIOS**. No requiere CocoaPods, XcodeGen ni paquetes remotos. El Bundle ID provisional es `com.traidores.juego.ios`; elegir uno definitivo al configurar firma/Firebase.

## Esta entrega

- Proyecto Xcode y package Swift `TraidoresCore`, aislados de Android.
- Menú con fondo, logo, medallón, fuente Bree Serif y música originales.
- Navegación a selección de modos, guía de 11 roles, ayuda, opciones, perfil pendiente y acerca de.
- Música activable, preferencia persistente, pausa al salir y respeto del modo silencio.
- Los modos de juego se anuncian como próximos: **todavía no hay partida local ni online**.
- Catálogo de mapas, roles/equipos y fases con identificadores conservados de Kotlin. Sin motor ni Firebase.

No se modifican archivos fuera de `ios/`. Referencia Android: `32e4fc09dfb726d5264b27804bb4f8c1f5ae1a21`, versión `0.1.34`.

## Verificación

Desde la raíz del repositorio:

```sh
bash ios/Scripts/test_core.sh
```

Las pruebas usan Swift Testing (Swift 6+) y comparan el catálogo y la serialización Swift con un fixture extraído directamente de Kotlin, incluyendo umbrales y exclusividad de mapas. El script resuelve las rutas de Testing cuando solo hay Command Line Tools. Con Xcode también se puede usar `swift test --disable-xctest --package-path ios/Packages/TraidoresCore`. La referencia se actualiza explícitamente con `python3 ios/Scripts/export_android_catalog.py`, tras revisar cualquier cambio de Android.

Resultado local: **3 pruebas de equivalencia aprobadas**, núcleo compilado con Swift 6.3.3. Se comprobaron sintaxis Swift de la app, formato de plists/proyecto, referencias y hashes de los 7 recursos importados. **La app iOS aún no fue compilada ni ejecutada**: falta Xcode y SDK iOS. Pasar las pruebas macOS del paquete no valida la UI, la firma ni el empaquetado iOS.

Cuando Xcode esté instalado, comprobar la build sin firma:

```sh
xcodebuild -project ios/TraidoresIOS/TraidoresIOS.xcodeproj \
  -scheme TraidoresIOS -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath ios/DerivedData CODE_SIGNING_ALLOWED=NO build
```

Después ejecutar desde Xcode en un simulador o en el iPhone. Seguir [Primera prueba en iPhone](docs/PRIMERA_PRUEBA_IPHONE.md). Pendientes: revisión visual real, accesibilidad, ciclo de vida/audio, ícono final y animación de introducción. No se promete una build validada hasta ese paso.

## Mantenimiento

- `Scripts/generate_xcode_project.py`: regenera proyecto, scheme y plists de forma determinista; no requiere librerías. Si se cambian esos archivos generados, actualizar también el script. Preferencias de firma locales en `Configuration/Local.xcconfig` o en Xcode.
- `Scripts/prepare_menu_assets.py`: requiere Pillow; copia o convierte únicamente recursos seleccionados a iOS. No redimensiona imágenes. Registra origen y hashes en [ASSET_MANIFEST.json](docs/ASSET_MANIFEST.json).
- `Packages/TraidoresCore`: dominio portable sin imports de UI o backend; base para el motor local siguiente.
- `TraidoresIOS/Configuration`: Debug/Release y ejemplo de configuración local; no guardar claves privadas.

Firebase puede esperar hasta el online. Esta build no se conecta a salas, cuentas ni servicios de producción. La IA futura usará la lógica local de Android: no necesita un servicio de IA externo.

Ver [plan actualizado](docs/PLAN_MIGRACION_IOS.md) y [contrato conservado](docs/ONLINE_CONTRACT.md).
