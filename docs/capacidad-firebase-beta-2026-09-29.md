# Traidores: capacidad y preparación para la exposición

Consulta del 29/09/2026, aproximadamente 23:09 ART. Exposición tentativamente el
27/10/2026: QR público hacia Google Play, asistentes desconocidos y datos móviles.

## Actualización del 30/09, 03:57 ART

Snapshot de solo lectura guardado en `output/firebase-capacity`, captura
2026-09-30T06:57:23.747Z. Billing continúa deshabilitado, Firestore Standard Native
en Santiago. En las últimas 24 horas móviles: 10.735 lecturas, 1.293 escrituras
Firestore y 24.396.823 bytes enviados RTDB. Pico RTDB observado en siete días:
quince conexiones. Las muestras disponibles de bloqueo por sobrecuota son cero.

La métrica mensual RTDB conserva una muestra de las 03:00 ART: 80.652.480 bytes
frente a 10.737.418.240 bytes de límite, alrededor de 0,75 %. Su antigüedad impide
atribuirle todo el tráfico de las partidas que finalizaron después. No sumar
contadores legacy de Firestore a los actuales, ni usar el mes para cuota diaria.
No se observa agotamiento en estas métricas; no certifican disponibilidad
ininterrumpida ni capacidad para una exposición con concurrencia desconocida.
El inventario Functions sigue sin estar disponible (403).

Se completaron dos partidas consecutivas de quince en 0.1.45, con cambio de
coordinador y regreso de todos. Son una sola sala; siguen pendientes aislamiento
de varias salas, recuperación del coordinador suplente durante una fase vigente,
distribución de Play y decisión de capacidad antes del QR público. Ver el plan
actual en `cierre-beta-2026-09-30.md`. Las secciones de prueba 0.1.40 más abajo
son históricas, no identifican el APK actual.

Límites verificados nuevamente el 30/09:
- https://firebase.google.com/docs/database/usage/limits — Spark, 100 conexiones.
- https://firebase.google.com/docs/firestore/quotas — 50.000 lecturas/día gratis,
  reinicio alrededor de medianoche del Pacífico.
- https://support.google.com/googleplay/android-developer/answer/14151465?hl=es
  — acceso a producción necesario para disponer de pruebas abiertas.

## Diagnóstico verificado

El proyecto Firebase `traidores` figura ACTIVE. Cloud Billing informa
`billingEnabled: false`: actualmente no está habilitado Blaze. Firestore es
Standard, Native, en `southamerica-west1`.

Consulta de solo lectura a las API de Firebase, Billing y Cloud Monitoring:

| Indicador | Resultado | Ventana y limitaciones |
|---|---:|---|
| Pico RTDB | 15 conexiones | Últimos 7 días, muestras agregadas por minuto |
| Descargas mensuales RTDB | 79.611.045 bytes, aproximadamente 79,6 MB | Última muestra mensual disponible |
| Límite mensual observado RTDB | 10.737.418.240 bytes | Cuota publicada como 10 GB/mes |
| Fracción mensual consumida | 0,74 % | Cociente de las dos métricas anteriores |
| Datos almacenados RTDB | 208.627 bytes | Última muestra disponible |
| Tráfico RTDB | 12.326.796 bytes | Últimas 24 horas, todos los usuarios |
| Lecturas Firestore | 8.348 | Últimas 24 horas, todos los usuarios |
| Escrituras Firestore | 796 | Últimas 24 horas, todos los usuarios |
| Lecturas Firestore | 42.906 | Septiembre hasta la consulta, ventana UTC |
| Escrituras Firestore | 4.741 | Septiembre hasta la consulta, ventana UTC |

No hay evidencia de agotamiento de capacidad en este volumen. Esto no certifica
la salud de todas las sesiones ni establece capacidad para 100 jugadores. Las
métricas tienen retraso y muestras discontinuas: el último valor de conexiones
no equivale a conexiones presentes ahora. La métrica de bloqueo por excesos tuvo
cinco muestras con valor cero; tampoco demuestra disponibilidad continua.

Las métricas nuevas de Firestore `read_ops_count` y `write_ops_count` son las
utilizadas. Las antiguas se conservan para contraste, nunca se suman a las nuevas.
Las 24 horas móviles no coinciden con el día de cuota, que reinicia en horario
del Pacífico. No corresponde convertir sus totales directamente en porcentaje
de cuota consumida hoy.

La consulta de inventario de Cloud Functions devolvió 403 PERMISSION_DENIED.
No permite afirmar si existe alguna función desplegada. El código del repositorio
documenta inicio y limpieza preparados y probados localmente, pendientes de
despliegue. Android de producción todavía coordina las partidas desde un cliente.

## Cuántas personas admite

Spark limita RTDB a 100 conexiones simultáneas por base. Cuenta dispositivos,
pestañas y procesos conectados; no descargas ni cuentas registradas. Lobby y
otras superficies que mantengan RTDB conectado también consumen conexiones.
Los emuladores de prueba comparten ese límite con el público.

Seis salas de 15 sumarían 90 dispositivos de juego antes de otros clientes.
Es un ejemplo aritmético, no una capacidad validada. Además existen cuotas
Standard de 50.000 lecturas y 20.000 escrituras Firestore por día y descargas
RTDB de 10 GB por mes. Estas pueden limitar el servicio aunque no se llegue a
100 conexiones. Por ahora se probó una sala de 15, con incidencias de cambio de
anfitrión en 0.1.39; los arreglos de 0.1.40 aún requieren repetición en vivo.

Blaze eleva el límite de RTDB a 200.000 conexiones por base. Ese techo del
proveedor no es una promesa de rendimiento del juego. La capacidad real debe
probarse con varias salas, tráfico, reconexiones y el protocolo actual.

Para un QR abierto y concurrencia desconocida se recomienda preparar Blaze
antes del evento, con medición y controles de gasto. Mantener Spark exige limitar
el acceso y organizar turnos con margen respecto del límite; no existe ese
control global de admisión implementado actualmente.

Fuentes consultadas el 29/09:
- https://firebase.google.com/pricing
- https://firebase.google.com/docs/database/usage/limits
- https://firebase.google.com/docs/firestore/quotas
- https://docs.cloud.google.com/monitoring/api/metrics_gcp_d_h

## Lo que faltaba validar en Android 0.1.40

APK `app/build/outputs/apk/debug/Traidores-0.1.40.apk`, versionCode 41.
Contiene recuperación de estado tras perder autoridad, descarte de carteles
históricos durante recuperación y ajustes para salas antiguas. No hay un APK
más reciente en este checkout. Main local está en `966c7e6`.

Prueba principal: sala NUEVA de 15, ritmo normal, todos con 0.1.40. Separar las
interrupciones; no bloquear dos participantes simultáneamente.

1. Invitado: durante discusión, con al menos 60 segundos restantes, minimizar y
   bloquear 30 segundos. Volver y medir desde desbloqueo hasta poder interactuar.
   Debe mostrar la discusión actual, chat y acciones correspondientes.
2. Anfitrión original: bloquear 30 segundos al entrar en el recuento. Dejar que
   los demás avancen. Volver cuando los otros estén en la noche, si ya llegaron.
   Debe quitar el recuento antiguo, aplicar la fase vigente y permitir la acción
   nocturna si está vivo y su rol puede actuar. Si regresa antes de la noche,
   repetir en otra ronda con el retorno ya en noche.
3. Segundo coordinador: después de identificar en registros quién asumió la
   coordinación, pausar ese dispositivo cerca del siguiente recuento. Es el caso
   que dejó al emulador Espía sin poder actuar en la prueba anterior. Requiere
   observar registros; no asumir que es siempre el emulador 6.
4. Terminar, copiar reportes desde el lobby y comprobar que vuelven todos sin
   ingresar por código. Cambiar mapa y completar una segunda partida en la misma
   sala. No debe quedar SINCRONIZANDO ni echar al creador.

Objetivo de aceptación propuesto: recuperación de un invitado en unos 5 segundos
con buena señal; registrar tiempo real si lo supera. El traspaso de coordinador
se evalúa además por continuidad de los demás y posibilidad de actuar en la fase
vigente. El objetivo es un criterio de prueba, no una garantía temporal existente.
No debe permitirse actuar a un jugador ya eliminado, incluso si fue por AFK.

Enviar: versión instalada; código de sala para localizarla; dispositivo pausado;
fase antes/después; segundos de pausa/recuperación; reporte de anfitrión y de
afectado (y nuevo coordinador si lo hubo). Si se atasca, captura y hora aproximada
antes de reiniciar. No hace falta copiar los 15 reportes si no hay otras incidencias.

Después: tres salas de 5 simultáneas para verificar aislamiento, chat y regreso.
Esto prueba varias salas con los mismos 15 dispositivos; no prueba 100 conexiones.
Finalmente repetir desde la versión distribuida por Google Play, con instalación
nueva, cuenta invitada y Google, permisos y redes móviles distintas.

## Medición, seguimiento y escalado

Se añadió `scripts/firebase-capacity-snapshot.cjs`. Requiere Node moderno,
dependencias instaladas y sesión de Firebase CLI con acceso al proyecto.

```text
node scripts/firebase-capacity-snapshot.cjs
```

Solo consulta metadatos, facturación y métricas. No despliega, no activa
facturación, no crea usuarios y no lee nombres, chat, roles ni correos. Guarda
una instantánea actual y archivos fechados en `output/firebase-capacity`, ignorado
por Git. Los errores de acceso o métricas sin muestras quedan identificados,
no se interpretan como cero. No es un monitor continuo ni crea alertas.

Medir una partida aislada: cerrar otras sesiones, registrar hora inicial y final,
jugadores, duración y rondas; consultar métricas tras su consolidación. Restar
actividad base del intervalo cuando corresponda. Registrar lecturas/escrituras,
descargas RTDB y reportes de clientes en `plantilla-medicion-online.csv`. El
contador de red de Android incluye otros servicios y no es facturación RTDB.

Repetir por sala y en paralelo. Escalonar una prueba sintética aislada en proyecto
de pruebas hacia 30, 60 y 100 conexiones, una vez habilitado el plan adecuado.
Reproducir listeners, chat y fases; una conexión vacía no simula el juego completo.
Detener el aumento si aparecen errores persistentes o degradación de recuperación.
Pruebas del emulador local de Firebase validan reglas/protocolo, no capacidad cloud.

Seguimiento sugerido antes y durante la exposición: conexiones, bytes enviados,
lecturas/escrituras, errores de permisos/cuota y Crashlytics; p95 de publicación,
tiempo de recuperación y revanchas fallidas. Preparar alertas de cuota y gasto
antes de abrir el QR. Las alertas de presupuesto por sí solas no frenan gasto.
Definir también límites de instancias del futuro backend y una estrategia de
admisión/pausa que pueda aplicarse de forma fiable.

El costo no se puede extrapolar de las pruebas actuales a asistentes desconocidos.
Calcular primero bytes y operaciones por participación y por hora en lobby.
Para N participaciones: descargas estimadas = N × bytes medidos por participación
+ actividad de lobby/reconexión. Calcular Firestore por separado. Como escenarios
de sensibilidad, 1.000 participaciones a 1 MB o 3 MB implican 1 GB o 3 GB; estos
valores NO son mediciones del juego. Blaze cobra por uso y conserva franquicias;
la tabla vigente indica RTDB a USD 1/GB por encima de 360 MB/día. Incluir también
Firestore, funciones, compilación y almacenamiento cuando se habiliten.

## Arquitectura futura y calendario propuesto

Ampliar el plan elimina el techo de Spark, pero no cambia el código del juego.
El anfitrión todavía calcula fases y resultados: si se suspende hay transferencia
de coordinación y recuperación de estado. La incidencia observada fue de ese
flujo, sin evidencia de saturación del proveedor.

Migrar la autoridad al servidor elimina esa dependencia para resolver roles,
votos, acciones nocturnas, tiempos y victorias, y mejora protección contra un APK
de anfitrión modificado. Aun requiere clientes que recuperen estado al reconectar.
Android e iOS deberían compartir contrato y backend. Pasos descritos en
`arquitectura-autoridad-online.md`: inicio, fases/acciones/votos, resultados,
limpieza y cierre de escrituras antiguas tras adopción. Functions requiere Blaze
para desplegar: https://firebase.google.com/docs/functions/get-started

Orden propuesto hasta el 27/10, si se confirma la fecha:
- Ahora: validar 0.1.40 con cambio de coordinador y revancha; resolver cualquier
  fallo antes de ampliar exposición pública.
- Primera mitad de octubre: medir varias salas y decidir/habilitar facturación
  antes del QR público. Presupuestar con mediciones; aplicar controles y comprobar
  distribución desde Play. La migración de autoridad puede trabajarse por etapas.
- Antes del 20/10: ensayo completo con redes móviles y versión de Play, con carga
  representativa. Fijar concurrencia validada y comportamiento al alcanzar límites.
- Semana previa: elegir la versión probada, evitar cambios grandes sin repetir
  ensayo y preparar explicación/reportes para asistentes y monitoreo del evento.

No se activó facturación ni se desplegaron funciones en este análisis. La fecha
del evento no garantiza que una migración completa esté lista; debe cumplir sus
propias pruebas antes de sustituir la arquitectura actual.

## Ampliación: optimización, costos y monetización

El límite de 50.000 lecturas por día es una franquicia de Firestore, no una
capacidad de jugadores ni un límite de Blaze. En Spark puede impedir operaciones;
en Blaze se cobra el exceso. Las 8.348 lecturas medidas fueron de todo el proyecto
durante 24 horas móviles, con pruebas, lobbies y reconexiones, no una partida aislada.

### Dónde optimizar sin recortar funciones

Inspección del código actual:
- `GameplayChatController.kt`: texto y emotes usan RTDB; chat acotado a 60 mensajes
  y reacciones a 30 eventos. Las imágenes de emotes vienen de recursos de la app.
- `GameplayMockActivity.publishTraitorPlanNotices`: escribe avisos de objetivos en
  `salas/{roomId}/chat_traidores` (RTDB). La acción nocturna necesaria sigue existiendo
  aunque se quite su aviso; eliminar el aviso no elimina esa operación Firestore.
- `LobbyChatController`: historial nuevo limitado a dos espacios por jugador.
- `LobbyActivity`: todos observan la colección de jugadores; el anfitrión renueva
  su documento de presencia cada 30 segundos para permitir relevo seguro.
- Gameplay consulta acciones; el invitado filtra por sí mismo, el anfitrión por
  partida. Existen contadores locales por flujo en `firestore_usage`.

El candidato concreto a revisar es la amplificación de cambios de presencia:
120 renovaciones del anfitrión por hora, repartidas a 15 observadores, pueden
implicar unas 1.800 lecturas de documentos por hora solo por ese cambio, más
costos dependientes de reglas. Es un modelo ilustrativo de una sala quieta con
15 observadores activos, no una atribución de las 8.348 lecturas. Cambiar el
intervalo sin modificar las reglas de relevo puede romper la recuperación.

Prioridades propuestas: medir por flujo, separar metadatos estables de presencia
frecuente, evitar recargas completas y consultas de acciones históricas en cada
reconexión, acotar buscador y perfiles, reducir payloads/checkpoints redundantes,
y comprobar listener activo solo cuando corresponde. Las reglas con lecturas
dependientes deben optimizarse preservando permisos. Varias medidas ya existen;
no se asegura un porcentaje adicional hasta medir antes/después. Mantener emotes
y avisos útiles mientras no se demuestre que son el costo dominante.

### Costos de referencia

Tabla oficial consultada el 29/09/2026, seleccionando Santiago
(`southamerica-west1`), Standard y tarifa sin compromiso:
- Lecturas excedentes: USD 0,043 por 100.000 documentos.
- Escrituras excedentes: USD 0,129 por 100.000 documentos.
- Eliminaciones excedentes: USD 0,014 por 100.000 documentos.

Se verificó la tabla regional incluida en el HTML oficial; la vista inicial del
sitio muestra Iowa y otros precios. Fuente: https://cloud.google.com/firestore/pricing

Con una base elegible para la franquicia, operaciones de documentos únicamente:

| Lecturas cada día | Costo diario de lecturas | 30 días iguales |
|---|---:|---:|
| 8.000 | USD 0 | USD 0 |
| 50.000 | USD 0 | USD 0 |
| 100.000 | USD 0,0215 | USD 0,645 |
| 1.000.000 | USD 0,4085 | USD 12,255 |
| 10.000.000 | USD 4,2785 | USD 128,355 |

Fórmula: max(lecturas diarias - 50.000, 0) / 100.000 × 0,043.
No aplicar la franquicia diaria una única vez a un total mensual.
No incluye índices cuando corresponda, escrituras, tráfico, almacenamiento,
Functions, impuestos, cambio de moneda ni cargos bancarios.

El tráfico también importa: la tabla RTDB vigente ofrece 360 MB/día sin costo
en Blaze y USD 1/GB excedente. Un escenario hipotético de 1 GB/día supone unos
USD 19,20 en 30 días por descargas RTDB, con esa franquicia y unidades publicadas.
No es consumo medido por jugador ni factura proyectada del juego. Fuente:
https://firebase.google.com/pricing

Hasta medir participaciones, usar escenarios y reservar margen. No se puede
afirmar cuántos días cubren ARS 50.000 sin tipo de cambio/cargos del emisor y
consumo representativo. El saldo de una tarjeta no limita la obligación de pagar
servicios ya usados: un cobro rechazado puede dejar deuda y suspender servicio.

### Pago del servidor y cobro de ingresos

Google Cloud admite tarjetas de crédito y débito elegibles, pero no prepagas;
algunas virtuales pueden rechazarse y se requieren cobros internacionales y
automáticos. No se certificó un producto específico de Lemon, Ualá, AstroPay o
Mercado Pago: la marca no determina si la tarjeta es débito bancaria o prepaga.
Comprobar el producto exacto y su aceptación sin usarla como control de gasto.

Fuentes:
- https://docs.cloud.google.com/billing/docs/how-to/payment-methods
- https://docs.cloud.google.com/support/docs/troubleshoot-signup

Para recibir dinero se configura la cuenta de pagos de AdMob y el perfil de
comerciante de Play, con métodos de cobro elegibles. No se cobra proporcionando
el número de tarjeta usada para Firebase. Play documenta transferencias en USD
para Argentina; si se usa transferencia internacional, comprobar que la cuenta
pueda recibirlas. Un CVU para transferencias locales no demuestra esa capacidad.
Las opciones efectivas son las que ofrezca cada perfil de pagos.

AdMob documenta umbral de USD 100 en cuentas USD y liquidación mensual. Los
ingresos estimados no están disponibles inmediatamente para pagar Cloud; mantener
reserva propia hasta cobrar. Fuentes:
- https://support.google.com/admob/answer/6168758
- https://support.google.com/admob/answer/2772208?hl=es

### Escenario de streamer: sesenta salas de doce por día

Modelo solicitado por el usuario, no medición de facturación por sala completa.
Las mediciones disponibles de A56/5555/5635 son parciales y no permiten fijar una
media real para todos los roles. Para presupuestar se toma un supuesto explícito
de 5.000 lecturas totales por partida de doce, con sensibilidad de 2.000–10.000.
No se sumaron contadores de reglas como si fueran facturación verificada ni se
extrapoló un invitado a todos los roles como dato comprobado.

| Partidas/día, doce participantes | Participaciones/día | Lecturas/día con supuesto 5.000 | Cargo lecturas/día | Cargo lecturas en 30 días iguales |
|---|---:|---:|---:|---:|
| 5 | 60 | 25.000 | USD 0 | USD 0 |
| 60 | 720 | 300.000 | USD 0,1075 | USD 3,225 |
| 200 | 2.400 | 1.000.000 | USD 0,4085 | USD 12,255 |

Un millón de lecturas EN UN DÍA cuesta USD 0,4085 por lecturas; no USD 12 ese día.
Un millón CADA DÍA durante treinta días cuesta USD 12,255 por lecturas.
Sesenta partidas con supuesto 2.000 lecturas generan 120.000/día y USD 0,903
mensuales; con 10.000 generan 600.000/día y USD 7,095 mensuales. Un millón equivale
a 500/200/100 partidas de doce para supuestos 2.000/5.000/10.000 respectivamente.
Las participaciones incluyen jugadores que pueden repetir; no son usuarios únicos.
Revanchas adicionales por lobby deben contarse como partidas adicionales.

RTDB: no confundir los bytes de aplicación de los reportes con descarga facturada.
En un ejemplo independiente de 10 MB facturados RTDB por sala de doce, sesenta
partidas producen 600 MB/día: exceso sobre 360 MB de 240 MB, aproximadamente USD
0,24/día y USD 7,20 en treinta días. Con 20 MB por sala producen 1,2 GB/día:
USD 0,84/día y USD 25,20 mensuales. Son hipótesis, no payload medido del juego.
Sumadas a las lecturas del escenario central serían USD 10,425–28,425/mes para
estos dos conceptos, antes de escrituras, almacenamiento, otros servicios,
impuestos y conversión/cargos del emisor. No presentar esa banda como cotización
de la factura completa ni como límite máximo de gasto.

Sesenta salas repartidas durante 24 h no significan 720 conexiones concurrentes.
Sesenta salas ACTIVAS a la vez con doce jugadores sí serían 720 antes de clientes
en menús y otras pruebas: superan Spark (100). Blaze aumenta el límite del
proveedor; no se comprobó que el juego soporte 720 participantes a la vez.
La tarifa de uso no cambia por llamarlo «saturado». Superar 50.000 lecturas en
Blaze implica facturación del exceso, no saturación física por ese umbral.

Monetización ilustrativa para sesenta partidas/día: hasta 720 oportunidades de
vídeo si participan doce cuentas sin pack y todas reciben un anuncio. Si se
sirven 720 impresiones/día, eCPM hipotético USD 1 produce USD 21,60/mes; USD 2,
USD 43,20/mes, con treinta días iguales. No son tarifas reales de Argentina,
pronóstico de llenado ni ingreso garantizado; banners se calculan por separado.

Pack USD 5: bajo supuesto de comisión Play 15 % cuando se cumplan condiciones
del programa/mercado, USD 4,25 antes de impuestos y reembolsos. Siete ventas:
USD 29,75. Puede cubrir el modelo de lecturas+RTDB de un mes, pero una compra
permanente no financia automáticamente el consumo futuro del comprador sin
anuncios. Reservar parte de cada venta y medir costos/ingresos reales.

Fuentes oficiales reconsultadas el 30/09:
- https://cloud.google.com/firestore/pricing — región Santiago, sin CUD.
- https://firebase.google.com/pricing — RTDB y conexiones.
- https://support.google.com/admob/answer/15337570?hl=en — cálculo/variación eCPM.
- https://support.google.com/googleplay/android-developer/answer/112622?hl=es
  — comisión según programa y mercado.
- https://support.google.com/admob/answer/3372975
- https://support.google.com/googleplay/android-developer/answer/2700656

Alertas de presupuesto no son cortes automáticos. Los spend caps actuales están
en Preview para servicios seleccionados como Functions; no incluyen Firestore
ni RTDB y pueden sobrepasarse por demora. Fuente:
https://firebase.google.com/docs/projects/billing/spend-caps

### Plan de monetización propuesto

1. Medir partida y lobby, validar reconexión, establecer gasto previsto y reserva.
2. Resolver pago de Cloud y cuentas de cobro; configurar alertas y controles
   antes de habilitar un uso abierto. No depender de cobrar publicidad el mismo día.
3. Un único pack de apoyo de compra permanente: tres banners de personajes,
   estilo/marco/insignia y emotes seleccionados; sin ventajas de gameplay y sin
   publicidad. No usar el nombre rechazado por el usuario. Durante la beta avisar
   que algunos cosméticos están disponibles temporalmente y que sus condiciones
   cambiarán al introducir monetización; no simular una compra permanente gratis.
4. Publicidad para quienes no tienen pack: banner inferior del inicio y anuncio
   al terminar una partida, con frecuencia acotada. Evitar interrumpir un lobby
   listo para arrancar o la reconexión. No anuncios por probar cosméticos.
5. Compras mediante Play Billing, validación en servidor, restauración y manejo
   de reembolsos. Cosméticos y quitar anuncios son bienes/funciones digitales
   aunque el producto se llame pack de apoyo.
6. Medir ingresos netos cobrados y costo por usuario; ajustar precio/frecuencia
   solo después de tener datos. Los compradores sin anuncios siguen consumiendo
   servidor, por lo que parte de la venta debe reservarse para uso futuro.

Modelo mensual: publicidad neta + ventas netas - servidor - costos de cobro y
tributarios. Separar ingresos devengados de dinero efectivamente disponible.
Para mercados como Argentina, la tarifa de Play publicada es 15 % del primer
millón USD anual si se participa en el programa correspondiente; no asumir
inscripción automática ni usar esa tasa para todos los mercados.

Fuentes:
- https://support.google.com/googleplay/android-developer/answer/10281818
- https://support.google.com/googleplay/android-developer/answer/112622?hl=es

Esta ampliación define un plan y escenarios. No elimina funciones del juego,
integra anuncios/compras, registra tarjetas ni cambia la facturación del proyecto.

### Aclaración de cuota e ingresos, 30/09

Las 10.735 lecturas consultadas son de 24 horas móviles, no del día exacto de
cuota de Google. La cuota gratuita de 50.000 lecturas se renueva cada día en
horario del Pacífico, también en Blaze. Repetir 10.735 lecturas cada día durante
treinta días (322.050 en el mes) mantiene el cargo de lecturas en cero si cada
día de cuota se mantiene debajo de 50.000. No sumar todos los días y aplicar
una única franquicia mensual de 50.000.

Tarifas de Santiago revalidadas en el contenido regional del HTML oficial de
https://cloud.google.com/firestore/pricing el 30/09: USD 0,043 por cien mil
lecturas excedentes y USD 0,129 por cien mil escrituras excedentes, sin CUD.
Los escenarios previos de 100.000 y un millón de lecturas diarias siguen siendo
USD 0,0215/0,4085 por día y USD 0,645/12,255 en treinta días. Son sólo lecturas;
incluir transferencias, escrituras, almacenamiento, otros servicios y cargos del
medio de pago cuando corresponda. El tráfico de hoy de RTDB (~24,4 MB en 24 h)
está lejos de la franquicia publicada de Blaze de 360 MB/día, pero las ventanas
de cuota y métricas no coinciden exactamente y tienen retraso.

Publicidad: el ingreso se calcula por impresiones servidas, no por prometer un
vídeo de treinta segundos. Fórmula ilustrativa: impresiones / 1000 × eCPM. Un
eCPM hipotético de USD 2 con mil impresiones de vídeo produciría USD 2; otro
hipotético de USD 0,20 con mil impresiones de banner produciría USD 0,20.
No son tarifas de AdMob para Argentina, un pronóstico del juego ni garantía de
llenado. Medir formato, país, impresiones y eCPM reales antes de presupuestar.
Una partida de quince puede ofrecer hasta quince vídeos, si se sirven a todos
los participantes sin pack de apoyo; no equivale a un vídeo por sala.

Propuesta: intersticial después del resultado, una vez asegurado el regreso
online; banner inferior del inicio/lobby, preservando espacio de gameplay y
controles. Sin publicidad durante recuperación, sin bloquear al resto de la
sala si un anuncio tarda o falla, y sin fijar un cierre artificial a treinta
segundos. Duración/cierre dependen del anuncio servido y del SDK. El pack de
apoyo elimina ambos. Este es un diseño propuesto, no integración autorizada de
anuncios ni habilitación de facturación. Los cobros tienen calendario/umbral;
AdMob en USD tiene umbral de pago de USD 100, así que ingresos estimados del día
no son saldo disponible para pagar Cloud ese mismo día.

Fuentes oficiales:
- https://firebase.google.com/docs/firestore/pricing
- https://firebase.google.com/pricing
- https://support.google.com/admob/answer/15337570?hl=en
- https://support.google.com/admob/answer/6201350?hl=es
- https://support.google.com/admob/answer/6066980?hl=es
- https://support.google.com/admob/answer/2772208?hl=es
