package com.audion.audiometry;

import java.util.HashMap;
import java.util.Map;

/**
 * Reference Equivalent Threshold Sound Pressure Level (RETSPL) conversion
 * for converting audiometric thresholds to absolute dB SPL values.
 * Based on ANSI S3.6-2018 and ISO 389 standards.
 */
public class RETSPLTable {
    
    /**
     * RETSPL values for different transducer types and frequencies
     * Values in dB SPL for 0 dB HL reference
     */
    
    // Supra-aural headphones (e.g., TDH-39, TDH-49)
    private static final Map<Integer, Float> RETSPL_SUPRA_AURAL = new HashMap<Integer, Float>() {{
        put(125, 45.0f);   // dB SPL
        put(250, 25.5f);
        put(500, 11.5f);
        put(1000, 7.0f);
        put(1500, 6.5f);
        put(2000, 9.0f);
        put(3000, 10.0f);
        put(4000, 9.5f);
        put(6000, 15.5f);
        put(8000, 13.0f);
    }};
    
    // Circumaural headphones (e.g., HDA 200)
    private static final Map<Integer, Float> RETSPL_CIRCUMAURAL = new HashMap<Integer, Float>() {{
        put(125, 22.0f);
        put(250, 12.0f);
        put(500, 5.5f);
        put(1000, 0.0f);
        put(1500, 2.0f);
        put(2000, 3.0f);
        put(3000, 3.5f);
        put(4000, 5.5f);
        put(6000, 2.0f);
        put(8000, -3.0f);
    }};
    
    // Insert earphones (e.g., ER-3A)
    private static final Map<Integer, Float> RETSPL_INSERT = new HashMap<Integer, Float>() {{
        put(125, 28.0f);
        put(250, 14.0f);
        put(500, 5.5f);
        put(1000, 0.0f);
        put(1500, 2.5f);
        put(2000, 3.0f);
        put(3000, 3.5f);
        put(4000, 5.5f);
        put(6000, 2.0f);
        put(8000, 0.0f);
    }};
    
    // Sound field (loudspeaker) - frontal incidence
    private static final Map<Integer, Float> RETSPL_SOUND_FIELD = new HashMap<Integer, Float>() {{
        put(125, 22.0f);
        put(250, 12.0f);
        put(500, 6.0f);
        put(1000, 0.0f);
        put(1500, 1.0f);
        put(2000, 1.0f);
        put(3000, 1.0f);
        put(4000, 1.0f);
        put(6000, 3.5f);
        put(8000, 3.0f);
    }};
    
    // Bluetooth earphones/earbuds (estimated values based on insert phone characteristics)
    private static final Map<Integer, Float> RETSPL_BLUETOOTH = new HashMap<Integer, Float>() {{
        put(125, 30.0f);   // Slightly higher due to seal variations
        put(250, 16.0f);
        put(500, 7.5f);
        put(1000, 2.0f);
        put(1500, 4.5f);
        put(2000, 5.0f);
        put(3000, 5.5f);
        put(4000, 7.5f);
        put(6000, 4.0f);
        put(8000, 2.0f);
    }};
    
    public enum TransducerType {
        SUPRA_AURAL,     // Over-ear headphones
        CIRCUMAURAL,     // Around-ear headphones  
        INSERT,          // In-ear monitors/earphones
        SOUND_FIELD,     // Loudspeaker/free field
        BLUETOOTH        // Wireless earbuds/headphones
    }
    
    /**
     * Convert hearing threshold from dB HL to dB SPL
     * @param frequencyHz Test frequency in Hz
     * @param thresholdHL Threshold in dB HL (hearing level)
     * @param transducerType Type of transducer used
     * @return Threshold in dB SPL (sound pressure level)
     */
    public static float convertHLtoSPL(int frequencyHz, float thresholdHL, TransducerType transducerType) {
        Map<Integer, Float> retsplTable = getRETSPLTable(transducerType);
        
        Float retsplValue = retsplTable.get(frequencyHz);
        if (retsplValue == null) {
            // Interpolate for frequencies not in table
            retsplValue = interpolateRETSPL(frequencyHz, retsplTable);
        }
        
        return thresholdHL + retsplValue;
    }
    
    /**
     * Convert threshold from dB SPL to dB HL
     * @param frequencyHz Test frequency in Hz
     * @param thresholdSPL Threshold in dB SPL
     * @param transducerType Type of transducer used
     * @return Threshold in dB HL
     */
    public static float convertSPLtoHL(int frequencyHz, float thresholdSPL, TransducerType transducerType) {
        Map<Integer, Float> retsplTable = getRETSPLTable(transducerType);
        
        Float retsplValue = retsplTable.get(frequencyHz);
        if (retsplValue == null) {
            retsplValue = interpolateRETSPL(frequencyHz, retsplTable);
        }
        
        return thresholdSPL - retsplValue;
    }
    
    /**
     * Get RETSPL correction value for specific frequency and transducer
     */
    public static float getRETSPLCorrection(int frequencyHz, TransducerType transducerType) {
        Map<Integer, Float> retsplTable = getRETSPLTable(transducerType);
        
        Float retsplValue = retsplTable.get(frequencyHz);
        if (retsplValue == null) {
            retsplValue = interpolateRETSPL(frequencyHz, retsplTable);
        }
        
        return retsplValue;
    }
    
    /**
     * Calculate hearing loss degree based on PTA in dB HL
     */
    public static String getHearingLossClassification(float ptaHL) {
        if (ptaHL <= 20) return "NORMAL";
        else if (ptaHL <= 35) return "MILD";
        else if (ptaHL <= 50) return "MODERATE";
        else if (ptaHL <= 65) return "MODERATELY_SEVERE";
        else if (ptaHL <= 80) return "SEVERE";
        else if (ptaHL <= 95) return "PROFOUND";
        else return "TOTAL";
    }
    
    /**
     * Calculate Pure Tone Average (PTA) for speech frequencies
     */
    public static float calculatePTA(Map<Integer, Integer> thresholds) {
        int[] speechFreqs = {500, 1000, 2000};
        float sum = 0;
        int count = 0;
        
        for (int freq : speechFreqs) {
            if (thresholds.containsKey(freq)) {
                sum += thresholds.get(freq);
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0;
    }
    
    /**
     * Calculate high frequency PTA (2-8kHz) for noise-induced hearing loss detection
     */
    public static float calculateHighFrequencyPTA(Map<Integer, Integer> thresholds) {
        int[] highFreqs = {2000, 3000, 4000, 6000, 8000};
        float sum = 0;
        int count = 0;
        
        for (int freq : highFreqs) {
            if (thresholds.containsKey(freq)) {
                sum += thresholds.get(freq);
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0;
    }
    
    /**
     * Detect noise notch (characteristic of noise-induced hearing loss)
     */
    public static boolean hasNoiseNotch(Map<Integer, Integer> thresholds) {
        // Typical noise notch: 4kHz threshold significantly worse than 2kHz and 8kHz
        Integer threshold2k = thresholds.get(2000);
        Integer threshold4k = thresholds.get(4000);
        Integer threshold8k = thresholds.get(8000);
        
        if (threshold2k == null || threshold4k == null || threshold8k == null) {
            return false;
        }
        
        // 4kHz threshold should be at least 15dB worse than 2kHz and 10dB worse than 8kHz
        return (threshold4k - threshold2k >= 15) && (threshold4k - threshold8k >= 10);
    }
    
    /**
     * Calculate asymmetry between ears
     */
    public static float calculateAsymmetry(Map<Integer, Integer> leftThresholds, 
                                         Map<Integer, Integer> rightThresholds) {
        float leftPTA = calculatePTA(leftThresholds);
        float rightPTA = calculatePTA(rightThresholds);
        
        return Math.abs(leftPTA - rightPTA);
    }
    
    /**
     * Determine if asymmetry is clinically significant
     */
    public static boolean hasSignificantAsymmetry(Map<Integer, Integer> leftThresholds,
                                                Map<Integer, Integer> rightThresholds) {
        float asymmetry = calculateAsymmetry(leftThresholds, rightThresholds);
        
        // Asymmetry > 15dB PTA or > 20dB at any frequency is significant
        if (asymmetry > 15) return true;
        
        // Check individual frequency asymmetry
        int[] standardFreqs = {250, 500, 1000, 2000, 4000, 6000, 8000};
        for (int freq : standardFreqs) {
            Integer leftThresh = leftThresholds.get(freq);
            Integer rightThresh = rightThresholds.get(freq);
            
            if (leftThresh != null && rightThresh != null) {
                if (Math.abs(leftThresh - rightThresh) > 20) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private static Map<Integer, Float> getRETSPLTable(TransducerType transducerType) {
        switch (transducerType) {
            case SUPRA_AURAL: return RETSPL_SUPRA_AURAL;
            case CIRCUMAURAL: return RETSPL_CIRCUMAURAL;
            case INSERT: return RETSPL_INSERT;
            case SOUND_FIELD: return RETSPL_SOUND_FIELD;
            case BLUETOOTH: return RETSPL_BLUETOOTH;
            default: return RETSPL_INSERT; // Default to insert phones
        }
    }
    
    private static float interpolateRETSPL(int frequency, Map<Integer, Float> retsplTable) {
        // Find the two closest frequencies for linear interpolation
        int lowerFreq = 0;
        int upperFreq = Integer.MAX_VALUE;
        
        for (int freq : retsplTable.keySet()) {
            if (freq <= frequency && freq > lowerFreq) {
                lowerFreq = freq;
            }
            if (freq >= frequency && freq < upperFreq) {
                upperFreq = freq;
            }
        }
        
        if (lowerFreq == 0) {
            // Frequency below range, use lowest available
            return retsplTable.values().iterator().next();
        }
        if (upperFreq == Integer.MAX_VALUE) {
            // Frequency above range, use highest available
            return retsplTable.get(lowerFreq);
        }
        if (lowerFreq == upperFreq) {
            // Exact match
            return retsplTable.get(lowerFreq);
        }
        
        // Linear interpolation
        float lowerValue = retsplTable.get(lowerFreq);
        float upperValue = retsplTable.get(upperFreq);
        
        float ratio = (float)(frequency - lowerFreq) / (upperFreq - lowerFreq);
        return lowerValue + ratio * (upperValue - lowerValue);
    }
    
    /**
     * Get maximum safe output level for frequency/transducer combination
     * Based on hearing safety guidelines (120 dB SPL max for brief tones)
     */
    public static float getMaxSafeOutputSPL(int frequencyHz, TransducerType transducerType) {
        // General safety limit: 120 dB SPL for brief pure tones
        float maxSafeSPL = 120.0f;
        
        // Convert to dB HL for this transducer
        float retsplCorrection = getRETSPLCorrection(frequencyHz, transducerType);
        return maxSafeSPL - retsplCorrection;
    }
    
    /**
     * Validate if test level is within safe limits
     */
    public static boolean isSafeTestLevel(int frequencyHz, float levelHL, TransducerType transducerType) {
        float maxSafeHL = getMaxSafeOutputSPL(frequencyHz, transducerType);
        return levelHL <= maxSafeHL;
    }
}