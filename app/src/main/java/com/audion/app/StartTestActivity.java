package com.audion.app;

import com.audion.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.audion.app.data.AppDatabase;
import com.audion.app.data.HearingProfile;
import com.audion.app.data.User;

import java.util.List;

public class StartTestActivity extends AppCompatActivity {

    private static final String TAG = "StartTestActivity";
    private Button btnStartTest;
    private TextView tvGreeting;
    private int userId;
    private int hearingProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_test);

        // Initialize views
        btnStartTest = findViewById(R.id.btnStartTest);
        tvGreeting = findViewById(R.id.tvGreeting);

        // Get USER_ID and HEARING_PROFILE_ID from intent
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        
        // Load user's name from database
        loadUserName();
        
        Log.d(TAG, "===== StartTestActivity Received =====");
        Log.d(TAG, "USER_ID: " + userId);
        Log.d(TAG, "HEARING_PROFILE_ID: " + hearingProfileId);
        
        if (userId < 0 || hearingProfileId < 0) {
            Log.e(TAG, "❌ ERROR: Invalid IDs received! Attempting to recover from database...");
            
            // Try to recover by querying the database
            new Thread(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(this);
                    List<User> users = db.userDao().getAllUsers();
                    List<HearingProfile> profiles = db.hearingProfileDao().getAllProfiles();
                    
                    Log.d(TAG, "Recovery attempt - Users found: " + (users != null ? users.size() : 0));
                    Log.d(TAG, "Recovery attempt - Profiles found: " + (profiles != null ? profiles.size() : 0));
                    
                    if (users != null && !users.isEmpty() && profiles != null && !profiles.isEmpty()) {
                        userId = users.get(0).getId();
                        hearingProfileId = profiles.get(0).getId();
                        
                        Log.d(TAG, "✅ Recovered - USER_ID: " + userId + ", HEARING_PROFILE_ID: " + hearingProfileId);
                        
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Profile recovered successfully", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        Log.e(TAG, "❌ Recovery failed - No user or profile found in database");
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Unable to load profile. Please restart the app.", Toast.LENGTH_LONG).show();
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Recovery error: " + e.getMessage(), e);
                }
            }).start();
        }

        // Start Test button click
        btnStartTest.setOnClickListener(v -> {
            Log.d(TAG, "Start Test button clicked");
            Log.d(TAG, "Passing to RightEarInstruction - USER_ID: " + userId + ", HEARING_PROFILE_ID: " + hearingProfileId);
            
            // Final validation before navigation
            if (userId < 0 || hearingProfileId < 0) {
                Log.e(TAG, "❌ Cannot proceed - Invalid IDs: USER_ID=" + userId + ", HEARING_PROFILE_ID=" + hearingProfileId);
                Toast.makeText(this, "Profile error. Please restart the app and try again.", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Mark onboarding as completed
            getSharedPreferences("AudionPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_completed", true)
                .apply();

            // Navigate directly to Right Ear Instruction with IDs
            Intent intent = new Intent(StartTestActivity.this, RightEarInstructionActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
            startActivity(intent);
            overridePendingTransition(R.anim.smooth_fade_in, R.anim.smooth_fade_out);
            finish();
        });
    }
    
    private void loadUserName() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                User user = db.userDao().getUserById(userId);
                runOnUiThread(() -> {
                    if (user != null && user.getName() != null && !user.getName().isEmpty()) {
                        tvGreeting.setText("Hi " + user.getName() + "!");
                    } else {
                        tvGreeting.setText("Hi!");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading user name: " + e.getMessage(), e);
                runOnUiThread(() -> tvGreeting.setText("Hi!"));
            }
        }).start();
    }
}
