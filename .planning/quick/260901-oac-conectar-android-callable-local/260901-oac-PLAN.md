---
quick_id: 260901-oac
status: complete
---

# Conectar Android a la callable local

## Goal

Permitir que una build debug, solo mediante opt-in explicito, inicie partidas con
`iniciarPartidaV2` y Firebase Emulator sin modificar el comportamiento release.

## Tasks

1. [x] Agregar Firebase Functions y una configuracion de emuladores temprana.
2. [x] Crear un cliente callable con respuesta tipada y parser probado.
3. [x] Conectar el boton de inicio en modo emulator conservando el flujo legacy.
4. [x] Probar parser, compilacion Android y backend emulado.
5. [x] Documentar el comando de activacion local y el procedimiento manual.

## Must Haves

- Release nunca usa emuladores ni cambia de autoridad.
- Debug tampoco usa emuladores salvo opt-in de Gradle.
- La app envia solo `roomId` y desempate; no envia jugadores ni roles.
- No hay fallback V2 a autoridad local despues de solicitar la callable.
- Se conserva el bloqueo de doble inicio.
