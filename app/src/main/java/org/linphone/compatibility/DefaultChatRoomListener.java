package org.linphone.compatibility;

/** Default callbacks for SDK listeners implemented by Android framework subclasses. */
public interface DefaultChatRoomListener extends org.linphone.core.ChatRoomListener {
    @Override
    default void onIsComposingReceived(org.linphone.core.ChatRoom arg0, org.linphone.core.Address arg1, boolean arg2) {}
    @Override
    default void onMessageReceived(org.linphone.core.ChatRoom arg0, org.linphone.core.ChatMessage arg1) {}
    @Override
    default void onMessagesReceived(org.linphone.core.ChatRoom arg0, org.linphone.core.ChatMessage[] arg1) {}
    @Override
    default void onNewEvent(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onNewEvents(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog[] arg1) {}
    @Override
    default void onChatMessageReceived(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onChatMessagesReceived(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog[] arg1) {}
    @Override
    default void onChatMessageSending(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onChatMessageSent(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onParticipantAdded(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onParticipantRemoved(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onParticipantAdminStatusChanged(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onStateChanged(org.linphone.core.ChatRoom arg0, org.linphone.core.ChatRoom.State arg1) {}
    @Override
    default void onSecurityEvent(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onSubjectChanged(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onUndecryptableMessageReceived(org.linphone.core.ChatRoom arg0, org.linphone.core.ChatMessage arg1) {}
    @Override
    default void onParticipantDeviceAdded(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onParticipantDeviceRemoved(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onParticipantDeviceStateChanged(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1, org.linphone.core.ParticipantDevice.State arg2) {}
    @Override
    default void onParticipantDeviceMediaAvailabilityChanged(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onConferenceJoined(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onConferenceLeft(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onEphemeralEvent(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onEphemeralMessageTimerStarted(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onEphemeralMessageDeleted(org.linphone.core.ChatRoom arg0, org.linphone.core.EventLog arg1) {}
    @Override
    default void onConferenceAddressGeneration(org.linphone.core.ChatRoom arg0) {}
    @Override
    default void onParticipantRegistrationSubscriptionRequested(org.linphone.core.ChatRoom arg0, org.linphone.core.Address arg1) {}
    @Override
    default void onParticipantRegistrationUnsubscriptionRequested(org.linphone.core.ChatRoom arg0, org.linphone.core.Address arg1) {}
    @Override
    default void onChatMessageShouldBeStored(org.linphone.core.ChatRoom arg0, org.linphone.core.ChatMessage arg1) {}
    @Override
    default void onChatMessageParticipantImdnStateChanged(org.linphone.core.ChatRoom arg0, org.linphone.core.ChatMessage arg1, org.linphone.core.ParticipantImdnState arg2) {}
    @Override
    default void onChatRoomRead(org.linphone.core.ChatRoom arg0) {}
    @Override
    default void onNewMessageReaction(org.linphone.core.ChatRoom arg0, org.linphone.core.ChatMessage arg1, org.linphone.core.ChatMessageReaction arg2) {}
    @Override
    default void onFullStateReceived(org.linphone.core.ChatRoom arg0) {}
}
