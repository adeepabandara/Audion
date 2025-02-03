package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import com.example.audion.data.AppDatabase;
import com.example.audion.data.User;
import com.example.audion.data.UserDao;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private AppDatabase db;
    private UserDao userDao;
    private TextView outputTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.db_check);  // Your layout with the TextView

        outputTextView = findViewById(R.id.outputTextView);

        // Initialize the Room database
        db = Room.databaseBuilder(
                getApplicationContext(),
                AppDatabase.class, 
                "audion-database"
        )
        .allowMainThreadQueries()  // For demonstration only
        .build();

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
            // Navigate to some other activity, say "HomeActivity"
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
            finish();  // optional if you don't want them to come back here
        }
    }

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
