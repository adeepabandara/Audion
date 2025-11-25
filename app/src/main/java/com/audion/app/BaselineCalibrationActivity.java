package com.audion.app;

import com.audion.app.R;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.audion.app.data.AppDatabase;
import com.audion.app.data.CalibrationDao;
import com.audion.app.data.CalibrationEntry;
import com.audion.app.data.HearingProfile;
import com.audion.app.data.HearingProfileDao;

public class BaselineCalibrationActivity extends AppCompatActivity {

    private SeekBar   volumeSlider;
    private Button    btnHear, btnNotHear, btnNext;
    private TextView  tvEarLabel, tvStaticInstruction, tvInstruction, tvCountdown, tvCountdownMessage;
    private com.airbnb.lottie.LottieAnimationView toneAnimation;
    private androidx.constraintlayout.widget.ConstraintLayout countdownOverlay;

    private int     calibrationValue;
    private boolean foundLower;
    private boolean foundUpper; 
    private boolean calibrationInProgress;
    private int     testCount;
    private String  currentEar;
    private int     userId;
    private int     profileId;

    private static final int SAMPLE_RATE = 44100;
    private static final int MIN_VOLUME = 0;
    private static final int MAX_VOLUME = 100;

    private int currentVolume = 50;
    private volatile boolean isPlaying = false;
    private AudioTrack track;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("BaselineCalibration", "=== onCreate STARTED ===");
        Log.d("BaselineCalibration", "Current task ID: " + getTaskId());
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_baseline_calibration);

        try {
            Intent intent = getIntent();
            currentEar = intent.getStringExtra("EAR");
            userId = intent.getIntExtra("USER_ID", -1);
            profileId = intent.getIntExtra("HEARING_PROFILE_ID", -1);
            
            Log.d("BaselineCalibration", "onCreate - EAR: " + currentEar + ", USER_ID: " + userId + ", PROFILE_ID: " + profileId);
            
            if (currentEar == null) {
                currentEar = "RIGHT";
                Log.d("BaselineCalibration", "EAR was null, defaulting to RIGHT");
            }

            // Validate required parameters
            if (userId == -1 || profileId == -1) {
                Log.e("BaselineCalibration", "Missing required parameters - USER_ID: " + userId + ", PROFILE_ID: " + profileId);
                // Try to get from previous activities or use defaults
                userId = (userId == -1) ? 1 : userId;
                profileId = (profileId == -1) ? 1 : profileId;
            }

            initializeViews();
            setupCalibration();
            
            Log.d("BaselineCalibration", "onCreate completed successfully for " + currentEar + " ear");
        } catch (Exception e) {
            Log.e("BaselineCalibration", "Error in onCreate", e);
            // If we can't initialize, go back to name activity
            finish();
        }
    }
    
    
    private void initializeViews() {
        volumeSlider        = findViewById(R.id.seekBarVolume);
        btnHear            = findViewById(R.id.buttonHear);
        btnNotHear         = findViewById(R.id.buttonNotHear);
        btnNext            = findViewById(R.id.buttonNextStep);
        tvEarLabel         = findViewById(R.id.tvEarLabel);
        tvStaticInstruction = findViewById(R.id.tvStaticInstruction);
        tvInstruction      = findViewById(R.id.textInstruction);
        tvCountdown        = findViewById(R.id.textCountdown);
        tvCountdownMessage = findViewById(R.id.textCountdownMessage);
        toneAnimation      = findViewById(R.id.toneAnimation);
        countdownOverlay   = findViewById(R.id.countdownOverlay);
    }
    
    private void setupCalibration() {
        tvEarLabel.setText(currentEar.toUpperCase() + " EAR");

        // Initialize calibration state
        foundLower = false;
        foundUpper = false;
        calibrationInProgress = false;
        testCount = 0;

        // Initially hide ALL controls - only countdown should be visible during countdown
        tvEarLabel.setVisibility(View.GONE);
        volumeSlider.setVisibility(View.GONE);
        btnHear.setVisibility(View.GONE);
        btnNotHear.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);
        tvStaticInstruction.setVisibility(View.GONE);
        tvInstruction.setVisibility(View.GONE);
        toneAnimation.setVisibility(View.GONE);  // Hide animation during countdown

        // Start countdown - only countdown should be visible
        startCountdown();

        // Set up button listeners  
        btnHear.setOnClickListener(v -> {
            Log.d("BaselineCalibration", "btnHear clicked - " + currentEar + " ear, testCount: " + testCount);
            stopTone();
            testCount++;
            
            // User heard the tone - try lower volume for better precision
            if (currentVolume > MIN_VOLUME + 10 && testCount < 4) {
                currentVolume -= 10; // Decrease volume to find threshold
                calibrationInProgress = true;
                // Play tone at new volume after a brief pause
                v.postDelayed(() -> playTone(), 500);
            } else {
                // We've found a good threshold or reached minimum tests
                foundLower = true;
                checkCalibrationComplete();
            }
        });

        btnNotHear.setOnClickListener(v -> {
            Log.d("BaselineCalibration", "btnNotHear clicked - " + currentEar + " ear, testCount: " + testCount);
            stopTone();
            testCount++;
            
            // User didn't hear the tone - try higher volume
            if (currentVolume < MAX_VOLUME - 10 && testCount < 4) {
                currentVolume += 10; // Increase volume to find threshold
                calibrationInProgress = true; 
                // Play tone at new volume after a brief pause
                v.postDelayed(() -> playTone(), 500);
            } else {
                // We've found a good threshold or reached minimum tests
                foundUpper = true;
                checkCalibrationComplete();
            }
        });

        btnNext.setOnClickListener(v -> finishBaseline());
    }

    private void startCountdown() {
        // Show full-screen countdown overlay
        countdownOverlay.setVisibility(View.VISIBLE);
        tvCountdown.setText("3");
        tvCountdownMessage.setText("Get ready for " + currentEar.toLowerCase() + " ear...");

        new CountDownTimer(3000, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                tvCountdown.setText(String.valueOf(secondsRemaining));
            }

            public void onFinish() {
                // Hide countdown overlay
                countdownOverlay.setVisibility(View.GONE);
                
                // Show main calibration interface immediately
                tvEarLabel.setVisibility(View.VISIBLE);
                tvStaticInstruction.setVisibility(View.VISIBLE);
                tvInstruction.setVisibility(View.VISIBLE);
                toneAnimation.setVisibility(View.VISIBLE);
                btnHear.setVisibility(View.VISIBLE);
                btnNotHear.setVisibility(View.VISIBLE);
                
                // Start the calibration test immediately
                calibrationInProgress = true;
                playTone();
            }
        }.start();
    }
    
    private void startAutoCompleteTimer() {
        // Auto-complete calibration after 30 seconds to prevent infinite loops
        new android.os.Handler().postDelayed(() -> {
            if (calibrationInProgress && btnNext.getVisibility() != View.VISIBLE) {
                Log.d("BaselineCalibration", "Auto-complete timer triggered - forcing completion");
                // Force completion if calibration is still running
                testCount = 5; // Set high test count to trigger completion
                checkCalibrationComplete();
            }
        }, 30000); // 30 seconds timeout
    }

    private void checkCalibrationComplete() {
        // Simple calibration completion logic:
        // Complete after 3-5 tests OR when we've found a reasonable range
        if (testCount >= 3 && (foundLower || foundUpper)) {
            calibrationComplete();
        } else if (testCount >= 5) {
            // Force completion after 5 tests regardless
            calibrationComplete();
        }
    }

    private void calibrationComplete() {
        stopTone();
        // Hide the thumbs up/down buttons immediately
        btnHear.setVisibility(View.GONE);
        btnNotHear.setVisibility(View.GONE);
        
        Log.d("Calibration", "Calibration complete for " + currentEar + " ear at volume: " + currentVolume);
        
        // Instead of showing Next button, go directly to results page
        finishBaseline();
    }

    private void finishBaseline() {
        Log.d("BaselineCalibration", "finishBaseline called for " + currentEar + " ear");
        stopTone();
        
        // Store calibration result in database
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                
                // Ensure HearingProfile exists
                HearingProfileDao profileDao = db.hearingProfileDao();
                HearingProfile profile = profileDao.getHearingProfileById(profileId);
                if (profile == null) {
                    // Create a default profile if it doesn't exist
                    Log.d("BaselineCalibration", "Creating default HearingProfile with ID: " + profileId);
                    HearingProfile newProfile = new HearingProfile("Standard Mode", "default_icon");
                    long newId = profileDao.insert(newProfile);
                    profileId = (int) newId;
                    Log.d("BaselineCalibration", "Created HearingProfile with new ID: " + profileId);
                }
                
                CalibrationDao calibrationDao = db.calibrationDao();
                CalibrationEntry calibration = new CalibrationEntry(userId, currentEar, currentVolume, profileId);
                calibrationDao.insert(calibration);
                
                Log.d("BaselineCalibration", "Calibration data saved successfully");
                
                runOnUiThread(() -> {
                    try {
                        Log.d("BaselineCalibration", "=== runOnUiThread callback executing ===");
                        Log.d("BaselineCalibration", "Calibration completed, proceeding to next step");
                        
                        // Check if we need to do the other ear or proceed to pure tone test
                        if ("RIGHT".equals(currentEar)) {
                            // Right ear done, proceed to left ear calibration
                            Log.d("BaselineCalibration", "Right ear completed, starting left ear calibration");
                            Intent leftEarIntent = new Intent(this, BaselineCalibrationActivity.class);
                            leftEarIntent.putExtra("EAR", "LEFT");
                            leftEarIntent.putExtra("USER_ID", userId);
                            leftEarIntent.putExtra("HEARING_PROFILE_ID", profileId);
                            Log.d("BaselineCalibration", "About to start left ear activity");
                            startActivity(leftEarIntent);
                            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                            Log.d("BaselineCalibration", "Left ear activity started, finishing current activity");
                            finish();
                        } else {
                            // Both ears done, proceed to pure tone test instruction
                            Log.d("BaselineCalibration", "Both ears completed, proceeding to pure tone test");
                            Intent instructionIntent = new Intent(this, RightEarInstructionActivity.class);
                            instructionIntent.putExtra("USER_ID", userId);
                            instructionIntent.putExtra("HEARING_PROFILE_ID", profileId);
                            Log.d("BaselineCalibration", "About to start right ear instruction activity");
                            startActivity(instructionIntent);
                            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                            Log.d("BaselineCalibration", "Right ear instruction activity started, finishing current activity");
                            finish();
                        }
                        
                    } catch (Exception e) {
                        Log.e("BaselineCalibration", "Failed to navigate to next step", e);
                        // Safety fallback - if navigation fails, just finish
                        finish();
                    }
                });
            } catch (Exception e) {
                Log.e("BaselineCalibration", "Failed to save calibration data", e);
                runOnUiThread(() -> finish());
            }
        }).start();
    }

    private void playTone() {
        if (isPlaying) return;

        int bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );

        track = new AudioTrack(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
            track.play();
            isPlaying = true;

            // Generate and play 1kHz tone
            new Thread(() -> {
                short[] buffer = new short[bufferSize / 2];
                double phase = 0;
                double increment = (2 * Math.PI * 1000) / SAMPLE_RATE; // 1kHz tone

                while (isPlaying && 
                       track != null && 
                       track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    
                    for (int i = 0; i < buffer.length; i++) {
                        buffer[i] = (short) (Math.sin(phase) * Short.MAX_VALUE * currentVolume / 100);
                        phase += increment;
                        if (phase >= 2 * Math.PI) {
                            phase -= 2 * Math.PI;
                        }
                    }
                    
                    // Add synchronized check to prevent writing to released AudioTrack
                    synchronized (this) {
                        if (track != null && isPlaying && track.getState() == AudioTrack.STATE_INITIALIZED) {
                            try {
                                track.write(buffer, 0, buffer.length);
                            } catch (IllegalStateException e) {
                                // AudioTrack was released while we were writing - exit gracefully
                                Log.w("BaselineCalibration", "AudioTrack write failed - track was released", e);
                                break;
                            }
                        } else {
                            // Track is null or not playing - exit the loop
                            break;
                        }
                    }
                }
            }).start();
        }
    }

    private void stopTone() {
        synchronized (this) {
            isPlaying = false;
            if (track != null) {
                try {
                    if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                        track.stop();
                    }
                } catch (IllegalStateException e) {
                    Log.w("BaselineCalibration", "Failed to stop AudioTrack - already released", e);
                } finally {
                    try {
                        track.release();
                    } catch (Exception e) {
                        Log.w("BaselineCalibration", "Failed to release AudioTrack", e);
                    }
                    track = null;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTone();
    }
}
