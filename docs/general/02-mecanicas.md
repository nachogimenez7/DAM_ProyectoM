# Mecánicas principales

Documento de reglas de juego tal como están implementadas en el código (`GameModels.kt`, `GameEngine.kt`, `RoleCatalog.kt`).

## Bandos

| Bando | Constante | Condición de victoria |
|---|---|---|
| **Pueblo** | `GameRules.TOWN_WINNER = "Pueblo"` | Eliminar a todos los roles "asesinos" vivos (`asesino`, `espia`). |
| **Traidores** | `GameRules.TRAITOR_WINNER = "Traidores"` | Igualar o superar en número al Pueblo entre los vivos. |
| **Neutral** | `"Neutral"` | Depende del rol (Desertor, Bufón). |

Roles traidores: `asesino`, `mercenario`, `espia` (`GameRules.traitorRoleKeys`).
Roles que matan de noche: `asesino`, `espia` (`GameRules.killerRoleKeys`).

**Cálculo del ganador** (`GameRules.winnerFor`):
- Si no queda ningún "killer" vivo → gana **Pueblo**.
- Si los traidores vivos ≥ pueblo vivo (sumando al Desertor si eligió Pueblo) → ganan **Traidores**.
- En otro caso, la partida continúa.

## Mapas y ambientaciones

Tres mapas (`LocalGameFactory.maps`), cada uno con un rol exclusivo:

| Mapa | Clave | Ambientación | Rol exclusivo |
|---|---|---|---|
| Pampa | `pampa` | Pueblo del Interior – Argentina, 1915 | **Payador** |
| Grecia | `grecia` | Antigua Grecia, Siglo V a.C. | **Oráculo** |
| Medieval | `medieval` | Feudo de Hierro, Época Medieval | **Bufón** |

Los nombres de los roles se localizan por mapa (p. ej. `policia` se muestra como "Comisario" en Pampa, "Detective" en Grecia/Medieval).

## Roles (11)

Definidos en `RoleCatalog.kt`. La columna "mínimo" es la cantidad de jugadores a partir de la cual el rol puede aparecer.

| Rol | Clave | Bando | Mínimo | Mapa | Estado |
|---|---|---|---|---|---|
| Aldeano | `aldeano` | Pueblo | 5 | Todos | Completo (sin habilidad) |
| Policía / Detective / Comisario | `policia` | Pueblo | 5 | Todos | Completo |
| Médico | `medico` | Pueblo | 5 | Todos | Completo |
| Alcalde | `alcalde` | Pueblo | 8 | Todos | Completo |
| Asesino | `asesino` | Traidores | 5 | Todos | Completo |
| Espía | `espia` | Traidores | 10 | Todos | Completo (co-ejecutor, comparte la fase del Asesino) |
| Mercenario | `mercenario` | Traidores | 7 | Todos | Completo |
| Desertor | `desertor` | Neutral | 9 | Todos | Completo |
| Payador | `payador` | Pueblo (Rol de Mapa) | 8 | Pampa | Completo |
| Bufón | `bufon` | Neutral (Rol de Mapa) | 8 | Medieval | **Parcial** (sin acción nocturna) |
| Oráculo | `oraculo` | Pueblo (Rol de Mapa) | 8 | Grecia | Completo |

### Habilidades

- **Aldeano:** sin habilidad; participa en debate y votación.
- **Policía:** cada noche investiga a un jugador y recibe pista (parece inocente / sospechoso).
- **Médico:** cada noche protege a un jugador; si iba a morir, se cancela la eliminación.
- **Alcalde:** puede revelarse en el debate; desde entonces su voto vale doble y decide empates entre los dos más votados.
- **Asesino:** los asesinos eligen en conjunto una víctima por noche; si queda uno, decide solo.
- **Espía:** traidor que **elige la víctima junto a los Asesinos** cada noche (comparte la fase del Asesino, sin fase propia) y aparece **inocente** ante la investigación del Policía. Si caen todos los Asesinos, sigue matando por sí mismo (la sucesión es automática al ser un ejecutor más).
- **Mercenario:** traidor; puede **silenciar** a una víctima para que no hable ni vote al día siguiente.
- **Desertor:** elige bando al comenzar; puede reconsiderar **una sola vez** cuando quedan ~2/3 de los jugadores iniciales (`ceil(initial*2/3)`); debe sobrevivir para ganar con su bando final.
- **Payador (Pampa):** una vez por partida abre un **Contrapunto** entre dos jugadores; al terminar señala a uno, que recibe un voto adicional.
- **Bufón (Medieval):** busca que el Pueblo lo expulse en votación. **Solo gana si lo expulsan**; no gana si muere de noche.
- **Oráculo (Grecia):** una vez por partida invoca a un jugador muerto para el debate del día siguiente; el muerto habla pero no vota.

## Fases del juego (`GamePhase`)

El enum define 14 fases:

1. `REPARTO` — Asignación y lectura de cartas (con compuerta de inicio configurable).
2. `NOCHE_ASESINO` — Los asesinos eligen víctima.
3. `NOCHE_MERCENARIO` — El mercenario silencia.
4. `NOCHE_POLICIA` — El policía investiga.
5. `NOCHE_MEDICO` — El médico protege.
6. `NOCHE_ORACULO` — El oráculo invoca a un muerto (mapa Grecia).
7. `AMANECER` — Se revelan muertes, silencios y efectos del oráculo.
8. `DIA_DEBATE` — Discusión.
9. `CONTRAPUNTO` — Debate 2v1 del Payador (mapa Pampa).
10. `VOTACION` — Votación de expulsión.
11. `RECUENTO_VOTOS` — Conteo y detección de empates.
12. `DESEMPATE_VOTACION` — Resolución de empate por nueva votación.
13. `ALCALDE_DESEMPATE` — El Alcalde (vivo y revelado) decide el empate.
14. `RESULTADO` — Pantalla de fin de partida.

## Acciones (`GameActionType`)

`KILL`, `SILENCE`, `INVESTIGATE`, `PROTECT`, `INVITE_DEAD`, `VOTE`. Cada acción se registra en `actionHistory` con actor, objetivo, ronda, fase y si es de conocimiento público.

## Composición de roles

Tres presets (`RoleCompositionPreset`):

- **RECOMENDADO:** equilibrado según cantidad de jugadores y mapa. Agrega Mercenario desde 7, Alcalde + rol exclusivo desde 8, Desertor desde 9, Espía desde 10.
- **CLÁSICO:** Asesino, Detective, Médico y Aldeanos.
- **CAÓTICO:** más asesinos y roles especiales.

Máximo de asesinos según jugadores: 1 (hasta 8), 2 (9–12), 3 (13+).

**Online:** usa un preset seguro reducido (`onlineSafeRoleComposition`): solo 1 Policía, 1 Médico, 1 Asesino y el resto Aldeanos, sin roles especiales.

## Tiempos (`GameTimingPreset`)

Tiempos en segundos: transición / noche / debate / votación.

| Preset | Transición | Noche | Debate | Votación |
|---|---|---|---|---|
| LENTO | 6 | 90 | 180 | 60 |
| NORMAL | 4 | 40 | 120 | 20 |
| RÁPIDO | 2 | 20 | 60 | 15 |

Cada valor es configurable dentro de rangos definidos en `GameTimingConfig`.

## Lectura inicial de roles (`RoleRevealGate`)

Tres modos (`RoleRevealMode`):
- **WAIT_FOR_ALL:** espera a que todos confirmen.
- **BALANCED:** espera hasta un máximo configurable (10–90s).
- **QUICK:** avanza al alcanzar el mínimo de lectura (5–30s).

## Modo test rápido

`quickTestMode` (configurable en Lobby → Opciones avanzadas) acelera fases sin acción humana y la votación/expulsión, para probar flujos rápidamente. Por defecto las partidas locales creadas por `LocalGameFactory.createSession` arrancan con `quickTestMode = true`.
