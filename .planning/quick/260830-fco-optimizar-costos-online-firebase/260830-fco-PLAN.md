---
quick_id: 260830-fco
status: complete
---

# Optimizar costos del modo online en Firebase

## Goal

Reducir lecturas y listeners de Firestore en lobby y gameplay sin degradar presencia,
expulsion, handoff de anfitrion, recuperacion ni resolucion autoritativa de la partida.

## Tasks

1. Medir y documentar los puntos que abren listeners, fuerzan lecturas de servidor o
   duplican presencia entre Firestore y Realtime Database.
2. Eliminar listeners privados innecesarios y evitar el solapamiento lobby-gameplay.
3. Usar el roster inmutable y la presencia de RTDB durante gameplay, conservando un
   mecanismo seguro para membresia y handoff.
4. Evitar reconciliaciones completas por cada cambio de listo cuando un snapshot de
   servidor ya confirma el mismo estado.
5. Agregar contadores diagnosticos y pruebas unitarias para las decisiones puras.

## Must Haves

- Solo el jugador cuyo rol lo necesita escucha su pista privada.
- Gameplay no escucha la coleccion completa de jugadores solo para obtener presencia.
- La expulsion y el cambio de anfitrion siguen funcionando.
- No se debilitan las reglas de seguridad de Firebase.
- Los listeners se retiran de forma determinista al cambiar de pantalla.
- Las optimizaciones quedan medibles en una nueva prueba de 14 jugadores.
- No ejecutar compilacion ni Gradle por instruccion del proyecto.

## Risks

- El handoff actualmente combina presencia RTDB con metadatos Firestore del jugador.
- El inicio online depende de distinguir snapshots locales de confirmaciones del servidor.
- `LobbyActivity.kt` contiene cambios locales del usuario que deben preservarse.
