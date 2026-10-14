package org.linphone.audio;

/** Call-scoped software receive gain. Never changes microphone or Android stream volume. */
public final class VoiceBoost {
    public static final int MAX_DB = 12;
    public interface Output {
        float gain();
        void gain(float value);
        // Persist before changing Core, which also persists its playback gain.
        boolean saveRecovery(float baseline, float applied, float previous);
        void clearRecovery();
    }
    private final Output output;
    private boolean active;
    private float baseline, applied;
    private int boost;

    public VoiceBoost(Output output) { this.output = output; }
    public int get() { return boost; }
    public int maximum() {
        float base = active ? baseline : output.gain();
        return Math.max(0, Math.min(MAX_DB, (int) Math.floor(MAX_DB - base)));
    }
    public boolean set(int requested) {
        // Preserve an independent settings change rather than restoring over it.
        if (active && Math.abs(output.gain() - applied) > 0.01f) reset();
        if (!active) baseline = output.gain();
        if (!Float.isFinite(baseline)) return false;
        int next = Math.max(0, Math.min(maximum(), requested));
        if (next == 0) { reset(); return true; }
        float value = baseline + next;
        if (!output.saveRecovery(baseline, value, output.gain())) return false;
        output.gain(value);
        applied = value;
        boost = next;
        active = true;
        return true;
    }
    public void reset() {
        if (active && Math.abs(output.gain() - applied) <= 0.01f) output.gain(baseline);
        active = false;
        boost = 0;
        output.clearRecovery();
    }
    public void recover(float original, float boosted, float previous) {
        if (Float.isFinite(original) && (Math.abs(output.gain() - boosted) <= 0.01f || Math.abs(output.gain() - previous) <= 0.01f)) output.gain(original);
        output.clearRecovery();
    }
}
