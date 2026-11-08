package com.fir.simulacro;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomPracticeActivity extends AppCompatActivity {
    public static final String EXTRA_TEMA = "tema";
    public static final String EXTRA_SUBTEMA = "subtema";
    public static final String EXTRA_STATE_POSITION = "statePosition";

    public static final String[] STATE_LABELS = {"Todas", "Sin hacer todavía", "Falladas", "Dudadas"};

    private FirQuestionsDbHelper dbHelper;
    private Spinner temaSpinner;
    private Spinner subtemaSpinner;
    private Spinner stateSpinner;
    private TextView countText;
    private Button startButton;

    public static List<String> statesFor(int statePosition) {
        switch (statePosition) {
            case 1:
                return Arrays.asList("Pendiente");
            case 2:
                return Arrays.asList("Fallada", "Duda_Fallada");
            case 3:
                return Arrays.asList("Duda_Primera", "Duda_Segunda");
            default:
                return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_practice);
        InsetsHelper.apply(this);
        startBackgroundAnimation();

        dbHelper = new FirQuestionsDbHelper(this);
        temaSpinner = findViewById(R.id.customTemaSpinner);
        subtemaSpinner = findViewById(R.id.customSubtemaSpinner);
        stateSpinner = findViewById(R.id.customStateSpinner);
        countText = findViewById(R.id.customCountText);
        startButton = findViewById(R.id.customStartButton);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        List<String> temas = new ArrayList<>();
        try {
            temas = dbHelper.getAvailableTemas();
        } catch (Exception e) {
            Toast.makeText(this, "Error cargando temas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        temaSpinner.setAdapter(createAdapter(this, "Todos los temas", temas));
        subtemaSpinner.setAdapter(createAdapter(this, "Todos los subtemas", new ArrayList<>()));
        stateSpinner.setAdapter(createAdapter(this, STATE_LABELS[0],
                Arrays.asList(STATE_LABELS).subList(1, STATE_LABELS.length)));

        temaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                List<String> subtemas = new ArrayList<>();
                try {
                    subtemas = dbHelper.getAvailableSubtemas(selection(temaSpinner));
                } catch (Exception ignored) {
                }
                subtemaSpinner.setAdapter(createAdapter(CustomPracticeActivity.this, "Todos los subtemas", subtemas));
                refreshCount();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        AdapterView.OnItemSelectedListener refreshListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                refreshCount();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        subtemaSpinner.setOnItemSelectedListener(refreshListener);
        stateSpinner.setOnItemSelectedListener(refreshListener);

        startButton.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_TEMA, selection(temaSpinner));
            result.putExtra(EXTRA_SUBTEMA, selection(subtemaSpinner));
            result.putExtra(EXTRA_STATE_POSITION, stateSpinner.getSelectedItemPosition());
            setResult(RESULT_OK, result);
            finish();
        });
        refreshCount();
    }

    private void refreshCount() {
        int available = 0;
        try {
            available = dbHelper.countQuestionsByTopic(
                    selection(temaSpinner),
                    selection(subtemaSpinner),
                    statesFor(stateSpinner.getSelectedItemPosition()));
        } catch (Exception e) {
            Toast.makeText(this, "Error contando preguntas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (available > 0) {
            countText.setText(available == 1 ? "1 pregunta disponible" : available + " preguntas disponibles");
        } else {
            countText.setText("No hay preguntas con esas condiciones");
        }
        startButton.setEnabled(available > 0);
        startButton.setAlpha(available > 0 ? 1f : 0.5f);
    }

    private static String selection(Spinner spinner) {
        return spinner.getSelectedItemPosition() <= 0 ? null : (String) spinner.getSelectedItem();
    }

    private static ArrayAdapter<String> createAdapter(Context context, String allLabel, List<String> values) {
        List<String> items = new ArrayList<>();
        items.add(allLabel);
        items.addAll(values);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void startBackgroundAnimation() {
        ViewGroup root = findViewById(android.R.id.content);
        if (root == null || root.getChildCount() == 0) {
            return;
        }
        View content = root.getChildAt(0);
        if (content != null && content.getBackground() instanceof AnimationDrawable) {
            AnimationDrawable animated = (AnimationDrawable) content.getBackground();
            animated.setEnterFadeDuration(2000);
            animated.setExitFadeDuration(2000);
            animated.start();
        }
    }
}
