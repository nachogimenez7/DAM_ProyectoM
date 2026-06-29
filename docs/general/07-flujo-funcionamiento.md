# Flujo general de funcionamiento

## Mapa de navegación (Activities)

```
MainActivity
 ├─ ProfileActivity ─ ProfileSelectionActivity
 ├─ RolesActivity
 ├─ AyudaActivity
 ├─ OpcionesActivity
 └─ JugarActivity
     ├─ LocalModeActivity ─────────────► LobbyActivity
     └─ OnlineModeActivity
          ├─ (crear sala) ─────────────► LobbyActivity
          ├─ LobbyBrowserActivity ─────► LobbyActivity
          └─ (recuperación en curso) ──► GameplayMockActivity

LobbyActivity ─► AssigningRolesActivity ─► GameplayMockActivity ─► (RESULTADO)
```

- La navegación es por `Intent` explícitos. No hay invalidación global de una partida iniciada al volver atrás.
- `AssigningRolesActivity` bloquea el back durante el reparto y hace `finish()` al abrir gameplay.
- `GameplayMockActivity` intercepta el back para cerrar overlays/chat antes de permitir salir.

## Flujo local (vs IA) — camino principal

1. **Main → Jugar → Local:** `LocalModeActivity` crea una `GameSession` con `LocalGameFactory.createSession` (jugador humano + bots, mapa Pampa por defecto, `quickTestMode = true`).
2. **Lobby:** el jugador ajusta cantidad de jugadores (5–15), mapa, tiempos, composición de roles, modo de lectura y opciones avanzadas.
3. **Reparto:** `LobbyActivity` llama a `LocalGameFactory.assignRoles` → baraja y asigna roles según la composición normalizada; abre `AssigningRolesActivity`.
4. **Lectura de carta:** el jugador ve su rol; `RoleRevealGate` decide cuándo iniciar según el modo configurado.
5. **Gameplay:** `GameplayMockActivity` ejecuta el ciclo de fases con `GameEngine`:
   - Noches (`NOCHE_ASESINO` → `NOCHE_MERCENARIO` → `NOCHE_POLICIA` → `NOCHE_MEDICO` → `NOCHE_ORACULO` según roles presentes).
   - `AMANECER` revela muertes/silencios/efectos.
   - `DIA_DEBATE` (+ `CONTRAPUNTO` si hay Payador).
   - `VOTACION` → `RECUENTO_VOTOS` → (`DESEMPATE_VOTACION` / `ALCALDE_DESEMPATE` si hay empate).
   - Se chequea condición de victoria (`GameRules.winnerFor`).
6. **Resultado:** al cumplirse una condición de victoria, fase `RESULTADO`; `WinnerResultsRenderer` y animadores presentan el desenlace (incluye victorias especiales como la del Bufón).

Durante el debate, los bots conversan e interactúan mediante `LocalBotAi` (personalidades, lectura de relaciones, planes de voto, reacciones a mensajes del humano).

### Chat de gameplay

`GameplayChatController` muestra un feed ambiental central con los ultimos mensajes del pueblo y permite expandir el chat completo al tocarlo. En local, el envio actualiza la `GameSession` y programa reacciones de bots. En online, el envio escucha/escribe en `partidas/{id}/chat`, manteniendo cooldown y limite de longitud desde cliente. Si el jugador esta eliminado, silenciado o la fase no permite hablar, el chat queda en solo lectura con un hint explicito.

## Ciclo del motor (`GameEngine`)

`GameSession` (inmutable) → `GameEngine.<resolución de fase>` → nueva `GameSession`. El motor:
- Resuelve acciones nocturnas (`resolveAssassin`, `resolveMercenary`, `resolvePolice`, `resolveMedic`, oráculo).
- Cuenta votos (con voto doble del Alcalde) y resuelve empates.
- Aplica cambio de bando del Desertor y el Contrapunto del Payador.
- Registra historia pública/privada, ledger de afirmaciones y acciones.

## Flujo online (experimental)

Resumen; contrato completo en [`../firebase-online-schema.md`](../firebase-online-schema.md).

1. **Crear sala:** `OnlineRoomFirestore.createRoom` escribe `partidas/{id}` (estado `esperando`) y al host en `partidas/{id}/jugadores/{uidTemporal}`. Verifica que el `codigoSala` (6 caracteres `A-HJ-NP-Z2-9`) no exista.
2. **Unirse:** por código (sólo salas `esperando`) o desde `LobbyBrowserActivity`. Cada jugador registra su presencia con `uidTemporal` e `orden`.
3. **Presencia/listos:** la sala no inicia hasta que `jugadoresActuales == jugadoresEsperados` y todos estén `listo`.
4. **Inicio:** el host crea `partidaInicial` **una sola vez** (transaccional, protegido por `partidaInicialCreada`), con preset seguro de roles (`onlineSafeRoleComposition`).
5. **Sincronización por fases:** el **host activo** resuelve y publica `estadoPartida`; los invitados registran acciones/votos en `partidas/{id}/acciones` y aplican el estado recibido. Noche y votación esperan el timer completo.
6. **Reingreso/recuperación:** un cliente reconstruye desde `partidaInicial` + `estadoPartida` (`OnlineMatchSessionBuilder`); si la sala está `esperando` vuelve al lobby, si está `en_juego` abre gameplay con su carta/estado.
7. **Handoff de host:** si el host activo cae, el primer conectado por `orden` puede tomar `hostActivoId` (`hostVersion` cuenta los handoffs).
8. **Fin/abandono:** la sala se marca `finalizada` o `abandonada` (limpieza manual; no hay TTL automático).

### Limitaciones del online (estado actual)
- Sin Firebase Auth (se usa `uidTemporal`), sin App Check, sin Cloud Functions.
- Las reglas validan forma/tamaño pero no frecuencia de escritura.
- El chat y las acciones se persisten append-only (sin update/delete).

## Persistencia transversal

- Preferencias y perfil → `SharedPreferences` (`TraidoresPrefs`): audio, idioma, vibración, tamaño de texto, nombre, avatar/banner/rol favorito, ID público, datos de recuperación de sala.
- Estado de Activity → `onSaveInstanceState` (gameplay y perfil guardan borradores/estado de overlays).
- Estado de partida local → `GameSession` `Serializable` viaja por `Intent`/`Bundle`.
- Música/efectos → `MusicManager` coordinado por `BaseActivity` según ciclo de vida.
