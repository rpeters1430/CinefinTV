#!/bin/bash

set -euo pipefail

BUILD_FILE="app/build.gradle.kts"
WORKFLOW_FILE=".github/workflows/release.yml"
BACKUP_FILE=""

cleanup_build_file() {
    if [[ -n "${BACKUP_FILE:-}" && -f "${BACKUP_FILE:-}" ]]; then
        cp "$BACKUP_FILE" "$BUILD_FILE"
        rm -f "$BACKUP_FILE"
    fi
}

has_release_signing() {
    [[ -n "${CINEFIN_RELEASE_STORE_FILE:-}" ]] &&
    [[ -n "${CINEFIN_RELEASE_STORE_PASSWORD:-}" ]] &&
    [[ -n "${CINEFIN_RELEASE_KEY_ALIAS:-}" ]] &&
    [[ -n "${CINEFIN_RELEASE_KEY_PASSWORD:-}" ]]
}

require_clean_worktree() {
    if ! git diff --quiet --ignore-submodules -- || ! git diff --cached --quiet --ignore-submodules --; then
        echo "Working tree has uncommitted changes. Commit or stash them before publishing."
        exit 1
    fi
}

require_main_branch() {
    local branch
    branch="$(git rev-parse --abbrev-ref HEAD)"
    if [[ "$branch" != "main" ]]; then
        echo "Publish from main. Current branch: $branch"
        exit 1
    fi
}

extract_version_code() {
    grep "versionCode =" "$BUILD_FILE" | awk '{print $3}'
}

extract_version_name() {
    grep "versionName =" "$BUILD_FILE" | sed 's/.*"\(.*\)".*/\1/'
}

update_build_version() {
    local old_code="$1"
    local new_code="$2"
    local old_name="$3"
    local new_name="$4"

    perl -0pi -e "s/versionCode = \Q$old_code\E/versionCode = $new_code/" "$BUILD_FILE"
    perl -0pi -e "s/versionName = \"\Q$old_name\E\"/versionName = \"$new_name\"/" "$BUILD_FILE"
}

main() {
    require_clean_worktree
    require_main_branch

    local old_version_code old_version_name
    old_version_code="$(extract_version_code)"
    old_version_name="$(extract_version_name)"

    echo "Current version: $old_version_name (code $old_version_code)"
    read -r -p "Enter new version name (e.g. 1.0.2): " new_version_name

    if [[ -z "$new_version_name" ]]; then
        echo "Version name cannot be empty."
        exit 1
    fi

    if [[ "$new_version_name" == "$old_version_name" ]]; then
        echo "New version name must differ from the current version."
        exit 1
    fi

    local new_version_code
    new_version_code=$((old_version_code + 1))
    local tag="v$new_version_name"

    if git rev-parse "$tag" >/dev/null 2>&1; then
        echo "Tag $tag already exists."
        exit 1
    fi

    BACKUP_FILE="$(mktemp)"
    cp "$BUILD_FILE" "$BACKUP_FILE"
    trap cleanup_build_file EXIT

    echo "Bumping to: $new_version_name (code $new_version_code)"
    update_build_version "$old_version_code" "$new_version_code" "$old_version_name" "$new_version_name"

    if has_release_signing; then
        echo "Building signed release APK locally"
        ./gradlew :app:assembleRelease
    else
        echo "Release signing variables not set locally; building debug APK for a compile check only"
        ./gradlew :app:assembleDebug
    fi

    git add "$BUILD_FILE"
    git commit -m "chore: bump version to $new_version_name"
    git tag "$tag"

    trap - EXIT
    rm -f "$BACKUP_FILE"
    BACKUP_FILE=""

    echo "Pushing main and tag $tag"
    git push origin main
    git push origin "$tag"

    cat <<EOF
-------------------------------------------------------
Published $tag.
GitHub Actions will build app-release.apk and update updates/version.json after the release job succeeds.
Workflow: $WORKFLOW_FILE
-------------------------------------------------------
EOF
}

main "$@"
