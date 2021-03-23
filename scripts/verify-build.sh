#!/usr/bin/env bash
#
# A script to verify that a locally compiled APK matches the released APK.
#
# Steps taken to achieve this:
#
#   1. Unpack both APK files
#   2. Remove meta information (containing things like the signature)
#   3. Recursively diff the two directories to ensure they match
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

GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[1;33m"
RESET="\033[0m"

function print_usage() {
    echo "Usage: $0 -n <version-name> -v <variant> -p <published-apk> [-l <local-apk>]"
    echo ""
    echo "Options:"
    echo "  -n <version-name>    The version name. Example: '4.43k'"
    echo "  -v <variant>         Variant to verify: Either googleplay or threemashop"
    echo "  -p <published-apk>   Path to the APK file extracted from the phone"
    echo "  -l <local-apk>       Optional: Path to the locally built APK"
    echo "  -h,--help            Print this help and exit"
}

function log() {
    echo -en "$1"
    echo -n "$2 $3"
    echo -e "$RESET"
}

function log_major() { log "$GREEN" "==>" "$1"; }
function log_minor() { log "$GREEN" "-->  " "$1"; }
function log_warning() { log "$YELLOW" "==>" "$1"; }
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
name=""
variant=""
published_apk=""
local_apk=""
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -n) name="$2"; shift ;;
        -v) variant="$2"; shift ;;
        -p) published_apk="$2"; shift ;;
        -l) local_apk="$2"; shift ;;
        -h|--help) print_usage; exit 0 ;;
        *) echo "Unknown parameter passed: $1"; print_usage; exit 1 ;;
    esac
    shift
done

# Process arguments
if [[ "$name" == "" || "$name" == .* ]]; then
    log_error 'Please set a valid version name with "-n <name>".'
    fail 'Example: "-n 4.43k"'
fi
if [[ "$published_apk" == "" ]]; then
    log_error 'Please set a valid published APK path with "-p <path>".'
    fail 'Example: "-p threema-extracted.apk"'
fi
published_apk=$(realpath "$published_apk")
if [[ "$variant" == "" ]]; then
    log_error 'Please set a valid build variant with "-v <variant>".'
    fail 'Example: "-v googleplay" or "-v threemashop"'
fi
case "$variant" in
    googleplay) variant_name="store_google" ;;
    threemashop) variant_name="store_threema" ;;
    *) fail "Invalid build variant: $variant" ;;
esac

# Validate target directory
targetdir=$(realpath "$DIR/../reproduce")
if [[ -d "$targetdir" ]]; then
    fail "The directory $targetdir already exists. Please remove it first."
fi
mkdir -p "$targetdir"/{published,local}

# Unpack published APK
if [[ ! -f "$published_apk" ]]; then
    fail "The published APK $published_apk could not be found."
fi
log_major "Unpacking published APK"
unzip -q -d "$targetdir/published/" "$published_apk"

# Determine local APK path
if [[ "$local_apk" == "" ]]; then
    log_major "Determine local APK path"
    lib_count="$(find "$targetdir/published/lib/" -mindepth 1 -maxdepth 1 -type d | wc -l | xargs)"
    log_minor "Found $lib_count libs"
    case "$lib_count" in
        1) architecture="$(ls "$targetdir/published/lib/")" ;;
        4) architecture="universal" ;;
        *) fail "Could not determine architecture of published APK"
    esac
    log_minor "Architecture: $architecture"
    if [ -f "$DIR/../release/$name/$variant/app-$variant_name-$architecture-release-unsigned.apk" ]; then
        local_apk="$(realpath "$DIR/../release/$name/$variant/app-$variant_name-$architecture-release-unsigned.apk")"
    else
        local_apk="$(realpath "$DIR/../release/$name/$variant/app-$variant_name-$architecture-release.apk")"
    fi
fi
if [[ ! -f "$local_apk" ]]; then
    fail "The local APK $local_apk could not be found."
fi
log_major "Comparing the following APKs:"
log_minor "Published: $published_apk"
log_minor "Local:     $local_apk"

# Unpack local APK
log_major "Unpacking local APK"
unzip -q -d "$targetdir/local/" "$local_apk"

# Remove meta information (containing things like the signature)
log_major "Removing meta information, containing things like the app signature:"
for path in META-INF/ resources.arsc; do
    for target in local published; do
        log_minor "rm -r $target/$path"
        rm -r "${targetdir:?}/${target:?}/${path:?}"
    done
done

# Diff!
log_major "Comparing releases"
diff -r "$targetdir/local/" "$targetdir/published/" && success=1 || success=0
if [ $success -eq 1 ]; then
    log_major "Success! The APKs match."
else
    log_warning "APK could not be verified."
    log_warning "Don't panic! First, make sure that you have compiled the correct version!"
    log_warning "If you cannot figure out why the verification failed,"
    log_warning "send us an e-mail to opensource@threema.ch containing the log above."
    exit 2
fi
