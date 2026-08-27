package com.fir.simulacro;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrizeBagActivity extends AppCompatActivity {
    private Button backButton;
    private LinearLayout bagContainer;
    private TextView emptyText;
    private AppDatabaseHelper appDatabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prize_bag);
        
        startBackgroundAnimation();

        backButton = findViewById(R.id.bagBackButton);
        bagContainer = findViewById(R.id.bagRewardsContainer);
        emptyText = findViewById(R.id.bagEmptyText);
        appDatabaseHelper = new AppDatabaseHelper(this);

        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBagUi();
    }

    private void refreshBagUi() {
        List<AppDatabaseHelper.PurchasedReward> unredeemed = appDatabaseHelper.getPurchasedRewardsNotRedeemed();
        Map<String, RewardVisual> rewardVisualByCode = loadRewardVisualsByCode();
        bagContainer.removeAllViews();

        if (unredeemed.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        emptyText.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AppDatabaseHelper.PurchasedReward reward : unredeemed) {
            View itemView = inflater.inflate(R.layout.item_prize_bag_reward, bagContainer, false);
            ImageView rewardImage = itemView.findViewById(R.id.bagRewardImage);
            TextView rewardName = itemView.findViewById(R.id.bagRewardName);
            Button redeemButton = itemView.findViewById(R.id.bagRedeemButton);
            Button cancelButton = itemView.findViewById(R.id.bagCancelButton);

            rewardName.setText(reward.name);
            loadRewardImage(rewardVisualByCode.get(reward.code), rewardImage);

            redeemButton.setOnClickListener(v -> {
                appDatabaseHelper.redeemReward(reward.id);
                Toast.makeText(this, "¡Premio canjeado!", Toast.LENGTH_SHORT).show();
                refreshBagUi();
            });
            cancelButton.setOnClickListener(v ->
                    Toast.makeText(this, "Canje cancelado", Toast.LENGTH_SHORT).show()
            );

            bagContainer.addView(itemView);
        }
    }

    private Map<String, RewardVisual> loadRewardVisualsByCode() {
        Map<String, RewardVisual> visualsByCode = new HashMap<>();

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
                String imagePath = cols.get(1).trim();
                String code = cols.get(3).trim();
                if (!code.isEmpty()) {
                    visualsByCode.put(code, new RewardVisual(imagePath, "", false));
                }
            }
        } catch (java.io.IOException ignored) {
        }

        try {
            for (AppDatabaseHelper.CustomReward customReward : appDatabaseHelper.getCustomRewards()) {
                visualsByCode.put(customReward.code, new RewardVisual("", customReward.imageUri, true));
            }
        } catch (IllegalStateException ignored) {
        }

        return visualsByCode;
    }

    private void loadRewardImage(RewardVisual rewardVisual, ImageView imageView) {
        if (rewardVisual == null) {
            imageView.setImageResource(R.drawable.reward_image_placeholder);
            return;
        }

        if (rewardVisual.imageUri != null && !rewardVisual.imageUri.isEmpty()) {
            Uri uri = Uri.parse(rewardVisual.imageUri);
            loadRewardImageFromUri(uri, imageView);
            return;
        }

        if (rewardVisual.customReward) {
            imageView.setImageResource(R.drawable.reward_image_placeholder);
            return;
        }

        String path = rewardVisual.imagePath.isEmpty() ? "prizes/default_prize.png" : rewardVisual.imagePath;
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

    private static class RewardVisual {
        final String imagePath;
        final String imageUri;
        final boolean customReward;

        RewardVisual(String imagePath, String imageUri, boolean customReward) {
            this.imagePath = imagePath;
            this.imageUri = imageUri;
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