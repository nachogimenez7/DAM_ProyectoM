# App Traidores

## What This Is

App Traidores es un juego móvil Android de deducción social con ambientación histórica y medieval. Actualmente permite recorrer menús, configurar partidas, usar perfiles personalizables y jugar una simulación local del flujo de una partida; las superficies online todavía funcionan con datos simulados.

Este ciclo de trabajo se concentra en pulir las pantallas de gameplay, lobby, perfil y chat antes de incorporar nuevos roles o servicios reales.

## Core Value

El jugador debe poder recorrer y utilizar las pantallas principales sin contenido cortado, controles confusos, rutas rotas ni pérdida inesperada de estado.

## Requirements

### Validated

- ✓ El jugador puede iniciar y configurar una partida local.
- ✓ El jugador puede recorrer un flujo simulado de gameplay con fases, roles, votación y resultado.
- ✓ El jugador puede abrir y personalizar visualmente un perfil local.
- ✓ El jugador puede consultar y usar las interfaces actuales de lobby y búsqueda de partidas simuladas.
- ✓ La aplicación mantiene una identidad visual histórica basada en fondos ilustrados, tonos oscuros y acentos dorados.

### Active

- [ ] Gameplay, lobby, perfil y chat se muestran correctamente en teléfonos dentro de su orientación actual.
- [ ] Los textos, botones, diálogos y paneles importantes no se cortan, superponen ni salen de la pantalla.
- [ ] Las acciones de navegación, regreso y cierre conservan una pila coherente y no dejan al jugador atrapado.
- [ ] Los controles llenos, deshabilitados, bloqueados o no disponibles comunican claramente su estado.
- [ ] Los estados vacíos necesarios en las superficies actuales tienen una presentación estable y comprensible.
- [ ] El chat permite escribir y leer mensajes recientes mientras el teclado está abierto.
- [ ] Los cambios de perfil en curso no se pierden inesperadamente por recreaciones normales de la pantalla.
- [ ] Las correcciones pueden incluir refactorizaciones internas pequeñas cuando sean necesarias para resolver el defecto de forma segura.

### Out of Scope

- Nuevos roles, incluido Oráculo o Bufón — se agregarán después de estabilizar las pantallas actuales.
- Firebase, autenticación, cuentas y base de datos — pertenecen a una futura etapa online.
- Networking y partidas online reales — los flujos simulados se mantienen durante este ciclo.
- Nuevas funciones de juego, monetización, banners de pago o galería de fotos — este proyecto corrige lo existente.
- Compatibilidad específica con tablets o incorporación de nuevas orientaciones — se priorizan teléfonos y la orientación que cada pantalla ya utiliza.
- Reorganizaciones amplias de paquetes o reescrituras arquitectónicas — aumentarían el riesgo sin ser necesarias para el objetivo actual.

## Context

- La aplicación es un proyecto Android nativo de un solo módulo, escrito principalmente en Kotlin y XML.
- Gameplay y lobby concentran gran parte de la lógica visual y utilizan numerosas dimensiones fijas.
- La navegación se implementa mediante `Intent`, `finish()` y comportamiento de regreso definido por cada `Activity`.
- El chat ajusta su presentación según la visibilidad del teclado y necesita conservar mensajes visibles durante la escritura.
- No existen pruebas instrumentadas o visuales para Activities, navegación, teclado, diálogos y recreación de pantallas.
- El usuario realizará la compilación y la validación visual en Android Studio; el trabajo automatizado no debe compilar la aplicación.

## Constraints

- **Alcance**: Gameplay, lobby, perfil y chat — son las superficies prioritarias antes de sumar roles.
- **Producto**: No agregar funciones nuevas — el objetivo es estabilización visual y de navegación.
- **Dispositivos**: Priorizar teléfonos — las tablets quedan fuera de esta etapa.
- **Orientación**: Mantener la orientación actual de cada pantalla — no se amplía la matriz de compatibilidad.
- **Diseño**: Conservar la identidad medieval y dorada — las correcciones deben integrarse con el estilo existente.
- **Implementación**: Permitir solo refactorizaciones pequeñas y justificadas — se evita transformar la corrección en una reescritura.
- **Verificación**: No ejecutar compilaciones — el usuario validará compilación y apariencia en Android Studio.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Estabilizar antes de añadir nuevos roles | Reduce la acumulación de defectos sobre pantallas frágiles | — Pending |
| Limitar el ciclo a gameplay, lobby, perfil y chat | Mantiene un alcance concreto y de alto impacto | — Pending |
| Priorizar teléfonos y orientaciones actuales | Evita ampliar el trabajo antes de corregir los problemas conocidos | — Pending |
| Admitir refactorizaciones pequeñas | Algunos defectos de estado o navegación no pueden resolverse únicamente en XML | — Pending |
| Mantener online y perfiles con datos simulados | Firebase y backend son proyectos posteriores | — Pending |
| Dejar la compilación y revisión visual al usuario | Respeta el flujo de validación acordado | — Pending |

## Evolution

Después de cada fase se revisarán los requisitos activos, las decisiones y la descripción del producto. Los requisitos solo pasarán a validados cuando la corrección correspondiente haya sido implementada y confirmada visualmente.

---
*Last updated: 2026-06-13 after project initialization*
