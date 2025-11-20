// SpeakerSelectionBottomSheet.java
package com.example.audion;

import com.audion.psap.R;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpeakerSelectionBottomSheet extends BottomSheetDialogFragment {

    public static class DetectedSpeaker {
        private int speakerId;
        private String name;
        private float duration;
        private float[] embedding;
        private float[] audioSamples;

        public DetectedSpeaker(int speakerId, String name, float duration, float[] embedding, float[] audioSamples) {
            this.speakerId = speakerId;
            this.name = name;
            this.duration = duration;
            this.embedding = embedding;
            this.audioSamples = audioSamples;
        }

        public int getSpeakerId() { return speakerId; }
        public String getName() { return name; }
        public float getDuration() { return duration; }
        public float[] getEmbedding() { return embedding; }
        public float[] getAudioSamples() { return audioSamples; }
    }

    public interface OnSpeakerSelectedListener {
        void onSpeakerSelected(DetectedSpeaker speaker);
    }

    private List<DetectedSpeaker> speakers = new ArrayList<>();
    private OnSpeakerSelectedListener listener;
    private SpeakerAdapter adapter;
    private int selectedPosition = -1;
    private MaterialButton selectButton;
    private androidx.core.widget.NestedScrollView scrollView;

    public static SpeakerSelectionBottomSheet newInstance(List<DetectedSpeaker> speakers) {
        SpeakerSelectionBottomSheet sheet = new SpeakerSelectionBottomSheet();
        sheet.speakers = speakers;
        return sheet;
    }

    public void setOnSpeakerSelectedListener(OnSpeakerSelectedListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply rounded corner theme
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_speaker_selection_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.speakersRecyclerView);
        selectButton = view.findViewById(R.id.selectButton);
        scrollView = view.findViewById(R.id.speakersScrollView);
        
        // Ensure button starts as disabled with light grey appearance
        selectButton.setEnabled(false);
        selectButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#CCCCCC")));
        selectButton.setTextColor(Color.parseColor("#999999"));

        adapter = new SpeakerAdapter(speakers, position -> {
            selectedPosition = position;
            selectButton.setEnabled(true);
            
            // Enable button appearance - restore primary color
            selectButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#0F766E")));
            selectButton.setTextColor(Color.parseColor("#FFFFFF"));
            
            // Expand bottom sheet and scroll to show the select button
            expandAndScrollToButton();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        selectButton.setOnClickListener(v -> {
            if (selectedPosition != -1 && listener != null) {
                listener.onSpeakerSelected(speakers.get(selectedPosition));
                dismiss();
            }
        });
    }
    
    private void expandAndScrollToButton() {
        // Expand the bottom sheet if collapsed
        if (getDialog() != null && getDialog().getWindow() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                
                // Expand the bottom sheet
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                
                // Scroll to bottom to show the select button
                scrollView.post(() -> {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                });
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Configure bottom sheet behavior to show max 4 speaker cards
        if (getDialog() != null && getDialog().getWindow() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                
                bottomSheet.post(() -> {
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    
                    // Calculate height dynamically based on speaker count
                    // Each card is approximately 68dp (56dp minHeight + 12dp margins)
                    // Header: ~80dp, Button: ~80dp, Total padding: ~32dp
                    float density = getResources().getDisplayMetrics().density;
                    int cardHeight = (int) (68 * density); // One card height
                    int headerHeight = (int) (80 * density);
                    int buttonHeight = (int) (88 * density); // Increased to ensure button is always visible
                    int padding = (int) (32 * density);
                    
                    // Show up to 4 cards without scrolling - button always visible unless >4 speakers
                    int speakerCount = speakers.size();
                    int maxCardsToShow;
                    
                    if (speakerCount <= 4) {
                        // Show all speakers with button visible (no scrolling needed)
                        maxCardsToShow = speakerCount;
                    } else {
                        // Show 3-4 cards with scrolling for the rest, button always visible
                        maxCardsToShow = 3;
                    }
                    
                    int idealHeight = headerHeight + (cardHeight * maxCardsToShow) + buttonHeight + padding;
                    
                    // Cap at 75% of screen height for safety
                    int maxHeight = (int) (screenHeight * 0.75);
                    int peekHeight = Math.min(idealHeight, maxHeight);
                    
                    behavior.setPeekHeight(peekHeight);
                    behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED);
                    behavior.setSkipCollapsed(false);
                    behavior.setDraggable(true);
                    behavior.setHideable(true);
                });
            }
        }
    }

    interface OnItemClickListener {
        void onItemClick(int position);
    }

    private class SpeakerAdapter extends RecyclerView.Adapter<SpeakerAdapter.ViewHolder> {
        private List<DetectedSpeaker> speakers;
        private int selectedPos = -1;
        private OnItemClickListener clickListener;
        private AudioTrack currentAudioTrack = null;
        private ImageButton currentPlayingButton = null;
        private ProgressBar currentProgressBar = null;
        private ViewHolder currentPlayingHolder = null;

        SpeakerAdapter(List<DetectedSpeaker> speakers, OnItemClickListener listener) {
            this.speakers = speakers;
            this.clickListener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_speaker_bottom_sheet, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DetectedSpeaker speaker = speakers.get(position);

            holder.nameText.setText(speaker.getName());
            holder.durationText.setText(String.format(Locale.getDefault(), "Duration: %.1fs", speaker.getDuration()));
            holder.radioButton.setChecked(position == selectedPos);
            
            // Log audio samples info for debugging
            Log.d("SpeakerBottomSheet", "Binding speaker: " + speaker.getName() + 
                  ", Audio samples: " + (speaker.getAudioSamples() != null ? speaker.getAudioSamples().length : "null"));

            View.OnClickListener selectListener = v -> {
                int oldPos = selectedPos;
                selectedPos = holder.getAdapterPosition();
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPos);
                if (clickListener != null) {
                    clickListener.onItemClick(selectedPos);
                }
            };

            holder.itemView.setOnClickListener(selectListener);
            holder.radioButton.setOnClickListener(selectListener);

            // Play button functionality with visual feedback
            holder.playButton.setOnClickListener(v -> {
                ImageButton button = (ImageButton) v;
                
                // If currently playing this speaker, stop it
                if (currentPlayingButton == button && currentAudioTrack != null) {
                    stopCurrentPlayback();
                    return;
                }
                
                // Stop current playback if any
                stopCurrentPlayback();
                
                // Set this button as playing and change color to red
                currentPlayingButton = button;
                currentProgressBar = holder.playbackProgress;
                currentPlayingHolder = holder;
                
                button.getBackground().setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);
                
                // Change icon to stop
                button.setImageResource(R.drawable.ic_stop);
                
                // Show progress indicator and reset progress
                holder.playbackProgress.setVisibility(View.VISIBLE);
                holder.playbackProgress.setProgress(0); // Start from 0%
                
                // Start playback
                playAudioSample(speaker.getAudioSamples(), holder);
            });
        }

        private void stopCurrentPlayback() {
            if (currentAudioTrack != null) {
                try {
                    if (currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        currentAudioTrack.stop();
                    }
                    currentAudioTrack.release();
                } catch (Exception e) {
                    Log.e("SpeakerBottomSheet", "Error stopping audio", e);
                }
                currentAudioTrack = null;
            }
            
            // Reset button color and icon
            if (currentPlayingButton != null) {
                final ImageButton buttonToReset = currentPlayingButton;
                currentPlayingButton.post(() -> {
                    try {
                        if (buttonToReset.getBackground() != null) {
                            buttonToReset.getBackground().clearColorFilter();
                        }
                        buttonToReset.setImageResource(R.drawable.ic_play);
                    } catch (Exception e) {
                        Log.e("SpeakerBottomSheet", "Error resetting button", e);
                    }
                });
                currentPlayingButton = null;
            }
            
            // Hide progress indicator and reset progress
            if (currentProgressBar != null) {
                final ProgressBar progressToHide = currentProgressBar;
                currentProgressBar.post(() -> {
                    try {
                        progressToHide.setVisibility(View.GONE);
                        progressToHide.setProgress(0); // Reset progress
                    } catch (Exception e) {
                        Log.e("SpeakerBottomSheet", "Error hiding progress", e);
                    }
                });
                currentProgressBar = null;
            }
            
            currentPlayingHolder = null;
        }

        private void playAudioSample(float[] audioSamples, ViewHolder holder) {
            if (audioSamples == null || audioSamples.length == 0) {
                Log.w("SpeakerBottomSheet", "No audio samples to play");
                holder.playButton.post(() -> {
                    holder.playButton.getBackground().clearColorFilter();
                    holder.playButton.setImageResource(R.drawable.ic_play);
                    holder.playbackProgress.setVisibility(View.GONE);
                });
                return;
            }

            Log.d("SpeakerBottomSheet", "Playing audio sample with " + audioSamples.length + " samples");

            new Thread(() -> {
                AudioTrack audioTrack = null;
                try {
                    // Convert float samples to short (PCM16)
                    short[] shortSamples = new short[audioSamples.length];
                    for (int i = 0; i < audioSamples.length; i++) {
                        float sample = audioSamples[i];
                        // Clamp to [-1.0, 1.0] and amplify a bit
                        sample = Math.max(-1.0f, Math.min(1.0f, sample * 2.0f));
                        shortSamples[i] = (short) (sample * Short.MAX_VALUE);
                    }

                    int sampleRate = 16000; // Common sample rate for speech
                    int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                    int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

                    int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                    
                    if (minBufferSize == AudioTrack.ERROR_BAD_VALUE || minBufferSize == AudioTrack.ERROR) {
                        Log.e("SpeakerBottomSheet", "Invalid AudioTrack configuration");
                        holder.playButton.post(() -> {
                            holder.playButton.getBackground().clearColorFilter();
                            holder.playButton.setImageResource(R.drawable.ic_play);
                            holder.playbackProgress.setVisibility(View.GONE);
                        });
                        return;
                    }
                    
                    // Use MODE_STREAM for better compatibility
                    int bufferSize = Math.max(minBufferSize * 4, shortSamples.length * 2);
                    
                    Log.d("SpeakerBottomSheet", "Creating AudioTrack - minBuffer: " + minBufferSize + ", bufferSize: " + bufferSize);
                    
                    audioTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            bufferSize,
                            AudioTrack.MODE_STREAM
                    );

                    if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                        Log.e("SpeakerBottomSheet", "AudioTrack not initialized properly - State: " + audioTrack.getState());
                        holder.playButton.post(() -> {
                            holder.playButton.getBackground().clearColorFilter();
                            holder.playButton.setImageResource(R.drawable.ic_play);
                            holder.playbackProgress.setVisibility(View.GONE);
                        });
                        if (audioTrack != null) {
                            audioTrack.release();
                        }
                        return;
                    }
                    
                    currentAudioTrack = audioTrack;
                    
                    Log.d("SpeakerBottomSheet", "AudioTrack initialized successfully, starting playback");
                    audioTrack.play();
                    
                    // Write audio data in chunks and update progress
                    int bytesWritten = 0;
                    int chunkSize = minBufferSize / 2; // Write in smaller chunks
                    
                    while (bytesWritten < shortSamples.length && currentAudioTrack != null) {
                        int toWrite = Math.min(chunkSize, shortSamples.length - bytesWritten);
                        int written = audioTrack.write(shortSamples, bytesWritten, toWrite);
                        
                        if (written < 0) {
                            Log.e("SpeakerBottomSheet", "Error writing audio data: " + written);
                            break;
                        }
                        
                        bytesWritten += written;
                        
                        // Update progress bar (0-100%)
                        final int progressPercent = (bytesWritten * 100) / shortSamples.length;
                        holder.playbackProgress.post(() -> holder.playbackProgress.setProgress(progressPercent));
                    }
                    
                    Log.d("SpeakerBottomSheet", "Audio data written: " + bytesWritten + " samples");

                    // Wait for playback to finish and update progress
                    int timeout = 0;
                    while (currentAudioTrack != null && 
                           currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING &&
                           timeout < 100) { // Max 10 seconds
                        Thread.sleep(100);
                        timeout++;
                        
                        // Update progress based on playback position (0-100%)
                        if (currentAudioTrack != null) {
                            int playbackHead = currentAudioTrack.getPlaybackHeadPosition();
                            final int progressPercent = Math.min(100, (playbackHead * 100) / shortSamples.length);
                            holder.playbackProgress.post(() -> holder.playbackProgress.setProgress(progressPercent));
                        }
                    }

                    // Reset button color, icon, and hide progress indicator when done
                    holder.playButton.post(() -> {
                        holder.playButton.getBackground().clearColorFilter();
                        holder.playButton.setImageResource(R.drawable.ic_play);
                        holder.playbackProgress.setVisibility(View.GONE);
                        holder.playbackProgress.setProgress(0); // Reset progress
                        currentPlayingButton = null;
                        currentProgressBar = null;
                        currentPlayingHolder = null;
                    });

                    if (currentAudioTrack != null) {
                        currentAudioTrack.stop();
                        currentAudioTrack.release();
                        currentAudioTrack = null;
                    }
                    
                    Log.d("SpeakerBottomSheet", "Audio playback finished");
                } catch (Exception e) {
                    Log.e("SpeakerBottomSheet", "Error playing audio", e);
                    holder.playButton.post(() -> {
                        holder.playButton.getBackground().clearColorFilter();
                        holder.playButton.setImageResource(R.drawable.ic_play);
                        holder.playbackProgress.setVisibility(View.GONE);
                        holder.playbackProgress.setProgress(0); // Reset progress
                        currentPlayingButton = null;
                        currentProgressBar = null;
                        currentPlayingHolder = null;
                    });
                    if (audioTrack != null) {
                        try {
                            audioTrack.release();
                        } catch (Exception ex) {
                            Log.e("SpeakerBottomSheet", "Error releasing AudioTrack", ex);
                        }
                    }
                }
            }).start();
        }

        @Override
        public int getItemCount() {
            return speakers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            RadioButton radioButton;
            TextView nameText;
            TextView durationText;
            ImageButton playButton;
            ProgressBar playbackProgress;

            ViewHolder(View itemView) {
                super(itemView);
                radioButton = itemView.findViewById(R.id.speakerRadioButton);
                nameText = itemView.findViewById(R.id.speakerNameText);
                durationText = itemView.findViewById(R.id.speakerDurationText);
                playButton = itemView.findViewById(R.id.playButton);
                playbackProgress = itemView.findViewById(R.id.playbackProgress);
            }
        }
    }
}
