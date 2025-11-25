package com.audion.app;

import com.audion.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PureToneIntroActivity extends AppCompatActivity {

    private Button btnStartTest;
    private TextView tvGreeting;
    private int userId;
    private int hearingProfileId;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_tone_intro);

        btnStartTest = findViewById(R.id.btnStartTest);
        tvGreeting = findViewById(R.id.tvGreeting);

        // Get data from intent
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        userName = getIntent().getStringExtra("user_name");

        // Set greeting with user's name
        if (userName != null && !userName.isEmpty()) {
            tvGreeting.setText("Hi " + userName + "!");
        } else {
            tvGreeting.setText("Hi!");
        }

        btnStartTest.setOnClickListener(v -> {
            // Navigate to Right Ear Instruction with all required data
            Intent intent = new Intent(PureToneIntroActivity.this, RightEarInstructionActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            intent.putExtra("user_name", userName);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_scale_out);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
