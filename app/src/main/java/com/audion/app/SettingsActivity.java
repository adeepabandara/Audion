package com.audion.app;

import com.audion.app.R;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.audion.app.config.FeatureFlags;
import com.audion.app.data.AppDatabase;
import android.widget.Toast;
import android.util.Log;

public class SettingsActivity extends AppCompatActivity {
    
    private static final String PRIVACY_POLICY_URL = "https://www.audion.live/privacy-policy";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialButton btnStartCal = findViewById(R.id.btnStartCalibration);
        btnStartCal.setOnClickListener(v -> {
            startActivity(new Intent(this, CalibrationInstructionActivity.class));
            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
        });
        
        // Add QA Settings - Long click to toggle new DSP pipeline
        btnStartCal.setOnLongClickListener(v -> {
            toggleDspPipeline();
            return true;
        });

        // Privacy Policy button
        MaterialButton btnPrivacyPolicy = findViewById(R.id.btnPrivacyPolicy);
        btnPrivacyPolicy.setOnClickListener(v -> {
            openPrivacyPolicy();
        });

        // Delete All Data button
        MaterialButton btnDeleteAllData = findViewById(R.id.btnDeleteAllData);
        btnDeleteAllData.setOnClickListener(v -> {
            showDeleteConfirmationDialog();
        });

        BottomNavigationView nav = findViewById(R.id.bottomNavigationView);
        nav.setSelectedItemId(R.id.navigation_settings);
        nav.setOnItemSelectedListener(this::onNavItemSelected);
    }

    private boolean onNavItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.navigation_home) {
            startActivity(new Intent(this, HomeActivity.class));
            overridePendingTransition(0,0);
            return true;
        } else if (id == R.id.navigation_frequencies) {
            startActivity(new Intent(this, FrequencyActivity.class));
            overridePendingTransition(0,0);
            return true;
        } else if (id == R.id.navigation_settings) {
            // Already on Settings
            return true;
        }
        return false;
    }
    
    private void openPrivacyPolicy() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open Privacy Policy", Toast.LENGTH_SHORT).show();
            Log.e("SettingsActivity", "Error opening Privacy Policy", e);
        }
    }
    
    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_data_confirmation_title)
            .setMessage(R.string.delete_data_confirmation_message)
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                dialog.dismiss();
            })
            .setPositiveButton(R.string.delete, (dialog, which) -> {
                deleteAllData();
                dialog.dismiss();
            })
            .show();
    }
    
    private void deleteAllData() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.deleting_data));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            try {
                // Clear database
                AppDatabase db = AppDatabase.getInstance(this);
                db.clearAllTables();
                
                // Clear SharedPreferences
                getSharedPreferences("AudionPrefs", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();
                
                // Wait a moment for UI effect
                Thread.sleep(1000);
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, R.string.data_deleted_toast, Toast.LENGTH_LONG).show();
                    
                    // Restart app to first-run state
                    Intent intent = new Intent(this, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error deleting data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("SettingsActivity", "Error deleting data", e);
                });
            }
        }).start();
    }
    
    /**
     * QA/Developer function to toggle DSP pipeline
     */
    private void toggleDspPipeline() {
        boolean currentState = FeatureFlags.useNewPipeline(this);
        boolean newState = !currentState;
        
        FeatureFlags.setUseNewPipeline(this, newState);
        FeatureFlags.setQaMode(this, true); // Enable QA mode when toggling pipeline
        
        String pipelineType = newState ? "NEW DSP" : "LEGACY";
        String message = String.format("🔧 %s Pipeline ENABLED\n\nRestart audio processing to take effect\n\n%s", 
                                      pipelineType, FeatureFlags.getDebugInfo(this));
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.i("QA", "DSP Pipeline toggled to: " + pipelineType);
    }
}