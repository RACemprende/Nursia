package com.fir.simulacro;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends AppCompatActivity {
    private Button backButton;
    private TextView noHeatmapDataText;
    private LinearLayout heatmapCalendarContainer;
    private TextView selectedDayTitleText;
    private TextView selectedDaySummaryText;
    private TextView selectedDayAnswersText;

    private AppDatabaseHelper appDatabaseHelper;
    private final Map<String, AppDatabaseHelper.DailyCalendarStats> dailyStatsByDay = new HashMap<>();
    private String selectedDayKey;
    private Calendar currentMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        
        startBackgroundAnimation();

        appDatabaseHelper = new AppDatabaseHelper(this);
        currentMonth = Calendar.getInstance();

        bindViews();
        backButton.setOnClickListener(v -> finish());
        loadMonthlyCalendar();
    }

    private void bindViews() {
        backButton = findViewById(R.id.backButton);
        noHeatmapDataText = findViewById(R.id.noHeatmapDataText);
        heatmapCalendarContainer = findViewById(R.id.heatmapCalendarContainer);
        selectedDayTitleText = findViewById(R.id.selectedDayTitleText);
        selectedDaySummaryText = findViewById(R.id.selectedDaySummaryText);
        selectedDayAnswersText = findViewById(R.id.selectedDayAnswersText);
    }

    private void loadMonthlyCalendar() {
        String monthKey = formatMonthKey(currentMonth);
        List<AppDatabaseHelper.DailyCalendarStats> entries = appDatabaseHelper.getDailyCalendarStats(monthKey);

        dailyStatsByDay.clear();
        for (AppDatabaseHelper.DailyCalendarStats entry : entries) {
            dailyStatsByDay.put(entry.day, entry);
        }

        String todayKey = formatDay(currentMonth.getTime());
        if (dailyStatsByDay.containsKey(todayKey)) {
            selectedDayKey = todayKey;
        } else if (!entries.isEmpty()) {
            selectedDayKey = entries.get(entries.size() - 1).day;
        } else {
            selectedDayKey = formatDay(currentMonth.getTime());
        }

        renderCalendarMonth();
        renderSelectedDayStats(selectedDayKey);
        noHeatmapDataText.setVisibility(entries.isEmpty() ? TextView.VISIBLE : TextView.GONE);
    }

    private void renderCalendarMonth() {
        heatmapCalendarContainer.removeAllViews();

        TextView monthTitle = new TextView(this);
        monthTitle.setText(formatMonthTitle(currentMonth));
        monthTitle.setTextSize(16f);
        monthTitle.setTextColor(Color.parseColor("#1B1B1B"));
        monthTitle.setPadding(0, 0, 0, dpToPx(6));
        heatmapCalendarContainer.addView(monthTitle);

        LinearLayout daysHeader = new LinearLayout(this);
        daysHeader.setOrientation(LinearLayout.HORIZONTAL);
        String[] dayLabels = {"L", "M", "X", "J", "V", "S", "D"};
        for (String label : dayLabels) {
            TextView dayLabel = new TextView(this);
            dayLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            dayLabel.setGravity(Gravity.CENTER);
            dayLabel.setText(label);
            dayLabel.setTextSize(11f);
            daysHeader.addView(dayLabel);
        }
        heatmapCalendarContainer.addView(daysHeader);

        Calendar monthStart = (Calendar) currentMonth.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        int totalDays = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDayOffset = ((monthStart.get(Calendar.DAY_OF_WEEK) + 5) % 7);
        int totalCells = firstDayOffset + totalDays;
        int totalRows = (int) Math.ceil(totalCells / 7.0);

        Calendar pointer = (Calendar) monthStart.clone();
        pointer.add(Calendar.DAY_OF_MONTH, -firstDayOffset);

        for (int row = 0; row < totalRows; row++) {
            LinearLayout weekRow = new LinearLayout(this);
            weekRow.setOrientation(LinearLayout.HORIZONTAL);
            weekRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            for (int col = 0; col < 7; col++) {
                boolean inCurrentMonth = pointer.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
                        && pointer.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR);
                String dayKey = formatDay(pointer.getTime());
                AppDatabaseHelper.DailyCalendarStats dayStats = dailyStatsByDay.get(dayKey);

                TextView dayCell = new TextView(this);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, dpToPx(36), 1f);
                cellParams.leftMargin = dpToPx(2);
                cellParams.rightMargin = dpToPx(2);
                cellParams.topMargin = dpToPx(2);
                cellParams.bottomMargin = dpToPx(2);
                dayCell.setLayoutParams(cellParams);
                dayCell.setGravity(Gravity.CENTER);
                dayCell.setTextSize(10f);
                dayCell.setText(inCurrentMonth ? String.valueOf(pointer.get(Calendar.DAY_OF_MONTH)) : "");

                if (!inCurrentMonth) {
                    dayCell.setBackground(buildCellBackground(Color.TRANSPARENT, false));
                } else {
                    boolean selected = dayKey.equals(selectedDayKey);
                    if (dayStats == null) {
                        dayCell.setTextColor(Color.parseColor("#667085"));
                        dayCell.setBackground(buildCellBackground(Color.parseColor("#ECEFF3"), selected));
                        dayCell.setContentDescription(dayKey + ": sin datos");
                    } else {
                        int heatColor = computeHeatColor(dayStats, dayKey);
                        dayCell.setTextColor(Color.WHITE);
                        dayCell.setBackground(buildCellBackground(heatColor, selected));
                        dayCell.setContentDescription(dayKey + ": " + formatPercent(dayStats.averageScorePercent));
                    }
                    dayCell.setOnClickListener(v -> {
                        selectedDayKey = dayKey;
                        renderCalendarMonth();
                        renderSelectedDayStats(dayKey);
                    });
                }

                weekRow.addView(dayCell);
                pointer.add(Calendar.DAY_OF_MONTH, 1);
            }
            heatmapCalendarContainer.addView(weekRow);
        }
    }

    private void renderSelectedDayStats(String dayKey) {
        AppDatabaseHelper.DailyCalendarStats stats = dailyStatsByDay.get(dayKey);
        selectedDayTitleText.setText("Día " + formatDisplayDate(dayKey));
        if (stats == null) {
            selectedDaySummaryText.setText("Preguntas realizadas: 0\nExámenes acabados: 0\nNota media: 0.0%");
            selectedDayAnswersText.setText("Aciertos: 0 · Falladas: 0 · En blanco: 0");
            return;
        }

        selectedDaySummaryText.setText(
                "Preguntas realizadas: " + stats.questionsDone +
                "\nExámenes acabados: " + stats.examsCompleted +
                "\nNota media: " + formatPercent(stats.averageScorePercent)
        );
        selectedDayAnswersText.setText(
                "Aciertos: " + stats.correctAnswers +
                " · Falladas: " + stats.failedAnswers +
                " · En blanco: " + stats.blankAnswers
        );
    }

    private GradientDrawable buildCellBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dpToPx(6));
        if (selected) {
            drawable.setStroke(dpToPx(2), Color.parseColor("#1B1B1B"));
        }
        return drawable;
    }

    private int computeHeatColor(AppDatabaseHelper.DailyCalendarStats stats, String dayKey) {
        if (stats.questionsDone <= 0 && stats.examsCompleted <= 0) {
            return Color.parseColor("#ECEFF3");
        }
        if (stats.correctAnswers <= 0) {
            return Color.parseColor("#C62828");
        }
        return interpolateColor(Color.parseColor("#C62828"), Color.parseColor("#2E7D32"),
                clamp(stats.averageScorePercent / 100.0, 0.0, 1.0));
    }

    private int interpolateColor(int startColor, int endColor, double ratio) {
        double safe = clamp(ratio, 0.0, 1.0);
        int r = (int) Math.round(Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * safe);
        int g = (int) Math.round(Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * safe);
        int b = (int) Math.round(Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * safe);
        return Color.rgb(r, g, b);
    }

    private double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private String formatMonthKey(Calendar calendar) {
        return String.format(
                Locale.US,
                "%04d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1
        );
    }

    private String formatMonthTitle(Calendar calendar) {
        SimpleDateFormat formatter = new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));
        String value = formatter.format(calendar.getTime());
        if (value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String formatDay(java.util.Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private String formatDisplayDate(String dayKey) {
        try {
            java.util.Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dayKey);
            if (date == null) {
                return dayKey;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return String.format(
                    Locale.getDefault(),
                    "%02d/%02d/%04d",
                    calendar.get(Calendar.DAY_OF_MONTH),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.YEAR)
            );
        } catch (Exception ignored) {
            return dayKey;
        }
    }

    private String formatPercent(double value) {
        return String.format(Locale.getDefault(), "%.1f%%", value);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
    private void startBackgroundAnimation() {
        android.view.ViewGroup root = findViewById(android.R.id.content);
        if (root == null || root.getChildCount() == 0) {
            return;
        }
        android.view.View content = root.getChildAt(0);
        if (content != null && content.getBackground() instanceof android.graphics.drawable.AnimationDrawable) {
            android.graphics.drawable.AnimationDrawable animated = (android.graphics.drawable.AnimationDrawable) content.getBackground();
            animated.setEnterFadeDuration(2000);
            animated.setExitFadeDuration(2000);
            animated.start();
        }
    }
}