<#
  set-name.ps1 - choose the name in the greeting ("Good evening, <name>").
  By default Glass Home uses the TV's name (Settings > My Fire TV > About), so
  "SAM's Fire TV" shows "Sam".

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\set-name.ps1 Sam
    powershell -ExecutionPolicy Bypass -File scripts\set-name.ps1 -Default   # use the TV's name again
#>
[CmdletBinding(PositionalBinding = $false)]
param(
  [Parameter(Position = 0)][string]$Name,
  [string]$Device,
  [switch]$Default
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tv-common.ps1')
$Device = Get-TvDevice $Device
if (-not $Default -and -not $Name) { throw "Pass a name, or -Default" }
$value = if ($Default) { '' } else { $Name }
Invoke-Tv $Device shell am broadcast -n dev.glasshome.launcher/.ConfigReceiver `
  -a dev.glasshome.launcher.SET_NAME --es name "'$value'" | Out-Null
if ($Default) { Write-Host "Greeting uses the TV's name again" } else { Write-Host "Greeting name set to: $Name" }
