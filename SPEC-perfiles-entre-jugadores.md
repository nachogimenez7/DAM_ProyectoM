# SPEC — Perfiles de jugador visibles entre jugadores (lobby + partida)

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). **Feature nueva** (diseño acordado con el usuario). Objetivo: que un jugador pueda **ver el perfil de otro** — en el lobby (perfil completo) y en la partida (mini-perfil). Se estrena en local (con bots), pero el modelo está pensado para el **online**, que es el foco del juego.
>
> **Decisiones tomadas con el usuario:**
> 1. Perfiles de bots: **híbrido** (roster escrito a mano para los bots recurrentes + generación random estable para el resto).
> 2. Stats de bots: **falsas pero coherentes** (estables por nombre, para que se sientan como jugadores reales).
> 3. Mini-perfil en partida: **tap si la carta no es accionable + mantener presionado si lo es** (no rompe la selección de objetivo).

## Contexto y desafío de fondo
- Hoy el perfil es **uno solo**: el del humano, en `SharedPreferences` (`ProfileActivity.loadProfile`, `ProfileActivity.kt:183-214`).
- `GamePlayer` (`GameModels.kt:104-114`) NO tiene datos de perfil — solo `name`/`initial`/`role`/estado. Por eso ningún jugador puede "ver" el de otro: no existe el dato.
- Ya existe infraestructura reutilizable: `ProfileRoleCatalog` (fotos por rol/mapa + encuadre), `ProfileCustomizationCatalog` (banners, logros), `EmoteCatalog`/`EmoteLoadout`, `PlayerPublicIdentity` (ID público, anclaje del online), y `alignAvatarToFocus` (`ProfileActivity.kt:959`, el encuadre tipo retrato del avatar).
- Los bots locales tienen nombres fijos: `defaultBots` (`GameModels.kt:387-402`): Thiago, Mora, Lautaro, Valen, Rami, Juli, Santi, Mili, Toto, Agus, Bruno, Lola, Fede, Cata.
- El lobby ya tiene un `showPlayerProfile(index, player)` (`LobbyActivity.kt:2273-2291`) que hoy abre un `AlertDialog` con 3 líneas (estado/tipo/mapa). Ese es el que se reemplaza por el perfil completo.

---

## Parte 1 — Modelo de datos `PlayerProfile` + fuentes

### 1.1 Data classes (nuevo archivo `PlayerProfile.kt`)
```kotlin
data class PlayerProfile(
    val name: String,
    val publicId: String,
    val bio: String,
    val avatarKey: String,        // clave de ProfileRoleCatalog
    val bannerKey: String,        // clave de ProfileCustomizationCatalog
    val favoriteRoleKey: String,
    val featuredAchievementIds: List<String>,
    val emoteIds: List<String>,
    val stats: PlayerStats
) : Serializable

data class PlayerStats(
    val matches: Int,
    val wins: Int,
    val hasProgress: Boolean      // false => la UI muestra "--" (como el perfil del humano hoy)
) : Serializable {
    val winRatePercent: Int
        get() = if (matches > 0) ((wins * 100.0) / matches).toInt() else 0
}
```

### 1.2 Dónde vive
Agregar a `GameSession` (`GameModels.kt`) un campo:
```kotlin
val playerProfiles: Map<String, PlayerProfile> = emptyMap()  // clave = GamePlayer.name
```
Se elige un mapa en la sesión (y no un campo en `GamePlayer`) para mantener `GamePlayer` liviano y que los perfiles viajen con la sesión por Intents/Bundles como el resto del estado. Son ~15 perfiles chicos (unos KB), sin problema de tamaño de Binder.

### 1.3 Fuente: humano
Extraer la lógica de `ProfileActivity.loadProfile` (`ProfileActivity.kt:183-214`) a un objeto compartido `PlayerProfileStore.loadHumanProfile(context): PlayerProfile`, y hacer que **tanto `ProfileActivity` como el nuevo store** la usen (no duplicar). Reusa las mismas claves/helpers: `PREF_NAME` (fallback `OpcionesActivity.PREF_PLAYER_NAME`), `PlayerPublicIdentity.currentPublicId`, `PREF_BIO`, `PREF_AVATAR`, `PREF_BANNER` (via `ProfileCustomizationCatalog.normalizeBannerKey`), `PREF_FAVORITE_ROLE`, `PREF_ACHIEVEMENTS`, `EmoteLoadout.selectedIds`. Stats del humano: por ahora `hasProgress = false` (muestra "--"); cuando exista tracking real de partidas se rellena de verdad (fuera de alcance de este spec).

### 1.4 Fuente: bots (híbrido) — `BotProfileFactory`
Nuevo `BotProfileFactory.profileFor(name: String): PlayerProfile`:
- **Roster a mano** (`BotProfileRoster`, un `Map<String, PlayerProfile>` o builders): perfiles escritos para los 14 nombres de `defaultBots`. Cada uno con bio con carácter, `avatarKey` (elegir de `ProfileRoleCatalog.entries`), banner, rol favorito, 2-3 logros destacados (`ProfileCustomizationCatalog.achievements[].id`), emotes. El usuario puede pulir los textos/elecciones exactas después; dejar 2-3 ejemplos completos y el resto con valores razonables.
- **Fallback random estable**: para cualquier nombre fuera del roster, generar determinísticamente por nombre. Usar una semilla estable derivada del nombre (p. ej. `name.hashCode()` acotado, o un `stableSeed(name)` local) para indexar pools de bio/avatar/banner/rol/logros/emotes. **Mismo nombre ⇒ mismo perfil siempre.**
- **Stats falsas pero estables** (`botStatsFor(name)`): `matches` en un rango plausible (p. ej. 20–400) y `winRate` 35–65%, ambos derivados de la semilla del nombre; `wins = round(matches * rate)`; `hasProgress = true`. Estables por nombre.

### 1.5 Rellenar la sesión
Donde se crea la sesión local (`GameModels.createSession`, `GameModels.kt:404`, y/o el builder del modo local), poblar `playerProfiles`: el humano via `PlayerProfileStore.loadHumanProfile`, cada bot via `BotProfileFactory.profileFor(bot.name)`. (En online, ese mismo mapa se rellenará desde Firestore — ver Parte 5.)

---

## Parte 2 — Vista de perfil completo (lobby)

Reemplazar el `AlertDialog` de `showPlayerProfile` (`LobbyActivity.kt:2273-2291`) por una **vista de perfil completo de solo lectura, en landscape** (el lobby es landscape; no usar la `ProfileActivity` que es portrait).

- Nuevo layout `view_player_profile.xml` (overlay/panel landscape) que **reusa la estética y las secciones de `activity_profile.xml`** pero **sin los íconos de edición** (`ProfileEditIcon`): banner, avatar (con el mismo encuadre — extraer `alignAvatarToFocus` a un util compartido y reusarlo, o replicarlo), nombre, `#publicId`, bio, ESTADISTICAS (partidas/victorias/% o "--" si `!hasProgress`), ROL FAVORITO, EMOTES, LOGROS DESTACADOS.
- Controlador `PlayerProfileView`/dialog que recibe un `PlayerProfile` y bindea todo. Renderiza logros via `ProfileCustomizationCatalog.achievementById`, banner via `ProfileCustomizationCatalog.banner`, foto+encuadre via `ProfileRoleCatalog.find(avatarKey)`, emotes via el mismo lookup que usa el perfil.
- `showPlayerProfile` pasa a resolver el `PlayerProfile` del jugador tocado (`session.playerProfiles[player.name]`) y mostrar esta vista. Para **tu propia** entrada: o abrís esta vista de solo lectura con un botón "EDITAR PERFIL" que va a `ProfileActivity`, o vas directo a `ProfileActivity`. Recomendado: la misma vista + botón "EDITAR" solo cuando es el humano.

---

## Parte 3 — Mini-perfil (partida)

En `bindSidePlayerCard` (`GameplayMockActivity.kt:4226`), el listener de la carta (`holder.root.setOnClickListener` en `GameplayMockActivity.kt:4362-4377`):
- **`isActionable`** → sigue seleccionando objetivo (igual que hoy).
- **Rama `else` (carta viva NO accionable, `GameplayMockActivity.kt:4375`)** → en vez de solo `GameplayEffect.ERROR`, **abrir el mini-perfil** de esa carta. Ese es el tap hoy desperdiciado.
- **Agregar `setOnLongClickListener`** en `holder.root` que abra el mini-perfil **siempre** (incluso si la carta es accionable o si el jugador está muerto). Así podés ver el perfil sin interferir con votar/matar.
- La rama de carta muerta (`!isAlive`, `GameplayMockActivity.kt:4371`) puede mantener el Toast en tap, pero el long-press igual abre el perfil (un muerto también tiene perfil).

Mini-perfil UI: overlay/dialog compacto (landscape) con avatar (encuadre reusado), nombre, `#publicId`, bio, y stats (partidas/victorias/%). Lee de `session.playerProfiles[name]`. Controlador `PlayerProfileMiniView(profile: PlayerProfile)`. Cerrar con tap fuera / back (engancharlo al manejo de overlays existente, `GameplayMockActivity.kt:1018` en adelante).

---

## Parte 4 — Verificación manual (Android Studio, el usuario)
- **Lobby:** tocar un bot muestra su **perfil completo** (banner, foto encuadrada, bio, stats coherentes, rol favorito, emotes, logros), no el AlertDialog viejo. Tocar tu propia entrada muestra tu perfil (con opción de editar).
- **Partida:** tocar una carta cuando **no** podés accionar sobre ella abre el **mini-perfil**; cuando **sí** podés accionar, el tap selecciona objetivo y **mantener presionado** abre el mini-perfil. Long-press sobre un muerto también abre su perfil.
- **Bots del roster** (Thiago, Mora, etc.): perfil escrito a mano, consistente entre partidas.
- **Bots fuera del roster / stats:** perfil random pero **estable** — el mismo nombre muestra siempre el mismo perfil y las mismas stats.
- El perfil del humano se ve igual que en su pantalla de perfil (mismo encuadre de foto, mismos datos).

---

## Parte 5 — Nota de diseño para el online (NO implementar aún)
`PlayerProfile` está pensado para **viajar por Firestore anclado al `publicId`** (ya reservado en el esquema "para perfil y futuros amigos", `docs/firebase-online-schema.md`, via `PlayerPublicIdentity`). En online, cada jugador de la sala publicará su `PlayerProfile` (o su `publicId` y el cliente lo resuelve), y el mapa `session.playerProfiles` se rellenará desde Firestore **en vez de** desde `BotProfileFactory`. Las vistas de las Partes 2 y 3 son **idénticas** local u online — esa es toda la gracia del diseño. Mantener el modelo serializable y las fuentes desacopladas de la UI para que el online sea un drop-in.

## Orden de entrega sugerido
1. Parte 1 (modelo + fuentes) — base, sin UI visible todavía.
2. Parte 2 (perfil completo en lobby) — primer resultado visible.
3. Parte 3 (mini-perfil en partida).
4. Parte 5 queda documentada para la etapa online.
