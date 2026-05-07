package org.lineageos.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.lineageos.platform.internal.display.TwilightTracker.TwilightState;

public class TwilightStateTest {

    @Test
    public void testIsNight() {
        TwilightState night = new TwilightState(true, 0, 0, 0, 0);
        TwilightState day = new TwilightState(false, 0, 0, 0, 0);
        assertTrue(night.isNight());
        assertFalse(day.isNight());
    }

    @Test
    public void testGetters() {
        TwilightState state = new TwilightState(true, 100L, 200L, 300L, 400L);
        assertEquals(100L, state.getYesterdaySunset());
        assertEquals(200L, state.getTodaySunrise());
        assertEquals(300L, state.getTodaySunset());
        assertEquals(400L, state.getTomorrowSunrise());
    }

    @Test
    public void testEqualsSame() {
        TwilightState a = new TwilightState(true, 1, 2, 3, 4);
        TwilightState b = new TwilightState(true, 1, 2, 3, 4);
        assertTrue(a.equals(b));
    }

    @Test
    public void testEqualsDifferent() {
        TwilightState a = new TwilightState(true, 1, 2, 3, 4);
        TwilightState b = new TwilightState(false, 1, 2, 3, 4);
        assertFalse(a.equals(b));
    }
}
