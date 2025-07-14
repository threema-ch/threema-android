#!/usr/bin/env bash
set -euo pipefail

find listings/ -name "*.png" -exec pngquant --speed 1 --strip --force --ext .png {} \;
