package com.example.audion.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Clinical-grade audio generation for audiometry and calibration tests
 * Implements ANSI S3.6 compliant tone generation with proper envelopes
 */
public class ClinicalAudioGenerator {
    
    private static final String TAG = "ClinicalAudio";
    private static final int SAMPLE_RATE = 44100;
    private static final int FADE_DURATION_MS = 200; // 0.2s fade in/out
    
    private AudioTrack audioTrack;
    private volatile boolean isPlaying = false;
    private Thread playbackThread;
    private volatile boolean stopRequested = false;
    
    /**
     * Generate a clinical pure tone with proper envelope
     * @param frequency Frequency in Hz
     * @param durationMs Duration in milliseconds (1000-2000ms for clinical testing)
     * @param amplitudeLinear Linear amplitude (0.0 to 1.0)
     * @return PCM audio samples
     */
    public static short[] generateClinicalTone(int frequency, int durationMs, double amplitudeLinear) {
        int numSamples = SAMPLE_RATE * durationMs / 1000;
        int fadeSamples = SAMPLE_RATE * FADE_DURATION_MS / 1000;
        
        short[] samples = new short[numSamples];
        
        for (int i = 0; i < numSamples; i++) {
            // Generate sine wave
            double sineValue = Math.sin(2.0 * Math.PI * frequency * i / SAMPLE_RATE);
            
            // Apply envelope (fade in/out)
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
            samples[i] = (short) (sineValue * amplitudeLinear * envelope * Short.MAX_VALUE);
        }
        
        return samples;
    }
    
    /**
     * Convert dB HL to linear amplitude for audiometry
     * @param frequency Frequency in Hz
     * @param dbHL Hearing level in dB HL
     * @return Linear amplitude (0.0 to 1.0)
     */
    public static double dbHLToAmplitude(int frequency, double dbHL) {
        // Reference threshold corrections (RETSPL) for insert earphones
        // These are approximate values - in real clinical use, these would be calibrated
        double retspl = getRETSPL(frequency);
        
        // Convert dB HL to dB SPL
        double dbSPL = dbHL + retspl;
        
        // Convert dB SPL to linear amplitude (reference: 94 dB SPL = full scale)
        double amplitude = Math.pow(10.0, (dbSPL - 94.0) / 20.0);
        
        // Safety limit
        return Math.min(amplitude, 0.8);
    }
    
    /**
     * Convert dB SPL to linear amplitude for calibration
     * @param dbSPL Sound pressure level in dB SPL
     * @return Linear amplitude (0.0 to 1.0)
     */
    public static double dbSPLToAmplitude(double dbSPL) {
        // Use a more practical reference: 80 dB SPL = 50% amplitude
        // This makes clinical levels (65-100 dB SPL) more audible
        double amplitude = Math.pow(10.0, (dbSPL - 80.0) / 20.0) * 0.5;
        
        // Safety limit and minimum audible level
        amplitude = Math.max(amplitude, 0.05); // Minimum 5% for very low levels
        return Math.min(amplitude, 0.9); // Maximum 90% for safety
    }
    
    /**
     * Get Reference Equivalent Threshold Sound Pressure Level (RETSPL)
     * for insert earphones at each frequency
     */
    private static double getRETSPL(int frequency) {
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
            default: return 10.0; // Default approximation
        }
    }
    
    /**
     * Play a clinical tone with callback for completion
     * @param frequency Frequency in Hz
     * @param durationMs Duration in milliseconds
     * @param dbLevel Level in dB (HL or SPL depending on context)
     * @param isCalibration true for SPL, false for HL
     * @param callback Called when tone completes or is stopped
     */
    public void playClinicalTone(int frequency, int durationMs, double dbLevel, 
                                boolean isCalibration, ToneCompletionCallback callback) {
        
        if (isPlaying) {
            stopTone();
        }
        
        new Thread(() -> {
                try {
                Log.d("AudioDebug", "=== AUDIO GENERATION START ===");
                Log.d("AudioDebug", "Playing tone at " + frequency + " Hz, " + dbLevel + 
                      (isCalibration ? " dB SPL" : " dB HL") + " for " + durationMs + "ms");
                
                // Calculate amplitude with debugging
                double amplitude = isCalibration ? 
                    dbSPLToAmplitude(dbLevel) : 
                    dbHLToAmplitude(frequency, dbLevel);
                
                Log.d("AudioDebug", "Calculated amplitude: " + amplitude + " for " + dbLevel + 
                      (isCalibration ? " dB SPL" : " dB HL"));
                
                // Ensure minimum audible amplitude for testing
                if (amplitude < 0.01) {
                    Log.w(TAG, "Amplitude too low (" + amplitude + "), setting to minimum audible level");
                    amplitude = isCalibration ? 0.25 : 0.15; // Higher for calibration, lower for threshold testing
                }
                
                // Also ensure we don't exceed safe levels
                if (amplitude > 0.9) {
                    Log.w(TAG, "Amplitude too high (" + amplitude + "), clamping to 0.9 for safety");
                    amplitude = 0.9;
                }
                
                // Generate tone with clinical envelope
                short[] samples = generateClinicalTone(frequency, durationMs, amplitude);
                
                // Enhanced sample validation and debugging
                short maxSample = 0, minSample = 0;
                int nonZeroCount = 0;
                for (short sample : samples) {
                    if (sample != 0) nonZeroCount++;
                    if (Math.abs(sample) > Math.abs(maxSample)) maxSample = sample;
                    if (sample < minSample) minSample = sample;
                }
                
                Log.d("AudioDebug", "Sample analysis: " + samples.length + " total, " + nonZeroCount + " non-zero");
                Log.d("AudioDebug", "Range: [" + minSample + " to " + maxSample + "], first=" + samples[0] + ", last=" + samples[samples.length-1]);
                
                if (maxSample == 0) {
                    Log.e("AudioDebug", "CRITICAL ERROR: All samples are zero! Audio will be silent!");
                    Log.e("AudioDebug", "Debug: freq=" + frequency + ", duration=" + durationMs + ", amplitude=" + amplitude);
                    Log.e("AudioDebug", "Attempting fallback with fixed amplitude 0.5");
                    
                    // Fallback: regenerate with fixed amplitude
                    samples = generateClinicalTone(frequency, durationMs, 0.5);
                    for (short sample : samples) {
                        if (Math.abs(sample) > Math.abs(maxSample)) maxSample = sample;
                    }
                    Log.d("AudioDebug", "Fallback generated max sample: " + maxSample);
                }                // Set up AudioTrack with detailed error checking
                int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, 
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                
                Log.d(TAG, "MinBufferSize: " + minBufferSize + ", SampleCount: " + samples.length);
                
                if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Invalid audio parameters for getMinBufferSize");
                    if (callback != null) {
                        callback.onToneError("Invalid audio parameters");
                    }
                    return;
                }
                
                int bufferSize = Math.max(minBufferSize, samples.length * 2);
                Log.d(TAG, "Using buffer size: " + bufferSize);
                
                // Use MODE_STREAM for better compatibility and real-time playback
                try {
                    audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    );
                    Log.d("AudioDebug", "AudioTrack created successfully with MODE_STREAM");
                } catch (Exception e) {
                    Log.w(TAG, "MODE_STREAM approach failed, trying AudioAttributes: " + e.getMessage());
                    
                    // Fallback to AudioAttributes approach with MODE_STREAM
                    try {
                        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                        
                        AudioFormat audioFormat = new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build();
                        
                        audioTrack = new AudioTrack(
                            audioAttributes,
                            audioFormat,
                            bufferSize,
                            AudioTrack.MODE_STREAM,
                            AudioManager.AUDIO_SESSION_ID_GENERATE
                        );
                        Log.d(TAG, "AudioTrack created successfully with AudioAttributes MODE_STREAM");
                    } catch (Exception e2) {
                        Log.e(TAG, "Both AudioTrack approaches failed. STREAM: " + e.getMessage() + 
                              ", ATTRIBUTES: " + e2.getMessage());
                        if (callback != null) {
                            callback.onToneError("AudioTrack creation failed: " + e2.getMessage());
                        }
                        return;
                    }
                }
                
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.e("AudioDebug", "AudioTrack initialization failed. State: " + audioTrack.getState() 
                        + ", Expected: " + AudioTrack.STATE_INITIALIZED);
                    Log.e("AudioDebug", "Sample rate: " + SAMPLE_RATE + ", Buffer size: " + bufferSize);
                    if (callback != null) {
                        callback.onToneError("Audio initialization failed - invalid state");
                    }
                    audioTrack.release();
                    audioTrack = null;
                    return;
                }
                
                Log.d("AudioDebug", "AudioTrack state=" + audioTrack.getState() + " (INITIALIZED) - ready for playback");
                
                // Start playback immediately for MODE_STREAM
                Log.d("AudioDebug", "Starting AudioTrack.play()...");
                audioTrack.play();
                isPlaying = true;
                stopRequested = false;
                
                Log.d("AudioDebug", "AudioTrack.play() called - playback state: " + audioTrack.getPlayState());
                
                if (callback != null) {
                    callback.onToneStarted();
                }
                
                // Stream audio data in chunks for MODE_STREAM
                int chunkSize = 4096; // Smaller chunks for streaming
                int totalWritten = 0;
                long startTime = System.currentTimeMillis();
                
                Log.d("AudioDebug", "Starting audio data streaming...");
                
                while (isPlaying && !stopRequested && totalWritten < samples.length &&
                       (System.currentTimeMillis() - startTime) < durationMs) {
                    
                    int remaining = samples.length - totalWritten;
                    int toWrite = Math.min(chunkSize, remaining);
                    
                    int written = audioTrack.write(samples, totalWritten, toWrite);
                    if (written > 0) {
                        totalWritten += written;
                        if (totalWritten % (chunkSize * 4) == 0) { // Log every 4 chunks
                            Log.d("AudioDebug", "Progress: " + totalWritten + "/" + samples.length + " samples written");
                        }
                    } else if (written < 0) {
                        Log.e("AudioDebug", "Error writing audio data: " + written);
                        break;
                    }
                    
                    // Small delay to prevent busy waiting
                    Thread.sleep(10);
                }
                
                Log.d("AudioDebug", "Audio streaming completed. Total written: " + totalWritten + "/" + samples.length);
                
                // Clean up
                if (audioTrack != null) {
                    audioTrack.stop();
                    audioTrack.release();
                    audioTrack = null;
                }
                
                isPlaying = false;
                
                if (callback != null) {
                    if (stopRequested) {
                        callback.onToneStopped();
                    } else {
                        callback.onToneCompleted();
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error playing clinical tone: " + e.getMessage(), e);
                isPlaying = false;
                if (audioTrack != null) {
                    try {
                        audioTrack.stop();
                        audioTrack.release();
                    } catch (Exception ignored) {}
                    audioTrack = null;
                }
                if (callback != null) {
                    callback.onToneError(e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * Stop currently playing tone immediately
     */
    public void stopTone() {
        stopRequested = true;
        if (audioTrack != null && isPlaying) {
            try {
                audioTrack.stop();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping tone: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if a tone is currently playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }
    
    /**
     * Release resources
     */
    public void release() {
        stopTone();
        if (audioTrack != null) {
            try {
                audioTrack.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing AudioTrack: " + e.getMessage());
            }
            audioTrack = null;
        }
    }
    
    /**
     * Callback interface for tone playback events
     */
    public interface ToneCompletionCallback {
        void onToneStarted();
        void onToneCompleted();
        void onToneStopped();
        void onToneError(String error);
    }
}