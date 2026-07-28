# Dirección visual de los logros

Estado: dirección visual aprobada y primera familia de prueba generada.

## Sistema visual común

La propuesta es que todos parezcan **medallones del archivo secreto del pueblo**:

- formato cuadrado de 512 x 512, pensado para que Google lo recorte también como círculo;
- acabado híbrido: 70 % ilustración pintada y 30 % volumen 3D controlado en aros, cera,
  relieves y materiales;
- medallón redondo de metal envejecido, visto de frente;
- fondo oscuro con textura de madera, cuero o esmalte;
- un único símbolo grande en el centro, sin personajes completos ni detalles diminutos;
- iluminación cálida lateral, como luz de vela;
- sin palabras: Google usa el mismo ícono en todos los idiomas;
- color vivo en el símbolo central, porque Google genera automáticamente la variante bloqueada
  en escala de grises;
- aro exterior por rareza:
  - bronce martillado;
  - plata ennegrecida;
  - oro antiguo;
- pequeña marca `T` grabada en la parte inferior del aro para unir toda la colección.

La composición importante debe quedar dentro del 75 % central para sobrevivir al recorte
circular de las notificaciones de Play Games.

## Conceptos

### 1. Te agradezco infinitamente

- ID: `profile_created`
- Rareza: bronce
- Concepto principal: un libro de registro abierto, una pluma cruzada y un sello de cera roja
  con la `T` de Traidores.
- Lectura: el jugador dejó su nombre registrado en el pueblo.
- Color de acento: rojo lacre y papel marfil.
- Archivo: `achievement_profile_created.png`
- Puntos sugeridos: 5.

### 2. Ser primero no es lo importante, es lo único

- ID: `assassin_kills_25`
- Rareza: bronce
- Concepto principal: una daga oscura clavada sobre una placa con cinco grupos de marcas de
  conteo, insinuando 25 sin escribir el número.
- Lectura: acumulación de eliminaciones del Asesino.
- Color de acento: rojo carmesí.
- Archivo: `achievement_assassin_kills_25.png`
- Puntos sugeridos: 10.

### 3. Y me gusta el rol, el maldito rol

- ID: `jester_wins_5`
- Rareza: bronce
- Concepto principal: una máscara sonriente de bufón sobre una urna de votos, con cinco fichas
  doradas cayendo dentro.
- Lectura: lograr que el pueblo vote al Bufón repetidas veces.
- Color de acento: violeta, verde venenoso y pequeños detalles dorados.
- Archivo: `achievement_jester_wins_5.png`
- Puntos sugeridos: 10.

### 4. Hoy dormís afuera

- ID: `expel_all_killers`
- Rareza: bronce
- Concepto principal: un portón de pueblo cerrándose; dos dagas negras quedan tiradas del lado
  exterior mientras una antorcha permanece dentro.
- Lectura: todos los asesinos fueron expulsados.
- Color de acento: naranja de antorcha contra azul nocturno.
- Archivo: `achievement_expel_all_killers.png`
- Puntos sugeridos: 15.

### 5. Lo que no te mata, te infecta

- ID: `deserter_wins_10`
- Rareza: bronce
- Concepto principal: una bandera partida en dos colores, atravesada por huellas que cambian de
  un lado al otro; una mitad conserva el sol del pueblo y la otra una luna roja.
- Lectura: cambiar de bando y sobrevivir.
- Color de acento: verde enfermizo pasando a rojo oscuro.
- Archivo: `achievement_deserter_wins_10.png`
- Puntos sugeridos: 15.

### 6. Ya nadie va a escuchar tu voto

- ID: `mercenary_same_target_3`
- Rareza: plata
- Concepto principal: una ficha de voto con una boca grabada, cerrada por tres correas o tres
  cadenas tensas.
- Lectura: silenciar tres veces al mismo objetivo.
- Color de acento: azul acero y un pequeño sello rojo.
- Archivo: `achievement_mercenary_same_target_3.png`
- Puntos sugeridos: 25.

### 7. Sobreviviendo dije, sobreviviendo

- ID: `villager_survives_12`
- Rareza: plata
- Concepto principal: una lámpara de aceite encendida junto a una espiga de trigo al amanecer,
  rodeada por un aro interior con doce muescas.
- Lectura: un aldeano común que llega vivo al final contra todo pronóstico.
- Color de acento: trigo dorado, cielo celeste y luz cálida.
- Archivo: `achievement_villager_survives_12.png`
- Puntos sugeridos: 25.

### 8. El alcalde que fue prometido

- ID: `mayor_power_wins_15`
- Rareza: plata
- Concepto principal: un mazo de alcalde golpeando el centro de una balanza con dos fichas de
  voto perfectamente empatadas.
- Lectura: el voto de poder que decide la expulsión.
- Color de acento: azul real y oro.
- Archivo: `achievement_mayor_power_wins_15.png`
- Puntos sugeridos: 30.

### 9. Quién te ha visto y quién te ve

- ID: `total_wins_50`
- Rareza: oro
- Concepto principal: una copa antigua rodeada por laureles; en la copa puede grabarse el
  numeral romano `L`, que significa 50 y funciona como símbolo, no como texto localizado.
- Lectura: veterano con cincuenta victorias.
- Color de acento: oro brillante y verde laurel.
- Archivo: `achievement_total_wins_50.png`
- Puntos sugeridos: 50.

### 10. Traidores Supremo

- ID: `traidores_supremo`
- Rareza: oro
- Concepto principal: el emblema `T` coronado, con nueve gemas pequeñas alrededor del aro que
  representan los otros nueve logros.
- Lectura: completar toda la colección.
- Color de acento: oro intenso, negro obsidiana y gemas con los colores de los demás logros.
- Archivo: `achievement_traidores_supremo.png`
- Puntos sugeridos: 100.
- Estado sugerido en Play Games: oculto hasta desbloquearlo.

## Decisiones para cerrar antes de generarlos

1. Elegir si los medallones deben verse más como **ilustración pintada** o como **metal 3D
   realista**. La recomendación es ilustración pintada: combina mejor con los retratos y se lee
   mejor en tamaño pequeño.
2. Confirmar si mantenemos símbolos sin rostros. Esto da más coherencia y evita que cada logro
   parezca pertenecer a un mapa o personaje diferente.
3. Confirmar los tres conceptos más interpretables:
   - portón y dagas para “Hoy dormís afuera”;
   - bandera partida y huellas para Desertor;
   - ficha con tres correas para Mercenario.

Después de aprobar la dirección conviene generar primero una hoja de prueba con cuatro
medallones: registro, Asesino, Aldeano y Supremo. Si funcionan juntos y siguen legibles a 64 px,
se produce la colección completa.

## Primera prueba aprobada

Generada el 25 de julio de 2026 con Registro, Asesino, Aldeano y Supremo:

- `docs/desarrollo/assets/achievements/achievement_profile_created.png`
- `docs/desarrollo/assets/achievements/achievement_assassin_kills_25.png`
- `docs/desarrollo/assets/achievements/achievement_villager_survives_12.png`
- `docs/desarrollo/assets/achievements/achievement_traidores_supremo.png`
- hoja conjunta: `docs/desarrollo/assets/achievements/prueba-familia-4-medallones.png`
- pruebas de lectura: `prueba-lectura-128px.png` y `prueba-lectura-64px.png`

Los cuatro símbolos conservan una silueta diferenciable a 64 px. La marca `T` inferior se usa
en los logros normales; Supremo usa la `T` coronada central y no repite la marca.

## Colección completa

Completada el 26 de julio de 2026. Además de los cuatro medallones de prueba, se generaron:

- `docs/desarrollo/assets/achievements/achievement_jester_wins_5.png`
- `docs/desarrollo/assets/achievements/achievement_expel_all_killers.png`
- `docs/desarrollo/assets/achievements/achievement_deserter_wins_10.png`
- `docs/desarrollo/assets/achievements/achievement_mercenary_same_target_3.png`
- `docs/desarrollo/assets/achievements/achievement_mayor_power_wins_15.png`
- `docs/desarrollo/assets/achievements/achievement_total_wins_50.png`

Vistas de control:

- colección completa: `docs/desarrollo/assets/achievements/coleccion-completa-10-medallones.png`
- lectura real: `docs/desarrollo/assets/achievements/coleccion-completa-lectura-64px.png`

Los diez archivos individuales están exportados a 512 x 512 y son los que deben cargarse en
Play Games Services. Las hojas de colección son solamente material de control.
