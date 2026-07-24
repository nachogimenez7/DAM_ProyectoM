# Decisiones de arquitectura (ADR)

Registro de decisiones importantes **inferidas del código actual**. Cada una documenta qué se decidió, el contexto y las consecuencias observables. No son propuestas: describen el estado real.

---

## ADR-01 — Navegación por Intents explícitos (sin Navigation Component)

**Decisión:** cada Activity navega con `Intent` explícitos y maneja su propio back.
**Contexto:** app de pocas pantallas, foco en simplicidad y entrega rápida.
**Consecuencias:**
- (+) Simple, sin dependencia extra ni grafo de navegación.
- (−) El grafo de navegación es implícito; no hay invalidación global de una partida iniciada; el comportamiento de back se repite por Activity.

---

## ADR-02 — Estado de partida como `GameSession` inmutable y `Serializable`

**Decisión:** todo el estado de una partida vive en `GameSession` (data class inmutable), que se actualiza con `copy()` y viaja por `Intent`/`Bundle`.
**Contexto:** se necesita pasar el estado entre Activities y persistirlo en `onSaveInstanceState`.
**Consecuencias:**
- (+) Transiciones predecibles, fácil de razonar, encaja con `onSaveInstanceState`.
- (+) `GameEngine` puede ser un reductor puro (sesión → sesión).
- (−) `GameSession` es muy grande (50+ campos); cambios de modelo impactan serialización.
- (−) `Serializable` (no `Parcelable`) es menos eficiente, aceptable para esta escala.

---

## ADR-03 — Motor de reglas centralizado (`GameEngine`) basado en copias

**Decisión:** las reglas (fases, votos, acciones nocturnas, desempates, victorias) se concentran en `GameEngine`, que recibe y devuelve `GameSession`.
**Contexto:** reglas complejas y testeables que deben servir tanto a local como a online.
**Consecuencias:**
- (+) Reglas testeables sin Android (tests JVM).
- (+) Reutilizable: el host online resuelve con el mismo motor.
- (−) `GameEngine` creció a ~2.000 líneas (máquina de estados de facto).

---

## ADR-04 — Catálogo de roles centralizado con localización por mapa

**Decisión:** `RoleCatalog` es la única fuente de roles: claves, equipos, mínimos, mapa exclusivo, nombres localizados por ambientación e historias.
**Contexto:** 3 mapas con nombres distintos para el mismo rol y 3 roles exclusivos.
**Consecuencias:**
- (+) Un solo lugar para reglas de disponibilidad y presentación.
- (−) Acopla presentación (nombres/historias) con datos de dominio en un mismo objeto.

---

## ADR-05 — IA local conversacional propia (`LocalBotAi`)

**Decisión:** implementar la IA de bots a mano (personalidades, memoria, planes de voto, generación de líneas en español) en vez de un motor externo.
**Contexto:** experiencia offline creíble sin red ni dependencias de IA.
**Consecuencias:**
- (+) Funciona offline, sin costos ni latencia.
- (−) ~3.300 líneas con plantillas y gramática española embebidas; difícil de extender y sin cobertura de tests.

---

## ADR-06 — Online sobre Firestore con host autoritativo y `uidTemporal` (sin Auth)

**Decisión:** el multijugador usa Firestore directo desde el cliente; el **host activo** resuelve fases y publica el estado autoritativo; la identidad es un `uidTemporal` local, no Firebase Auth.
**Contexto:** prototipar multijugador rápido sin backend.
**Consecuencias:**
- (+) Multijugador funcional sin servidor propio.
- (−) Seguridad limitada: las reglas validan forma/tamaño pero no autoría real ni frecuencia; sin Auth/App Check/Functions. Explícitamente **experimental**.
- (−) Lógica de handoff de host, recuperación y sincronización añade gates y watchdogs (`Online*`).

---

## ADR-07 — Persistencia local con `SharedPreferences` (namespace único)

**Decisión:** preferencias, perfil e identidad local en `SharedPreferences` bajo `TraidoresPrefs`.
**Contexto:** datos pequeños clave-valor, sin necesidad de base local relacional.
**Consecuencias:**
- (+) Simple y síncrono.
- (−) Sin esquema ni migraciones formales (hay una migración manual de audio en `AudioPreferences`).
- (−) No apto para datos relacionales o históricos (no se persisten partidas locales terminadas).

---

## ADR-08 — Sin base de datos relacional

**Decisión:** no se usa SQLite/Room ni un RDBMS. Persistencia = Firestore (NoSQL) + SharedPreferences.
**Contexto:** los datos online son documentales y los locales son clave-valor.
**Consecuencias:**
- (+) Menos complejidad para el alcance actual.
- (−) Para la materia de Bases de Datos, el DER/Modelo Relacional debe construirse como **diseño propuesto/normalizado** derivado del dominio, no como reflejo de una BD existente. Ver [facultad/08-der-modelo-relacional.md](../facultad/08-der-modelo-relacional.md).

---

## ADR-09 — Aplicación exclusivamente vertical

**Decisión:** todas las Activities, incluidas Lobby, reparto de roles y Gameplay, declaran `screenOrientation="portrait"`. No se mantienen recursos ni ramas alternativas para landscape.
**Contexto:** la interfaz vertical pasó a ser la única experiencia soportada y las variantes apaisadas quedaron obsoletas.
**Consecuencias:**
- (+) Una sola interfaz para diseñar, probar y mantener.
- (+) El manifiesto expresa directamente la orientación efectiva.
- (−) Volver a admitir otra orientación requeriría diseñarla e implementarla nuevamente.

---

## ADR-10 — Chat de gameplay extraído a controller

**Decisión:** el chat de `GameplayMockActivity` se extrae a `GameplayChatController`, con `GameplayMockActivity` actuando como `ChatHost`.
**Contexto:** el chat mezcla UI, teclado, mensajes locales, listener online y reacciones de bots. Mantenerlo dentro del monolito hacía más riesgoso seguir puliendo gameplay.
**Consecuencias:**
- (+) Primer corte concreto del monolito sin cambiar el flujo de partida.
- (+) El controller encapsula feed ambiental, panel expandido, estado abierto/cerrado, no leídos, cooldown online y bots.
- (+) Deja un patrón para extraer luego eventos/narrador u otros subsistemas.
- (−) Sigue acoplado a vistas concretas de gameplay; no es un componente reutilizable fuera de esta pantalla.
