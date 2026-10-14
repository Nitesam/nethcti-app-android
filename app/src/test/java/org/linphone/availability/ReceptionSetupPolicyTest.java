package org.linphone.availability;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.linphone.availability.ReceptionSetupPolicy.*;

public class ReceptionSetupPolicyTest {
    @Test public void onlyApplicableControlsOnEverySupportedVersion() {
        for (int sdk = 28; sdk <= 37; sdk++) {
            assertEquals(sdk >= 34, applicable(sdk, "Google").contains(Id.FULL_SCREEN));
            assertEquals(sdk >= 37, applicable(sdk, "Google").contains(Id.LOCAL_NETWORK));
            assertFalse(applicable(sdk, "Google").contains(Id.SAMSUNG));
            assertTrue(applicable(sdk, "SAMSUNG").contains(Id.SAMSUNG));
            assertTrue(applicable(sdk, null).contains(Id.BATTERY));
        }
    }
    @Test public void alreadyExemptNeverRepeatsConsentRequest() {
        assertEquals(BatteryRoute.APP_DETAILS, batteryRoutes(true).get(0));
        assertFalse(batteryRoutes(true).contains(BatteryRoute.REQUEST_EXEMPTION));
        assertEquals(BatteryRoute.REQUEST_EXEMPTION, batteryRoutes(false).get(0));
        assertEquals(BatteryRoute.OPTIMIZATION_LIST, batteryRoutes(false).get(1));
        assertEquals(BatteryRoute.APP_DETAILS, batteryRoutes(false).get(2));
        assertEquals(BatteryRoute.SETTINGS, batteryRoutes(false).get(3));
    }
}
