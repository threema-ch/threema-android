#!/usr/bin/env bash
set -euo pipefail

function _print_usage() {
    echo "Usage: $0 [--no-container]"
    echo ""
    echo "Options:"
    echo "  --no-container       To not source the dev container environment."
    echo "  -h,--help            Print this help and exit."
}

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h | --help)
            _print_usage
            exit 0
            ;;
        --no-container)
            _no_container=1
            ;;
        *) echo "Unknown parameter passed: $1"; _print_usage; exit 1 ;;
    esac
    shift
done

# Clean and load dev container
cd "$(dirname "$0")/.."
[[ -d ./build/uniffi ]] && rm -r ./build/uniffi/

if [[ -z ${_no_container+x} ]] ; then
    source ./.devcontainer/env.sh
fi

# Ensure consistent target dir, even if `build.target-dir` config option is set for Cargo
export CARGO_TARGET_DIR=target

# Build library
cargo build --locked -F uniffi -p libthreema --release

# Generate Kotlin bindings
cargo run \
    --locked \
    -p uniffi-bindgen generate \
    --library ./target/release/liblibthreema.so \
    --language kotlin \
    --out-dir ./build/uniffi/kotlin

# Generate Swift bindings
cargo run \
    --locked \
    -p uniffi-bindgen generate \
    --library ./target/release/liblibthreema.so \
    --language swift \
    --out-dir ./build/uniffi/swift

# Unload dev container if it was started
if [[ -z ${_no_container+x} ]] ; then
    deactivate --only-if-started
fi
