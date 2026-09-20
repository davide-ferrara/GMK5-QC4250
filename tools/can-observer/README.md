# QC4250 CAN observer

Receive-only diagnostic utility for the vendor `canbus_service`. It registers
both `android.os.ICanbusInterface` and `android.os.ICarEventXYInerface`, then
prints raw `onResult(byte[], size)` callbacks and decoded `onCarEvent(int[])`
callbacks. It does not call any transmit or vehicle-control method.

Run it with the tablet's ADB serial:

```sh
tools/can-observer/run.sh 10.122.39.92:42941
```

Stop it with Ctrl-C. The temporary device-side file is
`/data/local/tmp/golf-can-observer.jar`.

The current vehicle configuration publishes exterior-light changes through
the vendor broadcast `xy.xygala.lamplet`, with the integer extra
`lamplet_state`. A verified passive live view is available with:

```sh
tools/can-observer/observe-lights.sh 10.122.39.92:42941
```

Observed values are `0` for exterior-light illumination off and `1` for on.
This signal is currently binary: it does not by itself distinguish position
lights from dipped headlights.
