# Traidores

Juego móvil de deducción social para Android, desarrollado en Kotlin. Incluye partidas locales contra IA y partidas online de 5 a 15 jugadores sobre Firebase Firestore y Realtime Database.

Versión actual de pruebas: **0.1.34** (`versionCode 35`).

## Abrir y compilar

1. Abrí esta carpeta desde Android Studio.
2. Esperá la sincronización de Gradle.
3. Seleccioná un emulador o celular Android.
4. Ejecutá `app`.

La compilación necesita `app/google-services.json`. El archivo contiene la configuración de Firebase y no se guarda en Git.

Desde PowerShell también se puede compilar con:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

## Estructura

- `app/`: código, pruebas y recursos de Android.
- `functions/`: backend preparado para Firebase Functions.
- `scripts/`: pruebas de reglas, simulaciones y mantenimiento.
- `docs/`: documentación técnica vigente.
- `database.rules.json`, `firestore.rules`: reglas de seguridad desplegables.
- `roles_gauchos/`, `roles_griegos/`, `roles_medievales/`: arte original de los roles.
- `assets/pack_bienvenida/`: fuentes finales del pack de apoyo.

## Verificación

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
npm run test:firestore-rules
npm run test:database-rules
```

La documentación principal está en [docs/README.md](docs/README.md).
