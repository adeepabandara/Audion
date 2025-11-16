package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AppCompatActivity;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.CalibrationProfileEntity;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingTestResult;
import com.example.audion.utils.CustomToast;
import com.example.audion.views.AudiogramView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TestResultsActivity extends AppCompatActivity {

    private static final String TAG = "TestResultsActivity";
    
    private AudiogramView audiogramView;
    private MaterialButton btnProceedToHome;
    private TextView titleText, userNameText, testDateText;
    
    private int userId;
    private int hearingProfileId;
    private String userName;
    private boolean fromNewProfile; // Flag to track new profile creation flow
    
    // Data containers
    private List<HearingTestResult> leftEarResults;
    private List<HearingTestResult> rightEarResults;
    private CalibrationProfileEntity leftEarCalibration;
    private CalibrationProfileEntity rightEarCalibration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_results);

        // Get data from intent
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        fromNewProfile = getIntent().getBooleanExtra("FROM_NEW_PROFILE", false);

        initializeViews();
        loadUserData();
        loadTestResults();
        setupButtons();
    }

    private void initializeViews() {
        titleText = findViewById(R.id.titleText);
        userNameText = findViewById(R.id.userNameText);
        testDateText = findViewById(R.id.testDateText);
        audiogramView = findViewById(R.id.audiogramView);
        btnProceedToHome = findViewById(R.id.btnProceedToHome);
        
        // Set current date with time
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy HH:mm", Locale.getDefault());
        testDateText.setText(dateFormat.format(new Date()));
    }

    private void loadUserData() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                HearingProfile profile = db.hearingProfileDao().getHearingProfileById(hearingProfileId);
                
                runOnUiThread(() -> {
                    if (profile != null) {
                        userName = profile.getName();
                        titleText.setText(userName + " Your Test Results");
                        userNameText.setText(userName);
                    } else {
                        userName = "Unknown User";
                        titleText.setText("Your Test Results");
                        userNameText.setText(userName);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading user data", e);
                runOnUiThread(() -> {
                    userName = "Unknown User";
                    titleText.setText("Your Test Results");
                    userNameText.setText(userName);
                });
            }
        }).start();
    }

    private void loadTestResults() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                
                Log.d(TAG, "═══════════════════════════════════════");
                Log.d(TAG, "Loading test results for:");
                Log.d(TAG, "  User ID: " + userId);
                Log.d(TAG, "  Profile ID: " + hearingProfileId);
                Log.d(TAG, "═══════════════════════════════════════");
                
                // Load Pure Tone test results
                List<HearingTestResult> allResults = db.hearingTestResultDao()
                    .getResultsByUserAndProfile(userId, hearingProfileId);
                
                Log.d(TAG, "Query returned " + allResults.size() + " total results");
                
                leftEarResults = new ArrayList<>();
                rightEarResults = new ArrayList<>();
                
                for (HearingTestResult result : allResults) {
                    Log.d(TAG, String.format("Result: %s ear, %dHz, %.1f dB HL, ProfileID=%d", 
                        result.getEarSide(), result.getFrequency(), 
                        result.getThresholdDbHL(), result.getHearingProfileId()));
                    
                    if ("LEFT".equals(result.getEarSide())) {
                        leftEarResults.add(result);
                    } else if ("RIGHT".equals(result.getEarSide())) {
                        rightEarResults.add(result);
                    }
                }
                
                // Load Calibration data
                List<CalibrationProfileEntity> calibrations = db.calibrationProfileDao()
                    .getForUserProfile(userId, hearingProfileId);
                
                Log.d(TAG, "Query returned " + calibrations.size() + " calibration results");
                
                for (CalibrationProfileEntity calib : calibrations) {
                    if ("LEFT".equals(calib.getEarSide())) {
                        leftEarCalibration = calib;
                    } else if ("RIGHT".equals(calib.getEarSide())) {
                        rightEarCalibration = calib;
                    }
                }
                
                runOnUiThread(() -> {
                    displayCombinedResults();
                    Log.d(TAG, "═══════════════════════════════════════");
                    Log.d(TAG, "Displaying: " + leftEarResults.size() + " LEFT results, " + 
                          rightEarResults.size() + " RIGHT results");
                    Log.d(TAG, "═══════════════════════════════════════");
                    
                    if (allResults.isEmpty()) {
                        CustomToast.showError(this, "⚠️ No test data found for this profile", CustomToast.LENGTH_LONG);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading test results", e);
                runOnUiThread(() -> {
                    CustomToast.showError(this, "Error loading test results: " + e.getMessage(), CustomToast.LENGTH_LONG);
                });
            }
        }).start();
    }

    private void displayCombinedResults() {
        if (audiogramView != null) {
            // AudiogramView will display both ears - pass both datasets
            audiogramView.setLeftEarResults(leftEarResults);
            audiogramView.setRightEarResults(rightEarResults);
        }
    }

    private void setupButtons() {
        btnProceedToHome.setOnClickListener(v -> {
            // Show success toast if this was a new profile creation
            if (fromNewProfile) {
                CustomToast.showSuccess(this, "Profile created successfully!", CustomToast.LENGTH_LONG);
            }
            
            // Navigate directly to HomeActivity (the actual home screen with audio controls)
            Intent intent = new Intent(TestResultsActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
