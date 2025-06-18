package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.CalibrationEntry;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class GraphActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private String[] ears = {"LEFT","RIGHT"};

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audiogram);
        bottomNav            = findViewById(R.id.bottomNavigationView);

        viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new AudiogramPagerAdapter(this));

        com.google.android.material.tabs.TabLayout tabs = findViewById(R.id.tabLayout);
        new com.google.android.material.tabs.TabLayoutMediator(tabs, viewPager,
            (tab, position) -> tab.setText(ears[position]))
            .attach();



        bottomNav.setSelectedItemId(R.id.navigation_settings);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_settings) {
                // Already on Home
                return true;
            } else if (id == R.id.navigation_frequencies) {
                startActivity(new Intent(this, FrequencyActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.navigation_music) {
                startActivity(new Intent(this, MusicPlayerActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.navigation_home) {
                startActivity(new Intent(this, HomeActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private class AudiogramPagerAdapter extends FragmentStateAdapter {
        AudiogramPagerAdapter(@NonNull FragmentActivity fa) { super(fa); }
        @NonNull @Override
        public Fragment createFragment(int pos) {
            return AudiogramFragment.newInstance(ears[pos]);
        }
        @Override public int getItemCount() { return ears.length; }
    }


}
