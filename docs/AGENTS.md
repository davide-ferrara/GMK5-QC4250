# Golf Mk5 — QC4250 Android Head Unit

Investigation of an aftermarket Android head unit installed in a Volkswagen
Golf Mk5. The project records verified package and activity findings that help
identify the OEM launcher, factory settings, voice overlay, CAN bus, and
rear-camera components before making further changes. Inspection was read-only
except for the explicitly documented, reversible per-user disable tests below.

**Suggested GitHub repository name:** `golf-mk5-qc4250`

## Device

- Head-unit model: QC4250
- SoC: Qualcomm SM4250 (`bengal` platform)
- Android: 11 (SDK 30)
- Display: 1024×600
- Android Auto app: ZLink / X-Car (`com.zjinnova.zlink`)

## Verified component map

| Role | Package / activity | APK location on device | Finding |
|---|---|---|---|
| OEM home launcher | `com.android.launcher` / `com.xyauto.common.activity.MainActivity` | `/odmdir/system/app/Launcher/Launcher.apk` | Registered HOME launcher. |
| Alternate Launcher3 | `com.android.launcher3` / `com.android.launcher3.uioverrides.QuickstepLauncher` | `/odmdir/system_ext/priv-app/Launcher3QuickStep/Launcher3QuickStep.apk` | Also registered as a HOME handler. |
| Factory settings | `com.xyauto.Settings/.MainActivity` | `/odmdir/system/app/XyautoSettings/XyautoSettings.apk` | Opens the vendor parameter UI. |
| Engineering / parameter UI | `com.xygala.pvcanset/.Main` | `/odmdir/system/app/PvCanset/PvCanset.apk` | Implements “Parameter Settings - V1.0.2” and rows 701–714. Confirmed in foreground via ADB. |
| Android Settings | `com.android.settings` | `/odmdir/system_ext/priv-app/Settings/Settings.apk` | System settings and developer options. |
| Voice robot overlay | `com.txznet.txz` | `/vendor/app/WakeUp/WakeUp.apk` | Disabling this package for user 0 made the robot overlay disappear. It can be re-enabled. |
| Voice/vehicle adapter | `com.txznet.smartadapter` | `/vendor/app/TXZO/TXZO.apk` | Receives voice, ACC, backcar, media-source, and weather-view events; treat as vehicle-integrated. |
| Voice assistant companion | `com.txznet.aipal` | `/vendor/app/TXZAIPal/TXZAIPal.apk` | TXZ companion app. |
| TXZ weather | `com.txznet.weather` | `/vendor/app/TXZWeather/TXZWeather.apk` | TXZ weather component. |
| OEM music | `com.acloud.stub.localmusic` / `.QtActivity` | `/odmdir/system/app/GalaMusic/GalaMusic.apk` | Disabling it for user 0 stopped the MEDIA button from launching the unwanted bundled music. |
| Rear camera | `com.xygala.backcar` | `/odmdir/system/app/GalaBackcar/GalaBackcar.apk` | `BackCarService` process/window observed while the reverse-camera view was active; the prior Android activity remained resumed. |
| CAN bus | `com.kyhero.car.myhost` | `/odmdir/system/app/CanBus/CanBus.apk` | Vehicle integration; do not disable casually. |
| CAN bus (second component) | `com.kyhero.car.myhost2` | `/odmdir/system/app/CanBus2/CanBus2.apk` | Vehicle integration; do not disable casually. |

The voice overlay test disabled only `com.txznet.txz` for user 0, and the robot
disappeared. The voice adapter, CAN bus, rear-camera package, and launcher were
left enabled. Android package enable/disable state is per-user and reversible;
re-enable with:

```sh
adb shell pm enable --user 0 com.txznet.txz
```

The OEM GalaMusic app (`com.acloud.stub.localmusic`) was also disabled for user
0 after confirming that it handled the unwanted MEDIA-button playback. The
owner verified that the change works. Two bundled tracks were found at
`/system/media/insidefiles/MeiXiaoqin-No.mp3` and
`/system/media/insidefiles/a-Beleve.mp3`; they were not deleted. Restore the app
with:

```sh
adb shell pm enable --user 0 com.acloud.stub.localmusic
```

Two further apps reported disabled by the owner, whose exact package IDs are
still to be confirmed on the device:

| Name | Package | Status |
|---|---|---|
| PODOFO Voice | *to confirm* (`pm list packages | grep -i podofo`) | Disabled by owner. |
| PODOFO Plus | *to confirm* | Disabled by owner. |

The KEEP/DISABLE policy, blacklist of never-disable packages, candidate list,
and reversible procedure are documented in [DISABLE.md](./DISABLE.md).

## Findings and constraints

- Wireless ADB pairing and connection were verified on the local network.
- The engineering screen is implemented by `com.xygala.pvcanset/.Main`, not by
  `com.xyauto.sysconfig`.
- The rear-camera image can be presented by a service/window while Android
  continues to report another activity as resumed. Activity-only checks can
  therefore miss the camera overlay.
- `com.txznet.smartadapter` listens for ACC, voice-control, and reverse-camera
  events. Its removal could affect vehicle behavior even if the voice assistant
  itself is unwanted.
- ZLink / X-Car (`com.zjinnova.zlink`) is the intended Android Auto component.
- No APKs or extracted firmware are included in this repository. The OEM APKs
  are proprietary; this repository contains only package identifiers,
  on-device paths, and observations.

## Scope

This is a device-specific field notebook, not a universal compatibility guide.
Findings were collected from one unit. Treat changes to CAN, MCU, audio, power,
reverse-camera, or vehicle-integration packages as potentially disruptive and
verify dependencies on the target unit first.
