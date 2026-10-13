[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaExe)
$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $serverRoot
if (-not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) { throw 'Java executable missing.' }
. (Join-Path $PSScriptRoot 'server_trust.ps1')
Import-MuxiServerTrust -ServerRoot $serverRoot
& $JavaExe '@user_jvm_args.txt' '-Djdk.net.unixdomain.tmpdir=C:\Temp' '@libraries/net/neoforged/neoforge/21.1.250/win_args.txt' nogui
exit $LASTEXITCODE
