# Reparto propuesto: fotos y online — 3 de octubre de 2026

Actualización del 4 de octubre: este documento conserva el plan inicial. Los adaptadores de cuenta y perfil iOS ya están implementados; las salas y la subida de fotos iOS siguen pendientes. El estado del cierre y el próximo trabajo están en `cierre-jornada-2026-10-04.md`; los detalles de cuenta, en `cuentas-google-apple.md`.

Pedido: foto de perfil de cada ganador debajo de su rol en la ventana final (tanto si el jugador ganó como si perdió). Android e iOS usan ahora un retrato circular de 24 dp/pt, antes de 32, manteniendo carta → nombre → rol → foto. Sin foto se conserva un respaldo. Los bots no heredan la foto humana aunque sus nombres coincidan.

Este documento propone el reparto; no se enviaron mensajes a Claude ni se inició otra tarea. Las modificaciones de fotos están sin commit y deben conservarse.

Orden confirmado por el usuario: preparar y probar primero; dejar la comprobación de la tarjeta y el alta de Cloud/Blaze para cuando se puedan hacer pruebas reales. No iniciar ahora la prueba de 90 días.

Tras el plan entregado por Claude, Codex creó las interfaces Swift y dejó su revisión en `ios/docs/REVISION_CODEX_ONLINE_2026-10-03.md`. Ese documento y los archivos `Platform/Online/OnlineModels.swift`, `OnlineServices.swift` y `OnlineContract.swift` sustituyen las interfaces propuestas en el primer plan. Los adaptadores Firebase reales siguen pendientes.

## Codex

Responsable de Android y archivos compartidos: `app/**`, `firebase.json`, `storage.rules`, `firestore.rules`, pruebas de reglas y documentación del contrato.

Próximo bloque: comprobar subida/descarga y recuperación de fotos con el emulador, errores de red, reemplazo y eliminación; revisar tamaño, caché y publicación. Preparar contratos y capa de servicios Firebase para iOS en `ios/TraidoresIOS/TraidoresIOS/Platform/Online/` cuando se acuerde ese bloque. No activar Cloud/Billing ni desplegar al proyecto real durante esta preparación.

Formato compartido vigente: Firebase Auth UID identifica al dueño; `fotoPerfil` es la URL de la foto en el documento público y el jugador de sala. Ver `fotos-perfil-storage-beta.md` y `firebase-online-schema.md`. La referencia se conserva al entrar a una partida; no se copia el archivo por partida. No inventar un segundo backend para iOS.

## Claude

Responsable de vistas SwiftUI en `ios/TraidoresIOS/TraidoresIOS/Features/` y de registrar nuevos archivos/dependencias en `project.pbxproj`. No regenerar el proyecto con el script antiguo ni modificar Android o las reglas Firebase.

Primer bloque acotado: preparar recorrido iOS de acceso a cuenta, perfil público y crear/ingresar sala/lobby contra los contratos de servicios acordados. Entregar estados de carga, desconexión, error y reintento y las fotos del roster. El gameplay online completo va después de probar este recorrido entre plataformas; no basta con quitar el rótulo «PRÓXIMAMENTE».

Conservar los cambios de foto local ya realizados en `MenuDestinations.swift`, `LocalGameView.swift`, `VoteCeremonyView.swift` y `MatchResultView.swift`. En resultados mantener carta → nombre → rol → foto de 24 pt. No volver a introducir la selección de foto de Google Play Games.

Antes de que ambos implementen, Codex define interfaces de servicios y modelos; Claude confirma qué necesitan las vistas. Un solo responsable registra archivos y paquetes en el proyecto de Xcode. Nada de commits o reemplazos de archivos ajenos sin pedido del usuario.

## Contrato de cuenta y fotos para las vistas

Este contrato define los datos y comportamientos compartidos. No significa que los servicios Firebase de iOS ya estén implementados.

| Dato de presentación | Campo compartido | Regla |
| --- | --- | --- |
| Identidad del jugador | `uidTemporal` y ID del documento | UID de Firebase Auth; no usar nombre, correo ni `publicId` como dueño del archivo. |
| Número de perfil | `publicId` | Cadena numérica sin `#`; las vistas agregan el símbolo. No editable. |
| Nombre | `nombrePerfil` | Hasta 18 caracteres. |
| Foto publicada | `fotoPerfil` | URL de descarga; ausencia o cadena vacía significa sin foto de galería publicada. |
| Avatar de respaldo | `avatarPerfil` | Clave del catálogo de ilustraciones. |
| Foto antigua | `fotoPlayGames` | Solo compatibilidad de lectura; no mostrar una opción nueva para elegirla. |

- Cuenta: distinguir sin sesión, invitado y cuenta registrada. Vincular correo/contraseña a un invitado conserva su UID. Entrar en una cuenta existente recupera su UID, número y perfil antes de mostrar sus datos; no asociar la foto local de la cuenta anterior al nuevo UID.
- Leer perfil: recuperar `perfiles_publicos/{uid}` al entrar con una cuenta existente. Una foto publicada debe poder mostrarse aunque el nuevo dispositivo no tenga el archivo de galería original.
- Elegir foto: mostrar la selección local inmediatamente y marcarla pendiente. La publicación recibe un JPEG cuadrado de hasta 512 px, sin EXIF y de hasta 256 KiB. El selector y la vista no contienen código Firebase.
- Publicar: subir a `profilePhotos/{uid}/avatar_{sha256}.jpg`, obtener su URL y guardar `fotoPerfil` con timestamp de servidor. Mostrar éxito remoto después de confirmar la escritura del perfil. Si falla, conservar la selección local y permitir reintento; la última foto publicada sigue siendo la referencia remota.
- Quitar foto: publicar `fotoPerfil = ""` y volver al avatar ilustrado. La limpieza del objeto anterior ocurre después de confirmar el cambio del perfil.
- Sala: copiar `fotoPerfil` al documento `partidas/{sala}/jugadores/{uid}` al publicar el perfil del jugador. El roster entrega esta referencia a mesa, votos y resultados; las vistas no hacen una consulta adicional de perfil por jugador.
- Carga de imagen: mientras se descarga o si falla, mostrar el avatar de respaldo. Cancelar/ignorar respuestas antiguas al reutilizar una vista para otro jugador. En resultados, la foto circular de 24 pt aparece debajo del rol.

Estados que deben poder representar las vistas: sin foto, selección local pendiente, publicando, foto publicada y error con reintento. Una operación que termina tras un cambio de cuenta no puede actualizar la presentación de la cuenta nueva.

## Secuencia conjunta

1. Congelar contrato de cuenta/perfil/sala y los estados que consumirán las vistas.
2. Codex implementa servicios/Android y pruebas locales; Claude implementa presentación iOS usando esos servicios. Trabajar en carpetas distintas.
3. Integrar en el mismo checkout y revisar perfil → sala → partida → votación → resultado, primero local/emulado.
4. Cuando las pruebas reales estén listas, activar la prueba Cloud y Storage y validar dos cuentas. Empezar con Android ↔ Android si iOS aún no tiene cuenta/perfil/lobby; sumar Android ↔ iOS al integrar esos servicios. No hace falta completar todo el gameplay online de iOS para comprobar Storage. Decidir la fecha del alta según estas pruebas, para aprovechar los 90 días.

## Encargo listo para copiar a Claude

“Leé `docs/reparto-codex-claude-fotos-online.md`, incluido el contrato de cuenta y fotos, y `docs/fotos-perfil-storage-beta.md`. Prepará un plan acotado para las pantallas iOS de cuenta, perfil público y crear/ingresar sala/lobby. Conservá los cambios actuales de fotos locales, incluida la foto de ganadores debajo del rol, a 24 pt. Codex será responsable del backend, Android, reglas Firebase y capa de servicios; antes de implementar las vistas, confirmá los datos y operaciones que necesitás. Primero preparamos y probamos en local; Cloud/Blaze se activa al llegar a las pruebas reales. No toques Android, las reglas, la facturación ni regeneres el proyecto Xcode. Indicá archivos a modificar y pruebas de interfaz necesarias.”
