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
# Count titles from the index on the TV: a title on two apps is one picture but two titles.
$index = (Invoke-Tv $Device shell "run-as $pkg cat files/featured/index.json 2>/dev/null") -join "`n"
$count = 0
if ($index -match '"items"') { try { $count = @(($index | ConvertFrom-Json).items).Count } catch { } }
Write-Host "Featured titles on the TV: $count"
