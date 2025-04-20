package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class NewProfileBottomSheet extends BottomSheetDialogFragment {

    private EditText editTextProfileName;
    private Spinner spinnerIcon;
    private Button buttonNext;
    private int userId;

    // Use this factory method to create a new instance with the USER_ID passed in.
    public static NewProfileBottomSheet newInstance(int userId) {
        NewProfileBottomSheet fragment = new NewProfileBottomSheet();
        Bundle args = new Bundle();
        args.putInt("USER_ID", userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_new_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (getArguments() != null) {
            userId = getArguments().getInt("USER_ID", 1); // default to 1 if not set
        } else {
            userId = 1;
        }
        
        editTextProfileName = view.findViewById(R.id.editTextProfileName);
        spinnerIcon = view.findViewById(R.id.spinnerIcon);
        buttonNext = view.findViewById(R.id.buttonNext);

        // Setup spinner with predefined icon list.
        String[] iconList = new String[] {"Icon1", "Icon2", "Icon3", "Icon4"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), 
            android.R.layout.simple_spinner_item, iconList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerIcon.setAdapter(adapter);

        buttonNext.setOnClickListener(v -> {
            String profileName = editTextProfileName.getText().toString().trim();
            if (profileName.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a profile name", Toast.LENGTH_SHORT).show();
                return;
            }
            String icon = spinnerIcon.getSelectedItem().toString();

            // Insert new profile in a background thread.
            new Thread(() -> {
                HearingProfile newProfile = new HearingProfile(profileName, icon);
                long newId = AppDatabase.getInstance(getContext()).hearingProfileDao().insert(newProfile);
                // Navigate to GeneralInstructionActivity on the UI thread.
                getActivity().runOnUiThread(() -> {
                    Intent intent = new Intent(getActivity(), GeneralInstructionActivity.class);
                    intent.putExtra("USER_ID", userId);
                    intent.putExtra("HEARING_PROFILE_ID", (int) newId);
                    startActivity(intent);
                    getActivity().finish();
                });
            }).start();
        });
    }
}
