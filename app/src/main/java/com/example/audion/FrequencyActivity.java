package com.example.audion;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.example.audion.fragments.LeftEarFragment;
import com.example.audion.fragments.RightEarFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;

import android.view.Window;
import android.widget.ImageView;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrequencyActivity extends AppCompatActivity {
    public static final int USER_ID = 1;

    private static final String PREFS_NAME              = "com.example.audion.PREFERENCES";
    private static final String KEY_SELECTED_PROFILE_ID = "selectedProfileId";

    private ImageView ivProfileIcon;
    private AppCompatTextView tvSelectedProfile;
    private Chip chipLeft, chipRight;
    private BottomNavigationView bottomNav;

    private List<HearingProfile> profileList = new ArrayList<>();
    private int currentHearingProfileId = -1;

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

        bottomNav.setSelectedItemId(R.id.navigation_frequencies);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_home) {
                startActivity(new Intent(this, HomeActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.navigation_settings) {
                startActivity(new Intent(this, MusicPlayerActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return true;
        });

        tvSelectedProfile.setOnClickListener(v -> {
            reorderProfiles(profileList, currentHearingProfileId);
            ProfileSelectionBottomSheet sheet =
                ProfileSelectionBottomSheet.newInstance(
                    new ArrayList<>(profileList),
                    currentHearingProfileId
                );
            sheet.setOnProfileSelectedListener(this::applyProfileSelection);
            sheet.show(getSupportFragmentManager(), "ProfileSelection");
        });

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

        loadHearingProfiles();
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
}
