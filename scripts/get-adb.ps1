<#
  get-adb.ps1 - download Google's platform-tools (adb) into tools\platform-tools.
  Source: https://dl.google.com/android/repository/platform-tools-latest-windows.zip
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $root 'tools'
$zip = Join-Path $tools 'platform-tools-latest-windows.zip'
New-Item -ItemType Directory -Force $tools | Out-Null
Write-Host "Downloading platform-tools (adb) into tools\platform-tools"
Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
Expand-Archive -Path $zip -DestinationPath $tools -Force
$sig = Get-AuthenticodeSignature (Join-Path $tools 'platform-tools\adb.exe')
if ($sig.Status -ne 'Valid' -or $sig.SignerCertificate.Subject -notmatch 'Google LLC') {
  throw "adb.exe signature check failed: $($sig.Status)"
}
Write-Host "adb ready (signed by Google LLC)"
