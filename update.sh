#!/usr/bin/env bash
set -e
SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
cd "$SCRIPT_DIR"

echo "📥 Git pull..."
git pull --ff-only

echo "🐳 Pull aktuelle Images..."
docker compose pull

echo "🔄 Starte Container neu..."
docker compose up -d --remove-orphans

echo "✅ Update fertig."
