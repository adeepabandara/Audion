// app/src/main/java/com/example/audion/model/Song.java
package com.audion.app.model;

public class Song {
    private final long id;
    private final String title;
    private final String artist;
    private final String data;
    private final String albumArtUri;

    public Song(long id, String title, String artist, String data, String albumArtUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.data = data;
        this.albumArtUri = albumArtUri;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getData() { return data; }
    public String getAlbumArtUri() { return albumArtUri; }
}
