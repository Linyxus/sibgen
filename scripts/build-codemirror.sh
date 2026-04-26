#!/usr/bin/env bash
# Build the vendored CodeMirror 6 IIFE bundle that ships with each rendered page.
# Run this after editing js-src/codemirror/entry.ts or bumping CM6 deps.
#
# Output: src/main/resources/sibgen/js/codemirror.iife.js (~100 KB)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$REPO_ROOT/js-src/codemirror"
OUT_FILE="$REPO_ROOT/src/main/resources/sibgen/js/codemirror.iife.js"

cd "$SRC_DIR"

if [ ! -d node_modules ]; then
  echo "==> Installing CodeMirror dependencies into $SRC_DIR/node_modules"
  npm install --silent --no-audit --no-fund
fi

echo "==> Bundling entry.ts -> $OUT_FILE"
npx --yes esbuild entry.ts \
  --bundle \
  --format=iife \
  --target=es2018 \
  --minify \
  --legal-comments=none \
  --outfile="$OUT_FILE"

ls -lh "$OUT_FILE"
