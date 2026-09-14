# Auditoría del modo IA — 5 de septiembre de 2026

## Resultado y alcance

La IA local tiene una base aprovechable: percepción del chat, memoria social y privada, personalidades, coordinación de traidores, estrategias de voto y un director de conversación. Los componentes revisados deciden mediante reglas y puntuaciones y generan diálogo con plantillas; no es necesario incorporar un modelo remoto para resolver los problemas siguientes.

Esta entrega analiza el código y ejecuta pruebas existentes. No modifica el comportamiento de los bots, no publica una versión y no mide todavía la experiencia de una persona jugando. Los hallazgos estáticos siguientes requieren escenarios de regresión al implementarse; no se presentan como reproducciones completas en la interfaz.

## Hallazgos priorizados

### 1. Declaraciones de rol sin distinguir negación o discurso referido — prioridad alta

`BotPerception.kt`, `roleClaimFrom`: los patrones buscan fragmentos como `soy policía` dentro del mensaje sin comprobar la negación anterior ni quién está hablando en una cita. Por su construcción, `no soy policía` contiene una coincidencia positiva; también `Mora dice que soy policía`. Esto puede alimentar declaraciones y contradicciones falsas en el razonamiento posterior.

Mejora: reconocer negaciones, preguntas, citas e hipótesis antes de registrar una declaración propia. Conservar el hablante, el fragmento de evidencia y una confianza. Ante ambigüedad, preguntar o abstenerse. Esfuerzo pequeño para las negaciones directas; medio para cubrir lenguaje variado sin romper frases ya admitidas.

### 2. Repetir acusaciones aumenta artificialmente su peso — prioridad alta

`BotTargetingStrategy.kt`, `scoreCandidate`: cada mensaje acusatorio suma 5 por palabras señal y puede sumar otros 4 en Normal o 5 en Difícil por su interpretación estructurada. Esos dos componentes cuentan mensajes, sin limitar aportes por hablante. `recentPublicMessages` toma los últimos 16 mensajes: el efecto está acotado por esa ventana, pero repetir una acusación puede dominarla. Una lectura privada de inocencia resta solamente 12 o 16 en este mismo cálculo.

Esto demuestra acumulación en la puntuación, no que todos los caminos de voto necesariamente terminen votando al inocente: existen planes y condiciones adicionales.

Mejora: unificar la evidencia de cada mensaje; limitar repeticiones del mismo emisor; distinguir fuentes independientes; ponderar credibilidad y antigüedad. Comprobar que una repetición no equivale a una nueva prueba y que una lectura propia conserva suficiente peso. Esfuerzo medio.

### 3. Investigar usa sospecha, sin medir información nueva — prioridad alta

`LocalBotAi.kt`, `chooseInvestigationTarget`: el policía elige el primer sospechoso del ranking general. No excluye objetivos ya investigados ni penaliza repetir información. Además, una lectura privada sospechosa aumenta ese ranking, favoreciendo volver sobre el mismo objetivo si sigue vivo.

Mejora: una estrategia específica de investigación que priorice incertidumbre e información nueva. Permitir repetir cuando las mecánicas de cambio de bando u otra evidencia lo justifiquen. Esfuerzo pequeño a medio.

### 4. El oráculo consulta un registro con acciones privadas — prioridad alta

`LocalBotAi.kt`, `chooseOracleTarget`: al ordenar candidatos suma `actionHistory.count { it.actor == player.name }`. `GameEngine.kt` registra allí investigaciones, protecciones y silenciamientos, además de votos. Aunque solo se use la cantidad y no el contenido, la elección recibe información de actividad que no necesariamente fue pública.

Mejora: usar únicamente hechos visibles para ese bot y sus conocimientos autorizados. Añadir una prueba de invariancia: cambiar acciones ocultas ajenas, conservando lo observable, no debe cambiar su decisión. El arreglo puntual es pequeño; introducir una vista de conocimiento por bot en todo el sistema es un trabajo medio a grande.

### 5. La prueba masiva no permite concluir balance por mapa y dificultad — prioridad alta para validación

`BotMassSimulationTest.kt`, `simulate500MatchesAcrossAllMapsAndPlayerCounts`: el mismo índice selecciona mapa con módulo 3, cantidad con módulo 6 y dificultad con paridad. Solo se recorren 6 combinaciones de las 36 posibles para 3 mapas × 6 cantidades × 2 dificultades. La cantidad queda ligada al mapa y a la dificultad.

Además, el bucle avanza fases sin simular una conversación humana; todos los participantes son bots. Es útil para detectar bloqueos del motor, pero no valida comprensión del chat ni compara limpiamente Normal con Difícil.

Mejora: matriz completa de combinaciones, escenarios reproducibles, conversaciones guionadas y métricas separadas: bloqueos, victorias por equipo, investigaciones repetidas, coherencia entre explicación y voto, reacción a spam y uso de información oculta. Esfuerzo medio. No se recalculó el balance en esta entrega.

### 6. Difícil incluye presión explícita sobre el humano — decisión de diseño a revisar

`BotTargetingStrategy.kt`: el bono diurno hacia humanos sin lectura pública útil crece de 4 a 14 en Difícil. La probabilidad del bono nocturno llega a 45 %, frente a 20 % en Normal. No es un error accidental: coincide con la especificación previa de dificultad. Esas probabilidades activan un bono de selección; no son probabilidades finales de morir.

Mejora propuesta: conservar el desafío, pero trasladar más diferencia a planificación, calidad de evidencia y manejo de incertidumbre. Comparar partidas equivalentes antes de retocar los valores. Esfuerzo medio; requiere evaluación de balance.

### 7. Conversación con presupuestos conservadores — oportunidad de experiencia

`BotConversationDirector.kt`: las intervenciones autónomas del debate están limitadas a 2 en rondas tempranas y 3 después; hay pausas para dar lugar al humano. Esta intención es razonable. No prueba por sí sola que la mesa quede muda: el comportamiento final depende de las reacciones y de cómo la interfaz administra los contadores.

Mejora: probar presupuestos adaptados a preguntas pendientes, conflictos y tiempo restante, manteniendo espacio para responder. Priorizar contenido nuevo y coherencia entre lo dicho y lo votado sobre aumentar cantidad de frases. Esfuerzo medio, con prueba manual necesaria.

## Verificación realizada

Ejecución de `testDebugUnitTest` limitada a cinco clases:

| Clase | Pruebas | Fallos/errores |
|---|---:|---:|
| BotPerceptionTest | 6 | 0 |
| BotPlayerCenteredBrainTest | 11 | 0 |
| BotConversationDirectorTest | 37 | 0 |
| BotTableMemoryTest | 6 | 0 |
| TraitorPlanBrainTest | 5 | 0 |
| Total | 65 | 0 |

Gradle terminó correctamente. Los resultados XML están en `app/build/test-results/testDebugUnitTest`. No se ejecutaron tareas de release ni de despliegue en esta revisión. Las pruebas existentes no contienen todos los escenarios adversariales aquí propuestos.

## Orden recomendado de implementación

1. Negaciones de rol, investigación con información nueva y selección del oráculo con datos permitidos, cada uno con una regresión que reproduzca el defecto antes del arreglo.
2. Evidencia social sin doble conteo y escenarios de acusaciones repetidas, fuentes independientes y lecturas privadas.
3. Matriz de simulación completa y reproducible para evaluar esos cambios.
4. Ajustar dificultad y ritmo de conversación con métricas y partidas manuales.

Las primeras correcciones son acotadas y pueden entregarse por separado. Rehacer todo el cerebro o añadir conversación generativa sería un proyecto mayor y no es requisito para mejorar la IA actual.

## Por qué quedó pendiente la autoridad del servidor online

El anfitrión todavía participa como autoridad del estado y del resultado. Las reglas de acceso pueden validar quién escribe y algunas condiciones de transición, pero no sustituyen el cálculo completo de muertes, habilidades y victorias. Para resistir un anfitrión modificado, el servidor debe recibir intenciones y calcular resultados sin confiar en el estado propuesto por el cliente.

Es una migración de arquitectura que afecta al motor, backend, protocolo y compatibilidad con APK anteriores. Aplicarla parcialmente como si ya resolviera toda la integridad sería engañoso. Puede dividirse: primero cierre y recuento de votos en servidor, después acciones nocturnas y finalmente condiciones de victoria, con pruebas de concurrencia y reintentos. La primera etapa reduce riesgo, pero no elimina por sí sola todas las facultades del anfitrión. El informe online anterior documenta ese límite; esta revisión de IA no lo modifica.
