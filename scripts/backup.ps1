<#
  backup.ps1 - snapshot the TV's package lists, properties and settings into backup\<timestamp>.
  The first snapshot is recorded in backup\BASELINE; restore.ps1 compares against it.
  Later snapshots never replace the baseline.
#>
[CmdletBinding(PositionalBinding = $false)]
param([string]$Device)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tv-common.ps1')
$Device = Get-TvDevice $Device
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dir = Join-Path $script:Root "backup\$stamp"
New-Item -ItemType Directory -Force $dir | Out-Null

function Save([string]$name, [string[]]$command) {
  Invoke-Tv $Device shell @command | Set-Content -Encoding utf8 (Join-Path $dir $name)
}
Save 'getprop.txt' @('getprop')
Save 'packages-all-detailed.txt' @('pm', 'list', 'packages', '-f', '-i', '-U')
Save 'packages-all.txt' @('pm', 'list', 'packages')
Save 'packages-system.txt' @('pm', 'list', 'packages', '-s')
Save 'packages-thirdparty.txt' @('pm', 'list', 'packages', '-3')
Save 'packages-enabled.txt' @('pm', 'list', 'packages', '-e')
Save 'packages-disabled.txt' @('pm', 'list', 'packages', '-d')
foreach ($ns in 'system', 'secure', 'global') { Save "settings-$ns.txt" @('settings', 'list', $ns) }
Save 'home-activity.txt' @('cmd', 'package', 'query-activities', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')

$baseline = Join-Path $script:Root 'backup\BASELINE'
if (-not (Test-Path $baseline)) {
  Set-Content -Encoding ascii -NoNewline $baseline "backup/$stamp"
  Write-Host "Baseline recorded: backup\$stamp"
}
Write-Host "Snapshot saved to backup\$stamp"
