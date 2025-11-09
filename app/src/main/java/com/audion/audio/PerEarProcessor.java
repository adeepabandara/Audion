package com.audion.audio;

import android.util.Log;

/**
 * Per-Ear 4-Band Filterbank Processor for frequency-specific amplification.
 * 
 * Architecture:
 * - 4 bandpass filters covering 250-6000 Hz
 * - Per-band gain based on audiogram thresholds
 * - Optional compression for loud sounds
 * - Soft clipping using tanh() function
 * - RMS monitoring for level tracking
 * 
 * Processing: input -> 4-band split -> per-band gains -> sum -> compression -> tanh() clip
 */
public class PerEarProcessor {
    private static final String TAG = "PerEarProcessor";
    
    // PHASE 4: Expanded to 5-band filterbank for 8 kHz coverage
    private static final int NUM_BANDS = 5;
    private static final float[][] BAND_RANGES = {
        {250f, 750f},    // Band 0: Low frequencies (vowels, low-frequency speech energy)
        {750f, 1500f},   // Band 1: Mid-low frequencies (vowel formants)
        {1500f, 3000f},  // Band 2: Mid-high frequencies (clarity zone, /sh/, /ch/)
        {3000f, 6000f},  // Band 3: High frequencies (fricatives /s/, /z/)
        {6000f, 8000f}   // Band 4: Very high frequencies (/s/, /f/, /th/ - PHASE 4 ADDITION)
    };
    
    private final BandPassFilter[] filters;
    private final float[] bandGains;
    private final float[][] bandBuffers;
    private final int frameSize;
    private final int sampleRate;
    private final String earSide;
    
    // Phase 3: WDRC compressors (one per band)
    private final WDRCCompressor[] compressors;
    private boolean wdrcEnabled = false;
    
    // Phase 3: Per-band UCL limiting
    private final float[] bandUCLLimits;  // UCL - 5 dB for each band (linear)
    private boolean uclLimitingEnabled = false;
    private int[] limiterActivations = new int[NUM_BANDS];
    
    // Phase 3: Global limiter (0.97 hard clip)
    private static final float GLOBAL_LIMITER_THRESHOLD = 0.97f;
    private int globalLimiterActivations = 0;
    
    // RMS monitoring
    private double sumSquares = 0.0;
    private int sampleCount = 0;
    private static final int RMS_WINDOW_SAMPLES = 48000;  // 1 second @ 48kHz
    
    /**
     * Create per-ear processor with 4-band filterbank.
     * 
     * @param earSide "LEFT" or "RIGHT"
     * @param frameSize Number of samples per processing frame (typically 480)
     * @param sampleRate Sample rate in Hz (typically 48000)
     */
    public PerEarProcessor(String earSide, int frameSize, int sampleRate) {
        this.earSide = earSide;
        this.frameSize = frameSize;
        this.sampleRate = sampleRate;
        
        // Initialize filters for 4 bands
        filters = new BandPassFilter[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            filters[i] = new BandPassFilter(BAND_RANGES[i][0], BAND_RANGES[i][1], sampleRate);
        }
        
        // Initialize gain array (unity gain by default)
        bandGains = new float[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            bandGains[i] = 1.0f;
        }
        
        // Pre-allocate band buffers (avoid allocations in processing loop)
        bandBuffers = new float[NUM_BANDS][frameSize];
        
        // Phase 3: Initialize WDRC compressors for each band
        compressors = new WDRCCompressor[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            // Create compressor with clinical parameters:
            // - Threshold: -25 dBFS (~ 40 dB SPL for typical input)
            // - Ratio: 2.5:1 (moderate compression)
            // - Attack: 10ms, Release: 80ms
            // - Knee: 10 dB
            compressors[i] = new WDRCCompressor(-25.0f, 2.5f, 10.0f, 80.0f, 10.0f, sampleRate);
        }
        
        // Initialize UCL limits (will be set later from calibration data)
        bandUCLLimits = new float[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            bandUCLLimits[i] = 0.95f;  // Default: 95% of full scale
        }
        
        Log.i(TAG, String.format("[%s] PerEarProcessor initialized: %d bands (PHASE 4: added 6-8kHz), frameSize=%d, sampleRate=%d",
            earSide, NUM_BANDS, frameSize, sampleRate));
        Log.i(TAG, String.format("[%s] Phase 3: WDRC compressors initialized (threshold=-25dBFS, ratio=2.5:1)", 
            earSide));
    }
    
    /**
     * Set per-band linear gains.
     * 
     * @param gains Array of 4 linear gain factors (e.g., [1.5, 2.0, 2.5, 3.0])
     */
    public void setBandGains(float[] gains) {
        if (gains == null || gains.length != NUM_BANDS) {
            Log.e(TAG, String.format("[%s] Invalid gains array length: %d (expected %d)",
                earSide, gains != null ? gains.length : 0, NUM_BANDS));
            return;
        }
        
        System.arraycopy(gains, 0, bandGains, 0, NUM_BANDS);
        
        Log.i(TAG, String.format("[%s] Band gains set: B1=%.2fx B2=%.2fx B3=%.2fx B4=%.2fx",
            earSide, bandGains[0], bandGains[1], bandGains[2], bandGains[3]));
    }
    
    /**
     * Process audio frame through 4-band filterbank with Phase 3 enhancements.
     * 
     * Pipeline: 
     * 1. Input -> 4-band split (BPF)
     * 2. Per-band WDRC compression (optional)
     * 3. Per-band gains
     * 4. Per-band UCL limiting
     * 5. Sum all bands
     * 6. Tanh() soft clipping
     * 7. Global 0.97 hard limiter
     * 
     * @param input Input buffer (float samples, normalized +-1.0)
     * @param output Output buffer (float samples, normalized +-1.0)
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        // Step 1: Split input into 4 frequency bands
        for (int band = 0; band < NUM_BANDS; band++) {
            filters[band].process(input, bandBuffers[band], length);
        }
        
        // Step 2: Apply WDRC compression per band (Phase 3)
        if (wdrcEnabled) {
            for (int band = 0; band < NUM_BANDS; band++) {
                compressors[band].process(bandBuffers[band], bandBuffers[band], length);
            }
        }
        
        // Step 3: Apply per-band gains
        for (int band = 0; band < NUM_BANDS; band++) {
            for (int i = 0; i < length; i++) {
                bandBuffers[band][i] *= bandGains[band];
            }
        }
        
        // Step 4: Per-band UCL limiting (Phase 3)
        if (uclLimitingEnabled) {
            for (int band = 0; band < NUM_BANDS; band++) {
                for (int i = 0; i < length; i++) {
                    float sample = bandBuffers[band][i];
                    float absSample = Math.abs(sample);
                    
                    if (absSample > bandUCLLimits[band]) {
                        bandBuffers[band][i] = Math.signum(sample) * bandUCLLimits[band];
                        limiterActivations[band]++;
                    }
                }
            }
        }
        
        // Step 5: Sum all bands
        for (int i = 0; i < length; i++) {
            float sum = 0.0f;
            for (int band = 0; band < NUM_BANDS; band++) {
                sum += bandBuffers[band][i];
            }
            output[i] = sum;
        }
        
        // Step 6: Soft clipping using tanh() to prevent hard clipping
        for (int i = 0; i < length; i++) {
            // tanh() maps (-inf, +inf) -> (-1, +1) with smooth saturation
            output[i] = (float) Math.tanh(output[i]);
        }
        
        // Step 7: Global hard limiter at 0.97 (Phase 3)
        for (int i = 0; i < length; i++) {
            float absSample = Math.abs(output[i]);
            if (absSample > GLOBAL_LIMITER_THRESHOLD) {
                output[i] = Math.signum(output[i]) * GLOBAL_LIMITER_THRESHOLD;
                globalLimiterActivations++;
            }
        }
        
        // Step 8: Update RMS statistics
        for (int i = 0; i < length; i++) {
            sumSquares += output[i] * output[i];
            sampleCount++;
        }
    }
    
    /**
     * Enable or disable WDRC compression.
     * 
     * @param enabled true to enable WDRC per band
     */
    public void setWDRCEnabled(boolean enabled) {
        this.wdrcEnabled = enabled;
        Log.i(TAG, String.format("[%s] WDRC compression %s", earSide, enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Enable or disable per-band UCL limiting.
     * 
     * @param enabled true to enable UCL-based limiting
     */
    public void setUCLLimitingEnabled(boolean enabled) {
        this.uclLimitingEnabled = enabled;
        Log.i(TAG, String.format("[%s] UCL limiting %s", earSide, enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Set per-band UCL limits (UCL - 5 dB).
     * 
     * @param uclLimitsDb Array of 4 UCL values in dBFS
     */
    public void setBandUCLLimits(float[] uclLimitsDb) {
        if (uclLimitsDb == null || uclLimitsDb.length != NUM_BANDS) {
            Log.e(TAG, String.format("[%s] Invalid UCL limits array", earSide));
            return;
        }
        
        // Convert dBFS to linear and apply -5 dB safety margin
        for (int i = 0; i < NUM_BANDS; i++) {
            float uclLinear = dbToLinear(uclLimitsDb[i]);
            bandUCLLimits[i] = uclLinear * dbToLinear(-5.0f);  // UCL - 5 dB
        }
        
        Log.i(TAG, String.format("[%s] UCL limits set: %.2f, %.2f, %.2f, %.2f",
            earSide, bandUCLLimits[0], bandUCLLimits[1], bandUCLLimits[2], bandUCLLimits[3]));
    }
    
    /**
     * Set compression ratio for all bands (scene-adaptive).
     * 
     * @param ratio Compression ratio (e.g., 2.5 for 2.5:1)
     */
    public void setCompressionRatio(float ratio) {
        // Note: Current WDRCCompressor doesn't support runtime ratio changes
        // This is a placeholder for future enhancement
        Log.d(TAG, String.format("[%s] Compression ratio set to %.1f:1 (requires compressor re-init)",
            earSide, ratio));
    }
    
    /**
     * Get WDRC compression statistics for a specific band.
     * 
     * @param band Band index (0-3)
     * @return Compression statistics
     */
    public WDRCCompressor.CompressionStats getCompressionStats(int band) {
        if (band < 0 || band >= NUM_BANDS) {
            return null;
        }
        return compressors[band].getStats();
    }
    
    /**
     * Get limiter activation counts.
     * 
     * @return Array: [band0, band1, band2, band3, global]
     */
    public int[] getLimiterActivations() {
        int[] activations = new int[NUM_BANDS + 1];
        System.arraycopy(limiterActivations, 0, activations, 0, NUM_BANDS);
        activations[NUM_BANDS] = globalLimiterActivations;
        return activations;
    }
    
    /**
     * Reset limiter activation counters.
     */
    public void resetLimiterActivations() {
        for (int i = 0; i < NUM_BANDS; i++) {
            limiterActivations[i] = 0;
        }
        globalLimiterActivations = 0;
    }
    
    /**
     * Reset WDRC compressor statistics.
     */
    public void resetCompressionStats() {
        for (WDRCCompressor compressor : compressors) {
            compressor.resetStats();
        }
    }
    
    /**
     * Get current RMS level and reset accumulator.
     * Call this periodically (e.g., once per second) to monitor signal levels.
     * 
     * @return RMS level in dBFS (0 dBFS = full scale), or -96 dBFS if silent
     */
    public float getRMSAndReset() {
        if (sampleCount == 0) {
            return -96.0f;  // Silence
        }
        
        double rms = Math.sqrt(sumSquares / sampleCount);
        float rmsDb = (float) (20.0 * Math.log10(rms + 1e-10));  // Add epsilon to avoid log(0)
        
        // Reset accumulator
        sumSquares = 0.0;
        sampleCount = 0;
        
        return rmsDb;
    }
    
    /**
     * Reset all filter states (clear history).
     * Call when audio stream is interrupted or restarted.
     */
    public void reset() {
        for (BandPassFilter filter : filters) {
            filter.reset();
        }
        for (WDRCCompressor compressor : compressors) {
            compressor.reset();
        }
        sumSquares = 0.0;
        sampleCount = 0;
        resetLimiterActivations();
        Log.i(TAG, String.format("[%s] Processor reset", earSide));
    }
    
    public String getEarSide() {
        return earSide;
    }
    
    public float[] getBandGains() {
        return bandGains.clone();
    }
    
    // Utility functions
    
    private static float dbToLinear(float db) {
        return (float) Math.pow(10.0, db / 20.0);
    }
}
