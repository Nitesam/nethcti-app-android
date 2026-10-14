package org.linphone.availability;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;
import org.linphone.R;

/** Every settings action has a visible fallback; never assumes an OEM activity exists. */
public final class SettingsNavigator {
    public interface Launcher { void start(Intent intent); }
    private SettingsNavigator() {}

    public static boolean tryOpen(List<Intent> routes, Launcher launcher) {
        for (Intent intent : routes) {
            try {
                launcher.start(intent);
                return true;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Try the next public Android settings action, without logging device data.
            }
        }
        return false;
    }

    public static Intent appDetails(String packageName) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
    }

    public static List<Intent> batteryRoutes(String packageName, boolean exempt) {
        List<Intent> routes = new ArrayList<>();
        for (ReceptionSetupPolicy.BatteryRoute route : ReceptionSetupPolicy.batteryRoutes(exempt)) {
            switch (route) {
                case REQUEST_EXEMPTION:
                    routes.add(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName)));
                    break;
                case OPTIMIZATION_LIST: routes.add(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); break;
                case APP_DETAILS: routes.add(appDetails(packageName)); break;
                case SETTINGS: routes.add(new Intent(Settings.ACTION_SETTINGS)); break;
            }
        }
        return routes;
    }

    public static void open(Activity activity, List<Intent> routes) {
        if (!tryOpen(routes, activity::startActivity)) {
            new AlertDialog.Builder(activity).setTitle(R.string.reception_configure)
                    .setMessage(R.string.reception_settings_unavailable)
                    .setPositiveButton(android.R.string.ok, null).show();
        }
    }
}
