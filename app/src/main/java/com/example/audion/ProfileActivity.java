package com.example.audion;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingTestResult;
import com.example.audion.views.AudiogramView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private BottomNavigationView bottomNav;
    private TextView profileTitle;
    private TextView testDateText;
    private AudiogramView audiogramView;
    
    private int userId;
    private int hearingProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Get user ID and profile ID from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AudionPrefs", MODE_PRIVATE);
        userId = prefs.getInt("CURRENT_USER_ID", 1);  // Default to user ID 1
        hearingProfileId = prefs.getInt("selectedProfileId", -1);  // Use same key as HomeActivity
        
        Log.d(TAG, "ProfileActivity loaded with User ID: " + userId + ", Profile ID: " + hearingProfileId);

        initializeViews();
        setupBottomNavigation();
        loadProfileData();
    }

    private void initializeViews() {
        profileTitle = findViewById(R.id.profileTitle);
        testDateText = findViewById(R.id.testDateText);
        audiogramView = findViewById(R.id.audiogramView);
        bottomNav = findViewById(R.id.bottomNavigationView);
    }

    private void setupBottomNavigation() {
        // Set Profile as selected
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navigation_settings);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.navigation_home) {
                    startActivity(new Intent(this, HomeActivity.class));
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (id == R.id.navigation_frequencies) {
                    startActivity(new Intent(this, FrequencyActivity.class));
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (id == R.id.navigation_settings) {
                    // Already on Profile
                    return true;
                }
                return false;
            });
        }
    }

    private void loadProfileData() {
        // Get user name from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AudionPrefs", MODE_PRIVATE);
        String userName = prefs.getString("user_name", "User");
        
        // Set title with user name immediately
        profileTitle.setText(userName + "'s Hearing Profile");
        
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                
                // If hearingProfileId is -1, get the default profile or first available profile
                int profileIdToLoad = hearingProfileId;
                if (profileIdToLoad <= 0) {
                    List<HearingProfile> profiles = db.hearingProfileDao().getAllProfiles();
                    if (profiles != null && !profiles.isEmpty()) {
                        profileIdToLoad = profiles.get(0).getId();
                        Log.d(TAG, "Using first available profile ID: " + profileIdToLoad);
                    } else {
                        Log.e(TAG, "No profiles found in database");
                        runOnUiThread(() -> {
                            testDateText.setText("No hearing profile found. Please create a profile first.");
                        });
                        return;
                    }
                }
                
                // Load user profile
                HearingProfile profile = db.hearingProfileDao().getHearingProfileById(profileIdToLoad);
                
                // Load test results
                List<HearingTestResult> allResults = db.hearingTestResultDao()
                    .getResultsByUserAndProfile(userId, profileIdToLoad);
                
                Log.d(TAG, "Loaded " + allResults.size() + " test results for user " + userId + " and profile " + profileIdToLoad);
                
                // Separate left and right ear results
                List<HearingTestResult> leftEarResults = new ArrayList<>();
                List<HearingTestResult> rightEarResults = new ArrayList<>();
                long latestTestTimestamp = 0;
                
                for (HearingTestResult result : allResults) {
                    Log.d(TAG, "Result: " + result.getEarSide() + " " + result.getFrequency() + "Hz = " + result.getThresholdDbHL() + "dB HL");
                    
                    if ("LEFT".equals(result.getEarSide())) {
                        leftEarResults.add(result);
                    } else if ("RIGHT".equals(result.getEarSide())) {
                        rightEarResults.add(result);
                    }
                    
                    // Track latest test timestamp
                    if (result.getTestTimestamp() > latestTestTimestamp) {
                        latestTestTimestamp = result.getTestTimestamp();
                    }
                }
                
                Log.d(TAG, "Left ear results: " + leftEarResults.size() + ", Right ear results: " + rightEarResults.size());
                
                final Date finalTestDate = latestTestTimestamp > 0 ? new Date(latestTestTimestamp) : null;
                
                runOnUiThread(() -> {
                    // Display audiogram
                    if (!leftEarResults.isEmpty() || !rightEarResults.isEmpty()) {
                        audiogramView.setLeftEarResults(leftEarResults);
                        audiogramView.setRightEarResults(rightEarResults);
                        audiogramView.invalidate(); // Force redraw
                        
                        // Set test date
                        if (finalTestDate != null) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
                            testDateText.setText("Last test: " + dateFormat.format(finalTestDate));
                        } else {
                            testDateText.setText("Last test: Recently completed");
                        }
                    } else {
                        testDateText.setText("No test data available. Complete a hearing test to see your audiogram.");
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading profile data", e);
                runOnUiThread(() -> {
                    testDateText.setText("Error loading test data");
                });
            }
        }).start();
    }
}
