# Checklist de implementacion

## 1. Crear servidor

- Crear servidor llamado `Traidores | Oficial`.
- Subir icono desde `app/src/main/res/drawable/logo_traidores_clean.png`.
- Activar Community Server.
- Configurar idioma principal: Espanol.
- Crear invitacion permanente solo cuando la estructura este lista.

## 1.5. Opcion automatizada

Si se va a usar el aplicador:

- Crear una aplicacion en Discord Developer Portal.
- Crear un bot dentro de esa aplicacion.
- Generar la invitacion del bot:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\new-discord-bot-invite.ps1 -ClientId "CLIENT_ID_NUMERICO"
```

- Abrir la URL generada e invitar el bot al servidor con permisos administrativos temporales.
- Copiar el ID del servidor con modo desarrollador de Discord.
- Ejecutar primero el dry-run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\apply-discord-server.ps1
```

- Aplicar roles y canales:

```powershell
$env:DISCORD_GUILD_ID = "id-del-servidor"
$env:DISCORD_BOT_TOKEN = "token-del-bot"
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\apply-discord-server.ps1 -Apply
```

- Publicar textos iniciales si la estructura quedo bien:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\apply-discord-server.ps1 -Apply -PostMessages
```

- Quitar permisos administrativos del bot o expulsarlo si ya no se necesita.

## 2. Crear roles

Equipo:

- `Fundador`
- `Administrador`
- `Moderador`
- `Equipo de desarrollo`

Comunidad:

- `Miembro`
- `Espanol`
- `English`
- `Android`
- `iOS`
- `Busco partida`
- `Looking for game`

Revisar que solo el equipo tenga permisos administrativos.

## 3. Crear categorias y canales

Crear en este orden:

1. `EMPEZA AQUI`
2. `INFORMACION OFICIAL`
3. `BUSCAR PARTIDA`
4. `COMUNIDAD`
5. `DESARROLLO Y SOPORTE`
6. `ENGLISH`

La categoria `ENGLISH` debe quedar al final.

## 4. Configurar entrada

- Activar pantalla de reglas de Discord Community.
- Instalar bot de captcha.
- Configurar rol `Miembro` al completar captcha y aceptar reglas.
- Antes de `Miembro`, el usuario solo ve `EMPEZA AQUI`.

## 5. Configurar roles por botones

Botones necesarios:

- `Espanol`
- `English`
- `Android`
- `iOS`
- `Busco partida`
- `Looking for game`

No preguntar edad ni crear roles de edad.

## 6. Configurar busqueda de partida

- Crear `#buscar-partida` como foro.
- Autoarchivar publicaciones a las 24 horas.
- Fijar formato de busqueda desde `channel-copy.md`.
- Permitir mencionar `@Busco partida`.
- Crear cinco salas de voz: `Sala 1` a `Sala 5`.

## 7. Configurar seccion inglesa

- Crear `#english-welcome`, `#english-rules`, `#english-chat`, `#find-a-game`.
- Crear `English Lobby 1` y `English Lobby 2`.
- Mantener esta categoria al final.
- Permitir acceso a usuarios con rol `English`.

## 8. Configurar tickets

Tipos:

- Error
- Denuncia
- Ayuda
- Apelacion

Permisos:

- Denuncias: usuario + fundador + moderadores autorizados.
- Apelaciones: usuario + fundador + moderadores autorizados.
- Errores y ayuda: usuario + equipo.

## 9. Configurar AutoMod

Bloquear o revisar:

- Menciones masivas.
- Spam de enlaces.
- Invitaciones a otros servidores no autorizadas.
- Palabras o frases de acoso grave.
- Contenido sexual explicito.
- Doxxing evidente.

Desactivar permisos de `@everyone` para mencionar `@everyone` y `@here`.

## 10. Publicar textos

Copiar textos desde `channel-copy.md` en:

- `#bienvenida`
- `#reglas`
- `#seguridad`
- `#elige-tus-roles`
- `#como-jugar`
- `#como-buscar-partida`
- `#humor-sin-filtro`
- Canales ingleses
- Plantillas de tickets

## 11. Revisión antes de abrir

- Ejecutar verificador automatico:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\test-discord-server.ps1
```

- Entrar con cuenta de prueba o vista previa como miembro nuevo.
- Confirmar que un nuevo usuario no ve canales internos antes de aceptar reglas.
- Confirmar que `Miembro` ve las categorias correctas.
- Confirmar que `English` ve la seccion inglesa.
- Confirmar que miembros no pueden usar `@everyone`.
- Confirmar que tickets son privados.
- Confirmar que busqueda de partida archiva a las 24 horas.

## 12. Publicacion

- Crear invitacion permanente.
- Agregarla a `Traidores.me`.
- Usar los textos finales de `launch-kit.md`.
- Publicar primer anuncio:

```text
Bienvenidos a Traidores | Oficial.

Este servidor abre para reunir a la comunidad antes del lanzamiento completo. Ya pueden presentarse, seguir avances, reportar errores y usar la seccion de busqueda cuando haya partidas disponibles.

Gracias por estar desde el principio.
```
