package com.example.audion;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG                  = "HomeActivity";
    private static final int    PERMISSION_REQUEST_CODE = 1;

    // SharedPrefs to sync selected profile
    private static final String PREFS_NAME                = "com.example.audion.PREFERENCES";
    private static final String KEY_SELECTED_PROFILE_ID   = "selectedProfileId";

    // UI Elements
    private MaterialButton  toggleButton;
    private TextView        tvSelectedProfile;
    private BottomNavigationView bottomNav;
    private SeekBar         amplificationSeekBar;
    private WaveformView    waveformInputView;
    private WaveformView    waveformOutputView;

    // Streaming flag
    private boolean isStreaming = false;

    // Profile list & selection
    private List<HearingProfile> profileList = new ArrayList<>();
    private int currentProfileId = -1;

    // Test‐result DAO
    private HearingTestResultDao hearingTestResultDao;

    // Always user 1
    private static final int USER_ID = 1;

    // Pref keys for processing
    public static final String KEY_NOISE_REMOVAL   = "noiseRemoval";
    public static final String KEY_AMPLIFICATION   = "amplificationFactor";

    // Receiver for waveform data
    private final android.content.BroadcastReceiver waveformReceiver =
        new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                float inputLevel  = intent.getFloatExtra("inputLevel", 0f);
                float outputLevel = intent.getFloatExtra("outputLevel",0f);
                if (waveformInputView != null)  waveformInputView .addAmplitude(Math.abs(inputLevel));
                if (waveformOutputView!= null)  waveformOutputView.addAmplitude(Math.abs(outputLevel));
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_variation_one);

        toggleButton         = findViewById(R.id.toggleButton);
        tvSelectedProfile    = findViewById(R.id.tvSelectedProfile);
        bottomNav            = findViewById(R.id.bottomNavigationView);
        amplificationSeekBar = findViewById(R.id.seekBar);
        waveformInputView    = findViewById(R.id.waveformInput);
        waveformOutputView   = findViewById(R.id.waveformOutput);

        // DB & DAO
        AppDatabase db               = AppDatabase.getInstance(this);
        hearingTestResultDao         = db.hearingTestResultDao();

        // Bottom nav
        bottomNav.setSelectedItemId(R.id.navigation_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_frequencies) {
                startActivity(new Intent(this, FrequencyActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.navigation_home) {
                // already here
                return true;
            } else if (id == R.id.navigation_settings) {
                startActivity(new Intent(this, MusicPlayerActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return false;
        });

        // Toggle
        toggleButton.setOnClickListener(v -> {
            if (!isStreaming) {
                startAudioStreamingService();
                toggleButton.setText("Stop");
                toggleButton.setIconResource(R.drawable.ic_stop);
                toggleButton.setBackgroundTintList(getResources().getColorStateList(R.color.red_circle));
            } else {
                stopAudioStreamingService();
                toggleButton.setText("Start");
                toggleButton.setIconResource(R.drawable.ic_play);
                toggleButton.setBackgroundTintList(getResources().getColorStateList(R.color.green_circle));
            }
            isStreaming = !isStreaming;
        });

        // Amplification
        amplificationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar sb,int progress,boolean fromUser){
                float maxDb       = 40.0f;
                float currentDb   = (progress/(float)sb.getMax())*maxDb;
                float ampFactor   = (float)Math.pow(10,currentDb/20f);
                getSharedPreferences(PREFS_NAME,MODE_PRIVATE)
                    .edit()
                    .putFloat(KEY_AMPLIFICATION, ampFactor)
                    .apply();
                Log.d("Amplification","Prog:"+progress+"→"+ampFactor);
            }
            @Override public void onStartTrackingTouch(SeekBar sb){}
            @Override public void onStopTrackingTouch(SeekBar sb){}
        });

        // Noise removal
        SwitchMaterial noiseRemovalSwitch = findViewById(R.id.noiseRemovalSwitch);
        noiseRemovalSwitch.setOnCheckedChangeListener((btn,isChecked)->{
            getSharedPreferences(PREFS_NAME,MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NOISE_REMOVAL, isChecked)
                .apply();
        });

        // Profile selector
        tvSelectedProfile.setOnClickListener(v->{
            reorderProfiles(profileList, currentProfileId);
            ProfileSelectionBottomSheet bottomSheet =
                ProfileSelectionBottomSheet.newInstance(
                    new ArrayList<>(profileList),
                    currentProfileId
                );
            bottomSheet.setOnProfileSelectedListener(profile -> {
                currentProfileId = profile.getId();
                getSharedPreferences(PREFS_NAME,MODE_PRIVATE)
                  .edit()
                  .putInt(KEY_SELECTED_PROFILE_ID, currentProfileId)
                  .apply();
                tvSelectedProfile.setText(profile.getName());
                updateGainsForProfile(profile.getId());
            });
            bottomSheet.show(getSupportFragmentManager(), "ProfileSelectionBottomSheet");
        });

        // Load & default‐select
        loadHearingProfiles();

        if (!hasPermissions()) {
            requestPermissions();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // re‐sync if frequency page changed it
        SharedPreferences sp = getSharedPreferences(PREFS_NAME,MODE_PRIVATE);
        int saved = sp.getInt(KEY_SELECTED_PROFILE_ID, -1);
        if (saved != -1 && saved != currentProfileId) {
            currentProfileId = saved;
            new Thread(() -> {
                HearingProfile hp = AppDatabase
                    .getInstance(this)
                    .hearingProfileDao()
                    .getHearingProfileById(saved);
                final String name = hp.getName();
                runOnUiThread(() -> tvSelectedProfile.setText(name));
            }).start();
        }

        // register waveform receiver
        registerReceiver(
          waveformReceiver,
          new IntentFilter("com.example.audion.WAVEFORM_UPDATE"),
          Context.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(waveformReceiver);
    }

    // Permissions & streaming
    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;
    }
    private void requestPermissions() {
        ActivityCompat.requestPermissions(
          this,
          new String[]{Manifest.permission.RECORD_AUDIO},
          PERMISSION_REQUEST_CODE
        );
    }
    private void startAudioStreamingService(){
        if (!hasPermissions()){ requestPermissions(); return; }
        Intent svc = new Intent(this,AudioStreamingService.class);
        ContextCompat.startForegroundService(this, svc);
    }
    private void stopAudioStreamingService(){
        stopService(new Intent(this,AudioStreamingService.class));
    }

    private void loadHearingProfiles() {
        new Thread(() -> {
            profileList = AppDatabase
                .getInstance(this)
                .hearingProfileDao()
                .getAllProfiles();

            if (profileList == null || profileList.isEmpty()) {
                long id = AppDatabase
                    .getInstance(this)
                    .hearingProfileDao()
                    .insert(new HearingProfile("Default Profile",""));
                profileList = new ArrayList<>();
                profileList.add(
                  AppDatabase
                    .getInstance(this)
                    .hearingProfileDao()
                    .getHearingProfileById((int)id)
                );
            }

            if (currentProfileId == -1) {
                currentProfileId = profileList.get(0).getId();
            }

            HearingProfile sel = null;
            for (HearingProfile hp : profileList) {
                if (hp.getId() == currentProfileId) {
                    sel = hp;
                    break;
                }
            }
            if (sel == null) {
                sel = profileList.get(0);
                currentProfileId = sel.getId();
            }

            final String  finalName = sel.getName();
            final int     finalId   = sel.getId();

            runOnUiThread(() -> {
                tvSelectedProfile.setText(finalName);
                updateGainsForProfile(finalId);
            });
        }).start();
    }

    private void reorderProfiles(List<HearingProfile> profiles, int selectedId) {
        int idx = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId() == selectedId) {
                idx = i;
                break;
            }
        }
        if (idx > 0) {
            HearingProfile hp = profiles.remove(idx);
            profiles.add(0, hp);
        }
    }

    private void updateGainsForProfile(int profileId) {
        new Thread(() -> {
            List<HearingTestResult> results =
                AppDatabase
                  .getInstance(this)
                  .hearingTestResultDao()
                  .getResultsForUserAndProfile(USER_ID, profileId);

            float[] newGains = new float[8];
            for (int i = 0; i < 8; i++) newGains[i] = 1f;

            for (HearingTestResult r : results) {
                int f = r.getFrequency();
                float g = r.getAmplitudeStep()/100f;
                switch (f) {
                    case 125:  newGains[0] = g; break;
                    case 250:  newGains[1] = g; break;
                    case 500:  newGains[2] = g; break;
                    case 1000: newGains[3] = g; break;
                    case 2000: newGains[4] = g; break;
                    case 3000: newGains[5] = g; break;
                    case 4000: newGains[6] = g; break;
                    case 8000: newGains[7] = g; break;
                }
            }

            for (int i = 0; i < newGains.length; i++) {
                Log.d(TAG, "Band " + i + " gain: " + newGains[i]);
            }
        }).start();
    }
}
