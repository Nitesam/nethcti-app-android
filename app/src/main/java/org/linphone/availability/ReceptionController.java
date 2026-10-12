package org.linphone.availability;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import androidx.core.content.ContextCompat;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.*;
import java.util.ArrayList;
import java.util.List;

/** Single owner of the user's reception choice. All Core access is on its main thread. */
public final class ReceptionController {
    public static final String CHANNEL = "incoming_calls_v3";
    private static ReceptionController instance;
    public static synchronized ReceptionController get(Context context) {
        if (instance == null) instance = new ReceptionController(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> observers = new ArrayList<>();
    private Core core;
    private Runnable pendingOutgoing;
    private long outgoingDeadline, probeDeadline, nextProbe, nextRetry;
    private int attempts;
    private boolean checking, networkChanged, sawRegistration;
    private boolean serviceStartBlocked;
    private Boolean previousDndAccess;
    private RegistrationState registration = RegistrationState.None;
    private final RingAudioOverride audio;
    private String lastStatus = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (core == null) return;
            reconcile();
            handler.postDelayed(this, 1000);
        }
    };
    private final CoreListenerStub listener = new CoreListenerStub() {
        @Override public void onRegistrationStateChanged(Core c, ProxyConfig p, RegistrationState state, String ignored) {
            if (!p.equals(c.getDefaultProxyConfig())) return;
            registration = state;
            sawRegistration = true;
            if (state == RegistrationState.Ok) {
                checking = false;
                attempts = 0;
                nextProbe = SystemClock.elapsedRealtime() + 300000;
                if (pendingOutgoing != null) {
                    Runnable action = pendingOutgoing;
                    pendingOutgoing = null;
                    action.run();
                }
            } else if (state == RegistrationState.Progress) {
                if (!checking) { checking = true; probeDeadline = SystemClock.elapsedRealtime() + 30000; }
            } else if (state == RegistrationState.Failed || state == RegistrationState.Cleared) {
                checking = false;
                nextRetry = SystemClock.elapsedRealtime() + ReceptionPolicy.retryDelay(attempts++);
            }
            publish();
        }
        @Override public void onCallStateChanged(Core c, Call call, Call.State state, String ignored) {
            // Deferred: Core may still include a call being released while delivering callbacks.
            handler.post(() -> { if (core != null) { audio.finishIfNoRinging(core); reconcile(); } });
        }
    };
    private ReceptionController(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("reception", Context.MODE_PRIVATE);
        audio = new RingAudioOverride(context);
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            private void changed() {
                handler.post(() -> {
                    networkChanged = true;
                    checking = false;
                    registration = RegistrationState.None;
                    nextRetry = 0;
                    publish();
                });
            }
            @Override public void onAvailable(Network network) { changed(); }
            @Override public void onLost(Network network) { changed(); }
        });
    }
    public boolean isEnabled() { return preferences.getBoolean("enabled", false); }
    public boolean keepService() {
        return ReceptionPolicy.needsRegistration(isEnabled(), pendingOutgoing != null,
                core != null && core.getCallsNb() > 0);
    }
    public void attach(Core value) {
        if (core == value) return;
        detach();
        core = value;
        serviceStartBlocked = false;
        registration = RegistrationState.None;
        sawRegistration = false;
        nextRetry = 0;
        core.addListener(listener);
        handler.post(tick);
    }
    public void detach() {
        handler.removeCallbacks(tick);
        if (core != null) core.removeListener(listener);
        core = null;
        pendingOutgoing = null;
        checking = false;
        audio.restore();
        publish();
    }
    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean("enabled", enabled).commit();
        if (!enabled && core != null) {
            for (Call call : core.getCalls()) {
                if (isRinging(call)) decline(call);
            }
            audio.restore();
        }
        if (enabled && !LinphoneService.isReady()) {
            Intent intent = new Intent(context, LinphoneService.class);
            try {
                ContextCompat.startForegroundService(context, intent);
                serviceStartBlocked = false;
            } catch (RuntimeException failure) {
                serviceStartBlocked = true;
                android.util.Log.w("Reception", "Service start blocked: " + failure.getClass().getSimpleName());
            }
        }
        nextRetry = 0;
        reconcile();
        publish();
    }
    public static boolean isRinging(Call call) {
        return call.getState() == Call.State.IncomingReceived || call.getState() == Call.State.IncomingEarlyMedia;
    }
    public boolean blockIncoming(Call call) {
        if (!isEnabled() && isRinging(call)) {
            decline(call);
            return true;
        }
        return false;
    }
    private void decline(Call call) {
        android.util.Log.i("Reception", "incoming_rejected reception_disabled");
        ErrorInfo info = Factory.instance().createErrorInfo();
        info.set("SIP", Reason.TemporarilyUnavailable, 480, "Reception disabled", null);
        call.declineWithErrorInfo(info);
    }
    public void placeOutgoing(Runnable action) {
        if (core == null || pendingOutgoing != null) return;
        if (core.getDefaultProxyConfig() != null && core.getDefaultProxyConfig().isRegisterEnabled() && core.getDefaultProxyConfig().getState() == RegistrationState.Ok) {
            action.run();
            reconcile();
            return;
        }
        pendingOutgoing = action;
        outgoingDeadline = SystemClock.elapsedRealtime() + 30000;
        nextRetry = 0;
        reconcile();
    }
    private void reconcile() {
        if (core == null) return;
        long now = SystemClock.elapsedRealtime();
        if (pendingOutgoing != null && now >= outgoingDeadline) {
            pendingOutgoing = null;
            android.widget.Toast.makeText(context, R.string.reception_outgoing_failed, android.widget.Toast.LENGTH_LONG).show();
        }
        ProxyConfig proxy = core.getDefaultProxyConfig();
        if (proxy != null) {
            boolean wanted = keepService();
            if (proxy.isRegisterEnabled() != wanted) {
                proxy.edit();
                proxy.setRegisterEnabled(wanted);
                proxy.done();
                if (wanted) { checking = true; probeDeadline = now + 30000; }
            }
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            boolean network = cm.getActiveNetwork() != null;
            if (!network) {
                registration = RegistrationState.None;
                checking = false;
            } else if (wanted) {
                if (checking && now >= probeDeadline) {
                    checking = false;
                    registration = RegistrationState.Failed;
                    nextRetry = now + ReceptionPolicy.retryDelay(attempts++);
                }
                if (!checking && (networkChanged || (registration != RegistrationState.Ok && now >= nextRetry)
                        || (isEnabled() && now >= nextProbe))) {
                    networkChanged = false;
                    checking = true;
                    probeDeadline = now + 30000;
                    core.refreshRegisters();
                }
            }
        }
        if (LinphoneService.isReady()) {
            if (keepService()) LinphoneService.instance().getNotificationManager().ensureReceptionForeground();
            else LinphoneService.instance().getNotificationManager().removeForegroundServiceNotificationIfPossible();
        }
        publish();
    }
    public String configurationProblem() {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        boolean dndAccess = nm.isNotificationPolicyAccessGranted();
        NotificationChannel channel = nm.getNotificationChannel(CHANNEL);
        // The channel normally predates the user's grant. Refresh it when returning
        // from Settings, without restarting the service or recreating the channel.
        // Android preserves a bypass choice that the user has locked in channel settings.
        if (dndAccess && !Boolean.TRUE.equals(previousDndAccess) && channel != null && !channel.canBypassDnd()) {
            channel.setBypassDnd(true);
            nm.createNotificationChannel(channel);
            channel = nm.getNotificationChannel(CHANNEL);
        }
        previousDndAccess = dndAccess;
        if (!nm.areNotificationsEnabled()) return context.getString(R.string.reception_need_notifications);
        if (channel == null || channel.getImportance() < NotificationManager.IMPORTANCE_HIGH || channel.getSound() == null)
            return context.getString(R.string.reception_need_channel);
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) return context.getString(R.string.reception_need_screen);
        if (!context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(context.getPackageName()))
            return context.getString(R.string.reception_need_battery);
        if (!dndAccess || !channel.canBypassDnd())
            return context.getString(R.string.reception_need_dnd);
        int filter = nm.getCurrentInterruptionFilter();
        if (filter == NotificationManager.INTERRUPTION_FILTER_NONE || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS)
            return context.getString(R.string.reception_sound_blocked);
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED)
            return context.getString(R.string.reception_need_network);
        return "";
    }
    public boolean isConnected() {
        return isEnabled() && core != null && registration == RegistrationState.Ok && !networkChanged && configurationProblem().isEmpty();
    }
    public String status() {
        if (!isEnabled()) return context.getString(R.string.reception_off);
        if (serviceStartBlocked) return context.getString(R.string.reception_start_blocked);
        String problem = configurationProblem();
        if (!problem.isEmpty()) return context.getString(R.string.reception_setup) + ": " + problem;
        if (core == null) return context.getString(R.string.reception_connecting);
        if (core.getDefaultProxyConfig() == null) return context.getString(R.string.reception_setup) + ": " + context.getString(R.string.no_account);
        if (registration == RegistrationState.Ok && !networkChanged) return context.getString(R.string.reception_connected);
        return context.getString(sawRegistration ? R.string.reception_reconnecting : R.string.reception_connecting);
    }
    public void addObserver(Runnable observer) { observers.add(observer); observer.run(); }
    public void removeObserver(Runnable observer) { observers.remove(observer); }
    private void publish() {
        String value = status();
        if (!lastStatus.equals(value)) {
            lastStatus = value;
            android.util.Log.i("Reception", "state=" + value);
            for (Runnable observer : new ArrayList<>(observers)) observer.run();
            if (LinphoneService.isReady()) LinphoneService.instance().getNotificationManager().updateReceptionStatus(value);
        }
    }
    public void prepareRing() { audio.begin(); }
    public void finishRingIfNeeded() { if (core != null) audio.finishIfNoRinging(core); }
    public void showSetup(Activity activity) {
        String[] choices = {context.getString(R.string.reception_need_notifications), context.getString(R.string.reception_need_channel),
                context.getString(R.string.reception_need_screen), context.getString(R.string.reception_need_battery),
                context.getString(R.string.reception_need_dnd), context.getString(R.string.reception_samsung)};
        new AlertDialog.Builder(activity).setTitle(R.string.reception_setup)
            .setItems(choices, (dialog, which) -> {
                Intent intent;
                switch (which) {
                    case 0: intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName()); break;
                    case 1: intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL); break;
                    case 2: intent = new Intent(Build.VERSION.SDK_INT >= 34 ? Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT : Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())); break;
                    case 3: intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.getPackageName())); break;
                    case 4:
                        if (context.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted()) {
                            intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL);
                        } else {
                            intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                        }
                        break;
                    default:
                        new AlertDialog.Builder(activity).setMessage(R.string.reception_samsung_help).setPositiveButton(android.R.string.ok, (d,w) -> activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())))).show();
                        return;
                }
                try { activity.startActivity(intent); } catch (ActivityNotFoundException e) {
                    activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())));
                }
            }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
