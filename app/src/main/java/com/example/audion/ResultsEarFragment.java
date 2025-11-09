package com.example.audion;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.audion.data.CalibrationProfileEntity;
import com.example.audion.data.HearingTestResult;
import com.example.audion.views.AudiogramView;
import com.example.audion.views.CalibrationSummaryView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResultsEarFragment extends Fragment {

    private static final String TAG = "ResultsEarFragment";
    private static final String ARG_EAR_SIDE = "ear_side";
    private static final String ARG_RESULTS = "results";
    private static final String ARG_CALIBRATION = "calibration";

    private String earSide;
    private List<HearingTestResult> results;
    private CalibrationProfileEntity calibration;

    private AudiogramView audiogramView;
    private CalibrationSummaryView calibrationSummaryView;
    private TextView noDataText, summaryText;

    public static ResultsEarFragment newInstance(String earSide, 
                                                List<HearingTestResult> results,
                                                CalibrationProfileEntity calibration) {
        ResultsEarFragment fragment = new ResultsEarFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EAR_SIDE, earSide);
        args.putSerializable(ARG_RESULTS, (Serializable) results);
        args.putSerializable(ARG_CALIBRATION, calibration);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            earSide = getArguments().getString(ARG_EAR_SIDE);
            results = (List<HearingTestResult>) getArguments().getSerializable(ARG_RESULTS);
            calibration = (CalibrationProfileEntity) getArguments().getSerializable(ARG_CALIBRATION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_results_ear, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        audiogramView = view.findViewById(R.id.audiogramView);
        calibrationSummaryView = view.findViewById(R.id.calibrationSummaryView);
        noDataText = view.findViewById(R.id.noDataText);
        summaryText = view.findViewById(R.id.summaryText);

        displayResults();
    }

    private void displayResults() {
        if (results == null || results.isEmpty()) {
            // No test data available
            audiogramView.setVisibility(View.GONE);
            calibrationSummaryView.setVisibility(View.GONE);
            summaryText.setVisibility(View.GONE);
            noDataText.setVisibility(View.VISIBLE);
            noDataText.setText("No test data available for " + earSide.toLowerCase() + " ear");
            return;
        }

        noDataText.setVisibility(View.GONE);

        // Display audiogram
        int earColor = "LEFT".equals(earSide) ? 
            Color.parseColor("#3498DB") : Color.parseColor("#E74C3C");
        audiogramView.setEarSide(earSide);
        audiogramView.setEarColor(earColor);
        audiogramView.setTestResults(results);

        // Display calibration summary
        if (calibration != null && calibration.getMclPerFrequencyJson() != null) {
            calibrationSummaryView.setVisibility(View.VISIBLE);
            calibrationSummaryView.setCalibrationData(calibration, earSide);
            
            // Generate summary text
            generateSummaryText();
        } else {
            calibrationSummaryView.setVisibility(View.GONE);
            summaryText.setText("Calibration data not available for this ear.");
        }
    }

    private void generateSummaryText() {
        if (calibration == null || calibration.getMclPerFrequencyJson() == null) {
            summaryText.setText("");
            return;
        }

        try {
            Gson gson = new Gson();
            Map<String, Float> mclData = gson.fromJson(
                calibration.getMclPerFrequencyJson(),
                new TypeToken<Map<String, Float>>(){}.getType()
            );

            if (mclData == null || mclData.isEmpty()) {
                summaryText.setText("");
                return;
            }

            // Calculate range
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;
            for (Float value : mclData.values()) {
                if (value < min) min = value;
                if (value > max) max = value;
            }

            String summary = String.format(Locale.getDefault(),
                "Your comfortable listening range is between %.0f–%.0f dB HL across key frequencies.",
                min, max);
            summaryText.setText(summary);

        } catch (Exception e) {
            Log.e(TAG, "Error generating summary text", e);
            summaryText.setText("");
        }
    }
}
