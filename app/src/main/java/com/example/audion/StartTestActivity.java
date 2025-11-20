package com.example.audion;

import com.audion.psap.R;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class StartTestActivity extends AppCompatActivity {

    private Button btnStartTest;
    private int userId;
    private int hearingProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_test);

        // Initialize views
        btnStartTest = findViewById(R.id.btnStartTest);

        // Get USER_ID and HEARING_PROFILE_ID from intent
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);

        // Start Test button click
        btnStartTest.setOnClickListener(v -> {
            // Mark onboarding as completed
            getSharedPreferences("AudionPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_completed", true)
                .apply();

            // Navigate directly to Right Ear Instruction with IDs
            Intent intent = new Intent(StartTestActivity.this, RightEarInstructionActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            startActivity(intent);
            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
            finish();
        });
    }
}
