package com.example.audion;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.User;
import com.example.audion.data.UserDao;
import android.widget.Toast;
import android.content.Intent;

public class UserCreationActivity extends AppCompatActivity {

    private EditText editTextName;
    // If you need age later, you can keep editTextAge, but for now it’s unused
    // private EditText editTextAge;
    private Button buttonSubmit;

    private AppDatabase db;
    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_creation);

        // Initialize Room database (for demonstration only; use background threads in production)
        db = Room.databaseBuilder(
                getApplicationContext(),
                AppDatabase.class,
                "audion-database"
        )
        .allowMainThreadQueries()
        .build();

        userDao = db.userDao();

        // Grab the UI components
        editTextName = findViewById(R.id.editTextName);
        buttonSubmit = findViewById(R.id.buttonSubmit);

        // Handle button click
        buttonSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createUserAndFinish();
            }
        });
    }

    private void createUserAndFinish() {
        // Get the user input for the name.
        String name = editTextName.getText().toString().trim();

        if (!name.isEmpty()) {
            // Create a new user and insert into DB.
            User newUser = new User(name);
            userDao.insert(newUser);  // This method returns void in your current setup

            // Now query the inserted user; assuming names are unique (or this is sufficient for your demo).
            User insertedUser = userDao.getUserByName(name);
            if (insertedUser == null) {
                Toast.makeText(this, "Error retrieving the created user", Toast.LENGTH_SHORT).show();
                return;
            }
            int newUserId = insertedUser.getId();

            // Navigate to GeneralInstructionActivity while passing the user ID.
            Intent intent = new Intent(this, GeneralInstructionActivity.class);
            intent.putExtra("USER_ID", newUserId);
            startActivity(intent);

            // Finish current activity.
            finish();
        } else {
            // Show a Toast message if the input is empty.
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
        }
    }
}
