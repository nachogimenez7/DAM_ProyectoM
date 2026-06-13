---
quick_id: 260613-qun
status: complete
---

# Bufon medieval implementado

## Delivered

- El Bufon aparece automaticamente y una sola vez en partidas medievales desde 8 jugadores.
- Gana su condicion especial unicamente cuando es expulsado por votacion.
- Su identidad y victoria quedan anunciadas en el historial publico.
- La partida continua, salvo que simultaneamente se cumpla una victoria normal de faccion.
- La celebracion bloqueante dura 5 segundos, no puede omitirse y luego habilita `CONTINUAR PARTIDA`.
- La capa incluye cornetas animadas, confeti dorado, rojo y verde, imagen del Bufon y musica de victoria.
- La victoria especial se conserva para la pantalla final y el resumen personal.
- Se agregaron pruebas para asignacion, expulsion, muerte nocturna y victoria simultanea.

## Verification

- XML de layout y drawables validado como XML bien formado.
- Todas las vistas nuevas tienen una referencia unica en layout y Activity.
- `git diff --check` no reporta errores.
- No se ejecuto Gradle, compilacion ni pruebas por pedido del usuario.
