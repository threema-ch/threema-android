# Dockerfile for an Android compilation image, based on Ubuntu LTS.
#
# NOTE: This docker image automatically accepts the Android SDK licenses. It may only be used
#       from a wrapper script that explicitly asks the user to accept the license!
#
# Arguments:
#
# - `SDK_VERSION`: Set this to the desired Android SDK version (e.g. `28`)
# - `BUILD_TOOLS_VERSION`: Set this to the desired build tools version (e.g. `28.0.3`)

FROM ubuntu:20.04

# Arguments
ARG SDK_VERSION=29
ARG BUILD_TOOLS_VERSION=29.0.3
ARG NDK_VERSION=21.1.6352462

# Install dependencies
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update -q \
 && apt-get -y -q install --no-install-recommends \
    build-essential file openjdk-8-jdk git wget unzip rsync vim-nox cpu-checker \
 && rm -rf /var/lib/apt/lists/*

# Download Android command line tools
RUN mkdir -p /opt/android/cmdline-tools \
 && cd /opt/android/cmdline-tools \
 && wget https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip \
 && unzip commandlinetools-linux-6609375_latest.zip \
 && rm commandlinetools-linux-6609375_latest.zip
ENV ANDROID_SDK_ROOT=/opt/android

# Install Android SDK
RUN yes Y | /opt/android/cmdline-tools/tools/bin/sdkmanager --licenses
RUN /opt/android/cmdline-tools/tools/bin/sdkmanager --install \
    "tools" \
    "platform-tools" \
    "platforms;android-${SDK_VERSION}" \
    "ndk;${NDK_VERSION}" \
    "patcher;v4" \
    "build-tools;${BUILD_TOOLS_VERSION}" \
    "emulator"

# Set env variables
ENV PATH="$ANDROID_SDK_ROOT/cmdline-tools/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Create users with typical UIDs to avoid problems with Docker when remapping the UID
RUN chmod a+w /home && for newuid in $(seq 1000 1010); do useradd -M -d /home  -u $newuid -s /bin/bash "user$newuid"; done

# Create cache directories in order to be able to control the permissions of mounted volumes
RUN mkdir -p /code/build /code/app/.cxx \
 && chmod 777 /code/build /code/app/.cxx
