# Paridad de gameplay Android / iOS — 2026-10-05

## Cambios implementados en Android

- LISTOS PARA VOTAR usa el botón derecho del panel inferior durante el debate. Conserva la espera inicial de 10 segundos, el contador, el borde verde y CANCELAR para desmarcarse. Se retiró el botón separado y el espacio que reservaba debajo del chat. Se reutiliza la sincronización online existente. Las demás fases mantienen sus acciones; Alcalde y Payador conservan sus habilidades en el control secundario.
- Recuento: candidato con avatar y nombre arriba; dorso, VOTOS: N y sellos de votantes debajo. Alcalde revelado conserva dos sellos y Contrapunto agrega el sello del Payador. La segunda decisión del Alcalde muestra solamente al elegido, sin votos ni sellos anteriores.
- Ajuste posterior pedido por el usuario: quitar la línea dorada debajo del candidato y agrandar el dorso. Tamaño normal 58 × 78 dp; variante densa 40 × 56 dp. Este pedido reemplaza el separador de la propuesta inicial.
- Resultado: EQUIPO GANADOR y EQUIPO PERDEDOR, perdedores compactos con acento #A89A82; avatar de perfil debajo de cada carta, con inicial si no hay foto. Bufón expulsado figura entre ganadores con GANÓ COMO BUFÓN. Desertor muestra su bando final y necesita sobrevivir para ganar. Se corrigió también ese criterio en el historial.
- Eliminados y silenciados reciben textos de observador durante votación y desempate.
- Se agregaron escenarios de vista previa dentro del acceso de depuración existente; no están disponibles en un APK de producción.

## Validación

- `:app:testDebugUnitTest :app:assembleDebug`: 697 pruebas, 80 suites, cero fallos, errores o pruebas omitidas.
- Después del ajuste visual del recuento se volvió a compilar el APK correctamente.
- Emulador Pixel_10: recuento con voto doble y Contrapunto; decisión del Alcalde; pantalla final con Bufón ganador y Desertor eliminado entre perdedores; botón LISTOS → CANCELAR con borde verde → LISTOS.
- Capturas en `docs/evidence/2026-10-05/android-ios-parity/`. La captura del Alcalde precede al ajuste de tamaño del dorso; la captura `recuento-cartas-grandes.png` muestra el diseño actual.

## Pendiente de integración manual

- Jugar una partida online real entre dispositivos para comprobar sincronización, cancelación de readiness y las habilidades de Alcalde/Payador con estos controles.
- Revisar visualmente los carteles de eliminado/silenciado: las pruebas de presentación pasan, pero el escenario local avanza automáticamente antes de obtener una captura estable.
- Revisar escenarios con muchos candidatos y fotos de perfil reales. Las capturas usan iniciales de jugadores ficticios.

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`. Cambios de Android preparados para subir a `main`, incluido Analytics de Claude. El checkout compartido se conserva en `ios-port`, con los cambios locales intactos; antes de continuar, integrar `main` en esa rama sin duplicar el trabajo Android. No se modificaron los archivos iOS de Claude ni se publicó un APK de producción.
