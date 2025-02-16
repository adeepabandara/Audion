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
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;

public class PureToneTestActivity extends AppCompatActivity {

    // Frequencies to test
    private final int[] frequencies = {250, 500, 1000, 2000};
    private int currentFreqIndex = 0;

    // Track which ear we're testing: "Right" first, then "Left"
    private String currentEar = "Right";

    // UI elements
    private TextView textViewFrequency;
    private ProgressBar progressBar;
    private Button buttonStart;
    private Button buttonHeard;
    private Button buttonNotHeard;

    // Audio / thread control
    private Thread playbackThread;
    private volatile boolean stopPlayback = false;

    // Keep track of the amplitude step (0..100) at which user hears the tone
    private int lastAmplitudeStep = 0;

    // Example: We'll assume there's a single user with ID=1 in your system
    // (Or you could retrieve the real user ID from your flow)
    private static final int DEMO_USER_ID = 1;

    // DAO reference (obtained from your already-initialized DB)
    private HearingTestResultDao hearingTestResultDao;

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

        // -- Get the already-initialized database and DAO --
        // Example: If MainActivity has a static getDatabase() method:
        AppDatabase db = MainActivity.getDatabase();
        hearingTestResultDao = db.hearingTestResultDao();

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

                    // Track which amplitude step we're on
                    lastAmplitudeStep = i;

                    // Because i must be effectively final for the lambda:
                    final int progressVal = i;
                    // Update progress bar in the UI thread
                    runOnUiThread(() -> progressBar.setProgress(progressVal));
                }

                audioTrack.stop();
                audioTrack.release();

                // If we finished all 100 steps and user hasn't clicked "I Heard"
                if (!stopPlayback) {
                    runOnUiThread(() -> {
                        Toast.makeText(
                                PureToneTestActivity.this,
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

    /**
     * Called when user clicks "I Heard".
     * We record the amplitude step at which they heard the tone.
     */
    private void onUserHeard() {
        stopPlayback = true;

        // Stop the thread if it's still alive
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }

        // Insert a DB record to indicate user heard it at 'lastAmplitudeStep'
        final int freq = frequencies[currentFreqIndex];
        HearingTestResult result = new HearingTestResult(
                DEMO_USER_ID,
                currentEar,       // e.g. "Right" or "Left"
                freq,
                lastAmplitudeStep // amplitude step at which user heard
        );
        hearingTestResultDao.insert(result);

        // Move to next frequency
        currentFreqIndex++;
        if (currentFreqIndex >= frequencies.length) {
            // If we finished all frequencies for the currentEar, check if we should switch
            if (currentEar.equals("Right")) {
                // Switch to left ear now
                currentEar = "Left";
                currentFreqIndex = 0;
                Toast.makeText(this,
                        "Now testing LEFT ear...",
                        Toast.LENGTH_SHORT
                ).show();
                updateFrequencyLabel();
                showStartUI();
            } else {
                // Already did "Left" ear, so we are fully done
                Toast.makeText(this, "All frequencies tested on BOTH ears!", Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            updateFrequencyLabel();
            showStartUI(); // Let user start the next frequency
        }
    }

    /**
     * Called when user clicks "I Didn't".
     * We can record amplitude=100 (or any sentinel) to indicate they didn't hear it by max volume.
     */
    private void onUserNotHeard() {
        stopPlayback = true;

        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }

        // Insert a DB record indicating user did NOT hear the tone at normal range
        final int freq = frequencies[currentFreqIndex];
        HearingTestResult result = new HearingTestResult(
                DEMO_USER_ID,
                currentEar,
                freq,
                100 // Indicate "didn't hear" up to step=100
        );
        hearingTestResultDao.insert(result);

        // Show Start so user can retest the same freq, if desired
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
            textViewFrequency.setText("Testing Frequency: " + freq + " Hz (" + currentEar + " Ear)");
        } else {
            textViewFrequency.setText("All done for " + currentEar + " ear!");
        }
    }
}
