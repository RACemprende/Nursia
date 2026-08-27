package com.fir.simulacro;

import android.content.Context;
import android.text.TextUtils;

public final class FirebaseConfig {
    private FirebaseConfig() {
    }

    public static boolean isConfigured(Context context) {
        return !TextUtils.isEmpty(context.getString(R.string.firebase_api_key))
                && !TextUtils.isEmpty(context.getString(R.string.firebase_app_id))
                && !TextUtils.isEmpty(context.getString(R.string.firebase_project_id))
                && !TextUtils.isEmpty(context.getString(R.string.firebase_web_client_id));
    }

    public static String getApiKey(Context context) {
        return context.getString(R.string.firebase_api_key);
    }

    public static String getAppId(Context context) {
        return context.getString(R.string.firebase_app_id);
    }

    public static String getProjectId(Context context) {
        return context.getString(R.string.firebase_project_id);
    }

    public static String getStorageBucket(Context context) {
        return context.getString(R.string.firebase_storage_bucket);
    }

    public static String getDatabaseUrl(Context context) {
        return context.getString(R.string.firebase_database_url);
    }

    public static String getWebClientId(Context context) {
        return context.getString(R.string.firebase_web_client_id);
    }
}
