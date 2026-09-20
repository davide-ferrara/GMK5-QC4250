# Golf Mk5 minimal radio prototype

This is the Golf Mk5 radio UI for the QC4250 OEM radio. It replaces GalaRadio
in the custom launcher, but does not disable or remove GalaRadio: the OEM app
continues to supply the protected tuner and audio backend.

The prototype starts the exported OEM service
`com.acloud.stub.extradio/com.radio.service.RadioService` with the two actions
observed in GalaRadio:

- `xy.android.fmradio.rev`: seek the previous station;
- `xy.android.fmradio.frd`: seek the next station.

It listens for `xy.update.freq` and displays the integer extra named `freq`.
The OEM application remains installed as the protected tuner and audio backend.

Version 0.3 starts that OEM service immediately when the Radio screen opens,
so the tuner/audio source is selected even before the first seek or preset
command. It also uses larger, high-contrast controls sized for a 1024×600
in-car display. It includes an instrument-cluster-inspired Golf V interface,
eight local presets (tap to recall, long press to store), a tuning scale, and the RDS
Program Service name. GalaRadio does not broadcast the RDS PS field, so this
device-specific prototype reads the `McuRadio` diagnostic line. The QC4250
build exposes this diagnostic stream to the debug app; no manual permission
grant or root access was required in the verified installation.

The app has no network permission and does not modify GalaRadio.

Build the release variants with:

```sh
./gradlew :radio-app:assembleRelease :launcher-app:assembleRelease
adb install -r radio-app/build/outputs/apk/release/radio-app-release.apk
adb install -r launcher-app/build/outputs/apk/release/launcher-app-release.apk
```

This module deliberately contains no OEM APK, library, or proprietary code.
