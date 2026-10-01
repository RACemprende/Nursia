package com.fir.simulacro;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    private int[] questionCountOptions;

    private NumberPicker hourPicker;
    private NumberPicker minutePicker;
    private Spinner questionCountSpinner;
    private SeekBar thresholdSlider;
    private TextView thresholdValueText;
    private TextView permissionStatusText;
    private Button openPermissionSettingsButton;
    private Button testNotificationButton;
    private Button onboardingButton;
    private Button selectTheoryCommunitiesButton;
    private Button selectLegislationCommunitiesButton;
    private Button saveButton;
    private Button cancelButton;
    private AppDatabaseHelper appDatabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        startBackgroundAnimation();

        appDatabaseHelper = new AppDatabaseHelper(this);
        bindViews();
        setupControls();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CloudSyncManager.syncSilently(this);
        refreshPermissionStatus();
    }

    private void bindViews() {
        hourPicker = findViewById(R.id.hourPicker);
        minutePicker = findViewById(R.id.minutePicker);
        questionCountSpinner = findViewById(R.id.questionCountSpinner);
        thresholdSlider = findViewById(R.id.thresholdSlider);
        thresholdValueText = findViewById(R.id.thresholdValueText);
        permissionStatusText = findViewById(R.id.permissionStatusText);
        openPermissionSettingsButton = findViewById(R.id.openPermissionSettingsButton);
        testNotificationButton = findViewById(R.id.testNotificationButton);
        onboardingButton = findViewById(R.id.onboardingButton);
        selectTheoryCommunitiesButton = findViewById(R.id.selectTheoryCommunitiesButton);
        selectLegislationCommunitiesButton = findViewById(R.id.selectLegislationCommunitiesButton);
        saveButton = findViewById(R.id.saveSettingsButton);
        cancelButton = findViewById(R.id.cancelSettingsButton);
    }

    private void setupControls() {
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setFormatter(value -> String.format(java.util.Locale.getDefault(), "%02d", value));
        hourPicker.setValue(UserSettings.getReminderHour(this));

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setFormatter(value -> String.format(java.util.Locale.getDefault(), "%02d", value));
        minutePicker.setValue(UserSettings.getReminderMinute(this));

        questionCountOptions = UserSettings.getQuizQuestionCountOptions();
        String[] optionLabels = new String[questionCountOptions.length];
        for (int i = 0; i < questionCountOptions.length; i++) {
            optionLabels[i] = String.valueOf(questionCountOptions[i]);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                optionLabels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        questionCountSpinner.setAdapter(adapter);

        int currentCount = UserSettings.getQuizQuestionCount(this);
        int selectedIndex = 1;
        for (int i = 0; i < questionCountOptions.length; i++) {
            if (questionCountOptions[i] == currentCount) {
                selectedIndex = i;
                break;
            }
        }
        questionCountSpinner.setSelection(selectedIndex);

        int thresholdPercent = UserSettings.getAccuracyThresholdPercent(this);
        thresholdSlider.setMax(100);
        thresholdSlider.setProgress(thresholdPercent);
        thresholdValueText.setText(thresholdPercent + "%");

        refreshCommunityButtons();
    }

    private void setupListeners() {
        openPermissionSettingsButton.setOnClickListener(v -> openRelevantPermissionSettings());
        testNotificationButton.setOnClickListener(v -> testNotificationNow());
        onboardingButton.setOnClickListener(v -> showOnboardingAgain());
        selectTheoryCommunitiesButton.setOnClickListener(v -> showCommunityPicker(false));
        selectLegislationCommunitiesButton.setOnClickListener(v -> showCommunityPicker(true));
        thresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thresholdValueText.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        saveButton.setOnClickListener(v -> saveSettings());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void refreshPermissionStatus() {
        boolean notificationsGranted = isNotificationPermissionGranted();
        boolean exactAlarmGranted = canScheduleExactAlarms();
        String notifStatus = notificationsGranted ? "OK" : "FALTA";
        String alarmStatus = exactAlarmGranted ? "OK" : "FALTA";
        permissionStatusText.setText(
                "Permiso notificaciones: " + notifStatus + "\n" +
                "Permiso alarma exacta: " + alarmStatus
        );
    }

    private boolean isNotificationPermissionGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private void openRelevantPermissionSettings() {
        if (!isNotificationPermissionGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
            return;
        }
        if (!canScheduleExactAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        Toast.makeText(this, "Ya tienes todos los permisos necesarios", Toast.LENGTH_SHORT).show();
    }

    private void testNotificationNow() {
        NotificationScheduler.createNotificationChannel(this);
        NotificationScheduler.triggerTestReminderNow(this);
        Toast.makeText(this, "Notificación de prueba enviada", Toast.LENGTH_SHORT).show();
    }

    private void showOnboardingAgain() {
        OnboardingHelper.resetOnboarding(this);
        startActivity(new Intent(this, OnboardingActivity.class));
        finish();
    }

    private void showCommunityPicker(boolean forLegislation) {
        final List<String> allCommunities = appDatabaseHelper.getAvailableCommunitiesForCategory(forLegislation);
        if (allCommunities.isEmpty()) {
            Toast.makeText(this, "No hay comunidades disponibles para esta categoría.", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> currentSelection = forLegislation
                ? UserSettings.getLegislationCommunitiesFilter(this)
                : UserSettings.getTheoryCommunitiesFilter(this);

        final boolean[] checked = new boolean[allCommunities.size()];
        for (int i = 0; i < allCommunities.size(); i++) {
            checked[i] = currentSelection == null || currentSelection.contains(allCommunities.get(i));
        }

        String title = forLegislation
                ? "Comunidades (Legislación)"
                : "Comunidades (Teoría)";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(
                        allCommunities.toArray(new String[0]),
                        checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked
                )
                .setPositiveButton("Guardar", (dialog, which) -> {
                    Set<String> selected = new HashSet<>();
                    for (int i = 0; i < allCommunities.size(); i++) {
                        if (checked[i]) {
                            selected.add(allCommunities.get(i));
                        }
                    }
                    if (forLegislation) {
                        UserSettings.saveLegislationCommunities(this, selected);
                    } else {
                        UserSettings.saveTheoryCommunities(this, selected);
                    }
                    refreshCommunityButtons();
                })
                .setNeutralButton("Todas", (dialog, which) -> {
                    Set<String> all = new HashSet<>(allCommunities);
                    if (forLegislation) {
                        UserSettings.saveLegislationCommunities(this, all);
                    } else {
                        UserSettings.saveTheoryCommunities(this, all);
                    }
                    refreshCommunityButtons();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void refreshCommunityButtons() {
        selectTheoryCommunitiesButton.setText(buildCommunityButtonLabel(false));
        selectLegislationCommunitiesButton.setText(buildCommunityButtonLabel(true));
    }

    private String buildCommunityButtonLabel(boolean forLegislation) {
        String prefix = forLegislation ? "Legislación: " : "Teoría: ";
        List<String> available = appDatabaseHelper.getAvailableCommunitiesForCategory(forLegislation);
        Set<String> selected = forLegislation
                ? UserSettings.getLegislationCommunitiesFilter(this)
                : UserSettings.getTheoryCommunitiesFilter(this);
        if (selected == null) {
            return prefix + "Todas";
        }
        if (selected.isEmpty()) {
            return prefix + "Ninguna";
        }
        if (!available.isEmpty() && selected.containsAll(available)) {
            return prefix + "Todas";
        }
        return prefix + selected.size() + "/" + available.size();
    }

    private void saveSettings() {
        int hour = hourPicker.getValue();
        int minute = minutePicker.getValue();
        int selectedPosition = questionCountSpinner.getSelectedItemPosition();
        int count = UserSettings.getQuizQuestionCount(this);
        if (selectedPosition >= 0 && selectedPosition < questionCountOptions.length) {
            count = questionCountOptions[selectedPosition];
        }
        int thresholdPercent = thresholdSlider.getProgress();

        UserSettings.saveReminderTime(this, hour, minute);
        UserSettings.saveQuizQuestionCount(this, count);
        UserSettings.saveAccuracyThresholdPercent(this, thresholdPercent);
        NotificationScheduler.scheduleDailyReminder(this);

        Toast.makeText(this, "Ajustes guardados", Toast.LENGTH_SHORT).show();
        finish();
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