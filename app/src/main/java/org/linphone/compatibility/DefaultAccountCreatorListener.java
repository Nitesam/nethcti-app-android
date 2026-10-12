package org.linphone.compatibility;

/** Default callbacks for SDK listeners implemented by Android framework subclasses. */
public interface DefaultAccountCreatorListener extends org.linphone.core.AccountCreatorListener {
    @Override
    default void onCreateAccount(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onIsAccountExist(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onActivateAccount(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onSendToken(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onAccountCreationRequestToken(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onAccountCreationTokenUsingRequestToken(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onIsAccountActivated(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onLinkAccount(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onActivateAlias(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onIsAliasUsed(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onIsAccountLinked(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onRecoverAccount(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onUpdateAccount(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
    @Override
    default void onLoginLinphoneAccount(org.linphone.core.AccountCreator arg0, org.linphone.core.AccountCreator.Status arg1, java.lang.String arg2) {}
}
