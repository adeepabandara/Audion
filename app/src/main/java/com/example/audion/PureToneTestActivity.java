package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.audion.audio.ToneGenerator;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.example.audion.R;
import com.google.android.material.button.MaterialButton;

public class PureToneTestActivity extends AppCompatActivity {

    private final int[] frequencies = {1000, 2000, 3000, 4000, 8000, 1000, 500, 250};
    private int currentFreqIndex = 0;
    private String currentEar;  // <-- this field must be set, not shadowed

    private ProgressBar progressBar;
    private MaterialButton buttonStart, buttonHeard, buttonNotHeard;

    private Thread playbackThread;
    private volatile boolean stopPlayback = false;
    private int lastAmplitudeStep = 0;

    private HearingTestResultDao hearingTestResultDao;
    private int userId, hearingProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_tone_test);

        // 1) First initialize currentEar from the Intent
        currentEar = getIntent().getStringExtra("EAR");
        if (currentEar == null) currentEar = "LEFT";

        // 2) Now update the title text safely
        TextView title = findViewById(R.id.title);
        title.setText((currentEar.equalsIgnoreCase("LEFT") ? "Left" : "Right") + " Ear Test");

        // 3) Immediately set the ear image
        ImageView earImage = findViewById(R.id.imageViewEar);
        if ("left".equalsIgnoreCase(currentEar.trim())) {
            earImage.setImageResource(R.drawable.left_ear);
        } else {
            earImage.setImageResource(R.drawable.right_ear);
        }

        // 4) Grab the rest of your Intent extras
        userId           = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        if (userId < 0 || hearingProfileId < 0) {
            Toast.makeText(this, "Missing USER_ID or HEARING_PROFILE_ID", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 5) Wire up your UI
        progressBar    = findViewById(R.id.progressBar);
        buttonStart    = findViewById(R.id.buttonStart);
        buttonHeard    = findViewById(R.id.buttonHeard);
        buttonNotHeard = findViewById(R.id.buttonNotHeard);

        progressBar.setMax(100);
        progressBar.setProgress(0);

        hearingTestResultDao = AppDatabase.getInstance(this).hearingTestResultDao();

        buttonStart.setOnClickListener(v -> startRampForCurrentFrequency());
        buttonHeard.setOnClickListener(v -> {
            stopPlayback = true;
            recordHeardAndAdvance();
        });
        buttonNotHeard.setOnClickListener(v -> {
            stopPlayback = true;
            recordNotHeardAndAdvance();
        });

        showStartUI();
    }

    private void startRampForCurrentFrequency() {
        showResponseUI();
        stopPlayback = false;
        progressBar.setProgress(0);

        final int freq = frequencies[currentFreqIndex];
        playbackThread = new Thread(() -> {
            AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                44100 * 2,
                AudioTrack.MODE_STREAM
            );
            track.play();

            for (int i = 1; i <= 100 && !stopPlayback; i++) {
                double amp = i / 100.0;
                short[] chunk = ToneGenerator.generateSineWaveChunk(44100, 50, freq, amp);
                track.write(chunk, 0, chunk.length);
                lastAmplitudeStep = i;
                final int progress = i;
                runOnUiThread(() -> progressBar.setProgress(progress));
            }

            track.stop();
            track.release();

            if (!stopPlayback) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Reached max for " + freq + " Hz", Toast.LENGTH_SHORT).show();
                    showCompletionUI();
                });
            }
        });
        playbackThread.start();
    }

    private void recordHeardAndAdvance() {
        markStepperCompleted(currentFreqIndex);
        new Thread(() -> {
            int freq = frequencies[currentFreqIndex];
            hearingTestResultDao.insert(new HearingTestResult(
                userId, currentEar, freq, lastAmplitudeStep, hearingProfileId
            ));
            runOnUiThread(this::advanceToNextFrequency);
        }).start();
    }

    private void recordNotHeardAndAdvance() {
        markStepperCompleted(currentFreqIndex);
        new Thread(() -> {
            int freq = frequencies[currentFreqIndex];
            hearingTestResultDao.insert(new HearingTestResult(
                userId, currentEar, freq, 100, hearingProfileId
            ));
            runOnUiThread(this::advanceToNextFrequency);
        }).start();
    }

    private void advanceToNextFrequency() {
        currentFreqIndex++;
        if (currentFreqIndex >= frequencies.length) {
            Toast.makeText(this,
                (currentEar.equalsIgnoreCase("LEFT") ? "Left Ear" : "Right Ear") + " Test Complete!",
                Toast.LENGTH_SHORT
            ).show();
            Intent next = new Intent(this,
                currentEar.equalsIgnoreCase("LEFT")
                  ? RightEarInstructionActivity.class
                  : HomeActivity.class
            );
            next.putExtra("USER_ID", userId);
            next.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            startActivity(next);
            finish();
            return;
        }
        progressBar.setProgress(0);
        showStartUI();
    }

    private void showStartUI() {
        buttonStart   .setText("Start");
        buttonStart   .setVisibility(View.VISIBLE);
        buttonHeard   .setVisibility(View.GONE);
        buttonNotHeard.setVisibility(View.GONE);
    }

    private void showResponseUI() {
        buttonStart   .setVisibility(View.GONE);
        buttonHeard   .setVisibility(View.VISIBLE);
        buttonNotHeard.setVisibility(View.GONE);
    }

    private void showCompletionUI() {
        buttonHeard   .setVisibility(View.GONE);
        buttonNotHeard.setVisibility(View.VISIBLE);
        buttonStart   .setText("Start again");
        buttonStart   .setVisibility(View.VISIBLE);
    }

    private void markStepperCompleted(int index) {
        int imgId, txtId;
        switch (index) {
            case 0: imgId = R.id.step1Image; txtId = R.id.step1Text; break;
            case 1: imgId = R.id.step2Image; txtId = R.id.step2Text; break;
            case 2: imgId = R.id.step3Image; txtId = R.id.step3Text; break;
            case 3: imgId = R.id.step4Image; txtId = R.id.step4Text; break;
            case 4: imgId = R.id.step5Image; txtId = R.id.step5Text; break;
            case 5: imgId = R.id.step6Image; txtId = R.id.step6Text; break;
            case 6: imgId = R.id.step7Image; txtId = R.id.step7Text; break;
            case 7: imgId = R.id.step8Image; txtId = R.id.step8Text; break;
            default: return;
        }

        ImageView iv = findViewById(imgId);
        iv.setImageResource(R.drawable.circle_completed);

        TextView tv = findViewById(txtId);
        tv.setText("\u2713");
        tv.setTextColor(ContextCompat.getColor(this, R.color.white));
    }
}
