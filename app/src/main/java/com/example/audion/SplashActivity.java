package com.example.audion;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "audion_onboarding";
    private static final String KEY_IS_FIRST_TIME_USER = "is_first_time_user";
    private static final String KEY_HEARING_TEST_COMPLETED = "hearing_test_completed";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide the ActionBar if it exists
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_splash);

        // Check if user has already completed onboarding and hearing tests
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstTimeUser = prefs.getBoolean(KEY_IS_FIRST_TIME_USER, true);
        boolean hearingTestCompleted = prefs.getBoolean(KEY_HEARING_TEST_COMPLETED, false);

        // Delay for 3 seconds then navigate appropriately
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent;
                
                if (isFirstTimeUser) {
                    // First time user, start new carousel onboarding
                    intent = new Intent(SplashActivity.this, OnboardingCarouselActivity.class);
                } else {
                    // IMPORTANT: Always go to HomeActivity for existing users
                    // HomeActivity will enforce correct hearing setup order with database checks
                    // This prevents bypassing the mandatory Calibration → Pure Tone → Results flow
                    intent = new Intent(SplashActivity.this, HomeActivity.class);
                }
                
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
                finish();
            }
        }, 3000);
    }
}
