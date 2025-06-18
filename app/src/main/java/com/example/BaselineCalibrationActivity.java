package com.example.audion;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Window;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.CalibrationDao;
import com.example.audion.data.CalibrationEntry;
import com.example.audion.R;

public class BaselineCalibrationActivity extends AppCompatActivity {

    private SeekBar   volumeSlider;
    private Button    btnHear, btnNotHear, btnNext;
    private TextView  tvEarLabel, tvStaticInstruction, tvInstruction, tvCountdown;
    private LottieAnimationView toneAnimation;

    private int     calibrationValue;
    private boolean foundLower;
    private int     userId = 1;
    private int     profileId;
    private String  currentEar;

    private CalibrationDao calibrationDao;
    private AudioTrack     track;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baseline_calibration);

        /* 1) Pull extras -------------------------------------------------- */
        currentEar = getIntent().getStringExtra("EAR");
        if (currentEar == null) currentEar = "RIGHT";
        profileId = getIntent().getIntExtra("HEARING_PROFILE_ID", 1);

        calibrationDao = AppDatabase.getInstance(this).calibrationDao();

        /* 2) Bind views --------------------------------------------------- */
        tvEarLabel          = findViewById(R.id.tvEarLabel);
        tvStaticInstruction = findViewById(R.id.tvStaticInstruction);
        tvInstruction       = findViewById(R.id.textInstruction);
        tvCountdown         = findViewById(R.id.textCountdown);
        volumeSlider        = findViewById(R.id.seekBarVolume);
        btnHear             = findViewById(R.id.buttonHear);
        btnNotHear          = findViewById(R.id.buttonNotHear);
        btnNext             = findViewById(R.id.buttonNextStep);
        toneAnimation       = findViewById(R.id.toneAnimation);
        toneAnimation.setVisibility(View.GONE);

        /* 3) Dynamic ear label ------------------------------------------- */
        tvEarLabel.setText(currentEar.equalsIgnoreCase("LEFT")
                           ? "Left Ear "
                           : "Right Ear ");

        /* 4) Initial UI state -------------------------------------------- */
        btnNext.setEnabled(false);
        volumeSlider.setVisibility(View.GONE);
        btnHear.setVisibility(View.GONE);
        btnNotHear.setVisibility(View.GONE);

        calibrationValue = 50;
        foundLower       = false;
        volumeSlider.setProgress(calibrationValue);

        /* 5) Button listeners -------------------------------------------- */
        btnHear.setOnClickListener(v -> onHear(true));
        btnNotHear.setOnClickListener(v -> onHear(false));
        btnNext.setOnClickListener(v -> finishBaseline());

        /* 6) 3-2-1 countdown --------------------------------------------- */
        tvInstruction.setText("You will hear the tone in");
        tvCountdown.setVisibility(View.VISIBLE);

        new CountDownTimer(3000, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000) + 1;
                tvCountdown.setText(String.valueOf(sec));
            }
            @Override public void onFinish() {
                tvCountdown.setVisibility(View.GONE);
                toneAnimation.setVisibility(View.VISIBLE);
                toneAnimation.playAnimation();

                tvInstruction.setText("Volume: " + calibrationValue + "%");
                volumeSlider.setVisibility(View.VISIBLE);
                btnHear.setVisibility(View.VISIBLE);
                btnNotHear.setVisibility(View.VISIBLE);

                playTone(1000, calibrationValue / 100f);
            }
        }.start();
    }

    /* ------------------------ Calibration logic ------------------------ */

    private void onHear(boolean heard) {
        stopTone();

        if (!foundLower && calibrationValue == 0 && heard) {
            saveBaselineAndLock(0);
            return;
        }
        if (foundLower && calibrationValue >= 100 && !heard) {
            saveBaselineAndLock(100);
            return;
        }

        if (!foundLower) {
            calibrationValue = Math.max(0, calibrationValue - 5);
            if (!heard) {
                foundLower = true;
                tvInstruction.setText("Too low, increasing...");
            }
        } else {
            calibrationValue = Math.min(100, calibrationValue + 2);
            if (heard) {
                saveBaselineAndLock(calibrationValue);
                return;
            }
        }

        volumeSlider.setProgress(calibrationValue);
        tvInstruction.setText("Volume: " + calibrationValue + "%");
        playTone(1000, calibrationValue / 100f);
    }

    private void saveBaselineAndLock(int value) {
        new Thread(() ->
            calibrationDao.insert(new CalibrationEntry(
                userId, currentEar, value, profileId
            ))
        ).start();

        stopTone();
        toneAnimation.pauseAnimation();
        toneAnimation.setVisibility(View.GONE);
        tvStaticInstruction.setVisibility(View.GONE);   // ← hide mint card

        volumeSlider.setVisibility(View.GONE);
        btnHear.setVisibility(View.GONE);
        btnNotHear.setVisibility(View.GONE);
        btnNext.setVisibility(View.VISIBLE);
        btnNext.setEnabled(true);

        tvInstruction.setText(
            "Results for " + currentEar + " Ear: " + value + "%\n\nTap Next to Continue"
        );
    }

    private void finishBaseline() {
        stopTone();
        if ("RIGHT".equalsIgnoreCase(currentEar)) {
            Intent i = new Intent(this, BaselineCalibrationActivity.class);
            i.putExtra("EAR", "LEFT");
            i.putExtra("HEARING_PROFILE_ID", profileId);
            startActivity(i);
        } else {
            startActivity(FrequencySweepActivity.intentFor(
                this, "RIGHT", userId, profileId
            ));
        }
        finish();
    }

    /* -------------------- Tone-generation helpers ---------------------- */

    private void playTone(int freqHz, float amp) {
        int sr      = 44100;
        int bufSize = AudioTrack.getMinBufferSize(
            sr, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );
        short[] buf = new short[bufSize];
        for (int i = 0; i < bufSize; i++) {
            buf[i] = (short) (Math.sin(2 * Math.PI * i * freqHz / sr)
                              * amp * Short.MAX_VALUE);
        }
        track = new AudioTrack(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            new AudioFormat.Builder()
                .setSampleRate(sr)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        );
        track.play();
        new Thread(() -> {
            while (track != null &&
                   track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.write(buf, 0, buf.length);
            }
        }).start();
    }

    private void stopTone() {
        if (track != null) {
            track.stop();
            track.release();
            track = null;
        }
    }
}
