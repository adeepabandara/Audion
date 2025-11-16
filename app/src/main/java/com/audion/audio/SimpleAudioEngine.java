package com.audion.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.example.audion.RNNoise;
import com.example.audion.data.HearingTestResult;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 2 Audio Engine: Per-ear 4-band stereo processing.
 * 
 * Architecture:
 * 1. AudioRecord captures 480 samples (10ms @ 48kHz) MONO/STEREO
 * 2. Convert to float[] (normalize to ±1.0)
 * 3. Optional: RNNoise processing (MIC mode only)
 * 4. Duplicate mono → left/right processing chains OR use stereo capture
 * 5. Each ear: 4-band filterbank → per-band gains → sum → soft clip
 * 6. Interleave left/right → stereo float[]
 * 7. Write to AudioTrack (STEREO, PCM_FLOAT)
 * 
 * Audio Modes:
 * - MIC: Microphone input (environmental amplification) - max 40 dB
 * - MEDIA: Phone media input (music/video/calls amplification) - max 30 dB
 * 
 * Processing mode:
 * - PHASE1_MODE: Global gain (legacy Phase 1 behavior)
 * - PHASE2_MODE: Per-ear 4-band processing
 */
public final class SimpleAudioEngine {
    private static final String TAG = "SimpleAudioEngine";
    
    /**
     * Callback interface for waveform updates
     */
    public interface WaveformCallback {
        void onWaveformUpdate(float inputLevel, float outputLevel);
    }
    
    // Waveform callback
    private WaveformCallback waveformCallback;
    private int waveformFrameCounter = 0;  // For throttling logs;
    
    /**
     * Audio input mode
     */
    public enum AudioMode {
        MIC,    // Microphone input (environmental amplification)
        MEDIA   // Phone media input (system playback amplification)
    }
    
    // Current audio mode
    private AudioMode currentMode = AudioMode.MIC;
    private final AtomicReference<AudioMode> audioMode = new AtomicReference<>(AudioMode.MIC);
    
    // MediaProjection for audio capture (API 29+)
    private MediaProjection mediaProjection;
    
    // Maximum gain per mode
    private static final float MAX_GAIN_MIC_DB = 40.0f;     // Environmental amplification
    private static final float MAX_GAIN_MEDIA_DB = 30.0f;   // Media amplification
    
    // AGC for media mode
    private static final float TARGET_RMS_DBFS = -12.0f;    // Target loudness for media
    private float currentAgcGain_dB = 0.0f;                 // AGC adjustment
    private static final float AGC_ADJUST_RATE = 1.0f;      // dB per second
    
    // Audio I/O
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    
    // RNNoise processor
    private RNNoise rnnoise;
    
    // Phase 2: Per-ear processors
    private PerEarProcessor leftProcessor;
    private PerEarProcessor rightProcessor;
    private boolean phase2Enabled = false;
    
    // Phase 3: Adaptive feedback canceller
    private FeedbackCanceller feedbackCanceller;
    private boolean feedbackCancellerEnabled = false;
    
    // Phase 3: Scene analyzer
    private SceneAnalyzer sceneAnalyzer;
    private boolean sceneAnalysisEnabled = false;
    private SceneAnalyzer.Scene currentScene = SceneAnalyzer.Scene.SPEECH;
    
    // Phase 5: Multiband WDRC + Look-Ahead Limiter
    private MultibandWDRC leftMultibandWDRC;
    private MultibandWDRC rightMultibandWDRC;
    private DualStageLimiter leftLimiter;
    private DualStageLimiter rightLimiter;
    private boolean advancedDspEnabled = false;  // Enable multiband WDRC + look-ahead limiting
    
    // Phase 6: Intelligent gain staging
    private GainStagingManager leftGainStaging;
    private GainStagingManager rightGainStaging;
    
    // Processing thread
    private Thread processingThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // Runtime controls (thread-safe atomic variables)
    private final AtomicBoolean noiseReductionEnabled = new AtomicBoolean(true);
    private final AtomicReference<Float> amplificationGain = new AtomicReference<>(1.0f);
    
    // Personalization status
    private final AtomicBoolean personalizationAvailable = new AtomicBoolean(false);
    
    // Speaker isolation controls (Focus Mode - Phase 4: Smooth attenuation)
    private final AtomicBoolean speakerIsolationEnabled = new AtomicBoolean(false);
    private final AtomicBoolean selectedSpeakerActive = new AtomicBoolean(true);
    
    // Phase 4: Smooth crossfade for speaker isolation (100ms ramp @ 48kHz = 4800 samples)
    private float currentFocusGain = 1.0f;  // Current gain (0.0 = muted, 1.0 = full volume)
    private float targetFocusGain = 1.0f;   // Target gain
    private static final int CROSSFADE_SAMPLES = 4800;  // 100ms @ 48kHz
    private static final float GAIN_ACTIVE = 1.0f;      // Full volume when selected speaker is talking
    private static final float GAIN_INACTIVE = 0.1f;    // -20 dB when selected speaker is NOT talking
    
    // Pre-allocated buffers (10ms frames @ 48kHz)
    private final short[] captureBuffer = new short[AudioConfig.FRAME_SIZE_SAMPLES];
    private final float[] floatInput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    private final float[] floatProcessed = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    
    // Phase 3: Feedback canceller buffers
    private final float[] feedbackCorrected = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    
    // Phase 1 buffers
    private final short[] outputBuffer = new short[AudioConfig.FRAME_SIZE_SAMPLES];
    
    // Phase 2 buffers (stereo float)
    private final float[] leftOutput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    private final float[] rightOutput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    private final float[] stereoFloatBuffer = new float[AudioConfig.FRAME_SIZE_SAMPLES * 2];
    
    // Phase 5: Multiband WDRC + Look-Ahead Limiter buffers
    private final float[] leftWdrcOutput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    private final float[] rightWdrcOutput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    private final float[] leftLimitedOutput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    private final float[] rightLimitedOutput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    
    // Phase 6: Per-band gain buffers
    private final float[] leftWdrcInput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    private final float[] rightWdrcInput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
    
    // Safety limiter threshold
    private static final float LIMITER_THRESHOLD = 0.95f;
    
    // Statistics
    private long framesProcessed = 0;
    private long totalProcessingTimeUs = 0;
    private long rmsLogCounter = 0;
    private static final int RMS_LOG_INTERVAL_FRAMES = 100; // Log RMS every 100 frames (~1 sec)
    
    // Phase 4: Audio quality metrics tracking
    private AudioQualityMetrics qualityMetrics = null;
    
    /**
     * Initialize AudioRecord based on audio mode
     * 
     * @param mode AudioMode.MIC for microphone input, AudioMode.MEDIA for system playback capture
     * @return true if successful, false otherwise
     */
    private boolean initializeAudioRecordForMode(AudioMode mode) {
        try {
            // Release existing AudioRecord if any
            if (audioRecord != null) {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
                audioRecord = null;
            }
            
            int captureBufferSize = Math.max(
                AudioRecord.getMinBufferSize(
                    AudioConfig.SAMPLE_RATE,
                    AudioConfig.CHANNEL_IN_CONFIG,
                    AudioConfig.ENCODING_FORMAT
                ),
                AudioConfig.FRAME_SIZE_SAMPLES * AudioConfig.BYTES_PER_SAMPLE * 4 // 40ms buffer
            );
            
            if (mode == AudioMode.MIC) {
                // Standard microphone input
                audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AudioConfig.SAMPLE_RATE,
                    AudioConfig.CHANNEL_IN_CONFIG,
                    AudioConfig.ENCODING_FORMAT,
                    captureBufferSize
                );
                Log.i(TAG, "AudioRecord initialized: MIC mode, MONO, PCM_16BIT, 48kHz");
                
            } else if (mode == AudioMode.MEDIA) {
                // Media playback capture (requires API 29+ and MediaProjection)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Log.e(TAG, "Media mode requires Android 10 (API 29) or higher");
                    return false;
                }
                
                if (mediaProjection == null) {
                    Log.e(TAG, "Media mode requires MediaProjection - call setMediaProjection() first");
                    return false;
                }
                
                // Build AudioPlaybackCaptureConfiguration
                android.media.AudioPlaybackCaptureConfiguration captureConfig = 
                    new android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();
                
                // Create AudioRecord with playback capture
                audioRecord = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(AudioConfig.SAMPLE_RATE)
                        .setChannelMask(AudioConfig.CHANNEL_IN_CONFIG)
                        .setEncoding(AudioConfig.ENCODING_FORMAT)
                        .build())
                    .setBufferSizeInBytes(captureBufferSize)
                    .build();
                
                Log.i(TAG, "AudioRecord initialized: MEDIA mode (AudioPlaybackCapture), MONO, PCM_16BIT, 48kHz");
            }
            
            if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord for mode: " + mode);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AudioRecord for mode " + mode, e);
            return false;
        }
    }
    
    /**
     * Set MediaProjection for media capture mode
     * Must be called before switching to MEDIA mode
     * 
     * @param projection MediaProjection obtained from MediaProjectionManager
     */
    public synchronized void setMediaProjection(MediaProjection projection) {
        this.mediaProjection = projection;
        Log.i(TAG, "MediaProjection set for media capture");
    }
    
    /**
     * Switch audio input mode
     * 
     * @param mode AudioMode.MIC or AudioMode.MEDIA
     * @return true if mode switched successfully
     */
    public synchronized boolean setAudioMode(AudioMode mode) {
        if (currentMode == mode) {
            Log.d(TAG, "Already in mode: " + mode);
            return true;
        }
        
        Log.i(TAG, "Switching audio mode: " + currentMode + " → " + mode);
        
        boolean wasRunning = isRunning.get();
        
        // Stop processing if running
        if (wasRunning) {
            stop();
        }
        
        // Reinitialize AudioRecord for new mode
        if (!initializeAudioRecordForMode(mode)) {
            Log.e(TAG, "Failed to initialize AudioRecord for mode: " + mode);
            // Try to revert to previous mode
            if (!initializeAudioRecordForMode(currentMode)) {
                Log.e(TAG, "CRITICAL: Failed to revert to previous mode!");
                return false;
            }
            return false;
        }
        
        // Update mode and gain limits
        currentMode = mode;
        audioMode.set(mode);
        
        // Update GainStagingManager max gain
        float maxGainDb = (mode == AudioMode.MEDIA) ? MAX_GAIN_MEDIA_DB : MAX_GAIN_MIC_DB;
        if (leftGainStaging != null) {
            leftGainStaging.setMaxGainDb(maxGainDb);
        }
        if (rightGainStaging != null) {
            rightGainStaging.setMaxGainDb(maxGainDb);
        }
        
        Log.i(TAG, "Mode switched to " + mode + " (max gain: " + maxGainDb + " dB)");
        
        // Restart if was running
        if (wasRunning) {
            if (!start()) {
                Log.e(TAG, "Failed to restart after mode switch");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get current audio mode
     */
    public AudioMode getAudioMode() {
        return audioMode.get();
    }
    
    /**
     * Initialize the audio engine
     */
    public synchronized boolean initialize() {
        Log.i(TAG, "Initializing SimpleAudioEngine");
        
        try {
            // Validate configuration
            AudioConfig.validate();
            
            // Initialize RNNoise
            rnnoise = new RNNoise();
            rnnoise.initialize();
            Log.i(TAG, "RNNoise initialized (480-sample frames @ 48kHz)");
            
            // Initialize AudioRecord based on current mode
            if (!initializeAudioRecordForMode(currentMode)) {
                throw new RuntimeException("Failed to initialize AudioRecord for mode: " + currentMode);
            }
            
            // Initialize AudioTrack for playback
            // Phase 2: Use ENCODING_PCM_FLOAT for better precision with per-ear processing
            AudioFormat.Builder audioFormatBuilder = new AudioFormat.Builder()
                .setSampleRate(AudioConfig.SAMPLE_RATE)
                .setChannelMask(AudioConfig.CHANNEL_OUT_CONFIG);
            
            // Use FLOAT encoding for Phase 2, INT16 for Phase 1
            int encoding = phase2Enabled ? AudioFormat.ENCODING_PCM_FLOAT : AudioConfig.ENCODING_FORMAT;
            audioFormatBuilder.setEncoding(encoding);
            
            int bytesPerSample = phase2Enabled ? 4 : 2; // float=4 bytes, short=2 bytes
            int playbackBufferSize = Math.max(
                AudioTrack.getMinBufferSize(
                    AudioConfig.SAMPLE_RATE,
                    AudioConfig.CHANNEL_OUT_CONFIG,
                    encoding
                ),
                AudioConfig.FRAME_SIZE_SAMPLES * bytesPerSample * 2 * 4 // Stereo * 40ms buffer
            );
            
            audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                    .build())
                .setAudioFormat(audioFormatBuilder.build())
                .setBufferSizeInBytes(playbackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build();
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new RuntimeException("Failed to initialize AudioTrack");
            }
            
            String encodingStr = phase2Enabled ? "PCM_FLOAT" : "PCM_16BIT";
            Log.i(TAG, "AudioTrack initialized: STEREO, " + encodingStr + ", 48kHz, buffer=" + playbackBufferSize);
            
            // Initialize Phase 2 processors if enabled
            if (phase2Enabled) {
                leftProcessor = new PerEarProcessor("LEFT", AudioConfig.FRAME_SIZE_SAMPLES, AudioConfig.SAMPLE_RATE);
                rightProcessor = new PerEarProcessor("RIGHT", AudioConfig.FRAME_SIZE_SAMPLES, AudioConfig.SAMPLE_RATE);
                Log.i(TAG, "Phase 2 per-ear processors initialized");
            }
            
            // Phase 3: Initialize feedback canceller
            feedbackCanceller = new FeedbackCanceller();
            feedbackCancellerEnabled = true;  // Enable by default
            Log.i(TAG, "Phase 3: Feedback canceller initialized");
            
            // Phase 3: Initialize scene analyzer
            sceneAnalyzer = new SceneAnalyzer();
            sceneAnalysisEnabled = true;  // Enable by default
            Log.i(TAG, "Phase 3: Scene analyzer initialized");
            
            // Phase 4: Initialize audio quality metrics
            qualityMetrics = new AudioQualityMetrics();
            Log.i(TAG, "Phase 4: Audio quality metrics initialized");
            
            // Phase 5: Initialize Multiband WDRC + Dual-Stage Limiters
            if (phase2Enabled) {
                leftMultibandWDRC = new MultibandWDRC("LEFT", AudioConfig.FRAME_SIZE_SAMPLES, AudioConfig.SAMPLE_RATE);
                rightMultibandWDRC = new MultibandWDRC("RIGHT", AudioConfig.FRAME_SIZE_SAMPLES, AudioConfig.SAMPLE_RATE);
                
                // Phase 6: Initialize Dual-Stage Limiters (look-ahead + soft-clipping)
                leftLimiter = new DualStageLimiter("LEFT", AudioConfig.SAMPLE_RATE);
                rightLimiter = new DualStageLimiter("RIGHT", AudioConfig.SAMPLE_RATE);
                
                advancedDspEnabled = true;  // Enable by default
                Log.i(TAG, "Phase 5+6: Multiband WDRC + Dual-Stage Limiters initialized");
                Log.i(TAG, "  - 5 bands: 250-750, 750-1500, 1500-3000, 3000-6000, 6000-8000 Hz");
                Log.i(TAG, "  - Stage 1: Look-ahead limiter (-3 dBFS, 10ms window)");
                Log.i(TAG, "  - Stage 2: Soft-clipping (tanh @ -1 dBFS)");
                Log.i(TAG, "  - Total latency: ~10ms");
                
                // Phase 6: Initialize Gain Staging Managers
                leftGainStaging = new GainStagingManager();
                rightGainStaging = new GainStagingManager();
                Log.i(TAG, "Phase 6: GainStagingManager initialized");
                Log.i(TAG, "  - UCL-aware gain calculation");
                Log.i(TAG, "  - Per-band frequency-dependent gains (5 bands)");
                Log.i(TAG, "  - Adaptive loudness control (target RMS -12 dBFS)");
            }
            
            Log.i(TAG, "════════════════════════════════════════════════════════");
            Log.i(TAG, "SimpleAudioEngine initialized successfully");
            if (phase2Enabled) {
                Log.i(TAG, "Mode: PHASE 2+3+4+5 - Advanced clinical DSP pipeline");
                Log.i(TAG, "Pipeline: Mic → FeedbackCancel → RNNoise → Multiband WDRC (5-band) → Look-Ahead Limiter → Speakers");
                Log.i(TAG, "Features: Multiband WDRC, Look-ahead limiting, NAL-NL2/DSL fitting, feedback cancellation, scene analysis");
            } else {
                Log.i(TAG, "Mode: PHASE 1 - Global gain mono processing");
                Log.i(TAG, "Pipeline: AudioRecord → RNNoise → Gain → Limiter → Stereo duplicate → AudioTrack");
            }
            Log.i(TAG, "════════════════════════════════════════════════════════");
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SimpleAudioEngine", e);
            cleanup();
            return false;
        }
    }
    
    /**
     * Start audio processing
     */
    public synchronized boolean start() {
        if (isRunning.get()) {
            Log.w(TAG, "SimpleAudioEngine already running");
            return true;
        }
        
        if (audioRecord == null || audioTrack == null || rnnoise == null) {
            Log.e(TAG, "Cannot start - not initialized");
            return false;
        }
        
        AudioMode currentMode = audioMode.get();
        Log.i(TAG, "Starting SimpleAudioEngine in mode: " + currentMode);
        
        // Verify MediaProjection for MEDIA mode
        if (currentMode == AudioMode.MEDIA && mediaProjection == null) {
            Log.e(TAG, "CRITICAL: Starting in MEDIA mode but MediaProjection is NULL!");
        }
        
        isRunning.set(true);
        framesProcessed = 0;
        totalProcessingTimeUs = 0;
        
        // Start audio I/O
        audioRecord.startRecording();
        audioTrack.play();
        
        Log.i(TAG, "AudioRecord state: " + audioRecord.getState() + ", Recording state: " + audioRecord.getRecordingState());
        
        // Start processing thread
        processingThread = new Thread(this::processingLoop, "SimpleAudioProcessing");
        processingThread.setPriority(Thread.MAX_PRIORITY);
        processingThread.start();
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
        Log.i(TAG, "★★★ SimpleAudioEngine STARTED ★★★");
        Log.i(TAG, "MODE: " + currentMode);
        Log.i(TAG, "INPUT: MONO, PCM_16BIT, 48kHz");
        Log.i(TAG, "PROCESSING: RNNoise (480 samples/frame, 10ms)");
        Log.i(TAG, "OUTPUT: STEREO, PCM_16BIT, 48kHz, LOW_LATENCY");
        Log.i(TAG, "════════════════════════════════════════════════════════");
        
        return true;
    }
    
    /**
     * Stop audio processing
     */
    public synchronized void stop() {
        if (!isRunning.get()) {
            Log.d(TAG, "SimpleAudioEngine already stopped");
            return;
        }
        
        Log.i(TAG, "Stopping SimpleAudioEngine");
        
        isRunning.set(false);
        
        // Wait for processing thread to finish
        if (processingThread != null) {
            try {
                processingThread.join(1000);
                if (processingThread.isAlive()) {
                    Log.w(TAG, "Processing thread did not stop within timeout");
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for processing thread");
            }
            processingThread = null;
        }
        
        // Stop audio I/O
        if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop();
        }
        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause();
            audioTrack.flush();
        }
        
        // Log statistics
        if (framesProcessed > 0) {
            double avgProcessingTimeUs = totalProcessingTimeUs / (double) framesProcessed;
            double avgProcessingTimePercent = (avgProcessingTimeUs / 10000.0) * 100.0; // 10ms = 10000us
            Log.i(TAG, String.format("Processed %d frames, avg processing time: %.1f μs (%.1f%% of frame time)",
                framesProcessed, avgProcessingTimeUs, avgProcessingTimePercent));
        }
        
        Log.i(TAG, "SimpleAudioEngine stopped");
    }
    
    /**
     * Release all resources
     */
    public synchronized void release() {
        stop();
        cleanup();
        Log.i(TAG, "SimpleAudioEngine released");
    }
    
    /**
     * Main processing loop - runs in high-priority thread
     */
    private void processingLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        
        Log.d(TAG, "Processing thread started (Phase " + (phase2Enabled ? "2" : "1") + ")");
        
        while (isRunning.get()) {
            long frameStartTime = System.nanoTime();
            
            try {
                // Step 1: Read 480 samples from AudioRecord (MONO)
                int samplesRead = audioRecord.read(captureBuffer, 0, AudioConfig.FRAME_SIZE_SAMPLES);
                
                if (samplesRead != AudioConfig.FRAME_SIZE_SAMPLES) {
                    Log.w(TAG, "AudioRecord read incomplete: " + samplesRead + " / " + AudioConfig.FRAME_SIZE_SAMPLES);
                    continue;
                }
                
                // Step 2: Convert short to float (normalize to ±1.0)
                for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                    floatInput[i] = captureBuffer[i] / 32768.0f;
                }
                
                // Debug: Log audio levels periodically to verify capture
                if (framesProcessed % 200 == 0) {
                    float maxLevel = 0;
                    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                        maxLevel = Math.max(maxLevel, Math.abs(floatInput[i]));
                    }
                    Log.d(TAG, String.format("Mode=%s, Frame=%d, MaxLevel=%.3f", 
                        audioMode.get().name(), framesProcessed, maxLevel));
                }
                
                // Focus Mode: Process audio frame for diarization
                try {
                    com.example.audion.FocusModeManager.getInstance().processAudioFrame(floatInput);
                } catch (Exception e) {
                    // Don't let diarization errors crash audio processing
                    Log.w(TAG, "Diarization processing error: " + e.getMessage());
                }
                
                // Check current audio mode
                AudioMode mode = audioMode.get();
                
                // Phase 3 Step 2a: Adaptive feedback cancellation (MIC mode only)
                if (mode == AudioMode.MIC && feedbackCancellerEnabled && feedbackCanceller != null) {
                    feedbackCanceller.process(floatInput, feedbackCorrected, AudioConfig.FRAME_SIZE_SAMPLES);
                    // Use feedback-corrected signal for rest of pipeline
                    System.arraycopy(feedbackCorrected, 0, floatInput, 0, AudioConfig.FRAME_SIZE_SAMPLES);
                }
                
                // Phase 3 Step 2b: Scene analysis (MIC mode only)
                if (mode == AudioMode.MIC && sceneAnalysisEnabled && sceneAnalyzer != null) {
                    SceneAnalyzer.Scene detectedScene = sceneAnalyzer.process(floatInput, AudioConfig.FRAME_SIZE_SAMPLES);
                    if (detectedScene != currentScene) {
                        currentScene = detectedScene;
                        // Apply scene-specific settings
                        applySceneSettings(currentScene);
                    }
                }
                
                // Step 3: RNNoise processing (MIC mode only)
                if (mode == AudioMode.MIC && noiseReductionEnabled.get()) {
                    // RNNoise expects non-normalized float (raw short values as float)
                    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                        floatInput[i] = captureBuffer[i];
                    }
                    
                    RNNoise.ProcessResult result = rnnoise.processFrame(floatInput);
                    
                    // Convert back to normalized float
                    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                        floatProcessed[i] = result.audio[i] / 32768.0f;
                    }
                    
                    // Log occasionally
                    if (framesProcessed % 100 == 0) {
                        Log.d(TAG, String.format("Frame %d: RNNoise active, VAD=%.3f", 
                            framesProcessed, result.vadProbability));
                    }
                } else {
                    // Passthrough - just normalize
                    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                        floatProcessed[i] = captureBuffer[i] / 32768.0f;
                    }
                }
                
                // Step 4: Process based on mode
                if (phase2Enabled) {
                    processPhase2();
                } else {
                    processPhase1();
                }
                
                // Calculate waveform levels and send callback
                if (waveformCallback != null) {
                    // Input RMS (normalized float)
                    float inRms = calculateRMS(floatInput, AudioConfig.FRAME_SIZE_SAMPLES);
                    
                    // Output RMS (normalized float from left/right output or mono output)
                    float outRms;
                    if (phase2Enabled && leftOutput != null && rightOutput != null) {
                        // Average left and right channel RMS
                        float leftRms = calculateRMS(leftOutput, AudioConfig.FRAME_SIZE_SAMPLES);
                        float rightRms = calculateRMS(rightOutput, AudioConfig.FRAME_SIZE_SAMPLES);
                        outRms = (leftRms + rightRms) / 2.0f;
                    } else {
                        // Use mono output
                        outRms = calculateRMS(floatProcessed, AudioConfig.FRAME_SIZE_SAMPLES);
                    }
                    
                    // Clamp to 0-1 range for UI display
                    float normIn = Math.max(0f, Math.min(1f, inRms));
                    float normOut = Math.max(0f, Math.min(1f, outRms));
                    
                    waveformCallback.onWaveformUpdate(normIn, normOut);
                    
                    // Log every 100 frames (~1 second) to verify callback is working
                    waveformFrameCounter++;
                    if (waveformFrameCounter % 100 == 0) {
                        android.util.Log.d(TAG, "Waveform callback: in=" + normIn + " out=" + normOut);
                    }
                }
                
                // Update statistics
                framesProcessed++;
                long frameTime = (System.nanoTime() - frameStartTime) / 1000; // Microseconds
                totalProcessingTimeUs += frameTime;
                
                // Phase 4: Update quality metrics (if Phase 2+ enabled)
                if (phase2Enabled && qualityMetrics != null) {
                    // Store copy of input for metrics
                    float[] leftInputCopy = new float[AudioConfig.FRAME_SIZE_SAMPLES];
                    float[] rightInputCopy = new float[AudioConfig.FRAME_SIZE_SAMPLES];
                    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                        leftInputCopy[i] = floatProcessed[i];  // Mono input (same for both)
                        rightInputCopy[i] = floatProcessed[i];
                    }
                    
                    qualityMetrics.updateMetrics(
                        leftInputCopy, rightInputCopy,
                        leftOutput, rightOutput,
                        frameTime * 1000,  // Convert μs to ns
                        AudioConfig.FRAME_SIZE_SAMPLES
                    );
                }
                
                // Warn if processing is too slow
                if (frameTime > 8000) { // 80% of 10ms budget
                    Log.w(TAG, String.format("Frame %d took %.1f ms (exceeds budget)", 
                        framesProcessed, frameTime / 1000.0));
                }
                
                // Log RMS periodically
                rmsLogCounter++;
                if (rmsLogCounter >= RMS_LOG_INTERVAL_FRAMES && phase2Enabled) {
                    float leftRMS = leftProcessor.getRMSAndReset();
                    float rightRMS = rightProcessor.getRMSAndReset();
                    Log.i(TAG, String.format("[RMS] Left=%.1f dBFS, Right=%.1f dBFS", leftRMS, rightRMS));
                    rmsLogCounter = 0;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in processing loop", e);
            }
        }
        
        Log.d(TAG, "Processing thread finished");
    }
    
    /**
     * Phase 1 processing: Global gain + stereo duplicate
     */
    private void processPhase1() {
        // Convert float to short with amplification
        float currentGain = amplificationGain.get();
        
        for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
            float amplified = floatProcessed[i] * 32768.0f * currentGain;
            
            // Clamp to short range
            if (amplified > 32767f) amplified = 32767f;
            if (amplified < -32768f) amplified = -32768f;
            
            outputBuffer[i] = (short) amplified;
        }
        
        // Apply safety limiter
        for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
            if (outputBuffer[i] > 32767 * LIMITER_THRESHOLD) {
                outputBuffer[i] = (short) (32767 * LIMITER_THRESHOLD);
            } else if (outputBuffer[i] < -32768 * LIMITER_THRESHOLD) {
                outputBuffer[i] = (short) (-32768 * LIMITER_THRESHOLD);
            }
        }
        
        // Duplicate to stereo
        short[] stereoBuffer = new short[AudioConfig.FRAME_SIZE_SAMPLES * 2];
        for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
            stereoBuffer[i * 2] = outputBuffer[i];     // Left
            stereoBuffer[i * 2 + 1] = outputBuffer[i]; // Right
        }
        
        // Write to AudioTrack
        int written = audioTrack.write(stereoBuffer, 0, stereoBuffer.length);
        if (written != stereoBuffer.length) {
            Log.w(TAG, "AudioTrack write incomplete: " + written + " / " + stereoBuffer.length);
        }
    }
    
    /**
     * Phase 2 processing: Per-ear processing with advanced DSP chain
     * 
     * Pipeline (Phase 6):
     * 1. RNNoise (already done - MIC mode only)
     * 2. Phase 6: Per-band gains from GainStagingManager (UCL-aware)
     * 3. Phase 5: Multiband WDRC (adaptive compression)
     * 4. Phase 6: Dual-Stage Limiter (look-ahead + soft-clipping)
     * 5. Speaker isolation (Focus Mode)
     * 
     * NOTE: Media mode AGC removed - user gain control takes priority
     */
    private void processPhase2() {
        // Media mode: AGC DISABLED - user gain slider should control output level
        // The GainStagingManager below will handle amplification
        
        if (advancedDspEnabled && leftMultibandWDRC != null && leftLimiter != null) {
            // Phase 6: Intelligent gain staging → Adaptive WDRC → Dual-Stage Limiter
            
            // Step 1: Get per-band gains from GainStagingManager
            float[] leftBandGains = (leftGainStaging != null) ? 
                leftGainStaging.getLeftBandGains() : new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
            float[] rightBandGains = (rightGainStaging != null) ? 
                rightGainStaging.getRightBandGains() : new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
            
            // Calculate average gain (simplified - full per-band application later)
            float leftAvgGain = (leftBandGains[0] + leftBandGains[1] + leftBandGains[2] + 
                                leftBandGains[3] + leftBandGains[4]) / 5.0f;
            float rightAvgGain = (rightBandGains[0] + rightBandGains[1] + rightBandGains[2] + 
                                 rightBandGains[3] + rightBandGains[4]) / 5.0f;
            
            // SAFETY: Enforce absolute 40 dB maximum (100x linear gain)
            final float MAX_LINEAR_GAIN = 100.0f;  // 40 dB = 10^(40/20) = 100
            if (leftAvgGain > MAX_LINEAR_GAIN) {
                leftAvgGain = MAX_LINEAR_GAIN;
                Log.w(TAG, String.format("⚠️ Left gain capped at 40 dB (was %.1f dB)", 20*Math.log10(leftAvgGain)));
            }
            if (rightAvgGain > MAX_LINEAR_GAIN) {
                rightAvgGain = MAX_LINEAR_GAIN;
                Log.w(TAG, String.format("⚠️ Right gain capped at 40 dB (was %.1f dB)", 20*Math.log10(rightAvgGain)));
            }
            
            // Feedback Prevention: Gentle high-frequency rolloff at extreme gains only
            // High frequencies (6-8 kHz) most prone to acoustic feedback
            float maxGain = Math.max(leftAvgGain, rightAvgGain);
            if (maxGain > 63.0f) {  // Above 36 dB (63x linear), apply gentle rolloff
                float hfReduction = 0.7f;  // Only -3 dB at highest frequencies (preserves clarity)
                leftBandGains[4] *= hfReduction;   // Band 4: 6-8 kHz
                rightBandGains[4] *= hfReduction;
                // Log first occurrence
                if (framesProcessed % 100 == 0) {
                    Log.i(TAG, String.format("[Feedback Prevention] Gain=%.1f dB → HF reduction -3 dB", 
                        20*Math.log10(maxGain)));
                }
            }
            
            // Apply gains BEFORE WDRC (key change from Phase 5!)
            for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                leftWdrcInput[i] = floatProcessed[i] * leftAvgGain;
                rightWdrcInput[i] = floatProcessed[i] * rightAvgGain;
            }
            
            // Step 2: Multiband WDRC (adaptive compression)
            leftMultibandWDRC.process(leftWdrcInput, leftWdrcOutput, AudioConfig.FRAME_SIZE_SAMPLES);
            rightMultibandWDRC.process(rightWdrcInput, rightWdrcOutput, AudioConfig.FRAME_SIZE_SAMPLES);
            
            // Step 3: Dual-Stage Limiter (final safety)
            leftLimiter.process(leftWdrcOutput, leftOutput, AudioConfig.FRAME_SIZE_SAMPLES);
            rightLimiter.process(rightWdrcOutput, rightOutput, AudioConfig.FRAME_SIZE_SAMPLES);
            
            // Step 4: Update adaptive loudness control (every 100ms)
            if (framesProcessed % 10 == 0 && leftGainStaging != null && rightGainStaging != null) {
                // Calculate RMS of WDRC output (before limiting)
                float rmsSum = 0.0f;
                for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                    float sample = (leftWdrcOutput[i] + rightWdrcOutput[i]) / 2.0f;  // Average channels
                    rmsSum += sample * sample;
                }
                float rms = (float) Math.sqrt(rmsSum / AudioConfig.FRAME_SIZE_SAMPLES);
                float rms_dBFS = 20.0f * (float) Math.log10(Math.max(rms, 1e-6f));
                
                // Feedback detection: Only trigger on sustained near-clipping (likely feedback, not music)
                if (rms_dBFS > -1.5f) {  // Changed from -6 dBFS (was too sensitive, killed clarity)
                    Log.w(TAG, String.format("⚠️ FEEDBACK DETECTED! RMS=%.1f dBFS - Reducing output", rms_dBFS));
                    // Apply gentle attenuation to prevent feedback
                    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                        leftOutput[i] *= 0.6f;  // -4.5 dB reduction (was -10 dB, too aggressive)
                        rightOutput[i] *= 0.6f;
                    }
                }
                
                // Update adaptive gain in GainStagingManager
                leftGainStaging.updateAdaptiveLoudness(rms_dBFS);
                rightGainStaging.updateAdaptiveLoudness(rms_dBFS);
            }
            
        } else {
            // Legacy: Use old PerEarProcessor (5-band filterbank + per-band WDRC)
            leftProcessor.process(floatProcessed, leftOutput, AudioConfig.FRAME_SIZE_SAMPLES);
            rightProcessor.process(floatProcessed, rightOutput, AudioConfig.FRAME_SIZE_SAMPLES);
        }
        
        // Phase 4: Apply speaker isolation with strong attenuation
        // Uses FocusModeManager for real-time diarization checking
        if (speakerIsolationEnabled.get()) {
            boolean speakerActive = false;
            
            try {
                // Check with FocusModeManager for real-time speaker activity
                speakerActive = com.example.audion.FocusModeManager.getInstance().isSelectedSpeakerActive();
            } catch (Exception e) {
                // On error, don't mute (safety default)
                speakerActive = true;
                if (framesProcessed % 100 == 0) {
                    Log.w(TAG, "[Focus Mode] Error checking speaker activity: " + e.getMessage());
                }
            }
            
            // Debug logging every 50 frames (~0.5 seconds)
            if (framesProcessed % 50 == 0) {
                Log.d(TAG, String.format("[Focus Mode] Speaker active: %s, Current gain: %.2f, Target gain: %.2f",
                    speakerActive, currentFocusGain, targetFocusGain));
            }
            
            // Set target gain: 1.0 (full volume) if selected speaker is talking, 0.1 (-20dB) if not
            targetFocusGain = speakerActive ? GAIN_ACTIVE : GAIN_INACTIVE;
            
            // Apply smooth crossfade (100ms ramp to prevent clicks)
            float gainStep = (targetFocusGain - currentFocusGain) / CROSSFADE_SAMPLES;
            
            for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                // Interpolate gain toward target
                if (Math.abs(currentFocusGain - targetFocusGain) > 0.001f) {
                    currentFocusGain += gainStep;
                } else {
                    currentFocusGain = targetFocusGain;  // Snap to target when close
                }
                
                // Apply ramped gain
                leftOutput[i] *= currentFocusGain;
                rightOutput[i] *= currentFocusGain;
            }
        } else {
            // Reset gain when Focus Mode disabled
            currentFocusGain = 1.0f;
            targetFocusGain = 1.0f;
        }
        
        // Phase 6: Global gain REMOVED - now handled by GainStagingManager
        // Legacy: Only apply global gain for Phase 1 mode (non-advanced DSP)
        if (!advancedDspEnabled) {
            float currentGain = amplificationGain.get();
            if (currentGain != 1.0f) {
                for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
                    leftOutput[i] *= currentGain;
                    rightOutput[i] *= currentGain;
                }
            }
        }
        
        // Interleave stereo
        for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
            stereoFloatBuffer[i * 2] = leftOutput[i];       // Left channel
            stereoFloatBuffer[i * 2 + 1] = rightOutput[i];  // Right channel
        }
        
        // Write to AudioTrack (float format)
        int written = audioTrack.write(stereoFloatBuffer, 0, stereoFloatBuffer.length, AudioTrack.WRITE_BLOCKING);
        if (written != stereoFloatBuffer.length) {
            Log.w(TAG, "AudioTrack write incomplete: " + written + " / " + stereoFloatBuffer.length);
        }
    }
    
    /**
     * Phase 3: Apply scene-specific DSP settings.
     */
    private void applySceneSettings(SceneAnalyzer.Scene scene) {
        if (!phase2Enabled || leftProcessor == null || rightProcessor == null) {
            return;
        }
        
        // Adjust compression ratios based on scene
        float recommendedRatio = sceneAnalyzer.getRecommendedCompressionRatio();
        leftProcessor.setCompressionRatio(recommendedRatio);
        rightProcessor.setCompressionRatio(recommendedRatio);
        
        Log.i(TAG, String.format("Scene changed to %s: Compression ratio=%.1f:1", 
            scene, recommendedRatio));
    }
    
    /**
     * Apply automatic gain control for media mode.
     * Targets -12 dBFS RMS with ±1 dB/s adjustment rate.
     */
    private void applyMediaModeAGC() {
        // Calculate RMS of current frame
        float sumSquares = 0.0f;
        for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
            float sample = floatProcessed[i];
            sumSquares += sample * sample;
        }
        float rms = (float) Math.sqrt(sumSquares / AudioConfig.FRAME_SIZE_SAMPLES);
        float rms_dBFS = 20.0f * (float) Math.log10(Math.max(rms, 1e-10f));
        
        // Calculate gain adjustment needed
        float error_dB = TARGET_RMS_DBFS - rms_dBFS;
        
        // Apply gradual adjustment (±1 dB/s max)
        float frameDuration_s = AudioConfig.FRAME_SIZE_SAMPLES / (float) AudioConfig.SAMPLE_RATE;
        float maxAdjustment_dB = AGC_ADJUST_RATE * frameDuration_s;
        float adjustment_dB = Math.max(-maxAdjustment_dB, Math.min(maxAdjustment_dB, error_dB));
        
        currentAgcGain_dB += adjustment_dB;
        
        // Clamp AGC gain to reasonable range (-20 to +20 dB)
        currentAgcGain_dB = Math.max(-20.0f, Math.min(20.0f, currentAgcGain_dB));
        
        // Apply AGC gain to processed audio
        float agcLinearGain = (float) Math.pow(10.0f, currentAgcGain_dB / 20.0f);
        for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
            floatProcessed[i] *= agcLinearGain;
        }
        
        // Log occasionally
        if (framesProcessed % 100 == 0) {
            Log.d(TAG, String.format("[Media AGC] RMS=%.1f dBFS, AGC gain=%.1f dB, target=%.1f dBFS", 
                rms_dBFS, currentAgcGain_dB, TARGET_RMS_DBFS));
        }
    }
    
    /**
     * Clean up resources
     */
    private void cleanup() {
        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord", e);
            }
            audioRecord = null;
        }
        
        if (audioTrack != null) {
            try {
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioTrack", e);
            }
            audioTrack = null;
        }
        
        if (rnnoise != null) {
            try {
                rnnoise.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error destroying RNNoise", e);
            }
            rnnoise = null;
        }
    }
    
    /**
     * Check if engine is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Set waveform callback for UI updates
     */
    public void setWaveformCallback(WaveformCallback callback) {
        this.waveformCallback = callback;
    }
    
    /**
     * Enable or disable noise reduction (RNNoise processing)
     * Can be called while audio is running - takes effect on next frame
     * 
     * @param enabled true to enable RNNoise, false to bypass (passthrough)
     */
    public void setNoiseReductionEnabled(boolean enabled) {
        boolean wasEnabled = noiseReductionEnabled.getAndSet(enabled);
        if (wasEnabled != enabled) {
            Log.e(TAG, "════════════════════════════════════════════════════════");
            Log.e(TAG, "★★★ NOISE REDUCTION " + (enabled ? "ENABLED" : "DISABLED") + " ★★★");
            Log.e(TAG, "════════════════════════════════════════════════════════");
        }
    }
    
    /**
     * Get current noise reduction state
     */
    public boolean isNoiseReductionEnabled() {
        return noiseReductionEnabled.get();
    }
    
    /**
     * Set amplification gain
     * Can be called while audio is running - takes effect on next frame
     * 
     * @param gainDb Gain in decibels (0 to 100 dB)
     *               0 dB = 1.0x (no change)
     *               20 dB = 10.0x
     *               40 dB = 100.0x
     */
    public void setAmplificationDb(float gainDb) {
        // Clamp to safe range
        gainDb = Math.max(0.0f, Math.min(100.0f, gainDb));
        
        // Phase 6: Use GainStagingManager for UCL-aware gain calculation
        if (leftGainStaging != null && rightGainStaging != null) {
            leftGainStaging.setDesiredGain(gainDb);
            rightGainStaging.setDesiredGain(gainDb);
            
            Log.i(TAG, String.format("[Phase 6] Desired gain: %.1f dB → Effective gain: L=%.1f dB, R=%.1f dB",
                gainDb,
                leftGainStaging.getLeftEffectiveGainDb(),
                rightGainStaging.getRightEffectiveGainDb()));
        }
        
        // Legacy: Convert dB to linear gain for Phase 1 mode
        float gainLinear = (float) Math.pow(10.0, gainDb / 20.0);
        
        float oldGain = amplificationGain.getAndSet(gainLinear);
        if (Math.abs(oldGain - gainLinear) > 0.01f) {
            Log.i(TAG, String.format("Amplification set to %.1f dB (%.2fx linear)", gainDb, gainLinear));
        }
    }
    
    /**
     * Get current amplification gain in linear scale
     */
    public float getAmplificationGain() {
        return amplificationGain.get();
    }
    
    /**
     * Get processing statistics
     */
    public String getStats() {
        if (framesProcessed == 0) {
            return "No frames processed yet";
        }
        double avgProcessingTimeUs = totalProcessingTimeUs / (double) framesProcessed;
        double avgProcessingTimePercent = (avgProcessingTimeUs / 10000.0) * 100.0;
        return String.format("Frames: %d, Avg processing: %.1f μs (%.1f%% of frame)",
            framesProcessed, avgProcessingTimeUs, avgProcessingTimePercent);
    }
    
    /**
     * Set personalization availability status (Phase 1).
     * Indicates whether audiogram and calibration data are available.
     * 
     * @param hasAudiogram True if audiogram data exists
     * @param hasCalibration True if calibration data exists
     */
    public void setPersonalizationAvailable(boolean hasAudiogram, boolean hasCalibration) {
        boolean isAvailable = hasAudiogram && hasCalibration;
        boolean wasAvailable = personalizationAvailable.getAndSet(isAvailable);
        
        if (wasAvailable != isAvailable) {
            Log.i(TAG, "════════════════════════════════════════════════════════");
            Log.i(TAG, "[Personalization] Status changed: " + (isAvailable ? "AVAILABLE" : "NOT AVAILABLE"));
            Log.i(TAG, "  Audiogram: " + (hasAudiogram ? "✓" : "✗"));
            Log.i(TAG, "  Calibration: " + (hasCalibration ? "✓" : "✗"));
            Log.i(TAG, "════════════════════════════════════════════════════════");
        }
    }
    
    /**
     * Get personalization availability status.
     * 
     * @return True if both audiogram and calibration data are available
     */
    public boolean isPersonalizationAvailable() {
        return personalizationAvailable.get();
    }
    
    /**
     * Enable or disable speaker isolation (Focus Mode).
     * When enabled and selectedSpeakerActive is false, audio will be muted.
     * Can be called while audio is running - takes effect on next frame.
     * 
     * @param enabled true to enable speaker isolation, false to disable
     */
    public void setSpeakerIsolationEnabled(boolean enabled) {
        boolean wasEnabled = speakerIsolationEnabled.getAndSet(enabled);
        if (wasEnabled != enabled) {
            Log.i(TAG, "════════════════════════════════════════════════════════");
            Log.i(TAG, "[Focus Mode] Speaker isolation " + (enabled ? "ENABLED" : "DISABLED"));
            Log.i(TAG, "════════════════════════════════════════════════════════");
        }
    }
    
    /**
     * Set whether the selected speaker is currently active.
     * When speaker isolation is enabled and this is false, audio will be muted.
     * 
     * @param active true if selected speaker is active, false otherwise
     */
    public void setSelectedSpeakerActive(boolean active) {
        selectedSpeakerActive.set(active);
    }
    
    /**
     * Get current speaker isolation state.
     * 
     * @return True if speaker isolation is enabled
     */
    public boolean isSpeakerIsolationEnabled() {
        return speakerIsolationEnabled.get();
    }
    
    /**
     * Get whether selected speaker is currently active.
     * 
     * @return True if selected speaker is active
     */
    public boolean isSelectedSpeakerActive() {
        return selectedSpeakerActive.get();
    }
    
    // ========================================================================
    // Phase 2: Per-Ear Processing Methods
    // ========================================================================
    
    /**
     * Enable Phase 2 per-ear processing mode.
     * MUST be called before initialize().
     * 
     * @param enabled True to enable Phase 2 mode
     */
    public void setPhase2Enabled(boolean enabled) {
        if (audioRecord != null || audioTrack != null) {
            Log.e(TAG, "Cannot change phase mode after initialization!");
            return;
        }
        
        this.phase2Enabled = enabled;
        Log.i(TAG, "Phase 2 mode " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Check if Phase 2 mode is enabled.
     */
    public boolean isPhase2Enabled() {
        return phase2Enabled;
    }
    
    /**
     * Load audiogram data and configure per-ear processors using clinical prescriptions.
     * 
     * PHASE 4 UPGRADE: Now uses validated NAL-NL2 or DSL v5 fitting formulas
     * instead of simple half-gain rule.
     * 
     * @param leftEar Left ear audiogram (list of HearingTestResult)
     * @param rightEar Right ear audiogram (list of HearingTestResult)
     * @param fittingMode NAL_NL2 (clarity-focused) or DSL_V5 (comfort-focused)
     */
    public void setAudiogramData(List<HearingTestResult> leftEar, List<HearingTestResult> rightEar,
                                GainFitting.FittingMode fittingMode) {
        if (!phase2Enabled) {
            Log.w(TAG, "Phase 2 not enabled - audiogram data ignored");
            return;
        }
        
        if (leftProcessor == null || rightProcessor == null) {
            Log.w(TAG, "Processors not initialized - cannot set audiogram data");
            return;
        }
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
        Log.i(TAG, "[Phase 4] Loading audiogram data with " + fittingMode + " prescription");
        
        // Calculate per-band gains using clinical prescriptions
        // Input level: 65 dB SPL (conversational speech reference)
        float[] leftGains = GainFitting.calculateBandGains(leftEar, fittingMode, 65.0f);
        float[] rightGains = GainFitting.calculateBandGains(rightEar, fittingMode, 65.0f);
        
        // Configure processors
        leftProcessor.setBandGains(leftGains);
        rightProcessor.setBandGains(rightGains);
        
        // Log applied gains (Phase 4: Extended to 5 bands)
        Log.i(TAG, String.format("[LEFT]  Band gains: B1=%.2fx B2=%.2fx B3=%.2fx B4=%.2fx B5=%.2fx",
            leftGains[0], leftGains[1], leftGains[2], leftGains[3], leftGains[4]));
        Log.i(TAG, String.format("[RIGHT] Band gains: B1=%.2fx B2=%.2fx B3=%.2fx B4=%.2fx B5=%.2fx",
            rightGains[0], rightGains[1], rightGains[2], rightGains[3], rightGains[4]));
        
        // Convert to dB for logging
        for (int i = 0; i < 5; i++) {
            float leftDb = 20.0f * (float) Math.log10(leftGains[i] + 1e-10f);
            float rightDb = 20.0f * (float) Math.log10(rightGains[i] + 1e-10f);
            Log.i(TAG, String.format("[Band %d] Left=%.1f dB, Right=%.1f dB", i+1, leftDb, rightDb));
        }
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
        
        // Phase 5: Configure Multiband WDRC parameters based on hearing loss severity
        if (advancedDspEnabled && leftMultibandWDRC != null && rightMultibandWDRC != null) {
            configureWDRCFromAudiogram(leftEar, leftMultibandWDRC, "LEFT");
            configureWDRCFromAudiogram(rightEar, rightMultibandWDRC, "RIGHT");
        }
        
        // Mark personalization as available
        personalizationAvailable.set(true);
    }
    
    /**
     * Phase 5: Configure Multiband WDRC parameters based on hearing loss severity.
     * Maps audiogram thresholds to compression ratios (2:1 to 4:1).
     * 
     * @param audiogram Hearing test results
     * @param wdrc MultibandWDRC processor to configure
     * @param earName "LEFT" or "RIGHT" for logging
     */
    private void configureWDRCFromAudiogram(List<HearingTestResult> audiogram, 
                                           MultibandWDRC wdrc, String earName) {
        if (audiogram == null || audiogram.isEmpty()) {
            Log.w(TAG, String.format("[%s] No audiogram data - using default WDRC parameters", earName));
            return;
        }
        
        // Map frequencies to 5 bands
        // Band 0: 250-750 Hz   → Use 250, 500 Hz
        // Band 1: 750-1500 Hz  → Use 1000 Hz
        // Band 2: 1500-3000 Hz → Use 2000 Hz
        // Band 3: 3000-6000 Hz → Use 4000, 6000 Hz
        // Band 4: 6000-8000 Hz → Use 8000 Hz
        
        float[] bandThresholds = new float[5];
        
        // Extract thresholds from audiogram
        java.util.Map<Integer, Float> thresholdMap = new java.util.HashMap<>();
        for (HearingTestResult result : audiogram) {
            thresholdMap.put(result.getFrequency(), result.getThresholdDbHL());
        }
        
        // Calculate average threshold per band
        bandThresholds[0] = (thresholdMap.getOrDefault(250, 20.0f) + 
                            thresholdMap.getOrDefault(500, 20.0f)) / 2.0f;
        bandThresholds[1] = thresholdMap.getOrDefault(1000, 20.0f);
        bandThresholds[2] = thresholdMap.getOrDefault(2000, 20.0f);
        bandThresholds[3] = (thresholdMap.getOrDefault(4000, 20.0f) + 
                            thresholdMap.getOrDefault(6000, 20.0f)) / 2.0f;
        bandThresholds[4] = thresholdMap.getOrDefault(8000, 20.0f);
        
        // Configure each band based on hearing loss severity
        Log.i(TAG, String.format("[%s] Configuring Multiband WDRC from audiogram:", earName));
        for (int band = 0; band < 5; band++) {
            float threshold = bandThresholds[band];
            
            // Determine compression ratio based on hearing loss:
            // Mild (0-25 dB HL):       2:1 ratio, -35 dBFS threshold
            // Moderate (25-40 dB HL):  2.5:1 ratio, -35 dBFS threshold
            // Moderate-Severe (40-55): 3:1 ratio, -35 dBFS threshold
            // Severe (55-70):          3.5:1 ratio, -30 dBFS threshold
            // Profound (>70):          4:1 ratio, -25 dBFS threshold
            
            float ratio;
            float thresholdDbFS;
            
            if (threshold <= 25.0f) {
                ratio = 2.0f;
                thresholdDbFS = -35.0f;
            } else if (threshold <= 40.0f) {
                ratio = 2.5f;
                thresholdDbFS = -35.0f;
            } else if (threshold <= 55.0f) {
                ratio = 3.0f;
                thresholdDbFS = -35.0f;
            } else if (threshold <= 70.0f) {
                ratio = 3.5f;
                thresholdDbFS = -30.0f;
            } else {
                ratio = 4.0f;
                thresholdDbFS = -25.0f;
            }
            
            wdrc.setBandParameters(band, thresholdDbFS, ratio);
            
            Log.i(TAG, String.format("  Band %d [HL=%.1f dB]: Ratio=%.1f:1, Threshold=%.1f dBFS",
                band, threshold, ratio, thresholdDbFS));
        }
    }
    
    /**
     * Overload for backward compatibility - defaults to NAL-NL2.
     */
    public void setAudiogramData(List<HearingTestResult> leftEar, List<HearingTestResult> rightEar) {
        setAudiogramData(leftEar, rightEar, GainFitting.FittingMode.NAL_NL2);
    }
    
    /**
     * Enable or disable WDRC compression in per-ear processors (Phase 3).
     * 
     * @param enabled True to enable WDRC
     */
    public void setWDRCEnabled(boolean enabled) {
        if (!phase2Enabled || leftProcessor == null || rightProcessor == null) {
            Log.w(TAG, "Phase 2 not active - WDRC setting ignored");
            return;
        }
        
        leftProcessor.setWDRCEnabled(enabled);
        rightProcessor.setWDRCEnabled(enabled);
        
        Log.i(TAG, "[Phase 3] WDRC compression " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Enable or disable UCL-based per-band limiting (Phase 3).
     * 
     * @param enabled True to enable UCL limiting
     */
    public void setUCLLimitingEnabled(boolean enabled) {
        if (!phase2Enabled || leftProcessor == null || rightProcessor == null) {
            Log.w(TAG, "Phase 2 not active - UCL limiting setting ignored");
            return;
        }
        
        leftProcessor.setUCLLimitingEnabled(enabled);
        rightProcessor.setUCLLimitingEnabled(enabled);
        
        Log.i(TAG, "[Phase 3] UCL limiting " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Enable or disable adaptive feedback cancellation (Phase 3).
     * 
     * @param enabled True to enable feedback cancellation
     */
    public void setFeedbackCancellerEnabled(boolean enabled) {
        this.feedbackCancellerEnabled = enabled;
        Log.i(TAG, "[Phase 3] Feedback canceller " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * PHASE 4: Load calibration data (MCL/UCL) and configure WDRC + limiters.
     * 
     * MCL (Most Comfortable Level): Used to set WDRC compression thresholds
     * UCL (Uncomfortable Loudness Level): Used for per-band limiting (UCL - 5 dB)
     * 
     * @param leftCalibration List of calibration entries for left ear
     * @param rightCalibration List of calibration entries for right ear
     */
    public void setCalibrationData(java.util.Map<Integer, Float> leftMCL,
                                   java.util.Map<Integer, Float> leftUCL,
                                   java.util.Map<Integer, Float> rightMCL,
                                   java.util.Map<Integer, Float> rightUCL) {
        if (!phase2Enabled || leftProcessor == null || rightProcessor == null) {
            Log.w(TAG, "Phase 2 not active - calibration data ignored");
            return;
        }
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
        Log.i(TAG, "[Phase 4] Loading calibration data (MCL/UCL)");
        
        // Default fallback values if data missing
        final float DEFAULT_MCL_DB_HL = 65.0f;  // Conversational speech level
        final float DEFAULT_UCL_DB_HL = 95.0f;  // Conservative UCL
        
        // Map frequencies to 5 bands (Phase 4: Extended to 8 kHz)
        // Band 0: 250-750 Hz   → Use 250, 500 Hz
        // Band 1: 750-1500 Hz  → Use 1000 Hz
        // Band 2: 1500-3000 Hz → Use 2000 Hz
        // Band 3: 3000-6000 Hz → Use 4000, 6000 Hz
        // Band 4: 6000-8000 Hz → Use 8000 Hz (NEW - Phase 4)
        
        float[] leftUCLBands = new float[5];
        float[] rightUCLBands = new float[5];
        
        // Band 0: Average 250 & 500 Hz
        float leftUCL0 = (leftUCL.getOrDefault(250, DEFAULT_UCL_DB_HL) + 
                         leftUCL.getOrDefault(500, DEFAULT_UCL_DB_HL)) / 2.0f;
        float rightUCL0 = (rightUCL.getOrDefault(250, DEFAULT_UCL_DB_HL) + 
                          rightUCL.getOrDefault(500, DEFAULT_UCL_DB_HL)) / 2.0f;
        
        // Band 1: 1000 Hz
        float leftUCL1 = leftUCL.getOrDefault(1000, DEFAULT_UCL_DB_HL);
        float rightUCL1 = rightUCL.getOrDefault(1000, DEFAULT_UCL_DB_HL);
        
        // Band 2: 2000 Hz
        float leftUCL2 = leftUCL.getOrDefault(2000, DEFAULT_UCL_DB_HL);
        float rightUCL2 = rightUCL.getOrDefault(2000, DEFAULT_UCL_DB_HL);
        
        // Band 3: Average 4000 & 6000 Hz
        float leftUCL3 = (leftUCL.getOrDefault(4000, DEFAULT_UCL_DB_HL) + 
                         leftUCL.getOrDefault(6000, DEFAULT_UCL_DB_HL)) / 2.0f;
        float rightUCL3 = (rightUCL.getOrDefault(4000, DEFAULT_UCL_DB_HL) + 
                          rightUCL.getOrDefault(6000, DEFAULT_UCL_DB_HL)) / 2.0f;
        
        // Band 4: 8000 Hz - NEW in Phase 4
        float leftUCL4 = leftUCL.getOrDefault(8000, DEFAULT_UCL_DB_HL);
        float rightUCL4 = rightUCL.getOrDefault(8000, DEFAULT_UCL_DB_HL);
        
        // Convert dB HL to dBFS (approximate: dBFS = dB HL - 80 dB SPL reference)
        leftUCLBands[0] = leftUCL0 - 80.0f;
        leftUCLBands[1] = leftUCL1 - 80.0f;
        leftUCLBands[2] = leftUCL2 - 80.0f;
        leftUCLBands[3] = leftUCL3 - 80.0f;
        leftUCLBands[4] = leftUCL4 - 80.0f;
        
        rightUCLBands[0] = rightUCL0 - 80.0f;
        rightUCLBands[1] = rightUCL1 - 80.0f;
        rightUCLBands[2] = rightUCL2 - 80.0f;
        rightUCLBands[3] = rightUCL3 - 80.0f;
        rightUCLBands[4] = rightUCL4 - 80.0f;
        
        // Apply UCL limits (UCL - 5 dB safety margin)
        leftProcessor.setBandUCLLimits(leftUCLBands);
        rightProcessor.setBandUCLLimits(rightUCLBands);
        
        // Enable UCL limiting
        leftProcessor.setUCLLimitingEnabled(true);
        rightProcessor.setUCLLimitingEnabled(true);
        
        // Phase 5+6: Configure Dual-Stage Limiters (fixed thresholds, no UCL config needed)
        if (advancedDspEnabled && leftLimiter != null && rightLimiter != null) {
            // Use the most conservative (lowest) UCL across all bands
            float leftMinUCL = Math.min(Math.min(Math.min(Math.min(leftUCL0, leftUCL1), leftUCL2), leftUCL3), leftUCL4);
            float rightMinUCL = Math.min(Math.min(Math.min(Math.min(rightUCL0, rightUCL1), rightUCL2), rightUCL3), rightUCL4);
            
            // Note: DualStageLimiter has fixed thresholds (-3 dBFS and -1 dBFS)
            // UCL compliance is handled by GainStagingManager BEFORE limiting
            
            Log.i(TAG, String.format("[Phase 5+6] Dual-Stage Limiter initialized (fixed thresholds)"));
            Log.i(TAG, String.format("  UCL reference: LEFT=%.1f dB HL, RIGHT=%.1f dB HL", leftMinUCL, rightMinUCL));
            
            // Phase 6: Configure Gain Staging Managers with UCL limits (in dB SPL)
            if (leftGainStaging != null && rightGainStaging != null) {
                leftGainStaging.setUCLLimits(leftMinUCL, leftMinUCL);  // Use global UCL
                rightGainStaging.setUCLLimits(rightMinUCL, rightMinUCL);
                
                Log.i(TAG, String.format("[Phase 6] GainStagingManager UCL: LEFT=%.1f dB SPL, RIGHT=%.1f dB SPL",
                    leftMinUCL, rightMinUCL));
            }
        }
        
        Log.i(TAG, String.format("[LEFT]  UCL limits: B0=%.1f B1=%.1f B2=%.1f B3=%.1f B4=%.1f dBFS",
            leftUCLBands[0], leftUCLBands[1], leftUCLBands[2], leftUCLBands[3], leftUCLBands[4]));
        Log.i(TAG, String.format("[RIGHT] UCL limits: B0=%.1f B1=%.1f B2=%.1f B3=%.1f B4=%.1f dBFS",
            rightUCLBands[0], rightUCLBands[1], rightUCLBands[2], rightUCLBands[3], rightUCLBands[4]));
        
        // TODO: Use MCL data to adjust WDRC thresholds per band
        // For now, WDRC uses fixed -25 dBFS threshold
        
        Log.i(TAG, "[Phase 4] Calibration data loaded and applied");
        Log.i(TAG, "════════════════════════════════════════════════════════");
    }
    
    /**
     * Enable or disable scene analysis (Phase 3).
     * 
     * @param enabled True to enable scene analysis
     */
    public void setSceneAnalysisEnabled(boolean enabled) {
        this.sceneAnalysisEnabled = enabled;
        Log.i(TAG, "[Phase 3] Scene analysis " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Get current scene (Phase 3).
     * 
     * @return Current detected scene
     */
    public SceneAnalyzer.Scene getCurrentScene() {
        return currentScene;
    }
    
    /**
     * Phase 5: Enable or disable advanced DSP (Multiband WDRC + Look-Ahead Limiter).
     * 
     * @param enabled True to enable advanced DSP
     */
    public void setAdvancedDspEnabled(boolean enabled) {
        this.advancedDspEnabled = enabled;
        Log.i(TAG, "[Phase 5] Advanced DSP (Multiband WDRC + Look-Ahead Limiter) " + 
            (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Phase 5: Get Multiband WDRC statistics.
     * 
     * @return String containing compression statistics for both channels
     */
    public String getMultibandWDRCStats() {
        if (!advancedDspEnabled || leftMultibandWDRC == null || rightMultibandWDRC == null) {
            return "Advanced DSP not enabled";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("   MULTIBAND WDRC STATISTICS\n");
        sb.append("═══════════════════════════════════════\n\n");
        
        sb.append("LEFT CHANNEL:\n");
        for (int band = 0; band < 5; band++) {
            MultibandWDRC.CompressionStats stats = leftMultibandWDRC.getBandStats(band);
            if (stats != null) {
                sb.append(String.format("  Band %d [%.0f-%.0f Hz]:\n", 
                    band, stats.lowFreq, stats.highFreq));
                sb.append(String.format("    Max GR: %.1f dB\n", stats.maxGainReductionDb));
                sb.append(String.format("    Active: %.1f%%\n", stats.getActivationPercent()));
            }
        }
        
        sb.append("\nRIGHT CHANNEL:\n");
        for (int band = 0; band < 5; band++) {
            MultibandWDRC.CompressionStats stats = rightMultibandWDRC.getBandStats(band);
            if (stats != null) {
                sb.append(String.format("  Band %d [%.0f-%.0f Hz]:\n", 
                    band, stats.lowFreq, stats.highFreq));
                sb.append(String.format("    Max GR: %.1f dB\n", stats.maxGainReductionDb));
                sb.append(String.format("    Active: %.1f%%\n", stats.getActivationPercent()));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Phase 5+6: Get Dual-Stage Limiter statistics.
     * 
     * @return String containing limiter statistics for both channels
     */
    public String getLimiterStats() {
        if (!advancedDspEnabled || leftLimiter == null || rightLimiter == null) {
            return "Advanced DSP not enabled";
        }
        
        // Phase 6: DualStageLimiter returns string statistics directly
        String leftStats = leftLimiter.getStatistics();
        String rightStats = rightLimiter.getStatistics();
        
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("   DUAL-STAGE LIMITER STATISTICS\n");
        sb.append("═══════════════════════════════════════\n\n");
        
        sb.append("LEFT CHANNEL:\n");
        sb.append(leftStats);
        sb.append("\n\nRIGHT CHANNEL:\n");
        sb.append(rightStats);
        
        sb.append("\n\nTotal Latency: 10 ms (look-ahead window)\n");
        
        return sb.toString();
    }
    
    /**
     * Phase 5+6: Reset DSP statistics.
     */
    public void resetDspStats() {
        if (advancedDspEnabled && leftMultibandWDRC != null && rightMultibandWDRC != null) {
            leftMultibandWDRC.resetStats();
            rightMultibandWDRC.resetStats();
            if (leftLimiter != null) leftLimiter.reset();
            if (rightLimiter != null) rightLimiter.reset();
            if (leftGainStaging != null) leftGainStaging.reset();
            if (rightGainStaging != null) rightGainStaging.reset();
            Log.i(TAG, "[Phase 5+6] DSP statistics reset");
        }
    }
    
    /**
     * Get feedback canceller statistics (Phase 3).
     * 
     * @return Feedback stats or null if disabled
     */
    public FeedbackCanceller.FeedbackStats getFeedbackStats() {
        if (feedbackCanceller != null) {
            return feedbackCanceller.getStats();
        }
        return null;
    }
    
    /**
     * Get limiter activation counts (Phase 3).
     * 
     * @return Array [leftBand0, leftBand1, leftBand2, leftBand3, leftGlobal, rightBand0, rightBand1, rightBand2, rightBand3, rightGlobal]
     */
    public int[] getLimiterActivations() {
        if (!phase2Enabled || leftProcessor == null || rightProcessor == null) {
            return new int[10];
        }
        
        int[] leftActivations = leftProcessor.getLimiterActivations();
        int[] rightActivations = rightProcessor.getLimiterActivations();
        
        int[] combined = new int[10];
        System.arraycopy(leftActivations, 0, combined, 0, 5);
        System.arraycopy(rightActivations, 0, combined, 5, 5);
        
        return combined;
    }
    
    /**
     * Reset Phase 3 statistics.
     */
    public void resetPhase3Stats() {
        if (leftProcessor != null) {
            leftProcessor.resetLimiterActivations();
            leftProcessor.resetCompressionStats();
        }
        if (rightProcessor != null) {
            rightProcessor.resetLimiterActivations();
            rightProcessor.resetCompressionStats();
        }
        if (feedbackCanceller != null) {
            feedbackCanceller.reset();
        }
        if (sceneAnalyzer != null) {
            sceneAnalyzer.reset();
        }
        Log.i(TAG, "[Phase 3] Statistics reset");
    }
    
    /**
     * Enable or disable compression in per-ear processors (legacy Phase 2 method).
     * 
     * @param enabled True to enable compression
     * @deprecated Use setWDRCEnabled() for Phase 3
     */
    @Deprecated
    public void setCompressionEnabled(boolean enabled) {
        if (!phase2Enabled || leftProcessor == null || rightProcessor == null) {
            Log.w(TAG, "Phase 2 not active - compression setting ignored");
            return;
        }
        
        // Map to WDRC for Phase 3
        setWDRCEnabled(enabled);
    }
    
    /**
     * Reset per-ear processor states.
     * Call when starting/stopping to avoid click artifacts.
     */
    public void resetProcessors() {
        if (leftProcessor != null) {
            leftProcessor.reset();
        }
        if (rightProcessor != null) {
            rightProcessor.reset();
        }
        if (feedbackCanceller != null) {
            feedbackCanceller.reset();
        }
        if (sceneAnalyzer != null) {
            sceneAnalyzer.reset();
        }
        Log.d(TAG, "[Phase 2/3] All processors reset");
    }
    
    /**
     * Get current RMS levels for both ears (dBFS).
     * 
     * @return Array [leftRMS, rightRMS] in dBFS
     */
    public float[] getCurrentRMS() {
        if (!phase2Enabled || leftProcessor == null || rightProcessor == null) {
            return new float[]{-96.0f, -96.0f}; // Silence
        }
        
        return new float[]{
            leftProcessor.getRMSAndReset(),
            rightProcessor.getRMSAndReset()
        };
    }
    
    /**
     * PHASE 4: Get audio quality metrics snapshot.
     * 
     * @return Metrics snapshot or null if not available
     */
    public AudioQualityMetrics.MetricsSnapshot getQualityMetrics() {
        if (qualityMetrics != null) {
            return qualityMetrics.getSnapshot();
        }
        return null;
    }
    
    /**
     * PHASE 4: Reset quality metrics counters.
     */
    public void resetQualityMetrics() {
        if (qualityMetrics != null) {
            qualityMetrics.reset();
            Log.i(TAG, "[Phase 4] Quality metrics reset");
        }
    }
    
    /**
     * Calculate RMS of float audio buffer (normalized ±1.0)
     * @param buffer Audio samples
     * @param length Number of samples
     * @return RMS value (0.0 to ~1.0)
     */
    private float calculateRMS(float[] buffer, int length) {
        float sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        return (float) Math.sqrt(sum / length);
    }
}
