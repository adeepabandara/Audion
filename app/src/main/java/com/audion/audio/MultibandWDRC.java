package com.audion.audio;

import android.util.Log;

/**
 * Multiband Wide Dynamic Range Compressor (WDRC) with 5-band processing.
 * 
 * Architecture:
 * - 5 frequency bands: 250-750, 750-1500, 1500-3000, 3000-6000, 6000-8000 Hz
 * - 2nd-order Butterworth bandpass filters (RBJ formula)
 * - Per-band soft-knee compression
 * - Configurable threshold, ratio, attack/release times
 * - RMS envelope detection with smooth gain transitions
 * - Optimized for real-time processing (no dynamic allocations)
 * 
 * Usage: Apply after RNNoise and before look-ahead limiter.
 */
public class MultibandWDRC {
    private static final String TAG = "MultibandWDRC";
    
    // Band configuration (5 bands covering 250-8000 Hz)
    private static final int NUM_BANDS = 5;
    private static final float[][] BAND_RANGES = {
        {250f, 750f},    // Band 0: Low frequencies (vowels)
        {750f, 1500f},   // Band 1: Mid-low (vowel formants)
        {1500f, 3000f},  // Band 2: Mid-high (clarity /sh/, /ch/)
        {3000f, 6000f},  // Band 3: High (fricatives /s/, /z/)
        {6000f, 8000f}   // Band 4: Very high (/s/, /f/, /th/)
    };
    
    // Butterworth bandpass filters (2nd order, RBJ formula)
    private final BiquadFilter[] bandFilters;
    
    // Per-band compressors
    private final BandCompressor[] compressors;
    
    // Pre-allocated buffers (avoid allocations in processing loop)
    private final float[][] bandBuffers;
    private final int frameSize;
    private final int sampleRate;
    private final String channelName;
    
    // Statistics
    private long framesProcessed = 0;
    private final float[] maxGainReduction = new float[NUM_BANDS];
    private final int[] compressionActivations = new int[NUM_BANDS];
    
    /**
     * Create multiband WDRC processor.
     * 
     * @param channelName "LEFT" or "RIGHT" for logging
     * @param frameSize Samples per frame (typically 480 @ 48kHz = 10ms)
     * @param sampleRate Sample rate in Hz (48000)
     */
    public MultibandWDRC(String channelName, int frameSize, int sampleRate) {
        this.channelName = channelName;
        this.frameSize = frameSize;
        this.sampleRate = sampleRate;
        
        // Initialize bandpass filters for each band
        bandFilters = new BiquadFilter[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            bandFilters[i] = BiquadFilter.createBandpass(
                BAND_RANGES[i][0],  // Low cutoff
                BAND_RANGES[i][1],  // High cutoff
                sampleRate
            );
        }
        
        // Initialize compressors with clinical parameters optimized for 40 dB max gain
        compressors = new BandCompressor[NUM_BANDS];
        
        // Per-band configuration as per user requirements:
        // Low band (250-500 Hz): Less aggressive (strong fundamentals)
        compressors[0] = new BandCompressor(
            -45.0f,  // threshold_dB (low band, allow more headroom)
            1.5f,    // ratio (gentle compression for fundamentals)
            5.0f,    // attack_ms
            100.0f,  // release_ms
            10.0f,   // knee_width_dB (very smooth)
            sampleRate
        );
        
        // Mid-low band (500-1000 Hz): Moderate compression
        compressors[1] = new BandCompressor(
            -40.0f,  // threshold_dB
            2.0f,    // ratio (moderate compression)
            5.0f,    // attack_ms
            100.0f,  // release_ms
            10.0f,   // knee_width_dB
            sampleRate
        );
        
        // Mid band (1000-2000 Hz): Standard compression
        compressors[2] = new BandCompressor(
            -35.0f,  // threshold_dB (speech critical range)
            2.5f,    // ratio (balanced compression)
            5.0f,    // attack_ms
            100.0f,  // release_ms
            10.0f,   // knee_width_dB
            sampleRate
        );
        
        // Mid-high band (2000-4000 Hz): More compression (consonants)
        compressors[3] = new BandCompressor(
            -30.0f,  // threshold_dB
            3.0f,    // ratio (protect high frequencies)
            5.0f,    // attack_ms
            100.0f,  // release_ms
            8.0f,    // knee_width_dB (tighter for clarity)
            sampleRate
        );
        
        // High band (4000-8000 Hz): Most aggressive (sibilance control)
        compressors[4] = new BandCompressor(
            -25.0f,  // threshold_dB (prevent harsh sibilance)
            3.0f,    // ratio (strong compression for highs)
            5.0f,    // attack_ms
            100.0f,  // release_ms
            8.0f,    // knee_width_dB
            sampleRate
        );
        
        Log.i(TAG, String.format("[%s] MultibandWDRC tuned for 40 dB max gain:", channelName));
        Log.i(TAG, "  Band 0 (250-500 Hz): -45 dBFS @ 1.5:1");
        Log.i(TAG, "  Band 1 (500-1k Hz):  -40 dBFS @ 2.0:1");
        Log.i(TAG, "  Band 2 (1-2k Hz):    -35 dBFS @ 2.5:1");
        Log.i(TAG, "  Band 3 (2-4k Hz):    -30 dBFS @ 3.0:1");
        Log.i(TAG, "  Band 4 (4-8k Hz):    -25 dBFS @ 3.0:1");
        
        // Pre-allocate band buffers
        bandBuffers = new float[NUM_BANDS][frameSize];
        
        // Initialize statistics
        for (int i = 0; i < NUM_BANDS; i++) {
            maxGainReduction[i] = 0.0f;
        }
        
        Log.i(TAG, String.format("[%s] MultibandWDRC initialized: %d bands, frameSize=%d, sampleRate=%d",
            channelName, NUM_BANDS, frameSize, sampleRate));
    }
    
    /**
     * Configure per-band compression parameters based on audiogram/hearing loss.
     * 
     * @param bandIndex Band index (0-4)
     * @param thresholdDb Threshold in dBFS (typically -35 to -25)
     * @param ratio Compression ratio (2:1 to 4:1, higher for severe loss)
     */
    public void setBandParameters(int bandIndex, float thresholdDb, float ratio) {
        if (bandIndex < 0 || bandIndex >= NUM_BANDS) {
            Log.e(TAG, String.format("[%s] Invalid band index: %d", channelName, bandIndex));
            return;
        }
        
        compressors[bandIndex].setParameters(thresholdDb, ratio);
        // Debug logging removed for production
        // Log.d(TAG, String.format("[%s] Band %d: threshold=%.1f dBFS, ratio=%.1f:1",
        //     channelName, bandIndex, thresholdDb, ratio));
    }
    
    /**
     * Set compression parameters for all bands (uniform configuration).
     * 
     * @param thresholdDb Threshold in dBFS
     * @param ratio Compression ratio
     */
    public void setAllBandParameters(float thresholdDb, float ratio) {
        for (int i = 0; i < NUM_BANDS; i++) {
            setBandParameters(i, thresholdDb, ratio);
        }
    }
    
    /**
     * Process audio frame through multiband WDRC.
     * 
     * Pipeline:
     * 1. Split input into 5 frequency bands (Butterworth BPF)
     * 2. Apply per-band soft-knee compression
     * 3. Sum all bands
     * 4. Normalize output to ±1.0
     * 
     * @param input Input buffer (float, ±1.0)
     * @param output Output buffer (float, ±1.0)
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        if (length > frameSize) {
            Log.e(TAG, String.format("[%s] Length %d exceeds frameSize %d", channelName, length, frameSize));
            return;
        }
        
        // Step 1: Split input into 5 frequency bands
        for (int band = 0; band < NUM_BANDS; band++) {
            bandFilters[band].process(input, bandBuffers[band], length);
        }
        
        // Step 2: Apply per-band compression
        for (int band = 0; band < NUM_BANDS; band++) {
            float gainReduction = compressors[band].process(bandBuffers[band], bandBuffers[band], length);
            
            // Update statistics
            if (gainReduction > maxGainReduction[band]) {
                maxGainReduction[band] = gainReduction;
            }
            if (gainReduction > 0.5f) {
                compressionActivations[band]++;
            }
        }
        
        // Step 3: Sum all bands
        for (int i = 0; i < length; i++) {
            float sum = 0.0f;
            for (int band = 0; band < NUM_BANDS; band++) {
                sum += bandBuffers[band][i];
            }
            output[i] = sum;
        }
        
        // Step 4: Normalize output to prevent clipping from band summation
        // Find peak in output
        float peak = 0.0f;
        for (int i = 0; i < length; i++) {
            float abs = Math.abs(output[i]);
            if (abs > peak) peak = abs;
        }
        
        // If peak exceeds 1.0, normalize to ±1.0
        if (peak > 1.0f) {
            float scale = 1.0f / peak;
            for (int i = 0; i < length; i++) {
                output[i] *= scale;
            }
        }
        
        framesProcessed++;
    }
    
    /**
     * Get compression statistics for a specific band.
     * 
     * @param bandIndex Band index (0-4)
     * @return CompressionStats object
     */
    public CompressionStats getBandStats(int bandIndex) {
        if (bandIndex < 0 || bandIndex >= NUM_BANDS) {
            return null;
        }
        
        return new CompressionStats(
            bandIndex,
            BAND_RANGES[bandIndex][0],
            BAND_RANGES[bandIndex][1],
            maxGainReduction[bandIndex],
            compressionActivations[bandIndex],
            framesProcessed
        );
    }
    
    /**
     * Reset statistics.
     */
    public void resetStats() {
        for (int i = 0; i < NUM_BANDS; i++) {
            maxGainReduction[i] = 0.0f;
            compressionActivations[i] = 0;
        }
        framesProcessed = 0;
    }
    
    /**
     * Log current statistics (for debugging).
     */
    public void logStats() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] WDRC Stats (frames=%d):\n", channelName, framesProcessed));
        for (int i = 0; i < NUM_BANDS; i++) {
            float activationPercent = framesProcessed > 0 
                ? (compressionActivations[i] * 100.0f / framesProcessed) 
                : 0.0f;
            sb.append(String.format("  Band %d [%.0f-%.0f Hz]: Max GR=%.1f dB, Active=%.1f%%\n",
                i, BAND_RANGES[i][0], BAND_RANGES[i][1], maxGainReduction[i], activationPercent));
        }
        Log.i(TAG, sb.toString());
    }
    
    /**
     * Compression statistics container.
     */
    public static class CompressionStats {
        public final int bandIndex;
        public final float lowFreq;
        public final float highFreq;
        public final float maxGainReductionDb;
        public final int activations;
        public final long totalFrames;
        
        CompressionStats(int bandIndex, float lowFreq, float highFreq, 
                        float maxGainReductionDb, int activations, long totalFrames) {
            this.bandIndex = bandIndex;
            this.lowFreq = lowFreq;
            this.highFreq = highFreq;
            this.maxGainReductionDb = maxGainReductionDb;
            this.activations = activations;
            this.totalFrames = totalFrames;
        }
        
        public float getActivationPercent() {
            return totalFrames > 0 ? (activations * 100.0f / totalFrames) : 0.0f;
        }
    }
    
    /**
     * Per-band compressor with soft-knee response.
     */
    private static class BandCompressor {
        private float thresholdDb;
        private float ratio;
        private final float attackCoeff;
        private final float releaseCoeff;
        private final float kneeWidthDb;
        
        // State
        private float envelope = 0.0f;
        private float gainSmooth = 1.0f;
        
        BandCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, 
                      float kneeWidthDb, int sampleRate) {
            this.thresholdDb = thresholdDb;
            this.ratio = ratio;
            this.kneeWidthDb = kneeWidthDb;
            
            // Calculate exponential smoothing coefficients
            this.attackCoeff = (float) Math.exp(-1000.0 / (attackMs * sampleRate));
            this.releaseCoeff = (float) Math.exp(-1000.0 / (releaseMs * sampleRate));
        }
        
        void setParameters(float thresholdDb, float ratio) {
            this.thresholdDb = thresholdDb;
            this.ratio = ratio;
        }
        
        /**
         * Process audio with soft-knee compression.
         * 
         * @param input Input buffer
         * @param output Output buffer
         * @param length Number of samples
         * @return Maximum gain reduction in this frame (dB)
         */
        float process(float[] input, float[] output, int length) {
            float maxGainReduction = 0.0f;
            
            for (int i = 0; i < length; i++) {
                float sample = input[i];
                float absSample = Math.abs(sample);
                
                // Update RMS envelope
                if (absSample > envelope) {
                    // Attack: fast response
                    envelope = attackCoeff * envelope + (1.0f - attackCoeff) * absSample;
                } else {
                    // Release: slow response
                    envelope = releaseCoeff * envelope + (1.0f - releaseCoeff) * absSample;
                }
                
                // Calculate input level in dB
                float inputLevelDb = linearToDb(envelope);
                
                // Calculate gain reduction using soft knee
                float gainReductionDb = calculateGainReduction(inputLevelDb);
                
                // Convert to linear gain
                float targetGain = dbToLinear(-gainReductionDb);
                
                // Smooth gain to prevent zipper noise
                gainSmooth = 0.95f * gainSmooth + 0.05f * targetGain;
                
                // Apply gain
                output[i] = sample * gainSmooth;
                
                // Track max gain reduction
                if (gainReductionDb > maxGainReduction) {
                    maxGainReduction = gainReductionDb;
                }
            }
            
            return maxGainReduction;
        }
        
        /**
         * Calculate gain reduction using soft-knee curve.
         * 
         * @param inputLevelDb Input level in dBFS
         * @return Gain reduction in dB (positive value)
         */
        private float calculateGainReduction(float inputLevelDb) {
            if (inputLevelDb < thresholdDb - kneeWidthDb / 2.0f) {
                // Below knee: no compression
                return 0.0f;
            } else if (inputLevelDb < thresholdDb + kneeWidthDb / 2.0f) {
                // Inside knee: quadratic interpolation
                float x = inputLevelDb - thresholdDb + kneeWidthDb / 2.0f;
                float kneeFactor = x * x / (2.0f * kneeWidthDb);
                return kneeFactor * (1.0f - 1.0f / ratio);
            } else {
                // Above knee: full compression
                float excess = inputLevelDb - thresholdDb;
                return excess * (1.0f - 1.0f / ratio);
            }
        }
        
        private static float linearToDb(float linear) {
            if (linear <= 1e-6f) return -120.0f;
            return (float) (20.0 * Math.log10(linear));
        }
        
        private static float dbToLinear(float db) {
            return (float) Math.pow(10.0, db / 20.0);
        }
    }
}
