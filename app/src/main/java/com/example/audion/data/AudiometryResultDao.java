package com.example.audion.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AudiometryResultDao {

    @Insert
    long insert(AudiometryResult result);

    @Update
    void update(AudiometryResult result);

    @Delete
    void delete(AudiometryResult result);

    @Query("SELECT * FROM audiometry_results WHERE userId = :userId AND hearingProfileId = :profileId")
    List<AudiometryResult> getResultsForUserAndProfile(int userId, int profileId);

    @Query("SELECT * FROM audiometry_results WHERE userId = :userId AND earSide = :earSide AND hearingProfileId = :profileId")
    List<AudiometryResult> getResultsForEar(int userId, String earSide, int profileId);

    @Query("SELECT * FROM audiometry_results WHERE userId = :userId AND earSide = :earSide AND frequency = :frequency AND hearingProfileId = :profileId ORDER BY testTimestamp DESC LIMIT 1")
    AudiometryResult getLatestResultForFrequency(int userId, String earSide, int frequency, int profileId);

    @Query("SELECT * FROM audiometry_results WHERE userId = :userId AND hearingProfileId = :profileId ORDER BY testTimestamp DESC")
    List<AudiometryResult> getAllResultsForUser(int userId, int profileId);

    @Query("SELECT DISTINCT frequency FROM audiometry_results WHERE userId = :userId AND earSide = :earSide AND hearingProfileId = :profileId ORDER BY frequency")
    List<Integer> getTestedFrequencies(int userId, String earSide, int profileId);

    @Query("SELECT COUNT(*) FROM audiometry_results WHERE userId = :userId AND earSide = :earSide AND hearingProfileId = :profileId")
    int getResultCountForEar(int userId, String earSide, int profileId);

    @Query("SELECT AVG(thresholdDbHL) FROM audiometry_results WHERE userId = :userId AND earSide = :earSide AND frequency IN (500, 1000, 2000) AND hearingProfileId = :profileId")
    Float getPTA3(int userId, String earSide, int profileId);

    @Query("SELECT AVG(thresholdDbHL) FROM audiometry_results WHERE userId = :userId AND earSide = :earSide AND frequency IN (500, 1000, 2000, 4000) AND hearingProfileId = :profileId")
    Float getPTA4(int userId, String earSide, int profileId);

    @Query("DELETE FROM audiometry_results WHERE userId = :userId AND hearingProfileId = :profileId")
    void deleteAllResultsForUser(int userId, int profileId);

    @Query("DELETE FROM audiometry_results WHERE userId = :userId AND earSide = :earSide AND hearingProfileId = :profileId")
    void deleteResultsForEar(int userId, String earSide, int profileId);

    @Query("SELECT * FROM audiometry_results WHERE isReliable = 1 AND userId = :userId AND hearingProfileId = :profileId")
    List<AudiometryResult> getReliableResults(int userId, int profileId);

    @Query("SELECT * FROM audiometry_results ORDER BY testTimestamp DESC")
    List<AudiometryResult> getAll();
}