# Convenciones de código

Convenciones observadas en el código actual. Reflejan la práctica real, no un estándar externo impuesto.

## Nomenclatura

- **PascalCase** para clases, objetos, enums y data classes.
- Sufijos por rol del archivo: Activities terminan en `Activity`; adapters en `Adapter`; coordinadores de animación en `Animator`.
- **camelCase** para funciones, campos y locales.
- Recursos en **lowercase snake_case** (`bg_player_avatar_offline`, `rol_detective_griego`).
- Constantes en **UPPER_SNAKE_CASE** dentro de `companion object`.
- Orquestación de UI: prefijos `show*`, `render*`, `update*`, `handle*`, `toggle*`, `resolve*`.
- Helpers de consulta/guarda: prefijos `is*`, `can*`, `should*`, `needs*`.
- `lateinit var` común para vistas ligadas a la Activity.

## Estilo

- Kotlin official style (habilitado en `gradle.properties`).
- Indentación de 4 espacios; comas finales (trailing commas) en declaraciones multilínea.
- Atributos XML generalmente uno por línea.
- No hay formatter/lint adicional comprometido más allá de los defaults de Android/Kotlin.
- Muchos **strings en español hardcodeados** en Kotlin y XML; `values/strings.xml` cubre sólo un subconjunto. Al tocar una pantalla, preferir mover texto repetido/accesible a recursos en lugar de duplicar.

## Organización de imports

1. Imports del framework Android.
2. Imports AndroidX.
3. Librería estándar Kotlin/Java al final.
- Sin alias de paths ni módulos barrel.

## Diseño de funciones

- Guard clauses seguidas de actualizaciones de estado inmutable vía `copy()`.
- Reglas puras extraídas a `GameEngine`, `GameplayTableUi` o helpers de geometría.
- Construcción dinámica de vistas en renderers/adapters cuando es práctico.
- **Métodos largos** y de múltiples responsabilidades concentrados en `GameplayMockActivity.kt` y `LobbyActivity.kt`.

## Manejo de errores

- Early returns ante estado/acción inválidos.
- `Toast` para feedback recuperable; `AlertDialog` para confirmación y edición de valores.
- Fallbacks seguros cuando falta un extra de `Intent` o un drawable (`resources.getIdentifier()` con placeholder).
- Evitar `!!` (non-null assertions); preferir guards y `lateinit`.
- Cada Activity gestiona su propio click y comportamiento de back; no hay abstracción central de rutas.

## Visibilidad y módulos

- Mayoría de helpers como `object` o clase simple.
- `internal` para detalles de implementación testeables (p. ej. `GameplayFeedbackState`).
- Preferir transformaciones inmutables del estado de juego; las banderas de UI de Activity son mutables y sensibles al ciclo de vida.

## Convenciones de UI/XML

- Botones compartidos `BtnGold` y `BtnDark` desde `themes.xml`.
- Sistema visual: paneles marrón oscuro, bordes/acentos dorados, fuentes personalizadas, arte de mapas.
- Targets táctiles típicos de 44dp; el trabajo nuevo debería alcanzar ≥48dp donde el layout lo permita.
- Evitar agregar anchos/altos fijos a `activity_gameplay_mock.xml` (ya contiene muchas dimensiones fijas).
- Usar `ScrollView`/RecyclerView o constraints responsivas para pantallas que puedan desbordar con fuentes grandes o pantallas chicas.

## Logging

- No hay framework de logging de aplicación ni estrategia consistente de `Log.d/e`.
- Existe `OnlineDebugLog` para depuración puntual del online.
- Preferir verificar correcciones visuales con casos de prueba/capturas, no con logs temporales en producción.

## Comentarios

- Escasos; suelen identificar una sección de UI o explicar una excepción de negocio.
- Comentar sólo cálculos de layout no obvios, workarounds de ciclo de vida y orden de estado.
- No narrar binding básico ni asignaciones triviales.

## Idioma del dominio

- Terminología de negocio en español: fases (`REPARTO`, `AMANECER`, `VOTACION`), roles (`asesino`, `medico`), campos Firestore (`jugadoresEsperados`, `codigoSala`), y "Dios" como persona narradora de los mensajes del sistema.
