package com.audion.app;

import com.audion.app.R;




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
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.audion.app.utils.CustomToast;
import com.audion.app.utils.EarbudsChecker;



import android.content.res.ColorStateList;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.Drawable;



import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.audion.app.data.AppDatabase;
import com.audion.app.data.CalibrationDao;
import com.audion.app.data.CalibrationEntry;
import com.audion.app.data.CalibrationProfileDao;
import com.audion.app.data.CalibrationProfileEntity;
import com.audion.app.data.HearingProfile;
import com.audion.app.data.HearingTestResult;
import com.audion.app.data.HearingTestResultDao;
import com.audion.audio.GainPrescriptionHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import android.widget.LinearLayout;


import java.util.ArrayList;
import java.util.List;

import tourguide.tourguide.Overlay;
import tourguide.tourguide.Pointer;
import tourguide.tourguide.ToolTip;
import tourguide.tourguide.TourGuide;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // UI & streaming fields
    private WaveformView      waveformView;
    private int waveformUpdateCount = 0;
    private MaterialButton    toggleButton;
    private TextView          tvSelectedProfile;
    private TextView          noiseStatusText;
    private ImageView         ivProfileIcon;
    private BottomNavigationView bottomNav;
    private SeekBar           amplificationSeekBar;
    private SwitchMaterial    noiseRemovalSwitch;
    private TabLayout         tabLayout;
    private LinearLayout      volumeCard;

    private boolean           isStreaming = false;
    private List<HearingProfile> profileList = new ArrayList<>();
    private int               currentProfileId = -1;
    private HearingTestResultDao hearingTestResultDao;
    private static final int  USER_ID = 1;

    // Permissions & receivers
    private static final int  REQUEST_RECORD_AUDIO   = 101;
    private static final int  PERMISSION_REQUEST_CODE = 1;
    private static final int  REQUEST_MEDIA_PROJECTION = 102;  // For phone audio capture
    private static final String PREFS_NAME          = "com.audion.app.PREFERENCES";
    private static final String KEY_SELECTED_PROFILE_ID = "selectedProfileId";
    private static final String KEY_IS_STREAMING    = "isStreaming";
    public static final String KEY_NOISE_REMOVAL    = "noiseRemoval";
    public static final String KEY_AMPLIFICATION    = "amplificationFactor";

    private final BroadcastReceiver wfReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            float out = intent.getFloatExtra("outputLevel", 0f);
            if (waveformView != null) {
                waveformView.addLevel(out);
                waveformUpdateCount++;
                if (waveformUpdateCount % 100 == 0) {
                    android.util.Log.d("HomeActivity", "Waveform received: " + out + " (count=" + waveformUpdateCount + ")");
                }
            }
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

    // Confirmation flags for thresholds
    private boolean allowAbove40 = false;
    private boolean allowAbove70 = false;
    private int     lastProgress = 0;

    private SeekBar.OnSeekBarChangeListener gainChangeListener;

    // Audio source toggle
    private LinearLayout micToggleOption;
    private LinearLayout phoneToggleOption;
    private ImageView micIcon;
    private ImageView phoneIcon;
    private boolean isMicrophoneSource = true;  // true = Mic, false = Phone

    // Tour/overlay fields
    private TourGuide mCurrentTourGuideOverlay;
    private View      currentStepTarget;
    private boolean   tourActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);


        super.onCreate(savedInstanceState);



        setContentView(R.layout.activity_variation_one);

        // Bind views

        waveformView         = findViewById(R.id.waveformView);
        toggleButton         = findViewById(R.id.toggleButton);
        bottomNav            = findViewById(R.id.bottomNavigationView);
        amplificationSeekBar = findViewById(R.id.seekBar);
        noiseStatusText      = findViewById(R.id.noiseStatusText);
        noiseRemovalSwitch   = findViewById(R.id.noiseRemovalSwitch);
        tabLayout            = findViewById(R.id.tabLayout);
        
        // Audio source toggle views
        micToggleOption      = findViewById(R.id.micToggleOption);
        phoneToggleOption    = findViewById(R.id.phoneToggleOption);
        micIcon              = findViewById(R.id.micIcon);
        phoneIcon            = findViewById(R.id.phoneIcon);
        
        // Optional views (may not exist in all layouts)
        tvSelectedProfile    = findViewById(R.id.tvSelectedProfile);
        ivProfileIcon        = findViewById(R.id.ivProfileIcon);
        volumeCard           = findViewById(R.id.volumeCard);

        hearingTestResultDao = AppDatabase.getInstance(this).hearingTestResultDao();

        // Initialize waveform with flat line (always visible now)
        if (waveformView != null) {
            waveformView.reset();
        }

        // Check and update personalization status banner
        updatePersonalizationStatusBanner();

        // Request RECORD_AUDIO permission if needed
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.RECORD_AUDIO },
                    REQUEST_RECORD_AUDIO
            );
        }


        Intent launch = getIntent();
        if (launch.getBooleanExtra("START_TOUR", false)) {
            String type = launch.getStringExtra("TOUR_TYPE");
            switch (type) {
                case "PLAY":
                    startPlayTour();
                    break;
                case "AMPLIFY":
                    startAmplifyTour();
                    break;
                case "NOISE":
                    startNoiseTour();
                    break;
                default:
                    // no default
            }
        }


        // ─── Setup TabLayout at top ───────────────────────────────────────────────
        tabLayout.addTab(tabLayout.newTab().setText("Normal"));
        tabLayout.addTab(tabLayout.newTab().setText("Focus"));
        tabLayout.selectTab(tabLayout.getTabAt(0));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                // Only “Normal” stays selected; “Focus” triggers activity.
                if (tab.getPosition() == 0) { }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        LinearLayout tabStrip = (LinearLayout) tabLayout.getChildAt(0);
        if (tabStrip != null && tabStrip.getChildCount() > 1) {
            View focusTabView = tabStrip.getChildAt(1);
            focusTabView.setOnClickListener(v -> {
                openFocusActivity();
                tabLayout.selectTab(tabLayout.getTabAt(0));
            });
        }

        // ToggleButton’s normal listener
        toggleButton.setOnClickListener(HomeActivity.this::handleToggleNormalClick);



        // Bottom navigation (only if it exists in layout)
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navigation_home);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.navigation_home) {
                    // Already on Home
                    return true;
                } else if (id == R.id.navigation_frequencies) {
                    startActivity(new Intent(this, FrequencyActivity.class));
                    overridePendingTransition(0, 0);
                    finish();  // Close current activity so back button works properly
                    return true;
                } else if (id == R.id.navigation_settings) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    overridePendingTransition(0, 0);
                    finish();  // Close current activity so back button works properly
                    return true;
                }
                return false;
            });
        }

        // ─── Amplification SeekBar with confirmation dialogs ─────────────────────────
        final float maxDb = 40f;  // SeekBar spans 0–40 dB (safe consumer hearing assistance limit)

        // (A) Intercept touch so user cannot drag past thresholds if not confirmed
        amplificationSeekBar.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float curDb = (lastProgress / (float) amplificationSeekBar.getMax()) * maxDb;
                if (curDb >= 20f && !allowAbove40) {
                    return true; // block further movement
                }
                if (curDb >= 30f && !allowAbove70) {
                    return true;
                }
            }
            return false; // otherwise allow SeekBar to handle it
        });



// 1) install the listener
        gainChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                // compute dB, thresholds
                float curDb = (progress / (float) sb.getMax()) * maxDb;
                int maxProgress = sb.getMax();
                int threshold40 = Math.round((20f / maxDb) * maxProgress);  // 20 dB warning threshold
                int threshold70 = Math.round((30f / maxDb) * maxProgress);  // 30 dB warning threshold

                // Try to update colors if using old drawable structure (backward compatibility)
                try {
                    // grab the three layers of the track
                    LayerDrawable ld = (LayerDrawable) sb.getProgressDrawable().mutate();
                    Drawable prLayer  = ld.findDrawableByLayerId(android.R.id.progress);
                    Drawable secLayer = ld.findDrawableByLayerId(android.R.id.secondaryProgress);
                    Drawable bgLayer  = ld.findDrawableByLayerId(android.R.id.background);

                    // fetch our colors
                    int primaryColor = ContextCompat.getColor(HomeActivity.this, R.color.primary);
                    int bgColor      = ContextCompat.getColor(HomeActivity.this, R.color.background);
                    int redAlert     = ContextCompat.getColor(HomeActivity.this, R.color.red_alert);
                    int lightRedAlert   = ContextCompat.getColor(HomeActivity.this, R.color.red_alert_light);

                    // tint the filled portion: red if ≥20 dB, otherwise primary
                    if (prLayer != null) prLayer.setTint(curDb >= 20f ? redAlert : primaryColor);
                    // the 0→20dB zone (secondaryProgress) stays background
                    if (secLayer != null) secLayer.setTint(bgColor);
                    // the rest of the bar is always red
                    if (bgLayer != null) bgLayer.setTint(lightRedAlert);
                    sb.setSecondaryProgress(threshold40);

                    // also tint the thumb the same way
                    Drawable thumb = sb.getThumb();
                    if (thumb != null) {
                        thumb = thumb.mutate();
                        thumb.setTint(curDb >= 20f ? redAlert : primaryColor);
                        sb.setThumb(thumb);
                    }
                } catch (Exception e) {
                    // New modern seekbar - skip color changes
                    Log.d(TAG, "Modern seekbar in use, skipping legacy color updates");
                }

                if (fromUser) {
                    // Extreme warning (30 dB)
                    if (curDb > 30f && !allowAbove70 && lastProgress <= threshold70) {
                        sb.setEnabled(false);
                        sb.setOnSeekBarChangeListener(null);
                        sb.setProgress(threshold70);
                        lastProgress = threshold70;
                        showThresholdDialog(
                                "High Amplification",
                                "Amplification above 30 dB is for severe hearing loss. Ensure comfortable listening levels. Continue?",
                                () -> {
                                    allowAbove70 = true;
                                    sb.setEnabled(true);
                                    // SMOOTH RAMP: Gradually increase gain to avoid click
                                    rampGainSmoothly(threshold70, progress, maxProgress, maxDb, sb);
                                    sb.setOnSeekBarChangeListener(gainChangeListener);
                                },
                                () -> {
                                    sb.setEnabled(true);
                                    sb.setProgress(threshold70);
                                    lastProgress = threshold70;
                                    sb.setOnSeekBarChangeListener(gainChangeListener);
                                }
                        );
                        return;
                    }
                    // High warning (20 dB)
                    if (curDb > 20f && curDb <= 30f && !allowAbove40 && lastProgress <= threshold40) {
                        sb.setEnabled(false);
                        sb.setOnSeekBarChangeListener(null);
                        sb.setProgress(threshold40);
                        lastProgress = threshold40;
                        showThresholdDialog(
                                "Moderate Amplification",
                                "Amplification above 20 dB is for moderate hearing loss. Your hearing is protected. Continue?",
                                () -> {
                                    allowAbove40 = true;
                                    sb.setEnabled(true);
                                    // SMOOTH RAMP: Gradually increase gain to avoid click
                                    rampGainSmoothly(threshold40, progress, maxProgress, maxDb, sb);
                                    sb.setOnSeekBarChangeListener(gainChangeListener);
                                },
                                () -> {
                                    sb.setEnabled(true);
                                    sb.setProgress(threshold40);
                                    lastProgress = threshold40;
                                    sb.setOnSeekBarChangeListener(gainChangeListener);
                                }
                        );
                        return;
                    }
                    // reset confirmations if moved back down
                    if (curDb <= 20f) allowAbove40 = false;
                    if (curDb <= 30f) allowAbove70 = false;

                    // finally apply the new gain
                    sb.setProgress(progress);
                    lastProgress = progress;
                }
                
                // Apply gain immediately - 15ms smoothing prevents artifacts
                applyGain(progress, maxProgress, maxDb);
            }

            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb)  { }
        };



        amplificationSeekBar.setOnSeekBarChangeListener(gainChangeListener);

// 2) force one initial tint/layout pass immediately:
        amplificationSeekBar.post(() ->
                gainChangeListener.onProgressChanged(
                        amplificationSeekBar,
                        amplificationSeekBar.getProgress(),
                        false
                )
        );


        new Thread(() -> {
            CalibrationDao calDao = AppDatabase
                    .getInstance(this)
                    .calibrationDao();

            // userId is always 1 in your case, profileId is whatever’s currently selected
            List<CalibrationEntry> entries =
                    calDao.getForUserProfile(USER_ID, currentProfileId);

            if (!entries.isEmpty()) {
                // take the first entry (or pick LEFT/RIGHT if you want to be fancy)
                int baselineStep = entries.get(0).getBaselineStep();

                // clamp it to [0..maxProg]
                final int maxProg = amplificationSeekBar.getMax();
                final int prog    = Math.max(0, Math.min(baselineStep, maxProg));

                // now post back to the UI thread
                runOnUiThread(() -> {
                    amplificationSeekBar.setProgress(prog);
                    // repaint the bar’s colors & thresholds
                    gainChangeListener.onProgressChanged(
                            amplificationSeekBar,
                            prog,
                            false
                    );
                });
            }
        }).start();


        // Noise Removal Switch listener
        noiseRemovalSwitch.setOnCheckedChangeListener((btn, checked) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_NOISE_REMOVAL, checked)
                    .commit();  // Use commit() for immediate write
            noiseStatusText.setText(
                    checked ? "Noise Cancellation ON" : "Noise Cancellation OFF"
            );
            
            // DIRECT UPDATE: Call service method directly (instant, no broadcast delay)
            SimpleAudioStreamingService.updateNoiseReductionDirect(checked);
            
            // NOTE: Broadcast removed to prevent race conditions with direct updates
            // Service reads SharedPrefs on startup via applySettings()
            Log.e("HomeActivity", "★★★★★ TOGGLE SWITCHED: Noise = " + (checked ? "ON" : "OFF") + " → Direct update sent");
        });
        // Read with default true (noise reduction ON by default)
        boolean isOn = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_NOISE_REMOVAL, true);
        noiseRemovalSwitch.setChecked(isOn);
        noiseStatusText.setText(isOn
                ? "Noise Cancellation ON"
                : "Noise Cancellation OFF"
        );

        // Audio Source Toggle listeners
        setupAudioSourceToggle();

        // Profile selection (if view exists)
        if (tvSelectedProfile != null) {
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
                    if (ivProfileIcon != null) {
                        ivProfileIcon.setImageResource(iconResForKey(profile.getIcon()));
                    }
                    
                    // Broadcast profile change to audio service for real-time personalization update
                    Intent reloadIntent = new Intent("com.audion.app.RELOAD_PROFILE");
                    reloadIntent.putExtra("PROFILE_ID", currentProfileId);
                    sendBroadcast(reloadIntent);
                    
                    Log.i(TAG, "Profile switched to: " + profile.getName() + " (ID: " + currentProfileId + ")");
                    
                    updateGainsForProfile(currentProfileId);
                });
                bs.show(getSupportFragmentManager(), "ProfileSelectionBS");
            });
        }

        loadHearingProfiles();
    }

    private void setupAudioSourceToggle() {
        // Set initial state
        updateToggleUI(isMicrophoneSource);
        
        // Microphone toggle click
        micToggleOption.setOnClickListener(v -> {
            if (!isMicrophoneSource) {
                // Switch to microphone mode
                switchToMicrophoneMode();
            }
        });
        
        // Phone audio toggle click
        phoneToggleOption.setOnClickListener(v -> {
            if (isMicrophoneSource) {
                // Check Android version
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                    CustomToast.showError(this, "Phone audio mode requires Android 10 or higher", CustomToast.LENGTH_LONG);
                    return;
                }
                
                // Request MediaProjection permission for audio capture
                requestMediaProjectionPermission();
            }
        });
        
        // Restore saved state
        isMicrophoneSource = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean("audio_source_mic", true);
        updateToggleUI(isMicrophoneSource);
    }
    
    private void switchToMicrophoneMode() {
        isMicrophoneSource = true;
        updateToggleUI(true);
        
        // Save preference
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean("audio_source_mic", true)
            .apply();
        
        // Switch audio engine mode
        Intent intent = new Intent("com.audion.app.SET_AUDIO_MODE");
        intent.putExtra("audio_mode", "MIC");
        sendBroadcast(intent);
        
        CustomToast.showSuccess(this, "Listening to environment");
        Log.i(TAG, "Audio mode: Microphone (environment)");
    }
    
    private void switchToPhoneMode() {
        isMicrophoneSource = false;
        updateToggleUI(false);
        
        // Save preference
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean("audio_source_mic", false)
            .apply();
        
        // Switch audio engine mode
        Intent intent = new Intent("com.audion.app.SET_AUDIO_MODE");
        intent.putExtra("audio_mode", "MEDIA");
        sendBroadcast(intent);
        
        CustomToast.showSuccess(this, "Amplifying phone media");
        Log.i(TAG, "Audio mode: Phone media");
    }
    
    private void requestMediaProjectionPermission() {
        // Show explanation dialog with improved wording
        new AlertDialog.Builder(this)
            .setTitle(R.string.audio_capture_permission_title)
            .setMessage(R.string.audio_capture_permission_message)
            .setPositiveButton(R.string.i_understand_allow, (dialog, which) -> {
                // Request MediaProjection permission
                android.media.projection.MediaProjectionManager projectionManager = 
                    (android.media.projection.MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (projectionManager != null) {
                    startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
                }
            })
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                dialog.dismiss();
                CustomToast.showError(this, getString(R.string.phone_audio_permission_required));
            })
            .show();
    }
    
    private void updateToggleUI(boolean isMic) {
        if (isMic) {
            // Microphone selected
            micToggleOption.setBackgroundResource(R.drawable.toggle_option_selected);
            phoneToggleOption.setBackgroundResource(R.drawable.toggle_option_unselected);
            micIcon.setColorFilter(getResources().getColor(R.color.primary));
            phoneIcon.setColorFilter(getResources().getColor(R.color.onboarding_text_secondary));
        } else {
            // Phone audio selected
            micToggleOption.setBackgroundResource(R.drawable.toggle_option_unselected);
            phoneToggleOption.setBackgroundResource(R.drawable.toggle_option_selected);
            micIcon.setColorFilter(getResources().getColor(R.color.onboarding_text_secondary));
            phoneIcon.setColorFilter(getResources().getColor(R.color.primary));
        }
    }

    private void applyGain(int progress, int maxProgress, float maxDb) {
        float curDb = (progress / (float) maxProgress) * maxDb;
        
        Log.e(TAG, "════════════════════════════════════════════════════════");
        Log.e(TAG, String.format("applyGain() CALLED: progress=%d, maxProgress=%d, curDb=%.2f", progress, maxProgress, curDb));
        Log.e(TAG, String.format("  isStreaming=%s (audio %s)", isStreaming, isStreaming ? "RUNNING" : "STOPPED"));
        
        // Store as dB directly (not linear factor) for SimpleAudioEngine
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean saved = prefs.edit()
                .putFloat(KEY_AMPLIFICATION, curDb)  // Store dB value directly
                .commit();  // Use commit() for immediate write
        
        Log.e(TAG, String.format("SharedPrefs WRITE: saved=%s, key=%s, value=%.2f", saved, KEY_AMPLIFICATION, curDb));
        
        // Verify it was written
        float readBack = prefs.getFloat(KEY_AMPLIFICATION, -999f);
        Log.e(TAG, String.format("SharedPrefs READ-BACK: %.2f dB (should be %.2f)", readBack, curDb));
        
        // DIRECT UPDATE: Call service static method (instant, no broadcast delay)
        if (isStreaming) {
            boolean serviceExists = SimpleAudioStreamingService.updateGainDirect(curDb);
            if (serviceExists) {
                Log.e(TAG, String.format("★★★★★ GAIN UPDATE SENT: %.1f dB (real-time or queued for engine start)", curDb));
            } else {
                Log.e(TAG, "⚠️ SERVICE STARTING - gain saved to SharedPrefs, will apply when ready");
            }
        } else {
            Log.e(TAG, "⚠️ AUDIO STOPPED - gain saved to SharedPrefs, will apply when you press Start");
        }
        
        // NOTE: Broadcast removed to prevent race conditions with direct updates
        // Service reads SharedPrefs on startup via applySettings()
        Log.e(TAG, "════════════════════════════════════════════════════════");
    }

    /**
     * Ramp gain smoothly from start to end position to avoid clicks.
     * Used when confirming high gain warnings.
     */
    private void rampGainSmoothly(int startProgress, int endProgress, int maxProgress, float maxDb, SeekBar seekBar) {
        Handler handler = new Handler(Looper.getMainLooper());
        int steps = 10; // 10 steps over ~200ms = smooth ramp
        int stepDelay = 20; // 20ms between steps
        
        for (int i = 0; i <= steps; i++) {
            final int currentStep = i;
            handler.postDelayed(() -> {
                // Linear interpolation from start to end
                int currentProgress = startProgress + (int)((endProgress - startProgress) * (currentStep / (float)steps));
                seekBar.setProgress(currentProgress);
                lastProgress = currentProgress;
                applyGain(currentProgress, maxProgress, maxDb);
            }, i * stepDelay);
        }
    }

    private void showThresholdDialog(
            String title,
            String message,
            Runnable onConfirm,
            Runnable onCancel
    ) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", (dialog, which) -> {
                    onCancel.run();
                    dialog.dismiss();
                })
                .setPositiveButton("I'm sure", (dialog, which) -> {
                    onConfirm.run();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(
                this,
                wfReceiver,
                new IntentFilter("com.audion.app.WAVEFORM_UPDATE"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        ContextCompat.registerReceiver(
                this,
                stopStreamingReceiver,
                new IntentFilter("com.audion.app.STOP_STREAMING"),
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // MediaProjection permission granted
                Log.i(TAG, "MediaProjection permission granted");
                
                // Send MediaProjection result to service
                Intent intent = new Intent("com.audion.app.SET_MEDIA_PROJECTION");
                intent.putExtra("result_code", resultCode);
                intent.putExtra("result_data", data);
                sendBroadcast(intent);
                
                // Wait a bit for MediaProjection to be set in service, then switch mode
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    switchToPhoneMode();
                }, 500); // 500ms delay to ensure MediaProjection is ready
            } else {
                // Permission denied
                Log.w(TAG, "MediaProjection permission denied");
                CustomToast.showError(this, "Permission required for phone audio mode");
                // Keep toggle on microphone
                isMicrophoneSource = true;
                updateToggleUI(true);
            }
        }
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

        float ampDb = sp.getFloat(KEY_AMPLIFICATION, 0.0f);  // Read as dB directly
        int maxDb = 40;  // 0-40 dB range
        int prog = Math.round((ampDb / maxDb) * amplificationSeekBar.getMax());
        amplificationSeekBar.setProgress(prog);

        noiseRemovalSwitch.setChecked(sp.getBoolean(KEY_NOISE_REMOVAL, true));  // Default true

        int saved = sp.getInt(KEY_SELECTED_PROFILE_ID, -1);
        if (saved != -1 && saved != currentProfileId) {
            currentProfileId = saved;
            new Thread(() -> {
                HearingProfile hp = AppDatabase
                        .getInstance(this)
                        .hearingProfileDao()
                        .getHearingProfileById(saved);
                runOnUiThread(() -> {
                    if (tvSelectedProfile != null) {
                        tvSelectedProfile.setText(hp.getName());
                    }
                    if (ivProfileIcon != null) {
                        ivProfileIcon.setImageResource(iconResForKey(hp.getIcon()));
                    }
                });
            }).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, but don't auto-start streaming
                Log.d(TAG, "Microphone permission granted");
            } else {
                // Permission denied
                CustomToast.showError(this, "Microphone permission required");
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioStreamingService();
                isStreaming = true;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_IS_STREAMING, true)
                        .apply();
                updateToggleUi(true);
            } else {
                CustomToast.showError(this, "Microphone permission required");
            }
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
                new Intent(this, SimpleAudioStreamingService.class)
        );
    }

    private void stopAudioStreamingService() {
        stopService(new Intent(this, SimpleAudioStreamingService.class));
    }

    private void openFocusActivity() {
        stopAudioStreamingService();
        Intent intent = new Intent(this, FocusActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
    }

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
            // Check for earbuds before starting
            if (!EarbudsChecker.areEarbudsConnected(this)) {
                showEarbudsRequiredSheet();
                return;
            }
            
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

    private void startTour() {
        tourActive = true;
        if (mCurrentTourGuideOverlay != null) {
            mCurrentTourGuideOverlay.cleanUp();
        }

        final boolean[] step1Active = { true };
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
                step1Active[0] = false;
                step1.cleanUp();

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

                amplificationSeekBar.setOnTouchListener((v2, event) -> {
                    boolean step2ActiveInner = true;
                    if (step2ActiveInner && event.getAction() == MotionEvent.ACTION_DOWN) {
                        step2ActiveInner = false;
                        step2.cleanUp();

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

                        noiseRemovalSwitch.setOnCheckedChangeListener((button, checked) -> {
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                    .edit()
                                    .putBoolean(KEY_NOISE_REMOVAL, checked)
                                    .apply();
                            noiseStatusText.setText(
                                    checked ? "Noise Cancellation ON" : "Noise Cancellation OFF"
                            );
                        });

                        noiseRemovalSwitch.setOnClickListener(sw -> {
                            step3.cleanUp();
                            tourActive = false;
                            currentStepTarget = null;

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

                        handleToggleNormalClick(v);
                        return true;
                    }
                    return false;
                });
            } else {
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
                cancelTour();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void cancelTour() {
        tourActive = false;
        if (mCurrentTourGuideOverlay != null) {
            mCurrentTourGuideOverlay.cleanUp();
            mCurrentTourGuideOverlay = null;
        }
        currentStepTarget = null;

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
                // Only update UI elements if they exist in the layout
                if (tvSelectedProfile != null) {
                    tvSelectedProfile.setText(name);
                }
                if (ivProfileIcon != null) {
                    ivProfileIcon.setImageResource(iconResForKey(iconKey));
                }
                updateGainsForProfile(currentProfileId);
            });
        }).start();
    }

    private void reorderProfiles(List<HearingProfile> list, int selId) {
        // Only reorder if there's more than one profile
        if (list.size() <= 1) {
            return;
        }
        
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == selId) { idx = i; break; }
        }
        if (idx > 0) {
            HearingProfile hp = list.remove(idx);
            list.add(0, hp); // Move to top, not end
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
            // Waveform is always visible - just reset it for streaming
            if (waveformView != null) {
                waveformView.reset(); // Initialize with zeros for smooth animation
            }
        } else {
            toggleButton.setText("Start");
            toggleButton.setIconResource(R.drawable.ic_play);
            toggleButton.setBackgroundTintList(
                    getResources().getColorStateList(R.color.green_circle)
            );
            // Waveform stays visible - just reset it to flat line
            if (waveformView != null) {
                waveformView.reset();
            }
        }
    }

    private void showEarbudsRequiredSheet() {
        EarbudsRequiredBottomSheet bottomSheet = new EarbudsRequiredBottomSheet();
        bottomSheet.setOnEarbudsConnectedListener(() -> {
            // Automatically start streaming when earbuds are connected
            if (hasMicPermission()) {
                startAudioStreamingService();
                isStreaming = true;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_IS_STREAMING, true)
                        .apply();
                updateToggleUi(isStreaming);
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{ Manifest.permission.RECORD_AUDIO },
                        PERMISSION_REQUEST_CODE
                );
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "earbuds_required");
    }


    
private void startPlayTour() {
    tourActive = true;
    if (mCurrentTourGuideOverlay != null) {
        mCurrentTourGuideOverlay.cleanUp();
    }

    currentStepTarget = toggleButton;

    mCurrentTourGuideOverlay = TourGuide.init(this)
        // keep the click technique
        .with(TourGuide.Technique.CLICK)
        // white pointer, pointing from below and centered
        .setPointer(new Pointer()
            .setColor(Color.WHITE)
            .setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL))
        // tooltip above the button, same text + styling
        .setToolTip(new ToolTip()
            .setTitle("Start/Stop Streaming")
            .setDescription("Tap here to begin or stop audio streaming")
            .setBackgroundColor(Color.parseColor("#0F766E"))
            .setTextColor(Color.WHITE)
            .setShadow(true)
            .setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL))
        // semi‐transparent black overlay
        .setOverlay(new Overlay()
            .setBackgroundColor(0xAA000000));

    // play it on the actual toggle button
    mCurrentTourGuideOverlay.playOn(toggleButton);
}


private void startAmplifyTour() {
    tourActive = true;
    if (mCurrentTourGuideOverlay != null) {
        mCurrentTourGuideOverlay.cleanUp();
    }

    // 1) Highlight the whole card
    View card = findViewById(R.id.volumeCard);
    currentStepTarget = card;
    mCurrentTourGuideOverlay = TourGuide.init(this)
        .with(TourGuide.Technique.CLICK)  // CLICK-only is an option too
        .setPointer(new Pointer()
            .setColor(Color.WHITE)
            .setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL))
        .setToolTip(new ToolTip()
            .setTitle("Amplification Level")
            .setDescription("Drag here to adjust amplification")
            .setBackgroundColor(Color.parseColor("#0F766E"))
            .setTextColor(Color.WHITE)
            .setGravity(Gravity.BOTTOM | Gravity.CENTER))
        .setOverlay(new Overlay()
            .setBackgroundColor(Color.parseColor("#99000000"))
            .setStyle(Overlay.Style.RECTANGLE));
    mCurrentTourGuideOverlay.playOn(card);

    // 2) Intercept a drag (ACTION_MOVE) on that same card
    card.setOnTouchListener((v, ev) -> {
        if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            // drag started—clean up the overlay and proceed
            mCurrentTourGuideOverlay.cleanUp();
            tourActive = false;
            // e.g. move on to the next step of your tour:
            startNoiseTour();  
            return true;
        }
        return false;
    });
}

private void startNoiseTour() {
    tourActive = true;
    if (mCurrentTourGuideOverlay != null) mCurrentTourGuideOverlay.cleanUp();

    View target = findViewById(R.id.noiseRemovalSwitch);
    currentStepTarget = target;

    mCurrentTourGuideOverlay = TourGuide.init(this)
        .with(TourGuide.Technique.CLICK)
        .setPointer(new Pointer()
            .setColor(Color.WHITE)
            .setGravity(Gravity.END | Gravity.CENTER_VERTICAL))    // point from the right
        .setToolTip(new ToolTip()
            .setTitle("Noise Cancellation")
            .setDescription("Tap here\nto toggle noise removal")
            .setBackgroundColor(Color.parseColor("#0F766E"))
            .setTextColor(Color.WHITE)
            .setShadow(true)
            .setGravity(Gravity.START | Gravity.CENTER_VERTICAL)) // align the bubble to the left
        .setOverlay(new Overlay()
            .setBackgroundColor(Color.parseColor("#88000000")));

    mCurrentTourGuideOverlay.playOn(target);
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
    
    /**
     * Check calibration status and update the personalization status banner
     */
    private void updatePersonalizationStatusBanner() {
        try {
            // Get the status card and its child views
            com.google.android.material.card.MaterialCardView statusCard = findViewById(R.id.personalizationStatusCard);
            TextView statusTitle = findViewById(R.id.personalizationStatusTitle);
            TextView statusSubtitle = findViewById(R.id.personalizationStatusSubtitle);
            ImageView statusIcon = findViewById(R.id.personalizationStatusIcon);
            com.google.android.material.button.MaterialButton actionButton = findViewById(R.id.personalizationActionButton);
            
            if (statusCard == null) return;
            
            // Check if calibration data exists for current user and profile
            SharedPreferences prefs = getSharedPreferences("HearingProfilePrefs", MODE_PRIVATE);
            int userId = prefs.getInt("selected_user_id", 1);
            int hearingProfileId = prefs.getInt("selected_hearing_profile_id", 1);
            
            // Query clinical data on background thread (Phase 1)
            AppDatabase db = AppDatabase.getInstance(this);
            new Thread(() -> {
                try {
                    // Check audiogram data
                    HearingTestResultDao audiogramDao = db.hearingTestResultDao();
                    List<HearingTestResult> allResults = audiogramDao.getResultsForUserAndProfile(userId, hearingProfileId);
                    int leftAudiogramCount = 0, rightAudiogramCount = 0;
                    for (HearingTestResult r : allResults) {
                        if ("LEFT".equals(r.getEarSide())) leftAudiogramCount++;
                        else if ("RIGHT".equals(r.getEarSide())) rightAudiogramCount++;
                    }
                    boolean hasAudiogram = (leftAudiogramCount > 0 || rightAudiogramCount > 0);
                    
                    // Check calibration data
                    CalibrationProfileDao calibrationDao = db.calibrationProfileDao();
                    int leftCalCount = calibrationDao.getProfileCountByUserAndEar(userId, "LEFT", hearingProfileId);
                    int rightCalCount = calibrationDao.getProfileCountByUserAndEar(userId, "RIGHT", hearingProfileId);
                    boolean hasCalibration = (leftCalCount > 0 || rightCalCount > 0);
                    
                    // Load calibration profiles for UCL-based SeekBar max
                    List<CalibrationProfileEntity> leftCal = calibrationDao.getForEar(userId, "LEFT", hearingProfileId);
                    List<CalibrationProfileEntity> rightCal = calibrationDao.getForEar(userId, "RIGHT", hearingProfileId);
                    CalibrationProfileEntity leftCalEntity = leftCal.isEmpty() ? null : leftCal.get(0);
                    CalibrationProfileEntity rightCalEntity = rightCal.isEmpty() ? null : rightCal.get(0);
                    
                    float safeMaxGainDb = GainPrescriptionHelper.calculateSafeMaxGain(leftCalEntity, rightCalEntity);
                    
                    // Update UI on main thread
                    runOnUiThread(() -> {
                        if (hasAudiogram && hasCalibration) {
                            // Full personalization available - Phase 1 ✅
                            statusTitle.setText("✅ Personalized for your hearing");
                            statusTitle.setTextColor(ContextCompat.getColor(this, R.color.primary));
                            statusSubtitle.setText("Audiogram & calibration active");
                            statusIcon.setImageResource(R.drawable.ic_check_circle);
                            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary));
                            actionButton.setText("RE-TEST");
                            actionButton.setOnClickListener(v -> {
                                Intent intent = new Intent(this, GeneralInstructionActivity.class);
                                startActivity(intent);
                                overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                            });
                            
                            // Set SeekBar max based on UCL (Phase 1)
                            if (amplificationSeekBar != null) {
                                int seekBarMax = (int) Math.ceil(safeMaxGainDb);
                                amplificationSeekBar.setMax(seekBarMax);
                                Log.i(TAG, String.format("[Personalization] SeekBar max set to %d dB (from UCL)", seekBarMax));
                            }
                        } else {
                            // Incomplete personalization ⚠️
                            statusTitle.setText("⚠️ Generic settings (no hearing profile)");
                            statusTitle.setTextColor(ContextCompat.getColor(this, R.color.red_alert));
                            
                            if (!hasAudiogram && !hasCalibration) {
                                statusSubtitle.setText("Run hearing test for personalized audio");
                            } else if (!hasAudiogram) {
                                statusSubtitle.setText("Calibration done, audiometry needed");
                            } else {
                                statusSubtitle.setText("Audiometry done, calibration needed");
                            }
                            
                            statusIcon.setImageResource(R.drawable.ic_hearing);
                            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.red_alert));
                            actionButton.setText("START TEST");
                            actionButton.setOnClickListener(v -> {
                                Intent intent = new Intent(this, GeneralInstructionActivity.class);
                                startActivity(intent);
                                overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                            });
                            
                            // Use default max for SeekBar
                            if (amplificationSeekBar != null) {
                                amplificationSeekBar.setMax(100); // Default 100 dB
                            }
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("HomeActivity", "Error checking personalization status: " + e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            android.util.Log.e("HomeActivity", "Error updating personalization banner: " + e.getMessage());
        }
    }
}
