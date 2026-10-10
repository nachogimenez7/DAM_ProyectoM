# Mesa habitual V3: ventanas y pruebas Android

Continuación del 8/10. Android sigue siendo la prioridad. Esta entrega adapta la
presentación existente; no modifica el motor del servidor ni añade un arbitraje
local. Los cambios y mediciones anteriores están en AVANCE_ONLINE_ANDROID_2026-10-08.md
y MEDICION_V3_BETA_ANDROID.md.

## Cambios

- Desempate: misma ventana, marco según mapa, cartas y retratos cacheados del juego.
  Selección y confirmación envían una intención V3; se deshabilitan durante envío,
  pérdida de sincronía y vencimiento. Cerrar/abrir chat no repite el sonido.
- Segundo empate: todos ven la ventana de última palabra del Alcalde durante el
  plazo publicado. La presentación pública no consulta su rol ni su incapacidad
  secreta. Solo su proyección autorizada puede habilitar revelar/decidir.
- Contrapunto: ilustración, nombres y sonido existentes; dos participantes pueden
  escribir, el resto lee. El Payador señala con una intención, no con GameEngine.
- Recuento y resultado: VoteResultAnimator tiene una entrada para totales V3. Usa
  votosTotales y expulsadoDia ya publicados; no fabrica identidades de votantes ni
  revela roles privados. Los totales incluyen el peso del Alcalde calculado en el
  servidor. Continuar cierra la presentación sin cambiar la fase.
- Parser Android: acepta los totales como objeto o lista numérica de RTDB, valida
  cantidades/órdenes y rechaza un recuento filtrado durante una votación abierta.
- Reloj: evita reasignar texto idéntico cada medio segundo. El plazo continúa aunque
  haya una ilustración abierta; al llegar a cero espera publicación del servidor.
- Recuperación QA: ServerGameSmokeActivity permite entrar como otro participante
  de una sala existente, exclusivamente en Debug con Firebase emulado traidores-local.
  No incorpora credenciales, puertas QA ni tokens sintéticos en Release.

Estas ventanas usan los tres listeners existentes y perfiles precargados. No
añaden listeners, lecturas de perfiles en Firestore ni nuevas callables. Los
retratos usan las identidades precargadas y la caché habitual del juego.
No se cambiaron los archivos iOS de Claude.

## Pruebas

- 722 unitarias Android, cero fallos. APK QA compilada.
- 55 proyecciones generadas por el motor Node, interpretadas por el contrato Android.
  Pruebas nuevas: incapacidad secreta del Alcalde, expiración/objetivo de fase vieja,
  presentación pública del Contrapunto y recuento agregado con voto doble.
- Recorrido nativo dirigido en AVD con Auth, Functions, Firestore y RTDB aislados:
  Desertor inicial y revisión estando silenciado; voto de desempate; Alcalde
  silenciado sin acciones y Alcalde capaz que revela/decide; Contrapunto abierto
  por dos acciones, chat real permitido/denegado y señalamiento; jaula del silencio;
  Oráculo con voz pero sin voto; recuento cuyo botón no altera phaseIndex.
- Capturas inspeccionadas de Desertor, desempate, Contrapunto y recuento.
  Las fases dirigidas se preparan como fixtures Admin exclusivamente en emuladores;
  los toques viajan por el SDK Android y las callables al motor real. Este ensayo
  no equivale a una partida humana ni a una medición de capacidad en Cloud.
- Recorrido general nativo completo aprobado tanto en AVD como en el Samsung A56:
  entrada desde lobby, Auth/callable/rol privado, acción confirmada, cierre de app
  y avance por Tasks, reconexión, chat RTDB, resultado, archivo por cuenta y revancha
  sin chat anterior. APK separada com.traidores.juego.v3qa instalada en el A56;
  la aplicación habitual se conserva. Logs: output/server-v3-native-full-qa.log y
  output/server-v3-a56-qa.log; capturas del teléfono: output/server-v3-a56-*.png.
  El A56 usó Firebase emulado en la Mac; no fue una prueba contra producción.
  Se descartó el intento iniciado antes de completar la instalación inalámbrica.
  El capturador rápido requirió esperar el final de la animación de entrada antes
  de tocar el botón: usar coordenadas anteriores no comprobaba una acción real.

Reproducción del recorrido dirigido, después de compilar e instalar la APK QA:

```sh
TRAIDORES_QA_ADB=/Users/ignaciogimenez/Library/Android/sdk/platform-tools/adb \
TRAIDORES_QA_DEVICE=emulator-5554 \
node scripts/with-jdk.cjs node_modules/.bin/firebase emulators:exec \
  --config firebase.authority-qa.json --project traidores-local \
  --only auth,firestore,database,functions \
  '/Users/ignaciogimenez/.local/bin/node scripts/test-server-table-android.cjs'
```

El script elimina sus usuarios/sala/RTDB/limitadores y restaura el gate emulado.
WindowDump.java es una utilidad de pruebas fuera de la app: lee accesibilidad sin
esperar que el reloj quede inmóvil, y evita reutilizar un XML viejo si falla un dump.
No se incluye en el APK. Los emuladores se cierran al terminar.

## Lo que aún falta antes de abrir V3

1. Aprobación visual del usuario en A56 y partidas con personas. El recorrido de
   QA automatizado no mide comprensión, estrategia ni condiciones de red reales.
2. Cerrar la ceremonia de expulsión/Bufón y el audio de todas las fases; adaptar
   emotes con protocolo validado y definir crónica completa. El recuento agregado
   no reconstruye votos individuales que el servidor no publica. Los eventos V3
   actuales son de la ronda vigente: no llamarlos historial completo de la partida.
3. Desplegar la optimización de publicaciones previamente preparada, manteniendo
   acceso restringido; comparar bytes/latencia y varias salas simultáneas en Cloud.
4. Probar distribución firmada por Play, cuentas/invitados, fotos, cortes de red,
   reconexión y apertura gradual. Release sigue con SERVER_ONLINE_V3=false.

Esta continuación no desplegó Functions/reglas ni abrió el gate de Cloud. No se
hizo commit/push; se preservan los cambios anteriores de Android, backend e iOS.
La configuración temporal app/src/debug/google-services.json se eliminó. APK
conservada en output/android-v3/traidores-v3-a56-qa.apk. Se cerraron los emuladores
Firebase usados por estos recorridos y se eliminaron las salas/cuentas de prueba.
