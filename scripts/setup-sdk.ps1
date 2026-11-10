<#
  setup-sdk.ps1 - download the Android SDK packages into tools\android-sdk (host builds via mise).
  Requires Java 17 on PATH (mise provides it). Docker builds do not need this script.
  Installing the packages requires accepting the Android SDK License Agreement.
  Usage metrics of the Android CLI are turned off with --no-metrics.
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $root 'tools\android-sdk'
$zipName = 'commandlinetools-win-16111833_latest.zip'
$zip = Join-Path $root "tools\$zipName"
# The command-line tools now ship the Android CLI ("android"); sdkmanager is a deprecated
# wrapper around it and does not accept --no-metrics.
$android = Join-Path $sdk 'cmdline-tools\latest\bin\android.exe'

if (-not (Test-Path $android)) {
  New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
  Write-Host "Downloading $zipName"
  Invoke-WebRequest -UseBasicParsing -Uri "https://dl.google.com/android/repository/$zipName" -OutFile $zip
  $tmp = Join-Path $root 'tools\cmdline-tools-tmp'
  Expand-Archive -Path $zip -DestinationPath $tmp -Force
  Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
  Remove-Item $tmp -Recurse -Force
  Remove-Item $zip -Force
}

Write-Host "Installing SDK packages (Android SDK terms: https://developer.android.com/studio/terms)"
& $android --no-metrics "--sdk=$sdk" sdk install 'platforms/android-34' 'build-tools/34.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw "android sdk install failed" }
& $android --no-metrics "--sdk=$sdk" sdk list
Write-Host "Android SDK ready in $sdk"
