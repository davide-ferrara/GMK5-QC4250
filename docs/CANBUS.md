# CAN Bus Investigation

This document records the read-only CAN/vehicle-signal experiments performed
on the QC4250 head unit installed in the Golf Mk5. The findings apply only to
this unit, CAN box, vehicle configuration, and firmware.

## Safety and scope

The investigation was passive. No CAN packets or vehicle-control commands were
intentionally transmitted. In particular, the observer does not call the
vendor methods `setValue`, `setCanbusDataToUser`, or `deviceOnkey`.

Vehicle actions were performed manually by the owner. Tests involving movement
were done at low speed, with no interaction with ADB while the vehicle was
moving.

## Observed architecture

The head unit does not expose a Linux SocketCAN interface. There is no `can0`,
`vcan0`, or other CAN network interface under `/sys/class/net`.

Three Qualcomm high-speed UART devices are present:

| Device | Driver | Permissions |
|---|---|---|
| `/dev/ttyHS0` | `msm_geni_serial` | `system:system`, mode `0660` |
| `/dev/ttyHS1` | `msm_geni_serial` | `system:system`, mode `0660` |
| `/dev/ttyHS2` | `msm_geni_serial` | `system:system`, mode `0660` |

The ADB `shell` user cannot open these UARTs directly. The apparent vehicle
data path is:

```text
Vehicle CAN
  -> external CAN box / MCU
  -> vendor serial protocol over a ttyHS UART
  -> Android system service and vendor applications
  -> Binder callbacks, broadcasts, properties, and UI behavior
```

Relevant running components include:

- `canbus_service`, implementing `android.os.ICanbusService`;
- `com.kyhero.car.myhost`, running with the privileged `system` UID;
- `com.kyhero.car.myhost2`;
- `android.hardware.car_event_monitor@1.0-service`;
- `com.android.server.xy.NewkeypadMonitor` inside `system_server`.

The CAN applications have `android.permission.SERIAL_PORT`. Their code contains
support for door, light, key, radar, trajectory, climate-control, and vehicle
settings data, but the presence of these models does not prove that the
installed CAN box supplies the corresponding signals.

## Vendor interfaces

The device framework contains the hidden Binder interface
`android.os.ICanbusService`. Relevant methods reconstructed from
`framework.jar` include:

```text
setValue(byte[]) -> int
setCanbusInterface(ICanbusInterface)
removeCanbusInterface(ICanbusInterface)
setCanbusInterface_other(ICanbusInterface, int)
removeCanbusInterface_other(ICanbusInterface, int)
regCarEventInterface(ICarEventXYInerface)
unregCarEventInterface(ICarEventXYInerface)
setCanbusDataToUser(int, int, int[], int)
deviceOnkey(int, int, int)
```

The receive interfaces expose:

```text
ICanbusInterface.onResult(byte[], int)
ICanbusInterface.enterOrQuitBackcar(int)
ICarEventXYInerface.onCarEvent(int[])
```

The native car-event HAL contains explicit support for power, speed,
acceleration, steering-wheel angle, gearbox, radar, lights, doors, and the
rear-camera GPIO. It refers to properties including:

```text
persist.vendor.cem
persist.vendor.cem.power.status
persist.vendor.cem.speed
persist.vendor.cem.angle
persist.vendor.cem.radar
persist.vendor.cem.light
persist.vendor.cem.cardoor
```

During these experiments, the signal-specific properties remained empty.

## Passive observer

The receive-only diagnostic utility is under
[`tools/can-observer`](./tools/can-observer/). It runs through `app_process`,
registers the two receive callbacks, and prints timestamps and received arrays.

```sh
tools/can-observer/run.sh DEVICE_SERIAL
```

Before the forgotten CAN-box cable was connected, the observer registered but
received no raw data. With the cable connected it receives repeated raw
`onResult` frames for vehicle state changes. No useful structured
`onCarEvent` data has been observed; all confirmed signals below come from
the raw callback.

## Confirmed readable signals

### Exterior lights: binary on/off

The general exterior-light state is published through the broadcast:

```text
action: xy.xygala.lamplet
extra:  lamplet_state
```

Observed values:

| Value | Meaning |
|---:|---|
| `0` | Exterior-light illumination off |
| `1` | Exterior-light illumination on |

The owner independently repeated and confirmed the result. The signal is
binary and does not distinguish position lights from dipped headlights.

It can be monitored with:

```sh
tools/can-observer/observe-lights.sh DEVICE_SERIAL
```

### Launcher integration

The launcher subscribes dynamically to `xy.xygala.lamplet` while its main
activity is foregrounded. `VehicleSignals` maps `lamplet_state` to the
read-only `ExteriorLightsState` API (`Off` for `0`, `On` for `1`). The current
Info page displays this as **ON** or **OFF**; before the first broadcast it
explicitly displays that it is waiting for a vehicle signal. Registration also
uses a matching sticky broadcast as the initial value if the OEM publishes
one; this firmware has not yet been confirmed to do so, so the UI never
invents an initial state.

The same observable state is intentionally kept outside the UI so a future
Golf render can select its lit/unlit asset from it.

### Doors: four-bit open-state field

With the CAN-box cable connected, the receive-only `ICanbusInterface.onResult`
callback publishes a repeated frame in this shape:

```text
2E 41 06 01 SS 00 00 00 00 CC
```

`SS` is a status byte. Its low nibble is the confirmed open-door bitset. Bit
`0x10` is the verified hood-open state. During owner-operated open/close
testing, the stable frame changed from `... 22 ...` (hood closed) to
`... 32 ...` (hood open) and back, leaving the low four door bits unchanged.
User testing also confirmed that bit `0x20` is the ignition/key-on state, not
the parking brake. A short `0xA0` close transition was observed; bit `0x80`
is not yet identified.

| Door | Location | Open status byte | Bit |
|---:|---|---:|---:|
| 1 | Driver | `0x21` | `0x01` |
| 2 | Front passenger | `0x23` | `0x02` |
| 3 | Rear, driver side | `0x25` | `0x04` |
| 4 | Rear, passenger side | `0x28` | `0x08` |

| State | On status byte | Bit |
|---|---:|---:|
| Hood open | `0x10` | `0x10` |
| Ignition / key on | `0x20` | `0x20` |

The launcher registers only `setCanbusInterface` (transaction 3) while its
main activity is visible and unregisters it with transaction 6 when it stops.
It decodes the low four bits and the verified hood bit `0x10` into a read-only
`DoorStates` flow, and bit `0x20` into a separate nullable ignition flow; it
does not call `setValue`,
`setCanbusDataToUser`, or `deviceOnkey`.

### Tailgate: not yet calibrated

The rear tailgate render is available in Info as a visual preview. The CAN
decoder leaves `tailgateOpen = null`; unknown is not reported as closed. Bit
`0x10` in this frame is now known to be the hood, so it must not be used for
the tailgate.

Before enabling decoding, capture repeated closed/open/closed tailgate cycles
with all four doors closed, then repeat with one side door open and with
ignition off/on. Compare the complete frames and verify a bit that tracks only
the tailgate; do not send guessed frames to the bus. The UI render bit `16` is
an internal image index, independent of the raw CAN status byte.

### Hood: verified raw bit

The `2E 41 06 01` raw status byte uses `SS & 0x10` for the hood. The launcher
updates `hoodOpen` directly from that receive-only frame; it does not bind the
OEM structured door service or invoke any vehicle-control transaction. The
current hood state is displayed in **Project information** as `Cofano`. The
top-view car assets do not yet contain a front-hood-open rendering.

### Vehicle speed: signed 16-bit field

During slow forward and reverse parking movements, frames of this form changed
continuously:

```text
2E 26 02 LL HH CC
```

Interpreting `LL HH` as a signed little-endian 16-bit integer gives the
observed sequence `-32`, `68`, `100`, `272`, `308`. A scale of 0.01 km/h would
correspond to `-0.32`, `0.68`, `1.00`, `2.72`, and `3.08 km/h`, consistent with
the owner’s forward/reverse parking manoeuvres. Turning the steering wheel
while stationary with the engine running did not publish this frame, further
supporting the speed interpretation rather than steering angle.

The owner subsequently observed `15.76 km/h` in the launcher, consistent with
the instrument cluster. This confirms both the signed direction and the
`0.01 km/h` scale. The launcher exposes the value read-only; reverse is
negative on the wire, while the home screen uses its magnitude to decide that
the vehicle is moving. The Info page retains the last received CAN speed to
aid diagnostics after the vehicle stops.

### Reverse gear / rear-camera trigger

Reverse engagement is reliably visible, but primarily through a dedicated
GPIO rather than a confirmed raw CAN frame:

```text
/sys/class/gpio/gpio1209/value
CAR_EVENT_CAMERAGPIO gpiostate(1)  # reverse active
CAR_EVENT_CAMERAGPIO gpiostate(0)  # reverse inactive
xy.android.mcu.backcar.in
xy.android.mcu.backcar.out
```

When reverse was engaged, the camera service opened camera 0 and displayed its
overlay. The following vendor serial transmissions were correlated with the
state change:

```text
reverse active:   2E C4 01 8F AB
reverse inactive: 2E C4 01 0F 2B
```

These are messages transmitted by Android toward the MCU/CAN box (`tx`), not
proven raw vehicle CAN frames. The `0x80` bit correlates with the two states.

## Signals not exposed or not yet identified

The following tests produced no usable raw callback, structured callback,
property, setting, or distinct broadcast on this configuration:

| Signal | Test performed | Result |
|---|---|---|
| Structured car events / properties | Raw callback now works, but `onCarEvent` and the `persist.vendor.cem.*` properties remain empty | Use the verified raw frames instead. |
| Forward gears | First and second were selected and returned to neutral repeatedly while stationary, with clutch and brake applied | No repeatable direct gear-position frame. Reverse remains readable through its dedicated camera trigger. |
| Central locking | Locked then unlocked with all doors closed | No distinct lock/unlock frame; the previously seen `0x80` status bit did not recur and remains unidentified. |
| MFA / trip-computer data | The instrument cluster has no MFA controls or selectable driving-data pages | No in-car reference for odometer, trip distance, fuel, or external-temperature frames. |
| ACC / ignition | The door-status frame's `0x20` bit was confirmed in vehicle testing to follow ignition/key-on; transitions `2E 41 02 03 40 79` and `2E 41 02 03 00 B9` were also observed with concurrent changes | The launcher exposes only the confirmed `0x20` state. The `2E 41 02` frames remain uncalibrated. |
| Engine running / RPM | A varying `2E 14 01 VV CC` frame was observed with the engine running and `VV=0` after it stopped | Meaning and scale not calibrated. |
| Turn indicators | Left and right indicators enabled and disabled separately | No distinct event. |
| Hazard lights | Hazard lights enabled and disabled | No distinct event. |
| High beams | Fixed high beams and four on/off flashes | No distinct event. |

Opening a door coincided with general light-related activity in one capture,
but it did not contain door identity or door state and is not considered a
door signal.

During the speed experiment, messages with the following shape appeared in
the vendor application's transmit log:

```text
2E A2 08 C0 00 00 00 00 00 00 00 95
2E A2 08 C0 07 00 00 00 00 00 00 8E
2E A2 08 C0 06 00 00 00 00 00 00 8F
```

They were outbound (`tx`) messages and were not proven to encode vehicle
speed, so they must not be treated as speed data.

## Interpretation

The firmware has generic support for many vehicle signals, but the installed
CAN box and selected vehicle profile expose a specific subset to Android. For
this unit, the useful confirmed inputs are currently:

1. binary exterior-light state;
2. individual state of all four doors;
3. ignition/key-on state (`0x20` in the door-status frame);
4. hood state (`0x10` in the door-status frame);
5. signed vehicle speed at `0.01 km/h` resolution;
6. reverse/rear-camera state.

The negative results do not prove that the Golf Mk5 CAN network lacks the
signals. They show only that the current CAN box, configuration, and Android
integration do not publish them through the interfaces inspected here.

Directly opening a `ttyHS` device is not recommended: it requires privileged
access, may conflict with the service already owning the port, and could
interfere with vehicle integration. Future investigation should remain passive
and should prefer a separate physical CAN interface if raw bus traffic is
required.
