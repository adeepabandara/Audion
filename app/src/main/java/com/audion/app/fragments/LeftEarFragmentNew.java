package com.audion.app.fragments;

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

import com.audion.app.R;
import com.audion.app.data.AppDatabase;
import com.audion.app.data.HearingTestResult;
import com.audion.app.data.HearingTestResultDao;
import com.audion.app.utils.CustomToast;

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
public class LeftEarFragmentNew extends Fragment {
    private static final String TAG = "LeftEarFragmentNew";
    private static final int[] FREQUENCIES = {125, 250, 500, 1000, 2000, 3000, 4000, 8000};
    
    private int userId;
    private int profileId;
    private HearingTestResultDao dao;
    private Button btnSave;
    private Map<Integer, View> rowViews = new HashMap<>();
    private Map<Integer, Float> currentThresholds = new HashMap<>();
    private boolean hasChanges = false;

    public static LeftEarFragmentNew newInstance(int userId, int profileId) {
        LeftEarFragmentNew fragment = new LeftEarFragmentNew();
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
        
        // Find save button
//        btnSave = view.findViewById(R.id.btnSaveLeftEar);
//        btnSave.setVisibility(View.GONE);
//        btnSave.setOnClickListener(v -> saveThresholds());
        
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
            
            // Store current value
            currentThresholds.put(freq, (float) dbValue);
            
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
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            Log.d(TAG, "UI updated for " + freq + "Hz: " + dbValue + " dB HL");
        }
    }

    /**
     * Show save button when changes are made
     */
    private void showSaveButton() {
        if (!hasChanges) {
            hasChanges = true;
            btnSave.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Save all threshold changes to database
     */
    private void saveThresholds() {
        Log.d(TAG, "Saving thresholds...");
        btnSave.setEnabled(false);  // Prevent double-clicks
        
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
                android.content.Intent reloadIntent = new android.content.Intent("com.audion.app.RELOAD_PROFILE");
                reloadIntent.putExtra("profileId", profileId);
                requireActivity().sendBroadcast(reloadIntent);
                Log.d(TAG, "Sent RELOAD_PROFILE broadcast");
                
                // Update UI on main thread
                requireActivity().runOnUiThread(() -> {
                    btnSave.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    hasChanges = false;
                    CustomToast.showSuccess(requireContext(), "Saved successfully");
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving thresholds", e);
                requireActivity().runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    CustomToast.showError(requireContext(), "Error saving: " + e.getMessage(), CustomToast.LENGTH_LONG);
                });
            }
        }).start();
    }
}
