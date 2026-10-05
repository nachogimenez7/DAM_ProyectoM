# Casos de paridad para la autoridad de servidor

Fecha: 5 de octubre de 2026. Rama `ios-port`, base `14ab819` más los cambios sin confirmar de Codex y de paridad de gameplay.
Alcance pedido: revisar la paridad Android/iOS, preparar casos de prueba de roles y dejar las
diferencias entre motores. **No se tocó código**; los cambios van como propuesta (sección 4) para que
Codex los integre. Lo único creado además de este informe fueron copias descartables en el scratchpad.

Referencias: Android `GameEngine.kt`, `GameModels.kt` (`GameRules`), `MatchHistoryStore.kt`; iOS
`ClassicGame.swift`; backend `functions/src/onlineStartCore.js`, `onlineStartService.js`, `accountHistoryService.js`.

## 0. Decisiones cerradas por el usuario (5/10/2026)

Las cuatro preguntas abiertas de la primera versión de este informe quedaron resueltas. Codex: **estas decisiones mandan**
sobre lo que diga el resto del documento donde haya conflicto.

| # | Tema | Decisión | Afecta a |
|---|---|---|---|
| 1 | Silencio del Mercenario | No se puede silenciar al mismo jugador dos noches seguidas. Si el Mercenario quiere silenciar a quien ya silenció la noche anterior, esa víctima aparece con la **carta inhabilitada** y no se la puede elegir. Pasada otra noche, vuelve a ser elegible (una noche de cooldown, para no arruinar la experiencia). | iOS (D-02); Android ya lo cumple |
| 2 | Desertor frente a victoria traidora | Que el comportamiento dependa de quién es el anfitrión es un **error grave**. La regla debe ser la misma para todos y vivir en el servidor. Propuesta en 4.4-1. | Servidor, Android, iOS (D-03/D-04/D-05) |
| 3 | Alcalde silenciado | No puede decidir un segundo empate ni «opinar nada»: ni hablar, ni votar, ni revelarse, ni decidir. | **Android e iOS** (ALC-08), servidor |
| 4 | Oráculo muerto la misma noche | Si el Oráculo ya usó su poder esa noche, la invitación **se mantiene** aunque lo maten. | Servidor; ambos motores ya lo hacen por lectura, falta prueba (ORA-06) |

## 1. Qué se ejecutó y qué no

| Suite | Resultado | Comando / nota |
|---|---|---|
| Núcleo iOS (`TraidoresCore`) | **86 pruebas, 8 suites, todas pasan** | `bash ios/Scripts/test_core.sh` |
| Android (selección) | **217 pruebas, 0 fallos**: `GameEngineTest` 200, `MatchOutcomeTest` 2, `AfkPolicyTest` 4, `OnlineProtocolSimulationTest` 1, `OnlineDesertorGateTest` 4, `OnlineMatchReturnGateTest` 5, `OnlineRoleAssignmentOrderTest` 1 | `sh ./gradlew :app:testDebugUnitTest --tests … --offline`. `./gradlew` **no tiene permiso de ejecución** (`permission denied`); lo corrí con `sh`. No lo cambié. |
| Backend `functions` | **23 pruebas, 0 fallos** | `node --test` de las cuatro suites unitarias, con el Node del usuario (`~/.local/node/bin`; `npm` no está en el PATH de este shell) |
| Sondas propias (iOS) | 5 sondas en una copia del paquete en el scratchpad | Confirman D-01, D-03, D-05 y D-07 (ver sección 3). La quinta, sobre D-06, no concluyó nada útil y no la cito. No quedaron en el repo. |
| Sonda propia (backend) | `recordsForFinishedRoom` con Desertor vivo/muerto, `Cancelada`, Bufón | Ver HIS-01 |

No verifiqué: Firebase real ni emuladores (las pruebas de integración del backend existen pero **no las corrí**; necesitan emuladores y hay un emulador RTDB ajeno en el puerto 9000), pruebas de UI iOS, instrumentación Android, ni partidas con dos
dispositivos. Los puntos marcados «lectura» salen de leer el código, no de ejecutarlo.

El árbol de Android tiene cambios sin confirmar de Codex; las 217 pruebas corrieron sobre ese estado, no sobre `14ab819`.

**Backend, ya corregido por Codex.** Mientras revisaba, `accountHistoryService.js` cambió: ahora el Desertor
solo gana si `player.vivo === true && desertorBando === ganador`. Mi sonda lo confirma
(`vivo=true → won=true`, `vivo=false → won=false`). Los casos HIS-01 a HIS-04 sirven para dejarlo fijado.

## 2. Convenciones de los casos

- «Esperado» es el comportamiento de **Android hoy** salvo que diga lo contrario; Android es la referencia.
- **Cobertura**: `A` = ya hay prueba Android, `S` = prueba Swift, `B` = prueba backend, `—` = sin prueba.
  Donde Android y iOS difieren, lo digo y remito a la diferencia `D-xx`.
- Nombres de acción online de Android (`OnlineActionResolver`): `matar`, `silenciar`, `investigar`, `salvar`,
  `invitar_muerto`, `guardar_poder`, `votar`, más las acciones de bando del Desertor.
- Campos de estado online: `alcaldeRevelado`, `corrupcionAlcalde`, `candidatosAlcalde`, `candidatosDesempate`,
  `rondaVoto`, `desertorBando`, `desertorCambioBando`, `payadorUsado`, `jugadoresContrapunto`,
  `sospechaContrapunto`, `oraculoUsado`, `invitadoOraculo`, `victoriasEspeciales[{key,jugador,rol,ronda}]`,
  `silenciado`, `victimaNoche`, `expulsadoDia`, `ganador`.
- «n inicial» = jugadores al arrancar la partida; el umbral del Desertor es `ceil(2n/3)` (14 → 10).
- Mapas: Payador solo en `pampa`, Oráculo solo en `grecia`, Bufón solo en `medieval`.

## 3. Diferencias entre motores

Gravedad: **Alta** cambia quién gana o el resultado guardado; **Media** cambia lo que ve o puede hacer un jugador;
**Baja** es cosmético o solo afecta a bots locales.

| ID | Tema | Android | iOS | Gravedad | Evidencia |
|---|---|---|---|---|---|
| D-01 | Silencio del Mercenario sobre quien protege el Médico | El silencio **se anula** si `nightSilenceTarget == protectedPlayer` (`GameEngine.kt:421-431`) | Se aplica igual (`ClassicGame.swift:343-345`) | Media | Sonda iOS: Médico protege al silenciado → `canSpeak == false` en el debate. Android: `medicProtectionPreventsMercenarySilenceOnSameTarget` pasa. |
| D-02 | Cooldown de silencio | No se puede silenciar al mismo jugador dos rondas seguidas (`canBeSilenced`, `lastSilencedRound`, `GameEngine.kt:1960-1977`) | Sin restricción; la prueba `mercenarySilenceKeepsCurrentIOSPortTargetRules` fija lo contrario | Media | **Decidido (0.1): iOS debe cumplir el cooldown** y mostrar la carta inhabilitada. Prueba existente iOS (rondas 2 y 3 incluyen al mismo objetivo) y Android `silenceCooldownBlocksNextRoundAndAllowsFollowingRound`. |
| D-03 | Desertor humano que aún puede reconsiderar y los traidores alcanzan paridad (**regla decidida en 0.2, ver 4.4-1**) | `withWinnerCheck` **pausa** la victoria traidora (`winner = ""`) mientras `canDesertorReconsider` (`GameEngine.kt:2437-2447`). La UI obliga a decidir (diálogo no cancelable). | Termina de inmediato con victoria traidora | Alta | Sonda iOS: 14 jugadores, Desertor vivo con bando Pueblo, 6 vivos (≤10), 3 traidores vs 2 pueblo → `winner = traidores` en el amanecer. En Android es lectura de código: **ninguna prueba cubre la pausa**. |
| D-04 | Esa pausa en online | `canDesertorReconsider` usa `humanPlayer(session)`, o sea el jugador **local del anfitrión**. Si el Desertor es un invitado, el anfitrión no pausa y los traidores ganan sin dejarle reconsiderar | No existe online | Alta (para el servidor) | Lectura (`GameEngine.kt:558-565` y `maybeApplyOnlineDesertorChoice`, `GameplayMockActivity.kt:12960-13005`). El comportamiento hoy depende de quién sea el anfitrión. **Hay que definir una regla única** (ver DES-09). |
| D-05 | Ganador tras elegir bando | `chooseDesertorTeam` no evalúa ganador; se evalúa en el próximo amanecer/resultado | `chooseDeserterTeam` llama a `checkWinner()` al instante | Media | Sonda iOS: reconsiderar a Traidores con paridad → `RESULTADO` y `humanWon = true` de inmediato. Lectura Android. |
| D-06 | Empate entre asesinos/espía | Determinista: ordena por `stableVoteNoise("code:ronda:objetivo")` y luego por nombre (`GameEngine.kt:164-181`) | `tied.randomElement(using: &random)` con RNG con semilla (`ClassicGame.swift:725`) | Alta (para el servidor) | Lectura. El servidor necesita la función exacta. Hay puerto JS y vectores en 4.3. |
| D-07 | Segundo empate, Alcalde entre exactamente dos empatados | «Corrupción»: expulsa al rival, `voteRound = 4`, `alcaldeCorruption = true`, mensaje propio | `voteRound = 3`, mensaje «El Alcalde decidió…», sin marca de corrupción | Baja/Media | Sonda iOS: `voteRound: 3`, mensaje «El Alcalde decidió: Lautaro será expulsado». Android: `mayorCandidateSurvivesARepeatedTwoPlayerTieThroughCorruption`. Afecta a la animación/relato, no al expulsado. |
| D-08 | Elección de bando de un Desertor bot | Compara pueblo vs traidores vivos (sin contar al Desertor), con 25 % de «lealtad» por semilla estable (`GameEngine.kt:2459-2487`); inicial por semilla de nombres/roles | `living.count <= players.count / 2 ? traidores : pueblo`; inicial por RNG 50/50 | Baja | Lectura. Solo bots locales; no afecta al servidor. |
| D-09 | Desertor que nunca elige (online) | `OnlineDesertorGate`: desde la ronda 2 el anfitrión asigna un bando con semilla estable | No existe (el humano debe elegir en el reparto; los bots lo resuelven) | Alta (para el servidor) | Android: `OnlineDesertorGateTest` pasa. Sin ganador posible mientras `desertorBando` esté vacío y haya Desertor vivo, así que **el servidor debe reproducir la red de seguridad**. |
| D-10 | AFK online | Contadores separados noche/voto, expulsión a la 2.ª omisión seguida, cancelación si todos fallan (`applyOnlineAfkOpportunity`, `GameEngine.kt:1341-1427`) | No existe | Alta (para el servidor) | Android: `online*Afk*` pasan. iOS local no expulsa (Android local tampoco: `localAfkDoesNotExpelHumanAndGivesNeutralHint`), así que el local ya coincide. |
| D-11 | Bots en la ruta de resolución | Online usa `…WithRecordedVotes` / `skipOnlineNightAction` para no activar IA del anfitrión | `advance()` resuelve bots dentro del mismo motor | Baja | Lectura. Para el servidor no hay bots salvo `simulado`; no portar la IA. |

Coinciden sin diferencias que importen (verificado por lectura y por las pruebas existentes de ambos lados): composición
recomendada por mapa y cantidad (`recommendedDeckMatchesAndroidOnEveryMapAndCount` + `onlineStartCore.unit`), objetivos válidos
del asesino, Detective que lee al Espía como inocente, voto doble del Alcalde, voto extra del Contrapunto en voto y desempate,
Oráculo (solo muertos, desde la ronda 2, una vez, invitado solo habla en ese debate), victoria especial del Bufón,
Desertor que debe sobrevivir, y los dos neutrales fuera de la paridad.

## 4. Cambios de código propuestos (no aplicados)

### 4.1 iOS: silencio y protección (D-01, D-02)

En `ClassicGame.swift`:

```swift
// Estado nuevo (Codable con decodeIfPresent para no romper partidas guardadas)
public internal(set) var lastSilencedRound: [Int: Int] = [:]

// legalTargets, caso .mercenaryNight where player.role == .mercenary
return living.filter { $0.id != actor &&
    (lastSilencedRound[$0.id].map { round - $0 >= 2 } ?? true) }.map(\.id)

// advance(), caso .dawn: reemplaza el bloque de silencedPlayer
if let silenced = silencedPlayer {
    if silenced == protectedPlayer || !players[silenced].alive {
        silencedPlayer = nil            // protegido o muerto: no se aplica
    } else {
        lastSilencedRound[silenced] = round
        append("\(name(silenced)) no puede hablar ni votar durante el día.")
    }
}
```

Hay que cambiar la prueba `mercenarySilenceKeepsCurrentIOSPortTargetRules`: ronda 2 sin el objetivo, ronda 3 con él
(decisión 0.1, ya confirmada).

Detalles de la regla, tal como la implementa Android:
- El cooldown se registra **solo si el silencio llegó a aplicarse**. Si el Médico lo anuló o la víctima murió esa noche,
  `lastSilencedRound` no cambia y el Mercenario puede elegir a esa persona de nuevo. El usuario no objetó esto; si se quiere
  lo contrario, hay que cambiarlo en ambos motores.
- La carta inhabilitada es de UI: en iOS la mesa debe pintar como no elegible a quien no esté en `legalTargets`, no solo ignorar
  el toque. Hay que revisar `LocalGameView.swift` (selección de objetivos de noche).
- Online el dato ya viaja: `jugadores[].ultimaRondaSilenciado` en el estado autoritativo. El servidor debe validar el cooldown al
  recibir `silenciar` (`round - ultimaRondaSilenciado >= 2`) y no confiar en que el cliente deshabilite la carta.

### 4.2 iOS: corrupción del Alcalde (D-07)

En el caso `.voteCount` con `voteRound == 2`, cuando el Alcalde está entre dos empatados: `voteRound = 4`,
una marca `mayorCorruption = true` (para la animación) y el texto de Android «Corrupción en el pueblo: el Alcalde
impuso su autoridad y X será expulsado.». `resolveMayorTie` conserva `voteRound = 3` para el otro caso.

### 4.3 Servidor: desempate de asesinos (D-06)

Android usa **dos** funciones de ruido distintas; no confundirlas:

- Desempate de víctima (`GameEngine.stableVoteNoise`): `acc` arranca en **0**.
- `stableNoise` (lealtad del Desertor, bando automático): `acc` arranca en **17**.

```js
// Equivale a Kotlin Int con desbordamiento: (acc*31 + char.code) and 0x7fffffff
function stableVoteNoise(seed) {
  let acc = 0;
  for (let i = 0; i < seed.length; i += 1) acc = ((Math.imul(acc, 31) + seed.charCodeAt(i)) | 0) & 0x7fffffff;
  return acc;
}
// Entre los objetivos con más votos: menor ruido de `${codigo}:${ronda}:${objetivo}`, y luego nombre
// por orden de unidades UTF-16 (sort() por defecto, NO localeCompare).
```

Vectores calculados con este puerto (sin cotejar contra Kotlin; **hay que añadir una prueba Kotlin que fije los mismos valores**):

| Semilla | Valor |
|---|---|
| `ABCDE:2:Mora` | 2096758992 |
| `ABCDE:2:Thiago` | 839404083 |
| `ABCDE:3:Mora` | 2125388143 |
| `SALA-01:1:Ñandú` | 826538268 |

Con `stableNoise` (arranca en 17): `ABCDE:3:desertor-loyalty` → 950909504 (`% 4 == 0` → el Desertor bot se queda en su bando).

### 4.4 Servidor: reglas que Android resuelve en el cliente y hay que definir

1. **Desertor reconsiderando (D-03/D-04/D-05) — propuesta de regla única, decidida por el servidor.**
   Principio: la elección del Desertor es una acción de jugador más (como `votar`); el anfitrión no interviene y el resultado no depende de quién sea.
   - *Cuándo se abre.* Al evaluar el ganador (amanecer, resultado de votación o expulsión por AFK): si el resultado sería **Traidores**, hay un Desertor
     **vivo**, con bando ya elegido, sin reconsideración usada (`desertorCambioBando = false`) y `vivos <= ceil(2n/3)`, el servidor **no** cierra la
     partida. Escribe `desertorReconsideracion = {abierta: true, limiteEpochMs}` y espera.
   - *Quién puede actuar.* Solo ese Desertor, con la acción de bando que ya existe (`desertor_rethink`), eligiendo `Pueblo`, `Traidores` o **mantener**
     (elegir el mismo bando también consume la reconsideración, igual que hoy).
   - *Cierre.* Al llegar la acción, o al vencer `limiteEpochMs`, el servidor marca `desertorCambioBando = true`, cierra la ventana y **reevalúa el ganador en el
     acto** (no espera al próximo amanecer; así queda igual que iOS hoy y se elimina la demora accidental de Android). El vencimiento equivale a «mantener».
   - *Plazo.* Sugerencia: `votacionSeg` de la sala (20 s por defecto, 10–60). No cuenta como omisión AFK (acción opcional, como el Contrapunto).
   - *Victoria de Pueblo.* No se pausa (igual que Android hoy). Es asimétrico a propósito: pausar le daría al Desertor la chance de cambiarse al bando
     ganador conociendo el resultado. Con traidores a punto de ganar la chance existe, pero es el comportamiento actual de Android.
     Si querés cerrarla, la alternativa es abrir la ventana **al cruzar el umbral** (cuando los vivos pasan a `<= ceil(2n/3)`) en vez de al final.
     No lo propongo como cambio hoy porque altera la dificultad que ya tiene Android.
   - *Qué cambia en cada motor.* Servidor: la ventana y el plazo. Android: `withWinnerCheck` hoy usa `humanPlayer(session)`; en online debe leer la ventana
     del estado en lugar de preguntarle a `canDesertorReconsider` del jugador local. iOS: `checkWinner()` debe diferir la victoria traidora mientras el Desertor
     humano pueda reconsiderar, con el mismo diálogo obligatorio que Android; el Desertor bot sigue con su política local.
   - *Casos.* DES-09 (reescrito abajo) y DES-12 a DES-14.
2. **Bando automático (D-09).** Desde la ronda 2, si hay Desertor vivo sin bando: `desertorBando = ((stableNoise(code|nombres…|desertor-auto) >>> 1) & 1) == 0 ? "Pueblo" : "Traidores"`,
   con nombres en el orden repartido. Está en `OnlineDesertorGate.autoTeam`.
3. **AFK (D-10).** Ver AFK-01 a AFK-09; el servidor debe calcular «requeridos» y «actuaron» igual que
   `requiredOnlineNightPlayerIndexes` / `actedOnlineNightPlayerIndexes`.
4. **Orden dentro del amanecer.** Primero muerte (si `victima != protegido`), luego silencio (se anula si el silenciado está protegido o murió),
   luego invitación del Oráculo, luego comprobar ganador. Android: `resolveDawn`.

## 5. Casos de prueba

Plantilla de estado base salvo que se indique otra cosa: sala de **8 jugadores** (J1…J8), mapa `pampa`, ronda 1, composición recomendada
(Asesino, Comisario/Detective, Médico, Mercenario, Alcalde, Payador, 2 Aldeanos), reloj sin vencer. «V» = voto, «→» = acción sobre.

### 5.1 Alcalde

**ALC-01 — Voto doble tras revelarse** · Cobertura A (`revealedAlcaldeVoteCountsDouble`), S
- Estado: debate de la ronda 1, Alcalde (J5) vivo y sin revelar.
- Acciones: J5 se revela; votación: J5 → J2, J6 → J2, J1 → J3, J4 → J3.
- Esperado: J2 = 3 (J5 vale 2 + J6), J3 = 2 → expulsado J2. Sin la regla del Alcalde serían 2 y 2. `alcaldeRevelado = true`, mensaje de revelación publicado una sola vez; un segundo intento de revelarse no cambia nada.

**ALC-02 — Sin revelar, voto simple** · A, S
- Estado: igual, J5 no se revela. Acciones: mismos votos. Esperado: J2 = 2, J3 = 2 → empate (ALC-03).

**ALC-03 — Primer empate abre el desempate entre los empatados** · A (`firstTieOpensRestrictedSecondVoteAndKeepsChatAvailable`, `secondVoteOnlyAcceptsTiedCandidates…`), S
- Estado: ALC-02. Acciones: se cuenta la votación.
- Esperado: `candidatosDesempate = [J2, J3]`, `rondaVoto = 1`; fase de desempate con `votos` vaciados; solo se acepta voto a J2 o J3; J2 y J3 no pueden votarse a sí mismos; un voto a otro jugador se descarta.

**ALC-04 — Segundo empate sin Alcalde vivo: nadie se expulsa** · A (`repeatedTieWithoutMayorEndsTheDayWithoutExpulsion`, `deadMayorCannotResolveARepeatedTie`), S
- Estado: J5 muerto de noche; desempate J2/J3 vuelve a empatar. Esperado: `expulsadoDia = ""`, mensaje «El empate se repitió. Nadie será expulsado esta jornada.», sigue la noche siguiente.

**ALC-05 — Segundo empate, Alcalde fuera de los empatados: decide él** · A (`hiddenHumanMayorMayRevealAfterRepeatedTie…`), S
- Estado: J5 vivo y sin revelar; empate J2/J3 en el desempate.
- Acciones: J5 se revela y elige J3.
- Esperado: `expulsadoDia = J3`, `rondaVoto = 3`. Si J5 no se revela, no puede decidir (`chooseAlcaldeTie` exige `alcaldeRevelado`). Si no decide antes del plazo: «El Alcalde no decidió el empate. Nadie será expulsado.», sin AFK.

**ALC-06 — Segundo empate, Alcalde entre exactamente dos empatados: corrupción** · A (`mayorCandidateSurvivesARepeatedTwoPlayerTieThroughCorruption`), S
- Estado: empate final J5/J3. Esperado Android: se revela solo, **J3** expulsado, `rondaVoto = 4`, `corrupcionAlcalde = true`. **Diferencia iOS: D-07** (`rondaVoto = 3`, sin marca).

**ALC-07 — Segundo empate, Alcalde entre ≥3 empatados** · A, S
- Estado: 10+ jugadores, empate J5/J2/J3. Esperado: J5 se revela, decide entre **J2 y J3** (no puede elegirse a sí mismo), `rondaVoto = 4`, corrupción.

**ALC-08 — Alcalde silenciado (decisión 0.3)** · A — / S — (hoy **ambos motores lo permiten**, por lo que este caso falla hasta aplicar el cambio)
- Estado: ronda con segundo empate; el Mercenario silenció al Alcalde (J5) esa noche.
- Esperado, el silenciado **no puede hacer nada**: no habla, no vota (su voto doble no suma), **no se revela**, **no decide** un segundo empate.
  - J5 no está entre los empatados: se trata como si no hubiera Alcalde → «El empate se repitió. Nadie será expulsado esta jornada.» (`expulsadoDia = ""`).
  - J5 está entre los empatados (rama de corrupción): tampoco puede imponer su autoridad ni auto-revelarse; se trata igual, nadie es expulsado.
    *Es mi interpretación de «no puede decidir ni opinar nada»; confirmar solo este subcaso.*
  - Un Alcalde silenciado que ya estaba revelado sigue figurando como revelado, pero sin voto ni decisión ese día.
- Cambios (propuestos, Android es de solo lectura para mí): `GameEngine.revealAlcalde`, `chooseAlcaldeTie` y la rama del Alcalde en `resolveSecondTie` deben
  excluir `muted`; en iOS, `revealMayor`, `chooseMayorTie`/`mayorTieBreak` y el cierre `.voteCount` con `revealedMayorID` deben excluir a `silencedPlayer`.
  El servidor valida lo mismo al recibir la acción.

**ALC-09 — Alcalde revelado muere** · A (`deadMayorCannotResolveARepeatedTie`), S
- Estado: Alcalde revelado muere de noche. Esperado: ya no puede decidir ni su voto previo cuenta; no se puede revelar un Alcalde muerto.

**ALC-10 — Voto doble + Contrapunto forman un empate** · A (`revealedMayorVoteAndPayadorSuspicionCanCombineIntoATie`), S (`forcedTiesAccountForMayorWeightAndContrapuntoVote`)
- Estado: Alcalde revelado (voto 2) a J2; Contrapunto señaló a J3 (+1); un voto simple a J3. Esperado: J2 = 2, J3 = 2 → empate.

### 5.2 Payador / Contrapunto (solo `pampa`)

**PAY-01 — Elegir dos participantes** · A (`payadorSelectsTwoPlayersAndRestrictsContrapuntoChat`), S
- Estado: debate, Payador (J6) vivo, `payadorUsado = false`. Acciones: J6 → J2, J6 → J4.
- Esperado: tras la primera selección no cambia de fase; tras la segunda, fase `CONTRAPUNTO`, `payadorUsado = true`, `jugadoresContrapunto = [J2, J4]`. Solo J2 y J4 pueden hablar; el resto no. Rechazos: elegirse a sí mismo, repetir jugador, elegir un muerto.

**PAY-02 — Señalamiento = +1 voto en la votación y en el desempate del mismo día** · A (`payadorSuspicionAddsOneVote`, `payadorSuspicionAffectsTheRecordedVoteTally`), S (`payadorRestrictsSpeechAndAddsVoteToBothBallotsOnlyToday`)
- Estado: Contrapunto J2/J4. Acciones: J6 señala a J4; votación sin otros votos a J4.
- Esperado: `sospechaContrapunto = J4`, J4 empieza con +1 en el recuento. Si hay desempate, el +1 sigue contando. En la noche siguiente `sospechaContrapunto = ""`.

**PAY-03 — Sin señalamiento por plazo** · A (`optionalContrapuntoTimeoutDoesNotAccumulateAfk`), S
- Esperado: «El Contrapunto terminó sin un señalamiento.», `sospechaContrapunto = ""`, pasa a votación, **no suma AFK** (acción opcional).

**PAY-04 — Una sola vez por partida** · A, S
- Estado: ronda 2, `payadorUsado = true`. Esperado: nueva selección rechazada; `payadorUsado` **no** se reinicia entre rondas.

**PAY-05 — Payador muerto** · A (parcial), S
- Estado: Payador muerto en la noche. Esperado: no hay selección ni señalamiento; el debate sigue normal.

**PAY-06 — El Payador solo existe en `pampa`** · B (`onlineStartCore.unit`)
- Estado: sala `grecia`/`medieval` con composición personalizada que pide Payador. Esperado: el reparto lo descarta (`normalizedCustomComposition`).

### 5.3 Oráculo (solo `grecia`)

**ORA-01 — Primera noche sin poder** · A (`oracleCannotUseOrSavePowerDuringFirstNightEvenWithADeadCandidate`), S
- Estado: ronda 1, ya hay un muerto (p. ej. AFK). Esperado: no hay fase de Oráculo; el poder no se consume.

**ORA-02 — Invocar a un muerto** · A (`oracleInvitesOneDeadPlayerAnonymouslyForDiscussionOnly`), S (`oracleInvokesOnlyDeadPlayersAfterFirstNightAndOnlyOnce`)
- Estado: ronda 2, Oráculo vivo, J7 muerto en la ronda 1, `oraculoUsado = false`. Acciones: `invitar_muerto` → J7.
- Esperado: `oraculoUsado = true`, `invitadoOraculo = J7`; en el debate J7 puede hablar; **no** vota, **no** actúa, no cuenta como vivo para la victoria; al pasar a votación `invitadoOraculo = ""`. Rechazos: vivo, uno mismo, segundo uso (`oracleCannotRecordASecondUse`).

**ORA-03 — Guardar el poder** · A (`oracleCanSavePowerForAnotherNight`), S (`oracleSkipKeepsPowerAndGuestExpiresAtNight`)
- Acción: `guardar_poder`. Esperado: `oraculoUsado = false`; **cuenta como actuó** para AFK (está en `onlineNightActionsForRole`).

**ORA-04 — Sin muertos: sin decisión** · A (`oracleWithoutDeadPlayersHasNoDecisionAndKeepsPowerAutomatically`)
- Esperado: pasa de largo, sin pedir acción, **sin** exigirla para AFK (`roleRequiresAction` exige candidatos).

**ORA-05 — Invitado no cuenta para la victoria** · A (`oracleInvitedDeadPlayerDoesNotCountAsAliveForVictory`), S (`deadHumanOracleGuestCanSpeakButCannotUseAbilitiesOrVote`)
- Estado: queda 1 Asesino y 1 Pueblo vivos + invitado. Esperado: la paridad se evalúa sin el invitado.

**ORA-06 — Oráculo muere la misma noche en que invoca (decisión 0.4)** · A — / S — (pruebas que faltan; el comportamiento ya es el decidido por lectura)
- Estado: ronda 2, Oráculo vivo con `oraculoUsado = false`; envía `invitar_muerto` → J7 (muerto) y esa noche los asesinos lo matan.
- Esperado: `oraculoUsado = true`, `invitadoOraculo = J7`; el amanecer anuncia la muerte del Oráculo **y** la invitación; J7 habla en ese debate, no vota, no actúa.
  El Oráculo muerto ya no puede volver a usar nada (de todos modos el poder está gastado).
- Contraste: si lo matan **antes** de que envíe la acción (p. ej. AFK-expulsado en la noche anterior), no hay invitación.
- Servidor: la acción nocturna se valida contra el estado al **inicio** de la noche, no contra el resultado de la muerte de esa misma noche.

### 5.4 Desertor (14–15 jugadores; vivo y eliminado)

Estado base: 14 jugadores, un Desertor (JD), Pueblo y Traidores según composición recomendada. n inicial = 14, umbral 10.

**DES-01 — Elección inicial obligatoria** · A, S
- Esperado: sin `desertorBando` no hay noche (humano); Detective lo lee «inocente»; en el chat de traidores **no** entra (equipo Neutral).

**DES-02 — Gana con su bando final y vivo** · A (`MatchOutcomeTest`), S (`deserterWinRequiresFinalSideAndSurvival`), B (HIS-01)
- Estado: `desertorBando = Pueblo`, JD vivo. Gana Pueblo. Esperado: JD `won = true`.

**DES-03 — Eliminado no gana, aunque su bando gane** · A (`desertorHistoryRequiresSurvivalAndTheWinningFinalSide`), S, B (HIS-02, corregido)
- Estado: igual que DES-02 pero JD murió de noche / fue expulsado / AFK. Esperado: `won = false` en Android, iOS y backend. **Repetir para las tres causas de muerte**.

**DES-04 — Bando Traidores y ganan los Traidores** · A, S, B
- Estado: `desertorBando = Traidores`, JD vivo, ganan Traidores. Esperado: `won = true`. Si gana Pueblo con JD Traidores: `won = false`.

**DES-05 — Reconsiderar una sola vez** · A (`desertorCanReconsiderOnlyOnceAtThreshold`), S
- Estado: bando elegido, 11 vivos. Esperado: no puede (umbral 10). Con 10 vivos: puede; tras usarlo (**incluso eligiendo el mismo bando**, que Android marca como usado) `desertorCambioBando = true` y no puede de nuevo.

**DES-06 — El umbral usa el n inicial, no los vivos de ese momento** · A, S
- Estado: n inicial 14 o 15. Esperado: umbral 10 en ambos (`ceil(28/3)` = `ceil(30/3)`); si la partida arrancó con 15 y hay 11 vivos, todavía no puede; con 10 sí.

**DES-07 — Objetivos de noche** · A (`desertorAlignedWithTraitorsIsNotAssassinTarget`, `desertorAlignedWithTownCanBeAssassinTarget`), S
- Esperado: con bando Traidores y vivo **no** es objetivo; con bando Pueblo sí. Si muere el Desertor Traidor por voto, no hay efecto especial.

**DES-08 — Paridad: el Desertor no cuenta nunca** · A (`desertorSupportingTraitorsDoesNotAccelerateParityWin`, `desertorDoesNotCountForParityEvenWhenSupportingTraitors`, `desertorWithoutChosenTeamDelaysParityVictory`), S
- Esperado: con bando Traidores sigue sin sumar a los traidores; con bando **vacío** y Desertor vivo, la victoria traidora se demora (no la de Pueblo, que gana igual si no quedan asesinos/espías).

**DES-09 — Reconsideración pendiente frente a victoria traidora (decisión 0.2)** · Android — (sin prueba), S — (hoy diverge), servidor — (por implementar)
- Estado: 14 jugadores, JD con `desertorBando = Pueblo`, **6 vivos** (JD, Mercenario, 2 Asesinos, 2 Pueblo), `desertorCambioBando = false`.
- Acción: amanece sin víctima; traidores 3 ≥ pueblo 2.
- Esperado (regla 4.4-1): `ganador = ""`, `desertorReconsideracion.abierta = true` con plazo. JD envía `Traidores` → `desertorBando = Traidores`, `desertorCambioBando = true`, ventana cerrada y
  `ganador = Traidores` **en el mismo paso**; JD `won = true`. Mismo resultado si JD es el anfitrión o un invitado.
- Hoy: Android solo pausa si JD es el anfitrión; iOS no pausa nunca (**D-03/D-04**).

**DES-12 — La regla no depende del anfitrión** · — (clave para el servidor)
- Estado: DES-09 repetido tres veces: JD es el anfitrión; JD es invitado; JD es invitado y el anfitrión se desconecta y pasa a otro (traspaso de host) durante la ventana.
- Esperado: las tres terminan igual (ventana abierta, misma decisión → mismo ganador). El traspaso de host no reinicia el plazo ni cierra la ventana.

**DES-13 — Vence el plazo** · —
- Estado: DES-09, JD no responde. Esperado: al vencer, equivale a «mantener» (`desertorBando = Pueblo`, `desertorCambioBando = true`), gana Traidores, JD `won = false`; **sin** omisión AFK.

**DES-14 — Sin ventana cuando no corresponde** · —
- Esperado: no se abre si gana Pueblo; si JD está muerto (incluida expulsión por AFK en ese mismo paso); si JD ya usó la reconsideración; si `vivos > ceil(2n/3)`; ni si el bando está vacío (ahí aplica DES-08/DES-10).

**DES-10 — Bando automático si nunca elige** · A (`OnlineDesertorGateTest`)
- Estado: ronda 2, JD vivo y `desertorBando = ""`. Esperado: el anfitrión/servidor lo asigna con la semilla estable; dos anfitriones distintos resuelven lo mismo.

**DES-11 — Desertor expulsado por AFK** · A (`afkExpulsionKeepsItsOwnDeathCause`)
- Esperado: `alive = false`, causa AFK, `won = false` aunque su bando gane.

### 5.5 Bufón (solo `medieval`)

**BUF-01 — Expulsado por votación gana y la partida sigue** · A (`bufonWinsSpecialConditionWhenExpelledAndGameContinues`), S (`jesterExpulsionRecordsPersonalVictoryAndContinues`)
- Estado: 8 jugadores medieval, el Bufón (J3) recibe la mayoría. Esperado: `victoriasEspeciales = [{key: "bufon_expulsado", jugador: J3, rol: "bufon", ronda: 1}]`, mensaje con «ganó al ser expulsado por el pueblo», sigue la noche si ningún bando ganó.

**BUF-02 — Expulsado por decisión del Alcalde en el desempate** · A (`bufonExpelledByMayorTieDecisionGetsSpecialVictory`), S
- Esperado: mismo registro; vale también la rama de corrupción.

**BUF-03 — Muerto de noche no gana** · A (`bufonDoesNotWinWhenKilledAtNight`), S (`jesterNightDeathNeverWins…`)
- Esperado: sin victoria especial; si ya estaba muerto, no se la puede otorgar luego (`specialVictoryNeverAwardedToAlreadyDeadJester`).

**BUF-04 — Expulsado por AFK no gana** · A (parcial: `afkExpulsionKeepsItsOwnDeathCause`), S — (no aplica)
- Esperado: causa AFK, **sin** victoria especial. El servidor no debe asignarla fuera de `resolveResult`.

**BUF-05 — Victoria especial y victoria del bando a la vez** · A (`bufonSpecialVictoryIsKeptWhenFactionAlsoWins`), S
- Estado: la expulsión del Bufón deja a Pueblo sin asesinos. Esperado: `ganador = Pueblo` y el Bufón además figura en `victoriasEspeciales`; los jugadores de Pueblo ganan por equipo, el Bufón por la victoria especial.

**BUF-06 — Bufón vivo al final pierde** · A, B
- Estado: gana un bando y el Bufón no fue expulsado. Esperado: `won = false` para el Bufón (equipo Neutral).

**BUF-07 — No cuenta para la paridad** · A, S
- Estado: quedan 1 Traidor + Bufón. Esperado: `traidores 1 >= pueblo 0` → ganan Traidores. Si no queda Asesino/Espía y solo sobreviven Bufón y Mercenario: **gana Pueblo** (el Mercenario no mantiene viva la partida).

**BUF-08 — Una sola victoria por jugador** · A
- Esperado: no se duplica el registro aunque se reevalúe `resolveResult`.

### 5.6 Varios asesinos, Espía y Mercenario

**KIL-01 — Mayoría de votos de asesinos** · A (`multipleAssassinsVoteForOneNightVictim`), S (`livingKillersCoordinateOnHumanTarget`)
- Estado: 13 jugadores, 2 Asesinos + 1 Espía vivos. Acciones: A1 → J5, A2 → J5, Espía → J6. Esperado: víctima J5.

**KIL-02 — Empate entre asesinos** · A — (sin prueba directa del hash), S (aleatorio) · **D-06**
- Estado: A1 → J5, A2 → J6, Espía no vota. Esperado Android: gana el objetivo con menor `stableVoteNoise("codigo:ronda:objetivo")`, luego nombre. Con los vectores de 4.3: para `ABCDE`, ronda 2, «Mora» (2096758992) vs «Thiago» (839404083) → **Thiago**.

**KIL-03 — Un asesino no vota** · A (`applyOnlineAfkOpportunity`), —
- Esperado: decide el voto válido del otro; con ningún voto: sin víctima, y ambos acumulan omisión AFK de noche.

**KIL-04 — Objetivos válidos** · A, S
- Esperado: no se puede matar a Asesino, Espía, Mercenario, uno mismo ni al Desertor alineado con Traidores; sí al resto.

**KIL-05 — Médico protege a la víctima** · A, S
- Esperado: `nocheSinVictima = true`, no se anuncia quién fue protegido; el Médico puede protegerse y repetir.

**KIL-06 — El Espía es un ejecutor más y lee inocente** · A, S (`SpyClassicGameTests`)
- Esperado: participa del voto de la noche; el Detective que lo investiga ve «inocente»; el Mercenario y el Asesino salen «sospechoso».

**KIL-07 — Victoria de Pueblo sin Asesino/Espía** · A, S
- Esperado: `ganador = Pueblo` cuando mueren todos los asesinos y espías, aunque queden Mercenario o Desertor Traidor.

**KIL-08 — Silencio y muerte la misma noche** · A (`mercenarySilenceDoesNotMuteAPlayerKilledTheSameNight`), S
- Esperado: el silenciado muerto no queda «silenciado».

**KIL-09 — Silencio protegido** · A (`medicProtectionPreventsMercenarySilenceOnSameTarget`), S — (**D-01**, iOS aplica el silencio)
- Esperado Android: no hay silencio, no hay anuncio, `lastSilencedRound` no se actualiza.

**KIL-10 — Cooldown de silencio** · A (`silenceCooldownBlocksNextRoundAndAllowsFollowingRound`), S — (**D-02**)
- Esperado Android: ronda 1 silencia a J2; ronda 2 no puede; ronda 3 sí.

**KIL-11 — Composición con más de un asesino** · B (`una composicion personalizada elimina roles de otro mapa y limita asesinos`)
- Esperado: máximo 1 asesino hasta 7 jugadores, 2 desde 8, 3 desde 13; preset recomendado 2 desde 13.

### 5.7 AFK online

Parámetros Android: `AfkPolicy.CONSECUTIVE_MISSES_BEFORE_EXPULSION = 2`; habilitado en online (`afkExpulsionEnabled = true`), deshabilitado en local.

**AFK-01 — Dos noches seguidas sin actuar** · A (`onlineNightAfkTracksEveryRequiredPlayerAndExpelsOnSecondMiss`)
- Estado: J4 (Médico) vivo, `consecutiveNightAfk = 0`. Noche 1: no envía `salvar`. Esperado: contador 1, aviso «Perdiste tu acción. Si vuelves a ausentarte en tu próxima noche, serás expulsado por AFK.». Noche 2: tampoco. Esperado: J4 `alive = false`, `deathCause = AFK`, `muted = false`, mensaje «J4 fue expulsado por inactividad.» y se evalúa el ganador.

**AFK-02 — Actuar reinicia solo su contador** · A (`validNightActionResetsOnlyNightAfkStreak`, `onlineVoteActionResetsOnlyVoteAfkStreak`)
- Esperado: una acción nocturna válida pone `consecutiveNightAfk = 0` y no toca `consecutiveVoteAfk`, y al revés.

**AFK-03 — Votación: dos ausencias seguidas** · A (`firstVoteTimeoutAbstainsAndSecondConsecutiveTimeoutExpelsForAfk`)
- Esperado: la primera es abstención con aviso; la segunda expulsa.

**AFK-04 — El desempate no suma** · A (`AfkPolicyTest`, `shouldCountVoteWindow`)
- Esperado: no votar en `DESEMPATE_VOTACION` no incrementa el contador, porque pertenece a la misma votación.

**AFK-05 — Quienes no deben acumular** · A (`deadOrMutedHumanDoesNotAccumulateAfk`, `roleWithoutNightActionDoesNotAccumulateAfk`, `optionalContrapuntoTimeoutDoesNotAccumulateAfk`, `mayorTieTimeoutExpelsNobodyAndDoesNotCountAfk`)
- Esperado: muertos, silenciados (en la votación), roles sin acción nocturna (Aldeano, Alcalde, Payador, Bufón, Desertor), el Contrapunto y la decisión del Alcalde no suman omisiones. Requeridos de noche: Asesino, Espía, Mercenario, Policía, Médico y Oráculo (solo ronda >1, sin usar y con muertos).

**AFK-06 — Todos fallan: partida cancelada** · A (`onlineAfkCancelsWithoutWinnerWhenEverybodyMissesAgain`)
- Estado: todos los vivos requeridos estaban en su 2.ª omisión. Esperado: `ganador = "Cancelada"`, mensaje «Partida cancelada por inactividad. Ningún jugador respondió.», **sin** victorias especiales, **sin** historial ni estadística (el backend ya filtra `ganador` ≠ Pueblo/Traidores — confirmado en mi sonda).

**AFK-07 — Expulsión por AFK dispara el ganador** · A
- Esperado: si la expulsión deja sin asesinos al tablero, `ganador = Pueblo` en el mismo paso.

**AFK-08 — Regla desactivada** · A (`onlineAfkAccountingIsIgnoredWhenRuleIsDisabled`, `localAfkDoesNotExpelHumanAndGivesNeutralHint`)
- Esperado: sin contadores ni expulsión; en local el aviso es neutro («Perdiste tu acción de esta ronda.»). iOS local ya se comporta así (no hay AFK).

**AFK-09 — Los contadores se reinician al repartir** · A (`assigningRolesKeepsLobbyTimingAndResetsAfkStreaks`)
- Esperado: al empezar una partida/revancha ambos contadores en 0.

### 5.8 Revancha

**REV-01 — Reinicio de sala por el creador** · A (`OnlineMatchReturnGateTest`, `OnlineLobbyRules.canPrepareRematch`)
- Estado: partida terminada (`ganador` ≠ ""). Acción: el creador (no un anfitrión interino) ejecuta el reinicio.
- Esperado en la sala: `estado = esperando`, `partidaInicialCreada = false`, `partidaInicial` y `estadoPartida` borrados, `limpiezaPendiente = true`, `hostVersion + 1`, jugadores `listo = false` y sin voto de mapa.
- Rechazos: quien no es el creador; reinicio ya en curso; limpieza pendiente.

**REV-02 — Iniciar mientras la limpieza sigue pendiente** · B parcial (integración `creador anterior pierde autoridad tras migración y cleanup bloquea callable`; el código `cleanup-pending` de `evaluateStart` no tiene prueba unitaria propia)
- Esperado: `iniciarPartidaV2` responde `cleanup-pending` (o `room-not-waiting` si el estado no es `esperando`) hasta que la limpieza termine.

**REV-03 — Nuevo `matchId` y reparto nuevo** · B (integración `el backend inicia, reparte en privado y sincroniza RTDB`; no cubre una segunda partida en la misma sala)
- Esperado: `crypto.randomUUID()` distinto, roles barajados de nuevo, `repartos/{uid}` nuevos, `hostVersion + 1`. El historial del partido anterior no se toca.

**REV-04 — El historial no cuenta dos veces ni mezcla partidas** · B (integración `entrega concurrente cuenta una vez…` y `trigger desplegado en emulador… sobrevive al borrado de sala`)
- Esperado: la clave es `online:{matchId}`; el reintento del mismo evento no duplica; la revancha crea otro registro. Se usa la instantánea final del evento aunque la sala ya se haya reiniciado.

**REV-05 — Estado por partida limpio** · —
- Esperado tras la revancha: `alcaldeRevelado = false`, `payadorUsado = false`, `oraculoUsado = false`, `invitadoOraculo = ""`, `desertorBando = ""`, `desertorCambioBando = false`, `victoriasEspeciales = []`, `silenciado = ""`, `lastSilencedRound` vacío, AFK en 0, `ronda = 1`, `phaseIndex = 0`. **Sin prueba que lo verifique de punta a punta contra el backend**: conviene un caso de integración que arranque, termine, reinicie y vuelva a iniciar.

**REV-06 — Cambió la cantidad de jugadores** · B (`onlineStartCore.unit`: `cantidad y listo se verifican en el backend`)
- Esperado: `player-count-mismatch` si `jugadoresEsperados` no coincide con los activos; nadie puede iniciar salvo el anfitrión activo.

**REV-07 — Reintento idempotente de inicio** · B (integración `reintento de inicio no resucita muertos…` y `partida avanzada sin espejo…`)
- Esperado: `already_started` con el mismo `matchId`, sin repartir de nuevo; si `phaseIndex > 0` y el RTDB no coincide: `requires-match-recovery`.

**REV-08 — Revancha tras cancelación por AFK** · —
- Esperado: no hay historial de la partida cancelada; la sala se puede reiniciar con las mismas condiciones de REV-01 (`ganador` no vacío).

### 5.9 Historial: matriz de «gana» por rol

Regla común (Android `MatchOutcome.didHumanWin` y `recordsForFinishedRoom`): victoria especial → sí; Desertor → vivo **y** bando final == ganador; Pueblo ganador → equipo Pueblo (aunque esté muerto); Traidores ganadores → Asesino/Mercenario/Espía; `Cancelada` → no se registra.

| ID | Rol | Ganador | Vivo | Bando del Desertor | Esperado `won` | Cobertura |
|---|---|---|---|---|---|---|
| HIS-01 | Desertor | Pueblo | sí | Pueblo | **sí** | A, S, B (verificado) |
| HIS-02 | Desertor | Pueblo | **no** | Pueblo | **no** | A, S, B (corregido por Codex) |
| HIS-03 | Desertor | Traidores | sí | Traidores | sí | B (verificado) |
| HIS-04 | Desertor | Pueblo | sí | Traidores | no | A |
| HIS-05 | Bufón con victoria especial | Traidores | cualquiera | — | sí | B (verificado) |
| HIS-06 | Bufón sin victoria especial | cualquiera | cualquiera | — | no | A |
| HIS-07 | Aldeano muerto | Pueblo | no | — | sí | A, B |
| HIS-08 | Mercenario muerto | Traidores | no | — | sí | A, B |
| HIS-09 | cualquiera | `Cancelada` | — | — | sin registro | B (verificado) |

## 6. Orden sugerido para Codex

1. Implementar la regla del Desertor de 4.4-1 en el servidor (DES-09, DES-12 a DES-14) y el bando automático (D-09).
2. Portar el desempate de asesinos con el puerto de 4.3 y añadir la prueba Kotlin con los vectores.
3. Portar AFK (AFK-01 a AFK-09) junto con los requeridos/actuaron.
4. Alcalde silenciado (ALC-08) y Oráculo (ORA-06): validar en el servidor y escribir las pruebas; el cambio de motores va en 4.4-1/ALC-08.
5. iOS: 4.1 (cooldown y carta inhabilitada; ya aprobado), D-01 (silencio anulado por protección) y el diferimiento del Desertor. Cambia una prueba existente.
6. Añadir REV-05 como integración (arrancar, terminar, reiniciar y volver a iniciar) y REV-08; son los dos casos de revancha sin ninguna cobertura.
7. Poner permiso de ejecución a `gradlew` (`chmod +x`) o documentar el uso de `sh ./gradlew`.

## 7. Pendiente de confirmar

Todo lo decidido está en la sección 0. Quedan solo dos detalles menores, con la interpretación que propongo ya aplicada en el texto:

- ALC-08: Alcalde silenciado **entre los empatados** → nadie es expulsado (en vez de corrupción).
- 4.1: el cooldown del Mercenario se registra solo si el silencio llegó a aplicarse (no si lo anuló el Médico).
