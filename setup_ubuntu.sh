#!/bin/bash

# CinefinTV Ubuntu Setup Script
# This script sets up the environment to build the CinefinTV Android app.
# It installs OpenJDK 21, the Android SDK, and configures environment variables.
# 
# Usage:
#   chmod +x setup_ubuntu.sh
#   ./setup_ubuntu.sh

set -e

echo "Starting CinefinTV environment setup..."

# 1. Update and install basic dependencies
echo "Updating package lists and installing base dependencies..."
sudo apt update
sudo apt install -y openjdk-21-jdk git unzip wget curl libc6 libstdc++6 zlib1g libncurses6 libbz2-1.0

# 2. Define SDK paths
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 3. Create SDK directory
mkdir -p $ANDROID_HOME/cmdline-tools

# 4. Download and install Android Command Line Tools
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "Downloading Android Command Line Tools..."
    # Using the latest stable version of cmdline-tools
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
    unzip -q cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
    rm cmdline-tools.zip
    echo "Command Line Tools installed."
else
    echo "Android Command Line Tools already installed."
fi

# 5. Accept SDK licenses
echo "Accepting Android SDK licenses..."
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses

# 6. Install required SDK components
echo "Installing Android SDK platforms and build tools..."
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;36.0.0"

# 7. Add environment variables to .bashrc if they aren't there
if ! grep -q "ANDROID_HOME" "$HOME/.bashrc"; then
    echo "Adding environment variables to ~/.bashrc..."
    echo "" >> $HOME/.bashrc
    echo "# Android SDK" >> $HOME/.bashrc
    echo "export ANDROID_HOME=$HOME/android-sdk" >> $HOME/.bashrc
    echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools" >> $HOME/.bashrc
    # Detect architecture for JAVA_HOME
    ARCH=$(uname -m)
    if [ "$ARCH" = "x86_64" ]; then
        echo "export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64" >> $HOME/.bashrc
    elif [ "$ARCH" = "aarch64" ]; then
        echo "export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64" >> $HOME/.bashrc
    fi
fi

# 8. Optional: Emulator support
echo "If you wish to use an emulator, you may also need to install KVM:"
echo "sudo apt install -y qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils"
echo "And add your user to the kvm group: sudo adduser $USER kvm"

echo "-------------------------------------------------------"
echo "Setup complete!"
echo "Please restart your terminal or run: source ~/.bashrc"
echo "To build the app, run: ./gradlew assembleDebug"
echo "-------------------------------------------------------"
