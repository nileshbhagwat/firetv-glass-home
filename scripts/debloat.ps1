<#
  debloat.ps1 - apply config\debloat-packages.txt to the TV.
  Useful again after a factory reset or when a Fire OS update re-enables packages.
  Takes a baseline backup first if none exists. Every change is logged so that
  restore.ps1 can undo it. Run with -WhatIf to preview.
#>
[CmdletBinding(SupportsShouldProcess = $true, PositionalBinding = $false)]
param([string]$Device)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tv-common.ps1')
$Device = Get-TvDevice $Device

if (-not (Test-Path (Join-Path $script:Root 'backup\BASELINE'))) {
  & (Join-Path $PSScriptRoot 'backup.ps1') -Device $Device
}
$list = Get-Content (Join-Path $script:Root 'config\debloat-packages.txt') |
  ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') }
$installed = @(Invoke-Tv $Device shell pm list packages | ForEach-Object { $_ -replace '^package:', '' })
$disabled = @(Invoke-Tv $Device shell pm list packages -d | ForEach-Object { $_ -replace '^package:', '' })
$disabledLog = Join-Path $script:Root 'backup\disabled-packages.txt'
$restrictLog = Join-Path $script:Root 'backup\restricted-packages.txt'

foreach ($entry in $list) {
  $restrict = $entry.StartsWith('restrict:')
  $pkg = $entry -replace '^restrict:', ''
  if ($installed -notcontains $pkg) { Write-Host ("  {0,-48} not installed" -f $pkg); continue }
  if ($restrict) {
    if ($PSCmdlet.ShouldProcess($pkg, 'block background running')) {
      Invoke-Tv $Device shell cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore | Out-Null
      Add-Content $restrictLog "$pkg $(Get-Date -Format o) RUN_ANY_IN_BACKGROUND=ignore"
      Write-Host ("  {0,-48} background blocked" -f $pkg)
    }
  } elseif ($disabled -contains $pkg) {
    Write-Host ("  {0,-48} already disabled" -f $pkg)
  } elseif ($PSCmdlet.ShouldProcess($pkg, 'pm disable-user')) {
    # One pm command per shell call; chained pm commands can stop silently after the first.
    $result = (Invoke-Tv $Device shell pm disable-user --user 0 $pkg) -join ' '
    if ($result -match 'new state: disabled') {
      Add-Content $disabledLog "$pkg $(Get-Date -Format o) disabled"
      Write-Host ("  {0,-48} disabled" -f $pkg)
    } else {
      Write-Host ("  {0,-48} FAILED: {1}" -f $pkg, $result)
    }
  }
}
