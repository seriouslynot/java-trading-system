#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
(cd frontend && npm ci && npm run build && npm run lint)
mkdir -p app/src/main/resources/static
cp -R frontend/dist/. app/src/main/resources/static/
./gradlew build
