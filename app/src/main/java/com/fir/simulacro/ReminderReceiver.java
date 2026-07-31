package com.fir.simulacro;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean forceTest = intent != null && intent.getBooleanExtra(NotificationScheduler.EXTRA_FORCE_TEST, false);
        // Verificar si ya hizo un simulacro hoy — si es así no molestar
        AppDatabaseHelper db = new AppDatabaseHelper(context);
        boolean doneToday = db.hasCompletedSimulacroToday();
        int streak = db.getConsecutiveDaysStreak();
        db.close();

        if (doneToday && !forceTest) {
            // Ya hizo el simulacro hoy: no notificar, solo reprogramar para mañana
            NotificationScheduler.scheduleDailyReminder(context);
            return;
        }

        // Construir mensaje según la racha actual
        String title = forceTest ? "🔔 Prueba de recordatorio OPE SESPA" : "🔥 ¡No pierdas tu racha OPE SESPA!";
        String message;
        if (forceTest) {
            message = "Notificación de prueba enviada correctamente. Racha actual: " + streak + " días.";
        } else if (streak == 0) {
            message = "¡Empieza tu racha hoy! Haz tu simulacro diario.";
        } else if (streak == 1) {
            message = "Llevas 1 día de racha. ¡No la rompas hoy!";
        } else {
            message = "Llevas " + streak + " días seguidos. ¡Haz tu simulacro para mantener la racha!";
        }

        // Intent para abrir la app al tocar la notificación
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, openApp, piFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, builder.build());
        }

        // Reprogramar para el día siguiente
        NotificationScheduler.scheduleDailyReminder(context);
    }
}
