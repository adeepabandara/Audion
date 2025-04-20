package com.example.audion.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "hearing_test_results",
    foreignKeys = @ForeignKey(
        entity = HearingProfile.class,
        parentColumns = "id",
        childColumns = "hearingProfileId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "hearingProfileId")}
)
public class HearingTestResult {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int userId;
    private String earSide;      // e.g. "LEFT" or "RIGHT"
    private int frequency;       // e.g. 125, 250, 500, etc.
    private int amplitudeStep;   // e.g. some integer value
    private int hearingProfileId;

    public HearingTestResult(int userId, String earSide, int frequency, int amplitudeStep, int hearingProfileId) {
        this.userId = userId;
        this.earSide = earSide;
        this.frequency = frequency;
        this.amplitudeStep = amplitudeStep;
        this.hearingProfileId = hearingProfileId;
    }

    // Getters & Setters
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public int getUserId() {
        return userId;
    }
    public void setUserId(int userId) {
        this.userId = userId;
    }
    public String getEarSide() {
        return earSide;
    }
    public void setEarSide(String earSide) {
        this.earSide = earSide;
    }
    public int getFrequency() {
        return frequency;
    }
    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }
    public int getAmplitudeStep() {
        return amplitudeStep;
    }
    public void setAmplitudeStep(int amplitudeStep) {
        this.amplitudeStep = amplitudeStep;
    }
    public int getHearingProfileId() {
        return hearingProfileId;
    }
    public void setHearingProfileId(int hearingProfileId) {
        this.hearingProfileId = hearingProfileId;
    }
}
