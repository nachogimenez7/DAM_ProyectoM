# Encargo para Codex: activar V3 para la beta Android

Fecha: 8/10/2026 (jueves). Lo redactó Claude a pedido del usuario.
Meta: beta Android con el online V3 **antes del sábado 10/10**.
Va en paralelo con `BRIEF_CODEX_PARIDAD_VISUAL_MESA_V3.md`.

## Situación de partida (comprobada en el repositorio)

- `app/build.gradle:61`: Release fija `SERVER_ONLINE_V3=false`. Debug lo lee de
  `traidoresServerOnlineV3`. El protocolo de la sala lo decide el cliente al crearla
  (`LobbyActivity.kt:114`).
- Servidor: el gate `onlineMaintenance/serverAuthority` quedó en
  `{"enabled": false, "allowedHostUids": [], "allowedRoomIds": []}`.
  Solo afecta inicios nuevos; las partidas en curso siguen avanzando.
- La app no tiene Remote Config ni versión mínima obligatoria. App Check usa Play
  Integrity en Release (`TraidoresApplication.kt:41`).
- Release tiene `minifyEnabled = true`. **Nunca se probó V3 sobre un APK Release
  minificado.** La última entrega solo compiló `compileReleaseKotlin`.
- Hay versiones publicadas anteriores (`versionCode` 51, `0.1.50`) con el online
  anterior. Las reglas de Firestore ya separan las salas V3
  (`firestore.rules:256-274`); falta comprobarlo con un cliente viejo real.
- Los cambios de la última entrega (expulsión en `RESULTADO`, Bufón, `deadChat`)
  **no están desplegados** en Functions.

## Decisiones del usuario — CONFIRMADAS el 8/10

Las tres opciones recomendadas quedaron aprobadas: **(1)** V3 para todos los de la
beta, con el gate abierto y sin listas; **(2)** sin respaldo del online anterior en la
build beta: al apagarse, «Online en mantenimiento»; **(3)** prueba interna de Play
y después prueba **abierta**. Los pasos marcados con ⚑ ya pueden ejecutarse.

Detalle de cada decisión:

1. **Quién usa V3 en la beta.** Recomendado: todos los que instalen la build de la
   pista de prueba de Play. En el servidor, el gate con `enabled: true` y **sin**
   listas (omitirlas; una lista vacía bloquea), protegido por App Check, alertas de
   presupuesto y el interruptor de apagado. Alternativa: solo hosts en
   `allowedHostUids`, más seguro pero hay que agregar cada UID a mano.
2. **¿Se conserva el online anterior como respaldo en la build beta?** Recomendado:
   **no**. La build beta solo crea salas V3. Si se apaga el gate, el online muestra
   «Online en mantenimiento» en lugar de volver al protocolo con autoridad del
   anfitrión. Mantener los dos protocolos en la misma build duplica riesgos.
3. **Pista de Play.** Recomendado: primero «prueba interna», para verificar la build
   firmada por Play, y después «prueba cerrada» o «abierta» para la beta.

## Pasos

### Viernes — preparar y desplegar con el gate cerrado

1. **Despliegue de backend con el gate cerrado:** reglas e índices primero, después
   Functions. Verificar que una partida iniciada con el código anterior termine bien
   (está contemplado en `resolveResult`). Guardar backups de reglas y de la
   configuración del gate.
2. **Interruptor del cliente ⚑.** Reemplazar el `false` fijo de Release por una
   propiedad de compilación de la build beta (`traidoresServerOnlineV3=true` solo en
   esa variante o pista) **más** una comprobación en tiempo de ejecución. Antes de
   ofrecer el online, la app lee un documento público de solo lectura (por ejemplo
   `config/onlineV3 {enabled, minVersionCode}`), al abrir el modo online y nunca en
   la mesa. Si está apagado o la versión es menor: «Online en mantenimiento» o
   «Actualizá la app». Es **una lectura por entrada al modo online**: anotarla en
   la medición.
3. **Clientes viejos.** Con un APK `0.1.50` real, comprobar que no ve, no puede unirse
   y no puede romper salas V3 (reglas). También al revés: que la build beta no entra
   en salas del protocolo anterior. Si un viejo puede entrar, corregirlo en las reglas.
4. **Build Release real:** subir `versionCode` (52), compilar Release minificada y
   firmada, y revisar las reglas de R8/ProGuard para los DTO `Serializable` del
   contrato V3 y para Firebase. Subirla a **prueba interna** de Play.
5. **Prueba con la build de Play** (no el APK de QA ni Debug):
   - App Check con Play Integrity real: las callables V3 no se rechazan. La huella
     SHA-256 de la clave de firma de Play está registrada en Firebase.
   - Gate abierto solo para el UID de prueba (`allowedHostUids`) y una partida de al
     menos 5 jugadores entre el A56 y otros teléfonos o personas reales: lobby, partida
     completa, expulsión, Bufón si sale, desconexión y vuelta, abandono, resultado,
     historial y revancha.
   - Medir latencia de acciones y publicación (p50/p95), lecturas/escrituras, bytes de
     RTDB e invocaciones. Comparar con `MEDICION_V3_BETA_ANDROID.md`.
   - Prueba §3.6 con dos teléfonos lado a lado: nadie ve la expulsión antes que otro.
6. **Alertas y operación:** alerta de presupuesto de Firebase/GCP y procedimiento
   escrito de apagado (gate `enabled: false` + `config/onlineV3.enabled = false`;
   las partidas en curso terminan). Indicar quién lo ejecuta y cómo, desde la consola,
   sin la Mac.

### Sábado — go / no-go con el usuario

7. **Condiciones de entrada (todas):**
   - mesa aprobada por el usuario en el A56 (encargo visual);
   - paso 5 aprobado con la build de Play;
   - apagado probado una vez (cerrar el gate y verificar el mensaje de mantenimiento);
   - sin errores nuevos en los logs de Functions durante la prueba.
8. **Apertura ⚑:** abrir el gate según la decisión 1, promover la build a la pista
   beta y vigilar logs y consumo durante las primeras partidas. Anotar hora,
   versión y estado del gate en un documento de cierre.

## Fuera de este encargo

Publicidad, pack/tienda, bots, tráiler e iOS. No cambiar reglas de juego ni plazos
(ya decididos en la revisión de paridad). No desactivar App Check. No publicar
credenciales ni tokens de depuración. No commit/push sin pedido del usuario: las
ramas mezclan Android, backend e iOS y deben separarse antes de publicar.

## Entrega esperada

`docs/CIERRE_ACTIVACION_V3_BETA.md` con:
- qué se desplegó y cuándo, y el estado del gate;
- versión subida y pista;
- resultados del paso 5 con logs y capturas;
- el procedimiento de apagado probado;
- los pendientes.
Indicar explícitamente lo que **no** se probó.
