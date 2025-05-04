package com.k2fsa.sherpa.onnx;

/**
 * Represents an online audio stream for speaker embedding extraction.
 */
public class OnlineStream {
    // Native pointer to C++ object
    public long ptr = 0;

    static {
        System.loadLibrary("sherpa-onnx-jni");
    }

    /**
     * Constructor with pointer to native object.
     */
    public OnlineStream(long ptr) {
        this.ptr = ptr;
    }

    /**
     * Accept a waveform for processing.
     *
     * @param samples    The audio samples
     * @param sampleRate The sample rate of the audio
     */
    public void acceptWaveform(float[] samples, int sampleRate) {
        acceptWaveform(ptr, samples, sampleRate);
    }

    /**
     * Signal that no more input will be provided.
     */
    public void inputFinished() {
        inputFinished(ptr);
    }

    /**
     * Clean up native resources.
     */
    @Override
    protected void finalize() throws Throwable {
        if (ptr != 0L) {
            delete(ptr);
            ptr = 0;
        }
        super.finalize();
    }

    /**
     * Explicitly release resources instead of waiting for garbage collection.
     */
    public void release() {
        if (ptr != 0L) {
            delete(ptr);
            ptr = 0;
        }
    }

    /**
     * Use the stream and automatically release resources after use.
     *
     * @param callback The callback that uses the stream
     */
    public void use(StreamCallback callback) {
        try {
            callback.use(this);
        } finally {
            release();
        }
    }

    /**
     * Interface for stream use callback.
     */
    public interface StreamCallback {
        void use(OnlineStream stream);
    }

    // Native method declarations
    private native void acceptWaveform(long ptr, float[] samples, int sampleRate);
    private native void inputFinished(long ptr);
    private native void delete(long ptr);
} 