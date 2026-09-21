package com.golfv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val FORWARD_SPEED_DISPLAY_THRESHOLD_KPH = 0.5f

/** Ignore reverse and sub-0.5 km/h stationary noise from the OEM CAN bridge. */
internal fun shouldDisplayForwardSpeed(speedKph: Float): Boolean =
    speedKph > FORWARD_SPEED_DISPLAY_THRESHOLD_KPH

/**
 * Read-only vehicle signals published by the OEM Android integration.
 *
 * The service on this head unit broadcasts light changes rather than exposing
 * them through the reconstructed CAN Binder callbacks. Keeping the receiver
 * here makes the state usable by the Info screen now and by the car render
 * later, without ever sending a CAN or MCU command.
 */
class VehicleSignals(context: Context) {
    private val applicationContext = context.applicationContext
    private val _exteriorLights = MutableStateFlow(ExteriorLightsState.Unknown)
    val exteriorLights: StateFlow<ExteriorLightsState> = _exteriorLights.asStateFlow()
    private val _doors = MutableStateFlow<DoorStates?>(null)
    val doors: StateFlow<DoorStates?> = _doors.asStateFlow()
    private val _vehicleSpeedKph = MutableStateFlow(0f)
    val vehicleSpeedKph: StateFlow<Float> = _vehicleSpeedKph.asStateFlow()
    private val _lastVehicleSpeedKph = MutableStateFlow<Float?>(null)
    val lastVehicleSpeedKph: StateFlow<Float?> = _lastVehicleSpeedKph.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clearVehicleSpeed = Runnable { _vehicleSpeedKph.value = 0f }
    private val retryCanbusObserver = Runnable { startCanbusObserver() }
    private val canbusDeathRecipient = IBinder.DeathRecipient {
        mainHandler.post {
            canbusService = null
            isCanbusRegistered = false
            scheduleCanbusObserverRetry()
        }
    }
    private var isStarted = false
    private var isRegistered = false
    private var canbusService: IBinder? = null
    private var isCanbusRegistered = false

    private val canbusCallback = object : Binder(), IInterface {
        init {
            attachInterface(this, CANBUS_CALLBACK_DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CANBUS_CALLBACK_DESCRIPTOR)
                return true
            }
            if (code != CANBUS_CALLBACK_ON_RESULT) return super.onTransact(code, data, reply, flags)

            data.enforceInterface(CANBUS_CALLBACK_DESCRIPTOR)
            val frame = data.createByteArray()
            val declaredSize = data.readInt()
            doorStatesFromFrame(frame, declaredSize)?.let { _doors.value = it }
            vehicleSpeedKphFromFrame(frame, declaredSize)?.let(::updateVehicleSpeed)
            reply?.writeNoException()
            return true
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateExteriorLights(intent)
        }
    }

    /** Start receiving OEM light-state changes while the launcher is visible. */
    fun start() {
        if (isStarted) return
        isStarted = true

        try {
            val initialBroadcast = ContextCompat.registerReceiver(
                applicationContext,
                receiver,
                IntentFilter(EXTERIOR_LIGHTS_ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
            isRegistered = true
            // Android returns a matching sticky broadcast here when the OEM
            // provides one. The tested firmware has not yet proven that it
            // does, so the default remains Unknown when this is null.
            initialBroadcast?.let(::updateExteriorLights)
        } catch (_: SecurityException) {
            // A firmware with a protected or absent broadcast simply leaves
            // the UI in the unknown state; it must never break HOME.
        }
        startCanbusObserver()
    }

    /** Stop the dynamic receiver when this Activity is no longer foregrounded. */
    fun stop() {
        isStarted = false
        mainHandler.removeCallbacks(retryCanbusObserver)
        if (isRegistered) {
            applicationContext.unregisterReceiver(receiver)
            isRegistered = false
        }
        stopCanbusObserver()
        mainHandler.removeCallbacks(clearVehicleSpeed)
        _vehicleSpeedKph.value = 0f
    }

    private fun updateExteriorLights(intent: Intent) {
        if (intent.action != EXTERIOR_LIGHTS_ACTION || !intent.hasExtra(EXTERIOR_LIGHTS_EXTRA)) {
            return
        }

        ExteriorLightsState.fromBroadcastValue(
            intent.getIntExtra(EXTERIOR_LIGHTS_EXTRA, INVALID_LIGHTS_VALUE),
        )?.let { _exteriorLights.value = it }
    }

    private fun updateVehicleSpeed(speedKph: Float) {
        _vehicleSpeedKph.value = speedKph
        _lastVehicleSpeedKph.value = speedKph
        mainHandler.removeCallbacks(clearVehicleSpeed)
        // The CAN bridge stops forwarding this event when stationary. Do not
        // leave a last non-zero reading covering the car after it has stopped.
        if (speedKph != 0f) mainHandler.postDelayed(clearVehicleSpeed, SPEED_STALE_AFTER_MS)
    }

    /**
     * Registers only the receive side of the OEM CAN Binder interface.
     * No CAN/MCU transmit method is present in this class.
     */
    private fun startCanbusObserver() {
        if (!isStarted || isCanbusRegistered) return

        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val service = getService.invoke(null, CANBUS_SERVICE_NAME) as? IBinder
            if (service == null) {
                scheduleCanbusObserverRetry()
                return
            }
            service.linkToDeath(canbusDeathRecipient, 0)
            try {
                transactCanbus(service, CANBUS_TRANSACTION_REGISTER, canbusCallback)
            } catch (error: Exception) {
                service.unlinkToDeath(canbusDeathRecipient, 0)
                throw error
            }
            canbusService = service
            isCanbusRegistered = true
        } catch (error: Exception) {
            // A firmware that starts this service late or restarts it while HOME
            // is visible gets another registration attempt without restarting HOME.
            Log.w(TAG, "CAN receive callback unavailable", error)
            scheduleCanbusObserverRetry()
        }
    }

    private fun scheduleCanbusObserverRetry() {
        mainHandler.removeCallbacks(retryCanbusObserver)
        if (isStarted) mainHandler.postDelayed(retryCanbusObserver, CANBUS_RETRY_AFTER_MS)
    }

    private fun stopCanbusObserver() {
        mainHandler.removeCallbacks(retryCanbusObserver)
        val service = canbusService
        canbusService = null
        isCanbusRegistered = false
        if (service == null) return
        try {
            transactCanbus(service, CANBUS_TRANSACTION_UNREGISTER, canbusCallback)
        } catch (error: Exception) {
            Log.w(TAG, "CAN receive callback cleanup failed", error)
        } finally {
            runCatching { service.unlinkToDeath(canbusDeathRecipient, 0) }
        }
    }

    @Throws(RemoteException::class)
    private fun transactCanbus(service: IBinder, transaction: Int, callback: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(CANBUS_SERVICE_DESCRIPTOR)
            data.writeStrongBinder(callback)
            if (!service.transact(transaction, data, reply, 0)) {
                throw RemoteException("CAN Binder transaction $transaction rejected")
            }
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    companion object {
        private const val TAG = "VehicleSignals"
        const val EXTERIOR_LIGHTS_ACTION = "xy.xygala.lamplet"
        const val EXTERIOR_LIGHTS_EXTRA = "lamplet_state"
        private const val INVALID_LIGHTS_VALUE = -1

        private const val CANBUS_SERVICE_NAME = "canbus_service"
        private const val CANBUS_SERVICE_DESCRIPTOR = "android.os.ICanbusService"
        private const val CANBUS_CALLBACK_DESCRIPTOR = "android.os.ICanbusInterface"
        private const val CANBUS_TRANSACTION_REGISTER = 3
        private const val CANBUS_TRANSACTION_UNREGISTER = 6
        private const val CANBUS_CALLBACK_ON_RESULT = 1
        private const val CANBUS_RETRY_AFTER_MS = 2_000L
        private const val SPEED_STALE_AFTER_MS = 1_500L

        /**
         * Decodes the verified door-status frame, `2E 41 06 01 SS ...`.
         * The lower nibble of SS is the four-door open bitset; other status
         * bits (such as the short-lived 0xA0 close transition) are ignored.
         */
        internal fun doorStatesFromFrame(frame: ByteArray?, declaredSize: Int): DoorStates? {
            if (frame == null || declaredSize < 10 || frame.size < 10) return null
            if (frame[0].unsigned() != 0x2E || frame[1].unsigned() != 0x41 ||
                frame[2].unsigned() != 0x06 || frame[3].unsigned() != 0x01
            ) return null

            return DoorStates.fromStatusByte(frame[4].unsigned())
        }

        /** Decodes `2E 26 02 LL HH CC` as signed hundredths of km/h. */
        internal fun vehicleSpeedKphFromFrame(frame: ByteArray?, declaredSize: Int): Float? {
            if (frame == null || declaredSize < 6 || frame.size < 6) return null
            if (frame[0].unsigned() != 0x2E || frame[1].unsigned() != 0x26 ||
                frame[2].unsigned() != 0x02
            ) return null

            val raw = (frame[3].unsigned() or (frame[4].unsigned() shl 8)).toShort().toInt()
            return raw / 100f
        }

        private fun Byte.unsigned(): Int = toInt() and 0xFF
    }
}

/** The verified lower-nibble door-open bitset from the OEM CAN callback. */
data class DoorStates(
    val door1Driver: Boolean,
    val door2FrontPassenger: Boolean,
    val door3RearDriver: Boolean,
    val door4RearPassenger: Boolean,
) {
    /** Matches golf_top_00..15: driver front, passenger front, driver rear, passenger rear. */
    val renderMask: Int
        get() = (if (door1Driver) 1 else 0) or
            (if (door2FrontPassenger) 2 else 0) or
            (if (door3RearDriver) 4 else 0) or
            (if (door4RearPassenger) 8 else 0)

    val hasOpenDoor: Boolean
        get() = door1Driver || door2FrontPassenger || door3RearDriver || door4RearPassenger

    companion object {
        fun fromStatusByte(status: Int) = DoorStates(
            door1Driver = status and 0x01 != 0,
            door2FrontPassenger = status and 0x02 != 0,
            door3RearDriver = status and 0x04 != 0,
            door4RearPassenger = status and 0x08 != 0,
        )
    }
}

enum class ExteriorLightsState {
    Unknown,
    Off,
    On,
    ;

    companion object {
        /** Values confirmed on the QC4250 / Golf Mk5 setup. */
        fun fromBroadcastValue(value: Int): ExteriorLightsState? = when (value) {
            0 -> Off
            1 -> On
            else -> null
        }
    }
}
