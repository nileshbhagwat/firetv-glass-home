<#
  find-tv.ps1 - check the TV address in tv.env and find the TV again if it has changed.

  Prints the TV's IP address. If the TV no longer answers at TV_IP, the home network is
  searched for the TV's network card (TV_MAC, saved automatically the first time the TV
  answers) and tv.env is updated. All the TV scripts do this by themselves; glasshome.bat
  uses this script, and it is handy for checking the address by hand.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\find-tv.ps1
#>
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tv-common.ps1"
Resolve-TvIp
