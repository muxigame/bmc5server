function Import-MuxiServerTrust {
    [CmdletBinding()]
    param([Parameter(Mandatory=$true)][string]$ServerRoot)
    # Process scope only: Java inherits these values; secrets never become arguments.
    $names = @('MUXI_GAME_SOCIAL_ENABLED', 'MUXI_GAME_SOCIAL_URL', 'MUXI_GAME_SOCIAL_KEY')
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    $file = Join-Path $ServerRoot '.private\dedicated-trust.env'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return }
    try {
        if ((Get-Item -LiteralPath $file).Length -gt 65536) { throw 'Invalid' }
        $values = @{}
        foreach ($raw in [IO.File]::ReadAllLines($file)) {
            $line = $raw.Trim()
            if (-not $line -or $line.StartsWith('#')) { continue }
            if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid' }
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($names -notcontains $name) { continue }
            if ($values.ContainsKey($name)) { throw 'Invalid' }
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $values[$name] = $value
        }
        if ($values['MUXI_GAME_SOCIAL_ENABLED'] -notin @('0', '1')) { throw 'Invalid' }
        if ($values['MUXI_GAME_SOCIAL_ENABLED'] -eq '0') { return }
        if ($values['MUXI_GAME_SOCIAL_URL'] -cne 'https://mc.muxigame.com/api/internal/game/') { throw 'Invalid' }
        $key = [string]$values['MUXI_GAME_SOCIAL_KEY']
        if ($key -notmatch '^[\x21-\x7e]{32,512}$') { throw 'Invalid' }
        $coreFile = Join-Path $ServerRoot 'config\muxi-game-core.json'
        if ((Get-Item -LiteralPath $coreFile).Length -gt 65536) { throw 'Invalid' }
        $core = [IO.File]::ReadAllText($coreFile) | ConvertFrom-Json
        if ($core.features.terminalSso.enabled -ne $true -or $key -cne [string]$core.features.terminalSso.serverKey) { throw 'Invalid' }
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process') }
    } catch {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
        throw 'Invalid server trust configuration; check the private social file and matching terminal SSO settings (values redacted).'
    }
}
