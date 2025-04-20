package com.example.audion;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Binder;
import android.os.IBinder;

import com.example.audion.model.Song;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayerService extends Service {
    public static final String PREFS_NAME = "com.example.audion.PREFERENCES";
    public static final String KEY_AMPLIFICATION = "amplificationFactor";

    private final IBinder binder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private LoudnessEnhancer loudnessEnhancer;
    private DynamicsProcessing dynamicsProcessing;

    private final List<Song> playlist = new ArrayList<>();
    private int currentIndex = 0;
    private float volumeGain = 1f;

    public class LocalBinder extends Binder {
        public MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Load saved amplification (or default to 1.0×)
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        try {
            volumeGain = sp.getFloat(KEY_AMPLIFICATION, 1f);
        } catch (ClassCastException e) {
            int oldInt = sp.getInt(KEY_AMPLIFICATION, 100);
            volumeGain = oldInt / 100f;
            sp.edit().putFloat(KEY_AMPLIFICATION, volumeGain).apply();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /** Replace the current playlist */
    public void setPlaylist(List<Song> songs) {
        playlist.clear();
        playlist.addAll(songs);
    }

    /** Play the song at `index`, setting up both LoudnessEnhancer and a DynamicsProcessing limiter */
    public void play(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        Song song = playlist.get(index);

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnCompletionListener(mp -> next());
        } else {
            mediaPlayer.reset();
        }

        try {
            mediaPlayer.setDataSource(song.getData());
            mediaPlayer.prepare();

            // 1) Hit max system volume
            mediaPlayer.setVolume(1f, 1f);

            int sessionId = mediaPlayer.getAudioSessionId();

            // 2) LoudnessEnhancer boost
            if (loudnessEnhancer != null) {
                loudnessEnhancer.release();
            }
            loudnessEnhancer = new LoudnessEnhancer(sessionId);
            int boostMb = (int)((volumeGain - 1f) * 1000f); // e.g. 5× → +4000 mB = +40 dB
            loudnessEnhancer.setTargetGain(boostMb);
            loudnessEnhancer.setEnabled(true);

            // 3) DynamicsProcessing limiter-only to tame peaks
            if (dynamicsProcessing != null) {
                dynamicsProcessing.release();
            }

            // Build a single-channel, limiter-only configuration
            DynamicsProcessing.Limiter limiter = new DynamicsProcessing.Limiter(
                /* inUse= */      true,
                /* enabled= */    true,
                /* linkGroup= */  0,
                /* attackTime= */ 5f,    // ms
                /* releaseTime=*/ 100f,  // ms
                /* ratio= */      10f,   // 10:1 compression
                /* threshold= */  -3f,   // dBFS, negative means below full-scale
                /* postGain= */   0f     // dB after limiting
            );

            DynamicsProcessing.Config dpConfig = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    /* channelCount= */    1,
                    /* preEqInUse= */      false, /* preEqBands=*/  0,
                    /* mbcInUse= */        false, /* mbcBands=*/    0,
                    /* postEqInUse= */     false, /* postEqBands=*/  0,
                    /* limiterInUse= */    true
                )
                .setLimiterByChannelIndex(0, limiter)
                .setPreferredFrameDuration(10f)
                .build();

            dynamicsProcessing = new DynamicsProcessing(
                /* priority= */     0,
                /* audioSession= */ sessionId,
                /* cfg= */          dpConfig
            );
            dynamicsProcessing.setEnabled(true);

            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Pause playback. */
    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    /** Resume playback. */
    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    /** @return true if currently playing. */
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    /** Advance to next track (wraps). */
    public void next() {
        play((currentIndex + 1) % playlist.size());
    }

    /** Go back to previous track (wraps). */
    public void previous() {
        play((currentIndex - 1 + playlist.size()) % playlist.size());
    }

    /** @return the current Song, or null if none. */
    public Song getCurrentSong() {
        return playlist.isEmpty() ? null : playlist.get(currentIndex);
    }

    /**
     * Update the amplification factor.
     * Adjusts the LoudnessEnhancer (and the limiter threshold if you like).
     */
    public void setVolumeGain(float gain) {
        volumeGain = gain;
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit().putFloat(KEY_AMPLIFICATION, gain).apply();

        if (loudnessEnhancer != null) {
            int boostMb = (int)((volumeGain - 1f) * 1000f);
            loudnessEnhancer.setTargetGain(boostMb);
        }
        // The limiter is baked into the config, so it continues to tame peaks automatically.
    }

    @Override
    public void onDestroy() {
        if (dynamicsProcessing != null) {
            dynamicsProcessing.release();
            dynamicsProcessing = null;
        }
        if (loudnessEnhancer != null) {
            loudnessEnhancer.release();
            loudnessEnhancer = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
