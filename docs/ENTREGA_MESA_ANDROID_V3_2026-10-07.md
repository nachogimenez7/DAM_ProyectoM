# Estado de la adaptación Android V3 — 7 de octubre de 2026

## Alcance y corrección de rumbo

La prioridad es probar el online y medir su consumo para la beta Android. V3
traslada las decisiones de partida al servidor. Esto requiere conectar el cliente
a sus proyecciones; medir el backend, por sí solo, no requiere rehacer la mesa.

Se añadió un controlador V3 separado que reutiliza el XML y recursos del juego.
La primera presentación fue incompleta: panel inferior estrecho, tamaños distintos,
mensajes técnicos y falta de algunas ventanas habituales. El usuario la rechazó
en el A56. Se corrigió parte de la distribución usando los tamaños compartidos,
pero NO está aprobada ni constituye el producto final. El trabajo de presentación
se pausa mientras se decide si priorizar la medición o completar esta adaptación.

El flujo habitual sigue disponible. La aplicación de pruebas tiene otro package,
com.traidores.juego.v3qa, y no reemplaza el juego instalado en el teléfono.
Release mantiene SERVER_ONLINE_V3=false. No se abrió V3 globalmente ni se modificó
el enforcement de App Check en Cloud en este bloque.

## Implementación disponible

- ServerGameplayActivity conserva Auth, callables, tres proyecciones RTDB,
  confirmaciones con requestId, reloj del servidor y recuperación acotada.
- ServerGameTableRenderer usa activity_gameplay_mock y gameplay_table_section:
  cartas, retratos, rol privado, elección de objetivos, chat, amanecer y resultados.
  Usa los animadores existentes de rol, muerte, amanecer sin víctimas y victoria.
- ServerGameTablePresentation convierte solo los roles autorizados en modelos
  de arte; las revelaciones públicas nunca usan roles privados de aliados.
- La selección de objetivo y los diálogos vuelven a comprobar fase, plazo y
  acciones permitidas antes de enviar. El cliente no ejecuta GameEngine.
- El cursor de presentación se guarda por UID y matchId; los eventos se consumen
  por seq. La reconexión reconstruye la mesa sin repetir anuncios anteriores.
- El chat RTDB se observa cuando se abre el panel y se detiene al cerrarlo o
  suspender la app. Las fotos usan los perfiles ya obtenidos en el lobby.
- La revancha solicita prepararRevanchaV3; salir solicita abandonarPartidaV3.
  Salir antes del resultado sigue contando como derrota, vivo o muerto.

Tras la observación del usuario se recuperaron el ancho de la tarjeta inferior,
las métricas adaptativas compartidas de las cartas, el panel EVENTOS y los textos
propios de la partida. Una acción con objetivos se puede seleccionar directamente
desde las cartas. El resultado identifica sus anuncios como los de la ronda;
no presenta esa lista parcial como una crónica completa.

## Pruebas y evidencia

- Compilación Debug QA aprobada; suite Android: 716 pruebas, cero fallos.
- El ensayo nativo anterior verificó Auth, inicio callable, rol/acción privados,
  avance de Tasks con la app cerrada, reconexión, envío de chat mediante la UI,
  resultado archivado por cuenta y revancha con limpieza del chat.
  Ese mismo recorrido se volvió a ejecutar con las correcciones de distribución:
  aprobado en la AVD. La compilación Debug habitual también terminó correctamente.
- En el A56 se restauraron cuatro túneles ADB tras perder la conexión inalámbrica.
  Después inició la partida, recibió rol, avanzó por noche/debate y llegó al
  resultado de la ronda 2. La presentación visible fue rechazada por el usuario.
- El A56 utiliza Firebase Emulator Suite en la Mac y cuatro clientes automáticos
  autenticados. Son las reglas y Functions reales ejecutadas localmente; no es
  una medición de Cloud ni una partida entre cinco personas.
- El último APK QA se conserva en output/android-v3/traidores-v3-a56-qa.apk.
  La revisión visual corregida todavía no se instaló ni aprobó en el A56; se
  pausó la presentación después de la objeción del usuario.
  La configuración temporal app/src/debug/google-services.json se elimina tras
  compilar; el proyecto habitual conserva su configuración de Firebase.

## Pendientes reales

1. Acordar el siguiente bloque: medir V3, completar su presentación habitual o
   medir primero el online anterior. No mezclar los objetivos sin explicarlo.
2. Si se continúa la adaptación, integrar las presentaciones especiales de
   silencio, Oráculo, Contrapunto y desempate, además de audio, feedback y
   crónica; los controles y reglas de servidor no sustituyen esas presentaciones.
3. Las reacciones/emotes V3 necesitan un protocolo validado. El botón se ocultó
   en esta prueba para evitar escrituras del protocolo anterior.
4. Probar la mesa contra Cloud con allowlist y App Check; medir tráfico, lecturas,
   escrituras, funciones, Tasks, latencia y consumo en reposo. Las medidas de
   emuladores no constituyen una factura ni prueban capacidad en producción.
5. Probar con personas, red móvil/cortes y la versión firmada desde Play antes
   de decidir una apertura gradual de V3 para la beta.

## Trabajo paralelo

docs/BRIEF_CLAUDE_PACK_BETA_ANDROID.md propone trabajar en catálogo y cosméticos
del pack sin modificar el online. Anuncios, Play Billing, precios, derechos de
compra y recursos finales siguen como bloques separados. No se implementaron
anuncios ni microtransacciones en esta entrega.

No se hizo commit, push ni despliegue nuevo en Cloud en este bloque.
