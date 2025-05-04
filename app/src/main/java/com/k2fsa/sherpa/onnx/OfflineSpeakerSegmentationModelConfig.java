package com.k2fsa.sherpa.onnx;

/**
 * Configuration for the speaker segmentation model.
 * Java version of the original Kotlin data class.
 */
public class OfflineSpeakerSegmentationModelConfig {
    private OfflineSpeakerSegmentationPyannoteModelConfig pyannote = new OfflineSpeakerSegmentationPyannoteModelConfig();
    private int numThreads = 1;
    private boolean debug = false;
    private String provider = "cpu";
    
    public OfflineSpeakerSegmentationModelConfig() {
    }
    
    public OfflineSpeakerSegmentationPyannoteModelConfig getPyannote() {
        return pyannote;
    }
    
    public void setPyannote(OfflineSpeakerSegmentationPyannoteModelConfig pyannote) {
        this.pyannote = pyannote;
    }
    
    public int getNumThreads() {
        return numThreads;
    }
    
    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
} 