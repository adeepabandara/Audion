package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import com.google.android.material.button.MaterialButton;

public class CalibrationInstructionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Figure out which ear we're on (default to RIGHT)
        String ear = getIntent().getStringExtra("EAR");
        if (ear == null) ear = "RIGHT";

        // Choose the correct instruction layout
        int layoutRes = ear.equalsIgnoreCase("LEFT")
            ? R.layout.activity_calibration_left_instruction
            : R.layout.activity_calibration_right_instruction;
        setContentView(layoutRes);

        // Wire up the button
        final String finalEar = ear;
        int btnId = ear.equalsIgnoreCase("LEFT")
            ? R.id.buttonStartLeftCalibration
            : R.id.buttonStartRightCalibration;
        MaterialButton btn = findViewById(btnId);
        btn.setOnClickListener(v -> {
            Intent i = new Intent(CalibrationInstructionActivity.this,
                                  CalibrationTestActivity.class);
            i.putExtra("EAR", finalEar);
            startActivity(i);
            finish();
        });
    }
}
