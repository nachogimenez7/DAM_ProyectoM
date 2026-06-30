# Spec — Visual del log del chat (el cronista)

> Handoff Claude (diseño) → Codex. Código = fuente de verdad. Diff acotado; el usuario valida apariencia en Android Studio. Construye sobre el feed unificado ya implementado (`GameplayChatController`).

Decisiones tomadas (con el usuario): **banners anchos con ícono+color por tipo**, feed colapsado muestra **4-5 entradas**, estética **equilibrada** (temática pero legible). Hay un mockup de referencia (Claude lo mostró en chat).

## 1. Banners de "Dios" (sucesos) — ícono + color por tipo
El controller ya renderiza filas de evento con `backgroundColor` (`GameplayChatController` ~`:658`/`:813`). Extender a **banner ancho** con:
- **Ícono por tipo** (a la izquierda) + **color/tono por tipo** (fondo tenue + borde + texto legible) + **texto en serif** (`@font/bree_serif`, la "voz" del cronista), distinto del chat (sans).
- Borde completo (no border-left suelto) y `cornerRadius` ~8dp.

Mapa tipo → ícono → tono (los íconos: usar drawables simples del proyecto o glifos; el mockup usa Tabler solo de referencia):

| Suceso | Ícono | Tono |
|---|---|---|
| Cae la noche (NIGHT_FALL) | luna | azul noche |
| Amanecer sin muerte | sol | ámbar cálido |
| Muerte nocturna (ELIMINATION) | calavera | rojo |
| Expulsión por voto (EXPULSION) | martillo/maza | dorado (distinto del rojo de muerte) |
| Silencio (SILENCE) | mute | púrpura/gris |
| Empate (TIE_BREAK) | balanza | dorado tensión |

- **Votos individuales** ("X votó a Y"): NO usar banner ancho (saturaría). Mostrarlos como **línea fina** (texto tenue centrado o con ícono chico), o agrupados. Sólo los hitos (noche/amanecer/muerte/expulsión/empate/silencio) son banners.
- Reusar los tonos ya existentes (`GameplayActionTone`/paleta) en vez de hardcodear hex nuevos donde se pueda.

## 2. Separadores "DÍA N"
- Línea + label dorado centrado: `— DÍA 1 —`, `— DÍA 2 —`… El código ya hace `"DIA ${round}"` (`:714`); estilizarlo como separador (líneas a los lados, letra dorada espaciada) e insertarlo cuando cambia el día/ronda dentro del feed. (Si todavía se ve "DIA N", es build viejo.)

## 3. Nombres con color (identidad única)
- Los nombres de jugador en el chat usan `PlayerChatColor` (ya existe). Confirmar consistencia carta ↔ feed (es la única fuente de color).

## 4. Feed ambiental (colapsado): 4-5 entradas
- Subir de 3 a **4-5** entradas visibles (mezcla suceso+chat). Ajustar `CHAT_AMBIENT_MAX_MESSAGES` (hoy 3) → 5 y validar que no tape paneles superiores/inferiores ni cartas laterales.

## 5. Estética / marco por mapa (equilibrado)
- Usar los `bg_chat_box_grecia/medieval/pampa` como marco del feed, **sutil** (legibilidad primero). Translúcido sobre el mapa.
- Contraste suficiente del texto sobre el marco; serif para sucesos, sans para chat.

## 6. Detalles
- **Estado vacío:** si no hay mensajes aún, mostrar "El pueblo aún no habló." (en vez de vacío).
- **Scroll:** si el usuario está leyendo arriba y llega un mensaje, no saltar bruscamente al fondo (reusar la lógica de "mensajes nuevos" ya existente).
- Tamaños respetan `appliedGameplayTextScale` (setting de tamaño de texto).

## Documentación a actualizar al cerrar (Claude)
- `docs/general/07-flujo-funcionamiento.md` (cronista: banners por tipo, separadores, feed 4-5).
- `docs/desarrollo/decisiones-arquitectura.md` (ADR-10: detalle visual del cronista).
- `docs/desarrollo/backlog.md`.
