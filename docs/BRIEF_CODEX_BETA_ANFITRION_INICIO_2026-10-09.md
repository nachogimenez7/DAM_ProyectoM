# Encargo para Codex: beta con anfitrión, primera tanda (9/10/2026)

Lo redactó Claude. Contexto completo en `docs/PLAN_BETA_ANFITRION_Y_V3_LOCAL_2026-10-09.md`;
el usuario lo aprobó. Ajuste del reparto para avanzar en paralelo sin chocar en archivos:

- **Claude** escribe el código de las tareas 1 y 2: en `app/build.gradle`, `release` no
  puede salir en V3 por accidente y se usa versionCode 53; la build normal oculta las
  salas V3 (`LobbyBrowserActivity.kt`); y agrega el aviso del anfitrión
  (`OnlineModeActivity.kt`, `LobbyActivity.kt`). Después sigue con V3 solo en emuladores.
- **Codex** hace las pruebas en dispositivos y la medición. **No edites esos archivos de
  Android ni `ServerGame*.kt`.**

## Ahora (en paralelo con Claude)

### A. Fotos e historial reales (tarea 3)

1. Hacé una build normal con el árbol actual (sin `traidoresServerOnlineV3`) solo para
   esta prueba. Usá dos cuentas reales en dos dispositivos, contra producción.
2. La cuenta A sube una foto. En el dispositivo de B, comprobá que se vea en: perfil
   público, tarjeta online, lobby, cartas de la mesa, recuento, desempate y ganadores
   (debajo del rol). También en el menú y el perfil de A.
3. Reemplazá la foto, quitala, cortá la red durante una subida y abrí la sesión de A en
   otro dispositivo.
4. Terminá una partida con anfitrión y revisá el historial de las dos cuentas.
5. **Lo primero a mirar:** si la foto de A **no** aparece en el teléfono de B, avisá enseguida
   con log (`play_games_avatar_render_failure` u otros). Claude reemplaza el cargador en
   `PlayGamesProfileAvatar.kt`. No lo corrijas vos, para no chocar.

Entregá capturas por pantalla y el resultado de cada paso.

### B. Medición del consumo (tarea 4, primera parte)

1. **Consumo en reposo:** una ventana de 24 h sin partidas, con
   `scripts/firebase-capacity-snapshot.cjs` (solo lectura). Registrá aparte el costo de
   las instancias mínimas de `accionPartidaV3` y `resolverFaseV3` y de las ejecuciones de
   `repararPartidasV3`, que corre cada minuto.
2. **Propuesta, sin desplegar:** `minInstances: 0` en esas dos funciones y pausar el job
   de `repararPartidasV3` (o pasarlo a cada 15 min). No toques
   `limpiarSalasAbandonadasV1` ni las funciones de historial. Dejá preparado el cambio en
   `functions/src/onlineGameFunctions.js` y `functions/src/onlineGameRecovery.js`, con sus
   pruebas, y **pedile el OK al usuario antes de desplegar**.
3. La medición de una partida se hace en la tanda siguiente, junto con la regresión.

## Código de Claude: listo (9/10)

- `app/build.gradle`: `release` ya no hereda `traidoresServerOnlineV3`. Un release V3
  requiere la propiedad aparte `traidoresReleaseServerOnlineV3=true`. Comprobado:
  release con `-PtraidoresServerOnlineV3=true` genera `SERVER_ONLINE_V3 = false`.
  Versión 53 / 0.1.52.
- Build normal: el buscador oculta las salas `protocolVersion: 3`, y unirse por
  buscador o por código las rechaza con «Esta sala es de una versión de prueba».
- Aviso del anfitrión en «Crear sala» y una línea en el lobby que solo ve el anfitrión
  («Sos el anfitrión: mantené el juego abierto hasta el final.»), y solo en la build
  normal. Capturas en `output/beta-anfitrion/`, con emuladores locales.
- 742/742 pruebas Android aprobadas y el release compila.

Para las pruebas en dispositivos usá una build Debug normal contra producción (sin
`traidoresServerOnlineV3` ni `traidoresOnlineAuthorityEmulator`).

## Después (con esa build)

- **Regresión de la tarea 1:** partida completa con anfitrión en dos dispositivos. Cubrí
  reparto, rol, poderes (Médico, Comisario, Asesino; Alcalde en otra partida), anuncios
  decorados, chat público y de traidores, emotes, silencio, voto directo, desempate,
  resultado, victoria y revancha. Durante esa partida medí el consumo con «Copiar reporte
  beta» de anfitrión e invitado, más el snapshot del mismo intervalo.
- **Relevo de la tarea 2,** con el A56 de anfitrión: minimizar 30 s, cerrar la app, modo
  avión 60 s y salir desde el lobby. Repetilo con todos invitados (espera de 20 s). Medí
  el tiempo hasta `host_promoted` y anotá qué ve cada jugador.

Sin commit, push, publicación en Play ni despliegues sin pedido explícito del usuario.
`sources/` sigue siendo de solo lectura.
