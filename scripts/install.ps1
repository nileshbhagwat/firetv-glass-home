<#
  install.ps1 - install Glass Home on the TV and make it the home screen (host adb).
  The TV address comes from tv.env (TV_IP=...) unless -Device is given.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\install.ps1
    powershell -ExecutionPolicy Bypass -File scripts\install.ps1 -Device 192.168.1.50:5555 -NoHomeHelper
#>
param(
  [string]$Device,
  [switch]$NoHomeHelper
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }
if (-not $Device) {
  $envFile = Join-Path $root 'tv.env'
  if (-not (Test-Path $envFile)) { throw "Create tv.env from tv.env.example or pass -Device" }
  $ip = (Select-String -Path $envFile -Pattern '^TV_IP=(.+)$' | Select-Object -First 1).Matches[0].Groups[1].Value.Trim()
  $Device = "${ip}:5555"
}
$pkg = 'dev.glasshome.launcher'
$service = "$pkg/$pkg.HomeRedirectService"
$apk = Join-Path $root 'dist\glass-home-debug.apk'
if (-not (Test-Path $apk)) { throw "Missing $apk. Build first." }

function Connect-Tv {
  for ($i = 0; $i -lt 8; $i++) {
    if ((& $adb -s $Device get-state 2>&1) -match '^device') { return }
    & $adb disconnect $Device 2>&1 | Out-Null
    & $adb connect $Device 2>&1 | Out-Null
    Start-Sleep -Seconds 3
  }
  throw "Cannot reach $Device. Is ADB debugging on? (Fire OS turns it off when the TV restarts.)"
}
# The Wi-Fi link can drop briefly, so every call reconnects first.
function Tv { Connect-Tv; & $adb -s $Device @args 2>&1 }

Write-Host "Installing $apk"
Tv install -r $apk | Out-Host
Write-Host "Granting location permission (needed to show the Wi-Fi network name)"
Tv shell pm grant $pkg android.permission.ACCESS_FINE_LOCATION | Out-Host
Write-Host "Setting Glass Home as the default home app"
Tv shell cmd package set-home-activity "$pkg/.MainActivity" | Out-Host

if (-not $NoHomeHelper) {
  Write-Host "Enabling the Home button helper (accessibility service)"
  $current = ((Tv shell settings get secure enabled_accessibility_services) -join '').Trim()
  if ($current -match [regex]::Escape($pkg)) { $updated = $current }
  elseif (-not $current -or $current -eq 'null') { $updated = $service }
  else { $updated = "${current}:$service" }
  Tv shell settings put secure enabled_accessibility_services $updated | Out-Null
  Tv shell settings put secure accessibility_enabled 1 | Out-Null
}

Tv shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n "$pkg/.MainActivity" | Out-Null
Write-Host "Done. Glass Home is running on $Device."
