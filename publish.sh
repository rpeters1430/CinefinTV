#!/bin/bash

# CinefinTV One-Click Publish Script
# This script bumps the version, tags the commit, and triggers the GitHub release build.
# 
# Usage:
#   chmod +x publish.sh
#   ./publish.sh

set -e

# 1. Get current version info
OLD_VERSION_CODE=$(grep "versionCode =" app/build.gradle.kts | awk '{print $3}')
OLD_VERSION_NAME=$(grep "versionName =" app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')

echo "Current version: $OLD_VERSION_NAME (code $OLD_VERSION_CODE)"

# 2. Ask for new version info
read -p "Enter new version name (e.g., 1.0.2): " NEW_VERSION_NAME
NEW_VERSION_CODE=$((OLD_VERSION_CODE + 1))

echo "Bumping to: $NEW_VERSION_NAME (code $NEW_VERSION_CODE)"

# 3. Update build.gradle.kts
sed -i "s/versionCode = $OLD_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" app/build.gradle.kts
sed -i "s/versionName = \"$OLD_VERSION_NAME\"/versionName = \"$NEW_VERSION_NAME\"/" app/build.gradle.kts

# 4. Commit and Tag
git add app/build.gradle.kts
git commit -m "chore: bump version to $NEW_VERSION_NAME"
git push origin main

TAG="v$NEW_VERSION_NAME"
git tag "$TAG"
git push origin "$TAG"

echo "-------------------------------------------------------"
echo "Success! GitHub is now building and releasing $TAG."
echo "The update will be live in a few minutes."
echo "-------------------------------------------------------"
