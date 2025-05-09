package com.example.audion.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * The central Room database for our app,
 * containing both User and HearingTestResult tables.
 */
@Database(
    entities = { User.class, HearingTestResult.class },
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract HearingTestResultDao hearingTestResultDao();

    // If you add more entities or DAOs, list them here
}
