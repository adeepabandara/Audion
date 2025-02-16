package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResultDao;
import com.example.audion.data.User;
import com.example.audion.data.UserDao;

import java.util.List;

/**
 * MainActivity:
 * 1. Initializes the Room DB if not already.
 * 2. Checks if a User record exists:
 *    - if none, go to UserCreationActivity.
 *    - if at least one, then check # of hearing test records:
 *        => if 8, go HomeActivity
 *        => else if (1 <= count < 8), delete them, go InstructionActivity
 *        => else (count == 0), no need to delete, go InstructionActivity
 */
public class MainActivity extends AppCompatActivity {

    // Static DB reference so other activities can reuse it
    private static AppDatabase db;

    private UserDao userDao;
    private HearingTestResultDao hearingTestResultDao;
    private TextView outputTextView; // if you have a text view in db_check layout

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.db_check);

        outputTextView = findViewById(R.id.outputTextView);

        // Initialize the Room database once if it's null
        if (db == null) {
            db = Room.databaseBuilder(
                    getApplicationContext(),
                    AppDatabase.class,
                    "audion-database"
            )
            .allowMainThreadQueries()  // For demo only; use background threads in production
            .build();
        }

        // Now get DAOs
        userDao = db.userDao();
        hearingTestResultDao = db.hearingTestResultDao();

        // Check if any user exists
        List<User> users = userDao.getAllUsers();

        if (users.isEmpty()) {
            // No user -> UserCreation
            Intent intent = new Intent(this, UserCreationActivity.class);
            startActivity(intent);
            finish();
        } else {
            // At least one user
            int recordCount = hearingTestResultDao.getAllResults().size();

            if (recordCount == 8) {
                // All 8 done -> Home
                Intent intent = new Intent(this, HomeActivity.class);
                startActivity(intent);
                finish();
            } else if (recordCount >= 1 && recordCount < 8) {
                // If we have 1..7 records, delete them
                hearingTestResultDao.deleteAll();
                // Then go instruction
                Intent intent = new Intent(this, GeneralInstructionActivity.class);
                startActivity(intent);
                finish();
            } else {
                // recordCount == 0 (no hearing test records yet)
                // -> no need to delete -> go instruction
                Intent intent = new Intent(this, GeneralInstructionActivity.class);
                startActivity(intent);
                finish();
            }
        }
    }

    // Provide a static accessor for other Activities
    public static AppDatabase getDatabase() {
        return db;
    }

    // Optional helper
    private void displayUsers(List<User> users) {
        StringBuilder output = new StringBuilder();
        for (User user : users) {
            output.append("User: ")
                  .append(user.getName())
                  .append("\n");
        }
        if (outputTextView != null) {
            outputTextView.setText(output.toString());
        }
    }
}
