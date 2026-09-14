# Pulido online 0.1.24

## Resultado de la prueba de 5 jugadores

La prueba con cuatro emuladores y un teléfono real completó el flujo sincronizado, con inicio y
presentaciones simultáneas. Esta versión conserva ese protocolo y corrige problemas de interfaz y
estado observados durante la partida.

## Cambios

- La llegada del documento Firestore de un jugador ya no se interpreta como desconexión mientras
  todavía falta su primer registro de presencia RTDB. Un estado RTDB explícito sigue teniendo
  prioridad.
- El buscador espera el reloj del servidor y consulta únicamente salas actualizadas dentro de la
  ventana vigente. Una sala vieja no aparece transitoriamente desde caché ni consume una lectura
  visible innecesaria.
- Se eliminaron del proyecto real las siete salas públicas de prueba que habían quedado guardadas
  como `esperando`, incluidas `AAAAAAAA` y la sala de la prueba reciente una vez vencida su ventana.
- Se agregó `npm run cleanup:online-stale:preview` para auditar salas públicas abandonadas por más
  de 24 horas. La variante de borrado requiere `--apply` y la confirmación del proyecto real; se
  ejecuta desde una computadora administradora y no concede permisos destructivos a los jugadores.
- El chat ambiental mantiene el formato central de la vista compacta y queda más bajo. Su altura
  se adapta a mesas de 5–8, 9–12 y 13–15 jugadores. La vista abierta también queda limitada sobre
  la ficha propia; al escribir, el teclado puede usar la altura disponible.
- La carta trasera inferior usa recorte proporcional en lugar de estirarse a la fuerza. El recurso
  existente es de alta resolución, por lo que no se agregó otra imagen al APK.
- El contador posterior a una expulsión se presenta como sincronización automática. Cada cliente
  confirma al terminar su anuncio y el coordinador espera a todos. La presentación conserva el
  mínimo de tres segundos y ya no parece una votación ni un botón que el jugador deba pulsar.

## Prueba manual

1. Abrir Buscar partida y confirmar que `AAAAAAAA` no aparece.
2. Crear una sala de cinco e incorporar jugadores uno por uno. No debe mostrarse una desconexión
   antes de que llegue la presencia RTDB.
3. Abrir y cerrar el chat. Cerrado debe verse como panel central compacto; abierto no debe tapar la
   ficha propia; con teclado debe seguir siendo escribible.
4. Verificar la carta oculta del jugador inferior: debe mantener proporción y nitidez.
5. Expulsar a un jugador. El estado debe decir `MOSTRANDO ANUNCIO`, `ESPERANDO A TODOS` o
   `CONTINUANDO`, y avanzar sin una votación adicional después de que todos terminen.
