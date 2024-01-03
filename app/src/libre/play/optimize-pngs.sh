#!/usr/bin/env bash
set -euo pipefail

find listings/ -name "*.png" -exec optipng -o6 {} \;
