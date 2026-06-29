# Visión, Objetivos y Alcance

## Visión del proyecto

**App Traidores** es un juego móvil Android de **deducción social** con ambientación histórica y medieval. El jugador participa de partidas donde un bando oculto de **Traidores** intenta eliminar al **Pueblo**, mientras el Pueblo debe descubrir y expulsar a los traidores mediante debate y votación.

El proyecto busca ofrecer una experiencia jugable completa de una partida de deducción social en el teléfono, con:

- Una **simulación local contra IA** (bots conversacionales) totalmente jugable y offline.
- Un **modo online experimental** sobre Firebase Firestore para jugar entre varios dispositivos.
- Una identidad visual propia: paneles marrón oscuro, acentos dorados, tipografías personalizadas y arte de mapas según tres ambientaciones (Pampa argentina 1915, Antigua Grecia, Feudo Medieval).

> **Valor central (Core Value):** el jugador debe poder recorrer y utilizar las pantallas principales sin contenido cortado, controles confusos, rutas rotas ni pérdida inesperada de estado.

## Objetivos

### Objetivos del producto

1. Permitir jugar una partida completa de deducción social contra IA, de principio a fin (reparto de roles → noches → debate → votación → resultado).
2. Ofrecer configuración de partida flexible: cantidad de jugadores (5–15), mapa, tiempos por fase, composición de roles y modo de lectura de roles.
3. Brindar 11 roles con mecánicas diferenciadas y 3 roles exclusivos por mapa.
4. Proveer perfil de jugador personalizable (nombre, avatar, banner, rol favorito, biografía) con persistencia local.
5. Habilitar de forma experimental partidas online multi-dispositivo (crear/buscar sala por código, presencia, sincronización de fases).

### Objetivos del ciclo de trabajo actual

Según [`CLAUDE.md`](../../CLAUDE.md), el ciclo actual se concentra en **estabilización visual y de navegación** de las superficies prioritarias: **gameplay, lobby, perfil y chat**, antes de incorporar nuevos roles o servicios reales.

## Alcance

### Dentro del alcance

- Plataforma **Android** (minSdk 24, targetSdk 34), un único módulo `:app`.
- **Teléfonos** como dispositivo prioritario.
- Modo **local vs IA**: completo y estable.
- Modo **online**: experimental, con las limitaciones documentadas en [`firebase-online-schema.md`](../firebase-online-schema.md).
- Superficies prioritarias de pulido: gameplay, lobby, perfil, chat.
- Persistencia local mediante `SharedPreferences` (namespace `TraidoresPrefs`) y serialización de la sesión de juego vía `Intent`/`Bundle`.

### Fuera del alcance (estado actual)

- **Tablets** (quedan fuera de esta etapa).
- Ampliar la matriz de orientación de cada pantalla.
- Agregar funciones nuevas de producto (el foco es estabilización).
- **Backend autoritativo completo, Firebase Auth, App Check y Cloud Functions** para online (pendiente; hoy se usa `uidTemporal` sin login real).
- Internacionalización real: hay un selector de idioma en Opciones, pero la mayoría de los textos están hardcodeados en español (ver [Backlog](../desarrollo/backlog.md)).
- Testing de UI/instrumentación, screenshot testing y accesibilidad automatizada.

### Funcionalidades parcialmente implementadas (en desarrollo)

- **Modo online (Firestore):** crear/buscar sala, presencia, reconstrucción de partida y sincronización por fases existen, pero sin autenticación, sin validación de frecuencia y sin limpieza automática de salas. Marcado explícitamente como experimental en el código y en la documentación.
- **Espía:** modelado y considerado traidor/"killer" (aparece inocente ante la investigación), pero **no tiene fase ni acción propia**; comparte el asesinato con el Asesino.
- **Bufón:** tiene victoria especial por expulsión, pero **no tiene acción nocturna propia**.
- **Selector de idioma:** persiste la preferencia pero no cambia los textos de la app.

> Para el detalle subsistema por subsistema con referencias a archivo:línea, ver [`../../ESTADO_ACTUAL.md`](../../ESTADO_ACTUAL.md).
