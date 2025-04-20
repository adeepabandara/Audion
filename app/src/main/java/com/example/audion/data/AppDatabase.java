package com.example.audion.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import android.content.Context;
import androidx.room.Room;

/**
 * The central Room database for our app,
 * containing User, HearingTestResult and HearingProfile tables.
 */
@Database(
        entities = { User.class, HearingTestResult.class, HearingProfile.class },
        version = 2, // bumped version for schema changes
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract HearingTestResultDao hearingTestResultDao();
    public abstract HearingProfileDao hearingProfileDao();

    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "audion_db"
                    )
                    // For development, you might want to use fallbackToDestructiveMigration.
                    // .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
