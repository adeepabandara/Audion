package com.audion.app.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(
    tableName = "calibration_profiles",
    foreignKeys = @ForeignKey(
        entity = HearingProfile.class,
        parentColumns = "id",
        childColumns = "hearingProfileId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "hearingProfileId")}
)
public class CalibrationProfileEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int userId;
    private String profileName;
    private String earSide;          // "LEFT" or "RIGHT"
    
    // MCL and UCL values (in dB SPL) - averaged for backward compatibility
    private float mclDbSpl;
    private float uclDbSpl;
    private float dynamicRange;      // UCL - MCL
    
    // Per-frequency MCL and UCL data (JSON format: {"500": 65.0, "1000": 70.0, ...})
    private String mclPerFrequencyJson;  // JSON string of frequency->MCL mapping
    private String uclPerFrequencyJson;  // JSON string of frequency->UCL mapping
    
    // Real ear gain calculations
    private String realEarGainJson;  // JSON string of frequency->gain mapping
    private String deviceCorrections; // JSON string of device-specific corrections
    
    // Metadata
    private long createdTimestamp;
    private long lastUpdated;
    private int hearingProfileId;

    public CalibrationProfileEntity(int userId, String profileName, String earSide, 
                                  float mclDbSpl, float uclDbSpl, int hearingProfileId) {
        this.userId = userId;
        this.profileName = profileName;
        this.earSide = earSide;
        this.mclDbSpl = mclDbSpl;
        this.uclDbSpl = uclDbSpl;
        this.dynamicRange = uclDbSpl - mclDbSpl;
        this.hearingProfileId = hearingProfileId;
        this.createdTimestamp = System.currentTimeMillis();
        this.lastUpdated = this.createdTimestamp;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public String getEarSide() { return earSide; }
    public void setEarSide(String earSide) { this.earSide = earSide; }

    public float getMclDbSpl() { return mclDbSpl; }
    public void setMclDbSpl(float mclDbSpl) { 
        this.mclDbSpl = mclDbSpl; 
        updateDynamicRange();
    }

    public float getUclDbSpl() { return uclDbSpl; }
    public void setUclDbSpl(float uclDbSpl) { 
        this.uclDbSpl = uclDbSpl; 
        updateDynamicRange();
    }

    public float getDynamicRange() { return dynamicRange; }
    public void setDynamicRange(float dynamicRange) { this.dynamicRange = dynamicRange; }

    public String getRealEarGainJson() { return realEarGainJson; }
    public void setRealEarGainJson(String realEarGainJson) { this.realEarGainJson = realEarGainJson; }

    public String getDeviceCorrections() { return deviceCorrections; }
    public void setDeviceCorrections(String deviceCorrections) { this.deviceCorrections = deviceCorrections; }

    public long getCreatedTimestamp() { return createdTimestamp; }
    public void setCreatedTimestamp(long createdTimestamp) { this.createdTimestamp = createdTimestamp; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public int getHearingProfileId() { return hearingProfileId; }
    public void setHearingProfileId(int hearingProfileId) { this.hearingProfileId = hearingProfileId; }

    public String getMclPerFrequencyJson() { return mclPerFrequencyJson; }
    public void setMclPerFrequencyJson(String mclPerFrequencyJson) { 
        this.mclPerFrequencyJson = mclPerFrequencyJson;
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getUclPerFrequencyJson() { return uclPerFrequencyJson; }
    public void setUclPerFrequencyJson(String uclPerFrequencyJson) { 
        this.uclPerFrequencyJson = uclPerFrequencyJson;
        this.lastUpdated = System.currentTimeMillis();
    }

    private void updateDynamicRange() {
        this.dynamicRange = this.uclDbSpl - this.mclDbSpl;
        this.lastUpdated = System.currentTimeMillis();
    }
}