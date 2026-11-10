<#
  set-dock.ps1 - choose the dock apps (left to right) by package name.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\set-dock.ps1 -List
    powershell -ExecutionPolicy Bypass -File scripts\set-dock.ps1 com.netflix.ninja com.amazon.firebat com.amazon.firetv.youtube in.startv.hotstar
    powershell -ExecutionPolicy Bypass -File scripts\set-dock.ps1 -Default
#>
[CmdletBinding(PositionalBinding = $false)]
param(
  [string]$Device,
  [switch]$List,
  [switch]$Default,
  [Parameter(ValueFromRemainingArguments = $true)][string[]]$Packages
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tv-common.ps1')
$Device = Get-TvDevice $Device

if ($List) {
  $apps = @()
  foreach ($cat in 'android.intent.category.LEANBACK_LAUNCHER', 'android.intent.category.LAUNCHER') {
    $apps += Invoke-Tv $Device shell cmd package query-activities --brief -a android.intent.action.MAIN -c $cat |
      Where-Object { $_ -match '^\s+\S+/' } | ForEach-Object { ($_.Trim() -split '/')[0] }
  }
  $apps | Sort-Object -Unique
  return
}

if ($Default) { $value = '' }
elseif ($Packages -and $Packages.Count -gt 0) { $value = ($Packages -join ',') }
else { throw "Pass package names in order, -Default, or -List" }

Invoke-Tv $Device shell am broadcast -n dev.glasshome.launcher/.ConfigReceiver `
  -a dev.glasshome.launcher.SET_DOCK --es packages "'$value'" | Out-Null
if ($value) { Write-Host "Dock set to: $value" } else { Write-Host "Dock reset to the default apps" }
