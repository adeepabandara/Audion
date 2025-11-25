
package com.audion.app.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Feature flag system for safe rollout of new DSP pipeline.
 * Provides runtime switching between legacy and new audio processing chains.
 */
public final class FeatureFlags {
    private static final String TAG = "FeatureFlags";
    private static final String PREFS_NAME = "com.audion.app.feature_flags";
    
    // Feature flag keys
    private static final String KEY_USE_NEW_PIPELINE = "useNewPipeline";
    private static final String KEY_QA_MODE = "qaMode";
    private static final String KEY_DEBUG_LOGGING = "debugLogging";
    private static final String KEY_ALLOCATION_TRACKING = "allocationTracking";
    
    // Default values
    private static final boolean DEFAULT_USE_NEW_PIPELINE = true; // Test NEW pipeline in PASSTHROUGH mode
    private static final boolean DEFAULT_QA_MODE = false;
    private static final boolean DEFAULT_DEBUG_LOGGING = false;
    private static final boolean DEFAULT_ALLOCATION_TRACKING = false;
    
    /**
     * Main feature flag: Use new AudioEngine → DspGraph pipeline
     * When false, falls back to legacy AudioStreamingService processing
     */
    public static boolean useNewPipeline(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_USE_NEW_PIPELINE, DEFAULT_USE_NEW_PIPELINE);
        Log.d(TAG, "useNewPipeline: " + enabled);
        return enabled;
    }
    
    /**
     * Enable/disable new pipeline (typically from QA settings)
     */
    public static void setUseNewPipeline(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_USE_NEW_PIPELINE, enabled).apply();
        Log.i(TAG, "New pipeline " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * QA mode enables debug overlays and advanced telemetry
     */
    public static boolean qaMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_QA_MODE, DEFAULT_QA_MODE);
    }
    
    /**
     * Enable/disable QA mode
     */
    public static void setQaMode(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_QA_MODE, enabled).apply();
        Log.i(TAG, "QA mode " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Enhanced debug logging for pipeline debugging
     */
    public static boolean debugLogging(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DEBUG_LOGGING, DEFAULT_DEBUG_LOGGING);
    }
    
    /**
     * Enable/disable debug logging
     */
    public static void setDebugLogging(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DEBUG_LOGGING, enabled).apply();
    }
    
    /**
     * Allocation tracking for zero-allocation validation
     */
    public static boolean allocationTracking(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ALLOCATION_TRACKING, DEFAULT_ALLOCATION_TRACKING);
    }
    
    /**
     * Emergency fallback to legacy pipeline (for runtime errors)
     * This provides a safety mechanism if the new pipeline fails to start
     */
    public static void emergencyFallbackToLegacy(Context context, String reason) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_USE_NEW_PIPELINE, false).apply();
        Log.e(TAG, "EMERGENCY FALLBACK to legacy pipeline: " + reason);
    }
    
    /**
     * Check if system should attempt new pipeline startup
     * Returns false if recent failures occurred
     */
    public static boolean shouldAttemptNewPipeline(Context context) {
        // Could add failure counting logic here in future
        return useNewPipeline(context);
    }
    
    /**
     * Get all feature flags as debug string
     */
    public static String getDebugInfo(Context context) {
        return String.format(
            "FeatureFlags: Pipeline=%s, QA=%s, Debug=%s, Alloc=%s",
            useNewPipeline(context),
            qaMode(context),
            debugLogging(context),
            allocationTracking(context)
        );
    }
    
    /**
     * Reset all flags to defaults (for testing)
     */
    public static void resetToDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        Log.i(TAG, "All feature flags reset to defaults");
    }
}