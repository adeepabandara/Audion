package com.audion.audio;

/**
 * Biquad Filter - 2nd order IIR filter using RBJ (Robert Bristow-Johnson) formulas.
 * 
 * Supports:
 * - Bandpass filters (for multiband processing)
 * - Lowpass/Highpass filters
 * - Butterworth response (maximally flat passband)
 * 
 * Uses Direct Form II structure for numerical stability.
 */
public class BiquadFilter {
    // Filter coefficients (Direct Form II)
    private final float b0, b1, b2;  // Feedforward coefficients
    private final float a1, a2;       // Feedback coefficients (a0 normalized to 1.0)
    
    // State variables (Direct Form II)
    private float z1 = 0.0f;
    private float z2 = 0.0f;
    
    private final String filterType;
    
    /**
     * Create biquad filter with specified coefficients.
     * Use factory methods (createBandpass, etc.) for specific filter types.
     */
    private BiquadFilter(float b0, float b1, float b2, float a0, float a1, float a2, String filterType) {
        // Normalize coefficients by a0
        this.b0 = b0 / a0;
        this.b1 = b1 / a0;
        this.b2 = b2 / a0;
        this.a1 = a1 / a0;
        this.a2 = a2 / a0;
        this.filterType = filterType;
    }
    
    /**
     * Create 2nd-order Butterworth bandpass filter using RBJ formulas.
     * 
     * @param lowFreq Lower cutoff frequency (Hz)
     * @param highFreq Upper cutoff frequency (Hz)
     * @param sampleRate Sample rate (Hz)
     * @return Configured BiquadFilter
     */
    public static BiquadFilter createBandpass(float lowFreq, float highFreq, int sampleRate) {
        // Calculate center frequency and bandwidth
        float centerFreq = (float) Math.sqrt(lowFreq * highFreq);
        float bandwidth = (float) (Math.log(highFreq / lowFreq) / Math.log(2.0));
        
        // RBJ bandpass formula
        float w0 = (float) (2.0 * Math.PI * centerFreq / sampleRate);
        float cosW0 = (float) Math.cos(w0);
        float sinW0 = (float) Math.sin(w0);
        float alpha = (float) (sinW0 * Math.sinh(Math.log(2.0) / 2.0 * bandwidth * w0 / sinW0));
        
        // Calculate coefficients
        float b0 = alpha;
        float b1 = 0.0f;
        float b2 = -alpha;
        float a0 = 1.0f + alpha;
        float a1 = -2.0f * cosW0;
        float a2 = 1.0f - alpha;
        
        return new BiquadFilter(b0, b1, b2, a0, a1, a2, 
            String.format("Bandpass[%.0f-%.0fHz]", lowFreq, highFreq));
    }
    
    /**
     * Create 2nd-order Butterworth lowpass filter.
     * 
     * @param cutoffFreq Cutoff frequency (Hz)
     * @param sampleRate Sample rate (Hz)
     * @return Configured BiquadFilter
     */
    public static BiquadFilter createLowpass(float cutoffFreq, int sampleRate) {
        float w0 = (float) (2.0 * Math.PI * cutoffFreq / sampleRate);
        float cosW0 = (float) Math.cos(w0);
        float sinW0 = (float) Math.sin(w0);
        float alpha = sinW0 / (2.0f * 0.707f);  // Q = 0.707 for Butterworth
        
        float b0 = (1.0f - cosW0) / 2.0f;
        float b1 = 1.0f - cosW0;
        float b2 = (1.0f - cosW0) / 2.0f;
        float a0 = 1.0f + alpha;
        float a1 = -2.0f * cosW0;
        float a2 = 1.0f - alpha;
        
        return new BiquadFilter(b0, b1, b2, a0, a1, a2, 
            String.format("Lowpass[%.0fHz]", cutoffFreq));
    }
    
    /**
     * Create 2nd-order Butterworth highpass filter.
     * 
     * @param cutoffFreq Cutoff frequency (Hz)
     * @param sampleRate Sample rate (Hz)
     * @return Configured BiquadFilter
     */
    public static BiquadFilter createHighpass(float cutoffFreq, int sampleRate) {
        float w0 = (float) (2.0 * Math.PI * cutoffFreq / sampleRate);
        float cosW0 = (float) Math.cos(w0);
        float sinW0 = (float) Math.sin(w0);
        float alpha = sinW0 / (2.0f * 0.707f);  // Q = 0.707 for Butterworth
        
        float b0 = (1.0f + cosW0) / 2.0f;
        float b1 = -(1.0f + cosW0);
        float b2 = (1.0f + cosW0) / 2.0f;
        float a0 = 1.0f + alpha;
        float a1 = -2.0f * cosW0;
        float a2 = 1.0f - alpha;
        
        return new BiquadFilter(b0, b1, b2, a0, a1, a2, 
            String.format("Highpass[%.0fHz]", cutoffFreq));
    }
    
    /**
     * Process audio samples through the biquad filter.
     * Uses Direct Form II for numerical stability.
     * 
     * @param input Input buffer
     * @param output Output buffer (can be same as input)
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            // Direct Form II
            float w = input[i] - a1 * z1 - a2 * z2;
            output[i] = b0 * w + b1 * z1 + b2 * z2;
            
            // Update state
            z2 = z1;
            z1 = w;
        }
    }
    
    /**
     * Reset filter state (clear delay line).
     * Call this when audio stream restarts to prevent clicks.
     */
    public void reset() {
        z1 = 0.0f;
        z2 = 0.0f;
    }
    
    /**
     * Get filter type description.
     */
    public String getFilterType() {
        return filterType;
    }
    
    @Override
    public String toString() {
        return String.format("BiquadFilter[%s] b0=%.4f b1=%.4f b2=%.4f a1=%.4f a2=%.4f",
            filterType, b0, b1, b2, a1, a2);
    }
}
