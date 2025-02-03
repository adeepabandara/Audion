// File: app/src/main/java/com/example/myjavaroomapp/data/User.java
package com.example.audion.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String name;
    private int age;

    // Constructor without id (auto-generated)
    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // Getter and setter for id
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    // Getter and setter for name
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    // Getter and setter for age
    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }
}
