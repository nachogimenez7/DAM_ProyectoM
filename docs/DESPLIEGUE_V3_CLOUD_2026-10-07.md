# V3 en Firebase real — entrega para Claude y pruebas

7 de octubre de 2026. Proyecto `traidores`, Blaze mediante prueba gratuita.

## Desplegado

- Firestore y Realtime Database: reglas actuales, con aislamiento V3 y compatibilidad
  del protocolo anterior. Índices del repositorio; `serverOutbox.recoveryAtMs` está READY.
- Santiago: `iniciarPartidaV3`, `accionPartidaV3`, `recuperarFaseV3`,
  `prepararRevanchaV3`, `abandonarPartidaV3`, `publicarPartidaV3`.
- São Paulo: `resolverFaseV3` por Cloud Tasks y `repararPartidasV3` cada minuto.
- Se actualizó también `guardarHistorialOnlineV1`. La primera prueba real descubrió
  que su versión anterior daba victoria a un jugador que abandonó. La nueva versión
  conserva la regla ABANDONO → derrota, incluso si gana su bando.
- Las demás funciones existentes no se eliminaron ni se redesplegaron.

Las ocho funciones V3 y el trigger de historial están ACTIVE. El Scheduler está
ENABLED y ya ejecutó sin error. El worker permite invocaciones solo de la cuenta
interna `99323018581-compute@developer.gserviceaccount.com`; no tiene `allUsers`.
Su invoker explícito está fijado en `onlineGameFunctions.js`. Se concedió a esa
identidad encolado en la cola V3 y `actAs` sobre sí misma. Una migración a otra
cuenta de ejecución exige cambiar también este contrato de infraestructura.
Se corrigió así el fallo de firebase-tools 15.28.2 al gestionar una política de
cola sin bindings con `invoker: private`. No se hizo público el worker.

## Apertura controlada

`onlineMaintenance/serverAuthority` quedó al finalizar:

```json
{"enabled": false, "allowedHostUids": [], "allowedRoomIds": []}
```

Las listas son opcionales y exclusivas de Admin. Cuando existen, ambas deben
autorizar el inicio; una lista vacía o mal formada bloquea. No agregan lecturas:
se validan en la misma lectura del gate. El gate afecta inicios nuevos, no el
avance de partidas existentes. No hay apertura general de V3 ni cambio del
protocolo de salas previas. Para una prueba manual se habilitan solo el UID del
creador y su sala, y se vuelve a cerrar al terminar.

## Prueba real realizada

`scripts/test-server-v3-cloud.cjs` crea cinco cuentas descartables y una sala
privada. Admin prepara y limpia exclusivamente esos fixtures. Los clientes usan
Auth real, App Check real con credencial debug temporal registrada, SDK RTDB y
Firestore autenticados y las callables desplegadas. No usa emuladores ni ejecuta
el motor o los vencimientos desde el proceso de prueba.

Comprobado en Cloud:

1. Inicio rechazado con gate cerrado y aceptado solo para la sala y creador autorizados.
2. Lectura de proyección propia permitida y lectura de rol ajeno denegada.
3. Avance REPARTO → NOCHE con el creador desconectado, sin llamar a recuperación.
   Se exige además un log de `resolverFaseV3` con `operation=deadline` y `changed=true`;
   no se confunde el avance por Tasks con el cron de respaldo.
4. Abandonos aceptados por callable y final resuelto por servidor.
5. Historial persistido por cuenta; quien abandonó tiene `won=false`. Lectura del
   historial propio permitida y del ajeno denegada.
6. Revancha con otro `matchId`, proyecciones anteriores limpiadas y `listo` permitido
   de nuevo en el lobby.

La última corrida y la prueba adicional de `accionPartidaV3` con reintento del mismo
`requestId` se registran en `output/v3-cloud/qa.log` y `qa-evidence.json`. Este último
es la evidencia de éxito y limpieza; no contiene contraseñas, tokens ni roles.
Los tokens debug temporales se revocan y se borran sala, cuentas, historial,
perfiles y limitadores propios. El manifiesto de IDs permite revisar una interrupción.

Reproducción, con Node 22 y sesión Firebase CLI del proyecto:

```sh
TRAIDORES_REAL_FIREBASE_CONFIRM=traidores node scripts/test-server-v3-cloud.cjs
```

Es una prueba pequeña en el proyecto real, no una prueba de carga ni una partida
entre cinco personas. No ejecutar múltiples instancias a la vez: administra el
gate durante su corrida y exige que inicialmente esté cerrado.

Verificación previa: 32 integraciones de servicio/recuperación y 38 unitarias del
motor/endpoints/recuperación aprobadas. Tras cambiar IAM, 7 unitarias de endpoints;
para actualizar historial, 6 unitarias adicionales. Las integraciones se ejecutaron
con solo Firestore/RTDB emulados: Functions activo interfería con los tiempos fijos
de sus fixtures. No detener los emuladores que usa Claude en 8081/9000/9099/5001.

## Android preparado

APK `output/traidores-v3-cloud-debug.apk`, compilado con:

```sh
node scripts/with-jdk.cjs sh ./gradlew :app:assembleDebug \
  -PtraidoresServerOnlineV3=true -PtraidoresOnlineAuthorityEmulator=false
```

Instalado en la AVD Pixel_10 (`emulator-5558`). Se comprobó el menú online
conectado al proyecto real, con identidad de invitado. Se registró el proveedor
App Check debug de esta AVD; su nombre es `Codex Pixel10 V3 Cloud QA`.
No compartir su secreto ni usarlo como proveedor de Release. Retirar el registro
desde App Check cuando termine esta QA. Los tokens de las pruebas automáticas
son distintos y se revocan al terminar cada corrida.

Release conserva `SERVER_ONLINE_V3=false`. La pantalla de partida V3 sigue siendo
la presentación técnica; integrar la mesa habitual es trabajo pendiente. El
driver `ServerGameSmokeActivity` continúa bloqueado fuera de los emuladores y no
se reutiliza en Cloud. No se alteraron archivos iOS de Claude.

## Para Claude

Continuar el adaptador V3 de iOS, sobre el contrato vigente, y revisar:

- Usar configuración Firebase real sin `-firebase-emulator-host`, con App Check
  Debug registrado para su simulador. No desactivar enforcement para probar.
- Alta de jugador con `protocolVersion=3` y `puedeArbitrar=false`.
- Tres listeners por jugador: public, private propio y permissions propios.
- Reconstruir mesa desde snapshot; eventos una vez por `matchId+seq` y fases por
  `matchId+phaseIndex`. Revancha descarta todo el estado del match anterior.
- App cerrada: fases avanzan por Tasks; reconexión no ejecuta ClassicGame como árbitro.
- Cero antes de publicación: «Resolviendo…» y recuperación limitada con dispersión.
- Abandono vivo o muerto suma derrota; historial y contadores provienen de Firebase.
- Ventana del Alcalde sin revelar identidad secreta y Desertor según decisiones cerradas.

Reportar build, pasos, resultado y errores observados. Coordinar UID/sala con Codex
antes de abrir su prueba; no habilitar V3 para todos ni publicar credenciales en el
informe. Falta una prueba Android/iOS conjunta cuando su cliente esté disponible.
El usuario por ahora solo tiene la Mac: no se verificó aún una partida con cinco
personas/dispositivos. Puede usar Android Studio; instalar otro emulador no es necesario.

## Consumo y límites de esta entrega

El log real del vencimiento de la corrida aprobada registró 1 intento de
transacción, 1.363 ms de operación y 3.516 bytes de proyección. Es una observación
de una sola fase, no latencia garantizada ni bytes cobrados por receptor.

Falta medir partidas completas de 5/10/15 en Cloud, con intenciones y chat,
contención, lecturas del limitador, los tres listeners, reconexión y facturación.
La preparación y limpieza Admin de QA se separan de ese costo de gameplay.
El cron cada minuto tiene consumo aunque no haya partidas: lee control y consulta
outboxes; las instancias mínimas son cero, pero eso no significa costo total cero.
No se inferirá el precio mensual a partir de esta prueba corta. Mantener alertas
de presupuesto y revisar métricas antes de abrir una beta V3 general.

Backups de reglas e IAM anteriores, estado de despliegue e índices y logs bajo
`output/v3-cloud/` (ignorado por git). No adjuntar logs de Auth/App Check sin
revisarlos: pueden contener material sensible de debug.
