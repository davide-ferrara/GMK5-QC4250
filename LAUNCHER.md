# Golf Mk5 Launcher Plan

## 1. Goal

Build a small, device-specific Android launcher for the QC4250 head unit in a
Volkswagen Golf Mk5. The launcher is a polished bridge to Android Auto and a
few essential OEM applications, not a replacement infotainment system.

The result must:

- run as a normal APK installed with ADB;
- register as an Android HOME application and become the default launcher;
- leave the existing OEM launchers installed and enabled as a recovery path;
- look native to the car while remaining visually compatible with the current
  Pixel/Material style used by Android Auto;
- remain usable if the background animation or a target application fails;
- avoid changes to CAN, MCU, rear-camera, audio, and power-management packages.

The initial version is deliberately hardcoded. User customization and a
settings editor are out of scope.

## 2. Target device

| Property | Target |
|---|---|
| Head unit | QC4250 |
| SoC | Qualcomm SM4250 (`bengal`) |
| Android | Android 11 / API 30 |
| Display | 1024×600, landscape, 60 Hz |
| Input | Touch |
| Android Auto | ZLink / X-Car (`com.zjinnova.zlink`) |

The application is device-specific. Supporting phones, portrait layouts, or
other Android versions is not a version-one goal.

## 3. Product experience

### Startup sequence

1. Android starts the launcher as the selected HOME application.
2. A lightweight launch theme immediately displays a centered Golf mark. It
   must not show a blank or white frame.
3. After approximately one second, the launch mark fades into the main screen.
4. A silver Golf Mk5 completes one smooth 360-degree rotation at 60 fps.
5. The animation ends on the designed hero pose and remains visually frozen on
   that pose.
6. Returning from Android Auto or another application shows the final pose
   immediately; the rotation does not restart.

The rotation should play once per Android device boot, not once per Activity
resume. Store the last observed Android boot count in `SharedPreferences`. If
the boot count cannot be read, fall back to once per launcher process.

### Home screen

The home screen has three visual layers:

- a full-screen car render or its static final frame;
- a subtle contrast treatment behind foreground content, such as a dark
  gradient, without obscuring the car;
- a large clock/date area and a bottom dock with five graphic buttons.

The clock always uses a 24-hour format. The date follows the system locale,
date, and time zone. Seconds are not displayed. Time and date update without
polling every frame.

The dock contains graphic icons only, in this fixed order:

1. Android Auto / ZLink;
2. Radio;
3. OEM settings;
4. Android settings;
5. App drawer.

There are no visible text labels in the dock. Every button must still have an
accessibility description and pressed/focused feedback. Touch targets should
be at least 72 dp and spaced far enough apart for reliable use while parked or
at a brief glance. Settings buttons should be visually less prominent than
Android Auto and Radio.

### App drawer

The app drawer is a full-screen, touch-friendly grid:

- large application icons with labels;
- alphabetical ordering;
- no search field in version one;
- close with Back, Home, or a clear graphical close control;
- query launchable activities rather than displaying services or packages
  without a launcher entry.

Apply an explicit package blacklist to hide the custom launcher, the two other
HOME launchers, and known technical/vehicle components. At minimum, review and
exclude the following before the first vehicle build:

- `com.android.launcher`
- `com.android.launcher3`
- `com.kyhero.car.myhost`
- `com.kyhero.car.myhost2`
- `com.xygala.backcar`
- `com.txznet.txz`
- `com.txznet.smartadapter`
- `com.txznet.aipal`
- `com.txznet.weather`

This is a UI filter only. It must never disable, uninstall, stop, or modify
those packages.

### Immersive mode

Use sticky immersive mode to hide both system bars. A standard edge swipe must
temporarily reveal system navigation so the launcher never traps the user.
Restore immersive mode when focus returns, without continuously fighting a
user who has intentionally revealed the bars.

## 4. Visual direction

Use a restrained Material 3 visual language inspired by recent Pixel Android
releases, adapted to feel factory-installed in a Volkswagen:

- charcoal and near-black surfaces;
- silver/white primary content;
- a cool Volkswagen-like blue accent, without copying protected brand assets;
- large geometry, generous spacing, and minimal chrome;
- subtle fades and press states only;
- no widgets, feeds, weather, notifications, or decorative continuous motion.

The rotating car is the only major startup animation. Once it stops, the home
screen should be calm and suitable for driving. The final layout must be
checked for contrast against every frame of the animation.

## 5. Media specification

Create the source animation in Blender with the car finishing in a front
three-quarter hero pose. Export delivery assets as:

- `golf_rotation.mp4`: H.264, 1024×600, 60 fps, no audio, YUV 4:2:0, web/fast
  start enabled, approximately 6–8 seconds;
- `golf_final.webp`: lossless or high-quality WebP at 1024×600, made from the
  exact final video frame;
- a static launch-logo drawable suitable for the initial theme window.

1024×600 is the maximum useful delivery resolution because it is the panel's
native resolution. A larger file would be downscaled on every frame and would
increase APK size, memory bandwidth, and decoder load without adding visible
detail.

On playback completion, replace or cover the video surface with
`golf_final.webp` so the hero pose survives surface recreation. If media
initialization, decoding, or playback fails, show the same WebP immediately and
keep all controls operational. Playback must never block the main thread.

The 60 fps file must be tested on the physical QC4250 before release. If the
device drops frames or overheats, optimize bitrate/encoding first. A 30 fps
export may be kept only as an evidence-based compatibility fallback.

## 6. Technical stack

### Application

- Kotlin
- Jetpack Compose
- Material 3 components, with a small custom theme
- one Activity and simple in-memory screen state
- Android 11 / API 30 as the minimum supported version
- Gradle Kotlin DSL

Use a collision-free application ID, provisionally `com.golfv.launcher`.
Register the main Activity as exported with `MAIN`, `HOME`, and `DEFAULT`
intent categories. A `BOOT_COMPLETED` receiver is unnecessary: Android starts
the selected HOME application itself.

### Keep the APK small

Do not introduce Hilt, a database, Navigation Compose, a networking stack, an
image-loading library, analytics, crash reporting, or a separate domain/data
architecture. The launcher has only two screens and static configuration.

Use the platform media APIs behind a small Compose-compatible view for the
single local H.264 asset. Prefer this over adding a full streaming player
dependency unless device testing demonstrates a platform playback problem.
Keep icons as local vector drawables and package the video/image locally so the
launcher has no network dependency.

Use `SharedPreferences` only for the boot-animation marker. Use a simple sealed
screen state or enum for Home versus App Drawer.

### Package visibility and launching

Declare a manifest `<queries>` entry for launcher activities. Build the drawer
from `ACTION_MAIN` plus `CATEGORY_LAUNCHER`; do not request broad package access
unless testing proves it is required.

Centralize all hardcoded targets in one source file so they can later be
replaced without touching the UI:

| Action | Initial target |
|---|---|
| Android Auto | launch package `com.zjinnova.zlink` |
| Radio | placeholder package/component, to be discovered |
| OEM settings | `com.xyauto.Settings/.MainActivity` |
| Android settings | `Settings.ACTION_SETTINGS`, with package fallback |
| App drawer | internal launcher screen |

For every external target, resolve the intent before launching it. If it is
unavailable, keep the launcher running and show a short, non-blocking message.
Never allow a missing app to crash the HOME Activity.

## 7. PC-first development and testing

Test the actual Android APK on the PC rather than creating a separate desktop
UI that could drift from the vehicle build.

Create an Android Studio AVD with:

- Android 11 / API 30;
- 1024×600 landscape display;
- hardware graphics acceleration;
- a density close to the physical unit, updated after measuring the unit with
  `adb shell wm size` and `adb shell wm density`;
- gesture and three-button navigation tests where the emulator supports them.

Because the emulator does not contain the vendor applications, provide a debug
strategy for external buttons. Prefer either small debug-only stub activities
or injected debug targets; production builds must retain the real hardcoded
targets and contain no fake apps.

PC acceptance checks:

- cold launch never exposes a blank frame;
- rotation plays once and ends exactly on the fallback frame;
- activity pause/resume does not restart the video;
- clock/date respond to locale and time-zone changes while keeping 24-hour
  time;
- all five dock buttons have large, non-overlapping touch targets;
- immersive bars can be revealed and hidden safely;
- Back/Home behavior is predictable on both screens;
- drawer sorting, labels, icon loading, and blacklist work correctly;
- missing or crashing target apps do not crash the launcher;
- process death and recreation restore a usable static home screen;
- the layout has no clipping at 1024×600.

Add focused unit tests for target resolution, blacklist filtering, and app
sorting. Add Compose UI tests for dock actions and drawer navigation. A small
set of reference screenshots at 1024×600 should be reviewed manually after UI
changes.

The emulator cannot validate hardware video decoding, boot behavior, reverse
camera overlays, CAN interactions, or vendor activity contracts. Those require
a final, short on-device validation pass.

## 8. Vehicle installation and safe launcher switch

Do not disable or uninstall either existing launcher during development.
Install debug builds while the OEM launcher remains the default, then start the
new Activity explicitly for testing.

Before changing HOME, record the current resolver result and confirm the new
launcher appears as a candidate. The intended flow is:

```sh
adb install -r app-debug.apk
adb shell am start -n com.golfv.launcher/.MainActivity
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME
```

After the PC checks and explicit on-device launch checks pass, select the new
HOME application using Android's launcher chooser or a verified
`cmd package set-home-activity` command.

Keep this recovery target documented and tested:

```sh
adb shell cmd package set-home-activity \
  com.android.launcher/com.xyauto.common.activity.MainActivity
```

Changing the default HOME handler is sufficient to remove the Chinese launcher
from normal startup. Leaving it installed provides a safer rollback path. Do
not alter `com.xygala.backcar`, either CAN package, or
`com.txznet.smartadapter` as part of the launcher installation.

## 9. Delivery phases

### Phase 1 — skeleton and emulator

- create the minimal Compose project and HOME manifest;
- implement launch theme, immersive mode, clock/date, and five-button dock;
- add debug targets and validate the 1024×600 layout in the API 30 emulator.

### Phase 2 — navigation

- implement safe intent resolution for the four external buttons;
- implement the app drawer, sorting, icon cache, and blacklist;
- add unit and Compose UI tests;
- leave Radio connected to a visible placeholder/failure state.

### Phase 3 — Blender assets and playback

- produce the 60 fps animation, exact final-frame WebP, and launch mark;
- integrate one-shot playback and failure fallback;
- profile cold start, memory use, APK size, and emulator behavior.

### Phase 4 — controlled vehicle validation

- install without changing the default launcher;
- verify the real ZLink and both settings targets;
- discover and verify the Radio component;
- check 60 fps playback, touch sizing, system-bar recovery, suspend/resume,
  reboot, reverse-camera overlay, and Android Auto return behavior;
- correct hardcoded targets and media encoding based on measured results.

### Phase 5 — default HOME rollout

- confirm ADB reconnection and the OEM rollback command;
- set the new launcher as HOME without disabling either OEM launcher;
- reboot and verify launcher startup, ZLink, Radio, settings, app drawer,
  reverse camera, and vehicle power-cycle behavior;
- record the tested APK version and rollback result in the repository.

## 10. Version-one acceptance criteria

Version one is complete when:

- the same APK passes the API 30 PC/emulator checks and installs via ADB;
- it can be selected as HOME and starts reliably after a full head-unit reboot;
- the launch mark, one-time 360-degree animation, and exact static final pose
  transition without a visible flash;
- time, date, five icon-only actions, and the filtered app drawer remain usable
  when video playback or an external package is unavailable;
- ZLink, OEM settings, Android settings, and the verified Radio activity launch
  correctly on the unit;
- returning from those applications does not replay the animation;
- immersive navigation remains recoverable;
- rear-camera and CAN behavior remain unchanged;
- restoring the OEM launcher has been tested successfully.

## 11. Deferred work

- configurable dock targets or ordering;
- themes and user-selected media;
- widgets, weather, media controls, and phone connection state;
- automatic Android Auto launch;
- localization of launcher-owned text beyond system-provided app labels;
- support for other head units, resolutions, or Android releases.
