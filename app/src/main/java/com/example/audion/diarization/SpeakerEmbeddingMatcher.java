package com.example.audion.diarization;

import android.util.Log;

import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for matching speakers across multiple audio chunks
 * based on their voice embeddings.
 */
public class SpeakerEmbeddingMatcher {
    private static final String TAG = "SpeakerEmbeddingMatcher";
    
    // Similarity threshold for considering two embeddings to be from the same speaker
    // Lower values are more strict (require more similarity)
    private static final float DEFAULT_SIMILARITY_THRESHOLD = 0.45f;
    
    // Store speaker embeddings for global speaker IDs
    private final Map<Integer, float[]> speakerEmbeddings = new HashMap<>();
    
    // Store additional information about speakers
    private final Map<Integer, String> speakerNames = new HashMap<>();
    private final Map<Integer, Boolean> isEnrolledSpeaker = new HashMap<>();
    
    // Counter for assigning new global speaker IDs
    private int nextGlobalSpeakerId = 1;
    
    // Configurable similarity threshold
    private float similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
    
    /**
     * Set the similarity threshold for speaker matching.
     * @param threshold Value between 0.0 and 1.0, where higher values are more lenient in matching
     */
    public void setSimilarityThreshold(float threshold) {
        if (threshold < 0.0f || threshold > 1.0f) {
            Log.w(TAG, "Invalid threshold value: " + threshold + ", using default");
            this.similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
        } else {
            this.similarityThreshold = threshold;
            Log.d(TAG, "Set similarity threshold to: " + threshold);
        }
    }
    
    /**
     * Reset the matcher, clearing all stored embeddings and resetting
     * the global speaker ID counter.
     */
    public void reset() {
        speakerEmbeddings.clear();
        speakerNames.clear();
        isEnrolledSpeaker.clear();
        nextGlobalSpeakerId = 1;
        Log.d(TAG, "Reset speaker embedding matcher");
    }
    
    /**
     * Register an enrolled speaker embedding directly.
     * This allows using pre-enrolled speaker embeddings for identification.
     * 
     * @param embedding The speaker embedding vector
     * @param name Optional name for the speaker
     * @return The global speaker ID assigned to this embedding
     */
    public int registerSpeakerEmbedding(float[] embedding, String name) {
        return registerSpeakerEmbedding(embedding, name, true);
    }
    
    /**
     * Register a speaker embedding directly with specified enrollment status.
     * 
     * @param embedding The speaker embedding vector
     * @param name Optional name for the speaker
     * @param isEnrolled Whether this is an enrolled speaker (vs. detected)
     * @return The global speaker ID assigned to this embedding
     */
    public int registerSpeakerEmbedding(float[] embedding, String name, boolean isEnrolled) {
        if (embedding == null || embedding.length == 0) {
            Log.e(TAG, "Cannot register null or empty embedding");
            return -1;
        }
        
        // Assign a new global ID
        int globalId = nextGlobalSpeakerId++;
        
        // Store the embedding
        speakerEmbeddings.put(globalId, Arrays.copyOf(embedding, embedding.length));
        
        // Store additional info
        speakerNames.put(globalId, name != null ? name : "Speaker " + globalId);
        isEnrolledSpeaker.put(globalId, isEnrolled);
        
        Log.d(TAG, "Registered " + (isEnrolled ? "enrolled" : "detected") + 
              " speaker " + globalId + " with name: " + 
              (name != null ? name : "Speaker " + globalId));
        
        return globalId;
    }
    
    /**
     * Check if a detected speaker (identified by global ID) matches an enrolled speaker embedding.
     * 
     * @param globalSpeakerId The global speaker ID to check
     * @param enrolledEmbedding The enrolled speaker embedding to compare against
     * @return True if the detected speaker matches the enrolled embedding
     */
    public boolean isSpeakerMatchingEnrollment(int globalSpeakerId, float[] enrolledEmbedding) {
        if (!speakerEmbeddings.containsKey(globalSpeakerId) || enrolledEmbedding == null) {
            return false;
        }
        
        float[] detectedEmbedding = speakerEmbeddings.get(globalSpeakerId);
        float similarity = calculateCosineSimilarity(detectedEmbedding, enrolledEmbedding);
        
        return similarity >= similarityThreshold;
    }
    
    /**
     * Get the name of a speaker, if available.
     * 
     * @param globalSpeakerId The global speaker ID
     * @return The speaker name, or a default name if none is available
     */
    public String getSpeakerName(int globalSpeakerId) {
        return speakerNames.getOrDefault(globalSpeakerId, "Speaker " + globalSpeakerId);
    }
    
    /**
     * Check if a speaker is an enrolled speaker.
     * 
     * @param globalSpeakerId The global speaker ID
     * @return True if the speaker was enrolled, false if it was detected automatically
     */
    public boolean isEnrolledSpeaker(int globalSpeakerId) {
        return isEnrolledSpeaker.getOrDefault(globalSpeakerId, false);
    }
    
    /**
     * Match speakers in the current chunk with global speaker IDs.
     * 
     * @param segments Array of speaker diarization segments for the current chunk
     * @param embeddings Map of embeddings for each speaker in the current chunk
     * @return Map linking local speaker IDs to global speaker IDs
     */
    public Map<Integer, Integer> matchSpeakers(
            OfflineSpeakerDiarizationSegment[] segments, 
            Map<Integer, float[]> embeddings) {
        
        if (segments == null || segments.length == 0 || embeddings == null || embeddings.isEmpty()) {
            Log.w(TAG, "No segments or embeddings to match");
            return new HashMap<>();
        }
        
        Log.d(TAG, "Matching " + embeddings.size() + " speakers with " + 
              speakerEmbeddings.size() + " existing speakers");
        
        // Map from local speaker IDs to global speaker IDs
        Map<Integer, Integer> localToGlobalIdMap = new HashMap<>();
        
        // For each speaker in the current chunk
        for (Map.Entry<Integer, float[]> entry : embeddings.entrySet()) {
            int localSpeakerId = entry.getKey();
            float[] localEmbedding = entry.getValue();
            
            // Find most similar existing speaker embedding
            int bestMatchGlobalId = -1;
            float bestMatchSimilarity = 0;
            
            for (Map.Entry<Integer, float[]> existingEntry : speakerEmbeddings.entrySet()) {
                int globalSpeakerId = existingEntry.getKey();
                float[] globalEmbedding = existingEntry.getValue();
                
                float similarity = calculateCosineSimilarity(localEmbedding, globalEmbedding);
                
                if (similarity > bestMatchSimilarity) {
                    bestMatchSimilarity = similarity;
                    bestMatchGlobalId = globalSpeakerId;
                }
            }
            
            // If we found a good match, use that global ID
            if (bestMatchSimilarity >= similarityThreshold) {
                localToGlobalIdMap.put(localSpeakerId, bestMatchGlobalId);
                Log.d(TAG, "Matched local speaker " + localSpeakerId + 
                      " to global speaker " + bestMatchGlobalId + 
                      " with similarity " + bestMatchSimilarity);
                
                // Update the global embedding with a weighted average
                updateGlobalEmbedding(bestMatchGlobalId, localEmbedding);
            } 
            // Otherwise, assign a new global ID
            else {
                int newGlobalId = nextGlobalSpeakerId++;
                localToGlobalIdMap.put(localSpeakerId, newGlobalId);
                speakerEmbeddings.put(newGlobalId, Arrays.copyOf(localEmbedding, localEmbedding.length));
                
                // Initialize additional info for new speaker
                speakerNames.put(newGlobalId, "Speaker " + newGlobalId);
                isEnrolledSpeaker.put(newGlobalId, false);
                
                Log.d(TAG, "Created new global speaker " + newGlobalId + 
                      " for local speaker " + localSpeakerId + 
                      (bestMatchGlobalId != -1 ? 
                       " (best match was " + bestMatchGlobalId + 
                       " with similarity " + bestMatchSimilarity + ")" : ""));
            }
        }
        
        return localToGlobalIdMap;
    }
    
    /**
     * Calculate the cosine similarity between two embeddings.
     * 
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Similarity score between 0.0 and 1.0, where 1.0 is identical
     */
    private float calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            Log.e(TAG, "Embedding length mismatch: " + embedding1.length + 
                  " vs " + embedding2.length);
            return 0.0f;
        }
        
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;
        
        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }
        
        // Avoid division by zero
        if (norm1 == 0.0f || norm2 == 0.0f) {
            return 0.0f;
        }
        
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Update the global embedding for a speaker with a new embedding.
     * Uses a weighted average to gradually adapt the embedding.
     * 
     * @param globalSpeakerId Global speaker ID to update
     * @param newEmbedding New embedding to incorporate
     */
    private void updateGlobalEmbedding(int globalSpeakerId, float[] newEmbedding) {
        float[] existingEmbedding = speakerEmbeddings.get(globalSpeakerId);
        if (existingEmbedding == null || existingEmbedding.length != newEmbedding.length) {
            Log.e(TAG, "Cannot update embedding - length mismatch or missing embedding");
            return;
        }
        
        // Don't update enrolled speakers as aggressively
        float existingWeight = isEnrolledSpeaker.getOrDefault(globalSpeakerId, false) ? 
                0.9f : 0.7f;  // 90% weight for enrolled, 70% for detected
        float newWeight = 1.0f - existingWeight;
        
        // Compute weighted average
        for (int i = 0; i < existingEmbedding.length; i++) {
            existingEmbedding[i] = existingEmbedding[i] * existingWeight + 
                                   newEmbedding[i] * newWeight;
        }
        
        // Normalize the result
        float norm = 0.0f;
        for (int i = 0; i < existingEmbedding.length; i++) {
            norm += existingEmbedding[i] * existingEmbedding[i];
        }
        
        norm = (float) Math.sqrt(norm);
        if (norm > 0.0f) {
            for (int i = 0; i < existingEmbedding.length; i++) {
                existingEmbedding[i] /= norm;
            }
        }
    }
    
    /**
     * Get the number of global speakers tracked.
     * 
     * @return Number of unique speakers recognized across all chunks
     */
    public int getGlobalSpeakerCount() {
        return speakerEmbeddings.size();
    }
    
    /**
     * Clear all enrolled speakers from the system.
     * This removes only speakers that were explicitly enrolled, not those
     * detected during audio processing.
     */
    public void clearEnrolledSpeakers() {
        List<Integer> enrolledIds = new ArrayList<>();
        
        // Find all enrolled speaker IDs
        for (Map.Entry<Integer, Boolean> entry : isEnrolledSpeaker.entrySet()) {
            if (entry.getValue()) {
                enrolledIds.add(entry.getKey());
            }
        }
        
        // Remove them from all maps
        for (Integer id : enrolledIds) {
            speakerEmbeddings.remove(id);
            speakerNames.remove(id);
            isEnrolledSpeaker.remove(id);
        }
        
        Log.d(TAG, "Cleared " + enrolledIds.size() + " enrolled speakers");
    }
} 