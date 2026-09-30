package com.fir.simulacro;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class CrashLogger {
    private static final String CRASH_FILENAME = "nursia_last_crash.txt";
    private static boolean installed = false;

    private CrashLogger() {}

    static void install(Context appContext) {
        if (installed) return;
        installed = true;
        Context ctx = appContext.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrash(ctx, thread, throwable);
            } catch (Throwable ignored) {}
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private static void writeCrash(Context ctx, Thread thread, Throwable throwable) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== Nursia crash ===");
        pw.println("Fecha: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        pw.println("Thread: " + (thread == null ? "?" : thread.getName()));
        pw.println("Android: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")");
        pw.println("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        pw.println();
        throwable.printStackTrace(pw);
        pw.flush();
        byte[] bytes = sw.toString().getBytes("UTF-8");

        File internal = new File(ctx.getFilesDir(), CRASH_FILENAME);
        try (FileOutputStream fos = new FileOutputStream(internal)) {
            fos.write(bytes);
        }

        try {
            File external = ctx.getExternalFilesDir(null);
            if (external != null) {
                File externalFile = new File(external, CRASH_FILENAME);
                try (FileOutputStream fos = new FileOutputStream(externalFile)) {
                    fos.write(bytes);
                }
            }
        } catch (Throwable ignored) {}

        android.util.Log.e("Nursia", sw.toString());
    }

    static boolean hasCrash(Context ctx) {
        return new File(ctx.getFilesDir(), CRASH_FILENAME).exists();
    }

    static String readCrash(Context ctx) {
        File f = new File(ctx.getFilesDir(), CRASH_FILENAME);
        if (!f.exists()) return null;
        try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int read = fis.read(buf);
            return new String(buf, 0, read, "UTF-8");
        } catch (Exception e) {
            return "No se pudo leer el crash: " + e.getMessage();
        }
    }

    static void clearCrash(Context ctx) {
        File f = new File(ctx.getFilesDir(), CRASH_FILENAME);
        if (f.exists()) f.delete();
    }

    static void showIfPresent(final android.app.Activity activity) {
        if (!hasCrash(activity)) return;
        final String content = readCrash(activity);
        if (content == null || content.isEmpty()) {
            clearCrash(activity);
            return;
        }

        try {
            android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
            android.widget.TextView tv = new android.widget.TextView(activity);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad, pad, pad);
            tv.setTextIsSelectable(true);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setTextSize(12f);
            tv.setText(content);
            scroll.addView(tv);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("Último crash detectado")
                    .setView(scroll)
                    .setCancelable(false)
                    .setPositiveButton("Copiar", (d, w) -> {
                        try {
                            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("Nursia crash", content));
                                Toast.makeText(activity, "Copiado al portapapeles", Toast.LENGTH_LONG).show();
                            }
                        } catch (Throwable ignored) {}
                    })
                    .setNeutralButton("Compartir", (d, w) -> {
                        try {
                            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
                            i.setType("text/plain");
                            i.putExtra(android.content.Intent.EXTRA_SUBJECT, "Nursia crash log");
                            i.putExtra(android.content.Intent.EXTRA_TEXT, content);
                            activity.startActivity(android.content.Intent.createChooser(i, "Compartir crash log"));
                        } catch (Throwable ignored) {}
                    })
                    .setNegativeButton("Borrar y salir", (d, w) -> {
                        try {
                            clearCrash(activity);
                        } catch (Throwable ignored) {}
                        activity.finish();
                    })
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        } catch (Throwable dialogFailure) {
            android.util.Log.e("Nursia", "Fallo mostrando el diálogo de crash", dialogFailure);
            try {
                Toast.makeText(activity,
                        "Crash previo. Copia el fichero:\n" +
                                activity.getFilesDir() + "/" + CRASH_FILENAME +
                                "\no busca en Android/data/com.fir.simulacro/files/",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {}
        }
    }
}
