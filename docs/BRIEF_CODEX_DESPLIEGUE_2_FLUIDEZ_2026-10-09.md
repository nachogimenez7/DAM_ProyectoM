# Encargo para Codex: segundo despliegue de fluidez y prueba en el A56 — 9/10/2026

Lo redactó Claude después de tu práctica en Cloud («inicio desordenado, anuncios, acciones
lentas, falta fluidez»). La sesión de Claude no puede desplegar a producción, por eso
pasa por vos. El usuario ya aprobó desplegar y probar.

## Qué cambió (solo presentación y tiempos; ninguna regla de juego)

### Servidor

- `onlineGameService.js`: `DEADLINE_MARGIN_MS` pasa de 1500 a **250**. Se agrega
  `EARLY_DEADLINE_WAIT_MS = 3000`.
- `onlineGameFunctions.js` (`resolverFaseV3`): si la tarea llega antes del vencimiento
  por no más de 3 s, **espera dentro del worker** y vuelve a intentar una sola vez, en
  lugar de rechazarla con `unavailable` y caer en el backoff de la cola (1–10 s). Si la
  llegada es más temprana, se mantiene el rechazo. `sleep` es inyectable en
  `createServerEndpoints`.
- El margen se usa al encolar dentro de `publishServerOutbox`, que corre en todas las
  funciones que publican. Por eso hay que **desplegar todas las V3**: `iniciarPartidaV3`,
  `accionPartidaV3`, `recuperarFaseV3`, `prepararRevanchaV3`, `abandonarPartidaV3`,
  `publicarPartidaV3`, `resolverFaseV3` y `repararPartidasV3`. Hay que conservar las
  instancias mínimas ya aprobadas y el invoker privado del worker.
- Pruebas: unitarias de backend aprobadas; integración callable 1/1; servicio y
  recuperación 39/39 sin Functions.
- Medido localmente: recuento, amanecer y resultado sin expulsión cambian a los
  **3,1 s** exactos de su configuración de 3 s (antes, entre 3,2 y 15 s).

### Android (ya incluido en la APK nueva)

1. **Inicio ordenado:** el lobby V3 abre primero la pantalla de reparto del común
   (`AssigningRolesActivity`, solo dorsos) y después la mesa
   (`EXTRA_SERVER_MATCH_ID`). En una recuperación se entra directo, pero la mesa queda
   tapada («Preparando la mesa…») hasta el primer estado coherente.
2. **Amanecer:** la transición «AMANECER N» se muestra aunque la nube deje menos de
   2,5 s de fase. Ya no la cortaba el cambio a debate (en tu práctica se había salteado).
3. **Respuesta inmediata:** el cartel de confirmación del común aparece al tocar. El
   recibo no lo repite; si el servidor rechaza la acción, se retira y aparece el error.
4. 741 pruebas unitarias de Android aprobadas. Partida local de 8 jugadores completa,
   verificada.

APK Cloud QA nueva: **`output/traidores-v3-cloud-practica-claude-2.apk`**
(`com.traidores.juego.v3qa`).

## Pasos

1. Confirmar que el gate y `config/onlineV3` siguen cerrados.
2. Desplegar las 8 Functions V3 con el gate cerrado y verificar: estado ACTIVE,
   instancias mínimas, CPU y concurrencia de `accionPartidaV3`, e invoker del worker.
3. Práctica en el A56 con la APK nueva y tu procedimiento de práctica Cloud. **El usuario
   ofreció conectar un segundo teléfono** (el de su madre): si podés, armá la práctica
   con dos personas y tres bots. Una prueba entre personas es lo que falta.
4. Medir y entregar:
   - del vencimiento a `v3_render` de la fase siguiente, por fase;
   - de `v3_action_submit` a `v3_action_confirmed` en acciones humanas, incluida una
     nocturna;
   - logs `online_v3_operation` del worker: deben desaparecer los `unavailable`
     repetidos por llegada temprana.
5. Capturas del inicio (reparto → mesa), de un amanecer y de un cartel de acción.
   Anotar qué le pareció al usuario.

Sin abrir la beta, sin Play y sin commit ni push sin pedido del usuario. Al terminar,
el gate cerrado y la limpieza verificada.
