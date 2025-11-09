package com.example.audion;

import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.EntryXComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AudiogramFragment extends Fragment {
    private static final String ARG_EAR = "earSide";
    private String earSide;

    public static AudiogramFragment newInstance(String ear) {
        AudiogramFragment f = new AudiogramFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EAR, ear);
        f.setArguments(args);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_audiogram, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        earSide = requireArguments().getString(ARG_EAR, "LEFT");
        LineChart chart = view.findViewById(R.id.chart);
        setupChart(chart);
    }

    private void setupChart(LineChart chart) {
        new Thread(() -> {
            // 1) Load hearing test points
            List<HearingTestResult> pts = AppDatabase
                    .getInstance(requireContext())
                    .hearingTestResultDao()
                    .getResultsForEar(earSide, 1);

            // 2) Build entries using clinical dB HL values
            List<Entry> entries = new ArrayList<>();
            for (HearingTestResult r : pts) {
                // Use frequency directly (chart will handle display)
                // Use thresholdDbHL for clinical audiogram (NOT amplitudeStep)
                entries.add(new Entry(r.getFrequency(), r.getThresholdDbHL()));
            }

            // 3) Sort by frequency
            Collections.sort(entries, new EntryXComparator());

            requireActivity().runOnUiThread(() -> {
                if (entries.isEmpty()) {
                    chart.clear();
                    chart.invalidate();
                    return;
                }

                // 4) Create and style DataSet
                LineDataSet ds = new LineDataSet(entries, earSide + " Audiogram");
                ds.setMode(LineDataSet.Mode.LINEAR);  // Standard audiogram connection (not stepped)
                ds.setLineWidth(2f);
                ds.setDrawCircles(true);
                ds.setCircleRadius(4f);

                // ← your custom colors:
                ds.setColor(Color.parseColor("#0F766E"));        // line color
                ds.setCircleColor(Color.parseColor("#0F766E"));  // circle color

                LineData ld = new LineData(ds);
                chart.setData(ld);

                // ← disable default description
                chart.getDescription().setEnabled(false);

                // ← chart background colors
                chart.setBackgroundColor(Color.WHITE);
                chart.setDrawGridBackground(true);
                chart.setGridBackgroundColor(Color.WHITE);

                // 5) Configure axes for clinical audiogram
                float minX = entries.get(0).getX();
                float maxX = entries.get(entries.size() - 1).getX();

                XAxis x = chart.getXAxis();
                x.setPosition(XAxis.XAxisPosition.BOTTOM);
                x.setAxisMinimum(minX);
                x.setAxisMaximum(maxX);
                x.setGranularity(1f);
                x.setGranularityEnabled(true);

                // Clinical audiogram Y-axis: inverted (0 dB at top = better hearing)
                YAxis left = chart.getAxisLeft();
                left.setAxisMinimum(-10f);    // Allow slight negative values
                left.setAxisMaximum(120f);     // Maximum hearing loss
                left.setInverted(true);        // CRITICAL: 0 at top, 120 at bottom
                left.setGranularity(10f);      // 10 dB increments
                chart.getAxisRight().setEnabled(false);

                // 6) Refresh
                chart.notifyDataSetChanged();
                chart.invalidate();
            });
        }).start();
    }

}
