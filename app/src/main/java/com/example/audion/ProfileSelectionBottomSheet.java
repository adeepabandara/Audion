package com.example.audion;

import com.audion.psap.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audion.data.HearingProfile;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ProfileSelectionBottomSheet extends BottomSheetDialogFragment {

    public interface OnProfileSelectedListener {
        void onProfileSelected(HearingProfile profile);
    }

    private List<HearingProfile> profiles;
    private int selectedId;
    private OnProfileSelectedListener listener;

    public static ProfileSelectionBottomSheet newInstance(
        List<HearingProfile> profiles,
        int selectedId
    ) {
        ProfileSelectionBottomSheet f = new ProfileSelectionBottomSheet();
        f.profiles   = profiles;
        f.selectedId = selectedId;
        return f;
    }

    public void setOnProfileSelectedListener(OnProfileSelectedListener l) {
        listener = l;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply rounded corner theme
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
    }

    @Nullable @Override
    public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState
    ) {
        View v = inflater.inflate(
            R.layout.bottom_sheet_profile_selection,
            container,
            false
        );

        RecyclerView rv = v.findViewById(R.id.recyclerViewProfiles);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        // your existing ProfileAdapter takes (profiles, selectedId, clickCallback)
        ProfileAdapter adapter = new ProfileAdapter(
            profiles,
            selectedId,
            profile -> {
                if (listener != null) {
                    listener.onProfileSelected(profile);
                }
                dismiss();
            }
        );
        rv.setAdapter(adapter);

        MaterialButton btnAdd = v.findViewById(R.id.btnAddProfile);
        btnAdd.setOnClickListener(x -> {
            dismiss();
            // launch your new‑profile flow
            NewProfileBottomSheet np =
              NewProfileBottomSheet.newInstance(FrequencyActivity.USER_ID);
            np.show(getParentFragmentManager(), "NewProfile");
        });

        return v;
    }
}
