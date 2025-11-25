package com.audion.app;

import com.audion.app.R;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.audion.app.diarization.DirectDiarizationManager;
import com.audion.app.diarization.DirectDiarizationManager.SpeakerInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for displaying detected speakers in a RecyclerView.
 */
public class SpeakerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final String TAG = "SpeakerAdapter";
    private static final int VIEW_TYPE_CHUNK_HEADER = 0;
    private static final int VIEW_TYPE_SPEAKER = 1;
    
    private List<Object> items = new ArrayList<>();
    private DirectDiarizationManager diarizationManager;
    private AudioTrack currentAudioTrack;
    private SpeakerSelectionListener selectionListener;
    private Integer selectedSpeakerId = null;
    
    // Track current global speakers to display them with consistent colors
    private Map<Integer, Integer> globalSpeakerColorMap = new HashMap<>();
    private int nextColorIndex = 0;
    private int[] speakerColors = {
        0xFF4285F4, // Blue
        0xFFEA4335, // Red
        0xFFFFBF00, // Yellow
        0xFF34A853, // Green
        0xFF9C27B0, // Purple
        0xFFFF9800, // Orange
        0xFF00BCD4, // Cyan
        0xFFF44336, // Deep Red
        0xFF795548, // Brown
        0xFF607D8B  // Gray
    };
    
    /**
     * Interface for speaker selection callbacks.
     */
    public interface SpeakerSelectionListener {
        void onSpeakerSelected(int speakerId);
        void onSpeakerDeselected();
    }
    
    /**
     * Get the global speaker color map for sharing with other components.
     * 
     * @return Map of global speaker IDs to colors
     */
    public Map<Integer, Integer> getGlobalSpeakerColorMap() {
        return globalSpeakerColorMap;
    }
    
    /**
     * Set the selected speaker ID.
     * 
     * @param speakerId The speaker ID to select, or null to deselect
     */
    public void setSelectedSpeakerId(Integer speakerId) {
        this.selectedSpeakerId = speakerId;
        notifyDataSetChanged();
    }
    
    /**
     * Constructor with diarization manager.
     */
    public SpeakerAdapter(DirectDiarizationManager diarizationManager) {
        this.diarizationManager = diarizationManager;
    }
    
    /**
     * Set listener for speaker selection events.
     */
    public void setSelectionListener(SpeakerSelectionListener listener) {
        this.selectionListener = listener;
    }
    
    /**
     * Update the list of speakers with a history of speaker chunks.
     * 
     * @param speakerHistory List of speaker lists, each representing a chunk
     */
    public void updateSpeakerHistory(List<List<SpeakerInfo>> speakerHistory) {
        // Clear existing data
        items.clear();
        
        // Don't process empty history
        if (speakerHistory == null || speakerHistory.isEmpty()) {
            notifyDataSetChanged();
            return;
        }
        
        // Estimate capacity to avoid reallocations
        int estimatedSize = speakerHistory.size() * 5; // Assuming ~5 items per chunk (header + speakers)
        if (items instanceof ArrayList) {
            ((ArrayList<Object>) items).ensureCapacity(estimatedSize);
        }
        
        // Process in reverse order (newest chunks first)
        for (int i = speakerHistory.size() - 1; i >= 0; i--) {
            List<SpeakerInfo> chunk = speakerHistory.get(i);
            
            // Only add chunks that have speakers
            if (!chunk.isEmpty()) {
                // Add chunk header
                ChunkHeader header = new ChunkHeader(i, chunk.get(0).getChunkId());
                items.add(header);
                
                // Add speakers in this chunk
                items.addAll(chunk);
            }
        }
        
        notifyDataSetChanged();
    }
    
    /**
     * Update with a simple list of speakers (for backward compatibility).
     * 
     * @param speakers List of speakers
     */
    public void updateSpeakers(List<SpeakerInfo> speakers) {
        items.clear();
        if (!speakers.isEmpty()) {
            items.add(new ChunkHeader(0, speakers.get(0).getChunkId()));
            items.addAll(speakers);
        }
        notifyDataSetChanged();
    }
    
    /**
     * Get a consistent color for a global speaker ID.
     * 
     * @param globalSpeakerId The global ID of the speaker
     * @return Color integer to use for this speaker
     */
    private int getSpeakerColor(int globalSpeakerId) {
        if (!globalSpeakerColorMap.containsKey(globalSpeakerId)) {
            int colorIndex = nextColorIndex % speakerColors.length;
            globalSpeakerColorMap.put(globalSpeakerId, speakerColors[colorIndex]);
            nextColorIndex++;
        }
        return globalSpeakerColorMap.get(globalSpeakerId);
    }
    
    @Override
    public int getItemViewType(int position) {
        return (items.get(position) instanceof ChunkHeader) 
            ? VIEW_TYPE_CHUNK_HEADER 
            : VIEW_TYPE_SPEAKER;
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        if (viewType == VIEW_TYPE_CHUNK_HEADER) {
            View view = inflater.inflate(R.layout.item_chunk_header, parent, false);
            return new ChunkHeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_speaker, parent, false);
            return new SpeakerViewHolder(view);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ChunkHeaderViewHolder) {
            ChunkHeader header = (ChunkHeader) items.get(position);
            ((ChunkHeaderViewHolder) holder).bind(header);
        } else {
            SpeakerInfo speaker = (SpeakerInfo) items.get(position);
            ((SpeakerViewHolder) holder).bind(speaker);
        }
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * Stop current audio playback.
     */
    public void stopPlayback() {
        if (currentAudioTrack != null) {
            try {
                currentAudioTrack.stop();
                currentAudioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping playback", e);
            }
            currentAudioTrack = null;
        }
    }
    
    /**
     * Release resources.
     */
    public void release() {
        stopPlayback();
        globalSpeakerColorMap.clear();
        selectedSpeakerId = null;
    }
    
    /**
     * Reset color mapping when stopping processing.
     */
    public void resetColorMapping() {
        globalSpeakerColorMap.clear();
        nextColorIndex = 0;
        selectedSpeakerId = null;
        
        if (selectionListener != null) {
            selectionListener.onSpeakerDeselected();
        }
    }
    
    /**
     * ViewHolder for chunk headers.
     */
    static class ChunkHeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView chunkHeaderText;
        
        public ChunkHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            chunkHeaderText = itemView.findViewById(R.id.chunkHeaderText);
        }
        
        public void bind(ChunkHeader header) {
            chunkHeaderText.setText("Chunk " + header.getChunkId());
        }
    }
    
    /**
     * ViewHolder for speaker items.
     */
    class SpeakerViewHolder extends RecyclerView.ViewHolder {
        private final TextView speakerIdText;
        private final ImageButton playButton;
        private final RadioButton speakerRadioButton;
        
        public SpeakerViewHolder(@NonNull View itemView) {
            super(itemView);
            speakerIdText = itemView.findViewById(R.id.speakerIdText);
            playButton = itemView.findViewById(R.id.speakerPlayButton);
            speakerRadioButton = itemView.findViewById(R.id.speakerRadioButton);
        }
        
        public void bind(SpeakerInfo speaker) {
            // Use global ID for display
            int globalId = speaker.getGlobalId();
            speakerIdText.setText("Speaker " + globalId);
            
            // Set up play button
            playButton.setOnClickListener(v -> playSpeakerAudio(speaker));
            
            // Handle speaker isolation selection
            boolean isSelected = selectedSpeakerId != null && selectedSpeakerId == globalId;
            if (speakerRadioButton != null) {
                speakerRadioButton.setChecked(isSelected);
            }
            
            // Handle speaker selection for isolation
            itemView.setOnClickListener(v -> {
                if (isSelected) {
                    // Deselect
                    selectedSpeakerId = null;
                    if (selectionListener != null) {
                        selectionListener.onSpeakerDeselected();
                    }
                } else {
                    // Select new speaker and deselect any previously selected
                    selectedSpeakerId = globalId;
                    if (selectionListener != null) {
                        selectionListener.onSpeakerSelected(globalId);
                    }
                }
                notifyDataSetChanged(); // Refresh all views to update selection state
            });
        }
        
        private void playSpeakerAudio(SpeakerInfo speaker) {
            if (diarizationManager == null) {
                Toast.makeText(itemView.getContext(), "Diarization manager not available", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Stop any current playback
            stopPlayback();
            
            // Extract audio for this speaker from its chunk
            // Note: Use global ID for extraction if we're using cross-chunk identification
            float[] speakerAudio = diarizationManager.extractSpeakerAudio(speaker.getChunkId(), speaker.getGlobalId());
            
            if (speakerAudio == null || speakerAudio.length == 0) {
                Toast.makeText(itemView.getContext(), "No audio available for this speaker", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Get the sample rate
            int sampleRate = diarizationManager.getSampleRate();
            
            // Create an audio track for playback
            int minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT);
            
            currentAudioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(minBufferSize, speakerAudio.length * 4))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            
            // Set up completion callback
            currentAudioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack track) {
                    Log.d(TAG, "Audio playback completed");
                    if (track != null) {
                        track.release();
                        if (track == currentAudioTrack) {
                            currentAudioTrack = null;
                        }
                    }
                }
                
                @Override
                public void onPeriodicNotification(AudioTrack track) {
                    // Not used
                }
            });
            
            try {
                // Write audio data to track
                currentAudioTrack.write(speakerAudio, 0, speakerAudio.length, AudioTrack.WRITE_BLOCKING);
                
                // Set notification marker at end
                currentAudioTrack.setNotificationMarkerPosition(speakerAudio.length);
                
                // Start playback
                currentAudioTrack.play();
                
                // Toast to indicate playing
                Toast.makeText(itemView.getContext(), 
                       "Playing Speaker " + speaker.getGlobalId() + " (" + String.format("%.1f", speaker.getDuration()) + "s)", 
                       Toast.LENGTH_SHORT).show();
                
            } catch (Exception e) {
                Log.e(TAG, "Error playing speaker audio", e);
                Toast.makeText(itemView.getContext(), "Error playing audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                if (currentAudioTrack != null) {
                    currentAudioTrack.release();
                    currentAudioTrack = null;
                }
            }
        }
    }
    
    /**
     * Class to represent a chunk header in the list.
     */
    private static class ChunkHeader {
        private final int index;
        private final int chunkId;
        
        public ChunkHeader(int index, int chunkId) {
            this.index = index;
            this.chunkId = chunkId;
        }
        
        public int getIndex() {
            return index;
        }
        
        public int getChunkId() {
            return chunkId;
        }
    }
} 