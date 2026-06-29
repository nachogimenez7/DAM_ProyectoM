# Documentación — App Traidores

Índice maestro de la documentación del proyecto. La **fuente de verdad es el código**; estos documentos lo describen y se actualizan cuando el código cambia.

> Estado del proyecto: `0.1.0-alpha` (versionCode 1). Juego móvil Android de deducción social con modo **local vs IA** (estable) y modo **online experimental** sobre Firebase Firestore (en desarrollo).

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
│   └── 07-flujo-funcionamiento.md
├── desarrollo/                Documentación para desarrolladores
│   ├── guia-nuevos-desarrolladores.md
│   ├── backlog.md
│   └── decisiones-arquitectura.md
└── (documentación técnica previa, ya existente)
    ├── firebase-online-schema.md   Contrato Firestore del online (vigente y fiel al código)
    ├── project-structure.md        Estructura rápida (parcialmente desactualizada, ver nota)
    ├── map-exclusive-roles.md      Roles exclusivos por mapa
    ├── lan-role-readiness.md       Notas de lectura de roles
    ├── gameplay-vertical-draft.md  Borrador de gameplay vertical (no activo)
    └── discord/                    Material de comunidad/Discord
```

## Documentos de referencia ya existentes y vigentes

- [`firebase-online-schema.md`](firebase-online-schema.md) — Contrato completo de Firestore para el modo online. **Vigente y fiel al código** (revisado contra `OnlineRoomFirestore.kt` y `firestore.rules`). Es la fuente para el DER/Modelo Relacional de la facultad.
- [`../ESTADO_ACTUAL.md`](../ESTADO_ACTUAL.md) — Auditoría técnica de solo lectura basada en el código, subsistema por subsistema, con referencias a archivo:línea. Es la base de la sección "estado" del backlog.

## Avisos de documentos desactualizados

- [`project-structure.md`](project-structure.md) menciona archivos que **no existen** en el código actual (`PlayerProfileStore.kt`, `OnlineLobbyModels.kt`, `OnlineLobbyStore.kt`) y describe el online como "simulado/local". Ver [05-estructura-proyecto.md](general/05-estructura-proyecto.md) para la estructura corregida.

## Por dónde empezar

- ¿Sos nuevo en el código? → [desarrollo/guia-nuevos-desarrolladores.md](desarrollo/guia-nuevos-desarrolladores.md)
- ¿Querés entender el juego? → [general/02-mecanicas.md](general/02-mecanicas.md)
- ¿Querés saber qué falta? → [desarrollo/backlog.md](desarrollo/backlog.md)

> La documentación para la facultad (Análisis de Sistemas / Bases de Datos: casos de uso, modelo de dominio, DER/relacional, diccionario de datos) se movió fuera del repo a `Facultad/Objetos/App Traidores - Analisis y BD/`.
