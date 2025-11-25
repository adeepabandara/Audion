package com.audion.app;

import com.audion.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {

    private Button btnAppleSignIn;
    private TextView tvMainTitle, tvNatural, tvTechnology, tvDescription;
    private View[] indicators;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private int currentPage = 0;
    private Animation buttonPressAnimation;
    
    // Cache animations and dimensions for performance
    private Animation fadeOut, fadeIn;
    private int activeIndicatorWidth, activeIndicatorHeight, dotSize;
    
    // Onboarding content data
    private String[][] onboardingContent = {
        {"We believe in", "Natural", "Technology", "Experience the perfect harmony of natural sound processing and cutting-edge technology."},
        {"Discover", "Smart", "Solutions", "Our AI-powered algorithms adapt to your unique hearing profile for personalized audio."},
        {"Enjoy", "Crystal", "Clear", "Advanced noise filtering ensures you hear what matters most in any environment."},
        {"Connect", "With", "World", "Stay connected with friends, family, and your surroundings through enhanced audio."}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Hide title bar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        // Initialize views
        btnAppleSignIn = findViewById(R.id.btnAppleSignIn);
        tvMainTitle = findViewById(R.id.tvMainTitle);
        tvNatural = findViewById(R.id.tvNatural);
        tvTechnology = findViewById(R.id.tvTechnology);
        tvDescription = findViewById(R.id.tvDescription);
        
        // Initialize indicators
        indicators = new View[]{
            findViewById(R.id.indicator1),
            findViewById(R.id.indicator2),
            findViewById(R.id.indicator3),
            findViewById(R.id.indicator4)
        };

        // Load animations
        buttonPressAnimation = AnimationUtils.loadAnimation(this, R.anim.button_press);
        
        // Cache animations and dimensions for performance
        fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        activeIndicatorWidth = getResources().getDimensionPixelSize(R.dimen.indicator_active_width);
        activeIndicatorHeight = getResources().getDimensionPixelSize(R.dimen.indicator_active_height);
        dotSize = getResources().getDimensionPixelSize(R.dimen.indicator_dot_size);

        // Set click listeners
        btnAppleSignIn.setOnClickListener(v -> {
            v.startAnimation(buttonPressAnimation);
            // Navigate to NameActivity
            Intent intent = new Intent(this, NameActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
        });
        
        // Start carousel
        startCarousel();
    }
    
    private void startCarousel() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateContent();
                currentPage = (currentPage + 1) % onboardingContent.length;
                handler.postDelayed(this, 8000); // Change every 8 seconds
            }
        }, 8000);
    }
    
    private void updateContent() {
        String[] content = onboardingContent[currentPage];
        
        // Clear any existing listeners to prevent leaks
        fadeOut.setAnimationListener(null);
        
        // Fade out current content
        tvMainTitle.startAnimation(fadeOut);
        tvNatural.startAnimation(fadeOut);
        tvTechnology.startAnimation(fadeOut);
        tvDescription.startAnimation(fadeOut);
        
        // Update content after fade out completes and fade in
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            
            @Override
            public void onAnimationEnd(Animation animation) {
                // Update text content
                tvMainTitle.setText(content[0]);
                tvNatural.setText(content[1]);
                tvTechnology.setText(content[2]);
                tvDescription.setText(content[3]);
                
                // Fade in new content
                tvMainTitle.startAnimation(fadeIn);
                tvNatural.startAnimation(fadeIn);
                tvTechnology.startAnimation(fadeIn);
                tvDescription.startAnimation(fadeIn);
            }
            
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        
        // Update indicators with a slight delay
        handler.postDelayed(this::updateIndicators, 150);
    }
    
    private void updateIndicators() {
        for (int i = 0; i < indicators.length; i++) {
            if (i == currentPage) {
                indicators[i].setBackgroundResource(R.drawable.indicator_active);
                // Set active indicator dimensions using cached values
                indicators[i].getLayoutParams().width = activeIndicatorWidth;
                indicators[i].getLayoutParams().height = activeIndicatorHeight;
            } else {
                indicators[i].setBackgroundResource(R.drawable.indicator_dot);
                // Set inactive indicator dimensions using cached values
                indicators[i].getLayoutParams().width = dotSize;
                indicators[i].getLayoutParams().height = dotSize;
            }
            indicators[i].requestLayout();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
