package com.audion.audio;

/**
 * PostRNNoiseDeRinger - Reduces overlap-add artifacts from RNNoise.
 * 
 * Applies a short raised-cosine (Hann) window to the first and last
 * samples of each frame to smooth frame boundaries without modifying
 * RNNoise internals.
 * 
 * Window: 10 ms = 480 samples @ 48kHz
 * Edge fade: First and last 32 samples (0.67 ms)
 */
public class PostRNNoiseDeRinger {
    private static final String TAG = "PostRNNoiseDeRinger";
    
    private static final int FADE_SAMPLES = 32;  // ~0.67 ms @ 48kHz
    private final float[] fadeInWindow;
    private final float[] fadeOutWindow;
    
    /**
     * Create post-RNNoise de-ringing processor.
     */
    public PostRNNoiseDeRinger() {
        // Generate raised-cosine (Hann) windows for fade in/out
        fadeInWindow = new float[FADE_SAMPLES];
        fadeOutWindow = new float[FADE_SAMPLES];
        
        for (int i = 0; i < FADE_SAMPLES; i++) {
            // Hann window: 0.5 * (1 - cos(2*pi*i/N))
            double angle = Math.PI * i / (FADE_SAMPLES - 1);
            fadeInWindow[i] = (float) (0.5 * (1.0 - Math.cos(angle)));
            fadeOutWindow[i] = (float) (0.5 * (1.0 - Math.cos(Math.PI + angle)));
        }
    }
    
    /**
     * Process frame with edge smoothing.
     * 
     * Applies fade-in to first 32 samples and fade-out to last 32 samples
     * to reduce overlap-add boundary artifacts.
     * 
     * @param buffer Audio buffer (modified in-place)
     * @param length Frame length (typically 480 samples)
     */
    public void process(float[] buffer, int length) {
        // Apply fade-in to beginning
        int fadeInEnd = Math.min(FADE_SAMPLES, length);
        for (int i = 0; i < fadeInEnd; i++) {
            buffer[i] *= fadeInWindow[i];
        }
        
        // Apply fade-out to end
        int fadeOutStart = Math.max(0, length - FADE_SAMPLES);
        for (int i = fadeOutStart; i < length; i++) {
            int windowIndex = i - fadeOutStart;
            buffer[i] *= fadeOutWindow[windowIndex];
        }
    }
    
    /**
     * Process frame with edge smoothing (with separate input/output).
     * 
     * @param input Input buffer
     * @param output Output buffer
     * @param length Frame length
     */
    public void process(float[] input, float[] output, int length) {
        // Copy input to output first
        System.arraycopy(input, 0, output, 0, length);
        
        // Apply windowing in-place
        process(output, length);
    }
}
