#!/usr/bin/env bash
set -euo pipefail

function _print_usage {
    echo "Usage: $0 --target=web|nodejs [--no-container] -- [parameters for .devcontainer/env.sh]"
    echo
    echo "Use target 'web' to build for Threema Desktop and the examples."
    echo "Use target 'nodejs' to build for the bindings tests."
    echo "Use --no-container to not source a devcontainer environment."
}

while [[ "$#" -gt 0 ]]; do
    case "$1" in
        -h | --help)
            _print_usage
            exit 0
            ;;
        --target=web)
            _target='web'
            _build_dir='web'
            ;;
        --target=nodejs)
            _target='experimental-nodejs-module'
            _build_dir='nodejs'
            ;;
        --no-container)
            _no_container=1
            ;;
        --)
            shift;
            break
            ;;
        *)
            echo "Unknown parameter passed: $1"
            _print_usage
            exit 1
            ;;
    esac
    shift
done
if [[ -z ${_target+x} ]] || [[ -z ${_build_dir+x} ]] ; then
    _print_usage
    exit 1
fi

# Clean and load dev container
cd "$(dirname "$0")/.."
[[ -d ./build/wasm/$_build_dir ]] && rm -r ./build/wasm/$_build_dir

if [[ -z ${_no_container+x} ]] ; then
    source ./.devcontainer/env.sh
fi

# Ensure consistent target dir, even if `build.target-dir` config option is set for Cargo
export CARGO_TARGET_DIR=target

# Build library as WASM
cargo build \
    --locked \
    -F wasm \
    -p libthreema \
    --target wasm32-unknown-unknown \
    --release

# Create WASM bindings for Rust code
wasm-bindgen \
    --split-linked-modules \
    --encode-into always \
    --reference-types \
    --out-dir ./build/wasm/$_build_dir \
    --target $_target \
    ./target/wasm32-unknown-unknown/release/libthreema.wasm

# Optimise WASM
wasm-opt \
    ./build/wasm/$_build_dir/libthreema_bg.wasm \
    -o ./build/wasm/$_build_dir/libthreema_bg.wasm \
    -O \
    --enable-reference-types

# Unload dev container if it was started
if [[ -z ${_no_container+x} ]] ; then
    deactivate --only-if-started
fi
