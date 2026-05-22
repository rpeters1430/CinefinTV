#!/usr/bin/env bash
set -euo pipefail

REPO_URL="https://github.com/android/skills.git"
TARGET_DIR=".gemini/skills/android"

if [[ -d "$TARGET_DIR/.git" ]]; then
  echo "Updating existing Android skills checkout in $TARGET_DIR"
  git -C "$TARGET_DIR" fetch --depth=1 origin
  git -C "$TARGET_DIR" reset --hard origin/main
else
  echo "Cloning Android skills into $TARGET_DIR"
  rm -rf "$TARGET_DIR"
  git clone --depth 1 "$REPO_URL" "$TARGET_DIR"
fi

# Keep only skill content (no nested git history) so the project remains self-contained.
rm -rf "$TARGET_DIR/.git"

echo "Android skills synced at $TARGET_DIR"
