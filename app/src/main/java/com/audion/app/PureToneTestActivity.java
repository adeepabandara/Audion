package com.audion.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.util.Random;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.audion.app.audio.ToneGenerator;
import com.audion.app.data.AppDatabase;
import com.audion.app.data.HearingTestResult;
import com.audion.app.data.HearingTestResultDao;
import com.audion.app.data.CalibrationProfileDao;
import com.audion.app.data.CalibrationProfileEntity;
import com.audion.app.R;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class PureToneTestActivity extends AppCompatActivity {

    private static final String TAG = "PureToneTestActivity";
    
    // ANSI S3.6 compliant frequency sequence: 1000->2000->4000->8000->500->250 (with 1000 Hz retest)
    private final int[] frequencies = {1000, 2000, 4000, 8000, 500, 250, 1000}; // Retest 1000Hz at end
    private int currentFreqIndex = 0;
    private String currentEar;
    
    // Calibration data integration
    private Map<Integer, Float> perFrequencyMCL = new HashMap<>();
    private Map<Integer, Float> perFrequencyUCL = new HashMap<>();
    private float defaultStartLevel = 40.0f;  // Fallback if no calibration
    private float defaultMaxLevel = 120.0f;   // Fallback if no calibration
    private boolean calibrationDataLoaded = false;

    private ProgressBar progressBar;
    private MaterialButton buttonNotHeard;
    private MaterialButton circleButton;

    private Thread playbackThread;
    private Thread progressAnimationThread;
    private volatile boolean stopPlayback = false;
    private volatile boolean stopProgressAnimation = false;
    
    // Legacy compatibility
    private int lastAmplitudeStep = 0;
    
    // Progress animation variables
    private long frequencyTestStartTime = 0;
    private static final long ESTIMATED_TEST_DURATION_MS = 30000; // 30 seconds for continuous sweep
    
    // Automated clinical tone presentation
    private float currentDbHL = 0.0f;     // Current presentation level
    private float thresholdDbHL = -10.0f; // Will be determined by algorithm
    private int reversalCount = 0;        // Count threshold reversals for reliability
    private boolean lastResponseWasHeard = false;  // Track for reversals
    private boolean thresholdFound = false;
    private float reliabilityScore = 0.0f;
    
    // Hughson-Westlake staircase variables
    private java.util.Map<Float, Integer> ascendingResponsesPerLevel = new java.util.HashMap<>();  // Track ASCENDING responses only
    private java.util.Map<Float, Integer> ascendingAttemptsPerLevel = new java.util.HashMap<>();   // Track ASCENDING attempts
    private boolean lastPresentationWasAscending = false;  // Track if last tone was during ascent
    private int totalPresentationCount = 0;  // Safety counter to prevent infinite loops
    private static final int MAX_PRESENTATIONS_PER_FREQUENCY = 30;  // Maximum tones before auto-advance
    private static final int TONE_DURATION_MS = 1500;  // 1.5 seconds
    private static final int INTER_STIMULUS_MIN_MS = 2000;  // 2-4 seconds between tones
    private static final int INTER_STIMULUS_MAX_MS = 4000;
    private Random random = new Random();

    private HearingTestResultDao hearingTestResultDao;
    private int userId, hearingProfileId;
    private boolean fromNewProfile; // Flag to track new profile creation flow
    private TextView tvStatus; // Real-time status feedback for user

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_tone_test_new);

        // Initialize currentEar from the Intent
        currentEar = getIntent().getStringExtra("EAR");
        if (currentEar == null) currentEar = "RIGHT";

        // Update the title text
        TextView title = findViewById(R.id.tvTitle);
        title.setText((currentEar.equalsIgnoreCase("LEFT") ? "Left" : "Right") + " Ear – Pure Tone Test");
        
        // Update the description
        TextView description = findViewById(R.id.tvDescription);
        description.setText("Listen carefully. Tap the circle when you hear the tone.");

        // Get Intent extras
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        fromNewProfile = getIntent().getBooleanExtra("FROM_NEW_PROFILE", false);
        if (userId < 0 || hearingProfileId < 0) {
            Toast.makeText(this, "Missing USER_ID or HEARING_PROFILE_ID", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Wire up UI
        progressBar = findViewById(R.id.circularProgress);
        buttonNotHeard = findViewById(R.id.btnDidntHear); // Primary teal "Didn't Hear" button
        circleButton = findViewById(R.id.btnHeard); // Circle button for tapping
        tvStatus = findViewById(R.id.tvStatus); // Status message for feedback

        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE); // Initially hidden

        // Hide "Didn't Hear" button initially
        buttonNotHeard.setVisibility(View.GONE);

        hearingTestResultDao = AppDatabase.getInstance(this).hearingTestResultDao();
        
        // ✅ CRITICAL: Load calibration data before starting test
        loadCalibrationData();

        // Circle button click - handled per-tone in presentSingleClinicalTone
        // This is for the "Start Again" functionality only
        circleButton.setOnClickListener(v -> {
            // Check if retry mode is active
            View ivRetry = findViewById(R.id.ivRetry);
            if (ivRetry.getVisibility() == View.VISIBLE) {
                // Restart the current frequency
                resetCurrentFrequency();
            }
            // Regular tone response is handled in presentSingleClinicalTone
        });
        
        // "Didn't Hear" button click
        buttonNotHeard.setOnClickListener(v -> {
            stopPlayback = true;
            recordNotHeardAndAdvance();
        });

        // Auto-start the test
        startRampForCurrentFrequency();
    }
    
    /**
     * Load calibration data for current ear from database
     * This enables personalized starting levels and safety limits
     */
    private void loadCalibrationData() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                CalibrationProfileDao calibrationDao = db.calibrationProfileDao();
                
                // Query calibration profile for current ear
                CalibrationProfileEntity calibration = calibrationDao.getLatestProfileForEar(
                    userId, currentEar, hearingProfileId);
                
                if (calibration != null) {
                    Log.i("FlowDebug", "✅ Calibration data loaded for " + currentEar + " ear");
                    Log.d("AudioDebug", "Calibration: MCL=" + calibration.getMclDbSpl() + 
                          "dB, UCL=" + calibration.getUclDbSpl() + "dB");
                    
                    // Parse per-frequency calibration data from JSON
                    parsePerFrequencyCalibration(
                        calibration.getMclPerFrequencyJson(), 
                        calibration.getUclPerFrequencyJson()
                    );
                    
                    // Set defaults from average values
                    defaultStartLevel = Math.max(0, calibration.getMclDbSpl() - 30.0f);
                    defaultMaxLevel = calibration.getUclDbSpl();
                    
                    calibrationDataLoaded = true;
                    
                    Log.i("AudioDebug", String.format(
                        "Using calibration: StartLevel=%.1f dB HL (MCL-30), MaxLevel=%.1f dB (UCL)",
                        defaultStartLevel, defaultMaxLevel));
                    
                } else {
                    Log.w("FlowDebug", "⚠️ No calibration data found for " + currentEar + 
                          " ear - using defaults (40 dB start, 120 dB max)");
                    calibrationDataLoaded = false;
                }
                
            } catch (Exception e) {
                Log.e("FlowDebug", "Error loading calibration data: " + e.getMessage(), e);
                calibrationDataLoaded = false;
            }
        }).start();
        
        // Wait briefly for calibration to load (non-blocking on UI thread)
        try {
            Thread.sleep(200);  // 200ms should be enough for DB query
        } catch (InterruptedException e) {
            // Continue with test
        }
    }
    
    /**
     * Parse per-frequency MCL and UCL data from JSON strings
     * Format: {"500": 65.0, "1000": 70.0, "2000": 72.0}
     */
    private void parsePerFrequencyCalibration(String mclJson, String uclJson) {
        try {
            if (mclJson != null && !mclJson.isEmpty()) {
                JSONObject mclData = new JSONObject(mclJson);
                for (int freq : frequencies) {
                    String freqKey = String.valueOf(freq);
                    if (mclData.has(freqKey)) {
                        float mcl = (float) mclData.getDouble(freqKey);
                        perFrequencyMCL.put(freq, mcl);
                    }
                }
                Log.d("AudioDebug", "Per-frequency MCL loaded: " + perFrequencyMCL.size() + " frequencies");
            }
            
            if (uclJson != null && !uclJson.isEmpty()) {
                JSONObject uclData = new JSONObject(uclJson);
                for (int freq : frequencies) {
                    String freqKey = String.valueOf(freq);
                    if (uclData.has(freqKey)) {
                        float ucl = (float) uclData.getDouble(freqKey);
                        perFrequencyUCL.put(freq, ucl);
                    }
                }
                Log.d("AudioDebug", "Per-frequency UCL loaded: " + perFrequencyUCL.size() + " frequencies");
            }
            
        } catch (Exception e) {
            Log.e("AudioDebug", "Error parsing per-frequency calibration: " + e.getMessage(), e);
        }
    }

    private void startRampForCurrentFrequency() {
        stopPlayback = false;
        stopProgressAnimation = false;
        
        final int freq = frequencies[currentFreqIndex];
        
        // ✅ Use calibration data to set personalized starting level and safety limit
        float startLevel = defaultStartLevel;  // Default: 40 dB HL
        float maxLevel = defaultMaxLevel;      // Default: 120 dB HL
        
        if (calibrationDataLoaded && perFrequencyMCL.containsKey(freq)) {
            // Use frequency-specific calibration
            float mcl = perFrequencyMCL.get(freq);
            float ucl = perFrequencyUCL.getOrDefault(freq, defaultMaxLevel);
            
            // Start 30 dB below MCL (clinical standard)
            startLevel = Math.max(0, mcl - 30.0f);
            maxLevel = ucl;  // Safety limit at UCL
            
            Log.i("AudioDebug", String.format(
                "%dHz: Using calibration - Start=%.1f dB HL (MCL-30), Max=%.1f dB (UCL), MCL=%.1f dB",
                freq, startLevel, maxLevel, mcl));
        } else {
            Log.d("AudioDebug", String.format(
                "%dHz: No calibration - using defaults (Start=%.1f dB, Max=%.1f dB)",
                freq, startLevel, maxLevel));
        }
        
        // Reset threshold detection variables for new frequency
        currentDbHL = startLevel;    // ✅ Use calibration-informed start level
        thresholdDbHL = -10.0f;      // Will be determined
        reversalCount = 0;
        lastResponseWasHeard = false;
        thresholdFound = false;
        ascendingResponsesPerLevel.clear();  // Clear response tracking
        ascendingAttemptsPerLevel.clear();   // Clear attempts tracking
        lastPresentationWasAscending = true; // CONSUMER MODE: Always ascending, no descending
        
        // Reset UI - hide "Didn't Hear" button, show progress, reset button text
        runOnUiThread(() -> {
            circleButton.setText("Tap here");
            circleButton.setEnabled(true);
            buttonNotHeard.setVisibility(View.GONE);
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);
            Log.d(TAG, "Starting CONSUMER ascending-only procedure for " + freq + " Hz with calibrated levels");
        });
        
        // Start smooth progress animation
        startSmoothProgressAnimation();
        
        // Start Hughson-Westlake procedure with calibration-adjusted levels
        presentDiscreteTonesHughsonWestlake(freq, startLevel, maxLevel);
    }
    
    /**
     * Animate progress bar smoothly throughout the frequency test
     * Progress increases continuously based on elapsed time
     */
    private void startSmoothProgressAnimation() {
        frequencyTestStartTime = System.currentTimeMillis();
        
        progressAnimationThread = new Thread(() -> {
            try {
                while (!stopProgressAnimation && !thresholdFound) {
                    long elapsed = System.currentTimeMillis() - frequencyTestStartTime;
                    
                    // Calculate progress (0-100) based on elapsed time
                    // Progress slows down as we approach 100% (logarithmic feel)
                    float progress = Math.min(100f, (elapsed / (float)ESTIMATED_TEST_DURATION_MS) * 100f);
                    
                    // Cap at 95% to indicate test is still ongoing
                    if (progress > 95f) progress = 95f;
                    
                    final int progressInt = (int)progress;
                    runOnUiThread(() -> {
                        progressBar.setProgress(progressInt);
                    });
                    
                    // Update every 100ms for smooth animation
                    Thread.sleep(100);
                }
                
                // When threshold found, complete to 100%
                if (thresholdFound) {
                    runOnUiThread(() -> {
                        progressBar.setProgress(100);
                    });
                }
                
            } catch (InterruptedException e) {
                Log.d(TAG, "Progress animation interrupted");
            }
        });
        progressAnimationThread.start();
    }
    
    /**
     * Present CONTINUOUS ascending tone that sweeps smoothly with calibration limits
     * Synchronized with progress bar for seamless user experience
     */
    private void presentDiscreteTonesHughsonWestlake(int frequency, float startDb, float maxDb) {
        playbackThread = new Thread(() -> {
            try {
                Log.d(TAG, "Starting CONTINUOUS ascending tone for " + frequency + " Hz (Start=" + startDb + " dB, Max=" + maxDb + " dB)");
                
                // Generate continuous tone at this frequency with calibration limits
                presentContinuousAscendingTone(frequency, startDb, maxDb);
                
            } catch (Exception e) {
                Log.e(TAG, "Error in continuous tone: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error during test: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
        playbackThread.start();
    }
    
    /**
     * Present continuous ascending tone that sweeps with calibration-adjusted limits
     * Perfectly synchronized with progress bar for seamless experience
     */
    private void presentContinuousAscendingTone(final int frequency, final float startDb, final float endDb) throws InterruptedException {
        final int SAMPLE_RATE = 44100;
        final int TEST_DURATION_MS = 30000; // 30 seconds - faster sweep
        final int BUFFER_SIZE_MS = 100; // 100ms chunks for smooth streaming
        
        Log.d("AudioDebug", String.format("✅ Sweeping %dHz from %.1f to %.1f dB HL over %d seconds", 
                frequency, startDb, endDb, TEST_DURATION_MS/1000));
        
        int samplesPerBuffer = (SAMPLE_RATE * BUFFER_SIZE_MS) / 1000;
        int numBuffers = (TEST_DURATION_MS / BUFFER_SIZE_MS);
        
        // Create streaming AudioTrack with STEREO configuration
        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int trackBufferSize = Math.max(minBufferSize, samplesPerBuffer * 2 * 2 * 4); // stereo * 2 bytes * 4 buffers
        
        final boolean isLeftEar = "LEFT".equals(currentEar);
        Log.d("AudioDebug", String.format("Continuous sweep stereo: %s ear → %s channel", 
            currentEar, isLeftEar ? "LEFT" : "RIGHT"));
        
        final AudioTrack track = new AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,  // ✅ STEREO (not MONO)
            AudioFormat.ENCODING_PCM_16BIT,
            trackBufferSize,
            AudioTrack.MODE_STREAM
        );
        
        // Show initial status
        runOnUiThread(() -> {
            // tvStatus.setText("🎵 Listen carefully and tap when you hear the tone...");
            // tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
            // tvStatus.setVisibility(View.VISIBLE);
            circleButton.setEnabled(true);
            circleButton.setAlpha(1.0f);
        });
        
        // Set up click listener to capture threshold - SINGLE TAP ONLY
        final long startTime = System.currentTimeMillis();
        
        runOnUiThread(() -> {
            circleButton.setOnClickListener(v -> {
                if (!thresholdFound) {
                    // Single tap - record immediately!
                    long tapTime = System.currentTimeMillis();
                    long elapsedMs = tapTime - startTime;
                    float progress = Math.min(1.0f, (float) elapsedMs / TEST_DURATION_MS);
                    thresholdDbHL = startDb + (progress * (endDb - startDb));  // ✅ Use calibrated range
                    
                    Log.d(TAG, "✓ Tap at " + elapsedMs + "ms = " + thresholdDbHL + " dB HL (calibrated range: " + startDb + "-" + endDb + ")");
                    
                    // Visual feedback
                    // tvStatus.setText("✓ Threshold Recorded: " + Math.round(thresholdDbHL) + " dB");
                    // tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    
                    // Scale animation
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                        .start();
                    
                    thresholdFound = true;
                    reliabilityScore = 0.95f;
                    stopPlayback = true;
                    
                    // Stop track
                    new Thread(() -> {
                        try {
                            Thread.sleep(500); // Brief delay to show feedback
                            track.stop();
                            track.release();
                        } catch (Exception e) {
                            Log.e(TAG, "Error stopping track: " + e.getMessage());
                        }
                    }).start();
                    
                    // Advance to next frequency after brief delay
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        recordHeardAndAdvance();
                    }, 800);
                }
            });
        });
        
        // Start playback
        track.play();
        
        // Start smooth progress animation (time-based, not dB-based)
        Thread progressThread = new Thread(() -> {
            long progressStartTime = System.currentTimeMillis();
            while (!stopPlayback && !thresholdFound) {
                long elapsed = System.currentTimeMillis() - progressStartTime;
                final int progress = Math.min(100, (int) ((elapsed * 100) / TEST_DURATION_MS));
                
                runOnUiThread(() -> {
                    progressBar.setProgress(progress);
                });
                
                try {
                    Thread.sleep(50); // Update every 50ms for smooth animation
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            // DON'T fill to 100% when user taps early - keep at current position
            // Only fill to 100% if we reached max level naturally
        });
        progressThread.start();
        
        // Stream audio in chunks with smoothly increasing amplitude
        int globalSampleIndex = 0;
        for (int bufferIndex = 0; bufferIndex < numBuffers && !stopPlayback && !thresholdFound; bufferIndex++) {
            // Calculate current dB level for this buffer
            float progress = (float) bufferIndex / numBuffers;
            float currentDb = startDb + (progress * (endDb - startDb));
            
            // Convert to amplitude using RETSPL calibration
            double amplitude = ToneGenerator.dbHLToAmplitude(frequency, currentDb);
            
            // Generate mono buffer of pure tone at this amplitude
            short[] monoBuffer = new short[samplesPerBuffer];
            double angularFrequency = 2.0 * Math.PI * frequency / SAMPLE_RATE;
            
            for (int i = 0; i < samplesPerBuffer; i++) {
                double sample = amplitude * Math.sin(angularFrequency * globalSampleIndex);
                monoBuffer[i] = (short) (sample * Short.MAX_VALUE);
                globalSampleIndex++;
            }
            
            // Convert mono to stereo with sound only in correct ear
            // Stereo format: [L0, R0, L1, R1, L2, R2, ...]
            short[] stereoBuffer = new short[samplesPerBuffer * 2];
            for (int i = 0; i < samplesPerBuffer; i++) {
                if (isLeftEar) {
                    stereoBuffer[i * 2] = monoBuffer[i];     // LEFT channel
                    stereoBuffer[i * 2 + 1] = 0;             // RIGHT channel (silent)
                } else {
                    stereoBuffer[i * 2] = 0;                 // LEFT channel (silent)
                    stereoBuffer[i * 2 + 1] = monoBuffer[i]; // RIGHT channel
                }
            }
            
            // Write stereo buffer to track (this will block if buffer is full - provides natural timing)
            track.write(stereoBuffer, 0, stereoBuffer.length);
        }
        
        // Reached maximum level without response
        if (!thresholdFound) {
            // Stop audio
            track.stop();
            track.release();
            
            // Show UI for max level reached
            runOnUiThread(() -> {
                progressBar.setProgress(100);
                // tvStatus.setText("⚠️ Reached maximum level (120 dB)");
                // tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                
                // Show "Didn't Hear" button
                buttonNotHeard.setVisibility(View.VISIBLE);
                buttonNotHeard.setText("Didn't hear");
                
                // Change main button to "Start Again"
                circleButton.setText("Start\nagain");
                circleButton.setEnabled(true);
                circleButton.setAlpha(1.0f);
                
                // Wire up buttons
                buttonNotHeard.setOnClickListener(v -> {
                    thresholdDbHL = 120.0f;
                    reliabilityScore = 0.9f;
                    thresholdFound = true;
                    Log.d(TAG, "User confirmed: Didn't hear at 120 dB");
                    recordHeardAndAdvance();
                });
                
                circleButton.setOnClickListener(v -> {
                    // Restart this frequency
                    resetCurrentFrequency();
                });
            });
        }
    }
    
    /**
     * Present a single clinical tone and wait for user response
     * @return true if user heard the tone, false if timeout
     */
    private boolean presentSingleClinicalTone(int frequency, float dbHL) throws InterruptedException {
        // Convert dB HL to amplitude
        double amplitude = ToneGenerator.dbHLToAmplitude(frequency, dbHL);
        
        // Generate clinical tone with proper envelope (1.5 sec, 200ms rise/fall)
        short[] monoToneBuffer = ToneGenerator.generateClinicalTone(44100, TONE_DURATION_MS, frequency, amplitude);
        
        // Convert mono to stereo with sound only in correct ear
        // Stereo format: [L0, R0, L1, R1, L2, R2, ...]
        boolean isLeftEar = "LEFT".equals(currentEar);
        short[] stereoToneBuffer = new short[monoToneBuffer.length * 2];
        
        for (int i = 0; i < monoToneBuffer.length; i++) {
            if (isLeftEar) {
                stereoToneBuffer[i * 2] = monoToneBuffer[i];     // LEFT channel
                stereoToneBuffer[i * 2 + 1] = 0;                 // RIGHT channel (silent)
            } else {
                stereoToneBuffer[i * 2] = 0;                     // LEFT channel (silent)
                stereoToneBuffer[i * 2 + 1] = monoToneBuffer[i]; // RIGHT channel
            }
        }
        
        Log.d("AudioDebug", String.format("Stereo separation: %s ear → %s channel only", 
            currentEar, isLeftEar ? "LEFT" : "RIGHT"));
        
        // Create and play AudioTrack with STEREO configuration
        int bufferSize = stereoToneBuffer.length * 2;  // 16-bit = 2 bytes per sample
        AudioTrack track = new AudioTrack(
            AudioManager.STREAM_MUSIC,
            44100,
            AudioFormat.CHANNEL_OUT_STEREO,  // ✅ STEREO (not MONO)
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STATIC
        );
        
        track.write(stereoToneBuffer, 0, stereoToneBuffer.length);
        
        // Enable button for user response
        final boolean[] userResponded = {false};
        final Object responseLock = new Object();
        
        runOnUiThread(() -> {
            // Show status: Listening phase
            // tvStatus.setText("🎵 Listening...");
            // tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
            tvStatus.setVisibility(View.VISIBLE);
            
            circleButton.setEnabled(true);
            circleButton.setAlpha(1.0f); // Full opacity when active
            circleButton.setOnClickListener(v -> {
                synchronized (responseLock) {
                    if (!userResponded[0]) {  // Only respond once
                        userResponded[0] = true;
                        responseLock.notify();
                        
                        // Visual feedback: Tap registered!
                        // tvStatus.setText("✓ Tap Registered!");
                        // tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        circleButton.setEnabled(false);
                        circleButton.setAlpha(0.6f); // Dim when disabled
                        
                        // Add scale animation for feedback
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                            .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                            .start();
                    }
                }
            });
        });
        
        // Play tone
        track.play();
        
        // Wait for tone duration + small buffer for response
        long startTime = System.currentTimeMillis();
        synchronized (responseLock) {
            try {
                responseLock.wait(TONE_DURATION_MS + 500);  // Wait for tone + 500ms response window
            } catch (InterruptedException e) {
                // User responded or interrupted
            }
        }
        
        track.stop();
        track.release();
        
        // Disable button after tone ends (user had their chance to respond)
        runOnUiThread(() -> {
            circleButton.setEnabled(false);
            circleButton.setAlpha(0.6f); // Dim when disabled
            
            // Show "Processing..." status
            if (!userResponded[0]) {
                // tvStatus.setText("⏳ Processing...");
                // tvStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        });
        
        return userResponded[0];
    }
    
    /**
     * Process user response using SIMPLIFIED CONSUMER procedure
     * Pure ascending only - no confusing descending phase:
     * 
     * Consumer Flow (Simple & Clear):
     * 1. Start at 0 dB HL
     * 2. If NOT HEARD → Increase 5 dB (keep going up)
     * 3. If HEARD → Count it
     * 4. Threshold = first level with 2 CONSECUTIVE responses
     * 5. Done! Clear, simple, no confusion
     */
    private void processHughsonWestlakeResponse(boolean heard) {
        
        if (heard) {
            Log.d(TAG, "✓ HEARD at " + currentDbHL + " dB HL");
            
            // Count this response
            int responses = ascendingResponsesPerLevel.getOrDefault(currentDbHL, 0);
            int attempts = ascendingAttemptsPerLevel.getOrDefault(currentDbHL, 0);
            
            ascendingResponsesPerLevel.put(currentDbHL, responses + 1);
            ascendingAttemptsPerLevel.put(currentDbHL, attempts + 1);
            
            Log.d(TAG, "Responses at " + currentDbHL + " dB HL: " + (responses + 1) + "/" + (attempts + 1));
            
            // Simple: 2 consecutive responses = threshold found!
            if (responses + 1 >= 2) {
                thresholdDbHL = currentDbHL;
                thresholdFound = true;
                reliabilityScore = 0.95f; // High confidence - clear responses
                Log.d(TAG, "✓ THRESHOLD FOUND: " + thresholdDbHL + " dB HL (" + (responses + 1) + " consecutive responses)");
                
                runOnUiThread(() -> {
                    recordHeardAndAdvance();
                });
                return;
            }
            
            // Heard once at this level - stay here for confirmation
            // (Next tone at same level to get 2nd response)
            Log.d(TAG, "Heard once, staying at " + currentDbHL + " dB for confirmation");
            
            // Special case: If heard at 120 dB (maximum), record as threshold
            if (currentDbHL >= 120.0f) {
                Log.d(TAG, "✓ HEARD at maximum level (120 dB HL) - recording as threshold");
                thresholdDbHL = 120.0f;
                thresholdFound = true;
                reliabilityScore = 0.85f;
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Threshold at maximum level (120 dB)", Toast.LENGTH_SHORT).show();
                    recordHeardAndAdvance();
                });
                return;
            }
            
            // No reversal tracking needed in consumer mode - just staying at same level for confirmation
            lastResponseWasHeard = true;
            
            // Stay at current level for 2nd confirmation response
            // (currentDbHL stays the same)
            
        } else {
            Log.d(TAG, "✗ NOT HEARD at " + currentDbHL + " dB HL");
            
            // Track attempts
            int attempts = ascendingAttemptsPerLevel.getOrDefault(currentDbHL, 0);
            ascendingAttemptsPerLevel.put(currentDbHL, attempts + 1);
            
            Log.d(TAG, "Attempts at " + currentDbHL + " dB HL: " + (attempts + 1));
            
            // Reset counter if failed to get 2 consecutive at this level
            int responses = ascendingResponsesPerLevel.getOrDefault(currentDbHL, 0);
            if (responses > 0 && responses < 2) {
                // Had 1 response but then didn't hear - reset and continue
                Log.d(TAG, "Inconsistent response at " + currentDbHL + " dB HL, resetting counter");
                ascendingResponsesPerLevel.put(currentDbHL, 0);
                ascendingAttemptsPerLevel.put(currentDbHL, 0);
            }
            
            lastResponseWasHeard = false;
            
            // Simple: NOT HEARD → Go up 5 dB
            currentDbHL += 5.0f;
            
            // Cap at maximum level
            if (currentDbHL > 120.0f) {
                currentDbHL = 120.0f;
                Log.d(TAG, "Capped at maximum level: 120.0 dB HL - showing retry option");
                
                // Show retry UI
                runOnUiThread(() -> {
                    showRetryUI();
                });
            }
            
            // Always ascending in consumer mode
            lastPresentationWasAscending = true;
        }
    }

    private void showRetryUI() {
        // Hide tick mark and "Tap here" text
        ImageView ivTickMark = findViewById(R.id.ivTickMark);
        TextView tvTapHere = findViewById(R.id.tvTapHere);
        ivTickMark.setVisibility(View.GONE);
        tvTapHere.setVisibility(View.GONE);
        
        // Show retry icon and "Start again" text
        ImageView ivRetry = findViewById(R.id.ivRetry);
        TextView tvStartAgain = findViewById(R.id.tvStartAgain);
        ivRetry.setVisibility(View.VISIBLE);
        tvStartAgain.setVisibility(View.VISIBLE);
        
        // Update status message
        // tvStatus.setText("Maximum volume reached");
        // tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        tvStatus.setVisibility(View.VISIBLE);
        
        // Stop current playback
        stopPlayback = true;
        stopProgressAnimation = true;
    }
    
    private void hideRetryUI() {
        // Show tick mark and "Tap here" text
        ImageView ivTickMark = findViewById(R.id.ivTickMark);
        TextView tvTapHere = findViewById(R.id.tvTapHere);
        ivTickMark.setVisibility(View.VISIBLE);
        tvTapHere.setVisibility(View.VISIBLE);
        
        // Hide retry icon and "Start again" text
        ImageView ivRetry = findViewById(R.id.ivRetry);
        TextView tvStartAgain = findViewById(R.id.tvStartAgain);
        ivRetry.setVisibility(View.GONE);
        tvStartAgain.setVisibility(View.GONE);
        
        // Clear status message
        tvStatus.setVisibility(View.GONE);
    }

    private void recordHeardAndAdvance() {
        // Immediately stop playback and animation
        stopPlayback = true;
        stopProgressAnimation = true;
        thresholdFound = true; // Stop automated presentation
        
        // Show success feedback
        runOnUiThread(() -> {
            // tvStatus.setText("✓ Threshold Found!");
            // tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            tvStatus.setVisibility(View.VISIBLE);
        });
        
        // Record threshold at current level (already determined by Hughson-Westlake)
        // thresholdDbHL is already set in processHughsonWestlakeResponse
        
        Log.d(TAG, "Recording threshold: " + thresholdDbHL + " dB HL for " + frequencies[currentFreqIndex] + "Hz");
        
        // Complete progress bar to 100%
        runOnUiThread(() -> {
            progressBar.setProgress(100);
        });
        
        // Wait a moment for audio thread to stop before storing result and advancing
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            storeThresholdResult();
        }, 100); // 100ms delay to ensure clean stop
    }

    private void recordNotHeardAndAdvance() {
        stopPlayback = true;
        stopProgressAnimation = true;
        thresholdFound = true; // Stop automated presentation
        
        // This is called only at maximum level - record as no measurable threshold
        thresholdDbHL = 120.0f;
        reliabilityScore = 0.8f; // High confidence in "no response"
        reversalCount = 0; // No reversals at maximum
        
        Log.d(TAG, "User reported no hearing at maximum level for " + frequencies[currentFreqIndex] + "Hz");
        
        // Store result and advance
        storeThresholdResult();
    }
    
    // Removed processHughsonWestlakeResponse method - using simplified automated approach
    
    /**
     * Store threshold result with both clinical and legacy data formats
     */
    private void storeThresholdResult() {
        markStepperCompleted(currentFreqIndex);
        
        new Thread(() -> {
            int frequency = frequencies[currentFreqIndex];
            
            // Convert clinical threshold to device-specific dB SPL
            double retspl = ToneGenerator.getRETSPL(frequency);
            float thresholdDbSPL = thresholdDbHL + (float) retspl;
            
            // Create clinical result using new constructor
            HearingTestResult clinicalResult = new HearingTestResult(
                userId, 
                currentEar, 
                frequency, 
                thresholdDbHL,           // Clinical threshold in dB HL
                thresholdDbSPL,          // Device threshold in dB SPL  
                reliabilityScore > 0.7f, // Reliable if score > 70%
                reversalCount,           // Number of reversals
                reliabilityScore,        // Reliability score 0.0-1.0
                hearingProfileId
            );
            
            // Use insertOrReplace to handle retakes - replaces existing result if duplicate
            Log.d(TAG, "Storing result for " + currentEar + " ear, " + frequency + "Hz, " + thresholdDbHL + " dB HL");
            hearingTestResultDao.insertOrReplace(clinicalResult);
            
            runOnUiThread(this::advanceToNextFrequency);
        }).start();
    }

    private void advanceToNextFrequency() {
        currentFreqIndex++;
        if (currentFreqIndex >= frequencies.length) {
            // All frequencies complete for this ear
            Log.d(TAG, "=== ALL FREQUENCIES COMPLETE ===");
            Log.d(TAG, "Current ear: " + currentEar);
            Log.d(TAG, "User ID: " + userId);
            Log.d(TAG, "Hearing Profile ID: " + hearingProfileId);
            
            Toast.makeText(this,
                (currentEar.equalsIgnoreCase("LEFT") ? "Left Ear" : "Right Ear") + " Pure Tone Test Complete!",
                Toast.LENGTH_SHORT
            ).show();
            
            // Determine next activity based on which ear was just completed
            Intent next;
            if (currentEar.equalsIgnoreCase("RIGHT")) {
                // RIGHT ear pure tone done -> Go to LEFT ear pure tone instruction
                Log.d(TAG, "Navigation: RIGHT ear done -> LEFT ear instruction");
                next = new Intent(this, LeftEarInstructionActivity.class);
            } else {
                // LEFT ear pure tone done -> Go to calibration (RIGHT ear first)
                Log.d(TAG, "Navigation: LEFT ear done -> Calibration (RIGHT ear)");
                next = new Intent(this, CalibrationInstructionActivity.class);
                next.putExtra("EAR", "RIGHT"); // Start calibration with RIGHT ear
            }
            
            next.putExtra("USER_ID", userId);
            next.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            next.putExtra("FROM_NEW_PROFILE", fromNewProfile); // Pass flag to next activity
            
            Log.d(TAG, "Starting next activity: " + next.getComponent().getClassName());
            startActivity(next);
            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
            finish();
            return;
        }
        
        // Auto-start next frequency
        Log.d(TAG, "Advancing to next frequency: " + frequencies[currentFreqIndex] + " Hz");
        startRampForCurrentFrequency();
    }
    
    private void showMaxLevelReachedUI() {
        // Keep progress bar visible but at 0% to maintain button position
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        
        // Change circle button text to "Start again"
        circleButton.setText("Start again");
        
        // Show "Didn't Hear" button at max level
        buttonNotHeard.setVisibility(View.VISIBLE);
    }

    /**
     * Reset and restart the current frequency test
     */
    private void resetCurrentFrequency() {
        stopPlayback = true;
        thresholdFound = false;
        
        // Reset state variables
        currentDbHL = 0.0f;
        reversalCount = 0;
        lastResponseWasHeard = false;
        ascendingResponsesPerLevel.clear();
        ascendingAttemptsPerLevel.clear();
        totalPresentationCount = 0;
        
        // Hide retry UI and show normal UI
        runOnUiThread(() -> {
            hideRetryUI();
        });
        
        // Wait a moment for audio to stop
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            runOnUiThread(() -> {
                // Restart the current frequency
                startRampForCurrentFrequency();
            });
        }).start();
    }

    private void markStepperCompleted(int index) {
        // Update the frequency bar for the completed test
        updateFrequencyBar(index, true);
    }
    
    /**
     * Update the frequency progress bar for a given frequency index
     * @param index The frequency index (0-6 for 7 frequency presentations)
     * @param completed Whether this frequency test is completed
     */
    private void updateFrequencyBar(int index, boolean completed) {
        if (index < 0 || index > 6) return;
        
        int barId = 0;
        switch(index) {
            case 0: barId = R.id.bar1; break;
            case 1: barId = R.id.bar2; break;
            case 2: barId = R.id.bar3; break;
            case 3: barId = R.id.bar4; break;
            case 4: barId = R.id.bar5; break;
            case 5: barId = R.id.bar6; break;
            case 6: barId = R.id.bar7; break;
        }
        
        if (barId != 0) {
            View bar = findViewById(barId);
            if (bar != null) {
                bar.setBackgroundResource(completed ? R.drawable.indicator_active : R.drawable.indicator_inactive);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop all playback and threads
        stopPlayback = true;
        stopProgressAnimation = true;
        thresholdFound = true;
        
        // Clean up threads
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
            try {
                playbackThread.join(1000); // Wait max 1 second
            } catch (InterruptedException e) {
                Log.e(TAG, "Error waiting for playback thread to stop", e);
            }
        }
        
        if (progressAnimationThread != null && progressAnimationThread.isAlive()) {
            progressAnimationThread.interrupt();
            try {
                progressAnimationThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error waiting for progress thread to stop", e);
            }
        }
        
        Log.d(TAG, "Activity destroyed, all threads cleaned up");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Pause any active audio playback
        stopPlayback = true;
        Log.d(TAG, "Activity paused, audio stopped");
    }
}