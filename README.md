# Golf Mk5 + QC4250

Reverse-engineering notes for the Android head unit in a Volkswagen Golf Mk5.

## Passwords

- Factory Settings: `8888`
- Hidden Engineering / Developer options on this SM4250 unit: `y4250x`

## Current work

We are identifying OEM apps and dependencies so the unit can be simplified for
Android Auto while preserving the launcher, CAN bus, and rear camera. The TXZ
robot overlay disappeared after disabling `com.txznet.txz`; restore it with:

```sh
adb shell pm enable --user 0 com.txznet.txz
```

See [AGENTS.md](./AGENTS.md) for the verified component map and findings.

## Launcher development

The custom launcher plan is documented in [LAUNCHER.md](./LAUNCHER.md). The
project uses a local Android 11 emulator configured like the head unit:
1024×600, landscape, 160 dpi, and 60 Hz.

Install the local Android toolchain and create the emulator once:

```sh
make setup
```

For the normal development loop, start the emulator, compile the APK, install
it, and launch the application with:

```sh
make dev
```

The individual operations are also available:

```sh
make start       # Open the graphical Android emulator
make build       # Compile the debug APK
make run         # Build, install, and launch on the emulator
make test        # Run the Compose UI tests and restore the app afterward
make install     # Install the existing APK on the emulator
make launch      # Launch the installed app on the emulator
make stop        # Stop the emulator
make doctor      # Check Java, SDK, KVM, APK, and connected ADB devices
make help        # Show every available command
```

The generated debug APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Installing on the head unit

List the connected ADB targets and copy the exact serial of the head unit:

```sh
adb devices -l
```

Then install and launch using that explicit serial:

```sh
make install DEVICE=DEVICE_SERIAL
make launch DEVICE=DEVICE_SERIAL
```

Without `DEVICE=...`, the Make targets default to `emulator-5554`. This avoids
accidentally installing a development build on the car when multiple ADB
devices are connected. Installing the APK does not select it as the default
HOME application and does not disable either OEM launcher.

See [DEVELOPMENT.md](./DEVELOPMENT.md) for setup details and local generated
directories.

### Publishing an in-app update

The information page checks the repository's latest GitHub Release, downloads
its first `.apk` asset, verifies its GitHub SHA-256 digest, package name,
increasing `versionCode`, and signing certificate, then opens Android's package
installer. To publish an update:

1. increase both `versionCode` and `versionName` in `app/build.gradle.kts`;
2. build an APK signed with the same key as the APK already on the head unit;
3. create a non-draft, non-prerelease GitHub Release tagged with the same
   version (for example `v0.2.3`) and attach the APK.

If GitHub does not expose a digest for the asset, also attach a text file named
`<apk-name>.sha256` containing its SHA-256 value. Android requires a one-time
“Install unknown apps” authorization and always shows its own final install
confirmation. An APK signed with another key is rejected by the launcher and
cannot update the installed application.
