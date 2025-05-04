package com.k2fsa.sherpa.onnx;

import android.content.res.AssetManager;
import android.util.Log;

/**
 * Singleton class for speaker recognition functionality.
 * Provides central access to embedding extraction and speaker management.
 */
public class SpeakerRecognition {
    private static final String TAG = "SpeakerRecognition";
    
    // Please download the model file from
    // https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
    // and put it inside the assets directory.
    // Please don't put it in a subdirectory of assets
    private static final String MODEL_NAME = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx";
    
    private static SpeakerEmbeddingExtractor _extractor = null;
    private static SpeakerEmbeddingManager _manager = null;
    
    private SpeakerRecognition() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Get the speaker embedding extractor instance.
     * 
     * @return The SpeakerEmbeddingExtractor instance
     * @throws IllegalStateException if not initialized
     */
    public static SpeakerEmbeddingExtractor getExtractor() {
        if (_extractor == null) {
            throw new IllegalStateException("Speaker embedding extractor not initialized. Call initExtractor() first.");
        }
        return _extractor;
    }
    
    /**
     * Get the speaker embedding manager instance.
     * 
     * @return The SpeakerEmbeddingManager instance
     * @throws IllegalStateException if not initialized
     */
    public static SpeakerEmbeddingManager getManager() {
        if (_manager == null) {
            throw new IllegalStateException("Speaker embedding manager not initialized. Call initExtractor() first.");
        }
        return _manager;
    }
    
    /**
     * Initialize the extractor and manager.
     * 
     * @param assetManager Android asset manager, or null to load from file
     */
    public static synchronized void initExtractor(AssetManager assetManager) {
        if (_extractor != null) {
            return;
        }
        
        Log.i(TAG, "Initializing speaker embedding extractor");
        
        SpeakerEmbeddingExtractorConfig config = new SpeakerEmbeddingExtractorConfig(
                MODEL_NAME,
                2,
                false,
                "cpu"
        );
        
        if (assetManager != null) {
            _extractor = new SpeakerEmbeddingExtractor(assetManager, config);
        } else {
            _extractor = new SpeakerEmbeddingExtractor(config);
        }
        
        _manager = new SpeakerEmbeddingManager(_extractor.dim());
    }
    
    /**
     * Release resources.
     */
    public static synchronized void release() {
        if (_extractor != null) {
            _extractor.release();
            _extractor = null;
        }
        
        if (_manager != null) {
            _manager.release();
            _manager = null;
        }
    }
} 