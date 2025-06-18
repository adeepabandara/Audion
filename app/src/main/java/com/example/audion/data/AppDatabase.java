package com.example.audion.data;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

@Database(
        entities = { User.class, HearingTestResult.class, HearingProfile.class, CalibrationEntry.class },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    public abstract HearingTestResultDao hearingTestResultDao();
    public abstract HearingProfileDao hearingProfileDao();
    public abstract CalibrationDao calibrationDao();

    private static AppDatabase instance;
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "audion_db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}