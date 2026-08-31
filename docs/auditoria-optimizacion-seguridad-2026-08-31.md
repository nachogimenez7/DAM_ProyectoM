# Auditoría de optimización y seguridad — 31 de agosto de 2026

## Resultado medido

| Artefacto release | Antes | Después | Reducción |
|---|---:|---:|---:|
| APK sin firma | 100.013.175 bytes | 76.036.890 bytes | 23.976.285 bytes (23,97%) |
| Android App Bundle | 92.705.151 bytes | 79.751.052 bytes | 12.954.099 bytes (13,97%) |

La reducción no baja resolución ni recomprime audio con pérdida. R8 usa ahora referencias
estáticas y modo estricto para retirar arte histórico no alcanzable del binario. Además, 39 PNG
activos fueron convertidos a WebP lossless tras comparar los píxeles del resultado; el ahorro en
los archivos fuente es 5.488.387 bytes.

## Rendimiento y mantenimiento

- Se eliminó la resolución reflectiva con `resources.getIdentifier`; el catálogo estático evita
  búsquedas en tiempo de ejecución y permite al shrinker conocer exactamente qué imágenes usa.
- La aplicación ya no decodifica todos los efectos y emotes durante el arranque. `SoundPool`
  carga bajo demanda y libera memoria cuando toda la interfaz queda en segundo plano.
- La música larga sigue usando preparación asíncrona y libera su decodificador normal al pasar
  a segundo plano. La pista especial de victoria no se interrumpe por esta optimización.
- Gradle 8.14.5 usa ejecución paralela, caché de tareas y caché de configuración.
- Se actualizaron AndroidX, Material, Firebase BoM y Play Games manteniendo compileSdk 36.1.
  `core-ktx` queda en 1.17.0 porque 1.19.0 exige compileSdk 37 y AGP 9.1.
- `scripts/optimize-android-images.py` deja la conversión lossless reproducible y, por defecto,
  sólo informa: requiere `--apply` para modificar recursos.

## Seguridad aplicada

- Se desactivaron backup, transferencia de dispositivo y tráfico HTTP en claro desde el
  manifiesto, con reglas explícitas para Android antiguo y moderno.
- Los perfiles públicos no pueden cambiar de `publicId` una vez creados y sólo aceptan
  `actualizadaEn` generado por el servidor.
- El contador de IDs sólo puede crearse en 2 y luego avanzar exactamente de a uno, con marca de
  tiempo del servidor; ya no puede agotarse con un salto arbitrario.
- Credential Manager distingue la ausencia de credenciales de otros errores y conserva su flujo
  alternativo sin capturar una excepción genérica incorrecta.
- Dependencias de reglas/emuladores actualizadas. `npm audit` informa 0 vulnerabilidades; Node 22
  es el runtime declarado para las transitivas seguras.
- Release usa App Check con Play Integrity y debug usa el proveedor de depuración aislado en el
  source set correspondiente.

## Verificación ejecutada

- `testDebugUnitTest`: correcto.
- `lintDebug`: 0 errores. Quedan 1.225 advertencias, principalmente textos no externalizados,
  uso opcional de KTX y recursos fuente que R8 retira del release.
- `assembleRelease` y `bundleRelease`: correctos, con minificación y shrink de recursos.
- Reglas Firestore y Realtime Database contra sus emuladores: correctas.
- `npm audit`: 0 vulnerabilidades.

## Riesgos y acciones que no se resuelven sólo con código

1. **Antes de producción:** desplegar `firestore.rules` y `database.rules.json`; este trabajo no
   modifica Firebase productivo automáticamente.
2. **App Check:** comprobar métricas y activar enforcement para Firestore y Realtime Database en
   Firebase Console. Tener el proveedor en el APK no equivale a exigir tokens en el servidor.
3. **Clave Firebase:** `google-services.json` es configuración cliente, no un secreto. La API key
   debe restringirse en Google Cloud al package `com.traidores.juego`, certificados SHA válidos
   y APIs estrictamente necesarias.
4. **Autoridad de partida:** el anfitrión sigue calculando el estado. Un cliente anfitrión
   modificado puede hacer trampa; eliminar ese riesgo requiere un backend autoritativo.
5. **IDs públicos:** ahora son inmutables y el contador es monotónico, pero la unicidad absoluta
   frente a un cliente modificado requiere reservas de ID en servidor o una Cloud Function.
6. **Presupuesto de reglas:** algunas escrituras maliciosas rechazadas alcanzan el límite de
   1.000 expresiones de Firestore. Fallan cerradas, pero conviene dividir las reglas del estado
   autoritativo antes de seguir ampliando el protocolo.
7. **Audio:** las cinco pistas principales ocupan cerca de 26 MiB del APK. Una reducción adicional
   exigiría recodificación con pérdida y pruebas auditivas/dispositivos; se dejó fuera para no
   degradar el juego de forma silenciosa.
