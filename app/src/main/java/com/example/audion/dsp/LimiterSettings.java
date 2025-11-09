package com.example.audion.dsp;

/**
 * LimiterSettings - Personalized output limiter configuration
 * 
 * Contains critical safety parameters for hearing protection based on UCL measurements.
 * Enforces personalized Maximum Power Output (MPO) limits with device-specific safety margins.
 */
public class LimiterSettings {
    private float personalizedMPO = 85.0f;      // Personalized max output (dB SPL)
    private float uclBasedLimit = 95.0f;        // UCL-based limit (dB SPL)
    private float deviceSafetyLimit = 100.0f;   // Device-specific absolute limit
    private float attackTime = 1.0f;            // Fast attack for protection (ms)
    private float releaseTime = 50.0f;          // Release time (ms)
    private float threshold = -6.0f;            // Threshold relative to MPO (dB)
    private float ratio = Float.POSITIVE_INFINITY; // Hard limiting
    private boolean enableSoftKnee = false;     // Hard knee for protection
    
    public LimiterSettings() {
        // Conservative defaults for hearing safety
    }
    
    // Getters and setters
    public float getPersonalizedMPO() { return personalizedMPO; }
    public void setPersonalizedMPO(float mpo) { 
        // Enforce absolute safety limits
        this.personalizedMPO = Math.min(mpo, deviceSafetyLimit - 10.0f);
    }
    
    public float getUclBasedLimit() { return uclBasedLimit; }
    public void setUclBasedLimit(float limit) { this.uclBasedLimit = limit; }
    
    public float getDeviceSafetyLimit() { return deviceSafetyLimit; }
    public void setDeviceSafetyLimit(float limit) { this.deviceSafetyLimit = limit; }
    
    public float getAttackTime() { return attackTime; }
    public void setAttackTime(float attackTime) { 
        this.attackTime = Math.max(0.1f, attackTime); // Minimum for protection
    }
    
    public float getReleaseTime() { return releaseTime; }
    public void setReleaseTime(float releaseTime) { this.releaseTime = releaseTime; }
    
    public float getThreshold() { return threshold; }
    public void setThreshold(float threshold) { this.threshold = threshold; }
    
    public float getRatio() { return ratio; }
    public void setRatio(float ratio) { this.ratio = ratio; }
    
    public boolean isEnableSoftKnee() { return enableSoftKnee; }
    public void setEnableSoftKnee(boolean softKnee) { this.enableSoftKnee = softKnee; }
    
    /**
     * Get effective limiting threshold in dB SPL
     */
    public float getEffectiveThreshold() {
        return personalizedMPO + threshold; // threshold is negative offset
    }
    
    /**
     * Check if signal level requires limiting
     */
    public boolean requiresLimiting(float signalLevel) {
        return signalLevel > getEffectiveThreshold();
    }
    
    /**
     * Calculate limiting gain reduction
     */
    public float getLimitingGain(float inputLevel) {
        if (!requiresLimiting(inputLevel)) {
            return 0.0f; // No limiting needed
        }
        
        float overshoot = inputLevel - getEffectiveThreshold();
        
        if (Float.isInfinite(ratio)) {
            // Hard limiting - prevent any overshoot
            return -overshoot;
        } else {
            // Soft limiting with ratio
            return -overshoot * (1.0f - 1.0f / ratio);
        }
    }
    
    /**
     * Get absolute maximum safe level for this configuration
     */
    public float getAbsoluteMaxLevel() {
        return Math.min(personalizedMPO, Math.min(uclBasedLimit, deviceSafetyLimit));
    }
}