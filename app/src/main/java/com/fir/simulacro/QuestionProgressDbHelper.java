package com.fir.simulacro;

import android.content.Context;

public class QuestionProgressDbHelper {
    private final AppDatabaseHelper appDatabaseHelper;

    public QuestionProgressDbHelper(Context context) {
        this.appDatabaseHelper = new AppDatabaseHelper(context);
    }

    public void saveLatestProgress(String year, String questionNumber, String estado, long duracionMs, long timestampMs) {
        appDatabaseHelper.saveLatestProgress(year, questionNumber, estado, duracionMs, timestampMs);
    }
}
