# Spec — Director de Audio + golpes dramáticos (sonido + háptica)

Dos partes: (1) **Lista de audio a conseguir** (para Ignacio) y (2) **Spec de implementación** (para Codex).

Decisiones tomadas: SFX **únicos** para los 3 mapas (la música ya es por mapa); **háptica integrada** en los golpes fuertes; los **archivos los consigue el usuario** (banco libre).

---

## ✅ ESTADO: archivos ya colocados (jun 2026)
El usuario ya generó y dejó los SFX en `app/src/main/res/raw/` (mono, `.mp3`). Se renombraron a nombres de recurso válidos (Android raw solo admite `a-z 0-9 _`; los originales tenían espacios/paréntesis/tildes y **rompían el build**). Archivos finales:
`sfx_night_fall`, `sfx_dawn`, `sfx_elimination`, `sfx_expulsion`, `sfx_silence`, `sfx_vote_cast`, `sfx_tie_break`, `sfx_card_deal` (nuevo, reparto inicial).

**Aclaración elimination vs expulsion:** `sfx_elimination` = muerte (apuñalada + grito; muerte nocturna/general). `sfx_expulsion` = cuando el pueblo **vota y expulsa** a alguien (momento distinto). No son redundantes: son los dos "sabores" de baja.

---

## ✅ ESTADO: sonidos de emote (jul 2026)
Voces cortas estilo Clash Royale (hombre adulto cartoon, secas/limpias), **una por emoción** reutilizada en los tres temas (aldeano/asesino/detective) y en todos los mapas. Archivos en `app/src/main/res/raw/`:
`sfx_emote_happy` (wujuu), `sfx_emote_sad` (llanto), `sfx_emote_suspicious` (mmm de detective), `sfx_emote_angry` (gruñido con dientes apretados).

**Reproducción — canal aparte de `GameplaySoundEffects`:** los emotes usan `EmoteSoundEffects` (nuevo), un **canal único con "el último gana"** (un emote nuevo corta al anterior en vez de apilarse) + **throttle** de ~300ms. Así una avalancha de emotes (p. ej. muchos jugadores online reaccionando juntos) no ametralla ni superpone 15 audios. Respeta efectos on/off + volumen vía `AudioPreferences`.

**Cableado:** disparo único en `GameplayMockActivity.showReactionBubble(...)` resuelto por `spec.key` (`happy/sad/suspicious/angry`) → cubre humano y bots con un solo punto, y queda listo para emotes online cuando se sincronicen. El feedback háptico del emote sigue siendo solo del humano (`GameplayEffect.EMOTE`).

---

# PARTE 1 — Lista de audio a conseguir (para Ignacio) — YA COMPLETADA

Bajar de bancos **libres / CC0** para evitar problemas de licencia en una app publicada: [freesound.org](https://freesound.org) (filtrar License = "Creative Commons 0"), [Pixabay Sound Effects](https://pixabay.com/sound-effects/), [mixkit](https://mixkit.co/free-sound-effects/).

**Specs de cada archivo:**
- Formato: **`.ogg`** preferido (menor peso); mp3/wav también sirven.
- Mono, 44.1 kHz.
- **Cortos** (ver duración por ítem).
- **Loudness pareja** entre todos (normalizar para que ninguno reviente respecto de otro).
- Nombre de archivo en **minúsculas, sin espacios** (solo `a-z 0-9 _`) — usar exactamente el de la tabla.

| Archivo (poner en `app/src/main/res/raw/`) | Momento | Cómo debería sonar | Duración |
|---|---|---|---|
| `sfx_night_fall.ogg` | Cae la noche | **Búho** ululando (grave, nocturno) | 1–2 s |
| `sfx_dawn.ogg` | Amanece | **Gallo** cacareando | 1–2 s |
| `sfx_elimination.ogg` | Muerte nocturna | Golpe grave/sigiloso (campana grave, golpe sordo, último aliento) | 1–2 s |
| `sfx_expulsion.ogg` | Expulsión por votación | **Distinto** de la muerte: multitud/abucheo, portón que se cierra, empujón | 1–2 s |
| `sfx_silence.ogg` | Muteo / silencio | Amordazar / "shhh" reverberado / cadena corta | ~1 s |
| `sfx_vote_cast.ogg` | Voto emitido | Sello de madera / clic seco | 0.3–0.6 s |
| `sfx_tie_break.ogg` | Empate (desempate) | Tensión: redoble corto / doble campanada | 1–2 s |

Opcionales (mejoran, no bloquean):
- `sfx_victory_sting.ogg` — remate corto al ganar (además de la música de victoria). 1–2 s.

**Ya existen y se mantienen** (no hace falta conseguir): `card_shuffle_deal`, `oracle_ability`, `payador_ability`, `jester_victory`, `victory_music`, músicas de día/noche/menú.

> Sugerencia: probá 2–3 candidatos por sonido y elegí el que combine con la estética seria/medieval. Si querés, después te ayudo a elegir entre los que bajes.

---

# PARTE 2 — Spec de implementación (para Codex)

## Objetivo
Centralizar todo el SFX disperso en un único **`GameplayAudioDirector`** que mapea **evento de juego → sonido (+ háptica)**, y cablear los golpes nuevos (búho, gallo, expulsión distinta de muerte, voto, empate). Reemplaza la lógica de sonido duplicada en los animators.

## Estado actual (referencia)
- `GameplaySoundEffects.play(context, res)` — reproductor one-shot que respeta prefs de efectos (volumen/on-off). Lo usan oráculo/bufón/payador.
- Sonido **duplicado** en cada animator (cada uno crea su MediaPlayer, respetando prefs): `DeathRevealAnimator`→`death_reveal`, `SilenceRevealAnimator`→`silence_reveal`, `DayNightTransitionAnimator`→`transition_day/night`, `AssigningRolesActivity`→`card_shuffle_deal`.
- Archivos que **reemplazan** a los viejos al cablear el director (borrar los viejos una vez migrado): `sfx_elimination`↔`death_reveal`, `sfx_silence`↔`silence_reveal`, `sfx_night_fall`↔`transition_night`, `sfx_dawn`↔`transition_day`, `sfx_vote_cast`↔`vote_cast`, `sfx_card_deal`↔`card_shuffle_deal`.
- Orfano de música (sin referencia): `decisive_debate_music.mpeg` → cablear en `MusicManager` o borrar.
- Preferencia de vibración existente: `PREF_VIBRATION_ON` (en `OpcionesActivity`, namespace `TraidoresPrefs`).

## Diseño

**1. `GameplayAudioDirector` (capa semántica)**
```kotlin
enum class GameSound(val res: Int, val haptic: HapticLevel) {
    NIGHT_FALL(R.raw.sfx_night_fall, HapticLevel.LIGHT),
    DAWN(R.raw.sfx_dawn, HapticLevel.LIGHT),
    ELIMINATION(R.raw.sfx_elimination, HapticLevel.STRONG),
    EXPULSION(R.raw.sfx_expulsion, HapticLevel.STRONG),
    SILENCE(R.raw.sfx_silence, HapticLevel.MEDIUM),
    VOTE_CAST(R.raw.sfx_vote_cast, HapticLevel.NONE),
    TIE_BREAK(R.raw.sfx_tie_break, HapticLevel.MEDIUM),
    CARD_DEAL(R.raw.sfx_card_deal, HapticLevel.NONE),   // nuevo sonido del reparto inicial
    ORACLE(R.raw.oracle_ability, HapticLevel.NONE),
    PAYADOR(R.raw.payador_ability, HapticLevel.NONE),
    JESTER(R.raw.jester_victory, HapticLevel.MEDIUM),
}
enum class HapticLevel { NONE, LIGHT, MEDIUM, STRONG }

object GameplayAudioDirector {
    fun play(context: Context, sound: GameSound)   // sonido + háptica, respetando prefs
}
```
- El SFX puede reutilizar internamente `GameplaySoundEffects.play(...)` (no reescribir el manejo de MediaPlayer); el director agrega el **mapeo enum→res** y la **háptica**.
- **Háptica:** disparar `Vibrator`/`VibratorManager` según `HapticLevel`, respetando `PREF_VIBRATION_ON`. Usar `VibrationEffect` (API ≥26) con fallback a `vibrate(ms)` deprecado para API 24-25 (minSdk 24). Intensidades aprox.: LIGHT ~20ms, MEDIUM ~40ms, STRONG patrón corto (p. ej. 0,60,40,80).

**2. Desacoplar sonido de los animators**
- Quitar `playSound()`/MediaPlayer de `DeathRevealAnimator`, `SilenceRevealAnimator`, `DayNightTransitionAnimator` (y el de `AssigningRolesActivity`).
- El **llamador** dispara el golpe al iniciar la animación, eligiendo el `GameSound` correcto. Esto permite **diferenciar muerte vs expulsión** (mismo DeathRevealAnimator, distinto sonido según contexto):
  - Muerte nocturna (AMANECER revela kill) → `ELIMINATION`.
  - Expulsión por votación → `EXPULSION`.
- Transición de fase: noche → `NIGHT_FALL`; amanecer → `DAWN`. (Hoy `DayNightTransitionAnimator` ya distingue período; mover el disparo al llamador o que el animator llame al director con el `GameSound` correcto — elegir lo que menos acople, pero el sonido sale del director.)

**3. Cablear los golpes faltantes**
- `VOTE_CAST` al emitir voto (y revisar el orfano `vote_cast.mp3`: reemplazar por `sfx_vote_cast` o borrar).
- `TIE_BREAK` al entrar en `DESEMPATE_VOTACION` / `ALCALDE_DESEMPATE`.
- `decisive_debate_music.mpeg`: confirmar si se usa; si no, borrar o integrarlo a `MusicManager` como pista de debate decisivo.

**4. Preferencias y limpieza**
- El director respeta efectos on/off + volumen (vía `GameplaySoundEffects`) y `PREF_VIBRATION_ON` para háptica.
- Estandarizar formato de los nuevos a `.ogg`.

## Tarea extra — Volumen relativo por sonido (balance de mezcla)
Para emparejar el volumen percibido entre SFX sin re-editar archivos (cuando uno quedó más fuerte que otro):
- Agregar `relativeVolume: Float = 1f` a `GameSound`.
- `GameplaySoundEffects.play(context, res, volumeScale = 1f)` multiplica `volumeScale` dentro del `effectsVolume` ya aplicado (`player.setVolume(volume * scale, ...)`).
- El director pasa `sound.relativeVolume`.
- **Importante:** `MediaPlayer` NO amplifica (>1f no sube nada). Esto sólo sirve para **atenuar** los que quedaron muy fuertes. Los sonidos bajos (búho, cartas) se suben re-normalizando el archivo en Audacity, no por código.
- Valores finales se ajustan **de oído** (empezar todos en 1f y bajar los que molesten).

## Regla de proceso
- Diff acotado: esta tarea es audio + háptica + el cableado de sus golpes. La "mejora de calidad/seriedad de las animaciones" en sí es una tarea aparte (pulir cada `*Animator`), aunque el golpe (sonido+vibración) ya las hará sentir más serias.
- Si faltan archivos nuevos al implementar, dejar el mapeo apuntando a ellos igual (el usuario los coloca); no inventar sonidos.

## Documentación a actualizar al cerrar (lo hace Claude tras revisar)
- `docs/general/03-arquitectura.md` (capa de audio centralizada).
- `docs/general/05-estructura-proyecto.md` (`GameplayAudioDirector`).
- `docs/general/07-flujo-funcionamiento.md` (golpes de audio/háptica por fase/evento).
- `docs/desarrollo/decisiones-arquitectura.md` (ADR: director de audio + háptica).
- `docs/desarrollo/backlog.md` (D4/audio; orfanos resueltos).
