package com.fir.simulacro;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class StatisticsActivity extends AppCompatActivity {
    private Spinner subjectSpinner;
    private Spinner startMonthSpinner;
    private Spinner endMonthSpinner;
    private Button applyFiltersButton;
    private Button backButton;
    private TextView summaryText;
    private ProgressBar correctProgressBar;
    private TextView correctPercentText;
    private ProgressBar failedProgressBar;
    private TextView failedPercentText;
    private ProgressBar doubtedProgressBar;
    private TextView doubtedPercentText;
    private ProgressBar doubtAccuracyProgressBar;
    private TextView doubtAccuracyText;
    private TextView noDataText;
    private LinearLayout dailyChartContainer;

    private TextView consecutiveDaysText;
    private TextView noSimulacrosDataText;
    private LinearLayout simulacrosChartContainer;
    private RadioGroup aciertosGroupByGroup;
    private TextView noAciertosDataText;
    private LinearLayout aciertosChartContainer;
    private TextView thresholdGoalText;

    private AppDatabaseHelper appDatabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        appDatabaseHelper = new AppDatabaseHelper(this);
        bindViews();
        styleCharts();
        loadFilters();
        applyFiltersButton.setOnClickListener(v -> refreshStatistics());
        backButton.setOnClickListener(v -> finish());

        aciertosGroupByGroup.setOnCheckedChangeListener((group, checkedId) -> refreshGroupedCharts());

        refreshStatistics();
    }

    private void bindViews() {
        subjectSpinner = findViewById(R.id.subjectSpinner);
        startMonthSpinner = findViewById(R.id.startMonthSpinner);
        endMonthSpinner = findViewById(R.id.endMonthSpinner);
        applyFiltersButton = findViewById(R.id.applyFiltersButton);
        backButton = findViewById(R.id.backButton);
        summaryText = findViewById(R.id.summaryText);
        correctProgressBar = findViewById(R.id.correctProgressBar);
        correctPercentText = findViewById(R.id.correctPercentText);
        failedProgressBar = findViewById(R.id.failedProgressBar);
        failedPercentText = findViewById(R.id.failedPercentText);
        doubtedProgressBar = findViewById(R.id.doubtedProgressBar);
        doubtedPercentText = findViewById(R.id.doubtedPercentText);
        doubtAccuracyProgressBar = findViewById(R.id.doubtAccuracyProgressBar);
        doubtAccuracyText = findViewById(R.id.doubtAccuracyText);
        noDataText = findViewById(R.id.noDataText);
        dailyChartContainer = findViewById(R.id.dailyChartContainer);

        consecutiveDaysText = findViewById(R.id.consecutiveDaysText);
        noSimulacrosDataText = findViewById(R.id.noSimulacrosDataText);
        simulacrosChartContainer = findViewById(R.id.simulacrosChartContainer);
        aciertosGroupByGroup = findViewById(R.id.aciertosGroupByGroup);
        noAciertosDataText = findViewById(R.id.noAciertosDataText);
        aciertosChartContainer = findViewById(R.id.aciertosChartContainer);
        thresholdGoalText = findViewById(R.id.thresholdGoalText);
    }

    private void styleCharts() {
        correctProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#2E7D32")));
        failedProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#C62828")));
        doubtedProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#EF6C00")));
        doubtAccuracyProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#1565C0")));
    }

    private void loadFilters() {
        setSpinnerItems(subjectSpinner, appDatabaseHelper.getAvailableSubjects());
        List<String> months = appDatabaseHelper.getAvailableMonths();
        setSpinnerItems(startMonthSpinner, months);
        setSpinnerItems(endMonthSpinner, months);
    }

    private void setSpinnerItems(Spinner spinner, List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                items
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void refreshStatistics() {
        String subject = String.valueOf(subjectSpinner.getSelectedItem());
        String startMonth = String.valueOf(startMonthSpinner.getSelectedItem());
        String endMonth = String.valueOf(endMonthSpinner.getSelectedItem());

        if (!isValidMonthRange(startMonth, endMonth)) {
            Toast.makeText(this, "El mes inicial no puede ser posterior al final.", Toast.LENGTH_LONG).show();
            return;
        }

        AppDatabaseHelper.StatisticsData data = appDatabaseHelper.getStatistics(subject, startMonth, endMonth);
        renderStatistics(data);
        refreshGroupedCharts();
    }

    private void refreshGroupedCharts() {
        refreshSimulacrosChart();
        refreshAciertosChart();
        refreshQuestionsDoneChart();
    }

    private String getSelectedGroupBy() {
        int checkedId = aciertosGroupByGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.radioAciertosWeek) return "week";
        if (checkedId == R.id.radioAciertosMonth) return "month";
        return "day";
    }

    private void refreshSimulacrosChart() {
        String groupBy = getSelectedGroupBy();
        String startMonth = String.valueOf(startMonthSpinner.getSelectedItem());
        String endMonth = String.valueOf(endMonthSpinner.getSelectedItem());
        List<AppDatabaseHelper.ChartEntry> entries = appDatabaseHelper.getSimulacrosChart(groupBy, startMonth, endMonth);
        renderBarChart(simulacrosChartContainer, noSimulacrosDataText, entries,
                Color.parseColor("#1565C0"), "simulacros");
    }

    private void refreshAciertosChart() {
        String groupBy = getSelectedGroupBy();

        String startMonth = String.valueOf(startMonthSpinner.getSelectedItem());
        String endMonth = String.valueOf(endMonthSpinner.getSelectedItem());
        int thresholdPercent = UserSettings.getAccuracyThresholdPercent(this);
        thresholdGoalText.setText("Objetivo: " + thresholdPercent + "%");

        List<AppDatabaseHelper.ChartEntry> entries = appDatabaseHelper.getAciertosChart(groupBy, startMonth, endMonth);
        renderPercentBarChart(aciertosChartContainer, noAciertosDataText, entries, thresholdPercent);
    }

    private void refreshQuestionsDoneChart() {
        String groupBy = getSelectedGroupBy();
        String subject = String.valueOf(subjectSpinner.getSelectedItem());
        String startMonth = String.valueOf(startMonthSpinner.getSelectedItem());
        String endMonth = String.valueOf(endMonthSpinner.getSelectedItem());
        List<AppDatabaseHelper.ChartEntry> entries = appDatabaseHelper.getQuestionsDoneChart(groupBy, subject, startMonth, endMonth);
        renderBarChart(dailyChartContainer, noDataText, entries,
                Color.parseColor("#6A1B9A"), "preguntas");
    }

    private boolean isValidMonthRange(String startMonth, String endMonth) {
        if ("Todos".equals(startMonth) || "Todos".equals(endMonth)) {
            return true;
        }
        return startMonth.compareTo(endMonth) <= 0;
    }

    private void renderStatistics(AppDatabaseHelper.StatisticsData data) {
        double correctPercent = percentage(data.totalCorrect, data.totalDone);
        double failedPercent = percentage(data.totalFailed, data.totalDone);
        double doubtedPercent = percentage(data.totalDoubted, data.totalDone);
        double doubtAccuracy = percentage(data.doubtSecond, data.doubtFirst + data.doubtSecond);

        summaryText.setText("Preguntas hechas: " + data.totalDone);
        setPercentBar(correctProgressBar, correctPercent);
        correctPercentText.setText(formatPercent(correctPercent));
        setPercentBar(failedProgressBar, failedPercent);
        failedPercentText.setText(formatPercent(failedPercent));
        setPercentBar(doubtedProgressBar, doubtedPercent);
        doubtedPercentText.setText(formatPercent(doubtedPercent));
        setPercentBar(doubtAccuracyProgressBar, doubtAccuracy);
        doubtAccuracyText.setText(formatPercent(doubtAccuracy));

        String streak = data.consecutiveDaysStreak == 1
                ? "1 día" : data.consecutiveDaysStreak + " días";
        consecutiveDaysText.setText(streak);

    }

    private void renderBarChart(LinearLayout container, TextView noDataView,
                                 List<AppDatabaseHelper.ChartEntry> entries, int barColor, String unit) {
        container.removeAllViews();
        if (entries.isEmpty()) {
            noDataView.setVisibility(TextView.VISIBLE);
            return;
        }
        noDataView.setVisibility(TextView.GONE);

        int maxValue = 1;
        for (AppDatabaseHelper.ChartEntry e : entries) {
            maxValue = Math.max(maxValue, e.value);
        }

        LinearLayout chartArea = new LinearLayout(this);
        chartArea.setOrientation(LinearLayout.HORIZONTAL);
        chartArea.setGravity(Gravity.BOTTOM);
        chartArea.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(180)
        ));

        for (AppDatabaseHelper.ChartEntry e : entries) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            colParams.leftMargin = dpToPx(2);
            colParams.rightMargin = dpToPx(2);
            column.setLayoutParams(colParams);

            TextView value = new TextView(this);
            value.setText(String.valueOf(e.value));
            value.setTextSize(11f);
            value.setGravity(Gravity.CENTER_HORIZONTAL);

            View bar = new View(this);
            int barHeight = Math.max(dpToPx(8), (int) Math.round((e.value * dpToPx(120)) / maxValue));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    barHeight
            );
            barParams.topMargin = dpToPx(4);
            bar.setLayoutParams(barParams);
            bar.setBackgroundColor(barColor);

            TextView label = new TextView(this);
            label.setText(e.label);
            label.setTextSize(10f);
            label.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView unitLabel = new TextView(this);
            unitLabel.setText(unit);
            unitLabel.setTextSize(9f);
            unitLabel.setGravity(Gravity.CENTER_HORIZONTAL);

            column.addView(label);
            column.addView(value);
            column.addView(bar);
            column.addView(unitLabel);
            chartArea.addView(column);
        }
        container.addView(chartArea);
    }

    private void renderPercentBarChart(LinearLayout container, TextView noDataView,
                                       List<AppDatabaseHelper.ChartEntry> entries, int thresholdPercent) {
        container.removeAllViews();
        if (entries.isEmpty()) {
            noDataView.setVisibility(TextView.VISIBLE);
            return;
        }
        noDataView.setVisibility(TextView.GONE);

        int objectiveColor = Color.parseColor("#FF8F00");
        int aboveColor = Color.parseColor("#2E7D32");
        int belowColor = Color.parseColor("#C62828");
        int chartHeight = dpToPx(180);

        FrameLayout chartFrame = new FrameLayout(this);
        chartFrame.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                chartHeight
        ));

        LinearLayout columnsRow = new LinearLayout(this);
        columnsRow.setOrientation(LinearLayout.HORIZONTAL);
        columnsRow.setGravity(Gravity.BOTTOM);
        columnsRow.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        for (AppDatabaseHelper.ChartEntry e : entries) {
            boolean aboveThreshold = e.value >= thresholdPercent;
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            colParams.leftMargin = dpToPx(2);
            colParams.rightMargin = dpToPx(2);
            column.setLayoutParams(colParams);

            TextView value = new TextView(this);
            value.setText(e.value + "%");
            value.setTextSize(11f);
            value.setGravity(Gravity.CENTER_HORIZONTAL);

            View bar = new View(this);
            int barHeight = Math.max(dpToPx(8), (int) Math.round((e.value * dpToPx(120)) / 100.0));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    barHeight
            );
            barParams.topMargin = dpToPx(4);
            bar.setLayoutParams(barParams);
            bar.setBackgroundColor(aboveThreshold ? aboveColor : belowColor);

            TextView label = new TextView(this);
            label.setText(e.label);
            label.setTextSize(10f);
            label.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView statusText = new TextView(this);
            statusText.setText(aboveThreshold ? "↑" : "↓");
            statusText.setTextSize(10f);
            statusText.setTextColor(aboveThreshold ? aboveColor : belowColor);
            statusText.setGravity(Gravity.CENTER_HORIZONTAL);

            column.addView(label);
            column.addView(value);
            column.addView(bar);
            column.addView(statusText);
            columnsRow.addView(column);
        }

        View objectiveLine = new View(this);
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(2)
        );
        int clampedThreshold = Math.max(0, Math.min(100, thresholdPercent));
        lineParams.topMargin = (int) Math.round((100 - clampedThreshold) * chartHeight / 100.0);
        objectiveLine.setLayoutParams(lineParams);
        objectiveLine.setBackgroundColor(objectiveColor);

        TextView objectiveLabel = new TextView(this);
        FrameLayout.LayoutParams objectiveLabelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        objectiveLabelParams.leftMargin = dpToPx(4);
        objectiveLabelParams.topMargin = Math.max(0, lineParams.topMargin - dpToPx(18));
        objectiveLabel.setLayoutParams(objectiveLabelParams);
        objectiveLabel.setText("Objetivo " + thresholdPercent + "%");
        objectiveLabel.setTextSize(10f);
        objectiveLabel.setTextColor(objectiveColor);

        chartFrame.addView(columnsRow);
        chartFrame.addView(objectiveLine);
        chartFrame.addView(objectiveLabel);
        container.addView(chartFrame);
    }

    private void setPercentBar(ProgressBar progressBar, double value) {
        progressBar.setProgress((int) Math.round(value));
    }

    private double percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (numerator * 100.0) / denominator;
    }

    private String formatPercent(double value) {
        return String.format(Locale.getDefault(), "%.1f%%", value);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}
