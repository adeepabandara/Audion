package com.example.audion;

import com.audion.psap.R;

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
        
        // Disable continue button initially
        btnContinue.setEnabled(false);
        btnContinue.setAlpha(0.5f);
        
        // Enable/disable continue button based on name
        etName.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateContinueButton();
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Continue button click
        btnContinue.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save name to SharedPreferences (consent is implicit by clicking "Get Started" on carousel)
            getSharedPreferences("AudionPrefs", MODE_PRIVATE)
                .edit()
                .putString("user_name", name)
                .putBoolean("onboarding_completed", false) // Will be true after test
                .putBoolean("data_consent_granted", true)
                .putLong("data_consent_timestamp", System.currentTimeMillis())
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
                    overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                    finish();
                });
            }).start();
        });
    }
    
    private void updateContinueButton() {
        boolean nameValid = !TextUtils.isEmpty(etName.getText().toString().trim());
        
        btnContinue.setEnabled(nameValid);
        btnContinue.setAlpha(nameValid ? 1.0f : 0.5f);
    }
}
