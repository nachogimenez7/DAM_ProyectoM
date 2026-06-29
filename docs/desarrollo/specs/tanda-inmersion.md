# Spec — Tanda inmersión: fix Payador + botones, cronista/feed, audio

> Handoff Claude (diseño/review) → Codex (implementación). Código = fuente de verdad. Codex arranca en frío: leer todo. Diff acotado por tarea; el usuario valida compilación/apariencia en Android Studio.

Orden sugerido: **Tarea A (bug, prioritaria) → Tarea D (audio, archivos ya listos) → Tarea B (cronista) → Tarea C (tips, opcional)**.

---

## TAREA A — Bug del Payador (Contrapunto) + pulido de botones

### A.1 Bug funcional (prioritario)
**Repro:** partida de 15 jugadores, el humano es **Payador**. En `DIA_DEBATE` debe **señalar a 2 jugadores** para iniciar el Contrapunto. **No pasa nada** al intentar señalar (ni la selección ni el botón "SEÑALAR" responden).

**El motor SÍ lo soporta** (no tocar el motor salvo que sea la causa):
- `GameEngine.chooseContrapuntoPlayer(session, targetName)` (`GameEngine.kt:501`): en `DIA_DEBATE` y `!payadorUsed`, valida (vivo, no el propio payador, no repetido), agrega a `contrapuntoPlayers`; con el **2º** seleccionado pasa a `CONTRAPUNTO` (`payadorUsed=true`).
- `resolveHumanTargetAction` rama `DIA_DEBATE` (`GameEngine.kt:1028-1030`) → `chooseContrapuntoPlayer`; `isValidContrapuntoTarget` (`GameEngine.kt:1753`).

**Dónde está el problema (UI, `GameplayMockActivity`):** el camino de selección/confirmación para el Payador en `DIA_DEBATE` no dispara `performTargetAction`/`chooseContrapuntoPlayer`. Codex debe confirmar cuál de estas es la causa (probable combinación):
- **Botón deshabilitado:** `btnAction.isEnabled` (`~2666`) exige `!mustWaitForPhaseTimer()`. Si `DIA_DEBATE` "espera el timer", el botón queda inerte salvo que haya `selectedAction`. Verificar que la selección de un objetivo de Contrapunto habilite el botón (cuenta como `selectedAction`/`specialDecision`).
- **Cartas no seleccionables:** que el tap en una carta en `DIA_DEBATE` (Payador) realmente llegue a `performTargetAction` → `canActOnTarget` (debe ser true vía la rama `DIA_DEBATE` payador). Hoy las cartas muestran el badge de acción (truncado "CONTRAP...") pero el tap parece no registrar.
- **Selección de 2 pasos:** tras el 1º pick, `performTargetAction` hace `clearSelection()`; confirmar que se puede elegir el 2º y que el feedback "Falta un participante" aparece.

**Comportamiento esperado:** el Payador humano toca **2 jugadores vivos** (≠ él) en `DIA_DEBATE` → inicia Contrapunto; opción **"VOTAR SIN USAR"** para saltarlo; feedback claro tras el 1er señalamiento. Agregar/ajustar un **test** del flujo humano de Contrapunto si es viable.

### A.2 Pulido de botones de acción / señalar
- El badge de acción en las cartas laterales muestra **"CONTRAP..." truncado** → usar una etiqueta corta y legible (p. ej. **"SEÑALAR"** o un ícono) que entre en el badge.
- Mejorar el botón **"SEÑALAR"** (`btnAction` con ese label): darle **color/tono propio** distinguible (hoy usa el tono genérico de `applyPrimaryActionVisual`). Agregar un tono dedicado en `GameplayActionTone` (o un estilo) para Contrapunto/Señalar, coherente con la identidad dorada.
- Revisar contraste/legibilidad de los botones de acción en general (el usuario quiere "mejorarlos").

---

## TAREA B — Cronista / decoración del feed (inmersión)
Sobre el feed unificado ya implementado (chat + eventos de Dios). Objetivo: que se sienta un **cronista teatral** y "invite a entrar".

1. **Banners de "Dios" con peso:** ícono por tipo de suceso (☠ muerte, ⚖ voto/expulsión, 🌙 noche, ☀ amanecer) + **color por tono** (muerte rojiza, reutilizar `GameplayActionTone`/paleta) + entrada animada (que el suceso "caiga", no solo aparezca).
2. **Tipografía:** sucesos de Dios en serif (`@font/bree_serif`); chat de jugadores en la fuente casual → se distinguen de un vistazo.
3. **Encabezado temático por mapa:** un título del feed según `mapKey` ("El cronista del feudo" / "…de la polis" / "…del pueblo").
4. **Fondo por mapa:** usar bien los drawables ya existentes `bg_chat_box_grecia/medieval/pampa` en el feed; translúcido pero legible.
5. **Separadores "Día N":** divisor dentro del feed al cambiar de ronda/día → ayuda a leer el historial sin perder el hilo.
6. Mantener: filtro "Todo / Solo sucesos", feed ambiental (mezcla), expandido con composer, reglas de solo-lectura (eliminado/silenciado).

---

## TAREA C — Tips contextuales arriba (opcional, bienvenido)
- En la barra superior (subtítulo de fase), mostrar un **tip contextual por fase/rol**.
- **Reutilizar contenido existente:** `RoleCatalog.advice(roleKey)` ya tiene consejos por rol — usarlos para el tip del rol del jugador (ej. "Sos Médico: elegí a quién proteger"). No duplicar textos.
- Tip por fase para roles sin acción (ej. debate: "Compará quién acusó a quién").
- Mantener la barra **slim** (no fusionar con el feed).

---

## TAREA D — Audio director + háptica (archivos ya listos)
Detalle completo en `docs/desarrollo/specs/audio-director.md`. Resumen para implementar:

**Archivos ya en `raw/`** (mp3, mono, nombres válidos): `sfx_night_fall`, `sfx_dawn`, `sfx_elimination` (muerte), `sfx_expulsion` (votado por el pueblo), `sfx_silence`, `sfx_vote_cast`, `sfx_tie_break`, `sfx_card_deal`.

1. **`GameplayAudioDirector`** con `enum GameSound(res, haptic)` → centraliza el SFX (puede reutilizar `GameplaySoundEffects` por dentro) y agrega **háptica** (Vibrator, respetando `PREF_VIBRATION_ON`; `VibrationEffect` API≥26 con fallback). Niveles: ELIMINATION/EXPULSION=STRONG, SILENCE/TIE=MEDIUM, NIGHT_FALL/DAWN=LIGHT, VOTE/CARD_DEAL=NONE.
2. **Sacar el sonido de los animators** (`DeathRevealAnimator`, `SilenceRevealAnimator`, `DayNightTransitionAnimator`) y del reparto; el **llamador** dispara el `GameSound` correcto. Esto permite **diferenciar muerte nocturna (`ELIMINATION`) de expulsión por voto (`EXPULSION`)**.
3. **Cablear golpes nuevos:** `NIGHT_FALL` (búho) al caer la noche, `DAWN` (gallo) en `AMANECER`, `VOTE_CAST` al votar, `TIE_BREAK` en `DESEMPATE_VOTACION`/`ALCALDE_DESEMPATE`, `CARD_DEAL` (`sfx_card_deal`) en el reparto (`AssigningRolesActivity`, reemplaza `card_shuffle_deal`).
4. **Limpieza:** borrar los viejos que reemplazan los nuevos (`death_reveal`, `silence_reveal`, `transition_day/night`, `vote_cast`, `card_shuffle_deal`); revisar `decisive_debate_music.mpeg` (orfano).

---

## Documentación a actualizar al cerrar (lo hace Claude tras revisar)
- `docs/general/03-arquitectura.md` (audio centralizado / cronista).
- `docs/general/05-estructura-proyecto.md` (`GameplayAudioDirector`).
- `docs/general/07-flujo-funcionamiento.md` (golpes de audio/háptica; cronista; fix Payador).
- `docs/desarrollo/decisiones-arquitectura.md` (ADR audio director).
- `docs/desarrollo/backlog.md` (bug Payador resuelto; D4/audio; orfanos).
