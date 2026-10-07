# Análisis de la IA de Android — 6/10/2026

Punto de partida para rediseñar los bots antes de llevarlos a iOS. Se leyó el código (sin
modificarlo) y se corrió la simulación masiva existente.

## Tamaño y piezas

Unas 7.550 líneas en 19 archivos `Bot*.kt`, más `LocalBotAi.kt` y `TraitorPlanBrain.kt`.

| Pieza | Archivos | Qué hace |
|---|---|---|
| Decisiones | `LocalBotAi`, `BotTargetingStrategy`, `BotVoteStrategy`, `TraitorPlanBrain` | A quién matar, salvar, investigar y votar |
| Percepción | `BotPerception`, `BotCognitionModels` | Lee el chat con listas de palabras y patrones |
| Memoria | `BotTableMemory`, `BotConversationMemory` | Sospecha acumulada, reclamos de rol, contradicciones |
| Habla | `BotDialogueLines`, `BotRoleDialogue`, `BotConversationDialogue`, `BotSpeechStyle`, `BotQuickReplies`, `BotDirectReplies`, `BotHumanMessageEngine` | Unas 600 frases armadas con plantillas |
| Ritmo | `BotConversationDirector`, `BotMessageBursts` | Cuándo y cuántos bots hablan |
| Personalidad | `BotIdentity`, `BotRelationshipStrategy` | 6 personalidades (Tranqui, Picante, Jodón, Desconfiado, Impulsivo, Analítico) |

## Resultado de la simulación (`BotMassSimulationTest`, 500 partidas, 5–10 jugadores, 3 mapas)

- Traidores ganan **69 %**, Pueblo **31 %**.
- Las partidas duran **2,7 rondas** de promedio.
- 24 victorias especiales del Bufón; sin bloqueos.

## Falencias encontradas

### Deducción (lo que más pesa en el equilibrio)
1. **La sospecha sale casi solo del chat.** `scoreCandidate` suma por palabras («sospe», «raro»,
   «voto»…), menciones y reclamos de rol. **No usa el historial de votos** (quién votó a un
   inocente expulsado, quién nunca vota a cierto jugador) ni las muertes (a quién le conviene
   que muera X). Los votos guardados solo se usan para contestar «¿por qué votaste?».
2. **Asesino y Médico eligen con la misma fórmula** (`nightPressureScore`: el que más habla).
   El médico tiende a cubrir justo al que el asesino iba a matar, de casualidad, y el asesino
   nunca apunta a un Comisario o Médico que se reveló.
3. **El Comisario puede investigar dos veces al mismo.** Elige al primer sospechoso público;
   si ya lo encontró culpable, sigue arriba de la lista.
4. **El Comisario revela su información por azar** (`seed % 2`, o `% 5` en Difícil), no por
   estrategia (por ejemplo, revelar cuando encuentra un traidor). En Difícil el pueblo queda
   más débil porque su mejor información se calla.
5. **Ruido determinista por nombres.** `stableNoise(código, ronda, nombres)` decide desempates y
   «tiradas»: con la misma sala y nombres, los bots repiten patrones.

### Reglas pensadas alrededor del humano
6. Ajustes artificiales sobre el único humano: alivio de voto en Normal (`HUMAN_NORMAL_VOTE_RELIEF`),
   presión creciente en Difícil y una tirada para matarlo de noche (`humanNightTargetBonus`).
   Funciona, pero se siente como «la IA me tiene bronca» o «la IA me deja pasar», no como deducción.

### Conversación
7. **Frases de plantilla con huecos** (nombre + motivo genérico como «su postura todavía no cierra»).
   Con ~600 frases y muchas partidas, se repiten y suenan iguales entre bots.
8. **Entiende poco lo que escribe el jugador:** busca palabras clave; una frase irónica, negada
   («no sospecho de Lu») o con otro vocabulario se interpreta mal.
9. Lógica muy repartida (decisión, memoria y habla mezcladas en 20 archivos), difícil de
   balancear y de portar idéntica.

## Para iOS y el online

- En online no hay bots (salvo jugadores simulados), así que la IA nueva es para jugar contra la IA.
- Conviene que la decisión (a quién matar, votar…) sea pura y probada con la misma batería en
  Android e iOS; el habla puede venir después y apoyarse en esas decisiones.

## Decisiones del usuario (6/10/2026)

| Tema | Decisión |
|---|---|
| Prioridad | Que charle con dinamismo y sea inmersiva; también que deduzca mejor, esté equilibrada y tenga personalidad |
| Trato al humano | Igual que a un bot: sin reglas especiales. Si Normal y Difícil no se distinguen bien, se saca Difícil |
| Normal vs Difícil | Qué tan bien deducen |
| Volumen de charla | Menos y mejor: pocos mensajes, cada uno con un motivo concreto |
| Motor de charla | Reglas propias, sin internet ni costo |
| Plataformas | Diseño común y la misma batería de pruebas y simulación en Android e iOS |
| Mentiras de traidores | Mucho: fingen roles, inventan investigaciones, se cubren |
| Respuestas | Si nombrás a un bot, ese bot contesta siempre, con pausa natural |
| Equilibrio | 50/50 en la simulación |
| Duración | 4 a 6 rondas |
| Entre bots | Sí, con peleas y alianzas |
| Texto libre | Entender bien lo esencial: acusaciones, defensas, reclamos de rol, preguntas y negaciones |

## Próximo paso propuesto

1. Documento de diseño común (`ios/docs/IA_DISENO.md`): modelo de creencias por bot a partir de
   hechos observables (votos, muertes, reclamos, investigaciones declaradas, frases con negación),
   decisiones de noche y de voto, estrategia de mentira de los traidores, intención de cada mensaje
   y cómo la personalidad lo convierte en frase.
2. Batería común: escenarios en JSON (situación de mesa → decisión esperada) que corren Kotlin y
   Swift, más la simulación masiva con metas de 50/50 y 4–6 rondas.
3. Implementación en `TraidoresCore` (iOS) y en Android por Codex, contra la misma batería.
