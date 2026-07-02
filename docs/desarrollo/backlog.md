# Backlog / Tareas pendientes detectadas

Detectadas a partir del código y de [`../../ESTADO_ACTUAL.md`](../../ESTADO_ACTUAL.md). No son funcionalidades inventadas: son brechas, deuda técnica o trabajo parcial **observado en el código**.

> Clasificación de estado: **Parcial** = implementado pero incompleto; **Deuda** = funciona pero con riesgo/calidad a mejorar; **Faltante** = no existe.

## Funcionalidad parcial (en desarrollo)

| # | Ítem | Estado | Detalle |
|---|---|---|---|
| F1 | **Espía** | Completo | Traidor que **elige la víctima junto a los Asesinos** cada noche (comparte la fase del Asesino) y aparece **inocente** ante la investigación. Si caen todos los Asesinos sigue matando (sucesión automática). Estilo "Padrino". |
| F2 | **Bufón** | Parcial | Tiene victoria especial por expulsión, pero **sin acción nocturna propia**. Su rol depende del comportamiento social. |
| F3 | **Modo online** | Parcial / Experimental | Crear/buscar sala, presencia, reconstrucción y sync por fases funcionan, pero sin Auth, App Check, Cloud Functions ni limpieza automática. Ver `firebase-online-schema.md` → "Límites actuales". |
| F4 | **Selector de idioma** | Parcial | Persiste `language` en preferencias pero **no cambia los textos** (no hay i18n real). |
| F5 | **Reparto online** | Parcial | Usa preset seguro reducido (sólo Policía/Médico/Asesino/Aldeanos). Roles especiales no disponibles online. |

## Deuda técnica

| # | Ítem | Estado | Detalle |
|---|---|---|---|
| D1 | **`GameplayMockActivity.kt` (~6.300 líneas)** | Deuda / En reducción | Ya se extrajo el chat a `GameplayChatController`. Sigue concentrando fases, timers, online y animaciones; continuar con extracciones incrementales. |
| D2 | **`LobbyActivity.kt` (~2.400 líneas)** | Deuda | Construcción programática de diálogos dificulta consistencia visual. |
| D3 | **Strings hardcodeados en español** | Deuda | `values/strings.xml` cubre poco; mucho texto vive en Kotlin/XML. Bloquea i18n (F4). |
| D4 | **Sin logging estructurado** | Deuda | Sólo `OnlineDebugLog` puntual; no hay estrategia `Log.d/e`. |
| D5 | **Nombre `GameplayMockActivity`** | Deuda | "Mock" es engañoso: hoy es la pantalla real de gameplay (local y online). |
| D6 | **Doc desactualizada** | Deuda | `docs/project-structure.md` referencia archivos inexistentes (`PlayerProfileStore.kt`, `OnlineLobbyModels.kt`, `OnlineLobbyStore.kt`) y describe online como simulado. Reemplazada por `general/05-estructura-proyecto.md`. |
| D7 | **Muchas dimensiones fijas en `activity_gameplay_mock.xml`** | Deuda | Riesgo de desborde con fuentes grandes / pantallas chicas. |
| D8 | **Limpieza de salas Firestore** | Deuda | Manual; sin TTL ni Cloud Function programada. |

## Faltante (fuera del alcance actual, registrar para futuro)

| # | Ítem | Estado | Detalle |
|---|---|---|---|
| N1 | Firebase Auth real | Faltante | Reemplazar `uidTemporal` por identidad autenticada. |
| N2 | App Check | Faltante | Reducir clientes no autorizados. |
| N3 | Cloud Functions | Faltante | Validación de frecuencia, resolución centralizada de sala llena, limpieza. |
| N4 | Reglas Firestore por rol/host/estado | Faltante | Hoy validan forma/tamaño, no autoría real ni frecuencia. |
| N5 | Tests de UI / instrumentación / accesibilidad | Faltante | Sólo hay tests JVM de reglas y online. |
| N6 | Soporte tablets | Faltante | Excluido del alcance actual. |

## Riesgos a vigilar (de la auditoría)

- **Volver atrás desde gameplay** puede terminar la Activity dejando pantallas previas en el stack; no hay invalidación global de partida iniciada.
- **`onStop` del lobby online** marca desconectado salvo transición controlada; Android puede generar estados ambiguos.
- **Desempate con Alcalde muerto:** verificar que `ALCALDE_DESEMPATE` sólo se alcance con Alcalde vivo y revelado.
- **Cadenas de animación:** si un listener no dispara, animaciones siguientes podrían no iniciar.
- **Limpieza de listeners Firestore** en `GameplayMockActivity`: confirmar desuscripción en el ciclo de vida.

## Sugerencia de orden de ataque

1. Estabilización visual/navegación de gameplay/lobby/perfil/chat (objetivo del ciclo actual).
2. Corrección de documentación desactualizada (D6, este backlog).
3. Extracción incremental de responsabilidades en `GameplayMockActivity` (D1) con tests.
4. Completar jugabilidad de Bufón (F2) si vuelve al alcance. (Espía F1 resuelto: co-ejecutor + inocente, estilo "Padrino".)
5. Endurecer online (N1–N4) cuando se priorice multijugador real.
