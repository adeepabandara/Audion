package com.k2fsa.sherpa.onnx;

import android.content.res.AssetManager;

/**
 * Extracts speaker embeddings from audio data.
 * This is a Java wrapper for the native C++ implementation.
 */
public class SpeakerEmbeddingExtractor {
    private long ptr;

    static {
        System.loadLibrary("sherpa-onnx-jni");
    }

    /**
     * Constructor using asset manager for model loading.
     *
     * @param assetManager Android asset manager
     * @param config       Configuration for the extractor
     */
    public SpeakerEmbeddingExtractor(AssetManager assetManager, SpeakerEmbeddingExtractorConfig config) {
        ptr = newFromAsset(assetManager, config);
    }

    /**
     * Constructor using file path for model loading.
     *
     * @param config Configuration for the extractor
     */
    public SpeakerEmbeddingExtractor(SpeakerEmbeddingExtractorConfig config) {
        ptr = newFromFile(config);
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
     * Create a new stream for processing audio data.
     *
     * @return A new OnlineStream object
     */
    public OnlineStream createStream() {
        long streamPtr = createStream(ptr);
        return new OnlineStream(streamPtr);
    }

    /**
     * Check if the stream has enough data to compute an embedding.
     *
     * @param stream The stream to check
     * @return true if ready, false otherwise
     */
    public boolean isReady(OnlineStream stream) {
        return isReady(ptr, stream.ptr);
    }

    /**
     * Compute the embedding from the stream data.
     *
     * @param stream The stream containing audio data
     * @return Float array containing the embedding
     */
    public float[] compute(OnlineStream stream) {
        return compute(ptr, stream.ptr);
    }

    /**
     * Get the dimensionality of the embedding.
     *
     * @return The number of dimensions in the embedding vector
     */
    public int dim() {
        return dim(ptr);
    }

    /**
     * Extract speaker embedding from a single audio sample array.
     * 
     * @param samples Audio samples (mono, float)
     * @return Float array containing the speaker embedding
     */
    public float[] extract(float[] samples) {
        OnlineStream stream = createStream();
        try {
            stream.acceptWaveform(samples, 16000); // Assuming 16kHz sample rate
            stream.inputFinished();
            
            if (isReady(stream)) {
                return compute(stream);
            }
            return null;
        } finally {
            stream.release();
        }
    }

    // Native method declarations
    private native long newFromAsset(AssetManager assetManager, SpeakerEmbeddingExtractorConfig config);
    private native long newFromFile(SpeakerEmbeddingExtractorConfig config);
    private native void delete(long ptr);
    private native long createStream(long ptr);
    private native boolean isReady(long ptr, long streamPtr);
    private native float[] compute(long ptr, long streamPtr);
    private native int dim(long ptr);
} 