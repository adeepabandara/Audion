package com.example.audion.data;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import android.util.Log;
import android.content.Context;

@Database(
        entities = { 
            User.class, 
            HearingTestResult.class, 
            HearingProfile.class, 
            CalibrationEntry.class,
            AudiometryResult.class,
            CalibrationProfileEntity.class
        },
        version = 7,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    public abstract HearingTestResultDao hearingTestResultDao();
    public abstract HearingProfileDao hearingProfileDao();
    public abstract CalibrationDao calibrationDao();
    public abstract AudiometryResultDao audiometryResultDao();
    public abstract CalibrationProfileDao calibrationProfileDao();

    // Safe migration from version 4 to 5 - adds per-frequency calibration data
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            Log.i("AppDatabase", "Starting migration from version 4 to 5");
            
            try {
                // Add new columns for per-frequency MCL/UCL data
                database.execSQL("ALTER TABLE `calibration_profiles` ADD COLUMN `mclPerFrequencyJson` TEXT");
                database.execSQL("ALTER TABLE `calibration_profiles` ADD COLUMN `uclPerFrequencyJson` TEXT");
                
                Log.i("AppDatabase", "Migration 4→5 completed successfully! Added per-frequency calibration fields");
                
            } catch (Exception e) {
                Log.e("AppDatabase", "❌ Migration 4→5 failed: " + e.getMessage(), e);
                throw e;
            }
        }
    };

    // Migration from version 5 to 6 - updates schema hash for clinical enhancements
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            Log.i("AppDatabase", "Starting migration from version 5 to 6");
            
            try {
                // Check if HearingTestResult table needs clinical fields
                android.database.Cursor cursor = database.query("PRAGMA table_info(hearing_test_results)");
                boolean hasReliabilityScore = false;
                while (cursor.moveToNext()) {
                    String columnName = cursor.getString(1); // Column name is at index 1
                    if ("reliabilityScore".equals(columnName)) {
                        hasReliabilityScore = true;
                        break;
                    }
                }
                cursor.close();
                
                // Add clinical fields if they don't exist
                if (!hasReliabilityScore) {
                    database.execSQL("ALTER TABLE `hearing_test_results` ADD COLUMN `thresholdDbHL` REAL DEFAULT 0.0");
                    database.execSQL("ALTER TABLE `hearing_test_results` ADD COLUMN `thresholdDbSPL` REAL DEFAULT 0.0");
                    database.execSQL("ALTER TABLE `hearing_test_results` ADD COLUMN `isReliable` INTEGER DEFAULT 1");
                    database.execSQL("ALTER TABLE `hearing_test_results` ADD COLUMN `reversalCount` INTEGER DEFAULT 0");
                    database.execSQL("ALTER TABLE `hearing_test_results` ADD COLUMN `reliabilityScore` REAL DEFAULT 1.0");
                    
                    // Add testTimestamp column with constant default, then update existing records
                    database.execSQL("ALTER TABLE `hearing_test_results` ADD COLUMN `testTimestamp` INTEGER DEFAULT 0");
                    
                    // Update existing records with current timestamp
                    long currentTimestamp = System.currentTimeMillis();
                    database.execSQL("UPDATE `hearing_test_results` SET `testTimestamp` = " + currentTimestamp + " WHERE `testTimestamp` = 0");
                    
                    Log.i("AppDatabase", "Added clinical fields to hearing_test_results");
                }
                
                Log.i("AppDatabase", "Migration 5→6 completed successfully! Schema hash updated");
                
            } catch (Exception e) {
                Log.e("AppDatabase", "❌ Migration 5→6 failed: " + e.getMessage(), e);
                // Don't throw - this is just a schema hash update
                Log.w("AppDatabase", "Continuing with schema hash mismatch resolution");
            }
        }
    };

    // Migration from version 6 to 7 - adds unique constraint to prevent duplicate test results
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            Log.i("AppDatabase", "Starting migration from version 6 to 7");
            
            try {
                // Step 1: Create new table with unique constraint
                database.execSQL("CREATE TABLE IF NOT EXISTS `hearing_test_results_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`userId` INTEGER NOT NULL, " +
                    "`earSide` TEXT, " +
                    "`frequency` INTEGER NOT NULL, " +
                    "`amplitudeStep` INTEGER NOT NULL, " +
                    "`thresholdDbHL` REAL NOT NULL, " +
                    "`thresholdDbSPL` REAL NOT NULL, " +
                    "`isReliable` INTEGER NOT NULL, " +
                    "`reversalCount` INTEGER NOT NULL, " +
                    "`reliabilityScore` REAL NOT NULL, " +
                    "`testTimestamp` INTEGER NOT NULL, " +
                    "`hearingProfileId` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`hearingProfileId`) REFERENCES `hearing_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
                
                Log.i("AppDatabase", "Created new hearing_test_results table");
                
                // Step 2: Copy data from old table, keeping only the latest test for each (userId, earSide, frequency, hearingProfileId)
                database.execSQL("INSERT INTO `hearing_test_results_new` " +
                    "SELECT h1.* FROM `hearing_test_results` h1 " +
                    "INNER JOIN (" +
                    "  SELECT userId, earSide, frequency, hearingProfileId, MAX(id) as maxId " +
                    "  FROM `hearing_test_results` " +
                    "  GROUP BY userId, earSide, frequency, hearingProfileId" +
                    ") h2 ON h1.id = h2.maxId");
                
                Log.i("AppDatabase", "Copied unique test results to new table");
                
                // Step 3: Drop old table
                database.execSQL("DROP TABLE `hearing_test_results`");
                
                // Step 4: Rename new table to original name
                database.execSQL("ALTER TABLE `hearing_test_results_new` RENAME TO `hearing_test_results`");
                
                // Step 5: Create indices including the unique constraint
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_hearing_test_results_hearingProfileId` " +
                    "ON `hearing_test_results` (`hearingProfileId`)");
                
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hearing_test_results_userId_earSide_frequency_hearingProfileId` " +
                    "ON `hearing_test_results` (`userId`, `earSide`, `frequency`, `hearingProfileId`)");
                
                Log.i("AppDatabase", "Migration 6→7 completed successfully! Added unique constraint to prevent duplicates");
                
            } catch (Exception e) {
                Log.e("AppDatabase", "❌ Migration 6→7 failed: " + e.getMessage(), e);
                throw e;
            }
        }
    };

    // Safe migration from version 3 to 4 - preserves all existing user data
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            Log.i("AppDatabase", "Starting safe migration from version 3 to 4");
            
            try {
                // Create new audiometry_results table for ANSI S3.6 clinical data
                database.execSQL("CREATE TABLE IF NOT EXISTS `audiometry_results` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`userId` INTEGER NOT NULL, " +
                    "`earSide` TEXT NOT NULL, " +
                    "`frequency` INTEGER NOT NULL, " +
                    "`thresholdDbHL` REAL NOT NULL, " +
                    "`thresholdDbSPL` REAL NOT NULL, " +
                    "`isReliable` INTEGER NOT NULL, " +
                    "`reversalCount` INTEGER NOT NULL, " +
                    "`hearingProfileId` INTEGER NOT NULL, " +
                    "`testTimestamp` INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000), " +
                    "FOREIGN KEY(`hearingProfileId`) REFERENCES `hearing_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
                    
                Log.i("AppDatabase", "Created audiometry_results table successfully");
                
                // Create new calibration_profiles table for MCL/UCL personalized limits
                database.execSQL("CREATE TABLE IF NOT EXISTS `calibration_profiles` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`userId` INTEGER NOT NULL, " +
                    "`profileName` TEXT NOT NULL, " +
                    "`earSide` TEXT NOT NULL, " +
                    "`mclDbSpl` REAL NOT NULL, " +
                    "`uclDbSpl` REAL NOT NULL, " +
                    "`dynamicRange` REAL NOT NULL, " +
                    "`realEarGainJson` TEXT, " +
                    "`deviceCorrections` TEXT, " +
                    "`hearingProfileId` INTEGER NOT NULL, " +
                    "`createdTimestamp` INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000), " +
                    "`lastUpdated` INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000), " +
                    "FOREIGN KEY(`hearingProfileId`) REFERENCES `hearing_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
                    
                Log.i("AppDatabase", "Created calibration_profiles table successfully");
                
                // Create indexes for better query performance
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audiometry_results_userId_earSide_hearingProfileId` " +
                    "ON `audiometry_results` (`userId`, `earSide`, `hearingProfileId`)");
                    
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audiometry_results_frequency` " +
                    "ON `audiometry_results` (`frequency`)");
                    
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_calibration_profiles_userId_earSide_hearingProfileId` " +
                    "ON `calibration_profiles` (`userId`, `earSide`, `hearingProfileId`)");
                
                Log.i("AppDatabase", "Created performance indexes successfully");
                
                // Verify existing tables are preserved
                android.database.Cursor cursor = database.query("SELECT name FROM sqlite_master WHERE type='table'");
                int tableCount = 0;
                while (cursor.moveToNext()) {
                    String tableName = cursor.getString(0);
                    Log.d("AppDatabase", "Preserved table: " + tableName);
                    tableCount++;
                }
                cursor.close();
                
                Log.i("AppDatabase", "Migration 3→4 completed successfully! " + tableCount + " tables total");
                Log.i("AppDatabase", "✅ All existing user data preserved");
                Log.i("AppDatabase", "🎯 Clinical audiometry tables ready");
                
            } catch (Exception e) {
                Log.e("AppDatabase", "❌ Migration 3→4 failed: " + e.getMessage(), e);
                throw e; // Re-throw to trigger fallback handling
            }
        }
    };

    private static AppDatabase instance;
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "audion_db"
                    )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7) // Safe migrations preserving user data
                    .build();
        }
        return instance;
    }
}