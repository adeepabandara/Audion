package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialButton btnStartCal = findViewById(R.id.btnStartCalibration);
        btnStartCal.setOnClickListener(v -> {
            startActivity(new Intent(this, CalibrationInstructionActivity.class));
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
}