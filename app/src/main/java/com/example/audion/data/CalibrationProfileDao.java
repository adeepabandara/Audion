package com.example.audion.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CalibrationProfileDao {

    @Insert
    long insert(CalibrationProfileEntity profile);

    @Update
    void update(CalibrationProfileEntity profile);

    @Delete
    void delete(CalibrationProfileEntity profile);

    @Query("SELECT * FROM calibration_profiles WHERE userId = :userId AND hearingProfileId = :profileId")
    List<CalibrationProfileEntity> getProfilesForUser(int userId, int profileId);
    
    @Query("SELECT * FROM calibration_profiles WHERE userId = :userId AND hearingProfileId = :profileId")
    List<CalibrationProfileEntity> getForUserProfile(int userId, int profileId);

    @Query("SELECT * FROM calibration_profiles WHERE userId = :userId AND earSide = :earSide AND hearingProfileId = :profileId ORDER BY lastUpdated DESC LIMIT 1")
    CalibrationProfileEntity getLatestProfileForEar(int userId, String earSide, int profileId);
    
    @Query("SELECT * FROM calibration_profiles WHERE userId = :userId AND earSide = :earSide AND hearingProfileId = :profileId ORDER BY lastUpdated DESC")
    List<CalibrationProfileEntity> getForEar(int userId, String earSide, int profileId);

    @Query("SELECT * FROM calibration_profiles WHERE id = :profileId")
    CalibrationProfileEntity getProfileById(int profileId);

    @Query("SELECT * FROM calibration_profiles WHERE userId = :userId AND hearingProfileId = :profileId ORDER BY lastUpdated DESC")
    List<CalibrationProfileEntity> getAllProfilesForUser(int userId, int profileId);

    @Query("SELECT AVG(mclDbSpl) FROM calibration_profiles WHERE userId = :userId AND hearingProfileId = :profileId")
    Float getAverageMCL(int userId, int profileId);

    @Query("SELECT AVG(uclDbSpl) FROM calibration_profiles WHERE userId = :userId AND hearingProfileId = :profileId")
    Float getAverageUCL(int userId, int profileId);

    @Query("SELECT AVG(dynamicRange) FROM calibration_profiles WHERE userId = :userId AND hearingProfileId = :profileId")
    Float getAverageDynamicRange(int userId, int profileId);

    @Query("DELETE FROM calibration_profiles WHERE userId = :userId AND hearingProfileId = :profileId")
    void deleteAllProfilesForUser(int userId, int profileId);

    @Query("DELETE FROM calibration_profiles WHERE userId = :userId AND earSide = :earSide AND hearingProfileId = :profileId")
    void deleteProfilesForEar(int userId, String earSide, int profileId);

    @Query("SELECT COUNT(*) FROM calibration_profiles WHERE userId = :userId AND hearingProfileId = :profileId")
    int getProfileCount(int userId, int profileId);

    @Query("SELECT COUNT(*) FROM calibration_profiles WHERE earSide = :earSide")
    int getProfileCountByEarSide(String earSide);

    @Query("SELECT COUNT(*) FROM calibration_profiles WHERE userId = :userId AND earSide = :earSide AND hearingProfileId = :profileId")
    int getProfileCountByUserAndEar(int userId, String earSide, int profileId);

    @Query("UPDATE calibration_profiles SET realEarGainJson = :gainJson, lastUpdated = :timestamp WHERE id = :profileId")
    void updateRealEarGain(int profileId, String gainJson, long timestamp);

    @Query("UPDATE calibration_profiles SET deviceCorrections = :corrections, lastUpdated = :timestamp WHERE id = :profileId")
    void updateDeviceCorrections(int profileId, String corrections, long timestamp);
}