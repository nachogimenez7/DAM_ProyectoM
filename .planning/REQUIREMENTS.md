# Requirements: App Traidores Stabilization

**Defined:** 2026-06-13
**Core Value:** El jugador debe poder recorrer y utilizar las pantallas principales sin contenido cortado, controles confusos, rutas rotas ni pérdida inesperada de estado.

## v1 Requirements

### Gameplay

- [ ] **GAME-01**: El jugador puede ver y utilizar el encabezado, eventos, jugadores y panel inferior del gameplay en un teléfono compacto sin superposiciones ni contenido esencial fuera de pantalla.
- [ ] **GAME-02**: El jugador puede leer completos los nombres, roles, ayudas y mensajes activos del gameplay dentro del espacio disponible.
- [ ] **GAME-03**: El jugador distingue claramente cuándo una acción del gameplay está disponible, bloqueada o completada, y los controles rechazados no ejecutan acciones.
- [ ] **GAME-04**: El jugador puede abrir y cerrar las capas actuales del gameplay sin que una capa oculta conserve espacio, bloquee controles visibles o deje fondos incorrectos.

### Chat

- [ ] **CHAT-01**: El jugador puede escribir un mensaje y seguir viendo los mensajes recientes mientras el teclado está abierto.
- [ ] **CHAT-02**: El chat se adapta al espacio real dejado por el teclado sin franjas vacías, saltos abruptos ni controles tapados.
- [ ] **CHAT-03**: El jugador puede enviar un mensaje, ocultar el teclado y regresar al gameplay conservando una disposición estable.
- [ ] **CHAT-04**: Los mensajes nuevos permanecen alcanzables y legibles tanto con el teclado abierto como cerrado.

### Lobby

- [ ] **LOBBY-01**: El jugador puede leer y operar los diálogos de tiempos y opciones avanzadas en paisaje compacto sin contenido ni acciones fuera de pantalla.
- [ ] **LOBBY-02**: El jugador distingue y puede usar correctamente los estados de una sala: disponible, llena y en partida.
- [ ] **LOBBY-03**: Los botones para entrar, aplicar, cancelar, agregar o quitar reflejan en texto, apariencia y comportamiento su estado real.
- [ ] **LOBBY-04**: Cuando no existen salas o elementos disponibles, el jugador ve un estado vacío comprensible en lugar de un panel que parece roto.

### Profile

- [ ] **PROF-01**: El jugador puede recorrer el perfil y el modo de edición en un teléfono compacto sin textos, logros, botones o imágenes cortados.
- [ ] **PROF-02**: El jugador puede identificar y tocar con claridad cada elemento editable y regresar desde los selectores actuales al perfil correcto.
- [ ] **PROF-03**: El borrador de edición y el modo actual del perfil sobreviven una recreación normal de la Activity hasta que el jugador guarda o descarta.
- [ ] **PROF-04**: Los estados sin estadísticas, logro seleccionado o contenido definitivo presentan marcadores coherentes y no datos engañosos.

### Navigation and Usability

- [ ] **NAV-01**: El jugador puede recorrer menú, lobby, perfil y gameplay y regresar sin duplicar pantallas ni terminar en una ruta inesperada.
- [ ] **NAV-02**: En gameplay, Atrás cierra primero la capa transitoria visible y solo después abandona la pantalla según una prioridad consistente.
- [ ] **NAV-03**: Los botones visibles de volver, cerrar y cancelar producen el mismo resultado conceptual que el gesto o botón Atrás correspondiente.
- [ ] **USE-01**: Los controles importantes de las cuatro superficies tienen un área táctil utilizable, etiquetas comprensibles y contraste suficiente en estados activos y deshabilitados.

## v2 Requirements

### Extended Device Coverage

- **DEVICE-01**: El jugador puede utilizar todas las pantallas en tablets mediante layouts específicos.
- **DEVICE-02**: El jugador puede utilizar cada pantalla en orientaciones adicionales a las actuales.
- **TEST-01**: El equipo ejecuta automáticamente pruebas instrumentadas de rutas, recreación, teclado y tamaños de ventana en integración continua.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Oráculo, Bufón u otros roles nuevos | Se incorporarán después de estabilizar la interfaz actual |
| Firebase, autenticación y cuentas reales | Pertenecen a una futura etapa online |
| Networking y partidas online reales | Las superficies online permanecen simuladas |
| Compras, banners de pago y galería de fotos | Son funciones nuevas ajenas a este ciclo |
| Migración a Jetpack Compose | Sería una reescritura amplia y riesgosa |
| Reorganización completa de paquetes o arquitectura | No es necesaria para corregir los defectos actuales |
| Compilación o ejecución automatizada durante este ciclo | El usuario realizará la validación en Android Studio |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| GAME-01 | Phase 1 | Pending |
| GAME-02 | Phase 1 | Pending |
| GAME-03 | Phase 1 | Pending |
| GAME-04 | Phase 1 | Pending |
| CHAT-01 | Phase 2 | Pending |
| CHAT-02 | Phase 2 | Pending |
| CHAT-03 | Phase 2 | Pending |
| CHAT-04 | Phase 2 | Pending |
| LOBBY-01 | Phase 3 | Pending |
| LOBBY-02 | Phase 3 | Pending |
| LOBBY-03 | Phase 3 | Pending |
| LOBBY-04 | Phase 3 | Pending |
| PROF-01 | Phase 4 | Pending |
| PROF-02 | Phase 4 | Pending |
| PROF-03 | Phase 4 | Pending |
| PROF-04 | Phase 4 | Pending |
| NAV-01 | Phase 5 | Pending |
| NAV-02 | Phase 5 | Pending |
| NAV-03 | Phase 5 | Pending |
| USE-01 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0

---
*Requirements defined: 2026-06-13*
*Last updated: 2026-06-13 after roadmap creation*
