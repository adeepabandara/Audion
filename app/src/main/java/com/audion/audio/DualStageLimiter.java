package com.audion.audio;

/**
 * DualStageLimiter - Two-stage limiting for maximum transparency.
 * 
 * Stage 1: Look-ahead hard limiter at -3 dBFS
 *   - 10ms look-ahead window for transparent limiting
 *   - Prevents peaks from reaching soft-clipper
 * 
 * Stage 2: Soft-clipper at -1 dBFS
 *   - Hyperbolic tangent (tanh) for smooth saturation
 *   - Final safety net for any residual peaks
 * 
 * Benefits:
 * - Stage 1 catches most peaks transparently
 * - Stage 2 adds harmonic warmth rather than harsh distortion
 * - UCL-compliant output levels
 */
public class DualStageLimiter {
    private static final String TAG = "DualStageLimiter";
    
    // Stage 1: Look-ahead limiter
    private static final float STAGE1_THRESHOLD_DB = -3.0f;
    private static final float STAGE1_THRESHOLD_LINEAR = dbToLinear(STAGE1_THRESHOLD_DB);
    private static final int LOOKAHEAD_MS = 10;
    
    // Stage 2: Soft clipper
    private static final float STAGE2_THRESHOLD_DB = -1.0f;
    private static final float STAGE2_THRESHOLD_LINEAR = dbToLinear(STAGE2_THRESHOLD_DB);
    private static final float SOFT_CLIP_DRIVE = 1.2f;  // Gentle overdrive
    
    // Look-ahead buffer
    private final float[] lookaheadBuffer;
    private int writeIndex = 0;
    private int readIndex;
    private final int lookaheadSamples;
    
    // Attack/release for stage 1
    private final float attackCoeff;
    private final float releaseCoeff;
    private float gainEnvelope = 1.0f;
    
    // Statistics
    private long framesProcessed = 0;
    private long stage1_activations = 0;
    private long stage2_activations = 0;
    private float stage1_maxGainReduction = 0.0f;
    private float stage2_maxClipping = 0.0f;
    
    private final String channelName;
    
    public DualStageLimiter(String channelName, int sampleRate) {
        this.channelName = channelName;
        
        // Calculate look-ahead buffer size
        lookaheadSamples = (sampleRate * LOOKAHEAD_MS) / 1000;
        lookaheadBuffer = new float[lookaheadSamples];
        readIndex = 0;
        
        // Attack: 0.5ms (fast), Release: 50ms (moderate)
        float attackMs = 0.5f;
        float releaseMs = 50.0f;
        attackCoeff = (float) Math.exp(-1000.0 / (attackMs * sampleRate));
        releaseCoeff = (float) Math.exp(-1000.0 / (releaseMs * sampleRate));
    }
    
    /**
     * Process audio through dual-stage limiter.
     * 
     * @param input Input buffer
     * @param output Output buffer
     * @param length Number of samples
     */
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            float sample = input[i];
            
            // === STAGE 1: Look-ahead hard limiter ===
            
            // Write to look-ahead buffer
            lookaheadBuffer[writeIndex] = sample;
            writeIndex = (writeIndex + 1) % lookaheadSamples;
            
            // Find peak in look-ahead window
            float peak = 0.0f;
            for (int j = 0; j < lookaheadSamples; j++) {
                float abs = Math.abs(lookaheadBuffer[j]);
                if (abs > peak) peak = abs;
            }
            
            // Calculate required gain reduction
            float targetGain = 1.0f;
            if (peak > STAGE1_THRESHOLD_LINEAR) {
                targetGain = STAGE1_THRESHOLD_LINEAR / peak;
                stage1_activations++;
                
                float gainReductionDb = linearToDb(targetGain);
                if (gainReductionDb < stage1_maxGainReduction) {
                    stage1_maxGainReduction = gainReductionDb;
                }
            }
            
            // Smooth gain changes
            if (targetGain < gainEnvelope) {
                gainEnvelope = attackCoeff * gainEnvelope + (1.0f - attackCoeff) * targetGain;
            } else {
                gainEnvelope = releaseCoeff * gainEnvelope + (1.0f - releaseCoeff) * targetGain;
            }
            
            // Read from look-ahead buffer and apply gain
            float delayedSample = lookaheadBuffer[readIndex];
            readIndex = (readIndex + 1) % lookaheadSamples;
            
            float stage1Output = delayedSample * gainEnvelope;
            
            // === STAGE 2: Soft clipper ===
            
            float stage2Output;
            float absSample = Math.abs(stage1Output);
            
            if (absSample > STAGE2_THRESHOLD_LINEAR) {
                // Apply tanh soft-clipping
                float normalized = stage1Output / STAGE2_THRESHOLD_LINEAR;
                float driven = normalized * SOFT_CLIP_DRIVE;
                float clipped = (float) Math.tanh(driven) / (float) Math.tanh(SOFT_CLIP_DRIVE);
                stage2Output = clipped * STAGE2_THRESHOLD_LINEAR;
                
                stage2_activations++;
                
                float clippingAmount = absSample - STAGE2_THRESHOLD_LINEAR;
                if (clippingAmount > stage2_maxClipping) {
                    stage2_maxClipping = clippingAmount;
                }
            } else {
                stage2Output = stage1Output;
            }
            
            output[i] = stage2Output;
        }
        
        framesProcessed++;
    }
    
    /**
     * Get limiting statistics.
     */
    public String getStatistics() {
        if (framesProcessed == 0) return String.format("[%s] No frames processed", channelName);
        
        long totalSamples = framesProcessed * lookaheadBuffer.length;
        float stage1_engagement = (stage1_activations * 100.0f) / totalSamples;
        float stage2_engagement = (stage2_activations * 100.0f) / totalSamples;
        
        return String.format(
            "[%s] DualStageLimiter Stats:\n" +
            "  Stage 1 (Look-ahead): %.2f%% engaged, max GR=%.1f dB\n" +
            "  Stage 2 (Soft-clip): %.2f%% engaged, max clip=%.3f\n" +
            "  Total frames: %d",
            channelName,
            stage1_engagement, stage1_maxGainReduction,
            stage2_engagement, stage2_maxClipping,
            framesProcessed
        );
    }
    
    /**
     * Reset statistics.
     */
    public void reset() {
        framesProcessed = 0;
        stage1_activations = 0;
        stage2_activations = 0;
        stage1_maxGainReduction = 0.0f;
        stage2_maxClipping = 0.0f;
        
        // Clear look-ahead buffer
        for (int i = 0; i < lookaheadSamples; i++) {
            lookaheadBuffer[i] = 0.0f;
        }
        gainEnvelope = 1.0f;
    }
    
    /**
     * Check if limiter is currently active.
     */
    public boolean isActive() {
        return gainEnvelope < 0.99f;
    }
    
    /**
     * Get current gain reduction in dB.
     */
    public float getCurrentGainReduction() {
        return linearToDb(gainEnvelope);
    }
    
    // === Utility methods ===
    
    private static float linearToDb(float linear) {
        if (linear <= 1e-6f) return -120.0f;
        return (float) (20.0 * Math.log10(linear));
    }
    
    private static float dbToLinear(float db) {
        return (float) Math.pow(10.0, db / 20.0);
    }
}
