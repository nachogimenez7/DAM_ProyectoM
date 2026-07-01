# Roles exclusivos de mapa

## Bufon - Medieval

- Es un rol neutral.
- Su funcion oficial es ser eliminado por votacion del pueblo para obtener su
  condicion especial.
- La victoria se anuncia de forma destacada, pero no termina la partida.
- Morir durante la noche no cumple su condicion.
- Al finalizar la partida aparece junto a los ganadores finales como ganador
  especial, aunque Pueblo y Traidores hayan seguido jugando.

## Oraculo - Grecia

- Pertenece al Pueblo.
- Usa su poder una sola vez por partida durante la noche.
- Elige a un jugador muerto para que participe en el debate del dia siguiente.
- El anuncio identifica al jugador que vuelve, pero no revela quien es el
  Oraculo.
- El jugador convocado puede hablar, pero no votar, usar habilidades, recibir
  acciones ni contar como vivo para las condiciones de victoria.
- Al terminar el debate vuelve al chat de muertos.

## Payador - Pampa

- Pertenece al Pueblo.
- Puede iniciar un Contrapunto entre dos jugadores vivos durante el debate.
- Durante el Contrapunto solo hablan el Payador y los dos participantes
  elegidos.
- Al cerrar el Contrapunto, el Payador senala a uno de los participantes como
  mas sospechoso.
- Esa sospecha suma un voto adicional al recuento real del dia, incluso si el
  voto del Alcalde revelado tambien esta duplicado.
- El Payador solo puede usar el Contrapunto una vez por partida.

## Informacion de los muertos

- El chat de muertos no revela roles ajenos ni acciones ocultas.
- Los muertos conservan el historial publico, sus propias experiencias y sus
  conversaciones.
- El jugador convocado sirve como mensajero de las sospechas y conclusiones que
  se formaron en ese chat.

## Resultado de partida

- `winner` representa solamente el ganador final que detiene la partida.
- `specialVictories` acumula victorias personales que no detienen la partida.
- La presentacion final puede mostrar ambos resultados sin confundir sus reglas.
