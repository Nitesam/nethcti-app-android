package org.linphone.utils;

/*
ThemableActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import org.linphone.R;
import org.linphone.settings.LinphonePreferences;

public class ThemableActivity extends FragmentActivity {

    private androidx.activity.OnBackPressedCallback backCallback;

    protected void onBackRequested() {
        backCallback.setEnabled(false);
        try {
            getOnBackPressedDispatcher().onBackPressed();
        } finally {
            backCallback.setEnabled(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (LinphonePreferences.instance().isDarkModeEnabled()) {
            setTheme(R.style.LinphoneStyleDark);
        }

        super.onCreate(savedInstanceState);
        backCallback = new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                ThemableActivity.this.onBackRequested();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        // Keep legacy layouts clear of system bars under enforced edge-to-edge.
        android.view.View content = findViewById(android.R.id.content);
        final int left = content.getPaddingLeft(), top = content.getPaddingTop();
        final int right = content.getPaddingRight(), bottom = content.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(content);
    }
}
