# Spec — Fixes de prueba + assets de imagen por mapa

> Handoff Claude (review/diseño) → Codex. Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio. Surge de probar la build.

Orden: **Tarea A (URGENTE) → B → C → D → E (assets)**.

---

## TAREA A (URGENTE) — Revertir la ventana de acción a su tamaño compacto
La ventana **"INFORMACIÓN PRIVADA / VÍCTIMA ELEGIDA"** (al elegir a quién matar) quedó **enorme**. Antes era una **card compacta** y así estaba bien — el usuario solo quería decorarla, NO agrandarla.

- Layout: `privateFeedbackPanel` es `360dp × wrap_content` centrado (`activity_gameplay_mock.xml:206-212`) — eso está OK.
- **Causa probable:** `privateFeedbackPanel.setBackgroundResource(background)` (~`GameplayMockActivity.kt:2694`) le aplica un **fondo temático grande** (el del chat-box). 
- **Fix:** que la ventana vuelva a ser una **card chica que abraza su contenido** (wrap_content real, ancho ~300-320dp). Usar un fondo de panel **compacto** (su `bg_translucent_game_panel` o un panel temático pequeño), **no** el fondo del chat-box. Mantener la barra de tono, el botón **CONTINUAR** y la decoración sutil. **No agrandar.**
- Aplicar el mismo criterio a cualquier overlay de info privada que se haya inflado.

---

## TAREA B — Sacar todos los beeps sintéticos (ToneGenerator)
El "pim" agudo (al aparecer la carta y en otros toques) viene de `GameplayEffects` usando `ToneGenerator` (beeps del sistema, suenan baratos).
- Quitar **todos** los tonos `ToneGenerator` de `GameplayEffects.play(...)` (SELECT, PANEL, REVEAL, CONFIRM, ERROR, CHAT, COUNTDOWN).
- **Conservar la háptica/vibración** de `GameplayEffects` (eso queda).
- Los sonidos reales siguen saliendo por `GameplayAudioDirector` (SFX). Resultado: sin beeps sintéticos; solo SFX reales + vibración.
- Verificar que ningún flujo dependiera del beep para feedback (si algo quedaba "mudo", que use vibración o un SFX real existente).

---

## TAREA C — Nombres de cartas con color de jugador
En el debate, los **nombres bajo las cartas no se distinguen por color** (foto 3).
- Aplicar `PlayerChatColor.colorFor(nombre, session)` al `holder.name` de las cartas laterales, **visible** (con sombra/contraste si hace falta para legibilidad).
- Es la **misma fuente** que el chat → color de identidad consistente carta ↔ feed. (Estaba pendiente del spec del cronista.)

---

## TAREA D — Sacar el sello de cera del fondo del chat
La "mancha roja" en el chat (foto 4) es el **sello de cera** del `bg_chat_box_medieval`.
- Quitar el sello de cera del **fondo del chat/log** (que el marco quede limpio y legible en los 3 mapas).
- Reservar el sello de cera como elemento de los **overlays de sucesos** (Tarea E), no del feed.

---

## TAREA E — Assets de imagen por mapa (generados por Codex)
Para el log y los overlays de sucesos, **generar imágenes reales** por mapa con el generador de imágenes (en vez de shapes programáticos). Requisitos técnicos para que funcionen en Android:
- PNG con **transparencia**; **centro vacío/translúcido** (se ve el mapa detrás) y la decoración en **bordes/esquinas**.
- Preparar como **9-patch (`.9.png`)** o como **marco con zona segura** estirable, para que escale sin deformar. Sin texto dentro de la imagen.
- Resolución generosa (ej. ~1024px lado mayor) para densidades altas.
- Mantener legibilidad: contraste suficiente detrás del texto.

Prompts sugeridos (ajustar a gusto). Estilo general: histórico, oscuro, acentos dorados, sobrio, sin texto.

**Marco del log/feed (sutil, por mapa):**
- Pampa: "marco decorativo de cuero gastado y madera rústica, esquinas con tientos, paleta marrón cálido y dorado tenue, centro transparente, estilo sobrio".
- Grecia: "marco de mármol claro con greca/meandro dorado en los bordes, centro transparente, elegante y limpio".
- Medieval: "marco de madera oscura con herrajes de hierro en las esquinas, centro transparente — SIN sello de cera".

**Overlays de sucesos (más dramáticos, por mapa) — muerte / expulsión / víctima:**
- Pampa: "marco rústico de madera y cuero, tonos polvo y sangre seca, dramático pero sobrio, centro oscuro translúcido".
- Grecia: "marco de mármol y oro con motivo de laurel, centro oscuro translúcido, solemne".
- Medieval: "marco de madera y hierro con un sello de cera roja en una esquina, centro oscuro translúcido".

> El death reveal actual (foto 2) ya quedó bien — usar estos marcos para reforzar, sin romper lo que funciona.

---

## Verificación
- La ventana de acción vuelve a ser chica y decorada (no full-screen).
- No suena ningún "pim" sintético; la vibración sigue.
- Los nombres de las cartas se ven con el color de cada jugador.
- El chat no tiene la mancha de cera.
- Los marcos/overlays usan imágenes temáticas por mapa, estirables y legibles.

## Documentación a actualizar al cerrar (Claude)
- `docs/general/05-estructura-proyecto.md` (nuevos assets/drawables por mapa).
- `docs/general/07-flujo-funcionamiento.md` (sin beeps; overlays temáticos).
- `docs/desarrollo/decisiones-arquitectura.md` (ADR: assets generados por mapa; audio sin ToneGenerator).
- `docs/desarrollo/backlog.md`.
