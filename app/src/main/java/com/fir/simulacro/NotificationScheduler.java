package com.fir.simulacro;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class NotificationScheduler {

    private static final String TAG = "NotificationScheduler";
    public static final String CHANNEL_ID = "fir_reminder_channel";
    static final String ACTION_REMINDER = "com.fir.simulacro.DAILY_REMINDER";
    static final String EXTRA_FORCE_TEST = "extra_force_test";

    public static void createNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorio diario OPE SESPA",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Recuerda hacer tu simulacro diario OPE SESPA para no perder la racha");
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /** Programa (o reprograma) la alarma diaria a la hora configurada por el usuario. */
    public static void scheduleDailyReminder(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(ctx);
        int reminderHour = UserSettings.getReminderHour(ctx);
        int reminderMinute = UserSettings.getReminderMinute(ctx);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, reminderHour);
        cal.set(Calendar.MINUTE, reminderMinute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Si ya pasó la hora de hoy, programa para mañana
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarms not allowed; skipping daily reminder scheduling.");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to schedule daily reminder due to alarm permission restrictions.", e);
        }
    }

    public static void cancelReminder(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(buildPendingIntent(ctx));
    }

    public static void triggerTestReminderNow(Context ctx) {
        Intent intent = new Intent(ctx, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.putExtra(EXTRA_FORCE_TEST, true);
        ctx.sendBroadcast(intent);
    }

    private static PendingIntent buildPendingIntent(Context ctx) {
        Intent intent = new Intent(ctx, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(ctx, 0, intent, flags);
    }
}
