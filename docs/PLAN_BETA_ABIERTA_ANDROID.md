# Prioridad: beta abierta de Android

Decisión del usuario, 7 de octubre de 2026: Android pasa a ser la prioridad.
Orden inicial: online → consumo del servidor → publicidad → bots del modo vs IA.
El usuario añade dos bloques para la beta: cerrar pack de apoyo/compras cosméticas
y renovar tráiler/ficha de Play Store. Se mantienen online y consumo como prioridades
inmediatas; los nuevos bloques se detallan después de los cuatro originales.
El bloque iOS queda en el punto documentado en
`ios/docs/ENTREGA_CLIENTE_V3_2026-10-07.md`. No hace falta terminar ni publicar iOS
para validar la beta Android. Este plan no activa V3, anuncios ni nuevos despliegues.

El 8 de octubre se retoma la presentación habitual después de medir V3 primero.
Se completaron los 21 recorridos locales y tres partidas de 5/10/15 contra Cloud
con acceso restringido. El informe está en `docs/MEDICION_V3_BETA_ANDROID.md`.
La optimización de publicaciones y la adaptación de las ventanas del Desertor,
silencio y Oráculo se registran en `docs/AVANCE_ONLINE_ANDROID_2026-10-08.md`.
La mesa todavía requiere pruebas visuales en el A56; Release continúa cerrado a V3.
La continuación de ventanas, recuento y pruebas dirigidas se documenta en
`docs/VENTANAS_V3_ANDROID_2026-10-08.md`.

## Punto de partida comprobado en el repositorio

- V3 decide roles, fases y resultados en el servidor; el cliente envía intenciones.
- La entrega `docs/DESPLIEGUE_V3_CLOUD_2026-10-07.md` registra un ensayo reducido
  contra Firebase real y cierre del gate después de la prueba. Es evidencia de esa
  ejecución; volver a comprobar el estado actual antes de una prueba o apertura.
- Android tiene ensayo nativo con emuladores y cuatro participantes automáticos;
  no sustituye una partida con personas. Ver `docs/PRUEBA_V3_ANDROID_2026-10-07.md`.
- La mesa Android V3 está en adaptación en Debug: reutiliza el XML, arte y
  animadores del juego, pero su presentación todavía no está cerrada ni aprobada.
  La prueba del A56 mostró diferencias visuales que el usuario rechazó; no tratar
  esta versión como producto final. Ver `docs/ENTREGA_MESA_ANDROID_V3_2026-10-07.md`.
  `app/build.gradle` mantiene `SERVER_ONLINE_V3=false` en Release.
- Hay mediciones locales de partidas de 5/10/15 jugadores, con límites explícitos;
  no son la factura ni una prueba de capacidad en Cloud.
- En la revisión no apareció una integración del SDK de anuncios en Android.
  Los bots tienen una implementación local existente (`LocalBotAi` y módulos de
  percepción, memoria y conversación); se mejora esa base, sin rehacerla de cero.
- El pack de apoyo tiene marco/insignia y banners existentes, pero el equipamiento
  `support_preview` es una prueba local, no una compra ni un derecho adquirido en
  Firebase. No apareció integración de Play Billing en la revisión de Android.
- Hay cambios previos sin commit de Android/backend e iOS. Separar las entregas
  por sus ramas antes de publicar; este cierre no hace commit ni push.

## 1. Online: terminar Android y jugarlo

Primero ejecutar una partida Android V3 completa en la Mac con la herramienta QA
existente: un jugador humano en la AVD y cuatro clientes automáticos autenticados.
Registrar defectos observados y usar ese recorrido como referencia al integrar la
mesa habitual. La automatización prepara participantes, pero el juego lo arbitra V3.

Después integrar la experiencia habitual de Android sobre la proyección V3:
mesa, poderes, chat, voto, amanecer, desempate del Alcalde, Desertor y resultado.
Reconstruir desde el snapshot y presentar eventos una vez por matchId + seq;
el cliente nunca reparte roles ni resuelve fases por su cuenta.

Verificar, con acceso de prueba restringido:

- Crear/buscar/unirse; cuenta e invitado, perfiles y fotos publicados.
- Partida completa, acciones válidas/rechazadas y empates sin revelar roles secretos.
- Anfitrión desconectado o app cerrada: el servidor avanza; el jugador puede volver.
- Red interrumpida y toques repetidos: estado coherente, sin duplicar una acción.
- Salir antes del final, vivo o muerto: derrota registrada en historial y estadísticas.
- Resultado guardado una vez por cuenta, consultable después de reiniciar/recuperar.
- Revancha en la misma sala: matchId nuevo, sin rol, chat ni anuncios viejos.

Cerrar con una prueba pequeña contra Cloud y, cuando haya participantes disponibles,
una partida entre personas. La prueba mixta con iOS puede hacerse después.
Solo entonces preparar el comportamiento de Release y la apertura gradual de V3.

## 2. Consumo: medir durante el bloque 1 y ampliar el ensayo después

Tomar métricas desde la primera partida, para que la optimización acompañe al online.
Entregar un informe reproducible con:

- Lecturas/escrituras Firestore, almacenamiento y bytes RTDB.
- Invocaciones y duración de Functions, Tasks y Scheduler, incluido el costo en reposo.
- Lobby, chat, fotos/caché, historial y reconexiones además del motor.
- Latencia de acción hasta confirmación y publicación; p50/p95 y errores.
- Recorridos de 5/10/15 jugadores y salas simultáneas en escalones pequeños.

Separar preparación/limpieza de QA del uso del juego. Separar también lecturas
instrumentadas, JSON del SDK, tráfico real y costos publicados por Cloud: no son
intercambiables. Revisar región/tarifa vigente al convertir uso a dinero.

Corregir consultas repetidas, listeners que quedan abiertos, descargas de fotos
innecesarias y contención observada. Definir límites concretos de instancias,
peticiones y tamaño de datos según la evidencia. Mantener alertas de presupuesto:
son avisos, no un tope automático de gasto.

Salida: consumo por partida y por día en reposo; escenarios de actividad con sus
supuestos; criterio para ampliar o frenar la beta. No prometer una factura mensual
a partir de los ensayos locales.

## 3. Publicidad: primera integración y validación de ingresos

Propuesta para decidir al llegar a este bloque: empezar con anuncios recompensados
voluntarios, fuera de las fases activas, con una recompensa cosmética a definir.
No introducir una moneda nueva sin acordar para qué sirve. Si se estudian anuncios
entre partidas, fijar frecuencia y comprobar que no interfieran con la revancha.

Integrar carga, errores, cierre y concesión de recompensa una sola vez; el juego
debe poder seguir si no hay anuncio disponible. Durante desarrollo usar anuncios
de prueba y preparar el flujo de consentimiento aplicable. Guías oficiales:
[recompensados](https://developers.google.com/admob/android/rewarded),
[pruebas](https://developers.google.com/admob/android/test-ads) y
[consentimiento UMP](https://developers.google.com/admob/android/privacy).

Medir ingresos efectivos junto con costo de servidor, duración de sesiones y
abandono de jugadores. Añadir anuncios no garantiza rentabilidad: se comprueba
con esos datos después de publicarlos y tener usuarios.

## 4. Modo vs IA: mejorar decisiones y conversación

Revisar partidas del modo existente y priorizar comportamientos concretos:
sospechas y memoria consistentes, elección de objetivos, poderes de cada rol,
votos/empates, conversación menos repetitiva y diferencias de dificultad.

Mantener la IA en el dispositivo. Evaluar decisiones con la información que cada
bot puede conocer; evitar que una dificultad mayor equivalga a conocer secretos.
Usar partidas completas reproducibles y casos de roles para verificar que las
mejoras no introducen bloqueos ni alteran las reglas compartidas.

## 5. Pack de apoyo, emotes y estilos: compras reales

Terminar el catálogo y su presentación, reutilizando los recursos existentes.
Propuesta a acordar: pack con estilo de perfil, marco, insignia, banners y un
conjunto definido de emotes. Definir qué queda gratis, qué incluye el pack y qué
se vende separado; distinguir los cosméticos que ya usan los jugadores de los
nuevos exclusivos. Cantidad, arte final, precios y posible beneficio sobre anuncios
siguen pendientes de decisión, no se dan por aprobados en este plan.

Propuesta técnica inicial: compra permanente del pack y productos permanentes
para emotes/estilos, mediante Google Play Billing. Presentar una vista previa
antes de comprar y guardar en Firebase los derechos de la cuenta, separados del
cosmético equipado. Validar compra en el servidor, conceder una sola vez y confirmar
su procesamiento con Google; manejar pagos pendientes, cancelaciones, reembolsos
y restauración tras reinstalar o cambiar de dispositivo. Referencias oficiales:
[integración de Billing](https://developer.android.com/google/play/billing/integrate)
y [verificación de compras](https://developer.android.com/google/play/billing/security).

Verificar compras con cuentas de prueba de Play y recuperar el inventario desde
Firebase. Un selector local no debe acreditar una compra. Los clientes online
deben ver únicamente los cosméticos publicados y autorizados; el permiso para
usarlos no puede depender de una preferencia editable en el teléfono.

Salida: catálogo/precios acordados, recursos finales, compra y restauración
probadas, pack de prueba convertido en producto real y presentación consistente
en perfil, tarjeta online, lobby y partida según el alcance de cada cosmético.

## 6. Gameplay, tráiler y ficha de Play Store

Preparar ahora un listado breve de escenas; grabar la versión Android cuando
la mesa habitual V3 y los cosméticos a mostrar estén terminados. Mostrar juego
real: rol privado, noche/poder, debate, voto, tensión del empate, victoria/derrota
y perfil. Combinar escenas online y vs IA según lo que anuncie la ficha.

Grabar sin paneles de QA ni datos personales, con audio limpio y recursos cuyo
uso en promoción esté autorizado. Editar una pieza corta que explique el juego
y actualizar capturas, imagen destacada y textos con la misma versión visible.
No representar como disponible una función que todavía no se puede usar en beta.
Revisar formatos y requisitos vigentes antes de subirlos:
[recursos de vista previa de Play](https://support.google.com/googleplay/android-developer/answer/9866151?hl=es).

Salida: gameplay fuente, tráiler exportado y capturas/textos listos para revisión
antes de publicar. La promoción acompaña al producto probado.

## Cierre mínimo antes de publicar

Además de los cuatro bloques: verificar crashes/ANR, instalación y actualización
de Release, permisos, recuperación/borrado de cuenta, uso de fotos, privacidad y
declaraciones de datos/anuncios en Google Play. La declaración debe reflejar los
SDK y datos realmente usados; ver la
[guía oficial de Seguridad de los datos](https://support.google.com/googleplay/android-developer/answer/10787469?hl=es).

Revisión adicional del cierre, a pedido del usuario («¿me olvido de algo?»):

- Probar el AAB firmado e instalado desde Play, con App Check de Release, además
  del APK Debug. Incluir un Android físico, equipo de gama baja, red móvil/cortes
  y actualización desde la versión anterior. Consultar el
  [informe previo al lanzamiento](https://google.play/business/pre-launchreports/).
- Confirmar reportes/bloqueo de jugadores y contenido en la mesa V3, incluyendo
  fotos, nombres y chat, y un procedimiento para revisar reportes y retirar contenido.
  PlayerModeration ya escribe reportes en Firestore, pero su manejo de errores
  muestra «Gracias. Vamos a revisarlo.» también ante PERMISSION_DENIED: verificar
  que no dé una confirmación falsa cuando el servidor rechaza un reporte nuevo.
  Revisar la cobertura según la
  [política de contenido generado por usuarios](https://support.google.com/googleplay/android-developer/answer/9876937?hl=es).
- AccountDeletion y los enlaces de privacidad/borrado ya existen. Verificar su
  funcionamiento completo, incluida solicitud desde la web y limpieza efectiva
  de perfil/fotos/historial; actualizar las declaraciones al integrar anuncios/compras.
  Referencia: [eliminación de cuentas](https://support.google.com/googleplay/android-developer/answer/13327111?hl=es).
- Crashlytics ya está integrado. Confirmar que la build publicada envía errores
  útiles sin secretos; comprobar el canal COMENTARIOS / ERRORES y quién revisa
  los reportes durante la beta.
- Hacer una primera partida con personas que no conozcan el juego: comprobar que
  entienden su rol, cuándo actuar y cómo votar, sin explicación del desarrollador.

Primer siguiente paso: completar la medición Cloud y corregir las demoras observadas;
después volver a la presentación habitual y probarla en Android físico. No empezar
nuevas funciones sociales ni ampliar monetización durante este bloque. Las decisiones
pendientes de anuncios se presentan cuando toque ese bloque.
