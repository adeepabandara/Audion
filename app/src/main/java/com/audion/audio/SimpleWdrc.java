package com.audion.audio;

import android.util.Log;

/**
 * SimpleWdrc - Single-band Wide Dynamic Range Compression.
 * 
 * Lightweight compressor to reduce dynamic range before user amplification:
 * - Threshold: -28 dBFS
 * - Ratio: 2.5:1
 * - Knee: 6 dB (soft knee)
 * - Attack: 8 ms
 * - Release: 120 ms
 * 
 * Makes loud bursts less harsh without aggressive pumping.
 */
public class SimpleWdrc {
    private static final String TAG = "SimpleWdrc";
    
    // Compressor parameters
    private final float thresholdDb;     // -28 dBFS
    private final float ratio;           // 2.5:1
    private final float kneeDb;          // 6 dB soft knee
    private final float attackTimeMs;    // 8 ms
    private final float releaseTimeMs;   // 120 ms
    
    private final int sampleRate;
    private final float attackCoeff;
    private final float releaseCoeff;
    
    // State
    private float envelopeDb = -96.0f;  // Current envelope in dB
    
    // Statistics
    private float maxGainReduction = 0.0f;
    private long samplesProcessed = 0;
    
    /**
     * Create single-band WDRC compressor.
     * 
     * @param sampleRate Sample rate in Hz
     */
    public SimpleWdrc(int sampleRate) {
        this.sampleRate = sampleRate;
        this.thresholdDb = -28.0f;
        this.ratio = 2.5f;
        this.kneeDb = 6.0f;
        this.attackTimeMs = 8.0f;
        this.releaseTimeMs = 120.0f;
        
        // Calculate envelope follower coefficients
        this.attackCoeff = (float) Math.exp(-1000.0 / (attackTimeMs * sampleRate));
        this.releaseCoeff = (float) Math.exp(-1000.0 / (releaseTimeMs * sampleRate));
        
        Log.i(TAG, String.format("SimpleWdrc initialized: threshold=%.1f dB, ratio=%.1f:1, knee=%.1f dB, attack=%.1f ms, release=%.1f ms",
            thresholdDb, ratio, kneeDb, attackTimeMs, releaseTimeMs));
    }
    
    /**
     * Process audio buffer with WDRC compression.
     * 
     * @param input Input buffer (normalized float -1 to +1)
     * @param output Output buffer
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            float sample = input[i];
            
            // Calculate instantaneous level in dB
            float absLevel = Math.abs(sample);
            float instantDb = (absLevel > 1e-6f) ? 
                (float) (20.0 * Math.log10(absLevel)) : -96.0f;
            
            // Update envelope follower (attack/release)
            float coeff = (instantDb > envelopeDb) ? attackCoeff : releaseCoeff;
            envelopeDb = coeff * envelopeDb + (1.0f - coeff) * instantDb;
            
            // Calculate gain reduction with soft knee
            float gainReductionDb = 0.0f;
            
            if (envelopeDb > thresholdDb + kneeDb / 2.0f) {
                // Above knee - full compression
                float overshoot = envelopeDb - thresholdDb;
                gainReductionDb = overshoot * (1.0f - 1.0f / ratio);
            } else if (envelopeDb > thresholdDb - kneeDb / 2.0f) {
                // In knee region - soft transition
                float kneeInput = envelopeDb - thresholdDb + kneeDb / 2.0f;
                float kneeFactor = kneeInput / kneeDb;
                float overshoot = kneeInput - kneeDb / 2.0f;
                gainReductionDb = kneeFactor * overshoot * (1.0f - 1.0f / ratio);
            }
            // Else: below threshold, no compression
            
            // Apply gain reduction (convert dB to linear)
            float gainLinear = (float) Math.pow(10.0, -gainReductionDb / 20.0);
            output[i] = sample * gainLinear;
            
            // Update statistics
            if (gainReductionDb > maxGainReduction) {
                maxGainReduction = gainReductionDb;
            }
            samplesProcessed++;
        }
    }
    
    /**
     * Get current envelope level in dB.
     * 
     * @return Current envelope in dBFS
     */
    public float getEnvelopeDb() {
        return envelopeDb;
    }
    
    /**
     * Get maximum gain reduction since last reset.
     * 
     * @return Max gain reduction in dB
     */
    public float getMaxGainReduction() {
        return maxGainReduction;
    }
    
    /**
     * Reset statistics.
     */
    public void resetStats() {
        maxGainReduction = 0.0f;
        samplesProcessed = 0;
    }
    
    /**
     * Reset compressor state.
     */
    public void reset() {
        envelopeDb = -96.0f;
        resetStats();
    }
}
