package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.User;
import com.example.audion.data.UserDao;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Store a static reference so other activities can use the same DB instance
    private static AppDatabase db;
    private UserDao userDao;
    private TextView outputTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.db_check);  // Suppose you have a layout named db_check.xml

        outputTextView = findViewById(R.id.outputTextView);

        // Initialize the Room database once if it's null
        if (db == null) {
            db = Room.databaseBuilder(
                    getApplicationContext(),
                    AppDatabase.class, 
                    "audion-database"
            )
            .allowMainThreadQueries()  // For demonstration only; use background threads in production
            .build();
        }

        // Now get a Dao from the DB
        userDao = db.userDao();

        // Check if any user exists
        List<User> users = userDao.getAllUsers();

        if (users.isEmpty()) {
            // No user record - navigate to user creation screen
            Intent intent = new Intent(this, UserCreationActivity.class);
            startActivity(intent);
            finish();  // optional
        } else {
            // At least one user exists in the database
            // Navigate to PureToneTestActivity (or any other "Home" activity)
            Intent intent = new Intent(this, PureToneTestActivity.class);
            startActivity(intent);
            finish();  // optional
        }
    }

    /**
     * Static method so other Activities can retrieve the same DB instance
     */
    public static AppDatabase getDatabase() {
        return db;
    }

    // Optional helper method if you wanted to display user info on screen
    private void displayUsers(List<User> users) {
        StringBuilder output = new StringBuilder();
        for (User user : users) {
            output.append("User: ")
                  .append(user.getName())
                  .append("\n");
        }
        outputTextView.setText(output.toString());
    }
}
