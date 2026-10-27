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
    private static final String KEY_EXAM_DATE = "exam_date";
    private static final String KEY_QUIZ_QUESTION_COUNT = "quiz_question_count";
    private static final String KEY_ACCURACY_THRESHOLD_PERCENT = "accuracy_threshold_percent";
    private static final String KEY_THEORY_COMMUNITIES = "theory_communities";
    private static final String KEY_LEGISLATION_COMMUNITIES = "legislation_communities";
    private static final String KEY_THEORY_COMMUNITIES_SET = "theory_communities_set";
    private static final String KEY_LEGISLATION_COMMUNITIES_SET = "legislation_communities_set";
    private static final String KEY_EIR_SOURCE = "eir_source";
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

    /** Fecha del examen como "yyyy-MM-dd", o null si no hay ninguna. */
    public static String getExamDate(Context context) {
        String value = getPrefs(context).getString(KEY_EXAM_DATE, null);
        return parseExamDay(value) == null ? null : value;
    }

    public static void saveExamDate(Context context, String isoDate) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        if (parseExamDay(isoDate) == null) {
            editor.remove(KEY_EXAM_DATE);
        } else {
            editor.putString(KEY_EXAM_DATE, isoDate);
        }
        editor.apply();
        new AppDatabaseHelper(context).markCloudDirty();
    }

    public static String formatExamDate(int year, int month1to12, int day) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month1to12, day);
    }

    public static String formatExamDateForDisplay(String isoDate) {
        int[] parts = parseExamDay(isoDate);
        if (parts == null) {
            return "";
        }
        return String.format(java.util.Locale.getDefault(), "%02d/%02d/%04d", parts[2], parts[1], parts[0]);
    }

    /** Dias que faltan para el examen (0 = hoy), o null si no hay fecha o ya paso. */
    public static Integer getDaysUntilExam(Context context) {
        int[] parts = parseExamDay(getPrefs(context).getString(KEY_EXAM_DATE, null));
        if (parts == null) {
            return null;
        }
        java.util.Calendar now = java.util.Calendar.getInstance();
        long today = dayNumber(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1,
                now.get(java.util.Calendar.DAY_OF_MONTH));
        long exam = dayNumber(parts[0], parts[1], parts[2]);
        long diff = exam - today;
        return diff < 0 ? null : (int) diff;
    }

    /** Texto para el recordatorio, o null si no hay fecha de examen vigente. */
    public static String getExamCountdownText(Context context) {
        Integer days = getDaysUntilExam(context);
        if (days == null) {
            return null;
        }
        if (days == 0) {
            return "¡El examen es hoy!";
        }
        if (days == 1) {
            return "Solo queda 1 día para el examen.";
        }
        return "Solo quedan " + days + " días para el examen.";
    }

    private static long dayNumber(int year, int month1to12, int day) {
        java.util.Calendar utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month1to12 - 1, day);
        return utc.getTimeInMillis() / 86_400_000L;
    }

    private static int[] parseExamDay(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.trim().split("-");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (year < 2000 || month < 1 || month > 12 || day < 1 || day > 31) {
                return null;
            }
            return new int[]{year, month, day};
        } catch (NumberFormatException e) {
            return null;
        }
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

    public static boolean isEirSource(Context context) {
        return getPrefs(context).getBoolean(KEY_EIR_SOURCE, false);
    }

    public static void saveEirSource(Context context, boolean eir) {
        if (isEirSource(context) == eir) {
            return;
        }
        getPrefs(context).edit().putBoolean(KEY_EIR_SOURCE, eir).apply();
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
            String examDate = getExamDate(context);
            json.put(KEY_EXAM_DATE, examDate == null ? "" : examDate);
            json.put(KEY_EIR_SOURCE, isEirSource(context));
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
        if (json.has(KEY_EXAM_DATE)) {
            saveExamDate(context, json.optString(KEY_EXAM_DATE, ""));
        }
        if (json.has(KEY_EIR_SOURCE)) {
            saveEirSource(context, json.optBoolean(KEY_EIR_SOURCE, false));
        }
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
