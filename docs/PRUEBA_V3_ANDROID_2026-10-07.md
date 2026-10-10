# Prueba interactiva de V3 en Android

7 de octubre de 2026. Esta entrega permite probar el motor V3 desde Android sin
necesitar otros cuatro teléfonos. No despliega ni habilita V3 en Cloud.

## Qué se prueba

La aplicación usa Auth, Firestore, RTDB, callables y tareas emuladas del backend
V3. No ejecuta un motor local para decidir resultados. Se crea una sala de cinco
cuentas descartables: una para la persona y cuatro participantes automáticos.
Los automáticos leen la proyección pública y su propia proyección privada con
SDK autenticado y envían acciones por `accionPartidaV3`; no consultan roles ajenos
con Admin. El alta de cuentas/sala es preparación de QA con Admin. Para conectar
los listeners se espera únicamente el bit de membresía de cada participante;
antes de esa publicación los permisos RTDB correctamente deniegan acceso.

La pantalla de entrada espera «EMPEZAR PARTIDA». Después se puede leer el rol,
usar poderes, votar, chatear, salir y preparar revancha. Los automáticos responden
en reparto, noche y votaciones, y se marcan listos tras la revancha. No conversan
como personas ni sustituyen una prueba multijugador con teléfonos reales.
La presentación V3 sigue siendo la pantalla técnica existente; la integración
de la mesa habitual y la prueba conjunta con el adaptador iOS siguen pendientes.

## Reproducción

Se necesita Node 22, JDK de Android Studio y un Android emulado activo. Abrir una
AVD y usar su identificador real obtenido con `adb devices`.

```sh
node scripts/prepare-server-client-android.cjs prepare
node scripts/with-jdk.cjs sh ./gradlew :app:assembleDebug -PtraidoresServerOnlineV3=true -PtraidoresOnlineAuthorityEmulator=true
adb -s emulator-5558 install -r app/build/outputs/apk/debug/app-debug.apk
TRAIDORES_QA_ADB=/ruta/al/sdk/platform-tools/adb TRAIDORES_QA_DEVICE=emulator-5558 npm run play:authority-android
```

El último comando mantiene Firebase local y la sala activos. Al interrumpirlo,
el controlador cierra la app QA, detiene sus listeners, borra solo sus cuentas,
sala, historial y ledger, y restaura el gate emulado anterior. Requiere proyecto
`traidores-local`, los puertos aislados 18081/19000/19099 y un dispositivo emulado;
rechaza otros proyectos/destinos. El proveedor App Check sintético solo existe
en Debug y la actividad exige configuración emulada. Release conserva V3 apagado.

Después de compilar el APK QA, quitar su configuración temporal y reconstruir
Debug normal. Esto no modifica el APK QA ya instalado:

```sh
node scripts/prepare-server-client-android.cjs cleanup
node scripts/with-jdk.cjs sh ./gradlew :app:assembleDebug
```

## Evidencia

- Ensayo nativo completo aprobado: Auth, inicio, rol privado, intención, avance
  con la app cerrada, reconexión por lobby, chat RTDB, resultado, historial por
  cuenta y revancha sin chat anterior: `output/v3-play-native.log`.
- Cinco pruebas del controlador automático: coherencia de fase, muertos/final,
  aliados privados del asesino, cooldown del Mercenario, silencio/desempate y
  facultades del Alcalde: `output/v3-play-bot-tests.log`.
- Inicio manual comprobado en Android y cuatro acciones iniciales de los bots
  aceptadas por el servidor: `output/v3-play-interactive-verification.log`.
  Se corrigieron en QA el alta prematura de listeners antes de la membresía y
  la disposición de la pantalla de espera; se comprobó el botón en UI nativa.
- APK QA: `output/traidores-v3-qa.apk`; logs de compilación bajo `output/`.

No verifica Tasks/IAM reales ni costos facturados. El ensayo de Cloud requiere
desplegar las funciones y reglas con gate cerrado, comprobar permisos/índices,
habilitar únicamente los participantes de prueba y medir antes de abrir V3.
