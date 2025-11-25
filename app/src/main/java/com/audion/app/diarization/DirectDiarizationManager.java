package com.audion.app.diarization;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Direct manager for speaker diarization.
 * Processes chunks of audio directly for speaker identification.
 */
public class DirectDiarizationManager {
    private static final String TAG = "DirectDiarizationMgr";
    
    // Process chunks of 2 seconds each
    private static final float CHUNK_SIZE_SECONDS = 2.0f;
    private static final int TARGET_SAMPLE_RATE = 16000; // Sherpa-ONNX required sample rate
    private static final int CHUNK_SIZE = (int)(TARGET_SAMPLE_RATE * CHUNK_SIZE_SECONDS);
    
    private Context context;
    private boolean isInitialized = false;
    private boolean isProcessing = false;
    private ExecutorService executorService;
    private Handler mainHandler;
    
    // Buffer for collecting audio samples
    private List<Float> audioBuffer = new ArrayList<>();
    
    // Storage for audio chunks and segments
    private Map<Integer, float[]> chunkAudioBuffers = new HashMap<>();
    private Map<Integer, OfflineSpeakerDiarizationSegment[]> chunkSegments = new HashMap<>();
    
    // Storage for speaker embeddings
    private Map<Integer, Map<Integer, float[]>> chunkSpeakerEmbeddings = new HashMap<>();
    
    // Cross-chunk speaker identification
    private SpeakerEmbeddingMatcher embeddingMatcher = new SpeakerEmbeddingMatcher();
    private Map<Integer, Map<Integer, Integer>> chunkLocalToGlobalSpeakerIds = new HashMap<>();
    
    // Diarization results
    private OfflineSpeakerDiarizationSegment[] currentSegments = new OfflineSpeakerDiarizationSegment[0];
    private List<SpeakerInfo> detectedSpeakers = new ArrayList<>();
    
    // Track chunk processing
    private int currentChunkId = 0;
    private List<List<SpeakerInfo>> chunkSpeakerHistory = new ArrayList<>();
    
    // Callbacks
    private DiarizationListener listener;
    
    /**
     * Interface for diarization event callbacks.
     */
    public interface DiarizationListener {
        void onSpeakersDetected(List<SpeakerInfo> speakers);
        void onSpeakersHistoryUpdated(List<List<SpeakerInfo>> speakerHistory);
        void onBufferFillProgress(float progress);
        void onProcessingProgress(float progress);
        void onDiarizationError(String message);
    }
    
    /**
     * Class representing detected speaker information.
     */
    public static class SpeakerInfo {
        private final int id;
        private final float duration;
        private final int chunkId;
        private final int globalId;
        
        public SpeakerInfo(int id, float duration, int chunkId, int globalId) {
            this.id = id;
            this.duration = duration;
            this.chunkId = chunkId;
            this.globalId = globalId;
        }
        
        public int getId() {
            return id;
        }
        
        public float getDuration() {
            return duration;
        }
        
        public int getChunkId() {
            return chunkId;
        }
        
        public int getGlobalId() {
            return globalId;
        }
        
        @Override
        public String toString() {
            return "Speaker " + globalId + " (" + String.format("%.1f", duration) + "s) - Chunk " + chunkId;
        }
    }
    
    /**
     * Create a new DirectDiarizationManager.
     * 
     * @param context Android context for accessing resources
     */
    public DirectDiarizationManager(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Set a listener for diarization events.
     * 
     * @param listener The listener to set
     */
    public void setListener(DiarizationListener listener) {
        this.listener = listener;
    }
    
    /**
     * Set the similarity threshold for speaker matching across chunks.
     * 
     * @param threshold Value between 0.0 and 1.0, where higher values are more lenient
     */
    public void setSimilarityThreshold(float threshold) {
        embeddingMatcher.setSimilarityThreshold(threshold);
        Log.i(TAG, "Set speaker similarity threshold to " + threshold);
    }
    
    /**
     * Set the similarity threshold for speaker matching.
     * This is an alias for setSimilarityThreshold for consistent API.
     * 
     * @param threshold Value between 0.0 and 1.0, where higher values are more lenient
     */
    public void setSpeakerMatchingThreshold(float threshold) {
        setSimilarityThreshold(threshold);
    }
    
    /**
     * Initialize the diarization engine.
     * 
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize() {
        if (!isInitialized) {
            try {
                SpeakerDiarizationManager.initialize(context);
                isInitialized = true;
                Log.i(TAG, "Diarization initialized successfully");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error initializing diarization", e);
                if (listener != null) {
                    final String errorMsg = e.getMessage();
                    mainHandler.post(() -> listener.onDiarizationError("Initialization error: " + errorMsg));
                }
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if the diarization engine is initialized.
     * 
     * @return true if diarization is initialized, false otherwise
     */
    public boolean isInitialized() {
        return isInitialized && SpeakerDiarizationManager.isInitialized();
    }
    
    /**
     * Process a frame of audio for diarization.
     * 
     * @param frame Audio frame (array of float samples)
     */
    public void processFrame(float[] frame) {
        if (!isInitialized) {
            Log.e(TAG, "Diarization not initialized");
            return;
        }
        
        // Resample from 48kHz to 16kHz and add to buffer
        for (int i = 0; i < frame.length; i += 3) {
            audioBuffer.add(frame[i]); // Take every 3rd sample for 48kHz -> 16kHz
        }
        
        // Process if we have enough samples and not already processing
        if (audioBuffer.size() >= CHUNK_SIZE && !isProcessing) {
            processDiarizationBuffer();
        }
    }
    
    /**
     * Process a frame of audio for diarization.
     * This alias matches the new API for clarity.
     * 
     * @param frame Audio frame (array of float samples)
     */
    public void processAudioFrame(float[] frame) {
        processFrame(frame);
    }
    
    /**
     * Process the current audio buffer for diarization.
     * This is executed on a background thread.
     */
    private void processDiarizationBuffer() {
        if (isProcessing) {
            return;
        }
        
        isProcessing = true;
        currentChunkId++; // Increment chunk ID for each new chunk
        int processChunkId = currentChunkId; // Capture current chunk ID for this processing cycle
        
        Log.d(TAG, "Processing chunk " + processChunkId + " of " + CHUNK_SIZE_SECONDS + " seconds");
        
        executorService.execute(() -> {
            try {
                // Verify we have enough samples
                if (audioBuffer.size() < CHUNK_SIZE) {
                    Log.e(TAG, "Not enough samples in buffer: " + audioBuffer.size() + "/" + CHUNK_SIZE);
                    isProcessing = false;
                    return;
                }
                
                // Convert buffer to float array
                float[] sampleArray = new float[CHUNK_SIZE];
                for (int i = 0; i < CHUNK_SIZE; i++) {
                    sampleArray[i] = audioBuffer.get(i);
                }
                
                Log.d(TAG, "Created sample array for chunk " + processChunkId + " with " + sampleArray.length + " samples");
                
                // Store audio for this chunk (make a copy to avoid modification)
                storeChunkAudio(processChunkId, Arrays.copyOf(sampleArray, sampleArray.length));
                
                // Clear processed audio from buffer but keep any excess for next chunk
                if (audioBuffer.size() > CHUNK_SIZE) {
                    audioBuffer = new ArrayList<>(audioBuffer.subList(CHUNK_SIZE, audioBuffer.size()));
                } else {
                    audioBuffer.clear();
                }
                
                // Process diarization with progress callback
                currentSegments = SpeakerDiarizationManager.processSpeakerDiarization(
                        sampleArray,
                        (numProcessedChunks, numTotalChunks, arg) -> {
                            // Update progress through callback
                            if (listener != null && numTotalChunks > 0) {
                                final float progress = (float) numProcessedChunks / numTotalChunks * 100f;
                                mainHandler.post(() -> listener.onProcessingProgress(progress));
                            }
                            return 0; // 0 = continue processing, non-zero = cancel
                        });
                
                // Store segments for this chunk
                storeChunkSegments(processChunkId, currentSegments);
                
                // Extract embeddings for each speaker in the chunk
                Map<Integer, float[]> speakerEmbeddings = extractSpeakerEmbeddings(processChunkId, currentSegments);
                
                // Match speakers with global IDs
                Map<Integer, Integer> localToGlobalMap = embeddingMatcher.matchSpeakers(currentSegments, speakerEmbeddings);
                
                // Store the mapping for this chunk
                chunkLocalToGlobalSpeakerIds.put(processChunkId, localToGlobalMap);
                
                // Analyze the segments to identify speakers
                analyzeSpeakers(processChunkId);
                
                // Log results
                int speakerCount = (detectedSpeakers != null) ? detectedSpeakers.size() : 0;
                Log.i(TAG, "Diarization complete for chunk " + processChunkId + 
                      ": " + speakerCount + " speakers, " + 
                      (currentSegments != null ? currentSegments.length : 0) + " segments, " +
                      embeddingMatcher.getGlobalSpeakerCount() + " global speakers");
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing diarization", e);
                if (listener != null) {
                    final String errorMsg = e.getMessage();
                    mainHandler.post(() -> listener.onDiarizationError("Processing error: " + errorMsg));
                }
            } finally {
                isProcessing = false;
            }
        });
    }
    
    /**
     * Store audio for a specific chunk.
     * 
     * @param chunkId ID of the chunk
     * @param audioBuffer Audio samples for the chunk
     */
    private void storeChunkAudio(int chunkId, float[] audioBuffer) {
        // Store the audio buffer
        chunkAudioBuffers.put(chunkId, audioBuffer);
        
        // Manage memory by removing older chunks if we have too many
        if (chunkAudioBuffers.size() > 30) {  // Increased from 20 to 30
            List<Integer> sortedChunkIds = new ArrayList<>(chunkAudioBuffers.keySet());
            Collections.sort(sortedChunkIds);
            
            // Make sure we're not deleting chunks with useful content
            List<Integer> recentChunks = sortedChunkIds.subList(
                    Math.max(0, sortedChunkIds.size() - 15),
                    sortedChunkIds.size());
            
            // Remove the oldest chunks until we're at 25 (keep more extras for playback)
            while (sortedChunkIds.size() > 25) {  // Increased from 15 to 25
                int oldestChunkId = sortedChunkIds.get(0);
                // Only remove if we won't be removing a chunk with speakers
                if (!chunkLocalToGlobalSpeakerIds.containsKey(oldestChunkId) || 
                        sortedChunkIds.size() > 28) {  // Emergency cleanup if we're really full
                    chunkAudioBuffers.remove(oldestChunkId);
                    sortedChunkIds.remove(0);
                    Log.d(TAG, "Removed audio for old chunk " + oldestChunkId + " to save memory");
                } else {
                    Log.d(TAG, "Kept audio for old chunk " + oldestChunkId + " since it has speakers");
                    break;
                }
            }
        }
    }
    
    /**
     * Store diarization segments for a specific chunk.
     * 
     * @param chunkId ID of the chunk
     * @param segments Diarization segments for the chunk
     */
    private void storeChunkSegments(int chunkId, OfflineSpeakerDiarizationSegment[] segments) {
        // Debug log segments
        StringBuilder sb = new StringBuilder();
        sb.append("Segments for chunk ").append(chunkId).append(":\n");
        for (int i = 0; i < segments.length; i++) {
            OfflineSpeakerDiarizationSegment segment = segments[i];
            sb.append("   Speaker ").append(segment.getSpeakerId())
              .append(": ").append(String.format("%.2f", segment.getStartTime()))
              .append("-").append(String.format("%.2f", segment.getEndTime()))
              .append(" (").append(String.format("%.2f", segment.getEndTime() - segment.getStartTime()))
              .append("s)\n");
        }
        Log.d(TAG, sb.toString());
        
        // Store the segments
        chunkSegments.put(chunkId, segments);
        
        // Manage memory by removing older chunks if we have too many
        if (chunkSegments.size() > 30) {  // Increased from 20 to 30
            List<Integer> sortedChunkIds = new ArrayList<>(chunkSegments.keySet());
            Collections.sort(sortedChunkIds);
            
            // Remove the oldest chunks until we're at 25
            while (sortedChunkIds.size() > 25) {  // Increased from 15 to 25
                int oldestChunkId = sortedChunkIds.get(0);
                chunkSegments.remove(oldestChunkId);
                sortedChunkIds.remove(0);
                Log.d(TAG, "Removed segments for old chunk " + oldestChunkId + " to save memory");
            }
        }
    }
    
    /**
     * Extract embeddings for each unique speaker in the segments.
     * 
     * @param chunkId ID of the chunk being processed
     * @param segments Diarization segments for the chunk
     * @return Map of speaker IDs to their embedding vectors
     */
    private Map<Integer, float[]> extractSpeakerEmbeddings(int chunkId, OfflineSpeakerDiarizationSegment[] segments) {
        Map<Integer, float[]> speakerEmbeddings = new HashMap<>();
        
        if (segments == null || segments.length == 0) {
            Log.w(TAG, "No segments to extract embeddings from");
            return speakerEmbeddings;
        }
        
        float[] chunkAudio = chunkAudioBuffers.get(chunkId);
        if (chunkAudio == null) {
            Log.e(TAG, "No audio found for chunk " + chunkId);
            return speakerEmbeddings;
        }
        
        // Get unique speaker IDs from segments
        Map<Integer, List<OfflineSpeakerDiarizationSegment>> speakerSegments = new HashMap<>();
        for (OfflineSpeakerDiarizationSegment segment : segments) {
            int speakerId = segment.getSpeakerId();
            if (!speakerSegments.containsKey(speakerId)) {
                speakerSegments.put(speakerId, new ArrayList<>());
            }
            speakerSegments.get(speakerId).add(segment);
        }
        
        // For each speaker, combine their segments and extract an embedding
        for (Map.Entry<Integer, List<OfflineSpeakerDiarizationSegment>> entry : speakerSegments.entrySet()) {
            int speakerId = entry.getKey();
            List<OfflineSpeakerDiarizationSegment> speakerSegmentList = entry.getValue();
            
            // Skip if no segments
            if (speakerSegmentList.isEmpty()) {
                continue;
            }
            
            // Combine all audio for this speaker
            List<Float> speakerAudioList = new ArrayList<>();
            
            for (OfflineSpeakerDiarizationSegment segment : speakerSegmentList) {
                float startTime = segment.getStartTime();
                float endTime = segment.getEndTime();
                
                // Convert time to samples
                int startSample = Math.max(0, (int)(startTime * TARGET_SAMPLE_RATE));
                int endSample = Math.min(chunkAudio.length, (int)(endTime * TARGET_SAMPLE_RATE));
                
                // Add samples to list
                if (startSample < endSample && startSample < chunkAudio.length) {
                    for (int i = startSample; i < endSample && i < chunkAudio.length; i++) {
                        speakerAudioList.add(chunkAudio[i]);
                    }
                }
            }
            
            // Convert to array
            float[] speakerAudio = new float[speakerAudioList.size()];
            for (int i = 0; i < speakerAudioList.size(); i++) {
                speakerAudio[i] = speakerAudioList.get(i);
            }
            
            // Skip if we don't have enough audio
            if (speakerAudio.length < TARGET_SAMPLE_RATE * 0.5) { // Require at least 0.5 second
                Log.w(TAG, "Not enough audio for speaker " + speakerId + " in chunk " + chunkId);
                continue;
            }
            
            try {
                // Extract embedding using Sherpa ONNX
                float[] embedding = SpeakerDiarizationManager.extractSpeakerEmbedding(speakerAudio);
                
                if (embedding != null && embedding.length > 0) {
                    Log.d(TAG, "Extracted embedding for speaker " + speakerId + 
                          " in chunk " + chunkId + " from " + speakerAudio.length + " samples");
                    speakerEmbeddings.put(speakerId, embedding);
                } else {
                    Log.e(TAG, "Failed to extract embedding for speaker " + speakerId + 
                          " in chunk " + chunkId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting embedding for speaker " + speakerId, e);
            }
        }
        
        // Store the embeddings for this chunk
        chunkSpeakerEmbeddings.put(chunkId, speakerEmbeddings);
        
        return speakerEmbeddings;
    }
    
    /**
     * Extract audio for a specific speaker from a specific chunk.
     * 
     * @param chunkId ID of the chunk
     * @param speakerId ID of the speaker
     * @return Audio samples for the speaker, or empty array if none found
     */
    public float[] extractSpeakerAudio(int chunkId, int speakerId) {
        Log.d(TAG, "Extracting audio for speaker " + speakerId + " from chunk " + chunkId);
        
        // First check - do we have the chunk audio available?
        float[] chunkAudio = chunkAudioBuffers.get(chunkId);
        if (chunkAudio == null) {
            Log.e(TAG, "No audio found for chunk " + chunkId + " (likely already cleaned up)");
            return new float[0];
        }
        
        // Debug info about audio buffer
        Log.d(TAG, "Chunk " + chunkId + " audio buffer length: " + chunkAudio.length + " samples");
        
        // Get segments for this chunk
        OfflineSpeakerDiarizationSegment[] segments = chunkSegments.get(chunkId);
        if (segments == null) {
            Log.e(TAG, "No segments found for chunk " + chunkId + " (likely already cleaned up)");
            return new float[0];
        }
        
        // Check if we have segments at all
        if (segments.length == 0) {
            Log.e(TAG, "Chunk " + chunkId + " has no segments at all");
            return new float[0];
        }
        
        Log.d(TAG, "Chunk " + chunkId + " has " + segments.length + " segments");
        
        // Check if the speaker ID is a global ID
        boolean isGlobalId = false;
        int localSpeakerId = speakerId;
        
        // If this is a global ID, convert to local ID for this chunk
        Map<Integer, Integer> localToGlobalMap = chunkLocalToGlobalSpeakerIds.get(chunkId);
        if (localToGlobalMap != null) {
            Log.d(TAG, "Mapping info for chunk " + chunkId + ": " + localToGlobalMap.toString());
            
            for (Map.Entry<Integer, Integer> entry : localToGlobalMap.entrySet()) {
                if (entry.getValue() == speakerId) {
                    localSpeakerId = entry.getKey();
                    isGlobalId = true;
                    Log.d(TAG, "Converted global speaker ID " + speakerId + 
                          " to local ID " + localSpeakerId + " for chunk " + chunkId);
                    break;
                }
            }
        } else {
            Log.w(TAG, "No speaker ID mapping found for chunk " + chunkId);
        }
        
        // If we specified a global ID but couldn't find it in this chunk
        if (isGlobalId && localSpeakerId == speakerId) {
            Log.w(TAG, "Global speaker ID " + speakerId + " not found in chunk " + chunkId);
            
            // Fallback: Try using the global ID directly
            Log.d(TAG, "Attempting fallback: Using global ID directly as local ID");
            localSpeakerId = speakerId;
        }
        
        // Log all segments to help debug
        for (OfflineSpeakerDiarizationSegment segment : segments) {
            Log.d(TAG, "Segment in chunk " + chunkId + ": " +
                  "speakerId=" + segment.getSpeakerId() +
                  ", start=" + segment.getStartTime() +
                  ", end=" + segment.getEndTime());
        }
        
        // Collect all audio segments for this speaker
        List<Float> speakerAudioList = new ArrayList<>();
        boolean foundSpeaker = false;
        
        for (OfflineSpeakerDiarizationSegment segment : segments) {
            if (segment.getSpeakerId() == localSpeakerId) {
                foundSpeaker = true;
                float startTime = segment.getStartTime();
                float endTime = segment.getEndTime();
                
                // Convert time to samples
                int startSample = Math.max(0, (int)(startTime * TARGET_SAMPLE_RATE));
                int endSample = Math.min(chunkAudio.length, (int)(endTime * TARGET_SAMPLE_RATE));
                
                Log.d(TAG, String.format("Adding samples for speaker %d, time %.2f-%.2f, samples %d-%d",
                        localSpeakerId, segment.getStartTime(), segment.getEndTime(), startSample, endSample));
                
                // Add samples to list
                if (startSample < endSample && startSample < chunkAudio.length) {
                    for (int i = startSample; i < endSample && i < chunkAudio.length; i++) {
                        speakerAudioList.add(chunkAudio[i]);
                    }
                }
            }
        }
        
        if (!foundSpeaker) {
            Log.e(TAG, "Speaker " + localSpeakerId + " not found in segments for chunk " + chunkId);
            
            // Fallback: If using global ID didn't work, try all speakers
            if (speakerAudioList.isEmpty() && segments.length > 0) {
                Log.d(TAG, "Fallback: Returning audio from first speaker in chunk as last resort");
                int firstSpeaker = segments[0].getSpeakerId();
                
                for (OfflineSpeakerDiarizationSegment segment : segments) {
                    if (segment.getSpeakerId() == firstSpeaker) {
                        float startTime = segment.getStartTime();
                        float endTime = segment.getEndTime();
                        
                        // Convert time to samples
                        int startSample = Math.max(0, (int)(startTime * TARGET_SAMPLE_RATE));
                        int endSample = Math.min(chunkAudio.length, (int)(endTime * TARGET_SAMPLE_RATE));
                        
                        // Add samples to list
                        if (startSample < endSample && startSample < chunkAudio.length) {
                            for (int i = startSample; i < endSample && i < chunkAudio.length; i++) {
                                speakerAudioList.add(chunkAudio[i]);
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Fallback extracted " + speakerAudioList.size() + 
                      " samples from first speaker (ID: " + firstSpeaker + ")");
            }
        }
        
        if (speakerAudioList.isEmpty()) {
            Log.e(TAG, "No audio segments found for speaker " + localSpeakerId + " in chunk " + chunkId);
            
            // Extreme fallback: just return the first second of audio from this chunk
            if (chunkAudio.length > TARGET_SAMPLE_RATE) {
                Log.d(TAG, "Extreme fallback: Returning first second of chunk audio");
                float[] fallbackAudio = new float[TARGET_SAMPLE_RATE];
                System.arraycopy(chunkAudio, 0, fallbackAudio, 0, TARGET_SAMPLE_RATE);
                return fallbackAudio;
            }
            
            return new float[0];
        }
        
        // Convert to array
        float[] speakerAudio = new float[speakerAudioList.size()];
        for (int i = 0; i < speakerAudioList.size(); i++) {
            speakerAudio[i] = speakerAudioList.get(i);
        }
        
        Log.d(TAG, "Extracted " + speakerAudio.length + " samples for speaker " + localSpeakerId + 
              " from chunk " + chunkId);
        
        return speakerAudio;
    }
    
    /**
     * Get the sample rate of the audio.
     * 
     * @return Sample rate in Hz
     */
    public int getSampleRate() {
        return TARGET_SAMPLE_RATE;
    }
    
    /**
     * Analyze speaker segments to create speaker info objects.
     * 
     * @param chunkId ID of the chunk being analyzed
     */
    private void analyzeSpeakers(int chunkId) {
        Log.d(TAG, "Analyzing speakers from segments for chunk " + chunkId);
        if (currentSegments == null || currentSegments.length == 0) {
            Log.w(TAG, "No segments to analyze in chunk " + chunkId);
            
            // Add empty list for this chunk
            List<SpeakerInfo> emptySpeakers = new ArrayList<>();
            addChunkToHistory(chunkId, emptySpeakers);
            return;
        }
        
        // Get local to global ID mapping for this chunk
        Map<Integer, Integer> localToGlobalMap = chunkLocalToGlobalSpeakerIds.get(chunkId);
        if (localToGlobalMap == null) {
            localToGlobalMap = new HashMap<>(); // Empty map if no mapping available
            Log.w(TAG, "No speaker ID mapping found for chunk " + chunkId);
        }
        
        // Create a final copy for use in the lambda expression
        final Map<Integer, Integer> finalLocalToGlobalMap = localToGlobalMap;
        
        // Calculate total duration for each speaker
        Map<Integer, Float> speakerDurations = new HashMap<>();
        
        for (OfflineSpeakerDiarizationSegment segment : currentSegments) {
            int localSpeakerId = segment.getSpeakerId();
            float duration = segment.getEndTime() - segment.getStartTime();
            
            speakerDurations.put(
                    localSpeakerId, 
                    speakerDurations.getOrDefault(localSpeakerId, 0f) + duration
            );
        }
        
        // Create speaker info objects with local and global IDs
        List<SpeakerInfo> speakers = speakerDurations.entrySet().stream()
                .map(entry -> {
                    int localId = entry.getKey();
                    float duration = entry.getValue();
                    // Get global ID or use local ID if not found
                    int globalId = finalLocalToGlobalMap.getOrDefault(localId, localId);
                    return new SpeakerInfo(localId, duration, chunkId, globalId);
                })
                .sorted((a, b) -> Float.compare(b.getDuration(), a.getDuration())) // Sort by duration (descending)
                .collect(Collectors.toList());
        
        // Store speakers for this chunk and maintain history
        addChunkToHistory(chunkId, speakers);
        
        // Notify listener
        if (listener != null) {
            final List<List<SpeakerInfo>> speakersHistory = new ArrayList<>(chunkSpeakerHistory);
            mainHandler.post(() -> listener.onSpeakersHistoryUpdated(speakersHistory));
        }
    }
    
    /**
     * Add chunk speakers to history, keeping most recent chunks
     */
    private void addChunkToHistory(int chunkId, List<SpeakerInfo> speakers) {
        // Add new chunk
        chunkSpeakerHistory.add(speakers);
        
        // Keep only the last 10 chunks
        while (chunkSpeakerHistory.size() > 10) {
            chunkSpeakerHistory.remove(0);
        }
        
        // Update current speakers (all speakers from the latest chunk)
        detectedSpeakers = speakers;
        
        // Request runtime garbage collection (just a hint)
        System.gc();
    }
    
    /**
     * Get the list of currently detected speakers.
     * 
     * @return List of SpeakerInfo objects
     */
    public List<SpeakerInfo> getDetectedSpeakers() {
        return new ArrayList<>(detectedSpeakers);
    }
    
    /**
     * Get the current diarization segments.
     * 
     * @return Array of diarization segments
     */
    public OfflineSpeakerDiarizationSegment[] getCurrentSegments() {
        return currentSegments;
    }
    
    /**
     * Get the number of detected speakers.
     * 
     * @return Number of speakers
     */
    public int getSpeakerCount() {
        return detectedSpeakers.size();
    }
    
    /**
     * Get the number of global speakers identified across all chunks.
     * 
     * @return Number of unique speakers identified
     */
    public int getGlobalSpeakerCount() {
        return embeddingMatcher.getGlobalSpeakerCount();
    }
    
    /**
     * Get buffer fill percentage (0.0 - 1.0).
     * 
     * @return Buffer fill percentage
     */
    /*public float getBufferFillPercentage() {
        return Math.min(1.0f, audioBuffer.size() / (float)CHUNK_SIZE);
    }*/
    
    /**
     * Update the buffer status through the listener.
     */
    /*private void updateBufferStatus() {
        if (listener != null) {
            float percentage = getBufferFillPercentage();
            // Make sure percentage is between 0-100%
            float progressPercent = Math.min(100.0f, percentage * 100);
            mainHandler.post(() -> listener.onBufferFillProgress(progressPercent));
        }
    }*/
    
    /**
     * Clean up resources associated with diarization.
     * This performs a thorough cleanup of memory.
     */
    public void cleanup() {
        audioBuffer.clear();
        detectedSpeakers.clear();
        chunkSpeakerHistory.clear();
        currentSegments = new OfflineSpeakerDiarizationSegment[0];
        
        // Clean up stored audio and segments
        chunkAudioBuffers.clear();
        chunkSegments.clear();
        chunkSpeakerEmbeddings.clear();
        chunkLocalToGlobalSpeakerIds.clear();
        
        // Reset embedding matcher
        embeddingMatcher.reset();
        
        // Reset counters
        currentChunkId = 0;
        
        // Log cleanup
        Log.d(TAG, "Performed full memory cleanup");
    }
    
    /**
     * Release resources associated with diarization.
     */
    public void release() {
        if (isInitialized) {
            // Clean up memory
            cleanup();
            
            // Shut down executor service
            executorService.shutdown();
            
            // Release native resources
            SpeakerDiarizationManager.release();
            
            isInitialized = false;
            Log.i(TAG, "Released all resources");
        }
    }
    
    /**
     * Get the full audio for a specific chunk.
     * 
     * @param chunkId ID of the chunk
     * @return Float array containing the chunk's audio
     */
    protected float[] getChunkAudio(int chunkId) {
        float[] chunkAudio = chunkAudioBuffers.get(chunkId);
        if (chunkAudio == null) {
            Log.e(TAG, "No audio available for chunk " + chunkId);
            return new float[0];
        }
        
        // Return a copy to avoid modification
        return Arrays.copyOf(chunkAudio, chunkAudio.length);
    }
    
    /**
     * Get a list of all chunk IDs that are currently available in memory.
     * 
     * @return List of available chunk IDs
     */
    public List<Integer> getAvailableChunkIds() {
        // Create a list of chunk IDs that have both audio and segments available
        List<Integer> availableChunks = new ArrayList<>();
        
        // Get all chunk IDs that have audio
        Set<Integer> audioChunks = new HashSet<>(chunkAudioBuffers.keySet());
        
        // Get all chunk IDs that have segments
        Set<Integer> segmentChunks = new HashSet<>(chunkSegments.keySet());
        
        // Find the intersection (chunks that have both)
        audioChunks.retainAll(segmentChunks);
        
        availableChunks.addAll(audioChunks);
        
        // Sort the chunks by ID (chronological order)
        Collections.sort(availableChunks);
        
        Log.d(TAG, "Available chunks: " + availableChunks);
        
        return availableChunks;
    }
    
    /**
     * Get the underlying SpeakerEmbeddingMatcher used by this manager.
     * This can be used to register enrolled speakers directly.
     * 
     * @return The speaker embedding matcher
     */
    public SpeakerEmbeddingMatcher getEmbeddingMatcher() {
        return embeddingMatcher;
    }
    
    /**
     * Get the list of active speakers for the current frame.
     * This is useful for real-time speaker isolation.
     * 
     * @return List of currently active speakers
     */
    public List<SpeakerInfo> getActiveSpeakersForFrame() {
        List<SpeakerInfo> result = new ArrayList<>();
        
        // Get the current time in the latest segment
        if (chunkSegments.isEmpty() || currentChunkId == 0) {
            return result;
        }
        
        OfflineSpeakerDiarizationSegment[] segments = chunkSegments.get(currentChunkId);
        if (segments == null || segments.length == 0) {
            return result;
        }
        
        // Calculate the current time within the chunk (as a fraction of CHUNK_SIZE_SECONDS)
        // For real-time processing, we assume we're at the end of the most recent chunk
        float currentTime = CHUNK_SIZE_SECONDS;
        
        // Find segments that include the current time
        for (OfflineSpeakerDiarizationSegment segment : segments) {
            // Access segment properties using appropriate getters based on the actual API
            float startTime = segment.getStartTime();
            float endTime = segment.getEndTime();
            
            if (startTime <= currentTime && endTime >= currentTime) {
                // This speaker is active at the current time
                int localSpeakerId = segment.getSpeakerId();
                
                // Map to global ID
                Map<Integer, Integer> localToGlobal = chunkLocalToGlobalSpeakerIds.get(currentChunkId);
                if (localToGlobal != null && localToGlobal.containsKey(localSpeakerId)) {
                    int globalId = localToGlobal.get(localSpeakerId);
                    
                    // Add to result
                    SpeakerInfo speakerInfo = new SpeakerInfo(
                            localSpeakerId,
                            endTime - startTime,
                            currentChunkId,
                            globalId
                    );
                    result.add(speakerInfo);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Register an enrolled speaker with the embedding matcher.
     * 
     * @param name Name of the enrolled speaker
     * @param embedding Embedding vector for the speaker
     * @return The global ID assigned to this speaker
     */
    public int registerEnrolledSpeaker(String name, float[] embedding) {
        if (embeddingMatcher != null && embedding != null) {
            int speakerId = embeddingMatcher.registerSpeakerEmbedding(embedding, name, true);
            Log.i(TAG, "Registered enrolled speaker '" + name + "' with ID " + speakerId);
            return speakerId;
        }
        return -1;
    }
    
    /**
     * Clear all enrolled speakers from the matcher.
     */
    public void clearEnrolledSpeakers() {
        if (embeddingMatcher != null) {
            embeddingMatcher.clearEnrolledSpeakers();
            Log.i(TAG, "Cleared all enrolled speakers");
        }
    }
    
    /**
     * Check if a detected speaker matches an enrolled speaker embedding.
     * 
     * @param speakerId The global ID of the detected speaker
     * @param embedding The embedding of the enrolled speaker to compare with
     * @return true if they match, false otherwise
     */
    public boolean isSpeakerMatchingEnrollment(int speakerId, float[] embedding) {
        if (embeddingMatcher != null && embedding != null) {
            return embeddingMatcher.isSpeakerMatchingEnrollment(speakerId, embedding);
        }
        return false;
    }
    
    /**
     * Get the current chunk ID being processed.
     * 
     * @return The current chunk ID
     */
    public int getCurrentChunkId() {
        return currentChunkId;
    }
    
    /**
     * Get the active speakers for a specific chunk.
     * 
     * @param chunkId The ID of the chunk to get speakers for
     * @return List of speaker info objects for the chunk
     */
    public List<SpeakerInfo> getActiveSpeakers(int chunkId) {
        if (chunkSpeakerHistory.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Find the chunk in the history
        for (List<SpeakerInfo> speakers : chunkSpeakerHistory) {
            if (!speakers.isEmpty() && speakers.get(0).getChunkId() == chunkId) {
                return speakers;
            }
        }
        
        // If not found, use the most recent chunk as a fallback
        if (!chunkSpeakerHistory.isEmpty()) {
            return chunkSpeakerHistory.get(chunkSpeakerHistory.size() - 1);
        }
        
        return new ArrayList<>();
    }
} 