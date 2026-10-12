package org.linphone.availability;

import android.content.*;
import android.media.AudioManager;
import org.linphone.core.Core;

/** Owns temporary audio changes; preserves user edits and recovers a saved snapshot on restart. */
final class RingAudioOverride {
    private final AudioManager audio;
    private final SharedPreferences saved;
    private boolean applying;
    RingAudioOverride(Context context) {
        audio = context.getSystemService(AudioManager.class);
        saved = context.getSharedPreferences("ring_audio_restore", Context.MODE_PRIVATE);
        restore();
        IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        androidx.core.content.ContextCompat.registerReceiver(context, new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (applying || !saved.getBoolean("active", false)) return;
                SharedPreferences.Editor edit = saved.edit();
                if (audio.getRingerMode() != saved.getInt("appliedMode", -1)) edit.putBoolean("modeChanged", true);
                if (audio.getStreamVolume(AudioManager.STREAM_RING) != saved.getInt("appliedVolume", -1)) edit.putBoolean("volumeChanged", true);
                edit.apply();
            }
        }, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
    }
    void begin() {
        if (saved.getBoolean("active", false)) return;
        int mode = audio.getRingerMode();
        int volume = audio.getStreamVolume(AudioManager.STREAM_RING);
        int target = ReceptionPolicy.ringVolume(volume, audio.getStreamMaxVolume(AudioManager.STREAM_RING));
        saved.edit().clear().putBoolean("active", true).putInt("mode", mode).putInt("volume", volume)
                .putInt("appliedMode", AudioManager.RINGER_MODE_NORMAL).putInt("appliedVolume", target).commit();
        applying = true;
        try {
            audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            audio.setStreamVolume(AudioManager.STREAM_RING, target, 0);
        } catch (SecurityException e) {
            android.util.Log.w("Reception", "Audio override blocked by system policy");
        } finally { applying = false; }
    }
    void finishIfNoRinging(Core core) {
        for (org.linphone.core.Call call : core.getCalls()) {
            if (ReceptionController.isRinging(call)) return;
        }
        restore();
    }
    void restore() {
        if (!saved.getBoolean("active", false)) return;
        applying = true;
        try {
            boolean volume = ReceptionPolicy.restoreValue(audio.getStreamVolume(AudioManager.STREAM_RING), saved.getInt("appliedVolume", -1), saved.getBoolean("volumeChanged", false));
            boolean mode = ReceptionPolicy.restoreValue(audio.getRingerMode(), saved.getInt("appliedMode", -1), saved.getBoolean("modeChanged", false));
            // Mode and ring volume are coupled by Android: restoring silent mode also
            // zeros a volume the user may just have selected. Restore the pair only
            // while both still belong to this override.
            if (volume && mode) {
                audio.setStreamVolume(AudioManager.STREAM_RING, saved.getInt("volume", 0), 0);
                audio.setRingerMode(saved.getInt("mode", AudioManager.RINGER_MODE_NORMAL));
            }
        } catch (SecurityException e) {
            android.util.Log.w("Reception", "Audio restore blocked by system policy");
        } finally { saved.edit().clear().commit(); applying = false; }
    }
}
