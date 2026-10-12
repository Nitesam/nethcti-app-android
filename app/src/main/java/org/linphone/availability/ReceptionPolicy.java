package org.linphone.availability;

/** Platform-independent decisions, shared by the controller and regression tests. */
public final class ReceptionPolicy {
    private ReceptionPolicy() {}
    public static boolean needsRegistration(boolean enabled, boolean outgoing, boolean calls) {
        return enabled || outgoing || calls;
    }
    public static long retryDelay(int attempt) {
        return Math.min(30000L, 1000L << Math.min(Math.max(attempt, 0), 5));
    }
    public static int ringVolume(int previous, int maximum) {
        return Math.max(previous, (maximum + 1) / 2);
    }
    public static boolean restoreValue(int current, int applied, boolean userChanged) {
        return !userChanged && current == applied;
    }
}
