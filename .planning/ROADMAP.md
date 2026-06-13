# Roadmap: App Traidores Stabilization

## Overview

Este roadmap estabiliza las cuatro superficies prioritarias sin agregar funciones nuevas. El trabajo avanza por pantallas completas: primero gameplay y chat, luego lobby y perfil, y finalmente las rutas y criterios de usabilidad compartidos. Cada fase conserva la identidad visual actual y termina con comprobaciones manuales que el usuario puede ejecutar en Android Studio.

## Phases

- [ ] **Phase 1: Gameplay Visual Stability** - Corregir la composición principal, capas y estados del gameplay.
- [ ] **Phase 2: Chat and Keyboard Stability** - Mantener conversación y controles utilizables con el teclado abierto o cerrado.
- [ ] **Phase 3: Lobby Stability** - Corregir diálogos, disponibilidad de salas y estados vacíos.
- [ ] **Phase 4: Profile Stability** - Corregir el perfil, selectores y persistencia del borrador.
- [ ] **Phase 5: Navigation and Usability Guard** - Unificar Atrás, rutas, controles importantes y verificación final.

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
- [ ] 01-01: Auditar estados y corregir la estructura visual principal del gameplay.
- [ ] 01-02: Consolidar renderizado de capas y estados disponibles, bloqueados y completados.

### Phase 2: Chat and Keyboard Stability
**Goal**: El jugador puede conversar sin perder de vista los mensajes ni desarmar el gameplay.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: CHAT-01, CHAT-02, CHAT-03, CHAT-04
**Success Criteria**:
  1. Al escribir, los mensajes recientes y el campo de entrada siguen visibles.
  2. El espacio del chat responde al tamaño real del teclado sin franjas vacías ni controles tapados.
  3. Enviar, ocultar el teclado y reabrir el chat mantiene una disposición estable.
  4. Los mensajes nuevos permanecen alcanzables con el teclado abierto y cerrado.
**Plans**: 2 plans

Plans:
- [ ] 02-01: Reemplazar supuestos rígidos del teclado por un estado de viewport medido.
- [ ] 02-02: Estabilizar lista, envío, scroll y transiciones de apertura y cierre del chat.

### Phase 3: Lobby Stability
**Goal**: El lobby y la búsqueda de partidas comunican y ejecutan correctamente sus estados actuales.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: LOBBY-01, LOBBY-02, LOBBY-03, LOBBY-04
**Success Criteria**:
  1. Los diálogos de tiempos y opciones avanzadas conservan contenido y acciones visibles en paisaje compacto.
  2. Una sala disponible, llena o en partida presenta un texto y comportamiento inequívocos.
  3. Los controles de configuración muestran el mismo estado que realmente ejecutan.
  4. Una lista sin salas presenta un mensaje comprensible dentro del estilo de la aplicación.
**Plans**: 2 plans

Plans:
- [ ] 03-01: Hacer responsivos los diálogos y controles de configuración del lobby.
- [ ] 03-02: Centralizar disponibilidad de salas, botones y presentación del estado vacío.

### Phase 4: Profile Stability
**Goal**: El perfil y su edición son legibles, navegables y resistentes a una recreación normal de pantalla.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: PROF-01, PROF-02, PROF-03, PROF-04
**Success Criteria**:
  1. Perfil, edición, logros e imágenes se recorren sin contenido esencial cortado.
  2. Cada elemento editable y selector actual tiene un destino y regreso coherentes.
  3. Una recreación de Activity conserva el borrador y el modo de edición hasta guardar o descartar.
  4. Los datos todavía no disponibles se muestran como marcadores claros y no como estadísticas reales.
**Plans**: 2 plans

Plans:
- [ ] 04-01: Corregir composición, textos, controles editables y retornos de los selectores.
- [ ] 04-02: Preservar borrador, modo de edición y estados sin datos durante recreaciones.

### Phase 5: Navigation and Usability Guard
**Goal**: Las cuatro superficies comparten resultados previsibles de navegación y controles utilizables.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: NAV-01, NAV-02, NAV-03, USE-01
**Success Criteria**:
  1. Recorrer menú, lobby, perfil y gameplay y luego regresar no duplica pantallas ni abre destinos inesperados.
  2. Atrás en gameplay cierra primero la capa transitoria visible siguiendo una prioridad única.
  3. Volver, cerrar, cancelar y Atrás producen resultados conceptualmente coherentes.
  4. Los controles importantes tienen áreas táctiles utilizables, etiquetas comprensibles y estados con contraste suficiente.
**Plans**: 2 plans

Plans:
- [ ] 05-01: Normalizar rutas y prioridad de Atrás en gameplay, lobby y perfil.
- [ ] 05-02: Ejecutar auditoría final de controles, estados y matriz manual de navegación.

## Progress

**Execution Order:** Phase 1 -> Phase 2 -> Phase 3 -> Phase 4 -> Phase 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Gameplay Visual Stability | 0/2 | Not started | - |
| 2. Chat and Keyboard Stability | 0/2 | Not started | - |
| 3. Lobby Stability | 0/2 | Not started | - |
| 4. Profile Stability | 0/2 | Not started | - |
| 5. Navigation and Usability Guard | 0/2 | Not started | - |
