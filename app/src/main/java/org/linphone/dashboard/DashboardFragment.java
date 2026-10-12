package org.linphone.dashboard;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;

import java.util.Arrays;
import java.util.List;

public class DashboardFragment extends Fragment implements ContactsUpdatedListener {

    private View card1, card2;
    private org.linphone.availability.ReceptionController reception;
    private androidx.appcompat.widget.SwitchCompat receptionToggle;
    private android.widget.TextView receptionStatus;
    private boolean updatingReception;
    private final Runnable receptionObserver = () -> {
        if (receptionToggle == null) return;
        updatingReception = true;
        receptionToggle.setChecked(reception.isEnabled());
        receptionStatus.setText(reception.status());
        updatingReception = false;
    };
    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.dashboard, container, false);

        setButtonListener((MaterialButton) view.findViewById(R.id.dialer_btn));
        setButtonListener((MaterialButton) view.findViewById(R.id.history_btn));
        setButtonListener((MaterialButton) view.findViewById(R.id.contacts_btn));
        setButtonListener((MaterialButton) view.findViewById(R.id.presence_btn));

        reception = org.linphone.availability.ReceptionController.get(requireContext());
        receptionToggle = view.findViewById(R.id.reception_toggle);
        receptionStatus = view.findViewById(R.id.reception_status);
        receptionToggle.setOnCheckedChangeListener((button, checked) -> {
            if (updatingReception) return;
            if (checked && !requireContext().getSharedPreferences("reception", 0).getBoolean("consent", false)) {
                receptionObserver.run();
                new android.app.AlertDialog.Builder(requireContext()).setTitle(R.string.reception_title)
                    .setMessage(R.string.reception_consent)
                    .setPositiveButton(android.R.string.ok, (d,w) -> {
                        requireContext().getSharedPreferences("reception", 0).edit().putBoolean("consent", true).apply();
                        reception.setEnabled(true);
                        receptionObserver.run();
                        reception.showSetup(requireActivity());
                    }).setNegativeButton(android.R.string.cancel, null).show();
            } else { reception.setEnabled(checked); receptionObserver.run(); }
        });
        view.findViewById(R.id.reception_setup).setOnClickListener(v -> reception.showSetup(requireActivity()));
        reception.addObserver(receptionObserver);
        card1 = view.findViewById(R.id.card1);
        card2 = view.findViewById(R.id.card2);

        ContactsManager.getInstance().addContactsListener(this);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        /* Used to make Linphone logic of fragment transactions */
        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().setCurrentFragment(FragmentsAvailable.DASHBOARD);

            LinphoneActivity.instance().hideTabBar();
            LinphoneActivity.instance().hideTopBar();
        }
        receptionObserver.run();
        updateLastCalls();
    }

    @Override
    public void onContactsUpdated() {
        receptionObserver.run();
        updateLastCalls();
    }

    @Override public void onDestroyView() {
        reception.removeObserver(receptionObserver);
        receptionToggle = null;
        receptionStatus = null;
        super.onDestroyView();
    }

    private void updateLastCalls(){
        List<CallLog> calls = Arrays.asList(LinphoneManager.getLc().getCallLogs());
        /* Manage card visibility */
        switch (calls.size()) {
            case 0:
                card1.setVisibility(View.GONE);
                card2.setVisibility(View.GONE);
                setCardListeners(card1, null);
                setCardListeners(card2, null);
                break;
            case 1:
                card1.setVisibility(View.VISIBLE);
                card2.setVisibility(View.GONE);
                updateCardWithContactInfo(card1, calls.get(0));
                setCardListeners(card1, calls.get(0));
                setCardListeners(card2, null);
                break;
            default:
                card1.setVisibility(View.VISIBLE);
                card2.setVisibility(View.VISIBLE);
                updateCardWithContactInfo(card1, calls.get(0));
                updateCardWithContactInfo(card2, calls.get(1));
                setCardListeners(card1, calls.get(0));
                setCardListeners(card2, calls.get(1));
        }
    }

    private void setCardListeners(View card, final CallLog call) {
        if (call == null) {
            card.setOnClickListener(null);
            card.findViewById(R.id.call).setOnClickListener(null);
        } else {
            /* Set Card Click Listener */
            card.setOnClickListener( v -> {
                Address address;
                if (call.getDir() == Call.Dir.Incoming) {
                    address = call.getFromAddress();
                } else {
                    address = call.getToAddress();
                }

                LinphoneActivity.instance().displayHistoryDetail(address.asString(), call);
            });
            /* Set Call Button Listener */
            card.findViewById(R.id.call)
                    .setOnClickListener(v -> {
                        Address address;
                        if (call.getDir() == Call.Dir.Incoming) {
                            address = call.getFromAddress();
                        } else {
                            address = call.getToAddress();
                        }
                        int accountIndex =
                                LinphonePreferences.instance().getDefaultAccountIndex();
                        address.setDomain(
                                LinphonePreferences.instance()
                                        .getAccountDomain(accountIndex));
                        LinphoneActivity.instance()
                                .setAddresGoToDialerAndCall(
                                        address.asStringUriOnly(),
                                        address.getDisplayName());
                    });
        }
    }

    private void updateCardWithContactInfo(View card1, CallLog callLog) {
        View avatar = card1.findViewById(R.id.avatarImg);
        AppCompatTextView contactName = card1.findViewById(R.id.contact_name_tw);
        //AppCompatTextView contactAddress = card1.findViewById(R.id.contact_address_tw);
        ImageView callStateIcon = card1.findViewById(R.id.call_state_icon);

        Address address;
        if (callLog.getDir() == Call.Dir.Incoming) {
            address = callLog.getFromAddress();
            if (callLog.getStatus() == Call.Status.Missed) {
                callStateIcon.setImageResource(R.drawable.ic_call_missed);
            } else {
                callStateIcon.setImageResource(R.drawable.ic_call_in);
            }
        } else {
            address = callLog.getToAddress();
            callStateIcon.setImageResource(R.drawable.ic_call_out);
        }
        LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(address);
        String displayName = null;
        final String sipUri = (address != null) ? address.asString() : "";

        if (c != null) {
            displayName = c.getFullName();
            ContactAvatar.displayAvatar(c, avatar);
        }
        if (displayName == null) {
            contactName.setText(LinphoneUtils.getAddressDisplayName(sipUri));
            ContactAvatar.displayAvatar(LinphoneUtils.getAddressDisplayName(sipUri), avatar);
        } else {
            contactName.setText(displayName);
            ContactAvatar.displayAvatar(displayName, avatar);
        }
        //contactAddress.setText(LinphoneUtils.getDisplayableAddress(address));
    }

    @SuppressLint("NonConstantResourceId")
    private void setButtonListener(final MaterialButton button) {
        button.setOnClickListener(
                view -> {
                    if (button.getId() == R.id.dialer_btn) {
                        LinphoneActivity.instance().displayDialer();
                    } else if (button.getId() == R.id.history_btn) {
                        LinphoneActivity.instance().displayHistory();
                    } else if (button.getId() == R.id.contacts_btn) {
                        LinphoneActivity.instance().displayContacts(false);
                    } else if (button.getId() == R.id.presence_btn) {
                        LinphoneActivity.instance().displayUsersByPresence();
                    }
                });
    }
}
