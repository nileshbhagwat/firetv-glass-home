<#
  set-style.ps1 - choose the home style from the laptop (the remote's Menu button does the same).

  Styles: classic, ring (gallery ring), depth (depth clock), breakout (break-out card),
  mosaic (glass mosaic), cinema, spotlight, aura.

  Usage (from the project folder):
    powershell -ExecutionPolicy Bypass -File scripts\set-style.ps1 ring
#>
[CmdletBinding(PositionalBinding = $false)]
param(
  [Parameter(Position = 0, Mandatory = $true)]
  [ValidateSet('classic', 'ring', 'depth', 'breakout', 'mosaic', 'cinema', 'spotlight', 'aura')]
  [string]$Style,
  [string]$Device
)
. "$PSScriptRoot\tv-common.ps1"
$Device = Get-TvDevice $Device
$pkg = 'dev.glasshome.launcher'
Invoke-Tv $Device shell am broadcast -n "$pkg/.ConfigReceiver" -a "$pkg.SET_STYLE" --es style $Style | Out-Null
Write-Host "Home style set to $Style"
