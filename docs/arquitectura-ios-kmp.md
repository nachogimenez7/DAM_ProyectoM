# Camino gradual hacia iOS

## Decisión

Preparar el dominio para Kotlin Multiplatform (KMP), sin crear todavía el modulo
multiplataforma ni reescribir la interfaz Android.

La primera versión iOS podrá usar SwiftUI y los SDK oficiales de Firebase para Apple,
mientras comparte con Android las reglas de juego, modelos, validaciones y resolución de
fases. Esta separación evita acoplar el dominio a Activities, Views o SDKs de Firebase.

## Fronteras

### Compartible (`commonMain` futuro)

- Reglas y estado de la partida.
- Motor local y resolución autoritativa online.
- Política AFK, votos, empates y temporizadores lógicos.
- Presentaciones puras: etiquetas, textos y estados de controles.
- Puertas de sincronización y validaciones.
- Interfaces de repositorios, reloj y diagnósticos.

### Específico de Android

- Activities, Views, recursos y animaciones Android.
- Firebase Android SDK, Play Games y Crashlytics Android.
- Audio, vibración, permisos y ciclo de vida Android.

### Específico de iOS

- SwiftUI/UIKit, recursos y animaciones Apple.
- Firebase Apple SDK, Game Center y Crashlytics Apple.
- Audio, hápticos, permisos y ciclo de vida iOS.

## Dependencias permitidas

```text
Android UI ----> dominio compartido <---- iOS UI
    |                                      |
Firebase Android                      Firebase Apple
    |                                      |
    +------ implementan interfaces --------+
```

El dominio nunca debe importar `android.*`, `androidx.*`, `com.google.firebase.*`, UIKit
ni SwiftUI.

## Orden de migración

1. Extraer funciones puras de las Activities y cubrirlas con pruebas.
2. Encapsular Firebase detrás de interfaces pequeñas, sin cambiar el esquema remoto.
3. Separar modelos de cualquier tipo JVM/Android no portable.
4. Crear el modulo KMP y mover una pieza pura por vez.
5. Validar Android después de cada movimiento.
6. Crear el cliente iOS cuando haya acceso a macOS y Xcode.

## Lo que no se hará todavía

- Reescribir toda la UI con Compose Multiplatform.
- Duplicar la lógica del juego en Swift.
- Cambiar simultáneamente arquitectura, interfaz y esquema de Firebase.
- Agregar dependencias multiplataforma sin una pieza concreta que migrar.
