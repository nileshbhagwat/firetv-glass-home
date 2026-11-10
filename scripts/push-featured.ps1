<#
  push-featured.ps1 - copy the prepared featured titles (featured\out) to Glass Home on the TV.

  featured.bat runs this after preparing the titles. The images go into the launcher's own
  storage; nothing is downloaded on the TV.

  Usage (from the project folder):
    powershell -ExecutionPolicy Bypass -File scripts\push-featured.ps1
    powershell -ExecutionPolicy Bypass -File scripts\push-featured.ps1 -Remove   # clear them
#>
[CmdletBinding(PositionalBinding = $false)]
param(
  [string]$Device,
  [string]$Source,
  [switch]$Remove
)
. "$PSScriptRoot\tv-common.ps1"
$Device = Get-TvDevice $Device
$pkg = 'dev.glasshome.launcher'

if ($Remove) {
  Invoke-Tv $Device shell "run-as $pkg rm -rf files/featured" | Out-Null
} else {
  if (-not $Source) { $Source = Join-Path $script:Root 'featured\out' }
  if (-not (Test-Path (Join-Path $Source 'index.json'))) { throw "No featured titles in $Source. Run featured.bat first." }
  $tar = Join-Path $env:TEMP 'glasshome-featured.tar'
  if (Test-Path $tar) { Remove-Item $tar -Force }
  & "$env:SystemRoot\System32\tar.exe" -cf $tar -C $Source .
  if ($LASTEXITCODE -ne 0) { throw "Could not pack $Source" }
  $remote = '/data/local/tmp/glasshome-featured.tar'
  Write-Host "Copying featured titles to $Device ..."
  Invoke-Tv $Device push $tar $remote | Out-Null
  # Unpack into the app's private folder (run-as works because the launcher is a debug build),
  # replacing the previous set in one step.
  $unpack = "cat $remote | run-as $pkg sh -c 'rm -rf files/featured.new && mkdir -p files/featured.new && " +
            "tar -xf - -C files/featured.new && rm -rf files/featured && mv files/featured.new files/featured'; rm -f $remote"
  Invoke-Tv $Device shell $unpack | Out-Host
  Remove-Item $tar -Force
}
Invoke-Tv $Device shell am broadcast -n "$pkg/.ConfigReceiver" -a "$pkg.RELOAD" | Out-Null
$count = (Invoke-Tv $Device shell "run-as $pkg ls files/featured 2>/dev/null" | Where-Object { $_ -match '-card\.jpg$' }).Count
Write-Host "Featured titles on the TV: $count"
