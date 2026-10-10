# Despliegue y práctica Cloud en A56 — 9/10/2026

## Resultado

Se ejecutó el encargo `BRIEF_CODEX_DESPLIEGUE_Y_PRUEBA_A56_2026-10-09.md`. La partida completó dos rondas y escribió el historial, pero **el usuario rechazó la experiencia**: faltan anuncios correctamente presentados, las acciones tardan, el inicio aparece desordenado y después se recompone, y falta fluidez. No se aprueba la mesa ni la beta con esta prueba.

## Despliegue verificado

Actualizadas y ACTIVE `iniciarPartidaV3`, `accionPartidaV3`, `abandonarPartidaV3` en Santiago y `resolverFaseV3` en São Paulo. Publicación en la misma callable, con trigger como respaldo.

- `accionPartidaV3`: CPU 1, memoria 256 MiB, concurrencia 20, mínimo 1, máximo 4.
- `resolverFaseV3`: memoria 256 MiB, mínimo 1, máximo 2. Invocador únicamente `99323018581-compute@developer.gserviceaccount.com`; sin acceso público.
- Cola `resolverFaseV3`: RUNNING.
- 49 pruebas de núcleo, endpoints y publicación reejecutadas: aprobadas. Las 741 Android pertenecen a la entrega de Claude; Codex no las volvió a ejecutar en este bloque.

Firebase requirió `--force` para confirmar el aumento de facturación mínima previsto en el encargo. Las dos instancias mínimas continúan configuradas después de limpiar la práctica; cerrar el gate no elimina ese coste. La cifra aproximada de US$15/mes del brief no se confirmó con una cotización regional de la calculadora: el catálogo consultado venció por timeout. No tratarla como límite de gasto. Fuente de reglas de cobro: https://cloud.google.com/run/pricing.

Evidencias: `output/v3-cloud/2026-10-09-claude-deploy-confirmed.log`, `2026-10-09-claude-deploy-after.json` y `2026-10-09-claude-deploy-tests.log`.

## Práctica real

APK entregado por Claude instalado en el A56: `output/traidores-v3-cloud-practica-claude.apk`, paquete Debug aislado `com.traidores.juego.v3qa`, versión 52 / 0.1.51. Sala `qa-v3-interactive-1791523209347`, usuario como Aldeano y cuatro clientes SDK autenticados. Inicio manual por el usuario.

Recorrido registrado: REPARTO → NOCHE → AMANECER → DIA_DEBATE → VOTACION → RECUENTO_VOTOS → RESULTADO → NOCHE 2 → FINALIZADA. El A56 registró la última muerte antes de la ventana de victoria. Ganaron Traidores; historial del usuario `contabilizada:true`, `won:false`, coherente con su bando Pueblo.

Duración de la medición de bots: 312 segundos. Se aceptaron sus 13 acciones. El usuario emitió un voto en la ronda 1; no se midió una acción nocturna humana porque era Aldeano.

## Latencia observada

| Medición | Resultado |
|---|---:|
| Voto humano: toque → confirmación y proyección en A56 | 1.032 ms |
| Bots: mediana hasta recibo | 1.869 ms |
| Bots: peor recibo | 3.085 ms |
| Bots: mediana hasta proyección | 1.831 ms |
| Bots: peor proyección | 3.028 ms |
| Cierre inicial de REPARTO → primer render NOCHE | 4.970 ms |
| Otros vencimientos → primer render de fase siguiente | 3.078–3.506 ms |
| Fallos `online_v3_inline_publish_failed` encontrados | 0 |

El log de Cloud contiene 14 operaciones de acción (13 bots y 1 usuario); seis reintentaron la transacción, con dos intentos. La publicación inline evita un salto de trigger, pero no elimina la cadena de operaciones ni la contención. Mantener una instancia encendida tampoco resolvió toda la latencia.

Los tiempos de fase comparan el deadline del servidor con el reloj de pared del A56 y pueden contener error de reloj. `v3_render` marca el render de estado, no la finalización de sus animaciones. No extrapolar un único voto humano a todos los poderes.

## Consumo parcial y límites

El contador local del APK registró 59.935 bytes recibidos y 14.941 enviados durante 319 segundos. Cubre tráfico de todos sus servicios, no bytes facturados exclusivamente por RTDB. Para los cuatro bots, los callbacks JSON sumaron 50.648 bytes públicos, 12.217 privados, 7.068 de permisos y 5.524 de chat; tampoco equivalen a tráfico facturado.

La recolección de `cloud-metrics.json` terminó: 260 operaciones de lectura Firestore, 113 de escritura, 722.361 bytes enviados por RTDB y 72 solicitudes Cloud Run en la ventana consultada. Son contadores del proyecto, incluyen preparación, limpieza y trabajo de fondo y llegan con retraso; no son el coste aislado de esta partida. Los bytes de RTDB incluyen protocolo y cifrado.

También se verificaron las ubicaciones reales mediante las APIs de gestión: Firestore en Santiago (`southamerica-west1`) y RTDB en Iowa (`us-central1`). Las Functions de acción corren en Santiago y el worker en São Paulo. La publicación cruza regiones; es un componente a perfilar, no una demostración de que explique por sí solo toda la demora. Evidencia: `output/v3-cloud/2026-10-09-database-locations.json`.

## Diagnóstico que falta cerrar

1. **Inicio:** la activity construye la mesa antes del primer snapshot coherente y después la rellena. Revisar ese arranque con capturas de sus primeros segundos y presentar la mesa cuando perfil, rol, permisos y reloj permitan un primer render ordenado. No dar por probada toda la causa visual solo por esta lectura.
2. **Anuncios:** cotejar evento recibido → cola/ventana abierta → cierre en cada cambio de fase. La prueba registró eventos de muerte/expulsión/final, pero el usuario sigue indicando anuncios incorrectos. Reutilizar las ventanas comunes y comprobar secuencia/duración en el A56; los logs del backend no demuestran paridad visual.
3. **Latencia:** perfilar por separado limitador, transacción de juego, publicación RTDB, confirmación durable y entrega al cliente. El margen de Tasks sigue en 1.500 ms; forma parte de la espera entre fases. Medir y mantener el reintento seguro ante entrega temprana antes de reducirlo. No atribuir todas las demoras a arranque en frío.
4. Volver a probar acciones nocturnas humanas y dos rondas completas tras corregir causas reproducidas. Esta práctica completó el motor, pero no obtuvo aprobación del usuario.

## Cierre seguro

Se detuvo el controlador después de terminar la partida y guardar las evidencias. Sala, cuentas y token Debug temporales eliminados, `cleanupErrors:[]`. `onlineMaintenance/serverAuthority` vuelve a `enabled:false` con listas vacías; `config/onlineV3` continúa `enabled:false`, mínimo versión 52. No se abrió beta, no se publicó en Play y no se hizo commit/push.

Evidencias de la partida en `output/v3-cloud/qa-v3-interactive-1791523209347/`: `evidence.json`, `android-v3-events.log`, `android-metrics.txt`, `progress.jsonl`, `cloud-operations.json` y `report.json`. Capturas `2026-10-09-claude-a56-lobby.png`, `2026-10-09-claude-a56-playing.png` y `2026-10-09-claude-a56-reported-failure.png` bajo `output/v3-cloud/`.
