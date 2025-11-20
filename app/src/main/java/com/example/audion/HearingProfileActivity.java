package com.example.audion;

import com.audion.psap.R;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.example.audion.data.CalibrationProfileDao;
import com.example.audion.data.CalibrationProfileEntity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HearingProfileActivity extends AppCompatActivity {

    private static final String TAG = "HearingProfileActivity";
    
    private int userId;
    private int hearingProfileId;
    
    // UI Elements
    private Toolbar toolbar;
    private LineChart audiogramChart;
    private TextView leftEarSummary;
    private TextView rightEarSummary;
    private TextView calibrationSummary;
    private TextView lastTestDate;
    private MaterialButton retestHearingButton;
    private MaterialButton recalibrateButton;
    private MaterialCardView audiogramCard;
    private MaterialCardView calibrationCard;
    private TextView noDataMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hearing_profile);

        // Get intent data
        userId = getIntent().getIntExtra("USER_ID", 1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", 1);

        initializeViews();
        setupToolbar();
        loadHearingProfileData();
        setupButtons();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        audiogramChart = findViewById(R.id.audiogramChart);
        leftEarSummary = findViewById(R.id.leftEarSummary);
        rightEarSummary = findViewById(R.id.rightEarSummary);
        calibrationSummary = findViewById(R.id.calibrationSummary);
        lastTestDate = findViewById(R.id.lastTestDate);
        retestHearingButton = findViewById(R.id.retestHearingButton);
        recalibrateButton = findViewById(R.id.recalibrateButton);
        audiogramCard = findViewById(R.id.audiogramCard);
        calibrationCard = findViewById(R.id.calibrationCard);
        noDataMessage = findViewById(R.id.noDataMessage);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Your Hearing Profile");
        }
    }

    private void loadHearingProfileData() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                HearingTestResultDao hearingTestDao = db.hearingTestResultDao();
                CalibrationProfileDao calibrationDao = db.calibrationProfileDao();

                // Load hearing test data (pure tone audiometry)
                List<HearingTestResult> hearingTestResults = hearingTestDao.getResultsForUserAndProfile(userId, hearingProfileId);
                
                // Load calibration data
                List<CalibrationProfileEntity> calibrationProfiles = calibrationDao.getProfilesForUser(userId, hearingProfileId);

                Log.d(TAG, "Loaded " + hearingTestResults.size() + " hearing test results for profile " + hearingProfileId);
                Log.d(TAG, "Loaded " + calibrationProfiles.size() + " calibration profiles for profile " + hearingProfileId);

                runOnUiThread(() -> {
                    if (hearingTestResults.isEmpty() && calibrationProfiles.isEmpty()) {
                        showNoDataState();
                    } else {
                        showDataState();
                        displayAudiogramData(hearingTestResults);
                        displayCalibrationData(calibrationProfiles);
                        updateLastTestDate(hearingTestResults, calibrationProfiles);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading hearing profile data", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading hearing profile", Toast.LENGTH_SHORT).show();
                    showNoDataState();
                });
            }
        }).start();
    }

    private void showNoDataState() {
        noDataMessage.setVisibility(View.VISIBLE);
        audiogramCard.setVisibility(View.GONE);
        calibrationCard.setVisibility(View.GONE);
        
        retestHearingButton.setText("START HEARING TEST");
        recalibrateButton.setVisibility(View.GONE);
    }

    private void showDataState() {
        noDataMessage.setVisibility(View.GONE);
        audiogramCard.setVisibility(View.VISIBLE);
        calibrationCard.setVisibility(View.VISIBLE);
        
        retestHearingButton.setText("RETEST HEARING");
        recalibrateButton.setVisibility(View.VISIBLE);
    }

    private void displayAudiogramData(List<HearingTestResult> results) {
        if (results.isEmpty()) {
            leftEarSummary.setText("No audiogram data available");
            rightEarSummary.setText("No audiogram data available");
            return;
        }

        setupAudiogramChart();
        
        ArrayList<Entry> leftEarEntries = new ArrayList<>();
        ArrayList<Entry> rightEarEntries = new ArrayList<>();
        
        // Standard audiometric frequencies
        int[] frequencies = {250, 500, 1000, 2000, 3000, 4000, 8000};
        
        // Group results by ear and frequency
        for (HearingTestResult result : results) {
            int freqIndex = getFrequencyIndex(result.getFrequency(), frequencies);
            if (freqIndex >= 0) {
                Entry entry = new Entry(freqIndex, result.getThresholdDbHL());
                if ("LEFT".equals(result.getEarSide())) {
                    leftEarEntries.add(entry);
                } else if ("RIGHT".equals(result.getEarSide())) {
                    rightEarEntries.add(entry);
                }
            }
        }

        // Create datasets for chart
        LineDataSet leftEarDataSet = new LineDataSet(leftEarEntries, "Left Ear");
        leftEarDataSet.setColor(Color.BLUE);
        leftEarDataSet.setCircleColor(Color.BLUE);
        leftEarDataSet.setLineWidth(3f);
        leftEarDataSet.setCircleRadius(6f);
        leftEarDataSet.setValueTextSize(10f);

        LineDataSet rightEarDataSet = new LineDataSet(rightEarEntries, "Right Ear");
        rightEarDataSet.setColor(Color.RED);
        rightEarDataSet.setCircleColor(Color.RED);
        rightEarDataSet.setLineWidth(3f);
        rightEarDataSet.setCircleRadius(6f);
        rightEarDataSet.setValueTextSize(10f);

        LineData lineData = new LineData(leftEarDataSet, rightEarDataSet);
        audiogramChart.setData(lineData);
        audiogramChart.invalidate();

        // Update ear summaries
        updateEarSummaries(leftEarEntries, rightEarEntries);
    }

    private void setupAudiogramChart() {
        audiogramChart.getDescription().setEnabled(false);
        audiogramChart.setTouchEnabled(true);
        audiogramChart.setDragEnabled(true);
        audiogramChart.setScaleEnabled(true);
        audiogramChart.setPinchZoom(true);

        // Configure X axis (frequencies)
        XAxis xAxis = audiogramChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int[] frequencies = {250, 500, 1000, 2000, 3000, 4000, 8000};
                int index = (int) value;
                if (index >= 0 && index < frequencies.length) {
                    return frequencies[index] + "Hz";
                }
                return "";
            }
        });

        // Configure Y axis (hearing level in dB HL)
        YAxis leftAxis = audiogramChart.getAxisLeft();
        leftAxis.setInverted(true); // Invert Y-axis for audiogram convention
        leftAxis.setAxisMinimum(-10f);
        leftAxis.setAxisMaximum(120f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (int) value + " dB HL";
            }
        });

        audiogramChart.getAxisRight().setEnabled(false);
    }

    private int getFrequencyIndex(int frequency, int[] frequencies) {
        for (int i = 0; i < frequencies.length; i++) {
            if (frequencies[i] == frequency) {
                return i;
            }
        }
        return -1;
    }

    private void updateEarSummaries(ArrayList<Entry> leftEntries, ArrayList<Entry> rightEntries) {
        if (!leftEntries.isEmpty()) {
            float avgLeft = 0;
            for (Entry entry : leftEntries) {
                avgLeft += entry.getY();
            }
            avgLeft /= leftEntries.size();
            String leftStatus = getHearingStatus(avgLeft);
            leftEarSummary.setText(String.format(Locale.getDefault(), 
                "Left Ear: %.0f dB HL avg (%s)", avgLeft, leftStatus));
        } else {
            leftEarSummary.setText("Left Ear: No data");
        }

        if (!rightEntries.isEmpty()) {
            float avgRight = 0;
            for (Entry entry : rightEntries) {
                avgRight += entry.getY();
            }
            avgRight /= rightEntries.size();
            String rightStatus = getHearingStatus(avgRight);
            rightEarSummary.setText(String.format(Locale.getDefault(), 
                "Right Ear: %.0f dB HL avg (%s)", avgRight, rightStatus));
        } else {
            rightEarSummary.setText("Right Ear: No data");
        }
    }

    private String getHearingStatus(float avgThreshold) {
        if (avgThreshold <= 20) return "Normal";
        else if (avgThreshold <= 40) return "Mild Loss";
        else if (avgThreshold <= 70) return "Moderate Loss";
        else if (avgThreshold <= 90) return "Severe Loss";
        else return "Profound Loss";
    }

    private void displayCalibrationData(List<CalibrationProfileEntity> profiles) {
        if (profiles.isEmpty()) {
            calibrationSummary.setText("No calibration data available");
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Calibration Levels:\n");
        
        for (CalibrationProfileEntity profile : profiles) {
            summary.append(String.format(Locale.getDefault(),
                "%s Ear: MCL=%.1f dB, UCL=%.1f dB\n", 
                profile.getEarSide(), profile.getMclDbSpl(), profile.getUclDbSpl()));
        }

        calibrationSummary.setText(summary.toString().trim());
    }

    private void updateLastTestDate(List<HearingTestResult> hearingTestResults, 
                                   List<CalibrationProfileEntity> calibrationProfiles) {
        long latestTimestamp = 0;
        
        for (HearingTestResult result : hearingTestResults) {
            if (result.getTestTimestamp() > latestTimestamp) {
                latestTimestamp = result.getTestTimestamp();
            }
        }
        
        for (CalibrationProfileEntity profile : calibrationProfiles) {
            if (profile.getLastUpdated() > latestTimestamp) {
                latestTimestamp = profile.getLastUpdated();
            }
        }

        if (latestTimestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            String formattedDate = sdf.format(new Date(latestTimestamp));
            lastTestDate.setText("Last updated: " + formattedDate);
        } else {
            lastTestDate.setText("Test date unavailable");
        }
    }

    private void setupButtons() {
        retestHearingButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, GeneralInstructionActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            startActivity(intent);
            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
        });

        recalibrateButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CalibrationInstructionActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            intent.putExtra("EAR", "RIGHT"); // Start with right ear
            startActivity(intent);
            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning from tests
        loadHearingProfileData();
    }
}