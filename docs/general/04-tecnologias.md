# Tecnologías utilizadas

Datos extraídos de `app/build.gradle`, `build.gradle`, `settings.gradle`, `gradle.properties` y `gradle/wrapper/gradle-wrapper.properties`.

## Lenguajes

- **Kotlin 2.3.0** — Activities, reglas de juego, estado de presentación, adapters, coordinadores de animación, persistencia local.
- **XML** — layouts, temas, colores, drawables y manifest bajo `app/src/main/res/`.
- **Groovy Gradle DSL** — configuración de build (`build.gradle`, `app/build.gradle`).

## Runtime y SDK

| Ítem | Valor |
|---|---|
| `applicationId` | `com.traidores.juego` |
| `namespace` | `com.traidores.juego` |
| `minSdk` | 24 |
| `targetSdk` / `compileSdk` | 36 / 36.1 |
| `versionCode` | 1 |
| `versionName` | `0.1.0-alpha` |
| Bytecode Java | 8 (`sourceCompatibility` / `targetCompatibility` / `jvmTarget = 1.8`) |
| Minificación release | desactivada (`minifyEnabled false`) |

## Build

- **Gradle Wrapper 8.13**.
- **Android Gradle Plugin 8.13.0**.
- Plugin **`com.google.gms.google-services`** (Google Services) → requiere `app/google-services.json`.
- Kotlin official style habilitado (`gradle.properties`).
- Sin version catalog ni lockfile de dependencias.

## Dependencias

| Dependencia | Versión | Uso |
|---|---|---|
| `androidx.core:core-ktx` | 1.12.0 | APIs Kotlin-friendly de Android |
| `androidx.appcompat:appcompat` | 1.6.1 | Base de Activity y compatibilidad |
| `com.google.android.material:material` | 1.11.0 | Componentes y diálogos Material |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | Layout responsivo (poco usado aún) |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Listas de roles/perfiles |
| `androidx.cardview:cardview` | 1.0.0 | Tarjetas |
| `com.google.firebase:firebase-bom` | 33.12.0 | BOM de Firebase |
| `com.google.firebase:firebase-firestore` | (via BOM) | Base de datos online (NoSQL) |
| `junit:junit` | 4.13.2 | Tests unitarios JVM (`testImplementation`) |

> Nota: el código importa `com.google.android.gms.tasks.Task` (Google Play Services Tasks), disponible transitivamente vía Firestore.

## Permisos (AndroidManifest)

- `android.permission.VIBRATE`
- `android.permission.INTERNET` (requerido por el modo online Firestore)

## Persistencia

- **Firebase Firestore** (NoSQL documental) — modo online.
- **SharedPreferences** — namespace `TraidoresPrefs` (preferencias, perfil, identidad local).
- **Serialización Java** (`Serializable`) — `GameSession` y modelos viajan por `Intent`/`Bundle`.

## Testing

- **JUnit 4** sobre JVM en `app/src/test/java/com/traidores/juego/`.
- Cubre reglas/motor y la infraestructura online (gates, resolvers, mappers).
- **No hay** instrumentación Android, screenshot testing, ni tests de UI/accesibilidad.

## Entorno de desarrollo

- **Windows** como entorno activo.
- **Android Studio** como IDE previsto.
- Android SDK 34 y JDK requeridos.
- `local.properties` (gitignored) contiene la ruta del SDK.
- Sin dependencia de Node.js ni toolchain de línea de comandos extra.

## Recursos multimedia

- Fuentes personalizadas en `app/src/main/res/font/`.
- Imágenes de mapas/roles/fondos en `drawable/` y `drawable-nodpi/`, más fuentes externas en `roles_gauchos/`, `roles_griegos/`, `roles_medievales/`.
- Música y efectos de sonido en `app/src/main/res/raw/`.
