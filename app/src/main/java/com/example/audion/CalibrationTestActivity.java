package com.example.audion;

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

import com.google.android.material.button.MaterialButton;

public class CalibrationTestActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private TextView stepLabel;
    private ImageView earImage;
    private MaterialButton btnHeard, btnNotHeard;

    // exactly three calibration frequencies (Hz) and a 2 s tone each
    private final int[] freqs = {500, 1000, 2000};
    private static final int DURATION_MS = 2000;
    private int currentStep = 0;
    private String earSide;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_test);

        earImage    = findViewById(R.id.imageEar);
        stepLabel   = findViewById(R.id.textStepLabel);
        progressBar = findViewById(R.id.progressBar);
        btnHeard    = findViewById(R.id.buttonHeard);
        btnNotHeard = findViewById(R.id.buttonNotHeard);

        // set the ear icon
        earSide = getIntent().getStringExtra("EAR");
        earImage.setImageResource(
          "LEFT".equals(earSide)
            ? R.drawable.left_ear
            : R.drawable.right_ear
        );

        btnHeard.setVisibility(View.GONE);
        btnNotHeard.setVisibility(View.GONE);
        btnHeard   .setOnClickListener(v -> onUserResponse(true));
        btnNotHeard.setOnClickListener(v -> onUserResponse(false));

        startStep();
    }

    private void startStep() {
        // if we've done all 3 steps...
        if (currentStep >= freqs.length) {
            // if that was RIGHT, immediately launch LEFT
            if ("RIGHT".equals(earSide)) {
                Intent i = new Intent(this, CalibrationTestActivity.class);
                i.putExtra("EAR", "LEFT");
                startActivity(i);
            }
            finish();
            return;
        }

        // reset UI
        stepLabel  .setText("Step " + (currentStep+1) + " of " + freqs.length);
        progressBar.setProgress(0);
        btnHeard   .setVisibility(View.GONE);
        btnNotHeard.setVisibility(View.GONE);

        // play the tone + update progress
        new Thread(() -> {
            int sampleRate = 44_100;
            int numSamples = sampleRate * DURATION_MS / 1000;
            double[]   sample   = new double[numSamples];
            short[]    pcm      = new short[numSamples];
            int        freq     = freqs[currentStep];

            // generate sine wave
            for (int i = 0; i < numSamples; i++) {
                sample[i] = Math.sin(2 * Math.PI * i * freq / sampleRate);
                pcm[i]    = (short)(sample[i] * Short.MAX_VALUE);
            }

            // configure AudioTrack
            AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                pcm.length * 2,
                AudioTrack.MODE_STATIC
            );
            track.write(pcm, 0, pcm.length);
            track.play();

            // advance the progress bar in lock-step with the tone
            for (int p = 0; p <= 100; p++) {
                final int prog = p;
                runOnUiThread(() -> progressBar.setProgress(prog));
                try { Thread.sleep(DURATION_MS/100); }
                catch (InterruptedException x) { Thread.currentThread().interrupt(); }
            }

            track.stop();
            track.release();

            // show the response buttons
            runOnUiThread(() -> {
                btnHeard   .setVisibility(View.VISIBLE);
                btnNotHeard.setVisibility(View.VISIBLE);
            });
        }).start();
    }

    private void onUserResponse(boolean heard) {
        // TODO: persist (earSide, freqs[currentStep], heard)
        currentStep++;
        startStep();
    }
}
