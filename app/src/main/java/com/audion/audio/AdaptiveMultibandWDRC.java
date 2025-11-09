package com.audion.audio;

/**
 * AdaptiveMultibandWDRC - Intelligent multiband compression with adaptive ratios.
 * 
 * Features:
 * - 5 frequency bands (250-8000 Hz)
 * - Adaptive compression ratios based on input level
 * - Soft-knee transition (10 dB width)
 * - Fast attack (3ms), moderate release (100ms)
 * - Transparent compression for clean high-gain scenarios
 * 
 * Compression strategy:
 * - Low levels (<-45 dBFS): ratio 1.5:1 (gentle)
 * - Mid levels (-45 to -25 dBFS): ratio 2.5:1 (moderate)
 * - High levels (>-25 dBFS): ratio 4:1 (aggressive)
 */
public class AdaptiveMultibandWDRC {
    private static final String TAG = "AdaptiveMultibandWDRC";
    
    // Band configuration
    private static final int NUM_BANDS = 5;
    private static final float[][] BAND_RANGES = {
        {250f, 750f},    // Band 0: Low (vowels)
        {750f, 1500f},   // Band 1: Mid-low (formants)
        {1500f, 3000f},  // Band 2: Mid-high (clarity)
        {3000f, 6000f},  // Band 3: High (fricatives)
        {6000f, 8000f}   // Band 4: Very high (sibilants)
    };
    
    // Compression parameters
    private static final float THRESHOLD_DB = -45.0f;     // Start compression here
    private static final float KNEE_WIDTH_DB = 10.0f;     // Soft knee width
    private static final float RATIO_LOW = 1.5f;          // Gentle compression
    private static final float RATIO_MID = 2.5f;          // Moderate compression
    private static final float RATIO_HIGH = 4.0f;         // Aggressive compression
    
    // Level breakpoints for adaptive ratios
    private static final float LOW_TO_MID_THRESHOLD = -45.0f;
    private static final float MID_TO_HIGH_THRESHOLD = -25.0f;
    
    // Filters and compressors
    private final BiquadFilter[] bandFilters;
    private final AdaptiveCompressor[] compressors;
    
    // Buffers
    private final float[][] bandBuffers;
    private final int frameSize;
    private final int sampleRate;
    private final String channelName;
    
    // Statistics
    private long framesProcessed = 0;
    private final float[] maxGainReduction = new float[NUM_BANDS];
    private final int[] ratioUsage = new int[3];  // Low, Mid, High
    
    public AdaptiveMultibandWDRC(String channelName, int frameSize, int sampleRate) {
        this.channelName = channelName;
        this.frameSize = frameSize;
        this.sampleRate = sampleRate;
        
        // Initialize bandpass filters
        bandFilters = new BiquadFilter[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            bandFilters[i] = BiquadFilter.createBandpass(
                BAND_RANGES[i][0], 
                BAND_RANGES[i][1], 
                sampleRate
            );
        }
        
        // Initialize adaptive compressors
        compressors = new AdaptiveCompressor[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            compressors[i] = new AdaptiveCompressor(
                THRESHOLD_DB,
                3.0f,    // attack_ms
                100.0f,  // release_ms
                KNEE_WIDTH_DB,
                sampleRate
            );
        }
        
        // Pre-allocate buffers
        bandBuffers = new float[NUM_BANDS][frameSize];
        
        // Logging disabled to avoid Android dependency
        // System.out.println(String.format("[%s] AdaptiveMultibandWDRC initialized", channelName));
    }
    
    /**
     * Process audio with adaptive multiband compression.
     * 
     * @param input Input buffer
     * @param output Output buffer
     * @param length Number of samples
     */
    public void process(float[] input, float[] output, int length) {
        if (length > frameSize) {
            // Logging disabled: Length exceeds frameSize
            return;
        }
        
        // Step 1: Split into frequency bands
        for (int band = 0; band < NUM_BANDS; band++) {
            bandFilters[band].process(input, bandBuffers[band], length);
        }
        
        // Step 2: Apply adaptive compression per band
        for (int band = 0; band < NUM_BANDS; band++) {
            float gainReduction = compressors[band].process(
                bandBuffers[band], 
                bandBuffers[band], 
                length
            );
            
            if (gainReduction > maxGainReduction[band]) {
                maxGainReduction[band] = gainReduction;
            }
        }
        
        // Step 3: Sum bands
        for (int i = 0; i < length; i++) {
            float sum = 0.0f;
            for (int band = 0; band < NUM_BANDS; band++) {
                sum += bandBuffers[band][i];
            }
            output[i] = sum;
        }
        
        // Step 4: Normalize to prevent clipping from band summation
        float peak = 0.0f;
        for (int i = 0; i < length; i++) {
            float abs = Math.abs(output[i]);
            if (abs > peak) peak = abs;
        }
        
        // Keep peaks ≤ 0.8 for headroom before limiter
        if (peak > 0.8f) {
            float scale = 0.8f / peak;
            for (int i = 0; i < length; i++) {
                output[i] *= scale;
            }
        }
        
        framesProcessed++;
    }
    
    /**
     * Get compression statistics.
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] Adaptive WDRC Stats (frames=%d):\n", 
            channelName, framesProcessed));
        
        for (int band = 0; band < NUM_BANDS; band++) {
            sb.append(String.format("  Band %d [%.0f-%.0f Hz]: Max GR=%.1f dB\n",
                band, BAND_RANGES[band][0], BAND_RANGES[band][1], 
                maxGainReduction[band]));
        }
        
        long totalRatio = ratioUsage[0] + ratioUsage[1] + ratioUsage[2];
        if (totalRatio > 0) {
            sb.append(String.format("  Ratio usage: Low=%.1f%%, Mid=%.1f%%, High=%.1f%%\n",
                ratioUsage[0] * 100.0f / totalRatio,
                ratioUsage[1] * 100.0f / totalRatio,
                ratioUsage[2] * 100.0f / totalRatio));
        }
        
        return sb.toString();
    }
    
    /**
     * Reset statistics.
     */
    public void reset() {
        framesProcessed = 0;
        for (int i = 0; i < NUM_BANDS; i++) {
            maxGainReduction[i] = 0.0f;
        }
        ratioUsage[0] = ratioUsage[1] = ratioUsage[2] = 0;
    }
    
    /**
     * Adaptive compressor with level-dependent ratio.
     */
    private class AdaptiveCompressor {
        private final float thresholdDb;
        private final float kneeWidthDb;
        private final float attackCoeff;
        private final float releaseCoeff;
        
        private float envelope = 0.0f;
        private float gainSmooth = 1.0f;
        
        AdaptiveCompressor(float thresholdDb, float attackMs, float releaseMs, 
                          float kneeWidthDb, int sampleRate) {
            this.thresholdDb = thresholdDb;
            this.kneeWidthDb = kneeWidthDb;
            
            this.attackCoeff = (float) Math.exp(-1000.0 / (attackMs * sampleRate));
            this.releaseCoeff = (float) Math.exp(-1000.0 / (releaseMs * sampleRate));
        }
        
        float process(float[] input, float[] output, int length) {
            float maxGR = 0.0f;
            
            for (int i = 0; i < length; i++) {
                float sample = input[i];
                float absSample = Math.abs(sample);
                
                // Update envelope
                if (absSample > envelope) {
                    envelope = attackCoeff * envelope + (1.0f - attackCoeff) * absSample;
                } else {
                    envelope = releaseCoeff * envelope + (1.0f - releaseCoeff) * absSample;
                }
                
                // Calculate input level
                float inputLevelDb = linearToDb(envelope);
                
                // Select ratio adaptively based on level
                float ratio = selectRatio(inputLevelDb);
                
                // Calculate gain reduction with soft knee
                float gainReductionDb = calculateGainReduction(inputLevelDb, ratio);
                
                // Convert to linear and smooth
                float targetGain = dbToLinear(-gainReductionDb);
                gainSmooth = 0.95f * gainSmooth + 0.05f * targetGain;
                
                // Apply gain
                output[i] = sample * gainSmooth;
                
                if (gainReductionDb > maxGR) {
                    maxGR = gainReductionDb;
                }
            }
            
            return maxGR;
        }
        
        /**
         * Select compression ratio based on input level.
         */
        private float selectRatio(float inputLevelDb) {
            if (inputLevelDb < LOW_TO_MID_THRESHOLD) {
                ratioUsage[0]++;
                return RATIO_LOW;
            } else if (inputLevelDb < MID_TO_HIGH_THRESHOLD) {
                ratioUsage[1]++;
                return RATIO_MID;
            } else {
                ratioUsage[2]++;
                return RATIO_HIGH;
            }
        }
        
        /**
         * Calculate gain reduction with soft knee.
         */
        private float calculateGainReduction(float inputLevelDb, float ratio) {
            float kneeStart = thresholdDb - kneeWidthDb / 2.0f;
            float kneeEnd = thresholdDb + kneeWidthDb / 2.0f;
            
            if (inputLevelDb < kneeStart) {
                return 0.0f;
            } else if (inputLevelDb < kneeEnd) {
                // Soft knee: quadratic transition
                float x = inputLevelDb - kneeStart;
                float kneeFactor = x * x / (2.0f * kneeWidthDb);
                return kneeFactor * (1.0f - 1.0f / ratio);
            } else {
                // Full compression
                float excess = inputLevelDb - thresholdDb;
                return excess * (1.0f - 1.0f / ratio);
            }
        }
        
        private float linearToDb(float linear) {
            if (linear <= 1e-6f) return -120.0f;
            return (float) (20.0 * Math.log10(linear));
        }
        
        private float dbToLinear(float db) {
            return (float) Math.pow(10.0, db / 20.0);
        }
    }
}
