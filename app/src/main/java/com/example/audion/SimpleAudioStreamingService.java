package com.example.audion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.audion.audio.GainPrescriptionHelper;
import com.audion.audio.SimpleAudioEngine;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.CalibrationProfileDao;
import com.example.audion.data.CalibrationProfileEntity;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingProfileDao;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple foreground service for audio processing using the clean RNNoise pipeline.
 * 
 * Architecture:
 * AudioRecord (480 samples @ 48kHz) → RNNoise → Amplification → Safety Limiter → AudioTrack
 * 
 * Runtime Controls:
 * - Reads amplification and noise reduction settings from SharedPreferences
 * - Updates audio engine in real-time when preferences change
 */
public class SimpleAudioStreamingService extends Service {
    private static final String TAG = "SimpleAudioService";
    private static final int NOTIFICATION_ID = 0xBADA;
    private static final String CHANNEL_ID = "audion_simple_channel";
    
    // SharedPreferences keys (must match HomeActivity)
    private static final String PREFS_NAME = "com.example.audion.PREFERENCES";
    private static final String KEY_AMPLIFICATION = "amplificationFactor";
    private static final String KEY_NOISE_REMOVAL = "noiseRemoval";
    
    // Focus Mode: Speaker isolation support
    public static final String ACTION_SET_SPEAKER_ISOLATION = "com.example.audion.ACTION_SET_SPEAKER_ISOLATION";
    public static final String EXTRA_ISOLATION_ENABLED = "isolationEnabled";
    public static final String EXTRA_CHUNK_ID = "chunkId";
    public static final String EXTRA_SPEAKER_ACTIVE = "speakerActive";

    private SimpleAudioEngine audioEngine;
    private SharedPreferences prefs;
    private PreferenceChangeReceiver prefReceiver;
    private SpeakerIsolationReceiver speakerIsolationReceiver;
    
    // Speaker isolation state (for Focus Mode)
    private boolean speakerIsolationEnabled = false;
    private volatile boolean selectedSpeakerActive = false;
    
    // Personalization data (Phase 1)
    private static final int USER_ID = 1; // Default user ID
    private int hearingProfileId = -1;
    private List<HearingTestResult> leftEarAudiogram;
    private List<HearingTestResult> rightEarAudiogram;
    private CalibrationProfileEntity leftCalibration;
    private CalibrationProfileEntity rightCalibration;
    private float safeMaxGainDb = 100.0f; // Default max, updated from UCL

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded();
        
        // Initialize SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Register broadcast receiver for preference changes
        prefReceiver = new PreferenceChangeReceiver();
        IntentFilter filter = new IntentFilter("com.example.audion.PREFERENCES_CHANGED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(prefReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(prefReceiver, filter);
        }
        
        // Register broadcast receiver for speaker isolation (Focus Mode)
        speakerIsolationReceiver = new SpeakerIsolationReceiver();
        IntentFilter isolationFilter = new IntentFilter(ACTION_SET_SPEAKER_ISOLATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speakerIsolationReceiver, isolationFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(speakerIsolationReceiver, isolationFilter);
        }
        
        Log.e(TAG, "★★★ Broadcast receiver registered for PREFERENCES_CHANGED");
        Log.i(TAG, "★★★ Broadcast receiver registered for SPEAKER_ISOLATION");
        Log.i(TAG, "SimpleAudioStreamingService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: starting simple audio processing");
        
        // Start foreground notification
        startForeground(NOTIFICATION_ID, buildNotification());
        
        // Initialize and start audio engine
        if (audioEngine == null) {
            audioEngine = new SimpleAudioEngine();
            
            // Phase 2: Enable per-ear 4-band processing
            audioEngine.setPhase2Enabled(true);
            Log.i(TAG, "[Phase 2] Enabled per-ear 4-band processing mode");
            
            if (!audioEngine.initialize()) {
                Log.e(TAG, "Failed to initialize audio engine");
                stopSelf();
                return START_NOT_STICKY;
            }
            
            // Load personalization data (Phase 1)
            loadPersonalizationData();
            
            // Apply initial settings from SharedPreferences
            applySettings();
        }
        
        if (!audioEngine.isRunning()) {
            if (!audioEngine.start()) {
                Log.e(TAG, "Failed to start audio engine");
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        
        Log.i(TAG, "SimpleAudioStreamingService started successfully");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy: stopping simple audio processing");
        
        // Unregister broadcast receivers
        if (prefReceiver != null) {
            unregisterReceiver(prefReceiver);
            prefReceiver = null;
        }
        
        if (speakerIsolationReceiver != null) {
            unregisterReceiver(speakerIsolationReceiver);
            speakerIsolationReceiver = null;
        }
        
        if (audioEngine != null) {
            audioEngine.stop();
            audioEngine.release();
            audioEngine = null;
        }
        
        stopForeground(true);
        super.onDestroy();
        
        Log.i(TAG, "SimpleAudioStreamingService destroyed");
    }
    
    /**
     * Apply current settings from SharedPreferences to audio engine.
     * Phase 1: Includes UCL-based safety clamping.
     */
    private void applySettings() {
        if (audioEngine == null) return;
        
        // Read amplification (0-100 dB, default 0)
        float amplificationDb = prefs.getFloat(KEY_AMPLIFICATION, 0.0f);
        
        // Apply UCL-based safety clamping (Phase 1)
        if (leftCalibration != null || rightCalibration != null) {
            float clampedGain = GainPrescriptionHelper.clampToSafeGain(
                amplificationDb, 
                leftCalibration, 
                rightCalibration
            );
            
            if (clampedGain != amplificationDb) {
                Log.w(TAG, String.format("[Personalization] UCL safety clamp: %.1f dB → %.1f dB",
                    amplificationDb, clampedGain));
                amplificationDb = clampedGain;
            }
        }
        
        audioEngine.setAmplificationDb(amplificationDb);
        
        // Read noise removal (boolean, default true)
        boolean noiseRemoval = prefs.getBoolean(KEY_NOISE_REMOVAL, true);
        audioEngine.setNoiseReductionEnabled(noiseRemoval);
        
        Log.e(TAG, "════════════════════════════════════════════════════════");
        Log.e(TAG, "★★★ SETTINGS APPLIED ★★★");
        Log.e(TAG, String.format("[Personalization] Applied Gain: %.1f dB", amplificationDb));
        Log.e(TAG, String.format("Noise Removal: %s", noiseRemoval ? "ON" : "OFF"));
        Log.e(TAG, "════════════════════════════════════════════════════════");
    }
    
    /**
     * Load personalization data from database (Phase 1).
     * Loads audiogram and calibration profiles for both ears.
     * Calculates recommended gain and applies it if no manual override exists.
     */
    private void loadPersonalizationData() {
        Log.i(TAG, "[Personalization] Loading clinical data...");
        
        try {
            AppDatabase db = AppDatabase.getInstance(this);
            
            // Get or create hearing profile
            HearingProfileDao profileDao = db.hearingProfileDao();
            List<HearingProfile> profiles = profileDao.getAllProfiles();
            
            if (profiles.isEmpty()) {
                // Create default profile
                HearingProfile defaultProfile = new HearingProfile("Default Profile", "");
                hearingProfileId = (int) profileDao.insert(defaultProfile);
                Log.i(TAG, "[Personalization] Created default profile ID: " + hearingProfileId);
            } else {
                hearingProfileId = profiles.get(0).getId();
                Log.i(TAG, "[Personalization] Using profile ID: " + hearingProfileId);
            }
            
            // Load audiogram data
            HearingTestResultDao audiogramDao = db.hearingTestResultDao();
            List<HearingTestResult> allResults = audiogramDao.getResultsForUserAndProfile(USER_ID, hearingProfileId);
            leftEarAudiogram = new ArrayList<>();
            rightEarAudiogram = new ArrayList<>();
            for (HearingTestResult r : allResults) {
                if ("LEFT".equals(r.getEarSide())) {
                    leftEarAudiogram.add(r);
                } else if ("RIGHT".equals(r.getEarSide())) {
                    rightEarAudiogram.add(r);
                }
            }
            
            Log.i(TAG, String.format("[Personalization] AudiogramLoaded: Left=%d results, Right=%d results",
                leftEarAudiogram != null ? leftEarAudiogram.size() : 0,
                rightEarAudiogram != null ? rightEarAudiogram.size() : 0));
            
            // Load calibration data
            CalibrationProfileDao calibrationDao = db.calibrationProfileDao();
            List<CalibrationProfileEntity> leftProfiles = calibrationDao.getForEar(USER_ID, "LEFT", hearingProfileId);
            List<CalibrationProfileEntity> rightProfiles = calibrationDao.getForEar(USER_ID, "RIGHT", hearingProfileId);
            
            leftCalibration = leftProfiles.isEmpty() ? null : leftProfiles.get(0);
            rightCalibration = rightProfiles.isEmpty() ? null : rightProfiles.get(0);
            
            Log.i(TAG, String.format("[Personalization] Calibration: Left=%s, Right=%s",
                leftCalibration != null ? "✓" : "✗",
                rightCalibration != null ? "✓" : "✗"));
            
            // Calculate safe max gain from UCL
            safeMaxGainDb = GainPrescriptionHelper.calculateSafeMaxGain(leftCalibration, rightCalibration);
            Log.i(TAG, String.format("[Personalization] UCLLimit: Safe max gain = %.1f dB", safeMaxGainDb));
            
            // Check if personalization is complete
            boolean hasAudiogram = (leftEarAudiogram != null && !leftEarAudiogram.isEmpty()) ||
                                   (rightEarAudiogram != null && !rightEarAudiogram.isEmpty());
            boolean hasCalibration = leftCalibration != null || rightCalibration != null;
            
            // Notify engine of personalization status
            if (audioEngine != null) {
                audioEngine.setPersonalizationAvailable(hasAudiogram, hasCalibration);
                
                // Phase 2: Pass audiogram data to engine for per-ear processing
                if (audioEngine.isPhase2Enabled() && hasAudiogram) {
                    Log.i(TAG, "[Phase 2] Passing audiogram data to audio engine...");
                    audioEngine.setAudiogramData(leftEarAudiogram, rightEarAudiogram);
                    
                    // Enable compression for better dynamic range
                    audioEngine.setCompressionEnabled(true);
                }
            }
            
            // Calculate and apply recommended gain if:
            // 1. Personalization data exists
            // 2. No manual gain override in SharedPreferences (i.e., amplification is still 0)
            if (hasAudiogram && hasCalibration) {
                float currentGain = prefs.getFloat(KEY_AMPLIFICATION, 0.0f);
                
                if (currentGain == 0.0f) {
                    // No manual override - calculate recommended gain
                    float recommendedGain = GainPrescriptionHelper.calculateBinauralRecommendedGain(
                        leftEarAudiogram, 
                        rightEarAudiogram
                    );
                    
                    if (recommendedGain > 0.0f) {
                        // Apply safety clamping
                        recommendedGain = GainPrescriptionHelper.clampToSafeGain(
                            recommendedGain, 
                            leftCalibration, 
                            rightCalibration
                        );
                        
                        // Save to preferences
                        prefs.edit()
                            .putFloat(KEY_AMPLIFICATION, recommendedGain)
                            .apply();
                        
                        Log.i(TAG, "════════════════════════════════════════════════════════");
                        Log.i(TAG, "[Personalization] AppliedGain: Automatic baseline = " + 
                            String.format("%.1f dB", recommendedGain));
                        Log.i(TAG, "  Based on audiogram PTA (NAL-RP formula: Gain = 0.4 × PTA)");
                        Log.i(TAG, "  Clamped to safe UCL limit");
                        Log.i(TAG, "════════════════════════════════════════════════════════");
                    }
                } else {
                    Log.i(TAG, String.format("[Personalization] Manual gain override detected: %.1f dB (not applying automatic gain)",
                        currentGain));
                }
            } else {
                Log.w(TAG, "[Personalization] Incomplete data - using generic settings");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "[Personalization] Error loading clinical data: " + e.getMessage(), e);
        }
    }
    
    /**
     * BroadcastReceiver to listen for preference changes
     */
    private class PreferenceChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "★★★ PREFERENCES CHANGED - Updating audio engine ★★★");
            applySettings();
        }
    }
    
    /**
     * BroadcastReceiver to listen for speaker isolation state (Focus Mode)
     */
    private class SpeakerIsolationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean enabled = intent.getBooleanExtra(EXTRA_ISOLATION_ENABLED, false);
            boolean speakerActive = intent.getBooleanExtra(EXTRA_SPEAKER_ACTIVE, false);
            int chunkId = intent.getIntExtra(EXTRA_CHUNK_ID, -1);
            
            speakerIsolationEnabled = enabled;
            selectedSpeakerActive = speakerActive;
            
            if (audioEngine != null) {
                audioEngine.setSpeakerIsolationEnabled(speakerIsolationEnabled);
                audioEngine.setSelectedSpeakerActive(selectedSpeakerActive);
            }
            
            Log.d(TAG, String.format("[Focus Mode] Speaker isolation: enabled=%b, active=%b, chunk=%d",
                enabled, speakerActive, chunkId));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Audion Simple Stream",
                    NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Realtime audio processing with RNNoise");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audion Live (Simple)")
            .setContentText("RNNoise processing active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true);

        return builder.build();
    }
}
