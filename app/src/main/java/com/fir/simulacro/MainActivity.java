package com.fir.simulacro;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String PAUSED_QUIZ_PREFS = "paused_quiz_prefs";
    private static final String PAUSED_QUIZ_KEY = "paused_quiz_json";
    private static final int REAL_EXAM_QUESTION_COUNT = 200;
    private static final int WRONG_ANSWERS_PER_PENALTY = 3;
    private static final int[] OPTION_BUTTON_BACKGROUNDS = {
            R.drawable.option_button_neutral,
            R.drawable.option_button_neutral,
            R.drawable.option_button_neutral,
            R.drawable.option_button_neutral,
            R.drawable.option_button_neutral
    };

    private View rootScrollView;
    private View startLayout;
    private View quizLayout;
    private View resultLayout;
    private Button startButton;
    private Button resumePausedButton;
    private Button redeemButton;
    private Button failuresButton;
    private Button doubtsButton;
    private Button newQuestionsButton;
    private Button statisticsButton;
    private Button badgesButton;
    private View settingsButton;
    private TextView timerText;
    private TextView progressText;
    private TextView yearText;
    private TextView questionText;
    private LinearLayout optionsContainer;
    private Button blankButton;
    private Button exitQuizButton;
    private Button pauseQuizButton;
    private TextView feedbackText;
    private Button nextButton;
    private TextView pointsText;
    private TextView scoreText;
    private TextView netasText;
    private TextView finalTimeText;
    private TextView earnedPointsText;
    private Button downloadCsvButton;
    private Button restartButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startTimeMs = 0L;
    private long examStartEpochMs = 0L;
    private boolean timerRunning = false;
    private QuizMode currentQuizMode = QuizMode.ALL;

    private List<FirQuestion> selectedQuestions = new ArrayList<>();
    private int currentIndex = 0;
    private int score = 0;
    private final List<AnswerResult> results = new ArrayList<>();
    private long lastElapsedMs = 0L;
    private long currentQuestionStartMs = 0L;
    private final List<CheckBox> optionTickBoxes = new ArrayList<>();
    private final List<Button> optionButtons = new ArrayList<>();
    private int selectedTickIndex = -1;
    private boolean syncingTickSelection = false;
    private AppDatabaseHelper appDatabaseHelper;
    private FirQuestionsDbHelper firQuestionsDbHelper;
    private QuestionProgressDbHelper questionProgressDbHelper;

    private final ActivityResultLauncher<String> createCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri != null) {
                    saveResultsCsv(uri);
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    NotificationScheduler.scheduleDailyReminder(this);
                }
            });

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) return;
            long elapsed = SystemClock.elapsedRealtime() - startTimeMs;
            lastElapsedMs = elapsed;
            timerText.setText("Tiempo: " + formatElapsed(elapsed));
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        appDatabaseHelper = new AppDatabaseHelper(this);
        firQuestionsDbHelper = new FirQuestionsDbHelper(this);
        questionProgressDbHelper = new QuestionProgressDbHelper(this);
        setupListeners();
        updatePausedQuizUi();

        NotificationScheduler.createNotificationChannel(this);
        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                NotificationScheduler.scheduleDailyReminder(this);
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Android < 13: no requiere permiso en runtime
            NotificationScheduler.scheduleDailyReminder(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerRunning = false;
        handler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPointsUi();
        updatePausedQuizUi();
    }

    private void bindViews() {
        rootScrollView = findViewById(R.id.rootScrollView);
        startLayout = findViewById(R.id.startLayout);
        quizLayout = findViewById(R.id.quizLayout);
        resultLayout = findViewById(R.id.resultLayout);
        startButton = findViewById(R.id.startButton);
        resumePausedButton = findViewById(R.id.resumePausedButton);
        redeemButton = findViewById(R.id.redeemButton);
        failuresButton = findViewById(R.id.failuresButton);
        doubtsButton = findViewById(R.id.doubtsButton);
        newQuestionsButton = findViewById(R.id.newQuestionsButton);
        statisticsButton = findViewById(R.id.statisticsButton);
        badgesButton = findViewById(R.id.badgesButton);
        settingsButton = findViewById(R.id.settingsButton);
        timerText = findViewById(R.id.timerText);
        progressText = findViewById(R.id.progressText);
        yearText = findViewById(R.id.yearText);
        questionText = findViewById(R.id.questionText);
        optionsContainer = findViewById(R.id.optionsContainer);
        blankButton = findViewById(R.id.blankButton);
        exitQuizButton = findViewById(R.id.exitQuizButton);
        pauseQuizButton = findViewById(R.id.pauseQuizButton);
        feedbackText = findViewById(R.id.feedbackText);
        nextButton = findViewById(R.id.nextButton);
        pointsText = findViewById(R.id.pointsText);
        scoreText = findViewById(R.id.scoreText);
        netasText = findViewById(R.id.netasText);
        finalTimeText = findViewById(R.id.finalTimeText);
        earnedPointsText = findViewById(R.id.earnedPointsText);
        downloadCsvButton = findViewById(R.id.downloadCsvButton);
        restartButton = findViewById(R.id.restartButton);
    }

    private void setupListeners() {
        startButton.setOnClickListener(v -> startSimulation(null, "No hay preguntas disponibles para empezar.", QuizMode.ALL));
        resumePausedButton.setOnClickListener(v -> resumePausedQuiz());
        redeemButton.setOnClickListener(v -> startActivity(new Intent(this, RewardsActivity.class)));
        failuresButton.setOnClickListener(v -> startSimulation(
                createStateList("Fallada", "Duda_Fallada"),
                "No hay preguntas en Fallada o Duda_Fallada.",
                QuizMode.FAILURES
        ));
        doubtsButton.setOnClickListener(v -> startSimulation(
                createStateList("Duda_Primera", "Duda_Segunda"),
                "No hay preguntas en Duda_Primera o Duda_Segunda.",
                QuizMode.DOUBTS
        ));
        newQuestionsButton.setOnClickListener(v -> startSimulation(
                createStateList("Pendiente"),
                "No hay preguntas pendientes.",
                QuizMode.NEW
        ));
        statisticsButton.setOnClickListener(v -> startActivity(new Intent(this, StatisticsActivity.class)));
        badgesButton.setOnClickListener(v -> startActivity(new Intent(this, BadgesActivity.class)));
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        nextButton.setOnClickListener(v -> moveToNextQuestion());
        blankButton.setOnClickListener(v -> onQuestionMarkedWithoutAnswer("Vacia"));
        exitQuizButton.setOnClickListener(v -> confirmExitQuiz());
        pauseQuizButton.setOnClickListener(v -> pauseQuiz());
        restartButton.setOnClickListener(v -> showStartScreen());
        downloadCsvButton.setOnClickListener(v ->
                createCsvLauncher.launch("resultado_simulacro_enfermeria.csv")
        );
        applyBackgroundForMode(QuizMode.HOME);
        refreshPointsUi();
    }

    private void startSimulation(List<String> statesFilter, String emptyMessage, QuizMode quizMode) {
        clearPausedQuiz();
        List<FirQuestion> allQuestions = loadQuestionsFromDatabase(statesFilter);
        if (allQuestions.isEmpty()) {
            Toast.makeText(
                    this,
                    emptyMessage,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        currentQuizMode = quizMode;
        applyBackgroundForMode(quizMode);
        Collections.shuffle(allQuestions);
        int quizSize = Math.min(UserSettings.getQuizQuestionCount(this), allQuestions.size());
        selectedQuestions = new ArrayList<>(allQuestions.subList(0, quizSize));
        currentIndex = 0;
        score = 0;
        results.clear();
        feedbackText.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);

        startTimeMs = SystemClock.elapsedRealtime();
        examStartEpochMs = System.currentTimeMillis();
        timerRunning = true;
        handler.removeCallbacks(timerRunnable);
        handler.post(timerRunnable);

        startLayout.setVisibility(View.GONE);
        resultLayout.setVisibility(View.GONE);
        quizLayout.setVisibility(View.VISIBLE);

        showQuestion();
    }

    private void moveToNextQuestion() {
        currentIndex++;
        if (currentIndex >= selectedQuestions.size()) {
            finishSimulation();
            return;
        }
        feedbackText.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        showQuestion();
    }

    private void showQuestion() {
        FirQuestion q = selectedQuestions.get(currentIndex);
        currentQuestionStartMs = SystemClock.elapsedRealtime();
        progressText.setText("Pregunta " + (currentIndex + 1) + "/" + selectedQuestions.size());
        yearText.setText("Año: " + q.year);
        questionText.setText(q.statement);
        questionText.setMaxLines(Integer.MAX_VALUE);
        questionText.setEllipsize(null);

        optionsContainer.removeAllViews();
        optionTickBoxes.clear();
        optionButtons.clear();
        selectedTickIndex = -1;
        setActionButtonsEnabled(true);
        for (int i = 0; i < q.options.size(); i++) {
            final int index = i;
            String option = q.options.get(i);
            LinearLayout optionRow = new LinearLayout(this);
            optionRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                rowParams.topMargin = dpToPx(12);
            }
            optionRow.setLayoutParams(rowParams);

            CheckBox tickBox = new CheckBox(this);
            LinearLayout.LayoutParams tickParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tickParams.setMarginEnd(dpToPx(8));
            tickBox.setLayoutParams(tickParams);
            tickBox.setText("");
            tickBox.setOnClickListener(v -> onTickSelectionChanged(index, tickBox.isChecked()));
            optionTickBoxes.add(tickBox);

            Button btn = new Button(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.weight = 1f;
            btn.setLayoutParams(params);
            btn.setBackgroundResource(OPTION_BUTTON_BACKGROUNDS[i % OPTION_BUTTON_BACKGROUNDS.length]);
            btn.setText(option);
            btn.setAllCaps(false);
            btn.setTextColor(Color.parseColor("#4A4A4A"));
            btn.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            btn.setSingleLine(false);
            btn.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            btn.setOnClickListener(v -> onOptionSelected(index, btn.getText().toString()));
            optionButtons.add(btn);

            optionRow.addView(tickBox);
            optionRow.addView(btn);
            optionsContainer.addView(optionRow);
        }
    }

    private void onOptionSelected(int selectedIndex, String selectedText) {
        FirQuestion q = selectedQuestions.get(currentIndex);
        boolean correct = selectedIndex == q.correctIndex;
        String status = resolveAnswerStatus(q, selectedIndex);
        long durationMs = SystemClock.elapsedRealtime() - currentQuestionStartMs;
        long timestampMs = System.currentTimeMillis();
        highlightAnswerOptions(selectedIndex, q.correctIndex, correct);

        results.add(new AnswerResult(
                q.year,
                q.questionNumber,
                q.statement,
                selectedText,
                correct,
                status
        ));

        questionProgressDbHelper.saveLatestProgress(
                q.year,
                q.questionNumber,
                status,
                durationMs,
                timestampMs
        );
        maybeShowStreakAchievement(status);

        setOptionsEnabled(false);
        setActionButtonsEnabled(false);
        nextButton.setVisibility(View.VISIBLE);
    }

    private void onQuestionMarkedWithoutAnswer(String status) {
        FirQuestion q = selectedQuestions.get(currentIndex);
        long durationMs = SystemClock.elapsedRealtime() - currentQuestionStartMs;
        long timestampMs = System.currentTimeMillis();

        highlightAnswerOptions(-1, q.correctIndex, false);

        results.add(new AnswerResult(
                q.year,
                q.questionNumber,
                q.statement,
                status,
                false,
                status
        ));

        questionProgressDbHelper.saveLatestProgress(
                q.year,
                q.questionNumber,
                status,
                durationMs,
                timestampMs
        );
        maybeShowStreakAchievement(status);

        setOptionsEnabled(false);
        setActionButtonsEnabled(false);
        nextButton.setVisibility(View.VISIBLE);
    }

    private void maybeShowStreakAchievement(String status) {
        AppDatabaseHelper.StreakAchievement achievement = appDatabaseHelper.updateStreakForStatus(status);
        if (achievement == null) {
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_streak_achievement, null, false);
        ImageView achievementImage = dialogView.findViewById(R.id.achievementImage);
        TextView achievementTitle = dialogView.findViewById(R.id.achievementTitle);
        TextView achievementMessage = dialogView.findViewById(R.id.achievementMessage);
        Button continueButton = dialogView.findViewById(R.id.continueButton);

        achievementImage.setImageResource(getAchievementDrawable(achievement));
        if (achievement.positive) {
            achievementTitle.setText("Racha positiva de " + achievement.streakValue);
            achievementMessage.setText("Llevas " + achievement.streakValue + " aciertos seguidos.");
        } else {
            achievementTitle.setText("Racha negativa de " + achievement.streakValue);
            achievementMessage.setText("Llevas " + achievement.streakValue + " fallos seguidos.");
        }

        String badgeId = (achievement.positive ? "streak_pos_" : "streak_neg_") + achievement.streakValue;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        continueButton.setOnClickListener(v -> {
            dialog.dismiss();
            boolean newlyUnlocked = appDatabaseHelper.unlockBadge(badgeId);
            if (newlyUnlocked) {
                List<AppDatabaseHelper.Badge> badges = appDatabaseHelper.getAllBadges();
                for (AppDatabaseHelper.Badge b : badges) {
                    if (b.id.equals(badgeId)) {
                        showBadgeUnlockedDialog(b, null);
                        break;
                    }
                }
            }
        });
        dialog.show();
    }

    private void onTickSelectionChanged(int index, boolean checked) {
        if (syncingTickSelection) {
            return;
        }
        if (checked) {
            selectedTickIndex = index;
        } else if (selectedTickIndex == index) {
            selectedTickIndex = -1;
        }
        syncTickSelection();
    }

    private void syncTickSelection() {
        syncingTickSelection = true;
        for (int i = 0; i < optionTickBoxes.size(); i++) {
            optionTickBoxes.get(i).setChecked(i == selectedTickIndex);
        }
        syncingTickSelection = false;
    }

    private String resolveAnswerStatus(FirQuestion question, int selectedIndex) {
        boolean selectedCorrect = selectedIndex == question.correctIndex;
        boolean tickSelected = selectedTickIndex >= 0 && selectedTickIndex < question.options.size();
        boolean tickCorrect = tickSelected && selectedTickIndex == question.correctIndex;

        if (!tickSelected) {
            return selectedCorrect ? "Acertada" : "Fallada";
        }
        if (selectedCorrect) {
            return "Duda_Primera";
        }
        if (tickCorrect) {
            return "Duda_Segunda";
        }
        return "Duda_Fallada";
    }

    private String buildFeedbackText(FirQuestion question, String status) {
        switch (status) {
            case "Acertada":
                return "✅ Correcta";
            case "Fallada":
                return "❌ Incorrecta";
            case "Duda_Primera":
                return "✅ Dudada: acertaste con la respuesta pulsada.";
            case "Duda_Segunda":
                return "❌ Dudada";
            case "Duda_Fallada":
                return "❌ Dudada";
            case "Vacia":
                return "⬜ Pregunta dejada en blanco";
            default:
                return "";
        }
    }

    private void highlightAnswerOptions(int selectedIndex, int correctIndex, boolean correct) {
        for (int i = 0; i < optionButtons.size(); i++) {
            Button optionButton = optionButtons.get(i);
            optionButton.setBackgroundResource(R.drawable.option_button_neutral);
            optionButton.setAlpha(0.6f);
        }
        if (correct) {
            if (selectedIndex >= 0 && selectedIndex < optionButtons.size()) {
                Button selectedButton = optionButtons.get(selectedIndex);
                selectedButton.setBackgroundResource(R.drawable.option_button_correct);
                selectedButton.setAlpha(1f);
            }
            return;
        }
        if (selectedIndex >= 0 && selectedIndex < optionButtons.size()) {
            Button selectedButton = optionButtons.get(selectedIndex);
            selectedButton.setBackgroundResource(R.drawable.option_button_incorrect);
            selectedButton.setAlpha(1f);
        }
        if (correctIndex >= 0 && correctIndex < optionButtons.size()) {
            Button correctButton = optionButtons.get(correctIndex);
            correctButton.setBackgroundResource(R.drawable.option_button_correct);
            correctButton.setAlpha(1f);
        }
    }

    private void setOptionsEnabled(boolean enabled) {
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View row = optionsContainer.getChildAt(i);
            row.setEnabled(enabled);
            if (row instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) row;
                for (int j = 0; j < group.getChildCount(); j++) {
                    group.getChildAt(j).setEnabled(enabled);
                }
            }
        }
    }

    private void setActionButtonsEnabled(boolean enabled) {
        blankButton.setEnabled(enabled);
    }

    private void finishSimulation() {
        clearPausedQuiz();
        timerRunning = false;
        handler.removeCallbacks(timerRunnable);
        lastElapsedMs = SystemClock.elapsedRealtime() - startTimeMs;
        PointsStore.addPoints(this, score);
        int totalPoints = PointsStore.getPoints(this);

        int acertadas = 0, falladas = 0, dudaPrimera = 0, dudaSegunda = 0, dudaFallada = 0, vacias = 0;
        for (AnswerResult r : results) {
            switch (r.estado) {
                case "Acertada":    acertadas++;    break;
                case "Fallada":     falladas++;     break;
                case "Duda_Primera": dudaPrimera++; break;
                case "Duda_Segunda": dudaSegunda++; break;
                case "Duda_Fallada": dudaFallada++; break;
                case "Vacia":       vacias++;       break;
            }
        }
        double netas = calculateNetas();
        List<AppDatabaseHelper.Badge> postExamBadges = new ArrayList<>();
        boolean firstSimulationOfToday = false;
        if (results.size() == selectedQuestions.size()) {
            firstSimulationOfToday = !appDatabaseHelper.hasCompletedSimulacroToday();
            appDatabaseHelper.saveExamAttempt(
                    examStartEpochMs, lastElapsedMs,
                    acertadas, falladas, dudaPrimera, dudaSegunda, vacias, netas
            );
            postExamBadges.addAll(collectNewExamBadges(acertadas, falladas, dudaPrimera, dudaSegunda, dudaFallada, vacias));
            postExamBadges.addAll(checkAndGetDailyBadges());
            if (firstSimulationOfToday) {
                showFirstSimulationOfDayDialog(() -> showDailyBadgeSequence(postExamBadges, 0));
            } else {
                showDailyBadgeSequence(postExamBadges, 0);
            }
        }

        quizLayout.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);
        scoreText.setText(score + "/" + selectedQuestions.size());
        netasText.setText(String.format(Locale.getDefault(), "NETAS: %.2f", netas));
        finalTimeText.setText("Tiempo: " + formatElapsed(lastElapsedMs));
        earnedPointsText.setText("Has ganado " + score + " puntos. Total: " + totalPoints);
    }

    private void showFirstSimulationOfDayDialog(Runnable onDismiss) {
        int days = appDatabaseHelper.getConsecutiveDaysStreak();
        String title = days <= 1 ? "¡Buen comienzo!" : "¡Racha de " + days + " días!";
        String message = days <= 1
                ? "Has completado tu primer simulacro de hoy. ¡Sigue así y empieza tu racha!"
                : "Has completado tu primer simulacro de hoy.\n\n" +
                  "Llevas " + days + " días seguidos haciendo al menos un simulacro. " +
                  "¡Enhorabuena por la racha, sigue así!";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("¡A seguir!", (dialog, which) -> {
                    dialog.dismiss();
                    if (onDismiss != null) {
                        onDismiss.run();
                    }
                })
                .show();
    }

    private List<AppDatabaseHelper.Badge> collectNewExamBadges(int acertadas, int falladas, int dudaPrimera, int dudaSegunda, int dudaFallada, int vacias) {
        List<AppDatabaseHelper.Badge> unlocked = new ArrayList<>();
        List<AppDatabaseHelper.Badge> allBadges = appDatabaseHelper.getAllBadges();

        if (acertadas + dudaPrimera == selectedQuestions.size()
                && falladas == 0 && dudaSegunda == 0 && dudaFallada == 0 && vacias == 0) {
            maybeAddUnlockedBadge(unlocked, allBadges, "perfect_exam");
        }

        if (appDatabaseHelper.hasCompletedAllQuestions()) {
            maybeAddUnlockedBadge(unlocked, allBadges, "full_database");
        }

        if (appDatabaseHelper.hasAnyQuestionWithState("Duda_Segunda")) {
            maybeAddUnlockedBadge(unlocked, allBadges, "first_doubt_hit");
        }

        int attempts = appDatabaseHelper.getCompletedExamAttemptsCount();
        if (attempts >= 10) maybeAddUnlockedBadge(unlocked, allBadges, "exams_10");
        if (attempts >= 25) maybeAddUnlockedBadge(unlocked, allBadges, "exams_25");
        if (attempts >= 50) maybeAddUnlockedBadge(unlocked, allBadges, "exams_50");
        if (attempts >= 100) maybeAddUnlockedBadge(unlocked, allBadges, "exams_100");

        if (!selectedQuestions.isEmpty()) {
            int thresholdPercent = UserSettings.getAccuracyThresholdPercent(this);
            double accuracyPercent = ((acertadas + dudaPrimera) * 100.0) / selectedQuestions.size();
            if (accuracyPercent >= thresholdPercent) {
                maybeAddUnlockedBadge(unlocked, allBadges, "superando_limites");
            }
        }

        return unlocked;
    }

    private void maybeAddUnlockedBadge(List<AppDatabaseHelper.Badge> target, List<AppDatabaseHelper.Badge> allBadges, String badgeId) {
        boolean newlyUnlocked = appDatabaseHelper.unlockBadge(badgeId);
        if (!newlyUnlocked) return;
        for (AppDatabaseHelper.Badge b : allBadges) {
            if (badgeId.equals(b.id)) {
                target.add(b);
                break;
            }
        }
    }

    private void showStartScreen() {
        timerRunning = false;
        handler.removeCallbacks(timerRunnable);
        currentQuizMode = QuizMode.HOME;
        applyBackgroundForMode(QuizMode.HOME);
        startLayout.setVisibility(View.VISIBLE);
        quizLayout.setVisibility(View.GONE);
        resultLayout.setVisibility(View.GONE);
        timerText.setText("Tiempo: 00:00");
        refreshPointsUi();
        updatePausedQuizUi();
    }

    private void saveResultsCsv(Uri uri) {
        try (OutputStream stream = getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(stream)) {
            writer.write("Año,Número de pregunta,Enunciado,Respuesta marcada,Resultado\n");
            for (AnswerResult r : results) {
                String safeStatement = r.statement.replace("\"", "\"\"");
                String safeSelected = r.selectedAnswer.replace("\"", "\"\"");
                String resultText = r.correct ? "acertaste" : "fallaste";
                writer.write(r.year + "," + r.questionNumber + ",\"" + safeStatement + "\",\"" + safeSelected + "\"," + resultText + "\n");
            }
            writer.flush();
            Toast.makeText(this, "CSV guardado correctamente", Toast.LENGTH_LONG).show();
            shareResultsCsv(uri);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar el CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareResultsCsv(Uri uri) {
        Intent baseIntent = new Intent(Intent.ACTION_SEND);
        baseIntent.setType("text/csv");
        baseIntent.putExtra(Intent.EXTRA_STREAM, uri);
        baseIntent.putExtra(Intent.EXTRA_SUBJECT, "Resultados simulacro OPE SESPA");
        baseIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent whatsappIntent = new Intent(baseIntent);
        whatsappIntent.setPackage("com.whatsapp");

        if (whatsappIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(whatsappIntent);
        } else {
            startActivity(Intent.createChooser(baseIntent, "Compartir resultados CSV"));
        }
    }

    private List<FirQuestion> loadQuestionsFromDatabase(List<String> statesFilter) {
        List<FirQuestion> questions = new ArrayList<>();
        try {
            List<FirQuestionsDbHelper.QuestionRecord> sourceQuestions = statesFilter == null
                    ? firQuestionsDbHelper.loadAllQuestions()
                    : firQuestionsDbHelper.loadQuestionsByStates(statesFilter);
            for (FirQuestionsDbHelper.QuestionRecord question : sourceQuestions) {
                questions.add(new FirQuestion(
                        question.year,
                        question.questionNumber,
                        question.statement,
                        question.options,
                        question.correctIndex
                ));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error cargando base de preguntas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return questions;
    }

    private List<String> createStateList(String... states) {
        List<String> stateList = new ArrayList<>();
        Collections.addAll(stateList, states);
        return stateList;
    }

    private void refreshPointsUi() {
        if (pointsText != null) {
            pointsText.setText("Puntos: " + PointsStore.getPoints(this));
        }
    }

    private void confirmExitQuiz() {
        if (!isQuizActive()) {
            showStartScreen();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Salir del examen")
                .setMessage("Se perderá el progreso no terminado de este examen. ¿Quieres salir y descartarlo?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salir", (dialog, which) -> exitQuizWithoutSaving())
                .show();
    }

    private void exitQuizWithoutSaving() {
        clearPausedQuiz();
        resetCurrentQuizState();
        showStartScreen();
        Toast.makeText(this, "Examen descartado.", Toast.LENGTH_SHORT).show();
    }

    private void pauseQuiz() {
        if (!isQuizActive()) {
            showStartScreen();
            return;
        }

        try {
            savePausedQuiz();
            resetCurrentQuizState();
            showStartScreen();
            Toast.makeText(this, "Examen pausado. Puedes reanudarlo cuando quieras.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo pausar el examen: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resumePausedQuiz() {
        String pausedJson = getPausedQuizPrefs().getString(PAUSED_QUIZ_KEY, null);
        if (pausedJson == null) {
            updatePausedQuizUi();
            Toast.makeText(this, "No hay ningún examen en pausa.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject(pausedJson);
            selectedQuestions = parsePausedQuestions(json.getJSONArray("selectedQuestions"));
            if (selectedQuestions.isEmpty()) {
                throw new IllegalStateException("No se han podido restaurar las preguntas del examen en pausa.");
            }

            currentQuizMode = QuizMode.valueOf(json.getString("quizMode"));
            currentIndex = Math.max(0, Math.min(json.getInt("currentIndex"), selectedQuestions.size() - 1));
            score = Math.max(0, json.getInt("score"));
            examStartEpochMs = json.optLong("examStartEpochMs", System.currentTimeMillis());
            lastElapsedMs = Math.max(0L, json.optLong("elapsedMs", 0L));
            long currentQuestionElapsedMs = Math.max(0L, json.optLong("currentQuestionElapsedMs", 0L));
            int restoredTickIndex = json.optInt("selectedTickIndex", -1);
            boolean feedbackVisible = json.optBoolean("feedbackVisible", false);
            String restoredFeedbackText = json.optString("feedbackText", "");
            boolean nextVisible = json.optBoolean("nextVisible", false);

            results.clear();
            JSONArray storedResults = json.optJSONArray("results");
            if (storedResults != null) {
                for (int i = 0; i < storedResults.length(); i++) {
                    JSONObject resultJson = storedResults.getJSONObject(i);
                    results.add(new AnswerResult(
                            resultJson.getString("year"),
                            resultJson.getString("questionNumber"),
                            resultJson.getString("statement"),
                            resultJson.getString("selectedAnswer"),
                            resultJson.getBoolean("correct"),
                            resultJson.getString("estado")
                    ));
                }
            }

            startTimeMs = SystemClock.elapsedRealtime() - lastElapsedMs;
            timerRunning = true;
            handler.removeCallbacks(timerRunnable);
            handler.post(timerRunnable);

            startLayout.setVisibility(View.GONE);
            resultLayout.setVisibility(View.GONE);
            quizLayout.setVisibility(View.VISIBLE);
            applyBackgroundForMode(currentQuizMode);
            showQuestion();
            currentQuestionStartMs = SystemClock.elapsedRealtime() - currentQuestionElapsedMs;

            if (restoredTickIndex >= 0 && restoredTickIndex < optionTickBoxes.size()) {
                selectedTickIndex = restoredTickIndex;
                syncTickSelection();
            }

            feedbackText.setVisibility(View.GONE);

            if (nextVisible) {
                setOptionsEnabled(false);
                setActionButtonsEnabled(false);
                nextButton.setVisibility(View.VISIBLE);
            } else {
                setOptionsEnabled(true);
                setActionButtonsEnabled(true);
                nextButton.setVisibility(View.GONE);
            }

            clearPausedQuiz();
            Toast.makeText(this, "Examen reanudado.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            clearPausedQuiz();
            Toast.makeText(this, "No se pudo reanudar el examen en pausa.", Toast.LENGTH_LONG).show();
        }
    }

    private List<FirQuestion> parsePausedQuestions(JSONArray array) throws Exception {
        List<FirQuestion> questions = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject questionJson = array.getJSONObject(i);
            JSONArray optionsJson = questionJson.getJSONArray("options");
            List<String> options = new ArrayList<>();
            for (int j = 0; j < optionsJson.length(); j++) {
                options.add(optionsJson.getString(j));
            }
            questions.add(new FirQuestion(
                    questionJson.getString("year"),
                    questionJson.getString("questionNumber"),
                    questionJson.getString("statement"),
                    options,
                    questionJson.getInt("correctIndex")
            ));
        }
        return questions;
    }

    private void savePausedQuiz() throws Exception {
        JSONObject json = new JSONObject();
        json.put("quizMode", currentQuizMode.name());
        json.put("currentIndex", currentIndex);
        json.put("score", score);
        json.put("examStartEpochMs", examStartEpochMs);
        json.put("elapsedMs", Math.max(0L, SystemClock.elapsedRealtime() - startTimeMs));
        json.put("currentQuestionElapsedMs", Math.max(0L, SystemClock.elapsedRealtime() - currentQuestionStartMs));
        json.put("selectedTickIndex", selectedTickIndex);
        json.put("feedbackVisible", feedbackText.getVisibility() == View.VISIBLE);
        json.put("feedbackText", feedbackText.getText() == null ? "" : feedbackText.getText().toString());
        json.put("nextVisible", nextButton.getVisibility() == View.VISIBLE);

        JSONArray questionsArray = new JSONArray();
        for (FirQuestion question : selectedQuestions) {
            JSONObject questionJson = new JSONObject();
            questionJson.put("year", question.year);
            questionJson.put("questionNumber", question.questionNumber);
            questionJson.put("statement", question.statement);
            questionJson.put("correctIndex", question.correctIndex);

            JSONArray optionsArray = new JSONArray();
            for (String option : question.options) {
                optionsArray.put(option);
            }
            questionJson.put("options", optionsArray);
            questionsArray.put(questionJson);
        }
        json.put("selectedQuestions", questionsArray);

        JSONArray resultsArray = new JSONArray();
        for (AnswerResult result : results) {
            JSONObject resultJson = new JSONObject();
            resultJson.put("year", result.year);
            resultJson.put("questionNumber", result.questionNumber);
            resultJson.put("statement", result.statement);
            resultJson.put("selectedAnswer", result.selectedAnswer);
            resultJson.put("correct", result.correct);
            resultJson.put("estado", result.estado);
            resultsArray.put(resultJson);
        }
        json.put("results", resultsArray);

        getPausedQuizPrefs().edit().putString(PAUSED_QUIZ_KEY, json.toString()).apply();
        updatePausedQuizUi();
    }

    private void clearPausedQuiz() {
        getPausedQuizPrefs().edit().remove(PAUSED_QUIZ_KEY).apply();
        updatePausedQuizUi();
    }

    private void updatePausedQuizUi() {
        if (resumePausedButton != null) {
            resumePausedButton.setVisibility(hasPausedQuiz() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean hasPausedQuiz() {
        return getPausedQuizPrefs().contains(PAUSED_QUIZ_KEY);
    }

    private SharedPreferences getPausedQuizPrefs() {
        return getApplicationContext().getSharedPreferences(PAUSED_QUIZ_PREFS, MODE_PRIVATE);
    }

    private boolean isQuizActive() {
        return quizLayout != null
                && quizLayout.getVisibility() == View.VISIBLE
                && !selectedQuestions.isEmpty()
                && currentIndex >= 0
                && currentIndex < selectedQuestions.size();
    }

    private void resetCurrentQuizState() {
        timerRunning = false;
        handler.removeCallbacks(timerRunnable);
        startTimeMs = 0L;
        examStartEpochMs = 0L;
        lastElapsedMs = 0L;
        currentQuestionStartMs = 0L;
        selectedQuestions = new ArrayList<>();
        currentIndex = 0;
        score = 0;
        results.clear();
        selectedTickIndex = -1;
        syncingTickSelection = false;
        feedbackText.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        optionsContainer.removeAllViews();
        optionTickBoxes.clear();
    }

    private double calculateNetas() {
        if (selectedQuestions.isEmpty()) {
            return 0.0;
        }
        int fallos = 0;
        for (AnswerResult result : results) {
            if (!result.correct && !"Vacia".equals(result.selectedAnswer)) {
                fallos++;
            }
        }
        // Penalizacion: (fallos/2) * (85/N) donde N = numero de preguntas del simulacro
        double penalty = (fallos / 2.0) * (85.0 / selectedQuestions.size());
        return score - penalty;
    }

    private String formatElapsed(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    private void applyBackgroundForMode(QuizMode mode) {
        if (rootScrollView != null) {
            rootScrollView.setBackgroundResource(mode.backgroundResId);
        }
    }

    private int getAchievementDrawable(AppDatabaseHelper.StreakAchievement achievement) {
        if (achievement.positive) {
            switch (achievement.streakValue) {
                case 3:
                    return R.drawable.streak_positive_3_img;
                case 5:
                    return R.drawable.streak_positive_5_img;
                case 10:
                    return R.drawable.streak_positive_10_img;
                case 20:
                    return R.drawable.streak_positive_20_img;
                case 25:
                    return R.drawable.streak_positive_25;
                case 50:
                    return R.drawable.streak_positive_50;
                case 100:
                    return R.drawable.streak_positive_100;
                default:
                    return R.drawable.streak_positive_3_img;
            }
        }

        switch (achievement.streakValue) {
            case 3:
                return R.drawable.streak_negative_3_img;
            case 5:
                return R.drawable.streak_negative_5_img;
            case 10:
                return R.drawable.streak_negative_10_img;
            case 20:
                return R.drawable.streak_negative_20;
            case 25:
                return R.drawable.streak_negative_25;
            case 50:
                return R.drawable.streak_negative_50;
            case 100:
                return R.drawable.streak_negative_100;
            default:
                return R.drawable.streak_negative_3_img;
        }
    }

    private void showBadgeUnlockedDialog(AppDatabaseHelper.Badge badge, Runnable onDismiss) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_badge_unlocked, null, false);
        ImageView img = dialogView.findViewById(R.id.badgeImage);
        TextView nameView = dialogView.findViewById(R.id.badgeName);
        TextView descView = dialogView.findViewById(R.id.badgeDesc);
        Button continueBtn = dialogView.findViewById(R.id.continueButton);

        int resId = 0;
        if (badge.imagenDrawable != null) {
            resId = getResources().getIdentifier(badge.imagenDrawable, "drawable", getPackageName());
        }
        img.setImageResource(resId != 0 ? resId : R.drawable.badge_locked_default);
        nameView.setText(badge.nombre);
        descView.setText(badge.descripcion);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        continueBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDismiss != null) onDismiss.run();
        });
        dialog.show();
    }

    private java.util.List<AppDatabaseHelper.Badge> checkAndGetDailyBadges() {
        int days = appDatabaseHelper.getConsecutiveDaysStreak();
        int[] thresholds = {1, 3, 5, 7};
        String[] badgeIds = {"daily_1", "daily_3", "daily_5", "daily_7"};

        java.util.List<AppDatabaseHelper.Badge> toShow = new java.util.ArrayList<>();
        java.util.List<AppDatabaseHelper.Badge> allBadges = appDatabaseHelper.getAllBadges();

        for (int i = 0; i < thresholds.length; i++) {
            if (days >= thresholds[i]) {
                boolean newlyUnlocked = appDatabaseHelper.unlockBadge(badgeIds[i]);
                if (newlyUnlocked) {
                    for (AppDatabaseHelper.Badge b : allBadges) {
                        if (b.id.equals(badgeIds[i])) {
                            toShow.add(b);
                            break;
                        }
                    }
                }
            }
        }
        return toShow;
    }

    private void showDailyBadgeSequence(java.util.List<AppDatabaseHelper.Badge> badges, int index) {
        if (index >= badges.size()) return;
        AppDatabaseHelper.Badge badge = badges.get(index);
        showBadgeUnlockedDialog(badge, () -> showDailyBadgeSequence(badges, index + 1));
    }

    private static class FirQuestion {
        final String year;
        final String questionNumber;
        final String statement;
        final List<String> options;
        final int correctIndex;

        FirQuestion(String year, String questionNumber, String statement, List<String> options, int correctIndex) {
            this.year = year;
            this.questionNumber = questionNumber;
            this.statement = statement;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    private static class AnswerResult {
        final String year;
        final String questionNumber;
        final String statement;
        final String selectedAnswer;
        final boolean correct;
        final String estado;

        AnswerResult(String year, String questionNumber, String statement, String selectedAnswer, boolean correct, String estado) {
            this.year = year;
            this.questionNumber = questionNumber;
            this.statement = statement;
            this.selectedAnswer = selectedAnswer;
            this.correct = correct;
            this.estado = estado;
        }
    }

    private enum QuizMode {
        HOME(R.drawable.bg_home),
        ALL(R.drawable.bg_mode_all),
        FAILURES(R.drawable.bg_mode_failures),
        DOUBTS(R.drawable.bg_mode_doubts),
        NEW(R.drawable.bg_mode_new);

        final int backgroundResId;

        QuizMode(int backgroundResId) {
            this.backgroundResId = backgroundResId;
        }
    }
}
