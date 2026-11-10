# Shared helpers for the TV scripts: resolves the adb binary and the TV address,
# and reconnects automatically because the Wi-Fi ADB link can drop briefly.
$script:Root = Split-Path -Parent $PSScriptRoot
$script:Adb = Join-Path $script:Root 'tools\platform-tools\adb.exe'
if (-not (Test-Path $script:Adb)) { $script:Adb = 'adb' }

function Get-TvDevice([string]$Device) {
  if ($Device) { return $Device }
  return "$(Resolve-TvIp):5555"
}

# ---------------------------------------------------------------------------- TV address
# tv.env holds TV_IP and, once the TV has been reached, TV_MAC (its network card's ID). When
# the TV no longer answers at TV_IP (the router gave it a new address), the home network is
# searched for TV_MAC and tv.env is updated, so the scripts keep working without changes.
# Notes go to stderr so that scripts\find-tv.ps1 can print only the address.

function Write-TvNote([string]$Text) { [Console]::Error.WriteLine($Text) }

function Get-TvEnvValue([string]$Name) {
  $file = Join-Path $script:Root 'tv.env'
  if (-not (Test-Path $file)) { return $null }
  $m = Select-String -Path $file -Pattern "^$Name=(.*)$" | Select-Object -First 1
  if ($m) {
    $value = $m.Matches[0].Groups[1].Value.Trim()
    if ($value) { return $value }
  }
  return $null
}

function Set-TvEnvValue([string]$Name, [string]$Value, [string]$Comment) {
  $file = Join-Path $script:Root 'tv.env'
  $text = if (Test-Path $file) { [IO.File]::ReadAllText($file) } else { '' }
  if ($text -match "(?m)^$Name=") {
    $text = [regex]::Replace($text, "(?m)^$Name=[^\r\n]*", "$Name=$Value")
  } else {
    if ($text.Length -and -not $text.EndsWith("`n")) { $text += "`n" }
    if ($Comment) { $text += "`n# $Comment`n" }
    $text += "$Name=$Value`n"
  }
  [IO.File]::WriteAllText($file, $text, (New-Object Text.UTF8Encoding($false)))
}

function ConvertTo-MacKey([string]$Mac) {
  if (-not $Mac) { return $null }
  return $Mac.Trim().ToLowerInvariant().Replace(':', '-')
}

# IP address -> @{ Mac; Dynamic } from the PC's neighbour (ARP) table.
function Get-NeighbourTable {
  $table = @{}
  foreach ($line in (& arp.exe -a)) {
    if ($line -match '^\s*(\d+\.\d+\.\d+\.\d+)\s+([0-9a-fA-F]{2}(?:-[0-9a-fA-F]{2}){5})\s+(\w+)') {
      $table[$Matches[1]] = @{ Mac = $Matches[2].ToLowerInvariant(); Dynamic = $Matches[3] -eq 'dynamic' }
    }
  }
  return $table
}

function Test-TvHost([string]$Ip) {
  # The TV's Wi-Fi dozes between packets, so the first reply can take over a second: try twice.
  for ($attempt = 0; $attempt -lt 2; $attempt++) {
    try {
      if ((New-Object Net.NetworkInformation.Ping).Send($Ip, 1500).Status -eq 'Success') { return $true }
    } catch { }
    # Some networks block ping; the ADB port answers whenever ADB debugging is on.
    $client = New-Object Net.Sockets.TcpClient
    try {
      if ($client.ConnectAsync($Ip, 5555).Wait(2500) -and $client.Connected) { return $true }
    } catch { } finally { $client.Close() }
  }
  return $false
}

function ConvertTo-IpNumber([string]$Ip) {
  $b = ([Net.IPAddress]$Ip).GetAddressBytes(); [Array]::Reverse($b); return [BitConverter]::ToUInt32($b, 0)
}

function ConvertFrom-IpNumber([double]$N) {
  $b = [BitConverter]::GetBytes([uint32]$N); [Array]::Reverse($b); return ([Net.IPAddress]$b).ToString()
}

# Every address on the home network: the PC's own networks (those with a router; at most
# 1022 addresses each, otherwise the PC's /24) plus the /24 of the last known TV address.
function Get-HomeAddresses([string]$LastIp) {
  $ranges = @()
  if ($LastIp) { $ranges += , @($LastIp, 24) }
  try {
    foreach ($cfg in Get-NetIPConfiguration -ErrorAction Stop | Where-Object { $_.IPv4DefaultGateway -and $_.IPv4Address }) {
      foreach ($a in $cfg.IPv4Address) { $ranges += , @($a.IPAddress, [Math]::Max([int]$a.PrefixLength, 22)) }
    }
  } catch { }
  $addresses = New-Object 'System.Collections.Generic.HashSet[string]'
  foreach ($r in $ranges) {
    $size = [Math]::Pow(2, 32 - $r[1])
    $network = [Math]::Floor((ConvertTo-IpNumber $r[0]) / $size) * $size
    for ($i = 1; $i -lt $size - 1; $i++) { [void]$addresses.Add((ConvertFrom-IpNumber ($network + $i))) }
  }
  return $addresses
}

# Pings the whole home network at once (so every device appears in the neighbour table),
# then looks the TV up by its MAC address. Returns its IP address or $null.
function Find-TvByMac([string]$Mac, [string]$LastIp) {
  $mac = ConvertTo-MacKey $Mac
  $addresses = @(Get-HomeAddresses $LastIp)
  # Two rounds: the first wakes devices whose Wi-Fi is dozing, the second gets their replies.
  for ($round = 0; $round -lt 2; $round++) {
    $pings = foreach ($ip in $addresses) { (New-Object Net.NetworkInformation.Ping).SendPingAsync($ip, 1500) }
    try { [void][Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($pings), 6000) } catch { }
  }
  $hits = (Get-NeighbourTable).GetEnumerator() | Where-Object { $_.Value.Mac -eq $mac }
  # A permanent (static) entry may still point at an old address: prefer what was just seen.
  $hit = @($hits | Where-Object { $_.Value.Dynamic }) + @($hits | Where-Object { -not $_.Value.Dynamic }) | Select-Object -First 1
  if ($hit) { return $hit.Key }
  return $null
}

# The TV's current IP address. Remembers its MAC address the first time the TV answers, and
# finds the TV again, updating tv.env, after the router has given it a new address.
function Resolve-TvIp {
  if (-not (Test-Path (Join-Path $script:Root 'tv.env'))) { throw "Create tv.env from tv.env.example or pass -Device" }
  $ip = Get-TvEnvValue 'TV_IP'
  $mac = ConvertTo-MacKey (Get-TvEnvValue 'TV_MAC')
  if (-not $ip -and -not $mac) { throw "Set TV_IP in tv.env (Fire TV: Settings > My Fire TV > About > Network)." }
  if ($ip -and (Test-TvHost $ip)) {
    $entry = (Get-NeighbourTable)[$ip]
    $seen = if ($entry) { $entry.Mac } else { $null }
    if (-not $mac -and $seen) {
      Set-TvEnvValue 'TV_MAC' $seen "The TV's network card (MAC address), saved automatically so that the scripts can find the TV if its IP address changes."
      Write-TvNote "Saved the TV's network ID to tv.env, so the TV can be found if its address changes."
      return $ip
    }
    if (-not $mac -or -not $seen -or $seen -eq $mac) { return $ip }
    $why = "Another device now uses $ip."
  } elseif ($ip) {
    $why = "The TV does not answer at $ip."
  } else {
    $why = "tv.env has no TV_IP."
  }
  if (-not $mac) { return $ip }  # nothing to search for; adb reports the problem
  Write-TvNote "$why Looking for the TV on the home network..."
  $found = Find-TvByMac $mac $ip
  if (-not $found) {
    Write-TvNote "The TV was not found on the network. Is it switched on and connected to Wi-Fi?"
    return $ip
  }
  if ($found -ne $ip) {
    Set-TvEnvValue 'TV_IP' $found
    Write-TvNote "Found the TV at its new address $found and saved it to tv.env."
  }
  return $found
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
