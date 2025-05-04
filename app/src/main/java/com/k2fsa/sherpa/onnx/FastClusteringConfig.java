package com.k2fsa.sherpa.onnx;

/**
 * Configuration for the fast clustering algorithm.
 * Java version of the original Kotlin data class.
 */
public class FastClusteringConfig {
    private int numClusters = -1;
    private float threshold = 0.5f;
    
    public FastClusteringConfig() {
    }
    
    public FastClusteringConfig(int numClusters, float threshold) {
        this.numClusters = numClusters;
        this.threshold = threshold;
    }
    
    public int getNumClusters() {
        return numClusters;
    }
    
    public void setNumClusters(int numClusters) {
        this.numClusters = numClusters;
    }
    
    public float getThreshold() {
        return threshold;
    }
    
    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }
} 