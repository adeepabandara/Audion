package com.audion.audio;

import android.util.Log;

/**
 * PHASE 4: Real-time audio quality metrics collection.
 * 
 * Tracks:
 * - THD (Total Harmonic Distortion): Target < 3%
 * - SNR (Signal-to-Noise Ratio): Improvement over input
 * - Latency: Processing + buffering delays
 * - Channel Balance: L/R amplitude difference
 * - UCL Limiting Events: Safety activations
 * 
 * ANSI S3.6-2018 and IEC 60118-7 compliance requirements.
 */
public class AudioQualityMetrics {
    private static final String TAG = "AudioQualityMetrics";
    
    // Metrics accumulation
    private long processedFrames = 0;
    private double sumTHD = 0.0;
    private double sumSNR = 0.0;
    private double sumLatencyMs = 0.0;
    private double sumChannelBalance = 0.0;
    private int uclLimitingEvents = 0;
    
    // Timing tracking
    private long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 10000;  // Log every 10 seconds
    
    // Running averages (for last 10s)
    private static final int MAX_WINDOW_SAMPLES = 48000;  // 1 second @ 48kHz
    private final float[] leftWindow = new float[MAX_WINDOW_SAMPLES];
    private final float[] rightWindow = new float[MAX_WINDOW_SAMPLES];
    private int windowIndex = 0;
    
    /**
     * Update metrics with new audio frame.
     * 
     * @param leftInput Input audio (left)
     * @param rightInput Input audio (right)
     * @param leftOutput Processed audio (left)
     * @param rightOutput Processed audio (right)
     * @param processingTimeNs Processing time in nanoseconds
     * @param frameSize Number of samples in frame
     */
    public void updateMetrics(float[] leftInput, float[] rightInput,
                             float[] leftOutput, float[] rightOutput,
                             long processingTimeNs, int frameSize) {
        processedFrames++;
        
        // Calculate THD (simplified: ratio of harmonics to fundamental)
        double thd = calculateTHD(leftOutput, rightOutput, frameSize);
        sumTHD += thd;
        
        // Calculate SNR improvement (output RMS / input RMS)
        double snr = calculateSNR(leftInput, rightInput, leftOutput, rightOutput, frameSize);
        sumSNR += snr;
        
        // Latency: processing time + buffer delay
        double latencyMs = (processingTimeNs / 1_000_000.0) + (frameSize * 1000.0 / 48000.0);
        sumLatencyMs += latencyMs;
        
        // Channel balance (L vs R RMS difference)
        double balance = calculateChannelBalance(leftOutput, rightOutput, frameSize);
        sumChannelBalance += balance;
        
        // Store samples in rolling window
        for (int i = 0; i < frameSize && windowIndex < MAX_WINDOW_SAMPLES; i++) {
            leftWindow[windowIndex] = leftOutput[i];
            rightWindow[windowIndex] = rightOutput[i];
            windowIndex = (windowIndex + 1) % MAX_WINDOW_SAMPLES;
        }
        
        // Log every 10 seconds
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime >= LOG_INTERVAL_MS) {
            logMetrics();
            lastLogTime = currentTime;
        }
    }
    
    /**
     * Report UCL limiting event.
     */
    public void recordUCLEvent() {
        uclLimitingEvents++;
    }
    
    /**
     * Calculate THD (simplified approximation using harmonic ratios).
     * 
     * Full THD requires FFT analysis. This simplified version estimates
     * distortion from signal peaks and RMS ratio.
     */
    private double calculateTHD(float[] left, float[] right, int size) {
        // Simplified THD: ratio of signal variance to peak
        double leftRMS = calculateRMS(left, size);
        double rightRMS = calculateRMS(right, size);
        double avgRMS = (leftRMS + rightRMS) / 2.0;
        
        float leftPeak = findPeak(left, size);
        float rightPeak = findPeak(right, size);
        float avgPeak = (leftPeak + rightPeak) / 2.0f;
        
        // Approximate THD from crest factor deviation
        double crestFactor = avgRMS > 0 ? avgPeak / avgRMS : 0;
        double idealCrest = Math.sqrt(2.0);  // For sine wave
        double thd = Math.abs(crestFactor - idealCrest) / idealCrest * 100.0;
        
        return Math.min(thd, 100.0);  // Cap at 100%
    }
    
    /**
     * Calculate SNR improvement (output/input ratio).
     */
    private double calculateSNR(float[] leftIn, float[] rightIn,
                               float[] leftOut, float[] rightOut, int size) {
        double inputRMS = (calculateRMS(leftIn, size) + calculateRMS(rightIn, size)) / 2.0;
        double outputRMS = (calculateRMS(leftOut, size) + calculateRMS(rightOut, size)) / 2.0;
        
        if (inputRMS == 0 || outputRMS == 0) return 0.0;
        
        // SNR in dB
        return 20.0 * Math.log10(outputRMS / inputRMS);
    }
    
    /**
     * Calculate channel balance (L vs R difference in dB).
     */
    private double calculateChannelBalance(float[] left, float[] right, int size) {
        double leftRMS = calculateRMS(left, size);
        double rightRMS = calculateRMS(right, size);
        
        if (leftRMS == 0 || rightRMS == 0) return 0.0;
        
        // Balance in dB (positive = left louder, negative = right louder)
        return 20.0 * Math.log10(leftRMS / rightRMS);
    }
    
    /**
     * Calculate RMS (root-mean-square) amplitude.
     */
    private double calculateRMS(float[] buffer, int size) {
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            sum += buffer[i] * buffer[i];
        }
        return Math.sqrt(sum / size);
    }
    
    /**
     * Find peak absolute amplitude.
     */
    private float findPeak(float[] buffer, int size) {
        float peak = 0.0f;
        for (int i = 0; i < size; i++) {
            float abs = Math.abs(buffer[i]);
            if (abs > peak) peak = abs;
        }
        return peak;
    }
    
    /**
     * Log accumulated metrics.
     */
    private void logMetrics() {
        if (processedFrames == 0) return;
        
        double avgTHD = sumTHD / processedFrames;
        double avgSNR = sumSNR / processedFrames;
        double avgLatency = sumLatencyMs / processedFrames;
        double avgBalance = sumChannelBalance / processedFrames;
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
        Log.i(TAG, "[Phase 4] Audio Quality Metrics (10s interval)");
        Log.i(TAG, String.format("THD: %.2f%% (target: <3%%)", avgTHD));
        Log.i(TAG, String.format("SNR Improvement: %.1f dB", avgSNR));
        Log.i(TAG, String.format("Latency: %.2f ms (processing + buffer)", avgLatency));
        Log.i(TAG, String.format("Channel Balance: %.2f dB (L vs R)", avgBalance));
        Log.i(TAG, String.format("UCL Limiting Events: %d", uclLimitingEvents));
        Log.i(TAG, String.format("Processed Frames: %d (%.1f seconds)",
            processedFrames, processedFrames * 480.0 / 48000.0));
        
        // Warnings
        if (avgTHD > 3.0) {
            Log.w(TAG, "⚠️ WARNING: THD exceeds 3% target (ANSI S3.6 compliance risk)");
        }
        if (avgLatency > 20.0) {
            Log.w(TAG, "⚠️ WARNING: Latency exceeds 20ms target");
        }
        if (Math.abs(avgBalance) > 2.0) {
            Log.w(TAG, "⚠️ WARNING: Channel imbalance >2dB detected");
        }
        
        Log.i(TAG, "════════════════════════════════════════════════════════");
    }
    
    /**
     * Get current metrics snapshot for compliance reporting.
     */
    public MetricsSnapshot getSnapshot() {
        if (processedFrames == 0) {
            return new MetricsSnapshot(0, 0, 0, 0, 0, 0);
        }
        
        return new MetricsSnapshot(
            sumTHD / processedFrames,
            sumSNR / processedFrames,
            sumLatencyMs / processedFrames,
            sumChannelBalance / processedFrames,
            uclLimitingEvents,
            processedFrames
        );
    }
    
    /**
     * Reset metrics counters.
     */
    public void reset() {
        processedFrames = 0;
        sumTHD = 0.0;
        sumSNR = 0.0;
        sumLatencyMs = 0.0;
        sumChannelBalance = 0.0;
        uclLimitingEvents = 0;
        windowIndex = 0;
        lastLogTime = System.currentTimeMillis();
        Log.i(TAG, "[Phase 4] Metrics reset");
    }
    
    /**
     * Immutable metrics snapshot for reporting.
     */
    public static class MetricsSnapshot {
        public final double avgTHD;
        public final double avgSNR;
        public final double avgLatencyMs;
        public final double avgChannelBalance;
        public final int uclEvents;
        public final long totalFrames;
        
        MetricsSnapshot(double thd, double snr, double latency, double balance,
                       int uclEvents, long frames) {
            this.avgTHD = thd;
            this.avgSNR = snr;
            this.avgLatencyMs = latency;
            this.avgChannelBalance = balance;
            this.uclEvents = uclEvents;
            this.totalFrames = frames;
        }
    }
}
