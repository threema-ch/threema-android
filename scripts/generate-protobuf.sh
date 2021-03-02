#!/usr/bin/env bash
set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
ROOT="$DIR/../"

PROTOC=protoc
INPUT_DIR="${ROOT}app/src/protobuf/"
INPUT_FILE="${INPUT_DIR}call-signaling.proto"
OUTPUT_DIR="${ROOT}app/src/main/java/"

# Function to check whether a command exists or not
command_exists() { command -v "$1" >/dev/null 2>&1 ; }

# Ensure that protoc is installed
if ! command_exists $PROTOC; then
    echo "ERROR: Protobuf compiler \"$PROTOC\" not found in your PATH"
    exit 1
fi

# Ensure that protobuf file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "ERROR: Protobuf file $INPUT_FILE not found."
    echo "Did you clone the git submodule?"
    echo ""
    echo "  $ git submodule update --init"
    exit 2
fi

echo "Config:"
echo "  Input dir: $INPUT_DIR"
echo "  Output dir: $OUTPUT_DIR"
echo "Running $PROTOC..."
$PROTOC \
    -I=$INPUT_DIR \
    --java_out=lite:$OUTPUT_DIR \
    $INPUT_FILE
echo "Done!"
