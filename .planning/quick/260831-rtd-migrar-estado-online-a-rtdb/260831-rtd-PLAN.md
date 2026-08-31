---
quick_id: 260831-rtd
status: complete
---

# Migrar el estado autoritativo online a RTDB

## Goal

Evitar que cada avance de fase escrito por el anfitrion provoque una lectura del documento de
sala en todos los clientes, conservando gameplay, handoff, seguridad y recuperacion.

## Tasks

1. [x] Publicar y escuchar el estado caliente en Realtime Database.
2. [x] Mantener el listener Firestore de sala solo para control, abandono y cambio de anfitrion.
3. [x] Guardar un checkpoint durable separado que no tenga listeners durante gameplay.
4. [x] Usar el checkpoint al recuperar una partida cerrada.
5. [x] Conservar el campo legacy como fallback si RTDB rechaza una publicacion.
6. [x] Validar reglas y desplegar la configuracion Firebase.

## Must Haves

- Ninguna regla, fase, voto, AFK, pantalla ni timer cambia de comportamiento.
- El estado no expone roles que antes fueran privados.
- Un cliente que vuelve del segundo plano recibe el estado mas nuevo.
- El handoff de anfitrion sigue usando el documento de control Firestore existente.
- Si falla RTDB, el host publica por la ruta legacy y la mesa puede continuar.
- La recuperacion despues de cerrar la app usa el checkpoint mas reciente.
- No ejecutar Gradle ni compilaciones por instruccion del proyecto.
