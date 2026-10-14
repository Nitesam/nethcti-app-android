package org.linphone;

import android.app.*;
import android.content.*;
import android.os.*;
import org.linphone.core.*;

/** Seeds the previous APK, then verifies the new APK preserves real Core storage during adb install -r. */
final class UpgradeScenarios {
    private final Instrumentation test;
    UpgradeScenarios(Instrumentation test) { this.test = test; }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    void run(boolean seed) {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            check("ranchu".equals(Build.HARDWARE), "Dedicated emulator required");
            Context context = test.getTargetContext();
            context.startActivity(new Intent(context, LinphoneLauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long end = SystemClock.elapsedRealtime() + 20000;
            while (!LinphoneService.isReady() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
            check(LinphoneService.isReady(), "Service not ready");
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            test.runOnMainSync(() -> {
                try {
                    Core core = LinphoneManager.getLc();
                    SharedPreferences prefs = context.getSharedPreferences("reception", 0);
                    if (seed) {
                        check(core.getProxyConfigList().length == 0, "Refusing to change an existing account");
                        ProxyConfig account = core.createProxyConfig();
                        account.setIdentityAddress(Factory.instance().createAddress("sip:migration-test@10.0.2.2"));
                        account.setServerAddr("sip:10.0.2.2:15060;transport=udp");
                        account.setRegisterEnabled(false);
                        core.addProxyConfig(account);
                        core.setDefaultProxyConfig(account);
                        core.createCallLog(Factory.instance().createAddress("sip:migration-test@10.0.2.2"),
                                Factory.instance().createAddress("sip:peer@10.0.2.2"), Call.Dir.Outgoing,
                                10, 1700000000L, 1700000001L, Call.Status.Success, false, 4.0f);
                        check(core.getCallLogs().length > 0, "History seed not stored");
                        prefs.edit().putBoolean("enabled", true).putBoolean("consent", true).commit();
                        core.getConfig().sync();
                    } else {
                        check(prefs.getBoolean("enabled", false) && prefs.getBoolean("consent", false), "Reception preferences lost");
                        check(core.getProxyConfigList().length == 1, "Account lost");
                        check("migration-test".equals(core.getDefaultProxyConfig().getIdentityAddress().getUsername()), "Account changed");
                        check(core.getCallLogs().length > 0 && core.getCallLogs()[0].getDuration() == 10, "History lost");
                        org.linphone.availability.ReceptionController.get(context).setEnabled(false);
                        core.removeProxyConfig(core.getDefaultProxyConfig());
                        core.getConfig().sync();
                    }
                } catch (Throwable error) { failure.set(error); }
            });
            if (failure.get() != null) throw new AssertionError(failure.get());
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            result.putString("upgrade", seed ? "PASS: persisted test account, call history and reception preferences on " + version
                    : "PASS: account, call history and reception preferences preserved by update to " + version);
            code = Activity.RESULT_OK;
        } catch (Throwable error) { result.putString("failure", android.util.Log.getStackTraceString(error)); }
        test.finish(code, result);
    }
}
