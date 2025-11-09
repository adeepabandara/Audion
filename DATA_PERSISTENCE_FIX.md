# Data Persistence Fix - Hearing Profile Integration

## Issue Summary
**Problem**: User completes calibration test but sees "hearing profile incomplete" message. Audio plays with default (non-personalized) settings instead of using calibrated MCL/UCL values.

**Symptom Logs**:
```
PersonalizedGainMapper: W  No audiometry results found for WDRC settings - using defaults
PersonalizedGainMapper: W  No calibration data found - using conservative limiter defaults
```

## Root Cause Analysis

### Data Flow Mismatch
The application had a critical architectural inconsistency in data persistence:

1. **Calibration Test Storage** (CalibrationTestActivityRefactored.java):
   - Saves MCL/UCL data to `calibration_profiles` table
   - Uses `CalibrationProfileEntity` with per-frequency JSON data
   - Fields: `mclPerFrequencyJson`, `uclPerFrequencyJson`, `mclDbSpl`, `uclDbSpl`

2. **Personalization Loading** (PersonalizedGainMapper.java - BEFORE FIX):
   - Queried `audiometry_results` table via `audiometryDao.getResultsForEar()`
   - Expected `AudiometryResult` entities with `thresholdDbHL` values
   - **RESULT**: Empty results → fallback to default settings

### Table Schema Comparison

**calibration_profiles** (what test saves):
```sql
- id, userId, hearingProfileId
- earSide ("LEFT"/"RIGHT")
- mclDbSpl, uclDbSpl (average values)
- mclPerFrequencyJson ({"250": 65.0, "500": 70.0, ...})
- uclPerFrequencyJson ({"250": 95.0, "500": 100.0, ...})
- deviceCorrections, realEarGainJson
- createdTimestamp, lastUpdated
```

**audiometry_results** (what mapper tried to read):
```sql
- id, userId, hearingProfileId
- earSide, frequency
- thresholdDbHL (single frequency threshold)
- isReliable, testTimestamp
```

## Solution Implementation

### Changes to PersonalizedGainMapper.java

#### 1. Added JSON Parsing Support
```java
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Iterator;
```

#### 2. Created New Method: `generateWDRCSettingsFromCalibration()`
**Purpose**: Generate NAL-NL2 inspired WDRC settings from calibration MCL/UCL data

**Algorithm**:
```
For each frequency in mclPerFrequencyJson:
    1. Parse MCL value (dB SPL)
    2. Estimate hearing threshold: HTL = (MCL - 65.0) × 1.5
       - Assumes normal MCL ~65 dB SPL for conversational speech
       - MCL elevation indicates threshold elevation
    3. Calculate NAL-NL2 gain: Gain = 0.31 × (HTL - 20) × speechWeight × inputLevelFactor
    4. Store frequency-specific gain
```

**Compression Parameters**:
- Estimated HTL > 60 dB: CR=4:1, Kneepoint=-25dB (severe loss)
- Estimated HTL 40-60 dB: CR=3:1, Kneepoint=-30dB (moderate loss)
- Estimated HTL < 40 dB: CR=2:1, Kneepoint=-35dB (mild loss)

#### 3. Modified Primary Method: `generateWDRCSettings()`
**Priority Cascade**:
1. **PRIORITY 1**: Try `generateWDRCSettingsFromCalibration()` first
2. **PRIORITY 2**: Fall back to audiometry results (if available)
3. **PRIORITY 3**: Return default conservative settings

```java
public WDRCSettings generateWDRCSettings(int userId, String ear, int hearingProfileId) {
    // PRIORITY 1: Try calibration data first
    WDRCSettings calibrationSettings = generateWDRCSettingsFromCalibration(userId, ear, hearingProfileId);
    if (calibrationSettings != null) {
        Log.i(TAG, "Using calibration-based WDRC settings for " + ear + " ear");
        return calibrationSettings;
    }
    
    // PRIORITY 2: Fall back to audiometry results
    List<AudiometryResult> results = audiometryDao.getResultsForEar(userId, ear, hearingProfileId);
    if (results.isEmpty()) {
        Log.w(TAG, "No audiometry results found - using defaults");
        return new WDRCSettings();
    }
    
    // ... existing audiometry-based calculation ...
}
```

## Technical Details

### MCL to Hearing Threshold Conversion
**Clinical Basis**:
- Normal hearing: MCL ~65 dB SPL (comfortable conversational speech)
- Hearing loss: MCL elevation correlates with threshold elevation
- Scaling factor 1.5x accounts for recruitment phenomenon

**Formula**:
```java
double normalMCL = 65.0;
double estimatedHTL = Math.max(0, (mclDbSpl - normalMCL) * 1.5);
```

**Example**:
- MCL = 75 dB SPL → Estimated HTL = (75 - 65) × 1.5 = 15 dB HL (mild loss)
- MCL = 85 dB SPL → Estimated HTL = (85 - 65) × 1.5 = 30 dB HL (moderate loss)
- MCL = 95 dB SPL → Estimated HTL = (95 - 65) × 1.5 = 45 dB HL (moderately severe loss)

### NAL-NL2 Gain Calculation
**Per-Frequency Gains**:
```
Base Gain = 0.31 × (Estimated HTL - 20.0)
Speech Weight = {250Hz: 0.1, 500Hz: 0.2, 1kHz: 0.4, 2kHz: 0.5, 4kHz: 0.4, 8kHz: 0.2}
Input Level Factor = calculateInputLevelFactor(65dB SPL, HTL)
Final Gain = Base Gain × Speech Weight × Input Level Factor
```

**Input-Level Dependency** (NAL-NL2 Enhancement):
- Soft sounds (50 dB SPL): Factor = 1.2 (more gain)
- Medium sounds (65 dB SPL): Factor = 1.0 (reference)
- Loud sounds (80 dB SPL): Factor = 0.6 (less gain)

### Limiter Settings Integration
**Already Working**: `generateLimiterSettings()` already uses calibration data correctly:
```java
CalibrationProfileEntity calibration = calibrationDao.getLatestProfileForEar(userId, ear, hearingProfileId);
float personalizedMPO = calibration.getUclDbSpl();
float safeMPO = personalizedMPO - safetyMargin;
settings.setPersonalizedMPO(safeMPO);
```

## Expected Behavior After Fix

### Log Output (Success Case)
```
PersonalizedGainMapper: D  Calibration WDRC: 250Hz MCL=68.5dB SPL -> Estimated HTL=5.3dB HL -> Gain=0.5dB
PersonalizedGainMapper: D  Calibration WDRC: 500Hz MCL=72.0dB SPL -> Estimated HTL=10.5dB HL -> Gain=1.8dB
PersonalizedGainMapper: D  Calibration WDRC: 1000Hz MCL=75.5dB SPL -> Estimated HTL=15.8dB HL -> Gain=3.2dB
PersonalizedGainMapper: D  Calibration WDRC: 2000Hz MCL=80.0dB SPL -> Estimated HTL=22.5dB HL -> Gain=4.5dB
PersonalizedGainMapper: I  WDRC from calibration: LEFT ear, avgMCL=74.0dB SPL, est.HTL=13.5dB HL, CR=2.0:1, 6 frequencies
PersonalizedGainMapper: I  Using calibration-based WDRC settings for LEFT ear
```

### User Experience
1. **Complete Calibration Test**: User adjusts MCL/UCL sliders for each frequency
2. **Data Saved**: CalibrationProfileEntity stored with per-frequency JSON
3. **Navigate to Home**: "Go to Home" button → HomeActivity
4. **Audio Initialization**: AudioStreamingService loads personalization
5. **Personalization Applied**: generateWDRCSettingsFromCalibration() succeeds
6. **Clear Audio Output**: User hears personalized, comfortable amplification
7. **Profile Status**: "Hearing profile complete" indicator shows green

## Testing Checklist

### Pre-Test Setup
- [ ] Uninstall previous APK version
- [ ] Clear app data: `adb shell pm clear com.example.audion`
- [ ] Install new APK: `adb install -r app-debug.apk`

### Test Procedure
1. **Complete Calibration Test**:
   - Select LEFT ear
   - Set MCL values at all frequencies (250Hz - 8kHz)
   - Set UCL values at all frequencies
   - Observe "Calibration saved" log message

2. **Navigate to Home**:
   - Click "Go to Home" button
   - Verify navigation to HomeActivity (not splash screen)
   - Check profile status indicator

3. **Start Audio Streaming**:
   - Click "Play" button
   - Enable logcat filtering: `adb logcat | grep PersonalizedGainMapper`
   - Verify logs show "Using calibration-based WDRC settings"

4. **Verify Audio Quality**:
   - Audio should be clear and continuous (no choppiness)
   - Speech should be comfortable and intelligible
   - No distortion or excessive loudness
   - No "incomplete profile" warning

### Success Criteria
✅ No "No audiometry results found" warnings  
✅ Logs show "WDRC from calibration" messages  
✅ Per-frequency gains calculated and logged  
✅ Compression ratio set based on estimated hearing loss  
✅ Audio output is clear and personalized  
✅ Profile status shows "complete"

## Build Information
- **Modified File**: `PersonalizedGainMapper.java`
- **Build Command**: `gradlew assembleDebug`
- **Build Result**: ✅ BUILD SUCCESSFUL in 7s
- **Installation**: ✅ Success
- **APK Location**: `app/build/outputs/apk/debug/app-debug.apk`

## Backward Compatibility
- ✅ Existing audiometry-based workflow still supported (Priority 2)
- ✅ Default settings fallback intact (Priority 3)
- ✅ No breaking changes to database schema
- ✅ Limiter settings already using calibration correctly

## Related Files
- **Primary**: `PersonalizedGainMapper.java` (Lines 1-516)
- **Data Model**: `CalibrationProfileEntity.java` (calibration_profiles table)
- **DAO**: `CalibrationProfileDao.java` (getLatestProfileForEar method)
- **Test Activity**: `CalibrationTestActivityRefactored.java` (saveCalibrationData)
- **Service**: `AudioStreamingService.java` (loadPersonalizationDataAsync)

## References
- **Phase 5 Fix**: Database threading (background execution)
- **Phase 4 Fix**: Audio streaming buffer management
- **Phase 3 Fix**: Navigation flow (TestResultsActivity → HomeActivity)
- **Phase 2 Enhancement**: NAL-NL2 input-level compensation
- **Phase 1 Foundation**: Stereo processing, filterbank, SPL monitoring
