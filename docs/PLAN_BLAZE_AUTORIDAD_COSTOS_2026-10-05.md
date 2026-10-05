# Plan de Blaze, fotos, historial y autoridad online

Fecha: 5 de octubre de 2026. Proyecto: `traidores`; Android e iOS comparten backend.
Este documento conserva el diagnóstico inicial y el orden de trabajo. **Actualización del 5/10:**
Storage, las siete funciones y las reglas ya están desplegados; fotos e historial pasaron un ensayo
SDK con dos cuentas temporales en Firebase real. Ambas builds compilaron con fotos habilitadas.
Falta la aceptación visual nativa y trasladar la autoridad de la partida al servidor.
La [entrega del despliegue](ENTREGA_BLAZE_2026-10-05.md) detalla pruebas, regiones y pendientes.

## 1. Diagnóstico inicial, antes del despliegue

Esta tabla corresponde a la consulta inicial de las 14:59 UTC; sus pendientes A/B se resolvieron
como se describe en la entrega. C, D y E siguen siendo trabajo de migración y medición.

| Componente | Situación | Trabajo pendiente |
|---|---|---|
| Facturación | `billingEnabled: true`, confirmado por Cloud Billing | Verificar alertas y tratamiento de créditos |
| Firestore | Standard Native, `(default)`, `southamerica-west1` | Desplegar reglas/índices compatibles e historial |
| Realtime Database | Instancia activa en `us-central1` | Medir tráfico y adaptar permisos al nuevo protocolo |
| Storage | `traidores.firebasestorage.app` devuelve 404: el bucket no existe | Crear bucket y desplegar sus reglas |
| Cloud Functions | La API devuelve `SERVICE_DISABLED` | Habilitar dependencias y desplegar backend |
| Historial/fotos de las apps | Implementados y probados con emuladores; fotos deshabilitadas por configuración | Aceptación contra producción y habilitación en builds |
| Inicio online | `iniciarPartidaV2` preparado; Android todavía inicia mediante transacción cliente | Conectar ruta de servidor |
| Desarrollo de la partida | Android ejecuta `GameEngine` y publica como anfitrión activo | Migrar resolución completa al backend |
| Online iOS | Cuenta/perfil/historial preparados; salas siguen indisponibles en el bootstrap real | Adaptadores de salas y gameplay sobre el nuevo contrato |

En esa consulta inicial no podía confirmarse el inventario de Functions mientras su API estaba deshabilitada.
La captura del usuario muestra la prueba gratuita hasta el 4 de enero de 2027.
Activar Blaze no convierte automáticamente el código de Android en autoridad de servidor.

Hay cambios locales de paridad de gameplay y Analytics en Android e iOS. Se preservan;
antes de producir una versión hay que integrar y fijar la revisión que realmente se probará.
Referencia de implementación anterior: `ENTREGA_CLAUDE_FIREBASE_2026-10-04.md`.

## 2. Etapas y criterio para cerrarlas

### A. Preparar un despliegue compatible

1. Integrar los cambios actuales de ambas plataformas y revisar sus payloads frente a las reglas.
2. Actualizar `functions/package.json` y `firebase.json` a Node.js 22 y repetir los checks del backend.
   **Hecho:** Node 22 y 23 pruebas unitarias correctas.
   Node.js 20 está deprecado y tiene retiro previsto el 30/10/2026.
   [Calendario oficial de runtimes](https://docs.cloud.google.com/functions/docs/runtime-support).
3. Corregir la paridad de ganadores del historial: la versión inicial de `accountHistoryService.js`
   concedía victoria a un Desertor eliminado si coincidía el bando. **Corregido y desplegado:**
   exige `vivo=true` y bando final ganador; pruebas de eliminado/vivo, Bufón y demás roles correctas.
4. Revisar App Check antes de llamar a `iniciarPartidaV2`, que ya exige un token válido.
   Verificar Android de Play y el token debug de los dispositivos de desarrollo.
   iOS Release/App Attest y Apple siguen teniendo la dependencia del equipo Apple habilitado.

**Cierre:** build y pruebas de reglas/contratos/backend sobre una revisión identificada;
el resultado visible y el guardado en Firebase coinciden. No mezclar un AAB antiguo con reglas
que rechacen los campos que ese AAB todavía escribe.

### B. Configurar y desplegar fotos, historial y limpieza

1. Comprobar el presupuesto mensual del proyecto de USD 5, sus destinatarios y alertas
   al 50 %, 80 % y 100 %. **Verificado y configurado:** mensual, solo proyecto `traidores`,
   destinatarios IAM habilitados y los tres umbrales guardados.
2. En el seguimiento de costo posterior a la prueba, excluir los créditos promocionales
   del cálculo del presupuesto para que los USD 300 no oculten el consumo; conservar la
   franquicia gratuita. Revisar el filtro `Promotions`.
   [Filtros de ahorros y créditos](https://docs.cloud.google.com/billing/docs/how-to/budgets).
3. Crear el bucket desde Firebase en modo de producción. **Creado:** `traidores.firebasestorage.app`,
   **Standard, `us-central1`**, con reglas publicadas. Las fotos se comprimen y pueden almacenarse en caché;
   esta región además coincide con la RTDB existente. Firestore y cinco funciones están en Santiago;
   limpieza programada y borrado Auth están en São Paulo por disponibilidad regional de sus servicios.
   La franquicia de Storage para buckets nuevos exige regiones elegibles de EE. UU.;
   elegir Santiago para las fotos cambiaría el cálculo de costo.
   [Condiciones oficiales de Storage](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024).
4. Habilitar las APIs necesarias para el despliegue: Functions, Run, Build, Artifact Registry,
   Eventarc/Pub/Sub y Scheduler, según la generación y los triggers. Verificar cuentas de servicio
   y permisos del despliegue. **Habilitadas para estas siete funciones.** Cloud Tasks se incorpora
   en la etapa de autoridad.
5. Desplegar explícitamente las siete funciones preparadas:
   `iniciarPartidaV2`, `limpiarSalasAbandonadasV1`, `registrarSalaHuerfanaV1`,
   `programarLimpiezaSalaV2`, `guardarHistorialOnlineV1`, `contarPartidaLocalV1`,
   `borrarHistorialCuentaV1`. **Las siete están activas en Node 22.** Verificar región, triggers y una ejecución real.
6. Desplegar reglas e índices Firestore, reglas Storage y los cambios RTDB que hayan pasado
   las pruebas de compatibilidad. **Publicado y cotejado con producción.** Se añadió la etapa
   RTDB al script `prepare-firebase-release.cjs` mediante `--database`.
7. Habilitar fotos en las builds Android/iOS después de confirmar el bucket y sus reglas.
   **Hecho y compilado;** falta aceptación visual desde las apps.

**Cierre:** con dos cuentas reales, una foto publicada se ve en la otra cuenta y sobrevive
al reinicio; perfil, lobby, votación y resultado Android muestran la misma foto. Historial
online guarda una vez cada resultado y se recupera al reinstalar/iniciar sesión. En iOS se
validan perfil e historial local por cuenta; gameplay online se completa en una etapa posterior.
La limpieza conserva salas activas y el borrado de cuenta elimina únicamente sus propios datos.

El ensayo SDK en producción ya confirmó aislamiento, fotos, recuperación de sesión, triggers
de historial y borrado de cuentas. El final de sala del ensayo fue sembrado por QA con Admin;
no sustituye una partida real entre teléfonos ni la revisión visual de todas las pantallas.

El historial online inicial todavía recibe el resultado calculado por Android. La idempotencia
del guardado no certifica un resultado frente a un anfitrión modificado: eso se resuelve en D.
El historial local seguirá identificado como local, con las validaciones de cuenta existentes.

### C. Medir antes de trasladar el motor

Medir una sala quieta, una partida completa y una revancha, con 5, 10 y 15 jugadores.
Separar primera descarga de fotos de las descargas con caché, invitados/cuentas, desconexiones,
reingresos y uso de chat. Guardar hora inicial/final, versión, duración y rondas.

Combinar los contadores por flujo de Android con Cloud Monitoring y facturación. Añadir la
instrumentación equivalente a iOS y las operaciones internas del backend: las métricas locales
actuales son estimaciones parciales y no incluyen todos los reintentos ni lecturas de reglas.

Entregar por escenario: lecturas/escrituras Firestore, bytes RTDB, bytes/operaciones Storage,
invocaciones y tiempo CPU/memoria de Functions, reintentos, errores y latencias p50/p95.
Los contadores técnicos no necesitan mensajes, nombres, correos ni UID como etiquetas de métricas.

**Cierre:** tabla de consumo por participación y por hora de lobby, con actividad base descontada
y huecos de datos identificados. Registrar una referencia para comparar el protocolo nuevo.

### D. Trasladar la autoridad completa al servidor

Propuesta: funciones cortas que validan acciones y un motor de reglas puro en el backend,
con **Cloud Tasks para los vencimientos de fase**. Encaja con este juego por turnos y con
`functions/` existente; el diseño se valida primero en emuladores.

El creador conserva los controles de la sala y el inicio. El servidor ejecuta la partida:
reparto, barrera de entrada/lectura del rol, noches, debate, readiness, votos, desempates,
habilidades, eliminaciones, AFK, ganador, regreso al lobby y revancha.

Contrato propuesto, pendiente de implementar:

- `authorityMode=server`, versión de protocolo, `roomId`, `matchId`, `phaseIndex`,
  versión creciente de estado, plazo de servidor y una clave idempotente por solicitud.
- Identidad de actor obtenida de Auth; el cliente envía intención y objetivo por UID.
  El backend comprueba membresía, vida, rol, fase, plazo y frecuencia.
- Estado completo y secretos en almacenamiento accesible solo al backend. Estado público
  para la sala y datos privados específicos para cada jugador. El anfitrión deja de recibir
  todos los roles; permisos de chat y vida los publica el servidor.
- Una transacción por sala confirma la resolución, el estado duradero y una salida pendiente.
  La publicación RTDB y la programación de tareas tienen reintentos y reparación duraderos:
  no existe una transacción atómica entre Firestore, RTDB y Cloud Tasks.
- Cada tarea identifica partida/fase/versión. Una tarea duplicada, vencida o de la partida
  anterior no modifica una fase nueva. Las acciones concurrentes tampoco resuelven dos veces.
- El contador visual se calcula en el teléfono desde el plazo confirmado; no se escribe
  cada segundo. Una tarea retrasada se presenta como espera de resolución y no convierte
  a un teléfono en árbitro.
- Resultado final inmutable por `matchId` y registro idempotente de todos los participantes.
  Conservar la separación entre cancelación y partida terminada.

Secuencia interna de implementación:

1. Fijar matriz de reglas a partir de `GameEngine`, sus pruebas y los ajustes de paridad actuales.
   Incluir Alcalde, Payador/Contrapunto, Oráculo, Desertor, Bufón, varios asesinos y los tres mapas.
   **Informe recibido:** `ios/docs/CASOS_PARIDAD_SERVIDOR.md`. Su sección 0 manda:
   cooldown de una noche del Mercenario, ventana de reconsideración del Desertor en servidor,
   Alcalde silenciado sin voz/voto/revelación/decisión, invitación del Oráculo conservada si
   muere esa misma noche. Son requisitos pendientes del motor, no correcciones ya implementadas.
2. Motor backend determinista y pruebas de casos reales; aleatoriedad y reloj controlados
   en pruebas, asignación aleatoria confiable en producción.
3. Inicio, acciones autenticadas, tareas y estado privado/público; prueba de una partida
   completa con clientes de prueba, sin intervención del anfitrión.
4. Adaptar Android: observar estado confirmado, enviar acciones y recuperar sesión.
   El modo local conserva su funcionamiento; salas antiguas usan su protocolo identificado.
5. Adaptar iOS al mismo contrato: servicios de sala y lectura de estado del servidor.
   El motor local `ClassicGame` no se utiliza como árbitro de partidas online.
6. Ensayo mixto y publicación gradual en salas nuevas. Impedir versiones incompatibles
   en las salas de servidor; cerrar sus escrituras autoritativas de cliente en las reglas.

**Cierre:** apagar el teléfono del creador durante noche, debate, recuento y resultado no
detiene a los demás; se completa la partida y el historial sin su regreso. Repetir con pérdida
de red, reinicio del backend, entrega duplicada, acciones tardías y revanchas. Roles ocultos y
canales privados no son accesibles desde una cuenta ajena ni desde otro rol de la sala.

[Tareas programadas de Firebase](https://firebase.google.com/docs/functions/task-functions).
Functions está en Santiago y RTDB en EE. UU.: medir la latencia y el tráfico entre regiones
antes de aceptar el protocolo; mantener esa transferencia en el cálculo de costos.
[Regiones y latencia](https://firebase.google.com/docs/functions/locations).

### E. Optimizar, probar carga y publicar capacidad validada

Prioridades concretas de consumo:

- Observar solo la sala y fase vigentes; cerrar listeners al salir. Buscador e historial
  acotados, sin recargar colecciones completas ni consultar por jugador cada segundo.
- Mantener presencia mediante conexiones RTDB y estado en vivo compacto; evitar reflejar
  cada cambio en el documento de sala seguido por todos los clientes.
- Revisar los dos `onDocumentWritten(partidas/{roomId})`: limpiar/guardar historial se invocan
  ante cambios del documento, aunque no haya resultado nuevo. La cola de limpieza evita
  escrituras repetidas, pero su handler todavía lee tres documentos por ejecución.
  Usar eventos específicos o filtrar cambios irrelevantes antes de consultar, con pruebas
  de recuperación y revanchas. Medir también la invocación que sale temprano.
- Reutilizar fotos por URL/versionado y caché; no duplicar subidas por cambios cosméticos.
  Limpiar versiones antiguas únicamente cuando ya no sean necesarias para un perfil o partida;
  no aplicar un borrado por antigüedad que elimine el avatar todavía vigente.
- Límites de solicitudes y acciones por cuenta en servidor, deduplicación y reintentos con
  espera progresiva. Un cooldown visual por sí solo no protege consumo de un cliente modificado.
- `minInstances=0` inicialmente y límites de instancias, memoria y duración explícitos.
  Ajustar capacidad y warm instances solo a partir de latencia medida. Configurar retención
  de logs e imágenes de despliegue; incluir Eventarc, Scheduler y Build/Registry en los costos.

Propuesta de primer objetivo de ensayo: **100 jugadores simultáneos**, sin anunciarlo como
capacidad comprobada. Probar primero tres salas de cinco, luego 30, 60 y 100 participantes,
con fases/chat/fotos/reconexiones representativos. Emuladores para reglas y fallos; carga cloud
separada de usuarios públicos, con tope de ejecuciones y consumo controlado.

Medir p95 de acción confirmada, retraso de cambio de fase, recuperación, colas, conflictos y
errores. Objetivos iniciales a contrastar: acción p95 ≤ 2 s y recuperación p95 ≤ 5 s con
buena señal; registrar arranques fríos y fallos de red por separado. Detener el incremento
ante errores persistentes o crecimiento de cola. Añadir admisión de salas para la capacidad
aceptada; un aviso de presupuesto no sustituye esa admisión.

Blaze publica un máximo de 200.000 conexiones por RTDB y orientación de 1.000 escrituras/s;
estos valores no certifican la capacidad de Traidores.
[Límites RTDB](https://firebase.google.com/docs/database/usage/limits).

## 3. Cómo estimaremos el costo

El total combina Firestore, RTDB, fotos, ejecución del backend y servicios auxiliares.
Las conexiones simultáneas determinan presión y latencia; las operaciones y bytes acumulados
determinan consumo. Se calculan escenarios de 1.000, 10.000 y 100.000 participaciones por mes
a partir de C, separando distribución diaria y horas de lobby.

Tarifa Standard de Santiago comprobada el 05/10: lecturas USD 0,043, escrituras USD 0,129 y
borrados USD 0,014 por 100.000, sobre las franquicias diarias elegibles. Para lecturas:

`sumatoria_por_día(max(lecturas_día - 50.000, 0) / 100.000 * 0,043)`

| Lecturas diarias, durante 30 días iguales | Solo lecturas Firestore al mes |
|---|---:|
| 50.000 | USD 0 |
| 100.000 | USD 0,65 |
| 1.000.000 | USD 12,26 |
| 10.000.000 | USD 128,36 |

Son ejemplos aritméticos, no mediciones ni una factura completa.
[Precios regionales Firestore](https://cloud.google.com/firestore/pricing).

RTDB: referencia publicada USD 1/GB descargado sobre 360 MB/día. Con unidades decimales
orientativas, 1 GB cada día durante 30 días da aproximadamente USD 19,20 de descarga RTDB;
10 GB/día, aproximadamente USD 289,20. Incluir almacenamiento si excede su franquicia.
[Precios Firebase](https://firebase.google.com/pricing).

Fotos: medir tamaño medio, subidas, descargas efectivas tras caché y almacenamiento acumulado.
Con el máximo actual de 256 KiB, 1.000 fotos ocupan hasta 250 MiB por versión. Storage cobra
por bytes y operaciones; los límites gratuitos son mensuales y dependen de la región.
[Precios Storage](https://cloud.google.com/storage/pricing).

Functions v2 usa la facturación de Cloud Run: medir CPU/memoria/tiempo y solicitudes, además
de las operaciones Firestore que ejecuta. La generación 1 de borrado de cuentas se calcula
aparte. La cantidad gratuita de invocaciones por sí sola no prueba un costo total cero.
[Facturación de ejecución y despliegue](https://cloud.google.com/run/pricing).

Cloud Tasks contabiliza llamadas y entregas, incluidos reintentos: primer millón mensual
de operaciones sin cargo, después USD 0,40 por millón. Una tarea no equivale necesariamente
a una única operación. [Precios Cloud Tasks](https://cloud.google.com/tasks/pricing).

Separar consumo antes del crédito promocional, crédito aplicado y pago efectivo. Los créditos
de prueba no reducen el consumo técnico que queremos optimizar. Los importes anteriores están
en USD y no incluyen conversión bancaria ni impuestos.

### Controles de gasto

El presupuesto de alerta no suspende servicios. La documentación actual también ofrece
spend caps para Functions y otros productos específicos; comprobar disponibilidad y decidir
el comportamiento ante una pausa. Ese cap no cubre todo Firestore/RTDB/Storage.
[Alertas y spend caps de Firebase](https://firebase.google.com/docs/projects/billing/avoid-surprise-bills).

Durante la beta se mantiene la decisión de subir fotos sin SafeSearch. Validación de formato,
tamaño, permisos y frecuencia sigue aplicando. Las URLs actuales con token de descarga son
compartibles: App Check del SDK no convierte esos enlaces en privados ni limita por sí solo
su tráfico externo. Incluir ese tráfico en vigilancia de costos.

## 4. Línea base previa al despliegue

Snapshot local: `output/firebase-capacity/snapshot-2026-10-05T14-59-29-498Z.json`.

- Facturación activa; Firestore Santiago; RTDB activa en EE. UU.
- Pico disponible en siete días: 15 conexiones. Última muestra de conexiones: 02/10.
- Octubre hasta la consulta: 5.002 lecturas y 649 escrituras Firestore; últimas muestras 02/10.
  Últimas 24 horas de Firestore: **sin muestras**, no cero operaciones confirmado.
- Bytes enviados RTDB, octubre UTC: 3.878.511; última muestra 04/10.
- API de Functions y API de presupuestos deshabilitadas; bucket inexistente.

Estos números no describen una partida aislada ni prueban ausencia de saturación actual.
No sumar las métricas legacy a las actuales, ni convertir una ventana UTC móvil en cuota
diaria Firestore. Una foto descargada pertenece a Storage, no a los bytes de gameplay RTDB.

## 5. Reparto de trabajo y próximo bloque

Codex puede realizar backend, reglas, despliegue, adaptación Android, integración iOS y medición.
Claude no es un requisito para avanzar. Si el usuario quiere aprovecharlo, el encargo acotado es:

> Revisá las reglas actuales y los cambios de paridad de Android/iOS. Prepará una matriz de
> casos con estado inicial, acciones y resultado esperado: Alcalde/voto doble/desempate,
> Payador/Contrapunto, Oráculo, Desertor vivo y eliminado, Bufón, varios asesinos, AFK y revancha.
> Guardala en `ios/docs/CASOS_PARIDAD_SERVIDOR.md`. Señalá las diferencias entre motores;
> no modifiques backend, reglas, adaptadores online ni el proyecto Xcode. Esa matriz servirá
> para probar el futuro motor del servidor. No des por implementado el gameplay online iOS.

El usuario transmitió el encargo y devolvió el informe de Claude. No se enviaron mensajes
externos a Claude mediante herramientas. La entrega contiene una revisión acotada para él.

**Próximo bloque:** aceptación visual nativa de A/B, medición C y migración D para eliminar la
dependencia del anfitrión. La [entrega](ENTREGA_BLAZE_2026-10-05.md) identifica lo ya publicado
por base Git, hashes y resultados de aceptación; falta confirmar los cambios locales en Git.
Cada publicación de las apps se identifica por commit, versión y resultados de aceptación;
las salas de servidor solo se abren al público tras la prueba completa de D/E.
