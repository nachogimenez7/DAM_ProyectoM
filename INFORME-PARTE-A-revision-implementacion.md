# REVISIÓN — Implementación de la Parte A por Codex

> Auditoría de solo lectura (jul 2026) sobre el working tree tras la implementación de Codex. Contrasta cada propuesta de `INFORME-PARTE-A-modo-local.md` con el código real. **No se editó ningún archivo del repo.** No se compiló (restricción del ciclo): la verificación es estática, por lectura y grep.

## Veredicto

**Codex implementó la Parte A de forma completa y fiel — y además hizo los "cambios mayores" que el informe dejaba para después.** No solo aplicó los 8 quick wins; también ejecutó la migración ortográfica completa (tildes + cluster SEÑALAR) y creó `GameplayTextMarkers` para desacoplar el texto de los parsers, que era la solución de fondo recomendada en A.4.3. No encontré regresiones. Investigué a fondo los dos puntos donde la migración *podía* romper algo (comparador `== "SENALAR"` y un `.contains` de amanecer sin normalizar) y ambos están correctamente resueltos.

Único pendiente real: **correr los tests JVM en Android Studio** para confirmar verde (no puedo compilar). El resto son notas menores.

---

## La pieza central: `GameplayTextMarkers`

Codex creó [`GameplayTextMarkers.kt`](app/src/main/java/com/traidores/juego/GameplayTextMarkers.kt) con los marcadores como constantes (`DEATH="murió"`, `EXPELLED="fue expulsado"`, `SILENCED="Silenciados"`, etc.) y —clave— un `normalize()` que aplica `Normalizer.Form.NFD` + borra `\p{Mn}` + `lowercase`, más un `contains()` que normaliza **ambos** lados. Esto vuelve a todos los parsers **insensibles a tildes y a la ñ** a la vez que los strings visibles conservan la ortografía correcta.

Detalle fino que hace que todo cierre: NFD descompone `ñ` (U+00F1) en `n` + tilde combinante (U+0303, categoría `Mn`), que se elimina. Por eso `normalize("SEÑALAR")` → `"senalar"`. Esto es lo que salva los dos casos de abajo.

---

## Verificación ítem por ítem (A.5)

| # | Propuesta A.5 | Estado | Evidencia |
|---|---|---|---|
| QW1 | Marcador `"expulso a"` → `"fue expulsado"` | ✅ Hecho | [LocalBotAi.kt:921-923](app/src/main/java/com/traidores/juego/LocalBotAi.kt:921) usa `"fue expulsado"`/`"expulsar a"`/`"expulsó a"`; `latestExpelledTarget` [:2883-2896](app/src/main/java/com/traidores/juego/LocalBotAi.kt:2883) igual + mantiene `"expulso a"` por retro-compat. Revive reacciones a expulsiones y los 3 comportamientos de debate. |
| QW2 | `nextNightMessage` con número de noche | ✅ Hecho | [GameEngine.kt:1613-1618](app/src/main/java/com/traidores/juego/GameEngine.kt:1613): `"Noche ${session.round + 1}: …"` por mapa. Test dedicado en [GameEngineTest.kt:2657-2664](app/src/test/java/com/traidores/juego/GameEngineTest.kt:2657). |
| QW3 | Orden muerte-antes-de-amanecer en `ChronicleFeedPresenter` | ✅ Hecho | [ChronicleFeedPresenter.kt:78-82](app/src/main/java/com/traidores/juego/ChronicleFeedPresenter.kt:78): `DEATH` se evalúa antes que `DAWN`, con exclusión de `"no murio"`/`"nadie murio"`, y `SILENCE` ahora también matchea `"no puede hablar"`. Normaliza vía `GameplayTextMarkers`. |
| QW4 | Mojibake + ortografía | ✅ Hecho | Mojibake: [GameplayMockActivity.kt:6353](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:6353) `"¿Quieres cambiar de bando?"`. `acompaño` [LocalBotAi:1081](app/src/main/java/com/traidores/juego/LocalBotAi.kt:1081), `mañana` [:1118](app/src/main/java/com/traidores/juego/LocalBotAi.kt:1118) y [GameplayMockActivity:4043](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:4043), `zafan` [:1486](app/src/main/java/com/traidores/juego/LocalBotAi.kt:1486), `pequeña`/`señores` en RoleCatalog. Grep de las formas rotas: 0 resultados. |
| QW5 | `Muteados:` → `Silenciados:` | ✅ Hecho | [GameEngine.kt:1609](app/src/main/java/com/traidores/juego/GameEngine.kt:1609). El matcher acoplado mantiene **ambos** por retro-compat: [GameplayTableUi.kt:372-373](app/src/main/java/com/traidores/juego/GameplayTableUi.kt:372) (`"silenciados:"` y `"muteados:"`). |
| QW6 | Reescrituras del cronista (amanecer/votación/expulsión/"amanecer se acerca") | ✅ Hecho | Funciones extraídas y por mapa: `dawnNoDeathMessage`/`dawnDeathMessage` [GameEngine.kt:1551-1571](app/src/main/java/com/traidores/juego/GameEngine.kt:1551), `votingEndedMessage`/`tieEndedMessage` [:1573-1587](app/src/main/java/com/traidores/juego/GameEngine.kt:1573), `expulsionMessage` [:1589-1598](app/src/main/java/com/traidores/juego/GameEngine.kt:1589), `dawnApproachesMessage(session)` [:1543-1549](app/src/main/java/com/traidores/juego/GameEngine.kt:1543). Texto idéntico al propuesto. |
| QW7 | Anacronismos de bots + variantes medievales | ✅ Hecho (parcial en variantes) | `microfono`/`para el clip`: 0 resultados (reemplazadas). Variante nueva `"criando malvas"` en [LocalBotAi.kt:1424](app/src/main/java/com/traidores/juego/LocalBotAi.kt:1424). No agregó *todas* mis variantes opcionales (p. ej. `"no le fio ni una moneda"`), pero eran sugerencias, no bugs. |
| QW8 | Voseo→tuteo del sistema + Detective→investigador (Espía) | ✅ Hecho | `"No puedes actuar…"` [GameplayMockActivity:1229](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:1229), `"Puedes hablar, pero no votar"` [GameEngine:982](app/src/main/java/com/traidores/juego/GameEngine.kt:982), `"el investigador te ve como inocente"` [:960](app/src/main/java/com/traidores/juego/GameEngine.kt:960), y en [RoleCatalog:153](app/src/main/java/com/traidores/juego/RoleCatalog.kt:153) y [GameplayTableUi:417](app/src/main/java/com/traidores/juego/GameplayTableUi.kt:417). |

### Cambios mayores (que el informe dejaba para "después" y Codex ya hizo)
- **Migración ortográfica completa**: víctima, elección, Oráculo, día, Estás, etc., en todo el copy visible. Los parsers no se rompen porque todos pasan por `GameplayTextMarkers.normalize`.
- **Cluster SEÑALAR** (14 sitios) migrado a `ñ` de forma coordinada, con el comparador de tono resuelto (ver abajo).
- **`GameplayTextMarkers` como constantes compartidas** (mitigación A.4.3): productor y parsers comparten la fuente del marcador.

---

## Los dos puntos de riesgo que investigué — ambos SEGUROS

Estos son exactamente los lugares donde una migración así suele romperse en silencio. Los verifiqué de cerca porque un futuro cambio podría reintroducir el bug:

1. **Comparador de tono `== "SENALAR"` vs label `"SEÑALAR"`** — [GameplayTableUi.kt:177-190](app/src/main/java/com/traidores/juego/GameplayTableUi.kt:177). El label visible es `"SEÑALAR"` (con ñ, línea 173), pero el comparador de tono compara contra `"SENALAR"` (sin ñ, línea 190). Funciona porque línea 177 hace `GameplayTextMarkers.normalize(label).uppercase()`: `"SEÑALAR"` → NFD quita la tilde de la ñ → `"senalar"` → `.uppercase()` → `"SENALAR"` → matchea. ✅
2. **`.contains("amanecer: murio $playerName")` sin tilde** — [GameplayTableUi.kt:460-465](app/src/main/java/com/traidores/juego/GameplayTableUi.kt:460). El anuncio real ahora es `"Amanecer: murió X"` (mayúscula + tilde), pero la línea 460 normaliza el anuncio y la 462 normaliza el nombre antes del `contains`, así que compara `"amanecer: murio x"` contra `"amanecer: murio x"`. ✅

Verifiqué también que **no quedan llamadas huérfanas** a `dawnApproachesMessage()` sin argumento (cambió la firma a `(session)`): grep de `dawnApproachesMessage()` → 0 resultados, así que todos los call sites pasan `session` y el proyecto debería compilar en ese punto.

---

## Estado de los tests

Codex tocó `GameEngineTest`, `GameplayTableUiTest`, `RoleCatalogTest` y hay un `ChronicleFeedPresenterTest`. La mezcla es sana:
- **Asserts sobre salida del motor** actualizados a los strings nuevos con tilde: [GameEngineTest.kt:1233/1254/2963](app/src/test/java/com/traidores/juego/GameEngineTest.kt:1233), [GameplayTableUiTest.kt:410-453](app/src/test/java/com/traidores/juego/GameplayTableUiTest.kt:410).
- **Cobertura nueva del fix de número de noche**: [GameEngineTest.kt:2657-2664](app/src/test/java/com/traidores/juego/GameEngineTest.kt:2657).
- **Fixtures de entrada con strings viejos sin tilde** (`"Amanecer: murio Dina."`, `"Muteados: Tomas."`) mantenidos a propósito: prueban que los parsers toleran entrada no acentuada y el marcador legacy. No fallan porque la normalización los cubre.

No detecté ningún assert que espere una salida del motor con string viejo (que sí fallaría). **Aun así, no compilé: correr `./gradlew testDebugUnitTest` en Android Studio es el paso que falta para dar esto por cerrado.**

---

## Notas menores (nice-to-have, no bloqueantes)

- **Acoplamiento no obvio en [GameplayTableUi.kt:190](app/src/main/java/com/traidores/juego/GameplayTableUi.kt:190)**: que `"SEÑALAR"` matchee `== "SENALAR"` depende de que `normalize()` descomponga la ñ. Funciona, pero si alguien cambia `normalize()` para preservar la ñ, se rompe el tono del botón sin aviso. Un comentario de una línea (`// normalize() descompone la ñ → "senalar"`) lo blindaría.
- **Variantes de bots**: quedaron sin agregar algunas de las opcionales de A.2 (color medieval extra). Es contenido, no deuda; se puede sumar cuando se quiera.
- **Implicación online (sin acción)**: la crónica con tildes ahora viaja en `estadoPartida.anuncioPublico`/`historialPublico`. El camino online no parsea ese texto (resuelve por la subcolección `acciones`) y `appendGodEvents` deduplica por string exacto, así que no hay riesgo de acento ahí. Solo tenerlo presente si a futuro se agrega parsing de anuncios en online.

---

## Cierre

La Parte A quedó implementada al 100% de lo accionable, con los cambios mayores incluidos y sin regresiones detectables por lectura. La calidad del trabajo de Codex es alta: centralizó la normalización en vez de parchear string por string, que era justo la recomendación de fondo. Falta únicamente la validación de compilación/tests en Android Studio, que está fuera de mi alcance por la restricción del ciclo.
