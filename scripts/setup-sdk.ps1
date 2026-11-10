<#
  setup-sdk.ps1 - download the Android SDK packages into tools\android-sdk (host builds via mise).
  Requires Java 17 on PATH (mise provides it). Docker builds do not need this script.
  Running sdkmanager requires accepting the Android SDK License Agreement.
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $root 'tools\android-sdk'
$zipName = 'commandlinetools-win-16111833_latest.zip'
$zip = Join-Path $root "tools\$zipName"

if (-not (Test-Path (Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'))) {
  New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
  Write-Host "Downloading $zipName"
  Invoke-WebRequest -UseBasicParsing -Uri "https://dl.google.com/android/repository/$zipName" -OutFile $zip
  $tmp = Join-Path $root 'tools\cmdline-tools-tmp'
  Expand-Archive -Path $zip -DestinationPath $tmp -Force
  Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
  Remove-Item $tmp -Recurse -Force
  Remove-Item $zip -Force
}

$sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
Write-Host "Accepting the Android SDK License Agreement and installing packages"
$yes = ('y' + [Environment]::NewLine) * 20
$yes | & $sdkmanager "--sdk_root=$sdk" --licenses | Out-Null
& $sdkmanager "--sdk_root=$sdk" --install 'platforms;android-34' 'build-tools;34.0.0' 'platform-tools'
Write-Host "Android SDK ready in $sdk"
