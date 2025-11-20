package com.example.audion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.audion.psap.R;

public class LeftEarInstructionActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private Button buttonStartLeftTest;
    private int userId;
    private int hearingProfileId;
    private boolean fromNewProfile; // Flag to track new profile creation flow
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_left_ear_instruction);
        
        buttonStartLeftTest = findViewById(R.id.buttonStartLeftEarTest);
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        fromNewProfile = getIntent().getBooleanExtra("FROM_NEW_PROFILE", false);
        
        // NEW CLINICAL WORKFLOW: Pure Tone Test runs first (no calibration prerequisite)
        
        buttonStartLeftTest.setOnClickListener(v -> {
            // Add button press animation
            Animation buttonPress = AnimationUtils.loadAnimation(this, R.anim.button_press);
            v.startAnimation(buttonPress);
            
            // Check permissions before starting test
            if (checkAudioPermissions()) {
                startPureToneTest();
            } else {
                requestAudioPermissions();
            }
        });
    }

    private boolean checkAudioPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermissions() {
        ActivityCompat.requestPermissions(this, 
            new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            }, 
            PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "Permissions granted! Starting hearing test...", Toast.LENGTH_SHORT).show();
                startPureToneTest();
            } else {
                Toast.makeText(this, "Audio permissions are required for hearing tests. Please grant permissions and try again.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * REMOVED: OLD calibration prerequisite check
     * NEW CLINICAL WORKFLOW: Pure Tone Test runs first, then calibration
     * This method is no longer needed as pure tone testing doesn't require calibration
     */
    // private void verifyCalibrationCompleted() { ... } - REMOVED

    private void startPureToneTest() {
        Intent intent = new Intent(LeftEarInstructionActivity.this, PureToneTestActivity.class);
        intent.putExtra("EAR", "LEFT");
        intent.putExtra("USER_ID", userId);
        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
        intent.putExtra("FROM_NEW_PROFILE", fromNewProfile); // Pass flag to next activity
        startActivity(intent);
        overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        // PREVENT back button bypass - redirect to calibration if necessary
        new Thread(() -> {
            try {
                com.example.audion.data.AppDatabase db = com.example.audion.data.AppDatabase.getInstance(this);
                com.example.audion.data.CalibrationProfileDao calibrationProfileDao = db.calibrationProfileDao();
                boolean hasCalibrationProfile = calibrationProfileDao.getProfileCount(userId, hearingProfileId) > 0;
                
                runOnUiThread(() -> {
                    if (!hasCalibrationProfile) {
                        // Redirect to calibration instead of allowing back navigation
                        Toast.makeText(this, "⚠️ Complete calibration first", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, CalibrationInstructionActivity.class);
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("EAR", "RIGHT");
                        startActivity(intent);
                        finish();
                    } else {
                        // Allow normal back navigation if calibration is complete
                        super.onBackPressed();
                    }
                });
            } catch (Exception e) {
                // If error, redirect to calibration
                runOnUiThread(() -> super.onBackPressed());
            }
        }).start();
    }
}
