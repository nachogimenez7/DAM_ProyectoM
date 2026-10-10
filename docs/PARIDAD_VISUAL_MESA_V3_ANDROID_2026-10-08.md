# Mesa habitual Android conectada a V3 — 8/10/2026

Entrega del encargo `BRIEF_CODEX_PARIDAD_VISUAL_MESA_V3.md`. Se trabajó con el
emulador Pixel 10, por elección del usuario. La aprobación visual en el A56 sigue
pendiente; esta entrega no habilita V3 para la beta publicada ni despliega Cloud.

## Qué cambió y por qué

El problema principal era que V3 reutilizaba el XML, pero dibujaba otra mesa y otro
chat. Ahora se comparten las funciones de dibujo reales, con una fuente de datos
V3 separada de la autoridad del gameplay anterior.

- `GameplayPlayerColumns`: extracción de `renderPlayerColumns`, layout adaptativo,
  fábrica de cartas, animación de muerte, apariencia, volteo público y etiquetas de
  acción y estilo del botón principal del gameplay habitual. `GameplayMockActivity` y `ServerGameTableRenderer`
  llaman a este mismo componente. El gameplay habitual conserva su política de
  selección, pulsos y acciones; V3 valida las intenciones mediante su contrato.
- `GameplayChatController.ChatDataSource`: costura opcional para canales permitidos,
  envío y selección. La mesa habitual conserva su transporte. V3 delega en
  `ServerGameChat`; los listeners del protocolo anterior y las reacciones quedan
  bloqueados en esta modalidad. Se reutilizan el feed central, crónica, recuadro de
  ronda, mensajes con colores, pestañas, respuestas rápidas, avisos y panel ampliado.
- `ServerGameSessionAdapter`: construye un modelo nuevo de presentación desde la
  proyección autorizada. No copia roles, votos, historial de acciones ni objetivos
  de la identidad del lobby. No resuelve partidas. Público, privado y permisos
  siguen siendo la fuente de verdad; los ayudantes de dibujo leen el modelo.
- `ServerGameTableRenderer`: retirados el chat plano, la barra EVENTOS y la hoja
  propia de 360 dp. Se restauran título habitual, barra de progreso, CARTA OCULTA,
  VER CARTA y CONTINUAR. Se retienen las ceremonias y la protección visual contra
  anticipos de muerte, rol y Bufón. Las cartas se identifican por UID, incluso si
  los alias son iguales; el emparejamiento usa identidad de objeto, no igualdad de
  `GamePlayer`.
- El chat se inicializa después de la primera proyección: la recuperación puede
  traer una identidad de lobby sin jugadores. La prueba dirigida detectó ese cierre
  y se corrigió sin inventar jugadores ni roles para arrancar la interfaz.
- Expulsión completa desde 6.200 ms restantes, modo comprimido/estático según plazo
  y evento visto. El vencimiento corta la lectura final y no redibuja STATIC después
  del impacto. Debug registra modo y tiempo restante.

No se editó iOS ni sources en este bloque. Se conservaron los cambios anteriores
no confirmados de backend y de Claude. No se hicieron commits ni push en esta entrega.

## Autoridad y consumo

No se agregaron listeners de Firestore ni callables para la paridad visual. Se
mantienen público, privado y permisos en RTDB, más la consulta existente del canal
seleccionado (últimos 60 mensajes; anillo de 16 por jugador/canal). Cambiar de canal
retira la suscripción anterior; salir del primer plano la detiene. Se agregó el
contador local `v3_chat_suscripcion` para comprobar reconexiones/cambios de canal.

Hay una diferencia de consumo que no debe ocultarse: ahora el canal seleccionado
se escucha también con el chat cerrado para alimentar el feed central, como en la
mesa habitual. Sigue siendo una única consulta de chat, pero puede descargar más
bytes de RTDB que la versión V3 que solo escuchaba con el panel abierto. El envío
conserva el transporte y los límites existentes, sin escritura Firestore adicional.
Esto requiere repetir la medición de descargas en Cloud; las capturas y los tests
locales no equivalen a una factura ni a una prueba de saturación.

CONTINUAR puede permanecer deshabilitado mientras el servidor resuelve la fase.
Cerrar ventanas y animaciones no avanza el motor ni detiene deadlines.
El servidor valida acciones y chat. El modelo compartido no hereda votos individuales.
Las reacciones/emotes permanecen ocultos hasta contar con protocolo V3 validado.

## Evidencia visual reproducible

`GameplayParityActivity` existe solo en Debug. Permite comparar la mesa habitual
real y el renderer V3 con ocho jugadores, Pampa, ronda 2 y el mismo reparto visible.
No inicia partidas ni escribe Firebase. El script
`scripts/capture-gameplay-parity-android.py` captura siete pares y sus jerarquías:

| Par | Qué se revisa | Diferencia que debe distinguirse de un fallo visual |
| --- | --- | --- |
| noche | cartas, centro, encabezado, panel propio | V3 reúne los poderes en una noche simultánea; el fixture común es la fase Médica. |
| amanecer | centro y cartas durante fase sin acción | Las ceremonias con eventos nuevos se verifican aparte en Firebase emulado. |
| debate cerrado | crónica, colores, entrada compacta | Anuncio público real de V3; la mesa común puede mostrar su objetivo local en el encabezado. |
| debate abierto | panel, pestañas, mensajes y composición | Sin emotes/reacciones V3. |
| votación | cartas seleccionables y etiquetas habituales | V3 requiere confirmación de la intención; no expone votantes individuales. |
| recuento | crónica y panel propio | Sin votos en el fixture visual; el agregado real se prueba en la corrida dirigida. |
| resultado | centro, encabezado y panel | RESULTADO V3 puede ser un resultado diario sin ganador; el chat lo nombra como tal. |

Carpetas `output/paridad-visual/antes/` y `despues/`. Las capturas de antes son del
fixture inicial con controles V3 vacíos; las finales usan las opciones reales de
`ServerGameActionPolicy`. No se presentan como prueba del transporte. El reloj de
este fixture tiene 180 s en V3; el gameplay común utiliza su configuración habitual.
La fracción de la barra V3 se calcula desde el plazo recibido al entrar en la fase:
al reingresar puede empezar llena con el tiempo restante, aunque el contador y el
vencimiento conservan el deadline autoritativo. Esa precisión de la barra queda
como mejora menor; no afecta quién puede actuar ni cuándo vence la fase.

Se inspeccionaron imágenes del centro y chat ampliado y se contrastó la mesa común
antes/después. Las diferencias principales de estructura (centro vacío, chat plano,
cartas y panel propio de otros tamaños) quedan sustituidas por componentes compartidos.
La jerarquía de los siete pares confirmó límites coincidentes para el feed central,
columnas y panel inferior (`output/paridad-visual/bounds.json`). El recuadro y el
rótulo central también coinciden salvo en RESULTADO: el texto diario V3 ocupa una
línea menos que «FIN DE LA PARTIDA» de la mesa común del fixture (29 px menos de
alto). Es la misma función de dibujo adaptándose al texto, no otro layout. El título
superior ocupa el espacio que deja el botón de emotes oculto. La aceptación final
de todos los pares en el teléfono pertenece al usuario.

## Verificación y límites

- Android: 731 unitarias aprobadas, cero fallos; APK Debug compilada.
- `compileReleaseKotlin` aprobado, con las opciones por defecto (V3 público apagado).
- Partida nativa contra Firebase emulado: Auth, inicio callable, proyecciones y acción
  privada; Tasks avanzando con la app cerrada; reingreso y chat RTDB real; resultado,
  historial por cuenta y revancha limpia. La corrida final sobre la APK compartida
  aprobó después de esperar la estabilización del teclado en el script nativo. Logs `output/paridad-visual/native.log` y `native-final.log`.
- Corrida nativa dirigida completa aprobada (15 participantes): elección inicial y
  reconsideración del Desertor silenciado, desempate, recuento agregado, Alcalde
  silenciado/activo, Contrapunto con chat real y espectador de solo lectura, jaula
  de silencio, voz del Oráculo sin voto, amanecer de 4 s, expulsión de 8 s sin
  anticipos, Bufón de 12 s y reconexión estática durante RESULTADO. Log
  `output/paridad-visual/windows.log`. Cubre recuperación con identidad vacía.
- Logs de compilación: `output/paridad-visual/build.log`, `build-final.log` y `release.log`.
- APK aislada: `output/traidores-v3-mesa-compartida.apk`; solo para emuladores locales,
  no es una APK para beta ni para Firebase real. Se retiró la configuración
  Debug temporal después de compilar; la configuración real del proyecto se conserva.

## Próximo paso y revisión de Claude

Claude puede revisar, sin modificar iOS ni backend por este encargo:

1. Que el adaptador no invente información privada ni conserve estado de otra partida.
2. Que la costura del chat no active listeners antiguos, escriba `GameSession` ni
   habilite canales fuera de los permisos recibidos.
3. Que los pares de imágenes usen efectivamente las mismas funciones de dibujo,
   y que cualquier diferencia restante esté explicitada.

Después: instalar una build preparada para Firebase real en el A56, jugar y grabar
una ronda completa con plazos reales, obtener la aprobación visual del usuario,
desplegar el bloque de Functions/reglas pendiente mediante el procedimiento de
activación y repetir medición de 5/10/15 jugadores. No activar la beta antes de
esa aprobación. La publicidad, los cosméticos y la monetización siguen en otro bloque.
