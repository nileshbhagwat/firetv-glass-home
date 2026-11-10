#!/bin/sh
# Installs Glass Home on the TV and makes it the home screen. Runs inside the toolchain
# container, which reuses the host's ADB key (mounted at /root/.android).
# Usage: sh /work/docker/install.sh <tv-ip>
set -eu
TV="${1:?TV IP address required}:5555"
APK=/work/dist/glass-home-debug.apk
PKG=dev.glasshome.launcher
SERVICE=$PKG/$PKG.HomeRedirectService

connect() {
    i=0
    while [ $i -lt 8 ]; do
        adb connect "$TV" > /dev/null 2>&1 || true
        [ "$(adb -s "$TV" get-state 2>/dev/null)" = "device" ] && return 0
        i=$((i + 1))
        sleep 3
    done
    echo "Cannot reach $TV. Check that the TV is on and ADB debugging is enabled"
    echo "(Fire OS switches ADB debugging off whenever the TV restarts)."
    exit 1
}

# The Wi-Fi link can drop briefly, so every call reconnects first.
tv() {
    connect
    adb -s "$TV" "$@"
}

[ -f "$APK" ] || { echo "Missing $APK. Build first."; exit 1; }
adb start-server > /dev/null 2>&1
connect
echo "Installing $APK"
tv install -r "$APK"
echo "Granting location permission (needed to show the Wi-Fi network name)"
tv shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
echo "Setting Glass Home as the default home app"
tv shell cmd package set-home-activity $PKG/.MainActivity || true

if [ "${ENABLE_HOME_HELPER:-1}" = "1" ]; then
    echo "Enabling the Home button helper (accessibility service)"
    current=$(tv shell settings get secure enabled_accessibility_services | tr -d '\r')
    case "$current" in
        *"$PKG"*) updated="$current" ;;
        null|"") updated="$SERVICE" ;;
        *) updated="$current:$SERVICE" ;;
    esac
    tv shell settings put secure enabled_accessibility_services "$updated"
    tv shell settings put secure accessibility_enabled 1
fi

tv shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $PKG/.MainActivity > /dev/null
echo "Done. Glass Home is running on $TV."
