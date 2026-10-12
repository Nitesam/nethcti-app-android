package org.linphone.receivers;

/*
BootReceiver.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.compatibility.Compatibility;
import org.linphone.settings.LinphonePreferences;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        LinphonePreferences.instance().setContext(context);
        if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
            android.util.Log.d(
                    "LinphoneBootReceiver",
                    "Device is shutting down, destroying Core to unregister");
            LinphoneManager.destroy();
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            boolean autostart = org.linphone.availability.ReceptionController.get(context).isEnabled();
            android.util.Log.i(
                    "LinphoneBootReceiver", "Device is starting, auto_start is " + autostart);
            if (autostart && !LinphoneService.isReady()) {
                // Android 15+ forbids starting a phone-call service at boot.
                if (android.os.Build.VERSION.SDK_INT >= 35) {
                    showRestartNotification(context);
                    return;
                }
                Intent lLinphoneServiceIntent = new Intent(Intent.ACTION_MAIN);
                lLinphoneServiceIntent.setClass(context, LinphoneService.class);
                lLinphoneServiceIntent.putExtra("ForceStartForeground", true);
                lLinphoneServiceIntent.putExtra("startFromBootReceiver", true);
                Compatibility.startService(context, lLinphoneServiceIntent);
            }
        }
    }

    private void showRestartNotification(Context context) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        String channel = "restart_after_boot";
        manager.createNotificationChannel(new android.app.NotificationChannel(channel,
                context.getString(org.linphone.R.string.app_name), android.app.NotificationManager.IMPORTANCE_DEFAULT));
        android.app.PendingIntent open = android.app.PendingIntent.getActivity(context, 4,
                new Intent(context, org.linphone.LinphoneLauncherActivity.class),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(4, new androidx.core.app.NotificationCompat.Builder(context, channel)
                .setSmallIcon(org.linphone.R.drawable.ic_nethesis_24)
                .setContentTitle(context.getString(org.linphone.R.string.app_name))
                .setContentText(context.getString(org.linphone.R.string.restart_after_boot))
                .setContentIntent(open).setAutoCancel(true).build());
    }
}
