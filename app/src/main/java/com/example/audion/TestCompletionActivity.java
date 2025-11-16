package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import pl.droidsonroids.gif.GifImageView;

public class TestCompletionActivity extends AppCompatActivity {
    
    private GifImageView gifAnimation;
    private TextView tvCompletionMessage;
    private int userId;
    private int hearingProfileId;
    private boolean fromNewProfile; // Flag to track new profile creation flow
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_completion);
        
        // Get extras
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        fromNewProfile = getIntent().getBooleanExtra("FROM_NEW_PROFILE", false);
        
        // Initialize views
        gifAnimation = findViewById(R.id.gifAnimation);
        tvCompletionMessage = findViewById(R.id.tvCompletionMessage);
        
        // Fade in the completion message
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);
        tvCompletionMessage.startAnimation(fadeIn);
        
        // Navigate to results after 3 seconds
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(TestCompletionActivity.this, TestResultsActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            intent.putExtra("FROM_NEW_PROFILE", fromNewProfile); // Pass flag to final activity
            startActivity(intent);
            finish();
        }, 3000);
    }
}
