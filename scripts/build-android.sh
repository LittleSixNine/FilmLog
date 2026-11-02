#!/bin/sh
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -f "$PROJECT_ROOT/.local.env" ]; then
 set -a
 . "$PROJECT_ROOT/.local.env"
 set +a
fi
export BUILD_DIR="${BUILD_DIR:-$PROJECT_ROOT/artifacts/android}"
exec /bin/sh "$PROJECT_ROOT/app/build.sh"
