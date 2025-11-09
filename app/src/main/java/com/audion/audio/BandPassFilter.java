package com.audion.audio;

/**
 * 2nd-order Butterworth Bandpass Filter using RBJ (Robert Bristow-Johnson) cookbook.
 * 
 * Implementation: Direct Form II Transposed structure
 * Filter design: Q = centerFreq / bandwidth
 * 
 * Used for splitting audio into frequency bands for per-band gain processing.
 */
public class BandPassFilter {
    private static final String TAG = "BandPassFilter";
    
    // Filter coefficients (normalized)
    private float b0, b1, b2;  // Numerator coefficients
    private float a1, a2;       // Denominator coefficients (a0 = 1.0 after normalization)
    
    // State variables (Direct Form II)
    private float x1 = 0, x2 = 0;  // Input history
    private float y1 = 0, y2 = 0;  // Output history
    
    private final float centerFreq;
    private final float bandwidth;
    private final int sampleRate;
    
    /**
     * Create bandpass filter with specified frequency range.
     * 
     * @param lowFreq Lower cutoff frequency (Hz)
     * @param highFreq Upper cutoff frequency (Hz)
     * @param sampleRate Sample rate (Hz)
     */
    public BandPassFilter(float lowFreq, float highFreq, int sampleRate) {
        this.centerFreq = (float) Math.sqrt(lowFreq * highFreq);  // Geometric mean
        this.bandwidth = highFreq - lowFreq;
        this.sampleRate = sampleRate;
        
        calculateCoefficients();
        
        android.util.Log.d(TAG, String.format("BandPassFilter created: %.0f-%.0f Hz (center=%.0f, BW=%.0f) @ %d Hz",
            lowFreq, highFreq, centerFreq, bandwidth, sampleRate));
    }
    
    /**
     * Calculate RBJ bandpass filter coefficients.
     * Reference: https://webaudio.github.io/Audio-EQ-Cookbook/audio-eq-cookbook.html
     */
    private void calculateCoefficients() {
        // Calculate Q from bandwidth
        float Q = centerFreq / bandwidth;
        
        // Normalize frequencies to [0, pi]
        float omega = (float) (2.0 * Math.PI * centerFreq / sampleRate);
        float sinOmega = (float) Math.sin(omega);
        float cosOmega = (float) Math.cos(omega);
        
        // RBJ alpha parameter
        float alpha = sinOmega / (2.0f * Q);
        
        // Calculate raw coefficients
        float b0_raw = alpha;
        float b1_raw = 0.0f;
        float b2_raw = -alpha;
        
        float a0_raw = 1.0f + alpha;
        float a1_raw = -2.0f * cosOmega;
        float a2_raw = 1.0f - alpha;
        
        // Normalize by a0
        this.b0 = b0_raw / a0_raw;
        this.b1 = b1_raw / a0_raw;
        this.b2 = b2_raw / a0_raw;
        this.a1 = a1_raw / a0_raw;
        this.a2 = a2_raw / a0_raw;
    }
    
    /**
     * Process a single sample through the filter.
     * Direct Form II Transposed: y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
     * 
     * @param input Input sample
     * @return Filtered output sample
     */
    public float processSample(float input) {
        // Calculate output
        float output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        
        // Update state
        x2 = x1;
        x1 = input;
        y2 = y1;
        y1 = output;
        
        return output;
    }
    
    /**
     * Process an array of samples.
     * 
     * @param input Input buffer (short samples)
     * @param output Output buffer (float samples)
     */
    public void process(short[] input, float[] output) {
        for (int i = 0; i < input.length; i++) {
            float sample = input[i];
            output[i] = processSample(sample);
        }
    }
    
    /**
     * Process float array to float array.
     * 
     * @param input Input buffer (float samples)
     * @param output Output buffer (float samples)
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            output[i] = processSample(input[i]);
        }
    }
    
    /**
     * Reset filter state (clear history).
     */
    public void reset() {
        x1 = x2 = 0;
        y1 = y2 = 0;
    }
    
    public float getCenterFreq() {
        return centerFreq;
    }
    
    public float getBandwidth() {
        return bandwidth;
    }
}
