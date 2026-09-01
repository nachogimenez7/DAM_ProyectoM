# Documentación — App Traidores

Índice maestro de la documentación del proyecto. La **fuente de verdad es el código**; estos documentos lo describen y se actualizan cuando el código cambia.

> Estado del proyecto: `0.1.17` (versionCode 18). Juego móvil Android de deducción social con modo **local vs IA** y modo **online experimental** sobre Firebase.

## Cómo está organizada

```
docs/
├── README.md                  ← este índice
├── general/                   Documentación general del producto y del sistema
│   ├── 01-vision-objetivos-alcance.md
│   ├── 02-mecanicas.md
│   ├── 03-arquitectura.md
│   ├── 04-tecnologias.md
│   ├── 05-estructura-proyecto.md
│   ├── 06-convenciones-codigo.md
│   ├── 07-flujo-funcionamiento.md
│   └── 08-logros-y-progreso.md
├── desarrollo/                Documentación para desarrolladores
│   ├── guia-nuevos-desarrolladores.md
│   ├── backlog.md
│   └── decisiones-arquitectura.md
└── (documentación técnica previa, ya existente)
    ├── firebase-online-schema.md   Contrato Firestore del online (vigente y fiel al código)
    ├── arquitectura-autoridad-online.md
    │                                Límite de confianza actual y migración hacia backend
    ├── auditoria-optimizacion-seguridad-2026-08-31.md
    │                                Medidas, cambios y riesgos residuales de la revisión actual
    ├── seguridad-online.md         Auditoría de seguridad del online y plan gratuito
    ├── project-structure.md        Estructura rápida (parcialmente desactualizada, ver nota)
    ├── map-exclusive-roles.md      Roles exclusivos por mapa
    ├── banco-futuro-roles-y-personajes-historicos.md
    │                                Ideas para una expansión posterior al lanzamiento
    ├── lan-role-readiness.md       Notas de lectura de roles
    └── discord/                    Material de comunidad/Discord
```

## Documentos de referencia ya existentes y vigentes

- [`firebase-online-schema.md`](firebase-online-schema.md) — Contrato completo de Firestore para el modo online. **Vigente y fiel al código** (revisado contra `OnlineRoomFirestore.kt` y `firestore.rules`). Es la fuente para el DER/Modelo Relacional de la facultad.
- [`arquitectura-autoridad-online.md`](arquitectura-autoridad-online.md) — Frontera extraída para inicio/reparto, límite actual del anfitrión y migración compatible hacia autoridad de servidor.
- [`../ESTADO_ACTUAL.md`](../ESTADO_ACTUAL.md) — Auditoría técnica de solo lectura basada en el código, subsistema por subsistema, con referencias a archivo:línea. Es la base de la sección "estado" del backlog.
- [`seguridad-online.md`](seguridad-online.md) — Auditoría de seguridad del online (jul 2026): hallazgos priorizados, qué se cerró en `firestore.rules`, qué límites tiene el plan gratuito y qué exige Play Store. La implementación pendiente está en [`desarrollo/specs/SPEC-seguridad-y-moderacion-online.md`](desarrollo/specs/SPEC-seguridad-y-moderacion-online.md).
- [`auditoria-optimizacion-seguridad-2026-08-31.md`](auditoria-optimizacion-seguridad-2026-08-31.md) — Revisión actual: tamaño antes/después, rendimiento de arranque, dependencias, reglas y acciones externas pendientes.
- [`play-games-setup.md`](play-games-setup.md) — Estado de la integración de Play Games, datos del proyecto y orden exacto para terminar Play Console y Firebase.
- [`achievement-icon-concepts.md`](achievement-icon-concepts.md) — Dirección visual, símbolos y puntos propuestos para los 10 logros de Play Games.
- [`banco-futuro-roles-y-personajes-historicos.md`](banco-futuro-roles-y-personajes-historicos.md) — Banco de diseño para roles universales, exclusivos, personajes históricos y un posible modo Crónicas. Es material futuro y no modifica el roadmap de estabilización vigente.

## Avisos de documentos desactualizados

- [`project-structure.md`](project-structure.md) menciona archivos que **no existen** en el código actual (`PlayerProfileStore.kt`, `OnlineLobbyModels.kt`, `OnlineLobbyStore.kt`) y describe el online como "simulado/local". Ver [05-estructura-proyecto.md](general/05-estructura-proyecto.md) para la estructura corregida.

## Por dónde empezar

- ¿Sos nuevo en el código? → [desarrollo/guia-nuevos-desarrolladores.md](desarrollo/guia-nuevos-desarrolladores.md)
- ¿Querés entender el juego? → [general/02-mecanicas.md](general/02-mecanicas.md)
- ¿Querés entender los logros? → [general/08-logros-y-progreso.md](general/08-logros-y-progreso.md)
- ¿Querés saber qué falta? → [desarrollo/backlog.md](desarrollo/backlog.md)

> La documentación para la facultad (Análisis de Sistemas / Bases de Datos: casos de uso, modelo de dominio, DER/relacional, diccionario de datos) se movió fuera del repo a `Facultad/Objetos/App Traidores - Analisis y BD/`.
