package com.audion.app.diarization;

import android.util.Log;
import com.audion.app.RNNoise;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a buffer of audio frames for speaker diarization.
 * Uses a sliding window approach to maintain an audio buffer with overlap.
 */
public class DiarizationBufferManager {
    private static final String TAG = "DiarizationBuffer";
    
    // Default settings (15-second buffer with 5-second overlap)
    private static final int INPUT_SAMPLE_RATE = 48000;  // RNNoise output sample rate
    private static final int TARGET_SAMPLE_RATE = 16000; // Sherpa-ONNX required sample rate
    private static final float BUFFER_SIZE_SECONDS = 15.0f;
    private static final float OVERLAP_SECONDS = 5.0f;
    private static final int BUFFER_SIZE = (int)(TARGET_SAMPLE_RATE * BUFFER_SIZE_SECONDS);
    private static final int OVERLAP_SIZE = (int)(TARGET_SAMPLE_RATE * OVERLAP_SECONDS);
    private static final int DISCARD_SIZE = BUFFER_SIZE - OVERLAP_SIZE;
    
    private List<float[]> frameBuffer = new ArrayList<>();
    private int totalSamples = 0;
    private boolean bufferFilled = false;
    
    public DiarizationBufferManager() {
        Log.d(TAG, String.format("Initialized with buffer_size=%d, overlap=%d, discard=%d",
                BUFFER_SIZE, OVERLAP_SIZE, DISCARD_SIZE));
    }
    
    /**
     * Add a frame of audio samples to the buffer.
     * 
     * @param frame Audio frame (array of float samples)
     */
    public void addFrame(float[] frame) {
        if (frame == null || frame.length == 0) {
            Log.e(TAG, "Received empty or null frame");
            return;
        }
        
        // Convert from 48kHz to 16kHz
        float[] resampledFrame = resampleFrame(frame);
        frameBuffer.add(resampledFrame);
        totalSamples += resampledFrame.length;
        bufferFilled = totalSamples >= BUFFER_SIZE;
        
        Log.d(TAG, String.format("Added frame: size=%d, total=%d, target=%d, filled=%b, buffer_frames=%d", 
                resampledFrame.length, totalSamples, BUFFER_SIZE, bufferFilled, frameBuffer.size()));
        
        if (bufferFilled) {
            Log.d(TAG, "Buffer filled: " + totalSamples + " samples (" + (totalSamples / TARGET_SAMPLE_RATE) + " seconds)");
        }
    }
    
    /**
     * Resample a frame from 48kHz to 16kHz.
     * Simple linear resampling by taking every 3rd sample.
     */
    private float[] resampleFrame(float[] frame) {
        int outputLength = frame.length / 3; // 48kHz -> 16kHz is 3:1 ratio
        float[] resampled = new float[outputLength];
        
        for (int i = 0; i < outputLength; i++) {
            resampled[i] = frame[i * 3];
        }
        
        return resampled;
    }
    
    /**
     * Check if the buffer is filled (reached the required size).
     * 
     * @return true if buffer is filled, false otherwise
     */
    public boolean isBufferFilled() {
        Log.d(TAG, String.format("Checking buffer: total=%d, target=%d, filled=%b, buffer_frames=%d", 
                totalSamples, BUFFER_SIZE, bufferFilled, frameBuffer.size()));
        return bufferFilled;
    }
    
    /**
     * Get the current buffer as a single array.
     * 
     * @return Array of audio samples
     */
    public float[] getBuffer() {
        float[] buffer = new float[Math.min(totalSamples, BUFFER_SIZE)];
        int offset = 0;
        
        // Copy frames to buffer, starting from the most recent frames if we have more than we need
        int startIndex = Math.max(0, totalSamples - BUFFER_SIZE) / (RNNoise.FRAME_SIZE / 3);
        
        Log.d(TAG, String.format("Getting buffer: start_index=%d, buffer_frames=%d, target_size=%d", 
                startIndex, frameBuffer.size(), buffer.length));
        
        for (int i = startIndex; i < frameBuffer.size(); i++) {
            float[] frame = frameBuffer.get(i);
            int copyLength = Math.min(frame.length, buffer.length - offset);
            if (copyLength > 0) {
                System.arraycopy(frame, 0, buffer, offset, copyLength);
                offset += copyLength;
            }
        }
        
        if (buffer.length > 0) {
            Log.d(TAG, String.format("Retrieved buffer: size=%d", offset));
        } else {
            Log.e(TAG, "Retrieved empty buffer!");
        }
        
        return buffer;
    }
    
    /**
     * Shift the buffer by discarding the non-overlapping portion.
     * This maintains the overlap between consecutive processing windows.
     */
    public void shiftBuffer() {
        int discardSamples = 0;
        int framesToRemove = 0;
        
        // Count frames to remove
        for (int i = 0; i < frameBuffer.size() && discardSamples < DISCARD_SIZE; i++) {
            discardSamples += frameBuffer.get(i).length;
            framesToRemove++;
        }
        
        Log.d(TAG, String.format("Shifting buffer: removing %d frames, %d samples", framesToRemove, discardSamples));
        
        // Remove frames
        for (int i = 0; i < framesToRemove; i++) {
            frameBuffer.remove(0);
        }
        
        totalSamples -= discardSamples;
        bufferFilled = false;
        
        Log.d(TAG, String.format("Shifted buffer: discarded=%d, remaining=%d, buffer_frames=%d", 
                discardSamples, totalSamples, frameBuffer.size()));
    }
    
    /**
     * Get the current buffer size in seconds.
     */
    public float getBufferSizeSeconds() {
        return BUFFER_SIZE_SECONDS;
    }
    
    /**
     * Get the number of samples in the buffer.
     */
    public int getTotalSamples() {
        return totalSamples;
    }
    
    /**
     * Clear the buffer.
     */
    public void clear() {
        Log.d(TAG, "Clearing buffer");
        frameBuffer.clear();
        totalSamples = 0;
        bufferFilled = false;
    }
} 