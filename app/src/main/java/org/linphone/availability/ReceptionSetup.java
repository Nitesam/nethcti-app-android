package org.linphone.availability;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import java.util.*;
import org.linphone.R;
import org.linphone.availability.ReceptionSetupPolicy.Id;

/** Shared configuration facts and UI, independent of SIP registration and call lifetime. */
public final class ReceptionSetup {
    public static final class Entry {
        public final Id id;
        public final String title, description, state, problem;
        public final boolean ready;
        Entry(Id id, String title, String description, String state, boolean ready, String problem) {
            this.id = id; this.title = title; this.description = description;
            this.state = state; this.ready = ready; this.problem = problem;
        }
        @Override public String toString() { return title + " — " + state + "\n" + description; }
    }
    private final Context context;
    private Boolean previousDndAccess;
    public ReceptionSetup(Context context) { this.context = context.getApplicationContext(); }
    private String text(int id) { return context.getString(id); }
    public boolean batteryExempt() {
        return context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(context.getPackageName());
    }
    public List<Entry> entries() {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        boolean access = nm.isNotificationPolicyAccessGranted();
        NotificationChannel channel = nm.getNotificationChannel(ReceptionController.CHANNEL);
        // Preserve the 1.6.4 fix, while respecting user-locked channel choices.
        if (access && !Boolean.TRUE.equals(previousDndAccess) && channel != null && !channel.canBypassDnd()) {
            channel.setBypassDnd(true);
            nm.createNotificationChannel(channel);
            channel = nm.getNotificationChannel(ReceptionController.CHANNEL);
        }
        previousDndAccess = access;
        List<Entry> result = new ArrayList<>();
        for (Id id : ReceptionSetupPolicy.applicable(Build.VERSION.SDK_INT, Build.MANUFACTURER)) {
            boolean ready = false;
            int title, description, problem;
            String state = null;
            switch (id) {
                case NOTIFICATIONS:
                    title = R.string.reception_notifications;
                    description = Build.VERSION.SDK_INT >= 33 ? R.string.reception_notifications_permission_help : R.string.reception_notifications_help;
                    problem = R.string.reception_need_notifications;
                    ready = nm.areNotificationsEnabled(); break;
                case CHANNEL:
                    title = R.string.reception_channel; description = R.string.reception_channel_help;
                    problem = R.string.reception_need_channel;
                    ready = channel != null && channel.getImportance() >= NotificationManager.IMPORTANCE_HIGH && channel.getSound() != null; break;
                case FULL_SCREEN:
                    title = R.string.reception_full_screen; description = R.string.reception_screen_help;
                    problem = R.string.reception_need_screen;
                    ready = Build.VERSION.SDK_INT >= 34 && nm.canUseFullScreenIntent(); break;
                case BATTERY:
                    title = R.string.reception_background; description = R.string.reception_battery_help;
                    problem = R.string.reception_need_battery;
                    ready = batteryExempt();
                    if (ready) state = text(R.string.reception_battery_exempt);
                    break;
                case DND:
                    title = R.string.reception_dnd; description = R.string.reception_dnd_help;
                    problem = R.string.reception_need_dnd;
                    int filter = nm.getCurrentInterruptionFilter();
                    boolean blocked = filter == NotificationManager.INTERRUPTION_FILTER_NONE || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS;
                    ready = access && channel != null && channel.canBypassDnd() && !blocked;
                    if (blocked) { problem = R.string.reception_sound_blocked; state = text(problem); }
                    break;
                case LOCAL_NETWORK:
                    title = R.string.reception_local_network; description = R.string.reception_network_help;
                    problem = R.string.reception_need_network;
                    ready = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED; break;
                default:
                    title = R.string.reception_samsung; description = R.string.reception_samsung_help;
                    problem = 0; state = text(R.string.reception_manual_check); break;
            }
            if (state == null) state = text(ready ? R.string.reception_ready : R.string.reception_to_configure);
            result.add(new Entry(id, text(title), text(description), state, ready, ready || problem == 0 ? "" : text(problem)));
        }
        return result;
    }
    public String problem() {
        for (Entry entry : entries()) if (!entry.problem.isEmpty()) return entry.problem;
        return "";
    }
    private Intent channelSettings() {
        return new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, ReceptionController.CHANNEL);
    }
    private void open(Activity activity, Entry entry) {
        String pkg = context.getPackageName();
        if (entry.id == Id.BATTERY) {
            boolean exempt = batteryExempt();
            Toast.makeText(activity, exempt ? R.string.reception_battery_exempt_help : R.string.reception_battery_request_help, Toast.LENGTH_LONG).show();
            SettingsNavigator.open(activity, SettingsNavigator.batteryRoutes(pkg, exempt));
            return;
        }
        if (entry.id == Id.SAMSUNG) {
            new AlertDialog.Builder(activity).setTitle(R.string.reception_samsung).setMessage(R.string.reception_samsung_help)
                    .setPositiveButton(R.string.reception_open_settings, (d,w) -> SettingsNavigator.open(activity,
                            Arrays.asList(SettingsNavigator.appDetails(pkg), new Intent(Settings.ACTION_SETTINGS))))
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        Intent intent;
        switch (entry.id) {
            case NOTIFICATIONS:
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg); break;
            case CHANNEL: intent = channelSettings(); break;
            case FULL_SCREEN:
                intent = Build.VERSION.SDK_INT >= 34
                        ? new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + pkg))
                        : SettingsNavigator.appDetails(pkg);
                break;
            case DND:
                NotificationManager nm = context.getSystemService(NotificationManager.class);
                int filter = nm.getCurrentInterruptionFilter();
                intent = !nm.isNotificationPolicyAccessGranted() ? new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        : (filter == NotificationManager.INTERRUPTION_FILTER_NONE || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS)
                                ? new Intent(Settings.ACTION_SOUND_SETTINGS) : channelSettings();
                break;
            default: intent = SettingsNavigator.appDetails(pkg); break;
        }
        SettingsNavigator.open(activity, Arrays.asList(intent, SettingsNavigator.appDetails(pkg), new Intent(Settings.ACTION_SETTINGS)));
    }
    public void show(Activity activity) {
        List<Entry> current = new ArrayList<>(entries());
        ArrayAdapter<Entry> adapter = new ArrayAdapter<Entry>(activity, android.R.layout.simple_list_item_2, current) {
            @Override public android.view.View getView(int position, android.view.View recycled, android.view.ViewGroup parent) {
                android.widget.LinearLayout row = new android.widget.LinearLayout(activity);
                row.setOrientation(android.widget.LinearLayout.VERTICAL);
                int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
                row.setPadding(padding, padding / 2, padding, padding / 2);
                Entry entry = getItem(position);
                android.widget.TextView title = new android.widget.TextView(activity);
                title.setText(entry.title);
                title.setTextSize(16);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(title);
                android.widget.TextView details = new android.widget.TextView(activity);
                details.setText(entry.state + "\n" + entry.description);
                details.setTextSize(14);
                row.addView(details);
                return row;
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.reception_configure)
                .setAdapter(adapter, null).setNegativeButton(android.R.string.ok, null).create();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable refresh = new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) { dialog.dismiss(); return; }
                List<Entry> updated = entries();
                if (!updated.toString().equals(current.toString())) {
                    current.clear(); current.addAll(updated); adapter.notifyDataSetChanged();
                }
                if (dialog.isShowing()) handler.postDelayed(this, 1000);
            }
        };
        dialog.setOnDismissListener(d -> handler.removeCallbacks(refresh));
        dialog.show();
        // Keep the list open, so its verified states refresh when returning from Settings.
        dialog.getListView().setOnItemClickListener((parent, view, position, itemId) -> open(activity, current.get(position)));
        handler.post(refresh);
    }
}
