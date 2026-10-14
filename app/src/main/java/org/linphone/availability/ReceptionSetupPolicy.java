package org.linphone.availability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Version/manufacturer decisions kept independent of Android services. */
public final class ReceptionSetupPolicy {
    public enum Id { NOTIFICATIONS, CHANNEL, FULL_SCREEN, BATTERY, DND, LOCAL_NETWORK, SAMSUNG }
    public enum BatteryRoute { REQUEST_EXEMPTION, OPTIMIZATION_LIST, APP_DETAILS, SETTINGS }
    private ReceptionSetupPolicy() {}

    public static List<Id> applicable(int sdk, String manufacturer) {
        List<Id> result = new ArrayList<>(Arrays.asList(Id.NOTIFICATIONS, Id.CHANNEL));
        if (sdk >= 34) result.add(Id.FULL_SCREEN);
        result.add(Id.BATTERY);
        result.add(Id.DND);
        if (sdk >= 37) result.add(Id.LOCAL_NETWORK);
        if ("samsung".equalsIgnoreCase(manufacturer)) result.add(Id.SAMSUNG);
        return result;
    }

    public static List<BatteryRoute> batteryRoutes(boolean exempt) {
        return exempt
                ? Arrays.asList(BatteryRoute.APP_DETAILS, BatteryRoute.OPTIMIZATION_LIST, BatteryRoute.SETTINGS)
                : Arrays.asList(BatteryRoute.REQUEST_EXEMPTION, BatteryRoute.OPTIMIZATION_LIST,
                        BatteryRoute.APP_DETAILS, BatteryRoute.SETTINGS);
    }
}
