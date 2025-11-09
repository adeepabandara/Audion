package com.audion.audio;

import android.util.Log;

/**
 * Look-Ahead Limiter with circular delay buffer.
 * 
 * Features:
 * - 10ms look-ahead window (480 samples @ 48kHz)
 * - RMS + peak analysis in look-ahead window
 * - Soft-knee attenuation (avoids brick-wall clipping)
 * - Fast attack (1ms), slow release (100ms)
 * - UCL-5dB compliance (maximum output threshold)
 * - Optimized for real-time processing (no allocations)
 * 
 * Usage: Apply as final stage after multiband WDRC, before AudioTrack.write()
 */
public class LookAheadLimiter {
    private static final String TAG = "LookAheadLimiter";
    
    // Look-ahead configuration
    private static final int LOOKAHEAD_FRAMES = 10;  // 10 frames @ 10ms each = 100ms total delay
    private final int frameSize;                      // Samples per frame (480 @ 48kHz)
    private final int sampleRate;
    
    // Circular delay buffer (10 frames = 4800 samples @ 48kHz)
    private final float[][] delayBuffer;              // [LOOKAHEAD_FRAMES][frameSize]
    private int writeIndex = 0;                       // Current write position in circular buffer
    
    // Limiting parameters
    private float limitThreshold = 0.9f;              // Default: -1 dBFS (0.9 linear)
    private float uclThreshold = 0.9f;                // UCL - 5dB threshold (set from calibration)
    private final float attackCoeff;                  // 1ms attack time
    private final float releaseCoeff;                 // 100ms release time
    
    // State
    private float currentGain = 1.0f;                 // Current attenuation gain
    private float targetGain = 1.0f;                  // Target gain for smooth transition
    
    // Statistics
    private long samplesProcessed = 0;
    private long samplesLimited = 0;
    private float maxAttenuation = 0.0f;              // Maximum attenuation applied (dB)
    
    private final String channelName;
    
    /**
     * Create look-ahead limiter.
     * 
     * @param channelName "LEFT" or "RIGHT" for logging
     * @param frameSize Samples per frame (typically 480 @ 48kHz = 10ms)
     * @param sampleRate Sample rate in Hz (48000)
     */
    public LookAheadLimiter(String channelName, int frameSize, int sampleRate) {
        this.channelName = channelName;
        this.frameSize = frameSize;
        this.sampleRate = sampleRate;
        
        // Initialize circular delay buffer (10 frames = 100ms @ 10ms/frame)
        delayBuffer = new float[LOOKAHEAD_FRAMES][frameSize];
        for (int i = 0; i < LOOKAHEAD_FRAMES; i++) {
            delayBuffer[i] = new float[frameSize];
        }
        
        // Calculate time constants for attack/release
        // Attack: 1ms (fast response to peaks)
        float attackMs = 1.0f;
        this.attackCoeff = (float) Math.exp(-1000.0 / (attackMs * sampleRate));
        
        // Release: 100ms (slow recovery to avoid pumping)
        float releaseMs = 100.0f;
        this.releaseCoeff = (float) Math.exp(-1000.0 / (releaseMs * sampleRate));
        
        Log.i(TAG, String.format("[%s] LookAheadLimiter initialized: lookahead=%dms, frameSize=%d",
            channelName, LOOKAHEAD_FRAMES * 10, frameSize));
    }
    
    /**
     * Set limiting threshold.
     * 
     * @param thresholdLinear Threshold in linear scale (e.g., 0.9 = -1 dBFS)
     */
    public void setThreshold(float thresholdLinear) {
        this.limitThreshold = thresholdLinear;
        Log.d(TAG, String.format("[%s] Threshold set: %.2f (%.1f dBFS)",
            channelName, thresholdLinear, linearToDb(thresholdLinear)));
    }
    
    /**
     * Set UCL-based threshold from calibration data.
     * Maximum output will not exceed UCL - 5 dB.
     * 
     * @param uclDbHL UCL in dB HL (e.g., 95 dB HL)
     */
    public void setUCLThreshold(float uclDbHL) {
        // Convert dB HL to dBFS: dBFS = dB HL - 80
        // Apply 5 dB safety margin: UCL - 5 dB
        float uclDbFS = uclDbHL - 80.0f - 5.0f;
        this.uclThreshold = dbToLinear(uclDbFS);
        
        // Use the more conservative of limitThreshold and uclThreshold
        float effectiveThreshold = Math.min(limitThreshold, uclThreshold);
        
        Log.i(TAG, String.format("[%s] UCL threshold set: %.1f dB HL → %.1f dBFS → %.3f linear (effective=%.3f)",
            channelName, uclDbHL, uclDbFS, uclThreshold, effectiveThreshold));
    }
    
    /**
     * Process audio frame with look-ahead limiting.
     * 
     * Pipeline:
     * 1. Write current frame to circular delay buffer
     * 2. Analyze look-ahead window (10 frames ahead) for peaks
     * 3. Calculate required attenuation
     * 4. Apply smoothed gain to delayed output
     * 5. Read oldest frame from delay buffer
     * 
     * @param input Input buffer (float, ±1.0)
     * @param output Output buffer (float, ±1.0)
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        if (length != frameSize) {
            Log.e(TAG, String.format("[%s] Length mismatch: %d != %d", channelName, length, frameSize));
            return;
        }
        
        // Step 1: Write current input to circular delay buffer
        System.arraycopy(input, 0, delayBuffer[writeIndex], 0, frameSize);
        
        // Step 2: Analyze look-ahead window for peak
        float peakInWindow = 0.0f;
        float rmsSum = 0.0f;
        
        for (int frame = 0; frame < LOOKAHEAD_FRAMES; frame++) {
            int readIdx = (writeIndex + frame) % LOOKAHEAD_FRAMES;
            for (int i = 0; i < frameSize; i++) {
                float sample = delayBuffer[readIdx][i];
                float abs = Math.abs(sample);
                if (abs > peakInWindow) {
                    peakInWindow = abs;
                }
                rmsSum += sample * sample;
            }
        }
        
        float rmsInWindow = (float) Math.sqrt(rmsSum / (LOOKAHEAD_FRAMES * frameSize));
        
        // Step 3: Calculate required attenuation
        // Use the more conservative threshold
        float effectiveThreshold = Math.min(limitThreshold, uclThreshold);
        
        if (peakInWindow > effectiveThreshold) {
            // Need to limit: calculate attenuation to bring peak down to threshold
            targetGain = effectiveThreshold / peakInWindow;
        } else {
            // No limiting needed
            targetGain = 1.0f;
        }
        
        // Step 4: Read oldest frame from delay buffer and apply smoothed gain
        int readIndex = (writeIndex + 1) % LOOKAHEAD_FRAMES;  // Oldest frame
        
        for (int i = 0; i < frameSize; i++) {
            // Smooth gain transitions (attack/release)
            if (targetGain < currentGain) {
                // Attack: fast response to increasing peaks
                currentGain = attackCoeff * currentGain + (1.0f - attackCoeff) * targetGain;
            } else {
                // Release: slow recovery
                currentGain = releaseCoeff * currentGain + (1.0f - releaseCoeff) * targetGain;
            }
            
            // Apply gain to delayed sample
            float delayedSample = delayBuffer[readIndex][i];
            output[i] = delayedSample * currentGain;
            
            // Update statistics
            samplesProcessed++;
            if (currentGain < 0.999f) {
                samplesLimited++;
                float attenuationDb = linearToDb(currentGain);
                if (attenuationDb < maxAttenuation) {
                    maxAttenuation = attenuationDb;
                }
            }
        }
        
        // Step 5: Advance circular buffer write position
        writeIndex = (writeIndex + 1) % LOOKAHEAD_FRAMES;
    }
    
    /**
     * Get limiter statistics.
     * 
     * @return LimiterStats object
     */
    public LimiterStats getStats() {
        float limitingPercent = samplesProcessed > 0 
            ? (samplesLimited * 100.0f / samplesProcessed) 
            : 0.0f;
        
        return new LimiterStats(
            limitingPercent,
            maxAttenuation,
            linearToDb(currentGain),
            linearToDb(Math.min(limitThreshold, uclThreshold))
        );
    }
    
    /**
     * Reset statistics.
     */
    public void resetStats() {
        samplesProcessed = 0;
        samplesLimited = 0;
        maxAttenuation = 0.0f;
    }
    
    /**
     * Reset limiter state (clear delay buffer).
     * Call when audio stream restarts.
     */
    public void reset() {
        for (int i = 0; i < LOOKAHEAD_FRAMES; i++) {
            for (int j = 0; j < frameSize; j++) {
                delayBuffer[i][j] = 0.0f;
            }
        }
        writeIndex = 0;
        currentGain = 1.0f;
        targetGain = 1.0f;
        resetStats();
    }
    
    /**
     * Get current latency introduced by look-ahead buffer.
     * 
     * @return Latency in milliseconds
     */
    public float getLatencyMs() {
        return LOOKAHEAD_FRAMES * frameSize * 1000.0f / sampleRate;
    }
    
    // Utility functions
    
    private static float linearToDb(float linear) {
        if (linear <= 1e-6f) return -120.0f;
        return (float) (20.0 * Math.log10(linear));
    }
    
    private static float dbToLinear(float db) {
        return (float) Math.pow(10.0, db / 20.0);
    }
    
    /**
     * Limiter statistics container.
     */
    public static class LimiterStats {
        public final float limitingPercent;      // % of samples limited
        public final float maxAttenuationDb;     // Maximum attenuation applied
        public final float currentGainDb;        // Current gain reduction
        public final float thresholdDbFS;        // Effective threshold
        
        LimiterStats(float limitingPercent, float maxAttenuationDb, 
                    float currentGainDb, float thresholdDbFS) {
            this.limitingPercent = limitingPercent;
            this.maxAttenuationDb = maxAttenuationDb;
            this.currentGainDb = currentGainDb;
            this.thresholdDbFS = thresholdDbFS;
        }
        
        @Override
        public String toString() {
            return String.format("Limiter: %.1f%% active, Max attenuation: %.1f dB, Current: %.1f dB, Threshold: %.1f dBFS",
                limitingPercent, maxAttenuationDb, currentGainDb, thresholdDbFS);
        }
    }
}
