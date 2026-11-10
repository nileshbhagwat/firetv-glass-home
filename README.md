# Glass Home for Fire TV

A minimal home screen and a reversible debloat for a Xiaomi Fire TV
(model L43MB-FPIN, 2025; Fire OS 8.1.8.5, Android 11).

![Home screen](docs/images/home.png)

## Features

- Pitch-black home screen with a personal greeting ("Good evening, Sam", taken from the
  TV's name), a large thin clock and the date set in Outfit.
- Frosted pills for **Inputs**, **Wi-Fi** (shows the network name) and **Settings**.
- A dock band with your main apps; the focused app's name is shown in large type above it,
  over a soft glow tinted with a colour sampled from the app's banner.
- All other apps in a four-column grid that slides up when you press Down.
- One translucent glass highlight that springs from item to item; the focused tile lifts
  towards you in 3D, catches a brief light sheen and shows its name underneath.
- A content shelf: when an app that shares recommendations is focused in the dock (currently
  Netflix), its latest titles appear above the dock as artwork cards that pop out in turn.
- Deterministic D-pad navigation that remembers the last focused tile.
- Lightweight: no blur, no video, no network polling (about 115 MB PSS on the TV).
- Telemetry, advertising and unused preloaded apps disabled; nothing is uninstalled, and a
  single script restores everything.

| Inputs | Apps |
| --- | --- |
| ![Inputs menu](docs/images/inputs.png) | ![Apps grid](docs/images/apps.png) |

## Quick start

Requirements: Windows with Docker Desktop, and the TV on the same network as the PC.
[SETUP.md](SETUP.md) describes the toolchain, an alternative build with mise, and every
file that is downloaded.

1. **Enable ADB debugging on the TV**
   1. Settings > My Fire TV > About: select the TV name seven times until it reports that
      you are a developer.
   2. Settings > My Fire TV > Developer Options: turn **ADB Debugging** on.
   3. Note the IP address under Settings > My Fire TV > About > Network.
2. **Set the TV address**: copy `tv.env.example` to `tv.env` and set `TV_IP`.
3. **Run `glasshome.bat`** (double-click). The first run builds the toolchain image
   (about 1 GB, once). When the TV asks *Allow USB debugging?*, tick
   *Always allow from this computer* and select OK.

> Fire OS switches ADB Debugging off whenever the TV restarts. Turn it on again before
> running any of the scripts.

## Using the launcher

| Remote button | Action |
| --- | --- |
| Left / Right | Move within the current row; stops at the ends |
| Up / Down | Move exactly one row: top bar, dock, then each row of the apps grid |
| Select | Open the focused app or pill |
| Back | Return to the dock |
| Home | Return to Glass Home; pressing Home on Glass Home returns to the dock |

- **Inputs** lists the HDMI ports, AV and Live TV. Fire OS reserves its own input player for
  Amazon-signed apps, so HDMI and AV open in Glass Home's own full-screen player through the
  Android TV input framework; it shows "No signal" when nothing is connected. Back or Home
  returns to the launcher. Live TV opens Amazon's Live TV guide. (The picture itself has not
  yet been checked with a device connected.)
- **Wi-Fi** shows the connected network and opens the network settings.
- **Settings** opens the Fire TV settings.

## How the Home button works on Fire OS

Fire OS always sends the Home button to Amazon's own launcher, and that launcher is
protected: it cannot be disabled and the standard "default home app" setting is ignored.
Glass Home therefore includes a small accessibility service, *Glass Home: Home button*.
As soon as Amazon's home screen appears, the service opens Glass Home with the tile of the
app you just left in focus. Amazon's screen is visible for a fraction of a second.

After a memory-heavy app such as YouTube, Fire OS may have stopped its launcher in the
background. Pressing Home then shows Amazon's loading screen (three dots) for two to three
seconds while it reloads, and Glass Home appears once it has. Fire OS only reports the home
screen after that reload, and it does not pass the Home key to other apps, so this wait
cannot be skipped. At start-up, Fire OS opens Glass Home directly.

Fire TV's Settings screens pass through Amazon's home screen on the way in; the service
recognises this and leaves Settings open. The service only reacts to window changes and does
not read screen content. To turn it off, run `restore.bat` or disable it under
Settings > Accessibility on the TV.

## Customising

### Dock apps and order

```powershell
powershell -ExecutionPolicy Bypass -File scripts\set-dock.ps1 -List
powershell -ExecutionPolicy Bypass -File scripts\set-dock.ps1 com.netflix.ninja com.amazon.firebat com.amazon.firetv.youtube in.startv.hotstar com.onemainstream.sonyliv.android com.zee5.amazon
powershell -ExecutionPolicy Bypass -File scripts\set-dock.ps1 -Default
```

Up to four apps use full-size tiles; with more, the tiles shrink to fit the band (six is a
comfortable maximum). The default dock is defined in
`launcher/app/src/main/java/dev/glasshome/launcher/Prefs.java`.

| App | Package |
| --- | --- |
| Netflix | `com.netflix.ninja` |
| Prime Video | `com.amazon.firebat` |
| YouTube | `com.amazon.firetv.youtube` |
| JioHotstar | `in.startv.hotstar` |
| SonyLIV | `com.onemainstream.sonyliv.android` |
| Zee5 | `com.zee5.amazon` |
| Gallery | `com.amazon.tv.localgallery` |

The Appstore (`com.amazon.venezia`) has no tile but stays installed: apps from Amazon's store
use it in the background for updates and licence checks.

### Name in the greeting

The greeting uses the TV's name (Settings > My Fire TV > About): "SAM's Fire TV" shows
"Good evening, Sam". To use a different name:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\set-name.ps1 Sam
powershell -ExecutionPolicy Bypass -File scripts\set-name.ps1 -Default
```

### Content shelf

Apps can share recommendations with the home screen. On this TV only Netflix does; Prime
Video, JioHotstar, Zee5 and SonyLIV currently share nothing, and Amazon's own artwork comes
from Amazon's servers and is not available to other apps. Any app that starts sharing
appears in the shelf automatically. Reading recommendations needs notification access, which
the install scripts grant over ADB (Glass Home only reads notifications marked as content
recommendations) and `restore.bat` removes.

### Wallpaper

```powershell
powershell -ExecutionPolicy Bypass -File scripts\set-wallpaper.ps1 C:\path\to\image.jpg
powershell -ExecutionPolicy Bypass -File scripts\set-wallpaper.ps1 -Remove
```

JPEG, PNG and WebP are supported. The image fills the screen and is dimmed slightly so text
stays readable. Without a wallpaper the background is pure black.

### Updating the launcher

Run `glasshome.bat` again; settings are kept. Updates install in place because every build is
signed with `launcher/debug.keystore`, which is created on the first build and is not
committed. If that file is lost, uninstall the app once
(`adb uninstall dev.glasshome.launcher`) before installing a new build.

## Debloat

The package list lives in [`config/debloat-packages.txt`](config/debloat-packages.txt).
Packages are disabled with `pm disable-user --user 0`; nothing is uninstalled.

| Group | Packages |
| --- | --- |
| Telemetry and analytics | Xiaomi analytics, Amazon automatic content recognition (ACR), telemetry emitter, loggers, performance collectors, Wi-Fi metrics |
| Advertising and recommendations | Advertising ID service, sponsored content, autoplay previews, demo content, shopping, recommendation rows |
| Background services | Release notes, tutorials, Early Access, set-up services for other Amazon devices |
| Preloaded apps | Games hub, website shortcuts, Amazon Photos, European satellite operator apps, AirPlay, Alexa Smart Home, Amazon Kids, Amazon Luna, Amazon Music, Silk browser |
| Streaming apps not in use | JioCinema, Amazon miniTV |
| European broadcast services | Vewd HbbTV and Freeview Play, UK MHEG (not used in India) |
| System updates | Update helpers; Fire OS no longer installs new versions, so the setup stays as it is |
| Amazon extras | Amazon screensaver (the basic Android screensaver takes over), Hedwig notification service |
| Casting and mirroring | Casting from phone apps (YouTube, Netflix, Prime Video) and screen mirroring |
| Alexa and smart home | Alexa voice search, alerts and calling, Matter smart-home services |

In total 60 packages are disabled. Fire OS protects a few packages from being disabled
(device metrics, crash manager, log manager, usage statistics, ad deep-linking, the two OTA
update services and the help app). These are blocked from running in the background instead.

What this means in daily use:

- The remote's microphone button no longer works (Alexa is off).
- Phones cannot cast to the TV or mirror their screen.
- Fire OS does not update itself, which also means no new security patches. To allow updates
  again, remove the "System updates" lines from the list and re-enable the packages
  (see below).

Deliberately kept: remote pairing, keyboard, HDMI and Live TV services, Wi-Fi, the Appstore
and app sign-in, and the system help pages in Settings.

### Re-enable a single app

```powershell
tools\platform-tools\adb.exe connect 192.168.1.50:5555
tools\platform-tools\adb.exe shell pm enable --user 0 com.amazon.cloud9
```

If the package is listed in `config/debloat-packages.txt`, remove it from the list so that
`debloat.ps1` does not disable it again.

### Disable another app or re-apply the list

Add the package to `config/debloat-packages.txt`, then run (use `-WhatIf` to preview):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\debloat.ps1
```

Run the same command after a factory reset or if a Fire OS update re-enables packages.

### Restore everything

Run `restore.bat`. It re-enables every package disabled since the baseline backup, lifts the
background restrictions, turns off the Home button helper, makes Amazon's launcher the home
screen again and uninstalls Glass Home. Options:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 -WhatIf        # preview only
powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 -KeepLauncher  # keep Glass Home installed
```

The baseline backup (`backup\BASELINE`) holds the package lists, system properties and all
settings as they were before any change.

## Scripts

| Script | Purpose |
| --- | --- |
| `glasshome.bat` | One-click build in Docker and install on the TV |
| `restore.bat` | Undo the debloat and remove Glass Home |
| `scripts/install.ps1` | Install a built APK using the host's adb |
| `scripts/set-dock.ps1` | List apps, set or reset the dock |
| `scripts/set-wallpaper.ps1` | Set or remove the wallpaper |
| `scripts/debloat.ps1` | Apply `config/debloat-packages.txt` |
| `scripts/backup.ps1` | Snapshot packages, properties and settings |
| `scripts/restore.ps1` | Restore script used by `restore.bat` |
| `scripts/get-adb.ps1` | Download Google's platform-tools into `tools/` |
| `scripts/setup-sdk.ps1`, `scripts/build.ps1` | Host build with mise (see SETUP.md) |

## Troubleshooting

**The scripts cannot reach the TV.** Check that the TV is on and that ADB Debugging is
enabled; Fire OS turns it off after every restart.

**The connection drops for a few seconds at a time.** On this network the router
occasionally stops passing traffic between Wi-Fi devices. The scripts reconnect
automatically. Restarting the TV or the router clears it; for a permanent fix, run this once
in an administrator command prompt (replace the address and MAC with your TV's):

```bat
netsh interface ipv4 add neighbors "Wi-Fi" 192.168.1.50 aa-bb-cc-dd-ee-ff
```

Undo with `netsh interface ipv4 delete neighbors "Wi-Fi" 192.168.1.50`.

**Home opens Amazon's home screen.** The Home button helper is off. Run `glasshome.bat` or
`scripts\install.ps1` again to re-enable it.

**The Wi-Fi pill shows "Wi-Fi" instead of the network name.** Android only reveals the network
name to apps with location permission:
`adb shell pm grant dev.glasshome.launcher android.permission.ACCESS_FINE_LOCATION`.

**Diagnostics.** Enable the launcher's log with
`adb shell setprop log.tag.GlassHome DEBUG`, then read it with `adb logcat -s GlassHome`.

## Project layout

```
config/               Debloat package list
docker/               Toolchain image, container build and install scripts
docs/images/          Screenshots
launcher/             Android project (Java, AndroidX RecyclerView)
scripts/              PowerShell scripts for the TV and host builds
glasshome.bat         One-click build and install
restore.bat           One-click restore
mise.toml             Pinned toolchain for host builds
tv.env.example        TV address template
backup/, tools/, dist/, shots/   Local only (not committed)
```

## Credits

- [Outfit](https://github.com/Outfitio/Outfit-Fonts) typeface, copyright The Outfit Project
  Authors, licensed under the SIL Open Font License 1.1
  (`launcher/app/src/main/assets/fonts/OFL.txt`).
- Pill icons from Google's Material Icons, licensed under the Apache License 2.0.
- AndroidX RecyclerView, licensed under the Apache License 2.0.
