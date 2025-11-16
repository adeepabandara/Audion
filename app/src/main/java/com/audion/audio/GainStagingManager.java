package com.audion.audio;

import android.util.Log;

/**
 * GainStagingManager - Intelligent gain staging for high-amplification scenarios.
 * 
 * Manages the entire gain chain from user input to final output, ensuring:
 * - Clean amplification up to 40 dB (consumer hearing assistance safe limit)
 * - UCL-compliant output levels
 * - No hard clipping or distortion
 * - Frequency-dependent gain optimization
 * 
 * Pipeline:
 * User Slider (0-100) → Mapped to 0-40 dB → UCL Limiting → Per-Band Distribution → WDRC → Dual-Stage Limiting → Output
 */
public class GainStagingManager {
    private static final String TAG = "GainStagingManager";
    
    // Safety limits
    private float ABSOLUTE_MAX_GAIN_DB = 40.0f;   // Maximum safe gain (40 dB for MIC, 30 dB for MEDIA)
    private static final float SAFE_OUTPUT_HEADROOM_DB = 5.0f; // UCL - 5 dB safety margin
    private static final float REFERENCE_INPUT_SPL = 65.0f;    // Conversational speech level
    
    // Per-band gain distribution (5 bands)
    private static final int NUM_BANDS = 5;
    private final float[] bandGainFactors = {1.0f, 1.1f, 1.2f, 1.15f, 1.0f};  // Frequency weighting
    
    // UCL limits per ear
    private float leftUCL_dBSPL = 105.0f;   // Default UCL (safe conservative value)
    private float rightUCL_dBSPL = 105.0f;
    private boolean uclConfigured = false;  // Track if real UCL data has been set
    
    // Effective gains after UCL limiting
    private float leftEffectiveGainDb = 0.0f;
    private float rightEffectiveGainDb = 0.0f;
    
    // Per-band gains (dB)
    private final float[] leftBandGainsDb = new float[NUM_BANDS];
    private final float[] rightBandGainsDb = new float[NUM_BANDS];
    
    // Adaptive loudness control state
    private float targetRMS_dBFS = -12.0f;        // Target RMS for comfortable listening
    private float currentAdaptiveGain_dB = 0.0f;  // Adaptive gain adjustment
    private float rmsAccumulator = 0.0f;
    private int rmsFrameCount = 0;
    private static final int RMS_WINDOW_FRAMES = 200;  // ~2 seconds @ 10ms/frame
    
    // Statistics
    private long framesProcessed = 0;
    private float maxRequestedGain = 0.0f;
    private float maxEffectiveGain = 0.0f;
    
    public GainStagingManager() {
        // Initialize with unity gain
        for (int i = 0; i < NUM_BANDS; i++) {
            leftBandGainsDb[i] = 0.0f;
            rightBandGainsDb[i] = 0.0f;
        }
        
        Log.i(TAG, "GainStagingManager initialized");
        Log.i(TAG, "  Max user gain: " + ABSOLUTE_MAX_GAIN_DB + " dB");
        Log.i(TAG, "  Reference input: " + REFERENCE_INPUT_SPL + " dB SPL");
        Log.i(TAG, "  Safety margin: UCL - " + SAFE_OUTPUT_HEADROOM_DB + " dB");
    }
    
    /**
     * Set UCL limits from calibration data.
     * 
     * @param leftUCL UCL in dB SPL for left ear
     * @param rightUCL UCL in dB SPL for right ear
     */
    public void setUCLLimits(float leftUCL, float rightUCL) {
        this.leftUCL_dBSPL = leftUCL;
        this.rightUCL_dBSPL = rightUCL;
        this.uclConfigured = true;  // Mark as configured with real data
        
        Log.i(TAG, String.format("UCL limits set: LEFT=%.1f dB SPL, RIGHT=%.1f dB SPL", 
            leftUCL, rightUCL));
        
        // Recalculate effective gains with new UCL
        recalculateEffectiveGains();
    }
    
    /**
     * Set maximum gain limit (mode-dependent).
     * 
     * @param maxGainDb Maximum gain in dB (40 for MIC mode, 30 for MEDIA mode)
     */
    public void setMaxGainDb(float maxGainDb) {
        this.ABSOLUTE_MAX_GAIN_DB = maxGainDb;
        Log.i(TAG, "Max gain updated to: " + maxGainDb + " dB");
        
        // Recalculate effective gains to apply new limit
        recalculateEffectiveGains();
    }
    
    /**
     * Process user slider input (0-100 dB) and calculate safe effective gains.
     * 
     * @param desiredGainDb User-requested gain from slider (0-100 dB)
     */
    public void setDesiredGain(float desiredGainDb) {
        // Clamp to absolute maximum
        desiredGainDb = Math.min(desiredGainDb, ABSOLUTE_MAX_GAIN_DB);
        
        // If no calibration data, allow full gain range (testing mode)
        if (!uclConfigured) {
            leftEffectiveGainDb = desiredGainDb;
            rightEffectiveGainDb = desiredGainDb;
            
            Log.i(TAG, String.format("[GainStaging] TESTING MODE - No UCL configured, full gain allowed: %.1f dB", desiredGainDb));
        } else {
            // Calculate maximum insertion gain per ear based on UCL
            // maxInsertionGain = UCL - safetyMargin - referenceInputLevel
            float leftMaxInsertionDb = leftUCL_dBSPL - SAFE_OUTPUT_HEADROOM_DB - REFERENCE_INPUT_SPL;
            float rightMaxInsertionDb = rightUCL_dBSPL - SAFE_OUTPUT_HEADROOM_DB - REFERENCE_INPUT_SPL;
            
            // Clamp insertion gain to [0, absolute max]
            leftMaxInsertionDb = Math.max(0, Math.min(leftMaxInsertionDb, ABSOLUTE_MAX_GAIN_DB));
            rightMaxInsertionDb = Math.max(0, Math.min(rightMaxInsertionDb, ABSOLUTE_MAX_GAIN_DB));
            
            // Effective gain is minimum of desired and UCL-limited
            leftEffectiveGainDb = Math.min(desiredGainDb, leftMaxInsertionDb);
            rightEffectiveGainDb = Math.min(desiredGainDb, rightMaxInsertionDb);
            
            Log.i(TAG, String.format("[GainStaging] UCL MODE - Desired: %.1f dB → Effective: L=%.1f dB, R=%.1f dB (UCL: L=%.1f, R=%.1f dB SPL)",
                desiredGainDb, leftEffectiveGainDb, rightEffectiveGainDb, leftUCL_dBSPL, rightUCL_dBSPL));
            
            // Warn if gain is being limited
            if (Math.abs(desiredGainDb - leftEffectiveGainDb) > 5.0f || 
                Math.abs(desiredGainDb - rightEffectiveGainDb) > 5.0f) {
                Log.w(TAG, String.format("⚠️ Gain limited by UCL: Max insertion = L:%.1f dB, R:%.1f dB",
                    leftMaxInsertionDb, rightMaxInsertionDb));
            }
        }
        
        // Update statistics
        maxRequestedGain = Math.max(maxRequestedGain, desiredGainDb);
        maxEffectiveGain = Math.max(maxEffectiveGain, 
            Math.max(leftEffectiveGainDb, rightEffectiveGainDb));
        
        // Recalculate per-band gains
        recalculateEffectiveGains();
    }
    
    /**
     * Recalculate per-band gains with frequency-dependent weighting.
     */
    private void recalculateEffectiveGains() {
        // Distribute effective gain across bands with frequency weighting
        // Higher frequencies get slightly more gain for speech clarity
        
        float leftBaseGain = leftEffectiveGainDb + currentAdaptiveGain_dB;
        float rightBaseGain = rightEffectiveGainDb + currentAdaptiveGain_dB;
        
        for (int band = 0; band < NUM_BANDS; band++) {
            // Apply logarithmic frequency weighting
            leftBandGainsDb[band] = leftBaseGain * bandGainFactors[band];
            rightBandGainsDb[band] = rightBaseGain * bandGainFactors[band];
        }
    }
    
    /**
     * Get per-band linear gains for left ear.
     * 
     * @return Array of 5 linear gain factors
     */
    public float[] getLeftBandGains() {
        float[] linearGains = new float[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            linearGains[i] = dbToLinear(leftBandGainsDb[i]);
        }
        return linearGains;
    }
    
    /**
     * Get per-band linear gains for right ear.
     * 
     * @return Array of 5 linear gain factors
     */
    public float[] getRightBandGains() {
        float[] linearGains = new float[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            linearGains[i] = dbToLinear(rightBandGainsDb[i]);
        }
        return linearGains;
    }
    
    /**
     * Update adaptive loudness control based on recent RMS measurements.
     * Adjusts gain to maintain target RMS level.
     * 
     * @param currentRMS_dBFS Current frame RMS in dBFS
     */
    public void updateAdaptiveLoudness(float currentRMS_dBFS) {
        rmsAccumulator += currentRMS_dBFS;
        rmsFrameCount++;
        
        if (rmsFrameCount >= RMS_WINDOW_FRAMES) {
            // Calculate average RMS over window
            float avgRMS_dBFS = rmsAccumulator / rmsFrameCount;
            
            // Adjust adaptive gain to reach target
            float error = targetRMS_dBFS - avgRMS_dBFS;
            
            if (avgRMS_dBFS < -18.0f && error > 2.0f) {
                // Too quiet: increase gain
                currentAdaptiveGain_dB = Math.min(currentAdaptiveGain_dB + 2.0f, 6.0f);
                Log.d(TAG, String.format("Adaptive gain increased: %.1f dB (RMS=%.1f dBFS)", 
                    currentAdaptiveGain_dB, avgRMS_dBFS));
            } else if (avgRMS_dBFS > -10.0f && error < -2.0f) {
                // Too loud: reduce gain
                currentAdaptiveGain_dB = Math.max(currentAdaptiveGain_dB - 2.0f, -6.0f);
                Log.d(TAG, String.format("Adaptive gain reduced: %.1f dB (RMS=%.1f dBFS)", 
                    currentAdaptiveGain_dB, avgRMS_dBFS));
            }
            
            // Recalculate band gains with new adaptive offset
            recalculateEffectiveGains();
            
            // Reset accumulator
            rmsAccumulator = 0.0f;
            rmsFrameCount = 0;
        }
        
        framesProcessed++;
    }
    
    /**
     * Get current effective gain for left ear.
     * 
     * @return Effective gain in dB
     */
    public float getLeftEffectiveGainDb() {
        return leftEffectiveGainDb + currentAdaptiveGain_dB;
    }
    
    /**
     * Get current effective gain for right ear.
     * 
     * @return Effective gain in dB
     */
    public float getRightEffectiveGainDb() {
        return rightEffectiveGainDb + currentAdaptiveGain_dB;
    }
    
    /**
     * Get statistics summary.
     * 
     * @return Formatted statistics string
     */
    public String getStatistics() {
        return String.format(
            "Gain Staging Stats:\n" +
            "  Frames: %d\n" +
            "  Max Requested: %.1f dB\n" +
            "  Max Effective: %.1f dB\n" +
            "  Current Adaptive: %.1f dB\n" +
            "  Left UCL: %.1f dB SPL → Max Gain: %.1f dB\n" +
            "  Right UCL: %.1f dB SPL → Max Gain: %.1f dB",
            framesProcessed,
            maxRequestedGain,
            maxEffectiveGain,
            currentAdaptiveGain_dB,
            leftUCL_dBSPL,
            leftUCL_dBSPL - SAFE_OUTPUT_HEADROOM_DB - REFERENCE_INPUT_SPL,
            rightUCL_dBSPL,
            rightUCL_dBSPL - SAFE_OUTPUT_HEADROOM_DB - REFERENCE_INPUT_SPL
        );
    }
    
    /**
     * Reset adaptive gain and statistics.
     */
    public void reset() {
        currentAdaptiveGain_dB = 0.0f;
        rmsAccumulator = 0.0f;
        rmsFrameCount = 0;
        framesProcessed = 0;
        maxRequestedGain = 0.0f;
        maxEffectiveGain = 0.0f;
        recalculateEffectiveGains();
    }
    
    // Utility functions
    
    private static float dbToLinear(float db) {
        return (float) Math.pow(10.0, db / 20.0);
    }
    
    private static float linearToDb(float linear) {
        if (linear <= 1e-6f) return -120.0f;
        return (float) (20.0 * Math.log10(linear));
    }
}
