# Plan: beta Android con anfitrión y V3 solo local, 9/10/2026

Lo redactó Claude a pedido del usuario (`docs/PROMPT_CLAUDE_BETA_ANFITRION_Y_V3_LOCAL_2026-10-09.md`).
Es una revisión con propuesta. No se cambió código ni configuración, no se desplegó nada
y no se hizo commit.

Cada afirmación lleva una de estas marcas:
- **[código]**: comprobado leyendo el árbol actual;
- **[probado]**: hay una ejecución con evidencia;
- **[sin verificar]**: falta probarlo.

---

## 1. Estado actual

### 1.1 Cómo se elige el online con anfitrión

- **[código]** Una build sin `-PtraidoresServerOnlineV3` usa el camino anterior.
  `app/build.gradle` toma `false` por defecto, y en esta Mac ni `gradle.properties` ni
  `~/.gradle/gradle.properties` lo activan.
- **[código]** Con `SERVER_ONLINE_V3=false`:
  - `OnlineModeActivity` no lee `config/onlineV3` y habilita crear, buscar y unirse;
  - `LobbyActivity` arranca como siempre: inicio desde el cliente,
    `AssigningRolesActivity` y después `GameplayMockActivity`;
  - el historial se guarda con `guardarHistorialOnlineV1`, que no depende de V3.
- **Riesgo 1 [código].** Este bloque cambió en `release`: antes `SERVER_ONLINE_V3` era
  `false` fijo y ahora sale de la propiedad. `docs/PRUEBA_INTERNA_PLAY_V3_52.md` indica
  agregar `traidoresServerOnlineV3=true` al `~/.gradle/gradle.properties` **de la otra
  computadora**, la que firma. Si esa línea quedó ahí, el AAB firmado sale en V3 y
  muestra «Online en mantenimiento». Hay que quitarla y agregar una protección en el
  build (tarea 1).
- **Riesgo 2 [código].** Una build normal lista y deja unirse a salas
  `protocolVersion: 3`, porque el buscador filtra solo cuando `SERVER_ONLINE_V3` es true.
  Si entra a una, `LobbyActivity` la trata como V3. Hoy solo crean esas salas las APK de
  prueba, pero conviene ocultarlas (tarea 1).
- **[código]** No hay que borrar V3 ni convertir salas: el camino anterior sigue completo.

### 1.2 Regresiones posibles por los cambios compartidos

Los cambios sin commit tocan código que también usa la mesa con anfitrión:

| Cambio | Efecto en el online con anfitrión | Estado |
|---|---|---|
| `GameplayMockActivity` (−1050 líneas) pasa a `GameplayPlayerColumns`, `GameplayReactionUi`, `GameplayWindowInsets` y `GameplayEliminatedPlayerCard` | Cartas de la mesa, contornos, insignias, emotes y ficha del eliminado se dibujan con código nuevo. `renderPlayerColumns` ahora espera la publicación online | [código] 742 pruebas unitarias pasan, pero **ninguna partida online con anfitrión se jugó después del cambio** [sin verificar] |
| `GameplayChatController` con `ChatDataSource` | Las ramas nuevas solo corren con `dataSource` (V3). Cambio que sí afecta al común: el separador de no leídos descuenta los mensajes del chat ambiental | [código] |
| `VoteResultAnimator` | Se agregaron funciones nuevas para V3. El recorrido común no cambió | [código] |
| Silenciados sin emotes | Decisión del usuario, también para el común | [código] |
| `AssigningRolesActivity` | Solo actúa si llega `EXTRA_SERVER_MATCH_ID`, que el común no envía | [código] |
| `LobbyActivity` | `ServerRoomProtocol.compatible` acepta salas sin `protocolVersion`. La renovación del lease solo se saltea en salas V3 | [código] |
| `firestore.rules` y `database.rules.json` desplegados el 8/10 | Se reordenó `activeHostCanUpdateRoom` (mismo OR lógico). Se agregaron restricciones solo para salas V3 y nodos RTDB nuevos | [código] Las suites de reglas pasaron cuando Codex desplegó [probado, según `CIERRE_ACTIVACION_V3_BETA.md`] |

Conclusión: no veo regresiones en el código, pero la mesa común cambió mucho por
dentro y no tiene una partida online de prueba. **La tarea 1 es obligatoria antes de la
beta.**

### 1.3 Qué pasa hoy si el anfitrión se va

- **[código]** Si minimiza, bloquea o cierra la app, `onStop` lo marca desconectado en
  RTDB. El relevo (`GameplayMockActivity.handleOnlineHostHandoff`) elige al jugador
  conectado con cuenta registrada de menor orden. Una transacción confirma el cambio y
  el nuevo anfitrión lee `repartos` para recuperar los roles y sigue la partida. Si no
  queda nadie con cuenta, espera **20 s** antes de dejar que un invitado tome el control.
- **[código]** En el lobby, si sale aparece «Si salís, X quedará como anfitrión activo y
  la sala continuará».
- **[probado, 1/10, 0.1.46]** `docs/prueba-recuperacion-2026-10-01.md`: con el A56 de
  anfitrión bloqueado 30 s, el emulador tomó el control, recuperó los cinco roles y
  resolvió la noche. Cuando el usuario volvió, se recuperó en unos 5 s. En la revancha,
  la recuperación tardó 10 s o más y mostró un fondo de día con título de noche.
- **Límites:**
  - [sin verificar] una caída brusca de red (sin `onStop`) depende de que RTDB detecte
    el corte, lo que puede tardar bastante más;
  - [código] el anfitrión todavía calcula la partida, así que un cliente modificado puede
    hacer trampa;
  - [código] durante el relevo hay una pausa visible.

### 1.4 Fotos, perfiles e historial

- **[probado, 5/10, `ENTREGA_BLAZE_2026-10-05.md`]** Bucket `traidores.firebasestorage.app`
  y reglas de Storage, Firestore y RTDB publicados. `traidoresProfileStorage=true` por
  defecto. `guardarHistorialOnlineV1`, `contarPartidaLocalV1` y `borrarHistorialCuentaV1`
  están desplegadas. Con dos cuentas QA se probó: foto visible entre cuentas, escritura
  ajena rechazada, historial privado y borrado aislado. **Fue con scripts, no con
  teléfonos.**
- **[código]** Perfil, menú, tarjeta online, lobby, cartas de la mesa, recuento,
  desempate y ganadores usan el mismo `GameplayAvatarView`. Muestra la foto local propia
  o `profile.publicAvatarUri` (`fotoPerfil` del perfil público y del jugador en la sala).
  En resultados, la foto circular va debajo del rol.
- **Riesgo 3 [código, sin verificar].** Las URL de Storage se cargan con
  `PlayGamesProfileAvatar.render` → `ImageManager` de Play Games. Ese cargador está
  pensado para imágenes de Google. Si no descarga URL de `firebasestorage.app`, las fotos
  de los demás se ven como avatar ilustrado sin dar error. Es lo primero a comprobar en
  un teléfono.
- **[código]** Caché:
  - una LRU de miniaturas de 2 MB en memoria, más la caché propia de `ImageManager`;
  - no hay caché en disco propia, así que cada apertura puede volver a descargar.
  
  Compresión: JPEG de 512 px y hasta 256 KiB, sin EXIF. Reemplazo: cada versión tiene
  su URL; las anteriores quedan hasta que se quita la foto o se borra la cuenta (la
  limpieza de versiones viejas está pendiente). Si la subida falla, queda en cola por UID
  con reintento.
- **[código]** El historial por cuenta usa el resultado que el anfitrión deja en la sala
  y no depende de V3. Los invitados no tienen historial por cuenta.
- **Reportes y retiro de fotos (solo para señalar):**
  - [código] `PlayerModeration` escribe en `reportes`, pero no tiene un motivo «foto»;
  - muestra «Gracias. Vamos a revisarlo.» también ante `PERMISSION_DENIED`, el falso
    éxito que ya estaba anotado;
  - no hay herramienta para retirar la foto de una cuenta: hoy se haría a mano, borrando
    el archivo y el campo `fotoPerfil`.

### 1.5 Consumo

- **[código]** Ya existen la medición en el teléfono (Opciones → Pruebas online →
  «Copiar reporte beta», `docs/medicion-online-desde-opciones.md`) y
  `scripts/firebase-capacity-snapshot.cjs` (Cloud Monitoring, solo lectura).
- **[código] Costo en reposo que sigue corriendo aunque V3 esté cerrado:**
  - `accionPartidaV3` con `minInstances: 1` (cpu 1) y `resolverFaseV3` con
    `minInstances: 1`: se cobran instancias reservadas todo el tiempo;
  - `repararPartidasV3` corre **cada 1 minuto** (unas 43 000 ejecuciones y consultas por
    mes, aunque no haya partidas);
  - `limpiarSalasAbandonadasV1` cada 15 min, que el online anterior sí necesita.
- **[sin verificar]** No hay una medición de una partida con anfitrión desde el
  despliegue de Blaze. La de 0.1.46 da contadores parciales por teléfono, no la factura.

### 1.6 V3: fallas de la APK 3

Evidencia: `output/v3-cloud/qa-v3-interactive-1791530050812/`.

| Falla del usuario | Qué muestra la evidencia | Causa probable |
|---|---|---|
| Unos 10 s para empezar y no vio su rol | El servidor arrancó a las 07:14:34.4 (REPARTO, con vencimiento a 27 s). El teléfono empezó el reparto a las 07:15:02.0, **27,6 s después**. El primer `v3_render` ya fue `NOCHE`; `REPARTO` nunca se dibujó | Demora del lobby al reparto, sin medir por tramos. Además, la ventana del rol depende de ver `REPARTO`: si se pierde esa fase, no hay presentación |
| El Médico no pudo desmarcar ni protegerse | El servidor rechaza cambiar una acción nocturna (`night-action-already-submitted`). La política del cliente oculta las opciones después de confirmar. La carta propia no está entre los objetivos de la mesa | Hay que comparar con el común: seleccionar y deseleccionar, autoprotección y confirmación. Si el común permite cambiar, es una diferencia de reglas que el usuario debe confirmar |
| Anuncio de muerte solo con texto | Las capturas muestran «AMANECER 1», **el fondo vuelve a la noche** y unos 5 s después aparece la carta de muerte. La crónica muestra el texto antes de la carta | Orden y ritmo distintos a los del común, y el fondo vuelve a la noche |
| No vio a quién votó | La captura sí muestra «Votaste a: Ramón» y el contorno, pero en letra chica y tapado por el aviso inferior | Problema de visibilidad, no de datos. Hay que compararlo con el común |

---

## 2. Tareas (máximo cinco, por prioridad)

### Tarea 1: build de beta con anfitrión y partida de regresión

- **Cambios:**
  - `app/build.gradle`: `release` vuelve a `SERVER_ONLINE_V3=false`, salvo una propiedad
    explícita y distinta para QA. Si no, que falle con un mensaje claro;
  - `LobbyBrowserActivity.kt`: la build normal no lista ni deja unirse a salas
    `protocolVersion: 3`;
  - versionCode nuevo (53) para no confundirlo con el AAB 52 preparado como V3;
  - pedirle al usuario que quite `traidoresServerOnlineV3` del `gradle.properties` de la
    otra computadora.
- **Prueba:** una partida completa con anfitrión en build normal, con dos dispositivos
  (A56 y AVD, o dos AVD) y salas en modo prueba. Recorrer:
  - reparto, rol y mesa;
  - todos los poderes de una partida (Médico, Comisario, Asesino; Alcalde en otra);
  - anuncios decorados, chat público y de traidores, emotes y silencio;
  - voto directo, desempate, resultado, victoria y revancha.
  
  Capturas al lado de capturas de 0.1.50 si hay alguna disponible.
- **Cierre visible:** el usuario juega una partida en el A56 con la build normal y dice
  «es el online de siempre».

### Tarea 2: aviso del anfitrión y prueba de relevo

- **Dónde va el aviso:**
  - una línea chica bajo las opciones del diálogo «Crear sala» (`OnlineModeActivity`);
  - en el lobby, solo para el anfitrión y solo mientras espera, bajo «Sala completa…»
    (`LobbyActivity`).
  
  No va en la partida.
- **Texto:** el inicial del usuario, más una segunda frase solo si la prueba la confirma:
  «Si se desconecta, otro jugador con cuenta toma el control y la partida sigue tras una
  breve pausa».
- **Prueba de relevo**, con dos dispositivos y el A56 de anfitrión: minimizar 30 s,
  cerrar la app, modo avión 60 s y salir desde el lobby. Medir el tiempo hasta
  `host_promoted` y qué ve cada jugador. Repetir con todos invitados para medir la espera
  de 20 s.
- **Mejora chica, solo si la prueba lo justifica:** un aviso visible para todos mientras
  dura el relevo («Cambiando de anfitrión…»). No tocar el mecanismo de relevo sin
  evidencia de falla.
- **Cierre visible:** el aviso en las dos pantallas y una tabla con tiempos reales de
  relevo por caso. El texto final dice solo lo comprobado.

### Tarea 3: fotos e historial en teléfonos reales

- **Prueba:** dos cuentas reales en build normal, contra producción. Subir una foto y
  comprobar que se ve en perfil, menú, tarjeta online, lobby, mesa, recuento, desempate y
  ganadores, debajo del rol, **en el otro teléfono**. Después:
  - reemplazarla y quitarla;
  - cortar la red durante la subida;
  - terminar una partida con anfitrión y ver el historial de las dos cuentas;
  - reabrir sesión en otro dispositivo.
- **Si falla el Riesgo 3** (la foto ajena no aparece): reemplazar `ImageManager` por
  una descarga HTTPS con caché en disco chica, dentro de `PlayGamesProfileAvatar.kt`, sin
  dependencias nuevas.
- **Fuera de alcance:** SafeSearch, servicios pagos nuevos y moderación nueva. Solo se
  anota el estado de reportes y retiro de fotos (sección 1.4).
- **Cierre visible:** capturas de la foto de la cuenta A en el teléfono de la cuenta B, en
  cada pantalla, y el historial de ambas.

### Tarea 4: medición de consumo y reducción del costo en reposo de V3

- **Medir una partida con anfitrión:** reiniciar la medición en cada teléfono, jugar 5
  jugadores (o los disponibles), «Copiar reporte beta» en anfitrión e invitado y, en el
  mismo intervalo, `firebase-capacity-snapshot.cjs` para Firestore, RTDB, Storage y
  Functions. Anotar aparte la preparación y limpieza de QA.
- **Medir en reposo:** una ventana de 24 h sin partidas.
- **Propuesta para V3 cerrado** (requiere OK del usuario y despliegue de Codex):
  - `minInstances: 0` en `accionPartidaV3` y `resolverFaseV3`;
  - pausar el job de Scheduler de `repararPartidasV3`, o bajarlo a 15 min.
  
  Las funciones quedan desplegadas y sin costo fijo; el online anterior no las usa. No
  tocar `limpiarSalasAbandonadasV1` ni las funciones de historial.
- **No dar** una cifra mensual sin esta medición.
- **Cierre visible:** un informe con lecturas, escrituras y bytes por partida, el costo en
  reposo antes y después del cambio, y la configuración de instancias mínimas en 0.

### Tarea 5: V3 solo en emuladores, fallas de la APK 3

- **Herramientas:** Emulator Suite (`firebase.authority-qa.json`), la APK aislada
  `v3qa` y el script de bots con la AVD. Sin credenciales de producción y sin instalar en
  el A56 sin coordinarlo.
- **Recorridos dirigidos:**
  1. **Inicio:** log con hora en cada tramo (toque en INICIAR, respuesta de la callable,
     sala `en_juego` en el lobby, apertura del reparto, primera proyección y primer
     `v3_render`). Para reproducir la demora de Cloud, simular latencia con un retardo
     en el script. Corrección esperada: el rol se presenta la primera vez que el jugador
     ve su rol en ese `matchId`, aunque `REPARTO` ya haya terminado. Si la demora es del
     lobby, reducirla.
  2. **Médico:** misma secuencia en el común (`GameplayMockActivity` local) y en V3:
     seleccionar, deseleccionar, autoproteger, confirmar y cambiar. Alinear V3 con el
     común. Si hay que permitir cambiar una acción ya enviada, presentarlo como decisión
     de reglas antes de tocar `onlineGameCore.js`.
  3. **Amanecer y muerte:** comparar cuadro a cuadro con el común, con capturas cada
     0,5 s. Corregir el fondo que vuelve a la noche y el orden texto/carta.
  4. **Voto:** comparar el tamaño, la ubicación y la duración de «Votaste a» y del
     contorno con el común. Evitar que el aviso inferior lo tape.
- **Cierre visible:** capturas en pares (común y V3) de cada momento, en el emulador.
  V3 no vuelve a Cloud ni al teléfono sin un pedido del usuario.

---

## 3. División propuesta con Codex

Ningún archivo queda asignado a los dos al mismo tiempo.

| Tarea | Quién | Archivos |
|---|---|---|
| 1. Build y regresión con anfitrión | **Codex** | `app/build.gradle`, `LobbyBrowserActivity.kt`, docs de prueba |
| 2. Aviso y prueba de relevo | **Claude** (código) + **Codex** (prueba en dos dispositivos) | `OnlineModeActivity.kt` (diálogo), `LobbyActivity.kt` (línea del anfitrión) |
| 3. Fotos e historial reales | **Codex** (prueba en producción con dos cuentas). Si falla el cargador, **Claude** corrige | `PlayGamesProfileAvatar.kt` solo si hace falta |
| 4. Consumo y V3 en reposo | **Codex** (medición y despliegue con OK del usuario) | `functions/src/onlineGameFunctions.js`, `functions/src/onlineGameRecovery.js` y sus pruebas |
| 5. V3 local | **Claude** | `ServerGame*.kt`, `AssigningRolesActivity.kt`; `functions/src/onlineGameCore.js` solo si el usuario aprueba el cambio de reglas |

**Orden:** primero la tarea 1 (Codex) y la tarea 2 (Claude), en paralelo y en archivos
distintos. Después, la partida de regresión de la tarea 1 incluye el aviso y la prueba de
relevo de la tarea 2. Las tareas 3 y 4 pueden ir en paralelo con ellas. La tarea 5 es
independiente y no bloquea la beta.

Coordinación de archivos:
- `LobbyActivity.kt` es de Claude (tarea 2). Si la tarea 5 necesita tocarlo para la
  demora de inicio, Claude lo hace después de terminar la tarea 2.
- Las funciones de V3 son de Codex (tarea 4). Si la tarea 5 necesita cambiar
  `onlineGameCore.js`, se coordina después del despliegue de la tarea 4.

Hay que esperar la confirmación del usuario sobre esta división antes de implementar.

---

## 4. Ideas fuera de esta beta

No son requisitos y quedan para después:

- Limpieza periódica de versiones viejas de fotos en Storage.
- Motivo «foto inapropiada» en los reportes y un script de administración para retirar
  la foto de una cuenta.
- Corregir el falso «Gracias» ante `PERMISSION_DENIED` en los reportes.
- Caché de fotos en disco con encabezados HTTP, si la medición muestra descargas
  repetidas.
- Para V3, cuando vuelva: medir la latencia real del inicio en Cloud por tramos antes de
  otra práctica con el usuario, y hacer la práctica solo con la lista ya verificada en
  emuladores.
