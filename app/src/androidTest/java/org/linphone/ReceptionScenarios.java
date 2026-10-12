package org.linphone;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import org.linphone.availability.ReceptionController;
import org.linphone.core.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Dedicated emulator only; every SIP request goes to the host's loopback test peer. */
final class ReceptionScenarios {
    private final Instrumentation test;
    private Core core;
    private ReceptionController reception;
    private ProxyConfig account;
    private final long idleMillis;
    private String lastCallError = "";
    ReceptionScenarios(Instrumentation test, long idleMillis) { this.test = test; this.idleMillis = idleMillis; }
    private void main(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private void await(String label, BooleanSupplier condition, long millis) {
        long end = SystemClock.elapsedRealtime() + millis;
        AtomicBoolean ok = new AtomicBoolean();
        do {
            main(() -> ok.set(condition.getAsBoolean()));
            if (ok.get()) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < end);
        throw new AssertionError(label + ": " + lastCallError);
    }
    private void incoming() throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = "INCOMING".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("10.0.2.2"), 15060));
        }
    }
    private boolean hasRingNotification() {
        for (android.service.notification.StatusBarNotification notification : test.getTargetContext().getSystemService(NotificationManager.class).getActiveNotifications()) {
            if ((notification.getNotification().flags & Notification.FLAG_INSISTENT) != 0) return true;
        }
        return false;
    }
    private void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = test.getUiAutomation().executeShellCommand(command);
             java.io.FileInputStream stream = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) { /* wait for shell completion */ }
        }
    }
    void run() {
        Bundle results = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            if (!"ranchu".equals(Build.HARDWARE)) throw new AssertionError("Dedicated emulator required");
            Context context = test.getTargetContext();
            context.startActivity(new Intent(context, LinphoneLauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("Service startup", LinphoneService::isReady, 20000);
            main(() -> {
                core = LinphoneManager.getLc();
                if (core.getProxyConfigList().length != 0) throw new AssertionError("Test requires emulator without real accounts");
                core.addListener(new CoreListenerStub() {
                    @Override public void onCallStateChanged(Core c, Call call, Call.State state, String message) {
                        if (state == Call.State.Error) lastCallError = call.getReason() + ": " + message;
                    }
                });
                reception = ReceptionController.get(context);
                reception.setEnabled(false);
                core.setMediaEncryption(MediaEncryption.None);
                for (PayloadType pt : core.getAudioPayloadTypes()) pt.enable(pt.getMimeType().equalsIgnoreCase("PCMU"));
                account = core.createProxyConfig();
                account.setIdentityAddress(Factory.instance().createAddress("sip:test@10.0.2.2"));
                account.setServerAddr("sip:10.0.2.2:15060;transport=udp");
                account.setRegisterEnabled(false);
                core.addProxyConfig(account);
                core.setDefaultProxyConfig(account);
                reception.setEnabled(true);
            });
            await("ON registers", () -> account.getState() == RegistrationState.Ok, 15000);
            test.getUiAutomation().performGlobalAction(2); // HOME
            main(() -> {
                for (ActivityManager.AppTask task : context.getSystemService(ActivityManager.class).getAppTasks()) task.finishAndRemoveTask();
            });
            SystemClock.sleep(idleMillis);
            await("Survives old idle timeout", () -> LinphoneService.isReady() && LinphoneService.instance().getForegroundServiceType() != 0, 3000);
            AudioManager audio = context.getSystemService(AudioManager.class);
            main(() -> { audio.setStreamVolume(AudioManager.STREAM_RING, 0, 0); audio.setRingerMode(AudioManager.RINGER_MODE_SILENT); });
            int previousMode = audio.getRingerMode(), previousVolume = audio.getStreamVolume(AudioManager.STREAM_RING);
            incoming();
            await("Incoming notification rings", this::hasRingNotification, 10000);
            await("Silent override", () -> audio.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && audio.getStreamVolume(AudioManager.STREAM_RING) >= (audio.getStreamMaxVolume(AudioManager.STREAM_RING)+1)/2, 3000);
            main(() -> reception.setEnabled(false));
            await("OFF cancels ring", () -> !hasRingNotification() && core.getCallsNb() == 0, 6000);
            await("Restores audio", () -> audio.getRingerMode() == previousMode && audio.getStreamVolume(AudioManager.STREAM_RING) == previousVolume, 3000);
            await("OFF unregisters", () -> !account.isRegisterEnabled(), 3000);
            incoming();
            SystemClock.sleep(1500);
            if (hasRingNotification()) throw new AssertionError("OFF incoming rang");
            // Outgoing calls are user actions from a visible activity, including after task removal.
            context.startActivity(new Intent(context, LinphoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("Outgoing UI visible", () -> LinphoneActivity.isInstanciated() && !LinphoneActivity.instance().isOnBackground(), 10000);
            // Home initializes the production SRTP policy. This loopback peer speaks plain RTP;
            // override only the emulator fixture after the activity has initialized it.
            main(() -> core.setMediaEncryption(MediaEncryption.None));
            main(() -> LinphoneManager.getInstance().newOutgoingCall(Factory.instance().createAddress("sip:peer@10.0.2.2:15060;transport=udp")));
            await("OFF outgoing connects", () -> core.getCurrentCall() != null && core.getCurrentCall().getState() == Call.State.StreamsRunning, 15000);
            incoming();
            SystemClock.sleep(1500);
            if (hasRingNotification()) throw new AssertionError("OFF rang during outgoing");
            main(() -> {
                if (core.getCallsNb() != 1) throw new AssertionError("Unexpected incoming call retained");
                reception.setEnabled(true);
                reception.setEnabled(false);
                if (core.getCurrentCall().getState() != Call.State.StreamsRunning) throw new AssertionError("OFF interrupted an active call");
                core.getCurrentCall().terminate();
            });
            await("Temporary registration removed", () -> core.getCallsNb() == 0 && !account.isRegisterEnabled(), 7000);
            main(() -> reception.setEnabled(true));
            await("ON registers again", () -> account.getState() == RegistrationState.Ok, 15000);
            incoming();
            await("Second ring", this::hasRingNotification, 8000);
            main(() -> audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamMaxVolume(AudioManager.STREAM_RING), 0));
            SystemClock.sleep(300);
            main(() -> reception.setEnabled(false));
            await("Second ring stops", () -> !hasRingNotification(), 5000);
            if (audio.getStreamVolume(AudioManager.STREAM_RING) != audio.getStreamMaxVolume(AudioManager.STREAM_RING)) throw new AssertionError("User volume overwritten");
            main(() -> reception.setEnabled(true));
            await("Registered before outage", () -> account.getState() == RegistrationState.Ok, 15000);
            shell("cmd connectivity airplane-mode enable");
            await("Default network lost", () -> context.getSystemService(android.net.ConnectivityManager.class).getActiveNetwork() == null, 15000);
            await("No false green while offline", () -> !reception.isConnected(), 3000);
            shell("cmd connectivity airplane-mode disable");
            await("Registration recovers after network outage", reception::isConnected, 45000);
            results.putString("network_recovery", "PASS: no default network, no crash/false green, automatic reconnection");
            results.putString("reception", "PASS: removed from recents + idle " + idleMillis + "ms, ring notification, silent override/restore, manual volume preserved, OFF rejects, OFF outgoing, temporary registration cleanup");
            code = Activity.RESULT_OK;
        } catch (Throwable failure) {
            results.putString("failure", android.util.Log.getStackTraceString(failure));
        } finally {
            try { shell("cmd connectivity airplane-mode disable"); } catch (Exception ignored) { }
            main(() -> { if (reception != null) reception.setEnabled(false); if (core != null) { core.terminateAllCalls(); if (account != null) core.removeProxyConfig(account); core.getConfig().sync(); } });
        }
        test.finish(code, results);
    }
}
