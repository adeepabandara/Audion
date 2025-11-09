package com.audion.audio;

import android.util.Log;

/**
 * Lightweight scene analyzer for adaptive audio processing.
 * 
 * Analyzes incoming audio to detect listening environment:
 * - QUIET: Low RMS, low spectral activity
 * - SPEECH: Moderate RMS, mid-frequency energy dominance
 * - NOISE: High RMS, broadband energy
 * - MUSIC: Moderate-high RMS, harmonic structure
 * 
 * Analysis runs every 500ms using:
 * - Short-term RMS (energy level)
 * - Spectral centroid (frequency distribution)
 * - Zero-crossing rate (periodicity indicator)
 * 
 * Based on scene, adjusts:
 * - RNNoise aggressiveness
 * - Compression ratios
 * - Frequency emphasis
 */
public class SceneAnalyzer {
    private static final String TAG = "SceneAnalyzer";
    
    // Analysis parameters
    private static final int ANALYSIS_INTERVAL_MS = 500;     // Analyze every 500ms
    private static final int SAMPLE_RATE = 48000;
    private static final int ANALYSIS_INTERVAL_SAMPLES = (ANALYSIS_INTERVAL_MS * SAMPLE_RATE) / 1000;
    
    // Scene detection thresholds
    private static final float QUIET_RMS_THRESHOLD = -50.0f;      // dBFS
    private static final float SPEECH_RMS_MIN = -45.0f;           // dBFS
    private static final float SPEECH_RMS_MAX = -20.0f;           // dBFS
    private static final float NOISE_RMS_THRESHOLD = -15.0f;      // dBFS
    
    private static final float SPEECH_CENTROID_MIN = 1000.0f;     // Hz
    private static final float SPEECH_CENTROID_MAX = 3000.0f;     // Hz
    private static final float MUSIC_CENTROID_MIN = 500.0f;       // Hz
    private static final float MUSIC_CENTROID_MAX = 4000.0f;      // Hz
    
    /**
     * Detected scene types.
     */
    public enum Scene {
        QUIET,    // Background noise, minimal activity
        SPEECH,   // Speech-dominated environment
        NOISE,    // High-level broadband noise
        MUSIC     // Music or tonal content
    }
    
    // Analysis state
    private float[] analysisBuffer;
    private int bufferIndex = 0;
    private Scene currentScene = Scene.QUIET;
    private long lastAnalysisTime = 0;
    
    // Scene statistics
    private float currentRMS = -60.0f;
    private float currentCentroid = 1500.0f;
    private float currentZCR = 0.0f;
    
    // Scene history for smoothing
    private static final int HISTORY_LENGTH = 3;
    private Scene[] sceneHistory = new Scene[HISTORY_LENGTH];
    private int historyIndex = 0;
    
    /**
     * Create scene analyzer.
     */
    public SceneAnalyzer() {
        analysisBuffer = new float[ANALYSIS_INTERVAL_SAMPLES];
        
        // Initialize history with QUIET
        for (int i = 0; i < HISTORY_LENGTH; i++) {
            sceneHistory[i] = Scene.QUIET;
        }
        
        Log.i(TAG, String.format("SceneAnalyzer initialized: %d ms intervals (%d samples)",
            ANALYSIS_INTERVAL_MS, ANALYSIS_INTERVAL_SAMPLES));
    }
    
    /**
     * Process audio samples and update scene detection.
     * 
     * @param input Input audio samples (normalized ±1.0)
     * @param length Number of samples
     * @return Current detected scene (may update periodically)
     */
    public Scene process(float[] input, int length) {
        // Accumulate samples into analysis buffer
        for (int i = 0; i < length; i++) {
            analysisBuffer[bufferIndex] = input[i];
            bufferIndex++;
            
            // When buffer is full, perform analysis
            if (bufferIndex >= ANALYSIS_INTERVAL_SAMPLES) {
                analyzeScene();
                bufferIndex = 0;
            }
        }
        
        return currentScene;
    }
    
    /**
     * Analyze accumulated audio and classify scene.
     */
    private void analyzeScene() {
        // Calculate RMS
        currentRMS = calculateRMS(analysisBuffer, ANALYSIS_INTERVAL_SAMPLES);
        
        // Calculate spectral centroid (simplified using time-domain features)
        currentCentroid = estimateCentroid(analysisBuffer, ANALYSIS_INTERVAL_SAMPLES);
        
        // Calculate zero-crossing rate
        currentZCR = calculateZCR(analysisBuffer, ANALYSIS_INTERVAL_SAMPLES);
        
        // Classify scene based on features
        Scene detectedScene = classifyScene(currentRMS, currentCentroid, currentZCR);
        
        // Update scene history
        sceneHistory[historyIndex] = detectedScene;
        historyIndex = (historyIndex + 1) % HISTORY_LENGTH;
        
        // Use majority vote for stability
        Scene previousScene = currentScene;
        currentScene = getMajorityScene();
        
        // Log scene changes
        long currentTime = System.currentTimeMillis();
        if (currentScene != previousScene || (currentTime - lastAnalysisTime) > 5000) {
            Log.i(TAG, String.format("Scene: %s → %s (RMS=%.1f dBFS, Centroid=%.0f Hz, ZCR=%.3f)",
                previousScene, currentScene, currentRMS, currentCentroid, currentZCR));
            lastAnalysisTime = currentTime;
        }
    }
    
    /**
     * Calculate RMS in dBFS.
     */
    private float calculateRMS(float[] buffer, int length) {
        float sumSquares = 0.0f;
        for (int i = 0; i < length; i++) {
            sumSquares += buffer[i] * buffer[i];
        }
        float rms = (float) Math.sqrt(sumSquares / length);
        
        // Convert to dBFS
        if (rms < 1e-6f) {
            return -100.0f; // Floor
        }
        return (float) (20.0 * Math.log10(rms));
    }
    
    /**
     * Estimate spectral centroid using simplified time-domain method.
     * Uses magnitude spectrum approximation via Fourier-like energy distribution.
     */
    private float estimateCentroid(float[] buffer, int length) {
        // Simplified: Use zero-crossing rate and energy distribution
        // High ZCR → high centroid, low ZCR → low centroid
        
        // Calculate energy in different time scales (proxy for frequency)
        float shortTermEnergy = 0.0f;  // Fast changes (high freq)
        float longTermEnergy = 0.0f;   // Slow changes (low freq)
        
        for (int i = 1; i < length; i++) {
            float diff = buffer[i] - buffer[i-1];
            shortTermEnergy += diff * diff;
            longTermEnergy += buffer[i] * buffer[i];
        }
        
        // Ratio indicates spectral balance
        float ratio = (longTermEnergy > 0) ? (shortTermEnergy / longTermEnergy) : 0.0f;
        
        // Map ratio to frequency estimate (empirical mapping)
        float centroidEstimate = 500.0f + ratio * 3000.0f;
        
        // Clamp to reasonable range
        return Math.max(250.0f, Math.min(centroidEstimate, 6000.0f));
    }
    
    /**
     * Calculate zero-crossing rate (normalized).
     */
    private float calculateZCR(float[] buffer, int length) {
        int zeroCrossings = 0;
        for (int i = 1; i < length; i++) {
            if ((buffer[i-1] >= 0 && buffer[i] < 0) || (buffer[i-1] < 0 && buffer[i] >= 0)) {
                zeroCrossings++;
            }
        }
        return (float) zeroCrossings / length;
    }
    
    /**
     * Classify scene based on audio features.
     */
    private Scene classifyScene(float rms, float centroid, float zcr) {
        // QUIET: Very low RMS
        if (rms < QUIET_RMS_THRESHOLD) {
            return Scene.QUIET;
        }
        
        // NOISE: High RMS, broadband
        if (rms > NOISE_RMS_THRESHOLD) {
            return Scene.NOISE;
        }
        
        // SPEECH: Moderate RMS, mid-frequency centroid
        if (rms >= SPEECH_RMS_MIN && rms <= SPEECH_RMS_MAX) {
            if (centroid >= SPEECH_CENTROID_MIN && centroid <= SPEECH_CENTROID_MAX) {
                return Scene.SPEECH;
            }
        }
        
        // MUSIC: Moderate RMS, harmonic structure (broader centroid range)
        if (rms >= -40.0f && rms <= -15.0f) {
            if (centroid >= MUSIC_CENTROID_MIN && centroid <= MUSIC_CENTROID_MAX) {
                // Use ZCR to distinguish from speech (music has more periodicity)
                if (zcr < 0.15f) { // Lower ZCR indicates more tonal content
                    return Scene.MUSIC;
                }
            }
        }
        
        // Default: SPEECH (most common use case)
        return Scene.SPEECH;
    }
    
    /**
     * Get majority scene from history (for stability).
     */
    private Scene getMajorityScene() {
        int[] counts = new int[Scene.values().length];
        
        for (Scene scene : sceneHistory) {
            counts[scene.ordinal()]++;
        }
        
        // Find max count
        int maxCount = 0;
        Scene majorityScene = Scene.QUIET;
        for (Scene scene : Scene.values()) {
            if (counts[scene.ordinal()] > maxCount) {
                maxCount = counts[scene.ordinal()];
                majorityScene = scene;
            }
        }
        
        return majorityScene;
    }
    
    /**
     * Get current detected scene.
     */
    public Scene getCurrentScene() {
        return currentScene;
    }
    
    /**
     * Get current RMS level in dBFS.
     */
    public float getCurrentRMS() {
        return currentRMS;
    }
    
    /**
     * Get current spectral centroid estimate in Hz.
     */
    public float getCurrentCentroid() {
        return currentCentroid;
    }
    
    /**
     * Get recommended compression ratio for current scene.
     */
    public float getRecommendedCompressionRatio() {
        switch (currentScene) {
            case QUIET:
                return 1.5f;  // Minimal compression (preserve dynamics)
            case SPEECH:
                return 2.5f;  // Moderate compression (intelligibility)
            case NOISE:
                return 3.5f;  // Aggressive compression (comfort in noise)
            case MUSIC:
                return 2.0f;  // Gentle compression (preserve music quality)
            default:
                return 2.5f;
        }
    }
    
    /**
     * Get recommended RNNoise aggressiveness (0.0 - 1.0).
     */
    public float getRecommendedNoiseReduction() {
        switch (currentScene) {
            case QUIET:
                return 0.3f;  // Low (avoid over-processing)
            case SPEECH:
                return 0.7f;  // Moderate-high (enhance speech)
            case NOISE:
                return 1.0f;  // Maximum (aggressive noise suppression)
            case MUSIC:
                return 0.2f;  // Minimal (preserve musical details)
            default:
                return 0.5f;
        }
    }
    
    /**
     * Reset analyzer state.
     */
    public void reset() {
        bufferIndex = 0;
        currentScene = Scene.QUIET;
        currentRMS = -60.0f;
        currentCentroid = 1500.0f;
        currentZCR = 0.0f;
        
        for (int i = 0; i < HISTORY_LENGTH; i++) {
            sceneHistory[i] = Scene.QUIET;
        }
        historyIndex = 0;
        
        Log.i(TAG, "SceneAnalyzer reset");
    }
}
