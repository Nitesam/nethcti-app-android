package org.linphone.audio;

import android.app.Activity;
import android.media.AudioManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.linphone.R;
import org.linphone.core.AudioDevice;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;

public final class VoiceBoostDialog {
    private VoiceBoostDialog() { }
    public static AlertDialog show(Activity activity, Core core, VoiceBoostController boost) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, 0);
        TextView level = new TextView(activity);
        level.setId(R.id.voice_boost_level);
        content.addView(level);
        SeekBar slider = new SeekBar(activity);
        slider.setId(R.id.voice_boost_slider);
        slider.setContentDescription(activity.getString(R.string.voice_boost));
        content.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = new TextView(activity);
        hint.setText(R.string.voice_boost_hint);
        content.addView(hint);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.voice_boost)
                .setView(content).setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.voice_boost_reset, null).create();
        Runnable refresh = () -> {
            if (!boost.available()) { dialog.dismiss(); return; }
            slider.setMax(boost.getMaximumDb());
            slider.setProgress(boost.getBoostDb());
            level.setText(activity.getString(R.string.voice_boost_level, boost.getBoostDb()));
        };
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (!boost.setBoostDb(progress)) {
                    android.widget.Toast.makeText(activity, R.string.voice_boost_unavailable, android.widget.Toast.LENGTH_SHORT).show();
                }
                refresh.run();
            }
        });
        CoreListenerStub listener = new CoreListenerStub() {
            @Override public void onCallStateChanged(Core lc, Call call, Call.State state, String message) { refresh.run(); }
            @Override public void onAudioDeviceChanged(Core lc, AudioDevice device) { refresh.run(); }
        };
        core.addListener(listener);
        dialog.setOnDismissListener(ignored -> core.removeListener(listener));
        dialog.show();
        dialog.getWindow().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(ignored -> { boost.reset(); refresh.run(); });
        refresh.run();
        return dialog;
    }
}
