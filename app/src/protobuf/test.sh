#!/bin/sh
set -eu

ENTRYPOINTS="d2m d2d call-signaling"

for e in $ENTRYPOINTS; do
    DIR="out/$e/"
    ( mkdir -p "$DIR" && cd "$DIR" && mkdir -p cpp java py js )
    echo "Building $e.proto..."
    protoc \
      --cpp_out="$DIR/cpp" \
      --java_out="$DIR/java" \
      --python_out="$DIR/py" \
      --js_out="$DIR/js" \
      "$e.proto"
    echo "  OK"
done
