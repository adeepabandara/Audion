package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class HomeActivity extends AppCompatActivity {
    private Button buttonVariationOne;
    private Button buttonVariationTwo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonVariationOne = findViewById(R.id.buttonVariationOne);
        buttonVariationTwo = findViewById(R.id.buttonVariationTwo);

        buttonVariationOne.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, VariationOneActivity.class);
            startActivity(intent);
        });

        buttonVariationTwo.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, VariationTwoActivity.class);
            startActivity(intent);
        });
    }




    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Close current activity and go back
        return true;
    }
}
