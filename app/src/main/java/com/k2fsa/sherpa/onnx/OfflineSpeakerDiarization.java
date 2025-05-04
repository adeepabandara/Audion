package com.k2fsa.sherpa.onnx;

import android.content.res.AssetManager;
import android.util.Log;

/**
 * Main class for performing offline speaker diarization.
 * Java version of the original Kotlin class.
 */
public class OfflineSpeakerDiarization {
    private static final String TAG = "SpeakerDiarization";
    private long ptr;
    private final OfflineSpeakerDiarizationConfig config;
    
    static {
        System.loadLibrary("sherpa-onnx-jni");
    }
    
    /**
     * Create a new instance of the speaker diarization engine.
     * 
     * @param assetManager Asset manager to load models from assets folder, or null to load from file system
     * @param config Configuration for the diarization engine
     */
    public OfflineSpeakerDiarization(AssetManager assetManager, OfflineSpeakerDiarizationConfig config) {
        this.config = config;
        Log.d(TAG, "Initializing diarization with config: " + config);
        if (assetManager != null) {
            ptr = newFromAsset(assetManager, config);
            Log.d(TAG, "Created diarization instance from assets");
        } else {
            ptr = newFromFile(config);
            Log.d(TAG, "Created diarization instance from file system");
        }
        if (ptr == 0L) {
            Log.e(TAG, "Failed to create diarization instance");
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        if (ptr != 0L) {
            delete(ptr);
            ptr = 0;
        }
        super.finalize();
    }
    
    /**
     * Release native resources.
     * Should be called when the instance is no longer needed.
     */
    public void release() {
        try {
            finalize();
        } catch (Throwable throwable) {
            Log.e(TAG, "Error releasing resources", throwable);
        }
    }
    
    /**
     * Update the configuration.
     * Only clustering configuration is used. All other fields are ignored.
     * 
     * @param config The new configuration
     */
    public void setConfig(OfflineSpeakerDiarizationConfig config) {
        Log.d(TAG, "Updating config: " + config);
        setConfig(ptr, config);
    }
    
    /**
     * Get the sample rate used by the model.
     * 
     * @return Sample rate in Hz
     */
    public int getSampleRate() {
        return getSampleRate(ptr);
    }
    
    /**
     * Process audio samples and perform speaker diarization.
     * 
     * @param samples Array of audio samples (mono, float)
     * @return Array of speaker segments
     */
    public OfflineSpeakerDiarizationSegment[] process(float[] samples) {
        Log.d(TAG, "Processing " + samples.length + " samples");
        OfflineSpeakerDiarizationSegment[] segments = process(ptr, samples);
        if (segments != null) {
            Log.d(TAG, "Found " + segments.length + " speaker segments");
            for (int i = 0; i < segments.length; i++) {
                Log.d(TAG, "Segment " + i + ": " + segments[i]);
            }
        } else {
            Log.e(TAG, "Failed to process audio samples");
        }
        return segments;
    }
    
    /**
     * Process audio samples with progress callback.
     * 
     * @param samples Array of audio samples (mono, float)
     * @param callback Callback function to report progress
     * @param arg User data passed to callback
     * @return Array of speaker segments
     */
    public OfflineSpeakerDiarizationSegment[] processWithCallback(
            float[] samples,
            ProcessingCallback callback,
            long arg) {
        Log.d(TAG, "Processing with callback: " + samples.length + " samples");
        OfflineSpeakerDiarizationSegment[] segments = processWithCallback(ptr, samples, callback, arg);
        if (segments != null) {
            Log.d(TAG, "Found " + segments.length + " speaker segments with callback");
        } else {
            Log.e(TAG, "Failed to process audio samples with callback");
        }
        return segments;
    }
    
    /**
     * Interface for processing progress callbacks.
     */
    public interface ProcessingCallback {
        /**
         * Called during processing to report progress.
         * 
         * @param numProcessedChunks Number of chunks processed so far
         * @param numTotalChunks Total number of chunks to process
         * @param arg User data passed from processWithCallback
         * @return Non-zero to cancel processing, zero to continue
         */
        Integer invoke(int numProcessedChunks, int numTotalChunks, long arg);
    }
    
    // Native methods
    private native void delete(long ptr);
    
    private native long newFromAsset(
            AssetManager assetManager,
            OfflineSpeakerDiarizationConfig config);
    
    private native long newFromFile(
            OfflineSpeakerDiarizationConfig config);
    
    private native void setConfig(long ptr, OfflineSpeakerDiarizationConfig config);
    
    private native int getSampleRate(long ptr);
    
    private native OfflineSpeakerDiarizationSegment[] process(
            long ptr,
            float[] samples);
    
    private native OfflineSpeakerDiarizationSegment[] processWithCallback(
            long ptr,
            float[] samples,
            ProcessingCallback callback,
            long arg);
} 