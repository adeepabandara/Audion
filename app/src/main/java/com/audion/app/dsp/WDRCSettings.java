package com.audion.app.dsp;

import java.util.Map;
import java.util.HashMap;

/**
 * WDRCSettings - Wide Dynamic Range Compression configuration
 * 
 * Contains personalized parameters for WDRC processing based on audiometry results.
 * Implements NAL-inspired gain calculations with frequency-specific compensation.
 */
public class WDRCSettings {
    private Map<Integer, Float> frequencyGains = new HashMap<>();
    private float compressionRatio = 2.0f;
    private float kneepoint = -30.0f;
    private boolean speechOptimized = false;
    private float attackTime = 5.0f;  // milliseconds
    private float releaseTime = 50.0f; // milliseconds
    
    public WDRCSettings() {
        // Initialize with safe defaults
        initializeDefaults();
    }
    
    private void initializeDefaults() {
        // Default frequency gains (0dB = no change)
        frequencyGains.put(250, 0.0f);
        frequencyGains.put(500, 0.0f);
        frequencyGains.put(1000, 0.0f);
        frequencyGains.put(2000, 0.0f);
        frequencyGains.put(4000, 0.0f);
        frequencyGains.put(8000, 0.0f);
    }
    
    // Getters and setters
    public Map<Integer, Float> getFrequencyGains() { return frequencyGains; }
    public void setFrequencyGains(Map<Integer, Float> gains) { this.frequencyGains = gains; }
    
    public float getCompressionRatio() { return compressionRatio; }
    public void setCompressionRatio(float ratio) { this.compressionRatio = ratio; }
    
    public float getKneepoint() { return kneepoint; }
    public void setKneepoint(float kneepoint) { this.kneepoint = kneepoint; }
    
    public boolean isSpeechOptimized() { return speechOptimized; }
    public void setSpeechOptimized(boolean optimized) { this.speechOptimized = optimized; }
    
    public float getAttackTime() { return attackTime; }
    public void setAttackTime(float attackTime) { this.attackTime = attackTime; }
    
    public float getReleaseTime() { return releaseTime; }
    public void setReleaseTime(float releaseTime) { this.releaseTime = releaseTime; }
    
    /**
     * Get gain for specific frequency with interpolation
     */
    public float getGainForFrequency(int frequency) {
        if (frequencyGains.containsKey(frequency)) {
            return frequencyGains.get(frequency);
        }
        
        // Simple interpolation between nearest frequencies
        int lowerFreq = 0, upperFreq = Integer.MAX_VALUE;
        for (int freq : frequencyGains.keySet()) {
            if (freq <= frequency && freq > lowerFreq) {
                lowerFreq = freq;
            }
            if (freq >= frequency && freq < upperFreq) {
                upperFreq = freq;
            }
        }
        
        if (lowerFreq == 0) return frequencyGains.get(upperFreq);
        if (upperFreq == Integer.MAX_VALUE) return frequencyGains.get(lowerFreq);
        
        // Linear interpolation
        float lowerGain = frequencyGains.get(lowerFreq);
        float upperGain = frequencyGains.get(upperFreq);
        float ratio = (float)(frequency - lowerFreq) / (upperFreq - lowerFreq);
        
        return lowerGain + ratio * (upperGain - lowerGain);
    }
}