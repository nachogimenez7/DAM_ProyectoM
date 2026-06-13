---
quick_id: 260613-qun
status: in_progress
---

# Implementar Bufon medieval

## Goal

Incorporar un Bufon automatico en partidas medievales desde 8 jugadores. Si es expulsado por votacion, obtiene una victoria especial, su identidad se revela publicamente, aparece una celebracion no omitible de 5 segundos y luego la partida continua.

## Tasks

1. Incorporar el Bufon al mazo medieval y registrar su victoria especial al resolver una expulsion.
2. Crear la capa visual con cornetas, confeti, sonido, imagen, mensaje y boton tardio para continuar.
3. Integrar la celebracion con lifecycle, guardado de estado, resultado final e historial publico.
4. Agregar pruebas unitarias de asignacion, victoria por voto, no victoria nocturna y continuidad.

## Must Haves

- Maximo un Bufon, automatico desde 8 jugadores en mapa medieval.
- Solo gana al ser expulsado por votacion.
- La celebracion dura 5 segundos y no se puede omitir.
- El boton aparece al finalizar y reanuda la partida.
- El Bufon queda muerto, revelado y registrado como ganador especial.
- La victoria especial aparece tambien en el resultado final.
- No ejecutar compilacion ni pruebas por pedido del usuario.
