#!/usr/bin/env bash
#
# A build script for the release version of the Threema Android app.
#  _____ _
# |_   _| |_  _ _ ___ ___ _ __  __ _
#   | | | ' \| '_/ -_) -_) '  \/ _` |_
#   |_| |_||_|_| \___\___|_|_|_\__,_(_)
#
# Threema for Android
# Copyright (c) 2020 Threema GmbH
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License, version 3,
# as published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

set -euo pipefail

DOCKERIMAGE=threema/android-compile

GREEN="\033[0;32m"
RED="\033[0;31m"
RESET="\033[0m"

function print_usage() {
    echo "Usage: $0 -v <variants> [-b] [-k <dir>] [-o <dir>] [--no-image-export] --i-accept-the-android-sdk-license"
    echo ""
    echo "Options:"
    echo "  -v <variants>        Comma-separated variants to build: googleplay_private, googleplay_work, googleplay_onprem, hms_private, hms_work, threemashop_private, libre_private"
    echo "  -b,--build           (Re)build the Docker image"
    echo "  --no-cache           Clear Docker build cache"
    echo "  -k,--keystore <dir>  Path to the keystore directory"
    echo "  -o,--outdir <dir>    Path to the release output directory, will be created if it doesn't exist"
    echo "  --no-image-export    Skip the docker image export step"
    echo "  -h,--help            Print this help and exit"
}

function log() {
    echo -en "$1"
    echo -n "$2 $3"
    echo -e "$RESET"
}

function log_major() { log "$GREEN" "==>" "$1"; }
function log_minor() { log "$GREEN" "-->  " "$1"; }
function log_error() { log "$RED" "!!!" "Error: $1"; }

function fail() {
    log_error "$1"
    exit 1
}

# Re-implementation of realpath function (hello macOS)
function realpath() {
    OURPWD=$PWD
    cd "$(dirname "$1")"
    LINK=$(readlink "$(basename "$1")")
    while [ "$LINK" ]; do
        cd "$(dirname "$LINK")"
        LINK=$(readlink "$(basename "$1")")
    done
    REALPATH="$PWD/$(basename "$1")"
    cd "$OURPWD"
    echo "$REALPATH"
}

# Determine script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# If no arguments are passed, print usage
if [ "$#" -lt 1 ]; then print_usage; exit 1; fi

# Parse arguments
license=""
variants=""
build=0
no_cache=""
keystore=""
export_image=1
releasedir="$DIR/../release"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -v) variants="$2"; shift ;;
        -n) echo "Note: The -n parameter is deprecated and not needed anymore"; shift ;;
        -b|--build) build=1 ;;
        --no-cache) no_cache="--no-cache" ;;
        -k|--keystore) keystore="$2"; shift ;;
        -o|--outdir) releasedir="$2"; shift ;;
        --i-accept-the-android-sdk-license) license="accepted" ;;
        --no-image-export) export_image=0 ;;
        -h|--help) print_usage; exit 0 ;;
        *) echo "Unknown parameter passed: $1"; print_usage; exit 1 ;;
    esac
    shift
done
releasedir=$(realpath "$releasedir")

# Process arguments
IFS=', ' read -r -a variant_array <<< "$variants"
for variant in "${variant_array[@]}"; do
    case $variant in
        googleplay_private | googleplay_work | googleplay_onprem | hms_private | hms_work | threemashop_private | libre_private)
            # Valid
            ;;
        *)
            fail "Invalid build variant: $variant"
            ;;
    esac
done
if [ "$license" != "accepted" ]; then
    fail 'Please accept the license with "--i-accept-the-android-sdk-license"'
fi

# Determine build version and full name in the form of: "1.1[-beta1]-1000"
APP_BUILD_GRADLE_FILE="$DIR/../app/build.gradle.kts"
APP_VERSION_CODE=$(grep "^\s*val defaultVersionCode = \d*" "$APP_BUILD_GRADLE_FILE" | sed 's/[^0-9]*//g')
APP_VERSION_NAME_MAIN=$(grep '^val appVersion = "' "$APP_BUILD_GRADLE_FILE" | sed 's/^val appVersion = "\([^"]*\)".*/\1/')
APP_VERSION_NAME_SUFFIX=$(grep '^val betaSuffix = "' "$APP_BUILD_GRADLE_FILE" | sed 's/^val betaSuffix = "\([^"]*\)".*/\1/')
FULL_APP_VERSION_NAME="${APP_VERSION_NAME_MAIN}${APP_VERSION_NAME_SUFFIX}-${APP_VERSION_CODE}"

sdk_version=$(grep "^\s*compileSdk = [0-9]\+" "$APP_BUILD_GRADLE_FILE" | sed 's/[^0-9]*//g')
build_tools_version=$(grep "^\s*buildToolsVersion = \"\([0-9]\+\.\?\)\+\"" "$APP_BUILD_GRADLE_FILE" | sed 's/[^0-9\.]*//g')
rust_version=$(grep channel "$DIR/../domain/libthreema/rust-toolchain.toml" | cut -d'"' -f2)

# Validate target directory
mkdir -p "$releasedir"
name=${FULL_APP_VERSION_NAME//[^0-9\.a-zA-Z\-_]/}
if [[ "$name" == "" || "$name" == .* ]]; then
    fail "Could not process app version name ($FULL_APP_VERSION_NAME)"
fi
targetdir="$releasedir/$name"
log_major "Creating target directory $targetdir"
if [[ -d "$targetdir" ]]; then
    fail "Output directory $targetdir already exists. Please remove it first."
fi
mkdir "$targetdir"

# Build Docker image
if [ $build -eq 1 ]; then
    log_major "Building Docker image with args:"
    log_minor "app_version_code=$APP_VERSION_CODE"
    log_minor "app_version_name=$FULL_APP_VERSION_NAME"
    log_minor "sdk_version=$sdk_version"
    log_minor "build_tools_version=$build_tools_version"
    log_minor "rust_version=$rust_version"
    docker build $no_cache "$DIR/../scripts/" \
        --build-arg SDK_VERSION="$sdk_version" \
        --build-arg BUILD_TOOLS_VERSION="$build_tools_version" \
        --build-arg RUST_VERSION="$rust_version" \
        -t "$DOCKERIMAGE:latest" \
        -t "$DOCKERIMAGE:$APP_VERSION_CODE"
fi

# Build app variant(s)
for variant in "${variant_array[@]}"; do
    # Determine target and path
    case $variant in
        googleplay_private)
            target=assembleStore_googleRelease
            variant_dir="store_google"
            ;;
        googleplay_work)
            target=assembleStore_google_workRelease
            variant_dir="store_google_work"
            ;;
        googleplay_onprem)
            target=assembleOnpremRelease
            variant_dir="onprem"
            ;;
        hms_private)
            target=assembleHmsRelease
            variant_dir="hms"
            ;;
        hms_work)
            target=assembleHms_workRelease
            variant_dir="hms_work"
            ;;
        threemashop_private)
            target=assembleStore_threemaRelease
            variant_dir="store_threema"
            ;;
        libre_private)
            target=assembleLibreRelease
            variant_dir="libre"
            ;;
        *)
            fail "Invalid build variant: $variant"
            ;;
    esac

    # Compile
    log_major "Building gradle target $target"
    run_command="docker run --rm"
    if [ "${CI:-}" = "true" ]; then
        log_minor "CI detected, running Docker commands non-interactively"
    else
        run_command+=" -ti"
    fi
    run_command+=" -u \"$(id -u):$(id -g)\""
    run_command+=" -v \"$DIR/..\":/code"
    run_command+=" -v /dev/null:/code/local.properties"  # Mask local.properties file
    run_command+=" -v /code/build/"  # Mask root build directory
    run_command+=" -v /code/app/.cxx/"  # Mask ndk build directory
    if [ "$keystore" != "" ]; then
        log_minor "Using keystore at $keystore"
        keystore_realpath=$(realpath "$keystore")
        run_command+=" -v \"$keystore_realpath:/keystore\""
    fi
    run_command+=" \"$DOCKERIMAGE:$APP_VERSION_CODE\""
    run_command+=" /bin/bash -c \"cd /code && ./gradlew clean -PbuildUniversalApk $target\""
    eval "$run_command"

    # Copy files
    log_major "Copying generated files for variant $variant"
    mkdir -p "$targetdir/$variant/"{logs,mapping}/
    for f in "$DIR"/../app/build/outputs/apk/"$variant_dir"/release/*; do
        log_minor "$(basename "$f")"
        cp -r "$f" "$targetdir/$variant/"
    done
    for f in "$DIR"/../app/build/outputs/logs/*"$variant_dir"*; do
        log_minor "$(basename "$f")"
        cp "$f" "$targetdir/$variant/logs/"
    done
    for f in "$DIR"/../app/build/outputs/mapping/"$variant_dir"Release/*; do
        log_minor "$(basename "$f")"
        cp "$f" "$targetdir/$variant/mapping/"
    done
    for f in "$DIR"/../app/build/outputs/native-debug-symbols/"$variant_dir"Release/native-debug-symbols.zip; do
        log_minor "$(basename "$f")"
        cp "$f" "$targetdir/$variant/mapping/"
    done
    for f in "$DIR"/../app/build/outputs/sdk-dependencies/"$variant_dir"Release/sdkDependencies.txt; do
        log_minor "$(basename "$f")"
        cp "$f" "$targetdir/$variant/"
    done
done

# Export image
if [ $export_image -eq 1 ]; then
    log_major "Exporting docker image"
    docker image save -o "$targetdir/docker-image.tar" "$DOCKERIMAGE:$APP_VERSION_CODE"
    log_minor "Compressing docker image"
    gzip "$targetdir/docker-image.tar"
fi

log_major "Done! You can find the resulting files in the '$releasedir' directory."
