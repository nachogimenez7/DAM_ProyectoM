# Gameplay contra IA — auditoría y plan de pulido iOS

1 de octubre de 2026. Pedido del usuario: retomar el gameplay local (prioridad 3 del plan) para que se vea lindo, fluido y con animaciones, parecido a Android y aprovechando lo propio de iOS. El recorrido online previo a la partida (prioridad 2) sigue pendiente; el gameplay online no se toca.

Referencia Android: build `0.1.49` (`83318f3`) en emulador Pixel; capturas de lobby, reparto de rol y primera noche. Referencia iOS: `LocalGameView.swift` (≈3.150 líneas) y `TraidoresCore`.

## Diagnóstico

| # | Hallazgo | Prioridad |
|---|---|---|
| 1 | **La partida iOS no tiene sonido.** Android usa música de día por mapa (`day_music_*`), música de noche, música de victoria por bando y 14 efectos (`sfx_card_deal`, `sfx_night_fall`, `sfx_dawn`, `sfx_vote_cast`, `sfx_elimination`, `sfx_expulsion`, `sfx_no_death`, `sfx_tie_break`, habilidades de Payador/Oráculo…), más 10 sonidos de emotes (varios en OGG, que hay que convertir). iOS solo incorpora la música del menú y el ladrido. | Alta |
| 2 | Sin hápticos en ninguna acción. | Alta — **hecho en el bloque 1** |
| 3 | La transición día/noche movía sol y luna en arco aunque Reducir movimiento estuviera activo. | Alta — **hecho** |
| 4 | El temporizador era solo un número; Android muestra una barra que se vacía bajo la cabecera. | Media — **hecho**, con dígitos animados y urgencia en los últimos 5 s |
| 5 | Botones de cabecera de 30 pt (bajo el mínimo de 44 pt). | Media — **hecho** (zona táctil de 44 pt sin cambiar el dibujo) |
| 6 | Muerte y revelación sin animación: la mancha de sangre y la carta revelada aparecen de golpe. | Media |
| 7 | Votación: el recuento no anima conteos ni destaca al expulsado. | Media |
| 8 | Anuncio del amanecer sin vínculo visual con la carta de la víctima. | Media |
| 9 | `LocalGameView.swift` concentra todo en un archivo; el compilador ya no puede inferir tipos en la cabecera sin dividirla. | Media (deuda técnica) |
| 10 | `LocalGameStore` guarda en `UserDefaults.standard` incluso en pruebas UI. | Baja |

## Pedido del usuario sobre la IA (1/10)

Prioridad del rediseño de bots: **conversación normal y fluida**. Lo que más molesta (en Android) es que los bots se pregunten a sí mismos o digan cosas sin sentido; en iOS los bots repiten la misma frase todos a la vez. No portar la IA de Android tal cual: diseñar primero ritmo, turnos, a quién le habla cada bot (nunca a sí mismo), memoria de lo dicho y respuestas coherentes al humano; después implementar y probar con partidas simuladas.

## Avance de roles

Espía (desde 10 jugadores) y segundo Asesino (desde 13, preset recomendado de Android) en el motor y la partida; cartas y nombres según el mapa. Faltan Alcalde, Desertor y los exclusivos de mapa (Payador, Oráculo, Bufón).

## Bloques

1. **Hecho:** hápticos nativos (`sensoryFeedback`: objetivo elegido, cambio de período, últimos 5 s, fin de partida), Reducir movimiento en la transición, temporizador con barra y dígitos `numericText`, anillo de selección con resorte, zonas táctiles de 44 pt.
2. **Sonido de partida:** `GameAudio` con `AVAudioPlayer`, con el volumen multimedia como Android, las opciones y la interrupción en segundo plano; música de día/noche por mapa con fundido cruzado; efectos de reparto, anochecer, amanecer, voto, eliminación y victoria. Copiar los archivos con `prepare_menu_assets.py` y registrarlos en el manifiesto; convertir los OGG a AAC con `afconvert` solo si macOS los lee, o con otra herramienta acordada.
3. **Animaciones de momentos clave:** volteo 3D de la carta al revelar el rol (`rotation3DEffect`), mancha de sangre que se expande, recuento de votos con `numericText` y fichas que viajan al acusado, amanecer que nace de la carta de la víctima (`matchedGeometryEffect`). Todo con alternativa de fundido cuando Reducir movimiento está activo.
4. **Propio de iOS:** latido de Core Haptics durante la noche para el rol activo, `symbolEffect` en iconos de fase, sombreadores de SwiftUI (`layerEffect`) para el fundido de tinta del día a la noche. Solo después de 2 y 3.
5. **Deuda técnica:** dividir `LocalGameView.swift` por pantallas (lobby, reparto, mesa, chat, resultados) antes de agregar más animaciones; aislar el guardado de las pruebas UI.

Pruebas de gameplay (1/10): las del Médico, el Detective y la transición nocturna ya fallaban desde `95acf9f`. Causas: la noche 1 ahora hace esperar a los otros roles («ESPERAR» → «SALTAR NOCHE»), las pruebas tocaban la mesa mientras la transición la cubre y, a veces, el toque en EMPEZAR se perdía durante la entrada animada del panel. Arreglo: EMPEZAR acepta toques recién cuando el panel terminó de entrar (también protege al jugador), y las pruebas esperan a que los elementos sean tocables. Suite completa: 19/19.

Verificación de cada bloque: partida completa en simulador con texto por defecto y AX5, Reducir movimiento activado, pruebas UI de gameplay existentes y prueba en el iPhone 13.
