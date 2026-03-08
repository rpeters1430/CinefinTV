#!/bin/bash

# CinefinTV CachyOS (Arch-based) Setup Script
# This script sets up the environment to build the CinefinTV Android app.
# It installs OpenJDK 21, the Android SDK, and configures environment variables.

set -e

echo "Starting CinefinTV environment setup for CachyOS..."

# 1. Update and install basic dependencies
echo "Updating package lists and installing base dependencies..."
sudo pacman -Syu --needed --noconfirm \
    jdk21-openjdk \
    git \
    unzip \
    wget \
    curl \
    ncurses \
    bzip2

# 2. Ensure Java 21 is the active version
if command -v archlinux-java >/dev/null; then
    echo "Checking Java version..."
    CURRENT_JAVA=$(archlinux-java get)
    if [[ "$CURRENT_JAVA" != "java-21-openjdk" ]]; then
        echo "Setting Java 21 as default..."
        sudo archlinux-java set java-21-openjdk
    fi
fi

# Detect correct JAVA_HOME
if [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
else
    # Fallback search
    export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:bin/java::")
fi
echo "Using JAVA_HOME: $JAVA_HOME"

# 3. Define SDK paths
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 4. Create SDK directory
mkdir -p $ANDROID_HOME/cmdline-tools

# 5. Download and install Android Command Line Tools
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "Downloading Android Command Line Tools..."
    # Using the latest stable version of cmdline-tools (11076708)
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
    unzip -q cmdline-tools.zip -d $ANDROID_HOME/temp_tools
    
    # Correcting the internal 'cmdline-tools' folder structure for sdkmanager
    mkdir -p $ANDROID_HOME/cmdline-tools/latest
    mv $ANDROID_HOME/temp_tools/cmdline-tools/* $ANDROID_HOME/cmdline-tools/latest/
    rm -rf $ANDROID_HOME/temp_tools
    rm cmdline-tools.zip
    echo "Command Line Tools installed."
else
    echo "Android Command Line Tools already installed."
fi

# 6. Accept SDK licenses
echo "Accepting Android SDK licenses..."
# Explicitly pass JAVA_HOME to sdkmanager to ensure it uses JDK 21
JAVA_HOME=$JAVA_HOME yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses

# 7. Install required SDK components
echo "Installing Android SDK platforms and build tools..."
JAVA_HOME=$JAVA_HOME $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;36.0.0"

# 8. Add environment variables to shell config
# Arch/CachyOS users often use Zsh or Bash.
SHELL_CONFIGS=("$HOME/.bashrc" "$HOME/.zshrc")

for CONFIG in "${SHELL_CONFIGS[@]}"; do
    if [ -f "$CONFIG" ]; then
        # Remove old entries if they exist to prevent duplicates or outdated paths
        sed -i '/# Android SDK (CinefinTV)/,+4d' "$CONFIG"
        
        echo "Adding/Updating environment variables in $CONFIG..."
        {
            echo ""
            echo "# Android SDK (CinefinTV)"
            echo "export ANDROID_HOME=$HOME/android-sdk"
            echo "export JAVA_HOME=$JAVA_HOME"
            echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools"
        } >> "$CONFIG"
    fi
done

echo "-------------------------------------------------------"
echo "Setup complete!"
echo "Current Java version: $(java -version 2>&1 | head -n 1)"
echo "JAVA_HOME set to: $JAVA_HOME"
echo ""
echo "Please restart your terminal or run: source ~/.bashrc (or ~/.zshrc)"
echo "To build the app, run: ./gradlew assembleDebug"
echo "-------------------------------------------------------"
