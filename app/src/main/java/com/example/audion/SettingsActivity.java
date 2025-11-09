package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.example.audion.config.FeatureFlags;
import android.widget.Toast;
import android.util.Log;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialButton btnStartCal = findViewById(R.id.btnStartCalibration);
        btnStartCal.setOnClickListener(v -> {
            startActivity(new Intent(this, CalibrationInstructionActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
        });
        
        // Add QA Settings - Long click to toggle new DSP pipeline
        btnStartCal.setOnLongClickListener(v -> {
            toggleDspPipeline();
            return true;
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
        } else if (id == R.id.navigation_music) {
            startActivity(new Intent(this, MusicPlayerActivity.class));
            overridePendingTransition(0,0);
            return true;
        } else if (id == R.id.navigation_settings) {
            return true;
        }
        return false;
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