# Continuidad en la notebook — beta 0.1.13

## Antes de salir de la PC de escritorio

- Confirmar que el último commit de `main` esté publicado en GitHub.
- Subir a Drive el APK debug: `app/build/outputs/apk/debug/app-debug.apk`.
- Si el AAB se firma en la notebook, llevar la clave de carga y sus contraseñas por un medio
  privado. Los archivos `.jks` y `.keystore` están excluidos de Git a propósito.
- No depender del APK de la web para la clase: pesa más de 100 MB y no viaja en el repositorio
  Android.

## En la notebook

1. Ejecutar `git pull origin main` o clonar el repositorio nuevamente.
2. Abrir el proyecto raíz en Android Studio y esperar la sincronización de Gradle.
3. Confirmar que Android Studio usa su JDK integrado y que creó `local.properties` con la ruta
   correcta del SDK; ese archivo tampoco viaja por Git.
4. Ejecutar al menos `testDebugUnitTest` y `assembleDebug` antes de generar la entrega.
5. Generar un **Android App Bundle firmado**, no el AAB sin firma ni el APK debug.
6. Guardar una copia segura de la clave de carga usada para Play Console.

## Archivos de entrega

- Play Console: AAB firmado de la última versión.
- Profesor/Drive: APK debug o release generado después del último commit.
- Textos y gráficos: carpeta `Google Play/` del repositorio.

## Comportamiento esperado del tutorial

El indicador “ya visto” es local a cada instalación. Una actualización instalada sobre la app
existente conserva el dato; desinstalar, borrar datos o crear un emulador nuevo hace que aparezca
una vez nuevamente. Dentro de una instalación normal no debe repetirse al volver a otro lobby.
