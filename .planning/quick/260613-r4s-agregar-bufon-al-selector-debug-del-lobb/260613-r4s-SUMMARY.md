---
quick_id: 260613-r4s
status: complete
---

# Ayudas de prueba del Bufon

## Delivered

- El selector `PRUEBA: ROL HUMANO` incluye al Bufon.
- La seleccion valida que el rol pertenezca al mapa medieval y que existan al menos 8 jugadores.
- Opciones avanzadas muestra `IA DE PRUEBA: OBEDECER VOTOS` solo en lobby local y build depurable.
- Con la opcion activa, los bots reconocen `votenme`, `voten por mi`, `voten a Nombre` y `voten por Nombre`.
- Solo se consideran ordenes escritas por el jugador humano y objetivos vivos validos.
- Con la opcion apagada, la IA conserva su seleccion normal.
- Se agregaron pruebas para voto al humano, voto a un nombre, opcion apagada y mensajes falsos de bots.

## Verification

- Referencias y llaves revisadas estaticamente.
- `git diff --check` sin errores.
- No se ejecuto Gradle, compilacion ni pruebas por pedido del usuario.
