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

        buttonStartLeftTest  = findViewById(R.id.buttonStartLeftEarTest);


    buttonStartLeftTest.setOnClickListener(v -> {
        Intent intent = new Intent(LeftEarInstructionActivity.this, PureToneTestActivity.class);
        intent.putExtra("EAR", "LEFT");
        int userId = getIntent().getIntExtra("USER_ID", -1);
        intent.putExtra("USER_ID", userId);
        startActivity(intent);
        finish();
    });

    }
}
