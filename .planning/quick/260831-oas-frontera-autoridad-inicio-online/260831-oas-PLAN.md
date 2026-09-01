---
quick_id: 260831-oas
status: complete
---

# Frontera de autoridad para el inicio online

## Goal

Separar de `LobbyActivity` el contrato puro que valida y prepara el inicio y reparto de una
partida online, sin cambiar la jugabilidad, las rutas Firebase ni el formato compatible con la
version publicada.

## Tasks

1. [x] Modelar la solicitud, participantes y decisiones del inicio online sin dependencias Android/Firebase.
2. [x] Centralizar la construccion de payloads publicos, repartos privados y acceso RTDB.
3. [x] Integrar el contrato en la transaccion existente de `LobbyActivity`.
4. [x] Ejecutar las pruebas de autorizacion, idempotencia, conteo/listos, desempate y ausencia de roles en el payload publico.
5. [x] Documentar el limite actual y la siguiente migracion a un backend confiable.

## Estado de verificacion

- Casos JVM agregados en `OnlineMatchStartContractTest.kt`.
- `git diff --check`: correcto.
- Autorizacion explicita recibida para Gradle.
- `testDebugUnitTest`: 589 pruebas, 0 fallos, 0 errores y 0 omitidas.
- `assembleDebug`: correcto.

## Must Haves

- El AAB actual y las salas existentes conservan compatibilidad.
- No se despliegan reglas ni funciones durante esta extraccion.
- El reparto publico nunca contiene roles.
- Cada reparto privado conserva solo el rol propio y, para traidores, los aliados visibles.
- La transaccion sigue siendo idempotente y solo el anfitrion valido puede iniciarla.
- No ejecutar Gradle ni compilaciones Android por instruccion del proyecto.
