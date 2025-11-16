package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.User;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private ExecutorService executorService;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide the ActionBar if it exists
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_splash);

        executorService = Executors.newSingleThreadExecutor();
        
        // Check database for existing users
        checkForExistingUsers();
    }
    
    private void checkForExistingUsers() {
        executorService.execute(() -> {
            try {
                // Query database for users
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                List<User> users = db.userDao().getAllUsers();
                
                // Check if there are any users with valid names
                boolean hasExistingUser = false;
                if (users != null && !users.isEmpty()) {
                    for (User user : users) {
                        if (user.getName() != null && !user.getName().trim().isEmpty()) {
                            hasExistingUser = true;
                            Log.d(TAG, "Found existing user: " + user.getName());
                            break;
                        }
                    }
                }
                
                final boolean shouldShowOnboarding = !hasExistingUser;
                Log.d(TAG, "Has existing users: " + hasExistingUser + ", Show onboarding: " + shouldShowOnboarding);
                
                // Navigate after 3 second delay
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent;
                    
                    if (shouldShowOnboarding) {
                        // First time user - no profiles exist yet
                        // Show carousel onboarding and full flow
                        Log.d(TAG, "Navigating to OnboardingCarouselActivity (first-time user)");
                        intent = new Intent(SplashActivity.this, OnboardingCarouselActivity.class);
                    } else {
                        // Returning user - at least one profile exists
                        // Skip carousel and onboarding, go directly to Home
                        Log.d(TAG, "Navigating to HomeActivity (returning user)");
                        intent = new Intent(SplashActivity.this, HomeActivity.class);
                    }
                    
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
                    finish();
                }, 3000);
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking for existing users: " + e.getMessage(), e);
                
                // Fallback to onboarding flow if there's an error
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(SplashActivity.this, OnboardingCarouselActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
                    finish();
                }, 3000);
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
