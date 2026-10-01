# Paridad de menú y perfil — Android → iOS

Objetivo solicitado: cubrir las pantallas y comportamientos completos de Android, sin versiones resumidas. Gameplay se mantiene fuera de este bloque. Base Android: `origin/main` integrado hasta `966c7e6`.

## Implementado y comprobable sin servicios online

- Menú con navegación a modos, roles, ayuda, opciones, perfil, Acerca de y comentarios; avatar del perfil reflejado en la cabecera.
- Roles: tres mapas, nueve personajes por mapa, imágenes, historias, funciones, mínimos de jugadores, equipo y detalle completo.
- Ayuda: diez secciones desplegables, consejos por rol y tutorial de cuatro pasos repetible.
- Opciones: música, sonido de inicio Bandido, volúmenes persistentes e independientes, tamaño de lectura del menú/guías, vista previa, restablecimiento con confirmación y enlaces de soporte/privacidad.
- Perfil: banner de 112 puntos con avatar superpuesto de 112 puntos; nombre, estado de identidad, frase, estadísticas, rol favorito, cuatro emotes, logros destacados, última partida y cuenta.
- Modo de edición separado, botón superior editar/terminar y límites Android (20 para nombre, 40 para frase). Guardado automático, incluso al volver al menú sin terminar la edición; un nombre vacío conserva el nombre anterior a la edición.
- Avatar y rol favorito: selector filtrado por mapa, 27 retratos y selección señalada; ampliación del avatar y ficha del rol fuera de edición.
- Seis banners originales Android.
- Cuatro estilos de perfil: Clásico, Espacial, Abismo Real y Forja Infernal. Fondos Android y paletas de perfil; no modifican gameplay.
- Foto del iPhone mediante selector nativo de Fotos, avatar comprimido a un máximo de 512 píxeles, guardado automático local. Sin subida remota. Sin acceso general a la biblioteca ni solicitud de permiso de cámara.
- Catálogo completo de 20 emotes, filtros Clásicos/Memes/Legendarios, exactamente cuatro elecciones distintas. La selección se guarda automáticamente cuando está completa; con menos de cuatro se conserva la última completa. El emote «6 7» usa la secuencia Android de cuatro fotogramas a 110 ms, sin bucle y respetando Reducir movimiento.
- Catálogo de diez logros con nombre, descripción, rareza y estado pendiente. Ningún logro se concede por ver el catálogo.

Estilos, avatar, banner y rol favorito se guardan al elegirlos, sin confirmación adicional. Los campos de texto se guardan mientras se editan y al salir.

- Inicio Bandido Games en cada arranque real: tres fotogramas originales, entrada de 420 ms, ladrido a los 620 ms, destello dorado y salida al menú a los 2,2 s. Volver desde una pantalla o reactivar la app no repite el inicio. Música del menú después de la presentación; ladrido con interruptor/volumen independiente, respeta silencio y Reducir movimiento.

## Diferencias pendientes: NO considerar el port terminado

| Bloque | Comportamiento Android que falta conectar |
| --- | --- |
| Identidad y cuenta | Firebase Auth, invitado con alias cerrado/número estable, registro y acceso por correo, recuperación, vinculación Google, sincronización del perfil, cierre de sesión y eliminación con reautenticación. No existe `GoogleService-Info.plist` en este checkout. El perfil actual se identifica como local, no como una cuenta ni como invitado online. |
| Restricciones de invitado | Android deja cambiar alias y bloquea personalización sin registro. iOS permite preparar un perfil local para probar la interfaz; esa identidad no se publica online. Aplicar las restricciones al conectar Auth, sin convertir el perfil local en una cuenta ficticia. |
| Fotos | Captura con cámara, elección/recorte ajustable y foto remota de la cuenta Google. Fotos nativo y recorte circular local ya disponibles. |
| Estadísticas e historial | Conectar resultados reales, porcentaje y lista/detalle de partidas. Las cifras se muestran como no disponibles; no se declara que el jugador no haya jugado si el historial todavía no está conectado. |
| Logros destacados | Seguimiento real, desbloqueo, fecha, progreso y elección de hasta tres obtenidos. El catálogo informativo completo está disponible. |
| Audio | Efectos/confirmaciones y volumen de voces; no presentar interruptores que no afectan a ningún reproductor. |
| Vibración y efectos | Preferencia de vibración, demostración háptica y efectos visuales reducidos a nivel app. El emote animado ya respeta Reducir movimiento del sistema. |
| Notificaciones | Permiso iOS, política y entrega real. No crear avisos periódicos ni activar permisos durante las pruebas sin una acción del usuario. |
| Idioma | Android actualmente fuerza español y oculta el selector hasta completar traducciones. No añadir un selector inglés incompleto. |
| Online previo al juego | Crear/buscar sala, ingresar código, lobby y recuperación compatibles con el backend, sin adelantar gameplay online. |

## Verificación

Pruebas UI cubren edición/guardado/reinicio, selectores por mapa, estilo, carga de cuatro emotes, catálogo de logros, roles por mapa y ayuda/tutorial. La importación de Fotos necesita además revisión del selector nativo y una imagen de prueba; cámara/cuenta no se presentan como verificadas.

2026-09-30: cuatro pruebas UI aprobadas en iPhone 17 / iOS 27: persistencia automática al salir/reiniciar (nombre, avatar, banner, favorito), nombre vacío, estilos/emotes/logros y opciones de volumen/restablecimiento. Inicio capturado en simulador; audio original incluido en el bundle y reproductores preparados en una cola independiente para no bloquear la animación. La grabación del simulador no captura audio.

Las fuentes Android son la referencia ejecutable: `ProfileActivity.kt`, `activity_profile.xml`, `ProfileSelectionActivity.kt`, `ProfileRoleCatalog.kt`, `ProfileCustomizationCatalog.kt`, `CosmeticPilot.kt`, `EmoteCatalog.kt`, `OpcionesActivity.kt`, `GuestIdentity.kt`, `RolesActivity.kt` y `RoleDetailDialog.kt`.
