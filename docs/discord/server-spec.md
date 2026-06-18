# Especificacion del servidor Discord

## Principios

1. El servidor es principalmente hispanohablante.
2. La seccion en ingles existe, pero va agrupada al final.
3. No se crean roles publicos de edad.
4. No se permite buscar gente para encuentros presenciales. El modo LAN se explica como modo para amigos, familia o gente que ya se conoce y comparte la misma red.
5. El servidor debe funcionar aunque el juego siga en desarrollo.
6. La busqueda de partida debe ser facil desde el primer dia, sin depender de un bot propio.
7. El bot oficial de Traidores queda para una segunda etapa.

## Nombre

`Traidores | Oficial`

## Configuracion base

- Tipo: servidor de comunidad.
- Verificacion: captcha con bot y normas aceptadas antes de desbloquear canales.
- Edad minima comunicada: Discord requiere 13+.
- DM safety: recomendar a todos cerrar mensajes privados del servidor si no quieren recibir DMs.
- Invitacion publica: debe agregarse a `Traidores.me` cuando el servidor este listo.

## Categorias y canales

### EMPEZA AQUI

- `#bienvenida`
- `#reglas`
- `#seguridad`
- `#elige-tus-roles`
- `#como-jugar`
- `#sitio-oficial`

Permisos:

- `@everyone`: puede ver solo esta categoria antes de aceptar reglas.
- `Miembro`: puede ver el resto del servidor.
- Solo equipo: puede escribir en `#bienvenida`, `#reglas`, `#seguridad`, `#como-jugar`, `#sitio-oficial`.

### INFORMACION OFICIAL

- `#anuncios`
- `#actualizaciones`
- `#hoja-de-ruta`
- `#estado-del-juego`
- `#encuestas`

Permisos:

- `Fundador`, `Administrador`, `Moderador`, `Equipo de desarrollo`: pueden publicar segun necesidad.
- `Miembro`: puede leer y reaccionar.
- En `#encuestas`, los miembros pueden votar. La publicacion de encuestas queda para el equipo.

### BUSCAR PARTIDA

- `#buscar-partida`
- `#como-buscar-partida`
- `Sala 1`
- `Sala 2`
- `Sala 3`
- `Sala 4`
- `Sala 5`

`#buscar-partida` debe ser un canal de foro si Discord lo permite. Cada publicacion representa una busqueda. Si se usa canal de texto, fijar el formato y pedir que las busquedas se cierren manualmente.

Formato de busqueda:

```text
Horario:
Jugadores buscados:
Nivel: Casual / Competitivo
Codigo de sala:
Notas:
```

Configuracion recomendada:

- Autoarchivar publicaciones despues de 24 horas.
- Permitir codigo de sala publico en la primera version.
- Usar rol de mencion opcional `Busco partida`.
- Modo lento bajo si hay spam.

### COMUNIDAD

- `#chat-general`
- `#presentaciones`
- `#jugadas-y-anecdotas`
- `#memes`
- `#sugerencias`
- `#off-topic`
- `#humor-sin-filtro`

`#humor-sin-filtro` no significa sin reglas. Permite lenguaje fuerte y humor mas relajado, pero mantiene prohibiciones de acoso, discriminacion, contenido sexual, gore, politica, religion, doxxing y ataques personales.

### DESARROLLO Y SOPORTE

- `#avances-del-desarrollo`
- `#reportar-un-error`
- `#preguntas-y-ayuda`
- Sistema de tickets:
  - Error
  - Denuncia
  - Ayuda
  - Apelacion

Los tickets de denuncia deben ser privados para el usuario, fundador y moderadores autorizados. Si la denuncia es contra un moderador, la revisa el fundador.

### ENGLISH

Esta categoria va al final.

- `#english-welcome`
- `#english-rules`
- `#english-chat`
- `#find-a-game`
- `English Lobby 1`
- `English Lobby 2`

La seccion en ingles es funcional, no una copia completa del servidor. Su objetivo inicial es que quien no hable espanol pueda entender el juego, buscar partida y hablar sin quedar perdido.

## Roles

### Equipo

- `Fundador`
- `Administrador`
- `Moderador`
- `Equipo de desarrollo`

### Comunidad

- `Miembro`
- `Espanol`
- `English`
- `Android`
- `iOS`
- `Busco partida`
- `Looking for game`

No crear roles `13-17` ni `18+`.

## Colores de roles

- `Fundador`: `#d4a24e`
- `Administrador`: `#8F2633`
- `Moderador`: `#4a7fb5`
- `Equipo de desarrollo`: `#8a5fbf`
- `Miembro`: `#c4b69c`
- `English`: `#5a8a3c`
- `Busco partida`: `#e8b84b`

## Bots recomendados para primera version

Elegir bots conocidos antes de desarrollar uno propio:

- Captcha/anti-raid: Wick, Security, Captcha.bot o equivalente confiable.
- Roles por botones: Carl-bot, Sapphire, YAGPDB o equivalente.
- Tickets: Ticket Tool o equivalente.
- Moderacion: Dyno, Carl-bot, Sapphire o equivalente.

No instalar demasiados bots al principio. Prioridad:

1. Captcha y seguridad.
2. Roles por botones.
3. Tickets.
4. Moderacion/logs basicos.

## Bot oficial de Traidores, segunda etapa

Cuando haya actividad suficiente, el bot oficial podria:

1. Recibir una solicitud de buscar partida.
2. Preguntar idioma, horario y cantidad de jugadores.
3. Agrupar interesados.
4. Publicar o compartir codigo de sala.
5. Sugerir una sala de voz libre.
6. Cerrar la busqueda cuando se complete.

No conviene empezar por este bot porque un sistema automatico con poca gente puede hacer que el servidor parezca vacio.

## Reglas de moderacion

Expulsion o ban inmediato:

- Grooming o conducta sexual hacia menores.
- Solicitar, publicar o presionar por datos personales.
- Amenazas graves.
- Doxxing.
- Contenido sexual explicito o gore.
- Discriminacion grave.
- Estafas, malware o suplantacion.
- Acoso coordinado.

Faltas progresivas:

- Spam.
- Insultos personales.
- Provocaciones repetidas.
- Publicidad no autorizada.
- Desorden persistente en canales de busqueda.

Escalado sugerido:

1. Advertencia.
2. Timeout.
3. Expulsion.
4. Ban.

## Permisos criticos

- Nadie salvo equipo debe poder mencionar `@everyone` o `@here`.
- Los nuevos miembros no deben poder cambiar apodos de otros, administrar hilos, crear invitaciones masivas ni adjuntar archivos ejecutables.
- Los usuarios pueden subir imagenes desde el inicio, pero AutoMod debe bloquear enlaces sospechosos, spam y menciones masivas.
- Las salas de voz deben permitir entrar, hablar y compartir pantalla solo a miembros verificados.
