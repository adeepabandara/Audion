package com.audion.audio;

import android.util.Log;

/**
 * PreGainStage - Pre-amplification before RNNoise.
 * 
 * Applies moderate gain (0-10 dB, default 6 dB) to raise SNR
 * before noise reduction, with safety clamping to prevent clipping.
 * 
 * Safety: Clamps output to ±0.9 to maintain headroom for RNNoise.
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
        Log.i(TAG, String.format("PreGainStage initialized: gain=%.1f dB (%.3fx linear), safety clamp=±%.2f",
            this.gainDb, this.gainLinear, SAFETY_CLAMP));
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
        
        Log.d(TAG, String.format("Pre-gain set: %.1f dB (%.3fx linear)", this.gainDb, this.gainLinear));
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
            float amplified = input[i] * gainLinear;
            
            // Safety clamp to ±0.9
            if (amplified > SAFETY_CLAMP) {
                output[i] = SAFETY_CLAMP;
                samplesClamped++;
            } else if (amplified < -SAFETY_CLAMP) {
                output[i] = -SAFETY_CLAMP;
                samplesClamped++;
            } else {
                output[i] = amplified;
            }
            
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
