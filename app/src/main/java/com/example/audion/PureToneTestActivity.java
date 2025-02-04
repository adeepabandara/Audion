package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.audion.audio.ToneGenerator;

public class PureToneTestActivity extends AppCompatActivity {

    // Frequencies to test
    private final int[] frequencies = {250, 500, 1000, 2000};
    private int currentFreqIndex = 0;

    // UI elements
    private TextView textViewFrequency;
    private ProgressBar progressBar;
    private Button buttonStart;
    private Button buttonHeard;
    private Button buttonNotHeard;

    // Audio / thread control
    private Thread playbackThread;
    private volatile boolean stopPlayback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_tone_test);

        textViewFrequency = findViewById(R.id.textViewFrequency);
        progressBar       = findViewById(R.id.progressBar);
        buttonStart       = findViewById(R.id.buttonStart);
        buttonHeard       = findViewById(R.id.buttonHeard);
        buttonNotHeard    = findViewById(R.id.buttonNotHeard);

        // progress bar from 0..100
        progressBar.setMax(100);
        progressBar.setProgress(0);

        updateFrequencyLabel();

        // Initially show Start, hide Heard/NotHeard
        showStartUI();

        // Start button
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRampForCurrentFrequency();
            }
        });

        // "I Heard"
        buttonHeard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onUserHeard();
            }
        });

        // "I Didn't"
        buttonNotHeard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onUserNotHeard();
            }
        });
    }

    /**
     * Called when user clicks Start button.
     * We do a background thread that ramps amplitude from 0..1 in 100 steps.
     */
    private void startRampForCurrentFrequency() {
        final int freq = frequencies[currentFreqIndex];
        showResponseUI(); // Hide Start, show Heard/NotHeard

        stopPlayback = false;
        progressBar.setProgress(0);

        playbackThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // Create an AudioTrack in streaming mode
                AudioTrack audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        44100,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        44100 * 2, // bufferSize in bytes, arbitrary for streaming
                        AudioTrack.MODE_STREAM
                );

                audioTrack.play();

                // We'll ramp from 0.0 to 1.0 in 100 steps
                final int steps = 100;
                for (int i = 1; i <= steps; i++) {
                    if (stopPlayback) {
                        break;
                    }
                    double amplitude = i / 100.0; // e.g. 0.01..1.0
                    // Generate a short 50ms chunk at this amplitude
                    short[] chunk = ToneGenerator.generateSineWaveChunk(
                            44100,
                            50,   // 50ms
                            freq,
                            amplitude
                    );
                    // Write to AudioTrack
                    audioTrack.write(chunk, 0, chunk.length);

                    // Update progress bar
                    final int progressVal = i;
                    runOnUiThread(() -> progressBar.setProgress(progressVal));
                }

                audioTrack.stop();
                audioTrack.release();

                // If we finished all 100 steps (amplitude=1.0) and haven't clicked "I Heard"
                // then the user didn't catch it before we hit max volume
                if (!stopPlayback) {
                    runOnUiThread(() -> {
                        Toast.makeText(PureToneTestActivity.this,
                                "Max volume reached for " + freq + " Hz.\nYou can start again.",
                                Toast.LENGTH_SHORT
                        ).show();

                        // Show Start again, so user can retest the same frequency
                        showStartUI();
                    });
                }
            }
        });
        playbackThread.start();
    }

    private void onUserHeard() {
        // User says "I Heard" -> stop playback, move to next frequency
        stopPlayback = true;

        // Wait for the thread to finish
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }
        // Next frequency
        currentFreqIndex++;
        if (currentFreqIndex >= frequencies.length) {
            Toast.makeText(this, "All frequencies tested!", Toast.LENGTH_LONG).show();
            finish();
        } else {
            updateFrequencyLabel();
            showStartUI(); // Let user start the next frequency
        }
    }

    private void onUserNotHeard() {
        // User says "I Didn't" -> stop playback, stay on same frequency
        stopPlayback = true;

        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }
        // Show Start so user can retest the same freq
        showStartUI();
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
            textViewFrequency.setText("Testing Frequency: " + freq + " Hz");
        } else {
            textViewFrequency.setText("All done!");
        }
    }
}
