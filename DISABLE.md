# KEEP / DISABLE Policy — QC4250

Goal: slim down the system by disabling only unnecessary applications, without
breaking the CAN bus, rear camera, audio, power, or boot.

> **Golden rule:** never disable blindly. First identify the package, then
> disable it **one at a time**, verifying head-unit behavior. Every
> `pm disable-user` command is reversible with `pm enable`.

This document is the **verified inventory** of the unit. It was produced from a
read-only ADB dump (`pm list packages -u`, `-s`, `-3`, `-f`, `dumpsys package`).

- Device: `Bengal for arm64`, Android 11 (SDK 30)
- Total packages: **252** (244 system, 8 third-party)
- Currently disabled: **`com.txznet.txz` only**

## How to refresh the inventory

The head unit is not always connected. With ADB active:

```sh
adb devices -l
adb shell pm list packages --user 0 | sort      # installed for the owner
adb shell pm list packages -u                    # include uninstalled
adb shell pm list packages -s                    # system packages
adb shell pm list packages -3                    # third-party packages
adb shell pm list packages -d                    # disabled
adb shell pm list packages -f -s                 # with APK paths
```

## PODOFO status — disabled by owner, IDs to confirm

The two owner-reported apps, **PODOFO Voice** and **PODOFO Plus**, were
**disabled by the owner** — not removed — and the unit has **not** been rooted.
Their exact package IDs are still to be confirmed on the device. Verify with:

```sh
adb shell pm list packages -u | grep -i -E "podo|pve|voice|plus"
adb shell pm list packages -u -d --user 0
```

Candidate OEM suite that may correspond to the "Podofo" apps: `com.pve.*`
(`com.pve.appsetting`, `com.pve.apsbridge`, `com.pve.naviguide`,
`com.pve.xy360view`). Not confirmed.

## Category A — NEVER disable (technical blacklist)

Verified as critical or vehicle-integrated. Do not touch:

| Package | APK path | Reason |
|---|---|---|
| `com.kyhero.car.myhost` | `/odmdir/system/app/CanBus/CanBus.apk` | CAN bus; vehicle integration. |
| `com.kyhero.car.myhost2` | `/odmdir/system/app/CanBus2/CanBus2.apk` | CAN bus (second component). |
| `com.xygala.backcar` | `/odmdir/system/app/GalaBackcar/GalaBackcar.apk` | Rear camera (`BackCarService`). |
| `com.txznet.smartadapter` | `/vendor/app/TXZO/TXZO.apk` | Receives ACC, voice-control, reverse-camera, media-source, weather-view events. |
| `com.xygala.pvcanset` | `/odmdir/system/app/PvCanset/PvCanset.apk` | Engineering/parameter UI (rows 701–714). |
| `com.xyauto.Settings` | `/odmdir/system/app/XyautoSettings/XyautoSettings.apk` | Factory settings. |
| `com.android.settings` | `/odmdir/system_ext/priv-app/Settings/Settings.apk` | Android settings and developer options. |
| `com.zjinnova.zlink` | `/odmdir/system/app/zlink5/zlink5.apk` | The intended Android Auto component. |
| `com.android.launcher` | `/odmdir/system/app/Launcher/Launcher.apk` | OEM HOME launcher. |
| `com.android.launcher3` | `/odmdir/system_ext/priv-app/Launcher3QuickStep/Launcher3QuickStep.apk` | Second HOME handler. |
| `com.example.tmedia` | `/odmdir/system/app/CopyCanbus/CopyCanbus.apk` | **Despite the name, this is CopyCanbus, not a demo.** |
| `com.xygala.bluetooth` | `/odmdir/system/app/CHQbt/CHQbt.apk` | OEM Bluetooth stack. |
| `com.xyauto.dspSetting` | `/odmdir/system/app/DspSetting/DspSetting.apk` | DSP/audio tuning. |
| `com.xyauto.services` | `/odmdir/system/app/XyautoServices/XyautoServices.apk` | Vendor service host. |
| `com.xyauto.sysconfig` | — | Vendor configuration. |
| `com.xygala.avin` | `/odmdir/system/app/Avin/Avin.apk` | AV-in / video input path. |
| `com.xy.brakecheck` | `/odmdir/system/app/BrakeCheck/BrakeCheck.apk` | Vehicle parameter checks. |
| `android.car.app.drivng` | `/odmdir/system/app/AutoDriving/AutoDriving.apk` | Driving/ADAS integration. |
| `com.pve.apsbridge` | `/odmdir/system/app/Pveapsbridge/Pveapsbridge.apk` | APS/parking-sensor bridge. |
| `com.xy.toolcontrol` | — | Vehicle tool control. |
| `com.xy.zlinkactivationcode` | — | ZLink activation/DRM. |
| `com.android.systemui` | `/odmdir/system_ext/priv-app/SystemUI/SystemUI.apk` | System UI. |

Also keep all core AOSP infrastructure (do not disable): `android`,
`com.android.providers.*`, `com.android.networkstack*`, `com.android.phone.*`,
`com.android.server.telecom*`, `com.android.permissioncontroller`,
`com.android.keychain`, `com.android.shell`, `com.android.externalstorage`,
`com.android.documentsui`, `com.android.packageinstaller`,
`com.google.android.packageinstaller`, `com.android.inputmethod.latin`,
`com.android.inputdevices`, `com.android.location.fused`, `com.android.se`.

## Category B — Disabled and verified reversible

| Name | Package | Status | Notes |
|---|---|---|---|
| Voice robot overlay | `com.txznet.txz` | Disabled for user 0 | The robot disappeared. Re-enable with `adb shell pm enable --user 0 com.txznet.txz`. |
| PODOFO Voice | *to confirm* | Disabled by owner (not removed) | Exact package ID still to confirm on the device. |
| PODOFO Plus | *to confirm* | Disabled by owner (not removed) | Exact package ID still to confirm on the device. |

## Category C — Safe candidates (test one at a time)

Cosmetic, test, logger, or demo packages that appear non-critical. Verify the
ID, disable one, then test ignition, reverse camera, volume, CAN, launcher, and
Android Auto:

| Package | APK path | What it is |
|---|---|---|
| `com.xyauto.android.autotest` | — | Vendor autotest. |
| `com.xygala.sdmlogger` | `/odmdir/system/app/SDMLogger/SDMLogger.apk` | Diagnostic logger. |
| `com.example.android.locationattribution` | — | Google demo/sample. |
| `com.android.egg` | — | Android easter egg. |
| `com.android.protips` | — | "Protips" home-screen tips. |
| `com.xyauto.screensaver` | `/odmdir/system/app/ScreenSaver/ScreenSaver.apk` | Screensaver. |
| `com.xyauto.wallpaper` | `/odmdir/system/app/WallpaperChooser/WallpaperChooser.apk` | Wallpaper chooser. |
| `com.xyauto.theme` | `/odmdir/system/app/PveThemeSetting/PveThemeSetting.apk` | Theme settings. |
| `com.xyauto.colorpalette` | `/odmdir/system/app/Color/Color.apk` | Color palette. |
| `com.xyauto.videooc` | `/odmdir/system/app/VideoOC/VideoOC.apk` | Video output config. |
| `com.bumblebee.remindeasy` | `/odmdir/system/app/EasyRemind/EasyRemind.apk` | Reminder app. |
| `com.acloud.stub.logoselector` | `/odmdir/system/app/LogoSelector/LogoSelector.apk` | Boot-logo selector. |

## Category D — Only after deeper verification

Suspicious but potentially integrated. Do not disable without observing logs and
real usage first:

| Package | APK path | Why it is risky |
|---|---|---|
| `com.acloud.stub.localmusic` | GalaMusic | OEM music; may be the source selected by steering/media keys. |
| `com.acloud.stub.extradio` | GalaRadio | OEM radio; may be bound to the Radio hardware/button. |
| `com.acloud.stub.video` | GalaVideo | OEM video player. |
| `com.acloud.xy.search` | GalaProvider | Vendor search/content provider. |
| `com.pve.appsetting` | XYAppSettingView | PVE app settings. |
| `com.pve.naviguide` | PveNaviGuide | PVE navigation guide. |
| `com.xygala.socketclient` | — | Unknown socket client. |
| `com.xy.lockscreen` | LockScreen | Lockscreen; disabling may affect screen-off behavior. |
| `com.txznet.aipal` | `/vendor/app/TXZAIPal/TXZAIPal.apk` | TXZ companion; check `smartadapter` dependency. |
| `com.txznet.weather` | `/vendor/app/TXZWeather/TXZWeather.apk` | TXZ weather; may feed the launcher widget. |
| `com.baony.avm360` | — | 360° camera; hidden for user 0. |
| `com.pve.xy360view` | — | 360° view; hidden for user 0. |
| `com.xygala.backcar2` | — | Second rear-camera component; hidden for user 0. |
| `com.caf.fmradio` | — | FM radio. |
| `com.ex.dabplayer.pad` | `/vendor/app/DabPlayer/DabPlayer.apk` | DAB radio. |
| `com.here.app.maps` | `/vendor/app/herego/herego.apk` | HERE navigation. |
| `com.android.chrome` | `/odmdir/product/app/Chrome/Chrome.apk` | Bundled browser. |
| `com.google.android.gm` | Gmail2 | Bundled Gmail. |
| `com.google.android.apps.maps` | Maps | Google Maps; may be used for navigation. |
| `com.google.android.youtube` | YouTube | Bundled YouTube. |
| `com.google.android.tts` | SpeechServicesByGoogle | TTS; disabling can break voice/navigation prompts. |
| `com.google.android.feedback` | GoogleFeedback | Feedback agent. |
| `com.google.android.apps.restore` | GoogleRestore | Restore agent. |
| `com.google.android.onetimeinitializer` | GoogleOneTimeInitializer | GMS initializer. |
| `com.google.android.gms.location.history` | GoogleLocationHistory | Location history. |
| `com.google.android.syncadapters.*` | — | Google account sync. |

Keep all `com.qualcomm.*` / `vendor.qti.*` modem, location, and secure services
unless logs prove otherwise.

## Third-party (owner-installed, uninstall rather than disable)

| Package | Note |
|---|---|
| `com.golfv.launcher` | This project's launcher. |
| `com.termux` | Terminal emulator. |
| `org.fdroid.fdroid` | F-Droid. |
| `com.uptodown` | Uptodown store. |
| `com.vadhod.apkextractor` | APK extractor. |
| `de.szalkowski.activitylauncher` | Activity Launcher. |
| `com.android.vending` | Google Play Store. |
| `com.google.android.gms` | Google Play Services (Android Auto / ZLink may need it). |

## Operating procedure

1. Connect ADB and refresh the inventory (commands above).
2. For each candidate, inspect it: `adb shell dumpsys package <pkg>`.
3. Disable **one only**:
   ```sh
   adb shell pm disable-user --user 0 <pkg>
   ```
4. Verify immediately: ignition, reverse camera, volume, CAN, launcher, Android
   Auto.
5. If anything breaks, roll back immediately:
   ```sh
   adb shell pm enable --user 0 <pkg>
   ```
6. Record the outcome in `AGENTS.md` (package, status, reason) so the knowledge
   stays in the repo.

## Full rollback

To re-enable everything disabled for user 0:

```sh
adb shell pm list packages -d --user 0
adb shell pm enable --user 0 <pkg>
```
