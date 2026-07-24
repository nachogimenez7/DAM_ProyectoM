# Guía de testeo manual — modo local

> Esta guía es **para el usuario (Ignacio)**, para jugar de forma estructurada y sacar a la luz los bugs de UI que ningún test automático atrapa. No es un handoff a Codex. La lógica del motor (`GameEngine`) tiene 161 tests y está sólida; lo que NO tiene cobertura automática es toda la capa de interacción (`GameplayMockActivity`, 6.280 líneas) — y ahí es donde salieron TODOS los bugs serios de las últimas sesiones (el del Alcalde, el catastrófico de la noche que se saltaba). Por eso jugar a fondo es hoy lo de mayor valor antes de pasar al online.

## Cómo forzar tu propio rol (para no depender del azar)

En el Lobby de modo local, hay un botón **"ROL: X"** que cicla entre AZAR y cada rol específico (ASESINO, COMISARIO, MÉDICO, ALCALDE, PAYADOR, BUFÓN, ORÁCULO, DESERTOR, ESPÍA, ALDEANO). Tocalo hasta que diga el rol que querés probar, y esa partida te lo asigna a vos.

**Importante:** ese botón **solo aparece en un build de debug** — o sea, corriendo la app directo desde Android Studio (Run ▶), no en una release firmada. Si no lo ves, revisá que estés en build de debug y en modo local (no online). Algunos roles piden un mínimo de jugadores (el botón lo indica con "(N+)") — sumá bots si hace falta.

## Matriz mínima de partidas

La idea es cubrir los 3 mapas (cada uno tiene su rol exclusivo) y los roles menos verificados. Con **6 partidas** cubrís lo esencial:

| # | Mapa | Tu rol forzado | Por qué |
|---|------|----------------|---------|
| 1 | **Pampa** | PAYADOR | Rol exclusivo de pampa y el **menos verificado** de los tres — foco en que el Contrapunto sume el voto extra y NO delate que sos el Payador. |
| 2 | **Medieval** | BUFÓN | Rol exclusivo medieval — foco en que ganás si te **votan** (no si te matan de noche), y probá el camino de expulsión por **desempate del Alcalde**. |
| 3 | **Grecia** | ORÁCULO | Rol exclusivo griego — foco en que invocar un muerto NO revela tu identidad y que el invocado puede hablar pero no votar. |
| 4 | cualquiera | ALDEANO | Rol sin acción nocturna — foco en el bug que arreglamos: que **SÍ entres a la noche**, veas "ESPERAR" ~3.5s y recién ahí aparezca "SALTAR NOCHE". |
| 5 | cualquiera | ALCALDE | Foco en el split VOTAR / "Revelarme": que puedas votar sin revelarte, y que "Revelarme" desaparezca cuando ya elegiste a quién votar. |
| 6 | cualquiera | ASESINO | Partida "normal" desde el lado traidor — verificar el flujo de matar de noche, los reveals de muerte, y que el juego llegue a una condición de victoria. |

Si tenés tiempo para más, repetí una partida completa en cada mapa con rol AZAR, solo para ver el conjunto integrado.

## Checklist por fase (mirar en cada partida)

**Arranque / carta de rol**
- [ ] La carta de tu rol se muestra bien y "EMPEZAR" la cierra sin quedarse trabada.

**Transición día↔noche**
- [ ] Aparece el cartel "NOCHE N" / "DÍA N" al cambiar de fase.
- [ ] NO se ve un flash del mapa de la fase anterior al terminar la transición.

**Noche**
- [ ] Entrás a la noche de verdad (no salta directo al día), incluso con roles sin acción.
- [ ] Si tu rol tiene acción (asesino/médico/policía/etc.), la UI de elegir objetivo funciona y el resultado se aplica.
- [ ] Si tu rol NO tiene acción: ves "ESPERAR" apagado un momento, después "SALTAR NOCHE" habilitado, y saltar te lleva de una al amanecer.

**Amanecer / reveals**
- [ ] Si murió alguien: aparece el reveal de muerte (carta a la izquierda, texto a la derecha, con el marco del mapa).
- [ ] Si NO murió nadie: aparece la animación del sol ("EL PUEBLO RESPIRA / Nadie murió esta noche").
- [ ] Si hubo un silenciado: se anuncia correctamente.

**Día / chat**
- [ ] El chat central con textura de pergamino se ve bien y legible en **ese** mapa (mármol griego, madera medieval, símbolos patrios en pampa).
- [ ] Los bots comentan los sucesos de forma coherente (no repiten líneas idénticas, no se acusan a sí mismos).

**Emotes / reacciones (nuevo)**
- [ ] El botón de emotes (carita dorada, arriba) abre el selector de 4 reacciones.
- [ ] Al reaccionar: aparece la burbuja sobre tu carta, suena el efecto, y queda la línea en el chat.
- [ ] Los bots también reaccionan a los sucesos de forma coherente.

**Votación / empates**
- [ ] Podés votar tocando una carta; si sos Alcalde, podés votar SIN revelarte.
- [ ] Un empate abre la segunda votación restringida a los empatados.
- [ ] El desempate del Alcalde (si está vivo y revelado) funciona.

**Fin de partida**
- [ ] El juego llega a una condición de victoria clara y muestra la pantalla de ganador con sus datos.

## Zonas frágiles conocidas (mirar con lupa)

De las últimas sesiones, estos son los puntos donde ya aparecieron bugs — vale la pena prestarles atención extra:
1. **Roles sin acción nocturna** (Aldeano/Payador/Alcalde): que la noche NO se salte sola. Ya lo arreglamos, pero es el bug que más volvió.
2. **Alcalde**: que VOTAR y "Revelarme" no queden ambos activos a la vez con un objetivo ya elegido.
3. **Consistencia vertical**: revisá que gameplay, chat y ventanas entren correctamente en teléfonos bajos y con texto grande.
4. **Los 3 mapas**: el chat de pergamino y los marcos de reveal se ven distinto en cada mapa (mármol claro vs madera oscura) — confirmá legibilidad en los tres, no solo en uno.

## Cómo reportar lo que encuentres

Para cada bug, contame lo más concreto posible: **qué mapa, qué rol tenías, en qué fase pasó, y qué esperabas vs qué pasó**. Una captura del momento ayuda muchísimo (las capturas fijas nos vinieron mejor que los videos para diagnosticar). Con eso lo rastreo en el código y armamos la corrección.
