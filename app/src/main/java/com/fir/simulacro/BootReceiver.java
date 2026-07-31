package com.fir.simulacro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Reprograma la alarma diaria tras un reinicio del dispositivo.
 * Requiere el permiso RECEIVE_BOOT_COMPLETED en el Manifest.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            NotificationScheduler.scheduleDailyReminder(context);
        }
    }
}
