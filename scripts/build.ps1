<#
  build.ps1 - host build (mise). Produces dist\glass-home-debug.apk.
  Requires Java 17, Gradle 8.10.2 and ANDROID_HOME, all provided by mise.toml.
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $root 'launcher'
$keystore = Join-Path $launcher 'debug.keystore'
if (-not (Test-Path $keystore)) {
  Write-Host "Creating project debug keystore (launcher\debug.keystore)"
  & keytool -genkeypair -keystore $keystore -storepass android -alias androiddebugkey -keypass android `
    -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' | Out-Null
}
Push-Location $launcher
# --no-daemon: no Gradle process is left running (and holding memory) after the build.
try { & gradle --no-daemon --console=plain assembleDebug; if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" } }
finally { Pop-Location }
New-Item -ItemType Directory -Force (Join-Path $root 'dist') | Out-Null
Copy-Item (Join-Path $launcher 'app\build\outputs\apk\debug\app-debug.apk') (Join-Path $root 'dist\glass-home-debug.apk') -Force
Write-Host "Built dist\glass-home-debug.apk"
