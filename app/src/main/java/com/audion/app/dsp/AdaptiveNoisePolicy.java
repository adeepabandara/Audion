package com.audion.app.dsp;

import android.util.Log;

/**
 * AdaptiveNoisePolicy - Personalized noise reduction processor
 * 
 * Applies personalized noise reduction based on hearing loss severity.
 * Balances noise suppression with speech preservation.
 */
public class AdaptiveNoisePolicy {
    private static final String TAG = "AdaptiveNoisePolicy";
    
    private final String ear;
    private NoiseSettings settings;
    private float currentStrengthMultiplier = 1.0f;
    
    public AdaptiveNoisePolicy(String ear) {
        this.ear = ear;
        this.settings = new NoiseSettings(); // Default settings
        Log.d(TAG, "AdaptiveNoisePolicy created for " + ear + " ear");
    }
    
    /**
     * Apply personalized noise reduction settings
     */
    public void setPersonalizedStrength(NoiseSettings personalizedSettings) {
        this.settings = personalizedSettings;
        Log.i(TAG, "Personalized noise settings applied to " + ear + " ear");
        
        // Log key settings for verification
        Log.d(TAG, ear + " Noise - Strength: " + settings.getStrength());
        Log.d(TAG, ear + " Noise - Speech preservation: " + settings.getSpeechPreservation());
        Log.d(TAG, ear + " Noise - Adaptive mode: " + settings.isAdaptiveMode());
    }
    
    /**
     * Adjust noise reduction strength for processing mode
     */
    public void adjustStrengthForMode(float multiplier) {
        this.currentStrengthMultiplier = multiplier;
        Log.d(TAG, ear + " Noise strength multiplier: " + multiplier);
    }
    
    /**
     * Get effective noise reduction strength for current conditions
     */
    public float getEffectiveStrength(float currentSNR) {
        if (settings == null) {
            return 0.0f;
        }
        
        float baseStrength = settings.getEffectiveStrength(currentSNR);
        return baseStrength * currentStrengthMultiplier;
    }
    
    /**
     * Get speech preservation factor for frequency
     */
    public float getSpeechPreservationForFreq(float frequency) {
        if (settings == null) {
            return 1.0f; // Full preservation if not configured
        }
        
        return settings.getSpeechPreservationForFreq(frequency);
    }
    
    /**
     * Process audio buffer with adaptive noise reduction
     */
    public void processBuffer(float[] audioBuffer, float estimatedSNR) {
        if (settings == null || audioBuffer == null) {
            return;
        }
        
        float effectiveStrength = getEffectiveStrength(estimatedSNR);
        
        // Placeholder: In actual implementation, this would:
        // 1. Estimate noise spectrum
        // 2. Apply frequency-specific suppression
        // 3. Preserve speech frequencies based on settings
        // 4. Adapt strength based on SNR
        
        Log.v(TAG, "Processing noise reduction for " + ear + " ear, strength=" + effectiveStrength);
    }
    
    /**
     * Estimate Signal-to-Noise Ratio (placeholder)
     */
    public float estimateSNR(float[] audioBuffer) {
        // Placeholder: Real implementation would analyze signal and noise
        return 5.0f; // Assume moderate SNR
    }
    
    /**
     * Get current noise settings
     */
    public NoiseSettings getSettings() {
        return settings;
    }
    
    /**
     * Get processor status information
     */
    public String getStatusInfo() {
        if (settings == null) {
            return ear + " Noise: Not configured";
        }
        
        return ear + " Noise: Strength=" + (settings.getStrength() * currentStrengthMultiplier) +
               ", Speech pres=" + settings.getSpeechPreservation() +
               ", Adaptive=" + settings.isAdaptiveMode();
    }
}