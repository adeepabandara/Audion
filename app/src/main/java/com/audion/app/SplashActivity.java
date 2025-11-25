package com.audion.app;

import com.audion.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.audion.app.data.AppDatabase;
import com.audion.app.data.User;

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
                
                Log.d(TAG, "Total users found: " + (users != null ? users.size() : 0));
                
                // Check if there are any users with valid names
                boolean hasExistingUser = false;
                int foundUserId = -1;
                String foundUserName = null;
                if (users != null && !users.isEmpty()) {
                    for (User user : users) {
                        Log.d(TAG, "Checking user - ID: " + user.getId() + ", Name: '" + user.getName() + "'");
                        if (user.getName() != null && !user.getName().trim().isEmpty()) {
                            hasExistingUser = true;
                            foundUserId = user.getId();
                            foundUserName = user.getName();
                            Log.d(TAG, "✓ Found valid user: " + foundUserName + " (ID: " + foundUserId + ")");
                            break;
                        }
                    }
                }
                
                final int userId = foundUserId;
                
                // Check if user has completed the full test (both pure tone and calibration)
                boolean hasCompletedTest = false;
                if (hasExistingUser) {
                    try {
                        // Check for hearing test results (pure tone test)
                        List<com.audion.app.data.HearingTestResult> testResults = db.hearingTestResultDao().getResultsForUser(userId);
                        boolean hasPureToneResults = (testResults != null && !testResults.isEmpty());
                        Log.d(TAG, "Pure tone results count: " + (testResults != null ? testResults.size() : 0));
                        
                        // Check for calibration data - check CalibrationProfileEntity
                        boolean hasCalibrationData = false;
                        List<com.audion.app.data.HearingProfile> profiles = db.hearingProfileDao().getAllProfiles();
                        Log.d(TAG, "Hearing profiles count: " + (profiles != null ? profiles.size() : 0));
                        if (profiles != null && !profiles.isEmpty()) {
                            int profileId = profiles.get(0).getId();
                            // Check new calibration profile entities
                            com.audion.app.data.CalibrationProfileEntity leftProfile = db.calibrationProfileDao().getLatestProfileForEar(userId, "LEFT", profileId);
                            com.audion.app.data.CalibrationProfileEntity rightProfile = db.calibrationProfileDao().getLatestProfileForEar(userId, "RIGHT", profileId);
                            hasCalibrationData = (leftProfile != null && rightProfile != null);
                            Log.d(TAG, "Calibration profiles - LEFT: " + (leftProfile != null) + ", RIGHT: " + (rightProfile != null));
                        }
                        
                        // User has completed test only if BOTH pure tone and calibration are done
                        hasCompletedTest = hasPureToneResults && hasCalibrationData;
                        
                        Log.d(TAG, "===== NAVIGATION DECISION =====");
                        Log.d(TAG, "User exists: " + hasExistingUser + " (ID: " + userId + ", Name: " + foundUserName + ")");
                        Log.d(TAG, "Has pure tone results: " + hasPureToneResults);
                        Log.d(TAG, "Has calibration data: " + hasCalibrationData);
                        Log.d(TAG, "Test completed: " + hasCompletedTest);
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking test completion: " + e.getMessage(), e);
                    }
                }
                
                final boolean shouldShowOnboarding = !hasExistingUser;
                final boolean shouldShowTestIntro = hasExistingUser && !hasCompletedTest;
                
                Log.d(TAG, "Decision - Show onboarding: " + shouldShowOnboarding + ", Show test intro: " + shouldShowTestIntro);
                
                // Navigate after 3 second delay
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent;
                    
                    if (shouldShowOnboarding) {
                        // First time user - no user exists yet
                        Log.d(TAG, "→ Navigating to OnboardingCarouselActivity");
                        intent = new Intent(SplashActivity.this, OnboardingCarouselActivity.class);
                    } else if (shouldShowTestIntro) {
                        // User exists but hasn't completed test
                        Log.d(TAG, "→ Navigating to StartTestActivity");
                        intent = new Intent(SplashActivity.this, StartTestActivity.class);
                        intent.putExtra("USER_ID", userId);
                        
                        // Get the hearing profile ID for this user
                        int profileId = -1;
                        try {
                            List<com.audion.app.data.HearingProfile> profiles = db.hearingProfileDao().getAllProfiles();
                            Log.d(TAG, "Total profiles found: " + (profiles != null ? profiles.size() : 0));
                            if (profiles != null && !profiles.isEmpty()) {
                                profileId = profiles.get(0).getId();
                                intent.putExtra("HEARING_PROFILE_ID", profileId);
                                Log.d(TAG, "✅ Passing USER_ID: " + userId + ", HEARING_PROFILE_ID: " + profileId);
                            } else {
                                Log.e(TAG, "❌ No hearing profiles found in database!");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error getting profile ID: " + e.getMessage(), e);
                        }
                        
                        if (profileId < 0) {
                            Log.e(TAG, "❌ WARNING: No valid profile ID found, user will see error!");
                        }
                    } else {
                        // User exists and has completed test
                        Log.d(TAG, "→ Navigating to HomeActivity");
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
