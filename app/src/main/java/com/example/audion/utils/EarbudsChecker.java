package com.example.audion.utils;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

/**
 * Utility class to check if earbuds/headphones are connected
 */
public class EarbudsChecker {
    
    private static final String TAG = "EarbudsChecker";
    
    /**
     * Check if any audio output device (wired or Bluetooth) is connected
     * @param context Application context
     * @return true if earbuds/headphones are connected
     */
    public static boolean areEarbudsConnected(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        
        if (audioManager == null) {
            Log.w(TAG, "AudioManager is null");
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Modern method: Check audio devices
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                
                // Check for wired headphones/headset
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || 
                    type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    Log.d(TAG, "Wired earbuds detected");
                    return true;
                }
                
                // Check for Bluetooth headphones/headset
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                    type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    Log.d(TAG, "Bluetooth earbuds detected");
                    return true;
                }
            }
        } else {
            // Legacy method for older devices
            boolean hasEarbuds = audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
            Log.d(TAG, "Legacy check - earbuds connected: " + hasEarbuds);
            return hasEarbuds;
        }
        
        Log.d(TAG, "No earbuds detected");
        return false;
    }
}
