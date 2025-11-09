package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.User;
import com.example.audion.data.UserDao;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingProfileDao;

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
                            AppDatabase db = AppDatabase.getInstance(NameActivity.this);
                            UserDao userDao = db.userDao();
                            HearingProfileDao hearingProfileDao = db.hearingProfileDao();
                            
                            // Insert user and get ID
                            User user = new User(userName);
                            long userId = userDao.insert(user);
                            
                            // Create hearing profile for this session
                            HearingProfile hearingProfile = new HearingProfile(userName + "_Profile", "default");
                            long hearingProfileId = hearingProfileDao.insert(hearingProfile);
                            
                            runOnUiThread(() -> {
                                // CORRECTED CLINICAL FLOW: Navigate to Pure Tone Test first (RIGHT ear)
                                Intent intent = new Intent(NameActivity.this, RightEarInstructionActivity.class);
                                intent.putExtra("user_name", userName);
                                intent.putExtra("USER_ID", (int)userId);
                                intent.putExtra("HEARING_PROFILE_ID", (int)hearingProfileId);
                                startActivity(intent);
                                // Add smooth transition animation
                                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_scale_out);
                                finish();
                            });
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
