package com.audion.audio;

import android.util.Log;

/**
 * Adaptive Feedback Canceller using Least Mean Squares (LMS) algorithm.
 * 
 * Prevents acoustic feedback (whistling) at high gains by modeling the 
 * feedback path and subtracting predicted feedback from the input signal.
 * 
 * Algorithm:
 * 1. error[n] = mic[n] - Σ(w[k] × output[n-k])  (predict and subtract feedback)
 * 2. w[k] += μ × error[n] × output[n-k]         (adapt filter weights)
 * 3. Limit |w[k]| to prevent oscillation
 * 
 * Usage: Apply before all gain stages in the audio pipeline.
 */
public class FeedbackCanceller {
    private static final String TAG = "FeedbackCanceller";
    
    private static final int NUM_TAPS = 32;           // Filter length (32 taps ≈ 0.67ms @ 48kHz)
    private static final float STEP_SIZE = 0.0001f;   // LMS learning rate (μ)
    private static final float MAX_COEFF = 0.5f;      // Maximum coefficient magnitude
    
    // Adaptive filter state
    private final float[] weights;                    // Filter coefficients w[0..31]
    private final float[] outputHistory;              // Circular buffer of past outputs
    private int historyIndex = 0;                     // Current position in circular buffer
    
    // Statistics
    private long samplesProcessed = 0;
    private float averageErrorPower = 0.0f;
    private float averageWeightMagnitude = 0.0f;
    private boolean converged = false;
    
    /**
     * Create adaptive feedback canceller.
     */
    public FeedbackCanceller() {
        weights = new float[NUM_TAPS];
        outputHistory = new float[NUM_TAPS];
        
        // Initialize weights to zero (no feedback assumption)
        for (int i = 0; i < NUM_TAPS; i++) {
            weights[i] = 0.0f;
            outputHistory[i] = 0.0f;
        }
        
        Log.i(TAG, String.format("FeedbackCanceller initialized: %d taps, μ=%.6f, max coeff=%.2f",
            NUM_TAPS, STEP_SIZE, MAX_COEFF));
    }
    
    /**
     * Process audio block with adaptive feedback cancellation.
     * 
     * @param micInput Microphone input samples (normalized ±1.0)
     * @param amplifierOutput Amplifier output samples from previous frame
     * @param output Corrected output samples (feedback removed)
     * @param length Number of samples to process
     */
    public void process(float[] micInput, float[] amplifierOutput, float[] output, int length) {
        for (int n = 0; n < length; n++) {
            // Step 1: Predict feedback from past outputs
            float predictedFeedback = 0.0f;
            for (int k = 0; k < NUM_TAPS; k++) {
                int histIdx = (historyIndex - k + NUM_TAPS) % NUM_TAPS;
                predictedFeedback += weights[k] * outputHistory[histIdx];
            }
            
            // Step 2: Calculate error (actual mic input minus predicted feedback)
            float error = micInput[n] - predictedFeedback;
            output[n] = error;
            
            // Step 3: Update filter weights using LMS algorithm
            for (int k = 0; k < NUM_TAPS; k++) {
                int histIdx = (historyIndex - k + NUM_TAPS) % NUM_TAPS;
                float outputSample = outputHistory[histIdx];
                
                // LMS weight update: w[k] += μ × error × output[n-k]
                weights[k] += STEP_SIZE * error * outputSample;
                
                // Limit coefficient magnitude to prevent instability
                if (weights[k] > MAX_COEFF) {
                    weights[k] = MAX_COEFF;
                } else if (weights[k] < -MAX_COEFF) {
                    weights[k] = -MAX_COEFF;
                }
            }
            
            // Step 4: Update output history with current amplifier output
            // (This is the signal that could potentially feed back)
            if (amplifierOutput != null && n < amplifierOutput.length) {
                outputHistory[historyIndex] = amplifierOutput[n];
            } else {
                // If no amplifier output available, use processed output
                outputHistory[historyIndex] = output[n];
            }
            
            historyIndex = (historyIndex + 1) % NUM_TAPS;
            
            // Update statistics
            averageErrorPower = 0.999f * averageErrorPower + 0.001f * error * error;
            samplesProcessed++;
        }
        
        // Check convergence every 1000 samples
        if (samplesProcessed % 1000 == 0) {
            updateConvergenceStatus();
        }
    }
    
    /**
     * Simplified version without amplifier output (uses processed output as estimate).
     * 
     * @param micInput Microphone input samples
     * @param output Corrected output samples (also used for adaptation)
     * @param length Number of samples to process
     */
    public void process(float[] micInput, float[] output, int length) {
        process(micInput, null, output, length);
    }
    
    /**
     * Update convergence status based on weight stability.
     */
    private void updateConvergenceStatus() {
        // Calculate average weight magnitude
        float weightSum = 0.0f;
        for (int k = 0; k < NUM_TAPS; k++) {
            weightSum += Math.abs(weights[k]);
        }
        float currentWeightMag = weightSum / NUM_TAPS;
        
        // Check if weights are stable (not changing much)
        float weightChange = Math.abs(currentWeightMag - averageWeightMagnitude);
        averageWeightMagnitude = currentWeightMag;
        
        // Consider converged if weight change < 1% of max coefficient
        converged = (weightChange < MAX_COEFF * 0.01f);
        
        if (samplesProcessed % 48000 == 0) { // Log every second
            Log.d(TAG, String.format("Feedback canceller: Error power=%.6f, Avg weight=%.4f, Converged=%b",
                averageErrorPower, averageWeightMagnitude, converged));
        }
    }
    
    /**
     * Reset filter state (e.g., when audio stream restarts).
     */
    public void reset() {
        for (int i = 0; i < NUM_TAPS; i++) {
            weights[i] = 0.0f;
            outputHistory[i] = 0.0f;
        }
        historyIndex = 0;
        samplesProcessed = 0;
        averageErrorPower = 0.0f;
        averageWeightMagnitude = 0.0f;
        converged = false;
        
        Log.i(TAG, "FeedbackCanceller reset");
    }
    
    /**
     * Get current filter statistics.
     */
    public FeedbackStats getStats() {
        // Calculate max weight magnitude
        float maxWeight = 0.0f;
        for (int k = 0; k < NUM_TAPS; k++) {
            float absWeight = Math.abs(weights[k]);
            if (absWeight > maxWeight) {
                maxWeight = absWeight;
            }
        }
        
        return new FeedbackStats(
            averageErrorPower,
            averageWeightMagnitude,
            maxWeight,
            converged
        );
    }
    
    /**
     * Check if filter has converged.
     */
    public boolean isConverged() {
        return converged;
    }
    
    /**
     * Get current error power (measure of feedback suppression effectiveness).
     * Lower values indicate better suppression.
     */
    public float getErrorPower() {
        return averageErrorPower;
    }
    
    /**
     * Get average filter weight magnitude.
     * Higher values indicate stronger feedback path being canceled.
     */
    public float getAverageWeightMagnitude() {
        return averageWeightMagnitude;
    }
    
    /**
     * Manually freeze adaptation (useful for testing or when feedback is known to be absent).
     */
    public void freezeAdaptation(boolean freeze) {
        // Could add a flag to disable weight updates if needed
        // For now, just log the request
        Log.i(TAG, "Adaptation freeze requested: " + freeze);
    }
    
    /**
     * Statistics container for feedback cancellation analysis.
     */
    public static class FeedbackStats {
        public final float errorPower;          // Average error power
        public final float avgWeightMagnitude;  // Average |weight|
        public final float maxWeightMagnitude;  // Max |weight|
        public final boolean converged;         // Convergence status
        
        FeedbackStats(float errorPower, float avgWeightMagnitude, 
                     float maxWeightMagnitude, boolean converged) {
            this.errorPower = errorPower;
            this.avgWeightMagnitude = avgWeightMagnitude;
            this.maxWeightMagnitude = maxWeightMagnitude;
            this.converged = converged;
        }
        
        @Override
        public String toString() {
            return String.format("Feedback: Error power=%.6f, Avg weight=%.4f, Max weight=%.4f, Converged=%b",
                errorPower, avgWeightMagnitude, maxWeightMagnitude, converged);
        }
    }
}
