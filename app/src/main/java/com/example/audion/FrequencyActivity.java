package com.example.audion;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.example.audion.fragments.LeftEarFragment;
import com.example.audion.fragments.RightEarFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrequencyActivity extends AppCompatActivity {
    private static final String TAG = "FrequencyActivity";
    public static final int USER_ID = 1;

    private static final String PREFS_NAME              = "com.example.audion.PREFERENCES";
    private static final String KEY_SELECTED_PROFILE_ID = "selectedProfileId";

    private ImageView ivProfileIcon;
    private AppCompatTextView tvSelectedProfile;
    private Chip chipLeft, chipRight;
    private BottomNavigationView bottomNav;

    private List<HearingProfile> profileList = new ArrayList<>();
    private int currentHearingProfileId = -1;
    
    private int pendingNavigationId = -1;  // Store pending navigation destination

    // same keys/drawables as in HomeActivity.iconResForKey(...)
    private static final Map<String,Integer> ICON_MAP = new HashMap<>();
    static {
        ICON_MAP.put("home",           R.drawable.ic_home);
        ICON_MAP.put("school",         R.drawable.ic_school);
        ICON_MAP.put("train",          R.drawable.ic_train);
        ICON_MAP.put("palm_tree",      R.drawable.ic_palm_tree);
        ICON_MAP.put("noodles",        R.drawable.ic_noodles);
        ICON_MAP.put("glass_cocktail", R.drawable.ic_glass_cocktail);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        // 2) hide the support ActionBar (if you’re using AppCompat)
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_frequency);

        ivProfileIcon     = findViewById(R.id.ivProfileIcon);
        tvSelectedProfile = findViewById(R.id.tvSelectedProfile);
        chipLeft          = findViewById(R.id.chipLeft);
        chipRight         = findViewById(R.id.chipRight);
        bottomNav         = findViewById(R.id.bottomNavigationView);
        
        View profilePickerCard = findViewById(R.id.profilePickerCard);

        bottomNav.setSelectedItemId(R.id.navigation_frequencies);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            
            // Check for unsaved changes before navigating
            if (hasUnsavedChanges() && id != R.id.navigation_frequencies) {
                pendingNavigationId = id;
                showUnsavedChangesDialog();
                return false;  // Prevent navigation for now
            }
            
            return handleNavigation(id);
        });

        profilePickerCard.setOnClickListener(v -> {
            // Check for unsaved changes before switching profiles
            if (hasUnsavedChanges()) {
                pendingNavigationId = -3;  // Special value for profile switching
                showUnsavedChangesDialog();
                return;
            }
            
            showProfilePicker();
        });

        chipLeft.setOnClickListener(v -> {
            // Check for unsaved changes before switching ears
            if (hasUnsavedChanges() && chipRight.isChecked()) {
                pendingNavigationId = -4;  // Special value for ear switching to left
                showUnsavedChangesDialog();
                return;
            }
            
            chipLeft.setChecked(true);
            chipRight.setChecked(false);
            updateCurrentFragment();
        });
        chipRight.setOnClickListener(v -> {
            // Check for unsaved changes before switching ears
            if (hasUnsavedChanges() && chipLeft.isChecked()) {
                pendingNavigationId = -5;  // Special value for ear switching to right
                showUnsavedChangesDialog();
                return;
            }
            
            chipRight.setChecked(true);
            chipLeft.setChecked(false);
            updateCurrentFragment();
        });
        chipLeft.setChecked(true);

        loadHearingProfiles();
    }
    
    /**
     * Show profile picker bottom sheet
     */
    private void showProfilePicker() {
        reorderProfiles(profileList, currentHearingProfileId);
        ProfileSelectionBottomSheet sheet =
            ProfileSelectionBottomSheet.newInstance(
                new ArrayList<>(profileList),
                currentHearingProfileId
            );
        sheet.setOnProfileSelectedListener(this::applyProfileSelection);
        sheet.show(getSupportFragmentManager(), "ProfileSelection");
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int saved = sp.getInt(KEY_SELECTED_PROFILE_ID, -1);
        if (saved != -1 && saved != currentHearingProfileId && !profileList.isEmpty()) {
            for (HearingProfile hp : profileList) {
                if (hp.getId() == saved) {
                    applyProfileSelection(hp);
                    break;
                }
            }
        }
    }

    private void loadHearingProfiles() {
        new Thread(() -> {
            profileList = AppDatabase
                .getInstance(this)
                .hearingProfileDao()
                .getAllProfiles();

            if (profileList.isEmpty()) {
                long id = AppDatabase.getInstance(this)
                    .hearingProfileDao()          // ← fixed here
                    .insert(new HearingProfile("Default Profile","home"));
                profileList = List.of(
                  AppDatabase.getInstance(this)
                    .hearingProfileDao()         // ← and here
                    .getHearingProfileById((int)id)
                );
            }

            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int saved = sp.getInt(KEY_SELECTED_PROFILE_ID, -1);
            currentHearingProfileId = (saved != -1)
                ? saved
                : profileList.get(0).getId();

            HearingProfile sel = profileList.stream()
                .filter(hp -> hp.getId() == currentHearingProfileId)
                .findFirst()
                .orElse(profileList.get(0));

            runOnUiThread(() -> applyProfileSelection(sel));
        }).start();
    }

    private void applyProfileSelection(HearingProfile profile) {
        currentHearingProfileId = profile.getId();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_PROFILE_ID, currentHearingProfileId)
            .apply();

        tvSelectedProfile.setText(profile.getName());
        ivProfileIcon.setImageResource(
            ICON_MAP.getOrDefault(profile.getIcon(), R.drawable.ic_home)
        );
        updateCurrentFragment();
    }

    private void reorderProfiles(List<HearingProfile> list, int selId) {
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == selId) { idx = i; break; }
        }
        if (idx > 0) {
            HearingProfile hp = list.remove(idx);
            list.add(0, hp);
        }
    }

    private void updateCurrentFragment() {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (chipLeft.isChecked()) {
            tx.replace(R.id.fragmentContainer,
                       LeftEarFragment.newInstance(USER_ID, currentHearingProfileId));
        } else {
            tx.replace(R.id.fragmentContainer,
                       RightEarFragment.newInstance(USER_ID, currentHearingProfileId));
        }
        tx.commit();
    }
    
    /**
     * Check if the current fragment has unsaved changes
     */
    private boolean hasUnsavedChanges() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        Log.d(TAG, "Checking for unsaved changes. Fragment: " + (fragment != null ? fragment.getClass().getSimpleName() : "null"));
        
        if (fragment instanceof LeftEarFragment) {
            boolean hasChanges = ((LeftEarFragment) fragment).hasUnsavedChanges();
            Log.d(TAG, "LeftEarFragment has unsaved changes: " + hasChanges);
            return hasChanges;
        } else if (fragment instanceof RightEarFragment) {
            boolean hasChanges = ((RightEarFragment) fragment).hasUnsavedChanges();
            Log.d(TAG, "RightEarFragment has unsaved changes: " + hasChanges);
            return hasChanges;
        }
        Log.d(TAG, "No fragment or unknown fragment type");
        return false;
    }
    
    /**
     * Show dialog to prompt user about unsaved changes
     */
    private void showUnsavedChangesDialog() {
        Log.d(TAG, "Showing unsaved changes dialog");
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_unsaved_changes, null);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create();
        
        // Make dialog appear as overlay with rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame);
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }
        
        dialogView.findViewById(R.id.btnDialogDiscard).setOnClickListener(v -> {
            // Discard changes in current fragment
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (fragment instanceof LeftEarFragment) {
                ((LeftEarFragment) fragment).discardChangesExternal();
            } else if (fragment instanceof RightEarFragment) {
                ((RightEarFragment) fragment).discardChangesExternal();
            }
            
            dialog.dismiss();
            
            // Navigate to pending destination
            if (pendingNavigationId == -2) {
                // Handle back navigation
                pendingNavigationId = -1;
                FrequencyActivity.super.onBackPressed();
            } else if (pendingNavigationId == -3) {
                // Handle profile switching
                pendingNavigationId = -1;
                showProfilePicker();
            } else if (pendingNavigationId == -4) {
                // Handle ear switching to left
                pendingNavigationId = -1;
                chipLeft.setChecked(true);
                chipRight.setChecked(false);
                updateCurrentFragment();
            } else if (pendingNavigationId == -5) {
                // Handle ear switching to right
                pendingNavigationId = -1;
                chipRight.setChecked(true);
                chipLeft.setChecked(false);
                updateCurrentFragment();
            } else if (pendingNavigationId != -1) {
                handleNavigation(pendingNavigationId);
                pendingNavigationId = -1;
            }
        });
        
        dialogView.findViewById(R.id.btnDialogSave).setOnClickListener(v -> {
            // Save changes in current fragment
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (fragment instanceof LeftEarFragment) {
                ((LeftEarFragment) fragment).saveChanges();
            } else if (fragment instanceof RightEarFragment) {
                ((RightEarFragment) fragment).saveChanges();
            }
            
            dialog.dismiss();
            
            // Navigate to pending destination after a short delay to allow save to complete
            if (pendingNavigationId == -2) {
                // Handle back navigation
                new android.os.Handler().postDelayed(() -> {
                    pendingNavigationId = -1;
                    FrequencyActivity.super.onBackPressed();
                }, 500);  // 500ms delay
            } else if (pendingNavigationId == -3) {
                // Handle profile switching
                new android.os.Handler().postDelayed(() -> {
                    pendingNavigationId = -1;
                    showProfilePicker();
                }, 500);  // 500ms delay
            } else if (pendingNavigationId == -4) {
                // Handle ear switching to left
                new android.os.Handler().postDelayed(() -> {
                    pendingNavigationId = -1;
                    chipLeft.setChecked(true);
                    chipRight.setChecked(false);
                    updateCurrentFragment();
                }, 500);  // 500ms delay
            } else if (pendingNavigationId == -5) {
                // Handle ear switching to right
                new android.os.Handler().postDelayed(() -> {
                    pendingNavigationId = -1;
                    chipRight.setChecked(true);
                    chipLeft.setChecked(false);
                    updateCurrentFragment();
                }, 500);  // 500ms delay
            } else if (pendingNavigationId != -1) {
                new android.os.Handler().postDelayed(() -> {
                    handleNavigation(pendingNavigationId);
                    pendingNavigationId = -1;
                }, 500);  // 500ms delay
            }
        });
        
        dialog.show();
        
        // Set dialog width to match screen width minus larger margins (narrower than cards)
        if (dialog.getWindow() != null) {
            int displayWidth = getResources().getDisplayMetrics().widthPixels;
            int marginInPixels = (int) (32 * getResources().getDisplayMetrics().density); // 16dp on each side = 32dp total
            dialog.getWindow().setLayout(displayWidth - marginInPixels, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    
    /**
     * Handle navigation to different destinations
     */
    private boolean handleNavigation(int id) {
        if (id == R.id.navigation_home) {
            startActivity(new Intent(this, HomeActivity.class));
            overridePendingTransition(0,0);
            finish();
            return true;
        } else if (id == R.id.navigation_frequencies) {
            // Already on Frequencies
            return true;
        } else if (id == R.id.navigation_settings) {
            startActivity(new Intent(this, ProfileActivity.class));
            overridePendingTransition(0,0);
            finish();
            return true;
        }
        return false;
    }
    
    @Override
    public void onBackPressed() {
        // Check for unsaved changes before going back
        if (hasUnsavedChanges()) {
            pendingNavigationId = -2;  // Special value for back navigation
            showUnsavedChangesDialog();
        } else {
            super.onBackPressed();
        }
    }
}
