package com.example.audion.dsp;

import android.util.Log;

/**
 * PresenceFilter - High-frequency presence enhancement processor
 * 
 * Applies personalized presence boosting based on high-frequency hearing loss.
 * Optimizes speech clarity and consonant recognition.
 */
public class PresenceFilter {
    private static final String TAG = "PresenceFilter";
    
    private final String ear;
    private PresenceSettings settings;
    private boolean speechBandBoostEnabled = false;
    
    public PresenceFilter(String ear) {
        this.ear = ear;
        this.settings = new PresenceSettings(); // Default settings
        Log.d(TAG, "PresenceFilter created for " + ear + " ear");
    }
    
    /**
     * Apply personalized presence settings from audiometry results
     */
    public void setPersonalizedBoost(PresenceSettings personalizedSettings) {
        this.settings = personalizedSettings;
        Log.i(TAG, "Personalized presence settings applied to " + ear + " ear");
        
        // Log key settings for verification
        Log.d(TAG, ear + " Presence - Boost: " + settings.getPresenceBoost() + "dB");
        Log.d(TAG, ear + " Presence - HF compensation: " + settings.getHighFreqCompensation() + "dB");
        Log.d(TAG, ear + " Presence - Speech band: " + settings.isSpeechBandBoostEnabled());
    }
    
    /**
     * Enable/disable speech band boosting (activated in FOCUS mode)
     */
    public void setSpeechBandBoostEnabled(boolean enabled) {
        this.speechBandBoostEnabled = enabled;
        Log.d(TAG, ear + " Presence speech band boost: " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Get boost amount for specific frequency
     */
    public float getBoostForFrequency(float frequency) {
        if (settings == null) {
            return 0.0f;
        }
        
        float boost = settings.getBoostForFrequency(frequency);
        
        // Apply additional speech band boost if enabled
        if (speechBandBoostEnabled && isInSpeechBand(frequency)) {
            boost += 2.0f; // Additional 2dB for speech clarity
        }
        
        return boost;
    }
    
    /**
     * Check if frequency is in speech band for boosting
     */
    private boolean isInSpeechBand(float frequency) {
        return frequency >= 1000 && frequency <= 4000; // Speech clarity range
    }
    
    /**
     * Boost speech band frequencies (used in FOCUS mode)
     */
    public void boostSpeechBand(float[] audioBuffer) {
        if (!speechBandBoostEnabled || settings == null || audioBuffer == null) {
            return;
        }
        
        // Placeholder: In actual implementation, this would:
        // 1. Apply FFT to get frequency domain
        // 2. Boost frequencies in speech band (1-4 kHz)
        // 3. Apply personalized presence settings
        // 4. Transform back to time domain
        
        Log.v(TAG, "Boosting speech band for " + ear + " ear");
    }
    
    /**
     * Process audio buffer with presence enhancement
     */
    public void processBuffer(float[] audioBuffer) {
        if (settings == null || audioBuffer == null) {
            return;
        }
        
        // Placeholder: In actual implementation, this would:
        // 1. Apply frequency-specific presence boosting
        // 2. Enhance high-frequency content based on hearing loss
        // 3. Apply speech band boosting if enabled
        
        Log.v(TAG, "Processing audio buffer for " + ear + " ear with presence enhancement");
    }
    
    /**
     * Get current presence settings
     */
    public PresenceSettings getSettings() {
        return settings;
    }
    
    /**
     * Get processor status information
     */
    public String getStatusInfo() {
        if (settings == null) {
            return ear + " Presence: Not configured";
        }
        
        return ear + " Presence: Boost=" + settings.getPresenceBoost() + "dB" +
               ", HF Comp=" + settings.getHighFreqCompensation() + "dB" +
               ", Speech=" + (speechBandBoostEnabled ? "ON" : "OFF");
    }
}