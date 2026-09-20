package com.golfv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExteriorLightsStateTest {
    @Test
    fun mapsOnlyConfirmedOemBroadcastValues() {
        assertEquals(ExteriorLightsState.Off, ExteriorLightsState.fromBroadcastValue(0))
        assertEquals(ExteriorLightsState.On, ExteriorLightsState.fromBroadcastValue(1))
        assertNull(ExteriorLightsState.fromBroadcastValue(-1))
        assertNull(ExteriorLightsState.fromBroadcastValue(2))
    }
}
