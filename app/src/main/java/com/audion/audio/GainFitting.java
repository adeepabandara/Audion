package com.audion.audio;

import android.util.Log;
import com.example.audion.data.HearingTestResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clinical gain fitting formulas: NAL-NL2 and DSL v5.
 * 
 * NAL-NL2 (National Acoustic Laboratories - Nonlinear 2):
 * - Optimizes speech intelligibility while maintaining overall loudness comfort
 * - Generally prescribes less gain in low frequencies
 * - "Clarity" mode
 * 
 * DSL v5 (Desired Sensation Level):
 * - Ensures audibility across full frequency range
 * - Prescribes more gain than NAL-NL2, especially for severe losses
 * - "Comfort" mode
 * 
 * Formulas simplified from clinical research papers.
 */
public class GainFitting {
    private static final String TAG = "GainFitting";
    
    /**
     * Fitting mode selection.
     */
    public enum FittingMode {
        NAL_NL2,  // Clarity-focused (speech intelligibility)
        DSL_V5    // Comfort-focused (audibility across spectrum)
    }
    
    // NAL-NL2 frequency-dependent constants (simplified approximation)
    // Based on NAL-NL2 technical paper (Keidser et al. 2011)
    private static final Map<Integer, NALConstants> NAL_LOOKUP = new HashMap<Integer, NALConstants>() {{
        put(250,  new NALConstants(0.35f, 0.010f, -5.0f));
        put(500,  new NALConstants(0.40f, 0.012f, -3.0f));
        put(1000, new NALConstants(0.45f, 0.015f,  0.0f));
        put(2000, new NALConstants(0.50f, 0.018f,  2.0f));
        put(4000, new NALConstants(0.55f, 0.020f,  5.0f));
        put(6000, new NALConstants(0.52f, 0.019f,  4.0f));
        put(8000, new NALConstants(0.50f, 0.018f,  3.5f));  // Phase 4: Added 8 kHz
    }};
    
    // DSL v5 frequency-dependent constants (simplified approximation)
    // Based on DSL v5.0 technical paper (Scollie et al. 2005)
    private static final Map<Integer, DSLConstants> DSL_LOOKUP = new HashMap<Integer, DSLConstants>() {{
        put(250,  new DSLConstants(0.45f, 0.015f, -2.0f));
        put(500,  new DSLConstants(0.50f, 0.018f,  0.0f));
        put(1000, new DSLConstants(0.55f, 0.020f,  2.0f));
        put(2000, new DSLConstants(0.60f, 0.022f,  4.0f));
        put(4000, new DSLConstants(0.65f, 0.025f,  6.0f));
        put(6000, new DSLConstants(0.62f, 0.023f,  5.0f));
        put(8000, new DSLConstants(0.60f, 0.022f,  4.5f));  // Phase 4: Added 8 kHz
    }};
    
    /**
     * Calculate target gain for all 5 bands using selected fitting formula (Phase 4: Extended to 8 kHz).
     * 
     * @param audiogram List of hearing test results
     * @param mode Fitting mode (NAL-NL2 or DSL v5)
     * @param inputLevelDbSPL Input sound level in dB SPL (e.g., 65 for conversational speech)
     * @return Array of 5 linear gain multipliers for bands [250-750, 750-1500, 1500-3000, 3000-6000, 6000-8000 Hz]
     */
    public static float[] calculateBandGains(List<HearingTestResult> audiogram, FittingMode mode, 
                                            float inputLevelDbSPL) {
        if (audiogram == null || audiogram.isEmpty()) {
            Log.w(TAG, "No audiogram data - returning unity gains");
            return new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
        }
        
        // Map audiogram frequencies to bands (PHASE 4: Extended to 5 bands)
        // Band 0 (250-750 Hz):   Use 250, 500 Hz
        // Band 1 (750-1500 Hz):  Use 1000 Hz
        // Band 2 (1500-3000 Hz): Use 2000 Hz
        // Band 3 (3000-6000 Hz): Use 4000, 6000 Hz
        // Band 4 (6000-8000 Hz): Use 8000 Hz (NEW - Phase 4 for /s/, /f/, /th/)
        
        float[] gainDb = new float[5];
        
        // Band 0: Low frequencies (250-750 Hz)
        float threshold250 = getThresholdAtFrequency(audiogram, 250);
        float threshold500 = getThresholdAtFrequency(audiogram, 500);
        float avgThreshold0 = (threshold250 + threshold500) / 2.0f;
        gainDb[0] = calculateGainForFrequency(500, avgThreshold0, inputLevelDbSPL, mode);
        
        // Band 1: Mid-low frequencies (750-1500 Hz)
        float threshold1000 = getThresholdAtFrequency(audiogram, 1000);
        gainDb[1] = calculateGainForFrequency(1000, threshold1000, inputLevelDbSPL, mode);
        
        // Band 2: Mid-high frequencies (1500-3000 Hz)
        float threshold2000 = getThresholdAtFrequency(audiogram, 2000);
        gainDb[2] = calculateGainForFrequency(2000, threshold2000, inputLevelDbSPL, mode);
        
        // Band 3: High frequencies (3000-6000 Hz)
        float threshold4000 = getThresholdAtFrequency(audiogram, 4000);
        float threshold6000 = getThresholdAtFrequency(audiogram, 6000);
        float avgThreshold3 = (threshold4000 + threshold6000) / 2.0f;
        gainDb[3] = calculateGainForFrequency(4000, avgThreshold3, inputLevelDbSPL, mode);
        
        // Band 4: Very high frequencies (6000-8000 Hz) - PHASE 4: NEW
        float threshold8000 = getThresholdAtFrequency(audiogram, 8000);
        gainDb[4] = calculateGainForFrequency(8000, threshold8000, inputLevelDbSPL, mode);
        
        // Convert dB gains to linear multipliers
        float[] gainLinear = new float[5];
        for (int i = 0; i < 5; i++) {
            gainLinear[i] = dbToLinear(gainDb[i]);
        }
        
        Log.i(TAG, String.format("%s fitting: Band gains = [%.2f, %.2f, %.2f, %.2f, %.2f] (%.1f, %.1f, %.1f, %.1f, %.1f dB)",
            mode, gainLinear[0], gainLinear[1], gainLinear[2], gainLinear[3], gainLinear[4],
            gainDb[0], gainDb[1], gainDb[2], gainDb[3], gainDb[4]));
        
        return gainLinear;
    }
    
    /**
     * Calculate target gain for a specific frequency.
     * 
     * @param frequency Frequency in Hz
     * @param thresholdDbHL Hearing threshold at this frequency (dB HL)
     * @param inputLevelDbSPL Input sound level (dB SPL)
     * @param mode Fitting mode
     * @return Target gain in dB
     */
    private static float calculateGainForFrequency(int frequency, float thresholdDbHL, 
                                                   float inputLevelDbSPL, FittingMode mode) {
        if (mode == FittingMode.NAL_NL2) {
            return calculateNALGain(frequency, thresholdDbHL, inputLevelDbSPL);
        } else {
            return calculateDSLGain(frequency, thresholdDbHL, inputLevelDbSPL);
        }
    }
    
    /**
     * NAL-NL2 gain formula (simplified).
     * 
     * General form: Gain = A × (H + B × (L - 65)) + C
     * Where:
     *   H = hearing threshold (dB HL)
     *   L = input level (dB SPL)
     *   A, B, C = frequency-dependent constants
     * 
     * @param frequency Frequency in Hz
     * @param thresholdDbHL Hearing threshold (dB HL)
     * @param inputLevelDbSPL Input level (dB SPL)
     * @return Prescribed gain in dB
     */
    private static float calculateNALGain(int frequency, float thresholdDbHL, float inputLevelDbSPL) {
        NALConstants c = getNALConstants(frequency);
        
        // NAL-NL2 formula
        float gainDb = c.A * (thresholdDbHL + c.B * (inputLevelDbSPL - 65.0f)) + c.C;
        
        // Clamp to reasonable range
        gainDb = Math.max(0.0f, Math.min(gainDb, 60.0f));
        
        return gainDb;
    }
    
    /**
     * DSL v5 gain formula (simplified).
     * 
     * General form: Gain = A × H + B × (L - 60) + C
     * Where:
     *   H = hearing threshold (dB HL)
     *   L = input level (dB SPL)
     *   A, B, C = frequency-dependent constants
     * 
     * DSL typically prescribes more gain than NAL-NL2, especially for severe losses.
     * 
     * @param frequency Frequency in Hz
     * @param thresholdDbHL Hearing threshold (dB HL)
     * @param inputLevelDbSPL Input level (dB SPL)
     * @return Prescribed gain in dB
     */
    private static float calculateDSLGain(int frequency, float thresholdDbHL, float inputLevelDbSPL) {
        DSLConstants c = getDSLConstants(frequency);
        
        // DSL v5 formula
        float gainDb = c.A * thresholdDbHL + c.B * (inputLevelDbSPL - 60.0f) + c.C;
        
        // Clamp to reasonable range
        gainDb = Math.max(0.0f, Math.min(gainDb, 70.0f));
        
        return gainDb;
    }
    
    /**
     * Get NAL-NL2 constants for frequency (with interpolation).
     */
    private static NALConstants getNALConstants(int frequency) {
        if (NAL_LOOKUP.containsKey(frequency)) {
            return NAL_LOOKUP.get(frequency);
        }
        
        // Find nearest frequencies for interpolation
        int lowerFreq = 250;
        int upperFreq = 8000;  // Phase 4: Extended from 6000 to 8000
        
        for (int f : NAL_LOOKUP.keySet()) {
            if (f < frequency && f > lowerFreq) lowerFreq = f;
            if (f > frequency && f < upperFreq) upperFreq = f;
        }
        
        // Linear interpolation
        NALConstants lower = NAL_LOOKUP.get(lowerFreq);
        NALConstants upper = NAL_LOOKUP.get(upperFreq);
        float ratio = (float)(frequency - lowerFreq) / (upperFreq - lowerFreq);
        
        return new NALConstants(
            lower.A + ratio * (upper.A - lower.A),
            lower.B + ratio * (upper.B - lower.B),
            lower.C + ratio * (upper.C - lower.C)
        );
    }
    
    /**
     * Get DSL v5 constants for frequency (with interpolation).
     */
    private static DSLConstants getDSLConstants(int frequency) {
        if (DSL_LOOKUP.containsKey(frequency)) {
            return DSL_LOOKUP.get(frequency);
        }
        
        // Find nearest frequencies for interpolation
        int lowerFreq = 250;
        int upperFreq = 8000;  // Phase 4: Extended from 6000 to 8000
        
        for (int f : DSL_LOOKUP.keySet()) {
            if (f < frequency && f > lowerFreq) lowerFreq = f;
            if (f > frequency && f < upperFreq) upperFreq = f;
        }
        
        // Linear interpolation
        DSLConstants lower = DSL_LOOKUP.get(lowerFreq);
        DSLConstants upper = DSL_LOOKUP.get(upperFreq);
        float ratio = (float)(frequency - lowerFreq) / (upperFreq - lowerFreq);
        
        return new DSLConstants(
            lower.A + ratio * (upper.A - lower.A),
            lower.B + ratio * (upper.B - lower.B),
            lower.C + ratio * (upper.C - lower.C)
        );
    }
    
    /**
     * Get hearing threshold at specific frequency from audiogram.
     */
    private static float getThresholdAtFrequency(List<HearingTestResult> audiogram, int frequency) {
        for (HearingTestResult result : audiogram) {
            if (result.getFrequency() == frequency) {
                return result.getThresholdDbHL();
            }
        }
        
        // If exact frequency not found, interpolate from nearest frequencies
        HearingTestResult lower = null;
        HearingTestResult upper = null;
        
        for (HearingTestResult result : audiogram) {
            int f = result.getFrequency();
            if (f < frequency && (lower == null || f > lower.getFrequency())) {
                lower = result;
            }
            if (f > frequency && (upper == null || f < upper.getFrequency())) {
                upper = result;
            }
        }
        
        if (lower != null && upper != null) {
            // Linear interpolation in log frequency space
            float logFreq = (float)Math.log10(frequency);
            float logLower = (float)Math.log10(lower.getFrequency());
            float logUpper = (float)Math.log10(upper.getFrequency());
            float ratio = (logFreq - logLower) / (logUpper - logLower);
            
            return lower.getThresholdDbHL() + ratio * (upper.getThresholdDbHL() - lower.getThresholdDbHL());
        } else if (lower != null) {
            return lower.getThresholdDbHL();
        } else if (upper != null) {
            return upper.getThresholdDbHL();
        } else {
            // No audiogram data - assume normal hearing
            Log.w(TAG, "No audiogram data at " + frequency + " Hz - assuming 0 dB HL");
            return 0.0f;
        }
    }
    
    // Utility functions
    
    private static float dbToLinear(float db) {
        return (float) Math.pow(10.0, db / 20.0);
    }
    
    /**
     * NAL-NL2 frequency-dependent constants.
     */
    private static class NALConstants {
        final float A;  // Threshold scaling factor
        final float B;  // Level-dependent factor
        final float C;  // Frequency-dependent offset
        
        NALConstants(float a, float b, float c) {
            this.A = a;
            this.B = b;
            this.C = c;
        }
    }
    
    /**
     * DSL v5 frequency-dependent constants.
     */
    private static class DSLConstants {
        final float A;  // Threshold scaling factor
        final float B;  // Level-dependent factor
        final float C;  // Frequency-dependent offset
        
        DSLConstants(float a, float b, float c) {
            this.A = a;
            this.B = b;
            this.C = c;
        }
    }
}
