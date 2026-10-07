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

## Avance (3/10, tarde)

- **Ritmo de anuncios y recuento:** solo la opción «Reducir animaciones» del juego los simplifica (como la transición día/noche); el Reducir movimiento de iOS ya no los muestra de golpe. Los votos caen de a uno cada ~0,75 s; silencio y «El pueblo respira» quedan 1 s más en pantalla.
- **Votación como Android:** «TU VOTO ✓» en verde sobre la carta elegida, botón «TU VOTO: X», «Podés cambiar hasta el cierre» y cierre 3 s después del primer voto. En el debate, «VOTAR ANTES EN N · x/y» los primeros 10 s y luego «LISTOS PARA VOTAR · x/y»; los bots se suman de a uno y al estar todos pasa a la votación.
- **Lobby desde cero** (Codex): siempre 5 jugadores y opciones por defecto (sin revelar roles, con votos individuales); solo se recuerda el mapa. Sin «VER ÚLTIMO RESULTADO»; una partida terminada se descarta.
- **Opciones en partida:** tarjeta centrada como el diálogo de Android (sin desplazamiento en tamaño normal) con «REPORTAR UN PROBLEMA» (abre Comentarios/errores). Se presenta como modal propio (`fullScreenCover`): la mesa se redibuja cada segundo y, dentro de ella, la auditoría y VoiceOver perdían los elementos.
- Accesibilidad corregida: chat con estilos de texto que escalan (antes 12 pt fijos); con «Tamaño del texto: Normal» el tamaño del sistema pasa directo; botones enmarcados desde el estilo; estadísticas del perfil con color explícito.
- Pruebas: `LocalLobbyUITests` 24/24, `MenuAccessibilityUITests` 5/5, núcleo 39/39.

## Avance (5/10): mensajes rápidos y roles completos

Objetivo acordado con el usuario: preparar el gameplay para el online. Lo que importa es la usabilidad, aunque los bots todavía no reaccionen a todo.

- **Mensajes rápidos** (`TraidoresCore/QuickChat.swift`, con pruebas): son el catálogo de `BotQuickReplies` de Android, con tildes. Es lógica pura, independiente de `ClassicGame`; para el online se arma el `QuickChatContext` con el roster de la sala.
  - Botones contextuales (hasta 3) y «MÁS» fijo a la derecha, con las 6 categorías de Android, en el mismo orden (`menuOrder(.fixed)`).
  - En el chat de los asesinos aparece el «PLAN DE LOS ASESINOS».
  - Para las frases que piden un jugador, se toca su carta en la mesa: las cartas siguen boca abajo y no se revela ningún rol. Para «Soy…», se tocan las cartas de los roles en juego, que ya son públicos en «PARTIDA ·…».
  - Cada mensaje guarda una intención (`QuickChatIntent`) para la futura conversación de los bots.
  - Con texto de accesibilidad, un solo botón «MENSAJES» abre el menú del sistema.
- **Nombre del jugador:** es el del perfil, como en Android; ya no dice «Vos». Si un bot se llama igual, toma el nombre «Nico».
- **Roles** (motor de Codex en `ClassicGame`, 29 pruebas nuevas; interfaz en `LocalGameView`):
  - **Desertor:** elige bando antes de EMPEZAR y puede cambiarlo una vez, con confirmación.
  - **Alcalde:** se revela con confirmación, su voto se cuenta doble y decide el segundo empate en la fase `.mayorTieBreak`, con las cartas marcadas EXPULSAR.
  - **Payador:** usa «ABRIR CONTRAPUNTO» y toca dos cartas. En la fase `.counterpoint` solo hablan los dos elegidos, con un temporizador de la mitad del debate y como mínimo 15 s. El Payador toca la carta que señala, y esa recibe un sello extra en el recuento.
  - **Oráculo:** a partir de la segunda noche invoca a un muerto o guarda su poder. Al invocado se lo marca INVOCADO en el debate.
  - **Bufón:** si el pueblo lo expulsa, gana. El aviso dice «¡GANASTE COMO BUFÓN!» y la partida sigue.
  - La victoria personal (`humanWon`) se usa en el resultado y en el historial de la cuenta.
- **Reparto con texto grande:** el panel del rol se desplaza cuando no entra en pantalla (antes, EMPEZAR quedaba fuera de alcance).
- Cosas chicas de Android: el lobby dice «PRACTICAR CONTRA LA IA» y Estilo y Emotes muestran el aviso de cosméticos de la beta.
- **Pendiente:**
  - La mesa en tamaño AX5 necesita una pasada propia: la cabecera, el título del chat y el panel inferior se desbordan desde antes de este bloque.
  - Animación de victoria del Bufón (`JesterVictoryAnimator`).
  - Conversación de los bots usando `QuickChatIntent`.
  - Emotes en partida.

### Correcciones tras la prueba en el iPhone (5/10)

- **Rol de práctica:** como en el lobby de Android, cada rol exige su mapa y su mínimo de jugadores (Mercenario 7, Alcalde/Payador/Oráculo/Bufón 8, Espía 10, Desertor 14). El selector muestra el requisito («Alcaldesa · desde 8 jugadores», «Bufón · solo Medieval») y, si no se cumple, INICIAR PARTIDA avisa con «No se puede iniciar» y no reparte otro rol. Los argumentos `-ui-testing-role=` siguen forzando el rol para las pruebas.
- **Engranaje del lobby:** abre las opciones generales (`TableOptionsPanel` sin «SALIR DE LA PARTIDA»), igual que el `AccessibilityOptionsDialog` del lobby de Android. Las reglas de la partida siguen en «OPCIONES AVANZADAS» (`lobby.advanced`).
- **Votación sin voto propio:** si el jugador está eliminado o silenciado, el cartel dice «ESTÁS ELIMINADO» o «HOY NO PODÉS VOTAR» en lugar de invitar a tocar una carta.
- **Recuento (pedido del usuario, que también se pasa a Android):** cada tarjeta lleva como título la foto y el nombre de quien recibe los votos; debajo van la carta, «VOTOS: N» y los sellos de quienes votaron.
- **Resultado (pedido del usuario, que también se pasa a Android):** «EQUIPO GANADOR» y debajo «EQUIPO PERDEDOR», más compacto, con nombre y rol de cada jugador. El Bufón expulsado aparece entre los ganadores como «GANÓ COMO BUFÓN»; el Desertor muestra su bando final («DESERTOR · PUEBLO»).

### Emotes en partida y nombres legibles (5/10)

- **Emotes** (`TraidoresCore/Reactions.swift`, con pruebas; interfaz en `LocalGameView`, sección `Emotes`):
  - Botón de emotes en el panel del jugador. Abre la paleta con los 4 emotes del Perfil (`menu.profileEmotes`).
  - Solo funcionan en las fases públicas del día (debate, Contrapunto, votaciones y desempate del Alcalde), y no mientras hay una transición o un anuncio en pantalla.
  - Límite de 2 por ronda y 10 s de espera entre uno y otro, con avisos («Esperá N s para otro emote», «Ya usaste tus emotes de esta ronda»).
  - La burbuja va sobre la carta (o sobre el panel propio): aparece en 0,18 s, queda 3,65 s y se va. El «6 7» está animado.
  - Los bots reaccionan cada 5 a 10 s con el set de su rol (Asesino medieval, Comisario gaucho; el resto usa el griego) y una emoción según la fase, como en Android. En las pruebas UI solo reaccionan con `-ui-testing-emotes`.
  - Sonidos: `Scripts/prepare_emote_audio.py` copia los MP3 y convierte a AAC los OGG, que AVAudioPlayer no lee. Hay un canal propio donde el último emote gana, con un mínimo de 0,3 s entre sonidos.
- **Nombres en la mesa:** llevan una plaquita oscura detrás, para que se lean sobre los mapas de día.

## Punto de partida para la próxima sesión (3/10)

Continuidad adicional: [CONTINUIDAD_CODEX_2026-10-03.md](CONTINUIDAD_CODEX_2026-10-03.md), con el arreglo del botón, pruebas, ícono e instalación/lanzamiento en el iPhone 13. El audio en el dispositivo aún requiere escucha del usuario.

Estado al cerrar el commit `ios: dawn reveals, match audio, vote ceremony and table options`:

**Verificado en simulador (iPhone 17, iOS 27):** anuncio de muerte (grabado cuadro por cuadro), «El pueblo respira», marco 9-patch de Pampa y Grecia, música/efectos (se oyen en el simulador), recuento con sellos y «Mayoría alcanzada», expulsión completa hasta «X FUE EXPULSADO». Suite `LocalLobbyUITests` 20/20 **antes** de agregar `VoteCeremonyView`.

**Retoma de Codex (3/10):** Xcode 27.0 y el mismo simulador iPhone 17 disponibles. Core: 39/39. La suite completa `LocalLobbyUITests` dio 19/20: el toque en «VER EXPULSIÓN» caía sobre el fondo del botón, fuera del texto, y no avanzaba. Se movieron el marco y `contentShape` dentro de la etiqueta del botón, con estilo `.plain` y altura mínima de 44 pt. La repetición de `testVotingReviewSnapshotAndRecount` pasó y recorrió tanto el recuento como la expulsión (adjunto «Pampa expulsión»); no se repitió la suite completa tras este cambio. Resultados en `/tmp/traidores-codex-verification/`. El guardado de pruebas ya está aislado: `LocalLobbyView` inyecta su suite de `UserDefaults` en `LocalGameStore`, aunque el valor por defecto del store sea `.standard`.

**Estado vigente al entregar a Claude (3/10):** el usuario rechazó el primer diseño de ganadores y pidió que sea exactamente como Android. `MatchResultView` fue reescrita con el arte original de corona/cortinas, títulos originales, solo equipo ganador, crónica, dos botones inferiores y animación escalonada. Bree Serif en títulos/botones y Roboto real en textos secundarios; duración real persistida. Compilación e instalación en iPhone aprobadas; lanzamiento final no confirmado. Traidores y Mercenario con tiempos reales pasaron antes de agregar Roboto. **Pueblo sigue fallando la auditoría de Dynamic Type en «COMISARIO», incluso con Roboto.** AX5 se interrumpió por pedido de cierre; no está validado. Ver el estado vigente y orden inmediato en `CONTINUIDAD_CODEX_2026-10-03.md`; sus notas del diseño inicial están marcadas como reemplazadas. El iconito de foto de perfil de cada jugador es un pedido futuro, todavía no implementado.

**Pendiente de verificar (primero):**
1. **Comprobado el 3/10:** suite completa y corrección/repetición de la prueba del recuento, según la nota anterior.
2. **Comprobada con tiempos reales el 3/10:** bota/carta por encima del marco y cierre del hueco. Hoja `evidence/2026-10-03/patada-cuadros.jpg`. Corregido además el título final de expulsión que se truncaba con puntos suspensivos.
3. **Comprobado el 3/10:** silencio con humano Mercenario en partida de siete jugadores; jaula/candado y nombre del silenciado. Ver la evidencia y la grabación indicadas en la nota de continuidad.
4. **Instalado y lanzado en el iPhone 13 el 3/10.** Falta oír el audio real (volumen relativo música/efectos, que la música vuelva tras cada anuncio) y confirmar que el ladrido sigue bien.
5. **Primero corregir Dynamic Type de «COMISARIO» en el cierre y verificar la versión con Roboto;** después accesibilidad de lo nuevo: AX5 en los tres anuncios y en el recuento (hay `ViewThatFits` → `ScrollView`), VoiceOver (con VoiceOver los anuncios y el recuento no avanzan solos), Reducir animaciones de la app.

**Cómo está armado:**
- `Platform/GameAudio.swift`: `GameAudio` en el entorno desde `LocalMatchFlow`; `LocalTableView.desiredMusic(_:)` decide la pista (nil = pausa con fundido). Efectos con `playEffect(_:)`; el impacto de la expulsión lo dispara `VoteCeremonyView.onImpact`.
- `Features/LocalGame/DawnRevealViews.swift`: `RevealFrameArt`/`RevealPanel` (marco por mapa, también lo usan `LocalEventCard`, el resultado privado y los compañeros traidores) y las tres vistas de amanecer. `DawnAnnouncement` guarda ids de jugador.
- `Features/LocalGame/VoteCeremonyView.swift`: cubre las fases `voteCount` y `result` sin ganador; cada botón llama a `performPrimaryAction` de la mesa. Avanza solo a los 8 s salvo con `-ui-testing` o VoiceOver.
- `Features/LocalGame/MatchResultView.swift`: ceremonia con diseño de Android, cartas del equipo ganador y crónica con estadísticas/historia. La mesa se retira del árbol de vistas cuando hay ganador; el cierre espera a los anuncios y las transiciones pendientes.
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
