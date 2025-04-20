package com.example.audion.data;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.List;

public class HearingProfileWithResults {

    @Embedded
    public HearingProfile hearingProfile;

    @Relation(
         parentColumn = "id",
         entityColumn = "hearingProfileId"
    )
    public List<HearingTestResult> hearingTestResults;
}
