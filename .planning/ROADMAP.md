# Roadmap: App Traidores Stabilization

## Overview

Este roadmap estabiliza las cuatro superficies prioritarias sin agregar funciones nuevas. El trabajo avanza por pantallas completas: primero gameplay y chat, luego lobby y perfil, y finalmente las rutas y criterios de usabilidad compartidos. Cada fase conserva la identidad visual actual y termina con comprobaciones manuales que el usuario puede ejecutar en Android Studio.

## Phases

- [ ] **Phase 1: Gameplay Visual Stability** - Corregir la composiciÃ³n principal, capas y estados del gameplay. *(executed; pending visual validation)*
- [ ] **Phase 2: Chat and Keyboard Stability** - Mantener conversaciÃ³n y controles utilizables con el teclado abierto o cerrado. *(executed; pending visual validation)*
- [ ] **Phase 3: Lobby Stability** - Corregir diÃ¡logos, disponibilidad de salas y estados vacÃ­os. *(executed; pending visual validation)*
- [ ] **Phase 4: Profile Stability** - Corregir el perfil, selectores y persistencia del borrador. *(executed; pending visual validation)*
- [ ] **Phase 5: Navigation and Usability Guard** - Unificar AtrÃ¡s, rutas, controles importantes y verificaciÃ³n final.

## Phase Details

### Phase 1: Gameplay Visual Stability
**Goal**: El gameplay actual se mantiene legible y operable en paisaje compacto durante sus fases y capas existentes.
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: GAME-01, GAME-02, GAME-03, GAME-04
**Success Criteria**:
  1. El encabezado, eventos, jugadores y panel inferior permanecen visibles sin superposiciones esenciales.
  2. Los nombres, roles, ayudas y mensajes activos se leen completos dentro del espacio disponible.
  3. Las acciones bloqueadas o completadas se distinguen y no responden a pulsaciones.
  4. Abrir y cerrar una capa no deja espacio reservado, controles bloqueados ni fondos de una fase incorrecta.
**Plans**: 2 plans

Plans:
- [x] 01-01: Auditar estados y corregir la estructura visual principal del gameplay. *(Wave 1)*
- [x] 01-02: Consolidar renderizado de capas y estados disponibles, bloqueados y completados. *(Wave 2; blocked on 01-01)*

Cross-cutting constraints:
- No agregar funciones nuevas ni roles durante esta fase.
- Mantener la arquitectura Kotlin/XML actual.
- Priorizar telefonos en paisaje compacto.
- El usuario hara la validacion visual final en Android Studio.

### Phase 2: Chat and Keyboard Stability
**Goal**: El jugador puede conversar sin perder de vista los mensajes ni desarmar el gameplay.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: CHAT-01, CHAT-02, CHAT-03, CHAT-04
**Success Criteria**:
  1. Al escribir, los mensajes recientes y el campo de entrada siguen visibles.
  2. El espacio del chat responde al tamaÃ±o real del teclado sin franjas vacÃ­as ni controles tapados.
  3. Enviar, ocultar el teclado y reabrir el chat mantiene una disposiciÃ³n estable.
  4. Los mensajes nuevos permanecen alcanzables con el teclado abierto y cerrado.
**Plans**: 2 plans

Plans:
- [x] 02-01: Reemplazar supuestos rÃ­gidos del teclado por un estado de viewport medido.
- [x] 02-02: Estabilizar lista, envÃ­o, scroll y transiciones de apertura y cierre del chat.

### Phase 3: Lobby Stability
**Goal**: El lobby y la bÃºsqueda de partidas comunican y ejecutan correctamente sus estados actuales.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: LOBBY-01, LOBBY-02, LOBBY-03, LOBBY-04
**Success Criteria**:
  1. Los diÃ¡logos de tiempos y opciones avanzadas conservan contenido y acciones visibles en paisaje compacto.
  2. Una sala disponible, llena o en partida presenta un texto y comportamiento inequÃ­vocos.
  3. Los controles de configuraciÃ³n muestran el mismo estado que realmente ejecutan.
  4. Una lista sin salas presenta un mensaje comprensible dentro del estilo de la aplicaciÃ³n.
**Plans**: 2 plans

Plans:
- [x] 03-01: Hacer responsivos los diÃ¡logos y controles de configuraciÃ³n del lobby.
- [x] 03-02: Centralizar disponibilidad de salas, botones y presentaciÃ³n del estado vacÃ­o.

### Phase 4: Profile Stability
**Goal**: El perfil y su ediciÃ³n son legibles, navegables y resistentes a una recreaciÃ³n normal de pantalla.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: PROF-01, PROF-02, PROF-03, PROF-04
**Success Criteria**:
  1. Perfil, ediciÃ³n, logros e imÃ¡genes se recorren sin contenido esencial cortado.
  2. Cada elemento editable y selector actual tiene un destino y regreso coherentes.
  3. Una recreaciÃ³n de Activity conserva el borrador y el modo de ediciÃ³n hasta guardar o descartar.
  4. Los datos todavÃ­a no disponibles se muestran como marcadores claros y no como estadÃ­sticas reales.
**Plans**: 2 plans

Plans:
- [x] 04-01: Corregir composiciÃ³n, textos, controles editables y retornos de los selectores.
- [x] 04-02: Preservar borrador, modo de ediciÃ³n y estados sin datos durante recreaciones.

### Phase 5: Navigation and Usability Guard
**Goal**: Las cuatro superficies comparten resultados previsibles de navegaciÃ³n y controles utilizables.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: NAV-01, NAV-02, NAV-03, USE-01
**Success Criteria**:
  1. Recorrer menÃº, lobby, perfil y gameplay y luego regresar no duplica pantallas ni abre destinos inesperados.
  2. AtrÃ¡s en gameplay cierra primero la capa transitoria visible siguiendo una prioridad Ãºnica.
  3. Volver, cerrar, cancelar y AtrÃ¡s producen resultados conceptualmente coherentes.
  4. Los controles importantes tienen Ã¡reas tÃ¡ctiles utilizables, etiquetas comprensibles y estados con contraste suficiente.
**Plans**: 2 plans

Plans:
- [x] 05-01: Normalizar rutas y prioridad de Atras en gameplay, lobby y perfil.
- [x] 05-02: Ejecutar auditoria final de controles, estados y matriz manual de navegacion.

## Progress

**Execution Order:** Phase 1 -> Phase 2 -> Phase 3 -> Phase 4 -> Phase 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Gameplay Visual Stability | 2/2 | Pending visual validation | - |
| 2. Chat and Keyboard Stability | 2/2 | Pending visual validation | - |
| 3. Lobby Stability | 2/2 | Pending visual validation | - |
| 4. Profile Stability | 2/2 | Pending visual validation | - |
| 5. Navigation and Usability Guard | 2/2 | Pending visual validation | - |
