# Banco de futuro: roles y personajes históricos

> Estado: ideas de diseño para un roadmap posterior al lanzamiento. No forma parte
> del roadmap de estabilización actual y no autoriza implementación todavía.
>
> Última revisión: 10 de agosto de 2026.

## Objetivo

Guardar una dirección clara para ampliar la variedad de **Traidores** sin llenar
cada partida de poderes, romper la deducción social o volver irrelevantes los roles
actuales.

La propuesta separa tres capas:

1. roles universales que pueden aparecer en cualquier mapa;
2. roles exclusivos que refuerzan la identidad de Pampa, Grecia y Medieval;
3. personajes históricos, más poderosos y opcionales, con reglas propias.

El posible sistema roguelite se reserva para un modo separado llamado
provisionalmente **Crónicas**.

## Punto de partida del juego

- Las partidas admiten de 5 a 15 jugadores.
- Ya existen ocho roles universales: Aldeano, Policía, Médico, Alcalde,
  Asesino, Espía, Mercenario y Desertor.
- Cada mapa tiene un rol exclusivo: Payador, Oráculo o Bufón.
- La composición de roles es pública, aunque sus dueños permanecen ocultos.
- El Oráculo ya permite que un muerto vuelva a hablar durante un debate, pero no
  lo devuelve realmente a la partida.
- Los seis personajes históricos ya tienen una base en
  [`../PERSONAJES HISTORICOS.MD`](../PERSONAJES%20HISTORICOS.MD): una pareja por
  mapa, uno del Pueblo y otro de los Traidores, que reemplazan al Médico y al
  Asesino.

La composición recomendada actual deja solo dos Aldeanos en varios tamaños de
mesa. El objetivo futuro es **sumar todos estos roles al catálogo sin retirar los
actuales**. Más adelante, los presets automáticos podrán elegir una parte de ese
catálogo para mantener cada mesa legible; las partidas personalizadas podrán dar
al anfitrión mucha más libertad.

## Qué enseñan otros juegos

Las referencias sirven para detectar patrones, no para copiar roles literalmente.

- En [Werewolf de Plato](https://platoapp.com/es/juegos/hombre-lobo), el
  Nigromante puede revivir una carta Buena una vez, Pesadilla anula una habilidad
  nocturna y la Cortesana combina su visita con un riesgo para ella. Son tres
  formas distintas de pagar una habilidad fuerte: carga única, demora y peligro.
- La [lista de roles de Town of Salem](https://town-of-salem.fandom.com/es/wiki/Roles)
  incluye al bloqueador nocturno y al Retribucionista. En versiones posteriores,
  el [Retribucionista](https://townofsalem.wiki.gg/wiki/Retributionist_%28ToS2%29)
  dejó de devolver permanentemente a todos los muertos y pasó a reutilizar
  habilidades de cadáveres elegibles. Es una señal de que una resurrección completa
  puede confirmar demasiada información y cambiar demasiado la cantidad de vivos.
- En Blood on the Clocktower, el
  [Profesor](https://wiki.bloodontheclocktower.com/Professor) solo revive si el
  muerto elegido pertenece al tipo correcto y pierde la carga si falla. El
  [Posadero](https://wiki.bloodontheclocktower.com/Innkeeper) protege, pero una de
  las personas elegidas queda sin poder. El patrón útil es **poder fuerte más
  incertidumbre, coste o efecto secundario**.

## Principios de balance para Traidores

### 1. Un rol, una idea central

La habilidad principal debe poder explicarse en una frase. Las excepciones son
para resolver interacciones, no para añadir tres poderes escondidos en la misma
carta.

### 2. Catálogo grande, mesa legible

Agregar un rol significa conservarlo permanentemente como opción, no borrar otro.
La limitación se aplica solamente a la composición de una partida concreta: el
preset recomendado no necesita utilizar todos los roles disponibles al mismo
tiempo. Una sala personalizada puede permitir combinaciones más caóticas.

### 3. Mantener al menos dos roles sin poder

En el preset recomendado deberían quedar al menos dos Aldeanos entre 8 y 11
jugadores. Los roles simples son necesarios para que las declaraciones sean
discutibles y para que no todas las personas tengan información demostrable.

### 4. La pérdida de turno necesita límites

Nadie debería quedar bloqueado dos noches seguidas por la misma fuente. Los
bloqueos no deben apagar habilidades pasivas, votos o poderes diurnos salvo que la
carta lo diga expresamente.

### 5. Una vida vale más que una pista

Revivir agrega conversación, voto, presencia para la paridad y, potencialmente,
otra habilidad. Por eso una resurrección debe ser única, tardía y devolver una
versión debilitada del jugador.

### 6. Poder visible, dueño oculto

Los efectos que modifican votos, resucitan o atraviesan protecciones deberían
anunciarse. El anuncio genera deducción y contrajuego sin regalar la identidad del
rol.

### 7. La contraparte no debe ser un interruptor

Los personajes opuestos pueden responderse temáticamente, pero uno no debería
existir solo para apagar al otro. Si uno muere temprano, el rival todavía debe
tener contrajuego mediante reglas comunes.

## Roles universales propuestos

### 1. Mujer de Compañía / Cortesana — prioridad alta

**Arquetipo:** Neutral de lealtad y control.

**Nombre de trabajo:** Mujer de Compañía. “Cortesana” queda como alternativa más
corta para la carta. Se descarta el nombre vulgar porque desentona con el tono del
juego y puede convertirse fácilmente en un insulto entre jugadores.

El mismo rol puede localizarse por mapa, como ya ocurre con Policía/Comisario:

| Mapa | Nombre de carta sugerido |
|---|---|
| Pampa | **Pulpera** |
| Grecia | **Hetaira** |
| Medieval | **Cortesana** |

**Regla base propuesta:**

- En la primera noche elige en secreto si desea ganar con el Pueblo o con los
  Traidores.
- Permanece Neutral: no conoce las identidades de los Traidores y no entra en su
  chat.
- Gana únicamente si sobrevive y vence el bando elegido.
- Para la paridad cuenta como no-Traidor aunque haya elegido ayudar a los
  Traidores. Así su elección no entrega una victoria automática por cantidad.
- Desde la segunda noche dispone de **dos cargas** de Acompañar.
- Al acompañar a otro jugador, impide su habilidad nocturna activa durante esa
  noche.
- No puede elegirse a sí misma ni repetir el mismo objetivo en noches
  consecutivas.
- La carga se consume aunque el objetivo no tuviera una acción nocturna.
- El objetivo recibe un aviso privado de que no pudo actuar, pero no descubre a
  la Mujer de Compañía.
- Acompañar no protege a ninguno de los dos frente a una muerte y no anula
  habilidades pasivas o diurnas.
- Si bloquea al único Traidor capaz de matar, esa noche no hay asesinato. Si queda
  otro Asesino o Espía activo, la decisión grupal puede continuar.

**Compatibilidad:** puede existir en el mismo catálogo que el Desertor. Para el
preset recomendado conviene usar como máximo uno de los dos hasta que haya 12
jugadores; una sala personalizada podría incluirlos juntos.

**Mínimo sugerido:** 9 jugadores.

**Por qué funciona:** conserva la elección de bando imaginada originalmente, pero
la diferencia del Desertor: la Mujer de Compañía se compromete una sola vez y
recibe una herramienta fuerte que también puede perjudicar accidentalmente a su
propio bando.

### 2. Resucitador — idea en pausa

> No se recomienda para la primera expansión. Se conserva como experimento porque
> todavía no ofrece una decisión tan interesante como otros candidatos y se pisa
> parcialmente con la fantasía del Oráculo.

**Arquetipo:** Pueblo, protección diferida.

Nombres por mapa:

| Mapa | Nombre de carta sugerido |
|---|---|
| Pampa | **Curandera** |
| Grecia | **Sacerdotisa de Asclepio** |
| Medieval | **Taumaturga** |

**Regla base propuesta:**

- Una vez por partida, desde la tercera noche, elige a un jugador del Pueblo que
  haya muerto antes de esa noche.
- El elegido regresa al amanecer como **Convaleciente**.
- El Convaleciente vuelve a estar vivo, puede hablar y votar y cuenta para las
  condiciones de victoria.
- No recupera habilidades activas ni pasivas. Su función mecánica pasa a ser la de
  un Aldeano.
- No se puede revivir a un personaje histórico, a alguien ya revivido ni a un
  jugador que murió durante la misma noche.
- El regreso y el nombre del revivido se anuncian públicamente; la identidad del
  Resucitador sigue oculta.
- La partida no se reabre si ya llegó a Resultado.

**Compatibilidad:** pertenece a la familia de protección del Pueblo. Podría
coexistir con el Médico en una partida personalizada, aunque el preset recomendado
no debería juntar ambas defensas en mesas pequeñas.

**Restricción recomendada:** no coexistir con el Oráculo en el preset recomendado
de Grecia. Uno devuelve una voz por un día y el otro devuelve un voto permanente;
juntos darían demasiado control sobre el cementerio.

**Mínimo sugerido:** 10 jugadores.

**Por qué no conviene una resurrección completa:** devolver la habilidad original
permite repetir investigaciones o protecciones, confirma el bando del muerto y
agrega un voto. La condición Convaleciente conserva la fantasía de “volver” sin
duplicar todo el poder del rol.

## Banco ampliado de roles universales

Estos candidatos se sumarían al catálogo general y podrían recibir nombres o arte
distintos según el mapa, manteniendo la misma regla.

### Rastreador — Pueblo

Cada noche elige a un jugador y descubre **a quién visitó**, pero no qué hizo ni
cuál es su bando.

- Un jugador sin acción aparece como que se quedó en casa.
- No puede seguir al mismo objetivo dos noches consecutivas.
- Los Asesinos que participaron de una muerte aparecen visitando a la víctima.
- Un bloqueo exitoso hace que el objetivo aparezca en casa.

Es información fuerte, pero discutible: Médico, Policía y Asesino pueden visitar a
la misma persona por razones completamente distintas.

### Guardaespaldas — Pueblo

Cada noche protege a otra persona. La primera vez que ese objetivo fuera a morir
por un ataque normal, el Guardaespaldas muere en su lugar.

- No puede protegerse a sí mismo.
- No mata automáticamente al atacante.
- Hasta que el sacrificio ocurre puede seguir eligiendo a quién cuidar.
- Una muerte imparable atraviesa la protección si su carta lo especifica.

Se diferencia del Médico porque siempre paga con su propia vida cuando acierta.

### Interrogador — Pueblo

Una vez por partida elige a dos jugadores distintos y descubre si pertenecen al
**mismo bando o a bandos diferentes**. No aprende cuáles son esos bandos.

La información necesita una persona de referencia y permite que dos Traidores
parezcan una pareja inocente, por lo que no resuelve la partida por sí sola.

### Heredero — Pueblo, complejidad alta

Una vez por partida elige a un miembro muerto del Pueblo y obtiene **un solo uso
de su habilidad**, sin devolverlo a la vida.

- No puede heredar personajes históricos, condiciones de victoria neutrales ni
  habilidades asesinas.
- Una habilidad de uso ilimitado se convierte en una única carga.
- El muerto permanece en el chat de muertos y no recupera voto.

Es una alternativa al Resucitador que conserva el interés del cementerio sin
alterar la paridad ni confirmar a un nuevo jugador vivo.

### Encubridor — Traidores

Una vez por partida acompaña el asesinato grupal con **Borrar las huellas**. Si la
víctima muere, su rol aparece como Desconocido durante todo el día siguiente y se
revela al amanecer posterior.

- No provoca una muerte adicional.
- Si la víctima sobrevive, la carga se pierde.
- El propio muerto conoce su rol y conserva el acceso normal al chat de muertos.
- La composición pública permite deducir qué cartas siguen sin identificar.

Genera mentiras y acusaciones sin silenciar a una persona viva ni falsificar el
resultado final para siempre.

### Chantajista — Traidores

Una vez por partida descubre el rol exacto de un jugador vivo. El objetivo recibe
un mensaje privado informándole que alguien conoce su secreto, pero no sabe quién.

El Traidor gana información muy precisa; a cambio, la víctima puede cambiar su
declaración, preparar una trampa o avisar públicamente que existe un Chantajista.

### Sucesor — Traidores, experimental

No tiene poder mientras exista un Asesino o Espía vivo. Cuando muere el último
killer, el Sucesor se convierte en Asesino desde la noche siguiente.

- La sucesión se anuncia públicamente, pero no revela al jugador.
- Su presencia impide que el Pueblo gane únicamente por matar al primer grupo de
  killers.
- Debe reservarse para 11 jugadores o más.

Es dramático y evita que un Traidor de utilidad quede sin objetivo, pero requiere
explicar muy bien la condición de victoria desde el inicio.

### Ángel Guardián — Neutral

Al comenzar recibe en secreto a un jugador como Protegido. Dispone de dos
protecciones nocturnas y obtiene una victoria especial si el Protegido sigue vivo
cuando termina la partida, sin importar qué bando gane.

- No conoce automáticamente el rol de su Protegido.
- Puede protegerlo aunque pertenezca a los Traidores.
- Si el Protegido muere, pierde su condición especial pero continúa participando.

Produce alianzas extrañas sin exigir una conversión de bando.

### Apostador — Neutral

Durante las dos primeras noches elige a dos jugadores diferentes y apuesta en
secreto a qué bando pertenece cada uno. No recibe el resultado inmediatamente.
Si al terminar la partida acertó ambas apuestas y sigue vivo, obtiene una victoria
especial.

No entrega información utilizable durante el debate; su juego consiste en leer la
mesa, sobrevivir y decidir a quién favorecer sin tener certeza.

### Maldito — Pueblo convertible, muy experimental

Comienza como Pueblo y no tiene acción nocturna. La primera vez que los Traidores
intentan matarlo, sobrevive y se convierte secretamente en Traidor.

- Las investigaciones anteriores a la conversión siguen siendo verdaderas para
  el momento en que ocurrieron.
- Desde la noche siguiente conoce a los Traidores y participa de su estrategia.
- La composición inicial advierte que una conversión es posible.

Es una gran fuente de paranoia, pero cambia alianzas y sincronización online; debe
quedar para una expansión avanzada o un preset caótico.

### Primera selección recomendada

Después de la Mujer de Compañía, los candidatos más claros para prototipar son:

1. **Rastreador**, porque genera pistas sin revelar bandos directamente.
2. **Encubridor**, porque da a los Traidores una herramienta de engaño y no otra
   muerte.
3. **Guardaespaldas**, por su decisión emocional y su coste inmediato.
4. **Interrogador**, como rol de una sola jugada fácil de comprender.

Heredero, Sucesor y Maldito se reservan para cuando el motor soporte mejor cambios
de rol, prioridades y estados persistentes.

## Nuevos roles exclusivos sugeridos

La primera expansión puede añadir un rol nuevo por mapa al catálogo permanente.
Cada uno crea una respuesta temática al exclusivo actual sin retirar ninguna
carta existente.

### Pampa — Capanga

**Bando:** Traidores.

**Habilidad: Dar la cara, una vez por partida.** Si otro Traidor fuera a ser
expulsado, el Capanga puede revelarse y ocupar su lugar. El Capanga es expulsado y
el objetivo original permanece vivo.

- No puede salvarse a sí mismo.
- La mesa sí elimina a un Traidor y descubre la identidad del Capanga; el poder
  cambia cuál de ellos muere, no anula la votación.
- Puede negarse a intervenir y guardar la carga.
- Si decide actuar, la expulsión se resuelve inmediatamente y después se vuelven
  a comprobar las condiciones de victoria.

**Compatibilidad:** puede convivir en el catálogo con el Mercenario. En mesas
pequeñas conviene no acumular demasiados Traidores de utilidad.

**Mínimo sugerido:** 8 jugadores.

**Relación temática:** el Payador obliga a dos personas a sostener su palabra en
público; el Capanga demuestra lealtad con un sacrificio visible. Además protege al
killer sin borrar el acierto del Pueblo.

**Alternativa pampeana:** el **Baqueano** puede ser un rol del Pueblo con dos
cargas de rastreo. Sigue a un jugador y descubre a quién visitó esa noche. Es más
simple y menos explosivo, pero se parece al Rastreador universal; por eso conviene
elegir uno de los dos conceptos y no duplicarlos.

### Grecia — Sicofanta

**Bando:** Traidores.

**Habilidad: Falsa acusación, una vez por partida.** Durante la noche marca a un
jugador vivo que no sea Traidor. La próxima investigación policial sobre esa
persona devuelve “sospechoso”, sin importar su bando, y consume la marca.

- Si el marcado muere antes de ser investigado, la habilidad se pierde.
- El Policía no recibe una indicación de que el resultado fue alterado.
- La composición pública advierte que el Sicofanta está en juego, de modo que una
  acusación aislada deja de ser una prueba absoluta.

**Compatibilidad:** puede sumarse al catálogo junto al Mercenario. Para el preset
recomendado conviene controlar cuántas anulaciones y falsificaciones nocturnas
aparecen juntas.

**Mínimo sugerido:** 9 jugadores.

**Relación temática:** el Oráculo recupera testimonios reales del pasado; el
Sicofanta fabrica una acusación convincente en el presente.

### Medieval — Carcelero

**Bando:** Pueblo.

**Habilidad: Encierro, dos veces por partida.** Desde la segunda noche elige a otro
jugador. Esa persona no puede morir por un ataque nocturno normal, pero tampoco
puede ejecutar su habilidad activa esa noche.

- No puede encarcelarse a sí mismo ni repetir objetivo en noches consecutivas.
- El detenido recibe un aviso privado; no conoce al Carcelero.
- Las habilidades pasivas siguen funcionando.
- Una ejecución declarada “imparable”, como Excalibur, atraviesa la protección si
  su carta lo especifica.

**Compatibilidad:** puede sumarse al catálogo junto al Médico. En mesas pequeñas,
usar ambos vuelve más probable una noche sin muerte, aunque el bloqueo del
Carcelero también puede perjudicar al Pueblo.

**Mínimo sugerido:** 8 jugadores.

**Relación temática:** el Bufón quiere que el Pueblo castigue a la persona
equivocada; el Carcelero puede salvar a alguien, pero también encerrar por error a
quien necesitaba actuar.

## Guía de densidad para cada partida

Esta tabla no retira roles del catálogo. Solo orienta al futuro preset recomendado
cuando ya existan muchas cartas disponibles.

| Cupo | Opciones futuras | Regla |
|---|---|---|
| Asesinato Traidor | Asesino, Espía | Debe quedar al menos un killer vivo para que continúe la amenaza nocturna. |
| Investigación Pueblo | Policía | Mantener una sola fuente estable de información por defecto. |
| Protección Pueblo | Médico, Guardaespaldas, Resucitador, Carcelero | Limitar la densidad defensiva en mesas pequeñas; todos siguen disponibles en el catálogo. |
| Utilidad Traidora | Mercenario, Encubridor, Chantajista, Capanga, Sicofanta | Evitar que una misma mesa acumule demasiadas anulaciones y secretos perfectos. |
| Neutral de lealtad | Desertor, Mujer de Compañía | Máximo uno hasta 11 jugadores en el preset recomendado. |
| Identidad de mapa | Payador, Oráculo, Bufón u otra alternativa | Un exclusivo entre 8 y 11; un segundo solo desde 12. |
| Históricos | Una pareja del mapa | Entran juntos y reemplazan roles base. |

Reglas adicionales para el preset recomendado:

- Mantener al menos dos Aldeanos entre 8 y 11 jugadores.
- No usar Mujer de Compañía y Desertor juntos en el preset recomendado; permitirlo
  como opción personalizada.
- No usar Resucitador y Oráculo juntos en Grecia.
- No usar Payador y Capanga juntos antes de 12 jugadores en el preset recomendado.
- No permitir dos fuentes capaces de bloquear a la misma persona durante dos
  noches consecutivas.
- Los personajes históricos nunca consumen dos plazas adicionales: reemplazan al
  Médico y al Asesino como ya indica su documento actual.

## Personajes históricos: dirección recomendada

### Mantenerlos como una capa “legendaria” opcional

El nombre **Personajes históricos** es claro. “Legendarios” puede utilizarse como
etiqueta visual o nombre del modificador de partida, sin afirmar que todas las
figuras sean legendarias en sentido histórico.

La opción de lobby podría llamarse **Personajes históricos: activados** y mostrar
antes de empezar qué pareja puede aparecer en ese mapa.

### La pareja es obligatoria

Si aparece el histórico del Pueblo, también aparece el histórico de los
Traidores. Ambos reemplazan una pieza comparable del mazo. No conviene sortear uno
sin el otro ni vender poder mediante desbloqueos.

### No todos necesitan exactamente dos habilidades

La igualdad debe medirse por impacto esperado, no por cantidad de texto. Una
pasiva excelente puede valer tanto como una activa de una carga. Cada carta debería
tener como máximo:

1. una habilidad distintiva;
2. una carga o condición de activación;
3. un coste, exposición o forma clara de contrajuego.

### Revisión de las parejas ya ideadas

| Mapa | Pareja | Lectura de balance |
|---|---|---|
| Medieval | Juana de Arco / Rey Arturo | Es la pareja más directa: dos protecciones frente a una ejecución imparable. Excalibur debe reemplazar el asesinato grupal de esa noche, no añadir otra muerte. |
| Grecia | Jason / Circe | El Vellocino agrega una vida oculta; Circe elimina voz, voto y habilidad durante un ciclo. Son fuertes sin anularse directamente. Conviene que la transformación no pueda repetirse ni extenderse. |
| Pampa | Martina Chapanay / Juan Moreira | La incertidumbre del compañero y del disparo equilibra a Martina. Moreira ya paga su supervivencia revelándose; su “última corrida” debería reemplazar la muerte grupal de esa noche para evitar dos muertes garantizadas. |

Interacciones recomendadas que estaban pendientes:

- El disparo de Martina es una muerte normal y puede ser detenido por una
  protección.
- Si Martina dispara a Moreira y su supervivencia no fue usada, Moreira sobrevive
  y se revela.
- La última corrida de Moreira reemplaza, y no se suma, al asesinato de los
  Traidores de esa noche.
- Las activaciones históricas fuertes se anuncian públicamente, aunque el dueño
  siga oculto, salvo que la propia desventaja sea revelarse.

## Modo Crónicas: la idea roguelite

Elegir tres poderes libremente en el modo normal produciría demasiadas
combinaciones y volvería casi imposible deducir qué puede hacer cada persona. La
idea encaja mejor como modo separado y explícitamente caótico.

### Crónicas v1 — recomendada

Cada personaje histórico dispone de **tres conjuntos prefabricados**. Antes de la
partida, su jugador elige uno. Cada conjunto contiene:

- una pasiva;
- una activa limitada;
- una carga, debilidad o condición pública.

Esto da sensación de construcción de personaje sin multiplicar todas las
interacciones posibles. Los dos bandos eligen su conjunto simultáneamente y el
resto de la mesa conoce los tres conjuntos posibles, pero no cuál fue elegido.

### Crónicas v2 — solo después de validar v1

El jugador realiza tres elecciones estructuradas:

1. una de tres pasivas;
2. una de tres activas;
3. una de tres cargas o maldiciones obligatorias.

No puede escoger tres activas ni evitar la desventaja. Los poderes desbloqueables
amplían opciones horizontales; nunca entregan estadísticas superiores por pagar o
jugar más tiempo.

## Orden sugerido para un roadmap futuro

### Fase 0 — catálogo, reglas y simulación

- Preparar el catálogo para crecer sin eliminar roles actuales.
- Hacer que el preset recomendado seleccione una mesa legible desde ese catálogo.
- Escribir una tabla única de prioridad de acciones nocturnas.
- Registrar estadísticas por mapa, cantidad de jugadores, rol y preset.

### Fase 1 — un universal controlable

- Prototipar solo la Mujer de Compañía en local contra bots.
- Validar elección de bando, bloqueo del killer único y mensajes privados.
- No crear ilustraciones definitivas hasta cerrar las reglas.

### Fase 2 — identidad de mapas

- Prototipar Capanga, Sicofanta y Carcelero de a uno.
- Añadirlos al catálogo y decidir por separado cuándo aparecen en el preset.
- Probar cada mapa con y sin su rol nuevo.

### Fase 3 — segunda tanda universal

- Prototipar Rastreador, Encubridor, Guardaespaldas e Interrogador.
- Medir información confirmable, noches sin muerte y claridad de los avisos.
- Mantener Resucitador y Heredero en laboratorio hasta elegir solo uno.

### Fase 4 — personajes históricos

- Cerrar las seis reglas actuales y todas sus interacciones.
- Implementar la pareja completa de un solo mapa como prueba.
- Avanzar a ilustración solo después del playtest.

### Fase 5 — Crónicas

- Crear tres conjuntos prefabricados por personaje.
- Mantenerlo fuera del preset competitivo/recomendado.
- Evaluar el draft modular únicamente si los conjuntos son legibles y estables.

## Criterios de playtest

Los bots sirven para encontrar errores de reglas y tendencias de paridad, pero el
engaño y la frustración necesitan partidas humanas.

Para cada rol nuevo conviene medir:

- porcentaje de victoria por bando, mapa y tramo de jugadores (5–7, 8–10 y
  11–15);
- diferencia frente a una composición equivalente sin el rol nuevo;
- rondas promedio y cantidad de noches sin muerte;
- porcentaje de partidas en que la habilidad pudo usarse y produjo un efecto;
- cantidad de turnos perdidos por un mismo jugador;
- cuántas veces el efecto decidió una votación o alteró la paridad;
- si los jugadores entendieron por qué su acción falló.

Como alarma inicial, una variación sostenida mayor a 5–7 puntos porcentuales en la
victoria del bando respecto de la composición de referencia debería provocar un
ajuste. No es una meta estadística definitiva: debe combinarse con al menos una
tanda de playtests humanos y comentarios sobre diversión, claridad y capacidad de
contrajuego.

## Decisión resumida

- **Sí** a Mujer de Compañía, con Pulpera / Hetaira / Cortesana como posibles
  nombres de ambientación y dos bloqueos limitados.
- **En pausa** la resurrección. Heredar una sola habilidad de un muerto puede ser
  una alternativa más limpia que devolver otro jugador vivo.
- **Sí** a Rastreador, Encubridor, Guardaespaldas e Interrogador como primera
  ampliación del banco universal.
- **Sí** a una segunda tanda exclusiva: Capanga, Sicofanta y Carcelero forman un
  trío más dramático para prototipar; Baqueano queda como alternativa de Pampa.
- **Sí** a personajes históricos fuertes si aparecen por parejas, reemplazan
  roles base y tienen coste o exposición.
- **Sí** al concepto roguelite como **Modo Crónicas**, primero con conjuntos
  prefabricados y no con combinaciones libres dentro del modo normal.
