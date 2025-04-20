package com.example.audion.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audion.R;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RightEarFragment extends Fragment {

    private int userId;
    private int hearingProfileId;

    // Frequencies in Hz
    private static final int[] FREQUENCIES = {
        125, 250, 500, 1000, 2000, 3000, 4000, 8000
    };

    // Map frequency→row container so we can find our SeekBar & label
    private final Map<Integer, View> frequencyRowMap = new HashMap<>();
    private HearingTestResultDao dao;

    public static RightEarFragment newInstance(int userId, int hearingProfileId) {
        RightEarFragment fragment = new RightEarFragment();
        Bundle args = new Bundle();
        args.putInt("USER_ID", userId);
        args.putInt("HEARING_PROFILE_ID", hearingProfileId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup  container,
                             @Nullable Bundle     savedInstanceState) {
        return inflater.inflate(R.layout.fragment_right_ear, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1) Pull args
        if (getArguments() != null) {
            userId           = getArguments().getInt("USER_ID");
            hearingProfileId = getArguments().getInt("HEARING_PROFILE_ID", -1);
        }

        // 2) Wire up each row by its ID in fragment_right_ear.xml
        frequencyRowMap.put(125,  view.findViewById(R.id.row125HzRight));
        frequencyRowMap.put(250,  view.findViewById(R.id.row250HzRight));
        frequencyRowMap.put(500,  view.findViewById(R.id.row500HzRight));
        frequencyRowMap.put(1000, view.findViewById(R.id.row1000HzRight));
        frequencyRowMap.put(2000, view.findViewById(R.id.row2000HzRight));
        frequencyRowMap.put(3000, view.findViewById(R.id.row3000HzRight));
        frequencyRowMap.put(4000, view.findViewById(R.id.row4000HzRight));
        frequencyRowMap.put(8000, view.findViewById(R.id.row8000HzRight));

        // 3) Get our DAO
        dao = AppDatabase.getInstance(requireContext())
                        .hearingTestResultDao();

        // 4) Load & display right‑ear test results
        new Thread(() -> {
            List<HearingTestResult> allResults = dao.getResultsForUser(userId);
            // Keep only those for this profile *and* the right ear
            Map<Integer,Integer> freqToAmp = new HashMap<>();
            for (HearingTestResult r : allResults) {
                if (r.getHearingProfileId() == hearingProfileId &&
                    "right".equalsIgnoreCase(r.getEarSide())) {
                    freqToAmp.put(r.getFrequency(), r.getAmplitudeStep());
                }
            }

            // 5) Push into UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    for (int freq : FREQUENCIES) {
                        View row = frequencyRowMap.get(freq);
                        if (row == null) continue;

                        // Label
                        TextView label = row.findViewById(R.id.freqLabel);
                        label.setText(freq + " Hz");

                        // SeekBar
                        SeekBar bar = row.findViewById(R.id.frequencySeekBar);
                        int progress = freqToAmp.getOrDefault(freq, 50);
                        bar.setProgress(progress);
                    }
                });
            }
        }).start();
    }
}
