// File: app/src/main/java/com/example/myjavaroomapp/data/UserDao.java
package com.example.androidapp.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface UserDao {

    // Create: Insert a new user
    @Insert
    void insert(User user);

    // Read: Retrieve all users
    @Query("SELECT * FROM users")
    List<User> getAllUsers();

    // Read: Retrieve a user by id
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    User getUserById(int userId);

    // Update: Update an existing user
    @Update
    void update(User user);

    // Delete: Remove a user
    @Delete
    void delete(User user);
}
