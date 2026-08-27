package com.fir.simulacro;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RewardsActivity extends AppCompatActivity {
    private TextView rewardsPointsText;
    private LinearLayout rewardsContainer;
    private Button backButton;
    private Button prizeBagButton;
    private Button addRewardButton;
    private Button helpButton;
    private final List<RewardItem> rewards = new ArrayList<>();
    private AppDatabaseHelper appDatabaseHelper;
    private ActivityResultLauncher<String[]> imagePickerLauncher;
    private Uri pendingCustomRewardImageUri;
    private ImageView pendingCustomRewardPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);
        
        startBackgroundAnimation();

        rewardsPointsText = findViewById(R.id.rewardsPointsText);
        rewardsContainer = findViewById(R.id.rewardsContainer);
        backButton = findViewById(R.id.backButton);
        prizeBagButton = findViewById(R.id.prizeBagButton);
        addRewardButton = findViewById(R.id.addRewardButton);
        helpButton = findViewById(R.id.helpButton);
        appDatabaseHelper = new AppDatabaseHelper(this);
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        return;
                    }

                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (SecurityException e) {
                        Toast.makeText(this, "No se pudo guardar el permiso de la foto", Toast.LENGTH_SHORT).show();
                    }

                    pendingCustomRewardImageUri = uri;
                    if (pendingCustomRewardPreview != null) {
                        loadRewardImageFromUri(uri, pendingCustomRewardPreview);
                    }
                }
        );

        backButton.setOnClickListener(v -> finish());
        prizeBagButton.setOnClickListener(v -> startActivity(new Intent(this, PrizeBagActivity.class)));
        addRewardButton.setOnClickListener(v -> showAddRewardDialog());
        helpButton.setOnClickListener(v -> showHelpDialog());

        loadRewards();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CloudSyncManager.syncSilently(this);
        refreshUi();
    }

    private void loadRewards() {
        rewards.clear();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open("rewards.csv"))
        )) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> cols = CsvUtils.parseCsvLine(line);
                if (cols.size() < 4) {
                    continue;
                }
                String name = cols.get(0).trim();
                String image = cols.get(1).trim();
                Integer cost = tryParseInt(cols.get(2).trim());
                String code = cols.get(3).trim();
                if (name.isEmpty() || cost == null || code.isEmpty()) {
                    continue;
                }
                rewards.add(new RewardItem(name, image, "", cost, code));
            }
        } catch (java.io.IOException e) {
            Toast.makeText(this, "Error cargando premios: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            for (AppDatabaseHelper.CustomReward customReward : appDatabaseHelper.getCustomRewards()) {
                rewards.add(new RewardItem(customReward.name, "", customReward.imageUri, customReward.cost, customReward.code, true));
            }
        } catch (IllegalStateException e) {
            Toast.makeText(this, "Error cargando premios personalizados: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshUi() {
        loadRewards();
        int points = PointsStore.getPoints(this);
        rewardsPointsText.setText("Puntos: " + points);
        rewardsContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (RewardItem reward : rewards) {
            View itemView = inflater.inflate(R.layout.item_reward, rewardsContainer, false);
            ImageView rewardImage = itemView.findViewById(R.id.rewardImage);
            TextView rewardName = itemView.findViewById(R.id.rewardName);
            TextView rewardCost = itemView.findViewById(R.id.rewardCost);
            Button buyButton = itemView.findViewById(R.id.buyButton);

            rewardName.setText(reward.name);
            rewardCost.setText("Coste: " + reward.cost + " puntos");
            loadRewardImage(reward, rewardImage);

            boolean canBuy = points >= reward.cost;
            buyButton.setEnabled(canBuy);
            buyButton.setOnClickListener(v -> buyReward(reward));

            rewardsContainer.addView(itemView);
        }
    }

    private void buyReward(RewardItem reward) {
        if (!PointsStore.spendPoints(this, reward.cost)) {
            Toast.makeText(this, "No tienes suficientes puntos", Toast.LENGTH_LONG).show();
            refreshUi();
            return;
        }
        
        long purchasedAtMs = System.currentTimeMillis();
        appDatabaseHelper.recordRewardPurchase(reward.name, reward.cost, reward.code, purchasedAtMs);
        
        Toast.makeText(this, "¡Premio comprado! Compruébalo en tu mochila.", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void loadRewardImage(RewardItem reward, ImageView imageView) {
        if (reward.imageUri != null && !reward.imageUri.isEmpty()) {
            Uri uri = Uri.parse(reward.imageUri);
            loadRewardImageFromUri(uri, imageView);
            return;
        }

        if (reward.customReward) {
            imageView.setImageResource(R.drawable.reward_image_placeholder);
            return;
        }

        String path = reward.imagePath.isEmpty() ? "prizes/default_prize.png" : reward.imagePath;
        try (InputStream inputStream = getAssets().open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageResource(R.drawable.reward_image_placeholder);
            }
        } catch (java.io.IOException e) {
            imageView.setImageResource(R.drawable.reward_image_placeholder);
        }
    }

    private void loadRewardImageFromUri(Uri uri, ImageView imageView) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                imageView.setImageResource(R.drawable.reward_image_placeholder);
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageResource(R.drawable.reward_image_placeholder);
            }
        } catch (java.io.IOException | SecurityException e) {
            imageView.setImageResource(R.drawable.reward_image_placeholder);
        }
    }

    private Integer tryParseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showAddRewardDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_reward, null);
        ImageView preview = dialogView.findViewById(R.id.addRewardImagePreview);
        Button choosePhotoButton = dialogView.findViewById(R.id.choosePhotoButton);
        Button clearPhotoButton = dialogView.findViewById(R.id.clearPhotoButton);
        EditText descriptionInput = dialogView.findViewById(R.id.rewardDescriptionInput);
        EditText pointsInput = dialogView.findViewById(R.id.rewardPointsInput);

        pendingCustomRewardImageUri = null;
        pendingCustomRewardPreview = preview;
        preview.setImageResource(R.drawable.reward_image_placeholder);

        choosePhotoButton.setOnClickListener(v -> imagePickerLauncher.launch(new String[]{"image/*"}));
        clearPhotoButton.setOnClickListener(v -> {
            pendingCustomRewardImageUri = null;
            preview.setImageResource(R.drawable.reward_image_placeholder);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Añadir premio")
                .setView(dialogView)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnDismissListener(d -> {
            pendingCustomRewardPreview = null;
            pendingCustomRewardImageUri = null;
        });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String description = descriptionInput.getText().toString().trim();
            Integer points = tryParseInt(pointsInput.getText().toString().trim());
            if (description.isEmpty()) {
                descriptionInput.setError("Escribe una descripción");
                return;
            }
            if (points == null || points <= 0) {
                pointsInput.setError("Introduce un número de puntos válido");
                return;
            }

            String imageUri = pendingCustomRewardImageUri == null ? "" : pendingCustomRewardImageUri.toString();
            appDatabaseHelper.addCustomReward(description, points, imageUri, System.currentTimeMillis());
            Toast.makeText(this, "Premio añadido", Toast.LENGTH_SHORT).show();
            loadRewards();
            refreshUi();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void showHelpDialog() {
        String helpText = "Cómo funciona el sistema de canje:\n\n" +
                "• 1 pregunta acertada = 1 punto\n\n" +
                "Crear premios:\n" +
                "1. Pulsa el botón ➕ Añadir\n" +
                "2. Sube una foto del premio (opcional)\n" +
                "3. Escribe la descripción\n" +
                "4. Indica el número de puntos necesarios\n" +
                "5. Pulsa Guardar\n\n" +
                "Canjear premios:\n" +
                "1. Selecciona un premio de la lista\n" +
                "2. Pulsa Comprar (si tienes puntos suficientes)\n" +
                "3. El premio se añade a tu mochila\n" +
                "4. Abre la mochila (🎒)\n" +
                "5. Selecciona un premio y pulsa Canjear";

        new AlertDialog.Builder(this)
                .setTitle("Ayuda - Sistema de Canje")
                .setMessage(helpText)
                .setPositiveButton("De acuerdo", null)
                .show();
    }

    private static class RewardItem {
        final String name;
        final String imagePath;
        final String imageUri;
        final int cost;
        final String code;
        final boolean customReward;

        RewardItem(String name, String assetPath, String imageUri, int cost, String code) {
            this(name, assetPath, imageUri, cost, code, false);
        }

        RewardItem(String name, String assetPath, String imageUri, int cost, String code, boolean customReward) {
            this.name = name;
            this.imagePath = assetPath;
            this.imageUri = imageUri;
            this.cost = cost;
            this.code = code;
            this.customReward = customReward;
        }
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