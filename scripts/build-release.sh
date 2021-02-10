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
    echo "Usage: $0 -v <variants> -n <version-name> [-b] [-k <dir>] [-o <dir>] [--no-image-export] --i-accept-the-android-sdk-license"
    echo ""
    echo "Options:"
    echo "  -v <variants>        Comma-separated variants to build: googleplay, threemashop"
    echo "  -n <version-name>    The version name. Example: '4.43k'"
    echo "  -b,--build           (Re)build the Docker image"
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
name=""
build=0
keystore=""
export_image=1
releasedir="$DIR/../release"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -v) variants="$2"; shift ;;
        -n) name="$2"; shift ;;
        -b|--build) build=1 ;;
        -k|--keystore) keystore="$2"; shift ;;
        -o|--outdir) releasedir="$2"; shift ;;
        --i-accept-the-android-sdk-license) license="accepted" ;;
        --no-image-export) export_image=0 ;;
        -h|--help) print_usage; exit 0 ;;
        *) echo "Unknown parameter passed: $1"; print_usage; exit 1 ;;
    esac
    shift
done
releasedir=$(realpath $releasedir)

# Process arguments
IFS=', ' read -r -a variant_array <<< "$variants"
for variant in "${variant_array[@]}"; do
    case $variant in
        googleplay)
            # Valid
            ;;
        threemashop)
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
name=${name//[^0-9\.a-zA-Z\-]/}
if [[ "$name" == "" || "$name" == .* ]]; then
    log_error 'Please set a valid version name with "-n <name>".'
    fail 'Example: "-n 4.43k"'
fi

# Determine build version
app_version=$(grep "^\s*versionCode \d*" "$DIR/../app/build.gradle" | sed 's/[^0-9]*//g')
sdk_version=$(grep "^\s*compileSdkVersion \d*" "$DIR/../app/build.gradle" | sed 's/[^0-9]*//g')
build_tools_version=$(grep "^\s*buildToolsVersion \d*" "$DIR/../app/build.gradle" | sed 's/[^0-9\.]*//g')

# Validate target directory
mkdir -p "$releasedir"
targetdir="$releasedir/$name"
log_major "Creating target directory $targetdir"
if [[ -d "$targetdir" ]]; then
    fail "Output directory $targetdir already exists. Please remove it first."
fi
mkdir "$targetdir"

# Build Docker image
if [ $build -eq 1 ]; then
    log_major "Building Docker image with args:"
    log_minor "app_version=$app_version"
    log_minor "sdk_version=$sdk_version"
    log_minor "build_tools_version=$build_tools_version"
    docker build "$DIR/../scripts/" \
        --build-arg SDK_VERSION="$sdk_version" \
        --build-arg BUILD_TOOLS_VERSION="$build_tools_version" \
        -t "$DOCKERIMAGE:latest" \
        -t "$DOCKERIMAGE:$app_version"
fi

# Build app variant(s)
for variant in "${variant_array[@]}"; do
    # Determine target and path
    case $variant in
        googleplay)
            target=assembleStore_googleRelease
            variant_dir="store_google"
            ;;
        threemashop)
            target=assembleStore_threemaRelease
            variant_dir="store_threema"
            ;;
        *)
            fail "Invalid build variant: $variant"
            ;;
    esac

    # Compile
    log_major "Building gradle target $target"
    run_command="docker run --rm -ti"
    run_command+=" -u \"$(id -u):$(id -g)\""
    run_command+=" -v \"$DIR/..:/code\""
    if [ "$keystore" != "" ]; then
        log_minor "Using keystore at $keystore"
        keystore_realpath=$(realpath "$keystore")
        run_command+=" -v \"$keystore_realpath:/keystore\""
    fi
    run_command+=" \"$DOCKERIMAGE:$app_version\""
    run_command+=" /bin/bash -c \"cd /code && ./gradlew clean $target\""
    eval "$run_command"

    # Copy files
    log_major "Copying generated files for variant $variant"
    mkdir -p "$targetdir/$variant/"{logs,mapping}/
    for f in "$DIR"/../app/build/outputs/apk/"$variant_dir"/release/*; do
        log_minor "$(basename "$f")"
        cp "$f" "$targetdir/$variant/"
    done
    for f in "$DIR"/../app/build/outputs/logs/*"$variant_dir"*; do
        log_minor "$(basename "$f")"
        cp "$f" "$targetdir/$variant/logs/"
    done
    for f in "$DIR"/../app/build/outputs/mapping/"$variant_dir"Release/*; do
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
    docker image save -o "$targetdir/docker-image.tar" "$DOCKERIMAGE:$app_version"
    log_minor "Compressing docker image"
    gzip "$targetdir/docker-image.tar"
fi

# Generate checksums
log_major "Generate APK checksums"
for variant in "${variant_array[@]}"; do
    log_minor "$variant"
    (cd "$targetdir/$variant/" && shasum -a 256 ./*.apk > apk-checksums-sha256.txt)
done

log_major "Done! You can find the resulting files in the '$releasedir' directory."
