package com.example.audion;

import android.content.Context;
import android.util.Log;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.AudiometryResult;
import com.example.audion.data.AudiometryResultDao;
import com.example.audion.data.CalibrationProfileEntity;
import com.example.audion.data.CalibrationProfileDao;
import com.example.audion.dsp.WDRCSettings;
import com.example.audion.dsp.PresenceSettings;
import com.example.audion.dsp.NoiseSettings;
import com.example.audion.dsp.LimiterSettings;

import org.json.JSONObject;
import org.json.JSONException;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * PersonalizedGainMapper - NAL-NL2 inspired gain calculation engine
 * 
 * Bridges ANSI S3.6 audiometry results and calibration profiles to DSP processor settings.
 * Implements NAL-NL2 inspired gain calculations with personalized MPO enforcement.
 * 
 * Phase 2 Enhancement: Added input-level dependency for adaptive gain curves.
 * 
 * Core responsibilities:
 * - Convert dB HL audiometry thresholds to frequency-specific gains
 * - Apply NAL-inspired gain calculations with input SPL compensation
 * - Map MCL/UCL calibration data to personalized limiter settings
 * - Generate processor-specific configurations (WDRC, Presence, Noise, Limiter)
 * - Enforce device safety limits with personalized UCL constraints
 */
public class PersonalizedGainMapper {
    private static final String TAG = "PersonalizedGainMapper";
    
    // NAL-NL2 inspired gain calculation constants
    private static final double[] NAL_INSERTION_GAIN_COEFFS = {
        0.31, 0.31, 0.31, 0.31, 0.31, 0.31  // 250Hz to 8kHz simplified
    };
    
    // Input-level dependent gain factors (NAL-NL2 enhancement)
    // Gain varies with input SPL for natural sound quality
    private static final double INPUT_SPL_SOFT = 50.0;   // Soft speech ~50 dB SPL
    private static final double INPUT_SPL_MEDIUM = 65.0; // Conversational speech ~65 dB SPL
    private static final double INPUT_SPL_LOUD = 80.0;   // Loud speech ~80 dB SPL
    
    // Gain adjustment factors for different input levels
    private static final double GAIN_FACTOR_SOFT = 1.2;   // +20% gain for soft sounds
    private static final double GAIN_FACTOR_MEDIUM = 1.0; // Baseline gain for medium sounds
    private static final double GAIN_FACTOR_LOUD = 0.6;   // -40% gain for loud sounds
    
    // Frequency-specific speech importance weighting
    private static final Map<Integer, Double> SPEECH_IMPORTANCE = new HashMap<Integer, Double>() {{
        put(250, 0.1);   // Low frequencies - less critical for speech
        put(500, 0.2);   // Speech energy present
        put(1000, 0.4);  // Core speech frequencies
        put(2000, 0.5);  // Most critical for speech clarity
        put(4000, 0.4);  // Consonant recognition
        put(8000, 0.2);  // Sibilant sounds
    }};
    
    // Device-specific safety margins (dB)
    private static final int HEADPHONE_SAFETY_MARGIN = 10;
    private static final int SPEAKER_SAFETY_MARGIN = 15;
    
    private final Context context;
    private final AudiometryResultDao audiometryDao;
    private final CalibrationProfileDao calibrationDao;
    
    public PersonalizedGainMapper(Context context) {
        this.context = context;
        AppDatabase db = AppDatabase.getInstance(context);
        this.audiometryDao = db.audiometryResultDao();
        this.calibrationDao = db.calibrationProfileDao();
    }
    
    /**
     * Calculate input-level dependent gain factor (NAL-NL2 enhancement)
     * 
     * NAL-NL2 varies gain based on input SPL for natural sound quality:
     * - Soft sounds (50 dB SPL): More gain (+20%)
     * - Conversational speech (65 dB SPL): Baseline gain (1.0x)
     * - Loud sounds (80 dB SPL): Reduced gain (-40%)
     * 
     * This creates adaptive compression curves similar to commercial hearing aids.
     * 
     * @param inputSpl Input sound pressure level in dB SPL
     * @param htl Hearing threshold level in dB HL
     * @return Gain multiplication factor (0.6 to 1.2)
     */
    private double calculateInputLevelFactor(double inputSpl, double htl) {
        // More severe hearing loss requires less input-level variation
        // Mild loss uses full range, severe loss uses compressed range
        double compressionRange = 1.0;
        
        if (htl > 60.0) {
            // Severe loss: Reduce input-level variation (0.8 to 1.1)
            compressionRange = 0.5;
        } else if (htl > 40.0) {
            // Moderate loss: Standard variation (0.7 to 1.15)
            compressionRange = 0.75;
        }
        // else: Mild loss uses full variation (0.6 to 1.2)
        
        // Piecewise linear input-level dependency
        double factor;
        if (inputSpl <= INPUT_SPL_SOFT) {
            // Soft input: maximum gain
            factor = GAIN_FACTOR_SOFT;
        } else if (inputSpl <= INPUT_SPL_MEDIUM) {
            // Soft to medium: linear interpolation
            double t = (inputSpl - INPUT_SPL_SOFT) / (INPUT_SPL_MEDIUM - INPUT_SPL_SOFT);
            factor = GAIN_FACTOR_SOFT + t * (GAIN_FACTOR_MEDIUM - GAIN_FACTOR_SOFT);
        } else if (inputSpl <= INPUT_SPL_LOUD) {
            // Medium to loud: linear interpolation
            double t = (inputSpl - INPUT_SPL_MEDIUM) / (INPUT_SPL_LOUD - INPUT_SPL_MEDIUM);
            factor = GAIN_FACTOR_MEDIUM + t * (GAIN_FACTOR_LOUD - GAIN_FACTOR_MEDIUM);
        } else {
            // Very loud input: minimum gain
            factor = GAIN_FACTOR_LOUD;
        }
        
        // Apply compression range based on hearing loss severity
        // Scales factor toward 1.0 for severe losses
        return 1.0 + (factor - 1.0) * compressionRange;
    }
    
    /**
     * Generate personalized WDRC settings from calibration data (MCL/UCL)
     * 
     * Uses MCL values as proxy for hearing thresholds in NAL-NL2 calculations.
     * MCL (Most Comfortable Level) typically correlates with hearing loss severity.
     * 
     * @param userId User identifier
     * @param ear "LEFT" or "RIGHT"
     * @param hearingProfileId Profile identifier
     * @return WDRCSettings configured from calibration data, or null if no calibration found
     */
    private WDRCSettings generateWDRCSettingsFromCalibration(int userId, String ear, int hearingProfileId) {
        try {
            // Get latest calibration profile for this ear
            CalibrationProfileEntity profile = calibrationDao.getLatestProfileForEar(userId, ear, hearingProfileId);
            
            if (profile == null || profile.getMclPerFrequencyJson() == null) {
                Log.d(TAG, "No calibration profile found for " + ear + " ear");
                return null;
            }
            
            // Parse MCL and UCL per-frequency JSON
            JSONObject mclData = new JSONObject(profile.getMclPerFrequencyJson());
            JSONObject uclData = null;
            if (profile.getUclPerFrequencyJson() != null) {
                uclData = new JSONObject(profile.getUclPerFrequencyJson());
            }
            
            Map<Integer, Float> frequencyGains = new HashMap<>();
            float avgDynamicRange = 0f;
            float avgMCL = 0f;
            int validFrequencies = 0;
            
            // Iterate through MCL data and calculate NAL-NL2 inspired gains
            // Use dynamic range (UCL-MCL) as indicator of hearing loss severity
            Iterator<String> keys = mclData.keys();
            while (keys.hasNext()) {
                String freqStr = keys.next();
                int frequency = Integer.parseInt(freqStr);
                double mclDbSpl = mclData.getDouble(freqStr);
                double uclDbSpl = (uclData != null && uclData.has(freqStr)) ? 
                                  uclData.getDouble(freqStr) : (mclDbSpl + 30.0);
                
                // Calculate dynamic range (UCL - MCL)
                // Normal hearing: DR ~40-50 dB
                // Hearing loss with recruitment: DR ~20-30 dB (narrow)
                double dynamicRange = uclDbSpl - mclDbSpl;
                
                // Estimate hearing threshold from dynamic range and MCL
                // Narrower DR suggests more severe hearing loss
                // Normal: MCL ~65dB, DR ~45dB -> HTL ~20dB
                // Loss: MCL ~70dB, DR ~25dB -> HTL ~45dB
                double normalDR = 45.0;
                double drFactor = Math.max(0, (normalDR - dynamicRange) / normalDR); // 0.0 = normal, 1.0 = severe
                
                // Base hearing threshold from reduced dynamic range
                double estimatedHTL = drFactor * 60.0; // Max 60 dB HL estimate
                
                // Additional threshold elevation if MCL is elevated above 65 dB SPL
                double mclElevation = Math.max(0, mclDbSpl - 65.0);
                estimatedHTL += mclElevation * 0.5; // Add 50% of MCL elevation
                
                // Ensure reasonable HTL range (0-80 dB HL)
                estimatedHTL = Math.min(80.0, estimatedHTL);
                
                // NAL-NL2 formula: Gain = f(HTL, InputSPL, frequency)
                // Use modified formula to ensure positive gains for hearing aid use
                double baseGain = 0.6 * estimatedHTL + 5.0; // Linear gain prescription
                
                // Apply speech importance weighting
                double speechWeight = SPEECH_IMPORTANCE.getOrDefault(frequency, 0.3);
                
                // Calculate input-level dependent gain for medium speech (65 dB SPL)
                double inputLevelFactor = calculateInputLevelFactor(INPUT_SPL_MEDIUM, estimatedHTL);
                double personalizedGain = baseGain * speechWeight * inputLevelFactor;
                
                frequencyGains.put(frequency, (float) personalizedGain);
                avgMCL += (float) mclDbSpl;
                avgDynamicRange += (float) dynamicRange;
                validFrequencies++;
                
                Log.d(TAG, String.format("Calibration WDRC: %dHz MCL=%.1fdB UCL=%.1fdB DR=%.1fdB -> HTL=%.1fdB HL -> Gain=%.1fdB", 
                                          frequency, mclDbSpl, uclDbSpl, dynamicRange, estimatedHTL, personalizedGain));
            }
            
            if (validFrequencies == 0) {
                return null;
            }
            
            avgMCL /= validFrequencies;
            avgDynamicRange /= validFrequencies;
            
            // Interpolate gains for missing filterbank frequencies
            // Filterbank has 10 bands: 250, 500, 750, 1000, 1500, 2000, 3000, 4000, 6000, 8000 Hz
            int[] filterbankFreqs = {250, 500, 750, 1000, 1500, 2000, 3000, 4000, 6000, 8000};
            for (int freq : filterbankFreqs) {
                if (!frequencyGains.containsKey(freq)) {
                    // Interpolate from nearest available frequencies
                    float interpolatedGain = interpolateGain(freq, frequencyGains);
                    frequencyGains.put(freq, interpolatedGain);
                    Log.d(TAG, String.format("Interpolated gain for %dHz: %.1fdB", freq, interpolatedGain));
                }
            }
            
            // Estimate average hearing loss from dynamic range
            double normalDR = 45.0;
            double drFactor = Math.max(0, (normalDR - avgDynamicRange) / normalDR);
            double estimatedAvgHTL = drFactor * 60.0;
            double mclElevation = Math.max(0, avgMCL - 65.0);
            estimatedAvgHTL += mclElevation * 0.5;
            estimatedAvgHTL = Math.min(80.0, estimatedAvgHTL);
            
            // Configure WDRC compression parameters based on estimated hearing loss
            WDRCSettings settings = new WDRCSettings();
            settings.setFrequencyGains(frequencyGains);
            
            if (estimatedAvgHTL > 60) {
                settings.setCompressionRatio(4.0f);
                settings.setKneepoint(-25.0f);
                settings.setSpeechOptimized(true);
            } else if (estimatedAvgHTL > 40) {
                settings.setCompressionRatio(3.0f);
                settings.setKneepoint(-30.0f);
                settings.setSpeechOptimized(true);
            } else {
                settings.setCompressionRatio(2.0f);
                settings.setKneepoint(-35.0f);
                settings.setSpeechOptimized(false);
            }
            
            Log.i(TAG, String.format("WDRC from calibration: %s ear, avgMCL=%.1fdB, avgDR=%.1fdB, est.HTL=%.1fdB HL, CR=%.1f:1, %d frequencies", 
                                      ear, avgMCL, avgDynamicRange, estimatedAvgHTL, settings.getCompressionRatio(), validFrequencies));
            
            return settings;
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing calibration JSON: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error generating WDRC from calibration: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate personalized WDRC settings based on audiometry results OR calibration data
     * Implements NAL-NL2 inspired gain calculations with input-level dependency
     * 
     * Priority: 1) Calibration data (if available), 2) Audiometry results, 3) Default settings
     */
    public WDRCSettings generateWDRCSettings(int userId, String ear, int hearingProfileId) {
        try {
            // PRIORITY 1: Try calibration data first (MCL/UCL based)
            WDRCSettings calibrationSettings = generateWDRCSettingsFromCalibration(userId, ear, hearingProfileId);
            if (calibrationSettings != null) {
                Log.i(TAG, "Using calibration-based WDRC settings for " + ear + " ear");
                return calibrationSettings;
            }
            
            // PRIORITY 2: Fall back to audiometry results
            List<AudiometryResult> results = audiometryDao.getResultsForEar(userId, ear, hearingProfileId);
            
            if (results.isEmpty()) {
                Log.w(TAG, "No audiometry results found for WDRC settings - using defaults");
                return new WDRCSettings(); // Default settings
            }
            
            // Calculate frequency-specific gains using NAL-NL2 approach with input-level dependency
            Map<Integer, Float> frequencyGains = new HashMap<>();
            float avgThreshold = 0f;
            int validResults = 0;
            
            // NAL-NL2 Enhancement: Calculate gains for multiple input levels
            // This creates adaptive compression curves that vary with input SPL
            for (AudiometryResult result : results) {
                if (result.isReliable()) {
                    int frequency = result.getFrequency();
                    float thresholdDbHL = result.getThresholdDbHL();
                    
                    // NAL-NL2 formula: Gain = f(HTL, InputSPL, frequency)
                    // Base gain from hearing threshold
                    double baseGain = 0.31 * (thresholdDbHL - 20.0);
                    
                    // Apply speech importance weighting
                    double speechWeight = SPEECH_IMPORTANCE.getOrDefault(frequency, 0.3);
                    
                    // Calculate input-level dependent gain for medium speech (65 dB SPL)
                    // This serves as the reference gain stored in WDRCSettings
                    double inputLevelFactor = calculateInputLevelFactor(INPUT_SPL_MEDIUM, thresholdDbHL);
                    double personalizedGain = baseGain * speechWeight * inputLevelFactor;
                    
                    frequencyGains.put(frequency, (float) personalizedGain);
                    avgThreshold += thresholdDbHL;
                    validResults++;
                    
                    Log.d(TAG, String.format("WDRC gain: %dHz = %.1fdB @ 65dB SPL (HTL=%.1fdB HL, factor=%.2f)", 
                                              frequency, personalizedGain, thresholdDbHL, inputLevelFactor));
                }
            }
            
            if (validResults == 0) {
                return new WDRCSettings(); // No reliable results
            }
            
            avgThreshold /= validResults;
            
            // Configure WDRC compression parameters based on hearing loss severity
            // NAL-NL2 Enhancement: Compression ratio creates input-level dependent gain curves
            WDRCSettings settings = new WDRCSettings();
            settings.setFrequencyGains(frequencyGains);
            
            // Adjust compression parameters based on average threshold
            // Higher compression ratios = more gain reduction for loud sounds (mimics input-level dependency)
            if (avgThreshold > 60) {
                // Severe hearing loss - more aggressive compression
                // High CR maintains audibility across wide dynamic range
                settings.setCompressionRatio(4.0f);  // 4:1 compression
                settings.setKneepoint(-25.0f);        // Early compression onset
                settings.setSpeechOptimized(true);
            } else if (avgThreshold > 40) {
                // Moderate hearing loss - standard compression
                // Balanced CR for speech clarity and comfort
                settings.setCompressionRatio(3.0f);  // 3:1 compression
                settings.setKneepoint(-30.0f);        // Standard onset
                settings.setSpeechOptimized(true);
            } else {
                // Mild hearing loss - light compression
                // Lower CR preserves natural dynamics
                settings.setCompressionRatio(2.0f);  // 2:1 compression
                settings.setKneepoint(-35.0f);        // Late onset
                settings.setSpeechOptimized(false);
            }
            
            Log.i(TAG, String.format("WDRC settings: %s ear, avgHTL=%.1fdB HL, CR=%.1f:1, %d frequencies", 
                                      ear, avgThreshold, settings.getCompressionRatio(), validResults));
            
            return settings;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating WDRC settings: " + e.getMessage());
            return new WDRCSettings(); // Fallback to defaults
        }
    }
    
    /**
     * Generate personalized presence filter settings for high-frequency clarity
     */
    public PresenceSettings generatePresenceSettings(int userId, String ear, int hearingProfileId) {
        try {
            // Focus on high-frequency audiometry results (2kHz+)
            List<AudiometryResult> results = audiometryDao.getResultsForEar(userId, ear, hearingProfileId);
            
            float highFreqLoss = 0f;
            int highFreqCount = 0;
            
            for (AudiometryResult result : results) {
                if (result.isReliable() && result.getFrequency() >= 2000) {
                    highFreqLoss += result.getThresholdDbHL();
                    highFreqCount++;
                }
            }
            
            PresenceSettings settings = new PresenceSettings();
            
            if (highFreqCount > 0) {
                highFreqLoss /= highFreqCount;
                
                // Calculate presence boost based on high-frequency hearing loss
                float presenceBoost = Math.min(highFreqLoss * 0.3f, 15.0f); // Cap at 15dB
                settings.setPresenceBoost(presenceBoost);
                
                // Enable speech band boosting for significant high-frequency loss
                settings.setSpeechBandBoostEnabled(highFreqLoss > 25.0f);
                settings.setHighFreqCompensation(highFreqLoss * 0.2f);
                
                Log.i(TAG, "Presence settings: " + ear + " ear, HF loss=" + highFreqLoss + "dB HL, boost=" + presenceBoost + "dB");
            }
            
            return settings;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating presence settings: " + e.getMessage());
            return new PresenceSettings();
        }
    }
    
    /**
     * Generate personalized noise reduction settings based on hearing profile
     */
    public NoiseSettings generateNoiseSettings(int userId, String ear, int hearingProfileId) {
        try {
            // Calculate overall hearing loss severity for noise reduction tuning
            List<AudiometryResult> results = audiometryDao.getResultsForEar(userId, ear, hearingProfileId);
            
            float avgThreshold = 0f;
            int validResults = 0;
            
            for (AudiometryResult result : results) {
                if (result.isReliable()) {
                    avgThreshold += result.getThresholdDbHL();
                    validResults++;
                }
            }
            
            NoiseSettings settings = new NoiseSettings();
            
            if (validResults > 0) {
                avgThreshold /= validResults;
                
                // More aggressive noise reduction for greater hearing loss
                // But preserve speech frequencies more carefully
                if (avgThreshold > 50) {
                    settings.setStrength(0.8f); // Strong noise reduction
                    settings.setSpeechPreservation(0.9f); // High speech preservation
                } else if (avgThreshold > 30) {
                    settings.setStrength(0.6f); // Moderate noise reduction
                    settings.setSpeechPreservation(0.7f); // Balanced
                } else {
                    settings.setStrength(0.4f); // Light noise reduction
                    settings.setSpeechPreservation(0.5f); // Standard
                }
                
                Log.i(TAG, "Noise settings: " + ear + " ear, avg threshold=" + avgThreshold + "dB HL, strength=" + settings.getStrength());
            }
            
            return settings;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating noise settings: " + e.getMessage());
            return new NoiseSettings();
        }
    }
    
    /**
     * Generate personalized limiter settings with UCL-based MPO
     * Critical for hearing safety - combines calibration UCL with device limits
     */
    public LimiterSettings generateLimiterSettings(int userId, String ear, int hearingProfileId, String deviceType) {
        try {
            // Get calibration profile for UCL data
            CalibrationProfileEntity calibration = calibrationDao.getLatestProfileForEar(userId, ear, hearingProfileId);
            
            LimiterSettings settings = new LimiterSettings();
            
            if (calibration != null) {
                // Use measured UCL as personalized maximum
                float personalizedMPO = calibration.getUclDbSpl();
                
                // Apply device-specific safety margins
                int safetyMargin = deviceType.toLowerCase().contains("headphone") ? 
                                  HEADPHONE_SAFETY_MARGIN : SPEAKER_SAFETY_MARGIN;
                
                float safeMPO = personalizedMPO - safetyMargin;
                settings.setPersonalizedMPO(safeMPO);
                settings.setUclBasedLimit(personalizedMPO);
                
                // Configure attack/release for hearing protection
                settings.setAttackTime(1.0f); // Fast attack for protection
                settings.setReleaseTime(50.0f); // Moderate release
                
                Log.i(TAG, "Limiter settings: " + ear + " ear, UCL=" + personalizedMPO + "dB SPL, safe MPO=" + safeMPO + "dB SPL");
                
            } else {
                // Fallback to conservative defaults without UCL data
                settings.setPersonalizedMPO(85.0f); // Conservative default
                settings.setUclBasedLimit(95.0f);   // Safety fallback
                Log.w(TAG, "No calibration data found - using conservative limiter defaults");
            }
            
            return settings;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating limiter settings: " + e.getMessage());
            // Return very conservative settings on error
            LimiterSettings fallback = new LimiterSettings();
            fallback.setPersonalizedMPO(80.0f);
            fallback.setUclBasedLimit(90.0f);
            return fallback;
        }
    }
    
    /**
     * Generate complete personalized settings package for both ears
     * Used by AudioStreamingService to configure all DSP processors
     */
    public PersonalizedSettingsPackage generateCompleteSettings(int userId, int hearingProfileId, String deviceType) {
        PersonalizedSettingsPackage packageData = new PersonalizedSettingsPackage();
        
        // Generate settings for both ears
        for (String ear : new String[]{"LEFT", "RIGHT"}) {
            WDRCSettings wdrc = generateWDRCSettings(userId, ear, hearingProfileId);
            PresenceSettings presence = generatePresenceSettings(userId, ear, hearingProfileId);
            NoiseSettings noise = generateNoiseSettings(userId, ear, hearingProfileId);
            LimiterSettings limiter = generateLimiterSettings(userId, ear, hearingProfileId, deviceType);
            
            packageData.setEarSettings(ear, wdrc, presence, noise, limiter);
        }
        
        Log.i(TAG, "Complete personalized settings generated for user " + userId + ", profile " + hearingProfileId);
        return packageData;
    }
    
    /**
     * Data container for complete personalized settings
     */
    public static class PersonalizedSettingsPackage {
        private final Map<String, WDRCSettings> wdrcSettings = new HashMap<>();
        private final Map<String, PresenceSettings> presenceSettings = new HashMap<>();
        private final Map<String, NoiseSettings> noiseSettings = new HashMap<>();
        private final Map<String, LimiterSettings> limiterSettings = new HashMap<>();
        
        public void setEarSettings(String ear, WDRCSettings wdrc, PresenceSettings presence, 
                                  NoiseSettings noise, LimiterSettings limiter) {
            wdrcSettings.put(ear, wdrc);
            presenceSettings.put(ear, presence);
            noiseSettings.put(ear, noise);
            limiterSettings.put(ear, limiter);
        }
        
        public WDRCSettings getWDRCSettings(String ear) { return wdrcSettings.get(ear); }
        public PresenceSettings getPresenceSettings(String ear) { return presenceSettings.get(ear); }
        public NoiseSettings getNoiseSettings(String ear) { return noiseSettings.get(ear); }
        public LimiterSettings getLimiterSettings(String ear) { return limiterSettings.get(ear); }
    }
    
    /**
     * Interpolate gain for a frequency not in the calibration data
     * Uses linear interpolation between nearest available frequencies
     */
    private float interpolateGain(int targetFreq, Map<Integer, Float> availableGains) {
        if (availableGains.isEmpty()) {
            return 0.0f; // No gain if no data
        }
        
        // Find closest lower and higher frequencies
        int lowerFreq = -1;
        int higherFreq = -1;
        float lowerGain = 0.0f;
        float higherGain = 0.0f;
        
        for (Map.Entry<Integer, Float> entry : availableGains.entrySet()) {
            int freq = entry.getKey();
            float gain = entry.getValue();
            
            if (freq <= targetFreq && (lowerFreq == -1 || freq > lowerFreq)) {
                lowerFreq = freq;
                lowerGain = gain;
            }
            if (freq >= targetFreq && (higherFreq == -1 || freq < higherFreq)) {
                higherFreq = freq;
                higherGain = gain;
            }
        }
        
        // Extrapolate if outside range
        if (lowerFreq == -1) {
            return higherGain; // Below lowest frequency - use lowest gain
        }
        if (higherFreq == -1) {
            return lowerGain; // Above highest frequency - use highest gain
        }
        
        // Linear interpolation
        if (lowerFreq == higherFreq) {
            return lowerGain; // Exact match
        }
        
        float ratio = (float)(targetFreq - lowerFreq) / (float)(higherFreq - lowerFreq);
        return lowerGain + ratio * (higherGain - lowerGain);
    }
}