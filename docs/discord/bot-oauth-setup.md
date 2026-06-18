# Bot OAuth setup

Este flujo permite que Codex configure el servidor usando un bot autorizado por vos, sin pedir contrasena ni token personal de Discord.

## 1. Crear aplicacion

1. Abrir Discord Developer Portal.
2. Crear una nueva aplicacion llamada `Traidores Setup`.
3. Entrar a la seccion `Bot`.
4. Crear el bot.
5. Desactivar `Public Bot` si no quieres que otras personas lo inviten.
6. Copiar el token del bot solo cuando lo vayas a usar.

No pegues el token en chats, documentos, capturas ni archivos del repo.

## 2. Generar invitacion

Copiar el `Application ID` o `Client ID` de la aplicacion y ejecutar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\new-discord-bot-invite.ps1 -ClientId "CLIENT_ID_NUMERICO"
```

El script imprime una URL de Discord OAuth.

Abrir esa URL con tu cuenta de Discord y elegir el servidor `Traidores | Oficial`.

La URL usa:

- Scope `bot`
- Scope `applications.commands`
- Permiso `Administrator`

El permiso `Administrator` es temporal para construir roles, categorias y canales sin pelear con permisos intermedios. Despues de aplicar la configuracion, conviene quitar ese permiso o expulsar el bot.

## 3. Obtener ID del servidor

1. En Discord, activar `Modo desarrollador`.
2. Click derecho sobre el servidor `Traidores | Oficial`.
3. Copiar ID del servidor.

## 4. Aplicar estructura

En PowerShell, desde la raiz del proyecto:

```powershell
$env:DISCORD_GUILD_ID = "ID_DEL_SERVIDOR"
$env:DISCORD_BOT_TOKEN = "TOKEN_DEL_BOT"
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\apply-discord-server.ps1 -Apply
```

Si eso termina bien, publicar textos iniciales:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\discord\scripts\apply-discord-server.ps1 -Apply -PostMessages
```

## 5. Cerrar acceso

Despues de usarlo:

1. Quitar permisos administrativos al bot o expulsarlo.
2. Regenerar el token del bot en Developer Portal.
3. Cerrar la terminal donde estaba el token.

## Limitaciones

El script crea roles, categorias, canales y mensajes iniciales. Algunas cosas conviene terminarlas desde la UI de Discord o con bots especializados:

- Captcha.
- Botones de roles.
- Tickets.
- AutoMod avanzado.
- Pantalla de reglas de Community Server.

Esas partes estan especificadas en `implementation-checklist.md`.
