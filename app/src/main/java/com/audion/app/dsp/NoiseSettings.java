package com.audion.app.dsp;

/**
 * NoiseSettings - Adaptive noise reduction configuration
 * 
 * Contains personalized parameters for noise reduction based on hearing loss severity.
 * Balances noise suppression with speech preservation.
 */
public class NoiseSettings {
    private float strength = 0.5f;              // Noise reduction strength (0-1)
    private float speechPreservation = 0.7f;    // Speech preservation level (0-1) 
    private boolean adaptiveMode = true;        // Adaptive strength based on SNR
    private float minSNR = -10.0f;              // Minimum SNR for processing
    private float maxSuppression = -20.0f;      // Maximum noise suppression (dB)
    private boolean spectralSubtraction = true; // Enable spectral subtraction
    
    public NoiseSettings() {
        // Balanced defaults
    }
    
    // Getters and setters
    public float getStrength() { return strength; }
    public void setStrength(float strength) { 
        this.strength = Math.max(0.0f, Math.min(1.0f, strength)); // Clamp 0-1
    }
    
    public float getSpeechPreservation() { return speechPreservation; }
    public void setSpeechPreservation(float preservation) { 
        this.speechPreservation = Math.max(0.0f, Math.min(1.0f, preservation)); // Clamp 0-1
    }
    
    public boolean isAdaptiveMode() { return adaptiveMode; }
    public void setAdaptiveMode(boolean adaptive) { this.adaptiveMode = adaptive; }
    
    public float getMinSNR() { return minSNR; }
    public void setMinSNR(float minSNR) { this.minSNR = minSNR; }
    
    public float getMaxSuppression() { return maxSuppression; }
    public void setMaxSuppression(float maxSuppression) { this.maxSuppression = maxSuppression; }
    
    public boolean isSpectralSubtraction() { return spectralSubtraction; }
    public void setSpectralSubtraction(boolean enabled) { this.spectralSubtraction = enabled; }
    
    /**
     * Calculate effective noise reduction for current SNR
     */
    public float getEffectiveStrength(float currentSNR) {
        if (!adaptiveMode) {
            return strength;
        }
        
        // Reduce strength for better SNR (less noise reduction needed)
        if (currentSNR > 10.0f) {
            return strength * 0.5f; // Light processing for good SNR
        } else if (currentSNR > 0.0f) {
            return strength * 0.8f; // Moderate processing
        } else {
            return strength; // Full processing for poor SNR
        }
    }
    
    /**
     * Get speech preservation factor for frequency band
     * Higher preservation for speech frequencies (300-3400 Hz)
     */
    public float getSpeechPreservationForFreq(float frequency) {
        if (frequency >= 300 && frequency <= 3400) {
            return speechPreservation; // Full preservation in speech band
        } else if (frequency >= 200 && frequency <= 4000) {
            return speechPreservation * 0.8f; // Reduced preservation near speech
        } else {
            return speechPreservation * 0.5f; // Minimal preservation outside speech
        }
    }
}