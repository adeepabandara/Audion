package com.example.audion;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.CalibrationEntry;
import com.example.audion.data.CalibrationDao;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.example.audion.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;

public class AudiogramActivity extends AppCompatActivity {
    private LineChart chart;
    private int userId, profileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audiogram);

        chart = findViewById(R.id.lineChartAudiogram);

        // 1) Pull extras
        Intent intent = getIntent();
        userId    = intent.getIntExtra("USER_ID", -1);
        profileId = intent.getIntExtra("HEARING_PROFILE_ID", -1);

        // 2) Load everything off the UI thread
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            CalibrationDao    calDao   = db.calibrationDao();
            HearingTestResultDao testDao = db.hearingTestResultDao();

            // 3) Fetch from the database
            List<CalibrationEntry>   cals    = calDao.getForUserProfile(userId, profileId);
            List<HearingTestResult> results = testDao.getResultsForUserAndProfile(userId, profileId);

            // 4) Split test‐results by ear
            List<Entry> entriesR = new ArrayList<>();
            List<Entry> entriesL = new ArrayList<>();
            for (HearingTestResult r : results) {
                float x = r.getFrequency();
                float y = r.getAmplitudeStep();
                if ("RIGHT".equalsIgnoreCase(r.getEarSide())) {
                    entriesR.add(new Entry(x, y));
                } else {
                    entriesL.add(new Entry(x, y));
                }
            }

            // 5) Pull out the two baselines
            float baselineR = 0, baselineL = 0;
            for (CalibrationEntry c : cals) {
                if ("RIGHT".equalsIgnoreCase(c.getEarSide())) {
                    baselineR = c.getBaselineStep();
                } else {
                    baselineL = c.getBaselineStep();
                }
            }
            // Plot each baseline at 1000Hz (you can choose any freq or plot horizontal lines)
            List<Entry> baseR = new ArrayList<>(); baseR.add(new Entry(1000f, baselineR));
            List<Entry> baseL = new ArrayList<>(); baseL.add(new Entry(1000f, baselineL));

            // 6) Now back to the UI thread to build the chart
            runOnUiThread(() -> {
                chart.clear();

                // Create four data‐sets
                LineDataSet setR     = new LineDataSet(entriesR, "Right Ear Test");
                LineDataSet setL     = new LineDataSet(entriesL, "Left Ear Test");
                LineDataSet baseRSet = new LineDataSet(baseR,    "Baseline Right Ear");
                LineDataSet baseLSet = new LineDataSet(baseL,    "Baseline Left Ear");

                // Style each
                setR.setLineWidth(2f);     setR.setCircleRadius(4f);
                setL.setLineWidth(2f);     setL.setCircleRadius(4f);
                baseRSet.setLineWidth(1f); baseRSet.setCircleRadius(6f);
                baseLSet.setLineWidth(1f); baseLSet.setCircleRadius(6f);

                // Put them into the chart
                LineData data = new LineData(setR, setL, baseRSet, baseLSet);
                chart.setData(data);

                // X‐axis in Hz/kHz
                chart.getXAxis().setGranularity(1f);
                chart.getXAxis().setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getAxisLabel(float value, AxisBase axis) {
                        int freq = Math.round(value);
                        return freq >= 1000 ? (freq/1000) + "k" : String.valueOf(freq);
                    }
                });

                // Left Y axis only
                chart.getAxisRight().setEnabled(false);

                // Legend & description
                Legend legend = chart.getLegend();
                legend.setWordWrapEnabled(true);

                Description desc = new Description();
                desc.setText("Audiogram (dB HL vs kHz)");
                chart.setDescription(desc);

                // Finally…
                chart.invalidate();
            });
        }).start();
    }
}
