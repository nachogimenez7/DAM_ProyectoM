# Spec — Loadout de emotes en el Perfil

> Handoff Claude → Codex. Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.
> Feature pedida por el usuario (excepción justificada al "no agregar funciones": es un pedido directo).

## Objetivo
Que el jugador **elija 4 emotes** (de los 12 disponibles) desde el **Perfil**, los vea ahí, y esos 4 sean los que puede usar en partida. Todos los 12 están disponibles desde el inicio (sin desbloqueo). Los bots **siguen usando sus emotes temáticos** por rol/mapa (el loadout es solo del humano).

## Estado actual (contexto)
- Los emotes son "reactions". Hoy están definidos **inline** en `GameplayMockActivity` como 3 sets de 4: `defaultReactionSpecs` (griego), `medievalAssassinReactionSpecs`, `gauchoDetectiveReactionSpecs` (líneas ~111-127). `ReactionSpec(key, imageRes, label, toneHex)`.
- Hoy el set se elige **solo** por mapa+rol en `reactionSpecsFor(player)` (~línea 3206). El jugador NO elige.
- La paleta en partida (`showReactionPalette`, ~2844) itera `reactionSpecsFor(humanPlayer)` y crea un botón por spec. Cada slot es independiente → un loadout con emotes de la misma emoción convive sin problema.
- El Perfil (`ProfileActivity`) ya tiene el patrón a espejar: **logros destacados** (elegir N) — `draftProfile.achievements: List<String>`, persistido en `PREF_ACHIEVEMENTS` (join con separador), render en 3 vistas, selector `showAchievementsSelector()` (~487). Y selectores `showAvatarSelector()`/`showBannerSelector()` con botones `editAvatar`/`editBanner`.
- Persistencia del perfil: `SharedPreferences` namespace `TraidoresPrefs` (`PREFS_NAME`).

---

## TAREA 1 — Catálogo único de emotes (`EmoteCatalog.kt`)
Crear `app/src/main/java/com/traidores/juego/EmoteCatalog.kt` como **única fuente de verdad** de los 12 emotes. Mover ahí las definiciones (hoy inline en gameplay).

```kotlin
data class EmoteSpec(
    val id: String,          // único: "griego_enojado", etc.
    val emotionKey: String,  // "angry" | "sad" | "happy" | "suspicious"
    val imageRes: Int,
    val label: String,
    val toneHex: String,
    val themeKey: String,    // "griego" | "medieval_asesino" | "gaucho_detective"
    val themeLabel: String   // "Aldeano griego" | "Asesino medieval" | "Detective gaucho"
)
```

Los 12 (id → emotionKey → drawable → label → tono → tema):
- **Aldeano griego** (`griego`): `griego_enojado`/angry/`reaction_angry`/"Enojado"/#C7442E · `griego_triste`/sad/`reaction_sad`/"Triste"/#5486B7 · `griego_contento`/happy/`reaction_happy`/"Contento"/#D9A53A · `griego_sospechoso`/suspicious/`reaction_suspicious`/"Sospechoso"/#8D6B33
- **Asesino medieval** (`medieval_asesino`): `medieval_enojado`/angry/`reaction_assassin_medieval_angry` · `medieval_triste`/sad/`reaction_assassin_medieval_sad` · `medieval_contento`/happy/`reaction_assassin_medieval_happy` · `medieval_sospechoso`/suspicious/`reaction_assassin_medieval_suspicious` (mismos labels/tonos por emoción)
- **Detective gaucho** (`gaucho_detective`): `gaucho_enojado`/angry/`reaction_detective_gaucho_angry` · `gaucho_triste`/sad/`reaction_detective_gaucho_sad` · `gaucho_contento`/happy/`reaction_detective_gaucho_happy` · `gaucho_sospechoso`/suspicious/`reaction_detective_gaucho_suspicious`

API del objeto:
- `EmoteCatalog.all: List<EmoteSpec>` (los 12, en orden por tema).
- `byId(id): EmoteSpec?`
- `byTheme(): Map<String, List<EmoteSpec>>` (para la grilla agrupada).
- `defaultLoadoutIds: List<String>` = los 4 `griego_*` (equivale al set default actual).
- `LOADOUT_SIZE = 4`.

---

## TAREA 2 — Persistencia del loadout
Guardar los 4 ids elegidos en el mismo namespace del perfil (`TraidoresPrefs`), clave `PREF_EMOTE_LOADOUT`, ids unidos por un separador (ej. `"|"`), igual que `PREF_ACHIEVEMENTS`.

Helper para leer desde cualquier Activity (perfil y gameplay):
```kotlin
object EmoteLoadout {
    // Devuelve exactamente 4 ids válidos; completa con defaults si faltan/son inválidos.
    fun selectedIds(context: Context): List<String>
    fun save(context: Context, ids: List<String>)
    fun selectedSpecs(context: Context): List<EmoteSpec> // byId sobre selectedIds
}
```
Validación: descartar ids desconocidos y **rellenar hasta 4** con `defaultLoadoutIds` sin duplicar. Si no hay nada guardado → `defaultLoadoutIds`.

---

## TAREA 3 — Perfil: apartado que muestra los 4 + selector
**En `ProfileActivity` (espejar el patrón de logros):**
- Agregar `emoteLoadout: List<String>` al data class del perfil (draft/saved).
- En `loadProfile`: leer `EmoteLoadout.selectedIds(this)`.
- En `persist()`: guardar `PREF_EMOTE_LOADOUT` (join). Incluirlo en el copy de `savedProfile` y en el descarte de edición (como achievements).
- Render: 4 `ImageView` (emoteOne..emoteFour) con `setImageResource(EmoteCatalog.byId(id).imageRes)`.
- Botón `editEmotes` (solo activo en modo edición, como `editAvatar`/`editBanner`) → `showEmoteSelector()`. Sumarlo a la lista de `editButtons` que se muestran/ocultan al editar.

**`showEmoteSelector()` — la "ventana grande":**
- `AlertDialog` con vista custom (`dialog_emote_selector.xml`): título "Elegí tus 4 emotes", contador "X/4", grilla de los **12 agrupados por tema** (3 bloques con su `themeLabel`, 4 emotes c/u), Cancelar/Aplicar.
- Cada emote = imagen tappable con estado seleccionado (borde dorado/check). Tocar alterna selección; máximo 4 (si ya hay 4 y tocás otro, o bien bloqueás con Toast "Ya elegiste 4" o reemplazás el más viejo — a elección, simple).
- "Aplicar": si hay exactamente 4 → `draftProfile.emoteLoadout = elegidos` (en orden), `renderProfile()`, dismiss. Si no → Toast "Elegí 4 emotes".
- Mantener orden de selección para que el loadout tenga orden estable.

---

## TAREA 4 — Partida: usar el loadout del humano
**En `GameplayMockActivity`:**
- Reconstruir los sets de emotes desde `EmoteCatalog` (evitar duplicar definiciones). Los sets temáticos de bots = `EmoteCatalog.byTheme()["medieval_asesino"]`, `["gaucho_detective"]`, `["griego"]`. `ReactionSpec` se arma desde `EmoteSpec` (key = emotionKey, imageRes, label, toneHex).
- `reactionSpecsFor(player)`:
  - **Humano** → `EmoteLoadout.selectedSpecs(this)` mapeado a `ReactionSpec` (sus 4 elegidos).
  - **Bots** → set temático actual por mapa/rol (sin cambios de comportamiento).
- El resto (paleta, burbujas, bots) queda igual: la paleta ya itera la lista y cada slot es independiente, así que loadouts con emociones repetidas funcionan. Si algún punto usa `key` como identificador único, usar `id`; la semántica de emoción sigue en `emotionKey`.

---

## TAREA 5 — Layouts
- `activity_profile.xml`: nuevo apartado "Emotes" (cerca de rol favorito/logros) con fila de 4 `ImageView` (`emoteOne..emoteFour`) + botón `editEmotes` (mismo estilo que `editBanner`). Respetar identidad medieval/dorada; touch targets ≥48dp.
- `dialog_emote_selector.xml`: contenedor con título, contador, la grilla agrupada (puede ser `GridLayout`/`RecyclerView`; con 12 ítems un `GridLayout` alcanza) y zona de botones. Fondo de panel oscuro dorado consistente.

---

## Online / "públicos" (futuro, NO ahora)
Los emotes son públicos (todos ven cuáles usás). En **local** solo aplica al humano (los bots son NPCs). Para **online** (experimental) habría que sincronizar `emoteLoadout` en el perfil público del jugador — dejar el loadout accesible pero **no** implementar sync online en esta tanda.

## Verificación
- [ ] En el Perfil (modo edición) aparece el apartado de emotes con 4 slots y botón editar.
- [ ] El selector muestra los 12 agrupados por tema; se eligen exactamente 4; se guardan y persisten al reabrir la app.
- [ ] En partida, la paleta del humano muestra sus 4 elegidos (aunque no coincidan con su rol/mapa).
- [ ] Los bots siguen mostrando emotes temáticos.
- [ ] Un loadout con dos emotes de la misma emoción (ej. dos "contento") funciona sin romper.

## Docs a actualizar al cerrar (Claude)
- `docs/general/05-estructura-proyecto.md` (nuevo `EmoteCatalog.kt`, dialog nuevo).
- `docs/general/02-mecanicas.md` o doc de perfil/personalización (loadout de emotes).
- `docs/desarrollo/backlog.md`.
