package com.example.audion;

import com.audion.psap.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.progressindicator.CircularProgressIndicator;

public class TestCompletionSplashActivity extends AppCompatActivity {

    private ImageView completionIcon;
    private TextView completionText;
    private TextView subtitleText;
    private CircularProgressIndicator progressIndicator;
    private int userId;
    private int hearingProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_completion_splash);

        // Get data from intent
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);

        initializeViews();
        startCompletionAnimation();
    }

    private void initializeViews() {
        completionIcon = findViewById(R.id.completionIcon);
        completionText = findViewById(R.id.completionText);
        subtitleText = findViewById(R.id.subtitleText);
        progressIndicator = findViewById(R.id.progressIndicator);
    }

    private void startCompletionAnimation() {
        // Start with elements invisible
        completionIcon.setAlpha(0f);
        completionText.setAlpha(0f);
        subtitleText.setAlpha(0f);

        // Animate the completion icon first
        completionIcon.animate()
                .alpha(1f)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(500)
                .setStartDelay(300)
                .withEndAction(() -> {
                    // Scale back to normal
                    completionIcon.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start();
                })
                .start();

        // Animate the completion text with a bounce effect
        completionText.animate()
                .alpha(1f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(400)
                .setStartDelay(800)
                .withEndAction(() -> {
                    // Scale back to normal
                    completionText.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start();
                })
                .start();

        // Animate the subtitle
        subtitleText.animate()
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(1200)
                .start();

        // Navigate to results page after 3 seconds
        new Handler().postDelayed(() -> {
            Intent resultIntent = new Intent(this, TestResultsActivity.class);
            resultIntent.putExtra("USER_ID", userId);
            resultIntent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(resultIntent);
            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
            finish();
        }, 3000);
    }
}
