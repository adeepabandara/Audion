package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class InstructionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instruction);

        TextView instructions = findViewById(R.id.textViewInstructions);
        Button startButton = findViewById(R.id.buttonStartTest);

        String nextEar = getIntent().getStringExtra("NEXT_EAR");
        if (nextEar == null) {
            // Initial instructions for left ear
            instructions.setText("Wear headphones. First, test LEFT ear. Click START when ready.");
            startButton.setText("Start Left Ear Test");
        } else if ("RIGHT".equals(nextEar)) {
            instructions.setText("Now, test RIGHT ear. Click START when ready.");
            startButton.setText("Start Right Ear Test");
        }

        startButton.setOnClickListener(v -> {
            String ear = (nextEar == null) ? "LEFT" : "RIGHT";
            Intent intent = new Intent(this, PureToneTestActivity.class);
            intent.putExtra("EAR", ear);
            startActivity(intent);
            finish();
        });
    }
}