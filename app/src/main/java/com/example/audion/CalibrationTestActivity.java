package com.example.audion;

import com.audion.psap.R;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.audion.audio.ClinicalAudioGenerator;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.CalibrationProfileDao;
import com.example.audion.data.CalibrationProfileEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clinical Calibration Test Activity
 * Implements MCL/UCL testing methodology following clinical audiometry standards
 * Uses three-button approach: Too Soft / Comfortable / Too Loud
 */
public class CalibrationTestActivity extends AppCompatActivity {
    private static final String TAG = "ClinicalCalibration";
    
    // UI Components
    private ProgressBar progressBar;
    private TextView stepLabel, frequencyDisplay, levelDisplay;
    private ImageView earImage;
    private Button btnTooSoft, btnComfortable, btnTooLoud;
    
    // Clinical calibration frequencies (Hz) - full audiometric range
    private final int[] frequencies = {250, 500, 1000, 2000, 4000, 8000};
    private int currentFreqIndex = 0;
    
    // Clinical parameters
    private String earSide;
    private int userId;
    private int hearingProfileId;
    private Map<Integer, Float> mclValues; // Most Comfortable Level per frequency
    private Map<Integer, Float> uclValues; // Uncomfortable Level per frequency
    private float currentLevel = 65.0f; // Starting level in dB SPL
    private boolean foundMCL = false;
    
    // Audio generation
    private ClinicalAudioGenerator audioGenerator;
    private volatile boolean isPlayingTone = false;
    private static final int TONE_DURATION_MS = 2000; // 2 second continuous tones
    
    // Threading
    private HandlerThread audioThread;
    private Handler audioHandler;
    private ExecutorService dbExecutor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_test_simplified);
        
        // Get intent parameters
        earSide = getIntent().getStringExtra("EAR");
        userId = getIntent().getIntExtra("USER_ID", 1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", 1);
        
        if (earSide == null) {
            Log.w(TAG, "EAR parameter is null! Defaulting to RIGHT");
            earSide = "RIGHT";
        }
        
        Log.d(TAG, "Starting clinical calibration for " + earSide + " ear");
        
        // Initialize data structures
        mclValues = new HashMap<>();
        uclValues = new HashMap<>();
        
        // Initialize audio components
        initializeAudio();
        
        // Initialize UI
        initializeUI();
        
        // Initialize database executor
        dbExecutor = Executors.newSingleThreadExecutor();
        
        // Start clinical calibration
        startClinicalCalibration();
    }
    
    private void initializeAudio() {
        audioGenerator = new ClinicalAudioGenerator();
        audioThread = new HandlerThread("ClinicalCalibrationAudio");
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());
    }
    
    private void initializeUI() {
        // Initialize UI elements
        progressBar = findViewById(R.id.progressBar);
        stepLabel = findViewById(R.id.textStepLabel);
        earImage = findViewById(R.id.imageEar);
        frequencyDisplay = findViewById(R.id.frequencyDisplay);
        levelDisplay = findViewById(R.id.levelDisplay);
        
        // Initialize clinical calibration buttons
        btnTooSoft = findViewById(R.id.btnTooSoft);
        btnComfortable = findViewById(R.id.btnComfortable);
        btnTooLoud = findViewById(R.id.btnTooLoud);
        
        // Set up clinical calibration button listeners
        btnTooSoft.setOnClickListener(v -> onClinicalResponse("TOO_SOFT"));
        btnComfortable.setOnClickListener(v -> onClinicalResponse("COMFORTABLE"));
        btnTooLoud.setOnClickListener(v -> onClinicalResponse("TOO_LOUD"));
        
        // Update button labels for clinical use
        btnTooSoft.setText("Too Soft");
        btnComfortable.setText("Comfortable");
        btnTooLoud.setText("Too Loud");
        
        // Set ear image - use calibration_test GIF for both ears
        earImage.setImageResource(R.drawable.calibration_test);
        
        // Initial button state
        setButtonsEnabled(false);
    }
    
    private void startClinicalCalibration() {
        currentFreqIndex = 0;
        currentLevel = 65.0f; // Clinical starting level
        foundMCL = false;
        
        Log.d("CalibrationFlow", "Starting calibration at " + currentLevel + " dB SPL for " + frequencies[currentFreqIndex] + " Hz");
        
        updateDisplay();
        
        // Add a small delay to ensure UI is ready, then play tone
        new android.os.Handler().postDelayed(() -> {
            playClinicalTone();
        }, 1000); // 1 second delay
    }
    
    private void updateDisplay() {
        if (currentFreqIndex >= frequencies.length) {
            completeClinicalCalibration();
            return;
        }
        
        final int freq = frequencies[currentFreqIndex];
        final int progress = (int) ((currentFreqIndex / (float) frequencies.length) * 100);
        
        runOnUiThread(() -> {
            stepLabel.setText("Calibrating " + earSide + " ear - Frequency " + (currentFreqIndex + 1) + " of " + frequencies.length);
            progressBar.setProgress(progress);
            
            // Update frequency progress bars
            updateFrequencyBar(currentFreqIndex, false);
            
            // Update frequency and level displays
            if (frequencyDisplay != null) {
                frequencyDisplay.setText(freq + " Hz");
                frequencyDisplay.setVisibility(View.VISIBLE);
            }
            if (levelDisplay != null) {
                levelDisplay.setText((int)currentLevel + " dB SPL");
                levelDisplay.setVisibility(View.VISIBLE);
            }
            
            // Update instruction text based on phase
            TextView textFrequency = findViewById(R.id.textFrequency);
            if (textFrequency != null) {
                if (foundMCL) {
                    textFrequency.setText("Finding uncomfortable level for " + freq + " Hz");
                } else {
                    textFrequency.setText("Finding comfortable level for " + freq + " Hz");
                }
            } else {
                Log.w("CalibrationFlow", "textFrequency view not found - skipping instruction update");
            }
        });
        
        Log.d("CalibrationFlow", "Display updated - Freq: " + freq + "Hz, Level: " + currentLevel + "dB, Progress: " + progress + "%");
    }
    
    private void playClinicalTone() {
        if (currentFreqIndex >= frequencies.length) {
            completeClinicalCalibration();
            return;
        }

        final int freq = frequencies[currentFreqIndex];
        
        // AUDIO FOCUS MANAGEMENT FOR CALIBRATION
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int focusResult = audioManager.requestAudioFocus(
            null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        );
        
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w("AudioDebug", "Audio focus not granted - may affect calibration accuracy");
        }
        
        // Set volume for calibration testing
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int calibrationVolume = (int) (maxVolume * 0.8); // 80% for calibration
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, calibrationVolume, 0);
        
        Log.d("CalibrationFlow", "Starting calibration for freq=" + freq + "Hz at level=" + currentLevel + "dB SPL");
        
        // Use new direct tone generation method
        setButtonsEnabled(false);
        playTone(freq, currentLevel);
    }
    
    private void onClinicalResponse(String response) {
        Log.d("CalibrationFlow", "=== BUTTON PRESS DETECTED ===");
        Log.d("CalibrationFlow", "Response: " + response + " at " + currentLevel + " dB SPL for " + frequencies[currentFreqIndex] + "Hz");
        Log.d("CalibrationFlow", "Current state - FreqIndex: " + currentFreqIndex + ", FoundMCL: " + foundMCL);
        
        if (currentFreqIndex >= frequencies.length) {
            completeClinicalCalibration();
            return;
        }
        
        final int freq = frequencies[currentFreqIndex];
        
        switch (response) {
            case "TOO_SOFT":
                if (foundMCL) {
                    // Finding UCL - use smaller steps (3 dB) for precision
                    Log.d("CalibrationFlow", "TOO_SOFT during UCL: Increasing level by 3 dB");
                    currentLevel += 3.0f;
                } else {
                    // Finding MCL - use standard 5 dB steps
                    Log.d("CalibrationFlow", "TOO_SOFT during MCL: Increasing level by 5 dB");
                    currentLevel += 5.0f;
                }
                
                if (currentLevel > 100.0f) {
                    currentLevel = 100.0f;
                    Log.w("CalibrationFlow", "Maximum level reached for " + freq + "Hz");
                    
                    if (!foundMCL) {
                        // Maximum reached without finding MCL - severe hearing loss
                        Log.w("CalibrationFlow", "Severe hearing loss suspected - setting MCL=UCL=100dB");
                        mclValues.put(freq, 100.0f);
                        uclValues.put(freq, 100.0f);
                        
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Maximum level reached for " + freq + "Hz - possible hearing loss", Toast.LENGTH_LONG).show();
                        });
                    } else {
                        // Maximum reached during UCL testing
                        Log.w("CalibrationFlow", "Maximum UCL reached for " + freq + "Hz");
                        uclValues.put(freq, 100.0f);
                        
                        runOnUiThread(() -> {
                            Toast.makeText(this, "High tolerance detected for " + freq + "Hz", Toast.LENGTH_SHORT).show();
                        });
                    }
                    
                    advanceToNextFrequency();
                } else {
                    Log.d("CalibrationFlow", "Playing louder tone at " + currentLevel + " dB");
                    updateDisplay();
                    playClinicalTone();
                }
                break;
                
            case "COMFORTABLE":
                Log.d("CalibrationFlow", "COMFORTABLE: MCL found for " + freq + "Hz at " + currentLevel + " dB SPL");
                mclValues.put(freq, currentLevel);
                foundMCL = true;
                
                // Validate MCL is in reasonable clinical range (55-75 dB SPL)
                if (currentLevel < 55.0f || currentLevel > 75.0f) {
                    final float mclLevel = currentLevel;
                    runOnUiThread(() -> {
                        String message = mclLevel < 55.0f ? 
                            "MCL unusually low (" + (int)mclLevel + " dB). Please verify." :
                            "MCL unusually high (" + (int)mclLevel + " dB). Please verify.";
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    });
                    Log.w("CalibrationValidation", "MCL outside normal range: " + currentLevel + " dB SPL for " + freq + "Hz");
                }
                
                // Progressive UCL testing - start 5 dB above MCL
                currentLevel += 5.0f;
                if (currentLevel > 100.0f) {
                    currentLevel = 100.0f;
                }
                
                Log.d("CalibrationFlow", "Starting progressive UCL testing at " + currentLevel + " dB (MCL + 5 dB)");
                updateDisplay();
                playClinicalTone();
                break;
                
            case "TOO_LOUD":
                if (foundMCL) {
                    // This is UCL - record and validate
                    Log.d("CalibrationFlow", "TOO_LOUD: UCL found for " + freq + "Hz at " + currentLevel + " dB SPL");
                    uclValues.put(freq, currentLevel);
                    
                    // Validate UCL is in reasonable clinical range (70-95 dB SPL)
                    if (currentLevel < 70.0f || currentLevel > 95.0f) {
                        final float uclLevel = currentLevel;
                        runOnUiThread(() -> {
                            String message = uclLevel < 70.0f ? 
                                "UCL unusually low (" + (int)uclLevel + " dB). Please verify." :
                                "UCL unusually high (" + (int)uclLevel + " dB). Please verify.";
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                        Log.w("CalibrationValidation", "UCL outside normal range: " + currentLevel + " dB SPL for " + freq + "Hz");
                    }
                    
                    // Validate dynamic range (UCL - MCL should be ≥ 15 dB)
                    float mcl = mclValues.get(freq);
                    float dynamicRange = currentLevel - mcl;
                    if (dynamicRange < 15.0f) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Small dynamic range (" + (int)dynamicRange + " dB) for " + freq + "Hz", Toast.LENGTH_LONG).show();
                        });
                        Log.w("CalibrationValidation", "Small dynamic range: " + dynamicRange + " dB for " + freq + "Hz");
                    }
                    
                    Log.i("CalibrationFlow", String.format("Frequency %dHz complete: MCL=%.1f dB, UCL=%.1f dB, Range=%.1f dB", 
                           freq, mcl, currentLevel, dynamicRange));
                    
                    advanceToNextFrequency();
                } else {
                    // Too loud without finding MCL - reduce level
                    Log.d("CalibrationFlow", "TOO_LOUD: Decreasing level by 5 dB (no MCL found yet)");
                    currentLevel -= 5.0f;
                    if (currentLevel < 30.0f) {
                        currentLevel = 30.0f;
                        Log.w("CalibrationFlow", "Minimum level reached for " + freq + "Hz, setting MCL=UCL=30dB");
                        mclValues.put(freq, 30.0f);
                        uclValues.put(freq, 30.0f);
                        
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Extreme sensitivity detected for " + freq + "Hz", Toast.LENGTH_LONG).show();
                        });
                        
                        advanceToNextFrequency();
                    } else {
                        Log.d("CalibrationFlow", "Playing quieter tone at " + currentLevel + " dB");
                        updateDisplay();
                        playClinicalTone();
                    }
                }
                break;
        }
    }
    
    private void advanceToNextFrequency() {
        // Mark previous frequency as completed
        if (currentFreqIndex >= 0 && currentFreqIndex < frequencies.length) {
            updateFrequencyBar(currentFreqIndex, true);
        }
        
        currentFreqIndex++;
        if (currentFreqIndex < frequencies.length) {
            // Reset for next frequency
            currentLevel = 65.0f;
            foundMCL = false;
            updateDisplay();
            playClinicalTone();
        } else {
            completeClinicalCalibration();
        }
    }
    
    /**
     * Updates the visual progress bar for a specific frequency
     * @param index The frequency index (0-5 for 6 frequencies)
     * @param completed Whether this frequency test is completed
     */
    private void updateFrequencyBar(int index, boolean completed) {
        if (index < 0 || index > 5) return;
        
        int barId = 0;
        switch(index) {
            case 0: barId = R.id.bar1; break;
            case 1: barId = R.id.bar2; break;
            case 2: barId = R.id.bar3; break;
            case 3: barId = R.id.bar4; break;
            case 4: barId = R.id.bar5; break;
            case 5: barId = R.id.bar6; break;
        }
        
        if (barId != 0) {
            View bar = findViewById(barId);
            if (bar != null) {
                bar.setBackgroundResource(completed ? R.drawable.indicator_active : R.drawable.indicator_inactive);
            }
        }
    }
    
    private void setButtonsEnabled(boolean enabled) {
        runOnUiThread(() -> {
            btnTooSoft.setEnabled(enabled);
            btnComfortable.setEnabled(enabled);
            btnTooLoud.setEnabled(enabled);
        });
    }
    
    private void completeClinicalCalibration() {
        // Stop any playing tone
        stopTone();
        
        // Save calibration data
        saveClinicalCalibrationData();
        
        // Navigate to next step
        navigateAfterCalibration();
    }
    
    private void saveClinicalCalibrationData() {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                CalibrationProfileDao dao = db.calibrationProfileDao();
                
                // Calculate averages
                float avgMCL = 0f;
                float avgUCL = 0f;
                int count = 0;
                
                for (int freq : frequencies) {
                    if (mclValues.containsKey(freq) && uclValues.containsKey(freq)) {
                        avgMCL += mclValues.get(freq);
                        avgUCL += uclValues.get(freq);
                        count++;
                    }
                }
                
                if (count > 0) {
                    avgMCL /= count;
                    avgUCL /= count;
                    
                    // Create JSON strings for per-frequency data
                    StringBuilder mclJson = new StringBuilder("{");
                    StringBuilder uclJson = new StringBuilder("{");
                    
                    for (int i = 0; i < frequencies.length; i++) {
                        int freq = frequencies[i];
                        if (mclValues.containsKey(freq) && uclValues.containsKey(freq)) {
                            if (i > 0) {
                                mclJson.append(",");
                                uclJson.append(",");
                            }
                            mclJson.append("\"").append(freq).append("\":").append(mclValues.get(freq));
                            uclJson.append("\"").append(freq).append("\":").append(uclValues.get(freq));
                        }
                    }
                    mclJson.append("}");
                    uclJson.append("}");
                    
                    Log.d("CalibrationFlow", "Per-frequency MCL data: " + mclJson.toString());
                    Log.d("CalibrationFlow", "Per-frequency UCL data: " + uclJson.toString());
                    
                    // Create calibration profile entity
                    CalibrationProfileEntity profileEntity = new CalibrationProfileEntity(
                        userId,
                        "Clinical Calibration Profile", // profileName
                        earSide,
                        avgMCL,
                        avgUCL,
                        hearingProfileId
                    );
                    
                    // Set per-frequency data
                    profileEntity.setMclPerFrequencyJson(mclJson.toString());
                    profileEntity.setUclPerFrequencyJson(uclJson.toString());
                    
                    // Save to database
                    dao.insert(profileEntity);
                    
                    Log.i("CalibrationFlow", "Clinical calibration saved - " + earSide + " ear: Avg MCL=" + avgMCL + ", Avg UCL=" + avgUCL);
                    
                    // Calibration completed - removed toast notification
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving clinical calibration data: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error saving calibration data", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void navigateAfterCalibration() {
        // Check which ear still needs calibration before navigating
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                CalibrationProfileDao dao = db.calibrationProfileDao();
                
                // Check calibration profiles for both ears FOR THE CURRENT USER
                CalibrationProfileEntity leftEarProfile = dao.getLatestProfileForEar(userId, "LEFT", hearingProfileId);
                CalibrationProfileEntity rightEarProfile = dao.getLatestProfileForEar(userId, "RIGHT", hearingProfileId);
                
                boolean hasLeftEar = (leftEarProfile != null);
                boolean hasRightEar = (rightEarProfile != null);
                
                Log.d("FlowDebug", "After " + earSide + " calibration - User " + userId + " - LEFT ear: " + hasLeftEar + ", RIGHT ear: " + hasRightEar);
                
                runOnUiThread(() -> {
                    if (!hasLeftEar) {
                        // LEFT ear still needs calibration
                        Log.d("FlowDebug", "LEFT ear missing for user " + userId + " → navigating to LEFT ear calibration");
                        Intent intent = new Intent(this, CalibrationInstructionActivity.class);
                        intent.putExtra("EAR", "LEFT");
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        startActivity(intent);
                        overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                        finish();
                    } else if (!hasRightEar) {
                        // RIGHT ear still needs calibration
                        Log.d("FlowDebug", "RIGHT ear missing for user " + userId + " → navigating to RIGHT ear calibration");
                        Intent intent = new Intent(this, CalibrationInstructionActivity.class);
                        intent.putExtra("EAR", "RIGHT");
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        startActivity(intent);
                        overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                        finish();
                    } else {
                        // Both ears calibrated - NEW WORKFLOW: Navigate to Test Completion Animation
                        Log.d("FlowDebug", "Both ears calibrated for user " + userId + " → navigating to test completion");
                        Intent intent = new Intent(this, TestCompletionActivity.class);
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        Log.d("FlowDebug", "NEW WORKFLOW: Calibration complete → showing completion animation");
                        startActivity(intent);
                        overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                        finish();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking calibration profiles: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    // Fallback - assume standard LEFT→RIGHT→PureTone flow
                    if ("LEFT".equals(earSide)) {
                        Intent intent = new Intent(this, CalibrationInstructionActivity.class);
                        intent.putExtra("EAR", "RIGHT");
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        startActivity(intent);
                        overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                        finish();
                    } else {
                        Intent intent = new Intent(this, RightEarInstructionActivity.class);
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        startActivity(intent);
                        overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                        finish();
                    }
                });
            }
        });
    }
    
    private void stopTone() {
        audioHandler.post(() -> {
            if (audioGenerator != null) {
                audioGenerator.stopTone();
            }
            isPlayingTone = false;
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up audio resources
        stopTone();
        if (audioThread != null) {
            audioThread.quitSafely();
        }
        
        // Clean up database executor
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Prevent accidental exit during calibration
        Toast.makeText(this, "Please complete the calibration test", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Play a calibration tone at specified frequency and dB level
     * Uses the same approach as PureToneTestActivity for reliable audio playback
     */
    private void playTone(int frequency, float dbLevel) {
        Log.d("CalibrationFlow", "=== PLAYING TONE ===");
        Log.d("CalibrationFlow", "freq=" + frequency + "Hz level=" + dbLevel + "dB");
        
        // Update UI displays
        runOnUiThread(() -> {
            if (frequencyDisplay != null) {
                frequencyDisplay.setText(frequency + " Hz");
                frequencyDisplay.setVisibility(View.VISIBLE);
            }
            if (levelDisplay != null) {
                levelDisplay.setText((int)dbLevel + " dB SPL");
                levelDisplay.setVisibility(View.VISIBLE);
            }
        });
        
        // Stop any currently playing tone
        stopTone();
        setButtonsEnabled(false);
        
        // Use simple streaming approach like PureToneTestActivity
        new Thread(() -> {
            try {
                // Calculate amplitude from dB SPL
                double amplitude = Math.pow(10.0, (dbLevel - 80.0) / 20.0) * 0.5; // Use 80 dB reference like PureTone
                amplitude = Math.max(amplitude, 0.1); // Minimum 10% for audibility
                amplitude = Math.min(amplitude, 0.9); // Maximum 90% for safety
                
                Log.d("CalibrationFlow", "Calculated amplitude: " + amplitude + " for " + dbLevel + " dB SPL");
                
                // Create AudioTrack with MODE_STREAM using modern AudioAttributes
                android.media.AudioTrack track = new android.media.AudioTrack.Builder()
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setAudioFormat(new android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(44100)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(44100 * 2) // 2 second buffer
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .build();
                
                track.play();
                Log.d("CalibrationFlow", "Started AudioTrack streaming");
                
                // Play for 2 seconds using ToneGenerator chunks
                int totalChunks = 40; // 2 seconds at 50ms per chunk
                for (int i = 0; i < totalChunks; i++) {
                    // Generate 50ms chunk using ToneGenerator (same as PureTone)
                    short[] chunk = com.example.audion.audio.ToneGenerator.generateSineWaveChunk(44100, 50, frequency, amplitude);
                    int written = track.write(chunk, 0, chunk.length);
                    
                    if (i == 0) {
                        Log.d("CalibrationFlow", "First chunk written: " + written + " samples, amplitude: " + amplitude);
                    }
                    
                    // Small delay between chunks
                    try {
                        Thread.sleep(40); // Slightly less than 50ms to avoid gaps
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                
                track.stop();
                track.release();
                Log.d("CalibrationFlow", "Tone playback completed successfully");
                
                // Enable buttons after playback
                runOnUiThread(() -> setButtonsEnabled(true));
                
            } catch (Exception e) {
                Log.e("CalibrationFlow", "Error playing tone: " + e.getMessage(), e);
                runOnUiThread(() -> setButtonsEnabled(true));
            }
        }).start();
    }
    
    /**
     * Fallback method to test basic AudioTrack functionality
     */
    private void tryBasicAudioTest(int frequency) {
        new Thread(() -> {
            try {
                Log.d("AudioFallback", "=== BASIC AUDIO TEST START ===");
                Log.d("AudioFallback", "Testing " + frequency + "Hz with fixed 0.5 amplitude");
                
                // Generate simple sine wave
                int sampleRate = 44100;
                int duration = 1000; // 1 second
                int numSamples = sampleRate * duration / 1000;
                short[] samples = new short[numSamples];
                
                for (int i = 0; i < numSamples; i++) {
                    double sineValue = Math.sin(2.0 * Math.PI * frequency * i / sampleRate);
                    samples[i] = (short) (sineValue * 0.5 * Short.MAX_VALUE);
                }
                
                Log.d("AudioFallback", "Generated " + samples.length + " samples, first=" + samples[0] + ", max expected=" + (short)(0.5 * Short.MAX_VALUE));
                
                // Create AudioTrack
                int bufferSize = android.media.AudioTrack.getMinBufferSize(sampleRate, 
                    android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT);
                
                android.media.AudioTrack audioTrack = new android.media.AudioTrack.Builder()
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setAudioFormat(new android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(Math.max(bufferSize, samples.length * 2))
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build();
                
                if (audioTrack.getState() == android.media.AudioTrack.STATE_INITIALIZED) {
                    audioTrack.write(samples, 0, samples.length);
                    audioTrack.play();
                    Log.d("AudioFallback", "Basic AudioTrack test SUCCESS - audio should be audible");
                    
                    Thread.sleep(duration);
                    audioTrack.stop();
                    audioTrack.release();
                } else {
                    Log.e("AudioFallback", "Basic AudioTrack test FAILED - initialization failed");
                }
                
            } catch (Exception e) {
                Log.e("AudioFallback", "Basic audio test exception: " + e.getMessage(), e);
            }
        }).start();
    }
}