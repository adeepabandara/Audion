package com.audion.app.dsp;

import android.util.Log;

/**
 * WdrcProcessor - Wide Dynamic Range Compression processor
 * 
 * Applies personalized WDRC settings based on audiometry results.
 * Implements NAL-inspired gain calculations with frequency-specific compensation.
 */
public class WdrcProcessor {
    private static final String TAG = "WdrcProcessor";
    
    private final String ear;
    private WDRCSettings settings;
    private boolean speechOptimizedMode = false;
    
    public WdrcProcessor(String ear) {
        this.ear = ear;
        this.settings = new WDRCSettings(); // Default settings
        Log.d(TAG, "WdrcProcessor created for " + ear + " ear");
    }
    
    /**
     * Apply personalized WDRC settings from audiometry results
     */
    public void setPersonalizedGain(WDRCSettings personalizedSettings) {
        this.settings = personalizedSettings;
        Log.i(TAG, "Personalized WDRC settings applied to " + ear + " ear");
        
        // Log key settings for verification
        Log.d(TAG, ear + " WDRC - Compression ratio: " + settings.getCompressionRatio());
        Log.d(TAG, ear + " WDRC - Kneepoint: " + settings.getKneepoint() + "dB");
        Log.d(TAG, ear + " WDRC - Speech optimized: " + settings.isSpeechOptimized());
        
        // Log frequency gains
        for (int freq : settings.getFrequencyGains().keySet()) {
            float gain = settings.getFrequencyGains().get(freq);
            Log.d(TAG, ear + " WDRC - " + freq + "Hz gain: " + gain + "dB");
        }
    }
    
    /**
     * Set speech optimization mode (activated in FOCUS mode)
     */
    public void setSpeechOptimizedMode(boolean optimized) {
        this.speechOptimizedMode = optimized;
        Log.d(TAG, ear + " WDRC speech optimization: " + (optimized ? "enabled" : "disabled"));
    }
    
    /**
     * Get gain for specific frequency
     */
    public float getFrequencyGain(int frequency) {
        if (settings == null) {
            return 0.0f;
        }
        
        float baseGain = settings.getGainForFrequency(frequency);
        
        // Apply speech optimization if enabled
        if (speechOptimizedMode && isInSpeechBand(frequency)) {
            baseGain *= 1.2f; // Boost speech frequencies by 20%
        }
        
        return baseGain;
    }
    
    /**
     * Check if frequency is in critical speech band (500-3000 Hz)
     */
    private boolean isInSpeechBand(int frequency) {
        return frequency >= 500 && frequency <= 3000;
    }
    
    /**
     * Process audio buffer with WDRC (placeholder for actual DSP)
     * In real implementation, this would apply compression based on settings
     */
    public void processBuffer(float[] audioBuffer) {
        if (settings == null || audioBuffer == null) {
            return;
        }
        
        // Placeholder: In actual implementation, this would:
        // 1. Analyze input level
        // 2. Apply frequency-specific gains
        // 3. Apply compression based on ratio and kneepoint
        // 4. Apply speech optimization if enabled
        
        Log.v(TAG, "Processing audio buffer for " + ear + " ear with WDRC");
    }
    
    /**
     * Get current WDRC settings
     */
    public WDRCSettings getSettings() {
        return settings;
    }
    
    /**
     * Get processor status information
     */
    public String getStatusInfo() {
        if (settings == null) {
            return ear + " WDRC: Not configured";
        }
        
        return ear + " WDRC: Ratio=" + settings.getCompressionRatio() + 
               ", Kneepoint=" + settings.getKneepoint() + "dB" +
               ", Speech=" + (speechOptimizedMode ? "ON" : "OFF");
    }
}