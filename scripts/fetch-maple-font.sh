#!/usr/bin/env bash
# Fetch the Maple Mono variable woff2 fonts (Roman + Italic) from the upstream
# repo's `variable` branch and drop them into src/main/resources/sibgen/fonts/,
# along with the OFL 1.1 license.
#
# The release zips on github.com/subframe7536/maple-font ship variable TTF and
# static woff2 — the variable woff2 we want lives only on the `variable`
# branch (auto-built per release). We pin to a specific SHA for reproducibility;
# bump it when a newer Maple Mono release is desired.
#
# The two woff2 files are committed to the repo; this script is a
# reproducibility aid, run by hand when bumping versions.

set -euo pipefail

# variable-branch HEAD as of 2026-04-05, which tracks the v7.9 release line.
SHA="${MAPLE_SHA:-d89be3884f6fb6e989ae934503f39f9c32d3222d}"
LICENSE_TAG="${MAPLE_LICENSE_TAG:-v7.9}"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FONTS_DIR="$REPO_ROOT/src/main/resources/sibgen/fonts"

ROMAN_URL="https://raw.githubusercontent.com/subframe7536/maple-font/$SHA/woff2/var/MapleMono%5Bwght%5D-VF.woff2"
ITALIC_URL="https://raw.githubusercontent.com/subframe7536/maple-font/$SHA/woff2/var/MapleMono-Italic%5Bwght%5D-VF.woff2"
LICENSE_URL="https://raw.githubusercontent.com/subframe7536/maple-font/refs/tags/$LICENSE_TAG/OFL.txt"

mkdir -p "$FONTS_DIR"

echo "==> Downloading Maple Mono variable woff2 (sha $SHA)"
curl -fsSL "$ROMAN_URL"   -o "$FONTS_DIR/MapleMono-Variable.woff2"
curl -fsSL "$ITALIC_URL"  -o "$FONTS_DIR/MapleMono-Variable-Italic.woff2"
curl -fsSL "$LICENSE_URL" -o "$FONTS_DIR/LICENSE-Maple-Mono.txt"

ls -lh "$FONTS_DIR/MapleMono-Variable.woff2" "$FONTS_DIR/MapleMono-Variable-Italic.woff2" "$FONTS_DIR/LICENSE-Maple-Mono.txt"
echo "==> Done. Maple Mono installed (sha $SHA, license $LICENSE_TAG)."
