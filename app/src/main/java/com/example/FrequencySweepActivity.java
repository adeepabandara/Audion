package com.example.audion;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
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
import com.example.audion.R;
import com.google.android.material.button.MaterialButton;
import android.view.Window;
import android.view.WindowManager;

public class FrequencySweepActivity extends AppCompatActivity {
    private final int[] frequencies = {1000, 2000, 3000, 4000, 8000, 1000, 500, 250};
    private int currentFreqIndex = 0;
    private String currentEar;

    private ProgressBar progressBar;
    private MaterialButton btnStart, btnHeard, btnNotHeard;
    private ImageView[] stepImages;
    private TextView[] stepLabels;
    private ImageView earImage;

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

        stepImages = new ImageView[]{
            findViewById(R.id.step1Image), findViewById(R.id.step2Image),
            findViewById(R.id.step3Image), findViewById(R.id.step4Image),
            findViewById(R.id.step5Image), findViewById(R.id.step6Image),
            findViewById(R.id.step7Image), findViewById(R.id.step8Image)
        };
        stepLabels = new TextView[]{
            findViewById(R.id.step1Text), findViewById(R.id.step2Text),
            findViewById(R.id.step3Text), findViewById(R.id.step4Text),
            findViewById(R.id.step5Text), findViewById(R.id.step6Text),
            findViewById(R.id.step7Text), findViewById(R.id.step8Text)
        };

        progressBar = findViewById(R.id.progressBar);
        progressBar.setMax(100);

        btnStart   = findViewById(R.id.buttonStart);
        btnHeard   = findViewById(R.id.buttonHeard);
        btnNotHeard= findViewById(R.id.buttonNotHeard);

        btnStart.setOnClickListener(v -> startToneRamp());
        btnHeard.setOnClickListener(v -> { stopPlayback = true; recordResponse(true); });
        btnNotHeard.setOnClickListener(v -> { stopPlayback = true; recordResponse(false); });

        showStartUI();
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
        resultDao.insert(new HearingTestResult(
            userId,
            currentEar,
            frequencies[stepIndex],
            heard ? lastAmplitudeStep : 100,
            profileId
        ));
    }).start();

    // 3) mark the UI immediately (still on the UI thread)
    if (stepIndex >= 0 && stepIndex < stepImages.length) {
        runOnUiThread(() -> markStepperCompleted(stepIndex));
    }

    // 4) now advance
    currentFreqIndex++;

    // 5a) if there are more tones, reset for the next one
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
            startActivity(new Intent(this, HomeActivity.class));
        }
        finish();
    }

    private void showStartUI() {
        btnStart.setText("Start");
        btnStart.setVisibility(View.VISIBLE);
        btnHeard.setVisibility(View.GONE);
        btnNotHeard.setVisibility(View.GONE);
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

    private void markStepperCompleted(int idx) {
        if (idx < 0 || idx >= stepImages.length) return;
        stepImages[idx].setImageResource(R.drawable.circle_completed);
        stepLabels[idx].setText("✓");
        stepLabels[idx].setTextColor(
            ContextCompat.getColor(this, R.color.white));
    }
}
