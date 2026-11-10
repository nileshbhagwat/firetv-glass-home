# Android build toolchain for Glass Home (Fire TV launcher).
# Every download is pinned and checksum-verified. SETUP.md lists them all.
FROM eclipse-temurin:17-jdk-jammy

ARG CMDLINE_TOOLS_ZIP=commandlinetools-linux-16111833_latest.zip
ARG CMDLINE_TOOLS_SHA1=e025545c62a8e64c7559119566a569fb1dec5f60
ARG GRADLE_VERSION=8.10.2
ARG GRADLE_SHA256=31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/cache/gradle
ENV PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/gradle/bin

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Android command-line tools (sdkmanager), verified against Google's published SHA-1
RUN curl -fsSL -o /tmp/clt.zip https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP} \
 && echo "${CMDLINE_TOOLS_SHA1}  /tmp/clt.zip" | sha1sum -c - \
 && mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && unzip -q /tmp/clt.zip -d /tmp/clt \
 && mv /tmp/clt/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
 && rm -rf /tmp/clt /tmp/clt.zip

# sdkmanager requires accepting the Android SDK License Agreement (accepted by the
# project owner, see SETUP.md). sdkmanager verifies each package's checksum itself.
RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager --install "platforms;android-34" "build-tools;34.0.0" "platform-tools" \
 && sdkmanager --list_installed

# Gradle, verified against the SHA-256 published on services.gradle.org
RUN curl -fsSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
 && echo "${GRADLE_SHA256}  /tmp/gradle.zip" | sha256sum -c - \
 && unzip -q /tmp/gradle.zip -d /opt \
 && mv /opt/gradle-${GRADLE_VERSION} /opt/gradle \
 && rm /tmp/gradle.zip \
 && gradle --version

WORKDIR /work
