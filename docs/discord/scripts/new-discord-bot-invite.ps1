param(
    [Parameter(Mandatory = $true)]
    [string]$ClientId,

    [string]$Permissions = "8"
)

$ErrorActionPreference = "Stop"

if ($ClientId -notmatch '^\d{15,25}$') {
    throw "ClientId must be the numeric Application ID from Discord Developer Portal."
}

$Scopes = [uri]::EscapeDataString("bot applications.commands")
$InviteUrl = "https://discord.com/oauth2/authorize?client_id=$ClientId&permissions=$Permissions&scope=$Scopes"

Write-Output "Open this URL while logged into the Discord account that owns Traidores | Oficial:"
Write-Output $InviteUrl
Write-Output ""
Write-Output "Security note: permissions=8 means Administrator. Use it only for setup, then remove Administrator from the bot or kick the bot."
