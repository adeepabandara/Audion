package com.example.audion.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CalibrationDao {
    @Insert
    void insert(CalibrationEntry entry);

    @Query("SELECT * FROM calibration_entries WHERE userId = :userId AND profileId = :profileId")
    List<CalibrationEntry> getForUserProfile(int userId, int profileId);

    @Query("SELECT * FROM calibration_entries WHERE userId = :userId AND profileId = :profileId")
    List<CalibrationEntry> getCalibrationsByUserAndProfile(int userId, int profileId);
}
