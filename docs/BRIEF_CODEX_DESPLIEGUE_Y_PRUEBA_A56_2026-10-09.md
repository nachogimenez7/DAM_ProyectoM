# Encargo para Codex: desplegar las correcciones y probar en el A56 — 9/10/2026

Lo redactó Claude. El usuario aprobó los tres pasos («sí, avanzá con las tres»). Claude
no pudo ejecutarlos porque su sesión bloquea los despliegues a producción. Contexto:
`docs/REVISION_MESA_V3_A56_2026-10-09.md`.

## Estado verificado por Claude antes de desplegar

- `onlineMaintenance/serverAuthority`: `enabled: false`, listas vacías.
  `config/onlineV3`: `enabled: false`, `minVersionCode: 52`.
- En `functions/` el único cambio desde tu último despliegue (`investigation-deploy`,
  0:34 del 9/10) es `functions/src/onlineGameFunctions.js`, hecho por Claude:
  - `publishNow`: inicio, acción y abandono publican la proyección en la misma llamada.
    El trigger queda como respaldo; si esta publicación falla se registra
    `online_v3_inline_publish_failed` y la intención aceptada no se rechaza.
  - `accionPartidaV3`: `cpu: 1`, `concurrency: 20`, `minInstances: 1`.
  - `resolverFaseV3`: `minInstances: 1`.
  - Decisión de costo aprobada por el usuario para la beta (estimación aproximada:
    US$ 15 por mes; confirmarla con la calculadora de Google).
- Pruebas: unitarias de backend aprobadas (incluida la de endpoints actualizada);
  integración callable 1/1; servicio y recuperación 39/39 (sin Functions); Android
  741/741.

## 1. Desplegar (con el gate cerrado)

```sh
node_modules/.bin/firebase deploy --project traidores \
  --only functions:iniciarPartidaV3,functions:accionPartidaV3,functions:abandonarPartidaV3,functions:resolverFaseV3
```

Verificar después del despliegue:
- estado `ACTIVE`;
- `accionPartidaV3` con `minInstances=1`, `cpu=1` y `concurrency=20`;
- `resolverFaseV3` con `minInstances=1`;
- que `resolverFaseV3` conserve su invoker privado y su cola (tuviste un problema con
  firebase-tools 15.28.2 al respecto).

Guardar la evidencia en `output/v3-cloud/2026-10-09-claude-deploy*.{log,json}`.

## 2. Práctica en el A56 contra Cloud

- APK ya compilado con el código corregido: `output/traidores-v3-cloud-practica-claude.apk`
  (`com.traidores.juego.v3qa`, Cloud QA, V3). Reemplaza al APK de práctica anterior.
- Usar tu procedimiento `scripts/play-server-v3-cloud-android.cjs`: gate solo para
  esa sala y su creador, token Debug de App Check temporal y limpieza al terminar.
- Pedirle al usuario que juegue al menos dos rondas.
- Registrar desde el A56:

  ```sh
  adb logcat -d -s TraidoresOnline:I | grep v3_
  ```

  Las líneas `v3_action_submit` → `v3_action_confirmed` / `v3_projection_confirmed`
  dan la latencia real de cada acción. Las `v3_render` dan el orden de fases.
- En los logs de Cloud, para la sala de prueba: `online_v3_operation` (`operation`,
  `durationMs`, `transactionAttempts`, `published`) y `online_v3_inline_publish_failed`
  (se espera 0).

## 3. Informar

- Latencia por acción (mediana y peor caso) y transacciones con reintentos.
- Demora de cada cambio de fase: de `limiteFaseEpochMs` a la siguiente `v3_render`.
- Lo que el usuario vio en el A56, con capturas.
- Confirmar la limpieza y que el gate quedó cerrado.

No abrir la beta, no promover en Play y no hacer commit ni push sin pedido explícito
del usuario.
