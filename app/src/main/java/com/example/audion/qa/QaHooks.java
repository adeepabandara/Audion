package com.example.audion.qa;

import android.util.Log;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QA hooks for runtime validation and telemetry of the audio processing pipeline.
 * Provides comprehensive monitoring and debugging capabilities.
 */
public final class QaHooks {
    private static final String TAG = "QaHooks";
    
    // Performance metrics
    private static final AtomicLong totalFramesProcessed = new AtomicLong(0);
    private static final AtomicLong totalProcessingTimeUs = new AtomicLong(0);
    private static final AtomicInteger underrunCount = new AtomicInteger(0);
    private static final AtomicInteger overrunCount = new AtomicInteger(0);
    private static final AtomicInteger allocationCount = new AtomicInteger(0);
    
    // Pipeline state tracking
    private static volatile String currentPipeline = "UNKNOWN";
    private static volatile String lastProcessingMode = "UNKNOWN";
    private static volatile boolean isEngineRunning = false;
    
    // DSP metrics
    private static volatile float lastLimiterEngagement = 0.0f;
    private static volatile float lastWdrcGainReduction = 0.0f;
    private static volatile String lastSceneType = "UNKNOWN";
    private static volatile float lastRnnoiseStrength = 0.0f;
    
    /**
     * Record frame processing for performance tracking
     */
    public static void recordFrameProcessed(long processingTimeUs) {
        totalFramesProcessed.incrementAndGet();
        totalProcessingTimeUs.addAndGet(processingTimeUs);
    }
    
    /**
     * Record buffer underrun event
     */
    public static void recordUnderrun() {
        int count = underrunCount.incrementAndGet();
        Log.w(TAG, "Audio underrun #" + count + " detected");
    }
    
    /**
     * Record buffer overrun event
     */
    public static void recordOverrun() {
        int count = overrunCount.incrementAndGet();
        Log.w(TAG, "Audio overrun #" + count + " detected");
    }
    
    /**
     * Record memory allocation in hot path (should be zero!)
     */
    public static void recordAllocation(String location) {
        int count = allocationCount.incrementAndGet();
        Log.e(TAG, "🚨 ALLOCATION IN HOT PATH #" + count + " at: " + location);
    }
    
    /**
     * Update pipeline state
     */
    public static void setPipelineState(String pipeline, boolean running) {
        currentPipeline = pipeline;
        isEngineRunning = running;
        Log.i(TAG, "Pipeline state: " + pipeline + " (running=" + running + ")");
    }
    
    /**
     * Update processing mode
     */
    public static void setProcessingMode(String mode) {
        lastProcessingMode = mode;
        Log.d(TAG, "Processing mode: " + mode);
    }
    
    /**
     * Update DSP metrics
     */
    public static void updateDspMetrics(float limiterEngagement, float wdrcGainReduction, 
                                       String sceneType, float rnnoiseStrength) {
        lastLimiterEngagement = limiterEngagement;
        lastWdrcGainReduction = wdrcGainReduction;
        lastSceneType = sceneType;
        lastRnnoiseStrength = rnnoiseStrength;
    }
    
    /**
     * Get comprehensive runtime stats as CSV row
     */
    public static String getCsvStats() {
        long frames = totalFramesProcessed.get();
        long totalTimeUs = totalProcessingTimeUs.get();
        double avgTimeUs = frames > 0 ? (double) totalTimeUs / frames : 0.0;
        
        return String.format("%s,%s,%b,%d,%d,%d,%d,%.2f,%.2f,%.2f,%s,%.2f",
            currentPipeline,
            lastProcessingMode,
            isEngineRunning,
            frames,
            underrunCount.get(),
            overrunCount.get(),
            allocationCount.get(),
            avgTimeUs,
            lastLimiterEngagement,
            lastWdrcGainReduction,
            lastSceneType,
            lastRnnoiseStrength
        );
    }
    
    /**
     * Get CSV header for stats
     */
    public static String getCsvHeader() {
        return "Pipeline,Mode,Running,Frames,Underruns,Overruns,Allocations,AvgTimeUs," +
               "LimiterEngagement,WdrcGainReduction,SceneType,RnnoiseStrength";
    }
    
    /**
     * Get human-readable debug summary
     */
    public static String getDebugSummary() {
        long frames = totalFramesProcessed.get();
        long totalTimeUs = totalProcessingTimeUs.get();
        double avgTimeUs = frames > 0 ? (double) totalTimeUs / frames : 0.0;
        
        return String.format(
            "📊 QA Metrics:\n" +
            "Pipeline: %s (%s)\n" +
            "Frames: %d (avg %.1fµs)\n" +
            "Issues: %d underruns, %d overruns\n" +
            "⚠️ Allocations: %d\n" +
            "DSP: Limiter %.1f%%, WDRC %.1fdB\n" +
            "Scene: %s, RNNoise: %.1f",
            currentPipeline, lastProcessingMode,
            frames, avgTimeUs,
            underrunCount.get(), overrunCount.get(),
            allocationCount.get(),
            lastLimiterEngagement, lastWdrcGainReduction,
            lastSceneType, lastRnnoiseStrength
        );
    }
    
    /**
     * Run one-shot tap test to measure round-trip latency
     */
    public static void runTapTest() {
        Log.i(TAG, "🎯 Starting tap test...");
        
        // Record start time
        long startTime = System.nanoTime();
        
        // Generate test impulse (would be injected into pipeline)
        // TODO: Implement actual impulse injection and detection
        
        // For now, just log the test initiation
        Log.i(TAG, "Tap test initiated at t=" + (startTime / 1000000) + "ms");
        
        // In a real implementation, this would:
        // 1. Inject a known impulse into the capture buffer
        // 2. Monitor the playback buffer for the same impulse
        // 3. Measure the time difference
        // 4. Log results to CSV file
    }
    
    /**
     * Reset all metrics (for testing)
     */
    public static void reset() {
        totalFramesProcessed.set(0);
        totalProcessingTimeUs.set(0);
        underrunCount.set(0);
        overrunCount.set(0);
        allocationCount.set(0);
        
        currentPipeline = "UNKNOWN";
        lastProcessingMode = "UNKNOWN";
        isEngineRunning = false;
        
        lastLimiterEngagement = 0.0f;
        lastWdrcGainReduction = 0.0f;
        lastSceneType = "UNKNOWN";
        lastRnnoiseStrength = 0.0f;
        
        Log.i(TAG, "QA metrics reset");
    }
    
    /**
     * Log pipeline banner on startup
     */
    public static void logPipelineBanner(String[] processingOrder) {
        StringBuilder banner = new StringBuilder();
        banner.append("\n🎵 AUDIO PIPELINE ACTIVE 🎵\n");
        banner.append("Pipeline: ").append(currentPipeline).append("\n");
        banner.append("Processing Order:\n");
        
        for (int i = 0; i < processingOrder.length; i++) {
            banner.append(String.format("  %d. %s\n", i + 1, processingOrder[i]));
        }
        
        banner.append("================================");
        Log.i(TAG, banner.toString());
    }
}