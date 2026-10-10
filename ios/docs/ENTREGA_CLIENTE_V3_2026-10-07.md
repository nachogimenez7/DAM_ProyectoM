# Cliente iOS V3 — continuación del 7 de octubre de 2026

Trabajo sobre la entrega de Claude, todavía sin commit. Las bases de FirebaseRooms,
ServerMatch, ServerMatchSession y OnlineMatchView son su entrega; no se reimplementaron.
Esta continuación modifica solo iOS. No cambia backend, reglas, funciones, Android,
allowlists de producción ni configuración de facturación.

## Comportamiento añadido y corregido

- Desertor: elección privada en REPARTO; explicación del sorteo del servidor si no elige;
  revisión única desde ronda 4 durante debate, incluso silenciado; ventana final con
  mantener/cambiar y confirmación que explica que ambas opciones consumen el uso.
- Revancha: botón solo para el creador, llama prepararRevanchaV3 y espera la publicación
  del matchId nuevo antes de volver al lobby de la misma sala. Descarta rol, objetivos,
  diálogos y anuncios anteriores. Evita volver a abrir la partida vieja si Firestore
  todavía publica el resultado anterior; espera acotada con posibilidad de reintentar.
- Reingreso: una sala V3 finalizada sigue siendo recuperable para ver el resultado.
  Un cambio de protocolo se rechaza en vez de activar un motor local.
- Salir: llamada al servidor tanto antes como después del resultado; antes del final,
  vivo o muerto, cuenta como derrota. Después del final conserva el resultado registrado.
  Se bloquean toques repetidos y se muestran errores de salida.
- Amanecer: se reutilizan DeathRevealView, NoDeathRevealView, SilenceRevealView,
  OracleRevealView y ContrapuntoRevealView. Capturan la proyección pública del anuncio:
  una revelación de muerte no usa roles privados ni roles revelados posteriormente.
  CONTINUAR únicamente cierra la presentación, nunca resuelve una fase.
- Resultado: retratos del perfil de ganadores debajo de su rol, usando el roster ya
  obtenido en el lobby. No requiere nuevas consultas de perfiles.
- Inicio: reintentos acotados ante la publicación tardía de permisos RTDB, cancelables
  al salir; callbacks antiguos no reinician la sesión. NIGHT_START no duplica el título.
- Presentaciones: cubrir la mesa con chat, rol o confirmación no pausa la sesión V3.
  Las revelaciones bloquean los toques y ocultan la mesa para VoiceOver hasta cerrarse.
- Acciones: entradas deshabilitadas sin conexión o durante sincronización; se vuelve a
  validar fase, acción y objetivo en el toque. Los reintentos usan el mismo requestId.
- Consumo: chat con listener solo mientras está abierto, anillo acotado existente;
  recuperación de fase solo con conexión y estado coherente. No se añadieron listeners
  por jugador ni consultas recurrentes de perfil.
- Accesibilidad: texto de mapas legible aun cuando solo el anfitrión puede elegirlos;
  estadísticas sin confirmar dicen «Sin datos». Los tests con letra AX5 desplazan los
  controles hasta hacerlos visibles antes de auditar sus textos.

## Validación reproducible

Simulador usado: iPhone 18 Pro Max, F9FA9920-CBD7-4F13-AABE-1D1A410D6818.
No usar iPhone 17 mientras otra sesión instala builds allí.

1. Desde la raíz del repositorio, preparar un directorio temporal con symlinks a
   firestore.rules, firestore.indexes.json, database.rules.json y functions/.
   Copiar ios/Scripts/server_v3_emulators.firebase.json como firebase.json en ese directorio.
2. Ejecutar los emuladores con project **traidores**, nunca otro identificador:

   ```sh
   node scripts/with-jdk.cjs firebase emulators:start --project traidores --config /ruta/temporal/firebase.json --only auth,firestore,database,functions
   ```

   Puertos aislados: Auth 29099, Firestore 28081, Functions 25001, RTDB 29000.
   No detener ni reconfigurar los emuladores que otra sesión tenga en los puertos habituales.
3. En otra terminal: `node ios/Scripts/server_v3_native_harness.cjs`.
   El controlador fija localhost y el project, usa solo Admin SDK de los emuladores
   y escucha únicamente en 127.0.0.1:29888. Datos y contraseña son de prueba local.
4. `bash ios/Scripts/test_server_v3_native.sh` compila y corre cinco casos nativos.
   La variable de opt-in se inyecta en el .xctestrun: ponerla solo delante de xcodebuild
   no la transmite al proceso XCTest. El script no modifica project.pbxproj.
5. `bash ios/Scripts/test_core.sh`; `plutil -lint ios/TraidoresIOS/TraidoresIOS.xcodeproj/project.pbxproj`.

Los casos nativos entran por la interfaz, se unen al lobby y lo inician mediante la
callable real emulada. Para llegar de manera reproducible a los casos límite, el
controlador fija roles y fases de las salas de prueba; las acciones, chats, abandono
y revancha de la app usan los SDK, callables y reglas reales de los emuladores.
Esto no sustituye una partida completa entre teléfonos ni una medición de Cloud Tasks
y latencia en producción. Las pruebas de pantallas con servicios en memoria siguen
separadas y solo se usan con argumentos explícitos de UI testing.

### Resultado de esta ejecución

- Build Debug para el simulador indicado: aprobado. `project.pbxproj`: `plutil -lint` OK.
- Núcleo: **106/106**, en 10 suites. Contratos compartidos Android/iOS: aprobados.
- OnlineFlowUITests: **15 casos habilitados verificados**, entre la corrida completa y
  los reintentos dirigidos después de corregir fallos. Tres casos quedaron omitidos
  por requerir fixtures independientes de cuentas/media; no se cuentan como aprobados:
  `testNativeHistoryAndPhotosAgainstEmulators`, `testNativeLocalResultsReachFirebase`
  y `testRealAccountAndProfileAgainstEmulators`.
- Los **cinco casos V3 nativos pasaron juntos** en la regresión final: acceso inicial
  sin el aviso falso, una sola presentación de noche, chat público + voto, canales
  Traidores/Espectadores + abandono estando muerto, Desertor inicial/ronda 4/ventana
  final, resultado recuperado/revancha y amanecer sin revelar rol oculto ni repetir
  eventos al reingresar. El voto confirma recepción en el servidor; no sustituye
  una ronda completa de votos y doble empate con varios clientes.
- Letra AX5: navegación, código, lobby y acceso al botón de listo aprobados; capturas
  revisadas. La auditoría automática excluye recortes de controles fuera del viewport
  y no se repite sobre la misma pantalla desplazada: iOS 27 reutilizó rectángulos y
  recortes anteriores y marcó como contraste bajo texto crema sobre fondo oscuro.
  Los controles desplazados se revisaron en la captura; `.textClipped` mantiene la
  exclusión documentada previamente en el test.
- `git diff --check`, sintaxis del controlador Node y del script Bash: OK.

Evidencia local (temporal): `/tmp/traidores-ios-v3-core.log`,
`/tmp/traidores-ios-v3-verified.xcresult`,
`/tmp/traidores-ios-v3-final-regression.xcresult` y
`/tmp/traidores-ios-v3-ax-final.xcresult`. Las corridas iniciales registran los fallos
corregidos; la regresión V3 final y el reintento AX5 documentan su verificación.

## Próximo bloque: Firebase real y Android + iOS

1. Registrar en App Check el token debug de ESTE simulador bajo la app iOS del proyecto.
   El token se copia de forma privada a la consola; no se publica en docs ni en git.
2. Autorizar exclusivamente el UID y la sala de prueba en onlineMaintenance/serverAuthority.
   Mantener enforcement de App Check y rollout V3 restringido.
3. Probar una partida V3 completa con cliente Android e iOS: temporizadores sin anfitrión,
   chat, votar, doble empate, Desertor, reconexión, abandono, resultado/historial y revancha.
4. Medir lecturas Firestore, bytes RTDB, invocaciones y Tasks para ese recorrido. Estos
   emuladores no son evidencia de un costo mensual ni de latencia con contención real.

No hay que regenerar el proyecto Xcode. Los archivos de app de Claude ya están registrados
en project.pbxproj; los archivos nuevos de esta continuación son documentación y scripts.

## Cambio de prioridad al cerrar esta entrega

El usuario prioriza la beta abierta Android: online, consumo, publicidad y bots,
en ese orden; añade pack de apoyo/compras cosméticas y tráiler/ficha de Play Store.
La prueba iOS contra Cloud y la prueba mixta quedan documentadas
como pendientes, sin bloquear el siguiente bloque Android. Ver
`docs/PLAN_BETA_ABIERTA_ANDROID.md` desde la raíz del repositorio.
