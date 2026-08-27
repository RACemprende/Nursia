package com.fir.simulacro;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public final class UserSettings {

    private static final String PREFS_NAME = "fir_user_settings";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_QUIZ_QUESTION_COUNT = "quiz_question_count";
    private static final String KEY_ACCURACY_THRESHOLD_PERCENT = "accuracy_threshold_percent";
    private static final int[] QUIZ_QUESTION_COUNT_OPTIONS = {10, 20, 30, 40, 50};

    private static final int DEFAULT_REMINDER_HOUR = 20;
    private static final int DEFAULT_REMINDER_MINUTE = 0;
    private static final int DEFAULT_QUIZ_QUESTION_COUNT = 10;
    private static final int DEFAULT_ACCURACY_THRESHOLD_PERCENT = 70;

    private UserSettings() {
    }

    public static int getReminderHour(Context context) {
        return clamp(getPrefs(context).getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR), 0, 23);
    }

    public static int getReminderMinute(Context context) {
        return clamp(getPrefs(context).getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE), 0, 59);
    }

    public static int getQuizQuestionCount(Context context) {
        int value = getPrefs(context).getInt(KEY_QUIZ_QUESTION_COUNT, DEFAULT_QUIZ_QUESTION_COUNT);
        for (int option : QUIZ_QUESTION_COUNT_OPTIONS) {
            if (option == value) {
                return option;
            }
        }
        return DEFAULT_QUIZ_QUESTION_COUNT;
    }

    public static int getAccuracyThresholdPercent(Context context) {
        return clamp(
                getPrefs(context).getInt(KEY_ACCURACY_THRESHOLD_PERCENT, DEFAULT_ACCURACY_THRESHOLD_PERCENT),
                0,
                100
        );
    }

    public static void saveReminderTime(Context context, int hour, int minute) {
        getPrefs(context)
                .edit()
                .putInt(KEY_REMINDER_HOUR, clamp(hour, 0, 23))
                .putInt(KEY_REMINDER_MINUTE, clamp(minute, 0, 59))
                .apply();
        new AppDatabaseHelper(context).markCloudDirty();
    }

    public static void saveQuizQuestionCount(Context context, int count) {
        int normalized = DEFAULT_QUIZ_QUESTION_COUNT;
        for (int option : QUIZ_QUESTION_COUNT_OPTIONS) {
            if (option == count) {
                normalized = option;
                break;
            }
        }
        getPrefs(context)
                .edit()
                .putInt(KEY_QUIZ_QUESTION_COUNT, normalized)
                .apply();
        new AppDatabaseHelper(context).markCloudDirty();
    }

    public static void saveAccuracyThresholdPercent(Context context, int thresholdPercent) {
        getPrefs(context)
                .edit()
                .putInt(KEY_ACCURACY_THRESHOLD_PERCENT, clamp(thresholdPercent, 0, 100))
                .apply();
        new AppDatabaseHelper(context).markCloudDirty();
    }

    public static JSONObject exportSnapshot(Context context) {
        JSONObject json = new JSONObject();
        try {
            json.put(KEY_REMINDER_HOUR, getReminderHour(context));
            json.put(KEY_REMINDER_MINUTE, getReminderMinute(context));
            json.put(KEY_QUIZ_QUESTION_COUNT, getQuizQuestionCount(context));
            json.put(KEY_ACCURACY_THRESHOLD_PERCENT, getAccuracyThresholdPercent(context));
        } catch (Exception ignored) {
        }
        return json;
    }

    public static void importSnapshot(Context context, JSONObject json) {
        if (json == null) {
            return;
        }
        saveReminderTime(context, json.optInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR), json.optInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE));
        saveQuizQuestionCount(context, json.optInt(KEY_QUIZ_QUESTION_COUNT, DEFAULT_QUIZ_QUESTION_COUNT));
        saveAccuracyThresholdPercent(context, json.optInt(KEY_ACCURACY_THRESHOLD_PERCENT, DEFAULT_ACCURACY_THRESHOLD_PERCENT));
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int[] getQuizQuestionCountOptions() {
        return QUIZ_QUESTION_COUNT_OPTIONS.clone();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
