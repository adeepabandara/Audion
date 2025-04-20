package com.example.audion.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public interface HearingProfileDao {

    @Insert
    long insert(HearingProfile profile);

    @Query("SELECT * FROM hearing_profiles WHERE id = :id")
    HearingProfile getHearingProfileById(int id);

    // Fetch all hearing profiles.
    @Query("SELECT * FROM hearing_profiles")
    List<HearingProfile> getAllProfiles();

    // Example: get a profile along with its associated hearing test results.
    @Transaction
    @Query("SELECT * FROM hearing_profiles WHERE id = :id")
    HearingProfileWithResults getProfileWithResults(int id);
}
