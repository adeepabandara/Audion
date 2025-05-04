package com.example.audion.diarization;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FastClusteringConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for Speaker Diarization functionality.
 * Provides easy access to the diarization engine and handles initialization.
 * Converted from the Kotlin SpeakerDiarizationObject in the Sherpa-Onnx project.
 */
public class SpeakerDiarizationManager {
    private static final String TAG = "SpeakerDiarizationMgr";
    
    // Model file names in assets
    private static final String SEGMENTATION_MODEL = "segmentation.onnx";
    private static final String EMBEDDING_MODEL = "embedding.onnx";
    
    private static OfflineSpeakerDiarization diarizationEngine;
    
    // Embedding extractor for speaker identification
    private static SpeakerEmbeddingExtractor embeddingExtractor;
    
    // Default settings for clustering
    private static int numSpeakers = -1; // -1 means auto-detect
    private static float maxSpeakerCount = 8;
    private static float minSpeechDuration = 0.5f;
    private static float maxSpeechDuration = 15.0f;
    private static float threshold = 0.5f;
    
    private SpeakerDiarizationManager() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Initialize the speaker diarization engine with the given context.
     * Safe to call multiple times - will only initialize once.
     * 
     * @param context Android context for accessing assets
     */
    public static synchronized void initialize(Context context) throws IOException {
        if (diarizationEngine != null) {
            return;
        }
        
        Log.i(TAG, "Initializing Sherpa-Onnx speaker diarization");
        
        // Create segmentation configuration
        OfflineSpeakerSegmentationPyannoteModelConfig pyannoteConfig = 
            new OfflineSpeakerSegmentationPyannoteModelConfig(SEGMENTATION_MODEL);
        
        OfflineSpeakerSegmentationModelConfig segmentationConfig = 
            new OfflineSpeakerSegmentationModelConfig();
        segmentationConfig.setPyannote(pyannoteConfig);
        segmentationConfig.setDebug(true);
        
        // Create embedding configuration
        SpeakerEmbeddingExtractorConfig embeddingConfig = 
            new SpeakerEmbeddingExtractorConfig();
        embeddingConfig.setModel(EMBEDDING_MODEL);
        embeddingConfig.setDebug(true);
        embeddingConfig.setNumThreads(2);
        
        // Create clustering configuration
        FastClusteringConfig clusteringConfig = new FastClusteringConfig();
        clusteringConfig.setNumClusters(-1);  // Auto-determine number of clusters
        clusteringConfig.setThreshold(0.3f);  // Lower threshold to be more lenient
        
        // Create main configuration
        OfflineSpeakerDiarizationConfig config = new OfflineSpeakerDiarizationConfig();
        config.setSegmentation(segmentationConfig);
        config.setEmbedding(embeddingConfig);
        config.setClustering(clusteringConfig);
        config.setMinDurationOn(0.1f);  // Lower minimum duration for speech segments
        config.setMinDurationOff(0.3f); // Lower minimum duration for silence
        
        // Set additional parameters using existing methods - modified for compatibility
        // Instead of direct setters, we'll set these via appropriate methods if available
        // or skip them if they don't exist in the current API
        
        try {
            // Initialize engine
            diarizationEngine = new OfflineSpeakerDiarization(context.getAssets(), config);
            Log.i(TAG, "Speaker diarization initialized successfully with config: " + config);
            
            // Initialize embedding extractor for cross-chunk speaker identification
            embeddingExtractor = new SpeakerEmbeddingExtractor(context.getAssets(), embeddingConfig);
            Log.i(TAG, "Speaker embedding extractor initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize speaker diarization", e);
            throw new IOException("Failed to initialize speaker diarization: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get the diarization engine instance.
     * Must call initialize() first.
     * 
     * @return The diarization engine
     * @throws IllegalStateException if initialize() was not called
     */
    public static OfflineSpeakerDiarization getDiarizationEngine() {
        if (diarizationEngine == null) {
            throw new IllegalStateException("Speaker diarization not initialized. Call initialize() first.");
        }
        return diarizationEngine;
    }
    
    /**
     * Check if the diarization engine is initialized.
     * 
     * @return true if diarization is initialized, false otherwise
     */
    public static boolean isInitialized() {
        return diarizationEngine != null && embeddingExtractor != null;
    }
    
    /**
     * Process audio samples and perform speaker diarization.
     * 
     * @param samples Array of audio samples (mono, float)
     * @return Array of speaker segments
     * @throws IllegalStateException if initialize() was not called
     */
    public static OfflineSpeakerDiarizationSegment[] processSpeakerDiarization(float[] samples) {
        return getDiarizationEngine().process(samples);
    }
    
    /**
     * Process audio samples with progress callback.
     * 
     * @param samples Array of audio samples (mono, float)
     * @param callback Callback function to report progress
     * @return Array of speaker segments
     * @throws IllegalStateException if initialize() was not called
     */
    public static OfflineSpeakerDiarizationSegment[] processSpeakerDiarization(
            float[] samples,
            OfflineSpeakerDiarization.ProcessingCallback callback) {
        return getDiarizationEngine().processWithCallback(samples, callback, 0);
    }
    
    /**
     * Get the sample rate required by the model.
     * 
     * @return Sample rate in Hz
     * @throws IllegalStateException if initialize() was not called
     */
    public static int getSampleRate() {
        return getDiarizationEngine().getSampleRate();
    }
    
    /**
     * Update clustering configuration.
     * 
     * @param numClusters Number of clusters (-1 for auto-determine)
     * @param threshold Clustering threshold
     * @throws IllegalStateException if initialize() was not called
     */
    public static void updateClusteringConfig(int numClusters, float threshold) {
        FastClusteringConfig clusteringConfig = new FastClusteringConfig(numClusters, threshold);
        OfflineSpeakerDiarizationConfig config = new OfflineSpeakerDiarizationConfig();
        config.setClustering(clusteringConfig);
        getDiarizationEngine().setConfig(config);
    }
    
    /**
     * Extract speaker embedding from audio samples.
     * This is used for identifying speakers across different audio chunks.
     * 
     * @param samples Audio samples for a specific speaker
     * @return Float array containing the speaker's embedding vector
     */
    public static float[] extractSpeakerEmbedding(float[] samples) {
        if (embeddingExtractor == null) {
            Log.e(TAG, "Embedding extractor not initialized");
            return null;
        }
        
        if (samples == null || samples.length < 1600) { // At least 0.1s at 16kHz
            Log.e(TAG, "Not enough samples to extract embedding");
            return null;
        }
        
        try {
            return embeddingExtractor.extract(samples);
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract speaker embedding: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Clean up resources.
     */
    public static synchronized void release() {
        if (diarizationEngine != null) {
            try {
                diarizationEngine.release();
            } catch (Exception e) {
                Log.e(TAG, "Error closing diarization engine", e);
            }
            diarizationEngine = null;
        }
        
        if (embeddingExtractor != null) {
            embeddingExtractor.release();
            embeddingExtractor = null;
        }
    }
} 