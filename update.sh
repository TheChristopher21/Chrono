#!/usr/bin/env bash
set -e
cd "$(dirname "$(readlink -f "$0")")"

echo "📥 Git pull..."
git pull --ff-only

echo "🐳 Pull aktuelle Images..."
docker compose pull

echo "🔄 Starte Container neu..."
docker compose up -d --remove-orphans

echo "✅ Update fertig."
