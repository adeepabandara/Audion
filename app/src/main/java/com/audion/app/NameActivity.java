package com.audion.app;

import com.audion.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.audion.app.data.AppDatabase;
import com.audion.app.data.User;
import com.audion.app.data.UserDao;
import com.audion.app.data.HearingProfile;
import com.audion.app.data.HearingProfileDao;

import java.util.List;

public class NameActivity extends AppCompatActivity {

    private EditText nameInput;
    private Button continueButton;
    private ImageView logoImage;
    private TextView questionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_name);

        // Initialize views
        nameInput = findViewById(R.id.nameInput);
        continueButton = findViewById(R.id.continueButton);
        logoImage = findViewById(R.id.ivLogo);
        questionText = findViewById(R.id.nameQuestion);

        // Set up keyboard handling
        setupKeyboardHandling();
        
        // Start entrance animations
        startEntranceAnimations();

        // Set up name input listener
        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Enable continue button when text is entered
                continueButton.setEnabled(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Set up continue button click listener
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Add button press animation
                Animation buttonPress = AnimationUtils.loadAnimation(NameActivity.this, R.anim.button_press);
                v.startAnimation(buttonPress);
                
                // Delay the navigation slightly to show the animation
                v.postDelayed(() -> {
                    String userName = nameInput.getText().toString().trim();
                    if (!userName.isEmpty()) {
                        // Store user name in database and proceed to next screen
                        new Thread(() -> {
                            try {
                                AppDatabase db = AppDatabase.getInstance(NameActivity.this);
                                UserDao userDao = db.userDao();
                                HearingProfileDao hearingProfileDao = db.hearingProfileDao();
                                
                                Log.d("NameActivity", "===== CREATING NEW USER =====");
                                Log.d("NameActivity", "User name: " + userName);
                                
                                // Insert user and get ID
                                User user = new User(userName);
                                long userId = userDao.insert(user);
                                
                                Log.d("NameActivity", "✅ User inserted successfully! User ID: " + userId);
                                
                                // Create hearing profile for this session
                                HearingProfile hearingProfile = new HearingProfile(userName + "_Profile", "default");
                                long hearingProfileId = hearingProfileDao.insert(hearingProfile);
                                
                                Log.d("NameActivity", "✅ Hearing profile created! Profile ID: " + hearingProfileId);
                                
                                // Verify the user was actually saved by reading it back
                                List<User> allUsers = userDao.getAllUsers();
                                Log.d("NameActivity", "🔍 Verification: Total users in database: " + (allUsers != null ? allUsers.size() : 0));
                                if (allUsers != null) {
                                    for (User u : allUsers) {
                                        Log.d("NameActivity", "  User: ID=" + u.getId() + ", Name='" + u.getName() + "'");
                                    }
                                }
                                
                                Log.d("NameActivity", "===== DATABASE WRITE COMPLETE =====");
                                
                                // Force database checkpoint to ensure data is written to disk
                                try {
                                    db.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL)");
                                    Log.d("NameActivity", "✅ Database checkpoint completed");
                                } catch (Exception e) {
                                    Log.w("NameActivity", "Database checkpoint warning: " + e.getMessage());
                                }
                                
                                final int finalUserId = (int)userId;
                                final int finalHearingProfileId = (int)hearingProfileId;
                                
                                // Add a small delay to ensure database write is fully committed
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                    Log.w("NameActivity", "Sleep interrupted: " + e.getMessage());
                                }
                                
                                runOnUiThread(() -> {
                                    // Navigate to Start Test Activity
                                    Intent intent = new Intent(NameActivity.this, StartTestActivity.class);
                                    intent.putExtra("user_name", userName);
                                    intent.putExtra("USER_ID", finalUserId);
                                    intent.putExtra("HEARING_PROFILE_ID", finalHearingProfileId);
                                    startActivity(intent);
                                    // Add smooth transition animation
                                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_scale_out);
                                    finish();
                                });
                            } catch (Exception e) {
                                Log.e("NameActivity", "❌ Error creating user: " + e.getMessage(), e);
                                runOnUiThread(() -> {
                                    Toast.makeText(NameActivity.this, 
                                        "Error saving profile. Please try again.", 
                                        Toast.LENGTH_SHORT).show();
                                });
                            }
                        }).start();
                    }
                }, 150);
            }
        });

        // Initially disable continue button
        continueButton.setEnabled(false);
        
        // Focus on name input
        nameInput.requestFocus();
    }

    private void setupKeyboardHandling() {
        // Since we're using ConstraintLayout with bottom-fixed button, 
        // keyboard handling is managed by the system automatically
    }
    
    private void startEntranceAnimations() {
        // Initially hide elements that will animate in
        logoImage.setAlpha(0f);
        questionText.setAlpha(0f);
        nameInput.setAlpha(0f);
        continueButton.setAlpha(0f);
        
        // Load animations
        Animation logoFadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_scale_in);
        Animation questionSlideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in);
        Animation inputSlideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in);
        Animation buttonSlideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in_delayed);
        
        // Start animations with delays
        logoImage.postDelayed(() -> {
            logoImage.setAlpha(1f);
            logoImage.startAnimation(logoFadeIn);
        }, 100);
        
        questionText.postDelayed(() -> {
            questionText.setAlpha(1f);
            questionText.startAnimation(questionSlideUp);
        }, 200);
        
        nameInput.postDelayed(() -> {
            nameInput.setAlpha(1f);
            nameInput.startAnimation(inputSlideUp);
        }, 400);
        
        continueButton.postDelayed(() -> {
            continueButton.setAlpha(1f);
            continueButton.startAnimation(buttonSlideUp);
        }, 600);
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Add smooth reverse transition animation
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
