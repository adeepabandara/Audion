package com.example.audion;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.example.audion.fragments.LeftEarFragment;
import com.example.audion.fragments.RightEarFragment;
import com.google.android.material.chip.Chip;
import java.util.ArrayList;
import java.util.List;

public class FrequencyActivity extends AppCompatActivity {
    public static final int USER_ID = 1;

    private static final String PREFS_NAME              = "com.example.audion.PREFERENCES";
    private static final String KEY_SELECTED_PROFILE_ID = "selectedProfileId";

    private androidx.appcompat.widget.AppCompatTextView tvSelectedProfile;
    private Chip chipLeft, chipRight;

    private List<HearingProfile> profileList = new ArrayList<>();
    private int currentHearingProfileId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_frequency);

        tvSelectedProfile = findViewById(R.id.tvSelectedProfile);
        chipLeft          = findViewById(R.id.chipLeft);
        chipRight         = findViewById(R.id.chipRight);

        // 1) load from DB off main thread
        loadHearingProfiles();

        // 2) open bottom sheet to pick profile
        tvSelectedProfile.setOnClickListener(v -> {
            reorderProfiles(profileList, currentHearingProfileId);
            ProfileSelectionBottomSheet sheet = ProfileSelectionBottomSheet
                .newInstance(new ArrayList<>(profileList), currentHearingProfileId);
            sheet.setOnProfileSelectedListener(profile -> {
                currentHearingProfileId = profile.getId();
                // persist selection
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_SELECTED_PROFILE_ID, currentHearingProfileId)
                    .apply();
                tvSelectedProfile.setText(profile.getName());
                updateCurrentFragment();
            });
            sheet.show(getSupportFragmentManager(), "ProfileSelection");
        });

        // 3) left/right chip toggles
        chipLeft.setOnClickListener(v -> {
            chipLeft.setChecked(true);
            chipRight.setChecked(false);
            updateCurrentFragment();
        });
        chipRight.setOnClickListener(v -> {
            chipRight.setChecked(true);
            chipLeft.setChecked(false);
            updateCurrentFragment();
        });

        chipLeft.setChecked(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // sync if HomeActivity changed it
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int saved = sp.getInt(KEY_SELECTED_PROFILE_ID, -1);
        if (saved != -1 && saved != currentHearingProfileId && !profileList.isEmpty()) {
            currentHearingProfileId = saved;
            // find name in memory
            for (HearingProfile hp : profileList) {
                if (hp.getId() == saved) {
                    tvSelectedProfile.setText(hp.getName());
                    updateCurrentFragment();
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

            if (profileList == null || profileList.isEmpty()) {
                long id = AppDatabase
                    .getInstance(this)
                    .hearingProfileDao()
                    .insert(new HearingProfile("Default Profile", ""));
                profileList = new ArrayList<>();
                profileList.add(
                  AppDatabase
                    .getInstance(this)
                    .hearingProfileDao()
                    .getHearingProfileById((int) id)
                );
            }

            // apply any previously saved selection
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int saved = sp.getInt(KEY_SELECTED_PROFILE_ID, -1);
            if (saved != -1) {
                currentHearingProfileId = saved;
            } else {
                currentHearingProfileId = profileList.get(0).getId();
            }

            HearingProfile sel = null;
            for (HearingProfile hp : profileList) {
                if (hp.getId() == currentHearingProfileId) {
                    sel = hp;
                    break;
                }
            }
            if (sel == null) {
                sel = profileList.get(0);
                currentHearingProfileId = sel.getId();
            }

            final String name = sel.getName();
            runOnUiThread(() -> {
                tvSelectedProfile.setText(name);
                updateCurrentFragment();
            });
        }).start();
    }

    private void reorderProfiles(List<HearingProfile> profiles, int selectedId) {
        int idx = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId() == selectedId) {
                idx = i;
                break;
            }
        }
        if (idx > 0) {
            HearingProfile hp = profiles.remove(idx);
            profiles.add(0, hp);
        }
    }

    private void updateCurrentFragment() {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (chipLeft.isChecked()) {
            tx.replace(
                R.id.fragmentContainer,
                LeftEarFragment.newInstance(USER_ID, currentHearingProfileId)
            );
        } else {
            tx.replace(
                R.id.fragmentContainer,
                RightEarFragment.newInstance(USER_ID, currentHearingProfileId)
            );
        }
        tx.commit();
    }
}
