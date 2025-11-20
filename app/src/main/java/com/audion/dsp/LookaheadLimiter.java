package com.audion.audio;

import android.util.Log;

/**
 * LookaheadLimiter - True-peak soft limiter with lookahead.
 * 
 * Prevents hard clipping with smooth gain reduction:
 * - Lookahead: 5 ms (240 samples @ 48kHz)
 * - Ceiling: -3 dBFS (0.7071 linear)
 * - Soft knee: 6 dB
 * - Release: 80 ms
 * 
 * This is the ONLY last-stage protector in the pipeline.
 */
public class LookaheadLimiter {
    private static final String TAG = "LookaheadLimiter";
    
    private final int sampleRate;
    private final float ceilingLinear;   // 0.7071 for -3 dBFS
    private final float ceilingDb;       // -3 dB
    private final float kneeDb;          // 6 dB soft knee
    private final float releaseTimeMs;   // 80 ms
    
    private final int lookaheadSamples;  // 240 samples @ 48kHz
    private final float[] lookaheadBuffer;
    private int bufferIndex = 0;
    
    private final float releaseCoeff;
    private float currentGainReduction = 0.0f;  // dB
    
    // Statistics
    private float maxPeak = 0.0f;
    private float maxGainReduction = 0.0f;
    private long samplesInLimiting = 0;
    private long totalSamples = 0;
    
    /**
     * Create lookahead limiter.
     * 
     * @param sampleRate Sample rate in Hz
     * @param ceilingDb Ceiling level in dBFS (e.g., -3.0)
     * @param lookaheadMs Lookahead time in milliseconds
     */
    public LookaheadLimiter(int sampleRate, float ceilingDb, float lookaheadMs) {
        this.sampleRate = sampleRate;
        this.ceilingDb = ceilingDb;
        this.ceilingLinear = (float) Math.pow(10.0, ceilingDb / 20.0);
        this.kneeDb = 6.0f;
        this.releaseTimeMs = 80.0f;
        
        // Calculate lookahead buffer size
        this.lookaheadSamples = (int) (lookaheadMs * sampleRate / 1000.0f);
        this.lookaheadBuffer = new float[lookaheadSamples];
        
        // Calculate release coefficient
        this.releaseCoeff = (float) Math.exp(-1000.0 / (releaseTimeMs * sampleRate));
        
        Log.i(TAG, String.format("LookaheadLimiter initialized: ceiling=%.1f dBFS (%.4f linear), lookahead=%d samples (%.1f ms), knee=%.1f dB, release=%.1f ms",
            ceilingDb, ceilingLinear, lookaheadSamples, lookaheadMs, kneeDb, releaseTimeMs));
    }
    
    /**
     * Process audio buffer with lookahead limiting.
     * 
     * @param input Input buffer (normalized float -1 to +1)
     * @param output Output buffer
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            // Store current sample in lookahead buffer
            lookaheadBuffer[bufferIndex] = input[i];
            
            // Find peak in lookahead window
            float peakInWindow = 0.0f;
            for (int j = 0; j < lookaheadSamples; j++) {
                float abs = Math.abs(lookaheadBuffer[j]);
                if (abs > peakInWindow) {
                    peakInWindow = abs;
                }
            }
            
            // Calculate required gain reduction with soft knee
            float peakDb = (peakInWindow > 1e-6f) ? 
                (float) (20.0 * Math.log10(peakInWindow)) : -96.0f;
            
            float targetGainReduction = 0.0f;
            float kneeStart = ceilingDb - kneeDb / 2.0f;
            float kneeEnd = ceilingDb + kneeDb / 2.0f;
            
            if (peakDb > kneeEnd) {
                // Above knee - full limiting
                targetGainReduction = peakDb - ceilingDb;
            } else if (peakDb > kneeStart) {
                // In knee region - soft transition
                float kneeInput = peakDb - kneeStart;
                float kneeFactor = kneeInput / kneeDb;
                targetGainReduction = kneeFactor * (peakDb - ceilingDb);
            }
            // Else: below knee, no limiting
            
            // Apply smooth release (instant attack, slow release)
            if (targetGainReduction > currentGainReduction) {
                // Attack: instant
                currentGainReduction = targetGainReduction;
            } else {
                // Release: exponential decay
                currentGainReduction = releaseCoeff * currentGainReduction + 
                                      (1.0f - releaseCoeff) * targetGainReduction;
            }
            
            // Apply gain reduction (convert dB to linear)
            float gainLinear = (float) Math.pow(10.0, -currentGainReduction / 20.0);
            
            // Get oldest sample from lookahead buffer (the one we're outputting now)
            int outputIndex = (bufferIndex + 1) % lookaheadSamples;
            float delayedSample = lookaheadBuffer[outputIndex];
            output[i] = delayedSample * gainLinear;
            
            // Update statistics
            float outputAbs = Math.abs(output[i]);
            if (outputAbs > maxPeak) {
                maxPeak = outputAbs;
            }
            if (currentGainReduction > maxGainReduction) {
                maxGainReduction = currentGainReduction;
            }
            if (currentGainReduction > 0.5f) {  // Limiting if GR > 0.5 dB
                samplesInLimiting++;
            }
            totalSamples++;
            
            // Advance buffer index
            bufferIndex = (bufferIndex + 1) % lookaheadSamples;
        }
    }
    
    /**
     * Get current gain reduction in dB.
     * 
     * @return Current gain reduction
     */
    public float getCurrentGainReduction() {
        return currentGainReduction;
    }
    
    /**
     * Get maximum output peak since last reset.
     * 
     * @return Max peak (linear, 0-1 range)
     */
    public float getMaxPeak() {
        return maxPeak;
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
     * Get percentage of time spent limiting.
     * 
     * @return Percentage (0-100)
     */
    public float getLimitingPercentage() {
        if (totalSamples == 0) return 0.0f;
        return (samplesInLimiting * 100.0f) / totalSamples;
    }
    
    /**
     * Get maximum output level in dBFS.
     * 
     * @return Max output in dBFS
     */
    public float getMaxPeakDb() {
        if (maxPeak < 1e-6f) return -96.0f;
        return (float) (20.0 * Math.log10(maxPeak));
    }
    
    /**
     * Reset statistics.
     */
    public void resetStats() {
        maxPeak = 0.0f;
        maxGainReduction = 0.0f;
        samplesInLimiting = 0;
        totalSamples = 0;
    }
    
    /**
     * Reset limiter state.
     */
    public void reset() {
        for (int i = 0; i < lookaheadSamples; i++) {
            lookaheadBuffer[i] = 0.0f;
        }
        bufferIndex = 0;
        currentGainReduction = 0.0f;
        resetStats();
    }
}
