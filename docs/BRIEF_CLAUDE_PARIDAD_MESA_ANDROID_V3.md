# Revisión y plan de paridad de la mesa Android V3

Fecha: 8/10/2026. Pedido del usuario: Claude revisa y planifica; Codex implementa
y verifica. Prioridad: el online Android debe verse y sentirse como la partida
habitual, con autoridad del servidor, para preparar la beta abierta.

## Reparto de trabajo

- Claude: revisión de solo lectura del código, recursos y capturas; propuesta
  concreta de integración y criterios de aceptación. Escribir únicamente
  `docs/REVISION_PARIDAD_MESA_ANDROID_V3.md`, sin editar código, recursos,
  configuración, backend ni los documentos de avance de Codex. No hacer commit.
- Codex: implementación Android, cualquier ajuste necesario del contrato/backend,
  pruebas automatizadas, pruebas nativas y mediciones. Mantener los cambios iOS
  existentes y el arbitraje del servidor.
- Si una decisión cambia reglas, privacidad, fases o plazos, marcarla explícitamente.
  Las correcciones de apariencia que reutilizan lo existente no requieren nuevas
  decisiones del usuario. Una propuesta de Claude no cuenta como cambio aplicado.

## Referencias actuales

Leer primero:

1. `docs/VENTANAS_V3_ANDROID_2026-10-08.md`: entrega y límites de las pruebas.
2. `docs/CONTRATO_AUTORIDAD_SERVIDOR_V3.md`: reglas y proyecciones autorizadas.
3. `docs/PLAN_BETA_ABIERTA_ANDROID.md` y `docs/MEDICION_V3_BETA_ANDROID.md`.

Mesa habitual y componentes reutilizables:

- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt`: pese al nombre,
  es la referencia de presentación del gameplay existente. Distinguir las rutas
  locales de las rutas del online anterior y sus diferencias de información.
- `app/src/main/res/layout/activity_gameplay_mock.xml` y los recursos de cada mapa.
- `VoteResultAnimator.kt`, `GameplayTableUi.kt`, `GameplayPhasePresentation.kt`,
  `GameplayAudioDirector.kt`, `GameplaySoundResolver.kt`, `ChronicleFeedPresenter.kt`.
- Identificar los animadores existentes de muerte, amanecer, silencio, Oráculo,
  Payador y victoria del Bufón, antes de proponer componentes nuevos.

Implementación V3 actual, bajo el mismo paquete:

- `ServerGameplayActivity.kt`: sincronización, acciones, recuperación y ciclo de vida.
- `ServerGameTableRenderer.kt`: adapta la mesa y ventanas existentes.
- `ServerGameTablePresentation.kt`: políticas de presentación.
- `ServerGameContract.kt`: DTO, acciones y permisos.
- `functions/src/onlineGameCore.js`: leer resolución y proyección; no editar.

Capturas disponibles: `output/server-v3-table-*.png` y
`output/server-v3-a56-*.png`. Distinguir captura inspeccionada de inferencia por
lectura. Si falta una captura necesaria, indicar cuál para que Codex la produzca.

## Qué está hecho y qué no queda certificado

Se reutilizan mesa, cartas, retratos cacheados, marcos por mapa, reparto, amanecer,
silencio, Oráculo, Contrapunto, desempate, decisión del Alcalde, recuento agregado
y resultado. Hay 722 pruebas unitarias Android y recorridos nativos aprobados
en AVD y A56 contra emuladores Firebase. Esto no certifica paridad visual completa,
una partida humana de varias rondas ni capacidad de varias salas en Cloud.

Pendientes conocidos: ceremonia de expulsión/Bufón, audio de todas las fases,
pulido del HUD y anuncios; emotes y crónica completa requieren definir su alcance
y datos. La APK QA separada no es la presentación final aprobada por el usuario.

## Punto prioritario: expulsión y fin de ronda

En el árbol actual, `resolveResult` marca al expulsado, emite `DAY_EXPULSION` y,
si no hay ganador, incrementa la ronda e inicia la noche. `projectServerGame`
filtra eventos a la ronda vigente. Por otra parte, `revealEvents` de Android
espera `DAY_EXPULSION` en `RESULTADO`. Revisar también los caminos con ganador,
Bufón y reconsideración del Desertor, y confirmar el problema con referencias.

Proponer la solución más pequeña que permita la ceremonia habitual sin anunciar
una eliminación antes de confirmación, revelar roles secretos, perder eventos,
repetir animaciones al reconectar ni tapar una acción mientras vence su plazo.
Comparar brevemente adaptación cliente frente a ajuste del contrato/fase si hace
falta. Indicar efectos sobre plazos, reconexión, bytes y publicaciones; no elegir
una reestructuración grande sin justificarla. No ejecutar GameEngine como árbitro
en salas V3 ni detener el reloj del servidor desde una animación.

## Entrega solicitada

Una revisión breve y accionable, con:

1. Matriz por fase: referencia habitual, diferencia V3 comprobada, componente
   existente a reutilizar, datos necesarios y criterio observable de aceptación.
2. Orden de implementación: primero transiciones/controles correctos; después
   presentación y sonido. Separar bloqueantes de pulido y funciones adicionales.
3. Propuesta concreta para expulsión/Bufón y reconexión durante la ceremonia.
4. Lista breve de pruebas de regresión: reparto, poderes y voto, muertes,
   desempates, silencio, Desertor, Bufón, final, abandono, reingreso y revancha.
   Indicar cuáles pueden automatizarse y cuáles necesitan observación visual.
5. Archivos y puntos de integración recomendados; señalar dudas o información
   faltante sin convertir hipótesis en defectos confirmados.

Mantener la información que V3 permite: recuentos agregados no autorizan inventar
votantes; incapacidad secreta del Alcalde no cambia la ventana pública; sonido y
animación deben deduplicarse por partida/fase/evento. No añadir listeners ni
consultas por dibujo o animación. Si se propone una publicación adicional, estimar
su frecuencia por partida y dejarla identificada para medición.

No rediseñar la identidad visual ni generar arte nuevo. El objetivo es recuperar
la experiencia habitual. El límite mínimo de esta revisión es presentación y
transiciones; publicidad, packs, tienda, bots e iOS quedan fuera de este encargo.
No modificar archivos bajo `sources/`. No desplegar, abrir V3 al público ni
desactivar App Check para esta revisión.
