package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.View;  // Added import for View
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.audion.audio.ToneGenerator;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.example.audion.R;

public class PureToneTestActivity extends AppCompatActivity {

    // Updated 8-frequency array. (Remove duplicate 1000 Hz if not desired.)
    private final int[] frequencies = {1000, 2000, 3000, 4000, 8000, 1000, 500, 250};
    private int currentFreqIndex = 0;
    private String currentEar; // "LEFT" or "RIGHT"

    private TextView title;
    private ProgressBar progressBar;
    private Button buttonStart;
    private Button buttonHeard;
    private Button buttonNotHeard;

    private Thread playbackThread;
    private volatile boolean stopPlayback = false;
    private int lastAmplitudeStep = 0;

    private HearingTestResultDao hearingTestResultDao;
    private int userId;
    private int hearingProfileId; // from the intent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_tone_test);

        userId = getIntent().getIntExtra("USER_ID", -1);
        if (userId == -1) {
            Toast.makeText(this, "Error: No user ID passed!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Retrieve the hearing profile id that this test belongs to.
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        if (hearingProfileId == -1) {
            Toast.makeText(this, "Error: No hearing profile id passed!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        currentEar = getIntent().getStringExtra("EAR");
        if (currentEar == null) currentEar = "LEFT";

        title = findViewById(R.id.title);
        progressBar = findViewById(R.id.progressBar);
        buttonStart = findViewById(R.id.buttonStart);
        buttonHeard = findViewById(R.id.buttonHeard);
        buttonNotHeard = findViewById(R.id.buttonNotHeard);

        progressBar.setMax(100);
        progressBar.setProgress(0);

        // Use your AppDatabase singleton.
        AppDatabase db = AppDatabase.getInstance(this);
        hearingTestResultDao = db.hearingTestResultDao();

        updateFrequencyLabel();
        showStartUI();

        buttonStart.setOnClickListener(view -> startRampForCurrentFrequency());
        buttonHeard.setOnClickListener(view -> onUserHeard());
        buttonNotHeard.setOnClickListener(view -> onUserNotHeard());
    }

    private void startRampForCurrentFrequency() {
        final int freq = frequencies[currentFreqIndex];
        showResponseUI();
        stopPlayback = false;
        progressBar.setProgress(0);

        playbackThread = new Thread(() -> {
            AudioTrack audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    44100,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    44100 * 2,
                    AudioTrack.MODE_STREAM
            );

            audioTrack.play();

            for (int i = 1; i <= 100; i++) {
                if (stopPlayback) break;

                double amplitude = i / 100.0;
                short[] chunk = ToneGenerator.generateSineWaveChunk(44100, 50, freq, amplitude);
                audioTrack.write(chunk, 0, chunk.length);
                lastAmplitudeStep = i;

                final int progressVal = i;
                runOnUiThread(() -> progressBar.setProgress(progressVal));
            }

            audioTrack.stop();
            audioTrack.release();

            if (!stopPlayback) {
                runOnUiThread(() -> {
                    Toast.makeText(PureToneTestActivity.this,
                            "Max volume reached for " + freq + " Hz.\nYou can start again.",
                            Toast.LENGTH_SHORT).show();
                    showStartUI();
                });
            }
        });
        playbackThread.start();
    }

    private void onUserHeard() {
        stopPlayback = true;
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }

        markStepperCompleted(currentFreqIndex);

        final int freq = frequencies[currentFreqIndex];
        final int amplitudeStep = lastAmplitudeStep;

        new Thread(() -> {
            HearingTestResult result = new HearingTestResult(userId, currentEar, freq, amplitudeStep, hearingProfileId);
            hearingTestResultDao.insert(result);

            runOnUiThread(() -> {
                currentFreqIndex++;
                if (currentFreqIndex >= frequencies.length) {
                    if ("LEFT".equals(currentEar)) {
                        Toast.makeText(PureToneTestActivity.this, "Left Ear Test Complete!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(PureToneTestActivity.this, RightEarInstructionActivity.class);
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(PureToneTestActivity.this, "Right Ear Test Complete!", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(PureToneTestActivity.this, HomeActivity.class);
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        startActivity(intent);
                        finish();
                    }
                } else {
                    updateFrequencyLabel();
                    showStartUI();
                }
            });
        }).start();
    }

    private void onUserNotHeard() {
        stopPlayback = true;
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }

        final int freq = frequencies[currentFreqIndex];
        new Thread(() -> {
            HearingTestResult result = new HearingTestResult(userId, currentEar, freq, 100, hearingProfileId);
            hearingTestResultDao.insert(result);
            runOnUiThread(this::showStartUI);
        }).start();
    }

    private void showStartUI() {
        buttonStart.setVisibility(View.VISIBLE);
        buttonHeard.setVisibility(View.GONE);
        buttonNotHeard.setVisibility(View.GONE);
    }

    private void showResponseUI() {
        buttonStart.setVisibility(View.GONE);
        buttonHeard.setVisibility(View.VISIBLE);
        buttonNotHeard.setVisibility(View.VISIBLE);
    }

    private void updateFrequencyLabel() {
        if (currentFreqIndex < frequencies.length) {
            int freq = frequencies[currentFreqIndex];
            // For example, you might update the title to show:
            // title.setText("Testing " + freq + " Hz (" + currentEar + " Ear)");
        }
    }

    // Updated method for 8 steps (0 to 7).
    private void markStepperCompleted(int stepIndex) {
        String tick = "\u2713";
        int tickColor = ContextCompat.getColor(this, R.color.white);

        switch (stepIndex) {
            case 0:
                ((ImageView) findViewById(R.id.step1Image))
                        .setImageResource(R.drawable.circle_completed);
                TextView step1Text = findViewById(R.id.step1Text);
                step1Text.setText(tick);
                step1Text.setTextColor(tickColor);
                break;
            case 1:
                ((ImageView) findViewById(R.id.step2Image))
                        .setImageResource(R.drawable.circle_completed);
                TextView step2Text = findViewById(R.id.step2Text);
                step2Text.setText(tick);
                step2Text.setTextColor(tickColor);
                break;
            case 2:
                ((ImageView) findViewById(R.id.step3Image))
                        .setImageResource(R.drawable.circle_completed);
                TextView step3Text = findViewById(R.id.step3Text);
                step3Text.setText(tick);
                step3Text.setTextColor(tickColor);
                break;
            case 3:
                ((ImageView) findViewById(R.id.step4Image))
                        .setImageResource(R.drawable.circle_completed);
                TextView step4Text = findViewById(R.id.step4Text);
                step4Text.setText(tick);
                step4Text.setTextColor(tickColor);
                break;
            case 4:
                ((ImageView) findViewById(R.id.step5Image))
                        .setImageResource(R.drawable.circle_completed);
                TextView step5Text = findViewById(R.id.step5Text);
                step5Text.setText(tick);
                step5Text.setTextColor(tickColor);
                break;
            case 5:
                ((ImageView) findViewById(R.id.step6Image))
                        .setImageResource(R.drawable.circle_completed);
                TextView step6Text = findViewById(R.id.step6Text);
                step6Text.setText(tick);
                step6Text.setTextColor(tickColor);
                break;
            case 6:
                ((ImageView) findViewById(R.id.step7Image))
                        .setImageResource(R.drawable.circle_completed);
                TextView step7Text = findViewById(R.id.step7Text);
                step7Text.setText(tick);
                step7Text.setTextColor(tickColor);
                break;
            case 7:
                ((ImageView) findViewById(R.id.step8Image))
                        .setImageResource(R.drawable.circle_completed);
                TextView step8Text = findViewById(R.id.step8Text);
                step8Text.setText(tick);
                step8Text.setTextColor(tickColor);
                break;
        }
    }
}
