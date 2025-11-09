package com.audion.audio;

import android.util.Log;

/**
 * PHASE 4: ANSI S3.6-2018 pure tone validation.
 * 
 * Validates that generated pure tones meet ANSI standards:
 * - Frequency accuracy: < 1% deviation
 * - Amplitude accuracy: ± 1 dB
 * 
 * Uses Goertzel algorithm (single-frequency DFT) for efficient validation.
 */
public class ToneValidator {
    private static final String TAG = "ToneValidator";
    
    // ANSI S3.6-2018 tolerances
    private static final double FREQ_TOLERANCE_PERCENT = 1.0;  // ±1%
    private static final double AMP_TOLERANCE_DB = 1.0;         // ±1 dB
    
    /**
     * Validate a pure tone signal.
     * 
     * @param audioData Audio samples (normalized float: -1.0 to 1.0)
     * @param sampleRate Sample rate in Hz
     * @param expectedFrequency Expected tone frequency in Hz
     * @param expectedAmplitudeDbFS Expected amplitude in dBFS (e.g., -20 dBFS)
     * @return Validation result
     */
    public static ValidationResult validate(float[] audioData, int sampleRate,
                                           double expectedFrequency, double expectedAmplitudeDbFS) {
        if (audioData == null || audioData.length == 0) {
            return new ValidationResult(false, 0, 0, "Empty audio data", 0, 0);
        }
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
        Log.i(TAG, String.format("[Phase 4] Validating tone: %.0f Hz @ %.1f dBFS",
            expectedFrequency, expectedAmplitudeDbFS));
        
        // Step 1: Detect actual frequency using Goertzel algorithm
        double detectedFrequency = detectFrequency(audioData, sampleRate, expectedFrequency);
        
        // Step 2: Measure amplitude at detected frequency
        double detectedAmplitudeDbFS = measureAmplitude(audioData, sampleRate, detectedFrequency);
        
        // Step 3: Calculate deviations
        double freqDeviationPercent = Math.abs((detectedFrequency - expectedFrequency) / expectedFrequency * 100.0);
        double ampDeviationDb = Math.abs(detectedAmplitudeDbFS - expectedAmplitudeDbFS);
        
        // Step 4: Check ANSI compliance
        boolean freqPass = freqDeviationPercent <= FREQ_TOLERANCE_PERCENT;
        boolean ampPass = ampDeviationDb <= AMP_TOLERANCE_DB;
        boolean overallPass = freqPass && ampPass;
        
        String message;
        if (overallPass) {
            message = "✓ ANSI S3.6-2018 COMPLIANT";
        } else {
            StringBuilder sb = new StringBuilder("✗ ANSI S3.6-2018 FAILED: ");
            if (!freqPass) {
                sb.append(String.format("Freq deviation %.2f%% (>1%%) ", freqDeviationPercent));
            }
            if (!ampPass) {
                sb.append(String.format("Amp deviation %.2f dB (>1dB)", ampDeviationDb));
            }
            message = sb.toString();
        }
        
        Log.i(TAG, String.format("Detected: %.2f Hz (%.3f%% deviation), %.1f dBFS (%.2f dB deviation)",
            detectedFrequency, freqDeviationPercent, detectedAmplitudeDbFS, ampDeviationDb));
        Log.i(TAG, message);
        Log.i(TAG, "════════════════════════════════════════════════════════");
        
        return new ValidationResult(
            overallPass,
            detectedFrequency,
            detectedAmplitudeDbFS,
            message,
            freqDeviationPercent,
            ampDeviationDb
        );
    }
    
    /**
     * Detect actual frequency using Goertzel algorithm (efficient single-bin DFT).
     * 
     * Goertzel is optimized for detecting specific frequencies without full FFT.
     */
    private static double detectFrequency(float[] audioData, int sampleRate, double targetFrequency) {
        // Search ±5% around target frequency
        double searchRange = targetFrequency * 0.05;
        int searchSteps = 50;
        double stepSize = (2 * searchRange) / searchSteps;
        
        double bestFrequency = targetFrequency;
        double maxMagnitude = 0.0;
        
        // Sweep frequencies around target
        for (int i = 0; i < searchSteps; i++) {
            double testFreq = targetFrequency - searchRange + i * stepSize;
            double magnitude = goertzelMagnitude(audioData, sampleRate, testFreq);
            
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude;
                bestFrequency = testFreq;
            }
        }
        
        return bestFrequency;
    }
    
    /**
     * Measure amplitude at specific frequency using Goertzel algorithm.
     * 
     * @return Amplitude in dBFS
     */
    private static double measureAmplitude(float[] audioData, int sampleRate, double frequency) {
        double magnitude = goertzelMagnitude(audioData, sampleRate, frequency);
        
        // Convert magnitude to dBFS (0 dBFS = full scale)
        if (magnitude < 1e-10) {
            return -96.0;  // Floor at -96 dBFS
        }
        
        return 20.0 * Math.log10(magnitude);
    }
    
    /**
     * Goertzel algorithm: Efficient single-frequency DFT.
     * 
     * Computes magnitude of specific frequency component.
     * 
     * @param audioData Audio samples
     * @param sampleRate Sample rate in Hz
     * @param targetFrequency Frequency to detect
     * @return Normalized magnitude (0.0 to 1.0)
     */
    private static double goertzelMagnitude(float[] audioData, int sampleRate, double targetFrequency) {
        int N = audioData.length;
        
        // Goertzel coefficient
        double k = (N * targetFrequency) / sampleRate;
        double omega = (2.0 * Math.PI * k) / N;
        double coeff = 2.0 * Math.cos(omega);
        
        // Goertzel filter states
        double s0 = 0.0;
        double s1 = 0.0;
        double s2 = 0.0;
        
        // Process samples
        for (int i = 0; i < N; i++) {
            s0 = audioData[i] + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        
        // Calculate magnitude
        double real = s1 - s2 * Math.cos(omega);
        double imag = s2 * Math.sin(omega);
        double magnitude = Math.sqrt(real * real + imag * imag);
        
        // Normalize by N
        return magnitude / N;
    }
    
    /**
     * Validate all standard audiometry frequencies (250-8000 Hz).
     * 
     * @param toneGenerator Function to generate tone samples: (freq, duration) -> float[]
     * @param sampleRate Sample rate
     * @param testAmplitude Test amplitude in dBFS
     * @return Array of validation results for each frequency
     */
    public static ValidationResult[] validateAllFrequencies(
            ToneGenerator toneGenerator,
            int sampleRate,
            double testAmplitude) {
        
        int[] standardFrequencies = {250, 500, 1000, 2000, 4000, 6000, 8000};
        ValidationResult[] results = new ValidationResult[standardFrequencies.length];
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
        Log.i(TAG, "[Phase 4] Validating all audiometry frequencies");
        Log.i(TAG, String.format("Test amplitude: %.1f dBFS", testAmplitude));
        Log.i(TAG, "════════════════════════════════════════════════════════");
        
        for (int i = 0; i < standardFrequencies.length; i++) {
            int frequency = standardFrequencies[i];
            
            // Generate 1-second tone
            float[] toneData = toneGenerator.generate(frequency, 1.0, sampleRate, testAmplitude);
            
            // Validate
            results[i] = validate(toneData, sampleRate, frequency, testAmplitude);
        }
        
        // Summary
        int passCount = 0;
        for (ValidationResult result : results) {
            if (result.pass) passCount++;
        }
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
        Log.i(TAG, String.format("[Phase 4] Validation complete: %d/%d frequencies passed",
            passCount, standardFrequencies.length));
        if (passCount == standardFrequencies.length) {
            Log.i(TAG, "✓ ALL FREQUENCIES ANSI S3.6-2018 COMPLIANT");
        } else {
            Log.w(TAG, "✗ SOME FREQUENCIES FAILED ANSI S3.6-2018 COMPLIANCE");
        }
        Log.i(TAG, "════════════════════════════════════════════════════════");
        
        return results;
    }
    
    /**
     * Interface for tone generation (for testing).
     */
    public interface ToneGenerator {
        /**
         * Generate a pure tone.
         * 
         * @param frequency Frequency in Hz
         * @param durationSeconds Duration in seconds
         * @param sampleRate Sample rate
         * @param amplitudeDbFS Amplitude in dBFS
         * @return Audio samples (normalized float: -1.0 to 1.0)
         */
        float[] generate(int frequency, double durationSeconds, int sampleRate, double amplitudeDbFS);
    }
    
    /**
     * Validation result.
     */
    public static class ValidationResult {
        public final boolean pass;
        public final double detectedFrequency;
        public final double detectedAmplitudeDbFS;
        public final String message;
        public final double frequencyDeviationPercent;
        public final double amplitudeDeviationDb;
        
        ValidationResult(boolean pass, double detectedFrequency, double detectedAmplitudeDbFS,
                        String message, double freqDev, double ampDev) {
            this.pass = pass;
            this.detectedFrequency = detectedFrequency;
            this.detectedAmplitudeDbFS = detectedAmplitudeDbFS;
            this.message = message;
            this.frequencyDeviationPercent = freqDev;
            this.amplitudeDeviationDb = ampDev;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %.0f Hz (%.2f%% dev), %.1f dBFS (%.2f dB dev)",
                pass ? "PASS" : "FAIL",
                detectedFrequency,
                frequencyDeviationPercent,
                detectedAmplitudeDbFS,
                amplitudeDeviationDb);
        }
    }
}
