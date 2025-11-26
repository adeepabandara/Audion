package com.audion.audio;

/**
 * GainSmoother - Exponential gain smoothing to prevent zipper noise.
 * 
 * Smooths gain changes with separate attack and release time constants
 * to avoid audible clicks when user adjusts amplification.
 * 
 * Attack: 10 ms (fast response to gain increases)
 * Release: 80 ms (slower response to gain decreases)
 */
public class GainSmoother {
    private static final String TAG = "GainSmoother";
    
    private float currentLinearGain = 1.0f;
    private float targetLinearGain = 1.0f;
    
    private final int sampleRate;
    private final float attackCoeff;   // Faster response to increases
    private final float releaseCoeff;  // Slower response to decreases
    
    /**
     * Create gain smoother.
     * 
     * @param sampleRate Sample rate in Hz
     * @param attackTimeMs Attack time constant in milliseconds
     * @param releaseTimeMs Release time constant in milliseconds
     */
    public GainSmoother(int sampleRate, float attackTimeMs, float releaseTimeMs) {
        this.sampleRate = sampleRate;
        
        // Calculate exponential smoothing coefficients
        // coeff = exp(-1 / (timeConstant * sampleRate))
        this.attackCoeff = (float) Math.exp(-1000.0 / (attackTimeMs * sampleRate));
        this.releaseCoeff = (float) Math.exp(-1000.0 / (releaseTimeMs * sampleRate));
    }
    
    /**
     * Set target gain in dB.
     * 
     * @param gainDb Target gain in decibels
     */
    public void setTargetDb(float gainDb) {
        // Convert dB to linear: linear = 10^(dB/20)
        targetLinearGain = (float) Math.pow(10.0, gainDb / 20.0);
    }
    
    /**
     * Set target gain in dB and immediately snap to it (no smoothing).
     * Use this when initializing or resetting to prevent transients.
     * 
     * @param gainDb Target gain in decibels
     */
    public void setTargetDbImmediate(float gainDb) {
        targetLinearGain = (float) Math.pow(10.0, gainDb / 20.0);
        currentLinearGain = targetLinearGain;
    }
    
    /**
     * Get next smoothed gain value (per-sample processing).
     * Simple exponential smoothing - no complex adaptive behavior.
     * 
     * @return Current smoothed linear gain
     */
    public float nextSample() {
        // If already very close to target, snap to it
        if (Math.abs(currentLinearGain - targetLinearGain) < 0.0001f) {
            currentLinearGain = targetLinearGain;
            return currentLinearGain;
        }
        
        // Use attack for increases, release for decreases (simple and predictable)
        float coeff = (targetLinearGain > currentLinearGain) ? attackCoeff : releaseCoeff;
        currentLinearGain = coeff * currentLinearGain + (1.0f - coeff) * targetLinearGain;
        
        return currentLinearGain;
    }
    
    /**
     * Process an entire buffer with smoothed gain.
     * 
     * @param input Input buffer
     * @param output Output buffer
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            float gain = nextSample();
            output[i] = input[i] * gain;
        }
    }
    
    /**
     * Get current smoothed gain (linear).
     * 
     * @return Current linear gain value
     */
    public float getCurrentLinearGain() {
        return currentLinearGain;
    }
    
    /**
     * Set current linear gain directly (for external smoothing).
     * 
     * @param linearGain Current linear gain value
     */
    public void setCurrentLinearGain(float linearGain) {
        this.currentLinearGain = linearGain;
    }
    
    /**
     * Get current smoothed gain in dB.
     * 
     * @return Current gain in decibels
     */
    public float getCurrentDb() {
        return (float) (20.0 * Math.log10(Math.max(1e-6f, currentLinearGain)));
    }
    
    /**
     * Reset to unity gain.
     */
    public void reset() {
        currentLinearGain = 1.0f;
        targetLinearGain = 1.0f;
    }
}
