---
quick_id: 260901-oae
status: complete
---

# Suavizar el error automatico de acceso online

## Goal

Evitar alertas repetitivas durante una reconexion transitoria y ocultar detalles de Firebase a
los jugadores, manteniendo el acceso online cerrado hasta verificar el servidor.

## Tasks

1. [x] Confirmar el disparador real mediante Logcat.
2. [x] Sustituir la alerta automatica por un estado inline con reintento.
3. [x] Cancelar reintentos obsoletos al salir de la pantalla.
4. [x] Reescribir mensajes tecnicos para hablar del servidor.
5. [x] Probar unidad, compilacion y reinicio con el servidor inaccesible.

## Must Haves

- Un fallo transitorio no abre ventanas ni apila avisos.
- Un bloqueo real de moderacion conserva su dialogo obligatorio.
- Las acciones siguen deshabilitadas hasta completar la verificacion.
- Ningun mensaje para jugadores menciona Firebase, Firestore o reglas internas.
