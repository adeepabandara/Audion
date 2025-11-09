package com.audion.audio;

/**
 * Wide Dynamic Range Compressor (WDRC) for clinical hearing aid DSP.
 * 
 * Features:
 * - Configurable compression ratio (2:1 - 3:1)
 * - Threshold typically 40 dB SPL with soft knee (10 dB)
 * - Attack time: 10ms, Release time: 80ms
 * - State-based gain smoothing to prevent artifacts
 * - RMS envelope follower for accurate level detection
 * 
 * Usage: Apply per-band after filtering, before final gain application.
 */
public class WDRCCompressor {
    private static final String TAG = "WDRCCompressor";
    
    // Compression parameters
    private final float threshold;      // Linear threshold (not dB)
    private final float ratio;          // Compression ratio (e.g., 3.0 = 3:1)
    private final float kneeWidth;      // Soft knee width in linear units
    private final float attackCoeff;    // Attack time coefficient
    private final float releaseCoeff;   // Release time coefficient
    
    // State variables
    private float envelope = 0.0f;      // RMS envelope follower
    private float gainState = 1.0f;     // Smoothed gain for artifact prevention
    
    // Statistics
    private long samplesProcessed = 0;
    private long samplesCompressed = 0;
    private float maxGainReduction = 1.0f;
    
    /**
     * Create WDRC compressor with clinical parameters.
     * 
     * @param thresholdDb Compression threshold in dBFS (e.g., -25 dBFS)
     * @param ratio Compression ratio (e.g., 3.0 for 3:1)
     * @param attackMs Attack time in milliseconds (typically 10ms)
     * @param releaseMs Release time in milliseconds (typically 80ms)
     * @param kneeDeltaDb Soft knee width in dB (typically 10 dB)
     * @param sampleRate Sample rate in Hz (48000)
     */
    public WDRCCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, 
                          float kneeDeltaDb, int sampleRate) {
        // Convert dB to linear
        this.threshold = dbToLinear(thresholdDb);
        this.ratio = ratio;
        this.kneeWidth = dbToLinear(kneeDeltaDb);
        
        // Calculate time constants (exponential smoothing)
        // coeff = exp(-1 / (time_seconds * sample_rate))
        this.attackCoeff = (float) Math.exp(-1000.0 / (attackMs * sampleRate));
        this.releaseCoeff = (float) Math.exp(-1000.0 / (releaseMs * sampleRate));
    }
    
    /**
     * Process audio block with WDRC.
     * 
     * @param input Input samples (normalized to ±1.0)
     * @param output Output samples (compressed)
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            float sample = input[i];
            float absSample = Math.abs(sample);
            
            // Update envelope follower (RMS-like)
            if (absSample > envelope) {
                // Attack: fast response to increasing level
                envelope = attackCoeff * envelope + (1.0f - attackCoeff) * absSample;
            } else {
                // Release: slow response to decreasing level
                envelope = releaseCoeff * envelope + (1.0f - releaseCoeff) * absSample;
            }
            
            // Calculate target gain based on envelope level
            float targetGain = calculateGain(envelope);
            
            // Smooth gain transitions to prevent zipper noise
            // Use simple one-pole filter
            float gainSmoothCoeff = 0.95f; // Smooth over ~20 samples @ 48kHz
            gainState = gainSmoothCoeff * gainState + (1.0f - gainSmoothCoeff) * targetGain;
            
            // Apply smoothed gain
            output[i] = sample * gainState;
            
            // Update statistics
            samplesProcessed++;
            if (gainState < 0.999f) {
                samplesCompressed++;
                if (gainState < maxGainReduction) {
                    maxGainReduction = gainState;
                }
            }
        }
    }
    
    /**
     * Calculate compression gain based on envelope level.
     * Uses soft knee for smooth transition around threshold.
     * 
     * @param level Input level (linear, 0-1)
     * @return Gain multiplier (linear, 0-1)
     */
    private float calculateGain(float level) {
        if (level <= threshold / 2.0f) {
            // Below knee: no compression (unity gain)
            return 1.0f;
        }
        
        // Calculate dB values for soft-knee computation
        float levelDb = linearToDb(level);
        float thresholdDb = linearToDb(threshold);
        float kneeDb = linearToDb(kneeWidth);
        
        float gainReductionDb;
        
        if (levelDb < thresholdDb - kneeDb / 2.0f) {
            // Below knee: unity gain
            gainReductionDb = 0.0f;
        } else if (levelDb < thresholdDb + kneeDb / 2.0f) {
            // Inside knee: quadratic interpolation for smooth transition
            float x = levelDb - thresholdDb + kneeDb / 2.0f;
            float kneeGain = x * x / (2.0f * kneeDb);
            gainReductionDb = -kneeGain * (1.0f - 1.0f / ratio);
        } else {
            // Above knee: full compression
            float excess = levelDb - thresholdDb;
            gainReductionDb = -excess * (1.0f - 1.0f / ratio);
        }
        
        // Convert back to linear gain
        return dbToLinear(gainReductionDb);
    }
    
    /**
     * Reset compressor state (e.g., when audio stream restarts).
     */
    public void reset() {
        envelope = 0.0f;
        gainState = 1.0f;
        samplesProcessed = 0;
        samplesCompressed = 0;
        maxGainReduction = 1.0f;
    }
    
    /**
     * Get compression statistics.
     * 
     * @return Statistics object with compression metrics
     */
    public CompressionStats getStats() {
        float compressionPercent = samplesProcessed > 0 
            ? (samplesCompressed * 100.0f / samplesProcessed) 
            : 0.0f;
        
        float maxReductionDb = linearToDb(maxGainReduction);
        
        return new CompressionStats(
            compressionPercent,
            maxReductionDb,
            linearToDb(envelope),
            linearToDb(gainState)
        );
    }
    
    /**
     * Reset statistics counters.
     */
    public void resetStats() {
        samplesProcessed = 0;
        samplesCompressed = 0;
        maxGainReduction = 1.0f;
    }
    
    /**
     * Get current envelope level in dBFS.
     */
    public float getEnvelopeDb() {
        return linearToDb(envelope);
    }
    
    /**
     * Get current gain reduction in dB.
     */
    public float getGainReductionDb() {
        return linearToDb(gainState);
    }
    
    // Utility functions
    
    private static float dbToLinear(float db) {
        return (float) Math.pow(10.0, db / 20.0);
    }
    
    private static float linearToDb(float linear) {
        if (linear <= 1e-6f) {
            return -120.0f; // Minimum dB floor
        }
        return (float) (20.0 * Math.log10(linear));
    }
    
    /**
     * Statistics container for compression analysis.
     */
    public static class CompressionStats {
        public final float compressionPercent;  // % of samples compressed
        public final float maxReductionDb;      // Maximum gain reduction
        public final float currentEnvelopeDb;   // Current envelope level
        public final float currentGainDb;       // Current gain reduction
        
        CompressionStats(float compressionPercent, float maxReductionDb, 
                        float currentEnvelopeDb, float currentGainDb) {
            this.compressionPercent = compressionPercent;
            this.maxReductionDb = maxReductionDb;
            this.currentEnvelopeDb = currentEnvelopeDb;
            this.currentGainDb = currentGainDb;
        }
        
        @Override
        public String toString() {
            return String.format("Compression: %.1f%%, Max reduction: %.1f dB, Envelope: %.1f dBFS, Gain: %.1f dB",
                compressionPercent, maxReductionDb, currentEnvelopeDb, currentGainDb);
        }
    }
}
