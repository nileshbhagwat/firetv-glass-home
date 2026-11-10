# Shared helpers for the TV scripts: resolves the adb binary and the TV address,
# and reconnects automatically because the Wi-Fi ADB link can drop briefly.
$script:Root = Split-Path -Parent $PSScriptRoot
$script:Adb = Join-Path $script:Root 'tools\platform-tools\adb.exe'
if (-not (Test-Path $script:Adb)) { $script:Adb = 'adb' }

function Get-TvDevice([string]$Device) {
  if ($Device) { return $Device }
  $envFile = Join-Path $script:Root 'tv.env'
  if (-not (Test-Path $envFile)) { throw "Create tv.env from tv.env.example or pass -Device" }
  $ip = (Select-String -Path $envFile -Pattern '^TV_IP=(.+)$' | Select-Object -First 1).Matches[0].Groups[1].Value.Trim()
  return "${ip}:5555"
}

function Connect-Tv([string]$Device) {
  $ErrorActionPreference = 'Continue'  # adb writes status to stderr; never treat it as fatal
  for ($i = 0; $i -lt 8; $i++) {
    if ((& $script:Adb -s $Device get-state 2>&1) -match '^device') { return }
    & $script:Adb disconnect $Device 2>&1 | Out-Null
    & $script:Adb connect $Device 2>&1 | Out-Null
    Start-Sleep -Seconds 3
  }
  throw "Cannot reach $Device. Is ADB debugging on? (Fire OS turns it off when the TV restarts.)"
}

# Usage: Invoke-Tv <device> <adb arguments...>. The device is taken positionally so that
# adb flags such as -d are never mistaken for PowerShell parameters.
function Invoke-Tv {
  $ErrorActionPreference = 'Continue'  # adb writes status to stderr; never treat it as fatal
  $device = $args[0]
  $rest = @($args | Select-Object -Skip 1)
  Connect-Tv $device
  & $script:Adb -s $device @rest 2>&1 | ForEach-Object { "$_" }
}
