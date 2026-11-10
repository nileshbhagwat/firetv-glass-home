<#
  restore.ps1 - undo the debloat and launcher change on the Fire TV.

  What it does:
    1. Re-enables every package that is disabled now but was NOT disabled in the backup
       (plus anything listed in backup\disabled-packages.txt).
    2. Lifts the background restriction (RUN_ANY_IN_BACKGROUND) on the protected
       packages listed in backup\restricted-packages.txt.
    3. Turns off the Glass Home accessibility helper, if it was enabled.
    4. Re-enables Amazon's launcher and makes it the default home again.
    5. Uninstalls the Glass Home launcher (skip with -KeepLauncher).

  The TV address comes from tv.env (TV_IP=...) unless -Device is given.

  Usage (from the project folder):
    powershell -ExecutionPolicy Bypass -File scripts\restore.ps1
    powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 -Device 192.168.1.50:5555 -KeepLauncher
    powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 -WhatIf      # show what would change
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
  [string]$Device,
  [string]$BackupDir,
  [switch]$KeepLauncher
)

$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }
if (-not $Device) {
  $envFile = Join-Path $root 'tv.env'
  if (-not (Test-Path $envFile)) { throw "Create tv.env from tv.env.example or pass -Device" }
  $ip = (Select-String -Path $envFile -Pattern '^TV_IP=(.+)$' | Select-Object -First 1).Matches[0].Groups[1].Value.Trim()
  $Device = "${ip}:5555"
}

$AmazonHome = 'com.amazon.tv.launcher/.ui.HomeActivity_vNext'
$GlassPkg   = 'dev.glasshome.launcher'

if (-not $BackupDir) {
  $latest = Join-Path $root 'backup\BASELINE'
  if (-not (Test-Path $latest)) { throw "No backup\BASELINE file found. Run scripts\backup.ps1 first or pass -BackupDir." }
  $BackupDir = Join-Path $root ((Get-Content $latest -Raw).Trim())
}
$origDisabledFile = Join-Path $BackupDir 'packages-disabled.txt'
if (-not (Test-Path $origDisabledFile)) { throw "Backup not found: $origDisabledFile" }

function Get-PkgNames([string[]]$lines) { $lines | Where-Object { $_ -match '^package:' } | ForEach-Object { ($_ -replace '^package:', '').Trim() } }
function Connect-Tv {
  for ($i = 0; $i -lt 6; $i++) {
    if ((& $adb -s $Device get-state 2>&1) -match '^device') { return }
    & $adb disconnect $Device 2>&1 | Out-Null
    & $adb connect $Device 2>&1 | Out-Null
    Start-Sleep -Seconds 3
  }
  throw "TV not connected/authorized at $Device (is ADB Debugging on? it turns off when the TV restarts)"
}
# The Wi-Fi link to this TV drops now and then, so every call reconnects and retries.
function Invoke-Adb {
  for ($try = 0; $try -lt 3; $try++) {
    Connect-Tv
    $out = & $adb -s $Device @args 2>&1
    if (($out -join ' ') -notmatch 'device offline|not found|error: closed') { return $out }
  }
  return $out
}

Write-Host "Connecting to $Device ..."
Connect-Tv

$origDisabled = @(Get-PkgNames (Get-Content $origDisabledFile))
$nowDisabled  = @(Get-PkgNames (Invoke-Adb shell pm list packages -d))
$ourLog = Join-Path $root 'backup\disabled-packages.txt'
$ours = if (Test-Path $ourLog) { @(Get-Content $ourLog | ForEach-Object { ($_ -split '\s+')[0] } | Where-Object { $_ }) } else { @() }

$toEnable = @($nowDisabled | Where-Object { $origDisabled -notcontains $_ }) + @($ours | Where-Object { $nowDisabled -contains $_ })
$toEnable = $toEnable | Sort-Object -Unique

Write-Host "Packages to re-enable: $($toEnable.Count)"
foreach ($p in $toEnable) {
  if ($PSCmdlet.ShouldProcess($p, 'pm enable')) {
    # one pm command per shell call
    $r = Invoke-Adb shell pm enable --user 0 $p
    Write-Host ("  {0,-55} {1}" -f $p, ($r -join ' '))
  }
}

$restrictLog = Join-Path $root 'backup\restricted-packages.txt'
if (Test-Path $restrictLog) {
  foreach ($p in (Get-Content $restrictLog | ForEach-Object { ($_ -split '\s+')[0] } | Where-Object { $_ } | Sort-Object -Unique)) {
    if ($PSCmdlet.ShouldProcess($p, 'appops RUN_ANY_IN_BACKGROUND default')) {
      Invoke-Adb shell cmd appops set $p RUN_ANY_IN_BACKGROUND default | Out-Null
      Write-Host ("  {0,-55} background allowed again" -f $p)
    }
  }
}

# Accessibility helper (only present if it was enabled for the Home-button redirect)
$a11y = (Invoke-Adb shell settings get secure enabled_accessibility_services) -join ''
if ($a11y -match [regex]::Escape($GlassPkg) -and $PSCmdlet.ShouldProcess('Glass Home accessibility helper', 'turn off')) {
  $rest = ($a11y -split ':' | Where-Object { $_ -and $_ -notmatch [regex]::Escape($GlassPkg) -and $_ -ne 'null' }) -join ':'
  if ($rest) { Invoke-Adb shell settings put secure enabled_accessibility_services $rest | Out-Null }
  else {
    Invoke-Adb shell settings delete secure enabled_accessibility_services | Out-Null
    # No other services remain: put accessibility_enabled back to its value at backup time.
    $orig = Select-String -Path (Join-Path $BackupDir 'settings-secure.txt') -Pattern '^accessibility_enabled=(\d)' |
      Select-Object -First 1 | ForEach-Object { $_.Matches[0].Groups[1].Value }
    if (-not $orig) { $orig = '0' }
    Invoke-Adb shell settings put secure accessibility_enabled $orig | Out-Null
  }
  Write-Host "  Home button helper turned off"
}

if ($PSCmdlet.ShouldProcess('Glass Home content shelf', 'remove notification access')) {
  Invoke-Adb shell cmd notification disallow_listener "$GlassPkg/$GlassPkg.RecommendationListener" | Out-Null
}

if ($PSCmdlet.ShouldProcess('com.amazon.tv.launcher', 'enable + set as home')) {
  Invoke-Adb shell pm enable --user 0 com.amazon.tv.launcher | Out-Host
  Invoke-Adb shell cmd package set-home-activity $AmazonHome | Out-Host
}

if (-not $KeepLauncher) {
  $installed = Invoke-Adb shell pm list packages $GlassPkg
  if ($installed -match [regex]::Escape($GlassPkg) -and $PSCmdlet.ShouldProcess($GlassPkg, 'uninstall')) {
    Invoke-Adb uninstall $GlassPkg | Out-Host
  }
}

Write-Host "`nHome now resolves to:"
Invoke-Adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | Select-Object -Last 1 | Out-Host
Write-Host "Still disabled (compare with packages-disabled.txt in the backup):"
Invoke-Adb shell pm list packages -d | Out-Host
Write-Host "Done. Press Home on the remote to check."
