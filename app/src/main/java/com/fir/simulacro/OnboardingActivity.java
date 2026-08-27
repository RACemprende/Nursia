package com.fir.simulacro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Button skipButton;
    private Button nextButton;
    private OnboardingAdapter adapter;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        
        startBackgroundAnimation();

        viewPager = findViewById(R.id.onboardingViewPager);
        tabLayout = findViewById(R.id.onboardingTabLayout);
        skipButton = findViewById(R.id.onboardingSkipButton);
        nextButton = findViewById(R.id.onboardingNextButton);

        adapter = new OnboardingAdapter(this, createPages());
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {}).attach();

        skipButton.setOnClickListener(v -> finishOnboarding());
        nextButton.setOnClickListener(v -> {
            if (currentPage < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentPage + 1, true);
            } else {
                finishOnboarding();
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPage = position;
                nextButton.setText(position == adapter.getItemCount() - 1 ? "Empezar" : "Siguiente");
            }
        });
    }

    private List<OnboardingPage> createPages() {
        List<OnboardingPage> pages = new ArrayList<>();
        pages.add(new OnboardingPage(
                "Bienvenido a OPE SESPA",
                "Simulacros de oposiciones de enfermería para aprobar",
                R.drawable.app_icon
        ));
        pages.add(new OnboardingPage(
                "Cómo jugar",
                "Responde preguntas de exámenes anteriores. Cada respuesta correcta = 1 punto. Ganarás puntos para canjeables.",
                R.drawable.badge_exams_3_img
        ));
        pages.add(new OnboardingPage(
                "Premios y puntos",
                "Usa tus puntos para canjear premios personalizados. Crea tus propios premios o usa los ejemplos.",
                R.drawable.streak_positive_5_img
        ));
        pages.add(new OnboardingPage(
                "Insignias y logros",
                "Desbloquea insignias por rachas, simulacros completados y otros logros. ¡Juega todos los días!",
                R.drawable.badge_daily_1_img
        ));
        return pages;
    }

    private void finishOnboarding() {
        OnboardingHelper.markOnboardingDone(this);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    public static class OnboardingPage {
        public String title;
        public String description;
        public int imageRes;

        public OnboardingPage(String title, String description, int imageRes) {
            this.title = title;
            this.description = description;
            this.imageRes = imageRes;
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