# Spec — Invitado y cuenta

> **Estado: F1 a F4 implementadas, más §8 (salas solo para cuentas)** — 25 de julio de 2026.
> Las reglas se verificaron con `npm run test:firestore-rules-invitados` contra el emulador.
> Falta compilar el Kotlin en Android Studio. El detalle está al final, en "Lo implementado".

Diseño del juego según el estado de identidad: qué puede hacer alguien sin registrarse, qué
gana al registrarse y cómo se cruza esa línea sin que nadie pierda nada.

Se apoya en dos ciclos previos: la identidad ya está resuelta en `AccountLink` (vincular sin
cambiar el uid) y la moderación está diseñada en
[`SPEC-seguridad-y-moderacion-online.md`](SPEC-seguridad-y-moderacion-online.md). Esta spec es
la que le da sentido al baneo global: sin cuenta obligatoria en algún punto, una sanción se
evade desinstalando.

## Decisiones tomadas (25 de julio de 2026)

| Tema | Decisión |
|------|----------|
| Online del invitado | Puede **buscar salas y unirse por código**. **No** puede crear salas, **no** puede ser anfitrión, **no** participa de invitaciones/amigos. |
| Nombre del invitado | **Lista cerrada de alias cómicos** + número derivado del uid: `Aguafiestas 4821`. Sin texto libre. |
| Personalización | Sólo registrados. El invitado ve todo y choca con la puerta. |
| Perfil ya personalizado de un invitado | **Se conserva congelado**: no se borra nada, pero no se puede editar hasta registrarse. |
| Número público `#` | **Sólo al registrarse.** El invitado no reserva número. |

> Interpretación de "no puede ser agregado": el invitado no entra al sistema de amigos ni de
> invitaciones a sala, porque ambos cuelgan del `#` y el invitado no tiene. Si querías decir
> otra cosa, corregilo acá antes de que Codex tome la spec.

---

## 1. Respuesta directa: ¿es mucho quilombo que el invitado no sea anfitrión?

**No, y encima se puede hacer valer en el servidor.** Las reglas de Firebase pueden distinguir
invitado de registrado sin ningún costo, así que no es una cortesía del cliente que se saltea
un APK modificado. Pero hay **un caso borde que sí hay que resolver**, y es lo único
verdaderamente delicado de toda esta spec.

Hay dos anfitriones distintos y no tienen el mismo riesgo:

**`hostId`, el creador de la sala — trivial.** Si el invitado no puede crear salas, nunca es
creador. Una línea en las reglas y listo.

**`hostActivoId`, el que resuelve las fases — acá está el problema.** Se traspasa solo cuando
el anfitrión se desconecta o su personaje muere, y el candidato es *el primer jugador vivo y
conectado por `orden`* ([GameplayMockActivity.kt:3050](../../../app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:3050)).
Si filtramos invitados y en una partida de 8 hay 6 invitados, puede pasar que **no quede ningún
registrado vivo y conectado**. En ese momento nadie puede publicar `estadoPartida` y la partida
se congela para los ocho. Cambiar "un invitado resuelve la fase" por "la partida muere" es un
mal negocio.

**Diseño propuesto: regla dura donde no hay urgencia, degradación donde sí la hay.**

- **Crear sala:** regla de servidor. El invitado no puede, punto.
- **Anfitrión estable del lobby** (`stableLobbyHostTransfer`): regla de servidor. El invitado
  no puede tomarlo. En el lobby no hay nada que se congele: si no queda ningún registrado, la
  sala se desarma, que es lo que ya pasa hoy.
- **Anfitrión activo en gameplay** (`handoffUpdate`): filtro **del cliente**, con escalón. Se
  prefiere siempre al registrado vivo y conectado de menor `orden`; si pasan **20 segundos sin
  que ningún registrado tome el relevo**, se habilita a los invitados por el mismo criterio.
  La regla no puede expresar esto porque no sabe si existe otro candidato mejor.

Lo que se acepta con eso: en una partida donde se caen todos los registrados, un invitado
termina siendo anfitrión durante 20 segundos de espera. Como el anfitrión activo es quien ve
el reparto completo (ver §F de la spec de seguridad), eso es una ventana chica de exposición.
La alternativa es una partida congelada, que es peor y además le pasa a todos.

---

## 2. Matriz: qué puede hacer cada uno

| | Invitado | Registrado |
|---|---|---|
| Jugar contra la IA | ✅ todo | ✅ todo |
| Buscar salas y unirse por código | ✅ | ✅ |
| Crear sala | ❌ | ✅ |
| Ser anfitrión | ❌ (salvo el escalón de §1) | ✅ |
| Entrar a salas marcadas "sólo cuentas" | ❌ | ✅ |
| Nombre | asignado `Invitado 4821` | libre, hasta 18 caracteres |
| Número `#` | ❌ no reserva | ✅ |
| Avatar, banner, bio, rol favorito | ❌ (ve todo, no elige) | ✅ |
| Logros destacados en el perfil | ❌ elegir · ✅ desbloquear | ✅ |
| Emotes | ✅ usa el set por defecto · ❌ elige cuáles | ✅ |
| Chat, respuestas rápidas, votar | ✅ | ✅ |
| Reportar y votar un silencio | ✅ | ✅ |
| Amigos e invitaciones (futuro) | ❌ | ✅ |
| Historial y estadísticas | ✅ local | ✅ local |

Tres criterios detrás de la matriz, por si aparece una función nueva y hay que ubicarla:

1. **Nada que afecte a la partida se le saca al invitado.** Puede jugar, hablar, votar,
   reportar y usar emotes. Un invitado no es un jugador de segunda dentro de la mesa.
2. **Lo que se le saca es identidad y autoridad**: representarse (personalización, nombre,
   `#`) y mandar (crear sala, ser anfitrión).
3. **Nada de lo que hace se pierde.** Logros, historial y estadísticas se acumulan igual y
   siguen ahí el día que se registre. El registro nunca es un reinicio.

---

## 3. Distinguir invitado de registrado — en el servidor

**Regla** (agregar a `firestore.rules`, junto a los otros helpers):

```
function isRegistered() {
  return signedIn()
    && (
      request.auth.token.firebase.sign_in_provider != 'anonymous'
      || request.auth.token.get('email', '') != ''
    );
}
```

Se chequean **las dos señales a propósito**. Después de `linkWithCredential`, el claim
`sign_in_provider` no siempre se actualiza en el token que el cliente ya tiene en mano: es un
problema conocido de tiempos de refresco. El claim `email`, en cambio, aparece en cuanto el
token se refresca. Con cualquiera de las dos, la cuenta cuenta como registrada.

**Y en el cliente, obligatorio:** después de un `linkWithCredential` exitoso, forzar
`user.getIdToken(true)` **antes** de cualquier escritura. Sin eso, el jugador se registra y
durante unos minutos el servidor lo sigue tratando como invitado — el peor bug posible de esta
spec, porque es intermitente y depende del reloj.

**Dónde se aplica:**

```
// partidas/{partidaId}
allow create: if isRegistered() && !isGloballyBanned() && ...   // el invitado no crea salas
```

y dentro de `stableLobbyHostTransfer(partidaId)`, sumar que el candidato sea registrado. Como
esa función mira `request.resource.data.hostId`, alcanza con exigir `isRegistered()` sobre
quien escribe, porque el candidato siempre es quien reclama o el anfitrión saliente.

---

## 4. Nombre asignado de invitado

**Formato:** alias de una lista cerrada más cuatro dígitos, por ejemplo `Aguafiestas 4821`.
La lista es cómica y de tono argentino: `Forastero`, `Mala Onda`, `Aguafiestas`, `Chamuyero`,
`Careta`, `Mufa`, `Perejil`, `Metepatas`, `Don Nadie`, `El Colado`, `Sospechoso`, `Rezongón`.
El invitado elige de la lista; nunca escribe texto libre. Ningún alias puede pasar de 13
caracteres: con el número, el nombre entero tiene que entrar en los 18 que aceptan las reglas.

**Generación:** derivado del uid, **sin tocar `meta/public_ids`**. Ya existe exactamente ese
criterio en `PlayerPublicIdentity.localFallbackPublicId`
([PlayerPublicIdentity.kt:182](../../../app/src/main/java/com/traidores/juego/PlayerPublicIdentity.kt:182)):
hash del uid, sin red, estable en el dispositivo. Dos invitados pueden colisionar; dentro de
una sala eso se resuelve como ya se resuelven los nombres repetidos.

**Lo que esto gana además del empujón al registro:** un invitado **no puede escribir un nombre
ofensivo**. Y es verificable en el servidor: dentro de `validPlayerDocument`, si el que escribe
no es `isRegistered()`, exigir `data.nombre.matches('^Invitado [0-9]{4}$')`. El vector de
nombres ofensivos queda cerrado de raíz para todo el que no dio un correo, que es justo el que
no tiene nada que perder.

**Contrapeso honesto — leelo antes de aprobar esto.** Elegiste el nombre asignado sabiendo que
complica jugar con amigos, y así queda. Pero hay una variante que conserva la propiedad de
seguridad y arregla lo social: **una lista cerrada de alias del pueblo** (`Forastero`,
`Pulpero`, `Vecino de la Pampa`, `Trotamundos`...). El invitado elige de una lista, nunca
escribe texto libre, y sus amigos lo reconocen en la sala. Cierra el mismo vector, cuesta lo
mismo y se siente mucho menos hostil en el primer minuto de juego. Queda a tu decisión: si no
decís nada, se implementa `Invitado 4821` a secas.

**En sala:** `nombreSala` del invitado es su nombre asignado, sin `#`. El sufijo `#` pasa a ser
una señal visible de "esta persona tiene cuenta".

---

## 5. El `#` sólo al registrarse

**Hoy pasa algo que no se quiso**: `ensurePublicId` se llama desde
[ProfileActivity.kt:545](../../../app/src/main/java/com/traidores/juego/ProfileActivity.kt:545),
así que **cualquiera que abre el perfil una vez consume un número del contador global**, aunque
nunca juegue online y aunque desinstale a los cinco minutos. El contador se gasta con gente que
no existe y el `#` no significa nada.

**Cambio:** `ensurePublicId` sólo se llama si `isRegistered()`. Eso afecta a los cuatro
llamadores actuales: `ProfileActivity`, `LobbyBrowserActivity`, `OnlineModeActivity` (×2) y
`AccountLink` (este último es el correcto y se queda como está).

**Al registrarse** se reserva el número, se muestra con la animación que ya existe y se publica
el perfil público.

**Migración:** el invitado que ya tiene número **se lo queda**. Es coherente con "se conserva
congelado" y evita que alguien vea desaparecer su `#` sin haber hecho nada.

---

## 6. La pantalla de perfil del invitado

Es la pieza de producto más importante de la spec: el perfil deja de ser una pantalla de
configuración y pasa a ser el lugar donde se explica qué te estás perdiendo.

**Qué NO hacer: esconder lo que está bloqueado.** Un invitado que no ve los avatares no sabe
que existen y no tiene ningún motivo para registrarse.

**Qué hacer:**

- Todo el perfil se muestra completo, con los valores por defecto (o los congelados, si ya los
  tenía).
- Cada control editable lleva un candado dorado, integrado con el estilo actual.
- El invitado **puede abrir** el selector de avatares, de banners y de emotes y verlos todos.
  Al **elegir** uno, aparece el diálogo de cuenta: *"Creá tu cuenta para quedarte con este
  avatar."* Chocar con la puerta después de ver adentro convierte muchísimo mejor que un botón
  que no existe.
- Arriba, una franja fija con el estado: *"Jugás como invitado. Creá tu cuenta para
  personalizar tu perfil, elegir tu nombre y tener tu número."* y el botón que ya existe.
- El bloque de cuenta que ya está en [ProfileActivity.kt:246](../../../app/src/main/java/com/traidores/juego/ProfileActivity.kt:246)
  se queda donde está; sólo cambia el texto, porque ahora vincular **desbloquea** en vez de
  solamente respaldar.

**Un detalle que ya estaba esperando esto:** el logro `profile_created` dice *"Te registraste en
Traidores y dejaste tu nombre grabado en el pueblo"*, pero hoy se otorga con
`AchievementTracker.ensureProfileOpened`, o sea con sólo abrir la pantalla. Pasa a otorgarse al
registrarse de verdad, que es lo que el texto ya decía.

---

## 7. Al registrarse: qué gana y qué conserva

**Conserva absolutamente todo**, y eso hay que decirlo en la interfaz porque es el miedo real
del jugador. Técnicamente sale gratis: `linkWithCredential` mantiene el mismo uid y el perfil
vive en `SharedPreferences`, así que no hay nada que migrar.

**Gana**, en este orden de mensaje:

1. Su nombre propio.
2. Personalización completa: avatar, banner, bio, rol favorito, logros destacados, emotes.
3. Su número `#`.
4. Crear sus propias salas y ser anfitrión.
5. Que nada de eso dependa de este celular.

### 7.1 Un agujero que hay que tapar en el mismo movimiento

El texto de la app ya promete *"conservás tu perfil y tu número aunque cambies de celular"*
(`profile_account_linked_toast`), y **hoy eso es falso a medias**. Cuando el correo ya existía,
`adoptExistingAccount` cambia el uid y recupera el `#`
([AccountLink.kt:94](../../../app/src/main/java/com/traidores/juego/AccountLink.kt:94)) — pero
**no baja el perfil visual**. Avatar, banner, bio y rol favorito quedan los del dispositivo
nuevo, aunque estén guardados en `perfiles_publicos/{uid}`.

Es el caso "cambié de celular", justo el que la función existe para resolver. El arreglo es
chico: en `recoverPublicId`, además del `publicId`, leer `nombrePerfil`, `bioPerfil`,
`avatarPerfil`, `bannerPerfil` y `rolFavoritoPerfil` del documento y escribirlos en
`SharedPreferences` antes de renderizar. Los datos ya están en el servidor; sólo no se leen.

---

## 8. Salas "sólo cuentas registradas"

Se desprende de la decisión de que el invitado pueda entrar a salas públicas: un invitado
baneado de una sala vuelve reinstalando, porque el uid es nuevo. No es evitable mientras el
invitado tenga acceso al online público.

**Propuesta:** un interruptor al crear la sala, **SÓLO CUENTAS REGISTRADAS**, apagado por
defecto. Cuando está encendido, `jugadores` create exige `isRegistered()` en esa sala.

Resuelve tres cosas de una: le da al anfitrión una herramienta real contra el griefer que
reinstala, hace que las partidas "en serio" sean un espacio donde el baneo funciona de verdad,
y agrega un motivo más para registrarse que no es cosmético. En el navegador de salas se
muestra con un candado, y al invitado que intenta entrar se le explica por qué no puede.

---

## 9. Recomendación aparte: entrar con Google

No estaba en las preguntas, pero es la palanca de conversión más grande que hay en Android y es
gratis. Correo y contraseña en un juego significa inventar una contraseña más; Google Sign-In
es un toque. Costo: la dependencia, configurar las huellas SHA-1 en Firebase y la pantalla de
consentimiento. Conviven sin problema: quien quiera correo, sigue teniendo correo.

Queda como recomendación, no como parte de las fases. Si lo aprobás, entra como §A.4.

---

## 10. Fases

| Fase | Qué entra | Tamaño |
|------|-----------|--------|
| **F1 — Cimientos** | `isRegistered()` en reglas y en cliente, `getIdToken(true)` tras vincular, nombre de invitado asignado y validado en el servidor, `#` sólo para registrados | mediano |
| **F2 — Perfil bloqueado** | Candados, previsualización sin guardar, franja de estado, textos nuevos, logro `profile_created` al registrarse | mediano |
| **F3 — Poderes de sala** | El invitado no crea salas ni es anfitrión estable (reglas), escalón de 20 s en el handoff de gameplay (cliente) | chico, delicado |
| **F4 — Continuidad** | Recuperar el perfil visual al entrar con una cuenta existente (§7.1) | chico |
| **F5 — Opcional** | Salas sólo para cuentas (§8), alias del pueblo (§4), Google Sign-In (§9) | a decidir |

**Orden obligatorio:** F1 antes que todo. F3 depende de F1, porque sin `isRegistered()` en las
reglas el filtro de anfitrión es sólo del cliente y se saltea.

### Criterios de aceptación

- Un invitado abre el perfil: ve todo, no puede guardar nada, y **no se reserva ningún `#`**
  (verificable en la consola: `meta/public_ids` no se mueve).
- Un invitado entra a una sala por código y juega una partida completa, con emotes y chat.
- Un invitado intenta crear una sala: no puede, ni desde la interfaz ni forzando la escritura.
- El anfitrión se cae en mitad de la partida con registrados vivos: el relevo lo toma un
  registrado. Si no queda ninguno, a los 20 segundos lo toma un invitado y **la partida sigue**.
- Un tester que ya tenía avatar y bio los sigue viendo después de actualizar, y no los puede
  cambiar.
- Ese mismo tester se registra: no pierde nada, gana el nombre y el `#`, y puede editar.
- Instalar en un celular nuevo y entrar con un correo existente devuelve el `#` **y** el avatar,
  el banner y la bio.

---

## Lo implementado (25 de julio de 2026)

Cambios de identidad y perfil:

- `GuestIdentity.kt` (nuevo): lista cerrada de 12 alias, numero de 4 digitos derivado del uid
  sin tocar el contador global, y el mismo patron de validacion que usan las reglas.
- `PlayerProfileStore.loadHumanProfile` y `PlayerPublicIdentity.profileName` devuelven el alias
  cuando no hay cuenta. Son los dos unicos lugares donde se resuelve el nombre, asi que las
  pantallas de online pasaron a usarlos en vez de leer la preferencia directo.
- `PlayerPublicIdentity.ensurePublicId` no reserva `#` para invitados, y `publicProfileFields`
  omite el campo cuando esta vacio: las reglas aceptan que no exista, pero rechazan una cadena
  que no sean digitos.
- La frase del perfil viaja vacia si no hay cuenta. Era el unico texto libre que quedaba —
  avatar, banner y rol favorito salen de catalogos cerrados.
- `AccountLink.refreshClaims` fuerza `getIdToken(true)` al vincular, y `recoverPublicId` ahora
  baja tambien el perfil visual: era la promesa de "conservas tu perfil aunque cambies de
  celular" que estaba a medio cumplir.

Perfil bloqueado (`ProfileActivity`):

- Candado dorado en vez de lapiz en cada control, salvo el nombre: el alias si se elige.
- El invitado puede recorrer los catalogos de avatar y banner; el corte esta al elegir.
- `saveChanges` no escribe nada para un invitado. Es importante: escribiria el nombre derivado
  como nombre propio y quedaria "Aguafiestas 4821" el dia que se registre.
- En el lugar del `#` dice `INVITADO` en vez de `#SIN ID`, que parecia un error del juego.

Poderes de sala:

- `partidas` create exige `isRegistered()`; el boton explica el motivo antes de que las reglas
  lo rechacen.
- `stableLobbyHostTransfer` exige cuenta a quien escribe **y** `publicId` en el documento de
  quien recibe la sala, porque en el traspaso del anfitrion saliente son personas distintas.
- El cliente filtra candidatos en el lobby (`canBeLobbyHost`) y en gameplay escalona:
  `OnlineLobbyRules.needsHostHandoff` se separo de `hostHandoffCandidate` justamente para poder
  distinguir "no hace falta relevo" de "hace falta y no hay a quien darselo".

### Verificacion

`scripts/test-firestore-rules-invitados.cjs` (nuevo, aparte del suite general para no chocar
con el trabajo en curso). Cubre: crear sala, entrar a sala, los 12 alias, nombre libre, alias
inventado, alias sin numero, `publicId` forjado, frase con texto, `nombreSala` distinto del
alias, marcarse listo, y el traspaso de anfitrion en las tres variantes. Incluye el caso del
**recien vinculado** (token con proveedor `anonymous` pero con correo), que es el que fallaria
si las reglas miraran una sola señal.

```bash
npm run test:firestore-rules-invitados
```

Necesita un JDK 11 o superior; el de Android Studio sirve
(`JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`).

Salas solo para cuentas (§8):

- Campo `soloCuentas` en la sala, interruptor al crear, apagado por defecto. **Inmutable
  despues de creada**: `activeHostCanUpdateRoom` lo pinea. Prenderlo con gente adentro dejaria
  a los invitados que ya estaban en una sala donde el servidor les rechaza todo.
- Se hace valer en el alta de `jugadores/{uid}`. No hace falta revisarlo en cada update porque
  el campo no cambia, y `isRegistered()` va primero en la condicion para que una cuenta no
  pague nunca esa lectura extra.
- En el navegador la sala aparece con candado y "Solo cuentas"; al invitado que la toca se le
  explica y se le ofrece ir al perfil. Lo mismo al entrar por codigo.

### Pendiente

- Compilar en Android Studio: no se ejecutan builds desde acá por convencion del proyecto, asi
  que el Kotlin esta sin compilar.
- El nombre que viaja al chat de RTDB no esta atado al de Firestore, asi que un cliente
  modificado todavia podria firmar un mensaje con otro nombre. Es anterior a esta spec y se
  cierra con App Check.
