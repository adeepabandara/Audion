// File: app/src/main/java/com/example/myjavaroomapp/MainActivity.java
package com.example.androidapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import com.example.androidapp.data.AppDatabase;
import com.example.androidapp.data.User;
import com.example.androidapp.data.UserDao;
import java.util.List;

public class Db_test extends AppCompatActivity {

    private AppDatabase db;
    private UserDao userDao;
    private TextView outputTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.db_check);

        // Get the TextView
        outputTextView = findViewById(R.id.outputTextView);

        // Build the Room database instance
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "my-database")
                .allowMainThreadQueries()  // For demo purposes only!
                .build();

        // Retrieve the DAO
        userDao = db.userDao();

        // Execute sample CRUD operations
        performCRUDOperations();
    }

    private void performCRUDOperations() {
        // Create: Insert a new user
        User newUser = new User("Adeepa", 25);
        userDao.insert(newUser);

        // Read: Retrieve all users
        List<User> users = userDao.getAllUsers();
        StringBuilder output = new StringBuilder();
        for (User user : users) {
            String userInfo = "User: " + user.getName() + ", Age: " + user.getAge() + "\n";
            Log.d("Db_test", userInfo);  // Log to Logcat
            output.append(userInfo);
        }

        // Update: Update the first user's age (if exists)
        if (!users.isEmpty()) {
            User firstUser = users.get(0);
            firstUser.setAge(26);
            userDao.update(firstUser);
        }

        // Delete: Remove the user we just created
        userDao.delete(newUser);

        // Optionally, display the output on the UI
        outputTextView.setText(output.toString());
    }
}
