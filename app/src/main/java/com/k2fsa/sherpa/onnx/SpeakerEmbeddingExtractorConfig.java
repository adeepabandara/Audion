package com.k2fsa.sherpa.onnx;

/**
 * Configuration for Speaker Embedding Extractor.
 */
public class SpeakerEmbeddingExtractorConfig {
    private String model = "";
    private int numThreads = 1;
    private boolean debug = false;
    private String provider = "cpu";
    
    public SpeakerEmbeddingExtractorConfig() {
    }
    
    public SpeakerEmbeddingExtractorConfig(String model) {
        this.model = model;
    }
    
    public SpeakerEmbeddingExtractorConfig(String model, int numThreads, boolean debug, String provider) {
        this.model = model;
        this.numThreads = numThreads;
        this.debug = debug;
        this.provider = provider;
    }
    
    // Getters and setters
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
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