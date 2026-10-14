package org.linphone.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceBoostTest {
    static class Output implements VoiceBoost.Output {
        float value, savedBase, savedApplied, savedPrevious;
        boolean pending, writable = true;
        public float gain() { return value; }
        public void gain(float v) { value = v; }
        public boolean saveRecovery(float base, float applied, float previous) {
            if (!writable) return false;
            savedBase = base; savedApplied = applied; savedPrevious = previous; pending = true; return true;
        }
        public void clearRecovery() { pending = false; }
    }
    @Test public void boundsAndRestoreOriginalGain() {
        Output o = new Output(); o.value = -3;
        VoiceBoost b = new VoiceBoost(o);
        assertTrue(b.set(30)); assertEquals(12, b.get()); assertEquals(9, o.value, 0.001);
        b.reset(); assertEquals(-3, o.value, 0.001); assertFalse(o.pending);
        assertTrue(b.set(-1)); assertEquals(0, b.get()); assertEquals(-3, o.value, 0.001);
    }
    @Test public void totalGainCannotExceedTwelveDb() {
        Output o = new Output(); o.value = 5;
        VoiceBoost b = new VoiceBoost(o);
        assertEquals(7, b.maximum()); b.set(12); assertEquals(7, b.get()); assertEquals(12, o.value, 0.001);
        b.set(4); assertEquals(9, o.value, 0.001); b.set(0); assertEquals(5, o.value, 0.001);
    }
    @Test public void manualChangeIsNotOverwritten() {
        Output o = new Output(); VoiceBoost b = new VoiceBoost(o); b.set(6);
        o.value = 2; b.reset(); assertEquals(2, o.value, 0.001);
        b.set(3); assertEquals(5, o.value, 0.001);
        o.value = -2; b.set(3); assertEquals(1, o.value, 0.001);
        b.reset(); assertEquals(-2, o.value, 0.001);
    }
    @Test public void crashRecoveryBeforeAndAfterCoreWrite() {
        for (boolean applied : new boolean[] {true, false}) {
            Output o = new Output(); o.value = -2; VoiceBoost b = new VoiceBoost(o);
            b.set(4); b.set(8);
            if (!applied) o.value = o.savedPrevious;
            new VoiceBoost(o).recover(o.savedBase, o.savedApplied, o.savedPrevious);
            assertEquals(-2, o.value, 0.001); assertFalse(o.pending);
        }
    }
    @Test public void crashRecoveryPreservesIndependentSetting() {
        Output o = new Output(); VoiceBoost b = new VoiceBoost(o); b.set(6); o.value = -4;
        new VoiceBoost(o).recover(o.savedBase, o.savedApplied, o.savedPrevious);
        assertEquals(-4, o.value, 0.001);
    }
    @Test public void persistenceFailureAndInvalidBaselineNeverAmplify() {
        Output o = new Output(); o.writable = false; VoiceBoost b = new VoiceBoost(o);
        assertFalse(b.set(12)); assertEquals(0, o.value, 0.001); assertEquals(0, b.get());
        o.value = Float.NaN; assertFalse(b.set(12));
        o.value = 18; assertEquals(0, b.maximum()); b.set(12); assertEquals(18, o.value, 0.001);
    }
}
