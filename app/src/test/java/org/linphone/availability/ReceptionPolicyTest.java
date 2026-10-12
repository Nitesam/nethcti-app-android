package org.linphone.availability;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReceptionPolicyTest {
    @Test public void offRetainsServiceOnlyForOutgoingOrExistingCalls() {
        assertFalse(ReceptionPolicy.needsRegistration(false, false, false));
        assertTrue(ReceptionPolicy.needsRegistration(false, true, false));
        assertTrue(ReceptionPolicy.needsRegistration(false, false, true));
        assertTrue(ReceptionPolicy.needsRegistration(true, false, false));
    }
    @Test public void retryNeverOverflowsOrExceedsThirtySeconds() {
        long previous = 0;
        for (int i=0;i<100;i++) {
            long value = ReceptionPolicy.retryDelay(i);
            assertTrue(value >= previous && value <= 30000);
            previous = value;
        }
    }
    @Test public void volumeFloorHandlesOddRangesWithoutLoweringLoudSettings() {
        assertEquals(4, ReceptionPolicy.ringVolume(0, 7));
        assertEquals(7, ReceptionPolicy.ringVolume(7, 7));
    }
    @Test public void manualChangeAndChangeBackBothPreventRestoration() {
        assertTrue(ReceptionPolicy.restoreValue(4, 4, false));
        assertFalse(ReceptionPolicy.restoreValue(7, 4, false));
        assertFalse(ReceptionPolicy.restoreValue(4, 4, true));
    }
}
