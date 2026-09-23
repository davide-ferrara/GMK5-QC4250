package com.golfv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorStatesTest {
    @Test
    fun keepsTheTailgateUnverifiedForEveryKnownDoorStatus() {
        for (status in listOf(0x00, 0x10, 0x20, 0x30, 0xA0)) {
            val state = VehicleSignals.doorStatesFromFrame(frame(status), 10)!!
            assertNull(state.tailgateOpen)
        }
    }

    @Test
    fun decodesTheUserVerifiedHoodBitWithoutChangingTheDoorBitset() {
        val closed = VehicleSignals.doorStatesFromFrame(frame(0x22), 10)!!
        val open = VehicleSignals.doorStatesFromFrame(frame(0x32), 10)!!

        assertFalse(closed.hoodOpen!!)
        assertTrue(open.hoodOpen!!)
        assertEquals(closed.door1Driver, open.door1Driver)
        assertEquals(closed.door2FrontPassenger, open.door2FrontPassenger)
        assertEquals(closed.door3RearDriver, open.door3RearDriver)
        assertEquals(closed.door4RearPassenger, open.door4RearPassenger)
        assertFalse(VehicleSignals.doorStatesFromFrame(frame(0x20), 10)!!.hoodOpen!!)
    }

    @Test
    fun decodesTheUserConfirmedIgnitionBit() {
        assertFalse(VehicleSignals.ignitionOnFromFrame(frame(0x00), 10)!!)
        assertFalse(VehicleSignals.ignitionOnFromFrame(frame(0x10), 10)!!)
        assertTrue(VehicleSignals.ignitionOnFromFrame(frame(0x20), 10)!!)
        assertTrue(VehicleSignals.ignitionOnFromFrame(frame(0x30), 10)!!)
        assertNull(VehicleSignals.ignitionOnFromFrame(frame(0x20), 9))
    }

    @Test
    fun tailgateAddsItsOwnRenderBitWithoutChangingTheFourDoors() {
        for (mask in 0..15) {
            val doors = DoorStates.fromStatusByte(mask)
            assertEquals(mask, doors.renderMask)
            assertEquals(mask, doors.copy(tailgateOpen = false).renderMask)
            val tailgateOpen = doors.copy(tailgateOpen = true)
            assertEquals(mask + 16, tailgateOpen.renderMask)
            assertTrue(tailgateOpen.hasOpenDoor)
        }
    }

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
