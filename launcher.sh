#!/usr/bin/env bash

set -Eeuo pipefail

GOLF_PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
GOLF_SDK_DIR="$GOLF_PROJECT_DIR/.android-sdk"
GOLF_ANDROID_USER_DIR="$GOLF_PROJECT_DIR/.android-user"
GOLF_AVD_DIR="$GOLF_PROJECT_DIR/.android-avd"
GOLF_GRADLE_CACHE_DIR="$GOLF_PROJECT_DIR/.gradle-cache"
GOLF_RUNTIME_DIR="$GOLF_PROJECT_DIR/.runtime"
GOLF_AVD_NAME="golf_mk5_api30"
GOLF_EMULATOR_SERIAL="emulator-5554"
GOLF_DEV_APK="$GOLF_PROJECT_DIR/app/build/outputs/apk/dev/debug/app-dev-debug.apk"
GOLF_STABLE_APK="$GOLF_PROJECT_DIR/app/build/outputs/apk/stable/debug/app-stable-debug.apk"
GOLF_DEV_PACKAGE="com.golfv.launcher.dev"
GOLF_STABLE_PACKAGE="com.golfv.launcher"
GOLF_DEV_ACTIVITY="$GOLF_DEV_PACKAGE/com.golfv.launcher.MainActivity"
GOLF_STABLE_ACTIVITY="$GOLF_STABLE_PACKAGE/.MainActivity"
GOLF_CMDLINE_TOOLS_VERSION="15859902"
GOLF_CMDLINE_TOOLS_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"

export ANDROID_HOME="$GOLF_SDK_DIR"
export ANDROID_SDK_ROOT="$GOLF_SDK_DIR"
export ANDROID_USER_HOME="$GOLF_ANDROID_USER_DIR"
export ANDROID_AVD_HOME="$GOLF_AVD_DIR"
export GRADLE_USER_HOME="$GOLF_GRADLE_CACHE_DIR"

GOLF_SDKMANAGER="$GOLF_SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
GOLF_AVDMANAGER="$GOLF_SDK_DIR/cmdline-tools/latest/bin/avdmanager"
GOLF_EMULATOR="$GOLF_SDK_DIR/emulator/emulator"
GOLF_ADB="$GOLF_SDK_DIR/platform-tools/adb"

usage() {
    cat <<'EOF'
Golf Mk5 launcher development helper

Usage:
  ./launcher.sh setup
  ./launcher.sh build
  ./launcher.sh start [--headless]
  ./launcher.sh run
  ./launcher.sh test
  ./launcher.sh install [ADB_SERIAL]
  ./launcher.sh launch [ADB_SERIAL]
  ./launcher.sh build-stable
  ./launcher.sh install-stable [ADB_SERIAL]
  ./launcher.sh launch-stable [ADB_SERIAL]
  ./launcher.sh set-home-stable [ADB_SERIAL]
  ./launcher.sh dev
  ./launcher.sh stop
  ./launcher.sh doctor

Commands:
  setup    Download the local Android SDK, accept its licenses interactively,
           install Android 11 emulator packages, and create the 1024x600 AVD.
  build    Compile the dev APK (com.golfv.launcher.dev).
  start    Open the Android 11 emulator; use --headless for CI-style use.
  run      Build, install, and launch on the local emulator.
  test     Run Compose UI tests on the local emulator, then restore the app.
  install  Install the dev APK. Defaults to emulator-5554. Passing an ADB
           serial is required to target a different device such as the car.
  launch   Launch the dev APK. Defaults to emulator-5554.
  build-stable     Compile the stable HOME APK without installing it.
  install-stable   Explicitly update/install the stable APK.
  launch-stable    Launch the installed stable APK.
  set-home-stable  Select the stable APK as Android's default HOME handler.
  dev      Start emulator, build, install, and launch in one command.
  stop     Stop only the emulator on port 5554.
  doctor   Check local requirements and print connected ADB devices.

Examples:
  ./launcher.sh dev
  ./launcher.sh test
  ./launcher.sh install 10.60.146.92:5555
  ./launcher.sh set-home-stable 10.60.146.92:5555
EOF
}

die() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

note() {
    printf '[golf-launcher] %s\n' "$*"
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Missing command: $1"
}

java_major_version() {
    java -version 2>&1 | awk -F '[".]' '/version/ { print ($2 == "1" ? $3 : $2); exit }'
}

ensure_java() {
    require_command java
    local golf_java_major
    golf_java_major="$(java_major_version)"
    [[ "$golf_java_major" =~ ^[0-9]+$ ]] || die "Unable to determine the Java version"
    (( golf_java_major >= 17 )) || die "Java 17 or newer is required; found Java $golf_java_major"
}

ensure_sdk() {
    [[ -x "$GOLF_SDKMANAGER" ]] || die "Android SDK is missing. Run: ./launcher.sh setup"
    [[ -x "$GOLF_ADB" ]] || die "Android platform-tools are missing. Run: ./launcher.sh setup"
    [[ -d "$GOLF_SDK_DIR/platforms/android-37.0" ]] ||
        die "Android platform 37.0 is missing. Run: ./launcher.sh setup"
}

write_local_properties() {
    printf 'sdk.dir=%s\n' "$GOLF_SDK_DIR" > "$GOLF_PROJECT_DIR/local.properties"
}

gradle() {
    ensure_java
    ensure_sdk
    write_local_properties
    "$GOLF_PROJECT_DIR/gradlew" \
        --gradle-user-home "$GOLF_GRADLE_CACHE_DIR" \
        "$@"
}

download_command_line_tools() {
    [[ -x "$GOLF_SDKMANAGER" ]] && return

    require_command curl
    require_command unzip
    require_command sha256sum

    local golf_temp_dir golf_archive golf_actual_hash
    golf_temp_dir="$(mktemp -d)"
    golf_archive="$golf_temp_dir/android-commandlinetools.zip"
    trap 'rm -rf -- "$golf_temp_dir"' RETURN

    note "Downloading Android command-line tools..."
    curl -L --fail --show-error \
        "https://dl.google.com/android/repository/commandlinetools-linux-${GOLF_CMDLINE_TOOLS_VERSION}_latest.zip" \
        -o "$golf_archive"

    golf_actual_hash="$(sha256sum "$golf_archive" | awk '{print $1}')"
    [[ "$golf_actual_hash" == "$GOLF_CMDLINE_TOOLS_SHA256" ]] ||
        die "Android command-line tools checksum mismatch"

    mkdir -p "$GOLF_SDK_DIR/cmdline-tools"
    unzip -q "$golf_archive" -d "$golf_temp_dir/unpacked"
    mv "$golf_temp_dir/unpacked/cmdline-tools" "$GOLF_SDK_DIR/cmdline-tools/latest"
    trap - RETURN
    rm -rf -- "$golf_temp_dir"
}

set_avd_config() {
    local golf_config="$GOLF_AVD_DIR/$GOLF_AVD_NAME.avd/config.ini"
    local golf_key="$1"
    local golf_value="$2"

    if grep -q "^${golf_key}=" "$golf_config"; then
        sed -i "s|^${golf_key}=.*|${golf_key}=${golf_value}|" "$golf_config"
    else
        printf '%s=%s\n' "$golf_key" "$golf_value" >> "$golf_config"
    fi
}

configure_avd() {
    local golf_config="$GOLF_AVD_DIR/$GOLF_AVD_NAME.avd/config.ini"

    mkdir -p "$GOLF_AVD_DIR"
    if [[ ! -f "$golf_config" ]]; then
        note "Creating Android 11 AVD..."
        "$GOLF_AVDMANAGER" create avd \
            --name "$GOLF_AVD_NAME" \
            --package "system-images;android-30;default;x86_64" \
            --device "pixel_2" \
            --force
    fi

    [[ -f "$golf_config" ]] || die "AVD creation failed: $golf_config was not created"
    set_avd_config "avd.id" "$GOLF_AVD_NAME"
    set_avd_config "avd.name" "Golf Mk5 API 30"
    set_avd_config "hw.gpu.enabled" "yes"
    set_avd_config "hw.gpu.mode" "auto"
    set_avd_config "hw.initialOrientation" "landscape"
    set_avd_config "hw.lcd.density" "160"
    set_avd_config "hw.lcd.height" "600"
    set_avd_config "hw.lcd.vsync" "60"
    set_avd_config "hw.lcd.width" "1024"
    set_avd_config "hw.ramSize" "2G"
    set_avd_config "showDeviceFrame" "no"
}

setup() {
    ensure_java
    mkdir -p "$GOLF_ANDROID_USER_DIR" "$GOLF_GRADLE_CACHE_DIR" "$GOLF_RUNTIME_DIR"
    download_command_line_tools

    note "Review and accept the Android SDK licenses required for local development."
    "$GOLF_SDKMANAGER" --licenses
    note "Installing Android SDK, emulator, and Android 11 system image..."
    "$GOLF_SDKMANAGER" \
        "platforms;android-37.0" \
        "build-tools;36.0.0" \
        "platform-tools" \
        "emulator" \
        "system-images;android-30;default;x86_64"

    configure_avd
    write_local_properties
    note "Setup complete. Run: ./launcher.sh dev"
}

adb_for() {
    local golf_serial="$1"
    shift
    "$GOLF_ADB" -s "$golf_serial" "$@"
}

emulator_is_online() {
    [[ "$(adb_for "$GOLF_EMULATOR_SERIAL" get-state 2>/dev/null || true)" == "device" ]]
}

wait_for_boot() {
    local golf_serial="$1"
    local golf_attempt

    note "Waiting for Android on $golf_serial..."
    adb_for "$golf_serial" wait-for-device
    for golf_attempt in $(seq 1 180); do
        if [[ "$(adb_for "$golf_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
            note "Android is ready."
            return
        fi
        sleep 1
    done
    die "Android did not finish booting within 180 seconds"
}

start_emulator() {
    ensure_sdk
    [[ -x "$GOLF_EMULATOR" ]] || die "Android emulator is missing. Run: ./launcher.sh setup"
    configure_avd

    if emulator_is_online; then
        note "Emulator is already running."
        return
    fi

    mkdir -p "$GOLF_RUNTIME_DIR"
    local -a golf_window_args=()
    local -a golf_accel_args
    if [[ "${1:-}" == "--headless" ]]; then
        golf_window_args=(-no-window)
    elif [[ -n "${1:-}" ]]; then
        die "Unknown start option: $1"
    fi

    if "$GOLF_EMULATOR" -accel-check 2>&1 | grep -q 'is installed and usable'; then
        golf_accel_args=(-accel on -gpu host)
    else
        note "KVM is unavailable; using slower software emulation."
        golf_accel_args=(-accel off -gpu swiftshader_indirect)
    fi

    note "Starting the 1024x600 Android 11 emulator..."
    nohup "$GOLF_EMULATOR" \
        -avd "$GOLF_AVD_NAME" \
        -port 5554 \
        -no-audio \
        -no-boot-anim \
        -no-snapshot \
        -memory 1536 \
        -cores 2 \
        "${golf_window_args[@]}" \
        "${golf_accel_args[@]}" \
        > "$GOLF_RUNTIME_DIR/emulator.log" 2>&1 &
    printf '%s\n' "$!" > "$GOLF_RUNTIME_DIR/emulator.pid"

    wait_for_boot "$GOLF_EMULATOR_SERIAL"
}

build_app() {
    note "Building dev APK..."
    gradle :app:assembleDevDebug
    [[ -f "$GOLF_DEV_APK" ]] || die "Build completed without producing $GOLF_DEV_APK"
    note "Dev APK ready: $GOLF_DEV_APK"
}

build_stable_app() {
    note "Building stable APK..."
    gradle :app:assembleStableDebug
    [[ -f "$GOLF_STABLE_APK" ]] ||
        die "Build completed without producing $GOLF_STABLE_APK"
    note "Stable APK ready: $GOLF_STABLE_APK"
}

install_app() {
    local golf_serial="${1:-$GOLF_EMULATOR_SERIAL}"
    ensure_sdk
    [[ -f "$GOLF_DEV_APK" ]] || die "Dev APK not found. Run: ./launcher.sh build"
    [[ "$(adb_for "$golf_serial" get-state 2>/dev/null || true)" == "device" ]] ||
        die "ADB device is not available: $golf_serial"

    note "Installing dev APK on $golf_serial..."
    adb_for "$golf_serial" install -r "$GOLF_DEV_APK"
}

launch_app() {
    local golf_serial="${1:-$GOLF_EMULATOR_SERIAL}"
    ensure_sdk
    note "Launching $GOLF_DEV_ACTIVITY on $golf_serial..."
    adb_for "$golf_serial" shell am start -n "$GOLF_DEV_ACTIVITY"
}

install_stable_app() {
    local golf_serial="${1:-$GOLF_EMULATOR_SERIAL}"
    ensure_sdk
    [[ -f "$GOLF_STABLE_APK" ]] ||
        die "Stable APK not found. Run: ./launcher.sh build-stable"
    [[ "$(adb_for "$golf_serial" get-state 2>/dev/null || true)" == "device" ]] ||
        die "ADB device is not available: $golf_serial"

    note "Installing stable APK on $golf_serial..."
    adb_for "$golf_serial" install -r "$GOLF_STABLE_APK"
}

launch_stable_app() {
    local golf_serial="${1:-$GOLF_EMULATOR_SERIAL}"
    ensure_sdk
    note "Launching $GOLF_STABLE_ACTIVITY on $golf_serial..."
    adb_for "$golf_serial" shell am start -n "$GOLF_STABLE_ACTIVITY"
}

set_home_stable() {
    local golf_serial="${1:-$GOLF_EMULATOR_SERIAL}"
    ensure_sdk
    [[ "$(adb_for "$golf_serial" get-state 2>/dev/null || true)" == "device" ]] ||
        die "ADB device is not available: $golf_serial"
    adb_for "$golf_serial" shell pm path "$GOLF_STABLE_PACKAGE" >/dev/null ||
        die "Stable package is not installed on $golf_serial"

    note "Selecting the stable package as HOME on $golf_serial..."
    adb_for "$golf_serial" shell cmd package set-home-activity "$GOLF_STABLE_ACTIVITY"
}

run_app() {
    start_emulator
    build_app
    install_app "$GOLF_EMULATOR_SERIAL"
    launch_app "$GOLF_EMULATOR_SERIAL"
}

test_app() {
    start_emulator
    note "Running Compose UI tests on $GOLF_EMULATOR_SERIAL..."
    ANDROID_SERIAL="$GOLF_EMULATOR_SERIAL" gradle \
        --no-configuration-cache \
        :app:connectedDevDebugAndroidTest

    # Android's connected-test runner removes the tested APK when it finishes.
    build_app
    install_app "$GOLF_EMULATOR_SERIAL"
    launch_app "$GOLF_EMULATOR_SERIAL"
    note "Tests passed and the launcher has been restored."
}

stop_emulator() {
    ensure_sdk
    if emulator_is_online; then
        note "Stopping $GOLF_EMULATOR_SERIAL..."
        adb_for "$GOLF_EMULATOR_SERIAL" emu kill >/dev/null
    else
        note "Emulator is not running."
    fi
}

doctor() {
    printf 'Project:  %s\n' "$GOLF_PROJECT_DIR"
    printf 'Java:     '
    if command -v java >/dev/null 2>&1; then
        java -version 2>&1 | head -n 1
    else
        printf 'missing\n'
    fi
    printf 'SDK:      %s\n' "$([[ -x "$GOLF_SDKMANAGER" ]] && printf ready || printf missing)"
    printf 'Emulator: %s\n' "$([[ -x "$GOLF_EMULATOR" ]] && printf ready || printf missing)"
    printf 'Dev APK:  %s\n' "$([[ -f "$GOLF_DEV_APK" ]] && printf '%s' "$GOLF_DEV_APK" || printf 'not built')"
    printf 'Stable:   %s\n' "$([[ -f "$GOLF_STABLE_APK" ]] && printf '%s' "$GOLF_STABLE_APK" || printf 'not built')"
    if [[ -x "$GOLF_EMULATOR" ]]; then
        "$GOLF_EMULATOR" -accel-check || true
    fi
    if [[ -x "$GOLF_ADB" ]]; then
        "$GOLF_ADB" devices -l
    fi
}

main() {
    cd "$GOLF_PROJECT_DIR"
    mkdir -p "$GOLF_ANDROID_USER_DIR" "$GOLF_GRADLE_CACHE_DIR" "$GOLF_RUNTIME_DIR"

    case "${1:-}" in
        setup)
            setup
            ;;
        build)
            build_app
            ;;
        build-stable)
            build_stable_app
            ;;
        start)
            shift
            start_emulator "${1:-}"
            ;;
        run)
            run_app
            ;;
        test)
            test_app
            ;;
        install)
            install_app "${2:-$GOLF_EMULATOR_SERIAL}"
            ;;
        launch)
            launch_app "${2:-$GOLF_EMULATOR_SERIAL}"
            ;;
        install-stable)
            install_stable_app "${2:-$GOLF_EMULATOR_SERIAL}"
            ;;
        launch-stable)
            launch_stable_app "${2:-$GOLF_EMULATOR_SERIAL}"
            ;;
        set-home-stable)
            set_home_stable "${2:-$GOLF_EMULATOR_SERIAL}"
            ;;
        dev)
            run_app
            ;;
        stop)
            stop_emulator
            ;;
        doctor)
            doctor
            ;;
        help|-h|--help)
            usage
            ;;
        '')
            usage
            exit 1
            ;;
        *)
            usage >&2
            die "Unknown command: $1"
            ;;
    esac
}

main "$@"
