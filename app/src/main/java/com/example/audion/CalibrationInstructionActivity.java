package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

public class CalibrationInstructionActivity extends AppCompatActivity {
    private boolean fromNewProfile; // Flag to track new profile creation flow
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // Use the simplified layout
        setContentView(R.layout.activity_calibration_instruction_simplified);

        // Figure out which ear we're on (default to RIGHT)
        String ear = getIntent().getStringExtra("EAR");
        if (ear == null) ear = "RIGHT";
        
        // Retrieve FROM_NEW_PROFILE flag
        fromNewProfile = getIntent().getBooleanExtra("FROM_NEW_PROFILE", false);

        // TEMPORARY: Skip pure tone prerequisite check to allow LEFT ear → Calibration flow
        // verifyPureToneCompleted(); // Commented out to fix navigation issue

        // Set up UI elements
        setupUI(ear);

        // Wire up the button
        final String finalEar = ear;
        MaterialButton btn = findViewById(R.id.buttonBegin);
        btn.setOnClickListener(v -> {
            // ✅ Use refactored SeekBar-based calibration activity
            Intent i = new Intent(CalibrationInstructionActivity.this,
                                  CalibrationTestActivityRefactored.class);
            i.putExtra("EAR", finalEar);
            i.putExtra("USER_ID", getIntent().getIntExtra("USER_ID", 1));
            i.putExtra("HEARING_PROFILE_ID", getIntent().getIntExtra("HEARING_PROFILE_ID", 1));
            i.putExtra("FROM_NEW_PROFILE", fromNewProfile); // Pass flag to next activity
            startActivity(i);
            try {
                overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
            } catch (Exception e) {
                // Fallback if animations don't exist
            }
            finish();
        });
    }
    
    private void setupUI(String ear) {
        TextView title = findViewById(R.id.tvTitle);
        TextView description = findViewById(R.id.tvDescription);
        ImageView earImage = findViewById(R.id.imageViewEar);
        
        // Update title and description based on ear
        if (ear.equalsIgnoreCase("LEFT")) {
            title.setText("Left Ear Calibration");
            description.setText("We'll play 3 standard levels for your left ear calibration");
            // Set left ear image if available
            try {
                earImage.setImageResource(R.drawable.left_ear);
            } catch (Exception e) {
                // Fallback to generic ear image or hide
                try {
                    earImage.setImageResource(R.drawable.ear);
                } catch (Exception ex) {
                    // No image available, keep default
                }
            }
        } else {
            title.setText("Right Ear Calibration");
            description.setText("We'll play 3 standard levels for your right ear calibration");
            // Set right ear image if available
            try {
                earImage.setImageResource(R.drawable.right_ear);
            } catch (Exception e) {
                // Fallback to generic ear image
                try {
                    earImage.setImageResource(R.drawable.ear);
                } catch (Exception ex) {
                    // No image available, keep default
                }
            }
        }
    }

    /**
     * NEW CLINICAL WORKFLOW: Verify Pure Tone Test completed before allowing calibration
     * Enforce the correct order: Pure Tone Test → Calibration
     */
    private void verifyPureToneCompleted() {
        new Thread(() -> {
            try {
                int userId = getIntent().getIntExtra("USER_ID", 1);
                int hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", 1);
                
                // Check hearing test database for pure tone test results
                com.example.audion.data.AppDatabase db = com.example.audion.data.AppDatabase.getInstance(this);
                com.example.audion.data.HearingTestResultDao hearingTestDao = db.hearingTestResultDao();
                
                // Check for pure tone results for both ears using the correct DAO methods
                int leftEarCount = hearingTestDao.getResultsForEar("LEFT", userId).size();
                int rightEarCount = hearingTestDao.getResultsForEar("RIGHT", userId).size();
                boolean hasPureToneResults = (leftEarCount > 0) && (rightEarCount > 0);
                
                android.util.Log.d("CalibrationInstruction", "Pure tone check - LEFT: " + leftEarCount + ", RIGHT: " + rightEarCount + ", Both complete: " + hasPureToneResults);
                
                runOnUiThread(() -> {
                    if (!hasPureToneResults) {
                        // BLOCK: Pure tone test not completed - redirect to pure tone testing
                        android.widget.Toast.makeText(this, "❌ Pure Tone Test required first! Redirecting...", android.widget.Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(this, RightEarInstructionActivity.class);
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish(); // Prevent back navigation to bypass pure tone test
                    }
                    // If pure tone results exist, allow calibration to proceed normally
                });
                
            } catch (Exception e) {
                android.util.Log.e("CalibrationInstruction", "Error checking pure tone status", e);
                runOnUiThread(() -> {
                    // Fallback - redirect to pure tone test if we can't verify
                    android.widget.Toast.makeText(this, "Unable to verify pure tone test - redirecting", android.widget.Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, RightEarInstructionActivity.class);
                    intent.putExtra("USER_ID", getIntent().getIntExtra("USER_ID", 1));
                    intent.putExtra("HEARING_PROFILE_ID", getIntent().getIntExtra("HEARING_PROFILE_ID", 1));
                    startActivity(intent);
                    finish();
                });
            }
        }).start();
    }
}
