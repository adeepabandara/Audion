package com.example.audion.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;

    public User(String name) {
        this.name = name;
    }

    // Getters / Setters
    public int getId() {
        return id;
    }
    public void setId(int id) { this.id = id; }

    public String getName() {
        return name;
    }
    public void setName(String name) { this.name = name; }
}
