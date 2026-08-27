package com.fir.simulacro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.List;

public class OnboardingAdapter extends FragmentStateAdapter {
    private final List<OnboardingActivity.OnboardingPage> pages;

    public OnboardingAdapter(FragmentActivity activity, List<OnboardingActivity.OnboardingPage> pages) {
        super(activity);
        this.pages = pages;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return OnboardingFragment.newInstance(pages.get(position));
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    public static class OnboardingFragment extends Fragment {
        private static final String ARG_TITLE = "title";
        private static final String ARG_DESC = "desc";
        private static final String ARG_IMAGE = "image";

        public static OnboardingFragment newInstance(OnboardingActivity.OnboardingPage page) {
            OnboardingFragment fragment = new OnboardingFragment();
            Bundle args = new Bundle();
            args.putString(ARG_TITLE, page.title);
            args.putString(ARG_DESC, page.description);
            args.putInt(ARG_IMAGE, page.imageRes);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_onboarding_page, container, false);

            TextView titleView = view.findViewById(R.id.onboardingPageTitle);
            TextView descView = view.findViewById(R.id.onboardingPageDescription);
            ImageView imageView = view.findViewById(R.id.onboardingPageImage);

            if (getArguments() != null) {
                titleView.setText(getArguments().getString(ARG_TITLE, ""));
                descView.setText(getArguments().getString(ARG_DESC, ""));
                imageView.setImageResource(getArguments().getInt(ARG_IMAGE, 0));
            }

            return view;
        }
    }
}