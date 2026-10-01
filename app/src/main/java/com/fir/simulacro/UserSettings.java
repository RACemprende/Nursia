package com.fir.simulacro;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public final class UserSettings {

    private static final String PREFS_NAME = "fir_user_settings";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_QUIZ_QUESTION_COUNT = "quiz_question_count";
    private static final String KEY_ACCURACY_THRESHOLD_PERCENT = "accuracy_threshold_percent";
    private static final String KEY_THEORY_COMMUNITIES = "theory_communities";
    private static final String KEY_LEGISLATION_COMMUNITIES = "legislation_communities";
    private static final String KEY_THEORY_COMMUNITIES_SET = "theory_communities_set";
    private static final String KEY_LEGISLATION_COMMUNITIES_SET = "legislation_communities_set";
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

    public static Set<String> getTheoryCommunitiesFilter(Context context) {
        return getCommunitiesFilter(context, KEY_THEORY_COMMUNITIES_SET, KEY_THEORY_COMMUNITIES);
    }

    public static Set<String> getLegislationCommunitiesFilter(Context context) {
        return getCommunitiesFilter(context, KEY_LEGISLATION_COMMUNITIES_SET, KEY_LEGISLATION_COMMUNITIES);
    }

    public static void saveTheoryCommunities(Context context, Set<String> communities) {
        saveCommunitiesFilter(context, KEY_THEORY_COMMUNITIES_SET, KEY_THEORY_COMMUNITIES, communities);
    }

    public static void saveLegislationCommunities(Context context, Set<String> communities) {
        saveCommunitiesFilter(context, KEY_LEGISLATION_COMMUNITIES_SET, KEY_LEGISLATION_COMMUNITIES, communities);
    }

    private static Set<String> getCommunitiesFilter(Context context, String flagKey, String setKey) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.getBoolean(flagKey, false)) {
            return null;
        }
        Set<String> stored = prefs.getStringSet(setKey, null);
        if (stored == null) {
            return new HashSet<>();
        }
        return new HashSet<>(stored);
    }

    private static void saveCommunitiesFilter(Context context, String flagKey, String setKey, Set<String> communities) {
        Set<String> normalized = communities == null ? new HashSet<>() : new HashSet<>(communities);
        getPrefs(context)
                .edit()
                .putStringSet(setKey, normalized)
                .putBoolean(flagKey, true)
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
            putCommunitiesIfPresent(json, KEY_THEORY_COMMUNITIES, getTheoryCommunitiesFilter(context));
            putCommunitiesIfPresent(json, KEY_LEGISLATION_COMMUNITIES, getLegislationCommunitiesFilter(context));
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
        Set<String> theoryCommunities = readCommunitiesArray(json, KEY_THEORY_COMMUNITIES);
        if (theoryCommunities != null) {
            saveTheoryCommunities(context, theoryCommunities);
        }
        Set<String> legislationCommunities = readCommunitiesArray(json, KEY_LEGISLATION_COMMUNITIES);
        if (legislationCommunities != null) {
            saveLegislationCommunities(context, legislationCommunities);
        }
    }

    private static void putCommunitiesIfPresent(JSONObject json, String key, Set<String> communities) throws org.json.JSONException {
        if (communities == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (String value : communities) {
            if (value != null) {
                array.put(value);
            }
        }
        json.put(key, array);
    }

    private static Set<String> readCommunitiesArray(JSONObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        JSONArray array = json.optJSONArray(key);
        if (array == null) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, null);
            if (value != null && !value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
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
