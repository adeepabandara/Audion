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
import androidx.fragment.app.FragmentActivity;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class NewProfileBottomSheet extends BottomSheetDialogFragment {

    private EditText editTextProfileName;
    private Spinner  spinnerIcon;
    private Button   buttonNext;
    private int      userId;

    // only resource IDs now
    private static final int[] ICON_RES_IDS = {
            R.drawable.ic_home,
            R.drawable.ic_school,
            R.drawable.ic_train,
            R.drawable.ic_palm_tree,
            R.drawable.ic_noodles,
            R.drawable.ic_glass_cocktail
    };

    public static NewProfileBottomSheet newInstance(int userId) {
        NewProfileBottomSheet frag = new NewProfileBottomSheet();
        Bundle args = new Bundle();
        args.putInt("USER_ID", userId);
        frag.setArguments(args);
        return frag;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_new_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,@Nullable  Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userId = getArguments() != null
               ? getArguments().getInt("USER_ID", 1)
               : 1;

        editTextProfileName = view.findViewById(R.id.editTextProfileName);
        spinnerIcon         = view.findViewById(R.id.spinnerIcon);
        buttonNext          = view.findViewById(R.id.buttonNext);

        // Build a list of Integers for the adapter:
        List<Integer> icons = new ArrayList<>();
        for (int res : ICON_RES_IDS) icons.add(res);

        // Adapter of Integers → our spinner_icon_item
        ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(
            requireContext(),
            R.layout.spinner_icon_item,
            icons
        ) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                return makeRow(position, convertView, parent);
            }
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                return makeRow(position, convertView, parent);
            }
            private View makeRow(int pos, View cv, ViewGroup parent) {
                View row = cv != null
                    ? cv
                    : LayoutInflater.from(getContext())
                        .inflate(R.layout.spinner_icon_item, parent, false);
                // bind icon
                row.<android.widget.ImageView>findViewById(R.id.imageIcon)
                   .setImageResource(getItem(pos));
                return row;
            }
        };
        spinnerIcon.setAdapter(adapter);

        buttonNext.setOnClickListener(v -> {
            String profileName = editTextProfileName.getText().toString().trim();
            if (profileName.isEmpty()) {
                Toast.makeText(requireContext(),
                  "Please enter a profile name",
                  Toast.LENGTH_SHORT).show();
                return;
            }
            // which icon was chosen?
            int pos   = spinnerIcon.getSelectedItemPosition();
            int resId = ICON_RES_IDS[pos];
            // derive a key from the resource name, then drop "ic_"
            String raw = requireContext()
                .getResources()
                .getResourceEntryName(resId);
            String iconKey = raw.startsWith("ic_")
                ? raw.substring(3)
                : raw;

            // Insert profile and start the test flow
            new Thread(() -> {
                HearingProfile p = new HearingProfile(profileName, iconKey);
                long id = AppDatabase
                           .getInstance(requireContext())
                           .hearingProfileDao()
                           .insert(p);

                FragmentActivity act = getActivity();
                if (act != null) {
                    act.runOnUiThread(() -> {
                        // Navigate directly to Right Ear Pure Tone Instruction
                        // This will start the flow: Right Ear PT -> Left Ear PT -> Right Ear Cal -> Left Ear Cal -> Results -> Home
                        Intent i = new Intent(act, RightEarInstructionActivity.class);
                        i.putExtra("USER_ID", userId);
                        i.putExtra("HEARING_PROFILE_ID", (int)id);
                        i.putExtra("FROM_NEW_PROFILE", true); // Flag to indicate this is a new profile creation
                        startActivity(i);
                        dismiss(); // Close the bottom sheet
                    });
                }
            }).start();
        });
    }
}
