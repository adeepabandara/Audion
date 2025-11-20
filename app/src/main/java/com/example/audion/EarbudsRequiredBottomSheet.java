package com.example.audion;

import com.audion.psap.R;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Bottom sheet that requires user to connect earbuds before proceeding
 * Monitors audio device connections and auto-dismisses when earbuds are connected
 */
public class EarbudsRequiredBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "EarbudsRequired";
    
    private AudioManager audioManager;
    private TextView statusText;
    private BroadcastReceiver audioDeviceReceiver;
    private boolean isReceiverRegistered = false;
    
    public interface OnEarbudsConnectedListener {
        void onEarbudsConnected();
    }
    
    private OnEarbudsConnectedListener listener;
    
    public static EarbudsRequiredBottomSheet newInstance() {
        return new EarbudsRequiredBottomSheet();
    }
    
    public void setOnEarbudsConnectedListener(OnEarbudsConnectedListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Make bottom sheet non-cancelable
        setCancelable(false);
        // Apply rounded corner theme
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_earbuds_required, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        statusText = view.findViewById(R.id.tvStatus);
        audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
        
        // Setup close button
        ImageButton btnClose = view.findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
        
        // Setup broadcast receiver to listen for audio device changes
        setupAudioDeviceReceiver();
        
        // Check if earbuds are already connected
        checkForEarbuds();
    }
    
    private void setupAudioDeviceReceiver() {
        audioDeviceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "Audio device broadcast received: " + action);
                
                if (AudioManager.ACTION_HEADSET_PLUG.equals(action) ||
                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action) ||
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                    
                    // Delay check to ensure device state is updated
                    if (getView() != null) {
                        getView().postDelayed(() -> checkForEarbuds(), 500);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        
        requireContext().registerReceiver(audioDeviceReceiver, filter);
        isReceiverRegistered = true;
        Log.d(TAG, "Audio device receiver registered");
    }
    
    private void checkForEarbuds() {
        boolean hasEarbuds = false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Modern method: Check audio devices
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                
                // Check for wired headphones/headset
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || 
                    type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    Log.d(TAG, "Wired earbuds detected: " + device.getProductName());
                    hasEarbuds = true;
                    break;
                }
                
                // Check for Bluetooth headphones/headset
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                    type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    Log.d(TAG, "Bluetooth earbuds detected: " + device.getProductName());
                    hasEarbuds = true;
                    break;
                }
            }
        } else {
            // Legacy method for older devices
            hasEarbuds = audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        }
        
        if (hasEarbuds) {
            Log.d(TAG, "Earbuds connected - dismissing bottom sheet");
            updateStatus("Connected! ✓", true);
            
            // Dismiss after short delay
            if (getView() != null) {
                getView().postDelayed(() -> {
                    if (listener != null) {
                        listener.onEarbudsConnected();
                    }
                    dismiss();
                }, 800);
            }
        } else {
            Log.d(TAG, "No earbuds detected");
            updateStatus("Waiting for audio device...", false);
        }
    }
    
    private void updateStatus(String message, boolean connected) {
        if (statusText != null) {
            statusText.setText(message);
            if (connected) {
                statusText.setTextColor(getResources().getColor(R.color.primary));
            } else {
                statusText.setTextColor(getResources().getColor(R.color.text_secondary));
            }
        }
    }
    
    @Override
    public void onStart() {
        super.onStart();
        
        // Make bottom sheet non-dismissible by touching outside
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().setCanceledOnTouchOutside(false);
            
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                
                behavior.setHideable(false);
                behavior.setDraggable(false);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Unregister receiver
        if (isReceiverRegistered && audioDeviceReceiver != null) {
            try {
                requireContext().unregisterReceiver(audioDeviceReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "Audio device receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
    }
}
