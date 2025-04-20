package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.example.audion.data.User;
import com.example.audion.data.UserDao;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Constant user ID 1 throughout the app.
    private static final int USER_ID = 1;

    private UserDao userDao;
    private HearingTestResultDao hearingTestResultDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Optionally, set a content view if needed:
        // setContentView(R.layout.activity_main);

        // Get the AppDatabase instance (using your singleton method)
        AppDatabase db = AppDatabase.getInstance(this);
        userDao = db.userDao();
        hearingTestResultDao = db.hearingTestResultDao();

        // Run all database logic on a background thread.
        new Thread(() -> {
            // 1. Check if a user with ID 1 exists.
            User user = userDao.getUserById(USER_ID);
            if (user == null) {
                // No user found, so navigate to UserCreationActivity.
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "No user found. Please create one.",
                            Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, UserCreationActivity.class);
                    startActivity(intent);
                    finish();
                });
            } else {
                // 2. User exists. Check the hearing test records.
                List<HearingTestResult> results = hearingTestResultDao.getResultsForUser(USER_ID);
                int recordCount = results.size();

                Intent intent;
                if (recordCount >= 16) {  // Now checks if eight or more records exist.
                    // Navigate to HomeActivity.
                    intent = new Intent(MainActivity.this, HomeActivity.class);
                } else {
                    // If fewer than 16 records exist, delete all records for user 1
                    // and navigate to GeneralInstructionActivity (to restart the test).
                    hearingTestResultDao.deleteResultsForUser(USER_ID);
                    intent = new Intent(MainActivity.this, GeneralInstructionActivity.class);
                }
                intent.putExtra("USER_ID", USER_ID);

                // Launch the next Activity on the main thread.
                runOnUiThread(() -> {
                    startActivity(intent);
                    finish();
                });
            }
        }).start();
    }
}
