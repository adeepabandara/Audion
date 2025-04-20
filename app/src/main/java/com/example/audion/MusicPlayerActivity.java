package com.example.audion;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audion.adapter.SongAdapter;
import com.example.audion.model.Song;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MusicPlayerActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_READ = 100;
    private static final String PERMISSION_TO_REQUEST =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_AUDIO
            : Manifest.permission.READ_EXTERNAL_STORAGE;

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private final List<Song> songs = new ArrayList<>();

    private ImageView imgAlbumArt;
    private TextView tvSongTitle;
    private ImageButton btnPrev, btnPlayPause, btnNext;
    private SeekBar seekBarVolume;
    private BottomNavigationView bottomNav;

    private MusicPlayerService playerService;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            playerService = ((MusicPlayerService.LocalBinder) binder).getService();
            bound = true;
            playerService.setPlaylist(songs);
            SharedPreferences sp = getSharedPreferences(
                MusicPlayerService.PREFS_NAME, MODE_PRIVATE
            );
            float amp = sp.getFloat(MusicPlayerService.KEY_AMPLIFICATION, 5f);
            playerService.setVolumeGain(amp);
            seekBarVolume.setProgress((int)(amp * 100));
            if (!songs.isEmpty()) updatePlayerUI();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        // --- BOTTOM NAVIGATION SETUP ---
        bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.navigation_settings);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_home) {
                startActivity(new Intent(this, HomeActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.navigation_frequencies) {
                startActivity(new Intent(this, FrequencyActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.navigation_settings) {
                // already here
                return true;
            }
            return false;
        });

        // --- VIEW BINDING ---
        recyclerView  = findViewById(R.id.recyclerViewSongs);
        imgAlbumArt   = findViewById(R.id.imgAlbumArt);
        tvSongTitle   = findViewById(R.id.tvSongTitle);
        btnPrev       = findViewById(R.id.btnPrev);
        btnPlayPause  = findViewById(R.id.btnPlayPause);
        btnNext       = findViewById(R.id.btnNext);
        seekBarVolume = findViewById(R.id.seekBarVolume);

        // allow up to 10× amplification
        seekBarVolume.setMax(1000);
        SharedPreferences sp = getSharedPreferences(
            MusicPlayerService.PREFS_NAME, MODE_PRIVATE
        );
        if (!sp.contains(MusicPlayerService.KEY_AMPLIFICATION)) {
            seekBarVolume.setProgress(500);
            sp.edit().putFloat(MusicPlayerService.KEY_AMPLIFICATION, 5f).apply();
        }

        // RecyclerView
        adapter = new SongAdapter(songs, pos -> {
            if (bound) {
                playerService.play(pos);
                updatePlayerUI();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Controls
        btnPrev.setOnClickListener(v -> {
            if (bound) { playerService.previous(); updatePlayerUI(); }
        });
        btnNext.setOnClickListener(v -> {
            if (bound) { playerService.next(); updatePlayerUI(); }
        });
        btnPlayPause.setOnClickListener(v -> {
            if (!bound) return;
            if (playerService.isPlaying()) {
                playerService.pause();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            } else {
                playerService.resume();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
        });
        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int prog, boolean u) {
                float gain = prog / 100f;
                if (bound) playerService.setVolumeGain(gain);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Permission
        if (ContextCompat.checkSelfPermission(this, PERMISSION_TO_REQUEST)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{ PERMISSION_TO_REQUEST },
                REQUEST_CODE_READ
            );
        } else {
            loadSongs();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent svc = new Intent(this, MusicPlayerService.class);
        startService(svc);
        bindService(svc, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQUEST_CODE_READ && res.length>0 && res[0]==PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION_TO_REQUEST)) {
            new AlertDialog.Builder(this)
              .setTitle("Permission needed")
              .setMessage("We need audio access to list your music files.")
              .setPositiveButton("OK", (d,w) -> ActivityCompat.requestPermissions(
                  this,
                  new String[]{ PERMISSION_TO_REQUEST },
                  REQUEST_CODE_READ
              ))
              .setNegativeButton("Cancel",null)
              .show();
        } else {
            Toast.makeText(this,"Permission denied; cannot list music.",Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSongs() {
        ContentResolver r = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String sel = MediaStore.Audio.Media.IS_MUSIC+"=?";
        String[] args = {"1"};
        Cursor c = r.query(uri, null, sel, args, null);
        if (c!=null) {
            int idCol      = c.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleCol   = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int artistCol  = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int dataCol    = c.getColumnIndex(MediaStore.Audio.Media.DATA);
            int albumIdCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            while (c.moveToNext()) {
                long id      = c.getLong(idCol);
                String title = c.getString(titleCol);
                String art   = c.getString(artistCol);
                String data  = c.getString(dataCol);
                long aId     = c.getLong(albumIdCol);
                Uri artUri = Uri.withAppendedPath(
                    Uri.parse("content://media/external/audio/albumart"),
                    String.valueOf(aId)
                );
                songs.add(new Song(id,title,art,data,artUri.toString()));
            }
            c.close();
            adapter.notifyDataSetChanged();
        }
    }

    private void updatePlayerUI() {
        Song cur = playerService.getCurrentSong();
        if (cur==null) return;
        tvSongTitle.setText(cur.getTitle());
        if (cur.getAlbumArtUri()!=null) {
            imgAlbumArt.setImageURI(Uri.parse(cur.getAlbumArtUri()));
        } else {
            imgAlbumArt.setImageResource(R.drawable.ic_music_placeholder);
        }
        btnPlayPause.setImageResource(
            playerService.isPlaying()
            ? android.R.drawable.ic_media_pause
            : android.R.drawable.ic_media_play
        );
    }
}
