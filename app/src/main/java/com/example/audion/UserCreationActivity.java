package com.example.audion;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.User;
import com.example.audion.data.UserDao;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingProfileDao;
import com.audion.psap.R;
import android.view.Window;
import android.view.WindowManager;

public class UserCreationActivity extends AppCompatActivity {

    private EditText editTextName;
    private Button buttonSubmit;
    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_creation);

        try {
            // Initialize DB using your singleton
            AppDatabase db = AppDatabase.getInstance(this);
            userDao = db.userDao();
        } catch (Exception e) {
            e.printStackTrace();
            // If database initialization fails, log and continue without database
            android.util.Log.e("UserCreationActivity", "Failed to initialize database: " + e.getMessage());
        }

        // Grab UI references
        editTextName = findViewById(R.id.editTextName);
        buttonSubmit = findViewById(R.id.buttonSubmit);

        // Check if name was passed from NameActivity
        String passedName = getIntent().getStringExtra("user_name");
        if (passedName != null && !passedName.isEmpty()) {
            editTextName.setText(passedName);
        }

        // On click, create user (and default hearing profile) in background
        buttonSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createUserAndFinish();
            }
        });
    }

    private void createUserAndFinish() {
        String name = editTextName.getText().toString().trim();

        if (!name.isEmpty()) {
            new Thread(() -> {
                try {
                    if (userDao == null) {
                        // Fallback if database isn't available
                        runOnUiThread(() -> {
                            Intent intent = new Intent(UserCreationActivity.this, GeneralInstructionActivity.class);
                            intent.putExtra("USER_ID", 1); // Default user ID
                            intent.putExtra("HEARING_PROFILE_ID", 1); // Default profile ID
                            startActivity(intent);
                            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
                            finish();
                        });
                        return;
                    }

                    // Insert the new user
                    User newUser = new User(name);
                    userDao.insert(newUser);

                    // Retrieve the inserted user
                    User insertedUser = userDao.getUserByName(name);
                    if (insertedUser == null) {
                        runOnUiThread(() ->
                            Toast.makeText(UserCreationActivity.this, "Error retrieving the created user", Toast.LENGTH_SHORT).show()
                        );
                        return;
                    }
                    int newUserId = insertedUser.getId();

                    // Create the default hearing profile for a first-time user.
                    // In a new DB the auto-generated ID should be 1.
                    AppDatabase db = AppDatabase.getInstance(UserCreationActivity.this);
                    HearingProfileDao hpDao = db.hearingProfileDao();
                    HearingProfile defaultProfile = new HearingProfile("Standard", "default_icon");
                    long profileId = hpDao.insert(defaultProfile);

                    // Pass both the USER_ID and the new HEARING_PROFILE_ID through the intent.
                    runOnUiThread(() -> {
                        Intent intent = new Intent(UserCreationActivity.this, GeneralInstructionActivity.class);
                        intent.putExtra("USER_ID", newUserId);
                        intent.putExtra("HEARING_PROFILE_ID", (int) profileId);
                        startActivity(intent);
                        // Add smooth transition animation
                        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
                        finish();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    android.util.Log.e("UserCreationActivity", "Database error: " + e.getMessage());
                    // Fallback navigation
                    runOnUiThread(() -> {
                        Intent intent = new Intent(UserCreationActivity.this, GeneralInstructionActivity.class);
                        intent.putExtra("USER_ID", 1); // Default user ID
                        intent.putExtra("HEARING_PROFILE_ID", 1); // Default profile ID
                        startActivity(intent);
                        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
                        finish();
                    });
                }
            }).start();
        } else {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
        }
    }
}
