#!/usr/bin/env bash

set -euo pipefail

GREEN="\033[0;32m"
RED="\033[0;31m"
RESET="\033[0m"

function log() {
    echo -en "$1"
    echo -n "$2 $3"
    echo -e "$RESET"
}

function log_major() { log "$GREEN" "==>" "$1"; }
function log_minor() { log "$GREEN" "--> " "$1"; }
function log_error() { log "$RED" "!!!" "Error: $1"; }

function fail() {
    log_error "$1"
    exit 1
}

DIR="./build/generated/source/protobuf/main"
mkdir -p "$DIR"
mkdir -p "$DIR/kotlin"
mkdir -p "$DIR/java"

log_major "Compiling protobuf"
for file in protobuf/*.proto; do
    log_minor "Building $file..."
    protoc \
      --proto_path=protobuf/ \
      --kotlin_out=lite:"$DIR/kotlin" \
      --java_out=lite:"$DIR/java" \
      -I=protobuf/ \
      "$file"
    log_minor "  OK"
done
