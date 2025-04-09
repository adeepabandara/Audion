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
        buttonStartRightTest   = findViewById(R.id.buttonStartRightEarTest);




    buttonStartRightTest.setOnClickListener(v -> {
        Intent intent = new Intent(RightEarInstructionActivity.this, PureToneTestActivity.class);
        intent.putExtra("EAR", "RIGHT");
        int userId = getIntent().getIntExtra("USER_ID", -1);
        intent.putExtra("USER_ID", userId);
        startActivity(intent);
        finish();
    });

    }
}


