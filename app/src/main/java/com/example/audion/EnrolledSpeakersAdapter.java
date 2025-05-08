package com.example.audion;

import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EnrolledSpeakersAdapter
        extends RecyclerView.Adapter<EnrolledSpeakersAdapter.ViewHolder> {

    public interface OnSpeakerSelectListener {
        void onSpeakerSelected(EnrollmentActivity.EnrolledSpeaker speaker, int position);
    }

    private final Context context;
    private final List<EnrollmentActivity.EnrolledSpeaker> speakers;
    private OnSpeakerSelectListener selectListener;
    private AudioTrack audioTrack;
    private boolean isPlaying = false;
    private int selectedPosition = -1;

    public EnrolledSpeakersAdapter(Context ctx, List<EnrollmentActivity.EnrolledSpeaker> list) {
        this.context = ctx;
        this.speakers = list;
    }

    public void setOnSpeakerSelectListener(OnSpeakerSelectListener l) {
        this.selectListener = l;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_enrolled_speaker, parent, false);
        return new ViewHolder(v);
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
        EnrollmentActivity.EnrolledSpeaker sp = speakers.get(pos);

        holder.nameTextView.setText(sp.getName());
        holder.durationTextView.setText(String.format("%.1f sec", sp.getDuration()));

        holder.speakerRadio.setChecked(pos == selectedPosition);
        View.OnClickListener select = v -> {
            selectedPosition = pos;
            notifyDataSetChanged();
            if (selectListener != null) selectListener.onSpeakerSelected(sp, pos);
        };
        holder.itemView.setOnClickListener(select);
        holder.speakerRadio.setOnClickListener(select);

        boolean hasAudio = sp.getAudioSamples()!=null && sp.getAudioSamples().length>0;
        holder.playButton.setEnabled(hasAudio);
        holder.playButton.setText(isPlaying && pos==selectedPosition ? "Stop" : "Play");
        holder.playButton.setOnClickListener(v -> {
            v.getContext().sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
            if (isPlaying) {
                stopPlayback();
                holder.playButton.setText("Play");
            } else if (hasAudio) {
                playSpeakerAudio(sp.getAudioSamples());
                holder.playButton.setText("Stop");
            } else {
                Toast.makeText(context,"No audio sample",Toast.LENGTH_SHORT).show();
            }
        });

        holder.renameButton.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setTitle("Rename Speaker");
            EditText in = new EditText(context);
            in.setInputType(InputType.TYPE_CLASS_TEXT);
            in.setText(sp.getName());
            b.setView(in);
            b.setPositiveButton("OK",(d,w)->{
                String nm = in.getText().toString().trim();
                if(!nm.isEmpty()){
                    sp.setName(nm);
                    notifyItemChanged(pos);
                }
            });
            b.setNegativeButton("Cancel",(d,w)->d.cancel());
            b.show();
        });
    }

    @Override public int getItemCount() {
        return speakers.size();
    }

    private void playSpeakerAudio(float[] samples) {
        stopPlayback();
        boolean isFocus = context instanceof FocusActivity;
        float gain = isFocus
                ? ((FocusActivity)context).getAmplificationFactor()
                : 1f;
        try {
            int sr = 16000;
            int bufSize = Math.max(
                    AudioTrack.getMinBufferSize(sr,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_FLOAT),
                    samples.length * 4
            );
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            // apply the user’s amplification to each sample:
            for (int i = 0; i < samples.length; i++) {
                samples[i] *= gain;
            }
// now write out the amplified buffer:
            audioTrack.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
            audioTrack.setNotificationMarkerPosition(samples.length);
            audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override public void onMarkerReached(AudioTrack t) {
                    stopPlayback();
                    notifyDataSetChanged();
                }
                @Override public void onPeriodicNotification(AudioTrack t) {}
            });
            audioTrack.play();
            isPlaying = true;
        } catch (Exception e) {
            Toast.makeText(context,"Play error: "+e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void stopPlayback() {
        if (audioTrack!=null) {
            try {
                if (audioTrack.getState()==AudioTrack.STATE_INITIALIZED) {
                    audioTrack.stop();
                    audioTrack.flush();
                }
                audioTrack.release();
            } catch (Exception ignored){}
        }
        audioTrack = null;
        isPlaying = false;
    }

    public void updatePlaybackVolume(float gain) {
        if (audioTrack!=null && isPlaying) {
            audioTrack.setVolume(gain);
        }
    }

    @Override public void onDetachedFromRecyclerView(@NonNull RecyclerView rv) {
        super.onDetachedFromRecyclerView(rv);
        stopPlayback();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        RadioButton speakerRadio;
        TextView    nameTextView, durationTextView;
        Button      playButton;
        ImageButton renameButton;

        ViewHolder(View iv) {
            super(iv);
            speakerRadio     = iv.findViewById(R.id.speakerRadio);
            nameTextView     = iv.findViewById(R.id.speakerNameTextView);
            durationTextView = iv.findViewById(R.id.durationTextView);
            playButton       = iv.findViewById(R.id.playButton);
            renameButton     = iv.findViewById(R.id.renameButton);
        }
    }
}
