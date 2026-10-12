package org.linphone.compatibility;

/** Default callbacks for SDK listeners implemented by Android framework subclasses. */
public interface DefaultFriendListListener extends org.linphone.core.FriendListListener {
    @Override
    default void onContactCreated(org.linphone.core.FriendList arg0, org.linphone.core.Friend arg1) {}
    @Override
    default void onContactDeleted(org.linphone.core.FriendList arg0, org.linphone.core.Friend arg1) {}
    @Override
    default void onContactUpdated(org.linphone.core.FriendList arg0, org.linphone.core.Friend arg1, org.linphone.core.Friend arg2) {}
    @Override
    default void onSyncStatusChanged(org.linphone.core.FriendList arg0, org.linphone.core.FriendList.SyncStatus arg1, java.lang.String arg2) {}
    @Override
    default void onPresenceReceived(org.linphone.core.FriendList arg0, org.linphone.core.Friend[] arg1) {}
    @Override
    default void onNewSipAddressDiscovered(org.linphone.core.FriendList arg0, org.linphone.core.Friend arg1, java.lang.String arg2) {}
}
