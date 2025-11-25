package com.audion.app.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "audiometry_results",
    foreignKeys = @ForeignKey(
        entity = HearingProfile.class,
        parentColumns = "id",
        childColumns = "hearingProfileId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "hearingProfileId")}
)
public class AudiometryResult {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int userId;
    private String earSide;          // "LEFT" or "RIGHT"
    private int frequency;           // Hz (250, 500, 1000, etc.)
    private float thresholdDbHL;     // dB HL threshold value
    private float thresholdDbSPL;    // dB SPL equivalent
    private boolean isReliable;      // Test reliability flag
    private int reversalCount;       // Number of reversals for threshold
    private long testTimestamp;      // When test was performed
    private int hearingProfileId;

    public AudiometryResult(int userId, String earSide, int frequency, 
                           float thresholdDbHL, float thresholdDbSPL, 
                           boolean isReliable, int reversalCount, int hearingProfileId) {
        this.userId = userId;
        this.earSide = earSide;
        this.frequency = frequency;
        this.thresholdDbHL = thresholdDbHL;
        this.thresholdDbSPL = thresholdDbSPL;
        this.isReliable = isReliable;
        this.reversalCount = reversalCount;
        this.hearingProfileId = hearingProfileId;
        this.testTimestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getEarSide() { return earSide; }
    public void setEarSide(String earSide) { this.earSide = earSide; }

    public int getFrequency() { return frequency; }
    public void setFrequency(int frequency) { this.frequency = frequency; }

    public float getThresholdDbHL() { return thresholdDbHL; }
    public void setThresholdDbHL(float thresholdDbHL) { this.thresholdDbHL = thresholdDbHL; }

    public float getThresholdDbSPL() { return thresholdDbSPL; }
    public void setThresholdDbSPL(float thresholdDbSPL) { this.thresholdDbSPL = thresholdDbSPL; }

    public boolean isReliable() { return isReliable; }
    public void setReliable(boolean reliable) { isReliable = reliable; }

    public int getReversalCount() { return reversalCount; }
    public void setReversalCount(int reversalCount) { this.reversalCount = reversalCount; }

    public long getTestTimestamp() { return testTimestamp; }
    public void setTestTimestamp(long testTimestamp) { this.testTimestamp = testTimestamp; }

    public int getHearingProfileId() { return hearingProfileId; }
    public void setHearingProfileId(int hearingProfileId) { this.hearingProfileId = hearingProfileId; }
}