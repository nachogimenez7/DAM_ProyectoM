# Segundo despliegue de fluidez y práctica A56 — 9/10/2026

## Resultado y criterio de aprobación

**Despliegue completado; experiencia de juego NO aprobada por el usuario.** Se ejecutó el encargo de Claude con la APK indicada, una persona en el A56 y cuatro bots SDK autenticados. El usuario aclaró que no dispone del segundo teléfono. La partida completó dos rondas, incluyendo empate y segunda votación; ganó Pueblo. El usuario era Asesino: historial `contabilizada:true`, `won:false`, coherente con el resultado.

Los tiempos y el avance del motor mejoraron respecto de la práctica anterior, pero el usuario informó pérdidas importantes de paridad visual y funcional. Su evaluación manda sobre las pruebas unitarias: no considerar esto listo para beta. El alcance de esta entrega es despliegue, práctica, medición y documentación; las correcciones de presentación listadas abajo siguen pendientes.

## Despliegue verificado

Las ocho Functions están ACTIVE con la actualización de esta entrega:

- Santiago: `iniciarPartidaV3`, `accionPartidaV3`, `recuperarFaseV3`, `prepararRevanchaV3`, `abandonarPartidaV3`, `publicarPartidaV3`.
- São Paulo: `resolverFaseV3`, `repararPartidasV3`.

Se conservó `accionPartidaV3` con mínimo 1, máximo 4, CPU 1, memoria 256 MiB y concurrencia 20; `resolverFaseV3` con mínimo 1, máximo 2 y memoria 256 MiB. Invoker del worker únicamente `serviceAccount:99323018581-compute@developer.gserviceaccount.com`, sin acceso público. Cola RUNNING. Recuperación con mínimo 0, máximo 1 y concurrencia 1.

Código entregado por Claude: margen de Tasks 250 ms y espera dentro del worker cuando llega hasta 3 s temprano, con un único segundo intento. No se cambió una regla de juego en este despliegue. Se reejecutaron 52 pruebas de núcleo, endpoints, entrega y recuperación: 52 aprobadas. Las 741 pruebas Android son las reportadas por Claude; no se recompiló ni se volvió a correr ese conjunto en esta entrega.

Antes y después del despliegue ambos controles estaban cerrados. Para el inicio manual se habilitó únicamente el UID temporal del anfitrión y esta sala privada; se restauró el gate inmediatamente después del inicio. `config/onlineV3` permaneció apagado todo el tiempo y App Check siguió exigido.

Evidencias: [log de despliegue](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/2026-10-09-claude-deploy-2.log) y [verificación de las ocho Functions e IAM](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/2026-10-09-claude-deploy-2-verified.json).

## Práctica real

APK `output/traidores-v3-cloud-practica-claude-2.apk`, paquete aislado Debug `com.traidores.juego.v3qa`. SHA-256: `9c843b1808d8e65bfb7845126fcc50515c9709daf9f7a21802b5aa5b2b092051`.

Sala `qa-v3-interactive-1791525904722`; partida `288d7339-d126-4a15-aafb-4a979a03c79f`. Comenzó aproximadamente 03:05 y terminó 03:14, hora Argentina. Bots medidos durante 506 s. Preparación y limpieza usaron Admin solo para los datos temporales; acciones, avance, plazos y publicación pasaron por Functions y clientes reales, sin forzar el reloj ni los roles. El usuario pulsó INICIAR y jugó manualmente.

Se observaron REPARTO, NOCHE, AMANECER, DIA_DEBATE, VOTACION, RECUENTO_VOTOS, RESULTADO, DESEMPATE_VOTACION y FINALIZADA. Esta partida no verifica todos los roles, dos humanos, recuperación, apagado con versión antigua ni carga concurrente.

## Acciones humanas: toque → confirmación

| Acción | Índice de fase | Tiempo |
|---|---:|---:|
| role_ack | 0 | 946 ms |
| matar | 1 | 955 ms |
| votar | 4 | 967 ms |
| matar | 7 | 936 ms |
| votar | 10 | 790 ms |
| votar | 12 | 680 ms |

Estos tiempos enlazan `v3_action_submit` y `v3_action_confirmed` en el mismo A56. Las dos acciones nocturnas quedan medidas. Son seis muestras, no un percentil representativo de una beta.

El cartel «VÍCTIMA ELEGIDA» se capturó mientras el estado de la mesa todavía decía «Enviando…»: la respuesta visual ya no espera al recibo. Las capturas se pidieron por log y llegaron por adb inalámbrico; sus tiempos no equivalen al primer píxel mostrado.

Los cuatro bots emitieron 15 acciones, todas aceptadas. Recibo p50 1.922 ms, p95/máximo 3.015 ms; proyección p50 1.742 ms, máximo 3.053 ms. Se registraron 21 operaciones de acción Cloud (15 bots + 6 humanas); nueve necesitaron dos intentos de transacción. No se observó fallo de publicación inline en la sala.

## Vencimiento → primer render de la siguiente fase

| Ronda | Transición | Tiempo |
|---:|---|---:|
| 1 | REPARTO → NOCHE | 3.862 ms |
| 1 | NOCHE → AMANECER | 1.917 ms |
| 1 | AMANECER → DIA_DEBATE | 1.549 ms |
| 1 | DIA_DEBATE → VOTACION | 1.959 ms |
| 1 | VOTACION → RECUENTO_VOTOS | 2.011 ms |
| 1 | RECUENTO_VOTOS → RESULTADO | 1.662 ms |
| 1 | RESULTADO → NOCHE | 1.949 ms |
| 2 | NOCHE → AMANECER | 2.034 ms |
| 2 | AMANECER → DIA_DEBATE | 1.645 ms |
| 2 | DIA_DEBATE → VOTACION | 1.940 ms |
| 2 | VOTACION → RECUENTO_VOTOS | 1.830 ms |
| 2 | RECUENTO_VOTOS → DESEMPATE_VOTACION | 1.737 ms |
| 2 | DESEMPATE_VOTACION → RECUENTO_VOTOS | 1.870 ms |
| 2 | RECUENTO_VOTOS → RESULTADO | 1.672 ms |
| 2 | RESULTADO → FINALIZADA | 1.742 ms |

`v3_render` marca recepción/render del estado, no el final de la animación. Se comparan el deadline del servidor y el reloj de pared Android; antes de jugar los relojes de Mac y A56 coincidían al muestrearlos con resolución de un segundo, pero esto no certifica su sincronización con el servidor.

El primer cambio tardó 3.862 ms; los demás, 1.549–2.034 ms. En la práctica anterior se midieron 4.970 ms en el primer cambio y 3.078–3.506 ms en los siguientes. Son dos partidas distintas; el resultado no aísla cada factor ni garantiza esa latencia bajo carga.

El worker registró 15 operaciones de deadline de esta sala, cada una con un intento de transacción. Duración interna 1.158–3.151 ms. No aparecieron errores `online_v3_operation_failed` ni `unavailable` en la ventana del worker. No se forzó una entrega temprana en Cloud, por lo que esta partida demuestra ausencia del problema durante la práctica, no todas las condiciones de carrera. Hubo dos o tres intentos de entrega RTDB por publicación; esa contención sigue siendo un punto de optimización distinto del backoff de Tasks.

Evidencias: [tiempos Android](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/android-timings.json), [log Android](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/android-v3-events.log), [operaciones del worker](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/worker-operations.json).

## Consumo y carga observados

Ventana Cloud consultada: 06:05:05–06:16:00 UTC, incluyendo preparación, partida y limpieza. Se recolectó nuevamente después de la primera consulta porque las métricas llegan con retraso. Estos son **contadores parciales de todo el proyecto**, no una factura ni operaciones atribuibles exclusivamente a esta partida.

| Indicador de proyecto | Valor observado |
|---|---:|
| Lecturas Firestore | 476 |
| Escrituras Firestore | 197 |
| Bytes enviados RTDB, con protocolo/cifrado | 1.362.064 |
| Bytes de payload RTDB | 985.540 |
| Solicitudes Cloud Run | 137 |

RTDB: pico 10 conexiones y pico del indicador de carga aproximadamente **0,49 %**, sumando los tipos de operación por muestra. El descriptor oficial de Monitoring define la métrica como fracción de carga agrupada por tipo. Es un valor bajo durante esta práctica; no mide capacidad máxima, no describe toda la cadena Firestore/Functions y no permite extrapolar a muchas salas simultáneas.

Contador local del A56 en 511 s: 105.216 bytes recibidos y 25.558 enviados. Incluye todos los servicios de la aplicación, no solo RTDB ni necesariamente bytes facturados. Los cuatro bots recibieron callbacks JSON con 100.256 bytes públicos, 20.240 privados, 12.599 de permisos y 36.460 de chat; tampoco son tráfico facturado. Se enviaron ocho mensajes de bots y dos del A56, además de emotes.

Monitoring registró aproximadamente 1.369 segundos de instancia facturable agregados para Cloud Run en la ventana. No son segundos de CPU de una sola partida ni una cotización en USD. Las dos instancias mínimas siguen configuradas como autorizó el usuario; cerrar el gate no elimina su coste de permanencia. No se afirma cuánto cuesta esta partida ni cuánto más cuesta V3 que el protocolo anterior: falta una comparación controlada y una estimación regional completa.

Evidencias: [métricas Cloud](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/cloud-metrics.json), [contador del A56](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/android-metrics.txt), [descriptor de carga](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/load-metric-description.json).

## Capturas solicitadas

Son muestras reales del A56 tomadas durante la partida. Las primeras capturas de rol y victoria pueden coincidir con la entrada animada y no prueban que todo su contenido esté listo en ese instante. La captura manual posterior del resultado resultó negra; no se usa como evidencia visual de la ventana.

| Momento | Archivo |
|---|---|
| Entrada al reparto habitual | [reparto](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/captures/reparto-1.png) |
| Primera mesa / ventana de rol entrando | [mesa inicial](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/captures/mesa-inicial-1.png) |
| Cartel de acción nocturna mientras envía | [víctima elegida](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/captures/confirmacion-inmediata-2.png) |
| Transición de amanecer | [amanecer](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/captures/amanecer-transicion-1.png) |
| Mesa del día 2, chat y anuncio nocturno | [debate](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/captures/debate-ronda-2.png) |
| Ventana final entrando | [resultado](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/captures/resultado-1.png) |

## Evaluación del usuario: checklist abierto para Claude y Codex

El usuario acepta la demora propia de una partida online; su rechazo principal es la pérdida de la mesa habitual. Declaró funcionando los emotes, la ventana de ganador y volver a la sala. Estas apreciaciones son evaluación manual del usuario, no pruebas automatizadas de todos sus casos.

Pendientes comunicados, **ninguno se marca corregido en esta entrega**:

- [ ] Eliminar el flash de «Preparando partida» al entrar, manteniendo un arranque coherente.
- [ ] Restaurar el acceso al perfil de los demás jugadores.
- [ ] Recuperar ritmo y duración habituales de los cambios de fase y día/noche.
- [ ] Presentar los anuncios con su ventana y decoración, no solo texto.
- [ ] Restaurar «Saltar discusión para votar», con validación y avance a cargo del servidor.
- [ ] Mantener abierto el chat después de enviar un mensaje.
- [ ] Mantener visible «Votaste a: …» tras enviar y confirmar.
- [ ] Mantener el contorno de la carta votada.
- [ ] Mostrar quién votó a quién y los votos por jugador según la configuración del online habitual.
- [ ] Revisar las opciones «Anuncios» y «Mis investigaciones»; el usuario no entiende su función o incorporación.

[Feedback completo registrado](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/user-feedback.json).

## Propuesta de siguiente bloque, sin iniciar otra reescritura

1. Claude revisa la mesa habitual y la V3 **por esta lista**, y entrega el componente común, el comportamiento esperado y el lugar donde se perdió la conexión. Reutilizar las ventanas, controles y estados comunes ya existentes. No crear otra mesa ni agregar menús para suplir comportamientos que deben verse en la propia mesa.
2. Codex conecta los componentes de presentación a la proyección y las intenciones V3. Separar las correcciones de UI de cambios reales de contrato: saltar debate y votos individuales necesitan revisar también autoridad, permisos y qué datos se publican.
3. Comprobar primero en emuladores cada pendiente, incluidos envío de chat y persistencia del voto. Preparar evidencia de que cada casilla está corregida antes de volver a pedir al usuario una práctica completa. Las pruebas de reglas ya existentes siguen siendo útiles; repetir regresiones necesarias sin pedir al usuario que reproduzca todo manualmente.
4. Nueva práctica breve A56 centrada en esta lista. Mantener pendiente una partida con varios humanos, carga concurrente, comparación controlada de consumo y las pruebas de activación/apagado antes de beta.

Puntos de código observados, **candidatos de revisión y no diagnósticos confirmados de todos los síntomas**:

- `ServerGameSessionAdapter` fija `showIndividualVotes=false`; el renderer también lo fuerza en el resultado. La ausencia de votos individuales no se resuelve solo dibujando una etiqueta: hay que alinear la proyección y el comportamiento común.
- El renderer ya contiene «Votaste a: …» y selección confirmada, pero el usuario no los vio persistir. Reproducir sus cambios de estado y el contorno en VOTACION y DESEMPATE_VOTACION; no asumir que la existencia del código prueba el resultado.
- `bindPlayer` habilita la carta solo si es objetivo de una acción y su click se usa para seleccionar objetivo. Revisar cómo conservar también la interacción habitual del perfil sin interferir con votar.
- `showRole` y las transiciones ya reutilizan elementos comunes. Aun así, falta validar su secuencia y permanencia visual con las capturas y el feedback, no únicamente con la presencia de clases compartidas.

Mover la autoridad al servidor busca que la partida avance con el anfitrión desconectado y que el cliente no pueda decidir roles o resultados. Esa ventaja no exige cambiar la experiencia visual. También traslada al servidor trabajo que antes hacía el teléfono; el consumo debe seguir midiéndose y optimizándose. Esta práctica muestra carga RTDB baja, no una garantía de coste o ausencia de saturación futura.

## Cierre y limpieza

`cleanupErrors:[]`. Verificación independiente: sala Firestore ausente, rama RTDB ausente, cero perfiles y cuentas temporales restantes, token Debug creado para la práctica eliminado y cero tareas propias pendientes (15 IDs comprobados). Aplicación de prueba detenida.

Estado final confirmado:

- `onlineMaintenance/serverAuthority`: `enabled:false`, `allowedHostUids:[]`, `allowedRoomIds:[]`.
- `config/onlineV3`: `enabled:false`, `minVersionCode:52`.

Se conservaron instancias mínimas e invoker privado. No se abrió beta, no se publicó en Play, no se hizo commit/push ni se modificó iOS. La única modificación adicional de código en este bloque fue al controlador QA para verificar el rollout cerrado y capturar pantallas automáticamente; se desplegó el backend existente entregado por Claude.

Evidencias: [limpieza y gates](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/cleanup-verified.json), [tareas propias](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/queue-cleanup-verified.json), [registro completo de práctica](/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM/output/v3-cloud/qa-v3-interactive-1791525904722/evidence.json).
