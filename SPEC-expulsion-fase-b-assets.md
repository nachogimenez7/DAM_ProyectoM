# SPEC — Expulsión Fase B: assets nuevos (bota, polvo, sello)

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). **La Fase A ya está implementada** en `VoteResultAnimator.kt` (easing, anticipación, squash, destello, sacudida, y la carta que sale volando en arco con giro; el sonido `GameSound.EXPULSION` ya se dispara en el frame de impacto vía el callback `onImpact`). Esta Fase B **suma los assets nuevos** sobre esa coreografía ya existente, sin rehacerla.
>
> **Tono acordado con el usuario:** medieval y con peso, con un toque de comedia **física** (que salga volando), pero **NADA de estrellitas/POW ni ruidos de dibujito**. El polvo es tierra, no chispas.
>
> Los assets (PNG transparentes) los genera/provee el usuario; este spec define **qué imágenes hacen falta, con qué nombre/tamaño, y exactamente dónde y cómo se animan** en el código.

## Contexto de la Fase A (puntos de enganche ya existentes)
En `VoteResultAnimator.kt`:
- `kickExpulsionCard(...)` — arma la bota "cargada" y hace la **anticipación** (la carta se afirma) → dispara el golpe de la bota.
- `onExpulsionImpact(...)` — el **frame de contacto**: `onImpact` (sonido+háptico), `playImpactFlash()`, `shakePanel()`, el retroceso de la bota, y el squash de la carta.
- `launchExpelledCard(...)` — la carta **sale volando** (arco + giro) con `AnimatorSet`.
- `revealExpelledRoleBeforeKick(...)` — la revelación del rol (solo si `session.revealRolesOnDeath`).
- La bota es el `ImageView` `voteKickBoot` (`activity_gameplay_mock.xml:1387-1396`, `150dp × 112dp`), ya cableado al animator por el constructor (`GameplayMockActivity.kt:713`).

---

## Asset 1 — Bota en poses (patada real en vez de deslizamiento)

**Imágenes** (mismo estilo/silueta/escala que `ic_kicking_boot`, canvas ~150×112, PNG transparente en `res/drawable-nodpi/`):
- `boot_windup.png` — bota **echada atrás/cargada** (más vertical, talón arriba).
- `boot_strike.png` — bota **extendida** en el momento del golpe (puede ser la actual `ic_kicking_boot`).
- `boot_recoil.png` — *(opcional)* bota en el **retroceso**.

**Wiring** (solo `setImageResource` en los puntos que ya existen; no cambia la coreografía):
1. En `kickExpulsionCard`, donde se prepara la bota ("La bota espera fuera de cuadro, cargada"): `boot.setImageResource(R.drawable.boot_windup)`.
2. En `kickExpulsionCard`, dentro del `withEndAction` de la anticipación, justo antes de `boot.animate()...` del golpe: `boot.setImageResource(R.drawable.boot_strike)`.
3. En `onExpulsionImpact`, antes del `boot.animate()` del retroceso: `boot.setImageResource(R.drawable.boot_recoil)` (o dejar `boot_strike` si no se hace el opcional).

---

## Asset 2 — Polvo / nube de tierra en el impacto

**Imagen:** `impact_dust.png` — nube de **tierra/polvo** terroso (tonos tierra/marrón-grisáceo, coherente con el mapa medieval). PNG transparente, canvas ~140×140. **No** estrellas ni "POW".

**Layout:** agregar un `ImageView` `voteKickDust` al `voteResultOverlay` (después de `voteKickBoot`, `activity_gameplay_mock.xml:1396`), `wrap_content`, `visibility="invisible"`, `contentDescription="@null"`, `src="@drawable/impact_dust"`. Pasarlo al constructor del `VoteResultAnimator` (`GameplayMockActivity.kt:703-717`) como `dust: ImageView` y bindearlo (`voteKickDust = findViewById(R.id.voteKickDust)`).

**Wiring** — en `onExpulsionImpact`, junto a `playImpactFlash()` / `shakePanel()`, posicionar el polvo en el **punto de contacto** (borde de la carta donde pega la bota) y reventarlo:
```kotlin
private fun playImpactDust(holder: VoteCardHolder) {
    val loc = IntArray(2); val ov = IntArray(2)
    overlay.getLocationOnScreen(ov)
    holder.root.getLocationOnScreen(loc)
    val cx = loc[0] - ov[0] + holder.root.width * 0.5f
    val cy = loc[1] - ov[1] + holder.root.height * 0.5f
    dust.visibility = View.VISIBLE
    dust.alpha = 0.9f
    dust.scaleX = 0.4f; dust.scaleY = 0.4f
    dust.translationX = cx - dust.width / 2f
    dust.translationY = cy - dust.height / 2f
    dust.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f)
        .setInterpolator(DecelerateInterpolator()).setDuration(360L)
        .withEndAction { dust.visibility = View.INVISIBLE }.start()
}
```
Llamarla desde `onExpulsionImpact`. Cancelarla/resetearla en `cancelAnimations()` (agregar `dust.animate().cancel(); dust.visibility = View.INVISIBLE`).

*(Nota: el `dust.width` puede ser 0 si el overlay aún no midió el `ImageView`; si pasa, usar un tamaño fijo en dp para centrar en vez de `dust.width`.)*

---

## Asset 3 — Sello de lacre (la sentencia estampada)

**Imagen:** `expulsion_seal.png` — **sello de lacre** medieval (cera roja/oscura con una marca heráldica). Si lleva texto, que diga **"EXPULSADO"** o **"DESTERRADO"** — **evitar "CULPABLE"**: el pueblo puede expulsar a un inocente, así que un veredicto de culpa sería incorrecto. PNG transparente, canvas ~96×96.

**Layout/creación:** agregar el sello como **hijo de la carta de expulsión** para que vuele con ella. En `createExpulsionCard(...)` (`VoteResultAnimator.kt`), agregar al `portrait` (o al `root`) un `ImageView` con `impact_dust`... perdón, `expulsion_seal`, `visibility = GONE`, guardado en el `VoteCardHolder` (agregar campo `seal: ImageView`).

**Wiring** — estampar el sello al inicio de `kickExpulsionCard` (durante la anticipación, "sentencia pronunciada, después la patada"). Funciona en ambos modos (con o sin revelación de rol):
```kotlin
private fun stampSeal(holder: VoteCardHolder) {
    val seal = holder.seal
    seal.visibility = View.VISIBLE
    seal.alpha = 0f
    seal.rotation = -18f
    seal.scaleX = 2.1f; seal.scaleY = 2.1f
    seal.animate().alpha(1f).rotation(-6f).scaleX(1f).scaleY(1f)
        .setInterpolator(OvershootInterpolator(1.6f)).setDuration(240L).start()
}
```
Como el sello es hijo de la carta, la `AnimatorSet` de `launchExpelledCard` lo arrastra al salir volando: no hay que animarlo aparte. Resetear (`GONE`, alpha/scale) en `cancelAnimations()` y al crear cada carta nueva.

---

## Asset 4 (opcional) — Líneas de velocidad al salir volando
`speed_lines.png` (PNG transparente, estelas). Un `ImageView` detrás de la carta que aparece al arrancar `launchExpelledCard` y se desvanece rápido. Baja prioridad; solo si el usuario lo quiere.

---

## Verificación manual (Android Studio, el usuario)
- La bota cambia de pose (cargada → golpe → retroceso), no se desliza plana.
- En el impacto revienta una nube de **tierra** (no estrellas) sincronizada con el sonido y la sacudida.
- El sello de lacre se estampa sobre la carta y **sale volando junto con ella**.
- El sello **no** dice "CULPABLE"; dice "EXPULSADO"/"DESTERRADO" o es una marca heráldica.
- Nada de esto rompe los modos con/sin revelación de rol (`revealRolesOnDeath`) ni la corrupción del Alcalde.
