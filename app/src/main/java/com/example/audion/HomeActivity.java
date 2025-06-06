package com.example.audion;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import tourguide.tourguide.Overlay;
import tourguide.tourguide.Pointer;
import tourguide.tourguide.ToolTip;
import tourguide.tourguide.TourGuide;

public class HomeActivity extends AppCompatActivity {

    // ─── Tour/overlay fields ────────────────────────────────────────────────────
    private TourGuide      mCurrentTourGuideOverlay;
    private View           currentStepTarget;
    private boolean        tourActive = false;

    // ─── UI & streaming fields ──────────────────────────────────────────────────
    private WaveformView               waveformView;
    private MaterialButton             toggleButton;
    private TextView                   tvSelectedProfile;
    private TextView                   noiseStatusText;
    private ImageView                  ivProfileIcon;
    private BottomNavigationView       bottomNav;
    private SeekBar                    amplificationSeekBar;
    private SwitchMaterial             noiseRemovalSwitch;

    private boolean                    isStreaming = false;
    private List<HearingProfile>       profileList = new ArrayList<>();
    private int                        currentProfileId = -1;
    private HearingTestResultDao       hearingTestResultDao;
    private static final int           USER_ID = 1;

    // ─── Permissions & receivers ────────────────────────────────────────────────
    private static final int           REQUEST_RECORD_AUDIO   = 101;
    private static final int           PERMISSION_REQUEST_CODE = 1;
    private static final String        PREFS_NAME             = "com.example.audion.PREFERENCES";
    private static final String        KEY_SELECTED_PROFILE_ID = "selectedProfileId";
    private static final String        KEY_IS_STREAMING        = "isStreaming";
    public  static final String        KEY_NOISE_REMOVAL       = "noiseRemoval";
    public  static final String        KEY_AMPLIFICATION       = "amplificationFactor";

    private final BroadcastReceiver wfReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            float out = intent.getFloatExtra("outputLevel", 0f);
            waveformView.addLevel(out);
        }
    };

    private final BroadcastReceiver stopStreamingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            stopAudioStreamingService();
            isStreaming = false;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_IS_STREAMING, false)
                    .apply();
            updateToggleUi(false);
        }
    };

    private static final String TAG = "HomeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Remove title, enable fullscreen/cutout
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_variation_one);

        // ───────── Bind views ────────────────────────────────────────────────────
        waveformView         = findViewById(R.id.waveformView);
        toggleButton         = findViewById(R.id.toggleButton);
        tvSelectedProfile    = findViewById(R.id.tvSelectedProfile);
        ivProfileIcon        = findViewById(R.id.ivProfileIcon);
        bottomNav            = findViewById(R.id.bottomNavigationView);
        amplificationSeekBar = findViewById(R.id.seekBar);
        noiseStatusText      = findViewById(R.id.noiseStatusText);
        noiseRemovalSwitch   = findViewById(R.id.noiseRemovalSwitch);
        Button focusBtn      = findViewById(R.id.focus);

        hearingTestResultDao = AppDatabase.getInstance(this).hearingTestResultDao();

        // ───────── Request RECORD_AUDIO permission if needed ────────────────────
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.RECORD_AUDIO },
                    REQUEST_RECORD_AUDIO
            );
        }

        // ───────── Focus button ──────────────────────────────────────────────────
        focusBtn.setOnClickListener(v -> openFocusActivity());

        // ───────── ToggleButton's NORMAL listener ────────────────────────────────
        toggleButton.setOnClickListener(HomeActivity.this::handleToggleNormalClick);

        // ───────── “Get Started” launches the tour ───────────────────────────────
        findViewById(R.id.btnGetStarted).setOnClickListener(v -> startTour());

        // ───────── Bottom navigation ─────────────────────────────────────────────
        bottomNav.setSelectedItemId(R.id.navigation_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_frequencies) {
                startActivity(new Intent(this, FrequencyActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.navigation_settings) {
                startActivity(new Intent(this, MusicPlayerActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return true;
        });

        // ───────── Amplification SeekBar normal listener ────────────────────────
        amplificationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean u) {
                float maxDb = 40f;
                float curDb = (progress / (float) sb.getMax()) * maxDb;
                float ampFactor = (float) Math.pow(10, curDb / 20f);
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putFloat(KEY_AMPLIFICATION, ampFactor)
                        .apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        // ───────── Noise Removal Switch normal listener ─────────────────────────
        noiseRemovalSwitch.setOnCheckedChangeListener((btn, checked) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_NOISE_REMOVAL, checked)
                    .apply();
            noiseStatusText.setText(
                    checked ? "Noise Cancellation ON" : "Noise Cancellation OFF"
            );
        });

        // Restore saved noise‐removal state
        boolean isOn = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_NOISE_REMOVAL, false);
        noiseRemovalSwitch.setChecked(isOn);
        noiseStatusText.setText(isOn
                ? "Noise Cancellation ON"
                : "Noise Cancellation OFF"
        );

        // ───────── Profile selection ────────────────────────────────────────────
        tvSelectedProfile.setOnClickListener(v -> {
            reorderProfiles(profileList, currentProfileId);
            ProfileSelectionBottomSheet bs =
                    ProfileSelectionBottomSheet.newInstance(
                            new ArrayList<>(profileList),
                            currentProfileId
                    );
            bs.setOnProfileSelectedListener(profile -> {
                currentProfileId = profile.getId();
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_SELECTED_PROFILE_ID, currentProfileId)
                        .apply();
                tvSelectedProfile.setText(profile.getName());
                ivProfileIcon.setImageResource(iconResForKey(profile.getIcon()));
                updateGainsForProfile(currentProfileId);
            });
            bs.show(getSupportFragmentManager(), "ProfileSelection");
        });

        // ───────── Load stored profiles ─────────────────────────────────────────
        loadHearingProfiles();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register waveform updates
        ContextCompat.registerReceiver(
                this,
                wfReceiver,
                new IntentFilter("com.example.audion.WAVEFORM_UPDATE"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        // Register stop-streaming listener
        ContextCompat.registerReceiver(
                this,
                stopStreamingReceiver,
                new IntentFilter("com.example.audion.STOP_STREAMING"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(wfReceiver);
        unregisterReceiver(stopStreamingReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isStreaming = sp.getBoolean(KEY_IS_STREAMING, false);
        if (isStreaming) {
            startAudioStreamingService();
        }
        updateToggleUi(isStreaming);

        float ampFactor = sp.getFloat(KEY_AMPLIFICATION, 1f);
        double curDb = 20 * Math.log10(ampFactor);
        int prog = Math.round((float) ((curDb / 40f) * amplificationSeekBar.getMax()));
        amplificationSeekBar.setProgress(prog);

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
        stopAudioStreamingService();
        Intent intent = new Intent(this, FocusActivity.class);
        startActivity(intent);
    }

    /**
     * Exactly your existing “haptic + start/stop streaming” logic.
     * Called both normally and inside the tour.
     */
    private void handleToggleNormalClick(View v) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                );
            } else {
                vibrator.vibrate(100);
            }
        }

        if (!isStreaming) {
            if (hasMicPermission()) {
                startAudioStreamingService();
                isStreaming = true;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_IS_STREAMING, true)
                        .apply();
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
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_IS_STREAMING, false)
                    .apply();
        }
        updateToggleUi(isStreaming);
    }

    /**
     * Three‐step tour:
     *   1) Highlight toggleButton
     *   2) Highlight amplificationSeekBar
     *   3) Highlight noiseRemovalSwitch
     *
     * Tapping outside currentStepTarget cancels the tour immediately.
     */
    private void startTour() {
        tourActive = true;

        // Clean up any previous overlay
        if (mCurrentTourGuideOverlay != null) {
            mCurrentTourGuideOverlay.cleanUp();
        }

        final boolean[] step1Active = { true };

        // ─── Step 1: Highlight toggleButton ─────────────────────────────────────
        currentStepTarget = toggleButton;
        TourGuide step1 = TourGuide.init(this)
                .with(TourGuide.Technique.CLICK)
                .setPointer(new Pointer()
                        .setColor(Color.parseColor("#ffffff"))
                        .setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL))
                .setToolTip(new ToolTip()
                        .setTitle("Start/Stop Streaming")
                        .setDescription("Tap here to begin or stop audio streaming")
                        .setBackgroundColor(Color.parseColor("#0F766E"))
                        .setTextColor(Color.WHITE)
                        .setShadow(true)
                        .setGravity(Gravity.TOP | Gravity.CENTER))
                .setOverlay(new Overlay()
                        .setBackgroundColor(Color.parseColor("#AA000000")));
        mCurrentTourGuideOverlay = step1;
        step1.playOn(toggleButton);

        toggleButton.setOnClickListener(v -> {
            if (step1Active[0]) {
                // First tap during Step 1 → advance to Step 2
                step1Active[0] = false;
                step1.cleanUp();

                // ─── Step 2: Highlight amplificationSeekBar ─────────────────────
                currentStepTarget = amplificationSeekBar;
                TourGuide step2 = TourGuide.init(HomeActivity.this)
                        .with(TourGuide.Technique.CLICK)
                        .setPointer(new Pointer()
                                .setColor(Color.parseColor("#ffffff"))
                                .setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL))
                        .setToolTip(new ToolTip()
                                .setTitle("Amplification Level")
                                .setDescription("Drag to adjust amplification factor")
                                .setBackgroundColor(Color.parseColor("#0F766E"))
                                .setTextColor(Color.WHITE)
                                .setShadow(true)
                                .setGravity(Gravity.BOTTOM | Gravity.CENTER))
                        .setOverlay(new Overlay()
                                .setBackgroundColor(Color.parseColor("#99000000"))
                                .setStyle(Overlay.Style.RECTANGLE));
                mCurrentTourGuideOverlay = step2;
                step2.playOn(amplificationSeekBar);

                amplificationSeekBar.setOnTouchListener(new View.OnTouchListener() {
                    private boolean step2Active = true;

                    @Override
                    public boolean onTouch(View v2, MotionEvent event) {
                        if (step2Active && event.getAction() == MotionEvent.ACTION_DOWN) {
                            // First touch during Step 2 → advance to Step 3
                            step2Active = false;
                            step2.cleanUp();

                            // ─── Step 3: Highlight noiseRemovalSwitch ─────────────
                            currentStepTarget = noiseRemovalSwitch;
                            TourGuide step3 = TourGuide.init(HomeActivity.this)
                                    .with(TourGuide.Technique.CLICK)
                                    .setPointer(new Pointer()
                                            .setColor(Color.parseColor("#ffffff"))
                                            .setGravity(Gravity.START | Gravity.CENTER_VERTICAL))
                                    .setToolTip(new ToolTip()
                                            .setTitle("Noise Cancellation")
                                            .setDescription("Tap here to toggle noise removal")
                                            .setBackgroundColor(Color.parseColor("#0F766E"))
                                            .setTextColor(Color.WHITE)
                                            .setShadow(true)
                                            .setGravity(Gravity.END | Gravity.CENTER_VERTICAL))
                           
                                    .setOverlay(new Overlay()
                                            .setBackgroundColor(Color.parseColor("#88000000")));
                            mCurrentTourGuideOverlay = step3;
                            step3.playOn(noiseRemovalSwitch);

                            noiseRemovalSwitch.setOnClickListener(sw -> {
                                // First tap during Step 3 → finish the tour
                                step3.cleanUp();
                                tourActive = false;
                                currentStepTarget = null;

                                // Restore original listeners
                                toggleButton.setOnClickListener(HomeActivity.this::handleToggleNormalClick);
                                amplificationSeekBar.setOnTouchListener(null);
                                noiseRemovalSwitch.setOnCheckedChangeListener((button, checked) -> {
                                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                            .edit()
                                            .putBoolean(KEY_NOISE_REMOVAL, checked)
                                            .apply();
                                    noiseStatusText.setText(
                                            checked ? "Noise Cancellation ON" : "Noise Cancellation OFF"
                                    );
                                });
                            });

                            // Also run the toggle logic on this first tap
                            handleToggleNormalClick(v);

                            // **Consume** this ACTION_DOWN so no further touch logic runs:
                            return true;
                        }
                        return false;
                    }
                });
            } else {
                // If tapped after step1 but before cleanup, just run normal logic
                handleToggleNormalClick(v);
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (tourActive && ev.getAction() == MotionEvent.ACTION_DOWN && currentStepTarget != null) {
            int rawX = (int) ev.getRawX();
            int rawY = (int) ev.getRawY();

            int[] loc = new int[2];
            currentStepTarget.getLocationOnScreen(loc);
            int left   = loc[0];
            int top    = loc[1];
            int right  = left + currentStepTarget.getWidth();
            int bottom = top + currentStepTarget.getHeight();

            if (!(rawX >= left && rawX <= right && rawY >= top && rawY <= bottom)) {
                // Tapped outside highlighted view → cancel the tour
                cancelTour();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /** Clean up any active overlay and restore original listeners. */
    private void cancelTour() {
        tourActive = false;
        if (mCurrentTourGuideOverlay != null) {
            mCurrentTourGuideOverlay.cleanUp();
            mCurrentTourGuideOverlay = null;
        }
        currentStepTarget = null;

        // Restore toggleButton’s normal listener
        toggleButton.setOnClickListener(HomeActivity.this::handleToggleNormalClick);

        // Restore SeekBar’s normal listener
        amplificationSeekBar.setOnTouchListener(null);

        // Restore noiseRemovalSwitch’s normal listener
        noiseRemovalSwitch.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_NOISE_REMOVAL, checked)
                    .apply();
            noiseStatusText.setText(
                    checked ? "Noise Cancellation ON" : "Noise Cancellation OFF"
            );
        });
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
                        .insert(new HearingProfile("Default Profile", "home"));
                profileList.add(
                        AppDatabase
                                .getInstance(this)
                                .hearingProfileDao()
                                .getHearingProfileById((int) id)
                );
            }
            if (currentProfileId == -1) {
                currentProfileId = profileList.get(0).getId();
            }
            HearingProfile sel = null;
            for (HearingProfile hp : profileList) {
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

    private void reorderProfiles(List<HearingProfile> list, int selId) {
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == selId) { idx = i; break; }
        }
        if (idx > 0) {
            HearingProfile hp = list.remove(idx);
            list.add(hp);
        }
    }

    private void updateGainsForProfile(int profileId) {
        new Thread(() -> {
            List<HearingTestResult> results =
                    hearingTestResultDao.getResultsForUserAndProfile(USER_ID, profileId);
            for (HearingTestResult r : results) {
                Log.d(TAG, "Freq=" + r.getFrequency() + " → gain=" + (r.getAmplitudeStep() / 100f));
            }
        }).start();
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
