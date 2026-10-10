# Encargo para Claude: beta con anfitrión y V3 local

## Decisión del usuario — 9/10/2026

La beta Android usará el online habitual con autoridad del anfitrión. V3 seguirá en desarrollo y pruebas únicamente con emuladores locales hasta que funcione correctamente y conserve la presentación habitual. Esta decisión sustituye los planes anteriores que ponían V3 como requisito para la beta. No queremos rehacer la mesa ni perder fotos, perfiles, historial o mejoras útiles ya realizadas.

Tu tarea ahora es revisar el árbol actual y proponer un plan pequeño y concreto. Por ahora editá solo el documento de entrega: no implementes cambios de código, no despliegues, no abras gates, no hagas commit/push y no publiques en Play. `sources/` sigue siendo solo lectura. Hay cambios compartidos de Codex y Claude sin commit: preservalos.

## Contexto que debés leer

- `docs/PAUSA_V3_Y_ONLINE_ANFITRION_2026-10-09.md`: cierre de la práctica y errores observados. Su restricción anterior a continuar V3 queda reemplazada por la autorización actual de trabajarlo solo localmente.
- `docs/PLAN_BETA_ABIERTA_ANDROID.md`: prioridades generales; sus pasos de activación V3 quedaron pospuestos por esta nueva decisión.
- `docs/historial-cuenta-firebase.md` y la implementación actual de perfil/fotos/historial.
- Los briefs de mesa común y los adaptadores V3, solo para detectar cambios compartidos que puedan afectar al online anterior.

## Qué necesitamos de tu revisión

1. **Beta con anfitrión.** Confirmá cómo seleccionar el camino anterior en una build normal y qué necesita revisarse para conservar reparto de roles, mesa, poderes, anuncios decorados, chat, votación, resultado y revancha. Diferenciá lo comprobado en código de lo probado. Indicá si los cambios comunes recientes introdujeron regresiones. No borres V3 ni conviertas salas V3 existentes al protocolo anterior.

2. **Aviso del anfitrión.** Proponé una ubicación discreta al crear la sala o en el lobby. Texto inicial: «Para una partida estable, recomendamos que el anfitrión tenga buena conexión y mantenga el juego abierto hasta el final». Revisá qué ocurre hoy si se desconecta o sale, incluyendo la recuperación/traspaso existente, para que el texto refleje el comportamiento real. Priorizá mejoras pequeñas sobre ese mecanismo; explicá las limitaciones que sigan existiendo.

3. **Storage, perfiles e historial reales.** Auditá primero lo ya implementado y desplegado; no lo rehagas. Precisá qué falta para que una foto publicada se vea en perfil, menú, tarjeta online, lobby, cartas/votaciones y ganadores debajo del rol. Revisá reglas, acceso por cuenta, compresión, caché, reemplazo/borrado y fallos de subida. Confirmá que el historial por cuenta funciona también con resultados del motor con anfitrión; no debe depender de activar V3. No agregues Vision SafeSearch ni servicios nuevos pagos: el usuario pospuso ese filtro. Señalá el estado del reporte y retiro de fotos existentes sin ampliar ahora el alcance.

4. **Consumo del online anterior.** Proponé cómo medir una partida y el consumo en reposo: Firestore, RTDB, fotos, historial y Functions. Separá preparación de QA del uso real. No prometas una cifra sin medición. Señalá cómo reducir las instancias mínimas exclusivas de V3 mientras no se use Cloud, conservando servicios necesarios para el online anterior; por ahora solo proponelo, no cambies la configuración.

5. **V3 únicamente local.** Proponé recorridos dirigidos y comparación con la mesa habitual para reproducir los fallos de la APK 3: inicio lento, rol no presentado, Médico sin poder desmarcar objetivo, anuncio de muerte sin decoración e identificación del voto ausente. Usá Firebase Emulator Suite y rutas de prueba existentes, sin credenciales ni acceso a producción y sin instalar APKs en el teléfono sin coordinarlo. El emulador sirve para corregir funcionamiento y presentación; no certifica latencia, facturación o capacidad de Cloud. V3 no vuelve a ser requisito de la beta.

## Entrega

Creá `docs/PLAN_BETA_ANFITRION_Y_V3_LOCAL_2026-10-09.md` con:

- Estado actual: qué existe, qué falta y qué no está verificado.
- Máximo cinco tareas pequeñas, ordenadas por prioridad, con archivos afectados y criterio de cierre visible para el usuario.
- Qué proponés hacer vos y qué Codex, sin editar los mismos archivos a la vez. Esperá que el usuario coordine la división antes de implementarla.
- Ideas adicionales separadas de los requisitos de esta beta; no agregues funciones sociales ni otra migración.

El objetivo es recuperar una beta Android familiar y funcional con fotos e historial reales, medir su consumo y avanzar después con publicidad/cosméticos. V3 queda como trabajo local independiente, sin presión por publicarlo ahora.
