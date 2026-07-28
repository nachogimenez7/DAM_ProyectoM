# Spec — Google Play Games Services

Integrar Play Games Services (PGS) como identidad principal del juego, más logros, guardado en
la nube y tablas.

Se apoya en [`SPEC-invitado-y-cuenta.md`](SPEC-invitado-y-cuenta.md): esa spec hizo que crear
salas, ser anfitrión y personalizar el perfil requieran cuenta, y dejó la fricción del registro
como su punto débil. PGS es lo que la elimina.

> **Ya verificado (25 de julio de 2026):** una sesión de Play Games pasa las reglas como cuenta
> registrada **sin cambiar una sola línea de `firestore.rules`**. Crea salas, entra a salas
> marcadas "solo cuentas" y usa nombre libre. El caso está fijado en
> `scripts/test-firestore-rules-invitados.cjs`.

## Por qué vale la pena

1. **Fricción cero.** PGS v2 autentica solo, al abrir el juego, sin botón de sign-in
   ([doc](https://developer.android.com/games/pgs/platform-authentication)). Para la mayoría de
   los jugadores de Android, "registrado" pasa a ser el estado por defecto en vez de un
   trámite.
2. **Los baneos valen más.** Renovar una cuenta de Google cuesta bastante más que crear un
   correo descartable, así que el baneo global de
   [`SPEC-seguridad-y-moderacion-online.md`](SPEC-seguridad-y-moderacion-online.md) se vuelve
   una sanción real.
3. **Nombres ya moderados.** El gamertag de Play Games lo modera Google. Resuelve el problema
   de los nombres ofensivos mejor que nuestra lista cerrada de alias.
4. **Ahorra cuota.** El guardado en la nube de PGS no consume lecturas de Firestore, que en el
   plan gratuito topean en 50.000 por día.

## Costo

PGS es gratuito. Lo único que hace falta es la cuenta de Play Console (**US$ 25 por única vez**,
no anual), que Ignacio ya tiene.

---

# PARTE 1 — Para Ignacio (consolas, sin código)

El orden importa. Hay un paso donde la mayoría se equivoca y está marcado.

## 1. Crear el juego en Play Games Services

Play Console → tu app → **Play Games Services** → **Configuración y gestión** → **Configuración**.

> **⚠️ Acá está el error clásico.** Cuando pregunte si el juego ya usa APIs de Google, respondé
> que **sí** y elegí **el mismo proyecto de Google Cloud que usa Firebase** (el del
> `google-services.json`). Si dejás que cree un proyecto nuevo, Firebase Auth y Play Games
> quedan en proyectos distintos y no hay forma de vincularlos después sin rehacer todo.

## 2. Credenciales

Dentro de Play Games Services → **Credenciales**, hacen falta **dos**:

- Una credencial **Android**, con la huella **SHA-1** de tu clave de firma. Vas a querer cargar
  dos: la de **Play App Signing** (Play Console → Integridad de la aplicación) para las
  compilaciones de release, y la de tu **clave de depuración** para poder probar desde Android
  Studio. Sin la de depuración, el sign-in te va a fallar en tu propio celular.
- Una credencial de tipo **Web**, que es la que da el `client ID` y el `client secret`. Play
  Games la necesita para poder entregarle a Firebase un código de autorización de servidor.

## 3. Habilitar el proveedor en Firebase

Firebase Console → **Authentication** → **Sign-in method** → habilitar **Play Games**, pegando
el client ID y el client secret **del cliente Web** del paso anterior.

Esto convive con Correo/contraseña: los dos proveedores quedan activos y el jugador usa el que
le toque.

## 4. Testers

Play Games Services → **Testers**: agregá tu cuenta de Google. Mientras el juego no esté
publicado, **solo los testers pueden iniciar sesión**. Es la otra causa habitual de "no me
funciona y no entiendo por qué".

## 5. Crear logros y tablas

En Play Console se cargan uno por uno. Para los 10 logros que ya existen en
`ProfileCustomizationCatalog` vas a necesitar, por cada uno: nombre, descripción, puntos y un
**ícono de 512×512**. Ese es el trabajo de assets de esta etapa.

Anotá el **ID que Play Console le asigna a cada logro**: son cadenas opacas y hay que mapearlas
contra los IDs internos (§B).

## 6. Probar en un dispositivo real

El sign-in de PGS necesita Google Play Services y una cuenta de Google activa. En un emulador
tiene que ser una imagen con Play Store; en un dispositivo real anda directo.

---

# PARTE 2 — Implementación

Orden obligatorio: **§A antes que todo lo demás**, porque las otras tres cuelgan de la sesión.

## §A. Sign-in automático — la base

**Dependencia** en `app/build.gradle`:

```groovy
implementation 'com.google.android.gms:play-services-games-v2'
```

Conviene fijar la versión vigente al momento de implementarlo; no la dejo escrita acá porque
envejece mal.

**Manifest**, dentro de `<application>`:

```xml
<meta-data
    android:name="com.google.android.gms.games.APP_ID"
    android:value="@string/game_services_project_id" />
```

El valor va **sí o sí como recurso string** (el ID es numérico y si se escribe directo, Android
lo interpreta como entero y la app crashea al arrancar).

**Inicialización** en `TraidoresApplication.onCreate()`, junto a App Check:
`PlayGamesSdk.initialize(this)`.

**Flujo de sesión**, en un objeto nuevo `PlayGamesIdentity.kt` que siga el mismo criterio que
`AccountLink`:

1. `PlayGames.getGamesSignInClient(activity).isAuthenticated()` — PGS ya autenticó solo.
2. Si autenticó, pedir el código de servidor con
   `requestServerSideAccess(webClientId, /* forceRefreshToken = */ false)`.
3. `PlayGamesAuthProvider.getCredential(codigo)` y **vincular, no reemplazar**:
   `currentUser.linkWithCredential(...)` sobre la sesión anónima que ya existe. Igual que con el
   correo, eso **conserva el mismo uid**, así que el `#`, el perfil público y todo el historial
   quedan donde están.
4. **Forzar `getIdToken(true)`.** Esto no es opcional acá: un jugador de Play Games **no tiene
   correo**, así que la única señal que lo distingue de un invitado ante las reglas es
   `sign_in_provider`. Si el token no se refresca, el servidor lo sigue tratando como invitado
   y no puede crear salas. `AccountLink.refreshClaims` ya hace exactamente esto — reutilizarlo.

**Casos que hay que resolver, no son opcionales:**

- **La cuenta de PGS ya pertenece a otro usuario de Firebase** (reinstalación, cambio de
  celular). `linkWithCredential` falla con `FirebaseAuthUserCollisionException` y hay que entrar
  con esa cuenta, igual que hace `AccountLink.adoptExistingAccount`. Ahí el uid cambia y hay que
  recuperar el `#` **y el perfil visual** — la función ya lo hace desde `perfiles_publicos`.
- **El jugador ya vinculó correo y además tiene Play Games.** Se vincula PGS como proveedor
  adicional del mismo uid. No hay conflicto.
- **El jugador rechaza Play Games o no lo tiene.** Sigue el camino de invitado tal cual está
  hoy, con su alias de la lista cerrada. Nada de lo que construimos se cae.

**Nombre del jugador:** usar el gamertag de PGS como nombre de perfil por defecto, recortado a
18 caracteres. Ya viene moderado por Google. El jugador puede cambiarlo después, porque con
cuenta el nombre es libre.

**Criterio de aceptación:** abrir el juego en un celular con Play Games y quedar registrado sin
tocar nada; poder crear una sala inmediatamente; desinstalar, reinstalar y recuperar el mismo
`#`, el mismo avatar y el mismo historial.

## §B. Logros sincronizados

**Mapeo**: los IDs internos (`ProfileCustomizationCatalog.ACH_*`) contra los IDs opacos de Play
Console. Van como recursos string en un `values/play_games_ids.xml`, no hardcodeados en Kotlin,
para que se puedan cambiar sin tocar código.

**Enganche**: `AchievementTracker.unlock()` es el único punto donde se desbloquea algo, así que
alcanza con que además llame a `PlayGames.getAchievementsClient(activity).unlock(idRemoto)`.

Dos detalles:

- El desbloqueo local **sigue siendo la fuente de verdad**. PGS es un espejo: si falla la red,
  el logro igual queda desbloqueado en el juego. Nunca al revés.
- Al vincular la cuenta por primera vez, empujar los logros que el jugador ya tenía
  desbloqueados de invitado. Si no, alguien con 30 partidas se registra y su perfil de Play
  Games aparece vacío.

## §C. Guardado en la nube

Play Console → Play Games Services → habilitar **Partidas guardadas**.

Se guarda un único snapshot con el perfil (`TraidoresPrefs`) y el historial de
`MatchHistoryStore`, serializado como JSON.

**Lo más delicado de las cuatro partes es el conflicto**: dos celulares con la misma cuenta
generan dos versiones. Regla propuesta, simple y defendible: **gana la que tenga más partidas
jugadas**; a igualdad, la más reciente. Es determinista y no requiere que el jugador decida
nada.

Cuándo guardar: al terminar una partida y al salir del perfil. No en cada cambio.

## §D. Tablas y amigos

**Tablas**: crear en Play Console al menos "Victorias totales" y "Partidas jugadas".
`submitScore` desde `MatchHistoryStore.record()`, que ya es el lugar donde se cierra una partida.

**Amigos**: `PlayGames.getPlayersClient(activity).loadFriends(...)`. Requiere que el jugador
otorgue permiso con un diálogo del sistema, y ese permiso se puede negar — la interfaz tiene que
funcionar igual sin la lista.

Esto reemplaza la idea de "agregar amigos por `#`" que estaba pendiente: sale gratis, ya está
moderado y no hay que construir invitaciones ni solicitudes.

---

## Qué NO resuelve

Para que no se confunda con lo que sigue pendiente:

- **No reemplaza a App Check.** Son cosas distintas: PGS dice quién sos, App Check dice que la
  petición viene de tu APK.
- **No cierra el secreto de roles.** Eso sigue necesitando el reparto por jugador (§F de la
  spec de seguridad).
- **No sirve fuera de Android.** Es una limitación explícita de Play Games.
- **No sube el techo de 100 conexiones simultáneas** de Realtime Database, que sigue siendo el
  primer muro para un lanzamiento público.

## Orden sugerido

| Fase | Depende de | Tamaño |
|------|-----------|--------|
| §A Sign-in | Partes 1.1 a 1.4 de la consola | mediano |
| §B Logros | §A + los 10 íconos y los IDs de Play Console | chico |
| §C Guardado en la nube | §A | mediano, delicado por los conflictos |
| §D Tablas y amigos | §A | chico |

La primera fase es la que cambia el juego; las otras tres son mejoras que se pueden hacer de a
una y en cualquier orden.
