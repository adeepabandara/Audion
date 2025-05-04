package com.example.audion.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audion.AudioStreamingService;
import com.example.audion.R;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;

import java.util.HashMap;
import java.util.Map;

public class RightEarFragment extends Fragment {
    private static final int[] FREQUENCIES = {
      125,250,500,1000,2000,3000,4000,8000
    };

    private int userId, profileId;
    private boolean saveShown=false;
    private HearingTestResultDao dao;
    private Button btnSave;
    private final Map<Integer,View> rowMap=new HashMap<>();

    public static RightEarFragment newInstance(int u,int p){
        RightEarFragment f=new RightEarFragment();
        Bundle b=new Bundle();
        b.putInt("USER_ID",u);
        b.putInt("PROFILE_ID",p);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(
      @NonNull LayoutInflater inf,
      @Nullable ViewGroup  ct,
      @Nullable Bundle     bs
    ){
        return inf.inflate(R.layout.fragment_right_ear,ct,false);
    }

    @Override
    public void onViewCreated(
      @NonNull View v,
      @Nullable Bundle bs
    ){
        super.onViewCreated(v,bs);
        if(getArguments()!=null){
            userId=v.getContext().getSharedPreferences(
              "com.example.audion.PREFERENCES",0
            ).getInt("selectedProfileId",1);
            profileId=getArguments().getInt("PROFILE_ID");
        }
        dao=AppDatabase.getInstance(requireContext()).hearingTestResultDao();
        btnSave=v.findViewById(R.id.btnSaveRightEar);
        btnSave.setOnClickListener(x->onSave());
        for(int f:FREQUENCIES){
            int resId = getResources().getIdentifier(
              "row"+f+"HzRight","id",requireContext().getPackageName()
            );
            View row=v.findViewById(resId);
            rowMap.put(f,row);
        }
        new Thread(() -> {
            Map<Integer,Integer> map=new HashMap<>();
            for(HearingTestResult r:dao.getResultsForUserAndProfile(userId,profileId)){
                if("right".equalsIgnoreCase(r.getEarSide())){
                    map.put(r.getFrequency(),r.getAmplitudeStep());
                }
            }
            requireActivity().runOnUiThread(() -> {
                for(int freq:FREQUENCIES){
                    View row=rowMap.get(freq);
                    TextView tv=row.findViewById(R.id.freqLabel);
                    tv.setText(freq+" Hz");
                    SeekBar sb=row.findViewById(R.id.frequencySeekBar);
                    int p=map.getOrDefault(freq,50);
                    sb.setProgress(p);
                    attachListener(sb,freq);
                }
            });
        }).start();
    }

    private void attachListener(SeekBar sb,int freq){
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(
              SeekBar s,int prog,boolean u
            ){
                if(!saveShown){
                    btnSave.setVisibility(View.VISIBLE);
                    saveShown=true;
                }
                Intent i=new Intent(requireContext(),AudioStreamingService.class);
                i.setAction(AudioStreamingService.ACTION_UPDATE_GAIN);
                i.putExtra(AudioStreamingService.EXTRA_EAR,"right");
                i.putExtra(AudioStreamingService.EXTRA_FREQ,freq);
                i.putExtra(AudioStreamingService.EXTRA_AMPL,prog);
                requireContext().startService(i);
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
    }

    private void onSave(){
        new Thread(() -> {
            for(int freq:FREQUENCIES){
                View row=rowMap.get(freq);
                int prog=((SeekBar)row.findViewById(
                  R.id.frequencySeekBar)).getProgress();
                HearingTestResult existing =
                  dao.findUserEarFrequency(userId,"right",freq);
                if(existing!=null){
                    existing.setAmplitudeStep(prog);
                    dao.update(existing);
                } else {
                    dao.insert(new HearingTestResult(
                      userId,"right",freq,prog,profileId
                    ));
                }
            }
            requireActivity().runOnUiThread(() -> {
                btnSave.setVisibility(View.GONE);
                saveShown=false;
            });
        }).start();
    }
}
