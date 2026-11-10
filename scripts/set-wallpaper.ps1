<#
  set-wallpaper.ps1 - set or remove the background image (JPEG, PNG or WebP).
  The image is scaled to fill the screen and dimmed slightly so text stays readable.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\set-wallpaper.ps1 C:\path\to\image.jpg
    powershell -ExecutionPolicy Bypass -File scripts\set-wallpaper.ps1 -Remove
#>
[CmdletBinding(PositionalBinding = $false)]
param(
  [Parameter(Position = 0)][string]$Image,
  [string]$Device,
  [switch]$Remove
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tv-common.ps1')
$Device = Get-TvDevice $Device
$pkg = 'dev.glasshome.launcher'
$tmp = '/data/local/tmp/glasshome-wallpaper'

# The image lives in the launcher's private storage. Fire OS does not let ADB write to
# app storage directly, so the file is streamed in with run-as (available on debug builds).
Invoke-Tv $Device shell "run-as $pkg rm -f files/wallpaper.jpg files/wallpaper.png files/wallpaper.webp" | Out-Null
if (-not $Remove) {
  if (-not $Image -or -not (Test-Path $Image)) { throw "Pass an image path, or -Remove" }
  $ext = [IO.Path]::GetExtension($Image).ToLowerInvariant().TrimStart('.')
  if ($ext -eq 'jpeg') { $ext = 'jpg' }
  if ($ext -notin 'jpg', 'png', 'webp') { throw "Use a .jpg, .png or .webp image" }
  Invoke-Tv $Device push (Resolve-Path $Image).Path $tmp | Out-Null
  Invoke-Tv $Device shell "cat $tmp | run-as $pkg sh -c 'cat > files/wallpaper.$ext' && rm $tmp" | Out-Host
}
Invoke-Tv $Device shell am broadcast -n dev.glasshome.launcher/.ConfigReceiver -a dev.glasshome.launcher.RELOAD | Out-Null
if ($Remove) { Write-Host "Wallpaper removed" } else { Write-Host "Wallpaper set" }
