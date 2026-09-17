# Local development

The repository includes one helper script for the complete local workflow.
Run commands from the repository root.

## First-time setup

```sh
make setup
```

This keeps the Android SDK, Android 11 emulator, AVD, and Gradle cache inside
ignored project directories. The Android SDK licenses are shown and accepted
interactively. Java 17 or newer, `curl`, and `unzip` must already be installed.

## Daily workflow

Start the graphical 1024×600 emulator, compile, install, and open the launcher:

```sh
make dev
```

Other useful commands:

```sh
make build
make start
make run
make test
make stop
make doctor
```

`test` runs the Compose instrumentation tests on `emulator-5554`. The Android
test runner removes the tested APK afterward, so the script automatically
rebuilds, reinstalls, and reopens the normal debug application.

## Installing on the head unit

Always pass the exact ADB serial when the target is not the local emulator:

```sh
adb devices -l
make install DEVICE=DEVICE_SERIAL
make launch DEVICE=DEVICE_SERIAL
```

The script intentionally defaults to `emulator-5554`; it never guesses which
physical device should receive the APK. Installing the APK does not change the
default HOME application and does not disable the OEM launcher.

Run `make help` to see the complete command list. `launcher.sh` remains the
implementation behind these short Make targets and can still be used directly
for automation.

## Generated and local-only files

The following directories are ignored by version control:

- `.android-sdk/`
- `.android-user/`
- `.android-avd/`
- `.gradle-cache/`
- `.runtime/`
- `local.properties`

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```
