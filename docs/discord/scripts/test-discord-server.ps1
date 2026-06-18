param(
    [switch]$Json
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DiscordDir = Resolve-Path (Join-Path $ScriptDir "..")
$ManifestPath = Join-Path $DiscordDir "discord-server-manifest.json"

$Manifest = Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json
$GuildId = $env:DISCORD_GUILD_ID
$BotToken = $env:DISCORD_BOT_TOKEN
$ApiBase = "https://discord.com/api/v10"

if (-not $GuildId -or -not $BotToken) {
    throw "Set DISCORD_GUILD_ID and DISCORD_BOT_TOKEN before running this verifier."
}

function Invoke-DiscordApi([string]$Route) {
    return Invoke-RestMethod -Method GET -Uri "$ApiBase$Route" -Headers @{
        Authorization = "Bot $BotToken"
    }
}

function Find-ByName($Items, [string]$Name) {
    return @($Items | Where-Object { $_.name -eq $Name } | Select-Object -First 1)[0]
}

function Find-Channel($Channels, [string]$Name, [int]$Type) {
    $Expected = $Name.ToLowerInvariant()
    return @($Channels | Where-Object { $_.name -eq $Expected -and $_.type -eq $Type } | Select-Object -First 1)[0]
}

$ChannelTypes = @{
    text     = 0
    voice    = 2
    category = 4
    forum    = 15
}

$Roles = @(Invoke-DiscordApi "/guilds/$GuildId/roles")
$Channels = @(Invoke-DiscordApi "/guilds/$GuildId/channels")

$Results = [ordered]@{
    serverName = $Manifest.serverName
    roles = @()
    categories = @()
    channels = @()
    manualChecks = @(
        "Community Server enabled",
        "Rules screening enabled",
        "Captcha bot installed and configured",
        "Role button bot installed and configured",
        "Ticket bot installed and configured",
        "AutoMod configured",
        "Bot Administrator permission removed after setup",
        "Permanent invite added to Traidores.me"
    )
}

foreach ($Role in $Manifest.roles) {
    $Existing = Find-ByName $Roles $Role.name
    $Results.roles += [ordered]@{
        name = $Role.name
        exists = [bool]$Existing
        id = if ($Existing) { $Existing.id } else { $null }
    }
}

foreach ($Category in $Manifest.categories) {
    $ExistingCategory = Find-Channel $Channels $Category.name $ChannelTypes.category
    $Results.categories += [ordered]@{
        name = $Category.name
        exists = [bool]$ExistingCategory
        id = if ($ExistingCategory) { $ExistingCategory.id } else { $null }
    }

    foreach ($Channel in $Category.channels) {
        $Type = $ChannelTypes[$Channel.type]
        $ExistingChannel = Find-Channel $Channels $Channel.name $Type
        $Results.channels += [ordered]@{
            category = $Category.name
            name = $Channel.name
            type = $Channel.type
            exists = [bool]$ExistingChannel
            id = if ($ExistingChannel) { $ExistingChannel.id } else { $null }
        }
    }
}

$MissingRoles = @($Results.roles | Where-Object { -not $_.exists })
$MissingCategories = @($Results.categories | Where-Object { -not $_.exists })
$MissingChannels = @($Results.channels | Where-Object { -not $_.exists })
$Passed = $MissingRoles.Count -eq 0 -and $MissingCategories.Count -eq 0 -and $MissingChannels.Count -eq 0

$Results.summary = [ordered]@{
    passed = $Passed
    missingRoles = $MissingRoles.Count
    missingCategories = $MissingCategories.Count
    missingChannels = $MissingChannels.Count
}

if ($Json) {
    $Results | ConvertTo-Json -Depth 8
    exit $(if ($Passed) { 0 } else { 1 })
}

Write-Output "Discord server verification: $($Manifest.serverName)"
Write-Output "Roles missing: $($MissingRoles.Count)"
Write-Output "Categories missing: $($MissingCategories.Count)"
Write-Output "Channels missing: $($MissingChannels.Count)"

if ($MissingRoles.Count -gt 0) {
    Write-Output ""
    Write-Output "Missing roles:"
    $MissingRoles | ForEach-Object { Write-Output "- $($_.name)" }
}

if ($MissingCategories.Count -gt 0) {
    Write-Output ""
    Write-Output "Missing categories:"
    $MissingCategories | ForEach-Object { Write-Output "- $($_.name)" }
}

if ($MissingChannels.Count -gt 0) {
    Write-Output ""
    Write-Output "Missing channels:"
    $MissingChannels | ForEach-Object { Write-Output "- [$($_.category)] $($_.name) ($($_.type))" }
}

Write-Output ""
Write-Output "Manual checks still required:"
$Results.manualChecks | ForEach-Object { Write-Output "- $_" }

exit $(if ($Passed) { 0 } else { 1 })
