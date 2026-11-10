#!/bin/sh
# Builds the launcher inside the toolchain container.
# Output: dist/glass-home-debug.apk
set -eu
cd /work/launcher
if [ ! -f debug.keystore ]; then
    echo "Creating project debug keystore (launcher/debug.keystore)"
    keytool -genkeypair -keystore debug.keystore -storepass android \
        -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
        -validity 10000 -dname "CN=Android Debug,O=Android,C=US" > /dev/null
fi
# Build output and Gradle's project cache stay inside the container, separate from host builds.
export GLASSHOME_BUILD_DIR=/tmp/glasshome/app-build
gradle --no-daemon --console=plain --project-cache-dir /tmp/glasshome/gradle-project assembleDebug
mkdir -p /work/dist
cp "$GLASSHOME_BUILD_DIR/outputs/apk/debug/app-debug.apk" /work/dist/glass-home-debug.apk
echo "Built dist/glass-home-debug.apk"
