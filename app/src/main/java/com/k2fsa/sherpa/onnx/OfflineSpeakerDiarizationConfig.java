package com.k2fsa.sherpa.onnx;

/**
 * Main configuration for offline speaker diarization.
 * Java version of the original Kotlin data class.
 */
public class OfflineSpeakerDiarizationConfig {
    private OfflineSpeakerSegmentationModelConfig segmentation = new OfflineSpeakerSegmentationModelConfig();
    private SpeakerEmbeddingExtractorConfig embedding = new SpeakerEmbeddingExtractorConfig();
    private FastClusteringConfig clustering = new FastClusteringConfig();
    private float minDurationOn = 0.2f;
    private float minDurationOff = 0.5f;
    
    public OfflineSpeakerDiarizationConfig() {
    }
    
    public OfflineSpeakerSegmentationModelConfig getSegmentation() {
        return segmentation;
    }
    
    public void setSegmentation(OfflineSpeakerSegmentationModelConfig segmentation) {
        this.segmentation = segmentation;
    }
    
    public SpeakerEmbeddingExtractorConfig getEmbedding() {
        return embedding;
    }
    
    public void setEmbedding(SpeakerEmbeddingExtractorConfig embedding) {
        this.embedding = embedding;
    }
    
    public FastClusteringConfig getClustering() {
        return clustering;
    }
    
    public void setClustering(FastClusteringConfig clustering) {
        this.clustering = clustering;
    }
    
    public float getMinDurationOn() {
        return minDurationOn;
    }
    
    public void setMinDurationOn(float minDurationOn) {
        this.minDurationOn = minDurationOn;
    }
    
    public float getMinDurationOff() {
        return minDurationOff;
    }
    
    public void setMinDurationOff(float minDurationOff) {
        this.minDurationOff = minDurationOff;
    }
} 