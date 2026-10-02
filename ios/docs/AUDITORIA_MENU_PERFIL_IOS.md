# Auditoría visual y funcional — menú y perfil iOS

1 de octubre de 2026 · rama `ios-port` sobre `1db36b9` · simulador iPhone 17 / iOS 27, Xcode 27.0. Referencia Android: `activity_main.xml`, `activity_profile.xml` y el inventario de [PARIDAD_MENU_PERFIL_IOS.md](PARIDAD_MENU_PERFIL_IOS.md). Las capturas se tomaron con el tamaño de texto por defecto y con el tamaño de accesibilidad máximo (AX5), en el estilo de perfil Abismo Real.

Fuera de alcance: gameplay, Firebase y cuentas (siguen el orden del plan).

## P1 — bloquean accesibilidad o confunden el uso

| # | Hallazgo | Archivo |
|---|---|---|
| 1 | Con AX5 el título «TRAIDORES» se parte como «TRAIDORE / S», JUGAR queda fuera de la primera pantalla y los íconos fijos (música, volver/editar, lápiz del avatar) desbordan sus marcos; el lápiz tapa la cara del avatar. | `Features/Menu/MenuView.swift`, `DesignSystem/TraidoresTheme.swift` (`MenuHeader`), `Features/Menu/MenuDestinations.swift` (cabecera del perfil) |
| 2 | Jerarquía invertida en el perfil: el nombre usa 32 pt fijos con `minimumScaleFactor`, mientras «PERFIL LOCAL» escala; con texto grande el subtítulo queda más grande que el nombre. | `MenuDestinations.swift` (`ProfileView`) |
| 3 | Las opciones «Grande» y «Muy grande» fijan `.xLarge` / `.xxxLarge` y **reducen** el texto de quien usa tamaños de accesibilidad del sistema. | `TraidoresTheme.swift` (`MenuPage`), `MenuView.swift` |
| 4 | `TraidoresButtonStyle` ignora `isEnabled`: botones deshabilitados se ven activos (ANTERIOR en el primer paso del tutorial, ABRIR CORREO con mensaje vacío, foto mientras carga). | `TraidoresTheme.swift` |

## P2 — jerarquía visual, espaciado y legibilidad

| # | Hallazgo | Archivo |
|---|---|---|
| 5 | Encabezados de sección del perfil en `caption` (12 pt) frente a 19 sp en Android, sin rasgo de encabezado para VoiceOver. | `MenuDestinations.swift` (`profileHeading`) |
| 6 | Alineación inconsistente: el panel centra un `VStack` y las notas quedan con sangrías distintas. | `MenuDestinations.swift` |
| 7 | Tarjetas invisibles: la frase y «Última partida» usan el mismo color que el panel, sin borde. | `MenuDestinations.swift` |
| 8 | Los estilos no-clásicos mantienen botones marrones y etiquetas doradas (Estilo, Ver todos los logros, Nombre/Frase). | `MenuDestinations.swift`, `TraidoresTheme.swift` |
| 9 | Edición: campos sin superficie visible ni contador; el texto de ayuda menciona Android. | `MenuDestinations.swift` |
| 10 | Introducciones y títulos de sección de Roles, Ayuda y Opciones van directamente sobre el fondo ilustrado (dorado sobre pergamino, bajo contraste). | `GuideScreens.swift`, `MenuDestinations.swift` (`OptionsView`) |
| 11 | El título «Opciones» no va en mayúsculas como el resto de pantallas; el selector de tamaño de texto no muestra su etiqueta. | `MenuDestinations.swift` (`OptionsView`) |

## P3 — VoiceOver, textos y detalles

| # | Hallazgo | Archivo |
|---|---|---|
| 12 | Fuera de edición, los emotes del perfil son botones sin acción (VoiceOver anuncia «botón»). | `MenuDestinations.swift` |
| 13 | Las estadísticas se leen como «raya, Partidas»; falta una etiqueta «sin datos» combinada. | `MenuDestinations.swift` (`stat`) |
| 14 | Textos visibles que exponen detalles del port: «como en Android», «perfil iOS», «historial de iOS», «Guía de las reglas de Android». | `MenuDestinations.swift`, `GuideScreens.swift` |
| 15 | Contenido heredado de Android sin tildes («Epoca», «ordenes»). Se corrige en el origen Android o en el exportador, no a mano en Swift. | `AndroidMenuReference.swift` (generado) |
| 16 | Las pruebas UI escriben en los `UserDefaults` reales del simulador (nombre «Ignacio XXXX» persistente tras correrlas). | `TraidoresIOSUITests/LocalLobbyUITests.swift` |

Sin hallazgos: Reducir movimiento se respeta en la intro (solo un fundido de opacidad) y en el emote «6 7»; imágenes decorativas ocultas para VoiceOver; destinos de 44 pt en la mayoría de los controles; tema oscuro forzado de forma coherente.

## Bloques

1. **Bloque 1 — hecho (1/10/2026, sin commit):** P1 completo (1–4) y perfil (5–9, 12–14). `MenuTextSize.resolved`, `TraidoresButtonStyle` con estado deshabilitado y colores del estilo, cabecera y menú con íconos fijos y Large Content Viewer, logotipo sin cortes, perfil con encabezados Bree Serif + rasgo header, tarjetas visibles, campos con contador, emotes no interactivos fuera de edición y estadísticas «sin datos» para VoiceOver. Guardado, límites e identificadores sin cambios. Verificado en iPhone 17 con texto por defecto y AX5; 7 pruebas UI de menú/perfil y 29 de Core aprobadas. Tres pruebas (`About`, `Roles`, `Help`) ya fallaban en `1db36b9` porque tocaban el menú durante la intro de Bandido; ahora esperan al botón. Revisión de Codex: un riesgo medio en `ViewThatFits` del campo, resuelto fijando el tamaño de la etiqueta en la variante horizontal.
2. **Bloque 2:** legibilidad de Roles, Ayuda y Opciones (10, 11, 14 restante); estadísticas del perfil apiladas en tamaños de accesibilidad (en AX5 las tres tarjetas quedan desparejas); contenido del menú que pasa bajo la barra de estado; aislamiento de `UserDefaults` en pruebas (16).
3. **Bloque 3:** pasada de VoiceOver en el iPhone 13 y textos heredados (15). Revisar en un ancho de 375 pt los campos y botones largos («ESTILO DEL PERFIL · ABISMO REAL» ocupa cinco líneas en AX5).
