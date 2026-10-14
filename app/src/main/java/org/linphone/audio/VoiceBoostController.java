package org.linphone.audio;

import android.content.Context;
import android.content.SharedPreferences;
import org.linphone.core.AudioDevice;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;

/** Owned by the Core, so activity recreation/backgrounding does not lose the restore state. */
public final class VoiceBoostController extends CoreListenerStub {
    private final Core core;
    private final VoiceBoost gain;
    private String outputId;
    private Call boostedCall;

    public VoiceBoostController(Context context, Core core) {
        this.core = core;
        SharedPreferences recovery = context.getSharedPreferences("voice_boost_recovery", Context.MODE_PRIVATE);
        gain = new VoiceBoost(new VoiceBoost.Output() {
            // SDK 5.4.127 MSVolume uses 10^(parameter/10) for sample amplitude.
            // Express the UI in amplitude dB (20*log10), verified by the RTP tone test.
            public float gain() { return core.getPlaybackGainDb() * 2f; }
            public void gain(float value) { core.setPlaybackGainDb(value / 2f); core.getConfig().sync(); }
            public boolean saveRecovery(float base, float value, float previous) {
                return recovery.edit().putFloat("base", base).putFloat("applied", value).putFloat("previous", previous).commit();
            }
            public void clearRecovery() { if (recovery.contains("base")) recovery.edit().clear().commit(); }
        });
        if (recovery.contains("base")) gain.recover(recovery.getFloat("base", 0), recovery.getFloat("applied", 0), recovery.getFloat("previous", 0));
        core.addListener(this);
    }
    public boolean available() {
        Call call = core.getCurrentCall();
        return call != null && call.getState() == Call.State.StreamsRunning && !core.isInConference();
    }
    public int getBoostDb() { return gain.get(); }
    public int getMaximumDb() { return gain.maximum(); }
    public boolean setBoostDb(int db) {
        if (!available()) return false;
        boostedCall = core.getCurrentCall();
        AudioDevice device = boostedCall.getOutputAudioDevice();
        outputId = device == null ? null : device.getId();
        return gain.set(db);
    }
    public void reset() { gain.reset(); boostedCall = null; outputId = null; }
    public void close() { reset(); core.removeListener(this); }
    @Override public void onAudioDeviceChanged(Core lc, AudioDevice device) {
        Call current = lc.getCurrentCall();
        AudioDevice output = current == null ? null : current.getOutputAudioDevice();
        String id = output == null ? null : output.getId();
        if (gain.get() != 0 && !java.util.Objects.equals(outputId, id)) reset();
    }
    @Override public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
        if (lc.getCallsNb() == 0 || (boostedCall != null &&
                (boostedCall != lc.getCurrentCall() || (boostedCall == call && state != Call.State.StreamsRunning)))) reset();
    }
}
