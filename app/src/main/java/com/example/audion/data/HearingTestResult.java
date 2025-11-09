package com.example.audion.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
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
    indices = {
        @Index(value = "hearingProfileId"),
        @Index(value = {"userId", "earSide", "frequency", "hearingProfileId"}, unique = true)
    }
)
public class HearingTestResult {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int userId;
    private String earSide;      // e.g. "LEFT" or "RIGHT"
    private int frequency;       // e.g. 125, 250, 500, etc.
    
    // LEGACY FIELD - kept for backward compatibility
    private int amplitudeStep;   // e.g. some integer value (1-100)
    
    // NEW CLINICAL FIELDS
    private float thresholdDbHL;    // Clinical threshold in dB HL (ANSI S3.6)
    private float thresholdDbSPL;   // Device-specific threshold in dB SPL
    private boolean isReliable;     // Test reliability based on reversals
    private int reversalCount;      // Number of threshold reversals
    private float reliabilityScore; // 0.0-1.0 based on consistency
    private long testTimestamp;     // When test was performed
    
    private int hearingProfileId;

    // Legacy constructor for backward compatibility
    @Ignore
    public HearingTestResult(int userId, String earSide, int frequency, int amplitudeStep, int hearingProfileId) {
        this.userId = userId;
        this.earSide = earSide;
        this.frequency = frequency;
        this.amplitudeStep = amplitudeStep;
        this.hearingProfileId = hearingProfileId;
        this.testTimestamp = System.currentTimeMillis();
        this.isReliable = false; // Default to unreliable for legacy data
        this.reversalCount = 0;
        this.reliabilityScore = 0.0f;
    }
    
    // New clinical constructor with dB HL threshold
    public HearingTestResult(int userId, String earSide, int frequency, 
                           float thresholdDbHL, float thresholdDbSPL, 
                           boolean isReliable, int reversalCount, 
                           float reliabilityScore, int hearingProfileId) {
        this.userId = userId;
        this.earSide = earSide;
        this.frequency = frequency;
        this.thresholdDbHL = thresholdDbHL;
        this.thresholdDbSPL = thresholdDbSPL;
        this.isReliable = isReliable;
        this.reversalCount = reversalCount;
        this.reliabilityScore = reliabilityScore;
        this.hearingProfileId = hearingProfileId;
        this.testTimestamp = System.currentTimeMillis();
        this.amplitudeStep = 0; // Not used in new implementation
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
    
    // New clinical field getters and setters
    public float getThresholdDbHL() {
        return thresholdDbHL;
    }
    public void setThresholdDbHL(float thresholdDbHL) {
        this.thresholdDbHL = thresholdDbHL;
    }
    
    public float getThresholdDbSPL() {
        return thresholdDbSPL;
    }
    public void setThresholdDbSPL(float thresholdDbSPL) {
        this.thresholdDbSPL = thresholdDbSPL;
    }
    
    public boolean isReliable() {
        return isReliable;
    }
    public void setReliable(boolean reliable) {
        isReliable = reliable;
    }
    
    public int getReversalCount() {
        return reversalCount;
    }
    public void setReversalCount(int reversalCount) {
        this.reversalCount = reversalCount;
    }
    
    public float getReliabilityScore() {
        return reliabilityScore;
    }
    public void setReliabilityScore(float reliabilityScore) {
        this.reliabilityScore = reliabilityScore;
    }
    
    public long getTestTimestamp() {
        return testTimestamp;
    }
    public void setTestTimestamp(long testTimestamp) {
        this.testTimestamp = testTimestamp;
    }
}
