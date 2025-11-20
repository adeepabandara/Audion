package com.audion.audio;

import java.util.Random;

/**
 * TpdfDither - Triangular Probability Density Function dither.
 * 
 * Applies TPDF dither before float→short conversion to reduce
 * quantization noise artifacts at high gains.
 * 
 * TPDF dither adds ±1 LSB of randomness with triangular distribution,
 * which decorrelates quantization error and makes it less audible.
 */
public class TpdfDither {
    private static final float DITHER_AMPLITUDE = 1.0f / 65536.0f;  // ±1 LSB
    private final Random random;
    
    /**
     * Create TPDF dither processor.
     */
    public TpdfDither() {
        this.random = new Random();
    }
    
    /**
     * Create TPDF dither processor with seed.
     * 
     * @param seed Random seed for reproducible dither
     */
    public TpdfDither(long seed) {
        this.random = new Random(seed);
    }
    
    /**
     * Convert normalized float to PCM16 with TPDF dither.
     * 
     * @param sample Normalized float sample (-1.0 to +1.0)
     * @return 16-bit PCM sample
     */
    public short toPcm16(float sample) {
        // Generate TPDF dither: rand1 + rand2 - 1 (triangular distribution)
        float dither = (random.nextFloat() + random.nextFloat() - 1.0f) * DITHER_AMPLITUDE;
        
        // Add dither
        float dithered = sample + dither;
        
        // Clamp to valid range
        if (dithered > 1.0f) dithered = 1.0f;
        if (dithered < -1.0f) dithered = -1.0f;
        
        // Convert to 16-bit PCM
        return (short) Math.round(dithered * 32767.0f);
    }
    
    /**
     * Convert float buffer to short buffer with TPDF dither.
     * 
     * @param input Input float buffer (normalized -1 to +1)
     * @param output Output short buffer
     * @param length Number of samples to convert
     */
    public void process(float[] input, short[] output, int length) {
        for (int i = 0; i < length; i++) {
            output[i] = toPcm16(input[i]);
        }
    }
}
