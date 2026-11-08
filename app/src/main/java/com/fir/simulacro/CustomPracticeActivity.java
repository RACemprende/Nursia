package com.fir.simulacro;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    public static final String EXTRA_TEXTO = "texto";

    private static final int MIN_SEARCH_CHARS = 3;
    private static final long SEARCH_DEBOUNCE_MS = 400;

    public static final String[] STATE_LABELS = {"Todas", "Nuevas", "Falladas", "Dudadas"};

    private FirQuestionsDbHelper dbHelper;
    private Spinner temaSpinner;
    private Spinner subtemaSpinner;
    private final Button[] stateButtons = new Button[STATE_LABELS.length];
    private int selectedState = 0;
    private TextView countText;
    private Button startButton;
    private EditText searchInput;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refreshCount;

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
        buildStateButtons();

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

        searchInput = findViewById(R.id.customSearchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                startButton.setEnabled(false);
                startButton.setAlpha(0.5f);
                searchHandler.removeCallbacks(refreshRunnable);
                searchHandler.postDelayed(refreshRunnable, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        startButton.setOnClickListener(v -> {
            if (searchTooShort()) {
                return;
            }
            Intent result = new Intent();
            result.putExtra(EXTRA_TEMA, selection(temaSpinner));
            result.putExtra(EXTRA_SUBTEMA, selection(subtemaSpinner));
            result.putExtra(EXTRA_STATE_POSITION, selectedState);
            result.putExtra(EXTRA_TEXTO, searchText());
            setResult(RESULT_OK, result);
            finish();
        });
        refreshCount();
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }

    private void buildStateButtons() {
        LinearLayout group = findViewById(R.id.customStateGroup);
        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (3 * density);
        for (int i = 0; i < STATE_LABELS.length; i++) {
            final int position = i;
            Button button = new Button(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, (int) (44 * density), 1f);
            params.setMargins(i == 0 ? 0 : gap, 0, i == STATE_LABELS.length - 1 ? 0 : gap, 0);
            button.setLayoutParams(params);
            button.setText(STATE_LABELS[i]);
            button.setAllCaps(false);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(0, 0, 0, 0);
            button.setMaxLines(1);
            button.setTextSize(13);
            button.setOnClickListener(v -> {
                selectedState = position;
                updateStateButtons();
                refreshCount();
            });
            stateButtons[i] = button;
            group.addView(button);
        }
        updateStateButtons();
    }

    private void updateStateButtons() {
        for (int i = 0; i < stateButtons.length; i++) {
            boolean selected = i == selectedState;
            stateButtons[i].setBackgroundResource(selected ? R.drawable.chip_state_selected : R.drawable.chip_state_unselected);
            stateButtons[i].setTextColor(getResources().getColor(R.color.ink));
            stateButtons[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private String searchText() {
        String raw = searchInput.getText().toString();
        return raw.trim().isEmpty() ? null : raw;
    }

    private boolean searchTooShort() {
        String text = searchText();
        return text != null && text.length() < MIN_SEARCH_CHARS;
    }

    private void refreshCount() {
        if (searchTooShort()) {
            countText.setText("Escribe al menos " + MIN_SEARCH_CHARS + " caracteres para buscar");
            startButton.setEnabled(false);
            startButton.setAlpha(0.5f);
            return;
        }
        int available = 0;
        try {
            available = dbHelper.countQuestionsByTopic(
                    selection(temaSpinner),
                    selection(subtemaSpinner),
                    statesFor(selectedState),
                    searchText());
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
