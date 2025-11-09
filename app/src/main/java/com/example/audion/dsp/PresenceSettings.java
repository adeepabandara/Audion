package com.example.audion.dsp;

/**
 * PresenceSettings - High-frequency presence filter configuration
 * 
 * Contains personalized parameters for presence enhancement based on high-frequency 
 * hearing loss patterns. Optimizes speech clarity and consonant recognition.
 */
public class PresenceSettings {
    private float presenceBoost = 0.0f;          // dB boost in presence frequencies
    private boolean speechBandBoostEnabled = false;
    private float highFreqCompensation = 0.0f;   // Additional HF compensation
    private float centerFrequency = 3000.0f;     // Center of presence boost
    private float bandwidth = 2000.0f;           // Bandwidth of boost
    private float maxBoost = 15.0f;              // Safety limit for boost
    
    public PresenceSettings() {
        // Safe defaults - no processing
    }
    
    // Getters and setters
    public float getPresenceBoost() { return presenceBoost; }
    public void setPresenceBoost(float boost) { 
        this.presenceBoost = Math.min(boost, maxBoost); // Safety limit
    }
    
    public boolean isSpeechBandBoostEnabled() { return speechBandBoostEnabled; }
    public void setSpeechBandBoostEnabled(boolean enabled) { this.speechBandBoostEnabled = enabled; }
    
    public float getHighFreqCompensation() { return highFreqCompensation; }
    public void setHighFreqCompensation(float compensation) { this.highFreqCompensation = compensation; }
    
    public float getCenterFrequency() { return centerFrequency; }
    public void setCenterFrequency(float frequency) { this.centerFrequency = frequency; }
    
    public float getBandwidth() { return bandwidth; }
    public void setBandwidth(float bandwidth) { this.bandwidth = bandwidth; }
    
    public float getMaxBoost() { return maxBoost; }
    public void setMaxBoost(float maxBoost) { this.maxBoost = maxBoost; }
    
    /**
     * Calculate boost amount for specific frequency
     */
    public float getBoostForFrequency(float frequency) {
        if (!speechBandBoostEnabled && presenceBoost == 0.0f) {
            return 0.0f;
        }
        
        // Simple bell curve centered at centerFrequency
        float distance = Math.abs(frequency - centerFrequency);
        if (distance > bandwidth / 2) {
            return 0.0f; // Outside boost range
        }
        
        // Gaussian-like response
        float normalizedDistance = distance / (bandwidth / 2);
        float response = (float) Math.exp(-normalizedDistance * normalizedDistance);
        
        return presenceBoost * response + (frequency > 2000 ? highFreqCompensation * response : 0);
    }
}