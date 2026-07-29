# Seguridad del online — auditoría y plan

**Fecha:** 24 de julio de 2026
**Alcance:** modo online (Firestore + Realtime Database + Auth), cliente Android y distribución.
**Escenario objetivo:** publicación en Play Store, es decir gente que no conocés y que puede
tener incentivo real para hacer trampa o arruinar partidas.
**Restricción:** presupuesto cero. Todo lo que se propone acá funciona en el plan **Spark**
(gratuito) salvo donde se indica lo contrario.

---

## 1. Resumen en una página

Lo que **ya está bien** y conviene no romper:

- Toda la superficie exige `request.auth.uid`. No hay lectura ni escritura anónima sin sesión.
- La **autoría** está garantizada en los dos motores: `actorId == auth.uid` en cada mensaje de
  chat, cada acción y cada emote. Nadie puede escribir en nombre de otro jugador.
- Las reglas validan forma, tipos y tamaños campo por campo. Es un trabajo poco común de ver
  en un proyecto de este tamaño y es lo que evita la mitad de los abusos triviales.
- La identidad ya está lista para crecer: `AccountLink` vincula correo **sin cambiar el uid**,
  así que un baneo por cuenta no obliga a migrar nada.

Los **tres problemas de fondo**, en orden:

1. **Cualquier usuario autenticado podía tomar el control de cualquier sala.** Era el agujero
   más grave y es de autorización pura, no de diseño. **Arreglado hoy** en `firestore.rules`
   (ver sección 4).
2. **El reparto público fue reemplazado por repartos privados.** `partidaInicial` conserva
   metadatos públicos; cada jugador lee `repartos/{uid}` y el anfitrión activo puede enumerar
   todos los repartos para ejecutar el motor.
3. **Ya existe una primera capa de moderación.** Incluye silencio local, votación para bloquear
   texto libre, reportes, expulsión/baneo por sala y baneo global manual.

Y el **límite duro que hay que mirar antes de publicar**: el plan gratuito de Realtime
Database permite **100 conexiones simultáneas**. Con salas de hasta 15 jugadores eso es
alrededor de **6 salas jugando a la vez** en todo el mundo. No es un problema de seguridad,
pero define el techo del lanzamiento (sección 7).

---

## 2. Modelo de amenaza

No todos los atacantes son iguales. Ordenados por probabilidad real en un juego de deducción
social publicado en Play Store:

| # | Quién | Qué quiere | Qué necesita | Estado hoy |
|---|-------|-----------|--------------|------------|
| T1 | **El tóxico** | insultar, spamear, arruinar la partida | nada, la app tal cual | sin defensa |
| T2 | **El tramposo casual** | ver los roles de los demás | leer Firestore con las credenciales del APK | trivial |
| T3 | **El griefer** | romper salas ajenas, echar gente, congelar partidas | un script con `google-services.json` | **cerrado hoy** |
| T4 | **El que quema la cuota** | dejarte sin Firebase / hacerte gastar | un bucle de escrituras | parcial |
| T5 | **El que evade sanciones** | volver después de un ban | reinstalar la app | sin defensa |

T2 es el que más duele en este género: en un juego de mentiras, saber los roles no es una
ventaja, es el fin del juego. T1 es el más frecuente y es el que vos pediste atacar.

---

## 3. Hallazgos

Severidad: **CRÍTICO** rompe el juego o el proyecto · **ALTO** daño real y frecuente ·
**MEDIO** daño acotado o poco frecuente · **BAJO** higiene.

### A. Autorización de sala — servidor

| ID | Sev | Hallazgo | Estado |
|----|-----|----------|--------|
| A1 | CRÍTICO | Toma de anfitrión de cualquier sala | ✅ arreglado |
| A2 | ALTO | Contador de jugadores de salas ajenas | ✅ arreglado |
| A3 | MEDIO | Contador de IDs públicos quemable | ✅ arreglado |
| A4 | MEDIO | Escritura libre en `pruebas/` | ✅ arreglado |

**A1 — Toma de anfitrión de cualquier sala.** `handoffUpdate()` sólo pedía `signedIn()` y que
el nuevo `hostActivoId` fuera vos mismo. No verificaba que fueras jugador de esa sala. Con una
sola escritura, cualquier cuenta del mundo podía convertirse en anfitrión activo de cualquier
partida en curso y, a partir de ahí, encadenar: editar los documentos de `jugadores`
(`isRoomActiveHost`), publicar un `estadoPartida` falso (`activeHostCanUpdateRoom`) y borrar la
sala entera (`allow delete` acepta `hostActivoId`). Los IDs de sala se obtienen listando
`partidas`, que es legible para cualquier autenticado.
*Arreglo:* `handoffUpdate(partidaId)` ahora exige `isActiveRoomMember(partidaId)`. Verificado
contra el único llamador real ([GameplayMockActivity.kt:3092](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:3092)),
que siempre es un jugador activo de la sala.

**A2 — Contador de jugadores ajeno.** `waitingPlayerCountUpdate()` tampoco pedía membresía:
cualquiera podía mover `jugadoresActuales` ±1 en cualquier sala en espera, dejándola
permanentemente "llena" o desbloqueando el inicio con gente de menos.
*Arreglo:* exige membresía. Usa `existsAfter()` y no `exists()` porque el alta de un jugador
crea su documento y actualiza el contador **en la misma transacción**
([LobbyBrowserActivity.kt:256](app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt:256),
[OnlineModeActivity.kt:906](app/src/main/java/com/traidores/juego/OnlineModeActivity.kt:906)):
con `exists()` se rompería el ingreso a salas.

**A3 — `meta/public_ids` quemable.** La regla aceptaba cualquier salto hacia arriba hasta
999.999.999.999. Una escritura y el espacio de `#` queda agotado para siempre, sin vuelta atrás
salvo edición manual.
*Arreglo:* exactamente `+1`, que es lo único que hace el cliente
([PlayerPublicIdentity.kt:80](app/src/main/java/com/traidores/juego/PlayerPublicIdentity.kt:80)).

**A4 — `pruebas/` abierto.** `allow read, write: if signedIn()` sobre cualquier documento y
cualquier contenido: un vector cómodo para inflar tu cuota de escrituras y de almacenamiento.
*Arreglo:* sólo el documento `conexion_inicial` y sólo con los cinco campos que escribe el
botón de Opciones.

### B. Secreto del juego — trampa

| ID | Sev | Hallazgo | Estado |
|----|-----|----------|--------|
| B1 | CRÍTICO | `partidaInicial` exponía el reparto completo | resuelto con repartos privados |
| B2 | CRÍTICO | `estadoClientes.rolKey` filtraba el rol en vivo | resuelto |
| B3 | ALTO | Canales secretos limitados a miembros, no al rol | riesgo residual |
| B4 | ALTO | El anfitrión es la autoridad del juego | inherente al modelo P2P |

**B1 — Resuelto.** `partidaInicial` conserva únicamente metadatos públicos. El rol se guarda en
`repartos/{uid}`, legible por su dueño y por el anfitrión activo. El anfitrión sigue viendo el
reparto completo porque ejecuta el motor autoritativo; eliminar ese último grado de confianza
requiere backend.

**B2 — Resuelto.** `estadoClientes` ya no publica `rolKey` ni el jugador completo.

**B3 — Riesgo residual.** Los canales ya exigen presencia/membresía y los listeners arrancan
después de publicar presencia. RTDB no puede consultar el reparto privado de Firestore, así que
un miembro con cliente modificado todavía podría intentar leer un canal que su rol no muestra.
*Arreglo:* exigir nodo de presencia en esa sala para leer, igual que ya se exige para escribir
y borrar. **No lo aplico hoy** porque hay una carrera real: en
[LobbyActivity.kt:363-367](app/src/main/java/com/traidores/juego/LobbyActivity.kt:363) la
presencia se inicia justo antes de los listeners de chat, pero su escritura es asíncrona
(`onDisconnect()` y recién después `setValue`). Si el listener se engancha antes de que el nodo
exista, RTDB **cancela el listener** y el chat queda muerto sin reintento. Es un cambio de
reglas + cliente coordinado, y va a la spec con ese orden explícito. Aclaración honesta: esto
sólo tapa al de afuera; un traidor vivo de la sala sigue pudiendo leer el canal de los muertos.
El secreto real entre miembros necesita el mismo backend que B1.

**B4 — El anfitrión es la autoridad.** El host resuelve fases, decide víctimas y publica
`estadoPartida`; el resto aplica. Un anfitrión con un APK modificado puede fabricar cualquier
resultado. Es una consecuencia del modelo elegido, no un bug, y sólo se cierra con un servidor
autoritativo (Cloud Functions, plan Blaze). Lo que sí se puede hacer gratis es **reducir la
superficie**: App Check para que un APK modificado no pueda hablar con Firebase, y mantener el
handoff de host restringido a miembros (ya hecho en A1).

### C. Abuso y moderación

| ID | Sev | Hallazgo | Estado |
|----|-----|----------|--------|
| C1 | ALTO | Sin reportar, silenciar ni banear | resuelto en cliente y reglas |
| C2 | MEDIO | Sin límite de frecuencia del lado del servidor | spec |
| C3 | MEDIO | Nombres y biografías sin filtro | spec |
| C4 | MEDIO | Las sanciones se evaden reinstalando | spec |

**C1.** Hoy un jugador tóxico no tiene ningún freno: el anfitrión puede expulsarlo del lobby
([LobbyActivity.kt:1271](app/src/main/java/com/traidores/juego/LobbyActivity.kt:1271)) pero
vuelve a entrar de inmediato, y durante la partida no hay absolutamente nada.
*Hecho hoy:* dejé lista la mitad de servidor. `partidas/{id}/baneados/{uid}` sólo lo escribe el
anfitrión, y `jugadores` ahora rechaza el alta y las escrituras de un uid baneado. El baneo de
sala pasa a ser real, no una puerta giratoria. Falta la UI y el flujo, que van en la spec.

**C2.** El cooldown de chat es de 1200 ms y vive en el cliente
([GameplayChatController.kt:3214](app/src/main/java/com/traidores/juego/GameplayChatController.kt:3214)).
Un cliente modificado lo ignora. RTDB **sí** puede imponer un intervalo mínimo por uid con el
patrón de actualización multi-ruta; va en la spec.

**C3.** `nombre` (18), `bioPerfil` (40) y los mensajes (140) están limitados en longitud pero no
en contenido. Un nombre ofensivo llega a todas las salas y aparece en el perfil público. Con
moderación reactiva (reportes) alcanza para empezar; una lista de bloqueo local es barata.

**C4.** La identidad por defecto es anónima: desinstalar y reinstalar genera un uid nuevo. Un
baneo global sólo muerde si el online exige cuenta con correo. Es una decisión de producto: la
recomendación es **permitir jugar de invitado y exigir cuenta sólo para el online público**,
dejando el juego con amigos por código de sala accesible sin cuenta.

### D. Cliente y distribución

| ID | Sev | Hallazgo | Estado |
|----|-----|----------|--------|
| D1 | ALTO | App Check todavía debe registrarse/observarse en consola | código integrado, no aplicado |
| D2 | ALTO | `minifyEnabled false` en release | resuelto |
| D3 | MEDIO | `google-services.json` versionado en git | decisión tuya |
| D4 | BAJO | Logs con nombres, uids y estado de partida | spec |

**D1 — Es la pieza que falta y es gratis.** Sin App Check, `google-services.json` es todo lo que
hace falta para hablar con tu Firestore y tu RTDB desde un script de 20 líneas, sin la app.
Con App Check + Play Integrity, Firebase rechaza cualquier petición que no venga de **tu APK
firmado por Play, en un dispositivo íntegro**. Eso es lo que convierte la validación del
cliente en algo que vale: hoy un cooldown en el cliente no defiende de nadie; con App Check
defiende de casi todos. App Check está incluido en el plan gratuito
([Firebase Pricing](https://firebase.google.com/pricing)).

**D2.** Release sin ofuscación ni shrink. Con toda la lógica de reglas del juego en el cliente,
el APK es un manual de instrucciones para hacer trampa. `minifyEnabled true` no es seguridad
real, pero sube el costo del ataque casual y además reduce el tamaño.

**D3.** `app/google-services.json` **está commiteado** — `git ls-files` lo confirma, y no está en
`.gitignore`. El `CLAUDE.md` del proyecto afirma lo contrario ("gitignored"), así que conviene
corregir esa línea para que nadie tome la decisión equivocada más adelante. El archivo no
contiene un secreto en el sentido clásico (la clave de API de Firebase es pública por diseño),
pero sí expone el `project_id`, el `app_id` y la URL de la base, que es exactamente lo que
necesita el atacante T3. Si el repo `nachogimenez7/DAM_ProyectoM` es público, dalo por
publicado. La mitigación efectiva no es esconder el archivo: es App Check.

**D4.** `OnlineDebugLog` escribe a Logcat en release con nombres, uids y transiciones de fase.
En Android moderno otra app no puede leer tu Logcat, así que el riesgo es acotado, pero conviene
silenciarlo en release por higiene y por privacidad.

### E. Plataforma, costos y requisitos de Play

| ID | Sev | Hallazgo |
|----|-----|----------|
| E1 | BLOQUEANTE | RTDB gratuito: 100 conexiones simultáneas |
| E2 | ALTO | Firestore gratuito: 50.000 lecturas/día |
| E3 | BLOQUEANTE | Requisitos legales de Play Store |
| E4 | RESUELTO | `targetSdk 36`; `versionCode 1` sigue siendo pre-release |

**E1.** El plan Spark limita Realtime Database a **100 conexiones simultáneas** y no se puede
subir ([Realtime Database Limits](https://firebase.google.com/docs/database/usage/limits)).
Cada jugador online mantiene una conexión abierta para chat y presencia. Son ~6 salas de 15 o
~14 salas de 7, en total, en todo el mundo. Al llegar al tope, las conexiones nuevas se
rechazan: para el jugador se ve como "el chat no anda" o "no puedo entrar". No es un problema
de seguridad, pero si publicás en Play, es el primer muro que vas a chocar.

**E2.** 50.000 lecturas por día. Estimación gruesa: cada cliente recibe una lectura por cada
cambio del documento de sala, y el host publica `estadoPartida` en cada fase. Una partida de
15 jugadores con ~20 fases son unas 300 lecturas sólo del documento de sala, más la
subcolección de jugadores, más `acciones`. Del orden de **1.000 a 3.000 lecturas por partida**,
o sea entre **15 y 50 partidas grandes por día** antes de quedarte sin cuota. Conviene medirlo
en la consola durante los próximos playtests en vez de estimarlo.

**E3.** Con cuentas por correo, Play exige política de privacidad publicada, formulario de
*Data safety* completo y **un mecanismo de borrado de cuenta**, accesible desde la app y desde
una URL pública. Es requisito de publicación, no una recomendación.

**E4.** Migrado el 25 de julio de 2026 a `targetSdk 36` y `compileSdk 36.1`. El
`versionCode 1` se mantiene mientras no exista una primera versión subida a Play.

---

## 4. Qué se aplicó hoy

Sólo `firestore.rules`. Nada de código Kotlin, nada de RTDB. Cada cambio fue verificado contra
el llamador real en el cliente antes de escribirlo.

| Cambio | Qué cierra | Riesgo de romper algo |
|--------|-----------|----------------------|
| `handoffUpdate(partidaId)` exige jugador activo | A1 | ninguno: el único llamador es un jugador de la sala |
| `waitingPlayerCountUpdate(partidaId)` exige membresía con `existsAfter` | A2 | ninguno: `existsAfter` cubre el alta en la misma transacción |
| `meta/public_ids` sólo acepta `+1` | A3 | ninguno: es lo único que escribe el cliente |
| `pruebas/` limitado a `conexion_inicial` y a 5 campos | A4 | ninguno: coincide con el botón de Opciones |
| `partidas/{id}/baneados/{uid}` nuevo, sólo del anfitrión | C1 (mitad servidor) | ninguno: colección nueva, sin uso todavía |
| `bans/{uid}` nuevo, sin escritura desde cliente | C1 (mitad servidor) | ninguno: colección nueva |
| `jugadores` rechaza uid baneado (global o de sala) | C1 | ninguno mientras no existan documentos de baneo |

Costo: cada escritura sobre `jugadores` suma 2 lecturas facturables por los chequeos de baneo,
y el peor caso de llamadas de acceso por evaluación queda en 6, bien por debajo del límite de
10 de Firestore.

**No pude validar la sintaxis localmente**: el emulador de Firestore necesita Java 11 o
superior y en esta máquina hay Java 8. El propio `deploy` valida las reglas en el servidor y
las rechaza si no compilan, así que publicarlas es la verificación:

```bash
firebase deploy --only firestore:rules
```

Después de publicar, la prueba de humo mínima: crear sala, entrar desde otro celular, tocar
"probar Firebase" en Opciones, jugar una partida corta y verificar que el traspaso de anfitrión
sigue funcionando al cerrar la app del host.

---

## 5. Plan gratuito, en tres tandas

**Tanda 0 — App Check (una tarde, sin código de juego).** Es el multiplicador de todo lo demás.
Sin esto, cada validación del cliente vale cero; con esto, valen casi todas. Incluye el modo
debug para poder seguir probando desde Android Studio.

**Tanda 1 — Moderación (lo que pediste).** Silencio local, silencio por votación de la mesa,
expulsión del anfitrión y baneo de sala. La mitad de servidor ya está publicada; falta la UI, el
flujo de votación y la mitad de RTDB.

**Tanda 2 — Cierre del secreto y reportes.** Borrar `rolKey` de `estadoClientes`, mover el
reparto a `repartos/{uid}`, cerrar la lectura de los canales de RTDB por presencia, y una cola
de reportes que revisás a mano desde la consola con baneo global.

Todo el detalle ejecutable está en
[SPEC-seguridad-y-moderacion-online.md](docs/desarrollo/specs/SPEC-seguridad-y-moderacion-online.md).

---

## 6. Lo que no se puede hacer sin plan de pago

Para que quede registrado y no se relitigue más adelante:

- **Servidor autoritativo de partida.** Que el reparto y la resolución no dependan de un
  celular. Es la única forma de eliminar B1, B2 y B4 de verdad.
- **Baneos y reportes procesados automáticamente.** Sin Cloud Functions, el baneo global se
  aplica a mano desde la consola de Firebase. A la escala de un lanzamiento inicial eso es
  perfectamente viable; a escala de miles de usuarios, no.
- **Límite de frecuencia fuerte.** Las reglas pueden imponer un intervalo mínimo, pero no
  contar peticiones por hora ni detectar patrones.
- **Limpieza automática de salas viejas.** Hoy es manual. Alternativa gratuita: un script con
  credencial de servicio corriendo en GitHub Actions una vez por día, que además puede procesar
  la cola de reportes. No es tiempo real, pero para limpieza y sanciones alcanza.

Desde febrero de 2026 Cloud Functions exige el plan **Blaze**, que necesita tarjeta asociada
aunque el consumo real quede en cero dentro de la capa gratuita
([Firebase pricing plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans)).
Mientras no quieras asociar una tarjeta, el techo de este online es el que describe la sección 3:
**tramposo determinado adentro, todos los demás afuera**.

---

## Fuentes

- [Firebase Pricing](https://firebase.google.com/pricing)
- [Firebase pricing plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans)
- [Realtime Database Limits](https://firebase.google.com/docs/database/usage/limits)
