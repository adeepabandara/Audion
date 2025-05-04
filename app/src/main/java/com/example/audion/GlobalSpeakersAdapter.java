package com.example.audion;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.audion.diarization.DirectDiarizationManager;

public class GlobalSpeakersAdapter extends RecyclerView.Adapter<GlobalSpeakersAdapter.GlobalSpeakerViewHolder> {
    private static final String TAG = "GlobalSpeakersAdapter";

    private List<GlobalSpeakerInfo> globalSpeakers = new ArrayList<>();
    private SpeakerSelectionListener selectionListener;
    private Integer selectedSpeakerId = null;
    private DirectDiarizationManager diarizationManager;
    private AudioTrack currentAudioTrack;
    private FocusActivity focusActivity;
    private Map<Integer, Integer> globalSpeakerColorMap;
    private int[] speakerColors = {
        0xFF4285F4,0xFFEA4335,0xFFFFBF00,0xFF34A853,
        0xFF9C27B0,0xFFFF9800,0xFF00BCD4,0xFFF44336,
        0xFF795548,0xFF607D8B
    };
    private int nextColorIndex = 0;

    public static class GlobalSpeakerInfo {
        private final int globalId;
        private float totalDuration;
        private int chunkCount;
        private int lastChunkId;
        private List<Integer> chunkIds;
        public GlobalSpeakerInfo(int globalId) {
            this.globalId = globalId;
            this.chunkIds = new ArrayList<>();
        }
        public int getGlobalId() { return globalId; }
        public float getTotalDuration() { return totalDuration; }
        public int getChunkCount() { return chunkCount; }
        public int getLastChunkId() { return lastChunkId; }
        public List<Integer> getChunkIds() { return chunkIds; }
        public void addDuration(float d) { totalDuration += d; }
        public void incrementChunkCount() { chunkCount++; }
        public void setLastChunkId(int c) {
            lastChunkId = c;
            if (!chunkIds.contains(c)) chunkIds.add(c);
        }
    }

    public interface SpeakerSelectionListener {
        void onSpeakerSelected(int speakerId);
        void onSpeakerDeselected();
    }

    public GlobalSpeakersAdapter(FocusActivity activity, Map<Integer,Integer> colorMap) {
        this.focusActivity = activity;
        this.globalSpeakerColorMap = colorMap != null ? colorMap : new HashMap<>();
    }

    public void setDiarizationManager(DirectDiarizationManager m) { this.diarizationManager = m; }
    public void setSelectionListener(SpeakerSelectionListener l) { this.selectionListener = l; }

    public void updateGlobalSpeakers(List<List<DirectDiarizationManager.SpeakerInfo>> history) {
        if (history == null || history.isEmpty()) {
            globalSpeakers.clear();
            notifyDataSetChanged();
            return;
        }
        Map<Integer, GlobalSpeakerInfo> map = new HashMap<>();
        for (List<DirectDiarizationManager.SpeakerInfo> chunk : history) {
            if (chunk.isEmpty()) continue;
            int chunkId = chunk.get(0).getChunkId();
            Map<Integer,Boolean> seen = new HashMap<>();
            for (var s : chunk) {
                int id = s.getGlobalId();
                GlobalSpeakerInfo gi = map.computeIfAbsent(id, GlobalSpeakerInfo::new);
                gi.addDuration(s.getDuration());
                gi.setLastChunkId(chunkId);
                if (!seen.containsKey(id)) {
                    gi.incrementChunkCount();
                    seen.put(id,true);
                }
            }
        }
        globalSpeakers = new ArrayList<>(map.values());
        Collections.sort(globalSpeakers,
            (a,b) -> Float.compare(b.getTotalDuration(), a.getTotalDuration()));
        notifyDataSetChanged();
    }

    private int getSpeakerColor(int id) {
        if (!globalSpeakerColorMap.containsKey(id)) {
            globalSpeakerColorMap.put(id, speakerColors[nextColorIndex++ % speakerColors.length]);
        }
        return globalSpeakerColorMap.get(id);
    }

    @NonNull
    @Override
    public GlobalSpeakerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_global_speaker, parent, false);
        return new GlobalSpeakerViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GlobalSpeakerViewHolder h, int pos) {
        h.bind(globalSpeakers.get(pos));
    }

    @Override public int getItemCount() { return globalSpeakers.size(); }

    public void setSelectedSpeakerId(Integer id) {
        this.selectedSpeakerId = id;
        notifyDataSetChanged();
    }

    public void stopPlayback() {
        if (currentAudioTrack != null) {
            try {
                currentAudioTrack.stop();
                currentAudioTrack.release();
            } catch (Exception e) { Log.e(TAG,"Error stopping",e);}
            currentAudioTrack = null;
        }
    }

    public void release() {
        stopPlayback();
        selectedSpeakerId = null;
    }


    public void stopAllPlayback() {
    stopPlayback();
}

    class GlobalSpeakerViewHolder extends RecyclerView.ViewHolder {
        TextView speakerIdText, speakerStatsText;
        View colorIndicator, itemContainer;
        ImageButton playButton;
        public GlobalSpeakerViewHolder(@NonNull View v) {
            super(v);
            speakerIdText = v.findViewById(R.id.globalSpeakerIdText);
            speakerStatsText = v.findViewById(R.id.globalSpeakerStatsText);
            colorIndicator = v.findViewById(R.id.globalSpeakerColorIndicator);
            itemContainer = v.findViewById(R.id.globalSpeakerContainer);
            playButton = v.findViewById(R.id.globalSpeakerPlayButton);
        }
        public void bind(GlobalSpeakerInfo s) {
            int id = s.getGlobalId();
            speakerIdText.setText("Speaker " + id);
            speakerStatsText.setText(String.format("%.1fs • %d chunks",
                s.getTotalDuration(), s.getChunkCount()));
            colorIndicator.setBackgroundColor(getSpeakerColor(id));
            boolean isSel = selectedSpeakerId != null && selectedSpeakerId == id;
            itemContainer.setBackgroundResource(isSel ?
                R.drawable.selected_speaker_background : R.drawable.unselected_speaker_background);
            if (diarizationManager != null && s.getLastChunkId()>0) {
                playButton.setVisibility(View.VISIBLE);
                playButton.setOnClickListener(v -> playSpeakerAudio(s));
            } else {
                playButton.setVisibility(View.GONE);
            }
            itemView.setOnClickListener(v -> {
                if (selectionListener != null) {
                    if (isSel) {
                        selectedSpeakerId = null;
                        selectionListener.onSpeakerDeselected();
                    } else {
                        selectedSpeakerId = id;
                        selectionListener.onSpeakerSelected(id);
                    }
                    notifyDataSetChanged();
                }
            });
        }

        private void playSpeakerAudio(GlobalSpeakerInfo speaker) {
            if (diarizationManager == null) {
                String msg = "Diarization manager not available";
                Toast.makeText(itemView.getContext(),msg,Toast.LENGTH_SHORT).show();
                focusActivity.updateStatus("Error: " + msg);
                return;
            }
            stopPlayback();
            focusActivity.updateStatus("Trying to play Speaker " + speaker.getGlobalId() + "…");

            float[] speakerAudio = null;
            int usedChunkId = -1;

            if (speaker.getLastChunkId() > 0) {
                speakerAudio = diarizationManager.extractSpeakerAudio(
                    speaker.getLastChunkId(), speaker.getGlobalId());
                usedChunkId = speaker.getLastChunkId();
            }

            if (speakerAudio == null || speakerAudio.length == 0) {
                List<Integer> ids = new ArrayList<>(speaker.getChunkIds());
                Collections.sort(ids, Collections.reverseOrder());
                for (int cid : ids) {
                    if (cid != speaker.getLastChunkId()) {
                        speakerAudio = diarizationManager.extractSpeakerAudio(cid, speaker.getGlobalId());
                        if (speakerAudio != null && speakerAudio.length>0) {
                            usedChunkId = cid;
                            break;
                        }
                    }
                }
            }

            if (speakerAudio == null || speakerAudio.length==0) {
                String msg = "No audio for Speaker " + speaker.getGlobalId();
                Toast.makeText(itemView.getContext(),msg,Toast.LENGTH_SHORT).show();
                focusActivity.updateStatus("Error: " + msg);
                return;
            }

            // apply amplification from FocusActivity
            float amp = focusActivity.getAmplificationFactor();
            for (int i = 0; i < speakerAudio.length; i++){
                speakerAudio[i] *= amp;
            }

            int sampleRate = diarizationManager.getSampleRate();
            int minBuf = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);

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
                .setBufferSizeInBytes(Math.max(minBuf, speakerAudio.length*4))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

            currentAudioTrack.setPlaybackPositionUpdateListener(
                new AudioTrack.OnPlaybackPositionUpdateListener() {
                    @Override public void onMarkerReached(AudioTrack track) {
                        track.release();
                        if (track == currentAudioTrack) currentAudioTrack = null;
                    }
                    @Override public void onPeriodicNotification(AudioTrack track) {}
                });

            try {
                currentAudioTrack.write(speakerAudio, 0, speakerAudio.length, AudioTrack.WRITE_BLOCKING);
                currentAudioTrack.setNotificationMarkerPosition(speakerAudio.length);
                currentAudioTrack.play();
                String msg = String.format("Playing Speaker %d (Chunk %d, %.1fs)",
                    speaker.getGlobalId(), usedChunkId, speaker.getTotalDuration());
                Toast.makeText(itemView.getContext(),msg,Toast.LENGTH_SHORT).show();
                focusActivity.updateStatus(msg);
            } catch (Exception e) {
                Log.e(TAG,"Error playing audio",e);
                String em = "Error: "+e.getMessage();
                Toast.makeText(itemView.getContext(),em,Toast.LENGTH_SHORT).show();
                focusActivity.updateStatus("Error: " + em);
                if (currentAudioTrack != null) {
                    currentAudioTrack.release();
                    currentAudioTrack = null;
                }
            }
        }
    }
}
