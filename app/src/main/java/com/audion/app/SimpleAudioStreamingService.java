package com.audion.app;

import com.audion.app.R;

import android.app.Activity;
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
import com.audion.app.data.AppDatabase;
import com.audion.app.data.CalibrationProfileDao;
import com.audion.app.data.CalibrationProfileEntity;
import com.audion.app.data.HearingProfile;
import com.audion.app.data.HearingProfileDao;
import com.audion.app.data.HearingTestResult;
import com.audion.app.data.HearingTestResultDao;

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
    private static final String PREFS_NAME = "com.audion.app.PREFERENCES";
    private static final String KEY_AMPLIFICATION = "amplificationFactor";
    private static final String KEY_NOISE_REMOVAL = "noiseRemoval";
    
    // Focus Mode: Speaker isolation support
    public static final String ACTION_SET_SPEAKER_ISOLATION = "com.audion.app.ACTION_SET_SPEAKER_ISOLATION";
    public static final String EXTRA_ISOLATION_ENABLED = "isolationEnabled";
    public static final String EXTRA_CHUNK_ID = "chunkId";
    public static final String EXTRA_SPEAKER_ACTIVE = "speakerActive";

    private SimpleAudioEngine audioEngine;
    private SharedPreferences prefs;
    private PreferenceChangeReceiver prefReceiver;
    private SpeakerIsolationReceiver speakerIsolationReceiver;
    private AudioModeReceiver audioModeReceiver;
    private MediaProjectionReceiver mediaProjectionReceiver;
    private ProfileReloadReceiver profileReloadReceiver;
    
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
    
    // Static instance for direct callback (bypasses broadcast delay)
    private static SimpleAudioStreamingService instance = null;
    
    /**
     * Direct gain update - bypasses broadcast system for instant response
     * Always updates engine if it exists, even if not running yet (will apply on next frame)
     * Returns true if engine exists, false if service not started
     */
    public static boolean updateGainDirect(float gainDb) {
        if (instance != null && instance.audioEngine != null) {
            instance.audioEngine.setAmplificationDb(gainDb);
            boolean running = instance.audioEngine.isRunning();
            if (running) {
                Log.e(TAG, String.format("★★★ DIRECT GAIN UPDATE: %.2f dB (applied to running engine)", gainDb));
            } else {
                Log.e(TAG, String.format("★★★ DIRECT GAIN UPDATE: %.2f dB (stored in atomic var, will apply when engine starts)", gainDb));
            }
            return true;
        }
        Log.e(TAG, String.format("⚠️ DIRECT GAIN UPDATE FAILED: service not started (gain=%.2f dB queued in SharedPrefs)", gainDb));
        return false;
    }
    
    /**
     * Direct noise reduction update - bypasses broadcast system for instant response
     * Always updates engine if it exists, even if not running yet (will apply on next frame)
     * Returns true if engine exists, false if service not started
     */
    public static boolean updateNoiseReductionDirect(boolean enabled) {
        if (instance != null && instance.audioEngine != null) {
            instance.audioEngine.setNoiseReductionEnabled(enabled);
            boolean running = instance.audioEngine.isRunning();
            if (running) {
                Log.e(TAG, String.format("★★★ DIRECT NOISE UPDATE: %s (applied to running engine)", enabled ? "ON" : "OFF"));
            } else {
                Log.e(TAG, String.format("★★★ DIRECT NOISE UPDATE: %s (stored, will apply when engine starts)", enabled ? "ON" : "OFF"));
            }
            return true;
        }
        Log.e(TAG, String.format("⚠️ DIRECT NOISE UPDATE FAILED: service not started (%s queued in SharedPrefs)", enabled ? "ON" : "OFF"));
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;  // Set static reference for direct callbacks
        createNotificationChannelIfNeeded();
        
        // Initialize SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Register broadcast receiver for preference changes
        prefReceiver = new PreferenceChangeReceiver();
        IntentFilter filter = new IntentFilter("com.audion.app.PREFERENCES_CHANGED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(prefReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else{
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
        
        // Register broadcast receiver for audio mode switching (MIC/MEDIA)
        audioModeReceiver = new AudioModeReceiver();
        IntentFilter modeFilter = new IntentFilter("com.audion.app.SET_AUDIO_MODE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioModeReceiver, modeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(audioModeReceiver, modeFilter);
        }
        
        // Register broadcast receiver for MediaProjection setup
        mediaProjectionReceiver = new MediaProjectionReceiver();
        IntentFilter projectionFilter = new IntentFilter("com.audion.app.SET_MEDIA_PROJECTION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaProjectionReceiver, projectionFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaProjectionReceiver, projectionFilter);
        }
        
        // Register broadcast receiver for profile reload
        profileReloadReceiver = new ProfileReloadReceiver();
        IntentFilter profileReloadFilter = new IntentFilter("com.audion.app.RELOAD_PROFILE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(profileReloadReceiver, profileReloadFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(profileReloadReceiver, profileReloadFilter);
        }
        
        Log.e(TAG, "★★★ Broadcast receiver registered for PREFERENCES_CHANGED");
        Log.i(TAG, "★★★ Broadcast receiver registered for SPEAKER_ISOLATION");
        Log.i(TAG, "★★★ Broadcast receiver registered for RELOAD_PROFILE");
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
            
            // Set waveform callback for UI updates
            audioEngine.setWaveformCallback((inputLevel, outputLevel) -> {
                Intent wave = new Intent("com.audion.app.WAVEFORM_UPDATE")
                        .setPackage(getPackageName())
                        .putExtra("inputLevel", inputLevel)
                        .putExtra("outputLevel", outputLevel);
                sendBroadcast(wave);
                
                // Log occasionally to verify broadcast is sent
                if (System.currentTimeMillis() % 1000 < 50) {  // Roughly once per second
                    Log.d(TAG, "Broadcast waveform: in=" + inputLevel + " out=" + outputLevel);
                }
            });
            
            // Phase 2: DISABLED - Using clean Phase1 pipeline instead
            // Phase2 has aggressive filterbanks and compression that reduce clarity
            audioEngine.setPhase2Enabled(false);
            Log.i(TAG, "[Phase 1] Using clean single-band DSP pipeline");
            
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
        
        if (audioModeReceiver != null) {
            unregisterReceiver(audioModeReceiver);
            audioModeReceiver = null;
        }
        
        if (mediaProjectionReceiver != null) {
            unregisterReceiver(mediaProjectionReceiver);
            mediaProjectionReceiver = null;
        }
        
        if (profileReloadReceiver != null) {
            unregisterReceiver(profileReloadReceiver);
            profileReloadReceiver = null;
        }
        
        if (audioEngine != null) {
            audioEngine.stop();
            audioEngine.release();
            audioEngine = null;
        }
        
        stopForeground(true);
        instance = null;  // Clear static reference
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
        
        // Read noise removal (boolean, default true)
        boolean noiseRemoval = prefs.getBoolean(KEY_NOISE_REMOVAL, true);
        
        Log.e(TAG, "════════════════════════════════════════════════════════");
        Log.e(TAG, "★★★★★ APPLYING SETTINGS TO AUDIO ENGINE ★★★★★");
        Log.e(TAG, String.format("  Reading from SharedPrefs: Gain=%.1f dB, Noise=%s", amplificationDb, noiseRemoval ? "ON" : "OFF"));
        
        audioEngine.setAmplificationDb(amplificationDb);
        Log.e(TAG, String.format("  ✓ Called setAmplificationDb(%.1f)", amplificationDb));
        
        audioEngine.setNoiseReductionEnabled(noiseRemoval);
        Log.e(TAG, String.format("  ✓ Called setNoiseReductionEnabled(%s)", noiseRemoval));
        
        Log.e(TAG, "★★★★★ SETTINGS APPLIED SUCCESSFULLY ★★★★★");
        Log.e(TAG, "════════════════════════════════════════════════════════");
    }
    
    /**
     * Load personalization data from database (Phase 1).
     * Loads audiogram and calibration profiles for both ears.
     * Calculates recommended gain and applies it if no manual override exists.
     */
    private void loadPersonalizationData() {
        Log.i(TAG, "[Personalization] Loading clinical data...");
        
        // Run database access on background thread to avoid blocking main thread
        new Thread(() -> {
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
                
                // Phase 1: Load audiogram data for frequency-specific gains
                if (hasAudiogram && (leftEarAudiogram != null && !leftEarAudiogram.isEmpty())) {
                    // Convert audiogram to frequency-threshold map
                    java.util.Map<Integer, Integer> audiogramMap = new java.util.HashMap<>();
                    for (HearingTestResult result : leftEarAudiogram) {
                        // Convert amplitude step to approximate dB HL
                        // Assuming each step is 5 dB (adjust based on your calibration)
                        int thresholdDbHL = result.getAmplitudeStep() * 5;
                        audiogramMap.put(result.getFrequency(), thresholdDbHL);
                    }
                    
                    Log.i(TAG, "[Phase 1] Loading audiogram data for frequency-specific gains:");
                    for (java.util.Map.Entry<Integer, Integer> entry : audiogramMap.entrySet()) {
                        Log.i(TAG, String.format("  %d Hz: %d dB HL", entry.getKey(), entry.getValue()));
                    }
                    
                    audioEngine.setAudiogramData(audiogramMap);
                }
                
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
        }).start();  // End background thread
    }
    
    /**
     * BroadcastReceiver to listen for preference changes
     */
    private class PreferenceChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "═══════════════════════════════════════════════════════════");
            Log.e(TAG, "★★★★★ BROADCAST RECEIVED IN SERVICE ★★★★★");
            Log.e(TAG, "  Intent Action: " + intent.getAction());
            Log.e(TAG, "  Audio Engine Running: " + (audioEngine != null && audioEngine.isRunning()));
            Log.e(TAG, "═══════════════════════════════════════════════════════════");
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
    
    /**
     * BroadcastReceiver to listen for audio mode changes (MIC/MEDIA)
     */
    private class AudioModeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String modeStr = intent.getStringExtra("audio_mode");
            if (modeStr == null || audioEngine == null) {
                return;
            }
            
            Log.i(TAG, "[Audio Mode] Switching to: " + modeStr);
            
            com.audion.audio.SimpleAudioEngine.AudioMode mode;
            if ("MEDIA".equals(modeStr)) {
                mode = com.audion.audio.SimpleAudioEngine.AudioMode.MEDIA;
            } else {
                mode = com.audion.audio.SimpleAudioEngine.AudioMode.MIC;
            }
            
            boolean success = audioEngine.setAudioMode(mode);
            if (success) {
                Log.i(TAG, "[Audio Mode] Successfully switched to: " + modeStr);
            } else {
                Log.e(TAG, "[Audio Mode] Failed to switch to: " + modeStr);
            }
        }
    }
    
    /**
     * BroadcastReceiver to handle MediaProjection for phone audio capture
     */
    private class MediaProjectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int resultCode = intent.getIntExtra("result_code", -999);
            Intent data = intent.getParcelableExtra("result_data");
            
            Log.d(TAG, "[MediaProjection] Received broadcast: resultCode=" + resultCode + ", data=" + (data != null ? "present" : "null"));
            
            if (resultCode == Activity.RESULT_OK && data != null && audioEngine != null) {
                try {
                    android.media.projection.MediaProjectionManager projectionManager = 
                        (android.media.projection.MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    
                    if (projectionManager != null) {
                        android.media.projection.MediaProjection projection = 
                            projectionManager.getMediaProjection(resultCode, data);
                        
                        if (projection != null) {
                            audioEngine.setMediaProjection(projection);
                            Log.i(TAG, "[MediaProjection] Successfully set for audio capture");
                        } else {
                            Log.e(TAG, "[MediaProjection] Failed to create projection object");
                        }
                    } else {
                        Log.e(TAG, "[MediaProjection] MediaProjectionManager is null");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[MediaProjection] Error setting up: " + e.getMessage(), e);
                }
            } else {
                Log.w(TAG, "[MediaProjection] Invalid result or data - resultCode=" + resultCode + ", audioEngine=" + (audioEngine != null ? "present" : "null"));
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * BroadcastReceiver to handle profile reload requests
     */
    private class ProfileReloadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int profileId = intent.getIntExtra("PROFILE_ID", -1);
            Log.i(TAG, "★★★ PROFILE CHANGED - Reloading personalization for profile ID: " + profileId);
            
            if (profileId > 0) {
                // Run database query on background thread to avoid blocking main thread
                new Thread(() -> reloadPersonalizationForProfile(profileId)).start();
            } else {
                Log.e(TAG, "Invalid profile ID received: " + profileId);
            }
        }
    }
    
    /**
     * Reload personalization data for a specific profile ID
     */
    private void reloadPersonalizationForProfile(int profileId) {
        if (audioEngine == null) {
            Log.e(TAG, "[Reload] Audio engine not initialized");
            return;
        }
        
        try {
            AppDatabase db = AppDatabase.getInstance(this);
            HearingTestResultDao audiogramDao = db.hearingTestResultDao();
            
            // Load audiogram data for the new profile
            List<HearingTestResult> allResults = audiogramDao.getResultsForUserAndProfile(USER_ID, profileId);
            List<HearingTestResult> leftResults = new ArrayList<>();
            List<HearingTestResult> rightResults = new ArrayList<>();
            
            for (HearingTestResult r : allResults) {
                if ("LEFT".equals(r.getEarSide())) {
                    leftResults.add(r);
                } else if ("RIGHT".equals(r.getEarSide())) {
                    rightResults.add(r);
                }
            }
            
            Log.i(TAG, String.format("[Reload] Audiogram loaded: Left=%d results, Right=%d results",
                leftResults.size(), rightResults.size()));
            
            // Load calibration data for the new profile
            CalibrationProfileDao calibrationDao = db.calibrationProfileDao();
            List<CalibrationProfileEntity> leftProfiles = calibrationDao.getForEar(USER_ID, "LEFT", profileId);
            List<CalibrationProfileEntity> rightProfiles = calibrationDao.getForEar(USER_ID, "RIGHT", profileId);
            
            CalibrationProfileEntity leftCalib = leftProfiles.isEmpty() ? null : leftProfiles.get(0);
            CalibrationProfileEntity rightCalib = rightProfiles.isEmpty() ? null : rightProfiles.get(0);
            
            Log.i(TAG, String.format("[Reload] Calibration: Left=%s, Right=%s",
                leftCalib != null ? "✓" : "✗",
                rightCalib != null ? "✓" : "✗"));
            
            // Update cached data
            leftEarAudiogram = leftResults;
            rightEarAudiogram = rightResults;
            leftCalibration = leftCalib;
            rightCalibration = rightCalib;
            
            // Recalculate safe max gain
            safeMaxGainDb = GainPrescriptionHelper.calculateSafeMaxGain(leftCalib, rightCalib);
            Log.i(TAG, String.format("[Reload] UCL Limit: Safe max gain = %.1f dB", safeMaxGainDb));
            
            // Update audio engine with new audiogram data
            boolean hasAudiogram = !leftResults.isEmpty() || !rightResults.isEmpty();
            boolean hasCalibration = leftCalib != null || rightCalib != null;
            
            audioEngine.setPersonalizationAvailable(hasAudiogram, hasCalibration);
            
            if (hasAudiogram) {
                audioEngine.setAudiogramData(leftResults, rightResults);
                Log.i(TAG, "★★★ Audio engine updated with new profile personalization");
            } else {
                Log.w(TAG, "[Reload] No audiogram data available for this profile");
            }
            
            // Re-apply current settings with new safe max gain
            applySettings();
            
        } catch (Exception e) {
            Log.e(TAG, "[Reload] Failed to reload personalization: " + e.getMessage(), e);
        }
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
