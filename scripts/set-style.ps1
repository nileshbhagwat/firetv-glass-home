<#
  set-style.ps1 - choose the home style from the laptop (the remote's Menu button does the same).

  Styles: classic, ring (gallery ring), depth (depth clock), breakout (break-out card),
  mosaic (glass mosaic).
  -Titles: focused (the titles of the app focused in the dock; the default) or all (titles
  from all apps).

  Usage (from the project folder):
    powershell -ExecutionPolicy Bypass -File scripts\set-style.ps1 ring
    powershell -ExecutionPolicy Bypass -File scripts\set-style.ps1 -Titles all
    powershell -ExecutionPolicy Bypass -File scripts\set-style.ps1 depth -Titles focused
#>
[CmdletBinding(PositionalBinding = $false)]
param(
  [Parameter(Position = 0)]
  [ValidateSet('classic', 'ring', 'depth', 'breakout', 'mosaic')]
  [string]$Style,
  [ValidateSet('focused', 'all')]
  [string]$Titles,
  [string]$Device
)
if (-not $Style -and -not $Titles) { throw "Give a style, -Titles focused|all, or both." }
. "$PSScriptRoot\tv-common.ps1"
$Device = Get-TvDevice $Device
$pkg = 'dev.glasshome.launcher'
$extras = @()
if ($Style) { $extras += @('--es', 'style', $Style) }
if ($Titles) { $extras += @('--es', 'titles', $Titles) }
Invoke-Tv $Device shell am broadcast -n "$pkg/.ConfigReceiver" -a "$pkg.SET_STYLE" @extras | Out-Null
if ($Style) { Write-Host "Home style set to $Style" }
if ($Titles) { Write-Host "Titles: $(if ($Titles -eq 'all') { 'all apps' } else { 'the focused app' })" }
