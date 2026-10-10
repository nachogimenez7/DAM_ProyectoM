# Para Codex: traspaso de anfitrión y anuncio al salir corregidos (10/10/2026)

Gracias por la prueba de anuncios: encontró dos errores reales. Los corrigió Claude.

## 1. Traspaso de anfitrión: rechazado por el límite de 1000 expresiones (urgente)

**Causa.** No era un permiso faltante. La regla `update` de `partidas/{id}` evaluaba
`activeHostCanUpdateRoom`, `clientStateSelfPublish`, `waitingPlayerCountUpdate` y
`handoffUpdate` antes de `stableLobbyHostTransfer`. Con una sala de tamaño real (después de
una partida: `configLobby`, `ultimoResultado` y demás) se agota el límite de 1000
expresiones de Firestore y la operación se rechaza. El emulador lo dice tal cual:
`maximum of 1000 expressions to evaluate has been reached ... @ L986`.

`scripts/test-firestore-rules.cjs` pasaba porque usa una sala mínima. **Esto afecta a la
versión publicada desde el despliegue de reglas del 8/10:** en salas reales, «Salir»
traspasando la sala y «Pasar anfitrión» fallan.

**Corrección** (`firestore.rules`, regla `update` de la sala): se decide primero por
`hostId`. Solo `stableLobbyHostTransfer` cambia `hostId` (las otras ramas lo prohíben o no lo
incluyen en `changedOnly`). Entonces:

- si `hostId` cambia, se evalúa solo `stableLobbyHostTransfer` + `validRoomPostUpdate`;
- si `hostId` no cambia, se evalúan las ramas de antes, en el mismo orden.

Los permisos son exactamente los mismos; solo se evitan evaluaciones inútiles.

**Pruebas:**
- Nueva: `scripts/test-host-transfer-rules.cjs`, con una sala de tamaño real. Cubre la salida
  con traspaso, el traspaso manual, que un invitado no pueda quedarse con la sala si el
  creador está activo, y que no se pueda pasar la sala a un invitado sin cuenta. **Contra
  las reglas publicadas (`RULES_FILE=<HEAD>`) falla con el límite de 1000; con las nuevas
  pasa.**
- La reproducción con la instantánea real de PLLPGU también pasa (antes los tres casos daban
  `permission-denied`).
- Siguen pasando `test:firestore-rules`, `test:firestore-rules-invitados`,
  `test-account-history-rules`, `test-purchase-rules` y `test-server-authority-rules`.

## 2. Anuncio del anfitrión al salir: llegaba una visita tarde

Tu hipótesis era correcta: el menú online se reanudaba antes de que el lobby se detuviera.
Ahora la marca se levanta cuando `leavingOnlineLobby` pasa a `true` (salida confirmada,
nunca vuelve a `false`), no en `onStop`. Si el traspaso falla, `leavingOnlineLobby` no
cambia y no queda marca. Android: 748/748.

## Qué hacer (con OK del usuario)

1. **Desplegar solo las reglas de Firestore** (`firebase deploy --only firestore:rules`), con
   el respaldo previo como siempre. Incluyen además `derechos/` y `compras/` (cerradas al
   cliente salvo la lectura propia de `derechos`), que no afectan a nada existente.
2. Volver a correr la prueba de anuncios desde un build nuevo con el árbol actual (hostqa,
   demo), en una corrida limpia sin pérdida de conexión del A56:
   - punto 4: ningún relevo provocado por un anuncio;
   - punto 5: el anfitrión sale traspasando la sala a Nacho (debe funcionar) y ve el video
     **en el primer** regreso al menú online, una sola vez;
   - el traspaso manual «Pasar anfitrión» también funciona.
3. Si podés, probá el traspaso también con la app publicada del A56 después de desplegar
   las reglas, para confirmar que se arregla en producción.

No edites los archivos de Android de Claude. Sin commit ni push.
