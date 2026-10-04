# Cierre de jornada — 4 de octubre de 2026

## Trabajo que se guarda

- Android y Firebase compartido (`main`): preparación de fotos de galería publicadas en `fotoPerfil`, recuperación de la referencia y presentación en salas, partida, votaciones y resultados. Storage sigue desactivado por defecto hasta configurar y probar el bucket real. Reglas y pruebas de Storage/Firestore preparadas; no desplegadas en producción.
- La autoridad de partida Android omite jugadores con `puedeArbitrar = false`, como los clientes iOS. La comprobación de acceso consulta el servidor; una lectura del caché no confirma el acceso.
- iOS (`ios-port`): cuenta y perfil con adaptadores Firebase reales, vinculación/recuperación por Google, Apple y correo, número público compartido, recuperación y edición del perfil en Firestore. Los botones de cuenta de Perfil y Online usan fondo oscuro.
- Apple permanece deshabilitado hasta disponer de un equipo del Apple Developer Program, configurar la capacidad y la firma, y probar la autorización nativa. Los escenarios falsos se reservan para pruebas explícitas de interfaz en Debug.

Detalles del bloque de cuentas: `docs/cuentas-google-apple.md`. Fotos: `docs/fotos-perfil-storage-beta.md`.

## Verificación realizada

- 37 pruebas puntuales Android: `OnlineLobbyRulesTest` y `OnlineMatchProfileResolverTest`, aprobadas. La última corrida recompiló el cambio de acceso contra el servidor.
- Contratos iOS: `ios/Scripts/test_online_contracts.sh`, aprobados con concurrencia estricta y advertencias como errores.
- Compilación iOS Debug para simulador aprobada tras la revisión final; Release se había compilado durante el bloque de cuentas.
- Cuenta y perfil mediante los SDK reales contra Auth/Firestore emulados: aprobados. Interfaz compartida de cuenta desde Perfil/Online y auditoría de contraste/accesibilidad: aprobadas.
- La selección de una cuenta Google real y la autorización nativa Apple son pruebas externas pendientes; los tokens locales de los emuladores no las sustituyen.

## Próximo trabajo acordado

1. Integrar en Android la tarjeta de identidad que pasó Claude para `OnlineModeActivity`, visible únicamente después de confirmar el acceso. Usar la foto publicada del usuario, con la ilustración como respaldo; nunca la foto local de otra cuenta.
2. Empezar el historial real por cuenta en Firebase: guardar las partidas por Firebase UID, recuperar el historial en otra sesión y evitar resultados duplicados. Primero el cierre de partidas Android; la lectura desde iOS se conecta al mismo contrato.

Las salas y el gameplay online iOS todavía no están conectados: sus acciones siguen deshabilitadas. La subida de fotos iOS también sigue pendiente. Son bloques distintos del historial y no deben presentarse como terminados.

Este cierre guarda código y documentación. No activa Blaze ni la prueba de créditos y no cambia la configuración del proyecto Firebase remoto.
