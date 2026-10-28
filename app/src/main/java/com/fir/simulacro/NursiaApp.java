package com.fir.simulacro;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.Calendar;

public class NursiaApp extends Application {

    // Ultimo dia de uso: pasada esta fecha en el movil, la app queda bloqueada.
    private static final int LAST_DAY_YEAR = 2026;
    private static final int LAST_DAY_MONTH = Calendar.NOVEMBER;
    private static final int LAST_DAY_OF_MONTH = 8;
    private static final String CONTACT_EMAIL = "opotestsplaystore@gmail.com";

    static boolean isExpired() {
        Calendar limit = Calendar.getInstance();
        limit.clear();
        limit.set(LAST_DAY_YEAR, LAST_DAY_MONTH, LAST_DAY_OF_MONTH, 23, 59, 59);
        limit.set(Calendar.MILLISECOND, 999);
        return System.currentTimeMillis() > limit.getTimeInMillis();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                if (isExpired()) {
                    lock(activity);
                }
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private static void lock(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        disableAll(content);
        if (content.getTag(R.id.questionText) != null) {
            return;
        }
        content.setTag(R.id.questionText, Boolean.TRUE);
        content.getViewTreeObserver().addOnGlobalLayoutListener(() -> disableAll(content));

        if (activity instanceof androidx.appcompat.app.AppCompatActivity && !activity.isFinishing()) {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setMessage("¡¡¡Muchas gracias por participar en la prueba de Nursia!!!\n\nSi te ha gustado y quieres seguir usándola, por favor contacta con " + CONTACT_EMAIL)
                    .setCancelable(false)
                    .setPositiveButton("Copiar correo", null)
                    .create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Correo de contacto", CONTACT_EMAIL));
                    Toast.makeText(activity, "Correo copiado", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private static void disableAll(View view) {
        view.setEnabled(false);
        view.setClickable(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disableAll(group.getChildAt(i));
            }
        }
    }
}
