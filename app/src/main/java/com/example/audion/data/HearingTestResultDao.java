package com.example.audion.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object for hearing_test_results table.
 */
@Dao
public interface HearingTestResultDao {

    @Insert
    void insert(HearingTestResult result);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(HearingTestResult result);

    @Update
    void update(HearingTestResult result);

    @Query("SELECT * FROM hearing_test_results")
    List<HearingTestResult> getAllResults();

    @Query("SELECT * FROM hearing_test_results WHERE userId = :userId")
    List<HearingTestResult> getResultsForUser(int userId);

    // 1-param version (if needed)
    @Query("SELECT * FROM hearing_test_results WHERE earSide = :earSide")
    List<HearingTestResult> getResultsForEar(String earSide);

    // 2-param version: for given ear and user
    @Query("SELECT * FROM hearing_test_results WHERE earSide = :earSide AND userId = :userId")
    List<HearingTestResult> getResultsForEar(String earSide, int userId);

    // Look up exactly one record by user, ear, and frequency
    @Query("SELECT * FROM hearing_test_results WHERE userId = :userId AND earSide = :earSide AND frequency = :frequency LIMIT 1")
    HearingTestResult findUserEarFrequency(int userId, String earSide, int frequency);

    // Look up exactly one record by user, ear, frequency, AND profile
    @Query("SELECT * FROM hearing_test_results WHERE userId = :userId AND earSide = :earSide AND frequency = :frequency AND hearingProfileId = :profileId LIMIT 1")
    HearingTestResult findUserEarFrequencyForProfile(int userId, String earSide, int frequency, int profileId);

    @Query("DELETE FROM hearing_test_results WHERE userId = :userId")
    void deleteResultsForUser(int userId);

    @Query("DELETE FROM hearing_test_results")
    void deleteAll();


    @Query("SELECT * FROM hearing_test_results WHERE userId = :userId AND hearingProfileId = :profileId")
    List<HearingTestResult> getResultsForUserAndProfile(int userId, int profileId);

    @Query("SELECT * FROM hearing_test_results WHERE userId = :userId AND hearingProfileId = :profileId")
    List<HearingTestResult> getResultsByUserAndProfile(int userId, int profileId);
}
