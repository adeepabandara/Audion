package com.example.audion;

import android.content.Context;
import android.util.Log;

import com.example.audion.diarization.DirectDiarizationManager;

/**
 * Singleton manager for Focus Mode speaker isolation.
 * Provides shared access to diarization manager and selected speaker state
 * between FocusActivity and SimpleAudioStreamingService/SimpleAudioEngine.
 */
public class FocusModeManager {
    private static final String TAG = "FocusModeManager";
    private static FocusModeManager instance;
    
    private DirectDiarizationManager diarizationManager;
    private float[] selectedSpeakerEmbedding;
    private boolean focusModeActive = false;
    
    private FocusModeManager() {
        // Private constructor for singleton
    }
    
    public static synchronized FocusModeManager getInstance() {
        if (instance == null) {
            instance = new FocusModeManager();
        }
        return instance;
    }
    
    /**
     * Initialize Focus Mode with diarization manager.
     * Called by FocusActivity when starting processing.
     */
    public synchronized void initialize(Context context, DirectDiarizationManager manager) {
        this.diarizationManager = manager;
        this.focusModeActive = true;
        Log.i(TAG, "Focus Mode initialized");
    }
    
    /**
     * Set the selected speaker embedding for isolation.
     */
    public synchronized void setSelectedSpeakerEmbedding(float[] embedding) {
        this.selectedSpeakerEmbedding = embedding;
        Log.i(TAG, "Selected speaker embedding updated (length=" + 
            (embedding != null ? embedding.length : 0) + ")");
    }
    
    /**
     * Shutdown Focus Mode.
     * Called by FocusActivity when stopping processing.
     */
    public synchronized void shutdown() {
        this.focusModeActive = false;
        this.selectedSpeakerEmbedding = null;
        // Don't null out diarizationManager as FocusActivity manages its lifecycle
        Log.i(TAG, "Focus Mode shutdown");
    }
    
    /**
     * Check if the selected speaker is currently active.
     * This is called by SimpleAudioEngine on the audio processing thread.
     * 
     * @return true if selected speaker is active, false otherwise
     */
    public synchronized boolean isSelectedSpeakerActive() {
        if (!focusModeActive || diarizationManager == null || selectedSpeakerEmbedding == null) {
            Log.d(TAG, "isSelectedSpeakerActive: returning true (default) - focusModeActive=" + focusModeActive + 
                ", diarizationManager=" + (diarizationManager != null) + 
                ", selectedSpeakerEmbedding=" + (selectedSpeakerEmbedding != null));
            return true; // Default to true (don't mute) if not in focus mode
        }
        
        try {
            int currentChunkId = diarizationManager.getCurrentChunkId();
            var activeSpeakers = diarizationManager.getActiveSpeakers(currentChunkId);
            
            Log.d(TAG, "isSelectedSpeakerActive: chunkId=" + currentChunkId + 
                ", activeSpeakers count=" + activeSpeakers.size());
            
            if (activeSpeakers.isEmpty()) {
                Log.d(TAG, "isSelectedSpeakerActive: No active speakers - returning false (mute)");
                return false; // No active speakers - mute
            }
            
            // Check if any active speaker matches the selected embedding
            for (var speaker : activeSpeakers) {
                boolean matches = diarizationManager.isSpeakerMatchingEnrollment(
                        speaker.getGlobalId(), 
                        selectedSpeakerEmbedding);
                Log.d(TAG, "isSelectedSpeakerActive: checking speaker globalId=" + speaker.getGlobalId() + 
                    ", matches=" + matches);
                if (matches) {
                    Log.d(TAG, "isSelectedSpeakerActive: Selected speaker IS active - returning true (amplify)");
                    return true; // Selected speaker is active
                }
            }
            
            Log.d(TAG, "isSelectedSpeakerActive: Selected speaker NOT active - returning false (mute)");
            return false; // Selected speaker not among active speakers - mute
            
        } catch (Exception e) {
            Log.w(TAG, "Error checking speaker activity: " + e.getMessage());
            return true; // On error, don't mute
        }
    }
    
    /**
     * Process audio frame for diarization.
     * Called by SimpleAudioEngine on the audio processing thread.
     */
    public synchronized void processAudioFrame(float[] frame) {
        if (focusModeActive && diarizationManager != null) {
            try {
                diarizationManager.processAudioFrame(frame);
            } catch (Exception e) {
                Log.w(TAG, "Error processing audio frame: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if Focus Mode is currently active.
     */
    public synchronized boolean isFocusModeActive() {
        return focusModeActive;
    }
    
    /**
     * Get the diarization manager instance.
     */
    public synchronized DirectDiarizationManager getDiarizationManager() {
        return diarizationManager;
    }
}
