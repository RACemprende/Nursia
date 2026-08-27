package com.fir.simulacro;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public final class FirebaseInitializer {
    private FirebaseInitializer() {
    }

    public static boolean ensureInitialized(Context context) {
        if (FirebaseApp.getApps(context).size() > 0) {
            return true;
        }
        if (!FirebaseConfig.isConfigured(context)) {
            return false;
        }

        FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                .setApiKey(FirebaseConfig.getApiKey(context))
                .setApplicationId(FirebaseConfig.getAppId(context))
                .setProjectId(FirebaseConfig.getProjectId(context));

        String storageBucket = FirebaseConfig.getStorageBucket(context);
        if (storageBucket != null && !storageBucket.trim().isEmpty()) {
            builder.setStorageBucket(storageBucket);
        }
        String databaseUrl = FirebaseConfig.getDatabaseUrl(context);
        if (databaseUrl != null && !databaseUrl.trim().isEmpty()) {
            builder.setDatabaseUrl(databaseUrl);
        }

        FirebaseApp.initializeApp(context, builder.build());
        return FirebaseApp.getApps(context).size() > 0;
    }
}
