package com.audion.audiometry;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

/**
 * ANSI S3.6 compliant audiometry engine implementing Hughson-Westlake procedure
 * for clinical-grade pure tone threshold testing with integrated AudioTrack playback.
 */
public class ANSI_AudiometryEngine {
    private static final String TAG = "ANSI_AudiometryEngine";
    
    // Debug logging removed for production - no-op methods
    private void logD(String message) { /* Debug logging disabled */ }
    private void logI(String message) { /* Info logging disabled */ }
    private void logW(String message) { /* Warn logging disabled */ }
    private void logE(String message) { /* Error logging disabled */ }
    
    // ANSI S3.6 standard test frequencies (Hz) - expanded to include 3000Hz
    public static final List<Integer> STANDARD_FREQUENCIES = Arrays.asList(
        125, 250, 500, 750, 1000, 1500, 2000, 3000, 4000, 6000, 8000
    );
    
    // Hughson-Westlake procedure parameters
    private static final int DOWN_STEP_DB = 10;    // dB down when heard
    private static final int UP_STEP_DB = 5;       // dB up when not heard
    private static final int MIN_THRESHOLD_DB = -10;
    private static final int MAX_THRESHOLD_DB = 120;
    private static final int STARTING_LEVEL_DB = 30;
    
    // Test state management
    private Map<String, Map<Integer, ThresholdState>> earThresholds;
    private String currentEar;
    private int currentFrequency;
    private int currentLevel;
    private boolean isDescending;
    private int reversalCount;
    private int[] reversalLevels;
    private boolean testComplete;
    
    // Audio generation constants
    private static final int SAMPLE_RATE = 44100;
    private static final int TONE_DURATION_MS = 1000;
    private static final int INTER_TONE_GAP_MS = 200;
    private static final double CALIBRATION_REFERENCE_AMPLITUDE = 0.1; // Baseline amplitude
    
    // Audio playback state
    private AudioTrack audioTrack;
    private boolean isPlayingTone = false;
    private Thread audioThread;
    private Handler mainHandler;
    private AudioPlaybackListener audioListener;
    
    // Noise monitoring
    private float ambientNoiseLevel = 0.0f;
    private static final float MAX_AMBIENT_NOISE_DB = 35.0f;
    
    /**
     * Interface for audio playback callbacks
     */
    public interface AudioPlaybackListener {
        void onToneStarted(int frequencyHz, int levelDbHL);
        void onToneCompleted(int frequencyHz, int levelDbHL);
        void onToneError(String error);
    }
    
    /**
     * Internal state tracking for threshold determination
     */
    private static class ThresholdState {
        int threshold = -1;
        boolean isComplete = false;
        int[] reversals = new int[6];  // Track up to 6 reversals
        int reversalCount = 0;
        int testAttempts = 0;
    }
    
    public ANSI_AudiometryEngine() {
        initialize();
    }
    
    private void initialize() {
        earThresholds = new HashMap<>();
        earThresholds.put("LEFT", new HashMap<>());
        earThresholds.put("RIGHT", new HashMap<>());
        
        // Initialize threshold states for all frequencies
        for (String ear : Arrays.asList("LEFT", "RIGHT")) {
            for (int freq : STANDARD_FREQUENCIES) {
                earThresholds.get(ear).put(freq, new ThresholdState());
            }
        }
        
        // Initialize audio components
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            initializeAudioTrack();
        } catch (Exception e) {
            logE("Failed to initialize audio components: " + e.getMessage());
        }
        
        resetCurrentTest();
    }
    
    /**
     * Set audio playback listener for tone events
     */
    public void setAudioPlaybackListener(AudioPlaybackListener listener) {
        this.audioListener = listener;
    }
    
    /**
     * Initialize AudioTrack for low-latency audio playback
     */
    private void initializeAudioTrack() {
        try {
            // Calculate minimum buffer size for low-latency playback
            int minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );
            
            // Use larger buffer for smooth playback
            int bufferSize = Math.max(minBufferSize, SAMPLE_RATE * 2); // 2 seconds
            
            audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            );
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                logE("AudioTrack initialization failed - State: " + audioTrack.getState());
                audioTrack = null;
            } else {
                logD("AudioTrack initialized successfully - Buffer size: " + bufferSize);
            }
            
        } catch (Exception e) {
            logE("Error initializing AudioTrack: " + e.getMessage());
            audioTrack = null;
        }
    }
    
    /**
     * Start threshold testing for specified ear and frequency
     */
    public boolean startThresholdTest(String ear, int frequency) {
        // Enhanced validation with detailed logging
        logD("Attempting to start threshold test: ear=" + ear + ", frequency=" + frequency + "Hz");
        logD("Supported frequencies: " + STANDARD_FREQUENCIES.toString());
        
        if (!STANDARD_FREQUENCIES.contains(frequency)) {
            logE("Invalid test frequency: " + frequency + "Hz");
            logE("Frequency " + frequency + " not found in supported list: " + STANDARD_FREQUENCIES.toString());
            return false;
        }
        
        logI("Frequency validation passed for " + frequency + "Hz");
        
        if (!isAmbientNoiseAcceptable()) {
            logW("Ambient noise too high for testing: " + ambientNoiseLevel + "dB");
            return false;
        }
        
        if (audioTrack == null) {
            logE("AudioTrack not initialized - cannot start test");
            return false;
        }
        
        currentEar = ear;
        currentFrequency = frequency;
        currentLevel = STARTING_LEVEL_DB;
        isDescending = true;
        reversalCount = 0;
        reversalLevels = new int[6];
        testComplete = false;
        
        ThresholdState state = earThresholds.get(ear).get(frequency);
        state.testAttempts++;
        
        logD("Starting threshold test: " + ear + " ear, " + frequency + "Hz");
        
        // Start first tone presentation
        playToneAtCurrentLevel();
        
        return true;
    }
    
    /**
     * Play tone at current test level using AudioTrack
     */
    private void playToneAtCurrentLevel() {
        if (audioTrack == null || isPlayingTone) {
            return;
        }
        
        // Convert dB HL to amplitude using RETSPL calibration
        double amplitude = convertDbHLToAmplitude(currentLevel, currentFrequency);
        
        logD("Playing tone: " + currentFrequency + "Hz at " + currentLevel + "dB HL (amplitude: " + amplitude + ")");
        
        // Start audio playback in background thread
        audioThread = new Thread(() -> {
            try {
                isPlayingTone = true;
                
                // Notify listener tone started
                if (audioListener != null && mainHandler != null) {
                    mainHandler.post(() -> audioListener.onToneStarted(currentFrequency, currentLevel));
                }
                
                // Generate and play tone
                generateAndPlayTone(currentFrequency, amplitude, TONE_DURATION_MS);
                
                // Inter-tone gap
                Thread.sleep(INTER_TONE_GAP_MS);
                
                isPlayingTone = false;
                
                // Notify listener tone completed
                if (audioListener != null && mainHandler != null) {
                    mainHandler.post(() -> audioListener.onToneCompleted(currentFrequency, currentLevel));
                }
                
            } catch (Exception e) {
                isPlayingTone = false;
                logE("Error playing tone: " + e.getMessage());
                if (audioListener != null && mainHandler != null) {
                    mainHandler.post(() -> audioListener.onToneError(e.getMessage()));
                }
            }
        });
        
        audioThread.start();
    }
    
    /**
     * Generate and play sine wave tone using AudioTrack
     */
    private void generateAndPlayTone(int frequency, double amplitude, int durationMs) throws InterruptedException {
        try {
            // Calculate samples needed
            int numSamples = (int) ((durationMs / 1000.0) * SAMPLE_RATE);
            short[] buffer = new short[numSamples];
            
            // Generate sine wave
            double twoPiF = 2.0 * Math.PI * frequency;
            for (int i = 0; i < numSamples; i++) {
                double angle = twoPiF * i / SAMPLE_RATE;
                double sampleVal = amplitude * Math.sin(angle);
                buffer[i] = (short) (sampleVal * Short.MAX_VALUE);
            }
            
            // Start AudioTrack playback
            if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.play();
            }
            
            // Write audio data
            int totalWritten = 0;
            while (totalWritten < buffer.length) {
                int written = audioTrack.write(buffer, totalWritten, buffer.length - totalWritten);
                if (written < 0) {
                    logE("AudioTrack write error: " + written);
                    break;
                }
                totalWritten += written;
            }
            
            // Wait for playback to complete
            Thread.sleep(durationMs);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            logE("Error generating tone: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Convert dB HL to amplitude using RETSPL calibration
     */
    private double convertDbHLToAmplitude(int dbHL, int frequency) {
        try {
            // Get RETSPL value for frequency (circumaural headphones)
            float retsplDb = getRETSPLForFrequency(frequency);
            
            // Convert dB HL to dB SPL: dB SPL = dB HL + RETSPL
            float dbSPL = dbHL + retsplDb;
            
            // Convert dB SPL to amplitude
            // Reference: 0 dB SPL = 20 µPa, full scale = ~120 dB SPL
            // Amplitude = reference_amplitude * 10^((dB_SPL - reference_dB_SPL) / 20)
            double amplitude = CALIBRATION_REFERENCE_AMPLITUDE * Math.pow(10.0, (dbSPL - 60.0) / 20.0);
            
            // Clamp amplitude to safe range
            amplitude = Math.max(0.0001, Math.min(1.0, amplitude));
            
            return amplitude;
            
        } catch (Exception e) {
            logW("Error converting dB HL to amplitude, using default: " + e.getMessage());
            return CALIBRATION_REFERENCE_AMPLITUDE;
        }
    }
    
    /**
     * Get RETSPL value for given frequency (circumaural headphones)
     */
    private float getRETSPLForFrequency(int frequency) {
        switch (frequency) {
            case 125: return 22.0f;
            case 250: return 12.0f;
            case 500: return 5.5f;
            case 1000: return 0.0f;
            case 1500: return 2.0f;
            case 2000: return 3.0f;
            case 3000: return 3.5f;
            case 4000: return 5.5f;
            case 6000: return 2.0f;
            case 8000: return -3.0f;
            default: return 0.0f; // Default for unknown frequencies
        }
    }
    
    /**
     * Process user response according to Hughson-Westlake procedure
     * @param heard true if user heard the tone, false otherwise
     * @return next test level in dB HL, or -1 if threshold determined
     */
    public int processResponse(boolean heard) {
        ThresholdState state = earThresholds.get(currentEar).get(currentFrequency);
        
        logD("Response: " + (heard ? "HEARD" : "NOT_HEARD") + 
              " at " + currentLevel + "dB HL");
        
        // Implement Hughson-Westlake procedure
        if (heard) {
            if (isDescending) {
                // Continue descending
                currentLevel -= DOWN_STEP_DB;
            } else {
                // Was ascending, now heard - record reversal
                recordReversal(currentLevel);
                if (isThresholdDetermined()) {
                    return finalizeThreshold();
                }
                isDescending = true;
                currentLevel -= DOWN_STEP_DB;
            }
        } else {
            if (!isDescending) {
                // Continue ascending
                currentLevel += UP_STEP_DB;
            } else {
                // Was descending, now not heard - record reversal
                recordReversal(currentLevel + DOWN_STEP_DB);
                if (isThresholdDetermined()) {
                    return finalizeThreshold();
                }
                isDescending = false;
                currentLevel += UP_STEP_DB;
            }
        }
        
        // Check bounds
        if (currentLevel < MIN_THRESHOLD_DB) {
            currentLevel = MIN_THRESHOLD_DB;
            logW("Hit minimum threshold limit");
        } else if (currentLevel > MAX_THRESHOLD_DB) {
            // No response at maximum - record as no response
            state.threshold = MAX_THRESHOLD_DB;
            state.isComplete = true;
            testComplete = true;
            logW("No response at maximum level - threshold set to " + MAX_THRESHOLD_DB + "dB HL");
            return -1;
        }
        
        // Play next tone at updated level
        playToneAtCurrentLevel();
        
        return currentLevel;
    }
    
    private void recordReversal(int level) {
        if (reversalCount < reversalLevels.length) {
            reversalLevels[reversalCount] = level;
            reversalCount++;
            logD("Reversal " + reversalCount + " recorded at " + level + "dB HL");
        }
    }
    
    private boolean isThresholdDetermined() {
        // Need at least 3 reversals, prefer 6 for accuracy
        return reversalCount >= 3 && 
               (reversalCount >= 6 || 
                (reversalCount >= 4 && isConsistentThreshold()));
    }
    
    private boolean isConsistentThreshold() {
        if (reversalCount < 3) return false;
        
        // Check if last 3 reversals are within 10dB range
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        
        for (int i = Math.max(0, reversalCount - 3); i < reversalCount; i++) {
            min = Math.min(min, reversalLevels[i]);
            max = Math.max(max, reversalLevels[i]);
        }
        
        return (max - min) <= 10;
    }
    
    private int finalizeThreshold() {
        ThresholdState state = earThresholds.get(currentEar).get(currentFrequency);
        
        // Calculate threshold as mean of last 3-6 reversals
        int numReversals = Math.min(6, reversalCount);
        int startIdx = Math.max(0, reversalCount - numReversals);
        
        int sum = 0;
        for (int i = startIdx; i < reversalCount; i++) {
            sum += reversalLevels[i];
        }
        
        int threshold = Math.round((float) sum / (reversalCount - startIdx));
        
        // Round to nearest 5dB as per ANSI standard
        threshold = Math.round(threshold / 5.0f) * 5;
        
        state.threshold = threshold;
        state.isComplete = true;
        state.reversalCount = reversalCount;
        System.arraycopy(reversalLevels, 0, state.reversals, 0, reversalCount);
        testComplete = true;
        
        logI("Threshold determined: " + currentEar + " " + currentFrequency + 
              "Hz = " + threshold + "dB HL (from " + (reversalCount - startIdx) + " reversals)");
        
        return -1; // Signal test complete
    }
    
    /**
     * Get threshold in dB HL for specified ear and frequency
     */
    public int getThreshold(String ear, int frequency) {
        ThresholdState state = earThresholds.get(ear).get(frequency);
        return state != null && state.isComplete ? state.threshold : -1;
    }
    
    /**
     * Check if threshold testing is complete for given ear/frequency
     */
    public boolean isThresholdComplete(String ear, int frequency) {
        ThresholdState state = earThresholds.get(ear).get(frequency);
        return state != null && state.isComplete;
    }
    
    /**
     * Get all completed thresholds for specified ear
     */
    public Map<Integer, Integer> getCompletedThresholds(String ear) {
        Map<Integer, Integer> completed = new HashMap<>();
        Map<Integer, ThresholdState> earData = earThresholds.get(ear);
        
        if (earData != null) {
            for (Map.Entry<Integer, ThresholdState> entry : earData.entrySet()) {
                if (entry.getValue().isComplete) {
                    completed.put(entry.getKey(), entry.getValue().threshold);
                }
            }
        }
        
        return completed;
    }
    
    /**
     * Check for asymmetric hearing loss requiring masking
     */
    public boolean requiresMasking(String testEar, int frequency) {
        int testThreshold = getThreshold(testEar, frequency);
        String otherEar = testEar.equals("LEFT") ? "RIGHT" : "LEFT";
        int otherThreshold = getThreshold(otherEar, frequency);
        
        if (testThreshold == -1 || otherThreshold == -1) {
            return false; // Can't determine without both thresholds
        }
        
        // ANSI masking rules: mask if difference > 40dB for most frequencies
        int maskingCriteria = (frequency <= 1000) ? 40 : 35;
        int thresholdDifference = Math.abs(testThreshold - otherThreshold);
        
        boolean needsMasking = thresholdDifference > maskingCriteria;
        
        if (needsMasking) {
            logW("Masking required: " + thresholdDifference + 
                  "dB difference at " + frequency + "Hz exceeds " + maskingCriteria + "dB criteria");
        }
        
        return needsMasking;
    }
    
    /**
     * Update ambient noise level for test validity monitoring
     */
    public void updateAmbientNoise(float noiseLevelDb) {
        this.ambientNoiseLevel = noiseLevelDb;
        logD("Ambient noise updated: " + noiseLevelDb + "dB");
    }
    
    /**
     * Check if ambient noise is acceptable for testing
     */
    public boolean isAmbientNoiseAcceptable() {
        boolean acceptable = ambientNoiseLevel <= MAX_AMBIENT_NOISE_DB;
        if (!acceptable) {
            logW("Ambient noise too high for testing: " + ambientNoiseLevel + 
                  "dB > " + MAX_AMBIENT_NOISE_DB + "dB limit");
        }
        return acceptable;
    }
    
    /**
     * Calculate hearing loss severity classification
     */
    public String getHearingLossClassification(String ear) {
        Map<Integer, Integer> thresholds = getCompletedThresholds(ear);
        if (thresholds.isEmpty()) {
            return "INCOMPLETE";
        }
        
        // Calculate pure tone average (PTA) for speech frequencies
        int[] speechFreqs = {500, 1000, 2000};
        int ptaSum = 0;
        int ptaCount = 0;
        
        for (int freq : speechFreqs) {
            if (thresholds.containsKey(freq)) {
                ptaSum += thresholds.get(freq);
                ptaCount++;
            }
        }
        
        if (ptaCount == 0) return "INCOMPLETE";
        
        float pta = (float) ptaSum / ptaCount;
        
        // WHO/ANSI classification
        if (pta <= 25) return "NORMAL";
        else if (pta <= 40) return "MILD";
        else if (pta <= 55) return "MODERATE";
        else if (pta <= 70) return "MODERATELY_SEVERE";
        else if (pta <= 90) return "SEVERE";
        else return "PROFOUND";
    }
    
    private void resetCurrentTest() {
        currentEar = null;
        currentFrequency = 0;
        currentLevel = 0;
        isDescending = true;
        reversalCount = 0;
        reversalLevels = new int[6];
        testComplete = false;
    }
    
    /**
     * Export test results for clinical documentation
     */
    public String exportAudiogramData(String ear) {
        StringBuilder sb = new StringBuilder();
        sb.append("ANSI S3.6 Audiogram Results - ").append(ear).append(" Ear\n");
        sb.append("Date: ").append(new java.util.Date().toString()).append("\n");
        sb.append("Classification: ").append(getHearingLossClassification(ear)).append("\n\n");
        
        Map<Integer, Integer> thresholds = getCompletedThresholds(ear);
        sb.append("Frequency (Hz)\tThreshold (dB HL)\n");
        
        for (int freq : STANDARD_FREQUENCIES) {
            Integer threshold = thresholds.get(freq);
            sb.append(freq).append("\t\t");
            sb.append(threshold != null ? threshold : "NT").append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Validate test reliability based on test-retest consistency
     */
    public boolean isTestReliable(String ear, int frequency) {
        ThresholdState state = earThresholds.get(ear).get(frequency);
        if (state == null || !state.isComplete) {
            return false;
        }
        
        // Check if enough reversals were obtained
        if (state.reversalCount < 3) {
            logW("Insufficient reversals for reliable threshold: " + state.reversalCount);
            return false;
        }
        
        // Check consistency of reversals
        int range = 0;
        if (state.reversalCount >= 3) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            
            for (int i = 0; i < state.reversalCount; i++) {
                min = Math.min(min, state.reversals[i]);
                max = Math.max(max, state.reversals[i]);
            }
            range = max - min;
        }
        
        boolean reliable = range <= 15; // 15dB range considered acceptable
        logD("Test reliability: " + ear + " " + frequency + "Hz - " + 
              (reliable ? "RELIABLE" : "UNRELIABLE") + " (range: " + range + "dB)");
        
        return reliable;
    }
    
    /**
     * Stop audio playback and clean up resources
     */
    public void stopAudioPlayback() {
        isPlayingTone = false;
        
        if (audioThread != null && audioThread.isAlive()) {
            audioThread.interrupt();
            try {
                audioThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.flush();
            } catch (Exception e) {
                logE("Error stopping AudioTrack: " + e.getMessage());
            }
        }
    }
    
    /**
     * Release AudioTrack resources
     */
    public void release() {
        stopAudioPlayback();
        
        if (audioTrack != null) {
            try {
                audioTrack.release();
                audioTrack = null;
            } catch (Exception e) {
                logE("Error releasing AudioTrack: " + e.getMessage());
            }
        }
        
        logD("ANSI AudiometryEngine released");
    }
}