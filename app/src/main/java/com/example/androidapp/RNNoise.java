package com.example.androidapp;

/**
 * Java wrapper for RNNoise native library.
 * This class provides noise suppression functionality using RNNoise.
 */
public class RNNoise {
    private long state = 0;

    // Frame size for 10ms of 48kHz audio
    public static final int FRAME_SIZE = 480;

    // Load native libraries
    static {
        System.loadLibrary("rnnoise");
        System.loadLibrary("androidapp");
    }

    // Inner class to hold process results
    public static class ProcessResult {
        public final float[] audio;
        public final float vadProbability;

        public ProcessResult(float[] audio, float vadProbability) {
            this.audio = audio;
            this.vadProbability = vadProbability;
        }
    }

    /**
     * Initialize RNNoise state
     */
    public void initialize() {
        if (state != 0) {
            destroy();
        }
        state = createState();
        if (state == 0) {
            throw new IllegalStateException("Failed to create RNNoise state");
        }
    }

    /**
     * Process a frame of audio through RNNoise
     * @param input Float array of audio samples (must be FRAME_SIZE length)
     * @return ProcessResult containing processed audio and VAD probability
     */
    public ProcessResult processFrame(float[] input) {
        if (state == 0) {
            throw new IllegalStateException("RNNoise not initialized");
        }
        if (input.length != FRAME_SIZE) {
            throw new IllegalArgumentException("Input must be " + FRAME_SIZE + " samples (got " + input.length + ")");
        }
        ProcessResult result = processFrame(state, input);
        if (result == null || result.audio == null) {
            throw new RuntimeException("Processing failed");
        }
        return result;
    }

    /**
     * Clean up RNNoise state
     */
    public void destroy() {
        if (state != 0) {
            destroyState(state);
            state = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        destroy();
        super.finalize();
    }

    // Native methods
    private native long createState();
    private native void destroyState(long state);
    private native ProcessResult processFrame(long state, float[] input);
}