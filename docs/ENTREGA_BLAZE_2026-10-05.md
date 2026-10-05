# Entrega: Firebase real y siguiente bloque del online

Fecha: 5 de octubre de 2026. Proyecto Firebase `traidores` (99323018581), compartido por Android e iOS.
Base Git: `14ab81990a09933de66295db1d8eaf90cb720a77`, rama `ios-port`, más cambios locales.
Este documento identifica el despliegue por archivos y comprobaciones; no afirma que esos cambios
ya estén confirmados o subidos a Git. Se preservaron los cambios concurrentes de gameplay y sonido.

## 1. Qué está habilitado

- Blaze está vinculado a la prueba gratuita de Google Cloud que activó el usuario: USD 300,
  vencimiento mostrado el 4/1/2027. No se actualizó la cuenta de prueba a una cuenta completa de pago.
- Storage: bucket Firebase predeterminado `traidores.firebasestorage.app`, Standard, `US-CENTRAL1`.
  Coincide con la ubicación de la RTDB existente y es una región elegible para la franquicia de Storage.
  No hay permisos IAM públicos `allUsers` / `allAuthenticatedUsers` en el bucket.
- Reglas Storage, Firestore, índices Firestore y reglas RTDB publicados. La comparación de las reglas
  descargadas de producción con los archivos locales dio coincidencia.
- Node.js **22** en las siete funciones. APIs de ejecución, despliegue, eventos y Scheduler habilitadas.
  Cloud Tasks todavía no se incorporó: pertenece a la migración de autoridad.
- Android `traidoresProfileStorage=true` por defecto; iOS `TRAIDORES_PROFILE_STORAGE_ENABLED=YES`.
  Ambas builds Debug compilaron y se verificó el valor efectivo de sus flags.

### Funciones publicadas

| Función | Región | Generación | Máximo de instancias |
|---|---|---:|---:|
| `iniciarPartidaV2` | Santiago, `southamerica-west1` | 2 | 10 |
| `guardarHistorialOnlineV1` | Santiago | 2 | 2 |
| `contarPartidaLocalV1` | Santiago | 2 | 2 |
| `registrarSalaHuerfanaV1` | Santiago | 2 | 2 |
| `programarLimpiezaSalaV2` | Santiago | 2 | 2 |
| `limpiarSalasAbandonadasV1` | São Paulo, `southamerica-east1` | 2 | 1 |
| `borrarHistorialCuentaV1` | São Paulo | 1 | 2 |

Todas tienen mínimo de instancias 0 y memoria de 256 MiB. La limpieza está programada cada
15 minutos en UTC. El trigger de borrado de Auth es generación 1, que no se ofrece en Santiago.
Cloud Scheduler tampoco ofrece Santiago; por eso la función programada está en São Paulo.
Se retiró únicamente la copia nueva de limpieza que había quedado sin programación en Santiago.
Artifact Registry tiene una política de limpieza de imágenes de un día en ambas regiones.

Verificación final de las **16:08:25 UTC**: siete funciones `ACTIVE`, reglas coincidentes,
job de limpieza `ENABLED`, último estado de ejecución 0. El barrido de las 16:06:34 UTC
procesó 29 salas antiguas elegibles: 29 limpiadas y cero fallos. Se pausó temporalmente
el job mientras se añadía la protección del historial y quedó reanudado tras desplegarla.

## 2. Historial y protección de datos

`guardarHistorialOnlineV1` recibe el snapshot final duradero del evento de Firestore. El historial
privado vive en `cuentas/{uid}/historial/{recordId}`. Cada resultado y sus contadores se confirman
en una transacción; la clave derivada de `online:{matchId}` evita duplicados ante eventos repetidos.
Solo se registra para perfiles de cuentas con número público; invitados y jugadores simulados
no reciben historial por cuenta. Los resultados cancelados no se registran como partidas terminadas.

Se corrigió la victoria del Desertor en `accountHistoryService.js`: debe estar vivo **y** pertenecer
al bando ganador. Los demás miembros del Pueblo/Traidores pueden ganar aunque hayan sido eliminados;
la victoria especial del Bufón se conserva. Esta corrección ya está desplegada.

`contarPartidaLocalV1` contabiliza los registros locales creados por la cuenta bajo las reglas
vigentes, manteniendo `origen=local`. Ese origen no certifica una partida arbitrada por el servidor.
`borrarHistorialCuentaV1` elimina perfil, historial privado y fotos del UID eliminado, sin tocar otras cuentas.

### Limpieza sin perder el último resultado

`onlineRoomCleanupService.js` guarda el último resultado final recuperable **antes** de purgar
subcolecciones y el espejo RTDB. Si falla el archivado, conserva el snapshot completo y los bloqueos;
el siguiente barrido puede reintentar sin contar otra vez un resultado ya guardado.
Para una sala antigua sin evento de historial, la fecha recuperada usa su última actividad de servidor;
no permite reconstruir la hora exacta ni partidas anteriores cuyo snapshot ya fue sobrescrito.

La política conserva salas con presencia conectada, actividad reciente o reloj incierto. Las retenciones
actuales son un día para espera/finalizada/abandonada y siete días para `en_juego`. El barrido usa
una cola de vencimientos acotada y un descubrimiento inicial con cursor; no relee todas las salas
cada 15 minutos. Los tombstones impiden recreaciones durante o después de la purga.

## 3. Fotos y acceso

Las reglas permiten leer fotos con Auth y escribir solo dentro del UID de una cuenta registrada,
con JPEG de hasta 256 KiB y nombre versionado `avatar_<hash SHA-256>.jpg`. No se agregó SafeSearch,
según la decisión del usuario para la beta. El perfil publica la referencia de la foto y las apps
ya tienen el flujo de subida; ahora sus flags permiten usar el bucket real.

Los enlaces con token de descarga siguen siendo compartibles. Los permisos de lectura del SDK
y App Check no convierten un enlace compartido en privado. Storage conserva su soft delete
predeterminado de siete días; una eliminación lógica no implica liberación física instantánea.
Las versiones de fotos y esa retención se incluyen en el cálculo de almacenamiento.
[Retención y cargos de soft delete](https://docs.cloud.google.com/storage/docs/soft-delete).

La callable `iniciarPartidaV2` exige App Check. No se activó enforcement global de Firestore,
RTDB, Storage ni Auth. Falta la aceptación nativa de App Check con builds/dispositivos válidos;
no se relajó la callable para hacer pasar una prueba. Apple y App Attest Release mantienen
la dependencia del equipo Apple Developer habilitado del usuario.

## 4. Pruebas realizadas

| Validación | Resultado y alcance |
|---|---|
| Backend unitario, Node 22 | 23/23; inicio, ganador/historial y políticas de limpieza |
| Inicio con emuladores | 6/6; identidad, reintento y recuperación de inicio |
| Historial con emuladores | 5/5; triggers y aislamiento de cuentas |
| Limpieza con emuladores | 11/11; incluye archivado previo, fecha legacy, fallo y reintento |
| Reglas | Cuatro suites: Firestore, historial, Storage y RTDB; rechazos esperados comprobados |
| Firebase de producción | Dos cuentas QA temporales; foto visible entre cuentas, escritura ajena rechazada, historial privado, recuperación de sesión, deduplicación y borrado aislado |
| Android | `:app:assembleDebug --offline`, build correcta y flag de fotos efectivo `true` |
| iOS | Build Debug para simulador correcta; plist compilado con fotos `YES` |

La prueba de producción está en `scripts/test-firebase-production.cjs`. Por defecto solo consulta
el bucket; `--apply --photo <JPEG>` ejecuta el ensayo protegido al proyecto `traidores` y limpia
sus propias cuentas/datos QA. Las credenciales temporales permanecen en memoria.
El final online del ensayo fue un snapshot de sala privada creado por el script de QA con Admin:
prueba los triggers reales y el historial, **no** una partida jugada entre teléfonos.

No se instaló ni publicó un AAB/Release. Falta la prueba visual nativa con dos cuentas sin emuladores:
subir desde galería, recuperar el perfil, ver la foto en lobby/votación/ganadores y comprobar
el historial tras una partida completa. iOS todavía no ofrece gameplay online real (sección 6).

Evidencias locales, ignoradas por Git:

- `output/firebase-release-qa/production-sdk.log`
- `output/firebase-release-qa/cleanup-history-integration.log`
- `output/firebase-release-qa/verified-production.json`: inventario, reglas, presupuesto y hashes
- `output/firebase-capacity/snapshot-2026-10-05T15-50-57-782Z.json`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- App simulador: `output/ios-blaze-build/Build/Products/Debug-iphonesimulator/TraidoresIOS.app`

## 5. Costos: configuración y evidencia disponible

Presupuesto mensual **USD 5**, solo proyecto `traidores`, alertas al **50 %, 80 % y 100 %**.
Se excluyen créditos promocionales del seguimiento y se conserva `FREE_TIER`, para que los
USD 300 no oculten un aumento de consumo. Destinatarios IAM de facturación habilitados.
Es una alerta por correo; no es un corte automático del gasto ni un pago por crear el presupuesto.

La captura de métricas de las 15:50:57 UTC informa, en octubre UTC: **5.036 lecturas y 666 escrituras
Firestore**, y **3.878.511 bytes enviados RTDB**. Las muestras tienen retrasos distintos: RTDB
llegaba al 4/10 y las muestras de Firestore al 5/10 15:48:59. El pico disponible de conexiones
en siete días fue 15, con última muestra de conexiones el 2/10. No son costos por partida
ni una validación de capacidad. La ventana parcial posterior al despliegue tampoco representa
todo el consumo del ensayo QA.

El siguiente ensayo mide salas de 5/10/15 jugadores, lobby quieto, partida, chat, reconexión,
revancha y fotos con/sin caché. Combinar contadores por flujo con Monitoring y facturación:
lecturas/escrituras, bytes RTDB, operaciones/bytes Storage, ejecución CPU/memoria, reintentos
y latencias. Recién con esa referencia se extrapolan participaciones mensuales y capacidad.
El [plan general](PLAN_BLAZE_AUTORIDAD_COSTOS_2026-10-05.md) contiene las fórmulas y etapas.

## 6. Qué falta para dejar de depender del anfitrión

Android todavía calcula la partida en `GameEngine` y publica como anfitrión activo; la aplicación
aún no usa la callable como única ruta de inicio. El historial online conserva ese resultado
calculado por Android: deduplicarlo no protege contra un anfitrión modificado.
El bootstrap real de iOS conserva las salas indisponibles; cuenta/perfil/historial local sí
tienen servicios reales. No se habilitaron servicios falsos en Release.

La migración sigue el bloque D del plan: motor determinista backend, acciones autenticadas,
estado secreto solo de servidor, proyección pública/privada por jugador y tareas de vencimiento
duraderas. Después se conectan Android e iOS a ese contrato. Apagar al creador debe dejar
continuar noche/debate/voto/resultado y guardar el historial sin su regreso.

### Reglas decididas por el usuario: mandan sobre las diferencias actuales

Se leyó [CASOS_PARIDAD_SERVIDOR.md](../ios/docs/CASOS_PARIDAD_SERVIDOR.md). Su sección 0
es vinculante; estas reglas están pendientes de implementación del motor, no se presentan como resueltas:

1. **Mercenario:** no puede repetir la víctima de silencio la noche siguiente. Carta inhabilitada
   en iOS y validación de servidor con `ultimaRondaSilenciado`. Cooldown registrado cuando se aplica
   el silencio; protección del Médico o muerte esa noche lo anulan, conforme a Android.
2. **Desertor:** implementar 4.4-1 y DES-09/DES-12 a DES-14. Cuando la victoria sería Traidores y
   el Desertor vivo todavía puede reconsiderar, abrir ventana con plazo de servidor. Solo él
   elige; mantener o vencer consume la reconsideración; reevaluar ganador de inmediato. No hay
   dependencia de qué teléfono es anfitrión ni omisión AFK por esta acción opcional. Usar `votacionSeg`
   configurado de la sala; la sugerencia de 20 segundos del informe no cambia el valor actual por sí sola.
3. **Alcalde silenciado:** no habla, vota, se revela ni decide el segundo empate. También excluido
   de corrupción si está entre los empatados. Corregir validación en ambos motores y servidor (ALC-08).
4. **Oráculo:** la invitación enviada válidamente de noche permanece si lo matan esa misma noche.
   Validar contra el estado inicial de la noche y probar ORA-06.

También portar el desempate determinista de asesinos (ruido con acumulador 0, distinto del ruido
de lealtad con 17), bando automático del Desertor, AFK y reinicio de todas las habilidades en revancha.
Los vectores Kotlin/JS y las pruebas del servidor deben fijar la misma salida.

## 7. Reparto para el siguiente bloque

Codex implementa y conecta backend/Android/iOS. Claude revisa y prueba sin editar los mismos archivos.
El informe de paridad ya está entregado; no hace falta repetir esa auditoría entera.

Encargo acotado para Claude:

> Leé esta entrega y tu informe de paridad. Revisá especialmente el archivado antes de limpiar,
> la matriz de ganadores del historial y las cuatro decisiones de la sección 0. Señalá cualquier
> discrepancia con archivo/línea y caso reproducible. Para la migración, prepará fixtures con
> reloj y acciones para DES-09/DES-12 a DES-14, ALC-08, ORA-06 y cooldown de Mercenario;
> contrastalos con Codex cuando esté el motor. No modifiques funciones, reglas, adaptadores
> ni el proyecto Xcode. No actives el online iOS con servicios falsos. La prueba final será una
> partida mixta sin anfitrión y revancha, después de conectar ambos clientes.

Procedimiento de release preparado: `node scripts/prepare-firebase-release.cjs --storage --database`
es consulta. Agregar `--apply` despliega backend, Firestore, RTDB y Storage en ese orden.
`--force` se reservó al primer despliegue de triggers con reintentos y al inventario ya revisado;
no hace falta en releases ordinarias. El script no crea facturación ni cambia la prueba gratuita.

Fuentes de configuración y precios: [Firebase](https://firebase.google.com/pricing),
[Storage](https://cloud.google.com/storage/pricing),
[Scheduler: regiones](https://cloud.google.com/scheduler/docs/locations),
[Functions: regiones](https://firebase.google.com/docs/functions/locations),
[presupuestos](https://docs.cloud.google.com/billing/docs/how-to/budgets).
