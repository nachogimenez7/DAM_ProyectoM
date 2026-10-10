# Base de compras: «Sin anuncios», pack y estilos (10/10/2026)

Lo implementó Claude. Es la base que comparten «Sin anuncios», el Pack del Benefactor (antes «Sello del Pueblo»; el ID en Play sigue siendo `pack_sello_pueblo`) y
los estilos. **Todavía no hay pantalla de tienda.** Eso es lo próximo.

## Cómo funciona

1. El jugador, con una cuenta vinculada (los invitados no compran), toca comprar. Se abre
   la ventana de Google Play con el precio en pesos que fijes en Play Console.
2. Google cobra y le da al teléfono un comprobante (token). La compra queda atada a la
   cuenta de Traidores con un código cifrado del UID (`obfuscatedAccountId`).
3. El teléfono manda el comprobante a la función **`validarCompraV1`**. El servidor:
   - consulta a Google si la compra es real y está pagada;
   - rechaza pagos pendientes, compras canceladas y comprobantes de otra cuenta;
   - registra la compra **una sola vez** (`compras/{hash del token}`);
   - calcula los **derechos** de la cuenta a partir de todas sus compras activas
     (`derechos/{uid}`);
   - confirma la compra a Google (si no se confirma en 3 días, Google la reembolsa).
4. El teléfono lee `derechos/{uid}` (solo el dueño puede leerlo y nadie puede escribirlo
   desde el teléfono) y los guarda para usarlos sin conexión.
5. **«Restaurar compras»** vuelve a mandar todo lo comprado. Sirve al reinstalar o al
   cambiar de teléfono, y no duplica nada.
6. **Reembolsos y contracargos:** `revisarComprasAnuladasV1` corre todos los días a las
   6:00, consulta a Google las compras anuladas y quita solo lo que daba esa compra.

`AdFreeEntitlement` ya está conectado: con el derecho `sin_anuncios` (suelto o del pack) no
aparecen más videos.

## Productos (IDs que hay que crear en Play Console, tipo «producto único»)

| ID | Qué da | Precio |
|---|---|---|
| `sin_anuncios` | sin anuncios | US$ 2,99 · $3.999 |
| `pack_sello_pueblo` | sin anuncios, estilo Sello, marco, insignia, 3 banners, 5 emotes, emote Brindis, placa, burbujas y festejo | US$ 5,99 · $7.999 |
| `estilo_espacial` | estilo Espacial con su festejo | US$ 1,99 · $2.999 |
| `estilo_abismo_real` | estilo Abismo Real (mar) con su festejo | US$ 1,99 · $2.999 |
| `estilo_forja_infernal` | estilo Forja Infernal (fuego) con su festejo | US$ 1,99 · $2.999 |
| `estilos_tres` | los 3 estilos | US$ 3,99 · $5.999 |
| `emotes_memes` | Memes Pampeanos | US$ 1,99 (por confirmar) |

El catálogo vive en dos lugares que deben coincidir: `functions/src/purchaseService.js`
(`CATALOG`) y `PurchaseCatalog.kt`. Una prueba de Android lo verifica.

## Archivos

- Servidor: `functions/src/purchaseService.js` (lógica y cliente REST de Google Play),
  `functions/src/index.js` (`validarCompraV1`, `revisarComprasAnuladasV1`),
  `functions/package.json` (dependencia explícita `google-auth-library`, que ya venía
  instalada).
- Reglas: `firestore.rules` (`derechos/{uid}` solo lectura del dueño registrado,
  `compras/` cerrado).
- Android: `PlayPurchases.kt` (Play Billing 8), `AccountEntitlements.kt` (catálogo y
  derechos), `InterstitialAds.kt` (`AdFreeEntitlement`), `TraidoresApplication.kt`.

## Pruebas

- Backend: 84/84 unitarias, incluidas 6 nuevas de compras: el pack da todo, se rechazan
  pagos pendientes, cancelados y de otra cuenta, el otorgamiento es único e idempotente, un
  comprobante no se reusa en otra cuenta y un reembolso quita solo lo suyo.
- Reglas: `scripts/test-purchase-rules.cjs` 6/6 (dueño lee; nadie se otorga nada; otra
  cuenta, invitados y clientes no leen ni escriben compras). La suite existente de reglas
  de Firestore también pasa.
- Android: 748/748, incluida la paridad del hash de cuenta y del catálogo con el servidor.
- **No probado con Google Play real:** requiere los pasos de abajo.

## Lo que tiene que hacer el usuario en Play Console

1. **Perfil de pagos** (Configuración → Perfil de pagos), si todavía no existe. Sin eso no
   se pueden crear productos pagos.
2. **Crear los productos** de la tabla (Monetizar → Productos → Productos únicos) con esos
   IDs exactos, sus nombres y el precio en pesos para Argentina.
3. **Testers de licencias** (Configuración → Pruebas de licencias): agregar tu cuenta de
   Google y la de quien pruebe. Así las compras de prueba no cobran.
4. Las compras solo funcionan con una versión **subida a una pista de prueba** (prueba
   interna alcanza) e instalada desde Play.
5. **Dar permiso al servidor** (Usuarios y permisos → Invitar usuario): invitar la cuenta
   de servicio de las Functions (`99323018581-compute@developer.gserviceaccount.com`; Codex
   debe confirmarla) con permisos «Ver datos financieros» y «Administrar pedidos y
   suscripciones».

## Lo que tiene que hacer Codex (con OK del usuario)

1. Habilitar la **Google Play Android Developer API** en el proyecto `traidores`.
2. Confirmar qué cuenta de servicio ejecuta las Functions gen2 y pasársela al usuario
   para el paso 5.
3. Desplegar `validarCompraV1`, `revisarComprasAnuladasV1` y las reglas de Firestore.
4. Prueba con un tester de licencias: comprar `sin_anuncios`, ver que desaparecen los
   videos, reinstalar y restaurar, y reembolsar desde Play Console para comprobar que se
   quita al día siguiente.

## Pendientes y decisiones

- **Pantalla de tienda** (siguiente paso): lista, detalle con vista previa, «Probar»,
  estados (pendiente, comprado, sin conexión) y «Restaurar compras».
- **Borrar la cuenta:** hoy los derechos quedan atados al UID. Si alguien borra su cuenta y
  crea otra, no puede restaurar en la nueva. Decidir si se permite migrarlos.
- **Cartel «¿Preferís jugar sin anuncios?»** cada 3 videos: se activa cuando exista la
  tienda.
