package com.golfv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorStatesTest {
    @Test
    fun decodesTheVerifiedFourDoorBitset() {
        val allClosed = frame(0x20)
        val door1 = frame(0x21)
        val door2 = frame(0x23)
        val door3 = frame(0x25)
        val door4 = frame(0x28)

        assertFalse(VehicleSignals.doorStatesFromFrame(allClosed, allClosed.size)!!.hasOpenDoor)
        assertEquals(DoorStates(true, false, false, false), VehicleSignals.doorStatesFromFrame(door1, 10))
        assertEquals(DoorStates(true, true, false, false), VehicleSignals.doorStatesFromFrame(door2, 10))
        assertEquals(DoorStates(true, false, true, false), VehicleSignals.doorStatesFromFrame(door3, 10))
        assertEquals(DoorStates(false, false, false, true), VehicleSignals.doorStatesFromFrame(door4, 10))
    }

    @Test
    fun ignoresUnrelatedOrTruncatedFrames() {
        assertNull(VehicleSignals.doorStatesFromFrame(frame(0x28), 9))
        assertNull(VehicleSignals.doorStatesFromFrame(byteArrayOf(0x2E, 0x24, 0x02, 0, 8, 0), 6))
        assertTrue(VehicleSignals.doorStatesFromFrame(frame(0xA0), 10)!!.let { !it.hasOpenDoor })
    }

    @Test
    fun decodesSignedSpeedInHundredthsOfKph() {
        assertEquals(3.08f, VehicleSignals.vehicleSpeedKphFromFrame(speedFrame(0x34, 0x01), 6)!!, 0.0001f)
        assertEquals(-0.32f, VehicleSignals.vehicleSpeedKphFromFrame(speedFrame(0xE0, 0xFF), 6)!!, 0.0001f)
        assertNull(VehicleSignals.vehicleSpeedKphFromFrame(frame(0x20), 10))
    }

    @Test
    fun showsSpeedOnlyAboveTheForwardMovementThreshold() {
        assertFalse(shouldDisplayForwardSpeed(-0.32f))
        assertFalse(shouldDisplayForwardSpeed(0f))
        assertFalse(shouldDisplayForwardSpeed(0.5f))
        assertTrue(shouldDisplayForwardSpeed(0.51f))
        assertTrue(shouldDisplayForwardSpeed(15.76f))
    }

    private fun frame(status: Int) = byteArrayOf(
        0x2E,
        0x41,
        0x06,
        0x01,
        status.toByte(),
        0,
        0,
        0,
        0,
        0,
    )

    private fun speedFrame(low: Int, high: Int) = byteArrayOf(
        0x2E,
        0x26,
        0x02,
        low.toByte(),
        high.toByte(),
        0,
    )
}
