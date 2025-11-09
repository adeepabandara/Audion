package com.example.audion.audio;

import android.util.Log;

public class ToneGenerator {
    
    private static final String TAG = "ToneGenerator";
    private static final int FADE_DURATION_MS = 200; // ANSI-compliant 200ms rise/fall time

    /**
     * Generates a short PCM buffer of a pure sine wave at a given amplitude.
     * LEGACY METHOD - Use generateClinicalTone for ANSI compliance
     *
     * @param sampleRate   e.g., 44100 Hz
     * @param chunkMs      how long this chunk is, in milliseconds (e.g., 50)
     * @param frequency    the sine wave frequency in Hz (e.g., 1000)
     * @param amplitude    the amplitude [0.0..1.0] for this chunk
     * @return short[]     16-bit PCM buffer
     */
    public static short[] generateSineWaveChunk(int sampleRate, int chunkMs, int frequency, double amplitude) {
        int numSamples = (int) ((chunkMs / 1000.0) * sampleRate);
        short[] buffer = new short[numSamples];
        double twoPiF = 2.0 * Math.PI * frequency;

        for (int i = 0; i < numSamples; i++) {
            double angle = twoPiF * i / sampleRate;
            double sampleVal = amplitude * Math.sin(angle);
            buffer[i] = (short) (sampleVal * Short.MAX_VALUE);
        }
        return buffer;
    }
    
    /**
     * Generate ANSI S3.6 compliant clinical tone with proper envelope shaping
     * @param sampleRate Sample rate in Hz (44100 recommended)
     * @param durationMs Tone duration in milliseconds
     * @param frequency Frequency in Hz
     * @param amplitude Linear amplitude (0.0 to 1.0)
     * @return PCM audio samples with 200ms cosine-squared envelope
     */
    public static short[] generateClinicalTone(int sampleRate, int durationMs, int frequency, double amplitude) {
        int numSamples = sampleRate * durationMs / 1000;
        int fadeSamples = sampleRate * FADE_DURATION_MS / 1000;
        
        // Ensure minimum duration for proper envelope
        if (durationMs < FADE_DURATION_MS * 2) {
            Log.w(TAG, "Duration " + durationMs + "ms too short for 200ms envelope, using minimum");
            durationMs = FADE_DURATION_MS * 2;
            numSamples = sampleRate * durationMs / 1000;
        }
        
        short[] samples = new short[numSamples];
        double twoPiF = 2.0 * Math.PI * frequency;
        
        for (int i = 0; i < numSamples; i++) {
            // Generate sine wave
            double angle = twoPiF * i / sampleRate;
            double sineValue = Math.sin(angle);
            
            // Apply ANSI-compliant cosine-squared envelope
            double envelope = 1.0;
            
            if (i < fadeSamples) {
                // Fade in (cosine-squared envelope)
                double fadePosition = (double) i / fadeSamples;
                envelope = Math.sin(fadePosition * Math.PI / 2.0);
                envelope = envelope * envelope; // Square for smooth curve
            } else if (i >= numSamples - fadeSamples) {
                // Fade out (cosine-squared envelope)
                double fadePosition = (double) (numSamples - i - 1) / fadeSamples;
                envelope = Math.sin(fadePosition * Math.PI / 2.0);
                envelope = envelope * envelope; // Square for smooth curve
            }
            
            // Apply amplitude and envelope
            samples[i] = (short) (sineValue * amplitude * envelope * Short.MAX_VALUE);
        }
        
        return samples;
    }
    
    /**
     * Convert dB HL to linear amplitude using ANSI S3.6 RETSPL corrections
     * @param frequency Frequency in Hz
     * @param dbHL Hearing level in dB HL
     * @return Linear amplitude (0.0 to 1.0)
     */
    public static double dbHLToAmplitude(int frequency, double dbHL) {
        // Get RETSPL correction for this frequency
        double retspl = getRETSPL(frequency);
        
        // Convert dB HL to dB SPL: dB SPL = dB HL + RETSPL
        double dbSPL = dbHL + retspl;
        
        // Convert dB SPL to linear amplitude using 80 dB SPL reference
        // (more practical than 94 dB for clinical testing)
        double amplitude = Math.pow(10.0, (dbSPL - 80.0) / 20.0) * 0.5;
        
        // Apply safety limits
        amplitude = Math.max(amplitude, 0.001); // Minimum audible
        amplitude = Math.min(amplitude, 0.9);   // Safety maximum
        
        Log.d(TAG, String.format("dB HL conversion: %dHz %.1fdB HL -> %.1fdB SPL -> %.3f amplitude", 
               frequency, dbHL, dbSPL, amplitude));
        
        return amplitude;
    }
    
    /**
     * Convert linear amplitude to dB HL using ANSI S3.6 RETSPL corrections
     * @param frequency Frequency in Hz
     * @param amplitude Linear amplitude (0.0 to 1.0)
     * @return Hearing level in dB HL
     */
    public static double amplitudeToDbHL(int frequency, double amplitude) {
        // Convert amplitude to dB SPL (using 80 dB reference)
        double dbSPL = 20.0 * Math.log10(amplitude / 0.5) + 80.0;
        
        // Get RETSPL correction for this frequency
        double retspl = getRETSPL(frequency);
        
        // Convert dB SPL to dB HL: dB HL = dB SPL - RETSPL
        double dbHL = dbSPL - retspl;
        
        // Ensure reasonable clinical range
        dbHL = Math.max(dbHL, -10.0); // Minimum -10 dB HL
        dbHL = Math.min(dbHL, 120.0); // Maximum 120 dB HL
        
        return dbHL;
    }
    
    /**
     * Get Reference Equivalent Threshold Sound Pressure Level (RETSPL)
     * for insert earphones at each frequency per ANSI S3.6
     */
    public static double getRETSPL(int frequency) {
        switch (frequency) {
            case 125: return 26.0;
            case 250: return 14.0;
            case 500: return 8.5;
            case 750: return 7.5;
            case 1000: return 7.0;
            case 1500: return 9.0;
            case 2000: return 9.5;
            case 3000: return 11.5;
            case 4000: return 12.0;
            case 6000: return 16.0;
            case 8000: return 15.5;
            default:
                // Linear interpolation for intermediate frequencies
                Log.w(TAG, "Using interpolated RETSPL for " + frequency + "Hz");
                return interpolateRETSPL(frequency);
        }
    }
    
    /**
     * Interpolate RETSPL for frequencies not in the standard table
     */
    private static double interpolateRETSPL(int frequency) {
        // Find surrounding standard frequencies
        int[] standardFreqs = {125, 250, 500, 750, 1000, 1500, 2000, 3000, 4000, 6000, 8000};
        double[] standardRETSPL = {26.0, 14.0, 8.5, 7.5, 7.0, 9.0, 9.5, 11.5, 12.0, 16.0, 15.5};
        
        // Find bracketing frequencies
        for (int i = 0; i < standardFreqs.length - 1; i++) {
            if (frequency >= standardFreqs[i] && frequency <= standardFreqs[i + 1]) {
                // Linear interpolation
                double ratio = (double)(frequency - standardFreqs[i]) / (standardFreqs[i + 1] - standardFreqs[i]);
                return standardRETSPL[i] + ratio * (standardRETSPL[i + 1] - standardRETSPL[i]);
            }
        }
        
        // Default fallback
        return 10.0;
    }
}
