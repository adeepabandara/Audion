package com.example.audion.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "hearing_test_results")
public class HearingTestResult {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int userId;
    private String earSide;
    private int frequency;
    private int amplitudeStep;

    public HearingTestResult(int userId, String earSide, int frequency, int amplitudeStep) {
        this.userId = userId;
        this.earSide = earSide;
        this.frequency = frequency;
        this.amplitudeStep = amplitudeStep;
    }

    // Getters & Setters
    public int getId() {
        return id;
    }
    public void setId(int id) { this.id = id; }

    public int getUserId() {
        return userId;
    }
    public void setUserId(int userId) { this.userId = userId; }

    public String getEarSide() {
        return earSide;
    }
    public void setEarSide(String earSide) { this.earSide = earSide; }

    public int getFrequency() {
        return frequency;
    }
    public void setFrequency(int frequency) { this.frequency = frequency; }

    public int getAmplitudeStep() {
        return amplitudeStep;
    }
    public void setAmplitudeStep(int amplitudeStep) { this.amplitudeStep = amplitudeStep; }
}
