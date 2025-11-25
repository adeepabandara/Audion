package com.audion.app;

import com.audion.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.audion.app.data.AppDatabase;
import com.audion.app.data.HearingProfile;
import com.audion.app.data.HearingProfileDao;
import com.audion.app.data.User;
import com.audion.app.data.UserDao;

import java.util.List;

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
                try {
                    AppDatabase db = AppDatabase.getInstance(this);
                    UserDao userDao = db.userDao();
                    HearingProfileDao profileDao = db.hearingProfileDao();
                    
                    Log.d("WhatsYourNameActivity", "===== CREATING NEW USER =====");
                    Log.d("WhatsYourNameActivity", "User name: " + name);
                    
                    // Create User entity first
                    User user = new User(name);
                    long userId = userDao.insert(user);
                    
                    Log.d("WhatsYourNameActivity", "✅ User inserted successfully! User ID: " + userId);
                    
                    // Create a default hearing profile
                    HearingProfile profile = new HearingProfile(name + "'s Profile", "default_icon");
                    long profileId = profileDao.insert(profile);
                    
                    Log.d("WhatsYourNameActivity", "✅ Hearing profile created! Profile ID: " + profileId);
                    
                    // Verify the user was actually saved by reading it back
                    List<User> allUsers = userDao.getAllUsers();
                    Log.d("WhatsYourNameActivity", "🔍 Verification: Total users in database: " + (allUsers != null ? allUsers.size() : 0));
                    if (allUsers != null) {
                        for (User u : allUsers) {
                            Log.d("WhatsYourNameActivity", "  User: ID=" + u.getId() + ", Name='" + u.getName() + "'");
                        }
                    }
                    
                    // Verify the profile was actually saved by reading it back
                    List<HearingProfile> allProfiles = profileDao.getAllProfiles();
                    Log.d("WhatsYourNameActivity", "🔍 Verification: Total profiles in database: " + (allProfiles != null ? allProfiles.size() : 0));
                    if (allProfiles != null) {
                        for (HearingProfile p : allProfiles) {
                            Log.d("WhatsYourNameActivity", "  Profile: ID=" + p.getId() + ", Name='" + p.getName() + "'");
                        }
                    }
                    
                    Log.d("WhatsYourNameActivity", "===== DATABASE WRITE COMPLETE =====");
                    
                    // Force database checkpoint to ensure data is written to disk
                    try {
                        db.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL)");
                        Log.d("WhatsYourNameActivity", "✅ Database checkpoint completed");
                    } catch (Exception e) {
                        Log.w("WhatsYourNameActivity", "Database checkpoint warning: " + e.getMessage());
                    }
                    
                    final int finalUserId = (int)userId;
                    final int finalProfileId = (int)profileId;
                    
                    // Add a small delay to ensure database write is fully committed
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Log.w("WhatsYourNameActivity", "Sleep interrupted: " + e.getMessage());
                    }
                    
                    // Navigate to Start Test screen with IDs
                    runOnUiThread(() -> {
                        Intent intent = new Intent(WhatsYourNameActivity.this, StartTestActivity.class);
                        intent.putExtra("USER_ID", finalUserId);
                        intent.putExtra("HEARING_PROFILE_ID", finalProfileId);
                        startActivity(intent);
                        overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
                        finish();
                    });
                } catch (Exception e) {
                    Log.e("WhatsYourNameActivity", "❌ Error creating user: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        Toast.makeText(WhatsYourNameActivity.this, 
                            "Error saving profile. Please try again.", 
                            Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
    }
    
    private void updateContinueButton() {
        boolean nameValid = !TextUtils.isEmpty(etName.getText().toString().trim());
        
        btnContinue.setEnabled(nameValid);
        btnContinue.setAlpha(nameValid ? 1.0f : 0.5f);
    }
}
