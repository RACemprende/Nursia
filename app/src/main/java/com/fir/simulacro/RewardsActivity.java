package com.fir.simulacro;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RewardsActivity extends AppCompatActivity {
    private TextView rewardsPointsText;
    private LinearLayout rewardsContainer;
    private Button backButton;
    private final List<RewardItem> rewards = new ArrayList<>();
    private AppDatabaseHelper appDatabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);

        rewardsPointsText = findViewById(R.id.rewardsPointsText);
        rewardsContainer = findViewById(R.id.rewardsContainer);
        backButton = findViewById(R.id.backButton);
        appDatabaseHelper = new AppDatabaseHelper(this);

        backButton.setOnClickListener(v -> finish());

        loadRewards();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
                if (line.trim().isEmpty()) continue;
                List<String> cols = CsvUtils.parseCsvLine(line);
                if (cols.size() < 4) continue;
                String name = cols.get(0).trim();
                String image = cols.get(1).trim();
                Integer cost = tryParseInt(cols.get(2).trim());
                String code = cols.get(3).trim();
                if (name.isEmpty() || cost == null || code.isEmpty()) continue;
                rewards.add(new RewardItem(name, image, cost, code));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error cargando premios: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshUi() {
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
            loadRewardImage(reward.imagePath, rewardImage);

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
        refreshUi();

        long purchasedAtMs = System.currentTimeMillis();
        String qrContent = reward.code + "|" + purchasedAtMs;
        appDatabaseHelper.recordRewardPurchase(reward.name, reward.cost, reward.code, purchasedAtMs);
        showQrDialog(reward.name, qrContent);
    }

    private void showQrDialog(String rewardName, String qrContent) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reward_qr, null, false);
        ImageView qrImage = dialogView.findViewById(R.id.qrImage);
        TextView qrLabel = dialogView.findViewById(R.id.qrLabel);

        Bitmap qrBitmap = buildQrBitmap(qrContent, 800, 800);
        if (qrBitmap != null) {
            qrImage.setImageBitmap(qrBitmap);
        }
        qrLabel.setText("VALE POR UN \"" + rewardName + "\"");

        new AlertDialog.Builder(this)
                .setTitle("Premio comprado")
                .setView(dialogView)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private Bitmap buildQrBitmap(String content, int width, int height) {
        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            Toast.makeText(this, "No se pudo generar el QR", Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private void loadRewardImage(String assetPath, ImageView imageView) {
        try {
            String path = assetPath.isEmpty() ? "prizes/default_prize.png" : assetPath;
            InputStream inputStream = getAssets().open(path);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);
            inputStream.close();
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.app_icon);
        }
    }

    private Integer tryParseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class RewardItem {
        final String name;
        final String imagePath;
        final int cost;
        final String code;

        RewardItem(String name, String imagePath, int cost, String code) {
            this.name = name;
            this.imagePath = imagePath;
            this.cost = cost;
            this.code = code;
        }
    }
}
