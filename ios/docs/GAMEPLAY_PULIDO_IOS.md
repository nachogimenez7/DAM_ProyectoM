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

## Comparación con videos de partida (3/10)

El usuario grabó la misma partida en los dos teléfonos (Pampa, 8 jugadores, Asesino). Faltas en iOS, en el orden acordado:

1. **Anuncios de la noche con sonido** (`DeathRevealAnimator`, `NoDeathRevealAnimator`, `SilenceRevealAnimator`, tarjetas narrativas de `ChronicleFeedPresenter` en el chat). iOS muestra carteles simples sin animación ni sonido.
2. **Recuento de votos** (`VoteResultAnimator`): recuento con un sello por voto → «Mayoría alcanzada» → «Expulsión» → «Sentencia del pueblo» con lacre → «X fue expulsado». iOS muestra solo «El pueblo decidió».
3. **Sonido de partida** (música de día/noche por mapa, efectos, victoria).
4. **Rueda de opciones en la cabecera** (`AccessibilityOptionsDialog`: música, efectos, vibración, tamaño de texto, salir de la partida).
5. Votación con «TU VOTO ✓», «Voto registrado» y «Listos para votar».
6. Roles que faltan (antes que emotes).
7. Emotes en partida y pulido del chat (atajos, «N mensajes nuevos», tarjetas de sucesos, «está escribiendo…»). El chat de iOS con burbujas ya es mejor base que el de Android.
8. Al final: conversación de los bots.

Sin prioridad por ahora: nombre y color de estilo del perfil en el panel inferior.

### Avance (3/10)

- **Marco de eventos**: los paneles de evento usan el 9-patch actual de Android (`ui_frame_event_*`, exportado con `Scripts/export_nine_patch_frames.swift`) dibujado por `RevealPanel` con sus dos bandas elásticas por eje; reemplaza el marco cuadrado anterior.
- **Anuncios del amanecer** (`DawnRevealViews.swift`): muerte (temblor, destello rojo, manchas, «ROL OCULTO» o volteo de la carta si se revelan roles; CONTINUAR a los 2,9 s y cierre solo a los 9 s más), silencio (jaula y candado dibujados como los vectores de Android) y «El pueblo respira» con el sol. Con VoiceOver no se cierran solos.
- **Sonido de partida** (`GameAudio.swift`, archivos en `Resources/GameAudio` copiados con `Scripts/prepare_game_audio.py`): música de día por mapa, de noche y de victoria; se pausa en transiciones y anuncios y vuelve a 1,6 s de la transición, como `MusicManager`. Efectos de reparto, anochecer, amanecer, muerte, silencio, nadie murió, voto, desempate y expulsión.
- **Rueda de opciones** (Codex): engranaje en la cabecera con música, efectos, vibración, tamaño de texto y salir de la partida.
- **Recuento de votos** (`VoteCeremonyView.swift`): sellos que caen uno a uno, mayoría/empate, expulsión con lacre, bota que patea la carta y resultado; avanza solo a los 8 s.
- Diferencia deliberada: el texto «No puede hablar ni votar durante el día» usa `secondary` en lugar de `text_muted` de Android, que no alcanza contraste 4,5:1 sobre el panel.

## Punto de partida para la próxima sesión (3/10)

Estado al cerrar el commit `ios: dawn reveals, match audio, vote ceremony and table options`:

**Verificado en simulador (iPhone 17, iOS 27):** anuncio de muerte (grabado cuadro por cuadro), «El pueblo respira», marco 9-patch de Pampa y Grecia, música/efectos (se oyen en el simulador), recuento con sellos y «Mayoría alcanzada», expulsión completa hasta «X FUE EXPULSADO». Suite `LocalLobbyUITests` 20/20 **antes** de agregar `VoteCeremonyView`.

**Pendiente de verificar (primero):**
1. Correr `LocalLobbyUITests` completa otra vez: `testVotingReviewSnapshotAndRecount` ahora recorre la ceremonia (`table.voteCeremony`, `table.voteContinue`) y no se ejecutó todavía.
2. Repetir la patada en simulador: tras la última corrección el marco se dibuja como fondo (`RevealPanel`), así que la bota y la carta voladora deben pasar **por encima** del marco, y el hueco de la carta se cierra al terminar. Revisar con `recordVideo` + hoja de cuadros (ver `CLAUDE.md`).
3. Ver el anuncio de silencio (jaula) con un Mercenario en partida; no salió en las pruebas manuales.
4. Instalar en el iPhone 13 para oír el audio real (volumen relativo música/efectos, que la música vuelva tras cada anuncio) y confirmar que el ladrido sigue bien.
5. Accesibilidad de lo nuevo: AX5 en los tres anuncios y en el recuento (hay `ViewThatFits` → `ScrollView`), VoiceOver (con VoiceOver los anuncios y el recuento no avanzan solos), Reducir animaciones de la app.

**Cómo está armado:**
- `Platform/GameAudio.swift`: `GameAudio` en el entorno desde `LocalMatchFlow`; `LocalTableView.desiredMusic(_:)` decide la pista (nil = pausa con fundido). Efectos con `playEffect(_:)`; el impacto de la expulsión lo dispara `VoteCeremonyView.onImpact`.
- `Features/LocalGame/DawnRevealViews.swift`: `RevealFrameArt`/`RevealPanel` (marco por mapa, también lo usan `LocalEventCard`, el resultado privado y los compañeros traidores) y las tres vistas de amanecer. `DawnAnnouncement` guarda ids de jugador.
- `Features/LocalGame/VoteCeremonyView.swift`: cubre las fases `voteCount` y `result` sin ganador; cada botón llama a `performPrimaryAction` de la mesa. Avanza solo a los 8 s salvo con `-ui-testing` o VoiceOver.
- La rueda de opciones (`TableOptionsPanel` en `LocalGameView.swift`) reemplazó la flecha de salir de la mesa.

**Cambios de Android `main` mergeados en `ios-port` (29c01fe, beta 0.1.50) sin portar todavía:** el engranaje de partida muestra «REPORTAR UN PROBLEMA» (abre el diálogo de comentarios) también en partida local; Opciones oculta la medición online fuera de debug; perfil y selector de emotes muestran el aviso `beta_cosmetics_notice`; el lobby dice «PRACTICAR CONTRA LA IA» en vez de «MODO DE PRUEBA». Revisar `FeedbackDialog.kt`/`FeedbackQuota.kt` antes de portar el reporte.

**Siguiente en el orden acordado con el usuario:** votación con «TU VOTO ✓», «Voto registrado» y «Listos para votar» → roles faltantes (Alcalde, Desertor, Payador, Oráculo, Bufón) → emotes en partida y pulido del chat → al final, conversación de los bots.

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
