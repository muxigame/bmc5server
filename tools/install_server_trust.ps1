[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$ServerRoot, [switch]$ConfigurationOnly)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($ServerRoot)
$private = Join-Path $root '.private'
$coreFile = Join-Path $root 'config\muxi-game-core.json'
$envFile = Join-Path $private 'dedicated-trust.env'
$batFile = Join-Path $root 'start.bat'
$psFile = Join-Path $root 'start-muxi.ps1'
$utf8 = New-Object Text.UTF8Encoding($false)
$targetFiles = if ($ConfigurationOnly) { @($envFile) } else { @($envFile, $batFile, $psFile) }
# Validate every precondition before writing any production file.
$handles = @()
try {
    foreach ($file in $targetFiles) {
        $handles += [IO.File]::Open($file, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
    }
} catch {
    throw 'Server launch files are locked or not writable; gracefully stop the existing server/controller before installation. No files changed.'
} finally {
    foreach ($handle in $handles) { $handle.Dispose() }
}
foreach ($helper in @('server_trust.ps1', 'start_trusted_mc.ps1')) {
    if (-not (Test-Path -LiteralPath (Join-Path $root ('tools\' + $helper)))) { throw 'Install the checked launch helpers first.' }
}
$core = [IO.File]::ReadAllText($coreFile) | ConvertFrom-Json
$key = [string]$core.features.terminalSso.serverKey
if ($core.features.terminalSso.enabled -ne $true -or $key -notmatch '^[\x21-\x7e]{32,512}$') { throw 'Existing terminal SSO authority unavailable (values redacted).' }
$envText = [IO.File]::ReadAllText($envFile)
$batText = [IO.File]::ReadAllText($batFile)
$psText = [IO.File]::ReadAllText($psFile)
$oldBat = 'start "Minecraft" cmd /c ""%JAVA%" @user_jvm_args.txt -Djdk.net.unixdomain.tmpdir=C:\Temp @libraries/net/neoforged/neoforge/21.1.250/win_args.txt nogui"'
$newBat = 'start "Minecraft" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\start_trusted_mc.ps1" -JavaExe "%JAVA%"'
$oldPs = "& `$JavaExe '@user_jvm_args.txt' '@libraries/net/neoforged/neoforge/21.1.250/win_args.txt' nogui"
$newPs = ". (Join-Path `$PSScriptRoot 'tools\server_trust.ps1')`r`nImport-MuxiServerTrust -ServerRoot `$PSScriptRoot`r`n" + $oldPs
if (-not $batText.Contains($newBat)) {
    if ($batText.Split(@($oldBat), [StringSplitOptions]::None).Count -ne 2) { throw 'Unexpected batch launch entry; review before patching.' }
    $batText = $batText.Replace($oldBat, $newBat)
}
if (-not $psText.Contains('Import-MuxiServerTrust -ServerRoot $PSScriptRoot')) {
    if ($psText.Split(@($oldPs), [StringSplitOptions]::None).Count -ne 2) { throw 'Unexpected PowerShell launch entry; review before patching.' }
    $psText = $psText.Replace($oldPs, $newPs)
}
foreach ($name in @('MUXI_GAME_SOCIAL_ENABLED', 'MUXI_GAME_SOCIAL_URL', 'MUXI_GAME_SOCIAL_KEY', 'MUXI_TERMINAL_SSO_SERVER_KEY')) {
    if ([regex]::Matches($envText, ('(?m)^' + $name + '=.*$')).Count -ne 1) { throw 'Unexpected private trust schema (values redacted).' }
}
$replacement = @{
    MUXI_GAME_SOCIAL_ENABLED = '1'
    MUXI_GAME_SOCIAL_URL = 'https://mc.muxigame.com/api/internal/game/'
    MUXI_GAME_SOCIAL_KEY = $key
    MUXI_TERMINAL_SSO_SERVER_KEY = $key
}
foreach ($name in $replacement.Keys) {
    # MatchEvaluator treats key characters literally; never use replacement interpolation.
    $entry = $name + '=' + $replacement[$name]
    $envText = [regex]::Replace($envText, ('(?m)^' + $name + '=[^\r\n]*'), [Text.RegularExpressions.MatchEvaluator]{ param($m) $entry })
}
$backup = Join-Path $private ('trust-backup-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach ($file in $targetFiles) { Copy-Item -LiteralPath $file -Destination $backup }
# Existing files retain their ACL; backup stays inside the private server directory.
try {
    [IO.File]::WriteAllText($envFile, $envText, $utf8)
    if (-not $ConfigurationOnly) {
        [IO.File]::WriteAllText($batFile, $batText, $utf8)
        [IO.File]::WriteAllText($psFile, $psText, $utf8)
    }
    . (Join-Path $root 'tools\server_trust.ps1')
    Import-MuxiServerTrust -ServerRoot $root
} catch {
    $restored = $true
    foreach ($file in $targetFiles) {
        $original = Join-Path $backup ([IO.Path]::GetFileName($file))
        try { [IO.File]::WriteAllBytes($file, [IO.File]::ReadAllBytes($original)) } catch { $restored = $false }
    }
    if (-not $restored) { throw 'Server trust installation failed; recovery backup retained in the private server directory. Check launch file locks (values redacted).' }
    throw 'Server trust installation failed; original files restored (values redacted).'
}
if ($ConfigurationOnly) { Write-Output 'Installed unified server authority; existing launch patches deferred due to controller lock. No runtime restart performed.' }
else { Write-Output 'Installed: one existing server SSO/social authority, persistent process-only launch environment. No runtime restart performed.' }
