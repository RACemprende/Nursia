package com.fir.simulacro;

import android.content.Context;

public final class PointsStore {
    private PointsStore() {
    }

    public static int getPoints(Context context) {
        return new AppDatabaseHelper(context).getPoints();
    }

    public static void addPoints(Context context, int pointsToAdd) {
        new AppDatabaseHelper(context).addPoints(pointsToAdd);
    }

    public static boolean spendPoints(Context context, int cost) {
        return new AppDatabaseHelper(context).spendPoints(cost);
    }
}
