package com.fir.simulacro;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BadgesActivity extends AppCompatActivity {

    /*
     * IMÁGENES DE INSIGNIAS:
     * Coloca los PNG de insignias en:
     *   FirSimulacroApp/app/src/main/res/drawable-nodpi/
     * con estos nombres exactos:
     *   badge_daily_1_img.png    – Un día seguido
     *   badge_daily_3_img.png    – 3 días seguidos
     *   badge_daily_5_img.png    – 5 días seguidos
     *   badge_daily_7_img.png    – 7 días seguidos
     *   streak_positive_25_img.png, streak_positive_50_img.png, streak_positive_100_img.png
     *   streak_negative_20_img.png, streak_negative_25_img.png, streak_negative_50_img.png, streak_negative_100_img.png
     *   badge_perfect_exam_img.png, badge_full_database_img.png, badge_first_doubt_hit_img.png
     *   badge_exams_10_img.png, badge_exams_25_img.png, badge_exams_50_img.png, badge_exams_100_img.png
     *   badge_superando_limites_img.png
     * Los que no tengan PNG mostrarán el placeholder por defecto.
     */

    private static final int PAGE_SIZE = 6;

    private LinearLayout row1, row2, row3;
    private Button prevPageButton, nextPageButton, backButton;
    private TextView pageText;

    private List<AppDatabaseHelper.Badge> allBadges;
    private AppDatabaseHelper appDatabaseHelper;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_badges);
        
        startBackgroundAnimation();

        row1 = findViewById(R.id.row1);
        row2 = findViewById(R.id.row2);
        row3 = findViewById(R.id.row3);
        prevPageButton = findViewById(R.id.prevPageButton);
        nextPageButton = findViewById(R.id.nextPageButton);
        pageText = findViewById(R.id.pageText);
        backButton = findViewById(R.id.backButton);

        appDatabaseHelper = new AppDatabaseHelper(this);
        allBadges = appDatabaseHelper.getAllBadges();

        prevPageButton.setOnClickListener(v -> { if (currentPage > 0) { currentPage--; renderPage(); } });
        nextPageButton.setOnClickListener(v -> { if ((currentPage + 1) * PAGE_SIZE < allBadges.size()) { currentPage++; renderPage(); } });
        backButton.setOnClickListener(v -> finish());

        renderPage();
    }

    private void renderPage() {
        row1.removeAllViews();
        row2.removeAllViews();
        row3.removeAllViews();

        int totalPages = (int) Math.ceil((double) allBadges.size() / PAGE_SIZE);
        pageText.setText((currentPage + 1) + " / " + Math.max(1, totalPages));
        prevPageButton.setEnabled(currentPage > 0);
        nextPageButton.setEnabled((currentPage + 1) * PAGE_SIZE < allBadges.size());

        int start = currentPage * PAGE_SIZE;
        LinearLayout[] rows = {row1, row2, row3};

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int idx = start + row * 2 + col;
                if (idx < allBadges.size()) {
                    View cell = createBadgeCell(allBadges.get(idx));
                    rows[row].addView(cell);
                } else {
                    View empty = new View(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    empty.setLayoutParams(lp);
                    rows[row].addView(empty);
                }
            }
        }
    }

    private View createBadgeCell(AppDatabaseHelper.Badge badge) {
        View cell = LayoutInflater.from(this).inflate(R.layout.item_badge, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cell.setLayoutParams(lp);

        ImageView img = cell.findViewById(R.id.badgeImage);
        TextView questionMark = cell.findViewById(R.id.badgeQuestion);
        TextView name = cell.findViewById(R.id.badgeName);

        name.setText(badge.nombre);

        if (badge.desbloqueada) {
            int resId = getDrawableResId(badge.imagenDrawable);
            if (resId != 0) {
                img.setImageResource(resId);
            } else {
                img.setImageResource(R.drawable.badge_locked_default);
            }
            img.clearColorFilter();
            img.setImageAlpha(255);
            questionMark.setVisibility(View.GONE);
            img.setOnClickListener(v -> showBadgeDetail(badge));
        } else {
            img.setImageResource(R.drawable.badge_locked_default);
            img.clearColorFilter();
            img.setImageAlpha(255);
            questionMark.setVisibility(View.GONE);
            img.setOnClickListener(null);
        }

        return cell;
    }

    private void showBadgeDetail(AppDatabaseHelper.Badge badge) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_badge_detail, null);
        ImageView img = dialogView.findViewById(R.id.detailImage);
        TextView nameView = dialogView.findViewById(R.id.detailName);
        TextView descView = dialogView.findViewById(R.id.detailDesc);
        TextView dateView = dialogView.findViewById(R.id.detailDate);
        Button closeBtn = dialogView.findViewById(R.id.closeButton);

        int resId = getDrawableResId(badge.imagenDrawable);
        img.setImageResource(resId != 0 ? resId : R.drawable.badge_locked_default);
        nameView.setText(badge.nombre);
        descView.setText(badge.descripcion);
        if (badge.fechaDesbloqueo > 0) {
            String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(badge.fechaDesbloqueo));
            dateView.setText("Desbloqueada el " + date);
        } else {
            dateView.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private int getDrawableResId(String name) {
        if (name == null || name.isEmpty()) return 0;
        try {
            return getResources().getIdentifier(name, "drawable", getPackageName());
        } catch (Resources.NotFoundException e) {
            return 0;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (appDatabaseHelper != null) appDatabaseHelper.close();
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