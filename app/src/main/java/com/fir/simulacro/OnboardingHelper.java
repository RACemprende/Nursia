package com.fir.simulacro;

import android.content.Context;
import android.content.SharedPreferences;

public class OnboardingHelper {
    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_completed";

    public static boolean isOnboardingNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public static void markOnboardingDone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
    }

    public static void resetOnboarding(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, false).apply();
    }
}