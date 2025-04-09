package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.HearingTestResult;
import com.example.audion.data.HearingTestResultDao;
import com.example.audion.data.User;
import com.example.audion.data.UserDao;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity now shows a list of added users along with an "Add New User" button.
 * When a user is selected, it checks the hearing test records *for that user*:
 * - If 8 records are present, it navigates to HomeActivity.
 * - If there are 0 or between 1 and 7 records, it navigates to GeneralInstructionActivity (for restarting/resuming the test).
 */
public class MainActivity extends AppCompatActivity {

    // Static DB reference so that other activities can reuse it
    private static AppDatabase db;

    private UserDao userDao;
    private HearingTestResultDao hearingTestResultDao;
    private ListView userListView;
    private Button addUserButton;
    private List<User> users;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list); // Updated layout file

        // Initialize UI elements from the layout
        userListView = findViewById(R.id.userListView);
        addUserButton = findViewById(R.id.addUserButton);

        // Initialize the Room database (for demo purposes we allow queries on the main thread)
        if (db == null) {
            db = Room.databaseBuilder(
                    getApplicationContext(),
                    AppDatabase.class,
                    "audion-database"
            )
            .allowMainThreadQueries()  // In production, use background threads for DB operations
            .build();
        }

        // Obtain the DAOs
        userDao = db.userDao();
        hearingTestResultDao = db.hearingTestResultDao();

        // Load all users from the database
        users = userDao.getAllUsers();

        // Create an ArrayAdapter to display each user's ID and name in the ListView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                getUserDisplayList(users));
        userListView.setAdapter(adapter);

        // Set listener for list item clicks (an existing user is selected)
        userListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                User selectedUser = users.get(position);
                Toast.makeText(MainActivity.this,
                        "Selected User: " + selectedUser.getName(),
                        Toast.LENGTH_SHORT).show();

                // Pass selected user's ID in the next activity using modified logic
                proceedBasedOnHearingTest(selectedUser.getId());
            }
        });
        // Set listener for the "Add New User" button
        addUserButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Navigate to user creation screen
                Intent intent = new Intent(MainActivity.this, UserCreationActivity.class);
                startActivity(intent);
            }
        });
    }

    /**
     * Helper method to convert the list of User objects into a list of strings for display.
     */
    private List<String> getUserDisplayList(List<User> users) {
        List<String> displayList = new ArrayList<>();
        for (User user : users) {
            displayList.add("ID: " + user.getId() + " | Name: " + user.getName());
        }
        return displayList;
    }

    /**
     * Checks the hearing test records for the selected user and navigates accordingly.
     * - If there are 8 records, navigate to HomeActivity.
     * - If there are 1-7 records, delete them (to restart) and navigate to GeneralInstructionActivity.
     * - If no records exist, navigate directly to GeneralInstructionActivity.
     */
    private void proceedBasedOnHearingTest(int userId) {
        // Query only for the hearing test results of the selected user.
        List<HearingTestResult> resultsForUser = hearingTestResultDao.getResultsForUser(userId);
        int recordCount = resultsForUser.size();
        Intent intent;
        if (recordCount == 8) {
            intent = new Intent(MainActivity.this, HomeActivity.class);
        } else if (recordCount >= 1 && recordCount < 8) {
            // Delete incomplete results for this user
            hearingTestResultDao.deleteResultsForUser(userId);
            intent = new Intent(MainActivity.this, GeneralInstructionActivity.class);
        } else {
            // No test records yet for the user.
            intent = new Intent(MainActivity.this, GeneralInstructionActivity.class);
        }
        // Pass the user ID to the next activity
        intent.putExtra("USER_ID", userId);
        startActivity(intent);
        finish();
    }

    /**
     * Static accessor for the Room database instance.
     */
    public static AppDatabase getDatabase() {
        return db;
    }
}
