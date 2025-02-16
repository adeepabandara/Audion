package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class RightEarInstructionActivity extends AppCompatActivity {

    private TextView textViewInstructions;
    private Button buttonStartRightTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_right_ear_instruction);

        textViewInstructions   = findViewById(R.id.textViewRightEarInstructions);
        buttonStartRightTest   = findViewById(R.id.buttonStartRightEarTest);

        // Example instructions for the right ear
        textViewInstructions.setText(
            "RIGHT EAR INSTRUCTIONS:\n\n" +
            "1. Make sure the right earpiece is on your right ear.\n" +
            "2. Press Start when ready to begin the test."
        );


        buttonStartRightTest.setOnClickListener(v -> {
            // Start the PureToneTestActivity with EAR = "RIGHT"
            Intent intent = new Intent(RightEarInstructionActivity.this, PureToneTestActivity.class);
            intent.putExtra("EAR", "RIGHT");
            startActivity(intent);
            finish();  // optional
        });
    }
}
