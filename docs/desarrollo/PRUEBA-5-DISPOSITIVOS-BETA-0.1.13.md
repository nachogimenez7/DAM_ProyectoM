# Prueba rápida con 5 dispositivos — beta 0.1.13

Objetivo principal: comprobar que las salas públicas se descubren y actualizan sin depender del código.

## Preparación

- Instalar la misma beta 0.1.13 en los cinco dispositivos.
- Crear una sala nueva. No reutilizar `MBQMZD`: esa partida ya se inició con el orden de jugadores defectuoso de la compilación anterior.
- Usar una cuenta o identidad diferente en cada uno. Si los emuladores fueron clonados después de abrir la app, pueden haber copiado la misma identidad: verificar que cada perfil muestre un jugador distinto antes de crear la sala.
- Si sólo algunos emuladores reciben errores de Firebase, revisar Logcat: cada instalación debug puede necesitar su propio token de App Check registrado.
- Mantener visible la hora para anotar el momento exacto de cualquier fallo.
- Nombrar los dispositivos A, B, C, D y E.

## Prueba prioritaria: expulsión, reingreso y acciones

1. A crea una sala nueva y B, C, D y E ingresan.
2. A abre el perfil de E, baja hasta las acciones y toca **EXPULSAR DE LA SALA**.
3. En A, E debe desaparecer y el contador debe pasar de 5/5 a 4/5.
4. En E debe aparecer una sola ventana: **Fuiste expulsado**, con un único botón **VOLVER A JUGAR ONLINE**. No debe quedar visible ni utilizable el lobby viejo.
5. E toca el botón, vuelve al online y reingresa mientras siga libre el cupo. La expulsión no lo bloquea.
6. Los cinco marcan **LISTO** y A inicia. Nadie debe quedar en **SINCRONIZANDO**.
7. En cada fase nocturna, el jugador dueño del rol realiza su acción. Probar obligatoriamente **investigar** con el Detective y confirmar que no aparece “Firebase rechazó la acción”.
8. Verificar también matar, salvar y votar; después de cada toque, todos deben avanzar a la misma fase y ronda.

## Recorrido mínimo

1. A crea una sala pública de prueba para 5 jugadores y permanece dentro.
2. B, C, D y E abren **Buscar partida**. La sala debe aparecer en los cuatro.
3. B entra tocando la sala; C entra con el código. Ambos caminos deben llevar a la misma sala.
4. D actualiza o vuelve a abrir el buscador. El contador debe reflejar los jugadores ya unidos.
5. E entra desde el buscador. La sala llena no debe aceptar un sexto jugador.
6. C sale voluntariamente. El cupo debe volver a quedar libre y actualizarse en el navegador.
7. C reingresa y todos marcan **LISTO**. A inicia la partida.
8. Los cinco deben salir juntos del lobby; ningún equipo puede quedar indefinidamente en **SINCRONIZANDO 4/5** ni **0/5**.
9. Volver a crear una sala para 6 jugadores y repetir el inicio: debe superar **SINCRONIZANDO 5/6**.
10. Durante la partida, D cierra la app abruptamente, la abre de nuevo y prueba la recuperación.
11. Desde el perfil de otro jugador en el lobby, comprobar **SILENCIAR PARA MÍ**, **VOLVER A ESCUCHAR** y **REPORTAR**. En el anfitrión también debe aparecer **EXPULSAR DE LA SALA**.
12. Finalizar o abandonar correctamente y confirmar que la sala ya no aparece como disponible.

## Pruebas complementarias

- A crea una sala privada: no aparece en Buscar partida, pero admite el código.
- A crea dos salas públicas en distintos momentos: la más reciente aparece primero.
- El anfitrión expulsa a E: si queda un cupo, E puede volver a entrar.
- Después de una partida, dejar que el anfitrión original salga. La cuenta que hereda la sala
  debe poder salir también; antes de confirmar, el texto debe nombrar al siguiente anfitrión.
- En una instalación limpia, abrir el menú principal: el tutorial no aparece. Entrar al primer lobby, online o contra IA: aparece una sola vez. Volver a entrar: no se repite.
- Salir como invitado: el diálogo advierte que otra persona puede ocupar el lugar. Tras confirmar, el código de esa sala no debe quedar como reingreso si la partida nunca llegó a iniciarse.
- Abrir AYUDA: sólo una sección general puede quedar desplegada a la vez.
- En una instalación limpia, entrar por primera vez al online: aparecen las normas; **VOLVER** sale del modo y **ACEPTO Y CONTINÚO** permite continuar. No debe repetirse después de aceptarlas.

## Qué anotar si falla

- Hora exacta.
- Dispositivo y cuenta/identidad.
- Sala pública o privada y cantidad de jugadores.
- Pantalla y acción inmediatamente anterior.
- Mensaje mostrado y captura de pantalla.
- Si el ingreso por código seguía funcionando.

No borrar la sala ni limpiar los datos de los emuladores hasta guardar esta información.

El índice remoto necesario para filtrar por `estado`, `visibilidad` y `actualizadaEn` fue verificado en el proyecto Firebase `traidores` el 20 de agosto de 2026.
