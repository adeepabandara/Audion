package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.audion.adapter.OnboardingAdapter;
import com.example.audion.model.OnboardingSlide;

import java.util.ArrayList;
import java.util.List;

public class OnboardingCarouselActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button btnGetStarted;
    private View indicator1, indicator2, indicator3;
    private View[] indicators;
    
    private Handler autoScrollHandler;
    private Runnable autoScrollRunnable;
    private static final long AUTO_SCROLL_DELAY = 5000; // 5 seconds
    
    private List<OnboardingSlide> slides;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding_carousel);

        // Initialize views
        viewPager = findViewById(R.id.viewPager);
        btnGetStarted = findViewById(R.id.btnGetStarted);
        indicator1 = findViewById(R.id.indicator1);
        indicator2 = findViewById(R.id.indicator2);
        indicator3 = findViewById(R.id.indicator3);
        
        indicators = new View[]{indicator1, indicator2, indicator3};

        // Setup slides data
        setupSlides();

        // Setup ViewPager2 adapter
        OnboardingAdapter adapter = new OnboardingAdapter(slides);
        viewPager.setAdapter(adapter);

        // Setup page change callback for indicators
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateIndicators(position);
                resetAutoScroll();
            }
        });

        // Setup auto-scroll
        setupAutoScroll();

        // Get Started button click
        btnGetStarted.setOnClickListener(v -> {
            stopAutoScroll();
            Intent intent = new Intent(OnboardingCarouselActivity.this, WhatsYourNameActivity.class);
            startActivity(intent);
            finish();
        });

        // Initialize indicators
        updateIndicators(0);
    }

    private void setupSlides() {
        slides = new ArrayList<>();
        
        // Slide 1
        slides.add(new OnboardingSlide(
            "Welcome to Audion",
            "Audion stands beside you, helping you hear what matters with comfort and confidence.",
            R.drawable.c1
        ));
        
        // Slide 2
        slides.add(new OnboardingSlide(
            "Your Focus, Perfected",
            "Audion lets you tune in to the right voice, so every sound stays clear and close.",
            R.drawable.c2
        ));
        
        // Slide 3
        slides.add(new OnboardingSlide(
            "Hear. Connect. Belong.",
            "Audion helps you follow every word, making easier to share moments that matter.",
            R.drawable.c3
        ));
    }

    private void updateIndicators(int position) {
        for (int i = 0; i < indicators.length; i++) {
            if (i == position) {
                indicators[i].setBackgroundResource(R.drawable.indicator_active);
            } else {
                indicators[i].setBackgroundResource(R.drawable.indicator_inactive);
            }
        }
    }

    private void setupAutoScroll() {
        autoScrollHandler = new Handler(Looper.getMainLooper());
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                int currentItem = viewPager.getCurrentItem();
                int nextItem = (currentItem + 1) % slides.size();
                viewPager.setCurrentItem(nextItem, true);
                autoScrollHandler.postDelayed(this, AUTO_SCROLL_DELAY);
            }
        };
        autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
    }

    private void resetAutoScroll() {
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
            autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
        }
    }

    private void stopAutoScroll() {
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoScroll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoScroll();
    }
}
