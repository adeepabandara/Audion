// app/src/main/java/com/example/audion/adapter/SongAdapter.java
package com.audion.app.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.audion.app.R;
import com.audion.app.model.Song;

import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.Holder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private final List<Song> songs;
    private final OnItemClickListener listener;

    public SongAdapter(List<Song> songs, OnItemClickListener listener) {
        this.songs = songs;
        this.listener = listener;
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int i) {
        Song song = songs.get(i);
        h.title.setText(song.getTitle());
        h.artist.setText(song.getArtist());

        if (song.getAlbumArtUri() != null) {
            h.albumArt.setImageURI(Uri.parse(song.getAlbumArtUri()));
        } else {
            h.albumArt.setImageResource(R.drawable.ic_music_placeholder);
        }

        h.itemView.setOnClickListener(v -> listener.onItemClick(i));
    }

    @Override public int getItemCount() { return songs.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView albumArt;
        TextView title, artist;
        Holder(@NonNull View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.imgItemAlbumArt);
            title    = itemView.findViewById(R.id.tvItemTitle);
            artist   = itemView.findViewById(R.id.tvItemArtist);
        }
    }
}
