package com.audion.calibration;

import java.util.HashMap;
import java.util.Map;

/**
 * Audion is a Personal Sound Amplification Product (PSAP) for consumer use.
 * It is not a medical device and is not intended to diagnose, treat, or cure hearing conditions.
 * 
 * Calibration profile for storing personalized hearing support settings.
 * Includes Most Comfortable Level (MCL) and Real Ear Gain settings.
 */
public class CalibrationProfile {
    
    private int userId;
    private int profileId;
    private String profileName;
    private long timestamp;
    
    // Per-ear calibration data
    private Map<String, EarCalibration> earCalibrations;
    
    // Device-specific adjustments
    private String deviceModel;
    private DeviceType deviceType;
    private float deviceLatencyMs;
    
    public enum DeviceType {
        WIRED_HEADPHONES,
        BLUETOOTH_EARBUDS,
        BLUETOOTH_HEADPHONES,
        WIRED_EARBUDS,
        BONE_CONDUCTION
    }
    
    /**
     * Calibration data for individual ear
     */
    public static class EarCalibration {
        private float mostComfortableLevel;     // MCL in dB SPL
        private float uncomfortableLevel;       // UCL in dB SPL  
        private float dynamicRange;             // UCL - threshold range
        private Map<Integer, Float> realEarGain; // Per-frequency gain corrections
        private Map<Integer, Float> frequencyResponse; // Device frequency response
        
        public EarCalibration() {
            realEarGain = new HashMap<>();
            frequencyResponse = new HashMap<>();
        }
        
        // Getters and setters
        public float getMostComfortableLevel() { return mostComfortableLevel; }
        public void setMostComfortableLevel(float mcl) { this.mostComfortableLevel = mcl; }
        
        public float getUncomfortableLevel() { return uncomfortableLevel; }
        public void setUncomfortableLevel(float ucl) { this.uncomfortableLevel = ucl; }
        
        public float getDynamicRange() { return dynamicRange; }
        public void setDynamicRange(float range) { this.dynamicRange = range; }
        
        public Map<Integer, Float> getRealEarGain() { return realEarGain; }
        public void setRealEarGain(Map<Integer, Float> gain) { this.realEarGain = gain; }
        
        public Map<Integer, Float> getFrequencyResponse() { return frequencyResponse; }
        public void setFrequencyResponse(Map<Integer, Float> response) { this.frequencyResponse = response; }
        
        /**
         * Set real ear gain for specific frequency
         */
        public void setGainAtFrequency(int frequencyHz, float gainDb) {
            realEarGain.put(frequencyHz, gainDb);
        }
        
        /**
         * Get real ear gain for specific frequency (with interpolation)
         */
        public float getGainAtFrequency(int frequencyHz) {
            if (realEarGain.containsKey(frequencyHz)) {
                return realEarGain.get(frequencyHz);
            }
            
            // Interpolate between nearest frequencies
            return interpolateGain(frequencyHz);
        }
        
        private float interpolateGain(int targetFreq) {
            int lowerFreq = 0;
            int upperFreq = Integer.MAX_VALUE;
            
            for (int freq : realEarGain.keySet()) {
                if (freq <= targetFreq && freq > lowerFreq) {
                    lowerFreq = freq;
                }
                if (freq >= targetFreq && freq < upperFreq) {
                    upperFreq = freq;
                }
            }
            
            if (lowerFreq == 0 || upperFreq == Integer.MAX_VALUE) {
                return 0.0f; // No data available
            }
            
            if (lowerFreq == upperFreq) {
                return realEarGain.get(lowerFreq);
            }
            
            // Linear interpolation
            float lowerGain = realEarGain.get(lowerFreq);
            float upperGain = realEarGain.get(upperFreq);
            
            float ratio = (float)(targetFreq - lowerFreq) / (upperFreq - lowerFreq);
            return lowerGain + ratio * (upperGain - lowerGain);
        }
    }
    
    public CalibrationProfile(int userId, int profileId, String profileName) {
        this.userId = userId;
        this.profileId = profileId;
        this.profileName = profileName;
        this.timestamp = System.currentTimeMillis();
        this.earCalibrations = new HashMap<>();
        this.earCalibrations.put("LEFT", new EarCalibration());
        this.earCalibrations.put("RIGHT", new EarCalibration());
        this.deviceLatencyMs = 0.0f;
    }
    
    /**
     * Set Most Comfortable Level for specified ear
     */
    public void setMCL(String ear, float mclDbSpl) {
        EarCalibration earCal = earCalibrations.get(ear);
        if (earCal != null) {
            earCal.setMostComfortableLevel(mclDbSpl);
        }
    }
    
    /**
     * Get Most Comfortable Level for specified ear
     */
    public float getMCL(String ear) {
        EarCalibration earCal = earCalibrations.get(ear);
        return earCal != null ? earCal.getMostComfortableLevel() : 65.0f; // Default 65 dB SPL
    }
    
    /**
     * Set Uncomfortable Level for specified ear
     */
    public void setUCL(String ear, float uclDbSpl) {
        EarCalibration earCal = earCalibrations.get(ear);
        if (earCal != null) {
            earCal.setUncomfortableLevel(uclDbSpl);
            // Update dynamic range
            float threshold = getThresholdEstimate(ear);
            earCal.setDynamicRange(uclDbSpl - threshold);
        }
    }
    
    /**
     * Get Uncomfortable Level for specified ear
     */
    public float getUCL(String ear) {
        EarCalibration earCal = earCalibrations.get(ear);
        return earCal != null ? earCal.getUncomfortableLevel() : 95.0f; // Default 95 dB SPL
    }
    
    /**
     * Calculate real ear gain from audiogram and calibration
     */
    public void calculateRealEarGain(String ear, Map<Integer, Integer> audiogramHL) {
        EarCalibration earCal = earCalibrations.get(ear);
        if (earCal == null || audiogramHL == null) return;
        
        float mcl = earCal.getMostComfortableLevel();
        float ucl = earCal.getUncomfortableLevel();
        
        // Calculate gain for each frequency using modified NAL-NL1 approach
        for (Map.Entry<Integer, Integer> entry : audiogramHL.entrySet()) {
            int frequency = entry.getKey();
            int thresholdHL = entry.getValue();
            
            float gain = calculateNALGain(frequency, thresholdHL, mcl, ucl);
            earCal.setGainAtFrequency(frequency, gain);
        }
    }
    
    /**
     * Calculate NAL-inspired gain for frequency and threshold
     */
    private float calculateNALGain(int frequency, int thresholdHL, float mcl, float ucl) {
        // NAL-NL1 inspired formula with comfort level integration
        
        // Base gain calculation (simplified NAL)
        float baseGain = 0.31f * thresholdHL + 0.78f;
        
        // Frequency-specific adjustments
        float freqWeight = getFrequencyWeight(frequency);
        float adjustedGain = baseGain * freqWeight;
        
        // Comfort level constraints
        float dynamicRange = ucl - mcl;
        float maxGain = Math.min(adjustedGain, dynamicRange * 0.6f); // Don't exceed 60% of dynamic range
        
        // Safety limits
        return Math.max(0, Math.min(maxGain, 40.0f)); // Max 40dB gain
    }
    
    /**
     * Get frequency weighting for NAL calculation
     */
    private float getFrequencyWeight(int frequency) {
        // Frequency importance for speech understanding
        if (frequency <= 500) return 0.8f;
        else if (frequency <= 1000) return 1.0f;
        else if (frequency <= 2000) return 1.1f;
        else if (frequency <= 4000) return 0.9f;
        else return 0.7f;
    }
    
    /**
     * Apply device-specific frequency response correction
     */
    public void applyDeviceCorrection(String ear, Map<Integer, Float> deviceResponse) {
        EarCalibration earCal = earCalibrations.get(ear);
        if (earCal == null) return;
        
        earCal.setFrequencyResponse(deviceResponse);
        
        // Adjust real ear gain to compensate for device response
        Map<Integer, Float> correctedGain = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : earCal.getRealEarGain().entrySet()) {
            int freq = entry.getKey();
            float originalGain = entry.getValue();
            float deviceGain = deviceResponse.getOrDefault(freq, 0.0f);
            
            // Subtract device boost/cut from required gain
            correctedGain.put(freq, originalGain - deviceGain);
        }
        
        earCal.setRealEarGain(correctedGain);
    }
    
    /**
     * Set device information and latency
     */
    public void setDeviceInfo(String deviceModel, DeviceType deviceType, float latencyMs) {
        this.deviceModel = deviceModel;
        this.deviceType = deviceType;
        this.deviceLatencyMs = latencyMs;
    }
    
    /**
     * Get total gain for specific ear and frequency
     */
    public float getTotalGain(String ear, int frequency) {
        EarCalibration earCal = earCalibrations.get(ear);
        if (earCal == null) return 0.0f;
        
        return earCal.getGainAtFrequency(frequency);
    }
    
    /**
     * Check if calibration is complete for ear
     */
    public boolean isCalibrationComplete(String ear) {
        EarCalibration earCal = earCalibrations.get(ear);
        if (earCal == null) return false;
        
        return earCal.getMostComfortableLevel() > 0 && 
               !earCal.getRealEarGain().isEmpty();
    }
    
    /**
     * Export calibration settings for backup/sharing
     */
    public String exportSettings() {
        StringBuilder sb = new StringBuilder();
        sb.append("Calibration Profile: ").append(profileName).append("\n");
        sb.append("User ID: ").append(userId).append("\n");
        sb.append("Device: ").append(deviceModel).append(" (").append(deviceType).append(")\n");
        sb.append("Latency: ").append(deviceLatencyMs).append("ms\n\n");
        
        for (String ear : earCalibrations.keySet()) {
            EarCalibration earCal = earCalibrations.get(ear);
            sb.append(ear).append(" Ear:\n");
            sb.append("  MCL: ").append(earCal.getMostComfortableLevel()).append(" dB SPL\n");
            sb.append("  UCL: ").append(earCal.getUncomfortableLevel()).append(" dB SPL\n");
            sb.append("  Dynamic Range: ").append(earCal.getDynamicRange()).append(" dB\n");
            sb.append("  Real Ear Gain:\n");
            
            for (Map.Entry<Integer, Float> entry : earCal.getRealEarGain().entrySet()) {
                sb.append("    ").append(entry.getKey()).append("Hz: ");
                sb.append(String.format("%.1f", entry.getValue())).append("dB\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    private float getThresholdEstimate(String ear) {
        // Estimate threshold from existing gain data (reverse calculation)
        EarCalibration earCal = earCalibrations.get(ear);
        if (earCal == null || earCal.getRealEarGain().isEmpty()) {
            return 20.0f; // Default normal threshold
        }
        
        // Use average gain to estimate threshold
        float avgGain = 0;
        for (float gain : earCal.getRealEarGain().values()) {
            avgGain += gain;
        }
        avgGain /= earCal.getRealEarGain().size();
        
        // Reverse NAL formula approximation
        return (avgGain - 0.78f) / 0.31f;
    }
    
    // Getters and setters
    public int getUserId() { return userId; }
    public int getProfileId() { return profileId; }
    public String getProfileName() { return profileName; }
    public long getTimestamp() { return timestamp; }
    public String getDeviceModel() { return deviceModel; }
    public DeviceType getDeviceType() { return deviceType; }
    public float getDeviceLatencyMs() { return deviceLatencyMs; }
    public EarCalibration getEarCalibration(String ear) { return earCalibrations.get(ear); }
}