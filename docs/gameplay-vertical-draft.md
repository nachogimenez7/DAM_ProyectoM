# Borrador futuro: gameplay vertical

Este documento guarda el intento descartado de pasar el gameplay post-lobby a
vertical. La implementacion activa fue revertida; esto queda solo como memoria
tecnica para retomarlo mas adelante.

## Alcance que se intento

- Cambiar a `portrait` las Activities que aparecen despues de iniciar partida:
  `AssigningRolesActivity` y `GameplayMockActivity`.
- Mantener lobby y pantallas previas como estaban.
- Reorganizar solo la pantalla de gameplay, sin cambiar reglas de partida.
- Mostrar una recomendacion para 10 o mas jugadores: si las cartas quedan
  chicas, conviene jugar en horizontal.

## Enfoque de layout probado

- `activity_gameplay_mock.xml` dejaba de ser una mesa horizontal con columna de
  jugadores izquierda, centro y columna derecha.
- `gameplayBody` pasaba a `vertical`.
- `centerColumn` ocupaba todo el ancho disponible.
- Se agregaba una seccion `playerRows` dentro de `centerColumn`.
- Las dos listas laterales de jugadores se convertian en dos filas:
  `HorizontalScrollView` para `leftPlayersScroll` y `rightPlayersScroll`.
- `leftPlayersContainer` y `rightPlayersContainer` pasaban a orientacion
  horizontal.
- `bottomPlayerPanel` pasaba a ser mas alto y vertical:
  informacion del jugador arriba, botones abajo.
- `chatPanel` pasaba a casi todo el ancho, con margen inferior mayor para no
  tapar el panel del jugador.
- `actionFeedbackBanner` subia su margen inferior para quedar sobre el nuevo
  panel inferior.

## Ajustes Kotlin probados

En `GameplayMockActivity.kt`:

- `leftPlayersScroll` y `rightPlayersScroll` cambiaban de `ScrollView` a
  `HorizontalScrollView`.
- Se agregaba `largeGameOrientationHint: TextView`.
- `renderPlayerColumns()` calculaba metricas con una altura disponible aproximada
  para portrait:
  `(resources.configuration.screenHeightDp - 260).coerceAtLeast(360)`.
- El aviso de orientacion se mostraba desde `LARGE_GAME_HINT_PLAYER_COUNT = 10`.
- `applyAdaptiveGameplayLayout()` configuraba filas horizontales:
  `MATCH_PARENT` de ancho, altura igual a `metrics.itemHeightDp`, scrollbars
  horizontales y contenedores centrados.
- Cada carta de jugador usaba ancho `metrics.columnWidthDp`, alto
  `metrics.itemHeightDp` y `marginEnd` en lugar de `bottomMargin`.
- El chat probaba:
  `CHAT_PANEL_WIDTH_RATIO = 0.94f`,
  `CHAT_PANEL_COMPACT_WIDTH_RATIO = 0.96f`,
  `CHAT_PANEL_TOP_MARGIN_DP = 78`,
  `CHAT_PANEL_BOTTOM_MARGIN_DP = 148`.

## Resultado

La idea compilo en el intento original, pero visualmente no era viable todavia:
dejaba demasiado trabajo fino de correccion, jerarquia, espaciado y legibilidad.
Se decidio revertir el codigo y conservar esta nota para una futura iteracion.

## Si se retoma

- Conviene hacerlo como diseno especifico para portrait, no como parche rapido
  sobre la mesa horizontal.
- Considerar un `layout-port/activity_gameplay_mock.xml` separado para reducir
  riesgo sobre el layout horizontal.
- Definir primero mockups para 5, 8, 10, 12 y 15 jugadores.
- Revisar especialmente: filas de cartas, panel del jugador, chat, log de
  eventos, overlays de votacion/resultado y pantallas de victoria.
- Recien despues de aprobar una maqueta, mover Kotlin y XML.
