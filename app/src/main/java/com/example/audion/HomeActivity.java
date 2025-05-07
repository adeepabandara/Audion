package com.example.audion;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.Button;
import android.view.WindowManager;

import android.view.Window;



import android.os.Build;
import android.view.View;
import android.graphics.Color;

import androidx.annotation.DrawableRes;
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
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 101;

    private static final String TAG                     = "HomeActivity";
    private static final int    PERMISSION_REQUEST_CODE = 1;

    private static final String PREFS_NAME              = "com.example.audion.PREFERENCES";
    private static final String KEY_SELECTED_PROFILE_ID = "selectedProfileId";
    private static final String KEY_IS_STREAMING        = "isStreaming";
    public  static final String KEY_NOISE_REMOVAL       = "noiseRemoval";
    public  static final String KEY_AMPLIFICATION       = "amplificationFactor";

    private MaterialButton       toggleButton;
    private MaterialButton       focus;
    private TextView             tvSelectedProfile;
    private ImageView            ivProfileIcon;
    private BottomNavigationView bottomNav;
    private SeekBar              amplificationSeekBar;

    private boolean isStreaming = false;
    private List<HearingProfile> profileList = new ArrayList<>();
    private int currentProfileId = -1;
    private HearingTestResultDao hearingTestResultDao;
    private static final int USER_ID = 1;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        // 2) go full-screen (hides the status bar entirely)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 3) on Android P+ allow content into any cutout/notch area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_variation_one);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{ Manifest.permission.RECORD_AUDIO },
                REQUEST_RECORD_AUDIO
            );
        }



        Button focusBtn = findViewById(R.id.focus);
        toggleButton         = findViewById(R.id.toggleButton);
        tvSelectedProfile    = findViewById(R.id.tvSelectedProfile);
        ivProfileIcon        = findViewById(R.id.ivProfileIcon);
        bottomNav            = findViewById(R.id.bottomNavigationView);
        amplificationSeekBar = findViewById(R.id.seekBar);

        hearingTestResultDao = AppDatabase.getInstance(this).hearingTestResultDao();

        focusBtn.setOnClickListener(v -> openFocusActivity());

        bottomNav.setSelectedItemId(R.id.navigation_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_frequencies) {
                startActivity(new Intent(this, FrequencyActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.navigation_settings) {
                startActivity(new Intent(this, MusicPlayerActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return true;
        });

        toggleButton.setOnClickListener(v -> {
            if (!isStreaming) {
                if (hasMicPermission()) {
                    startAudioStreamingService();
                    isStreaming = true;
                } else {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{ Manifest.permission.RECORD_AUDIO },
                            PERMISSION_REQUEST_CODE
                    );
                }
            } else {
                stopAudioStreamingService();
                isStreaming = false;
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_IS_STREAMING, false)
                    .apply();
            updateToggleUi(isStreaming);
        });

        amplificationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar sb,int progress,boolean u){
                float maxDb     = 40f;
                float curDb     = (progress/(float)sb.getMax())*maxDb;
                float ampFactor = (float)Math.pow(10,curDb/20f);
                getSharedPreferences(PREFS_NAME,MODE_PRIVATE)
                        .edit()
                        .putFloat(KEY_AMPLIFICATION, ampFactor)
                        .apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb){}
            @Override public void onStopTrackingTouch(SeekBar sb){}
        });

        SwitchMaterial noiseRemovalSwitch = findViewById(R.id.noiseRemovalSwitch);
        noiseRemovalSwitch.setOnCheckedChangeListener((btn,checked)->{
            getSharedPreferences(PREFS_NAME,MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_NOISE_REMOVAL, checked)
                    .apply();
        });

        tvSelectedProfile.setOnClickListener(v->{
            reorderProfiles(profileList, currentProfileId);
            ProfileSelectionBottomSheet bs =
                    ProfileSelectionBottomSheet.newInstance(
                            new ArrayList<>(profileList),
                            currentProfileId
                    );
            bs.setOnProfileSelectedListener(profile -> {
                currentProfileId = profile.getId();
                getSharedPreferences(PREFS_NAME,MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_SELECTED_PROFILE_ID, currentProfileId)
                        .apply();
                tvSelectedProfile.setText(profile.getName());
                ivProfileIcon.setImageResource(iconResForKey(profile.getIcon()));
                updateGainsForProfile(currentProfileId);
            });
            bs.show(getSupportFragmentManager(), "ProfileSelection");
        });

        loadHearingProfiles();
    }

    @Override
    protected void onResume() {
        super.onResume();



        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isStreaming = sp.getBoolean(KEY_IS_STREAMING, false);
        updateToggleUi(isStreaming);

        float ampFactor = sp.getFloat(KEY_AMPLIFICATION, 1f);
        double curDb = 20 * Math.log10(ampFactor);
        int prog = Math.round((float)(curDb/40f)*amplificationSeekBar.getMax());
        amplificationSeekBar.setProgress(prog);

        SwitchMaterial noiseRemovalSwitch = findViewById(R.id.noiseRemovalSwitch);
        noiseRemovalSwitch.setChecked(sp.getBoolean(KEY_NOISE_REMOVAL, false));

        int saved = sp.getInt(KEY_SELECTED_PROFILE_ID, -1);
        if (saved != -1 && saved != currentProfileId) {
            currentProfileId = saved;
            new Thread(() -> {
                HearingProfile hp = AppDatabase
                        .getInstance(this)
                        .hearingProfileDao()
                        .getHearingProfileById(saved);
                runOnUiThread(() -> {
                    tvSelectedProfile.setText(hp.getName());
                    ivProfileIcon.setImageResource(iconResForKey(hp.getIcon()));
                });
            }).start();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioStreamingService();
            isStreaming = true;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_IS_STREAMING, true)
                    .apply();
            updateToggleUi(true);
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateToggleUi(boolean streaming) {
        if (streaming) {
            toggleButton.setText("Stop");
            toggleButton.setIconResource(R.drawable.ic_stop);
            toggleButton.setBackgroundTintList(
                    getResources().getColorStateList(R.color.red_circle)
            );
        } else {
            toggleButton.setText("Start");
            toggleButton.setIconResource(R.drawable.ic_play);
            toggleButton.setBackgroundTintList(
                    getResources().getColorStateList(R.color.green_circle)
            );
        }
    }

    private void startAudioStreamingService() {
        ContextCompat.startForegroundService(
                this,
                new Intent(this, AudioStreamingService.class)
        );
    }

    private void stopAudioStreamingService() {
        stopService(new Intent(this, AudioStreamingService.class));
    }

    private void openFocusActivity() {
        Intent intent = new Intent(this, FocusActivity.class);
        startActivity(intent);
    }

    private void loadHearingProfiles() {
        new Thread(() -> {
            profileList = AppDatabase
                    .getInstance(this)
                    .hearingProfileDao()
                    .getAllProfiles();
            if (profileList.isEmpty()) {
                long id = AppDatabase
                        .getInstance(this)
                        .hearingProfileDao()
                        .insert(new HearingProfile("Default Profile","home"));
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
            for (HearingProfile hp: profileList) {
                if (hp.getId() == currentProfileId) sel = hp;
            }
            if (sel == null) {
                sel = profileList.get(0);
                currentProfileId = sel.getId();
            }
            final String name = sel.getName();
            final String iconKey = sel.getIcon();
            runOnUiThread(() -> {
                tvSelectedProfile.setText(name);
                ivProfileIcon.setImageResource(iconResForKey(iconKey));
                updateGainsForProfile(currentProfileId);
            });
        }).start();
    }

    private void reorderProfiles(List<HearingProfile> list,int selId){
        int idx=-1;
        for(int i=0;i<list.size();i++){
            if(list.get(i).getId()==selId){ idx=i; break; }
        }
        if(idx>0){
            HearingProfile hp=list.remove(idx);
            list.add(hp);
        }
    }

    private void updateGainsForProfile(int profileId){
        new Thread(() -> {
            List<HearingTestResult> results =
                    AppDatabase
                            .getInstance(this)
                            .hearingTestResultDao()
                            .getResultsForUserAndProfile(USER_ID, profileId);
            for (HearingTestResult r:results){
                Log.d(TAG, "Freq="+r.getFrequency()+" → gain="+(r.getAmplitudeStep()/100f));
            }
        }).start();
    }

    @DrawableRes
    private int iconResForKey(String key) {
        switch (key) {
            case "home":           return R.drawable.ic_home;
            case "school":         return R.drawable.ic_school;
            case "train":          return R.drawable.ic_train;
            case "palm_tree":      return R.drawable.ic_palm_tree;
            case "noodles":        return R.drawable.ic_noodles;
            case "glass_cocktail": return R.drawable.ic_glass_cocktail;
            default:               return R.drawable.ic_home;
        }
    }
}
