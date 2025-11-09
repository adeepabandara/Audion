package com.example.audion.dsp;

import android.util.Log;

/**
 * LimiterProcessor - Personalized output limiter for hearing protection
 * 
 * CRITICAL SAFETY COMPONENT - Enforces personalized MPO limits based on UCL measurements.
 * Prevents hearing damage by limiting output to safe levels for each individual.
 */
public class LimiterProcessor {
    private static final String TAG = "LimiterProcessor";
    
    private final String ear;
    private LimiterSettings settings;
    private boolean safetyOverride = false; // Emergency override if needed
    
    public LimiterProcessor(String ear) {
        this.ear = ear;
        this.settings = new LimiterSettings(); // Conservative defaults
        Log.d(TAG, "LimiterProcessor created for " + ear + " ear - SAFETY CRITICAL");
    }
    
    /**
     * Apply personalized MPO settings - CRITICAL for hearing safety
     */
    public void setPersonalizedMPO(LimiterSettings personalizedSettings) {
        this.settings = personalizedSettings;
        Log.i(TAG, "SAFETY: Personalized limiter settings applied to " + ear + " ear");
        
        // Log critical safety parameters
        Log.i(TAG, ear + " LIMITER - Personalized MPO: " + settings.getPersonalizedMPO() + "dB SPL");
        Log.i(TAG, ear + " LIMITER - UCL-based limit: " + settings.getUclBasedLimit() + "dB SPL");
        Log.i(TAG, ear + " LIMITER - Device safety limit: " + settings.getDeviceSafetyLimit() + "dB SPL");
        Log.i(TAG, ear + " LIMITER - Effective threshold: " + settings.getEffectiveThreshold() + "dB SPL");
        
        // Verify safety limits are reasonable
        if (settings.getPersonalizedMPO() > 100.0f) {
            Log.w(TAG, "WARNING: High MPO limit detected for " + ear + " ear: " + settings.getPersonalizedMPO() + "dB SPL");
        }
    }
    
    /**
     * Check if input signal requires limiting
     */
    public boolean requiresLimiting(float signalLevel) {
        if (settings == null) {
            return signalLevel > 85.0f; // Conservative fallback
        }
        
        return settings.requiresLimiting(signalLevel);
    }
    
    /**
     * Calculate required gain reduction for limiting
     */
    public float getLimitingGain(float inputLevel) {
        if (settings == null) {
            // Emergency limiting if not configured
            return Math.min(0.0f, 85.0f - inputLevel);
        }
        
        return settings.getLimitingGain(inputLevel);
    }
    
    /**
     * Process audio buffer with safety limiting - CRITICAL FUNCTION
     */
    public void processBuffer(float[] audioBuffer) {
        if (audioBuffer == null) {
            return;
        }
        
        if (settings == null) {
            // Emergency limiting without configuration
            applyEmergencyLimiting(audioBuffer);
            Log.w(TAG, "SAFETY: Emergency limiting applied to " + ear + " ear - no personalized settings");
            return;
        }
        
        // Check each sample for safety
        for (int i = 0; i < audioBuffer.length; i++) {
            float sampleLevel = Math.abs(audioBuffer[i]);
            float sampleLevelDb = 20.0f * (float) Math.log10(sampleLevel + 1e-10f); // Convert to dB
            
            if (requiresLimiting(sampleLevelDb)) {
                float gainReduction = getLimitingGain(sampleLevelDb);
                float linearGain = (float) Math.pow(10.0f, gainReduction / 20.0f);
                audioBuffer[i] *= linearGain;
                
                // Log excessive limiting
                if (gainReduction < -10.0f) {
                    Log.w(TAG, "SAFETY: Heavy limiting applied to " + ear + " ear: " + gainReduction + "dB");
                }
            }
        }
        
        Log.v(TAG, "Safety limiting processed for " + ear + " ear");
    }
    
    /**
     * Apply emergency limiting when no personalized settings available
     */
    private void applyEmergencyLimiting(float[] audioBuffer) {
        final float EMERGENCY_LIMIT_DB = 85.0f; // Conservative emergency limit
        final float EMERGENCY_LIMIT_LINEAR = (float) Math.pow(10.0f, EMERGENCY_LIMIT_DB / 20.0f);
        
        for (int i = 0; i < audioBuffer.length; i++) {
            if (Math.abs(audioBuffer[i]) > EMERGENCY_LIMIT_LINEAR) {
                audioBuffer[i] = Math.signum(audioBuffer[i]) * EMERGENCY_LIMIT_LINEAR;
            }
        }
    }
    
    /**
     * Get absolute maximum safe level for current configuration
     */
    public float getAbsoluteMaxLevel() {
        if (settings == null) {
            return 85.0f; // Conservative fallback
        }
        
        return settings.getAbsoluteMaxLevel();
    }
    
    /**
     * Enable safety override (emergency use only)
     */
    public void setSafetyOverride(boolean override) {
        this.safetyOverride = override;
        Log.w(TAG, "SAFETY OVERRIDE " + (override ? "ENABLED" : "DISABLED") + " for " + ear + " ear");
    }
    
    /**
     * Get current limiter settings
     */
    public LimiterSettings getSettings() {
        return settings;
    }
    
    /**
     * Get processor status information
     */
    public String getStatusInfo() {
        if (settings == null) {
            return ear + " LIMITER: EMERGENCY MODE - 85dB limit";
        }
        
        return ear + " LIMITER: MPO=" + settings.getPersonalizedMPO() + "dB SPL" +
               ", UCL=" + settings.getUclBasedLimit() + "dB SPL" +
               ", Override=" + (safetyOverride ? "ON" : "OFF");
    }
    
    /**
     * Validate limiter configuration for safety
     */
    public boolean validateSafetyConfiguration() {
        if (settings == null) {
            Log.e(TAG, "SAFETY ERROR: No limiter settings configured for " + ear + " ear");
            return false;
        }
        
        float maxLevel = settings.getAbsoluteMaxLevel();
        if (maxLevel > 110.0f) {
            Log.e(TAG, "SAFETY ERROR: Unsafe maximum level for " + ear + " ear: " + maxLevel + "dB SPL");
            return false;
        }
        
        if (settings.getAttackTime() > 5.0f) {
            Log.w(TAG, "SAFETY WARNING: Slow attack time for " + ear + " ear: " + settings.getAttackTime() + "ms");
        }
        
        return true;
    }
}