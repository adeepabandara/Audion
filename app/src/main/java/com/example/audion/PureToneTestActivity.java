package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.audion.audio.ToneGenerator;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;

public class PureToneTestActivity extends AppCompatActivity {

    private final int[] frequencies = {250, 500, 1000, 2000};
    private int currentFreqIndex = 0;

    private String currentEar; // "LEFT" or "RIGHT"

    private TextView textViewFrequency;
    private ProgressBar progressBar;
    private Button buttonStart;
    private Button buttonHeard;
    private Button buttonNotHeard;

    private Thread playbackThread;
    private volatile boolean stopPlayback = false;
    private int lastAmplitudeStep = 0;

    private HearingTestResultDao hearingTestResultDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_tone_test);

        currentEar = getIntent().getStringExtra("EAR");
        if (currentEar == null) currentEar = "LEFT"; // fallback

        textViewFrequency = findViewById(R.id.textViewFrequency);
        progressBar       = findViewById(R.id.progressBar);
        buttonStart       = findViewById(R.id.buttonStart);
        buttonHeard       = findViewById(R.id.buttonHeard);
        buttonNotHeard    = findViewById(R.id.buttonNotHeard);

        progressBar.setMax(100);
        progressBar.setProgress(0);

        AppDatabase db = MainActivity.getDatabase();
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
                            Toast.LENGTH_SHORT
                    ).show();
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

        int freq = frequencies[currentFreqIndex];
        HearingTestResult result = new HearingTestResult(
                1,        // or get current user ID
                currentEar,
                freq,
                lastAmplitudeStep
        );
        hearingTestResultDao.insert(result);

        currentFreqIndex++;
        if (currentFreqIndex >= frequencies.length) {
            // done with this ear
            if ("LEFT".equals(currentEar)) {
                // after left ear -> go RightEarInstruction
                Toast.makeText(this, "Left Ear Test Complete!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, RightEarInstructionActivity.class);
                startActivity(intent);
                finish();
            } else {
                // done right ear => entire test complete
                Toast.makeText(this, "Right Ear Test Complete!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, HomeActivity.class);
                startActivity(intent);

                finish();
            }
        } else {
            updateFrequencyLabel();
            showStartUI();
        }
    }

    private void onUserNotHeard() {
        stopPlayback = true;
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }

        int freq = frequencies[currentFreqIndex];
        HearingTestResult result = new HearingTestResult(
                1,
                currentEar,
                freq,
                100
        );
        hearingTestResultDao.insert(result);

        showStartUI();
    }

    private void showStartUI() {
        buttonStart.setVisibility(android.view.View.VISIBLE);
        buttonHeard.setVisibility(android.view.View.GONE);
        buttonNotHeard.setVisibility(android.view.View.GONE);
    }

    private void showResponseUI() {
        buttonStart.setVisibility(android.view.View.GONE);
        buttonHeard.setVisibility(android.view.View.VISIBLE);
        buttonNotHeard.setVisibility(android.view.View.VISIBLE);
    }

    private void updateFrequencyLabel() {
        if (currentFreqIndex < frequencies.length) {
            int freq = frequencies[currentFreqIndex];
            textViewFrequency.setText("Testing Frequency: " + freq + " Hz (" + currentEar + " Ear)");
        } else {
            textViewFrequency.setText("All done for " + currentEar + " ear!");
        }
    }
}
