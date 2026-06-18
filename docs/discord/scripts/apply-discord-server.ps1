param(
    [switch]$Apply,
    [switch]$PostMessages
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DiscordDir = Resolve-Path (Join-Path $ScriptDir "..")
$ManifestPath = Join-Path $DiscordDir "discord-server-manifest.json"
$CopyPath = Join-Path $DiscordDir "channel-copy.md"

$Manifest = Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json
$ChannelCopy = Get-Content -Raw -LiteralPath $CopyPath

$GuildId = $env:DISCORD_GUILD_ID
$BotToken = $env:DISCORD_BOT_TOKEN
$ApiBase = "https://discord.com/api/v10"

$PermissionBits = @{
    Administrator      = [uint64]0x0000000000000008
    ViewChannel        = [uint64]0x0000000000000400
    SendMessages       = [uint64]0x0000000000000800
    ManageMessages     = [uint64]0x0000000000002000
    KickMembers        = [uint64]0x0000000000000002
    BanMembers         = [uint64]0x0000000000000004
    ModerateMembers    = [uint64]0x0000010000000000
    ReadMessageHistory = [uint64]0x0000000000010000
    AddReactions       = [uint64]0x0000000000000040
    AttachFiles        = [uint64]0x0000000000008000
    Connect            = [uint64]0x0000000000100000
    Speak              = [uint64]0x0000000000200000
}

$ChannelTypes = @{
    text     = 0
    voice    = 2
    category = 4
    forum    = 15
}

function Show-Usage {
    Write-Host @"
Usage:
  .\docs\discord\scripts\apply-discord-server.ps1
  .\docs\discord\scripts\apply-discord-server.ps1 -Apply
  .\docs\discord\scripts\apply-discord-server.ps1 -Apply -PostMessages

Required env for -Apply:
  `$env:DISCORD_BOT_TOKEN = "bot token from Discord Developer Portal"
  `$env:DISCORD_GUILD_ID = "server id for Traidores | Oficial"

Default mode is dry-run and does not call Discord.
"@
}

function ConvertTo-DiscordColor([string]$Hex) {
    return [Convert]::ToInt32($Hex.Replace("#", ""), 16)
}

function Get-Permissions($Names) {
    [uint64]$Mask = 0
    foreach ($Name in $Names) {
        if (-not $PermissionBits.ContainsKey($Name)) {
            throw "Unknown permission: $Name"
        }
        $Mask = $Mask -bor $PermissionBits[$Name]
    }
    return "$Mask"
}

function Invoke-DiscordApi([string]$Method, [string]$Route, $Body = $null) {
    if (-not $Apply) {
        Write-Host "[dry-run] $Method $Route"
        if ($null -ne $Body) {
            Write-Host ($Body | ConvertTo-Json -Depth 12)
        }
        return $null
    }

    if (-not $BotToken -or -not $GuildId) {
        Show-Usage
        throw "DISCORD_BOT_TOKEN and DISCORD_GUILD_ID are required when using -Apply."
    }

    $Headers = @{
        Authorization = "Bot $BotToken"
        "Content-Type" = "application/json"
    }

    $Params = @{
        Method  = $Method
        Uri     = "$ApiBase$Route"
        Headers = $Headers
    }

    if ($null -ne $Body) {
        $Params.Body = ($Body | ConvertTo-Json -Depth 12)
    }

    return Invoke-RestMethod @Params
}

function Get-GuildState {
    if (-not $Apply) {
        return @{
            roles = @()
            channels = @()
        }
    }

    return @{
        roles = @(Invoke-DiscordApi "GET" "/guilds/$GuildId/roles")
        channels = @(Invoke-DiscordApi "GET" "/guilds/$GuildId/channels")
    }
}

function Find-RoleByName($Roles, [string]$Name) {
    return @($Roles | Where-Object { $_.name -eq $Name } | Select-Object -First 1)[0]
}

function Find-ChannelByName($Channels, [string]$Name, [int]$Type) {
    $Normalized = $Name.ToLowerInvariant()
    return @($Channels | Where-Object { $_.name -eq $Normalized -and $_.type -eq $Type } | Select-Object -First 1)[0]
}

function New-TeamOverwrite([string]$RoleId) {
    return @{
        id = $RoleId
        type = 0
        allow = Get-Permissions @("ViewChannel", "SendMessages", "ReadMessageHistory", "AddReactions", "AttachFiles", "Connect", "Speak")
    }
}

function New-DenyEveryone {
    return @{
        id = if ($GuildId) { $GuildId } else { "GUILD_ID" }
        type = 0
        deny = Get-Permissions @("ViewChannel")
    }
}

function New-AllowMember([string]$RoleId) {
    return @{
        id = $RoleId
        type = 0
        allow = Get-Permissions @("ViewChannel", "SendMessages", "ReadMessageHistory", "AddReactions", "AttachFiles", "Connect", "Speak")
    }
}

function New-ReadOnlyMember([string]$RoleId) {
    return @{
        id = $RoleId
        type = 0
        allow = Get-Permissions @("ViewChannel", "ReadMessageHistory", "AddReactions")
        deny = Get-Permissions @("SendMessages")
    }
}

function Get-CategoryOverwrites([string]$Access, $RoleIds) {
    $Overwrites = @()

    if ($Access -ne "public-read") {
        $Overwrites += New-DenyEveryone
    }

    if ($Access -eq "public-read") {
        $Overwrites += @{
            id = if ($GuildId) { $GuildId } else { "GUILD_ID" }
            type = 0
            allow = Get-Permissions @("ViewChannel", "ReadMessageHistory")
            deny = Get-Permissions @("SendMessages")
        }
    }

    if ($Access -eq "members") {
        $Overwrites += New-AllowMember $RoleIds["Miembro"]
    }

    if ($Access -eq "english") {
        $Overwrites += New-AllowMember $RoleIds["English"]
    }

    foreach ($Name in @("Fundador", "Administrador", "Moderador", "Equipo de desarrollo")) {
        if ($RoleIds.ContainsKey($Name)) {
            $Overwrites += New-TeamOverwrite $RoleIds[$Name]
        }
    }

    return $Overwrites
}

function Get-ChannelOverwrites($Channel, [string]$CategoryAccess, $RoleIds) {
    if (-not $Channel.locked) {
        return $null
    }

    $Overwrites = @()

    if ($CategoryAccess -eq "public-read") {
        $Overwrites += @{
            id = if ($GuildId) { $GuildId } else { "GUILD_ID" }
            type = 0
            allow = Get-Permissions @("ViewChannel", "ReadMessageHistory")
            deny = Get-Permissions @("SendMessages")
        }
    } else {
        $Overwrites += New-ReadOnlyMember $RoleIds["Miembro"]
    }

    if ($CategoryAccess -eq "english") {
        $Overwrites += New-ReadOnlyMember $RoleIds["English"]
    }

    foreach ($Name in @("Fundador", "Administrador", "Moderador", "Equipo de desarrollo")) {
        if ($RoleIds.ContainsKey($Name)) {
            $Overwrites += New-TeamOverwrite $RoleIds[$Name]
        }
    }

    return $Overwrites
}

function Get-CopyForHeading([string]$Heading) {
    $Start = $ChannelCopy.IndexOf($Heading)
    if ($Start -lt 0) {
        throw "Heading not found in channel-copy.md: $Heading"
    }

    $ContentStart = $Start + $Heading.Length
    $Next = $ChannelCopy.IndexOf("`n## ", $ContentStart)
    if ($Next -lt 0) {
        return $ChannelCopy.Substring($ContentStart).Trim()
    }

    return $ChannelCopy.Substring($ContentStart, $Next - $ContentStart).Trim()
}

function Ensure-Roles($ExistingRoles) {
    $RoleIds = @{}

    foreach ($Role in $Manifest.roles) {
        $Existing = Find-RoleByName $ExistingRoles $Role.name
        $Payload = @{
            name = $Role.name
            color = ConvertTo-DiscordColor $Role.color
            hoist = [bool]$Role.hoist
            mentionable = [bool]$Role.mentionable
            permissions = Get-Permissions $Role.permissions
        }

        if ($Existing) {
            $RoleIds[$Role.name] = $Existing.id
            Write-Host "Role exists: $($Role.name)"
            continue
        }

        $Created = Invoke-DiscordApi "POST" "/guilds/$GuildId/roles" $Payload
        $RoleIds[$Role.name] = if ($Created) { $Created.id } else { "ROLE:$($Role.name)" }
        Write-Host "Role created: $($Role.name)"
    }

    return $RoleIds
}

function Ensure-Channels($ExistingChannels, $RoleIds) {
    $ChannelIds = @{}
    $Position = 0

    foreach ($Category in $Manifest.categories) {
        $CategoryPayload = @{
            name = $Category.name
            type = $ChannelTypes.category
            position = $Position
            permission_overwrites = Get-CategoryOverwrites $Category.access $RoleIds
        }
        $Position++

        $CategoryId = $null
        $ExistingCategory = Find-ChannelByName $ExistingChannels $Category.name $ChannelTypes.category
        if ($ExistingCategory) {
            $CategoryId = $ExistingCategory.id
            Write-Host "Category exists: $($Category.name)"
        } else {
            $CreatedCategory = Invoke-DiscordApi "POST" "/guilds/$GuildId/channels" $CategoryPayload
            $CategoryId = if ($CreatedCategory) { $CreatedCategory.id } else { "CATEGORY:$($Category.name)" }
            Write-Host "Category created: $($Category.name)"
        }

        foreach ($Channel in $Category.channels) {
            $Type = $ChannelTypes[$Channel.type]
            $Existing = Find-ChannelByName $ExistingChannels $Channel.name $Type
            if ($Existing) {
                $ChannelIds[$Channel.name] = $Existing.id
                Write-Host "Channel exists: $($Channel.name)"
                continue
            }

            $Payload = @{
                name = $Channel.name
                type = $Type
                parent_id = $CategoryId
                topic = $Channel.topic
                position = $Position
                default_auto_archive_duration = $Channel.autoArchiveDuration
                permission_overwrites = Get-ChannelOverwrites $Channel $Category.access $RoleIds
            }
            $Position++

            $Created = Invoke-DiscordApi "POST" "/guilds/$GuildId/channels" $Payload
            $ChannelIds[$Channel.name] = if ($Created) { $Created.id } else { "CHANNEL:$($Channel.name)" }
            Write-Host "Channel created: $($Channel.name)"
        }
    }

    return $ChannelIds
}

function Post-SeedMessages($ChannelIds) {
    if (-not $PostMessages) {
        return
    }

    foreach ($Item in $Manifest.messageSources) {
        if (-not $ChannelIds.ContainsKey($Item.channel)) {
            Write-Warning "Skipping message for $($Item.channel): channel id not found."
            continue
        }

        $Content = Get-CopyForHeading $Item.heading
        if (-not $Content) {
            Write-Warning "Skipping message for $($Item.channel): empty content."
            continue
        }

        Invoke-DiscordApi "POST" "/channels/$($ChannelIds[$Item.channel])/messages" @{ content = $Content } | Out-Null
        Write-Host "Posted seed message: $($Item.channel)"
    }
}

Write-Host "$(if ($Apply) { "Apply" } else { "Dry-run" }) Traidores Discord manifest"
if (-not $Apply) {
    Show-Usage
}

$State = Get-GuildState
$RoleIds = Ensure-Roles $State.roles
$RefreshedState = if ($Apply) { Get-GuildState } else { $State }
$ChannelIds = Ensure-Channels $RefreshedState.channels $RoleIds
Post-SeedMessages $ChannelIds

Write-Host "Done."
