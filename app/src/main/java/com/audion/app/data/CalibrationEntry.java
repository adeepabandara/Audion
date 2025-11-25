package com.audion.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "calibration_entries")
public class CalibrationEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int userId;
    public String earSide;
    public int value;          // holds the baseline step
    public int profileId;

    public CalibrationEntry(int userId, String earSide, int value, int profileId) {
        this.userId  = userId;
        this.earSide = earSide;
        this.value   = value;
        this.profileId = profileId;
    }

    public String getEarSide() {
        return earSide;
    }
    public int getBaselineStep() {
        return value;          // return the `value` field
    }
}

