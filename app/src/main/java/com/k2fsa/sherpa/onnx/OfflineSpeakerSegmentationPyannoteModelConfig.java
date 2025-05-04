package com.k2fsa.sherpa.onnx;

/**
 * Configuration for the Pyannote segmentation model.
 * Java version of the original Kotlin data class.
 */
public class OfflineSpeakerSegmentationPyannoteModelConfig {
    private String model = "";
    
    public OfflineSpeakerSegmentationPyannoteModelConfig() {
    }
    
    public OfflineSpeakerSegmentationPyannoteModelConfig(String model) {
        this.model = model;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
} 