# Handoff a Claude: mesa Android V3 en el A56

## Objetivo del usuario

La prioridad es que el online conserve la mesa habitual y funcione de forma ordenada: acción, confirmación, resultado privado/público correcto, transición y siguiente fase. El usuario volvió a informar que funciona muy mal después del segundo intento. No dar por resuelto el online por pruebas unitarias o capturas aisladas. No rediseñar el juego, introducir otra mesa experimental ni devolver autoridad al teléfono anfitrión.

## Estado y límites

- Repositorio `DAM_ProyectoM`, rama actual `ios-port`, árbol ampliamente modificado y compartido. Preservar los cambios existentes; no reset/stash global, no regenerar proyectos ni tocar `sources/`.
- En este bloque trabajar en Android. iOS tiene trabajo independiente; no editarlo.
- No commit/push, publicación en Play ni apertura general de V3 sin pedido del usuario.
- Firebase real: proyecto `traidores`, Functions de juego en `southamerica-west1`; worker/recuperación en `southamerica-east1`.
- El acceso general V3 permanece cerrado. Las prácticas usan solo una sala y creador temporales; App Check sigue exigido. No deshabilitarlo ni abrir reglas.
- APK aislado Debug `com.traidores.juego.v3qa`, `output/traidores-v3-cloud-practica.apk`. Es una prueba local instalada contra Firebase real, no una build de Play ni prueba de Play Integrity.
- A56: `adb-R5CY710D2MK-cyiEDY._adb-tls-connect._tcp`; adb en `/Users/ignaciogimenez/Library/Android/sdk/platform-tools/adb`. Verificar conexión antes de usarlo. No requiere instalar BlueStacks.

## Qué se hizo

Se conectó la mesa habitual a las proyecciones V3 del servidor mediante `ServerGameSessionAdapter` y `ServerGameTableRenderer`, reutilizando `activity_gameplay_mock`, cartas y animadores existentes. La autoridad de fases, roles, votos y resultados es del servidor; la UI presenta el estado y envía intenciones.

Se preparó una práctica de cinco participantes: usuario en A56 y cuatro clientes SDK autenticados que actúan como bots. El lobby y la mesa son las pantallas reales. Los bots sirven para reproducir el flujo y medir, pero no sustituyen una prueba humana multijugador.

Primera práctica: el usuario informó fallo de sincronización e investigación sin respuesta. Se interrumpió y limpió; no es partida completa aprobada.

Correcciones posteriores:

1. `ServerGameInitialAccessRetry.kt` y `ServerGameClient.kt`: esperar la publicación inicial de permisos RTDB con hasta 20 reintentos separados 1,5 s. Después de una primera proyección autorizada, la denegación sigue tratándose como pérdida de acceso. No se agregan lecturas de sondeo Firestore ni se relajan reglas.
2. `functions/src/onlineGameCore.js`: guardar la investigación privada al aceptar la acción en la misma transacción; al amanecer no duplicarla. Solo el investigador recibe la pista.
3. `ServerGameTablePresentation.kt`, `ServerGameSessionAdapter.kt`, `ServerGameTableRenderer.kt`: ventana habitual `RESPUESTA PRIVADA`, pista persistente y deduplicación por UID/partida. Cerrar la ventana no avanza ni detiene el reloj del servidor.
4. El panel privado recibió el mismo marco del mapa utilizado por otros anuncios. Esa última aplicación del marco compiló, pero la comprobación nativa focalizada fue anterior a ese ajuste visual.
5. `CloudQaLaunchActivity.kt` restaura el perfil temporal de la práctica antes del lobby, evitando preferencias de otra cuenta de QA.

Se desplegaron las cuatro Functions afectadas: `accionPartidaV3`, `resolverFaseV3`, `recuperarFaseV3` y `repararPartidasV3`. No se abrió acceso público.

## Qué se verificó y qué NO

- 37 pruebas del núcleo y 32 pruebas Android de contratos/espera inicial aprobadas.
- A56 con emuladores aislados: permisos retrasados deliberadamente 3,2 s se recuperan; investigar muestra pista durante NOCHE; CONTINUAR mantiene pista; reingreso no repite la ventana.
- Nueva build Cloud corregida compiló y se instaló. Lobby real mostró cinco listos. La segunda práctica avanzó según el registro de bots: REPARTO → NOCHE → AMANECER → DIA_DEBATE.
- El usuario volvió a indicar que funciona mal. No se identificó todavía el síntoma exacto de esta segunda práctica ni una nueva causa raíz. El progreso del backend no demuestra que la UI estuviera correcta.
- Una captura tomada al recibir el segundo reporte salió completamente negra y el filtro de logcat no capturó errores relevantes. No atribuir esa captura a un bug del juego sin verificar si el dispositivo estaba apagado/bloqueado.
- No hay una partida completa aprobada en el A56 ni una medición válida de su coste completo. Tampoco prueba desde Play ni multijugador humano.

## Archivos de referencia

- `docs/PRUEBA_A56_V3_2026-10-09.md`.
- `docs/REVISION_PARIDAD_MESA_ANDROID_V3.md`.
- `docs/BRIEF_CODEX_PARIDAD_VISUAL_MESA_V3.md`.
- `docs/BRIEF_CODEX_AJUSTES_MESA_Y_EMOTES_V3.md`.
- `docs/CONTRATO_AUTORIDAD_SERVIDOR_V3.md`.
- `app/src/main/java/com/traidores/juego/ServerGameplayActivity.kt`.
- `app/src/main/java/com/traidores/juego/ServerGameClient.kt`.
- `app/src/main/java/com/traidores/juego/ServerGameContract.kt`.
- `app/src/main/java/com/traidores/juego/ServerGameSessionAdapter.kt`.
- `app/src/main/java/com/traidores/juego/ServerGameTableRenderer.kt`.
- `app/src/main/java/com/traidores/juego/ServerGameTablePresentation.kt`.
- Mesa de referencia: `GameplayMockActivity.kt`, `GameplayTableUi.kt`, `GameplayChatController.kt`, los animadores y `app/src/main/res/layout/activity_gameplay_mock.xml`.

Evidencias:

- `output/v3-cloud/investigation-core-tests.log`.
- `output/v3-cloud/investigation-android-tests-build.log`.
- `output/v3-cloud/investigation-native-check.log`.
- `output/server-v3-table-investigation-private.png`.
- `output/v3-cloud/investigation-deploy.log`.
- `output/v3-cloud/interactive-practice.log`: primer intento.
- `output/v3-cloud/interactive-practice-fixed.log`: segundo intento.
- `output/v3-cloud/interactive-fixed-ready.png`: lobby del segundo intento.
- `output/v3-cloud/interactive-fixed-user-reported-failure.png`: captura negra, no diagnóstico confirmado.
- `output/v3-cloud/qa-v3-interactive-1791517303749/evidence.json`: estado de la segunda práctica y limpieza. Consultar si ya fue generado.

## Propuesta de trabajo

1. Reproducir el fallo observable antes de cambiar otra cosa. Identificar qué rol recibió el usuario y qué veía al tocar acciones, cerrar ventanas y cambiar de fase. Pedir una descripción breve del síntoma si las evidencias no bastan, sin asumir que es saturación.
2. Comparar mesa habitual y V3 en el mismo dispositivo/tamaño de letra: cartas, selección/acción, respuesta privada, chat, anuncios, superposición de ventanas, amanecer, votación y resultado.
3. Auditar el ciclo completo del renderer: snapshots de public/private/permissions, actualización coherente, presentación inicial, colas de animaciones, eventos únicos, prioridades de overlays, bloqueo/desbloqueo de input y limpieza al reconectar/cambiar fase. Separar fallo de backend de fallo de presentación con evidencia.
4. Corregir cada causa reproducida con el cambio mínimo. Si falta un DTO/evento del backend, documentarlo y coordinar el cambio concreto. Evitar reconstruir la mesa entera o añadir listeners/sondeos para ocultar carreras.
5. Comprobar un recorrido completo en Android: reparto, confirmar rol, acción nocturna y respuesta, amanecer, debate/chat, votación/resultado, siguiente noche y final. Después reconexión y abandono. Las ventanas visuales no pueden arbitrar fases ni dejar la mesa bloqueada.
6. Devolver causas raíz, archivos cambiados, pruebas reales, capturas comparativas y límites pendientes. No afirmar “online listo” hasta comprobar la partida completa.

## Herramientas de prueba

Compilar mediante `node scripts/with-jdk.cjs bash gradlew ...`.

La práctica Cloud usa `scripts/prepare-cloud-qa-android.cjs`, `scripts/play-server-v3-cloud-android.cjs` y `scripts/server-v3-cloud-measurement.cjs`. Las credenciales temporales se pasan internamente y no se imprimen ni guardan. La entrada y receptor de métricas existen solo en Debug. El controlador cierra el gate al comenzar y limpia sus propios recursos al cancelar/terminar.

Prueba focalizada local: `scripts/test-server-table-android.cjs` con `TRAIDORES_QA_INVESTIGATION_ONLY=true`; es una comprobación puntual, no una partida completa. Usa su APK/configuración de emuladores, diferente del APK Cloud. No reemplazar el APK equivocado ni pisar emuladores de otra sesión.

Durante el handoff Codex detuvo el controlador de la segunda práctica para cerrar/limpiar sus recursos y evitar actividad de prueba desatendida. Verificar el cierre en `interactive-practice-fixed.log` antes de preparar otra.
