# Pausa de V3 y continuidad del online con anfitrión

El usuario detuvo la práctica Cloud de la APK 3 y pidió volver al online que depende del anfitrión porque el proceso le resulta abrumador. No continuar la práctica ni la migración V3 sin una nueva indicación del usuario.

## Estado comprobado al cerrar

- Controlador de práctica y observador de interfaz detenidos. La app QA fue cerrada.
- Sala `qa-v3-interactive-1791530050812`, rama RTDB, cinco cuentas temporales, perfiles temporales y token App Check de la práctica eliminados. Limpieza sin errores, verificada a las 07:21:42 UTC.
- `onlineMaintenance/serverAuthority`: `enabled=false`, listas de anfitriones y salas vacías.
- `config/onlineV3`: `enabled=false`, `minVersionCode=52`.
- Sin apertura de beta, publicación en Play, commit ni push.
- Las ocho Functions de la entrega 3 siguen desplegadas. Se conservaron las instancias mínimas solicitadas previamente; cerrar el acceso no elimina el costo de esas instancias reservadas. Revisar su reducción si se decide mantener V3 detenido.

## Online anterior

El camino con autoridad del anfitrión continúa en el árbol actual. `app/build.gradle` mantiene `traidoresServerOnlineV3=false` por defecto; `gradle.properties` no lo sobrescribe. `LobbyActivity` conserva el inicio anterior y la entrada a `AssigningRolesActivity` / `GameplayMockActivity` cuando la sala no es V3. Las salas V3 conservan su protocolo; no degradar una partida V3 existente a una partida con anfitrión.

No se hizo un reset de Git, no se borraron los cambios de Claude y no se instaló otra APK tras la cancelación. La APK aislada de la práctica era V3; cerrar su gate no la convierte en la app anterior. Para volver a probar se debe preparar la build normal con V3 deshabilitado y hacer una comprobación acotada del camino anterior. Esta sesión solo comprobó su presencia y configuración por lectura; no certificó una partida de ese camino.

## Opinión del usuario: entrega 3 no aprobada

- Aproximadamente diez segundos para comenzar, según su percepción.
- No recibió la presentación de su rol; apareció directamente en la noche como Médico.
- No pudo desmarcar otro objetivo para salvarse a sí mismo.
- El anuncio de muerte mostró solo texto y no el cartel esperado.
- No vio a quién había votado.
- Detuvo la práctica por frustración. Los demás puntos de la lista no tienen aprobación del usuario.

El controlador registró un resultado Traidores en la segunda noche mientras se recibían las respuestas; eso no constituye aprobación de la presentación ni de la fluidez. La evidencia automática se conserva en `output/v3-cloud/qa-v3-interactive-1791530050812/`, incluyendo `evidence.json`, `progress.jsonl`, capturas, log Android y `cleanup-verified.json`.

Las fallas indicadas quedan pendientes; no se corrigieron después del pedido de detenerse. No mezclar esta evidencia con la medición de la APK 2.
