package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

public class GeneralInstructionActivity extends AppCompatActivity {

    private TextView textViewGeneral;
    private Button buttonProceed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_instruction);

        
        buttonProceed   = findViewById(R.id.buttonProceed);
        buttonProceed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Use GeneralInstructionActivity.this as the Context
                Intent intent = new Intent(GeneralInstructionActivity.this, LeftEarInstructionActivity.class);
                startActivity(intent);
            }
        });
    }
}
