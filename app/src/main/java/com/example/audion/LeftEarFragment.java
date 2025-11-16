package com.example.audion.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audion.R;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.example.audion.utils.CustomToast;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clean implementation of Left Ear frequency adjustment
 * Requirements:
 * - Load threshold data for selected profile
 * - Allow adjustment of dB HL values (0-120)
 * - Save changes to database
 * - Trigger audio personalization reload
 */
public class LeftEarFragment extends Fragment {
    private static final String TAG = "LeftEarFragment";
    private static final int[] FREQUENCIES = {125, 250, 500, 1000, 2000, 3000, 4000, 8000};
    
    private int userId;
    private int profileId;
    private HearingTestResultDao dao;
    // Removed btnSave and btnDiscard - buttons removed from UI
    // private Button btnSave;
    // private Button btnDiscard;
    // private View buttonContainer;
    private Map<Integer, View> rowViews = new HashMap<>();
    private Map<Integer, Float> currentThresholds = new HashMap<>();
    private Map<Integer, Float> originalThresholds = new HashMap<>();
    private boolean hasChanges = false;

    public static LeftEarFragment newInstance(int userId, int profileId) {
        LeftEarFragment fragment = new LeftEarFragment();
        Bundle args = new Bundle();
        args.putInt("USER_ID", userId);
        args.putInt("PROFILE_ID", profileId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_left_ear, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Get arguments
        if (getArguments() != null) {
            userId = getArguments().getInt("USER_ID", 1);
            profileId = getArguments().getInt("PROFILE_ID", -1);
            Log.d(TAG, "Initialized: userId=" + userId + ", profileId=" + profileId);
        }
        
        // Initialize DAO
        dao = AppDatabase.getInstance(requireContext()).hearingTestResultDao();
        
        // Removed button initialization - buttons removed from UI
        // buttonContainer = view.findViewById(R.id.buttonContainer);
        // btnSave = view.findViewById(R.id.btnSaveLeftEar);
        // btnDiscard = view.findViewById(R.id.btnDiscardLeftEar);
        // buttonContainer.setVisibility(View.GONE);
        // btnSave.setOnClickListener(v -> saveThresholds());
        // btnDiscard.setOnClickListener(v -> discardChanges());
        
        // Find all frequency row views
        for (int freq : FREQUENCIES) {
            int resId = getResources().getIdentifier("row" + freq + "Hz", "id", requireContext().getPackageName());
            View row = view.findViewById(resId);
            if (row != null) {
                rowViews.put(freq, row);
            } else {
                Log.e(TAG, "Could not find row for frequency: " + freq + "Hz");
            }
        }
        
        // Load data from database
        loadThresholds();
    }

    /**
     * Load threshold data from database for current user and profile
     */
    private void loadThresholds() {
        new Thread(() -> {
            try {
                Log.d(TAG, "Loading thresholds for userId=" + userId + ", profileId=" + profileId);
                
                // Query database
                List<HearingTestResult> results = dao.getResultsForUserAndProfile(userId, profileId);
                Log.d(TAG, "Found " + results.size() + " results");
                
                // Extract left ear thresholds
                Map<Integer, Float> thresholds = new HashMap<>();
                for (HearingTestResult result : results) {
                    if ("left".equalsIgnoreCase(result.getEarSide())) {
                        float threshold = result.getThresholdDbHL();
                        thresholds.put(result.getFrequency(), threshold);
                        Log.d(TAG, "Loaded: " + result.getFrequency() + "Hz = " + threshold + " dB HL");
                    }
                }
                
                // Update UI on main thread
                requireActivity().runOnUiThread(() -> updateUI(thresholds));
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading thresholds", e);
                requireActivity().runOnUiThread(() -> 
                    CustomToast.showError(requireContext(), "Error loading data")
                );
            }
        }).start();
    }

    /**
     * Update UI with loaded threshold values
     */
    private void updateUI(Map<Integer, Float> thresholds) {
        for (int freq : FREQUENCIES) {
            View row = rowViews.get(freq);
            if (row == null) continue;
            
            // Find views
            TextView dbLabel = row.findViewById(R.id.dbValueLabel);
            SeekBar seekBar = row.findViewById(R.id.frequencySeekBar);
            
            // Set frequency value in the label
            dbLabel.setText(freq + " Hz");
            
            // Get threshold value (default to 0 if not found)
            float threshold = thresholds.getOrDefault(freq, 0.0f);
            int dbValue = Math.round(threshold);
            
            // Store current value and original value
            currentThresholds.put(freq, (float) dbValue);
            originalThresholds.put(freq, (float) dbValue);
            
            // Configure SeekBar
            seekBar.setMax(120);  // 0-120 dB HL range
            seekBar.setProgress(dbValue);
            
            // Set up change listener
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        // Frequency label stays the same, only threshold value changes internally
                        currentThresholds.put(freq, (float) progress);
                        showSaveButton();
                    }
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // Apply to audio when user finishes adjusting
                    applyRealtimeAudioUpdate();
                }
            });
            
            Log.d(TAG, "UI updated for " + freq + "Hz: " + dbValue + " dB HL");
        }
    }

    /**
     * Apply real-time audio updates as user adjusts sliders
     */
    private void applyRealtimeAudioUpdate() {
        // Send broadcast to update audio engine immediately
        android.content.Intent reloadIntent = new android.content.Intent("com.example.audion.RELOAD_PROFILE");
        reloadIntent.putExtra("PROFILE_ID", profileId);
        reloadIntent.putExtra("realtime", true);
        requireActivity().sendBroadcast(reloadIntent);
        Log.d(TAG, "Sent real-time RELOAD_PROFILE broadcast for profileId=" + profileId);
    }

    /**
     * Show save button when changes are made
     */
    private void showSaveButton() {
        if (!hasChanges) {
            hasChanges = true;
//            buttonContainer.setVisibility(View.VISIBLE);
            Log.d(TAG, "Changes detected - showing save/discard buttons");
        }
    }

    /**
     * Discard all changes and restore original values
     */
    private void discardChanges() {
        Log.d(TAG, "Discarding changes...");
        
        // Restore original values
        for (int freq : FREQUENCIES) {
            View row = rowViews.get(freq);
            if (row == null) continue;
            
            TextView dbLabel = row.findViewById(R.id.dbValueLabel);
            SeekBar seekBar = row.findViewById(R.id.frequencySeekBar);
            
            float originalValue = originalThresholds.getOrDefault(freq, 0.0f);
            int dbValue = Math.round(originalValue);
            
            seekBar.setProgress(dbValue);
            dbLabel.setText(dbValue + " dB");
            currentThresholds.put(freq, originalValue);
        }
        
        // Hide button container
        hasChanges = false;
//        buttonContainer.setVisibility(View.GONE);
        
        // Apply real-time audio update to restore original audio settings
        applyRealtimeAudioUpdate();
        
        CustomToast.showSuccess(requireContext(), "Changes discarded");
    }

    /**
     * Save all threshold changes to database
     */
    private void saveThresholds() {
        Log.d(TAG, "Saving thresholds...");
        // btnSave.setEnabled(false);  // Prevent double-clicks - button removed
        
        new Thread(() -> {
            try {
                int savedCount = 0;
                
                for (Map.Entry<Integer, Float> entry : currentThresholds.entrySet()) {
                    int freq = entry.getKey();
                    float threshold = entry.getValue();
                    
                    // Check if record exists
                    HearingTestResult existing = dao.findUserEarFrequencyForProfile(
                        userId, "left", freq, profileId
                    );
                    
                    if (existing != null) {
                        // Update existing
                        existing.setThresholdDbHL(threshold);
                        existing.setThresholdDbSPL(threshold);
                        existing.setTestTimestamp(System.currentTimeMillis());
                        dao.update(existing);
                        Log.d(TAG, "Updated: " + freq + "Hz = " + threshold + " dB HL (ID: " + existing.getId() + ")");
                    } else {
                        // Insert new
                        HearingTestResult newResult = new HearingTestResult(
                            userId,
                            "left",
                            freq,
                            threshold,  // thresholdDbHL
                            threshold,  // thresholdDbSPL
                            true,       // isReliable
                            0,          // reversalCount
                            1.0f,       // reliabilityScore
                            profileId
                        );
                        dao.insert(newResult);
                        Log.d(TAG, "Inserted: " + freq + "Hz = " + threshold + " dB HL");
                    }
                    savedCount++;
                }
                
                Log.d(TAG, "Saved " + savedCount + " thresholds successfully");
                
                // Send broadcast to reload audio personalization
                android.content.Intent reloadIntent = new android.content.Intent("com.example.audion.RELOAD_PROFILE");
                reloadIntent.putExtra("PROFILE_ID", profileId);
                requireActivity().sendBroadcast(reloadIntent);
                Log.d(TAG, "Sent RELOAD_PROFILE broadcast");
                
                // Update UI on main thread
                requireActivity().runOnUiThread(() -> {
                    // buttonContainer.setVisibility(View.GONE); // Button removed
                    // btnSave.setEnabled(true); // Button removed
                    hasChanges = false;
                    
                    // Update original thresholds to match current values
                    originalThresholds.clear();
                    originalThresholds.putAll(currentThresholds);
                    
                    CustomToast.showSuccess(requireContext(), "Saved successfully");
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving thresholds", e);
                requireActivity().runOnUiThread(() -> {
                    // btnSave.setEnabled(true); // Button removed
                    CustomToast.showError(requireContext(), "Error saving: " + e.getMessage(), CustomToast.LENGTH_LONG);
                });
            }
        }).start();
    }
    
    /**
     * Check if there are unsaved changes
     */
    public boolean hasUnsavedChanges() {
        Log.d(TAG, "hasUnsavedChanges called: " + hasChanges);
        return hasChanges;
    }
    
    /**
     * Save changes (public method for external calls)
     */
    public void saveChanges() {
        saveThresholds();
    }
    
    /**
     * Discard changes (public method for external calls)
     */
    public void discardChangesExternal() {
        discardChanges();
    }
}
