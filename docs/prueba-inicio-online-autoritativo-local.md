# Prueba local del inicio online autoritativo

Esta ruta es solo para desarrollo. No usa datos de Firestore ni RTDB de produccion y no cambia
el comportamiento del AAB release.

## Android Emulator

1. Abrir una terminal en la raiz del proyecto y dejar los servicios ejecutandose:

   ```powershell
   npm run emulators:android
   ```

2. En otra terminal, compilar la APK debug con el opt-in local. Este comando selecciona
   automaticamente el JDK incluido en Android Studio:

   ```powershell
   npm run build:android-emulator
   ```

3. Instalar `app/build/outputs/apk/debug/app-debug.apk` en Android Emulator y abrir la app.
4. Crear y unir jugadores como en una sala normal. Todos los datos de esa ejecucion quedan en
   Firebase Emulator, visible en `http://127.0.0.1:4000`.
5. Al iniciar, Logcat debe incluir `firebase_emulators_enabled` y
   `online_callable_start_success`.

`10.0.2.2` es el host predeterminado porque Android Emulator lo usa para alcanzar la PC.

## Celular conectado por USB

1. Con el celular autorizado en `adb devices`, redirigir los tres puertos:

   ```powershell
   adb reverse tcp:5001 tcp:5001
   adb reverse tcp:8081 tcp:8081
   adb reverse tcp:9000 tcp:9000
   ```

2. Compilar usando localhost:

   ```powershell
   .\gradlew.bat assembleDebug `
     -PtraidoresOnlineAuthorityEmulator=true `
     -PfirebaseEmulatorHost=127.0.0.1
   ```

3. Instalar la APK generada. Repetir `adb reverse` para cada celular conectado.

## Volver al comportamiento normal

Compilar sin `-PtraidoresOnlineAuthorityEmulator=true`. Tanto debug normal como release dejan
`USE_ONLINE_AUTHORITY_EMULATOR=false`; la aplicacion vuelve a Firebase real y al inicio legacy.

## Casos manuales iniciales

- Solo el anfitrion puede iniciar.
- Todos deben estar listos.
- Un empate muestra el selector de mapa y el segundo intento comienza.
- Tocar varias veces no crea otra partida.
- Cada jugador ve su reparto privado y nunca roles ajenos.
- Cerrar y abrir un cliente conserva el mismo `matchId` y rol.
