package com.audion.audio;

/**
 * Central configuration for the production-grade audio pipeline.
 * 
 * All audio parameters, buffer sizes, and performance constants are defined here
 * to ensure consistency across the entire audio processing chain.
 */
public final class AudioConfig {
    // Audio format constants
    public static final int SAMPLE_RATE = 48000; // 48kHz standard for low-latency audio
    public static final int CHANNELS = 2; // Stereo for true binaural processing
    public static final int BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;
    
    // Frame processing - 10ms frames at 48 kHz (RNNoise standard)
    public static final int FRAME_SIZE_SAMPLES = 480; // Per channel (10ms at 48kHz)
    public static final int FRAME_SIZE_SAMPLES_STEREO = FRAME_SIZE_SAMPLES * CHANNELS; // Total interleaved samples
    public static final float FRAME_DURATION_MS = 10.0f;
    
    // Android audio format constants
    // INPUT: MONO (most devices have single mic, STEREO causes distortion/noise)
    // OUTPUT: STEREO (for binaural hearing aid output - we'll duplicate mono input)
    public static final int CHANNEL_IN_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_OUT_CONFIG = android.media.AudioFormat.CHANNEL_OUT_STEREO;
    public static final int ENCODING_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT;
    
    // Ring buffer configuration - REDUCED for low latency (<50ms target)
    public static final int RING_BUFFER_FRAMES = 2; // 20ms total buffer (reduced from 80ms)
    public static final int RING_BUFFER_SIZE_SAMPLES = RING_BUFFER_FRAMES * FRAME_SIZE_SAMPLES; // Per channel
    
    // Audio I/O buffer sizes - REDUCED for low latency (multiple of frame size for efficient processing)
    public static final int CAPTURE_BUFFER_FRAMES = 2; // 20ms (reduced from 40ms)
    public static final int PLAYBACK_BUFFER_FRAMES = 2; // 20ms (reduced from 40ms)
    public static final int CAPTURE_BUFFER_SIZE = CAPTURE_BUFFER_FRAMES * FRAME_SIZE_SAMPLES_STEREO * BYTES_PER_SAMPLE;
    public static final int PLAYBACK_BUFFER_SIZE = PLAYBACK_BUFFER_FRAMES * FRAME_SIZE_SAMPLES_STEREO * BYTES_PER_SAMPLE;
    
    // Safety processing limits
    public static final float MAX_SAFE_AMPLITUDE = 0.95f; // Leave headroom for limiter
    public static final float LIMITER_CEILING_DBFS = -1.0f; // -1 dBFS ceiling
    public static final float LIMITER_LOOKAHEAD_MS = 2.5f; // Look-ahead time
    public static final int LIMITER_LOOKAHEAD_SAMPLES = (int)(LIMITER_LOOKAHEAD_MS * SAMPLE_RATE / 1000);
    
    // WDRC (Wide Dynamic Range Compression) parameters
    public static final float WDRC_ATTACK_MS = 10.0f;
    public static final float WDRC_RELEASE_MS = 100.0f;
    public static final float WDRC_KNEE_WIDTH_DB = 10.0f;
    public static final float WDRC_RATIO = 2.5f;
    public static final float WDRC_THRESHOLD_DB = -25.0f; // Compression threshold
    
    // Calibration constants (placeholder - should be device-specific)
    public static final float DBFS_TO_DB_SPL_LEFT = 85.0f; // dBFS 0 = 85 dB SPL (example)
    public static final float DBFS_TO_DB_SPL_RIGHT = 85.0f;
    
    // MPO (Maximum Power Output) safety limits in dB SPL
    public static final float MPO_LIMIT_DB_SPL = 120.0f; // Safe hearing limit
    
    // Performance monitoring
    public static final int PERFORMANCE_STATS_WINDOW = 100; // Frames for moving average
    public static final float MAX_CPU_BUDGET_US = 8000.0f; // 8ms budget for 10ms frame
    public static final int HEALTH_CHECK_INTERVAL_FRAMES = 50; // Check every 500ms
    
    // Degrade thresholds
    public static final float CPU_DEGRADE_THRESHOLD_US = 7000.0f; // 7ms threshold
    public static final int QUEUE_DEPTH_DEGRADE_THRESHOLD = 6; // Frames in queue before degrade
    public static final int RECOVERY_STABLE_FRAMES = 100; // Frames to be stable before recovery
    
    // QA and instrumentation
    public static final boolean QA_MODE_ENABLED = false; // Set true for QA builds
    public static final String QA_LOG_PATH = "/Android/data/com.audion/files/";
    public static final int TAP_TEST_CORRELATION_LENGTH = 1024; // Samples for tap test
    
    // Phase 2 - Quality Enhancement Parameters
    
    // RNNoise+ Controller
    public static final float RNNOISE_SNR_THRESHOLD_MILD = 10.0f; // dB SNR for mild suppression
    public static final float RNNOISE_SNR_THRESHOLD_MEDIUM = 5.0f; // dB SNR for medium suppression  
    public static final float RNNOISE_SNR_THRESHOLD_STRONG = 0.0f; // dB SNR for strong suppression
    public static final float RNNOISE_STRENGTH_MILD = 0.3f; // Mild suppression strength
    public static final float RNNOISE_STRENGTH_MEDIUM = 0.6f; // Medium suppression strength
    public static final float RNNOISE_STRENGTH_STRONG = 0.85f; // Strong suppression strength
    public static final float RNNOISE_WATCHDOG_TIMEOUT_MS = 3.0f; // Per-frame processing timeout
    public static final int RNNOISE_VAD_LOOKBACK_FRAMES = 5; // Frames to consider for VAD stability
    
    // Scene Classifier Lite
    public static final int SCENE_CLASSIFIER_WINDOW_MS = 300; // Analysis window for scene classification
    public static final int SCENE_CLASSIFIER_WINDOW_FRAMES = SCENE_CLASSIFIER_WINDOW_MS * SAMPLE_RATE / 1000 / FRAME_SIZE_SAMPLES;
    public static final int SCENE_FFT_SIZE = 256; // FFT size for spectral analysis
    public static final float SCENE_MODULATION_FREQ_MIN = 4.0f; // Hz - minimum modulation frequency
    public static final float SCENE_MODULATION_FREQ_MAX = 16.0f; // Hz - maximum modulation frequency
    public static final float SCENE_SNR_SHORT_TERM_MS = 200.0f; // Short-term SNR window
    public static final float SCENE_SNR_LONG_TERM_MS = 2000.0f; // Long-term SNR window
    
    // Adaptive Policy Defaults - Balanced Profile
    
    // Speech scene
    public static final float POLICY_SPEECH_RNNOISE_STRENGTH = RNNOISE_STRENGTH_MILD;
    public static final float POLICY_SPEECH_PRESENCE_BOOST_DB = 2.0f;
    public static final float POLICY_SPEECH_WDRC_RELEASE_FACTOR = 1.0f; // Normal release
    public static final boolean POLICY_SPEECH_EXPANDER_ACTIVE = false;
    
    // Speech in noise scene  
    public static final float POLICY_SPEECH_NOISE_RNNOISE_STRENGTH = RNNOISE_STRENGTH_MEDIUM;
    public static final float POLICY_SPEECH_NOISE_PRESENCE_BOOST_DB = 4.0f;
    public static final float POLICY_SPEECH_NOISE_WDRC_RELEASE_FACTOR = 1.25f; // 25% slower release
    public static final boolean POLICY_SPEECH_NOISE_EXPANDER_ACTIVE = false;
    
    // Steady noise scene
    public static final float POLICY_STEADY_NOISE_RNNOISE_STRENGTH = RNNOISE_STRENGTH_STRONG;
    public static final float POLICY_STEADY_NOISE_PRESENCE_BOOST_DB = 3.0f;
    public static final float POLICY_STEADY_NOISE_WDRC_RELEASE_FACTOR = 1.4f; // 40% slower release
    public static final boolean POLICY_STEADY_NOISE_EXPANDER_ACTIVE = true;
    
    // Downward Expander (Noise Gate)
    public static final float EXPANDER_THRESHOLD_DBFS = -45.0f; // Gate threshold
    public static final float EXPANDER_RATIO = 2.0f; // Expansion ratio (gentle)
    public static final float EXPANDER_ATTACK_MS = 5.0f; // Fast attack
    public static final float EXPANDER_RELEASE_MS = 50.0f; // Moderate release
    public static final float EXPANDER_MAX_ATTENUATION_DB = 6.0f; // Maximum attenuation
    public static final float COMFORT_NOISE_LEVEL_DBFS = -50.0f; // Comfort noise floor
    public static final float COMFORT_NOISE_FADE_MS = 10.0f; // Fade in/out time
    
    // Presence Filter (EQ)
    public static final float PRESENCE_FILTER_CENTER_FREQ = 2500.0f; // Hz - center of presence boost
    public static final float PRESENCE_FILTER_BANDWIDTH = 2.0f; // Octaves - filter bandwidth
    public static final float PRESENCE_FILTER_Q = 0.707f; // Filter Q factor
    public static final float PRESENCE_FILTER_MAX_BOOST_DB = 5.0f; // Maximum boost allowed
    
    // Adaptive Feedback Canceller (AFC)
    public static final int AFC_FILTER_LENGTH = 64; // Adaptive filter taps (8-16ms at 48kHz)
    public static final float AFC_STEP_SIZE = 0.001f; // NLMS step size
    public static final float AFC_LEAK_FACTOR = 0.9999f; // Leaky adaptation factor
    public static final float AFC_MAX_ADAPTATION_GAIN = 10.0f; // Maximum adaptation gain
    public static final float AFC_FREEZE_VAD_THRESHOLD = 0.7f; // VAD threshold to freeze adaptation
    public static final float AFC_POWER_REGULARIZATION = 1e-6f; // Input power regularization
    public static final int AFC_FREEZE_FRAMES_ON_SPEECH = 10; // Frames to freeze after speech onset
    
    // Performance Budgets (Phase 2)
    public static final float RNNOISE_CONTROLLER_BUDGET_MS = 3.0f; // RNNoise+ controller budget
    public static final float SCENE_CLASSIFIER_BUDGET_MS = 0.5f; // Scene classification budget
    public static final float AFC_BUDGET_MS = 0.6f; // AFC processing budget
    
    // Channel identifiers
    public static final int CHANNEL_LEFT = 0;
    public static final int CHANNEL_RIGHT = 1;
    public static final int NUM_CHANNELS = 2;
    
    // DSP bypass modes
    public enum ProcessingMode {
        FULL_PROCESSING,     // All DSP active
        FULL,               // Alias for FULL_PROCESSING (test compatibility)
        BYPASS_RNNOISE,     // Skip noise reduction only
        BYPASS_WDRC,        // Skip WDRC, keep limiter
        SAFE_PASSTHROUGH,   // Only limiter active
        RAW_PASSTHROUGH     // NO processing at all - pure audio for debugging
    }
    
    // Scene Classification Labels (Phase 2)
    public enum SceneType {
        SPEECH,              // Clean speech, minimal noise
        SPEECH_IN_NOISE,     // Speech with competing background noise
        STEADY_NOISE,        // Consistent background noise without speech
        UNKNOWN              // Unclassified or transitional scene
    }
    
    // RNNoise Suppression Levels (Phase 2)  
    public enum RNNoiseStrength {
        MILD(RNNOISE_STRENGTH_MILD),
        MEDIUM(RNNOISE_STRENGTH_MEDIUM), 
        STRONG(RNNOISE_STRENGTH_STRONG),
        BYPASS(0.0f);
        
        public final float value;
        
        RNNoiseStrength(float value) {
            this.value = value;
        }
    }
    
    private AudioConfig() {
        // Prevent instantiation
    }
    
    /**
     * Validates audio configuration consistency.
     * @throws IllegalStateException if configuration is invalid
     */
    public static void validate() {
        // Validate that frame size matches the configured sample rate and frame duration
        int expectedFrameSize = Math.round(SAMPLE_RATE * (FRAME_DURATION_MS / 1000.0f));
        if (FRAME_SIZE_SAMPLES != expectedFrameSize) {
            throw new IllegalStateException("Frame size must match 10ms at configured SAMPLE_RATE: expected="
                + expectedFrameSize + " got=" + FRAME_SIZE_SAMPLES);
        }
        if (RING_BUFFER_FRAMES < 2) {
            throw new IllegalStateException("Ring buffer too small - minimum 2 frames required");
        }
        if (LIMITER_LOOKAHEAD_SAMPLES > FRAME_SIZE_SAMPLES / 2) {
            throw new IllegalStateException("Limiter lookahead too large for frame size");
        }
        if (CHANNELS != 2) {
            throw new IllegalStateException("Stereo processing requires 2 channels");
        }
    }
    
    /**
     * Converts dBFS to linear amplitude scale.
     */
    public static float dbfsToLinear(float dbfs) {
        return (float) Math.pow(10.0, dbfs / 20.0);
    }
    
    /**
     * Converts linear amplitude to dBFS.
     */
    public static float linearToDbfs(float linear) {
        return (float) (20.0 * Math.log10(Math.max(linear, 1e-6f)));
    }
    
    /**
     * Converts dB SPL to dBFS using calibration constant.
     */
    public static float dbSplToDbfs(float dbSpl, boolean isRightChannel) {
        float calibration = isRightChannel ? DBFS_TO_DB_SPL_RIGHT : DBFS_TO_DB_SPL_LEFT;
        return dbSpl - calibration;
    }
    
    /**
     * Converts dBFS to dB SPL using calibration constant.
     */
    public static float dbfsToDbSpl(float dbfs, boolean isRightChannel) {
        float calibration = isRightChannel ? DBFS_TO_DB_SPL_RIGHT : DBFS_TO_DB_SPL_LEFT;
        return dbfs + calibration;
    }
}