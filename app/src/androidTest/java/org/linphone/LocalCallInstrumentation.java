package org.linphone;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.linphone.call.CallManager;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.MediaEncryption;

/** Emulator-only integration test against tools/local_sip_peer.py, never a real PBX. */
public final class LocalCallInstrumentation extends Instrumentation {
    private Core core;
    private Call call;
    private CoreListenerStub listener;
    private MediaEncryption previousEncryption;
    private String previousContact;
    private org.linphone.core.ProxyConfig testAccount;
    private boolean testIncoming;
    private boolean testReception;
    private boolean testRevokedNotifications;
    private boolean testDndGrant;
    private boolean testSetup;
    private int expectedPageSize;
    private String upgradeMode;
    private long receptionIdleMillis = 95000;
    private final CountDownLatch incoming = new CountDownLatch(1);
    private final CountDownLatch incomingMedia = new CountDownLatch(1);
    private final StringBuilder states = new StringBuilder();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        testIncoming = arguments != null && "true".equals(arguments.getString("incoming"));
        testReception = arguments != null && "true".equals(arguments.getString("reception"));
        testRevokedNotifications = arguments != null && "true".equals(arguments.getString("revoked_notifications"));
        testDndGrant = arguments != null && "true".equals(arguments.getString("dnd_grant"));
        if (arguments != null) receptionIdleMillis = Long.parseLong(arguments.getString("reception_idle_ms", "95000"));
        upgradeMode = arguments == null ? null : arguments.getString("upgrade");
        testSetup = arguments != null && "true".equals(arguments.getString("setup"));
        expectedPageSize = arguments == null ? 0 : Integer.parseInt(arguments.getString("page_size", "0"));
        start();
    }

    @Override public void onStart() {
        if (upgradeMode != null) { new UpgradeScenarios(this).run("seed".equals(upgradeMode)); return; }
        if (testSetup) { new SetupScenarios(this).run(); return; }
        if (testReception) { new ReceptionScenarios(this, receptionIdleMillis).run(); return; }
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_CANCELED;
        try {
            require("ranchu".equals(Build.HARDWARE), "Only run on the dedicated emulator");
            require(Build.VERSION.SDK_INT >= 28, "Android 9 or later required");
            if (expectedPageSize != 0) require(Os.sysconf(OsConstants._SC_PAGESIZE) == expectedPageSize, "Unexpected memory page size");
            result.putInt("android_api", Build.VERSION.SDK_INT);
            result.putLong("page_size", Os.sysconf(OsConstants._SC_PAGESIZE));
            if (testDndGrant) {
                android.app.NotificationManager notifications = getTargetContext().getSystemService(android.app.NotificationManager.class);
                require(!notifications.isNotificationPolicyAccessGranted(), "Start with DND access revoked on a fresh emulator install");
                onMain(() -> {
                    org.linphone.compatibility.Compatibility.createNotificationChannels(getTargetContext());
                    org.linphone.availability.ReceptionController.get(getTargetContext()).configurationProblem();
                    require(!notifications.getNotificationChannel(org.linphone.availability.ReceptionController.CHANNEL).canBypassDnd(),
                            "Channel must predate the DND grant");
                });
                try (android.os.ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand("cmd notification allow_dnd it.nethesis.nethctiapp.debug");
                     java.io.FileInputStream stream = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
                    byte[] buffer = new byte[1024];
                    while (stream.read(buffer) != -1) { }
                }
                require(notifications.isNotificationPolicyAccessGranted(), "DND access grant failed");
                onMain(() -> {
                    org.linphone.availability.ReceptionController.get(getTargetContext()).configurationProblem();
                    android.app.NotificationChannel channel = notifications.getNotificationChannel(org.linphone.availability.ReceptionController.CHANNEL);
                    require(channel.canBypassDnd(), "Channel not refreshed after DND grant without restart");
                    require(channel.getImportance() == android.app.NotificationManager.IMPORTANCE_HIGH && channel.getSound() != null,
                            "Channel audio or importance altered");
                });
                result.putString("dnd_grant", "PASS: channel created before consent receives DND bypass without process/service restart");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (testRevokedNotifications) {
                require(!getTargetContext().getSystemService(android.app.NotificationManager.class).areNotificationsEnabled(),
                        "Revoke POST_NOTIFICATIONS on the emulator before this test");
                onMain(() -> {
                    org.linphone.availability.ReceptionController reception = org.linphone.availability.ReceptionController.get(getTargetContext());
                    require(reception.configurationProblem().equals(getTargetContext().getString(R.string.reception_need_notifications)),
                            "Missing notification configuration warning");
                    require(!reception.isConnected(), "False green after permission revocation/process restart");
                });
                result.putString("notification_revocation", "PASS: revoked runtime permission detected after process restart");
                finish(Activity.RESULT_OK, result);
                return;
            }
            verifyMinimalPermissionsAndAttachments(result);
            getTargetContext().startActivity(new Intent(getTargetContext(), LinphoneLauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline = SystemClock.elapsedRealtime() + 15000;
            while (SystemClock.elapsedRealtime() < deadline) {
                onMain(() -> core = LinphoneManager.getLcIfManagerNotDestroyedOrNull());
                if (core != null && LinphoneService.isReady()) break;
                SystemClock.sleep(100);
            }
            require(core != null && LinphoneService.isReady(), "Application service did not start");
            SystemClock.sleep(1000); // Let the launcher finish opening the login screen.
            CountDownLatch connected = new CountDownLatch(1);
            onMain(() -> {
                require(core.getProxyConfigList().length == 0, "Refusing to modify a configured account");
                previousEncryption = core.getMediaEncryption();
                previousContact = core.getPrimaryContact();
                core.setMediaEncryption(MediaEncryption.None);
                core.setPrimaryContact("sip:android-test@10.0.2.15");
                testAccount = core.createProxyConfig();
                testAccount.setIdentityAddress(Factory.instance().createAddress("sip:android-test@10.0.2.2"));
                testAccount.setServerAddr("sip:10.0.2.2:15060;transport=udp");
                testAccount.setRegisterEnabled(false);
                org.linphone.availability.ReceptionController.get(getTargetContext()).setEnabled(true);
                core.addProxyConfig(testAccount);
                core.setDefaultProxyConfig(testAccount);
                listener = new CoreListenerStub() {
                    @Override public void onCallStateChanged(Core lc, Call current, Call.State state, String message) {
                        synchronized (states) { states.append(state).append(' '); }
                        android.util.Log.i("LocalCallTest", state + ": " + message);
                        if (state == Call.State.OutgoingInit) call = current;
                        if (state == Call.State.StreamsRunning) connected.countDown();
                        if (state == Call.State.IncomingReceived) {
                            call = current;
                            incoming.countDown();
                        }
                        if (state == Call.State.StreamsRunning && current.getDir() == Call.Dir.Incoming) {
                            incomingMedia.countDown();
                        }
                    }
                };
                core.addListener(listener);
                CallManager.getInstance().inviteAddress(Factory.instance().createAddress(
                        "sip:loopback@10.0.2.2:15060;transport=udp"), false, false, false);
                call = core.getCurrentCall();
            });
            require(connected.await(20, TimeUnit.SECONDS), "No RTP streams started: " + states);
            final float[] bandwidth = new float[2];
            final int[] serviceType = new int[1];
            deadline = SystemClock.elapsedRealtime() + 12000;
            do {
                SystemClock.sleep(500);
                onMain(() -> {
                    require(call != null, "No current call");
                    bandwidth[0] = call.getAudioStats().getUploadBandwidth();
                    bandwidth[1] = call.getAudioStats().getDownloadBandwidth();
                    if (Build.VERSION.SDK_INT >= 29) serviceType[0] = LinphoneService.instance().getForegroundServiceType();
                });
            } while ((bandwidth[0] <= 0 || bandwidth[1] <= 0) && SystemClock.elapsedRealtime() < deadline);
            require(bandwidth[0] > 0 && bandwidth[1] > 0, "No bidirectional RTP: " + bandwidth[0] + "/" + bandwidth[1]);
            if (Build.VERSION.SDK_INT >= 30) require((serviceType[0] & 132) == 132, "Missing phoneCall/microphone service type: " + serviceType[0]);
            else if (Build.VERSION.SDK_INT >= 29) require((serviceType[0] & 4) == 4, "Missing phoneCall service type");
            else require(ReceptionScenarios.isForeground(this), "Service is not foreground");
            result.putString("rtp_kbit_per_second", bandwidth[0] + "/" + bandwidth[1]);
            result.putInt("foreground_service_type", serviceType[0]);
            int[] packets = new int[4];
            onMain(() -> {
                packets[0] = call.getAudioStats().getRtpPacketSent();
                packets[1] = call.getAudioStats().getRtpPacketRecv();
                org.linphone.assistant.AssistantActivity.instance().moveTaskToBack(true);
            });
            SystemClock.sleep(2500);
            onMain(() -> {
                packets[2] = call.getAudioStats().getRtpPacketSent();
                packets[3] = call.getAudioStats().getRtpPacketRecv();
            });
            require(packets[2] > packets[0] && packets[3] > packets[1], "RTP stopped in background");
            result.putString("background_rtp_packets", (packets[2] - packets[0]) + "/" + (packets[3] - packets[1]));
            onMain(() -> call.terminate());
            SystemClock.sleep(1200);
            onMain(() -> require(core.getCallsNb() == 0, "Call not released"));
            if (testIncoming) {
                getTargetContext().startActivity(new Intent(getTargetContext(), LinphoneLauncherActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                SystemClock.sleep(800);
                try (java.net.DatagramSocket control = new java.net.DatagramSocket()) {
                    byte[] command = "START_INCOMING".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    control.send(new java.net.DatagramPacket(command, command.length,
                            java.net.InetAddress.getByName("10.0.2.2"), 15060));
                }
                require(incoming.await(15, TimeUnit.SECONDS), "No incoming call received");
                onMain(() -> call.accept());
                require(incomingMedia.await(15, TimeUnit.SECONDS), "Incoming media did not start");
                SystemClock.sleep(2500);
                onMain(() -> {
                    require(call.getAudioStats().getRtpPacketSent() > 0, "No incoming call RTP sent");
                    require(call.getAudioStats().getRtpPacketRecv() > 0, "No incoming call RTP received");
                    call.terminate();
                });
                SystemClock.sleep(1200);
                onMain(() -> require(core.getCallsNb() == 0, "Incoming call not released"));
                result.putString("incoming", "RTP and hangup passed");
            }
            result.putString("call_states", states.toString());
            result.putString("stream", "Local call(s), bidirectional RTP, background audio packets and hangup passed.\n");
            resultCode = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error) + "\nStates: " + states + "\n");

        } finally {
            if (core != null && previousContact != null) {
                onMain(() -> {
                    if (core.getCallsNb() > 0) core.terminateAllCalls();
                    if (listener != null) core.removeListener(listener);
                    if (testAccount != null) core.removeProxyConfig(testAccount);
                    core.setMediaEncryption(previousEncryption);
                    core.setPrimaryContact(previousContact);
                    core.getConfig().sync();
                });
            }
        }
        finish(resultCode, result);
    }

    private void verifyMinimalPermissionsAndAttachments(Bundle result) throws Exception {
        android.content.Context context = getTargetContext();
        String[] declared = context.getPackageManager().getPackageInfo(context.getPackageName(),
                android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        for (String permission : declared) {
            require(!permission.startsWith("android.permission.READ_MEDIA_")
                    && !permission.equals("android.permission.READ_EXTERNAL_STORAGE")
                    && !permission.equals("android.permission.WRITE_EXTERNAL_STORAGE")
                    && !permission.equals("android.permission.READ_PHONE_STATE")
                    && !permission.equals("android.permission.READ_PHONE_NUMBERS")
                    && !permission.equals("android.permission.CALL_PHONE"), "Unnecessary permission: " + permission);
        }
        java.io.File directory = new java.io.File(context.getCacheDir(), "attachments");
        directory.mkdirs();
        java.io.File original = java.io.File.createTempFile("selected-", ".txt", directory);
        byte[] expected = "Selected document only".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(original.toPath(), expected);
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(context,
                context.getString(R.string.file_provider), original);
        String copied = org.linphone.utils.FileUtils.getFilePath(context, uri);
        require(copied != null, "Selected content URI was not copied");
        require(java.util.Arrays.equals(expected, java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(copied))),
                "Selected attachment bytes differ");
        require(org.linphone.utils.FileUtils.getFilePath(context, null) == null, "Cancelled selection must be harmless");
        original.delete();
        java.io.File copy = new java.io.File(copied);
        copy.delete();
        copy.getParentFile().delete();
        result.putString("privacy", "No library/storage/phone permissions; selected content URI copied successfully");
    }

    private void onMain(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Main-thread test operation failed", failure.get());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
