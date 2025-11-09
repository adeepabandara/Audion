package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

public class EarResultActivity extends AppCompatActivity {
    
    private TextView earLabel;
    private Chip resultChip;
    private MaterialButton buttonNext;
    private TextView tickAnimation; // Changed from LottieAnimationView to TextView
    
    // Navigation guard to prevent double navigation
    private boolean isNavigating = false;
    
    private String currentEar;
    private String result;
    private int userId;
    private int profileId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ear_result);
        
        Log.d("EarResultActivity", "onCreate started");
        
        // Get extras from intent
        currentEar = getIntent().getStringExtra("EAR");
        result = getIntent().getStringExtra("RESULT");
        userId = getIntent().getIntExtra("USER_ID", 1);
        profileId = getIntent().getIntExtra("HEARING_PROFILE_ID", 1);
        
        Log.d("EarResultActivity", "Received extras - EAR: " + currentEar + ", USER_ID: " + userId + ", PROFILE_ID: " + profileId);
        
        if (currentEar == null) currentEar = "RIGHT";
        if (result == null) result = "Calibration completed successfully!";
        
        try {
            // Initialize views
            earLabel = findViewById(R.id.tvEarLabel);
            resultChip = findViewById(R.id.resultChip);
            buttonNext = findViewById(R.id.buttonNext);
            tickAnimation = findViewById(R.id.tickAnimation);
            
            // Setup content based on ear
            setupContent();
            
            // Setup button click listener - with protection against double-clicks
            buttonNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isNavigating) {
                        Log.d("EarResultActivity", "Navigation already in progress, ignoring click");
                        return;
                    }
                    Log.d("EarResultActivity", "Next button clicked for ear: " + currentEar);
                    navigateNext();
                }
            });
            
            Log.d("EarResultActivity", "onCreate completed successfully");
        } catch (Exception e) {
            Log.e("EarResultActivity", "Error in onCreate", e);
            // If something fails, go back to main menu
            goToMainMenu();
        }
    }
    
    private void setupContent() {
        // Set ear label
        earLabel.setText(currentEar.equals("RIGHT") ? "Right Ear" : "Left Ear");
        
        // Set result text
        resultChip.setText(result);
        
        // Set button text and continue message based on which ear was just calibrated
        TextView continueText = findViewById(R.id.tvContinueText);
        
        if (currentEar.equals("RIGHT")) {
            buttonNext.setText("Next");
            if (continueText != null) {
                continueText.setText("Tap Next to continue to Left ear");
            }
        } else {
            // For left ear completion
            buttonNext.setText("Continue");
            if (continueText != null) {
                continueText.setText("Tap Continue to proceed to Pure Tone Test");
            }
        }
        
        // Show the success checkmark (no animation needed for TextView)
        if (tickAnimation != null) {
            tickAnimation.setVisibility(View.VISIBLE);
            Log.d("EarResultActivity", "Success checkmark displayed");
        }
    }
    
    private void navigateNext() {
        Log.d("EarResultActivity", "navigateNext called for ear: " + currentEar);
        
        // Prevent double navigation
        if (isNavigating) {
            Log.d("EarResultActivity", "Navigation already in progress, ignoring call");
            return;
        }
        isNavigating = true;
        
        // Disable button to prevent multiple clicks
        buttonNext.setEnabled(false);
        
        if (currentEar.equals("RIGHT")) {
            // Navigate to calibration instruction for LEFT ear (improved calibration flow)
            Log.d("EarResultActivity", "Navigating to LEFT ear calibration instruction");
            
            // Use a more explicit Intent without problematic flags
            Intent intent = new Intent(this, CalibrationInstructionActivity.class);
            intent.putExtra("EAR", "LEFT");
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", profileId);
            
            // Removed FLAG_ACTIVITY_CLEAR_TOP to prevent activity stack conflicts
            
            try {
                startActivity(intent);
                // Add animation
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                finish();
                Log.d("EarResultActivity", "Successfully started LEFT ear calibration");
            } catch (Exception e) {
                Log.e("EarResultActivity", "Failed to navigate to LEFT ear calibration", e);
                // Reset navigation flag on failure
                isNavigating = false;
                buttonNext.setEnabled(true);
                // Don't go to main menu on failure - stay on results page
                // Show an error message or retry option instead
                buttonNext.setText("Try Again");
                buttonNext.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Reset navigation guard before retry
                        isNavigating = false;
                        navigateNext(); // Retry the navigation
                    }
                });
            }
        } else {
            // LEFT ear calibration completed - navigate to General Instruction for Pure Tone Test
            Log.d("EarResultActivity", "LEFT ear completed, navigating to General Instruction");
            
            try {
                Intent intent = new Intent(this, GeneralInstructionActivity.class);
                intent.putExtra("USER_ID", userId);
                intent.putExtra("HEARING_PROFILE_ID", profileId);
                
                startActivity(intent);
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                finish();
                Log.d("EarResultActivity", "Successfully navigated to General Instruction");
            } catch (Exception e) {
                Log.e("EarResultActivity", "Failed to navigate to General Instruction", e);
                // Reset navigation flag on failure
                isNavigating = false;
                buttonNext.setEnabled(true);
                // More robust fallback - show error message and provide alternative options
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setTitle("Navigation Error");
                builder.setMessage("Unable to start Pure Tone Test instructions. Would you like to return to the main menu?");
                builder.setPositiveButton("Main Menu", (dialog, which) -> {
                    goToMainMenu();
                });
                builder.setNegativeButton("Try Again", (dialog, which) -> {
                    // Reset navigation guard and give user option to retry
                    isNavigating = false;
                    buttonNext.setEnabled(true);
                    navigateNext();
                });
                builder.setCancelable(false);
                builder.show();
            }
        }
    }
    
    private void goToMainMenu() {
        try {
            Log.d("EarResultActivity", "Going to main menu");
            // Go back to the name/main activity to start over
            Intent intent = new Intent(this, NameActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            Log.d("EarResultActivity", "Successfully navigated to main menu");
        } catch (Exception e) {
            Log.e("EarResultActivity", "Failed to navigate to main menu", e);
            // Ultimate fallback - just finish the activity
            finish();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d("EarResultActivity", "onResume called for ear: " + currentEar);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d("EarResultActivity", "onPause called for ear: " + currentEar);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("EarResultActivity", "onDestroy called for ear: " + currentEar);
    }
    
    @Override
    public void onBackPressed() {
        // Prevent back button from closing the results page unexpectedly
        Log.d("EarResultActivity", "onBackPressed - preventing default behavior");
        // Don't call super.onBackPressed() to prevent going back
        // User must use the Next/Continue button to proceed
    }
}
