#!/bin/bash

set -ex

cargo test --all
cargo test --all -F js
wasm-pack test --node
wasm-pack test --node -F js