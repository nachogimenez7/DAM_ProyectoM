# Ficha de Google Play — Traidores

Versión preparada: `0.1.13` (`versionCode 14`)
Canal recomendado: **prueba cerrada**
Idioma de la ficha: **Español (Latinoamérica) — es-419**
Estado comunicado: **beta**

## 1. Dónde se copia cada elemento

En Play Console, abrir la aplicación y entrar en **Presencia en Google Play Store > Ficha de Play Store principal** (`Main store listing`). Allí se cargan el nombre, las descripciones y los gráficos.

Las notas de versión se pegan al preparar una entrega en **Prueba y lanzamiento > Pruebas > Prueba cerrada > Crear versión**, dentro de **Novedades de esta versión** (`What's new in this release?`).

Límites vigentes comprobados en la documentación oficial:

- nombre: hasta 30 caracteres;
- descripción corta: hasta 80 caracteres;
- descripción completa: hasta 4.000 caracteres;
- notas de versión: hasta 500 caracteres por idioma;
- hasta 8 capturas por tipo de dispositivo.

Fuentes: [crear y configurar una aplicación](https://support.google.com/googleplay/android-developer/answer/9859152), [preparar una versión](https://support.google.com/googleplay/android-developer/answer/9859348) y [recursos de vista previa](https://support.google.com/googleplay/android-developer/answer/9866151).

## 2. Detalles de la aplicación

### Nombre de la aplicación

Campo: **Nombre de la aplicación**

```text
Traidores
```

Longitud: 9/30.

### Descripción corta

Campo: **Descripción corta**

```text
Mentí, investigá y sobreviví en partidas de deducción social para 5 a 15.
```

Longitud: 73/80.

### Descripción completa

Campo: **Descripción completa**

```text
En Traidores, cada participante recibe un rol secreto. Algunos protegen al Pueblo, otros atacan desde las sombras y los neutrales persiguen sus propios objetivos. La verdad importa, pero lograr que los demás te crean importa todavía más.

Cada partida alterna tres momentos:

• NOCHE: investigá, protegé, silenciá o elegí una víctima sin revelar tu identidad.
• DEBATE: compará versiones, encontrá contradicciones, defendete y sembrá dudas.
• VOTACIÓN: señalá a un sospechoso y decidí con el resto de la mesa quién debe abandonar el pueblo.

DOS FORMAS DE JUGAR

Practicá en solitario contra jugadores controlados por la IA o reunite con otras personas en salas online públicas y privadas. Armá mesas de 5 a 15 participantes, compartí un código con tus amigos o buscá una partida disponible.

TRES ESCENARIOS

Viajá por la Pampa de 1915, la Antigua Grecia y un feudo medieval. Cada ambientación transforma los personajes, la música, la atmósfera y su rol exclusivo: Payador, Oráculo o Bufón.

ROLES QUE CAMBIAN LA PARTIDA

Investigá como Detective, protegé como Médico, imponé tu voto como Alcalde o coordiná en secreto con los Traidores. Las composiciones pueden adaptarse a la cantidad de participantes y cada rol exige una estrategia diferente.

UNA MESA QUE RECUERDA

El chat, las votaciones y los acontecimientos de rondas anteriores dejan pistas. Personalizá tu perfil, consultá tu historial y aprendé de cada engaño.

Traidores se encuentra en beta. El modo online continúa en prueba y puede recibir ajustes de equilibrio, estabilidad e interfaz durante el desarrollo.
```

## 3. Notas de versión

Nombre interno recomendado de la versión: `0.1.13-beta cerrada`

Campo: **Novedades de esta versión**

```text
Primera beta cerrada de Traidores.

• Corregimos la aparición de salas públicas.
• Mejoramos la sincronización al iniciar partidas online.
• Corregimos acciones rechazadas después de una salida o reingreso.
• El tutorial aparece al entrar al primer lobby y puede repetirse desde Ayuda.
• Sumamos silencio, reporte y expulsión desde el perfil de cada jugador.
• Compactamos Ayuda y agregamos normas de comunidad.
• Simplificamos Opciones y ocultamos funciones aún no disponibles.
```

Para el cuadro multidioma de Play Console:

```text
<es-419>
Primera beta cerrada de Traidores.

• Corregimos la aparición de salas públicas.
• Mejoramos la sincronización al iniciar partidas online.
• Corregimos acciones rechazadas después de una salida o reingreso.
• El tutorial aparece al entrar al primer lobby y puede repetirse desde Ayuda.
• Sumamos silencio, reporte y expulsión desde el perfil de cada jugador.
• Compactamos Ayuda y agregamos normas de comunidad.
• Simplificamos Opciones y ocultamos funciones aún no disponibles.
</es-419>
```

## 4. Gráficos: archivo y orden recomendado

### Icono de la aplicación

Campo: **Icono de la aplicación**

Subir:

```text
Google Play/Traidores_Icono_512.png
```

Verificado: 512×512 px, PNG, 86,7 KB.

Texto alternativo sugerido:

```text
Emblema dorado de Traidores sobre un fondo oscuro.
```

### Gráfico de funciones principal

Campo: **Gráfico de funciones** (`Feature graphic`)

Subir:

```text
Google Play/Traidores_Grafico_Funciones_1024x500_v1.jpg
```

Verificado: 1024×500 px, JPG, 193,2 KB.

Motivo: comunica el tono, el título y la pregunta central con mejor lectura a tamaño pequeño. El paisaje reúne las tres ambientaciones sin depender de rótulos finos.

Texto alternativo sugerido:

```text
Tres pueblos al anochecer bajo el título Traidores y la pregunta ¿En quién vas a confiar?
```

Alternativas, no subir simultáneamente en esta ficha:

1. `Traidores_Grafico_Funciones_TRES_MAPAS_1024x500_v2.jpg`: útil para una ficha o campaña centrada en la variedad de mapas.
2. `Traidores_Grafico_Funciones_MEDIEVAL_1024x500.jpg`: útil para una campaña temática medieval.
3. `Traidores_Grafico_Fondo_v1.png`: archivo fuente de 1794×877; no cumple la medida final de 1024×500 y no debe cargarse directamente.

### Video de vista previa

Play Console pide una URL de YouTube; no acepta directamente el MP4 local. En **Video promocional**, pegar:

```text
https://www.youtube.com/watch?v=DIA_P_yFAT4
```

Es el enlace equivalente al Short ya publicado. Si Play Console redirige o no reconoce la URL de Shorts, usar exactamente la versión `watch?v=` anterior.

Archivo maestro recomendado para subir a YouTube:

```text
Sitio-Web-Traidores/media/trailer-traidores-horizontal.mp4
```

Si se agrega el video, Google Play lo mostrará antes de las capturas y usará el gráfico de funciones como portada.

## 5. Capturas de teléfono

Todas las capturas están verificadas en 1080×1920 px y cumplen la relación 9:16 recomendada para juegos. Subirlas en este orden:

### 1 — Promesa principal

Archivo:

```text
Google Play/Capturas/Telefono/01_Tu_rol_es_secreto.png
```

Texto incorporado: `TU ROL ES SECRETO`

Texto complementario: `Descubrí tu objetivo. Decidí cuánto contar y a quién engañar.`

Texto alternativo:

```text
Carta secreta del Asesino antes de comenzar una partida de Traidores.
```

### 2 — Cooperación secreta

Archivo:

```text
Google Play/Capturas/Telefono/02_Planea_en_las_sombras.png
```

Texto incorporado: `PLANEA EN LAS SOMBRAS`

Texto complementario: `Coordiná con tu bando sin que el Pueblo descubra el plan.`

Texto alternativo:

```text
Plan nocturno y chat privado de los Asesinos para elegir una víctima.
```

### 3 — Núcleo social

Archivo:

```text
Google Play/Capturas/Telefono/03_Debate_acusa_convence.png
```

Texto incorporado: `DEBATE. ACUSA. CONVENCE.`

Texto complementario: `Escuchá cada versión, encontrá contradicciones y defendete.`

Texto alternativo:

```text
Mesa durante el debate diurno con cartas de jugadores y chat público.
```

### 4 — Información privada

Archivo:

```text
Google Play/Capturas/Telefono/04_Investiga_cada_sospecha.png
```

Texto incorporado: `INVESTIGA CADA SOSPECHA`

Texto complementario: `Usá tu habilidad y decidí cuándo revelar lo que sabés.`

Texto alternativo:

```text
Resultado privado de una investigación que marca a un jugador como inocente.
```

### 5 — Consecuencias públicas

Archivo:

```text
Google Play/Capturas/Telefono/05_Carta_revelada.png
```

Texto incorporado: `CARTA REVELADA`

Texto complementario: `Cada decisión cambia lo que la mesa cree saber.`

Texto alternativo:

```text
Carta revelada de un Aldeano eliminado durante la partida.
```

### 6 — Victoria especial

Archivo:

```text
Google Play/Capturas/Telefono/06_Victoria_del_bufon.png
```

Texto incorporado: `EL BUFÓN LOS ENGAÑÓ`

Texto complementario: `Los roles neutrales persiguen objetivos propios.`

Texto alternativo:

```text
Anuncio de victoria especial del Bufón después de engañar al pueblo.
```

### 7 — Rol exclusivo de Grecia

Archivo:

```text
Google Play/Capturas/Telefono/07_Poder_del_oraculo.png
```

Texto incorporado: `¡UNA VOZ REGRESA!`

Texto complementario: `El Oráculo devuelve una voz al debate por una ronda.`

Texto alternativo:

```text
El Oráculo de Grecia permite que una jugadora eliminada vuelva a hablar.
```

### 8 — Rol exclusivo de la Pampa

Archivo:

```text
Google Play/Capturas/Telefono/08_Contrapunto_del_payador.png
```

Texto incorporado: `¡COMIENZA EL CONTRAPUNTO!`

Texto complementario: `El Payador enfrenta dos voces y obliga al resto a escuchar.`

Texto alternativo:

```text
El Payador inicia un contrapunto entre dos jugadores durante el debate.
```

## 6. Declaraciones que no deben olvidarse

Estas opciones no forman parte de la ficha textual, pero Play Console las exige antes de publicar:

- marcar **Sí, contiene anuncios** solamente en la versión que efectivamente incorpore el SDK y muestre anuncios;
- declarar las compras digitales y usar Google Play Billing cuando se implementen;
- seleccionar público objetivo adolescente/adulto y no incluir grupos menores de 13 años;
- completar clasificación de contenido, seguridad de datos, política de privacidad y acceso a la aplicación;
- explicar que existen salas públicas, chat entre jugadores, silenciamiento local y moderación del anfitrión;
- revisar nuevamente las declaraciones cada vez que se agregue un SDK de anuncios, analítica o pagos.

La beta `0.1.13` todavía no contiene SDK de anuncios ni Google Play Billing. Las declaraciones deben describir lo que hace el binario subido, no solamente lo planeado para versiones futuras.

## 7. Revisión final antes de guardar la ficha

- Nombre visible: `Traidores`.
- Idioma predeterminado: `Español (Latinoamérica)`.
- Categoría sugerida: `Juego > Estrategia`.
- Etiquetas sugeridas, si Play Console las ofrece: deducción, multijugador, estrategia, juego casual.
- Correo de asistencia: `bandidogamesestudio@gmail.com`.
- Política de privacidad: `https://www.traidores.me/privacidad`.
- Eliminación de cuenta: `https://www.traidores.me/eliminar-cuenta`.
- Guardar como borrador y revisar la vista previa de teléfono antes de enviar a prueba cerrada.
