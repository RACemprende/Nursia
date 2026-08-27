package com.fir.simulacro;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AppDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "fir_examenes.sqlite";
    private static final int DATABASE_VERSION = 6;
    private static final String QUESTIONS_CSV_ASSET = "sespa_enfermeria_questions.csv";
    private static final String QUESTIONS_DATASET_VERSION = "sespa_enfermeria_2019_2025_v2";
    private static final String QUESTIONS_VIEW = "questions_full";

    private static final String LEGACY_PROGRESS_DB_NAME = "fir_user_data.db";
    private static final String LEGACY_PREFS_NAME = "fir_simulacro_prefs";
    private static final String LEGACY_POINTS_KEY = "points";

    private static final String TABLE_EXAMS = "exams";
    private static final String TABLE_QUESTIONS = "questions";
    private static final String TABLE_QUESTION_PROGRESS = "question_progress";
    private static final String TABLE_APP_STATE = "app_state";
    private static final String TABLE_REWARD_PURCHASES = "reward_purchases";
    private static final String TABLE_CUSTOM_REWARDS = "custom_rewards";
    private static final String TABLE_EXAM_ATTEMPTS = "exam_attempts";
    private static final String TABLE_BADGES = "badges";

    private static final String STATE_POINTS = "points";
    private static final String STATE_PROGRESS_MIGRATED = "legacy_progress_migrated";
    private static final String STATE_POINTS_MIGRATED = "legacy_points_migrated";
    private static final String STATE_PROGRESS_V2_MIGRATED = "progress_state_v2_migrated";
    private static final String STATE_QUESTIONS_DATASET_VERSION = "questions_dataset_version";
    private static final String STATE_POSITIVE_STREAK = "positive_streak";
    private static final String STATE_NEGATIVE_STREAK = "negative_streak";
    private static final String STATE_BEST_POSITIVE_STREAK = "best_positive_streak";
    private static final String STATE_BEST_NEGATIVE_STREAK = "best_negative_streak";
    private static final String STATE_CLOUD_LAST_MODIFIED = "cloud_last_modified_ms";

    private static final int[] STREAK_THRESHOLDS = {3, 5, 10, 20, 25, 50, 100};

    private final Context context;
    private boolean initialized;

    public AppDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        ensureAppTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureAppTables(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureAppTables(db);
    }

    public synchronized List<QuestionRecord> loadAllQuestions() throws IOException {
        SQLiteDatabase db = getInitializedDatabase();
        return queryQuestions(db, null);
    }

    public synchronized List<QuestionRecord> loadQuestionsByStates(List<String> states) throws IOException {
        SQLiteDatabase db = getInitializedDatabase();
        return queryQuestions(db, states);
    }

    private List<QuestionRecord> queryQuestions(SQLiteDatabase db, List<String> states) {
        List<QuestionRecord> questions = new ArrayList<>();
        String sql;
        String[] args;

        if (states == null || states.isEmpty()) {
            sql = "SELECT exam_year, question_number, question_text, option_1, option_2, option_3, option_4, option_5, correct_option_number " +
                    "FROM " + QUESTIONS_VIEW + " WHERE correct_option_number BETWEEN ? AND ? " +
                    "ORDER BY exam_year, question_number";
            args = new String[]{"1", "5"};
        } else {
            String placeholders = TextUtils.join(",", java.util.Collections.nCopies(states.size(), "?"));
            sql = "SELECT q.exam_year, q.question_number, q.question_text, q.option_1, q.option_2, q.option_3, q.option_4, q.option_5, q.correct_option_number " +
                    "FROM " + QUESTIONS_VIEW + " q " +
                    "JOIN " + TABLE_QUESTION_PROGRESS + " p " +
                    "ON p.year = CAST(q.exam_year AS TEXT) AND p.question_number = CAST(q.question_number AS TEXT) " +
                    "WHERE q.correct_option_number BETWEEN ? AND ? AND p.estado IN (" + placeholders + ") " +
                    "ORDER BY q.exam_year, q.question_number";
            args = new String[states.size() + 2];
            args[0] = "1";
            args[1] = "5";
            for (int i = 0; i < states.size(); i++) {
                args[i + 2] = states.get(i);
            }
        }

        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                String statement = trimToNull(cursor.getString(2));
                String option1 = trimToNull(cursor.getString(3));
                String option2 = trimToNull(cursor.getString(4));
                String option3 = trimToNull(cursor.getString(5));
                String option4 = trimToNull(cursor.getString(6));
                String option5 = trimToNull(cursor.getString(7));
                int correctOptionNumber = cursor.getInt(8);

                if (statement == null || option1 == null || option2 == null || option3 == null || option4 == null) {
                    continue;
                }

                List<String> options = new ArrayList<>();
                options.add(option1);
                options.add(option2);
                options.add(option3);
                options.add(option4);
                if (option5 != null) {
                    options.add(option5);
                }

                if (correctOptionNumber < 1 || correctOptionNumber > options.size()) {
                    continue;
                }

                questions.add(new QuestionRecord(
                        String.valueOf(cursor.getInt(0)),
                        String.valueOf(cursor.getInt(1)),
                        statement,
                        options,
                        correctOptionNumber - 1
                ));
            }
        }

        return questions;
    }

    public synchronized void saveLatestProgress(String year, String questionNumber, String estado, long duracionMs, long timestampMs) {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        ContentValues values = new ContentValues();
        values.put("year", year);
        values.put("question_number", questionNumber);
        values.put("estado", estado);
        values.put("duracion_ms", duracionMs);
        values.put("timestamp_ms", timestampMs);
        db.insertWithOnConflict(TABLE_QUESTION_PROGRESS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        touchCloudState(db);
    }

    public synchronized int getPoints() {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        String value = getAppState(db, STATE_POINTS);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(value), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public synchronized void addPoints(int pointsToAdd) {
        if (pointsToAdd <= 0) {
            return;
        }
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        setPoints(db, getPoints() + pointsToAdd);
        touchCloudState(db);
    }

    public synchronized boolean spendPoints(int cost) {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        if (cost < 0) {
            return false;
        }

        db.beginTransaction();
        try {
            int currentPoints = getPointsInternal(db);
            if (currentPoints < cost) {
                return false;
            }
            setPoints(db, currentPoints - cost);
            touchCloudState(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void recordRewardPurchase(String rewardName, int cost, String rewardCode, long purchasedAtMs) {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        String qrPayload = rewardCode + "|" + purchasedAtMs;
        ContentValues values = new ContentValues();
        values.put("reward_name", rewardName);
        values.put("cost", cost);
        values.put("reward_code", rewardCode);
        values.put("purchased_at_ms", purchasedAtMs);
        values.put("qr_payload", qrPayload);
        values.put("redeemed", 0);
        db.insert(TABLE_REWARD_PURCHASES, null, values);
        touchCloudState(db);
    }

    public synchronized long addCustomReward(String rewardName, int cost, String imageUri, long createdAtMs) {
        String normalizedName = trimToNull(rewardName);
        if (normalizedName == null) {
            throw new IllegalArgumentException("El premio necesita una descripción");
        }
        if (cost <= 0) {
            throw new IllegalArgumentException("El premio necesita un coste mayor que cero");
        }

        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        ContentValues values = new ContentValues();
        values.put("reward_name", normalizedName);
        values.put("cost", cost);
        values.put("image_uri", trimToNull(imageUri));
        values.put("reward_code", "custom_" + UUID.randomUUID().toString());
        values.put("created_at_ms", createdAtMs);
        long rowId = db.insert(TABLE_CUSTOM_REWARDS, null, values);
        touchCloudState(db);
        return rowId;
    }

    public synchronized List<CustomReward> getCustomRewards() {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        List<CustomReward> rewards = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_CUSTOM_REWARDS,
                null,
                null,
                null,
                null,
                null,
                "created_at_ms ASC, id ASC"
        )) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("reward_name"));
                int cost = cursor.getInt(cursor.getColumnIndexOrThrow("cost"));
                String imageUri = cursor.getString(cursor.getColumnIndexOrThrow("image_uri"));
                String code = cursor.getString(cursor.getColumnIndexOrThrow("reward_code"));
                long createdAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("created_at_ms"));
                rewards.add(new CustomReward(id, name, cost, imageUri, code, createdAtMs));
            }
        }
        return rewards;
    }

    public synchronized List<PurchasedReward> getPurchasedRewardsNotRedeemed() {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        List<PurchasedReward> rewards = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_REWARD_PURCHASES,
                null,
                "redeemed = 0",
                null,
                null,
                null,
                "purchased_at_ms DESC"
        )) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("reward_name"));
                int cost = cursor.getInt(cursor.getColumnIndexOrThrow("cost"));
                String code = cursor.getString(cursor.getColumnIndexOrThrow("reward_code"));
                long purchasedAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("purchased_at_ms"));
                rewards.add(new PurchasedReward(id, name, cost, code, purchasedAtMs));
            }
        }
        return rewards;
    }

    public synchronized void redeemReward(int rewardId) {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        ContentValues values = new ContentValues();
        values.put("redeemed", 1);
        db.update(TABLE_REWARD_PURCHASES, values, "id = ?", new String[]{String.valueOf(rewardId)});
        touchCloudState(db);
    }

    public static class PurchasedReward {
        public final int id;
        public final String name;
        public final int cost;
        public final String code;
        public final long purchasedAtMs;

        public PurchasedReward(int id, String name, int cost, String code, long purchasedAtMs) {
            this.id = id;
            this.name = name;
            this.cost = cost;
            this.code = code;
            this.purchasedAtMs = purchasedAtMs;
        }
    }

    public static class CustomReward {
        public final int id;
        public final String name;
        public final int cost;
        public final String imageUri;
        public final String code;
        public final long createdAtMs;

        public CustomReward(int id, String name, int cost, String imageUri, String code, long createdAtMs) {
            this.id = id;
            this.name = name;
            this.cost = cost;
            this.imageUri = imageUri;
            this.code = code;
            this.createdAtMs = createdAtMs;
        }
    }

    public synchronized StreakAchievement updateStreakForStatus(String estado) {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        String normalized = trimToNull(estado);
        if (normalized == null) {
            return null;
        }

        db.beginTransaction();
        try {
            int positiveStreak = getIntAppState(db, STATE_POSITIVE_STREAK);
            int negativeStreak = getIntAppState(db, STATE_NEGATIVE_STREAK);

            if (isPositiveStatus(normalized)) {
                positiveStreak++;
                negativeStreak = 0;
                setAppState(db, STATE_POSITIVE_STREAK, String.valueOf(positiveStreak));
                setAppState(db, STATE_NEGATIVE_STREAK, "0");
                setAppState(db, STATE_BEST_POSITIVE_STREAK, String.valueOf(
                        Math.max(positiveStreak, getIntAppState(db, STATE_BEST_POSITIVE_STREAK))
                ));
                touchCloudState(db);
                db.setTransactionSuccessful();
                return isThreshold(positiveStreak)
                        ? new StreakAchievement(true, positiveStreak)
                        : null;
            }

            if (isNegativeStatus(normalized)) {
                negativeStreak++;
                positiveStreak = 0;
                setAppState(db, STATE_POSITIVE_STREAK, "0");
                setAppState(db, STATE_NEGATIVE_STREAK, String.valueOf(negativeStreak));
                setAppState(db, STATE_BEST_NEGATIVE_STREAK, String.valueOf(
                        Math.max(negativeStreak, getIntAppState(db, STATE_BEST_NEGATIVE_STREAK))
                ));
                touchCloudState(db);
                db.setTransactionSuccessful();
                return isThreshold(negativeStreak)
                        ? new StreakAchievement(false, negativeStreak)
                        : null;
            }

            setAppState(db, STATE_POSITIVE_STREAK, "0");
            setAppState(db, STATE_NEGATIVE_STREAK, "0");
            touchCloudState(db);
            db.setTransactionSuccessful();
            return null;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized List<String> getAvailableSubjects() {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        List<String> subjects = new ArrayList<>();
        subjects.add("Todas");
        try (Cursor cursor = db.rawQuery(
                "SELECT DISTINCT subject_name FROM " + QUESTIONS_VIEW +
                        " WHERE subject_name IS NOT NULL AND TRIM(subject_name) != '' ORDER BY subject_name",
                null
        )) {
            while (cursor.moveToNext()) {
                subjects.add(cursor.getString(0));
            }
        }
        return subjects;
    }

    public synchronized List<String> getAvailableMonths() {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        List<String> months = new ArrayList<>();
        months.add("Todos");
        try (Cursor cursor = db.rawQuery(
                "SELECT DISTINCT strftime('%Y-%m', timestamp_ms / 1000, 'unixepoch', 'localtime') AS month_value " +
                        "FROM " + TABLE_QUESTION_PROGRESS +
                        " WHERE timestamp_ms > 0 ORDER BY month_value DESC",
                null
        )) {
            while (cursor.moveToNext()) {
                String month = trimToNull(cursor.getString(0));
                if (month != null) {
                    months.add(month);
                }
            }
        }
        return months;
    }

    public synchronized StatisticsData getStatistics(String subjectFilter, String startMonth, String endMonth) {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        QueryFilter filter = buildStatisticsFilter(subjectFilter, startMonth, endMonth);

        StatisticsData data = new StatisticsData();
        try (Cursor cursor = db.rawQuery(
                "SELECT " +
                        "COUNT(*) AS total_done, " +
                        "SUM(CASE WHEN p.estado IN ('Acertada', 'Duda_Primera') THEN 1 ELSE 0 END) AS total_correct, " +
                        "SUM(CASE WHEN p.estado IN ('Fallada', 'Duda_Segunda', 'Duda_Fallada') THEN 1 ELSE 0 END) AS total_failed, " +
                        "SUM(CASE WHEN p.estado IN ('Duda_Primera', 'Duda_Segunda', 'Duda_Fallada') THEN 1 ELSE 0 END) AS total_doubted, " +
                        "SUM(CASE WHEN p.estado = 'Duda_Primera' THEN 1 ELSE 0 END) AS doubt_first, " +
                        "SUM(CASE WHEN p.estado = 'Duda_Segunda' THEN 1 ELSE 0 END) AS doubt_second, " +
                        "AVG(CASE WHEN p.duracion_ms > 0 THEN p.duracion_ms END) AS avg_duration_ms " +
                        "FROM " + TABLE_QUESTION_PROGRESS + " p " +
                        "JOIN " + QUESTIONS_VIEW + " q ON p.year = CAST(q.exam_year AS TEXT) AND p.question_number = CAST(q.question_number AS TEXT)" +
                        filter.whereClause,
                filter.args
        )) {
            if (cursor.moveToFirst()) {
                data.totalDone = cursor.getInt(0);
                data.totalCorrect = cursor.getInt(1);
                data.totalFailed = cursor.getInt(2);
                data.totalDoubted = cursor.getInt(3);
                data.doubtFirst = cursor.getInt(4);
                data.doubtSecond = cursor.getInt(5);
                data.averageDurationMs = cursor.isNull(6) ? 0L : Math.round(cursor.getDouble(6));
            }
        }

        try (Cursor cursor = db.rawQuery(
                "SELECT date(p.timestamp_ms / 1000, 'unixepoch', 'localtime') AS day_value, COUNT(*) AS total_day " +
                        "FROM " + TABLE_QUESTION_PROGRESS + " p " +
                        "JOIN " + QUESTIONS_VIEW + " q ON p.year = CAST(q.exam_year AS TEXT) AND p.question_number = CAST(q.question_number AS TEXT)" +
                        filter.whereClause +
                        " GROUP BY day_value ORDER BY day_value ASC",
                filter.args
        )) {
            while (cursor.moveToNext()) {
                data.dailyCounts.add(new DailyCount(cursor.getString(0), cursor.getInt(1)));
            }
        }

        data.bestPositiveStreak = getIntAppState(db, STATE_BEST_POSITIVE_STREAK);
        data.bestNegativeStreak = getIntAppState(db, STATE_BEST_NEGATIVE_STREAK);
        data.consecutiveDaysStreak = getConsecutiveDaysStreak();

        return data;
    }

    private synchronized SQLiteDatabase getInitializedDatabase() throws IOException {
        if (!initialized) {
            copyBundledDatabaseIfNeeded();
            SQLiteDatabase db = getWritableDatabase();
            migrateLegacyData(db);
            ensureQuestionDataset(db);
            initialized = true;
            return db;
        }
        return getWritableDatabase();
    }

    private SQLiteDatabase getInitializedDatabaseUnchecked() {
        try {
            return getInitializedDatabase();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo inicializar la base de datos local", e);
        }
    }

    private void copyBundledDatabaseIfNeeded() throws IOException {
        File databaseFile = context.getDatabasePath(DATABASE_NAME);
        if (databaseFile.exists()) {
            return;
        }

        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de bases de datos");
        }

        try (InputStream input = context.getAssets().open(DATABASE_NAME);
             OutputStream output = new FileOutputStream(databaseFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();
        }
    }

    private void migrateLegacyData(SQLiteDatabase db) {
        ensureAppTables(db);
        migrateLegacyQuestionProgress(db);
        migrateLegacyPoints(db);
        migrateProgressStatesToV2(db);
        seedPendingQuestionProgress(db);
    }

    private void ensureQuestionDataset(SQLiteDatabase db) throws IOException {
        ensureQuestionSchema(db);

        String currentVersion = getAppState(db, STATE_QUESTIONS_DATASET_VERSION);
        if (QUESTIONS_DATASET_VERSION.equals(currentVersion) && hasImportedQuestions(db)) {
            recreateQuestionsView(db);
            return;
        }

        db.beginTransaction();
        try {
            db.execSQL("DROP VIEW IF EXISTS " + QUESTIONS_VIEW);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTIONS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_EXAMS);
            ensureQuestionSchema(db);
            importQuestionsFromCsv(db);
            recreateQuestionsView(db);
            resetQuestionBoundProgress(db);
            seedPendingQuestionProgress(db);
            setAppState(db, STATE_QUESTIONS_DATASET_VERSION, QUESTIONS_DATASET_VERSION);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void ensureQuestionSchema(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_EXAMS + " (" +
                        "exam_year INTEGER PRIMARY KEY, " +
                        "exam_name TEXT NOT NULL" +
                        ")"
        );
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_QUESTIONS + " (" +
                        "exam_year INTEGER NOT NULL, " +
                        "question_number INTEGER NOT NULL, " +
                        "question_text TEXT NOT NULL, " +
                        "option_1 TEXT NOT NULL, " +
                        "option_2 TEXT NOT NULL, " +
                        "option_3 TEXT NOT NULL, " +
                        "option_4 TEXT NOT NULL, " +
                        "option_5 TEXT, " +
                        "correct_option_number INTEGER NOT NULL, " +
                        "correct_option_text TEXT, " +
                        "subject_name TEXT, " +
                        "section_name TEXT, " +
                        "PRIMARY KEY (exam_year, question_number), " +
                        "FOREIGN KEY (exam_year) REFERENCES " + TABLE_EXAMS + "(exam_year)" +
                        ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_questions_subject ON " +
                        TABLE_QUESTIONS + "(subject_name)"
        );
    }

    private void recreateQuestionsView(SQLiteDatabase db) {
        db.execSQL("DROP VIEW IF EXISTS " + QUESTIONS_VIEW);
        db.execSQL(
                "CREATE VIEW " + QUESTIONS_VIEW + " AS " +
                        "SELECT q.exam_year, q.question_number, q.question_text, " +
                        "q.option_1, q.option_2, q.option_3, q.option_4, q.option_5, " +
                        "q.correct_option_number, q.correct_option_text, q.subject_name, q.section_name " +
                        "FROM " + TABLE_QUESTIONS + " q"
        );
    }

    private boolean hasImportedQuestions(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_QUESTIONS,
                null
        )) {
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        }
    }

    private void importQuestionsFromCsv(SQLiteDatabase db) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(QUESTIONS_CSV_ASSET), StandardCharsets.UTF_8)
        )) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("El CSV de preguntas de enfermeria está vacio.");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (trimToNull(line) == null) {
                    continue;
                }

                List<String> fields = CsvUtils.parseCsvLine(line);
                if (fields.size() < 12) {
                    throw new IOException("Fila CSV invalida al importar preguntas de enfermeria.");
                }

                int examYear = parseRequiredInt(fields.get(0), "exam_year");
                int questionNumber = parseRequiredInt(fields.get(1), "question_number");
                String questionText = requireText(fields.get(2), "question_text");
                String option1 = requireText(fields.get(3), "option_1");
                String option2 = requireText(fields.get(4), "option_2");
                String option3 = requireText(fields.get(5), "option_3");
                String option4 = requireText(fields.get(6), "option_4");
                String option5 = trimToNull(fields.get(7));
                int correctOptionNumber = parseRequiredInt(fields.get(8), "correct_option_number");
                String correctOptionText = trimToNull(fields.get(9));
                String subjectName = trimToNull(fields.get(10));
                String sectionName = trimToNull(fields.get(11));

                int optionCount = option5 == null ? 4 : 5;
                if (correctOptionNumber < 1 || correctOptionNumber > optionCount) {
                    throw new IOException("Respuesta correcta fuera de rango en la pregunta " + examYear + "-" + questionNumber);
                }

                ContentValues examValues = new ContentValues();
                examValues.put("exam_year", examYear);
                examValues.put("exam_name", "Examen " + examYear);
                db.insertWithOnConflict(TABLE_EXAMS, null, examValues, SQLiteDatabase.CONFLICT_IGNORE);

                ContentValues questionValues = new ContentValues();
                questionValues.put("exam_year", examYear);
                questionValues.put("question_number", questionNumber);
                questionValues.put("question_text", questionText);
                questionValues.put("option_1", option1);
                questionValues.put("option_2", option2);
                questionValues.put("option_3", option3);
                questionValues.put("option_4", option4);
                questionValues.put("option_5", option5);
                questionValues.put("correct_option_number", correctOptionNumber);
                questionValues.put("correct_option_text", correctOptionText);
                questionValues.put("subject_name", subjectName);
                questionValues.put("section_name", sectionName);
                db.insertOrThrow(TABLE_QUESTIONS, null, questionValues);
            }
        }
    }

    private void resetQuestionBoundProgress(SQLiteDatabase db) {
        db.delete(TABLE_QUESTION_PROGRESS, null, null);
        db.delete(TABLE_EXAM_ATTEMPTS, null, null);
        setAppState(db, STATE_POSITIVE_STREAK, "0");
        setAppState(db, STATE_NEGATIVE_STREAK, "0");
        setAppState(db, STATE_BEST_POSITIVE_STREAK, "0");
        setAppState(db, STATE_BEST_NEGATIVE_STREAK, "0");
    }

    private void migrateLegacyQuestionProgress(SQLiteDatabase db) {
        if ("1".equals(getAppState(db, STATE_PROGRESS_MIGRATED))) {
            return;
        }

        File legacyDbFile = context.getDatabasePath(LEGACY_PROGRESS_DB_NAME);
        if (legacyDbFile.exists()) {
            try (SQLiteDatabase legacyDb = SQLiteDatabase.openDatabase(
                    legacyDbFile.getPath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );
                 Cursor cursor = legacyDb.query(
                         TABLE_QUESTION_PROGRESS,
                         new String[]{"year", "question_number", "estado", "duracion_ms", "timestamp_ms"},
                         null,
                         null,
                         null,
                         null,
                         null
                 )) {
                db.beginTransaction();
                try {
                    while (cursor.moveToNext()) {
                        ContentValues values = new ContentValues();
                        values.put("year", cursor.getString(0));
                        values.put("question_number", cursor.getString(1));
                        values.put("estado", mapLegacyEstado(cursor.getString(2)));
                        values.put("duracion_ms", cursor.getLong(3));
                        values.put("timestamp_ms", cursor.getLong(4));
                        db.insertWithOnConflict(TABLE_QUESTION_PROGRESS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } catch (Exception ignored) {
            }

            deleteIfExists(legacyDbFile);
            deleteIfExists(new File(legacyDbFile.getPath() + "-journal"));
        }

        setAppState(db, STATE_PROGRESS_MIGRATED, "1");
    }

    private void migrateLegacyPoints(SQLiteDatabase db) {
        if ("1".equals(getAppState(db, STATE_POINTS_MIGRATED))) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        int legacyPoints = Math.max(prefs.getInt(LEGACY_POINTS_KEY, 0), 0);
        if (getAppState(db, STATE_POINTS) == null) {
            setAppState(db, STATE_POINTS, String.valueOf(legacyPoints));
        }
        prefs.edit().remove(LEGACY_POINTS_KEY).apply();
        setAppState(db, STATE_POINTS_MIGRATED, "1");
    }

    private void migrateProgressStatesToV2(SQLiteDatabase db) {
        if ("1".equals(getAppState(db, STATE_PROGRESS_V2_MIGRATED))) {
            return;
        }

        db.beginTransaction();
        try {
            db.execSQL(
                    "UPDATE " + TABLE_QUESTION_PROGRESS +
                            " SET estado = 'Duda_Fallada' WHERE estado = 'Dudada'"
            );
            db.execSQL(
                    "UPDATE " + TABLE_QUESTION_PROGRESS +
                            " SET estado = 'Pendiente', duracion_ms = 0, timestamp_ms = 0 " +
                            "WHERE estado IS NULL OR TRIM(estado) = ''"
            );
            setAppState(db, STATE_PROGRESS_V2_MIGRATED, "1");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void seedPendingQuestionProgress(SQLiteDatabase db) {
        db.execSQL(
                "INSERT OR IGNORE INTO " + TABLE_QUESTION_PROGRESS + " (year, question_number, estado, duracion_ms, timestamp_ms) " +
                        "SELECT CAST(exam_year AS TEXT), CAST(question_number AS TEXT), 'Pendiente', 0, 0 FROM " + TABLE_QUESTIONS
        );
    }

    private void ensureAppTables(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_QUESTION_PROGRESS + " (" +
                        "year TEXT NOT NULL, " +
                        "question_number TEXT NOT NULL, " +
                        "estado TEXT NOT NULL, " +
                        "duracion_ms INTEGER NOT NULL, " +
                        "timestamp_ms INTEGER NOT NULL, " +
                        "PRIMARY KEY (year, question_number)" +
                        ")"
        );
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_APP_STATE + " (" +
                        "key TEXT PRIMARY KEY, " +
                        "value TEXT NOT NULL" +
                        ")"
        );
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_REWARD_PURCHASES + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "reward_name TEXT NOT NULL, " +
                        "cost INTEGER NOT NULL, " +
                        "reward_code TEXT NOT NULL, " +
                        "purchased_at_ms INTEGER NOT NULL, " +
                        "qr_payload TEXT NOT NULL, " +
                        "redeemed INTEGER DEFAULT 0" +
                        ")"
        );
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_CUSTOM_REWARDS + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "reward_name TEXT NOT NULL, " +
                        "cost INTEGER NOT NULL, " +
                        "image_uri TEXT, " +
                        "reward_code TEXT NOT NULL UNIQUE, " +
                        "created_at_ms INTEGER NOT NULL" +
                        ")"
        );
        ensureRewardPurchasesRedeemedColumn(db);
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_reward_purchases_timestamp ON " +
                        TABLE_REWARD_PURCHASES + "(purchased_at_ms)"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_custom_rewards_created_at ON " +
                        TABLE_CUSTOM_REWARDS + "(created_at_ms)"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_question_progress_timestamp ON " +
                        TABLE_QUESTION_PROGRESS + "(timestamp_ms)"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_question_progress_estado ON " +
                        TABLE_QUESTION_PROGRESS + "(estado)"
        );
    }

    private void ensureRewardPurchasesRedeemedColumn(SQLiteDatabase db) {
        boolean hasRedeemed = false;
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE_REWARD_PURCHASES + ")", null)) {
            while (cursor.moveToNext()) {
                String columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if ("redeemed".equals(columnName)) {
                    hasRedeemed = true;
                    break;
                }
            }
        }
        if (!hasRedeemed) {
            db.execSQL("ALTER TABLE " + TABLE_REWARD_PURCHASES + " ADD COLUMN redeemed INTEGER NOT NULL DEFAULT 0");
        }
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_EXAM_ATTEMPTS + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "timestamp_inicio INTEGER NOT NULL, " +
                        "duracion_ms INTEGER NOT NULL, " +
                        "acertadas INTEGER NOT NULL DEFAULT 0, " +
                        "falladas INTEGER NOT NULL DEFAULT 0, " +
                        "duda_primera INTEGER NOT NULL DEFAULT 0, " +
                        "duda_segunda INTEGER NOT NULL DEFAULT 0, " +
                        "vacias INTEGER NOT NULL DEFAULT 0, " +
                        "puntuacion_neta REAL NOT NULL DEFAULT 0" +
                        ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_exam_attempts_timestamp ON " +
                        TABLE_EXAM_ATTEMPTS + "(timestamp_inicio)"
        );
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_BADGES + " (" +
                        "id TEXT PRIMARY KEY, " +
                        "nombre TEXT NOT NULL, " +
                        "descripcion TEXT NOT NULL, " +
                        "imagen_drawable TEXT, " +
                        "desbloqueada INTEGER NOT NULL DEFAULT 0, " +
                        "fecha_desbloqueo INTEGER DEFAULT 0" +
                        ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_badges_desbloqueada ON " +
                        TABLE_BADGES + "(desbloqueada)"
        );
        seedBadges(db);
    }

    public synchronized boolean hasCompletedSimulacroToday() {
        try {
            SQLiteDatabase db = getInitializedDatabaseUnchecked();
            try (Cursor c = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_EXAM_ATTEMPTS + " " +
                    "WHERE date(timestamp_inicio/1000, 'unixepoch', 'localtime') = date('now', 'localtime')",
                    null)) {
                return c.moveToFirst() && c.getInt(0) > 0;
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error checking today simulacro", e);
            return false;
        }
    }

    public synchronized void saveExamAttempt(long timestampInicio, long duracionMs,
                                              int acertadas, int falladas,
                                              int dudaPrimera, int dudaSegunda,
                                              int vacias, double puntuacionNeta) {
        try {
            SQLiteDatabase db = getInitializedDatabase();
            ContentValues cv = new ContentValues();
            cv.put("timestamp_inicio", timestampInicio);
            cv.put("duracion_ms", duracionMs);
            cv.put("acertadas", acertadas);
            cv.put("falladas", falladas);
            cv.put("duda_primera", dudaPrimera);
            cv.put("duda_segunda", dudaSegunda);
            cv.put("vacias", vacias);
            cv.put("puntuacion_neta", puntuacionNeta);
            db.insert(TABLE_EXAM_ATTEMPTS, null, cv);
            touchCloudState(db);
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error saving exam attempt", e);
        }
    }

    public synchronized boolean unlockBadge(String badgeId) {
        try {
            SQLiteDatabase db = getInitializedDatabase();
            try (Cursor c = db.query(TABLE_BADGES, new String[]{"desbloqueada"}, "id = ?", new String[]{badgeId}, null, null, null)) {
                if (!c.moveToFirst()) return false;
                if (c.getInt(0) == 1) return false;
            }
            ContentValues cv = new ContentValues();
            cv.put("desbloqueada", 1);
            cv.put("fecha_desbloqueo", System.currentTimeMillis());
            db.update(TABLE_BADGES, cv, "id = ?", new String[]{badgeId});
            touchCloudState(db);
            return true;
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error unlocking badge " + badgeId, e);
            return false;
        }
    }

    public synchronized List<Badge> getAllBadges() {
        List<Badge> list = new ArrayList<>();
        try {
            SQLiteDatabase db = getInitializedDatabase();
            try (Cursor c = db.query(TABLE_BADGES, null, null, null, null, null, "rowid ASC")) {
                while (c.moveToNext()) {
                    list.add(new Badge(
                        c.getString(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("nombre")),
                        c.getString(c.getColumnIndexOrThrow("descripcion")),
                        c.getString(c.getColumnIndexOrThrow("imagen_drawable")),
                        c.getInt(c.getColumnIndexOrThrow("desbloqueada")) == 1,
                        c.getLong(c.getColumnIndexOrThrow("fecha_desbloqueo"))
                    ));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error getting badges", e);
        }
        return list;
    }

    public synchronized int getConsecutiveDaysStreak() {
        try {
            SQLiteDatabase db = getInitializedDatabase();
            try (Cursor c = db.rawQuery(
                "SELECT DISTINCT date(timestamp_inicio/1000, 'unixepoch', 'localtime') as day " +
                "FROM exam_attempts WHERE timestamp_inicio > 0 ORDER BY day DESC", null)) {

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                String expected = sdf.format(cal.getTime());
                int streak = 0;
                while (c.moveToNext()) {
                    String day = c.getString(0);
                    if (day.equals(expected)) {
                        streak++;
                        cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
                        expected = sdf.format(cal.getTime());
                    } else {
                        break;
                    }
                }
                return streak;
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error getting consecutive days", e);
            return 0;
        }
    }

    public synchronized int getCompletedExamAttemptsCount() {
        try {
            SQLiteDatabase db = getInitializedDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_EXAM_ATTEMPTS,
                    null
            )) {
                return c.moveToFirst() ? c.getInt(0) : 0;
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error counting exam attempts", e);
            return 0;
        }
    }

    public synchronized boolean hasAnyQuestionWithState(String estado) {
        String normalized = trimToNull(estado);
        if (normalized == null) return false;
        try {
            SQLiteDatabase db = getInitializedDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT EXISTS(SELECT 1 FROM " + TABLE_QUESTION_PROGRESS + " WHERE estado = ?)",
                    new String[]{normalized}
            )) {
                return c.moveToFirst() && c.getInt(0) == 1;
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error checking state " + normalized, e);
            return false;
        }
    }

    public synchronized boolean hasCompletedAllQuestions() {
        try {
            SQLiteDatabase db = getInitializedDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT " +
                            "SUM(CASE WHEN estado = 'Pendiente' THEN 1 ELSE 0 END) AS pending_count, " +
                            "COUNT(*) AS total_count " +
                            "FROM " + TABLE_QUESTION_PROGRESS,
                    null
            )) {
                if (!c.moveToFirst()) return false;
                int pendingCount = c.getInt(0);
                int totalCount = c.getInt(1);
                return totalCount > 0 && pendingCount == 0;
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error checking full completion", e);
            return false;
        }
    }

    public synchronized long getCloudLastModifiedMs() {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        return getLongAppState(db, STATE_CLOUD_LAST_MODIFIED);
    }

    public synchronized void markCloudDirty() {
        SQLiteDatabase db = getInitializedDatabaseUnchecked();
        touchCloudState(db);
    }

    public synchronized JSONObject exportCloudSnapshot() {
        try {
            SQLiteDatabase db = getInitializedDatabaseUnchecked();
            JSONObject snapshot = new JSONObject();
            snapshot.put("schemaVersion", 1);
            long updatedAtMs = Math.max(System.currentTimeMillis(), getCloudLastModifiedMs());
            snapshot.put("updatedAtMs", updatedAtMs);

            JSONObject appState = new JSONObject();
            appState.put(STATE_POINTS, getPointsInternal(db));
            appState.put(STATE_POSITIVE_STREAK, getIntAppState(db, STATE_POSITIVE_STREAK));
            appState.put(STATE_NEGATIVE_STREAK, getIntAppState(db, STATE_NEGATIVE_STREAK));
            appState.put(STATE_BEST_POSITIVE_STREAK, getIntAppState(db, STATE_BEST_POSITIVE_STREAK));
            appState.put(STATE_BEST_NEGATIVE_STREAK, getIntAppState(db, STATE_BEST_NEGATIVE_STREAK));
            appState.put(STATE_CLOUD_LAST_MODIFIED, updatedAtMs);
            snapshot.put("appState", appState);
            snapshot.put("settings", UserSettings.exportSnapshot(context));

            JSONArray progressArray = new JSONArray();
            try (Cursor cursor = db.query(TABLE_QUESTION_PROGRESS, null, null, null, null, null, "year ASC, question_number ASC")) {
                while (cursor.moveToNext()) {
                    JSONObject row = new JSONObject();
                    row.put("year", cursor.getString(cursor.getColumnIndexOrThrow("year")));
                    row.put("questionNumber", cursor.getString(cursor.getColumnIndexOrThrow("question_number")));
                    row.put("estado", cursor.getString(cursor.getColumnIndexOrThrow("estado")));
                    row.put("duracionMs", cursor.getLong(cursor.getColumnIndexOrThrow("duracion_ms")));
                    row.put("timestampMs", cursor.getLong(cursor.getColumnIndexOrThrow("timestamp_ms")));
                    progressArray.put(row);
                }
            }
            snapshot.put("questionProgress", progressArray);

            JSONArray attemptsArray = new JSONArray();
            try (Cursor cursor = db.query(TABLE_EXAM_ATTEMPTS, null, null, null, null, null, "timestamp_inicio ASC, id ASC")) {
                while (cursor.moveToNext()) {
                    JSONObject row = new JSONObject();
                    row.put("timestampInicio", cursor.getLong(cursor.getColumnIndexOrThrow("timestamp_inicio")));
                    row.put("duracionMs", cursor.getLong(cursor.getColumnIndexOrThrow("duracion_ms")));
                    row.put("acertadas", cursor.getInt(cursor.getColumnIndexOrThrow("acertadas")));
                    row.put("falladas", cursor.getInt(cursor.getColumnIndexOrThrow("falladas")));
                    row.put("dudaPrimera", cursor.getInt(cursor.getColumnIndexOrThrow("duda_primera")));
                    row.put("dudaSegunda", cursor.getInt(cursor.getColumnIndexOrThrow("duda_segunda")));
                    row.put("vacias", cursor.getInt(cursor.getColumnIndexOrThrow("vacias")));
                    row.put("puntuacionNeta", cursor.getDouble(cursor.getColumnIndexOrThrow("puntuacion_neta")));
                    attemptsArray.put(row);
                }
            }
            snapshot.put("examAttempts", attemptsArray);

            JSONArray badgesArray = new JSONArray();
            try (Cursor cursor = db.query(TABLE_BADGES, null, null, null, null, null, "rowid ASC")) {
                while (cursor.moveToNext()) {
                    JSONObject row = new JSONObject();
                    row.put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")));
                    row.put("desbloqueada", cursor.getInt(cursor.getColumnIndexOrThrow("desbloqueada")) == 1);
                    row.put("fechaDesbloqueo", cursor.getLong(cursor.getColumnIndexOrThrow("fecha_desbloqueo")));
                    badgesArray.put(row);
                }
            }
            snapshot.put("badges", badgesArray);

            JSONArray rewardsArray = new JSONArray();
            try (Cursor cursor = db.query(TABLE_REWARD_PURCHASES, null, null, null, null, null, "purchased_at_ms ASC, id ASC")) {
                while (cursor.moveToNext()) {
                    JSONObject row = new JSONObject();
                    row.put("rewardName", cursor.getString(cursor.getColumnIndexOrThrow("reward_name")));
                    row.put("cost", cursor.getInt(cursor.getColumnIndexOrThrow("cost")));
                    row.put("rewardCode", cursor.getString(cursor.getColumnIndexOrThrow("reward_code")));
                    row.put("purchasedAtMs", cursor.getLong(cursor.getColumnIndexOrThrow("purchased_at_ms")));
                    row.put("qrPayload", cursor.getString(cursor.getColumnIndexOrThrow("qr_payload")));
                    row.put("redeemed", cursor.getInt(cursor.getColumnIndexOrThrow("redeemed")) == 1);
                    rewardsArray.put(row);
                }
            }
            snapshot.put("rewardPurchases", rewardsArray);

            JSONArray customRewardsArray = new JSONArray();
            try (Cursor cursor = db.query(TABLE_CUSTOM_REWARDS, null, null, null, null, null, "created_at_ms ASC, id ASC")) {
                while (cursor.moveToNext()) {
                    JSONObject row = new JSONObject();
                    row.put("rewardName", cursor.getString(cursor.getColumnIndexOrThrow("reward_name")));
                    row.put("cost", cursor.getInt(cursor.getColumnIndexOrThrow("cost")));
                    row.put("imageUri", trimToNull(cursor.getString(cursor.getColumnIndexOrThrow("image_uri"))));
                    row.put("rewardCode", cursor.getString(cursor.getColumnIndexOrThrow("reward_code")));
                    row.put("createdAtMs", cursor.getLong(cursor.getColumnIndexOrThrow("created_at_ms")));
                    customRewardsArray.put(row);
                }
            }
            snapshot.put("customRewards", customRewardsArray);

            return snapshot;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo exportar la cuenta", e);
        }
    }

    public synchronized void importCloudSnapshot(JSONObject snapshot, boolean replace) {
        try {
            SQLiteDatabase db = getInitializedDatabaseUnchecked();
            db.beginTransaction();
            try {
                if (replace) {
                    db.delete(TABLE_QUESTION_PROGRESS, null, null);
                    db.delete(TABLE_EXAM_ATTEMPTS, null, null);
                    db.delete(TABLE_REWARD_PURCHASES, null, null);
                    db.delete(TABLE_CUSTOM_REWARDS, null, null);
                    db.execSQL("UPDATE " + TABLE_BADGES + " SET desbloqueada = 0, fecha_desbloqueo = 0");
                    clearCloudState(db);
                }

                JSONObject appState = snapshot.optJSONObject("appState");
                if (appState != null) {
                    setAppStateIfPresent(db, STATE_POINTS, appState.optString(STATE_POINTS, null));
                    setAppStateIfPresent(db, STATE_POSITIVE_STREAK, appState.optString(STATE_POSITIVE_STREAK, null));
                    setAppStateIfPresent(db, STATE_NEGATIVE_STREAK, appState.optString(STATE_NEGATIVE_STREAK, null));
                    setAppStateIfPresent(db, STATE_BEST_POSITIVE_STREAK, appState.optString(STATE_BEST_POSITIVE_STREAK, null));
                    setAppStateIfPresent(db, STATE_BEST_NEGATIVE_STREAK, appState.optString(STATE_BEST_NEGATIVE_STREAK, null));
                    setAppStateIfPresent(db, STATE_CLOUD_LAST_MODIFIED, appState.optString(STATE_CLOUD_LAST_MODIFIED, null));
                }

                JSONArray progressArray = snapshot.optJSONArray("questionProgress");
                if (progressArray != null) {
                    for (int i = 0; i < progressArray.length(); i++) {
                        JSONObject row = progressArray.getJSONObject(i);
                        ContentValues values = new ContentValues();
                        values.put("year", row.getString("year"));
                        values.put("question_number", row.getString("questionNumber"));
                        values.put("estado", row.getString("estado"));
                        values.put("duracion_ms", row.optLong("duracionMs", 0L));
                        values.put("timestamp_ms", row.optLong("timestampMs", 0L));
                        db.insertWithOnConflict(TABLE_QUESTION_PROGRESS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                JSONArray attemptsArray = snapshot.optJSONArray("examAttempts");
                if (attemptsArray != null) {
                    for (int i = 0; i < attemptsArray.length(); i++) {
                        JSONObject row = attemptsArray.getJSONObject(i);
                        ContentValues values = new ContentValues();
                        values.put("timestamp_inicio", row.optLong("timestampInicio", 0L));
                        values.put("duracion_ms", row.optLong("duracionMs", 0L));
                        values.put("acertadas", row.optInt("acertadas", 0));
                        values.put("falladas", row.optInt("falladas", 0));
                        values.put("duda_primera", row.optInt("dudaPrimera", 0));
                        values.put("duda_segunda", row.optInt("dudaSegunda", 0));
                        values.put("vacias", row.optInt("vacias", 0));
                        values.put("puntuacion_neta", row.optDouble("puntuacionNeta", 0.0));
                        db.insert(TABLE_EXAM_ATTEMPTS, null, values);
                    }
                }

                JSONArray rewardsArray = snapshot.optJSONArray("rewardPurchases");
                if (rewardsArray != null) {
                    for (int i = 0; i < rewardsArray.length(); i++) {
                        JSONObject row = rewardsArray.getJSONObject(i);
                        ContentValues values = new ContentValues();
                        values.put("reward_name", row.getString("rewardName"));
                        values.put("cost", row.optInt("cost", 0));
                        values.put("reward_code", row.getString("rewardCode"));
                        values.put("purchased_at_ms", row.optLong("purchasedAtMs", 0L));
                        values.put("qr_payload", row.optString("qrPayload", ""));
                        values.put("redeemed", row.optBoolean("redeemed", false) ? 1 : 0);
                        db.insert(TABLE_REWARD_PURCHASES, null, values);
                    }
                }

                JSONArray customRewardsArray = snapshot.optJSONArray("customRewards");
                if (customRewardsArray != null) {
                    for (int i = 0; i < customRewardsArray.length(); i++) {
                        JSONObject row = customRewardsArray.getJSONObject(i);
                        ContentValues values = new ContentValues();
                        values.put("reward_name", row.getString("rewardName"));
                        values.put("cost", row.optInt("cost", 0));
                        values.put("image_uri", trimToNull(row.optString("imageUri", null)));
                        values.put("reward_code", row.getString("rewardCode"));
                        values.put("created_at_ms", row.optLong("createdAtMs", System.currentTimeMillis()));
                        db.insertWithOnConflict(TABLE_CUSTOM_REWARDS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                    }
                }

                JSONArray badgesArray = snapshot.optJSONArray("badges");
                if (badgesArray != null) {
                    for (int i = 0; i < badgesArray.length(); i++) {
                        JSONObject row = badgesArray.getJSONObject(i);
                        ContentValues values = new ContentValues();
                        values.put("desbloqueada", row.optBoolean("desbloqueada", false) ? 1 : 0);
                        values.put("fecha_desbloqueo", row.optLong("fechaDesbloqueo", 0L));
                        db.update(TABLE_BADGES, values, "id = ?", new String[]{row.getString("id")});
                    }
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            JSONObject settingsObject = snapshot.optJSONObject("settings");
            if (settingsObject != null) {
                UserSettings.importSnapshot(context, settingsObject);
            }
            seedPendingQuestionProgress(db);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo importar la cuenta", e);
        }
    }

    private void seedBadges(SQLiteDatabase db) {
        String[][] badges = {
            {"streak_pos_3",   "Racha 3 aciertos",    "3 respuestas correctas seguidas",    "streak_positive_3_img"},
            {"streak_pos_5",   "Racha 5 aciertos",    "5 respuestas correctas seguidas",    "streak_positive_5_img"},
            {"streak_pos_10",  "Racha 10 aciertos",   "10 respuestas correctas seguidas",   "streak_positive_10_img"},
            {"streak_pos_20",  "Racha 20 aciertos",   "20 respuestas correctas seguidas",   "streak_positive_20_img"},
            {"streak_pos_25",  "Racha 25 aciertos",   "25 respuestas correctas seguidas",   "streak_positive_25_img"},
            {"streak_pos_50",  "Racha 50 aciertos",   "50 respuestas correctas seguidas",   "streak_positive_50_img"},
            {"streak_pos_100", "Racha 100 aciertos",  "100 respuestas correctas seguidas",  "streak_positive_100_img"},
            {"streak_neg_3",   "Racha 3 fallos",      "3 respuestas incorrectas seguidas",  "streak_negative_3_img"},
            {"streak_neg_5",   "Racha 5 fallos",      "5 respuestas incorrectas seguidas",  "streak_negative_5_img"},
            {"streak_neg_10",  "Racha 10 fallos",     "10 respuestas incorrectas seguidas", "streak_negative_10_img"},
            {"streak_neg_20",  "Racha 20 fallos",     "20 respuestas incorrectas seguidas", "streak_negative_20_img"},
            {"streak_neg_25",  "Racha 25 fallos",     "25 respuestas incorrectas seguidas", "streak_negative_25_img"},
            {"streak_neg_50",  "Racha 50 fallos",     "50 respuestas incorrectas seguidas", "streak_negative_50_img"},
            {"streak_neg_100", "Racha 100 fallos",    "100 respuestas incorrectas seguidas","streak_negative_100_img"},
            {"daily_1",  "Un día seguido",   "Completa tu primer simulacro",       "badge_daily_1_img"},
            {"daily_3",  "3 días seguidos",  "Simulacros 3 días consecutivos",     "badge_exams_3_img"},
            {"daily_5",  "5 días seguidos",  "Simulacros 5 días consecutivos",     "badge_daily_5_img"},
            {"daily_7",  "7 días seguidos",  "Simulacros 7 días consecutivos",     "badge_daily_7_img"},
            {"perfect_exam", "Simulacro perfecto", "Completa un simulacro sin ningún fallo", "badge_perfect_exam_img"},
            {"full_database", "Base completada", "Completa todas las preguntas de la base de datos", "badge_full_database_img"},
            {"first_doubt_hit", "Primera dudada acertada", "Consigue tu primera Duda_Segunda", "badge_first_doubt_hit_img"},
            {"exams_10", "10 simulacros", "Completa 10 simulacros", "badge_exams_10_img"},
            {"exams_25", "25 simulacros", "Completa 25 simulacros", "badge_exams_25_img"},
            {"exams_50", "50 simulacros", "Completa 50 simulacros", "badge_exams_50_img"},
            {"exams_100", "100 simulacros", "Completa 100 simulacros", "badge_exams_100_img"},
            {"superando_limites", "Superando Límites", "Supera tu umbral objetivo de aciertos en un simulacro", "badge_superando_limites_img"},
        };

        for (String[] b : badges) {
            ContentValues cv = new ContentValues();
            cv.put("id", b[0]);
            cv.put("nombre", b[1]);
            cv.put("descripcion", b[2]);
            cv.put("imagen_drawable", b[3]);
            cv.put("desbloqueada", 0);
            cv.put("fecha_desbloqueo", 0L);
            db.insertWithOnConflict(TABLE_BADGES, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
        setAppState(db, "badges_seeded", "1");
    }

    /**
     * Simulacros completados agrupados por groupBy ("day", "week", "month").
     */
    public synchronized List<ChartEntry> getSimulacrosChart(String groupBy, String startMonth, String endMonth) {
        List<ChartEntry> entries = new ArrayList<>();
        try {
            SQLiteDatabase db = getInitializedDatabaseUnchecked();
            String groupExpr;
            switch (groupBy) {
                case "week":
                    groupExpr = "strftime('%Y-W%W', timestamp_inicio/1000, 'unixepoch', 'localtime')";
                    break;
                case "month":
                    groupExpr = "strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime')";
                    break;
                default:
                    groupExpr = "date(timestamp_inicio/1000, 'unixepoch', 'localtime')";
                    break;
            }
            StringBuilder where = new StringBuilder("WHERE timestamp_inicio > 0");
            List<String> args = new ArrayList<>();
            String start = trimToNull(startMonth);
            if (start != null && !"Todos".equals(start)) {
                where.append(" AND strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime') >= ?");
                args.add(start);
            }
            String end = trimToNull(endMonth);
            if (end != null && !"Todos".equals(end)) {
                where.append(" AND strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime') <= ?");
                args.add(end);
            }
            try (Cursor c = db.rawQuery(
                    "SELECT " + groupExpr + " AS lbl, COUNT(*) AS cnt " +
                    "FROM " + TABLE_EXAM_ATTEMPTS + " " +
                    where + " GROUP BY lbl ORDER BY lbl ASC",
                    args.toArray(new String[0])
            )) {
                while (c.moveToNext()) {
                    entries.add(new ChartEntry(c.getString(0), c.getInt(1)));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error in getSimulacrosChart", e);
        }
        return entries;
    }

    /**
     * Número de preguntas hechas agrupadas por groupBy ("day", "week", "month"),
     * con filtros de asignatura y rango de meses.
     */
    public synchronized List<ChartEntry> getQuestionsDoneChart(String groupBy, String subjectFilter, String startMonth, String endMonth) {
        List<ChartEntry> entries = new ArrayList<>();
        try {
            SQLiteDatabase db = getInitializedDatabaseUnchecked();
            String groupExpr;
            switch (groupBy) {
                case "week":
                    groupExpr = "strftime('%Y-W%W', p.timestamp_ms/1000, 'unixepoch', 'localtime')";
                    break;
                case "month":
                    groupExpr = "strftime('%Y-%m', p.timestamp_ms/1000, 'unixepoch', 'localtime')";
                    break;
                default:
                    groupExpr = "date(p.timestamp_ms/1000, 'unixepoch', 'localtime')";
                    break;
            }

            QueryFilter filter = buildStatisticsFilter(subjectFilter, startMonth, endMonth);
            try (Cursor c = db.rawQuery(
                    "SELECT " + groupExpr + " AS lbl, COUNT(*) AS total " +
                    "FROM " + TABLE_QUESTION_PROGRESS + " p " +
                    "JOIN " + QUESTIONS_VIEW + " q ON p.year = CAST(q.exam_year AS TEXT) AND p.question_number = CAST(q.question_number AS TEXT)" +
                    filter.whereClause +
                    " GROUP BY lbl ORDER BY lbl ASC",
                    filter.args
            )) {
                while (c.moveToNext()) {
                    entries.add(new ChartEntry(c.getString(0), c.getInt(1)));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error in getQuestionsDoneChart", e);
        }
        return entries;
    }

    /**
     * % medio de aciertos por simulacro agrupado por groupBy ("day", "week", "month").
     * Aciertos = acertadas + duda_primera.
     */
    public synchronized List<ChartEntry> getAciertosChart(String groupBy, String startMonth, String endMonth) {
        List<ChartEntry> entries = new ArrayList<>();
        try {
            SQLiteDatabase db = getInitializedDatabaseUnchecked();
            String groupExpr;
            switch (groupBy) {
                case "week":
                    groupExpr = "strftime('%Y-W%W', timestamp_inicio/1000, 'unixepoch', 'localtime')";
                    break;
                case "month":
                    groupExpr = "strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime')";
                    break;
                default:
                    groupExpr = "date(timestamp_inicio/1000, 'unixepoch', 'localtime')";
                    break;
            }

            StringBuilder where = new StringBuilder("WHERE timestamp_inicio > 0");
            List<String> args = new ArrayList<>();
            String start = trimToNull(startMonth);
            if (start != null && !"Todos".equals(start)) {
                where.append(" AND strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime') >= ?");
                args.add(start);
            }
            String end = trimToNull(endMonth);
            if (end != null && !"Todos".equals(end)) {
                where.append(" AND strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime') <= ?");
                args.add(end);
            }

            try (Cursor c = db.rawQuery(
                    "SELECT " + groupExpr + " AS lbl, " +
                    "ROUND(100.0 * SUM(acertadas + duda_primera) / " +
                    "NULLIF(SUM(acertadas + falladas + duda_primera + duda_segunda + vacias), 0)) AS total " +
                    "FROM " + TABLE_EXAM_ATTEMPTS + " " +
                    where + " GROUP BY lbl ORDER BY lbl ASC",
                    args.toArray(new String[0])
            )) {
                while (c.moveToNext()) {
                    entries.add(new ChartEntry(c.getString(0), Math.max(0, Math.min(100, c.getInt(1)))));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error in getAciertosChart", e);
        }
        return entries;
    }

    public synchronized List<DailyAccuracyEntry> getDailyAccuracyTrend(String startMonth, String endMonth) {
        List<DailyAccuracyEntry> entries = new ArrayList<>();
        try {
            SQLiteDatabase db = getInitializedDatabaseUnchecked();
            StringBuilder where = new StringBuilder("WHERE timestamp_inicio > 0");
            List<String> args = new ArrayList<>();

            String start = trimToNull(startMonth);
            if (start != null && !"Todos".equals(start)) {
                where.append(" AND strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime') >= ?");
                args.add(start);
            }
            String end = trimToNull(endMonth);
            if (end != null && !"Todos".equals(end)) {
                where.append(" AND strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime') <= ?");
                args.add(end);
            }

            try (Cursor c = db.rawQuery(
                    "SELECT date(timestamp_inicio/1000, 'unixepoch', 'localtime') AS day_value, " +
                    "100.0 * SUM(acertadas + duda_primera) / " +
                    "NULLIF(SUM(acertadas + falladas + duda_primera + duda_segunda + vacias), 0) AS avg_accuracy " +
                    "FROM " + TABLE_EXAM_ATTEMPTS + " " +
                    where + " GROUP BY day_value ORDER BY day_value ASC",
                    args.toArray(new String[0])
            )) {
                while (c.moveToNext()) {
                    if (c.isNull(1)) {
                        continue;
                    }
                    double avg = Math.max(0.0, Math.min(100.0, c.getDouble(1)));
                    entries.add(new DailyAccuracyEntry(c.getString(0), avg));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error in getDailyAccuracyTrend", e);
        }
        return entries;
    }

    public synchronized List<DailyCalendarStats> getDailyCalendarStats(String monthKey) {
        List<DailyCalendarStats> result = new ArrayList<>();
        String normalizedMonth = trimToNull(monthKey);
        if (normalizedMonth == null) {
            return result;
        }
        try {
            SQLiteDatabase db = getInitializedDatabaseUnchecked();
            Map<String, DailyCalendarStats> byDay = new HashMap<>();

            try (Cursor c = db.rawQuery(
                    "SELECT date(timestamp_ms/1000, 'unixepoch', 'localtime') AS day_value, " +
                    "COUNT(*) AS questions_done, " +
                    "SUM(CASE WHEN estado IN ('Acertada', 'Duda_Primera') THEN 1 ELSE 0 END) AS correct_answers, " +
                    "SUM(CASE WHEN estado IN ('Fallada', 'Duda_Segunda', 'Duda_Fallada') THEN 1 ELSE 0 END) AS failed_answers " +
                    "FROM " + TABLE_QUESTION_PROGRESS + " " +
                    "WHERE timestamp_ms > 0 AND estado != 'Pendiente' " +
                    "AND strftime('%Y-%m', timestamp_ms/1000, 'unixepoch', 'localtime') = ? " +
                    "GROUP BY day_value ORDER BY day_value ASC",
                    new String[]{normalizedMonth}
            )) {
                while (c.moveToNext()) {
                    String day = c.getString(0);
                    DailyCalendarStats stats = getOrCreateCalendarStats(byDay, day);
                    stats.questionsDone = c.getInt(1);
                    stats.correctAnswers += c.getInt(2);
                    stats.failedAnswers += c.getInt(3);
                }
            }

            try (Cursor c = db.rawQuery(
                    "SELECT date(timestamp_inicio/1000, 'unixepoch', 'localtime') AS day_value, " +
                    "COUNT(*) AS exams_completed, " +
                    "COALESCE(SUM(acertadas + duda_primera), 0) AS total_correct, " +
                    "COALESCE(SUM(falladas + duda_segunda), 0) AS total_failed, " +
                    "COALESCE(SUM(vacias), 0) AS total_blank, " +
                    "COALESCE(ROUND(100.0 * SUM(acertadas + duda_primera) / " +
                    "NULLIF(SUM(acertadas + falladas + duda_primera + duda_segunda + vacias), 0), 1), 0) AS avg_score " +
                    "FROM " + TABLE_EXAM_ATTEMPTS + " " +
                    "WHERE timestamp_inicio > 0 " +
                    "AND strftime('%Y-%m', timestamp_inicio/1000, 'unixepoch', 'localtime') = ? " +
                    "GROUP BY day_value ORDER BY day_value ASC",
                    new String[]{normalizedMonth}
            )) {
                while (c.moveToNext()) {
                    String day = c.getString(0);
                    DailyCalendarStats stats = getOrCreateCalendarStats(byDay, day);
                    stats.examsCompleted = c.getInt(1);
                    stats.correctAnswers += c.getInt(2);
                    stats.failedAnswers += c.getInt(3);
                    stats.blankAnswers = c.getInt(4);
                    stats.averageScorePercent = c.getDouble(5);
                }
            }

            for (DailyCalendarStats stats : byDay.values()) {
                if (stats.examsCompleted <= 0 && stats.questionsDone > 0) {
                    int totalAnswers = stats.correctAnswers + stats.failedAnswers;
                    if (totalAnswers > 0) {
                        stats.averageScorePercent = (stats.correctAnswers * 100.0) / totalAnswers;
                    }
                }
            }

            result.addAll(byDay.values());
            Collections.sort(result, (a, b) -> a.day.compareTo(b.day));
        } catch (Exception e) {
            android.util.Log.e("AppDB", "Error in getDailyCalendarStats", e);
        }
        return result;
    }

    private DailyCalendarStats getOrCreateCalendarStats(Map<String, DailyCalendarStats> byDay, String day) {
        DailyCalendarStats existing = byDay.get(day);
        if (existing != null) {
            return existing;
        }
        DailyCalendarStats created = new DailyCalendarStats(day);
        byDay.put(day, created);
        return created;
    }

    private QueryFilter buildStatisticsFilter(String subjectFilter, String startMonth, String endMonth) {
        StringBuilder where = new StringBuilder(
                " WHERE p.timestamp_ms > 0 AND p.estado != 'Pendiente'"
        );
        List<String> args = new ArrayList<>();

        String subject = trimToNull(subjectFilter);
        if (subject != null && !"Todas".equals(subject)) {
            where.append(" AND q.subject_name = ?");
            args.add(subject);
        }

        String start = trimToNull(startMonth);
        if (start != null && !"Todos".equals(start)) {
            where.append(" AND strftime('%Y-%m', p.timestamp_ms / 1000, 'unixepoch', 'localtime') >= ?");
            args.add(start);
        }

        String end = trimToNull(endMonth);
        if (end != null && !"Todos".equals(end)) {
            where.append(" AND strftime('%Y-%m', p.timestamp_ms / 1000, 'unixepoch', 'localtime') <= ?");
            args.add(end);
        }

        return new QueryFilter(where.toString(), args.toArray(new String[0]));
    }

    private String getAppState(SQLiteDatabase db, String key) {
        try (Cursor cursor = db.query(
                TABLE_APP_STATE,
                new String[]{"value"},
                "key = ?",
                new String[]{key},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        }
    }

    private int getPointsInternal(SQLiteDatabase db) {
        String value = getAppState(db, STATE_POINTS);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(value), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int getIntAppState(SQLiteDatabase db, String key) {
        String value = getAppState(db, key);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(value), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void setPoints(SQLiteDatabase db, int points) {
        setAppState(db, STATE_POINTS, String.valueOf(Math.max(points, 0)));
    }

    private long getLongAppState(SQLiteDatabase db, String key) {
        String value = getAppState(db, key);
        if (value == null) {
            return 0L;
        }
        try {
            return Math.max(Long.parseLong(value), 0L);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void setAppState(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        db.insertWithOnConflict(TABLE_APP_STATE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void setAppStateIfPresent(SQLiteDatabase db, String key, String value) {
        if (value != null) {
            setAppState(db, key, value);
        }
    }

    private void clearCloudState(SQLiteDatabase db) {
        db.delete(TABLE_APP_STATE, "key IN (?, ?, ?, ?, ?, ?)", new String[]{
                STATE_POINTS,
                STATE_POSITIVE_STREAK,
                STATE_NEGATIVE_STREAK,
                STATE_BEST_POSITIVE_STREAK,
                STATE_BEST_NEGATIVE_STREAK,
                STATE_CLOUD_LAST_MODIFIED
        });
    }

    private void touchCloudState(SQLiteDatabase db) {
        setAppState(db, STATE_CLOUD_LAST_MODIFIED, String.valueOf(System.currentTimeMillis()));
    }

    private void deleteIfExists(File file) {
        if (file.exists()) {
            file.delete();
        }
    }

    private String mapLegacyEstado(String estado) {
        String normalized = trimToNull(estado);
        if (normalized == null) {
            return "Pendiente";
        }
        if ("Dudada".equals(normalized)) {
            return "Duda_Fallada";
        }
        return normalized;
    }

    private boolean isPositiveStatus(String estado) {
        return "Acertada".equals(estado) || "Duda_Primera".equals(estado);
    }

    private boolean isNegativeStatus(String estado) {
        return "Fallada".equals(estado) || "Duda_Segunda".equals(estado) || "Duda_Fallada".equals(estado);
    }

    private boolean isThreshold(int streakValue) {
        for (int threshold : STREAK_THRESHOLDS) {
            if (threshold == streakValue) {
                return true;
            }
        }
        return false;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replace("\uFEFF", "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireText(String value, String fieldName) throws IOException {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IOException("Falta el campo obligatorio " + fieldName + " en el CSV de preguntas.");
        }
        return normalized;
    }

    private int parseRequiredInt(String value, String fieldName) throws IOException {
        String normalized = requireText(value, fieldName);
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            throw new IOException("Valor numerico invalido para " + fieldName + ": " + normalized, e);
        }
    }

    public static class Badge {
        public final String id;
        public final String nombre;
        public final String descripcion;
        public final String imagenDrawable;
        public final boolean desbloqueada;
        public final long fechaDesbloqueo;

        public Badge(String id, String nombre, String descripcion, String imagenDrawable, boolean desbloqueada, long fechaDesbloqueo) {
            this.id = id;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.imagenDrawable = imagenDrawable;
            this.desbloqueada = desbloqueada;
            this.fechaDesbloqueo = fechaDesbloqueo;
        }
    }

    public static class QuestionRecord {
        public final String year;
        public final String questionNumber;
        public final String statement;
        public final List<String> options;
        public final int correctIndex;

        public QuestionRecord(String year, String questionNumber, String statement, List<String> options, int correctIndex) {
            this.year = year;
            this.questionNumber = questionNumber;
            this.statement = statement;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    public static class DailyCount {
        public final String day;
        public final int count;

        public DailyCount(String day, int count) {
            this.day = day;
            this.count = count;
        }
    }

    public static class ChartEntry {
        public final String label;
        public final int value;

        public ChartEntry(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    public static class DailyAccuracyEntry {
        public final String day;
        public final double averagePercent;

        public DailyAccuracyEntry(String day, double averagePercent) {
            this.day = day;
            this.averagePercent = averagePercent;
        }
    }

    public static class DailyCalendarStats {
        public final String day;
        public int questionsDone;
        public int examsCompleted;
        public double averageScorePercent;
        public int correctAnswers;
        public int failedAnswers;
        public int blankAnswers;

        public DailyCalendarStats(String day) {
            this.day = day;
        }
    }

    public static class StatisticsData {
        public int totalDone;
        public int totalCorrect;
        public int totalFailed;
        public int totalDoubted;
        public int doubtFirst;
        public int doubtSecond;
        public int bestPositiveStreak;
        public int bestNegativeStreak;
        public long averageDurationMs;
        public int consecutiveDaysStreak;
        public final List<DailyCount> dailyCounts = new ArrayList<>();
    }

    public static class StreakAchievement {
        public final boolean positive;
        public final int streakValue;

        public StreakAchievement(boolean positive, int streakValue) {
            this.positive = positive;
            this.streakValue = streakValue;
        }
    }

    private static class QueryFilter {
        final String whereClause;
        final String[] args;

        QueryFilter(String whereClause, String[] args) {
            this.whereClause = whereClause;
            this.args = args;
        }
    }
}
