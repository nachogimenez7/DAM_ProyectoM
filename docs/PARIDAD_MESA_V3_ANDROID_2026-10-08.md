# Mesa Android V3: aplicación de la revisión de Claude

Fecha: 8 de octubre de 2026. Base: `REVISION_PARIDAD_MESA_ANDROID_V3.md`,
incluidas las tres decisiones confirmadas del usuario. Esta entrega conserva
la autoridad del servidor y reutiliza la presentación del gameplay habitual.

## Implementado

**Servidor.** La expulsión se aplica al entrar en `RESULTADO`: muerte, causa
`VOTE`, evento `DAY_EXPULSION`, rol autorizado y victoria especial del Bufón
quedan en la misma publicación. El plazo es `max(transicionSeg, 8)` segundos,
o 12 para el Bufón; sin expulsión sigue siendo el plazo configurado. El ganador
se evalúa al vencer `RESULTADO`, para presentar primero la expulsión y después
la victoria. Se corrigió también el caso en que abandonar durante el resultado
abría la ventana del Desertor y dejaba al expulsado vivo.

El Bufón se anuncia públicamente al ser expulsado, aun con los roles al morir
ocultos. Su victoria especial nunca se publica antes. El recién expulsado tiene
`deadChat=false` durante `RESULTADO`; los muertos de rondas anteriores conservan
sus permisos. Una fase `RESULTADO` iniciada por el despliegue anterior todavía
puede completarse con seguridad, sin aplicar dos veces la muerte.

**Mesa Android.** Se conectaron `VoteResultAnimator` (carta, revelación permitida
y bota), `JesterVictoryAnimator`, `DayNightTransitionAnimator`, sonidos existentes
y `MusicManager`. El recuento muestra totales agregados una vez; el resultado
muestra expulsión o ausencia de expulsión. No se inventan votantes individuales.
La victoria del Bufón continúa la partida, sin acciones de espectador local.

Muerte, ausencia de víctimas y silencio avanzan automáticamente. La cola puede
terminar durante el debate cuando quedan al menos 10 segundos; cada ceremonia
tiene un máximo de 6 segundos. Oráculo y Contrapunto esperan su turno. Ventanas
de acción y selección siguen cerrándose al cambiar de fase. Ninguna ceremonia
detiene el reloj ni resuelve votos o poderes.

**Sin anticipos.** Mientras llega el impacto, el roster, estado propio, anuncios,
eventos y accesibilidad retienen la muerte/rol del expulsado. El Bufón se anuncia
al llegar a su celebración. Esta retención es exclusivamente visual: el snapshot
autoritativo y los permisos nunca se alteran. Los teléfonos reciben una publicación
coherente; su llegada puede diferir por la red, sin sincronización adicional.

**Reconexión.** Evento ya visto: resultado estático, sin repetir animación ni sonido.
Evento nuevo con tiempo suficiente: ceremonia completa o comprimida según el plazo.
Con menos de 2,5 segundos, se muestra directamente el estado final. El cursor por
`matchId:seq` y fase evita repeticiones. No se reconstruyen acontecimientos de
rondas anteriores ni se ejecuta `GameEngine` en la mesa V3.

La celebración del Bufón se reconoce mediante su clave pública de partida/UID,
no por el nombre. La carta de expulsión se selecciona por UID antes de entregarla
al animador compartido, evitando confusiones si dos jugadores tienen el mismo alias.

## Consumo

Sin nuevos listeners, callables ni publicaciones para estas ceremonias. Se mantienen
los tres listeners de público, privado y permisos. Cambia el contenido de la
publicación existente de resultado y se alarga esa fase algunos segundos; la música
y las animaciones son locales. Esto no es una nueva medición de facturación ni
una prueba de capacidad del servidor. La medición Cloud anterior permanece en
`MEDICION_V3_BETA_ANDROID.md`; hay que repetirla con el despliegue definitivo.

## Archivos principales

- `functions/src/onlineGameCore.js`: entrada y vencimiento de resultado, victoria
  especial y permisos del expulsado.
- `functions/test/onlineGameCore.unit.test.js` y
  `functions/test/onlineGameRecovery.integration.test.js`: casos actualizados.
- `ServerGameTableRenderer.kt`, `ServerGameTablePresentation.kt`,
  `VoteResultAnimator.kt`, `ServerGameContract.kt`, `ServerGameplayActivity.kt`.
- `scripts/generate-server-client-fixtures.cjs`: proyecciones reales regeneradas
  (59 casos) para `ServerGameContractTest`.
- `scripts/test-server-table-android.cjs`: prueba nativa dirigida, exclusivamente
  en emuladores aislados, con plazos reales de 4/8/12 segundos para este bloque.
  Los casos anteriores de ventanas usan plazos largos para operar la UI.

## Verificación

- Motor y demás unitarias backend: 71/71 aprobadas.
- Android: 728/728 unitarias aprobadas y APK Debug compilada.
- Compilación `compileReleaseKotlin` aprobada; V3 sigue apagado en Release.
- Integración backend: 39/39 aprobadas (Firestore + RTDB, sin workers automáticos).
- AVD Pixel 10, app separada `com.traidores.juego.v3qa`: muerte y silencio con
  amanecer real de 4 segundos; expulsión de 8 segundos sin anticipo en el roster;
  Bufón con roles ocultos y 12 segundos; reingreso durante la expulsión con resultado
  estático. Todos estos escenarios aprobaron sobre el APK final.
- Prueba nativa completa: Auth, App Check emulado, inicio callable, acción privada,
  avance con la app cerrada, reconexión, chat real, historial y revancha limpia.
- Las ventanas anteriores de Desertor, desempate, Alcalde, Contrapunto y Oráculo
  también aprobaron en una corrida previa del mismo bloque. La corrida final dirigida
  usa `TRAIDORES_QA_PARITY_ONLY=true` para no repetir esas ventanas.

Evidencias: `output/server-v3-parity-unit-all.log`, `server-v3-parity-build.log`,
`server-v3-parity-build-final.log`, `server-v3-parity-native-windows.log`,
`server-v3-parity-native-final.log`, `server-v3-parity-integration.log`, y capturas
`server-v3-table-real-*.png`. Se inspeccionaron visualmente la retención previa al
impacto, el silencio durante debate, el Bufón y el resultado estático al reconectar.

La corrida conjunta inicial de integración usó por error Functions activas sobre
fixtures de tiempo simulado; sus workers adelantaban las fases y esas comprobaciones
fallaron. La ejecución correcta, separada y sin Functions, terminó 39/39. El log
nativo conjunto conserva ese fallo de integración aunque los dos scripts nativos
ya habían aprobado. También se corrigieron reintentos de captura (sin reutilizar XML
viejo), la mayúscula automática del teclado y la espera de fin de la ceremonia antes
de abrir el chat. No hubo que cambiar permisos ni desactivar App Check para probar.

Los fixtures nativos preparan escenarios difíciles mediante Admin solo en los
emuladores. Las acciones del teléfono atraviesan Auth/App Check/Functions y los
vencimientos del bloque nuevo respetan el reloj real. No equivalen a varios
jugadores humanos ni a una prueba contra Firebase productivo.

No se hizo una escucha manual de todos los sonidos ni la comparación simultánea
entre dos teléfonos. El A56 no estaba conectado durante esta entrega. La configuración
Android temporal de los emuladores se retiró; el APK de QA queda en
`output/traidores-v3-mesa-paridad.apk` y requiere los emuladores de Firebase de la Mac.

Para repetir, preparar/compilar el APK QA con
`traidoresServerOnlineV3=true`, `traidoresOnlineAuthorityEmulator=true`,
`traidoresIsolatedQaApp=true`, `firebaseEmulatorHost=127.0.0.1` y los cuatro `adb reverse`
para 19099/18081/19000/15001. Los scripts nativos requieren Auth/Firestore/Database/
Functions de `firebase.authority-qa.json` y `--project traidores-local`.
**Las unitarias de integración con `nowMs` simulado requieren solo Firestore y Database**,
sin Functions. Mantener estos dos perfiles separados. Al terminar, ejecutar
`node scripts/prepare-server-client-android.cjs cleanup`.

## Siguiente paso

1. Despliegue restringido de Functions y ensayo en el A56, manteniendo App Check
   y la allowlist. Este bloque no modificó Firebase productivo ni activó Release.
2. Partidas completas, reconexión, expulsión final, revancha y consumo con varios
   clientes; grabación de una ronda con los plazos normales.
3. Decidir y verificar la activación V3 en la versión que se publicará. Hoy sigue
   apagada en Release; no basta instalar este APK de QA.

Pendientes de presentación: ceremonia específica de autorrevelación del Alcalde,
emotes V3 y crónica completa (el servidor publica eventos de la ronda vigente).
No se modificó `ios/` ni se cerró la paridad visual de iOS. Claude puede revisar
esta entrega y los cambios semánticos del resultado para su adaptador.
