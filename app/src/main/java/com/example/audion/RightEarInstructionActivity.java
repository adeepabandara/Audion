package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.example.audion.R;

public class RightEarInstructionActivity extends AppCompatActivity {

    private Button buttonStartRightTest;
    private int userId;
    private int hearingProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_right_ear_instruction);
        buttonStartRightTest = findViewById(R.id.buttonStartRightEarTest);
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);

        buttonStartRightTest.setOnClickListener(v -> {
            Intent intent = new Intent(RightEarInstructionActivity.this, PureToneTestActivity.class);
            intent.putExtra("EAR", "RIGHT");
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            startActivity(intent);
            finish();
        });
    }
}
