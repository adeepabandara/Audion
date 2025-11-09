package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingProfileDao;

public class WhatsYourNameActivity extends AppCompatActivity {

    private EditText etName;
    private Button btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whats_your_name);

        // Initialize views
        etName = findViewById(R.id.etName);
        btnContinue = findViewById(R.id.btnContinue);

        // Continue button click
        btnContinue.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save name to SharedPreferences (optional)
            getSharedPreferences("AudionPrefs", MODE_PRIVATE)
                .edit()
                .putString("user_name", name)
                .putBoolean("onboarding_completed", false) // Will be true after test
                .apply();

            // Create user profile in database in background thread
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                HearingProfileDao profileDao = db.hearingProfileDao();
                
                // Create a default hearing profile
                HearingProfile profile = new HearingProfile(name + "'s Profile", "default_icon");
                long profileId = profileDao.insert(profile);
                
                // Use simple USER_ID = 1 for single user app
                int userId = 1;
                
                // Navigate to Start Test screen with IDs
                runOnUiThread(() -> {
                    Intent intent = new Intent(WhatsYourNameActivity.this, StartTestActivity.class);
                    intent.putExtra("USER_ID", userId);
                    intent.putExtra("HEARING_PROFILE_ID", (int) profileId);
                    startActivity(intent);
                    finish();
                });
            }).start();
        });
    }
}
