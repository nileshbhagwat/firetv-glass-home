# Setup

This project keeps its toolchain inside the project folder, inside Docker, or in mise's
per-version tool store. The only global installs are Docker Desktop (Option A) and mise
(Option B).

- **Option A, Docker** (default): one click with `glasshome.bat`.
- **Option B, mise**: build directly on Windows with tools pinned in `mise.toml`.

Both options produce `dist/glass-home-debug.apk`.

## Requirements

- Windows 10 or 11.
- Docker Desktop (Option A) or mise (Option B).
- The TV on the same network as the PC, with ADB Debugging enabled
  (Settings > My Fire TV > Developer Options). Fire OS turns ADB Debugging off whenever the TV
  restarts.
- `tv.env` with the TV's address (copy `tv.env.example`).

## Option A: Docker

Double-click `glasshome.bat`. It:

1. Starts Docker Desktop if it is not running.
2. Builds the toolchain image `glasshome-builder:1` from `docker/Dockerfile` on the first run.
3. Builds the APK in a container (`docker/build.sh`), caching Gradle downloads in the Docker
   volume `glasshome-gradle`.
4. Installs the APK on the TV from a container (`docker/install.sh`), reusing the host's ADB
   key from `%USERPROFILE%\.android`, so the TV does not ask for authorisation again.

The equivalent commands, run from the project folder:

```bat
docker build -t glasshome-builder:1 docker
docker run --rm -v "%CD%:/work" -v glasshome-gradle:/cache/gradle glasshome-builder:1 sh /work/docker/build.sh
docker run --rm -v "%CD%:/work" -v "%USERPROFILE%\.android:/root/.android" glasshome-builder:1 sh /work/docker/install.sh 192.168.1.50
```

## Option B: mise, step by step

Tested on this machine: the build takes about three minutes and the APK carries the same
signing certificate as the Docker build, so either can update the installed app.

| Step | Command | What it does |
| --- | --- | --- |
| 1. mise | `winget install jdx.mise` | One-time; the only global install. Open a new terminal afterwards |
| 2. Trust | `mise trust` | Lets mise read this project's `mise.toml` |
| 3. Java and Gradle | `mise install` | Temurin JDK 17.0.21 and Gradle 8.10.2 (about 320 MB, kept by mise per version) |
| 4. Android SDK | `mise run sdk` | Platform 34, build tools 34.0.0 and platform-tools into `tools\android-sdk` (about 450 MB), with usage metrics off |
| 5. adb | `powershell -ExecutionPolicy Bypass -File scripts\get-adb.ps1` | Only needed if `tools\platform-tools` is missing |
| 6. Build | `mise run build` | Produces `dist\glass-home-debug.apk`; the first build downloads about 350 MB of Gradle libraries into `.gradle-home` |
| 7. Install | `mise run install` | Installs on the TV at the address in `tv.env` |

`mise.toml` points `ANDROID_HOME` at `tools/android-sdk` and `GRADLE_USER_HOME` at
`.gradle-home`, both inside the project.

## Downloads

Every file downloaded for this project, with its source and checksum.

### Host (project `tools/` folder, not committed)

| Item | Source | Size | Checksum |
| --- | --- | --- | --- |
| Android platform-tools 37.0.1 for Windows (adb) | `https://dl.google.com/android/repository/platform-tools-latest-windows.zip` | 8,044,989 bytes | SHA-256 `45f4d63113e895ebde0c90f194099a4676b6ac653bd28d54314a9e022bbc1a99`; `adb.exe` Authenticode-signed by Google LLC |

### Committed to the repository

| Item | Source | Size | Checksum |
| --- | --- | --- | --- |
| Outfit variable font (`Outfit.ttf`) | `https://github.com/google/fonts/raw/main/ofl/outfit/Outfit%5Bwght%5D.ttf` | 110,884 bytes | SHA-256 `fc7287273e66929776e2ba54f144fe699080bec29f61bf649d70d871468aeade` |
| Outfit licence (`OFL.txt`) | `https://github.com/google/fonts/raw/main/ofl/outfit/OFL.txt` | 4,389 bytes | SHA-256 `c676351bf8576b9aba743cd5eaa8c0e7ee0d51f805d720447b4df4ddb6a2e416` |

### Docker image `glasshome-builder:1`

Image ID `sha256:ae9cebeb596410bf038af158b959720e1a8aaf19eb187ad937ae830aea76afbe`,
2.16 GB in Docker Desktop's image store.

| Item | Source | Size | Checksum |
| --- | --- | --- | --- |
| Base image `eclipse-temurin:17-jdk-jammy` (OpenJDK 17.0.21, Ubuntu 22.04) | Docker Hub | n/a | Digest `sha256:057a0daec60e87c96571bf5c9c7792065bcb19e804d584cd37d4144038a35717` |
| `unzip` 6.0-26ubuntu3.2, `ca-certificates` 20260816ubuntu0.22.04.2 | `archive.ubuntu.com` (apt) | small | Verified by apt (`curl` 7.81.0 was already in the base image) |
| Android command-line tools `commandlinetools-linux-16111833_latest.zip` | `https://dl.google.com/android/repository/` | 172.7 MB | SHA-1 `e025545c62a8e64c7559119566a569fb1dec5f60` (checked in the Dockerfile) |
| SDK platform `platforms;android-34` (`platform-34-ext12_r01.zip`) | Google, via sdkmanager | 60.6 MB | SHA-1 `ba80ccbcc29b29f25ac926a08c0b2777f0bce842` (checked by sdkmanager) |
| SDK build tools `build-tools;34.0.0` (`build-tools_r34-linux.zip`) | Google, via sdkmanager | 58.4 MB | SHA-1 `d6d58e0c6925a9e4d9a541e84cd1f405c2f9d2a9` (checked by sdkmanager) |
| SDK platform-tools 37.0.1 for Linux | Google, via sdkmanager | 8.6 MB | SHA-1 `477254aa5f903c15cf51001717bdf347fb6b53e0` (checked by sdkmanager) |
| Gradle 8.10.2 (`gradle-8.10.2-bin.zip`) | `https://services.gradle.org/distributions/` | 130.4 MB | SHA-256 `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26` (checked in the Dockerfile) |

### Docker volume `glasshome-gradle` (Gradle cache, 365 MB)

Downloaded by Gradle from Google Maven (`maven.google.com`) and Maven Central:

- Android Gradle Plugin 8.7.3 and its dependencies (build-time only).
- Libraries packaged into the app:
  `androidx.recyclerview:recyclerview:1.3.2` and its dependencies
  `androidx.annotation:annotation:1.2.0`, `androidx.annotation:annotation-experimental:1.1.0`,
  `androidx.arch.core:core-common:2.1.0`, `androidx.arch.core:core-runtime:2.1.0`,
  `androidx.collection:collection:1.0.0`, `androidx.concurrent:concurrent-futures:1.0.0`,
  `androidx.core:core:1.7.0`, `androidx.core:core-ktx:1.5.0`,
  `androidx.customview:customview:1.0.0`, `androidx.customview:customview-poolingcontainer:1.0.0`,
  `androidx.lifecycle:lifecycle-common:2.3.1`, `androidx.lifecycle:lifecycle-runtime:2.3.1`,
  `androidx.versionedparcelable:versionedparcelable:1.1.1`,
  `com.google.guava:listenablefuture:1.0`, `org.jetbrains.kotlin:kotlin-stdlib:1.6.21`,
  `org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21`.

To list the resolved versions again:

```bat
docker run --rm -v "%CD%:/work" -v glasshome-gradle:/cache/gradle -w /work/launcher glasshome-builder:1 gradle -q :app:dependencies --configuration debugRuntimeClasspath
```

### mise build (Option B)

| Item | Source | Size | Location |
| --- | --- | --- | --- |
| mise 2026.11.2 (`mise-v2026.11.2-windows-x64.zip`) | winget package `jdx.mise`, from `github.com/jdx/mise` releases | 81.6 MB | Installed by the project owner |
| Temurin JDK 17.0.21+9 (`OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip`) | Eclipse Adoptium, via mise | 191.1 MB | `%LOCALAPPDATA%\mise\installs\java` |
| Gradle 8.10.2 (`gradle-8.10.2-bin.zip`) | `services.gradle.org`, via mise | 130.4 MB | `%LOCALAPPDATA%\mise\installs\gradle` |
| Android command-line tools `commandlinetools-win-16111833_latest.zip` | `https://dl.google.com/android/repository/` | 147.8 MB | `tools\android-sdk\cmdline-tools` |
| Platform 34, build tools 34.0.0, platform-tools 37.0.1 (Windows) | Google, via the Android CLI and the Android Gradle Plugin | about 130 MB | `tools\android-sdk` |
| Android Gradle Plugin 8.7.3 and the app libraries listed above | Google Maven and Maven Central, via Gradle | 344 MB | `.gradle-home` |

## Licences accepted

- **Android SDK License Agreement**: accepted through `sdkmanager --licenses` while building the
  Docker image, with the project owner's approval, on 10 November 2026. Option B accepts the
  same licence when `mise run sdk` runs `scripts/setup-sdk.ps1`.

## Usage metrics

- **Android CLI.** Since command-line tools 16111833 the SDK tools are the Android CLI
  (`android`, version 1.1.16717390), which collects usage metrics by default. `sdkmanager` is
  now a deprecated wrapper around it and cannot turn this off, so `scripts\setup-sdk.ps1`
  calls `android --no-metrics` for every command.
- **Before the switch.** The Docker image build and the first `mise run sdk` used
  `sdkmanager` with metrics on; one upload from the laptop took place on 10 November 2026.
  The three reports still queued in `%USERPROFILE%\.android\cli\analytics\metrics\spool`
  were deleted the same day. The Dockerfile still calls `sdkmanager`; this only matters when
  the image is rebuilt.
- **Android Studio and Gradle plugin analytics** are off:
  `%USERPROFILE%\.android\analytics.settings` contains `"hasOptedIn": false`.

## Files outside the project folder

| Location | Created by | Purpose |
| --- | --- | --- |
| `%USERPROFILE%\.android\adbkey`, `adbkey.pub` | adb | Key that the TV authorised with *Always allow from this computer* |
| `%USERPROFILE%\.android\bin\android-cli.exe`, `%USERPROFILE%\.android\cli\`, `%USERPROFILE%\.android\cache\` | Android CLI | CLI binary, its state and repository cache |
| `%LOCALAPPDATA%\mise\installs\java`, `...\gradle` | mise | Java 17 and Gradle 8.10.2 for Option B |
| Docker image `glasshome-builder:1` | `glasshome.bat` | Build toolchain |
| Docker volume `glasshome-gradle` | `glasshome.bat` | Gradle download cache |

## Removing the toolchain

```bat
docker rmi glasshome-builder:1
docker volume rm glasshome-gradle
rmdir /s /q tools dist .gradle-home launcher\.gradle launcher\app\build launcher\build
mise uninstall java@temurin-17.0.21+9 gradle@8.10.2
```

To stop the PC being trusted by the TV, delete `%USERPROFILE%\.android\adbkey*` and turn
ADB Debugging off on the TV.
