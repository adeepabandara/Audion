package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PureToneIntroActivity extends AppCompatActivity {

    private Button btnStartTest;
    private Button btnContinueLater;
    private int userId;
    private int hearingProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_tone_intro);

        btnStartTest = findViewById(R.id.btnStartTest);
        btnContinueLater = findViewById(R.id.btnContinueLater);

        // Get USER_ID and HEARING_PROFILE_ID from intent
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);

        btnStartTest.setOnClickListener(v -> {
            // Navigate to Right Ear Pure Tone Test with IDs
            Intent intent = new Intent(PureToneIntroActivity.this, RightEarInstructionActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            startActivity(intent);
        });

        btnContinueLater.setOnClickListener(v -> {
            // Navigate to Home Activity
            Intent intent = new Intent(PureToneIntroActivity.this, HomeActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
