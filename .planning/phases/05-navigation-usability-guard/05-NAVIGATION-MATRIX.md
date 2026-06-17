# Phase 5 Navigation Matrix

Manual checks pending on Android Studio/device.

## Main Routes

| Route | Action | Expected result |
|---|---|---|
| Main -> Jugar | Tap `JUGAR` | Opens mode selection screen once |
| Jugar -> Local | Tap `JUGAR LOCAL` | Opens local mode screen |
| Jugar -> Online | Tap `JUGAR EN LINEA` | Opens online mode screen |
| Local -> Lobby | Tap `CREAR SALA LOCAL` or `INGRESAR CODIGO` | Opens lobby with preserved back path to local mode |
| Online -> Browser | Tap `BUSCAR PARTIDA` | Opens room browser |
| Browser -> Lobby | Enter any joinable room | Opens lobby with preserved back path to browser |
| Online -> Quick/Create | Tap quick or create | Opens lobby with preserved back path to online mode |

## Back Behavior

| Screen | Visible state | Back result |
|---|---|---|
| Assigning roles | Card dealing animation | Returns to lobby and cancels pending auto-open to gameplay |
| Gameplay | Winner reveal | Returns to lobby |
| Gameplay | Role preview open | Closes role preview first |
| Gameplay | Chat open | Closes chat first |
| Gameplay | Action feedback banner visible | Hides banner first |
| Gameplay | Event log expanded | Collapses event log first |
| Gameplay | Blocking reveal/transition/tie/result/private feedback | Back does nothing until the blocking layer ends |
| Profile | Not editing | Returns to previous screen |
| Profile | Editing + choose `Seguir editando` | Remains on profile edit state |
| Profile | Editing + choose `Descartar` | Restores saved profile and exits profile |

## Empty and Disabled States

| Surface | Condition | Expected result |
|---|---|---|
| Lobby browser | Room full | Button shows `LLENA` and stays disabled |
| Lobby browser | Room in progress | Button stays disabled and announces that the room is in progress |
| Online guest lobby | Map/time/add/remove controls | Disabled; host-only behavior remains explicit |
| Lobby | Start button disabled | Communicates whether the lobby is waiting for host or missing players |
| Lobby player row | Host kick button | Disabled and explained as unavailable |

## Accessibility and Labels

| Control | Expected label |
|---|---|
| `JUGAR LOCAL` card | `Jugar una partida local` |
| `JUGAR EN LINEA` card | `Jugar una partida en linea` |
| Gameplay chat button | `Abrir chat`, `Cerrar chat`, or `Abrir chat, N mensajes nuevos` |
| Gameplay event log toggle | `Expandir eventos` or `Ocultar eventos` |
| Lobby player profile button | `Ver perfil de <jugador>` |
| Lobby player kick button | `Expulsar a <jugador>` or unavailable explanation |
| Profile edit icons | Explicit field-specific edit labels |

## Device-Only Checks

- Confirm compact phones still show the central map area correctly when gameplay starts with the event log collapsed.
- Confirm Android system back in `AssigningRolesActivity` never jumps into gameplay after returning to the lobby.
- Confirm the profile discard dialog exits the screen after `Descartar`.
- Confirm chat and event-log content descriptions remain coherent with TalkBack enabled.
- Confirm no route produces stacked duplicate screens during normal play-entry and return flows.
