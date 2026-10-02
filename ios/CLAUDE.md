# Claude Code — port iOS de Traidores

Claude es responsable principal del port nativo SwiftUI. Codex (plugin `codex@openai-codex`) actúa como revisor técnico y apoyo puntual. Estas notas complementan `AGENTS.md` y los documentos de `ios/docs/`; ante cualquier diferencia, mandan esos documentos.

## Antes de trabajar

- Leer la sección «Prioridad vigente» de `docs/PLAN_MIGRACION_IOS.md`, `docs/PARIDAD_MENU_PERFIL_IOS.md` y la auditoría más reciente (`docs/AUDITORIA_MENU_PERFIL_IOS.md`). Trabajar solo en el bloque vigente: no adelantar gameplay ni Firebase.
- `git status` primero. Puede haber cambios sin confirmar de otras personas o de Codex: conservarlos y no incluirlos en tus commits. Cambios solo bajo `ios/`; `app/`, recursos originales y `sources/` del proyecto ChatGPT son de solo lectura.
- Comprobar el entorno real (Xcode, simuladores y el iPhone 13 conectado) en lugar de confiar en documentos que pueden describir un entorno anterior.

## Calidad de interfaz

- Objetivo: visualmente excelente, cómoda en iPhone y fiel a Android (`app/src/main/res/layout/*.xml` y las Activities son la referencia). Conservar la identidad: personajes, mapas, fondos, Bree Serif, paleta de `TraidoresTheme` y tono rioplatense («vos», «tocá»).
- Una pantalla no está terminada porque compila. Verificar en simulador con capturas en tamaño de texto por defecto y en AX5 (`xcrun simctl ui booted content_size accessibility-extra-extra-extra-large`, después volver a `large`), revisar etiquetas y rasgos de VoiceOver, contraste y Reducir movimiento.
- Las opciones de texto del menú nunca reducen el tamaño del sistema (`MenuTextSize.resolved`). Íconos de tamaño fijo dentro de marcos de 44 pt con `accessibilityShowsLargeContentViewer()`; los logotipos pueden limitar Dynamic Type, el texto de lectura no.
- Los estilos de perfil pasan `accent` y `surface` a `TraidoresButtonStyle` y a las tarjetas; no volver al marrón clásico dentro de un estilo.
- Los textos visibles no mencionan «Android», «iOS» ni detalles del port.

## Proyecto y pruebas

- `ios/Scripts/generate_xcode_project.py` está desactualizado (al 1/10/2026 borraría archivos existentes del proyecto): no ejecutarlo hasta actualizarlo. Agregar archivos nuevos a mano en `project.pbxproj` (referencia, build file, grupo y fase) y validar con `plutil -lint`.
- Núcleo: `bash ios/Scripts/test_core.sh`. App: scheme `TraidoresIOS`; pruebas UI en `TraidoresIOSUITests` (ejecutar las del bloque tocado; la suite `LocalLobbyUITests` completa tarda ~7 min). Con `-ui-testing` el menú y el perfil usan una suite de `UserDefaults` propia; la partida guardada todavía no. En la mesa, esperar a que los elementos sean tocables (`waitUntilHittable`), no solo a que existan.
- Instalar en el iPhone 13: `Configuration/Local.xcconfig` (ignorado por git) fija el equipo; `xcodebuild … -destination 'id=<UDID>' -allowProvisioningUpdates` y `xcrun devicectl device install app`.
- Commits pequeños con prefijo `ios:`, solo cuando el usuario lo pida.

## Revisión con Codex

- `/codex:review` para revisar cambios concretos; `/codex:adversarial-review <foco>` para cuestionar arquitectura o compatibilidad. Indicar archivos, comportamiento esperado y riesgo específico; una revisión por bloque, sin repetir pruebas ya suficientes.
- No activar el review gate (`/codex:setup --enable-review-gate`).
- `/codex:transfer` crea una sesión nueva de Codex a partir de esta conversación (`codex resume <id>`); no es una sesión compartida en vivo.
