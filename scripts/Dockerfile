# Dockerfile for an Android compilation image, based on Ubuntu LTS.
#
# NOTE: This docker image automatically accepts the Android SDK licenses. It may only be used
#       from a wrapper script that explicitly asks the user to accept the license!
#
# Arguments:
#
# - `SDK_VERSION`: Set this to the desired Android SDK version (e.g. `35`)
# - `NDK_VERSION`: Set this to the desired Android NDK version (e.g. `25.2.9519653`)
# - `BUILD_TOOLS_VERSION`: Set this to the desired build tools version (e.g. `35.0.0`)
# - `RUST_VERSION`: Set this to the desired Rust version (e.g. `1.80`)

FROM docker.io/ubuntu:24.04

# Arguments
ARG SDK_VERSION=35
ARG BUILD_TOOLS_VERSION=35.0.0
ARG NDK_VERSION=25.2.9519653
ARG RUST_VERSION

# Install dependencies
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update -q \
 && apt-get -y -q install --no-install-recommends \
    build-essential file openjdk-17-jdk git wget unzip rsync vim-nox cpu-checker curl python3 protobuf-compiler \
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
    "build-tools;${BUILD_TOOLS_VERSION}" \
    "emulator"

# The location of the rustup-init script to install rust (rustup, rustc, cargo)
ENV RUSTUP_INIT=/rust/rustup-init.sh

# Set location to install rust
ENV RUSTUP_HOME=/rust/rustup
ENV CARGO_HOME=/rust/cargo

# Copy the rust installation script
COPY rustup-init.sh $RUSTUP_INIT

# Install Rust
RUN $RUSTUP_INIT -y --default-toolchain $RUST_VERSION

ENV PATH="$CARGO_HOME/bin:$PATH"

RUN rustup target add x86_64-linux-android \
  && rustup target add i686-linux-android \
  && rustup target add armv7-linux-androideabi \
  && rustup target add aarch64-linux-android

# Allow anyone to write to the cargo directory
RUN chmod a+w $CARGO_HOME

# Set env variables
ENV PATH="$ANDROID_SDK_ROOT/cmdline-tools/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Create users with typical UIDs to avoid problems with Docker when remapping the UID
RUN chmod a+w /home && for newuid in $(seq 1000 1010); do useradd -M -d /home  -u $newuid -s /bin/bash "user$newuid"; done

# Create cache directories in order to be able to control the permissions of mounted volumes
RUN mkdir -p /code/build /code/app/.cxx \
 && chmod 777 /code/build /code/app/.cxx
