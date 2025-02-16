package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class LeftEarInstructionActivity extends AppCompatActivity {

    // private ImageView imageViewEar;
    private TextView textViewInstructions;
    private Button buttonStartLeftTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_left_ear_instruction);

        // imageViewEar         = findViewById(R.id.imageViewLeftEar);
        textViewInstructions = findViewById(R.id.textViewLeftEarInstructions);
        buttonStartLeftTest  = findViewById(R.id.buttonStartLeftEarTest);

        // Example instructions for the left ear
        textViewInstructions.setText(
            "LEFT EAR INSTRUCTIONS:\n\n" +
            "1. Make sure the left earpiece is on your left ear.\n" +
            "2. Press Start when ready to begin the test."
        );

        // If you have a left-ear image in drawable
        // imageViewEar.setImageResource(R.drawable.left_ear);

        buttonStartLeftTest.setOnClickListener(v -> {
            // Start the PureToneTestActivity with EAR = "LEFT"
            Intent intent = new Intent(LeftEarInstructionActivity.this, PureToneTestActivity.class);
            intent.putExtra("EAR", "LEFT");
            startActivity(intent);
            finish();  // optional
        });
    }
}
