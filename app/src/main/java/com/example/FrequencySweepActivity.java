package com.example.audion;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.audion.audio.ToneGenerator;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.audion.psap.R;
import com.google.android.material.button.MaterialButton;
import android.view.Window;
import android.view.WindowManager;

public class FrequencySweepActivity extends AppCompatActivity {
    private final int[] frequencies = {1000, 2000, 3000, 4000, 8000, 1000, 500, 250};
    private int currentFreqIndex = 0;
    private String currentEar;

    private ProgressBar progressBar;
    private MaterialButton btnStart, btnHeard, btnNotHeard;
    private ImageView earImage;
    private TextView tvCountdown;

    private volatile boolean stopPlayback = false;
    private int lastAmplitudeStep = 0;

    private int userId, profileId;
    private HearingTestResultDao resultDao;

    public static Intent intentFor(Context ctx, String ear, int userId, int profileId) {
        Intent intent = new Intent(ctx, FrequencySweepActivity.class);
        intent.putExtra("EAR", ear);
        intent.putExtra("USER_ID", userId);
        intent.putExtra("HEARING_PROFILE_ID", profileId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_tone_test);

        // ←— grab your imageViewEar here
        earImage    = findViewById(R.id.imageViewEar);
        currentEar  = getIntent().getStringExtra("EAR");
        if (currentEar == null) currentEar = "LEFT";

        // **NEW**: set the drawable based on which ear we're testing
        if (currentEar.equalsIgnoreCase("LEFT")) {
            earImage.setImageResource(R.drawable.left_ear);
        } else {
            earImage.setImageResource(R.drawable.right_ear);
        }

        userId      = getIntent().getIntExtra("USER_ID", 1);
        profileId   = getIntent().getIntExtra("HEARING_PROFILE_ID", 1);
        resultDao   = AppDatabase.getInstance(this).hearingTestResultDao();

        TextView title = findViewById(R.id.title);
        title.setText((currentEar.equalsIgnoreCase("LEFT") ? "Left" : "Right") + " Ear Test");

        progressBar = findViewById(R.id.progressBar);
        progressBar.setMax(100);

        // tvCountdown = findViewById(R.id.tvCountdown);  // Optional - may not exist in shared layout
        
        btnStart   = findViewById(R.id.buttonStart);
        btnHeard   = findViewById(R.id.buttonHeard);
        btnNotHeard= findViewById(R.id.buttonNotHeard);

        btnStart.setOnClickListener(v -> startCountdownAndTest());
        btnHeard.setOnClickListener(v -> { stopPlayback = true; recordResponse(true); });
        btnNotHeard.setOnClickListener(v -> { stopPlayback = true; recordResponse(false); });

        showStartUI();
    }

    private void startCountdownAndTest() {
        // Hide all controls during countdown
        showCountdownUI();
        
        new CountDownTimer(3000, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                if (tvCountdown != null) {
                    tvCountdown.setText(String.valueOf(secondsRemaining));
                }
            }

            public void onFinish() {
                hideCountdownUI();
                startToneRamp();
            }
        }.start();
    }

    private void startToneRamp() {
        showResponseUI();
        stopPlayback = false;
        progressBar.setProgress(0);
        lastAmplitudeStep = 0;
        final int freq = frequencies[currentFreqIndex];

        new Thread(() -> {
            AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT),
                AudioTrack.MODE_STREAM
            );
            track.play();
            for (int i = 1; i <= 100 && !stopPlayback; i++) {
                float amp = i / 100f;
                short[] chunk = ToneGenerator.generateSineWaveChunk(44100, 50, freq, amp);
                track.write(chunk, 0, chunk.length);
                lastAmplitudeStep = i;
                int prog = i;
                runOnUiThread(() -> progressBar.setProgress(prog));
            }
            track.stop(); track.release();
            if (!stopPlayback) runOnUiThread(this::showCompletionUI);
        }).start();
    }

private void recordResponse(boolean heard) {
    // 1) snapshot the current index so nothing shifts under us
    final int stepIndex = currentFreqIndex;

    // 2) persist the result off the UI thread
    new Thread(() -> {
        try {
            // Ensure HearingProfile exists before inserting HearingTestResult
            AppDatabase db = AppDatabase.getInstance(this);
            com.example.audion.data.HearingProfileDao profileDao = db.hearingProfileDao();
            com.example.audion.data.HearingProfile profile = profileDao.getHearingProfileById(profileId);
            if (profile == null) {
                // Create a default profile if it doesn't exist
                android.util.Log.d("FrequencySweep", "Creating default HearingProfile with ID: " + profileId);
                com.example.audion.data.HearingProfile newProfile = new com.example.audion.data.HearingProfile("Standard Mode", "default_icon");
                long newId = profileDao.insert(newProfile);
                profileId = (int) newId;
                android.util.Log.d("FrequencySweep", "Created HearingProfile with new ID: " + profileId);
            }
            
            resultDao.insert(new HearingTestResult(
                userId,
                currentEar,
                frequencies[stepIndex],
                heard ? lastAmplitudeStep : 100,
                profileId
            ));
        } catch (Exception e) {
            android.util.Log.e("FrequencySweep", "Error inserting hearing test result", e);
        }
    }).start();

    // 3) now advance
    currentFreqIndex++;

    // 4) if there are more tones, reset for the next one
    if (currentFreqIndex < frequencies.length) {
        runOnUiThread(() -> {
            progressBar.setProgress(0);
            showStartUI();
        });
    }
    // 5b) otherwise you're done—move on
    else {
        runOnUiThread(this::completeSweep);
    }
}

    private void advanceToNext() {
        currentFreqIndex++;
        if (currentFreqIndex >= frequencies.length) {
            runOnUiThread(this::completeSweep);
        } else {
            runOnUiThread(() -> {
                progressBar.setProgress(0);
                showStartUI();
            });
        }
    }

    private void completeSweep() {
        if ("RIGHT".equalsIgnoreCase(currentEar)) {
            startActivity(FrequencySweepActivity.intentFor(
                this, "LEFT", userId, profileId));
        } else {
            // Both ears complete - navigate to test results
            Intent resultsIntent = new Intent(this, TestResultsActivity.class);
            resultsIntent.putExtra("USER_ID", userId);
            resultsIntent.putExtra("HEARING_PROFILE_ID", profileId);
            startActivity(resultsIntent);
        }
        finish();
    }

    private void showStartUI() {
        btnStart.setText("Start");
        btnStart.setVisibility(View.VISIBLE);
        btnHeard.setVisibility(View.GONE);
        btnNotHeard.setVisibility(View.GONE);
        if (tvCountdown != null) {
            tvCountdown.setVisibility(View.GONE);
        }
        progressBar.setVisibility(View.VISIBLE);
    }

    private void showCountdownUI() {
        // Hide everything except countdown
        btnStart.setVisibility(View.GONE);
        btnHeard.setVisibility(View.GONE);
        btnNotHeard.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        if (tvCountdown != null) {
            tvCountdown.setVisibility(View.VISIBLE);
            tvCountdown.setText("3");
        }
    }

    private void hideCountdownUI() {
        if (tvCountdown != null) {
            tvCountdown.setVisibility(View.GONE);
        }
        progressBar.setVisibility(View.VISIBLE);
    }

    private void showResponseUI() {
        btnStart.setVisibility(View.GONE);
        btnHeard.setVisibility(View.VISIBLE);
        btnNotHeard.setVisibility(View.VISIBLE);
    }

    private void showCompletionUI() {
        btnHeard.setVisibility(View.GONE);
        btnNotHeard.setVisibility(View.GONE);
        btnStart.setText("Repeat");
        btnStart.setVisibility(View.VISIBLE);
    }
}
