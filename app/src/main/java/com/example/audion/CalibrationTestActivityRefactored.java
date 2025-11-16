package com.example.audion;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.audion.audio.ToneGenerator;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.CalibrationProfileDao;
import com.example.audion.data.CalibrationProfileEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Refactored Clinical Calibration Test Activity
 * Implements SeekBar-based comfortable volume calibration with real-time feedback
 * 
 * SIMPLIFIED WORKFLOW:
 * 1. User adjusts SeekBar to find comfortable volume level
 * 2. User saves comfortable volume
 * 3. Automatically proceed to next frequency
 * 4. After all 3 frequencies → Navigate to PureToneInstructionActivity
 */
public class CalibrationTestActivityRefactored extends AppCompatActivity {
    private static final String TAG = "CalibrationRefactored";
    
    // Simplified frequency set: Low (500Hz), Mid (1000Hz), High (2000Hz)
    private final int[] frequencies = {500, 1000, 2000};
    private int currentFreqIndex = 0;
    
    // UI Components
    private TextView tvTitle, tvProgress, tvInstruction;
    // Removed: tvCurrentLevel, tvFrequencyLabel
    private SeekBar seekBarVolume;
    private MaterialButton btnSave;
    private ImageView earImage;
    private View bar1, bar2, bar3;  // Frequency progress bars
    
    // Clinical parameters
    private String earSide;
    private int userId;
    private int hearingProfileId;
    private boolean fromNewProfile; // Flag to track new profile creation flow
    
    // Data storage: [{freq: 500, MCL: 68.0}, ...]
    private List<FrequencyCalibrationData> calibrationData = new ArrayList<>();
    
    // Audio generation
    private AudioTrack audioTrack;
    private HandlerThread audioThread;
    private Handler audioHandler;
    private volatile boolean isPlayingTone = false;
    private ExecutorService dbExecutor;
    private double currentPhase = 0.0; // Track phase for continuous tone
    private volatile double currentAmplitude = 0.0; // Current amplitude for dynamic adjustment
    private volatile int currentFrequency = 0; // Current playing frequency
    
    // SeekBar maps to 30-100 dB SPL
    private static final float MIN_DB_SPL = 30f;
    private static final float MAX_DB_SPL = 100f;
    private static final int SAMPLE_RATE = 44100;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_seekbar);
        
        // Get intent parameters
        earSide = getIntent().getStringExtra("EAR");
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        fromNewProfile = getIntent().getBooleanExtra("FROM_NEW_PROFILE", false);
        
        if (earSide == null || userId < 0 || hearingProfileId < 0) {
            Log.e(TAG, "Missing required parameters: EAR=" + earSide + ", USER_ID=" + userId + ", PROFILE_ID=" + hearingProfileId);
            Toast.makeText(this, "Error: Missing required parameters", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        Log.d("FlowDebug", "Starting SeekBar calibration for " + earSide + " ear, User " + userId);
        
        // Initialize audio thread
        audioThread = new HandlerThread("CalibrationAudioThread");
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());
        
        // Initialize database executor
        dbExecutor = Executors.newSingleThreadExecutor();
        
        // Initialize UI
        initializeUI();
        
        // Start calibration
        startCalibration();
    }
    
    private void initializeUI() {
        // Top section: frequency progress bars
        bar1 = findViewById(R.id.barFreq1);
        bar2 = findViewById(R.id.barFreq2);
        bar3 = findViewById(R.id.barFreq3);
        
        // Title and progress
        tvTitle = findViewById(R.id.tvCalibrationTitle);
        tvProgress = findViewById(R.id.tvProgress);
        // Removed: tvFrequencyLabel = findViewById(R.id.tvFrequencyLabel);
        
        // Ear image - use calibration_test GIF for both ears
        earImage = findViewById(R.id.imageEar);
        // Always show calibration_test GIF during the test
        earImage.setImageResource(R.drawable.calibration_test);
        
        // Center section: instruction and SeekBar
        tvInstruction = findViewById(R.id.tvInstruction);
        seekBarVolume = findViewById(R.id.seekBarVolume);
        // Removed: tvCurrentLevel = findViewById(R.id.tvCurrentLevel);
        
        // Bottom section: buttons
        btnSave = findViewById(R.id.btnSaveVolume);
        
        // Set up SeekBar listener for real-time feedback
        seekBarVolume.setMax(100); // 0-100 maps to 30-100 dB SPL
        seekBarVolume.setProgress(50); // Start at mid-level (65 dB SPL)
        
        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float dbLevel = progressToDbSpl(progress);
                    // Removed: tvCurrentLevel.setText(String.format("%d dB SPL", (int)dbLevel));
                    
                    // Play continuous tone at current level for real-time feedback
                    playContinuousTone(frequencies[currentFreqIndex], dbLevel);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // User started adjusting - start playing tone
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Continue playing tone even after user lifts finger
                // Tone will stop when they press Save button
            }
        });
        
        // Save button handler
        btnSave.setOnClickListener(v -> onSaveButtonClicked());
    }
    
    private void startCalibration() {
        currentFreqIndex = 0;
        calibrationData.clear();
        
        updateUI();
        
        // Start playing initial tone at mid-level
        float initialDb = progressToDbSpl(seekBarVolume.getProgress());
        playContinuousTone(frequencies[currentFreqIndex], initialDb);
    }
    
    private void updateUI() {
        int freq = frequencies[currentFreqIndex];
        
        // Update progress text
        tvProgress.setText(String.format("Testing %d of %d frequencies", currentFreqIndex + 1, frequencies.length));
        
        // Update frequency label
        // Removed: Update frequency label
        // String freqLabel = "";
        // switch (freq) {
        //     case 500: freqLabel = "Low (500 Hz)"; break;
        //     case 1000: freqLabel = "Mid (1000 Hz)"; break;
        //     case 2000: freqLabel = "High (2000 Hz)"; break;
        // }
        // tvFrequencyLabel.setText(freqLabel);
        
        // Update frequency progress bars
        updateFrequencyBars();
        
        // Update instruction text
        tvInstruction.setText("Adjust until volume feels comfortable");
        btnSave.setText("Save Volume");
        btnSave.setVisibility(View.VISIBLE);
        seekBarVolume.setEnabled(true);
        
        // Removed: Update current level display
        // float currentDb = progressToDbSpl(seekBarVolume.getProgress());
        // tvCurrentLevel.setText(String.format("%d dB SPL", (int)currentDb));
    }
    
    private void updateFrequencyBars() {
        // Bar 1 (500 Hz) - Only show completed if data saved
        if (hasCalibrationData(500)) {
            bar1.setBackgroundResource(R.drawable.indicator_active);
        } else {
            bar1.setBackgroundResource(R.drawable.indicator_inactive);
        }
        
        // Bar 2 (1000 Hz) - Only show completed if data saved
        if (hasCalibrationData(1000)) {
            bar2.setBackgroundResource(R.drawable.indicator_active);
        } else {
            bar2.setBackgroundResource(R.drawable.indicator_inactive);
        }
        
        // Bar 3 (2000 Hz) - Only show completed if data saved
        if (hasCalibrationData(2000)) {
            bar3.setBackgroundResource(R.drawable.indicator_active);
        } else {
            bar3.setBackgroundResource(R.drawable.indicator_inactive);
        }
    }
    
    private boolean hasCalibrationData(int frequency) {
        for (FrequencyCalibrationData data : calibrationData) {
            if (data.frequency == frequency && data.MCL > 0) {
                return true;
            }
        }
        return false;
    }
    
    private void onSaveButtonClicked() {
        // Bounds check to prevent ArrayIndexOutOfBoundsException
        if (currentFreqIndex >= frequencies.length) {
            Log.w("CalibrationDebug", "Save button clicked after all frequencies completed. Ignoring.");
            return;
        }
        
        float currentDb = progressToDbSpl(seekBarVolume.getProgress());
        int freq = frequencies[currentFreqIndex];
        
        // Validate dB range
        if (currentDb < MIN_DB_SPL || currentDb > MAX_DB_SPL) {
            Toast.makeText(this, "Invalid level: " + (int)currentDb + " dB SPL", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate MCL is in reasonable range (40-85 dB SPL)
        if (currentDb < 40 || currentDb > 85) {
            String warning = currentDb < 40 ? 
                "Comfort level unusually low (" + (int)currentDb + " dB). Please verify." :
                "Comfort level unusually high (" + (int)currentDb + " dB). Please verify.";
            Toast.makeText(this, warning, Toast.LENGTH_LONG).show();
            Log.w("CalibrationValidation", warning + " for " + freq + "Hz");
        }
        
        // Stop audio playback
        stopTone();
        
        // Convert dB SPL to dB HL using RETSPL correction
        // This ensures Pure Tone Test receives calibrated values in the correct units
        double retspl = ToneGenerator.getRETSPL(freq);
        float mclDbHL = (float)(currentDb - retspl);
        float uclDbHL = mclDbHL + 20f; // UCL estimated as MCL + 20 dB
        
        Log.d("AudioDebug", String.format("Unit conversion for %dHz: %.1f dB SPL - %.1f RETSPL = %.1f dB HL", 
            freq, currentDb, retspl, mclDbHL));
        
        // Store calibration data in dB HL (not dB SPL)
        FrequencyCalibrationData data = new FrequencyCalibrationData(freq, mclDbHL, uclDbHL);
        data.uclEstimated = true;
        calibrationData.add(data);
        
        Log.d("FlowDebug", String.format("Saved: freq=%dHz, MCL=%.1f dB HL, UCL=%.1f dB HL (estimated)", 
            freq, mclDbHL, uclDbHL));
        
        // Show toast notification
        String freqLabel = freq == 500 ? "Low" : freq == 1000 ? "Mid" : "High";
        Toast.makeText(this, "✓ Comfort level saved for " + freqLabel + " (" + freq + " Hz)", 
            Toast.LENGTH_SHORT).show();
        
        // Move to next frequency
        currentFreqIndex++;
        
        if (currentFreqIndex < frequencies.length) {
            // More frequencies to test
            updateUI();
            
            // Reset SeekBar to mid-level for next frequency
            seekBarVolume.setProgress(50);
            
            // Start playing next frequency
            float initialDb = progressToDbSpl(seekBarVolume.getProgress());
            playContinuousTone(frequencies[currentFreqIndex], initialDb);
        } else {
            // All frequencies tested - show summary
            showCalibrationSummary();
        }
    }
    
    private void showCalibrationSummary() {
        stopTone();
        
        // Disable save button and seekbar to prevent further input
        btnSave.setEnabled(false);
        seekBarVolume.setEnabled(false);
        
        // Build summary dialog
        StringBuilder summary = new StringBuilder();
        summary.append("Calibration Complete!\n\n");
        summary.append(earSide).append(" Ear Results:\n\n");
        
        for (FrequencyCalibrationData data : calibrationData) {
            String freqLabel = data.frequency == 500 ? "Low" : data.frequency == 1000 ? "Mid" : "High";
            summary.append(String.format("%s (%d Hz): %d dB SPL\n", 
                freqLabel, data.frequency, (int)data.MCL));
        }
        
        Log.i("FlowDebug", "Calibration summary:\n" + summary.toString());
        
        // Show summary dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Calibration Complete!")
            .setMessage(summary.toString())
            .setPositiveButton("Continue", (dialog, which) -> {
                saveCalibrationData();
            })
            .setCancelable(false)
            .show();
    }
    
    private void saveCalibrationData() {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                CalibrationProfileDao dao = db.calibrationProfileDao();
                
                // Calculate averages for backward compatibility
                float avgMCL = 0f;
                float avgUCL = 0f;
                
                for (FrequencyCalibrationData data : calibrationData) {
                    avgMCL += data.MCL;
                    avgUCL += data.UCL;
                }
                avgMCL /= calibrationData.size();
                avgUCL /= calibrationData.size();
                
                // Create JSON arrays for per-frequency data
                // Format: [{freq:500, MCL:68.0, UCL:82.0}, {freq:1000, MCL:70.0, UCL:85.0}, ...]
                JSONArray calibrationArray = new JSONArray();
                JSONObject mclObject = new JSONObject();
                JSONObject uclObject = new JSONObject();
                
                for (FrequencyCalibrationData data : calibrationData) {
                    // Detailed array format
                    JSONObject freqData = new JSONObject();
                    freqData.put("freq", data.frequency);
                    freqData.put("MCL", data.MCL);
                    freqData.put("UCL", data.UCL);
                    freqData.put("uclEstimated", data.uclEstimated);
                    calibrationArray.put(freqData);
                    
                    // Simple object format for backward compatibility
                    mclObject.put(String.valueOf(data.frequency), data.MCL);
                    uclObject.put(String.valueOf(data.frequency), data.UCL);
                }
                
                Log.d("AudioDebug", "Calibration data JSON: " + calibrationArray.toString());
                Log.d("AudioDebug", "MCL per frequency: " + mclObject.toString());
                Log.d("AudioDebug", "UCL per frequency: " + uclObject.toString());
                
                // Create calibration profile entity
                CalibrationProfileEntity entity = new CalibrationProfileEntity(
                    userId,
                    "SeekBar Calibration Profile",
                    earSide,
                    avgMCL,
                    avgUCL,
                    hearingProfileId
                );
                
                // Store both formats for flexibility
                entity.setMclPerFrequencyJson(mclObject.toString());
                entity.setUclPerFrequencyJson(uclObject.toString());
                
                // Also store detailed array in deviceCorrections field for future use
                entity.setDeviceCorrections(calibrationArray.toString());
                
                // Save to database
                long id = dao.insert(entity);
                
                Log.i("FlowDebug", String.format("Calibration saved: ID=%d, %s ear, Avg MCL=%.1f, Avg UCL=%.1f", 
                       id, earSide, avgMCL, avgUCL));
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "✅ " + earSide + " ear calibration saved!", Toast.LENGTH_SHORT).show();
                    navigateAfterCalibration();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving calibration data: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error saving calibration data", Toast.LENGTH_LONG).show();
                    // Still navigate even if save failed
                    navigateAfterCalibration();
                });
            }
        });
    }
    
    private void navigateAfterCalibration() {
        // Check which ear still needs calibration
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                CalibrationProfileDao dao = db.calibrationProfileDao();
                
                CalibrationProfileEntity leftProfile = dao.getLatestProfileForEar(userId, "LEFT", hearingProfileId);
                CalibrationProfileEntity rightProfile = dao.getLatestProfileForEar(userId, "RIGHT", hearingProfileId);
                
                boolean hasLeft = (leftProfile != null);
                boolean hasRight = (rightProfile != null);
                
                Log.d("FlowDebug", "Navigation check: LEFT=" + hasLeft + ", RIGHT=" + hasRight);
                
                runOnUiThread(() -> {
                    Intent intent;
                    
                    if (!hasLeft && "RIGHT".equals(earSide)) {
                        // Just finished RIGHT, need LEFT
                        Log.d("FlowDebug", "Navigating to LEFT ear calibration");
                        intent = new Intent(this, CalibrationInstructionActivity.class);
                        intent.putExtra("EAR", "LEFT");
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        intent.putExtra("FROM_NEW_PROFILE", fromNewProfile); // Pass flag
                    } else if (!hasRight && "LEFT".equals(earSide)) {
                        // Just finished LEFT, need RIGHT
                        Log.d("FlowDebug", "Navigating to RIGHT ear calibration");
                        intent = new Intent(this, CalibrationInstructionActivity.class);
                        intent.putExtra("EAR", "RIGHT");
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        intent.putExtra("FROM_NEW_PROFILE", fromNewProfile); // Pass flag
                    } else {
                        // Both ears calibrated - navigate to Test Completion animation
                        Log.d("FlowDebug", "Both ears calibrated - navigating to Test Completion");
                        intent = new Intent(this, TestCompletionActivity.class);
                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                        intent.putExtra("FROM_NEW_PROFILE", fromNewProfile); // Pass flag
                    }
                    
                    startActivity(intent);
                    finish();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking calibration status: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    // Fallback navigation
                    Intent intent = new Intent(this, HomeActivity.class);
                    intent.putExtra("USER_ID", userId);
                    intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
                    startActivity(intent);
                    finish();
                });
            }
        });
    }
    
    /**
     * Play continuous tone with real-time amplitude adjustment
     * Implements stereo channel separation based on ear side
     */
    private void playContinuousTone(final int frequency, final float dbSpl) {
        audioHandler.post(() -> {
            try {
                // Validate dB range
                if (dbSpl < MIN_DB_SPL || dbSpl > MAX_DB_SPL) {
                    Log.w("AudioDebug", "dB level out of range: " + dbSpl + ", clamping to [30, 100]");
                }
                float clampedDb = Math.max(MIN_DB_SPL, Math.min(MAX_DB_SPL, dbSpl));
                
                // Convert dB SPL to amplitude (using 70 dB @ 50% amplitude reference)
                // Increased reference point for better audibility during calibration
                double amplitude = Math.pow(10.0, (clampedDb - 70.0) / 20.0) * 0.5;
                amplitude = Math.max(0.05, Math.min(1.0, amplitude)); // Safety limits: 5%-100%
                
                // Check if frequency changed or tone not playing yet
                if (currentFrequency != frequency || !isPlayingTone) {
                    // Frequency changed - need to restart tone
                    stopToneInternal();
                    
                    Log.d("AudioDebug", String.format("Starting new tone: %dHz at %.1f dB SPL (amplitude=%.3f) for %s ear", 
                           frequency, clampedDb, amplitude, earSide));
                    
                    currentFrequency = frequency;
                    currentAmplitude = amplitude;
                    
                    startNewTone(frequency, amplitude);
                } else {
                    // Same frequency - just update amplitude dynamically
                    currentAmplitude = amplitude;
                    Log.d("AudioDebug", String.format("Updating amplitude: %.1f dB SPL (amplitude=%.3f)", 
                           clampedDb, amplitude));
                }
                
            } catch (Exception e) {
                Log.e("AudioDebug", "Error in playContinuousTone: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Start a new tone generation thread
     */
    private void startNewTone(final int frequency, final double initialAmplitude) {
        try {
            // Use STEREO configuration for proper left/right separation
            int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
            
            Log.d("AudioDebug", "Stereo separation: " + earSide + " ear → " + 
                  ("LEFT".equals(earSide) ? "LEFT channel" : "RIGHT channel"));
            
            // Get minimum buffer size for STEREO
            int minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, 
                channelConfig, 
                AudioFormat.ENCODING_PCM_16BIT
            );
            
            if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Log.e("AudioDebug", "Invalid audio parameters - channelConfig=" + channelConfig + ", sampleRate=" + SAMPLE_RATE);
                return;
            }
            
            // Create AudioTrack with stereo separation
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(channelConfig)  // ✅ STEREO SEPARATION
                        .build())
                    .setBufferSizeInBytes(minBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
                
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.e("AudioDebug", "AudioTrack initialization failed");
                    return;
                }
                
                // Set volume to maximum (we control amplitude in the samples)
                audioTrack.setVolume(1.0f);
                
                // Request audio focus with a simple listener
                AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
                    Log.d("AudioDebug", "Audio focus change: " + focusChange);
                };
                int result = audioManager.requestAudioFocus(focusChangeListener, 
                    AudioManager.STREAM_MUSIC, 
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w("AudioDebug", "Audio focus not granted: " + result);
                }
                
                Log.d("AudioDebug", "AudioTrack initialized successfully, starting playback...");
                
                // Start playback
                audioTrack.play();
                isPlayingTone = true;
                
                // Make variables effectively final for lambda
                final int finalFrequency = frequency;
                final boolean isLeftEar = "LEFT".equals(earSide);
                
                // Reset phase for new tone
                currentPhase = 0.0;
                
                // Generate and stream tone continuously
                new Thread(() -> {
                    try {
                        // Generate continuous sine wave without envelope for calibration
                        // Use smaller chunks (50ms) for faster response to amplitude changes
                        int chunkDurationMs = 50;
                        int samplesPerChunk = (SAMPLE_RATE * chunkDurationMs) / 1000;
                        double angleIncrement = 2.0 * Math.PI * finalFrequency / SAMPLE_RATE;
                        
                        while (isPlayingTone) {
                            // Generate pure sine wave with phase continuity
                            // Use currentAmplitude (volatile) for dynamic updates
                            short[] monoChunk = new short[samplesPerChunk];
                            
                            for (int i = 0; i < samplesPerChunk; i++) {
                                double sample = Math.sin(currentPhase) * currentAmplitude;
                                monoChunk[i] = (short) (sample * Short.MAX_VALUE);
                                currentPhase += angleIncrement;
                                
                                // Keep phase in reasonable range to prevent precision issues
                                if (currentPhase > 2.0 * Math.PI) {
                                    currentPhase -= 2.0 * Math.PI;
                                }
                            }
                            
                            // Convert mono to stereo with sound only in correct ear
                            // Stereo format: [L0, R0, L1, R1, L2, R2, ...]
                            short[] stereoChunk = new short[monoChunk.length * 2];
                            for (int i = 0; i < monoChunk.length; i++) {
                                if (isLeftEar) {
                                    stereoChunk[i * 2] = monoChunk[i];     // LEFT channel
                                    stereoChunk[i * 2 + 1] = 0;            // RIGHT channel (silent)
                                } else {
                                    stereoChunk[i * 2] = 0;                // LEFT channel (silent)
                                    stereoChunk[i * 2 + 1] = monoChunk[i]; // RIGHT channel
                                }
                            }
                            
                            int written = audioTrack.write(stereoChunk, 0, stereoChunk.length);
                            if (written < 0) {
                                Log.e("AudioDebug", "AudioTrack write error: " + written);
                                break;
                            }
                            
                            // No sleep - write continuously for smooth tone
                        }
                    } catch (Exception e) {
                        Log.e("AudioDebug", "Error in tone playback loop: " + e.getMessage(), e);
                    }
                    // Don't stop here - let stopToneInternal() handle cleanup
                }, "TonePlaybackThread").start();
                
        } catch (Exception e) {
            Log.e("AudioDebug", "Error starting tone playback: " + e.getMessage(), e);
        }
    }
    
    private void stopTone() {
        audioHandler.post(this::stopToneInternal);
    }
    
    private void stopToneInternal() {
        isPlayingTone = false;
        
        // Give playback thread time to exit loop
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        if (audioTrack != null) {
            try {
                int state = audioTrack.getState();
                if (state == AudioTrack.STATE_INITIALIZED) {
                    int playState = audioTrack.getPlayState();
                    if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.pause();
                        audioTrack.flush();
                    }
                    audioTrack.stop();
                }
                audioTrack.release();
            } catch (IllegalStateException e) {
                Log.w("AudioDebug", "AudioTrack already released: " + e.getMessage());
            } catch (Exception e) {
                Log.e("AudioDebug", "Error stopping AudioTrack: " + e.getMessage());
            } finally {
                audioTrack = null;
            }
        }
    }
    
    /**
     * Convert SeekBar progress (0-100) to dB SPL (30-100)
     */
    private float progressToDbSpl(int progress) {
        return MIN_DB_SPL + (progress / 100f) * (MAX_DB_SPL - MIN_DB_SPL);
    }
    
    /**
     * Convert dB SPL (30-100) to SeekBar progress (0-100)
     */
    private int dbSplToProgress(float dbSpl) {
        return (int) (((dbSpl - MIN_DB_SPL) / (MAX_DB_SPL - MIN_DB_SPL)) * 100);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        stopTone();
        
        if (audioThread != null) {
            audioThread.quitSafely();
        }
        
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Prevent accidental exit during calibration
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Exit Calibration?")
            .setMessage("Your progress will be lost. Are you sure?")
            .setPositiveButton("Exit", (dialog, which) -> {
                stopTone();
                super.onBackPressed();
            })
            .setNegativeButton("Continue", null)
            .show();
    }
    
    /**
     * Data class to store calibration results per frequency
     */
    private static class FrequencyCalibrationData {
        int frequency;
        float MCL;
        float UCL;
        boolean uclEstimated = false;  // true if user skipped UCL testing
        
        FrequencyCalibrationData(int frequency, float MCL, float UCL) {
            this.frequency = frequency;
            this.MCL = MCL;
            this.UCL = UCL;
        }
    }
}
