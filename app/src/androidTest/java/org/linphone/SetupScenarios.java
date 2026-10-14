package org.linphone;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;
import org.linphone.availability.*;

/** Public-settings navigation on disposable emulators; never changes a physical phone. */
final class SetupScenarios {
    private final Instrumentation test;
    SetupScenarios(Instrumentation test) { this.test = test; }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void main(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new AssertionError(error.get());
    }
    private String shellOutput(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = test.getUiAutomation().executeShellCommand(command);
             java.io.FileInputStream stream = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] bytes = new byte[4096]; int read;
            while ((read = stream.read(bytes)) != -1) output.write(bytes, 0, read);
            return output.toString("UTF-8");
        }
    }
    private void shell(String command) throws Exception { shellOutput(command); }
    private android.view.accessibility.AccessibilityNodeInfo awaitPackage(String expected) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 12000;
        android.view.accessibility.AccessibilityNodeInfo root;
        do {
            root = test.getUiAutomation().getRootInActiveWindow();
            // Android 12L can retain the underlying app accessibility root for a system dialog.
            // Verify the actual focused window as well, rather than mistaking that cache for a failed launch.
            String windows = shellOutput("dumpsys window");
            if (!windows.contains("mCurrentFocus=")) windows = shellOutput("dumpsys activity activities");
            for (String line : windows.split("\n")) {
                if (line.contains("mCurrentFocus=") && line.contains(expected + "/")) return root;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Expected UI " + expected + ", found " + (root == null ? "no window" : root.getPackageName()));
    }
    void run() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            check("ranchu".equals(Build.HARDWARE), "Dedicated emulator required");
            android.accessibilityservice.AccessibilityServiceInfo accessibility = test.getUiAutomation().getServiceInfo();
            accessibility.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            test.getUiAutomation().setServiceInfo(accessibility);
            Context context = test.getTargetContext();
            String pkg = context.getPackageName();
            ReceptionSetup setup = new ReceptionSetup(context);
            List<Intent> routes = SettingsNavigator.batteryRoutes(pkg, false);
            List<String> attempted = new ArrayList<>();
            check(SettingsNavigator.tryOpen(routes, intent -> {
                attempted.add(intent.getAction());
                if (attempted.size() == 1) throw new ActivityNotFoundException();
                if (attempted.size() == 2) throw new SecurityException();
            }), "Fallback failed");
            check(attempted.equals(Arrays.asList(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)), "Fallback order wrong");
            check(!SettingsNavigator.tryOpen(routes, intent -> { throw new ActivityNotFoundException(); }), "Missing-settings failure not reported");
            context.startActivity(new Intent(context, LinphoneLauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long end = SystemClock.elapsedRealtime() + 20000;
            while (!LinphoneService.isReady() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
            check(LinphoneService.isReady(), "Service startup failed");
            SystemClock.sleep(1500); // Let Launcher finish its delayed account/assistant routing.
            shell("am start -W -n " + pkg + "/org.linphone.LinphoneActivity");
            end = SystemClock.elapsedRealtime() + 10000;
            while (!LinphoneActivity.isInstanciated() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
            check(LinphoneActivity.isInstanciated(), "Home startup failed");
            SystemClock.sleep(1000);
            shell("dumpsys deviceidle whitelist -" + pkg);
            check(!setup.batteryExempt(), "Fixture still exempt");
            main(() -> SettingsNavigator.open(LinphoneActivity.instance(), routes));
            awaitPackage("com.android.settings");
            shell("input keyevent 4"); // Cancel the consent dialog, wait for navigation to complete.
            awaitPackage(pkg);
            SystemClock.sleep(300);
            check(!setup.batteryExempt(), "Cancelling consent falsely marked battery configured");
            shell("dumpsys deviceidle whitelist +" + pkg);
            check(setup.batteryExempt(), "Newly granted exemption not detected");
            shell("am start -W -n " + pkg + "/org.linphone.LinphoneActivity");
            main(() -> SettingsNavigator.open(LinphoneActivity.instance(), SettingsNavigator.batteryRoutes(pkg, true)));
            android.view.accessibility.AccessibilityNodeInfo root = awaitPackage("com.android.settings");
            shell("input keyevent 4");
            awaitPackage(pkg);
            SystemClock.sleep(300);
            shell("am start -W -n " + pkg + "/org.linphone.LinphoneActivity");
            SystemClock.sleep(1000);
            main(() -> setup.show(LinphoneActivity.instance()));
            long visibleDeadline = SystemClock.elapsedRealtime() + 10000;
            do {
                root = test.getUiAutomation().getRootInActiveWindow();
                for (android.view.accessibility.AccessibilityWindowInfo window : test.getUiAutomation().getWindows()) {
                    android.view.accessibility.AccessibilityNodeInfo candidate = window.getRoot();
                    if (candidate != null && !candidate.findAccessibilityNodeInfosByText(context.getString(R.string.reception_background)).isEmpty()) {
                        root = candidate; break;
                    }
                }
                if (root != null && !root.findAccessibilityNodeInfosByText(context.getString(R.string.reception_background)).isEmpty()) break;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < visibleDeadline);
            SystemClock.sleep(500);
            try (java.io.FileOutputStream image = new java.io.FileOutputStream(new java.io.File(context.getFilesDir(), "setup.png"))) {
                test.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, image);
            }
            check(root != null && !root.findAccessibilityNodeInfosByText(context.getString(R.string.reception_background)).isEmpty(), "Adaptive list missing battery row");
            result.putString("setup", "PASS: actual battery request/cancel, granted state, already-exempt Settings opening, missing/denied activity fallbacks, adaptive list");
            result.putInt("android_api", Build.VERSION.SDK_INT);
            code = Activity.RESULT_OK;
        } catch (Throwable error) { result.putString("failure", android.util.Log.getStackTraceString(error)); }
        test.finish(code, result);
    }
}
