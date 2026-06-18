# Discord oficial de Traidores

Este paquete define la primera version operativa del servidor **Traidores | Oficial**.

El objetivo principal del servidor no es solo anunciar el juego: es que la gente entre, entienda rapido donde esta, encuentre partidas y se quede en una comunidad cuidada.

## Archivos

- `server-spec.md`: estructura, roles, permisos, bots y flujo de entrada.
- `channel-copy.md`: textos listos para pegar en canales, reglas, seguridad, busqueda de partida y tickets.
- `implementation-checklist.md`: pasos concretos para crear el servidor en Discord.
- `bot-oauth-setup.md`: pasos para crear e invitar el bot de configuracion sin compartir credenciales personales.
- `launch-kit.md`: textos de apertura, mensajes para redes y checklist final antes de publicar la invitacion.
- `assets/`: iconos y banner listos para Discord.
- `discord-server-manifest.json`: manifest estructurado de roles, categorias y canales.
- `scripts/new-discord-bot-invite.ps1`: genera el enlace OAuth para invitar el bot.
- `scripts/apply-discord-server.ps1`: aplicador para PowerShell usando la API oficial de Discord.
- `scripts/test-discord-server.ps1`: verifica roles, categorias y canales contra el manifest.
- `scripts/apply-discord-server.mjs`: aplicador alternativo para Node.js.

## Identidad visual

Usar los assets actuales del proyecto:

- Icono: `app/src/main/res/drawable/logo_traidores_clean.png` o `app/src/main/res/drawable/logo_traidores_transparente.webp`
- Icono recomendado para Discord: `docs/discord/assets/traidores-discord-icon-dark.png`
- Banner recomendado: `docs/discord/assets/traidores-discord-banner.png`
- Fondo oscuro: `#1a1510`
- Dorado principal: `#d4a24e`
- Rojo de advertencia/acento: `#8F2633`
- Texto claro: `#f0e6d2`

El tono del servidor debe ser claro, prolijo y de party game. Puede tener misterio y traicion en la ambientacion, pero la navegacion debe ser convencional para que nadie se pierda.

## Automatizacion

El camino principal en Windows es:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\apply-discord-server.ps1
```

Ese comando corre en modo dry-run y no toca Discord.

Para aplicar cambios reales hace falta:

1. Crear el servidor vacio `Traidores | Oficial`.
2. Crear una aplicacion/bot en Discord Developer Portal.
3. Generar la invitacion OAuth con `scripts/new-discord-bot-invite.ps1`.
4. Invitar el bot al servidor con permisos administrativos temporales.
5. Definir variables de entorno solo en la terminal actual:

```powershell
$env:DISCORD_GUILD_ID = "id-del-servidor"
$env:DISCORD_BOT_TOKEN = "token-del-bot"
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\apply-discord-server.ps1 -Apply
```

Para publicar tambien los textos iniciales:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\apply-discord-server.ps1 -Apply -PostMessages
```

No guardar el token en archivos. `.env` ya esta ignorado por Git por seguridad, pero lo mas seguro es mantener el token solo en la terminal y regenerarlo despues de usarlo.

Para auditar lo creado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\test-discord-server.ps1
```

El verificador comprueba roles, categorias y canales. Captcha, tickets, botones de roles, AutoMod y Community Server siguen siendo chequeos manuales.
