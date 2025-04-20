package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.example.audion.R;

public class LeftEarInstructionActivity extends AppCompatActivity {

    private Button buttonStartLeftTest;
    private int userId;
    private int hearingProfileId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_left_ear_instruction);
        
        buttonStartLeftTest = findViewById(R.id.buttonStartLeftEarTest);
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        
        buttonStartLeftTest.setOnClickListener(v -> {
            Intent intent = new Intent(LeftEarInstructionActivity.this, PureToneTestActivity.class);
            intent.putExtra("EAR", "LEFT");
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            startActivity(intent);
            finish();
        });
    }
}
