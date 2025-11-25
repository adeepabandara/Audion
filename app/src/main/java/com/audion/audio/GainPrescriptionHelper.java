package com.audion.audio;

import android.util.Log;

import com.audion.app.data.CalibrationProfileEntity;
import com.audion.app.data.HearingTestResult;

import java.util.List;

/**
 * Clinical gain prescription helper - Phase 1 implementation.
 * 
 * Implements simplified NAL-RP formula for baseline gain calculation
 * and UCL-based safety limiting.
 * 
 * Phase 1: Global gain based on Pure Tone Average (PTA)
 * Phase 2+: Per-frequency, per-band, WDRC compression
 */
public class GainPrescriptionHelper {
    private static final String TAG = "GainPrescription";
    
    /**
     * Calculate Pure Tone Average (PTA) from audiogram.
     * Uses 500, 1000, 2000 Hz thresholds (standard 3-frequency PTA).
     * 
     * @param audiogram List of hearing test results
     * @return PTA in dB HL, or 0.0 if insufficient data
     */
    public static float calculatePTA(List<HearingTestResult> audiogram) {
        if (audiogram == null || audiogram.isEmpty()) {
            Log.w(TAG, "No audiogram data available");
            return 0.0f;
        }
        
        float sum = 0;
        int count = 0;
        
        for (HearingTestResult result : audiogram) {
            int freq = result.getFrequency();
            if (freq == 500 || freq == 1000 || freq == 2000) {
                float threshold = result.getThresholdDbHL();
                sum += threshold;
                count++;
                // Debug logging removed for production
                // Log.d(TAG, "PTA calculation: " + freq + " Hz = " + threshold + " dB HL");
            }
        }
        
        if (count == 0) {
            Log.w(TAG, "No PTA frequencies (500/1000/2000 Hz) found in audiogram");
            return 0.0f;
        }
        
        float pta = sum / count;
        Log.i(TAG, String.format("PTA calculated: %.1f dB HL (from %d frequencies)", pta, count));
        
        return pta;
    }
    
    /**
     * Calculate recommended baseline gain from audiogram using simplified NAL-RP formula.
     * 
     * Formula: Gain ≈ 0.4 × PTA
     * 
     * This is a simplified approximation of NAL-RP (Revised, Profound) for consumer devices.
     * Full NAL-NL2 would include frequency-dependent factors, input level, and age corrections.
     * 
     * @param audiogram List of hearing test results for one ear
     * @return Recommended gain in dB (0.0 if no audiogram)
     */
    public static float calculateRecommendedGain(List<HearingTestResult> audiogram) {
        float pta = calculatePTA(audiogram);
        
        if (pta == 0.0f) {
            return 0.0f; // No audiogram → no recommendation
        }
        
        // NAL-RP simplified: Gain = 0.4 × PTA
        float recommendedGain = 0.4f * pta;
        
        Log.i(TAG, String.format("Recommended gain: %.1f dB (from PTA=%.1f dB HL)", 
            recommendedGain, pta));
        
        return recommendedGain;
    }
    
    /**
     * Calculate recommended baseline gain from both ears' audiograms.
     * Returns the average of left and right ear recommendations.
     * 
     * @param leftEar Left ear audiogram
     * @param rightEar Right ear audiogram
     * @return Average recommended gain in dB
     */
    public static float calculateBinauralRecommendedGain(
        List<HearingTestResult> leftEar, 
        List<HearingTestResult> rightEar
    ) {
        float leftGain = calculateRecommendedGain(leftEar);
        float rightGain = calculateRecommendedGain(rightEar);
        
        if (leftGain == 0.0f && rightGain == 0.0f) {
            Log.w(TAG, "No audiogram data for either ear");
            return 0.0f;
        }
        
        // If only one ear has data, use that
        if (leftGain == 0.0f) {
            Log.i(TAG, "Using right ear gain only: " + rightGain + " dB");
            return rightGain;
        }
        if (rightGain == 0.0f) {
            Log.i(TAG, "Using left ear gain only: " + leftGain + " dB");
            return leftGain;
        }
        
        // Average both ears
        float avgGain = (leftGain + rightGain) / 2.0f;
        
        Log.i(TAG, String.format("Binaural gain: Left=%.1f dB, Right=%.1f dB → Avg=%.1f dB",
            leftGain, rightGain, avgGain));
        
        return avgGain;
    }
    
    /**
     * Calculate safe maximum gain based on UCL (Uncomfortable Level).
     * 
     * Safety formula: Max Gain = avg UCL - 65 dB SPL
     * 
     * Rationale:
     * - 65 dB SPL = typical conversational speech input level
     * - UCL = uncomfortable level (typically 85-95 dB SPL)
     * - Max safe output = UCL
     * - Therefore: Max gain = UCL - input_level
     * 
     * @param leftCal Left ear calibration profile
     * @param rightCal Right ear calibration profile
     * @return Safe maximum gain in dB (100.0 if no calibration data)
     */
    public static float calculateSafeMaxGain(
        CalibrationProfileEntity leftCal,
        CalibrationProfileEntity rightCal
    ) {
        if (leftCal == null && rightCal == null) {
            Log.w(TAG, "No calibration data - using default max gain (100 dB)");
            return 100.0f; // Default fallback
        }
        
        float avgUCL;
        
        if (leftCal != null && rightCal != null) {
            // Both ears have calibration
            avgUCL = (leftCal.getUclDbSpl() + rightCal.getUclDbSpl()) / 2.0f;
            // Debug logging removed for production
            // Log.d(TAG, String.format("UCL: Left=%.1f dB SPL, Right=%.1f dB SPL → Avg=%.1f dB SPL",
            //     leftCal.getUclDbSpl(), rightCal.getUclDbSpl(), avgUCL));
        } else if (leftCal != null) {
            // Only left ear
            avgUCL = leftCal.getUclDbSpl();
            // Debug logging removed for production
            // Log.d(TAG, "UCL: Left only = " + avgUCL + " dB SPL");
        } else {
            // Only right ear
            avgUCL = rightCal.getUclDbSpl();
            // Debug logging removed for production
            // Log.d(TAG, "UCL: Right only = " + avgUCL + " dB SPL");
        }
        
        // Calculate safe max gain: UCL - typical input level (65 dB SPL)
        float safeMaxGain = avgUCL - 65.0f;
        
        // Clamp to reasonable bounds (0-100 dB)
        if (safeMaxGain < 0) {
            Log.w(TAG, "Calculated negative max gain (" + safeMaxGain + " dB) - clamping to 0 dB");
            safeMaxGain = 0;
        }
        if (safeMaxGain > 100.0f) {
            Log.w(TAG, "Calculated excessive max gain (" + safeMaxGain + " dB) - clamping to 100 dB");
            safeMaxGain = 100.0f;
        }
        
        Log.i(TAG, String.format("Safe max gain: %.1f dB (from avg UCL=%.1f dB SPL - 65 dB input)",
            safeMaxGain, avgUCL));
        
        return safeMaxGain;
    }
    
    /**
     * Clamp amplification gain to safe maximum based on UCL.
     * 
     * @param requestedGain Gain requested by user (dB)
     * @param leftCal Left ear calibration profile
     * @param rightCal Right ear calibration profile
     * @return Clamped gain (dB), guaranteed ≤ safe max
     */
    public static float clampToSafeGain(
        float requestedGain,
        CalibrationProfileEntity leftCal,
        CalibrationProfileEntity rightCal
    ) {
        float safeMaxGain = calculateSafeMaxGain(leftCal, rightCal);
        
        if (requestedGain > safeMaxGain) {
            Log.w(TAG, String.format("Clamping gain from %.1f dB to %.1f dB (UCL safety limit)",
                requestedGain, safeMaxGain));
            return safeMaxGain;
        }
        
        return requestedGain;
    }
    
    /**
     * Check if personalization data is complete and valid.
     * 
     * @param leftEar Left ear audiogram
     * @param rightEar Right ear audiogram
     * @param leftCal Left ear calibration
     * @param rightCal Right ear calibration
     * @return true if both audiogram and calibration exist for at least one ear
     */
    public static boolean hasCompletePersonalization(
        List<HearingTestResult> leftEar,
        List<HearingTestResult> rightEar,
        CalibrationProfileEntity leftCal,
        CalibrationProfileEntity rightCal
    ) {
        boolean hasAudiogram = (leftEar != null && !leftEar.isEmpty()) || 
                               (rightEar != null && !rightEar.isEmpty());
        boolean hasCalibration = leftCal != null || rightCal != null;
        
        boolean isComplete = hasAudiogram && hasCalibration;
        
        Log.i(TAG, String.format("Personalization check: Audiogram=%s, Calibration=%s → Complete=%s",
            hasAudiogram, hasCalibration, isComplete));
        
        return isComplete;
    }
    
    // ========================================================================
    // Phase 2: Per-Band Gain Prescription
    // ========================================================================
    
    /**
     * Calculate per-band gain factors from audiogram for 4-band filterbank.
     * 
     * Bands:
     * - Band 0: 250-750 Hz   (uses 500 Hz threshold)
     * - Band 1: 750-1500 Hz  (uses 1000 Hz threshold)
     * - Band 2: 1500-3000 Hz (uses 2000 Hz threshold)
     * - Band 3: 3000-6000 Hz (uses 4000 Hz threshold)
     * 
     * Formula: gain_dB = (80 - threshold_dBHL) × 0.5
     * 
     * This is a simplified frequency-specific gain prescription:
     * - 80 dB = reference "normal conversational + amplification" level
     * - 0.5 = insertion gain factor (NAL-RP approximation)
     * - For threshold = 40 dB HL → gain = (80-40)×0.5 = 20 dB
     * - For threshold = 60 dB HL → gain = (80-60)×0.5 = 10 dB
     * 
     * @param audiogram List of hearing test results
     * @return Array of 4 linear gain factors (NOT dB)
     */
    public static float[] calculatePerBandGains(List<HearingTestResult> audiogram) {
        // Default gains (unity = 1.0x = 0 dB)
        float[] gains = {1.0f, 1.0f, 1.0f, 1.0f};
        
        if (audiogram == null || audiogram.isEmpty()) {
            Log.w(TAG, "No audiogram data - using unity gains");
            return gains;
        }
        
        // Map test frequencies to bands
        // Band 0: 250-750 Hz → use 500 Hz threshold
        // Band 1: 750-1500 Hz → use 1000 Hz threshold
        // Band 2: 1500-3000 Hz → use 2000 Hz threshold
        // Band 3: 3000-6000 Hz → use 4000 Hz threshold
        int[] targetFreqs = {500, 1000, 2000, 4000};
        
        for (int band = 0; band < 4; band++) {
            float threshold = getThresholdAtFrequency(audiogram, targetFreqs[band]);
            
            if (threshold > 0) {
                // Calculate gain: gain_dB = (80 - threshold_dBHL) × 0.5
                float gainDb = (80.0f - threshold) * 0.5f;
                
                // Clamp to reasonable bounds (0-40 dB)
                if (gainDb < 0) {
                    // Debug logging removed for production
                    // Log.d(TAG, String.format("Band %d: Threshold %.1f dB HL is above 80 dB - using 0 dB gain", 
                    //     band, threshold));
                    gainDb = 0;
                }
                if (gainDb > 40.0f) {
                    // Debug logging removed for production
                    // Log.d(TAG, String.format("Band %d: Calculated gain %.1f dB exceeds limit - clamping to 40 dB",
                    //     band, gainDb));
                    gainDb = 40.0f;
                }
                
                // Convert dB to linear gain: gain = 10^(dB/20)
                gains[band] = (float) Math.pow(10.0, gainDb / 20.0);
                
                // Debug logging removed for production
                // Log.d(TAG, String.format("Band %d (%d Hz): Threshold=%.1f dB HL → Gain=%.1f dB (%.2fx)",
                //     band, targetFreqs[band], threshold, gainDb, gains[band]));
            } else {
                Log.w(TAG, String.format("Band %d (%d Hz): No threshold data - using unity gain",
                    band, targetFreqs[band]));
            }
        }
        
        Log.i(TAG, String.format("Per-band gains calculated: [%.2f, %.2f, %.2f, %.2f]",
            gains[0], gains[1], gains[2], gains[3]));
        
        return gains;
    }
    
    /**
     * Get threshold at specific frequency from audiogram.
     * If exact frequency not found, returns 0.0 (will use unity gain).
     * 
     * @param audiogram Audiogram results
     * @param frequency Target frequency (Hz)
     * @return Threshold in dB HL, or 0.0 if not found
     */
    private static float getThresholdAtFrequency(List<HearingTestResult> audiogram, int frequency) {
        for (HearingTestResult result : audiogram) {
            if (result.getFrequency() == frequency) {
                return result.getThresholdDbHL();
            }
        }
        return 0.0f; // Not found
    }
    
    /**
     * Calculate per-band gains with interpolation for missing frequencies.
     * 
     * This version interpolates between adjacent test frequencies if exact
     * frequency is missing, providing smoother frequency response.
     * 
     * @param audiogram Audiogram results
     * @return Array of 4 linear gain factors
     */
    public static float[] calculatePerBandGainsInterpolated(List<HearingTestResult> audiogram) {
        // For Phase 2, we use the simpler exact-frequency method
        // Interpolation can be added in Phase 3 if needed
        return calculatePerBandGains(audiogram);
    }
    
    /**
     * Calculate binaural per-band gains (average left and right ears).
     * 
     * This is used when mono processing is applied but we want to account
     * for asymmetric hearing loss in gain prescription.
     * 
     * @param leftEar Left ear audiogram
     * @param rightEar Right ear audiogram
     * @return Array of 4 linear gain factors (averaged)
     */
    public static float[] calculateBinauralPerBandGains(
        List<HearingTestResult> leftEar,
        List<HearingTestResult> rightEar
    ) {
        float[] leftGains = calculatePerBandGains(leftEar);
        float[] rightGains = calculatePerBandGains(rightEar);
        float[] avgGains = new float[4];
        
        for (int i = 0; i < 4; i++) {
            avgGains[i] = (leftGains[i] + rightGains[i]) / 2.0f;
        }
        
        Log.i(TAG, String.format("Binaural per-band gains: [%.2f, %.2f, %.2f, %.2f]",
            avgGains[0], avgGains[1], avgGains[2], avgGains[3]));
        
        return avgGains;
    }
}

