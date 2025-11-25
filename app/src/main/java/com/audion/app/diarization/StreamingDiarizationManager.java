package com.audion.app.diarization;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization.ProcessingCallback;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Manager for streaming speaker diarization.
 * Buffers audio frames and processes them for speaker identification.
 */
public class StreamingDiarizationManager {
    private static final String TAG = "DiarizationManager";
    
    private Context context;
    private DiarizationBufferManager bufferManager;
    private boolean isInitialized = false;
    private boolean isProcessing = false;
    private ExecutorService executorService;
    private Handler mainHandler;
    
    // Diarization results
    private OfflineSpeakerDiarizationSegment[] currentSegments = new OfflineSpeakerDiarizationSegment[0];
    private List<SpeakerInfo> detectedSpeakers = new ArrayList<>();
    
    // Callbacks
    private DiarizationListener listener;
    
    /**
     * Interface for diarization event callbacks.
     */
    public interface DiarizationListener {
        void onSpeakersDetected(List<SpeakerInfo> speakers);
        void onDiarizationProgress(float progress);
        void onDiarizationError(String message);
    }
    
    /**
     * Class representing detected speaker information.
     */
    public static class SpeakerInfo {
        private final int id;
        private final float duration;
        
        public SpeakerInfo(int id, float duration) {
            this.id = id;
            this.duration = duration;
        }
        
        public int getId() {
            return id;
        }
        
        public float getDuration() {
            return duration;
        }
        
        @Override
        public String toString() {
            return "Speaker " + id + " (" + String.format("%.1f", duration) + "s)";
        }
    }
    
    /**
     * Create a new StreamingDiarizationManager.
     * 
     * @param context Android context for accessing resources
     */
    public StreamingDiarizationManager(Context context) {
        this.context = context;
        this.bufferManager = new DiarizationBufferManager();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Set a listener for diarization events.
     * 
     * @param listener The listener to set
     */
    public void setListener(DiarizationListener listener) {
        this.listener = listener;
    }
    
    /**
     * Initialize the diarization engine.
     * 
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize() {
        if (!isInitialized) {
            try {
                SpeakerDiarizationManager.initialize(context);
                isInitialized = true;
                Log.i(TAG, "Diarization initialized successfully");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error initializing diarization", e);
                if (listener != null) {
                    final String errorMsg = e.getMessage();
                    mainHandler.post(() -> listener.onDiarizationError("Initialization error: " + errorMsg));
                }
                return false;
            }
        }
        return true;
    }
    
    /**
     * Process a frame of audio for diarization.
     * 
     * @param frame Audio frame (array of float samples)
     */
    public void processFrame(float[] frame) {
        if (!isInitialized) {
            Log.e(TAG, "Diarization not initialized");
            return;
        }
        
        Log.d(TAG, "Processing frame of size: " + frame.length);
        // Add frame to buffer
        bufferManager.addFrame(frame);
        
        // Process if buffer is filled and not already processing
        if (bufferManager.isBufferFilled() && !isProcessing) {
            Log.d(TAG, "Buffer filled, starting diarization process");
            processDiarizationBuffer();
        } else {
            Log.d(TAG, "Buffer not ready - filled: " + bufferManager.isBufferFilled() + 
                    ", processing: " + isProcessing + 
                    ", buffer fill: " + getBufferFillPercentage());
        }
    }
    
    /**
     * Process the current audio buffer for diarization.
     * This is executed on a background thread.
     */
    private void processDiarizationBuffer() {
        if (isProcessing) {
            Log.w(TAG, "Already processing diarization buffer");
            return;
        }
        
        isProcessing = true;
        Log.d(TAG, "Starting diarization process");
        
        executorService.execute(() -> {
            try {
                // Get the audio buffer
                float[] audioBuffer = bufferManager.getBuffer();
                if (audioBuffer == null || audioBuffer.length == 0) {
                    Log.e(TAG, "Empty audio buffer");
                    isProcessing = false;
                    return;
                }
                
                Log.d(TAG, "Processing buffer of size: " + audioBuffer.length);
                
                // Process with callback
                currentSegments = SpeakerDiarizationManager.processSpeakerDiarization(
                    audioBuffer,
                    new OfflineSpeakerDiarization.ProcessingCallback() {
                        @Override
                        public Integer invoke(int numProcessedChunks, int numTotalChunks, long arg) {
                            float progress = 100.0f * numProcessedChunks / numTotalChunks;
                            Log.d(TAG, "Diarization progress: " + progress + "%");
                            return 0;
                        }
                    }
                );
                
                // Analyze speakers
                analyzeSpeakers();
                
                // Shift buffer for next window
                bufferManager.shiftBuffer();
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing diarization buffer", e);
                if (listener != null) {
                    final String errorMsg = e.getMessage();
                    mainHandler.post(() -> listener.onDiarizationError("Processing error: " + errorMsg));
                }
            } finally {
                isProcessing = false;
            }
        });
    }
    
    /**
     * Analyze the diarization segments and update speaker information.
     */
    private void analyzeSpeakers() {
        Log.d(TAG, "Analyzing speakers from segments");
        if (currentSegments == null || currentSegments.length == 0) {
            Log.w(TAG, "No segments to analyze");
            detectedSpeakers.clear();
            return;
        }
        
        // Calculate total duration for each speaker
        Map<Integer, Float> speakerDurations = new HashMap<>();
        
        for (OfflineSpeakerDiarizationSegment segment : currentSegments) {
            int speakerId = segment.getSpeakerId();
            float duration = segment.getEndTime() - segment.getStartTime();
            Log.d(TAG, "Analyzing segment - speaker: " + speakerId + 
                    ", duration: " + duration + 
                    ", confidence: " + segment.getConfidence());
            
            speakerDurations.put(
                    speakerId, 
                    speakerDurations.getOrDefault(speakerId, 0f) + duration
            );
        }
        
        // Create speaker info objects
        List<SpeakerInfo> speakers = speakerDurations.entrySet().stream()
                .map(entry -> new SpeakerInfo(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Float.compare(b.getDuration(), a.getDuration())) // Sort by duration (descending)
                .collect(Collectors.toList());
        
        detectedSpeakers = speakers;
        
        // Notify listener
        if (listener != null) {
            final List<SpeakerInfo> speakersCopy = new ArrayList<>(speakers);
            mainHandler.post(() -> listener.onSpeakersDetected(speakersCopy));
        }
    }
    
    /**
     * Get the list of currently detected speakers.
     * 
     * @return List of SpeakerInfo objects
     */
    public List<SpeakerInfo> getDetectedSpeakers() {
        return new ArrayList<>(detectedSpeakers);
    }
    
    /**
     * Get the current diarization segments.
     * 
     * @return Array of diarization segments
     */
    public OfflineSpeakerDiarizationSegment[] getCurrentSegments() {
        return currentSegments;
    }
    
    /**
     * Get the number of detected speakers.
     * 
     * @return Number of speakers
     */
    public int getSpeakerCount() {
        return detectedSpeakers.size();
    }
    
    /**
     * Get buffer fill percentage (0.0 - 1.0).
     * 
     * @return Buffer fill percentage
     */
    public float getBufferFillPercentage() {
        float bufferSize = bufferManager.getBufferSizeSeconds() * SpeakerDiarizationManager.getSampleRate();
        return Math.min(1.0f, bufferManager.getTotalSamples() / bufferSize);
    }
    
    /**
     * Release resources associated with diarization.
     */
    public void release() {
        if (isInitialized) {
            executorService.shutdown();
            SpeakerDiarizationManager.release();
            isInitialized = false;
        }
    }
} 