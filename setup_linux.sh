#!/bin/bash

# CinefinTV Linux setup script
# Supports Ubuntu/Debian, CachyOS/Arch Linux, and Fedora.

set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
ANDROID_PLATFORM="platforms;android-36"
ANDROID_BUILD_TOOLS="build-tools;36.0.0"

log() {
    echo "[CinefinTV setup] $1"
}

require_sudo() {
    if ! command -v sudo >/dev/null 2>&1; then
        echo "This script requires sudo to install system packages."
        exit 1
    fi
}

detect_distro() {
    if [[ ! -r /etc/os-release ]]; then
        echo "Could not detect Linux distribution: /etc/os-release is missing."
        exit 1
    fi

    # shellcheck disable=SC1091
    source /etc/os-release

    DISTRO_ID="${ID:-unknown}"
    DISTRO_LIKE="${ID_LIKE:-}"

    case "$DISTRO_ID" in
        ubuntu|debian)
            PKG_FAMILY="apt"
            ;;
        cachyos|arch)
            PKG_FAMILY="pacman"
            ;;
        fedora)
            PKG_FAMILY="dnf"
            ;;
        *)
            if [[ "$DISTRO_LIKE" == *debian* ]]; then
                PKG_FAMILY="apt"
            elif [[ "$DISTRO_LIKE" == *arch* ]]; then
                PKG_FAMILY="pacman"
            elif [[ "$DISTRO_LIKE" == *fedora* ]] || [[ "$DISTRO_LIKE" == *rhel* ]]; then
                PKG_FAMILY="dnf"
            else
                echo "Unsupported distribution: ${DISTRO_ID} (${DISTRO_LIKE})"
                echo "Supported targets: Ubuntu, Debian, CachyOS, Arch Linux, Fedora."
                exit 1
            fi
            ;;
    esac
}

install_packages() {
    require_sudo

    case "$PKG_FAMILY" in
        apt)
            log "Installing dependencies with apt for ${DISTRO_ID}..."
            sudo apt update
            sudo apt install -y \
                openjdk-21-jdk \
                git \
                unzip \
                wget \
                curl \
                libc6 \
                libstdc++6 \
                zlib1g \
                libncurses6 \
                libbz2-1.0
            ;;
        pacman)
            log "Installing dependencies with pacman for ${DISTRO_ID}..."
            sudo pacman -Syu --needed --noconfirm \
                jdk21-openjdk \
                git \
                unzip \
                wget \
                curl \
                ncurses \
                bzip2
            if command -v archlinux-java >/dev/null 2>&1; then
                local current_java
                current_java="$(archlinux-java get || true)"
                if [[ "$current_java" != "java-21-openjdk" ]]; then
                    log "Setting Java 21 as the active JDK..."
                    sudo archlinux-java set java-21-openjdk
                fi
            fi
            ;;
        dnf)
            log "Installing dependencies with dnf for ${DISTRO_ID}..."
            sudo dnf install -y \
                java-21-openjdk-devel \
                git \
                unzip \
                wget \
                curl \
                glibc \
                libstdc++ \
                zlib \
                ncurses-libs \
                bzip2
            ;;
    esac
}

detect_java_home() {
    if command -v javac >/dev/null 2>&1; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
    elif command -v java >/dev/null 2>&1; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    else
        echo "Java was not installed correctly."
        exit 1
    fi

    export JAVA_HOME
    log "Using JAVA_HOME=${JAVA_HOME}"
}

install_cmdline_tools() {
    mkdir -p "$ANDROID_HOME/cmdline-tools"

    if [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
        log "Android command-line tools already installed."
        return
    fi

    log "Downloading Android command-line tools..."
    local zip_path
    local temp_dir

    zip_path="$(mktemp /tmp/cmdline-tools-XXXXXX.zip)"
    temp_dir="$(mktemp -d /tmp/cmdline-tools-XXXXXX)"

    wget "$CMDLINE_TOOLS_URL" -O "$zip_path"
    unzip -q "$zip_path" -d "$temp_dir"

    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
    mv "$temp_dir/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"

    rm -rf "$temp_dir"
    rm -f "$zip_path"
}

install_android_sdk() {
    export ANDROID_HOME
    export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

    log "Accepting Android SDK licenses..."
    yes | env JAVA_HOME="$JAVA_HOME" "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
        --sdk_root="$ANDROID_HOME" --licenses >/dev/null

    log "Installing Android SDK packages..."
    env JAVA_HOME="$JAVA_HOME" "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
        --sdk_root="$ANDROID_HOME" \
        "platform-tools" \
        "$ANDROID_PLATFORM" \
        "$ANDROID_BUILD_TOOLS"
}

update_shell_config() {
    local marker_start="# >>> CinefinTV Android SDK >>>"
    local marker_end="# <<< CinefinTV Android SDK <<<"
    local shell_configs=("$HOME/.bashrc" "$HOME/.zshrc")

    for config in "${shell_configs[@]}"; do
        if [[ ! -f "$config" ]]; then
            continue
        fi

        python3 - "$config" "$marker_start" "$marker_end" "$ANDROID_HOME" "$JAVA_HOME" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
start = sys.argv[2]
end = sys.argv[3]
android_home = sys.argv[4]
java_home = sys.argv[5]

block = "\n".join([
    start,
    f'export ANDROID_HOME="{android_home}"',
    f'export JAVA_HOME="{java_home}"',
    'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"',
    end,
    "",
])

text = path.read_text() if path.exists() else ""
if start in text and end in text:
    before, rest = text.split(start, 1)
    _, after = rest.split(end, 1)
    new_text = before.rstrip() + "\n\n" + block + after.lstrip("\n")
else:
    new_text = text.rstrip() + "\n\n" + block

path.write_text(new_text)
PY

        log "Updated $(basename "$config") with ANDROID_HOME and JAVA_HOME."
    done
}

print_summary() {
    echo "-------------------------------------------------------"
    echo "Setup complete for ${DISTRO_ID}."
    echo "Java: $(java -version 2>&1 | head -n 1)"
    echo "ANDROID_HOME: ${ANDROID_HOME}"
    echo "JAVA_HOME: ${JAVA_HOME}"
    echo
    echo "Restart your shell or run: source ~/.bashrc"
    echo "Build the app with: ./gradlew :app:assembleDebug"
    echo "Run unit tests with: ./gradlew :app:testDebugUnitTest"
    echo "-------------------------------------------------------"
}

main() {
    log "Starting CinefinTV environment setup..."
    detect_distro
    install_packages
    detect_java_home
    install_cmdline_tools
    install_android_sdk
    update_shell_config
    print_summary
}

main "$@"
