package com.k2fsa.sherpa.onnx;

/**
 * Manages speaker embeddings for identification and verification tasks.
 * This is a Java wrapper for the native C++ implementation.
 */
public class SpeakerEmbeddingManager {
    private long ptr;
    private final int dim;

    static {
        System.loadLibrary("sherpa-onnx-jni");
    }

    /**
     * Constructor.
     *
     * @param dim Dimensionality of the embeddings to store
     */
    public SpeakerEmbeddingManager(int dim) {
        this.dim = dim;
        this.ptr = create(dim);
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
     * Add a single speaker embedding.
     *
     * @param name      The name of the speaker
     * @param embedding The embedding vector for the speaker
     * @return true if successful, false otherwise
     */
    public boolean add(String name, float[] embedding) {
        return add(ptr, name, embedding);
    }

    /**
     * Add multiple embeddings for the same speaker.
     *
     * @param name       The name of the speaker
     * @param embeddings Array of embedding vectors for the speaker
     * @return true if successful, false otherwise
     */
    public boolean add(String name, float[][] embeddings) {
        return addList(ptr, name, embeddings);
    }

    /**
     * Remove a speaker from the database.
     *
     * @param name The name of the speaker to remove
     * @return true if successful, false otherwise
     */
    public boolean remove(String name) {
        return remove(ptr, name);
    }

    /**
     * Search for the closest speaker match in the database.
     *
     * @param embedding The embedding vector to search for
     * @param threshold Similarity threshold (higher values are more lenient)
     * @return The name of the closest matching speaker, or empty string if no match found
     */
    public String search(float[] embedding, float threshold) {
        return search(ptr, embedding, threshold);
    }

    /**
     * Verify if an embedding matches a specific speaker.
     *
     * @param name      The name of the speaker to verify against
     * @param embedding The embedding vector to verify
     * @param threshold Similarity threshold (higher values are more lenient)
     * @return true if the embedding matches the speaker, false otherwise
     */
    public boolean verify(String name, float[] embedding, float threshold) {
        return verify(ptr, name, embedding, threshold);
    }

    /**
     * Check if a speaker exists in the database.
     *
     * @param name The name of the speaker to check
     * @return true if the speaker exists, false otherwise
     */
    public boolean contains(String name) {
        return contains(ptr, name);
    }

    /**
     * Get the number of speakers in the database.
     *
     * @return The number of speakers
     */
    public int numSpeakers() {
        return numSpeakers(ptr);
    }

    /**
     * Get the names of all speakers in the database.
     *
     * @return Array of speaker names
     */
    public String[] allSpeakerNames() {
        return allSpeakerNames(ptr);
    }

    // Native method declarations
    private native long create(int dim);
    private native void delete(long ptr);
    private native boolean add(long ptr, String name, float[] embedding);
    private native boolean addList(long ptr, String name, float[][] embeddings);
    private native boolean remove(long ptr, String name);
    private native String search(long ptr, float[] embedding, float threshold);
    private native boolean verify(long ptr, String name, float[] embedding, float threshold);
    private native boolean contains(long ptr, String name);
    private native int numSpeakers(long ptr);
    private native String[] allSpeakerNames(long ptr);
} 