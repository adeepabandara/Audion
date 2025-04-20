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

public class LeftEarFragment extends Fragment {

    private int userId;
    private int hearingProfileId;
    private final int[] FREQUENCIES = {125, 250, 500, 1000, 2000, 3000, 4000, 8000};
    private Map<Integer, View> frequencyRowMap = new HashMap<>();
    private HearingTestResultDao dao;

    public static LeftEarFragment newInstance(int userId, int hearingProfileId) {
        LeftEarFragment fragment = new LeftEarFragment();
        Bundle args = new Bundle();
        args.putInt("USER_ID", userId);
        args.putInt("HEARING_PROFILE_ID", hearingProfileId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_left_ear, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);

        if(getArguments() != null){
            userId = getArguments().getInt("USER_ID");
            hearingProfileId = getArguments().getInt("HEARING_PROFILE_ID", -1);
        }

        frequencyRowMap.put(125, view.findViewById(R.id.row125Hz));
        frequencyRowMap.put(250, view.findViewById(R.id.row250Hz));
        frequencyRowMap.put(500, view.findViewById(R.id.row500Hz));
        frequencyRowMap.put(1000, view.findViewById(R.id.row1000Hz));
        frequencyRowMap.put(2000, view.findViewById(R.id.row2000Hz));
        frequencyRowMap.put(3000, view.findViewById(R.id.row3000Hz));
        frequencyRowMap.put(4000, view.findViewById(R.id.row4000Hz));
        frequencyRowMap.put(8000, view.findViewById(R.id.row8000Hz));

        dao = AppDatabase.getInstance(requireContext()).hearingTestResultDao();

        new Thread(() -> {
            List<HearingTestResult> results = dao.getResultsForUser(userId); // Use userId, not USER_ID
            // Filter for left ear and matching hearing profile ID.
            Map<Integer, Integer> freqToAmplitude = new HashMap<>();
            for(HearingTestResult result: results){
                if(result.getHearingProfileId() == hearingProfileId &&
                        "left".equalsIgnoreCase(result.getEarSide())){
                    freqToAmplitude.put(result.getFrequency(), result.getAmplitudeStep());
                }
            }
            if(getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    for (int freq: FREQUENCIES){
                        View row = frequencyRowMap.get(freq);
                        if(row != null){
                            TextView label = row.findViewById(R.id.freqLabel);
                            label.setText(freq + " Hz");
                            SeekBar seekBar = row.findViewById(R.id.frequencySeekBar);
                            if(freqToAmplitude.containsKey(freq)){
                                seekBar.setProgress(freqToAmplitude.get(freq));
                            } else {
                                seekBar.setProgress(50); // default value
                            }
                        }
                    }
                });
            }
        }).start();
    }
}
