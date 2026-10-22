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
import android.widget.PopupWindow;
import android.graphics.drawable.AnimationDrawable;
import android.content.ClipboardManager;
import android.content.ClipData;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String PAUSED_QUIZ_PREFS = "paused_quiz_prefs";
    private static final String PAUSED_QUIZ_KEY = "paused_quiz_json";
    private static final String PREF_QUIZ_TUTORIAL_SEEN = "quiz_tutorial_seen";
    private static final String PREF_DAILY_INTRO_SEEN = "daily_intro_seen";
    private static final String DAILY_RESULT_PREFS = "daily_result_prefs";
    private static final String DAILY_RESULT_KEY = "daily_result_json";
    private static final int REAL_EXAM_QUESTION_COUNT = 200;
    private static final int WRONG_ANSWERS_PER_PENALTY = 3;
    private static final int[] OPTION_BUTTON_BACKGROUNDS = {
            R.drawable.option_button_neutral,
            R.drawable.option_button_neutral,
            R.drawable.option_button_neutral,
            R.drawable.option_button_neutral,
            R.drawable.option_button_neutral
    };

    private android.widget.ScrollView rootScrollView;
    private View startLayout;
    private View quizLayout;
    private View resultLayout;
    private Button startButton;
    private Button dailyButton;
    private Button examButton;
    private TextView resultTitle;
    private LinearLayout examReviewContainer;
    private LinearLayout examComparisonContainer;
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
    private Button finishNowButton;
    private Button shareExamButton;
    private TextView feedbackText;
    private Button nextButton;
    private TextView pointsText;
    private TextView scoreText;
    private TextView netasText;
    private TextView finalTimeText;
    private TextView earnedPointsText;
    private Button anotherSimulationButton;
    private Button restartButton;
    private Button reviewAnswersButton;
    private Button shareDailyButton;
    private Button copyPromptButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startTimeMs = 0L;
    private long examStartEpochMs = 0L;
    private boolean timerRunning = false;
    private QuizMode currentQuizMode = QuizMode.ALL;
    private long examLimitMs = 0L;
    private boolean examTimedOut = false;
    private final List<Integer> examSelectedIndices = new ArrayList<>();

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
            if (currentQuizMode == QuizMode.EXAM) {
                long remaining = examLimitMs - elapsed;
                if (remaining <= 0) {
                    timerText.setText("Tiempo restante: 00:00");
                    examTimedOut = true;
                    finishSimulation();
                    return;
                }
                timerText.setText("Tiempo restante: " + formatElapsed(remaining + 999L));
                handler.postDelayed(this, Math.min(1000L, remaining));
                return;
            }
            timerText.setText("Tiempo: " + formatElapsed(elapsed));
            handler.postDelayed(this, 1000L);
        }
    };

    private boolean crashSafeMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLogger.install(getApplicationContext());
        setContentView(R.layout.activity_main);
        InsetsHelper.apply(this);

        if (CrashLogger.hasCrash(this)) {
            crashSafeMode = true;
            CrashLogger.showIfPresent(this);
            return;
        }

        // Mostrar onboarding si es primera vez
        if (OnboardingHelper.isOnboardingNeeded(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

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
        if (crashSafeMode) return;
        CloudSyncManager.syncSilently(this);
        refreshPointsUi();
        updatePausedQuizUi();
    }

    private void bindViews() {
        rootScrollView = findViewById(R.id.rootScrollView);
        startLayout = findViewById(R.id.startLayout);
        quizLayout = findViewById(R.id.quizLayout);
        resultLayout = findViewById(R.id.resultLayout);
        startButton = findViewById(R.id.startButton);
        dailyButton = findViewById(R.id.dailyButton);
        examButton = findViewById(R.id.examButton);
        resultTitle = findViewById(R.id.resultTitle);
        examReviewContainer = findViewById(R.id.examReviewContainer);
        examComparisonContainer = findViewById(R.id.examComparisonContainer);
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
        finishNowButton = findViewById(R.id.finishNowButton);
        shareExamButton = findViewById(R.id.shareExamButton);
        feedbackText = findViewById(R.id.feedbackText);
        nextButton = findViewById(R.id.nextButton);
        pointsText = findViewById(R.id.pointsText);
        scoreText = findViewById(R.id.scoreText);
        netasText = findViewById(R.id.netasText);
        finalTimeText = findViewById(R.id.finalTimeText);
        earnedPointsText = findViewById(R.id.earnedPointsText);
        anotherSimulationButton = findViewById(R.id.anotherSimulationButton);
        restartButton = findViewById(R.id.restartButton);
        reviewAnswersButton = findViewById(R.id.reviewAnswersButton);
        shareDailyButton = findViewById(R.id.shareDailyButton);
        copyPromptButton = findViewById(R.id.copyPromptButton);
    }

    private void setupListeners() {
        startButton.setOnClickListener(v -> startSimulation(null, "No hay preguntas disponibles para empezar.", QuizMode.ALL));
        resumePausedButton.setOnClickListener(v -> resumePausedQuiz());
        dailyButton.setOnClickListener(v -> startDailySimulation());
        examButton.setOnClickListener(v -> showExamCommunityMenu());
        redeemButton.setOnClickListener(v -> startActivity(new Intent(this, RewardsActivity.class)));
        failuresButton.setOnClickListener(v -> startSimulation(
                createStateList("Fallada", "Duda_Fallada"),
                "No hay preguntas en Fallada o Duda_Fallada.",
                QuizMode.FAILURES
        ));
        doubtsButton.setOnClickListener(v -> {
            List<FirQuestion> doubtQuestions = loadQuestionsFromDatabase(createStateList("Duda_Primera", "Duda_Segunda"));
            if (doubtQuestions.isEmpty()) {
                showDoubtsEmptyDialog();
            } else {
                startSimulation(createStateList("Duda_Primera", "Duda_Segunda"),
                        "No hay preguntas en Duda_Primera o Duda_Segunda.", QuizMode.DOUBTS);
            }
        });
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
        finishNowButton.setOnClickListener(v -> confirmFinishExamNow());
        shareExamButton.setOnClickListener(v -> shareExamResult());
        restartButton.setOnClickListener(v -> showStartScreen());
        shareDailyButton.setOnClickListener(v -> shareDailyResult());
        reviewAnswersButton.setOnClickListener(v -> {
            boolean show = examReviewContainer.getVisibility() != View.VISIBLE;
            examReviewContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            reviewAnswersButton.setText(show ? "Ocultar mis respuestas" : "Repasar mis respuestas");
            if (show && rootScrollView != null) {
                rootScrollView.post(() -> {
                    android.graphics.Rect r = new android.graphics.Rect();
                    examReviewContainer.getDrawingRect(r);
                    rootScrollView.offsetDescendantRectToMyCoords(examReviewContainer, r);
                    rootScrollView.smoothScrollTo(0, r.top);
                });
            }
        });
        copyPromptButton.setOnClickListener(v -> copyQuestionPromptToClipboard());
        anotherSimulationButton.setOnClickListener(v ->
                startSimulation(null, "No hay preguntas disponibles para empezar.", QuizMode.ALL)
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
        launchQuiz();
    }

    private void startDailySimulation() {
        if (showDailyResultIfAlreadyDone()) {
            return;
        }
        clearPausedQuiz();
        List<FirQuestion> bank = new ArrayList<>();
        try {
            for (FirQuestionsDbHelper.QuestionRecord q : firQuestionsDbHelper.loadAllQuestionsUnfiltered()) {
                bank.add(new FirQuestion(q.comunidad, q.year, q.questionNumber, q.statement, q.options, q.correctIndex));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error cargando base de preguntas: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (bank.isEmpty()) {
            Toast.makeText(this, "No hay preguntas disponibles para empezar.", Toast.LENGTH_LONG).show();
            return;
        }

        currentQuizMode = QuizMode.DAILY;
        applyBackgroundForMode(QuizMode.DAILY);
        selectedQuestions = new ArrayList<>();
        for (int index : DailySimulacro.pickIndices(bank.size(), DailySimulacro.currentDayIndex())) {
            selectedQuestions.add(bank.get(index));
        }
        Toast.makeText(this, "Simulacro del día " + DailySimulacro.currentDayLabel(), Toast.LENGTH_SHORT).show();
        launchQuiz();
    }

    private void launchQuiz() {
        currentIndex = 0;
        score = 0;
        results.clear();
        examSelectedIndices.clear();
        examTimedOut = false;
        feedbackText.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        applyExamUiRestrictions();

        startTimeMs = SystemClock.elapsedRealtime();
        examStartEpochMs = System.currentTimeMillis();
        timerRunning = true;
        handler.removeCallbacks(timerRunnable);
        handler.post(timerRunnable);

        startLayout.setVisibility(View.GONE);
        resultLayout.setVisibility(View.GONE);
        quizLayout.setVisibility(View.VISIBLE);

        showQuestion();

        // Tutorial primera vez
        boolean tutorialSeen = getSharedPreferences(PAUSED_QUIZ_PREFS, MODE_PRIVATE)
                .getBoolean(PREF_QUIZ_TUTORIAL_SEEN, false);
        boolean dailyIntroSeen = getSharedPreferences(PAUSED_QUIZ_PREFS, MODE_PRIVATE)
                .getBoolean(PREF_DAILY_INTRO_SEEN, false);
        if (!tutorialSeen || (currentQuizMode == QuizMode.DAILY && !dailyIntroSeen)) {
            handler.postDelayed(this::showFirstQuizTutorial, 600);
        }
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
        if (rootScrollView != null) {
            rootScrollView.scrollTo(0, 0);
        }
        progressText.setText("Pregunta " + (currentIndex + 1) + "/" + selectedQuestions.size());
        yearText.setText(buildQuestionHeader(q));
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
        boolean exam = currentQuizMode == QuizMode.EXAM;
        if (exam && SystemClock.elapsedRealtime() - currentQuestionStartMs < 400L) {
            return;
        }
        FirQuestion q = selectedQuestions.get(currentIndex);
        boolean correct = selectedIndex == q.correctIndex;
        String status = resolveAnswerStatus(q, selectedIndex);
        long durationMs = SystemClock.elapsedRealtime() - currentQuestionStartMs;
        long timestampMs = System.currentTimeMillis();
        if (!exam) {
            highlightAnswerOptions(selectedIndex, q.correctIndex, correct);
        }

        if (correct) {
            score++;
        }

        results.add(new AnswerResult(
                q.comunidad,
                q.year,
                q.questionNumber,
                q.statement,
                selectedText,
                correct,
                status
        ));
        examSelectedIndices.add(selectedIndex);

        questionProgressDbHelper.saveLatestProgress(
                q.comunidad,
                q.year,
                q.questionNumber,
                status,
                durationMs,
                timestampMs
        );
        if (exam) {
            moveToNextQuestion();
            return;
        }
        maybeShowStreakAchievement(status);

        setOptionsEnabled(false);
        setActionButtonsEnabled(false);
        nextButton.setVisibility(View.VISIBLE);
    }

    private void onQuestionMarkedWithoutAnswer(String status) {
        boolean exam = currentQuizMode == QuizMode.EXAM;
        if (exam && SystemClock.elapsedRealtime() - currentQuestionStartMs < 400L) {
            return;
        }
        FirQuestion q = selectedQuestions.get(currentIndex);
        long durationMs = SystemClock.elapsedRealtime() - currentQuestionStartMs;
        long timestampMs = System.currentTimeMillis();

        if (!exam) {
            highlightAnswerOptions(-1, q.correctIndex, false);
        }

        results.add(new AnswerResult(
                q.comunidad,
                q.year,
                q.questionNumber,
                q.statement,
                status,
                false,
                status
        ));
        examSelectedIndices.add(-1);

        questionProgressDbHelper.saveLatestProgress(
                q.comunidad,
                q.year,
                q.questionNumber,
                status,
                durationMs,
                timestampMs
        );
        if (exam) {
            moveToNextQuestion();
            return;
        }
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
                        showBadgeUnlockedDialog(b, () -> {
                            AppDatabaseHelper.Badge meta = unlockAllBadgesIfComplete();
                            if (meta != null) showBadgeUnlockedDialog(meta, null);
                        });
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

    private String buildQuestionHeader(FirQuestion q) {
        StringBuilder sb = new StringBuilder();
        if (q.comunidad != null && !q.comunidad.isEmpty()) {
            sb.append(q.comunidad).append(" · ");
        }
        sb.append("Año ").append(q.year);
        if (q.questionNumber != null && !q.questionNumber.isEmpty()) {
            sb.append(" · Nº ").append(q.questionNumber);
        }
        return sb.toString();
    }

    private String buildFeedbackText(FirQuestion question, String status) {
        switch (status) {
            case "Acertada":
                return "Correcta";
            case "Fallada":
                return "Incorrecta";
            case "Duda_Primera":
                return "Dudada: acertaste con la respuesta pulsada.";
            case "Duda_Segunda":
                return "Dudada";
            case "Duda_Fallada":
                return "Dudada";
            case "Vacia":
                return "Pregunta dejada en blanco";
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
        boolean exam = currentQuizMode == QuizMode.EXAM;
        if (exam) {
            lastElapsedMs = Math.min(lastElapsedMs, examLimitMs);
        }
        PointsStore.addPoints(this, score);
        int totalPoints = PointsStore.getPoints(this);
        int gradedScore = scoredCorrectCount();
        int gradedTotal = scoredQuestionCount();
        int gradedLimit = Math.min(gradedTotal, results.size());

        int acertadas = 0, falladas = 0, dudaPrimera = 0, dudaSegunda = 0, dudaFallada = 0, vacias = 0;
        for (int i = 0; i < gradedLimit; i++) {
            AnswerResult r = results.get(i);
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
            if (currentQuizMode == QuizMode.DAILY) {
                saveDailyResult(netas);
            }
            firstSimulationOfToday = !appDatabaseHelper.hasCompletedSimulacroToday();
            appDatabaseHelper.saveExamAttempt(
                    examStartEpochMs, lastElapsedMs,
                    acertadas, falladas, dudaPrimera, dudaSegunda, vacias, netas
            );
            postExamBadges.addAll(collectNewExamBadges(acertadas, falladas, dudaPrimera, dudaSegunda, dudaFallada, vacias));
            postExamBadges.addAll(checkAndGetDailyBadges());
            AppDatabaseHelper.Badge allBadgesBadge = unlockAllBadgesIfComplete();
            if (allBadgesBadge != null) postExamBadges.add(allBadgesBadge);
            if (firstSimulationOfToday) {
                showFirstSimulationOfDayDialog(() -> showDailyBadgeSequence(postExamBadges, 0));
            } else {
                showDailyBadgeSequence(postExamBadges, 0);
            }
        }

        quizLayout.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);
        scoreText.setText(gradedScore + "/" + gradedTotal);
        netasText.setText(String.format(Locale.getDefault(), "NOTA: %.2f", netas));
        finalTimeText.setText("Tiempo: " + formatElapsed(lastElapsedMs)
                + (exam ? " de " + formatElapsed(examLimitMs) : ""));
        earnedPointsText.setText("Has ganado " + score + " puntos. Total: " + totalPoints);

        if (exam) {
            resultTitle.setText(examTimedOut ? "Tiempo agotado" : "Resultado del simulacro");
            anotherSimulationButton.setVisibility(View.GONE);
            buildExamComparison(netas);
            buildExamReview();
            examReviewContainer.setVisibility(View.GONE);
            reviewAnswersButton.setText("Repasar mis respuestas");
            reviewAnswersButton.setVisibility(View.VISIBLE);
        } else {
            resultTitle.setText("Resultado");
            anotherSimulationButton.setVisibility(View.VISIBLE);
            examComparisonContainer.removeAllViews();
            examComparisonContainer.setVisibility(View.GONE);
            shareExamButton.setVisibility(View.GONE);
            buildExamReview();
            examReviewContainer.setVisibility(View.GONE);
            reviewAnswersButton.setText("Repasar mis respuestas");
            reviewAnswersButton.setVisibility(View.VISIBLE);
        }
        shareDailyButton.setVisibility(
                currentQuizMode == QuizMode.DAILY && examSelectedIndices.size() == selectedQuestions.size()
                        ? View.VISIBLE : View.GONE);
        if (rootScrollView != null) {
            rootScrollView.scrollTo(0, 0);
        }
    }

    private void shareDailyResult() {
        int n = selectedQuestions.size();
        if (n == 0 || examSelectedIndices.size() != n) {
            Toast.makeText(this, "No hay resultado que compartir", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] selected = new int[n];
        int[] correct = new int[n];
        String[] sources = new String[n];
        for (int i = 0; i < n; i++) {
            selected[i] = examSelectedIndices.get(i);
            FirQuestion q = selectedQuestions.get(i);
            correct[i] = q.correctIndex;
            sources[i] = ((q.comunidad == null ? "" : q.comunidad) + " " + q.year).trim();
        }
        DailyShareHelper.share(this, DailySimulacro.currentDayLabel(), selected, correct, sources);
    }

    private void shareExamResult() {
        if (selectedQuestions.isEmpty()) {
            return;
        }
        FirQuestion first = selectedQuestions.get(0);
        ExamScoreStats stats = ExamScoreStats.load(this, first.comunidad, first.year);
        if (stats == null) {
            Toast.makeText(this, "No hay resultado que compartir", Toast.LENGTH_SHORT).show();
            return;
        }
        String date = new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new java.util.Date());
        ExamShareHelper.share(this, first.comunidad, first.year, date,
                scoredCorrectCount(), scoredQuestionCount(), calculateNetas(),
                formatElapsed(lastElapsedMs), stats);
    }

    private void buildExamComparison(double netas) {
        examComparisonContainer.removeAllViews();
        examComparisonContainer.setVisibility(View.GONE);
        shareExamButton.setVisibility(View.GONE);
        if (selectedQuestions.isEmpty()) {
            return;
        }
        FirQuestion first = selectedQuestions.get(0);
        ExamScoreStats stats = ExamScoreStats.load(this, first.comunidad, first.year);
        if (stats == null) {
            return;
        }
        shareExamButton.setVisibility(View.VISIBLE);
        int rank = stats.rankOf(netas);
        int top = stats.topPercent(netas);
        int total = stats.scores.length;

        examComparisonContainer.addView(createReviewText("Comparación con los presentados", 20, Color.parseColor("#2B2B2B"), true, 0));
        examComparisonContainer.addView(createReviewText(
                String.format(Locale.getDefault(), "Habrías quedado el %d de %d", rank, total),
                17, Color.parseColor("#2B2B2B"), true, 10));
        examComparisonContainer.addView(createReviewText(
                String.format(Locale.getDefault(), "Estarías en el Top %d%%", top),
                15, Color.parseColor("#5a5852"), false, 2));

        HistogramView histogram = new HistogramView(this);
        histogram.setData(stats.scores, netas);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        examComparisonContainer.addView(histogram, params);
        examComparisonContainer.setVisibility(View.VISIBLE);
    }

    private void showFirstSimulationOfDayDialog(Runnable onDismiss) {
        int days = appDatabaseHelper.getConsecutiveDaysStreak();
        String title = days <= 1 ? "Buen comienzo!" : "Racha de " + days + " dias!";
        String message = days <= 1
                ? "Has completado tu primer simulacro de hoy. Sigue asi y empieza tu racha!"
                : "Has completado tu primer simulacro de hoy.\n\n" +
                  "Llevas " + days + " dias seguidos haciendo al menos un simulacro. " +
                  "Enhorabuena por la racha, sigue asi!";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("A seguir!", (dialog, which) -> {
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

        int[] progress = appDatabaseHelper.getQuestionProgressCounts();
        if (progress[1] > 0) {
            if (progress[0] >= progress[1]) maybeAddUnlockedBadge(unlocked, allBadges, "full_database");
            double donePercent = progress[0] * 100.0 / progress[1];
            if (donePercent >= 10) maybeAddUnlockedBadge(unlocked, allBadges, "db_progress_10");
            if (donePercent >= 25) maybeAddUnlockedBadge(unlocked, allBadges, "db_progress_25");
            if (donePercent >= 50) maybeAddUnlockedBadge(unlocked, allBadges, "db_progress_50");
            if (donePercent >= 75) maybeAddUnlockedBadge(unlocked, allBadges, "db_progress_75");
        }

        if (appDatabaseHelper.hasAnyQuestionWithState("Duda_Segunda")) {
            maybeAddUnlockedBadge(unlocked, allBadges, "first_doubt_hit");
        }

        int attempts = appDatabaseHelper.getCompletedExamAttemptsCount();
        if (attempts >= 10) maybeAddUnlockedBadge(unlocked, allBadges, "exams_10");
        if (attempts >= 50) maybeAddUnlockedBadge(unlocked, allBadges, "exams_50");
        if (attempts >= 100) maybeAddUnlockedBadge(unlocked, allBadges, "exams_100");

        int today = appDatabaseHelper.getExamAttemptsCountToday();
        if (today >= 2) maybeAddUnlockedBadge(unlocked, allBadges, "day_exams_2");
        if (today >= 3) maybeAddUnlockedBadge(unlocked, allBadges, "day_exams_3");
        if (today >= 5) maybeAddUnlockedBadge(unlocked, allBadges, "day_exams_5");
        if (today >= 7) maybeAddUnlockedBadge(unlocked, allBadges, "day_exams_7");
        if (today >= 10) maybeAddUnlockedBadge(unlocked, allBadges, "day_exams_10");

        if (!selectedQuestions.isEmpty()) {
            int thresholdPercent = UserSettings.getAccuracyThresholdPercent(this);
            double accuracyPercent = ((acertadas + dudaPrimera) * 100.0) / selectedQuestions.size();
            if (accuracyPercent >= thresholdPercent) {
                maybeAddUnlockedBadge(unlocked, allBadges, "superando_limites");
                int exceeded = appDatabaseHelper.incrementThresholdExceededCount();
                if (exceeded >= 5) maybeAddUnlockedBadge(unlocked, allBadges, "threshold_5");
                if (exceeded >= 10) maybeAddUnlockedBadge(unlocked, allBadges, "threshold_10");
                if (exceeded >= 25) maybeAddUnlockedBadge(unlocked, allBadges, "threshold_25");
                if (exceeded >= 50) maybeAddUnlockedBadge(unlocked, allBadges, "threshold_50");
            }
        }

        return unlocked;
    }

    private AppDatabaseHelper.Badge unlockAllBadgesIfComplete() {
        List<AppDatabaseHelper.Badge> badges = appDatabaseHelper.getAllBadges();
        AppDatabaseHelper.Badge meta = null;
        for (AppDatabaseHelper.Badge b : badges) {
            if ("all_badges".equals(b.id)) {
                meta = b;
            } else if (!b.desbloqueada) {
                return null;
            }
        }
        if (meta == null || !appDatabaseHelper.unlockBadge("all_badges")) return null;
        return meta;
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

    private void applyExamUiRestrictions() {
        boolean exam = currentQuizMode == QuizMode.EXAM;
        pauseQuizButton.setVisibility(exam ? View.GONE : View.VISIBLE);
        copyPromptButton.setVisibility(exam ? View.GONE : View.VISIBLE);
        finishNowButton.setVisibility(exam ? View.VISIBLE : View.GONE);
    }

    private void confirmFinishExamNow() {
        if (!isQuizActive() || currentQuizMode != QuizMode.EXAM) {
            return;
        }
        int remaining = selectedQuestions.size() - results.size();
        new AlertDialog.Builder(this)
                .setTitle("Terminar el simulacro")
                .setMessage("Las " + remaining + " preguntas sin responder (incluida la actual si no la has respondido) "
                        + "se dejarán en blanco y verás tu resultado. ¿Seguro que quieres terminar ya?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Terminar", (dialog, which) -> {
                    if (!isQuizActive() || currentQuizMode != QuizMode.EXAM) {
                        return;
                    }
                    while (results.size() < selectedQuestions.size()) {
                        FirQuestion q = selectedQuestions.get(results.size());
                        results.add(new AnswerResult(
                                q.comunidad, q.year, q.questionNumber, q.statement,
                                "Vacia", false, "Vacia"));
                        examSelectedIndices.add(-1);
                    }
                    finishSimulation();
                })
                .show();
    }

    private Map<String, Map<String, List<FirQuestion>>> loadExamCatalog() {
        Map<String, Map<String, List<FirQuestion>>> catalog = new LinkedHashMap<>();
        try {
            for (FirQuestionsDbHelper.QuestionRecord r : firQuestionsDbHelper.loadAllQuestionsUnfiltered()) {
                Map<String, List<FirQuestion>> years = catalog.get(r.comunidad);
                if (years == null) {
                    years = new LinkedHashMap<>();
                    catalog.put(r.comunidad, years);
                }
                List<FirQuestion> exam = years.get(r.year);
                if (exam == null) {
                    exam = new ArrayList<>();
                    years.put(r.year, exam);
                }
                exam.add(new FirQuestion(r.comunidad, r.year, r.questionNumber, r.statement, r.options, r.correctIndex));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error cargando base de preguntas: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return new LinkedHashMap<>();
        }
        for (Map<String, List<FirQuestion>> years : catalog.values()) {
            for (List<FirQuestion> exam : years.values()) {
                Collections.sort(exam, (a, b) -> Integer.compare(parseIntOrZero(a.questionNumber), parseIntOrZero(b.questionNumber)));
            }
        }
        return catalog;
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void showExamCommunityMenu() {
        Map<String, Map<String, List<FirQuestion>>> catalog = loadExamCatalog();
        if (catalog.isEmpty()) {
            Toast.makeText(this, "No hay exámenes disponibles.", Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> communities = new ArrayList<>(catalog.keySet());
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, communities) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                String name = communities.get(position);
                row.setText(name);
                row.setCompoundDrawablePadding(dpToPx(14));
                android.graphics.drawable.Drawable flag = loadFlagDrawable(name);
                if (flag != null) {
                    int height = dpToPx(26);
                    int w = flag.getIntrinsicWidth();
                    int h = flag.getIntrinsicHeight();
                    int width = (w > 0 && h > 0) ? Math.round(height * (w / (float) h)) : dpToPx(38);
                    flag.setBounds(0, 0, width, height);
                    row.setCompoundDrawablesRelative(flag, null, null, null);
                } else {
                    row.setCompoundDrawablesRelative(null, null, null, null);
                }
                return row;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle("SIMULACRO: elige comunidad")
                .setAdapter(adapter, (dialog, which) -> showExamYearMenu(catalog, communities.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private android.graphics.drawable.Drawable loadFlagDrawable(String comunidad) {
        String slug = java.text.Normalizer.normalize(comunidad, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        int resId = getResources().getIdentifier("flag_" + slug, "drawable", getPackageName());
        return resId == 0 ? null : ContextCompat.getDrawable(this, resId);
    }

    private void showExamYearMenu(Map<String, Map<String, List<FirQuestion>>> catalog, String comunidad) {
        Map<String, List<FirQuestion>> years = catalog.get(comunidad);
        if (years == null || years.isEmpty()) {
            return;
        }
        final List<String> yearKeys = new ArrayList<>(years.keySet());
        Collections.sort(yearKeys, (a, b) -> Integer.compare(parseIntOrZero(a), parseIntOrZero(b)));
        String[] labels = new String[yearKeys.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = "Año " + yearKeys.get(i) + "  (" + years.get(yearKeys.get(i)).size() + " preguntas)"
                    + (ExamScoreStats.isAvailable(this, comunidad, yearKeys.get(i)) ? "  📊 con comparación" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle(comunidad + ": elige examen")
                .setItems(labels, (dialog, which) -> {
                    String year = yearKeys.get(which);
                    confirmStartExam(catalog, comunidad, year, years.get(year));
                })
                .setNegativeButton("Atrás", (dialog, which) -> showExamCommunityMenu())
                .show();
    }

    private void confirmStartExam(Map<String, Map<String, List<FirQuestion>>> catalog, String comunidad,
                                  String year, List<FirQuestion> questions) {
        final int minutes = (int) Math.round(questions.size() * 1.25);
        final int reserves = Math.min(ExamReserves.get(this, comunidad, year), questions.size() - 1);
        new AlertDialog.Builder(this)
                .setTitle("¿Estás preparado?")
                .setMessage(comunidad + " · Año " + year + " · " + questions.size() + " preguntas\n\n"
                        + (reserves > 0
                        ? "Las últimas " + reserves + " son de reserva y no cuentan para la nota.\n\n"
                        : "")
                        + "Necesitarás " + minutes + " minutos para completar el examen.\n\n"
                        + "El tiempo es regresivo: si se agota, el simulacro termina aunque esté incompleto. "
                        + "No sabrás si aciertas o fallas hasta el final."
                        + (ExamScoreStats.isAvailable(this, comunidad, year)
                        ? "\n\n📊 Al terminar verás cómo habrías quedado frente a los que se presentaron ese año."
                        : ""))
                .setNegativeButton("Atrás", (dialog, which) -> showExamYearMenu(catalog, comunidad))
                .setPositiveButton("Empezar", (dialog, which) -> startExam(questions, minutes))
                .show();
    }

    private void startExam(List<FirQuestion> questions, int minutes) {
        clearPausedQuiz();
        currentQuizMode = QuizMode.EXAM;
        applyBackgroundForMode(QuizMode.EXAM);
        selectedQuestions = new ArrayList<>(questions);
        examLimitMs = minutes * 60_000L;
        launchQuiz();
    }

    private TextView createReviewText(String text, int sp, int color, boolean bold, int topMarginDp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(topMarginDp);
        view.setLayoutParams(params);
        return view;
    }

    private void buildExamReview() {
        final int green = Color.parseColor("#2E7D32");
        final int red = Color.parseColor("#C62828");
        final int gray = Color.parseColor("#757575");
        final int body = Color.parseColor("#4A4A4A");

        examReviewContainer.removeAllViews();
        examReviewContainer.addView(createReviewText("Revisión del examen", 20, Color.parseColor("#2B2B2B"), true, 0));

        for (int i = 0; i < selectedQuestions.size(); i++) {
            FirQuestion q = selectedQuestions.get(i);
            boolean answered = i < examSelectedIndices.size();
            int selected = answered ? examSelectedIndices.get(i) : -1;

            String verdict;
            int verdictColor;
            if (!answered) {
                verdict = "Sin responder";
                verdictColor = gray;
            } else if (selected < 0) {
                verdict = "Dejada en blanco";
                verdictColor = gray;
            } else if (selected == q.correctIndex) {
                verdict = "Acertada";
                verdictColor = green;
            } else {
                verdict = "Fallada";
                verdictColor = red;
            }

            examReviewContainer.addView(createReviewText((i + 1) + ". " + q.statement, 15, Color.parseColor("#2B2B2B"), true, 20));
            if (i >= scoredQuestionCount()) {
                verdict += " · RESERVA (no puntúa)";
            }
            examReviewContainer.addView(createReviewText(verdict, 14, verdictColor, true, 4));
            for (int j = 0; j < q.options.size(); j++) {
                StringBuilder line = new StringBuilder();
                line.append((char) ('A' + j)).append(") ").append(q.options.get(j));
                int color = body;
                boolean isCorrect = j == q.correctIndex;
                boolean isSelected = j == selected;
                if (isCorrect) {
                    color = green;
                    line.append("  ✓ correcta");
                    if (isSelected) line.append(" (tu respuesta)");
                } else if (isSelected) {
                    color = red;
                    line.append("  ✗ tu respuesta");
                }
                examReviewContainer.addView(createReviewText(line.toString(), 14, color, isCorrect || isSelected, 4));
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

    private List<FirQuestion> loadQuestionsFromDatabase(List<String> statesFilter) {
        List<FirQuestion> questions = new ArrayList<>();
        try {
            List<FirQuestionsDbHelper.QuestionRecord> sourceQuestions = statesFilter == null
                    ? firQuestionsDbHelper.loadAllQuestions()
                    : firQuestionsDbHelper.loadQuestionsByStates(statesFilter);
            for (FirQuestionsDbHelper.QuestionRecord question : sourceQuestions) {
                questions.add(new FirQuestion(
                        question.comunidad,
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
                .setMessage("Se perdera el progreso no terminado de este examen. Quieres salir y descartarlo?")
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
            Toast.makeText(this, "No hay ningun examen en pausa.", Toast.LENGTH_SHORT).show();
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
            examSelectedIndices.clear();
            JSONArray storedIndices = json.optJSONArray("selectedIndices");
            if (storedIndices != null) {
                for (int i = 0; i < storedIndices.length(); i++) {
                    examSelectedIndices.add(storedIndices.getInt(i));
                }
            }
            JSONArray storedResults = json.optJSONArray("results");
            if (storedResults != null) {
                for (int i = 0; i < storedResults.length(); i++) {
                    JSONObject resultJson = storedResults.getJSONObject(i);
                    results.add(new AnswerResult(
                            resultJson.optString("comunidad", ""),
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
            applyExamUiRestrictions();
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
                    questionJson.optString("comunidad", ""),
                    questionJson.getString("year"),
                    questionJson.getString("questionNumber"),
                    questionJson.getString("statement"),
                    options,
                    questionJson.getInt("correctIndex")
            ));
        }
        return questions;
    }

    private JSONArray questionsToJson() throws Exception {
        JSONArray questionsArray = new JSONArray();
        for (FirQuestion question : selectedQuestions) {
            JSONObject questionJson = new JSONObject();
            questionJson.put("comunidad", question.comunidad == null ? "" : question.comunidad);
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
        return questionsArray;
    }

    private SharedPreferences getDailyResultPrefs() {
        return getApplicationContext().getSharedPreferences(DAILY_RESULT_PREFS, MODE_PRIVATE);
    }

    private void saveDailyResult(double netas) {
        try {
            JSONObject json = new JSONObject();
            json.put("dayIndex", DailySimulacro.currentDayIndex());
            json.put("score", score);
            json.put("netas", netas);
            json.put("elapsedMs", lastElapsedMs);
            json.put("selectedQuestions", questionsToJson());
            json.put("selectedIndices", new JSONArray(examSelectedIndices));
            getDailyResultPrefs().edit().putString(DAILY_RESULT_KEY, json.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private boolean showDailyResultIfAlreadyDone() {
        String stored = getDailyResultPrefs().getString(DAILY_RESULT_KEY, null);
        if (stored == null) {
            return false;
        }
        try {
            JSONObject json = new JSONObject(stored);
            if (json.getLong("dayIndex") != DailySimulacro.currentDayIndex()) {
                return false;
            }
            List<FirQuestion> questions = parsePausedQuestions(json.getJSONArray("selectedQuestions"));
            JSONArray indices = json.getJSONArray("selectedIndices");
            if (questions.isEmpty()) {
                return false;
            }

            selectedQuestions = questions;
            examSelectedIndices.clear();
            for (int i = 0; i < indices.length(); i++) {
                examSelectedIndices.add(indices.getInt(i));
            }
            currentQuizMode = QuizMode.DAILY;
            applyBackgroundForMode(QuizMode.DAILY);

            startLayout.setVisibility(View.GONE);
            quizLayout.setVisibility(View.GONE);
            resultLayout.setVisibility(View.VISIBLE);
            resultTitle.setText("Simulacro del día " + DailySimulacro.currentDayLabel());
            scoreText.setText(json.getInt("score") + "/" + questions.size());
            netasText.setText(String.format(Locale.getDefault(), "NOTA: %.2f", json.getDouble("netas")));
            finalTimeText.setText("Tiempo: " + formatElapsed(json.optLong("elapsedMs", 0L)));
            earnedPointsText.setText("Ya has hecho el simulacro de hoy. Vuelve mañana para el siguiente.");
            anotherSimulationButton.setVisibility(View.VISIBLE);
            buildExamReview();
            examReviewContainer.setVisibility(View.VISIBLE);
            reviewAnswersButton.setVisibility(View.GONE);
            shareDailyButton.setVisibility(View.VISIBLE);
            shareExamButton.setVisibility(View.GONE);
            examComparisonContainer.removeAllViews();
            examComparisonContainer.setVisibility(View.GONE);
            if (rootScrollView != null) {
                rootScrollView.scrollTo(0, 0);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
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

        json.put("selectedQuestions", questionsToJson());
        json.put("selectedIndices", new JSONArray(examSelectedIndices));

        JSONArray resultsArray = new JSONArray();
        for (AnswerResult result : results) {
            JSONObject resultJson = new JSONObject();
            resultJson.put("comunidad", result.comunidad == null ? "" : result.comunidad);
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
        examSelectedIndices.clear();
        selectedTickIndex = -1;
        syncingTickSelection = false;
        feedbackText.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        optionsContainer.removeAllViews();
        optionTickBoxes.clear();
    }

    private int examReserveCount() {
        if (currentQuizMode != QuizMode.EXAM || selectedQuestions.isEmpty()) {
            return 0;
        }
        FirQuestion first = selectedQuestions.get(0);
        int reserves = ExamReserves.get(this, first.comunidad, first.year);
        return Math.min(reserves, selectedQuestions.size() - 1);
    }

    private int scoredQuestionCount() {
        return selectedQuestions.size() - examReserveCount();
    }

    private int scoredCorrectCount() {
        if (currentQuizMode != QuizMode.EXAM) {
            return score;
        }
        int limit = Math.min(scoredQuestionCount(), results.size());
        int correct = 0;
        for (int i = 0; i < limit; i++) {
            if (results.get(i).correct) {
                correct++;
            }
        }
        return correct;
    }

    private double calculateNetas() {
        if (selectedQuestions.isEmpty()) {
            return 0.0;
        }
        int scored = scoredQuestionCount();
        int limit = Math.min(scored, results.size());
        int fallos = 0;
        for (int i = 0; i < limit; i++) {
            AnswerResult result = results.get(i);
            if (!result.correct && !"Vacia".equals(result.selectedAnswer)) {
                fallos++;
            }
        }
        // Nota sobre 10: (aciertos - fallos/3) * 10 / N, donde N = preguntas puntuables (sin reservas)
        return (scoredCorrectCount() - (double) fallos / WRONG_ANSWERS_PER_PENALTY) * 10.0 / scored;
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
            if (rootScrollView.getBackground() instanceof AnimationDrawable) {
                AnimationDrawable animated = (AnimationDrawable) rootScrollView.getBackground();
                animated.setEnterFadeDuration(2000);
                animated.setExitFadeDuration(2000);
                animated.start();
            }
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
                case 30:
                    return R.drawable.streak_positive_30_img;
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
        Button shareBtn = dialogView.findViewById(R.id.shareBadgeButton);

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
        shareBtn.setOnClickListener(v -> BadgeShareHelper.share(this, badge));
        continueBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDismiss != null) onDismiss.run();
        });
        dialog.show();
    }

    private java.util.List<AppDatabaseHelper.Badge> checkAndGetDailyBadges() {
        int days = appDatabaseHelper.getConsecutiveDaysStreak();
        int[] thresholds = {1, 3, 5, 7, 10, 15, 20, 40, 50, 75, 100};
        String[] badgeIds = {"daily_1", "daily_3", "daily_5", "daily_7", "daily_10", "daily_15",
                "daily_20", "daily_40", "daily_50", "daily_75", "daily_100"};

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
    private void copyQuestionPromptToClipboard() {

    if (selectedQuestions == null
            || selectedQuestions.isEmpty()
            || currentIndex >= selectedQuestions.size()) {

        Toast.makeText(this, "No hay pregunta activa", Toast.LENGTH_SHORT).show();
        return;
    }

    FirQuestion q = selectedQuestions.get(currentIndex);

    StringBuilder sb = new StringBuilder();
    sb.append("Soy enfermero/a preparando las oposiciones OPE ENFERMERIA.\n");
    sb.append("Tengo la siguiente pregunta de examen y quiero que me expliques POR QUÉ la respuesta correcta es la que es, y por qué las demás opciones son incorrectas.\n\n");
    sb.append("PREGUNTA (").append(buildQuestionHeader(q)).append("):\n");
    sb.append(q.statement).append("\n\n");

    sb.append("OPCIONES:\n");

    char letter = 'A';
    for (int i = 0; i < q.options.size(); i++) {
        sb.append(letter).append(") ").append(q.options.get(i));

        if (i == q.correctIndex) {
            sb.append(" <- RESPUESTA CORRECTA");
        }

        sb.append("\n");
        letter++;
    }

    sb.append("\nPor favor, explica el razonamiento clínico o teórico que justifica que la opción correcta sea la ")
      .append((char)('A' + q.correctIndex))
      .append(", y desmonta brevemente las otras opciones.");

    String text = sb.toString();

    // Copiar
    ClipboardManager clipboard =
            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

    if (clipboard != null) {
        clipboard.setPrimaryClip(
                ClipData.newPlainText("Prompt OPE ENFERMERIA", text)
        );
    }

    // Compartir
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("text/plain");
    shareIntent.putExtra(Intent.EXTRA_TEXT, text);

    startActivity(
            Intent.createChooser(shareIntent, "Compartir texto")
    );

    Toast.makeText(this,
            "Prompt copiado y listo para compartir",
            Toast.LENGTH_SHORT).show();
}

    // private void copyQuestionPromptToClipboard() {
    //     if (selectedQuestions == null || selectedQuestions.isEmpty() || currentIndex >= selectedQuestions.size()) {
    //         Toast.makeText(this, "No hay pregunta activa", Toast.LENGTH_SHORT).show();
    //         return;
    //     }
    //     FirQuestion q = selectedQuestions.get(currentIndex);

    //     StringBuilder sb = new StringBuilder();
    //     sb.append("Soy enfermero/a preparando las oposiciones OPE SESPA.\n");
    //     sb.append("Tengo la siguiente pregunta de examen y quiero que me expliques POR QUE la respuesta correcta es la que es, y por que las demas opciones son incorrectas.\n\n");
    //     sb.append("PREGUNTA (").append(buildQuestionHeader(q)).append("):\n");
    //     sb.append(q.statement).append("\n\n");
    //     sb.append("OPCIONES:\n");
    //     char letter = 'A';
    //     for (int i = 0; i < q.options.size(); i++) {
    //         sb.append(letter).append(") ").append(q.options.get(i));
    //         if (i == q.correctIndex) {
    //             sb.append("  <- RESPUESTA CORRECTA");
    //         }
    //         sb.append("\n");
    //         letter++;
    //     }
    //     sb.append("\nPor favor, explica el razonamiento clinico o teorico que justifica que la opcion correcta sea la ").append((char)('A' + q.correctIndex)).append(", y desmonta brevemente las otras opciones.");

    //     ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    //     ClipData clip = ClipData.newPlainText("Prompt OPE SESPA", sb.toString());
    //     if (clipboard != null) {
    //         clipboard.setPrimaryClip(clip);
    //         Toast.makeText(this, "Prompt copiado al portapapeles", Toast.LENGTH_SHORT).show();
    //     }
    // //     // Compartir
    // //     Intent shareIntent = new Intent(Intent.ACTION_SEND);
    // //     shareIntent.setType("text/plain");
    // //     //shareIntent.putExtra(Intent.EXTRA_TEXT, text);

    // //     activity.startActivity(
    // //             Intent.createChooser(shareIntent, "Compartir texto")
    // //     );
    // }
    private static class FirQuestion {
        final String comunidad;
        final String year;
        final String questionNumber;
        final String statement;
        final List<String> options;
        final int correctIndex;

        FirQuestion(String comunidad, String year, String questionNumber, String statement, List<String> options, int correctIndex) {
            this.comunidad = comunidad;
            this.year = year;
            this.questionNumber = questionNumber;
            this.statement = statement;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    private static class AnswerResult {
        final String comunidad;
        final String year;
        final String questionNumber;
        final String statement;
        final String selectedAnswer;
        final boolean correct;
        final String estado;

        AnswerResult(String comunidad, String year, String questionNumber, String statement, String selectedAnswer, boolean correct, String estado) {
            this.comunidad = comunidad;
            this.year = year;
            this.questionNumber = questionNumber;
            this.statement = statement;
            this.selectedAnswer = selectedAnswer;
            this.correct = correct;
            this.estado = estado;
        }

        AnswerResult(String year, String questionNumber, String statement, String selectedAnswer, boolean correct, String estado) {
            this("", year, questionNumber, statement, selectedAnswer, correct, estado);
        }
    }

    private enum QuizMode {
        HOME(R.drawable.bg_gradient_anim),
        ALL(R.drawable.bg_mode_all),
        FAILURES(R.drawable.bg_mode_failures),
        DOUBTS(R.drawable.bg_mode_doubts),
        NEW(R.drawable.bg_mode_new),
        DAILY(R.drawable.bg_mode_all),
        EXAM(R.drawable.bg_mode_all);

        final int backgroundResId;

        QuizMode(int backgroundResId) {
            this.backgroundResId = backgroundResId;
        }
    }

    // ─── Diálogo explicativo de Dudas vacías ──────────────────────────────────

    private void showDoubtsEmptyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("\uD83E\uDD14  Sección Dudas")
                .setMessage(
                        "Aún no tienes preguntas guardadas en esta sección.\n\n" +
                        "Las dudas se registran cuando al responder marcas también el \u2611 checkbox " +
                        "de otra opción que te parece posible.\n\n" +
                        "\u25B6  Marca el \u2611 de la respuesta alternativa que te genera duda.\n" +
                        "\u25B6  Pulsa la respuesta que crees correcta.\n\n" +
                        "La pregunta quedará guardada aquí para repasarla más adelante. " +
                        "Cuantas más preguntas hagas, más se irá llenando esta sección."
                )
                .setPositiveButton("Entendido", null)
                .show();
    }

    // ─── Tutorial primera vez ─────────────────────────────────────────────────

    private static class TutorialStep {
        final String emoji;
        final String title;
        final String text;
        final int anchorViewId; // 0 = sin ancla específica

        TutorialStep(String emoji, String title, String text, int anchorViewId) {
            this.emoji = emoji;
            this.title = title;
            this.text = text;
            this.anchorViewId = anchorViewId;
        }
    }

    private void showFirstQuizTutorial() {
        List<TutorialStep> steps = new ArrayList<>();
        SharedPreferences tutorialPrefs = getSharedPreferences(PAUSED_QUIZ_PREFS, MODE_PRIVATE);
        if (currentQuizMode == QuizMode.DAILY && !tutorialPrefs.getBoolean(PREF_DAILY_INTRO_SEEN, false)) {
            tutorialPrefs.edit().putBoolean(PREF_DAILY_INTRO_SEEN, true).apply();
            steps.add(new TutorialStep(
                    "📅", "Simulacro del día",
                    "Son 10 preguntas, y son las mismas para todo el mundo ese día.\n\n" +
                    "¡Pícate y compite con tus amigos y compañeros! Al terminar podrás compartir tu resultado.",
                    0
            ));
        }
        if (tutorialPrefs.getBoolean(PREF_QUIZ_TUTORIAL_SEEN, false)) {
            showTutorialStep(steps, 0);
            return;
        }
        steps.add(new TutorialStep(
                "\uD83D\uDCDD", "Cómo responder",
                "Pulsa el botón con la opción que creas correcta.\n\n" +
                "La respuesta se marcará en verde si aciertas, y en rojo si fallas " +
                "(también se mostrará la correcta).",
                R.id.optionsContainer
        ));
        steps.add(new TutorialStep(
                "\u2611", "Marcar una duda",
                "Si dudas entre dos respuestas, marca primero el checkbox ☑ de la opción alternativa " +
                "y luego pulsa la que crees que es correcta.\n\n" +
                "La pregunta quedará guardada en la sección \"Dudas\" para repasarla.",
                R.id.optionsContainer
        ));
        steps.add(new TutorialStep(
                "\u26AA", "Dejar en blanco",
                "Si no sabes la respuesta o prefieres no contestar, pulsa \"Dejar vacía\".\n\n" +
                "No penaliza más que un fallo normal.",
                R.id.blankButton
        ));
        steps.add(new TutorialStep(
                "\uD83E\uDD16", "Preguntar a la IA",
                "Pulsa \"Preguntar a IA\" para copiar un prompt explicativo al portapapeles.\n\n" +
                "Luego pégalo en ChatGPT, Gemini o cualquier IA para obtener una explicación clínica detallada.",
                R.id.copyPromptButton
        ));
        showTutorialStep(steps, 0);
    }

    private void showTutorialStep(List<TutorialStep> steps, int index) {
        if (index >= steps.size()) {
            getSharedPreferences(PAUSED_QUIZ_PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_QUIZ_TUTORIAL_SEEN, true)
                    .apply();
            return;
        }
        TutorialStep step = steps.get(index);

        View bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_tutorial_bubble, null, false);
        TextView emojiView = bubbleView.findViewById(R.id.tutorialEmoji);
        TextView titleView = bubbleView.findViewById(R.id.tutorialTitle);
        TextView textView = bubbleView.findViewById(R.id.tutorialText);
        Button nextBtn = bubbleView.findViewById(R.id.tutorialNext);

        emojiView.setText(step.emoji);
        titleView.setText(step.title);
        textView.setText(step.text);

        boolean isLast = (index == steps.size() - 1);
        nextBtn.setText(isLast ? "Listo \u2714" : "Siguiente \u203A");

        PopupWindow popup = new PopupWindow(
                bubbleView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                false
        );
        popup.setElevation(dpToPx(8));
        popup.setOutsideTouchable(false);

        nextBtn.setOnClickListener(v -> {
            popup.dismiss();
            showTutorialStep(steps, index + 1);
        });

        View anchor = step.anchorViewId != 0 ? findViewById(step.anchorViewId) : quizLayout;
        if (anchor == null) anchor = quizLayout;
        final View finalAnchor = anchor;

        finalAnchor.post(() -> {
            int[] location = new int[2];
            finalAnchor.getLocationOnScreen(location);
            bubbleView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(dpToPx(290), android.view.View.MeasureSpec.AT_MOST),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            );
            int popupW = bubbleView.getMeasuredWidth();
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int xOff = Math.min(location[0] + dpToPx(8), screenW - popupW - dpToPx(16));
            xOff = Math.max(xOff, dpToPx(16));
            int yOff = location[1] - bubbleView.getMeasuredHeight() - dpToPx(16);
            if (yOff < dpToPx(60)) {
                yOff = location[1] + finalAnchor.getHeight() + dpToPx(8);
            }
            popup.showAtLocation(finalAnchor, android.view.Gravity.NO_GRAVITY, xOff, yOff);
        });
    }
}

