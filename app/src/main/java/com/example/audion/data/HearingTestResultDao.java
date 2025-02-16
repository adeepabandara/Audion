package com.example.audion.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HearingTestResultDao {
    @Insert
    void insert(HearingTestResult result);

    @Query("SELECT * FROM hearing_test_results")
    List<HearingTestResult> getAllResults();

    @Query("SELECT * FROM hearing_test_results WHERE userId = :userId")
    List<HearingTestResult> getResultsForUser(int userId);

    @Query("SELECT * FROM hearing_test_results WHERE earSide = :earSide")
    List<HearingTestResult> getResultsForEar(String earSide);

    @Query("DELETE FROM hearing_test_results")
    void deleteAll();
}
