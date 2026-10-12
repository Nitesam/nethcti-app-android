package org.linphone.compatibility;

import org.linphone.core.Call;
import org.linphone.core.Core;

/** Access the local conference through its calls with Linphone 5. */
public final class ConferenceCompat {
    private ConferenceCompat() {}

    public static int remoteParticipantCount(Core core) {
        for (Call call : core.getCalls()) {
            if (call.getConference() != null) {
                return call.getConference().getParticipantCount();
            }
        }
        return 0;
    }
}
