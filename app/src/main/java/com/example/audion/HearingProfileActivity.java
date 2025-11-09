package com.example.audion;

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
import com.example.audion.data.AudiometryResult;
import com.example.audion.data.AudiometryResultDao;
import com.example.audion.data.CalibrationDao;
import com.example.audion.data.CalibrationEntry;
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
                AudiometryResultDao audiometryDao = db.audiometryResultDao();
                CalibrationDao calibrationDao = db.calibrationDao();

                // Load audiometry data
                List<AudiometryResult> audiometryResults = audiometryDao.getAll();
                
                // Load calibration data
                List<CalibrationEntry> calibrationEntries = calibrationDao.getForUserProfile(userId, hearingProfileId);

                runOnUiThread(() -> {
                    if (audiometryResults.isEmpty() && calibrationEntries.isEmpty()) {
                        showNoDataState();
                    } else {
                        showDataState();
                        displayAudiogramData(audiometryResults);
                        displayCalibrationData(calibrationEntries);
                        updateLastTestDate(audiometryResults, calibrationEntries);
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

    private void displayAudiogramData(List<AudiometryResult> results) {
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
        for (AudiometryResult result : results) {
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

    private void displayCalibrationData(List<CalibrationEntry> entries) {
        if (entries.isEmpty()) {
            calibrationSummary.setText("No calibration data available");
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Calibration Levels:\n");
        
        for (CalibrationEntry entry : entries) {
            summary.append(String.format(Locale.getDefault(),
                "%s Ear: %d dB baseline\n", 
                entry.getEarSide(), entry.getBaselineStep()));
        }

        calibrationSummary.setText(summary.toString().trim());
    }

    private void updateLastTestDate(List<AudiometryResult> audiometryResults, 
                                   List<CalibrationEntry> calibrationEntries) {
        long latestTimestamp = 0;
        
        for (AudiometryResult result : audiometryResults) {
            if (result.getTestTimestamp() > latestTimestamp) {
                latestTimestamp = result.getTestTimestamp();
            }
        }
        
        for (CalibrationEntry entry : calibrationEntries) {
            // Note: CalibrationEntry might not have timestamp, using current approach
            // If timestamp is available, add similar check
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
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
        });

        recalibrateButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CalibrationInstructionActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            intent.putExtra("EAR", "RIGHT"); // Start with right ear
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
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