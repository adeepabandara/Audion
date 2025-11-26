package com.audion.audio;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * Real-time Frequency-Specific Gain Mapper for audiogram-based personalization.
 * 
 * Applies frequency-specific gains based on hearing test results (audiogram).
 * Uses 6-band filterbank covering standard audiometric frequencies:
 * 250 Hz, 500 Hz, 1000 Hz, 2000 Hz, 4000 Hz, 8000 Hz
 * 
 * Architecture:
 * - 6 bandpass filters covering 250-8000 Hz
 * - Per-band gains derived from audiogram thresholds (dB HL)
 * - NAL-NL2 inspired gain prescription
 * - Real-time processing with minimal latency
 * 
 * Usage:
 * 1. Create instance: new FrequencyGainMapper(sampleRate)
 * 2. Load audiogram: setAudiogramGains(frequencyMap)
 * 3. Process audio: process(input, output, length)
 */
public class FrequencyGainMapper {
    private static final String TAG = "FrequencyGainMapper";
    
    // Standard audiometric frequencies (Hz)
    private static final int[] AUDIOMETRIC_FREQUENCIES = {
        250, 500, 1000, 2000, 4000, 8000
    };
    
    // Number of frequency bands
    private static final int NUM_BANDS = 6;
    
    // Band frequency ranges (Hz) - designed to separate audiometric frequencies
    private static final float[][] BAND_RANGES = {
        {200f, 375f},     // Band 0: 250 Hz center
        {375f, 750f},     // Band 1: 500 Hz center
        {750f, 1500f},    // Band 2: 1000 Hz center
        {1500f, 3000f},   // Band 3: 2000 Hz center
        {3000f, 6000f},   // Band 4: 4000 Hz center
        {6000f, 10000f}   // Band 5: 8000 Hz center
    };
    
    // Bandpass filters (one per frequency band)
    private final BandPassFilter[] filters;
    
    // Per-band linear gains (updated from audiogram)
    private final float[] bandGains;
    
    // Pre-allocated buffers for band processing
    private final float[][] bandBuffers;
    
    // Sample rate
    private final int sampleRate;
    
    // Enable/disable flag
    private boolean enabled = false;
    
    /**
     * Create frequency gain mapper with 6-band filterbank.
     * 
     * @param sampleRate Sample rate in Hz (typically 48000)
     */
    public FrequencyGainMapper(int sampleRate) {
        this.sampleRate = sampleRate;
        
        // Initialize 6 bandpass filters
        filters = new BandPassFilter[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            filters[i] = new BandPassFilter(BAND_RANGES[i][0], BAND_RANGES[i][1], sampleRate);
            Log.d(TAG, String.format("Filter[%d]: %.0f-%.0f Hz (center ~%d Hz)", 
                i, BAND_RANGES[i][0], BAND_RANGES[i][1], AUDIOMETRIC_FREQUENCIES[i]));
        }
        
        // Initialize gain array (unity gain by default)
        bandGains = new float[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            bandGains[i] = 1.0f;
        }
        
        // Pre-allocate band buffers (avoid allocations in processing loop)
        bandBuffers = new float[NUM_BANDS][];
        for (int i = 0; i < NUM_BANDS; i++) {
            bandBuffers[i] = new float[AudioConfig.FRAME_SIZE_SAMPLES];
        }
        
        Log.i(TAG, String.format("FrequencyGainMapper initialized: %d bands, %d Hz", NUM_BANDS, sampleRate));
    }
    
    /**
     * Set frequency-specific gains from audiogram data.
     * 
     * Converts hearing thresholds (dB HL) to frequency-specific gains using
     * simplified NAL-NL2 inspired formula.
     * 
     * @param audiogramThresholds Map of frequency (Hz) to threshold (dB HL)
     *                            Example: {250: 25, 500: 30, 1000: 35, ...}
     */
    public void setAudiogramGains(Map<Integer, Integer> audiogramThresholds) {
        if (audiogramThresholds == null || audiogramThresholds.isEmpty()) {
            Log.w(TAG, "No audiogram data provided, using unity gains");
            enabled = false;
            return;
        }
        
        // Convert dB HL thresholds to linear gains using NAL-NL2 inspired formula
        for (int i = 0; i < NUM_BANDS; i++) {
            int frequency = AUDIOMETRIC_FREQUENCIES[i];
            Integer thresholdDbHL = audiogramThresholds.get(frequency);
            
            if (thresholdDbHL != null) {
                // NAL-NL2 simplified: Gain (dB) = 0.31 × HTL + k
                // Where k varies by frequency (speech importance weighting)
                double htl = thresholdDbHL.doubleValue();
                double frequencyWeight = getFrequencyWeight(frequency);
                
                // Calculate insertion gain in dB
                double gainDb = 0.31 * htl * frequencyWeight;
                
                // Clamp to reasonable range (0-40 dB)
                gainDb = Math.max(0.0, Math.min(40.0, gainDb));
                
                // Convert dB to linear gain
                bandGains[i] = (float) Math.pow(10.0, gainDb / 20.0);
                
                Log.i(TAG, String.format("Band %d (%d Hz): HTL=%.0f dBHL → Gain=%.1f dB (linear=%.2f)", 
                    i, frequency, htl, gainDb, bandGains[i]));
            } else {
                // No data for this frequency, use unity gain
                bandGains[i] = 1.0f;
                Log.w(TAG, String.format("Band %d (%d Hz): No audiogram data, using unity gain", 
                    i, frequency));
            }
        }
        
        enabled = true;
        Log.i(TAG, "Audiogram gains configured, frequency-specific processing enabled");
    }
    
    /**
     * Get frequency importance weight for NAL-NL2 calculation.
     * Higher weights for speech-critical frequencies (1-4 kHz).
     * 
     * @param frequency Frequency in Hz
     * @return Weight factor (0.8 to 1.2)
     */
    private double getFrequencyWeight(int frequency) {
        if (frequency <= 500) {
            return 0.9;   // Low frequencies: Less critical
        } else if (frequency <= 2000) {
            return 1.2;   // Mid frequencies: Most critical for speech
        } else if (frequency <= 4000) {
            return 1.1;   // High frequencies: Important for clarity
        } else {
            return 0.8;   // Very high frequencies: Less critical
        }
    }
    
    /**
     * Process audio buffer with frequency-specific gains.
     * 
     * @param input Input buffer (normalized float, -1.0 to +1.0)
     * @param output Output buffer (frequency-shaped audio)
     * @param length Number of samples to process
     */
    public void process(float[] input, float[] output, int length) {
        if (!enabled) {
            // Passthrough if not configured
            System.arraycopy(input, 0, output, 0, length);
            return;
        }
        
        // Zero output buffer
        for (int i = 0; i < length; i++) {
            output[i] = 0.0f;
        }
        
        // Split input into frequency bands, apply gains, and sum
        for (int band = 0; band < NUM_BANDS; band++) {
            // Filter input into this band
            filters[band].process(input, bandBuffers[band], length);
            
            // Apply per-band gain and accumulate to output
            float gain = bandGains[band];
            for (int i = 0; i < length; i++) {
                output[i] += bandBuffers[band][i] * gain;
            }
        }
    }
    
    /**
     * Process audio buffer in-place (input and output are same buffer).
     * 
     * @param buffer Input/output buffer
     * @param length Number of samples to process
     */
    public void processInPlace(float[] buffer, int length) {
        if (!enabled) {
            return; // No processing needed
        }
        
        // Need temporary buffer for in-place processing
        float[] temp = new float[length];
        process(buffer, temp, length);
        System.arraycopy(temp, 0, buffer, 0, length);
    }
    
    /**
     * Set enabled state.
     * 
     * @param enabled true to enable frequency-specific processing, false to bypass
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        Log.i(TAG, "Frequency-specific gain processing " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if frequency-specific processing is enabled.
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get current gain for a specific frequency band.
     * 
     * @param bandIndex Band index (0-5)
     * @return Linear gain for this band
     */
    public float getBandGain(int bandIndex) {
        if (bandIndex >= 0 && bandIndex < NUM_BANDS) {
            return bandGains[bandIndex];
        }
        return 1.0f;
    }
    
    /**
     * Get current gain in dB for a specific frequency band.
     * 
     * @param bandIndex Band index (0-5)
     * @return Gain in dB for this band
     */
    public float getBandGainDb(int bandIndex) {
        float linearGain = getBandGain(bandIndex);
        return 20.0f * (float) Math.log10(linearGain);
    }
    
    /**
     * Reset to unity gains (disable personalization).
     */
    public void reset() {
        for (int i = 0; i < NUM_BANDS; i++) {
            bandGains[i] = 1.0f;
        }
        enabled = false;
        Log.i(TAG, "Reset to unity gains, personalization disabled");
    }
}
