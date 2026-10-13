$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'server_trust.ps1')
$lab = Join-Path (Split-Path -Parent $PSScriptRoot) ('.artifacts\owner-131-trust-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $lab '.private'), (Join-Path $lab 'config') -Force | Out-Null
$key = 'synthetic-server-authority-131-0123456789'
$file = Join-Path $lab '.private\dedicated-trust.env'
$coreFile = Join-Path $lab 'config\muxi-game-core.json'
$utf8 = New-Object Text.UTF8Encoding($false)
$script:checks = 0
function Assert-Trust($value) { if (-not $value) { throw 'Trust test failed.' }; $script:checks++ }
function Write-Trust([string]$text) { [IO.File]::WriteAllText($file, $text, $utf8) }
function Write-Core([string]$authority) { [IO.File]::WriteAllText($coreFile, (@{features=@{terminalSso=@{enabled=$true;serverKey=$authority}}} | ConvertTo-Json -Depth 4), $utf8) }
function Reject-Trust {
    $failed = $false
    try { Import-MuxiServerTrust -ServerRoot $lab } catch {
        $failed = $true
        Assert-Trust ($_.Exception.Message -notlike ('*' + $key + '*'))
    }
    Assert-Trust $failed
    Assert-Trust (-not $env:MUXI_GAME_SOCIAL_ENABLED -and -not $env:MUXI_GAME_SOCIAL_URL -and -not $env:MUXI_GAME_SOCIAL_KEY)
}
$names = @('MUXI_GAME_SOCIAL_ENABLED', 'MUXI_GAME_SOCIAL_URL', 'MUXI_GAME_SOCIAL_KEY')
$saved = @{}
foreach ($n in $names) { $saved[$n] = [Environment]::GetEnvironmentVariable($n, 'Process') }
try {
    $env:MUXI_GAME_SOCIAL_ENABLED = '1'
    $env:MUXI_GAME_SOCIAL_KEY = $key
    Import-MuxiServerTrust -ServerRoot $lab
    Assert-Trust (-not $env:MUXI_GAME_SOCIAL_ENABLED -and -not $env:MUXI_GAME_SOCIAL_KEY)
    Write-Core $key
    $valid = "MUXI_GAME_SOCIAL_ENABLED=1`nMUXI_GAME_SOCIAL_URL=https://mc.muxigame.com/api/internal/game/`nMUXI_GAME_SOCIAL_KEY=$key`n"
    Write-Trust $valid
    Import-MuxiServerTrust -ServerRoot $lab
    Assert-Trust ($env:MUXI_GAME_SOCIAL_ENABLED -eq '1' -and $env:MUXI_GAME_SOCIAL_KEY -ceq $key)
    # A real child process inherits the key without receiving it in its command line.
    $probe = "if (`$env:MUXI_GAME_SOCIAL_ENABLED -eq '1' -and `$env:MUXI_GAME_SOCIAL_KEY.Length -ge 32) { exit 0 } else { exit 9 }"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($probe))
    & powershell.exe -NoProfile -EncodedCommand $encoded
    Assert-Trust ($LASTEXITCODE -eq 0)
    Write-Core 'synthetic-other-authority-0123456789'
    Reject-Trust
    Write-Core $key
    Write-Trust ($valid + "MUXI_GAME_SOCIAL_KEY=$key`n")
    Reject-Trust
    Write-Trust ($valid.Replace('https://mc.muxigame.com/api/internal/game/', 'https://attacker.example/api/internal/game/'))
    Reject-Trust
    Write-Trust ($valid.Replace($key, 'short'))
    Reject-Trust
    Write-Trust ($valid + 'not a dotenv assignment')
    Reject-Trust
    Write-Trust ('#' + ('x' * 65536))
    Reject-Trust
    Write-Trust "MUXI_GAME_SOCIAL_ENABLED=0`n"
    Import-MuxiServerTrust -ServerRoot $lab
    Assert-Trust (-not $env:MUXI_GAME_SOCIAL_ENABLED -and -not $env:MUXI_GAME_SOCIAL_KEY)
    Write-Trust ($valid.Replace("MUXI_GAME_SOCIAL_KEY=$key", "MUXI_GAME_SOCIAL_KEY='$key'") + 'UNRELATED_SETTING=ignored')
    Import-MuxiServerTrust -ServerRoot $lab
    Assert-Trust ($env:MUXI_GAME_SOCIAL_KEY -ceq $key)
    $tokens = $null; $parseErrors = $null
    foreach ($scriptFile in @('server_trust.ps1', 'start_trusted_mc.ps1', 'install_server_trust.ps1')) {
        [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $scriptFile), [ref]$tokens, [ref]$parseErrors)
        Assert-Trust ($parseErrors.Count -eq 0)
    }
    New-Item -ItemType Directory -Path (Join-Path $lab 'tools') | Out-Null
    foreach ($helper in @('server_trust.ps1', 'start_trusted_mc.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $helper) -Destination (Join-Path $lab 'tools') }
    $oldBat = 'start "Minecraft" cmd /c ""%JAVA%" @user_jvm_args.txt -Djdk.net.unixdomain.tmpdir=C:\Temp @libraries/net/neoforged/neoforge/21.1.250/win_args.txt nogui"'
    $oldPs = "& `$JavaExe '@user_jvm_args.txt' '@libraries/net/neoforged/neoforge/21.1.250/win_args.txt' nogui"
    [IO.File]::WriteAllText((Join-Path $lab 'start.bat'), $oldBat, $utf8)
    [IO.File]::WriteAllText((Join-Path $lab 'start-muxi.ps1'), $oldPs, $utf8)
    Write-Trust ($valid.Replace($key, 'synthetic-stale-social-authority-0123456789') + 'MUXI_TERMINAL_SSO_SERVER_KEY=synthetic-stale-terminal-authority-0123456789')
    & (Join-Path $PSScriptRoot 'install_server_trust.ps1') -ServerRoot $lab | Out-Null
    Assert-Trust ($env:MUXI_GAME_SOCIAL_KEY -ceq $key)
    $after = [IO.File]::ReadAllText($file)
    Assert-Trust ($after.Contains('MUXI_GAME_SOCIAL_KEY=' + $key) -and $after.Contains('MUXI_TERMINAL_SSO_SERVER_KEY=' + $key))
    Assert-Trust ([IO.File]::ReadAllText((Join-Path $lab 'start.bat')).Contains('tools\start_trusted_mc.ps1'))
    Assert-Trust ([IO.File]::ReadAllText((Join-Path $lab 'start-muxi.ps1')).Contains('Import-MuxiServerTrust -ServerRoot $PSScriptRoot'))
    & (Join-Path $PSScriptRoot 'install_server_trust.ps1') -ServerRoot $lab | Out-Null
    Assert-Trust ([IO.File]::ReadAllText($file) -ceq $after)
    Write-Output ('PASS: ' + $script:checks + ' trust bootstrap/install checks; synthetic files only, no Minecraft started.')
} finally {
    foreach ($n in $names) { [Environment]::SetEnvironmentVariable($n, $saved[$n], 'Process') }
}
