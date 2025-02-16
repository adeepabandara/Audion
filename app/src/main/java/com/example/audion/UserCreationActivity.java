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
    private EditText editTextAge;
    private Button buttonSubmit;

    private AppDatabase db;
    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_creation);

        // Initialize Room (You could also pass it via Intent or a Singleton/DI, but for simplicity:
        db = Room.databaseBuilder(
                getApplicationContext(),
                AppDatabase.class,
                "audion-database"
        )
        .allowMainThreadQueries() // For demonstration only
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
    // Get the user inputs
    String name = editTextName.getText().toString().trim();

    if (!name.isEmpty()) {
        // Create a new user and insert into DB
        User newUser = new User(name);
        userDao.insert(newUser);

        // Navigate to HomeActivity
        Intent intent = new Intent(this, PureToneTestActivity.class);
        startActivity(intent);

        // Finish current activity
        finish();
    } else {
        // Show a Toast message if the input is empty
        Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
    }
}

}
