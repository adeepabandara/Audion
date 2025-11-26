package com.audion.audio;

import android.util.Log;

/**
 * PreGainStage - Pre-amplification before RNNoise.
 * 
 * Applies configurable gain (0-10 dB) before noise reduction.
 * Default 0 dB (unity gain) for clean passthrough.
 * 
 * NOTE: Clamp removed - downstream limiter handles peak control.
 */
public class PreGainStage {
    private static final String TAG = "PreGainStage";
    
    private static final float MIN_GAIN_DB = 0.0f;
    private static final float MAX_GAIN_DB = 10.0f;
    private static final float DEFAULT_GAIN_DB = 6.0f;
    private static final float SAFETY_CLAMP = 0.9f;  // ±0.9 to leave headroom
    
    private float gainDb;
    private float gainLinear;
    
    // Statistics
    private long samplesProcessed = 0;
    private long samplesClamped = 0;
    
    /**
     * Create pre-gain stage with default gain.
     */
    public PreGainStage() {
        this(DEFAULT_GAIN_DB);
    }
    
    /**
     * Create pre-gain stage with specified gain.
     * 
     * @param gainDb Initial gain in dB (0-10 dB)
     */
    public PreGainStage(float gainDb) {
        setGainDb(gainDb);
        Log.i(TAG, String.format("PreGainStage initialized: gain=%.1f dB (%.3fx linear), NO CLAMP",
            this.gainDb, this.gainLinear));
    }
    
    /**
     * Set pre-gain in dB.
     * 
     * @param gainDb Gain in dB (clamped to 0-10 dB range)
     */
    public void setGainDb(float gainDb) {
        // Clamp to valid range
        this.gainDb = Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB, gainDb));
        
        // Convert to linear
        this.gainLinear = (float) Math.pow(10.0, this.gainDb / 20.0);
        
        // Debug logging removed for production
        // Log.d(TAG, String.format("Pre-gain set: %.1f dB (%.3fx linear)", this.gainDb, this.gainLinear));
    }
    
    /**
     * Get current gain in dB.
     * 
     * @return Current gain in dB
     */
    public float getGainDb() {
        return gainDb;
    }
    
    /**
     * Process audio buffer with pre-gain and safety clamping.
     * 
     * @param input Input buffer (normalized float -1 to +1)
     * @param output Output buffer
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            // Apply gain without clamping - let downstream limiter handle peaks
            // This prevents hard clipping distortion from the clamp
            output[i] = input[i] * gainLinear;
            samplesProcessed++;
        }
    }
    
    /**
     * Get percentage of samples that were clamped.
     * 
     * @return Clipping percentage (0-100)
     */
    public float getClampingPercentage() {
        if (samplesProcessed == 0) return 0.0f;
        return (samplesClamped * 100.0f) / samplesProcessed;
    }
    
    /**
     * Reset statistics.
     */
    public void resetStats() {
        samplesProcessed = 0;
        samplesClamped = 0;
    }
}
