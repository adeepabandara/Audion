package com.audion.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.audion.app.data.AppDatabase;
import com.audion.app.data.HearingProfile;
import com.audion.app.data.HearingProfileDao;
import com.audion.app.data.UserDao;
import com.audion.app.data.User;
import com.audion.app.R;
import android.view.Window;
import android.view.WindowManager;

public class GeneralInstructionActivity extends AppCompatActivity {

    private Button buttonProceed;
    private TextView tvGreeting;
    private int userId;
    private int hearingProfileId; // Will be -1 if not provided

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_instruction);

        buttonProceed = findViewById(R.id.buttonProceed);
        tvGreeting = findViewById(R.id.tvGreeting);
        userId = getIntent().getIntExtra("USER_ID", -1);
        hearingProfileId = getIntent().getIntExtra("HEARING_PROFILE_ID", -1);
        
        // Load and display user's name
        loadUserName();

        buttonProceed.setOnClickListener(view -> {
            // If no hearing profile ID came via the intent (scenario 2), create one.
            if (hearingProfileId == -1) {
                createNewHearingProfileAndProceed();
            } else {
                proceedToRightEarInstruction();
            }
        });
    }

    private void loadUserName() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(GeneralInstructionActivity.this);
            UserDao userDao = db.userDao();
            User user = userDao.getUserById(userId);
            runOnUiThread(() -> {
                if (user != null && user.getName() != null && !user.getName().isEmpty()) {
                    tvGreeting.setText("Hi " + user.getName() + "!");
                } else {
                    tvGreeting.setText("Hi!");
                }
            });
        }).start();
    }

    private void createNewHearingProfileAndProceed() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(GeneralInstructionActivity.this);
            HearingProfileDao hpDao = db.hearingProfileDao();
            // Create a new hearing profile (the next inline ID will be auto-generated)
            HearingProfile newProfile = new HearingProfile("New Profile", "icon_placeholder");
            long newId = hpDao.insert(newProfile);
            hearingProfileId = (int) newId;
            runOnUiThread(this::proceedToRightEarInstruction);
        }).start();
    }

    private void proceedToRightEarInstruction() {
        Intent intent = new Intent(GeneralInstructionActivity.this, RightEarInstructionActivity.class);
        intent.putExtra("USER_ID", userId);
        intent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
        finish();
    }
}
