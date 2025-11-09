# AUDION ANDROID APP - COMPLETE USER FLOW ANALYSIS

## Overview
The Audion app implements a comprehensive hearing assessment system with two main phases:
1. **Calibration Flow**: Device-specific calibration for personalized audio levels (MCL/UCL)
2. **Pure Tone Test Flow**: ANSI-compliant audiometry testing for hearing threshold detection

## 1️⃣ CALIBRATION FLOW

### Components Involved
- **CalibrationInstructionActivity**: Instruction screen with ear-specific setup
- **CalibrationTestActivity**: Core 3-frequency calibration logic (500Hz, 1000Hz, 2000Hz)
- **CalibrationProfile**: Audio processing calibration data
- **CalibrationProfileEntity**: Database persistence layer
- **CalibrationProfileDao**: Database access operations

### Detailed Flow Steps

#### Step A: Calibration Entry Point
```
NameActivity → CalibrationInstructionActivity
Intent extras: EAR="RIGHT", USER_ID, HEARING_PROFILE_ID
```

#### Step B: Calibration Instruction Screen
**CalibrationInstructionActivity.java**
- **Purpose**: Display ear-specific instruction and start calibration
- **UI Setup**: 
  - Dynamic title: "Left/Right Ear Calibration"
  - Ear-specific image: `R.drawable.left_ear` or `R.drawable.right_ear`
  - Description: "We'll play 3 standard levels for your [ear] calibration"
- **Navigation**: Click "Begin Calibration" → **CalibrationTestActivity**
- **Intent Data Passed**: `EAR`, `USER_ID`, `HEARING_PROFILE_ID`

#### Step C: Core Calibration Test
**CalibrationTestActivity.java**
- **Purpose**: Perform 3-frequency MCL (Most Comfortable Level) calibration
- **Initialization**:
  ```java
  earSide = getIntent().getStringExtra("EAR");  // "LEFT" or "RIGHT"
  freqs = {500, 1000, 2000};  // 3 test frequencies
  calibrationProfile = new CalibrationProfile(userId, hearingProfileId, "Calibration Profile");
  ```

- **Test Process**:
  1. **startStep()**: Cycles through 3 frequencies
  2. **startMCLMeasurement()**: For each frequency:
     - Start at 65.0 dB SPL
     - Display "Too Loud" / "Too Soft" buttons
     - Present 2-second sine wave tone
  3. **presentTone()**: Audio generation using `AudioTrack`
  4. **onUserResponse()**: User feedback processing
     - "Too Loud" → decrease level by 5 dB SPL
     - "Too Soft" → increase level by 5 dB SPL
     - Find stable MCL when level stabilizes around 65±20 dB SPL

- **Data Collection**:
  ```java
  mclValues.put(frequency, currentLevel);  // Store MCL for each frequency
  float estimatedUCL = currentLevel + 15.0f;  // UCL = MCL + 15dB
  uclValues.put(frequency, estimatedUCL);
  ```

#### Step D: Calibration Data Persistence
**saveCalibrationProfile()** method:
```java
// Calculate average MCL and UCL across 3 frequencies
float avgMCL = (MCL_500 + MCL_1000 + MCL_2000) / 3;
float avgUCL = (UCL_500 + UCL_1000 + UCL_2000) / 3;

// Save to database
CalibrationProfileEntity profileEntity = new CalibrationProfileEntity(
    userId, "Calibration Profile - " + earSide, earSide, 
    avgMCL, avgUCL, hearingProfileId
);
profileDao.insert(profileEntity);
```

#### Step E: Navigation Logic After Calibration
**navigateAfterCalibration()** method:
```java
// Check database for both ears
int rightEarProfiles = profileDao.getProfileCountByEarSide("RIGHT");
int leftEarProfiles = profileDao.getProfileCountByEarSide("LEFT");

if ("RIGHT".equals(earSide) && leftEarProfiles == 0) {
    // Just completed RIGHT → navigate to LEFT calibration
    Intent intent = new Intent(this, CalibrationInstructionActivity.class);
    intent.putExtra("EAR", "LEFT");
} else if ("LEFT".equals(earSide) && rightEarProfiles == 0) {
    // Just completed LEFT → navigate to RIGHT calibration
    Intent intent = new Intent(this, CalibrationInstructionActivity.class);
    intent.putExtra("EAR", "RIGHT");
} else {
    // Both ears calibrated → proceed to Pure Tone Test
    Intent intent = new Intent(this, RightEarInstructionActivity.class);
    intent.putExtra("CALIBRATION_COMPLETED", true);
}
```

### Calibration Data Structure
**CalibrationProfileEntity.java**:
```java
- id (auto-generated)
- userId, hearingProfileId (foreign keys)
- earSide: "LEFT" or "RIGHT"
- mclDbSpl: Most Comfortable Level in dB SPL
- uclDbSpl: Uncomfortable Level in dB SPL  
- dynamicRange: UCL - MCL
- realEarGainJson: Frequency-specific gain corrections
- createdTimestamp, lastUpdated
```

---

## 2️⃣ PURE TONE TEST FLOW

### Components Involved
- **RightEarInstructionActivity**: Entry point for pure tone testing
- **LeftEarInstructionActivity**: Left ear instruction screen
- **PureToneTestActivity**: Core ANSI-compliant audiometry engine
- **ANSI_AudiometryEngine**: Clinical-grade threshold detection
- **AudiometryResult**: Database entity for hearing thresholds
- **AudiometryResultDao**: Database access for test results

### Detailed Flow Steps

#### Step A: Pure Tone Test Entry
```
CalibrationTestActivity → RightEarInstructionActivity
Intent extras: USER_ID, HEARING_PROFILE_ID, CALIBRATION_COMPLETED=true
```

#### Step B: Right Ear Instruction
**RightEarInstructionActivity.java**
- **Purpose**: Prepare user for pure tone testing
- **Calibration Verification**:
  ```java
  verifyCalibrationCompleted() {
      boolean hasCalibrationProfile = calibrationProfileDao.getProfileCount(userId, hearingProfileId) > 0;
      if (!hasCalibrationProfile) {
          // BLOCK: Redirect to calibration
          Toast: "❌ Calibration required first!"
          Navigate to CalibrationInstructionActivity
      }
  }
  ```
- **Permission Check**: `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`
- **Navigation**: Click "Start Right Ear Test" → **PureToneTestActivity** with `EAR="RIGHT"`

#### Step C: Core Pure Tone Testing
**PureToneTestActivity.java**
- **Frequency Array**: `{1000, 2000, 3000, 4000, 8000, 1000, 500, 250}` Hz
- **ANSI Engine Initialization**:
  ```java
  audiometryEngine = new ANSI_AudiometryEngine();
  retsplTable = new RETSPLTable();  // Reference Equivalent Threshold SPL
  audiogramResults = new HashMap<Integer, Float>();
  ```

- **Test Process**:
  1. **startANSITestOrLegacy()**: Begin threshold detection
  2. **ANSI_AudiometryEngine.startThresholdTest()**: 
     - Uses Hughson-Westlake procedure
     - Adaptive threshold bracketing
     - Clinical validation with reversals
  3. **User Response Handling**:
     - "Can Hear" button → `audiometryEngine.processResponse(true)`
     - "Can't Hear" button → `audiometryEngine.processResponse(false)`
  4. **Threshold Detection**: Engine finds minimum audible level for each frequency

#### Step D: Pure Tone Data Persistence
**saveANSIResultAndAdvance()** method:
```java
// Create ANSI-compliant result
AudiometryResult ansiResult = new AudiometryResult(
    userId, currentEar, frequency,
    thresholdDbHL,      // Clinical threshold in dB HL
    thresholdDbSPL,     // SPL equivalent  
    isReliable,         // Test reliability flag
    reversalCount,      // Number of threshold reversals
    hearingProfileId
);
audiometryResultDao.insert(ansiResult);

// Also save legacy format for compatibility
HearingTestResult legacyResult = new HearingTestResult(
    userId, currentEar, frequency, lastAmplitudeStep, hearingProfileId
);
hearingTestResultDao.insert(legacyResult);
```

#### Step E: Ear-to-Ear Navigation
After RIGHT ear completion:
```java
if ("RIGHT".equals(currentEar)) {
    // Navigate to LEFT ear testing
    Intent leftEarIntent = new Intent(this, LeftEarInstructionActivity.class);
    leftEarIntent.putExtra("USER_ID", userId);
    leftEarIntent.putExtra("HEARING_PROFILE_ID", hearingProfileId);
} else {
    // Both ears complete → Results
    Intent resultsIntent = new Intent(this, TestResultsActivity.class);
}
```

#### Step F: Left Ear Testing
**LeftEarInstructionActivity.java**
- Same validation and permission logic as RIGHT ear
- **Navigation**: Click "Start Left Ear Test" → **PureToneTestActivity** with `EAR="LEFT"`

### Pure Tone Data Structure
**AudiometryResult.java**:
```java
- id (auto-generated)
- userId, hearingProfileId (foreign keys)
- earSide: "LEFT" or "RIGHT"
- frequency: Test frequency in Hz
- thresholdDbHL: Hearing Level threshold (clinical standard)
- thresholdDbSPL: Sound Pressure Level equivalent
- isReliable: ANSI test reliability flag
- reversalCount: Threshold detection accuracy metric
- testTimestamp: When test was performed
```

---

## 3️⃣ FLOW INTEGRATION & DATA FLOW

### Integration Points

#### A. Calibration → Pure Tone Validation
```java
// In RightEarInstructionActivity and LeftEarInstructionActivity
verifyCalibrationCompleted() {
    boolean hasCalibrationProfile = calibrationProfileDao.getProfileCount(userId, hearingProfileId) > 0;
    // BLOCKS pure tone test if calibration missing
}
```

#### B. HomeActivity Setup Enforcement
```java
checkAndEnforceHearingSetupOrder() {
    int rightEarProfiles = calibrationProfileDao.getProfileCountByEarSide("RIGHT");
    int leftEarProfiles = calibrationProfileDao.getProfileCountByEarSide("LEFT");
    boolean hasBothEarsCalibrated = (rightEarProfiles > 0) && (leftEarProfiles > 0);
    boolean hasAudiometryData = !audiometryDao.getAll().isEmpty();
    
    if (!hasBothEarsCalibrated) {
        // Force calibration
    } else if (!hasAudiometryData) {
        // Force pure tone test
    }
    // Allow HomeActivity only if both complete
}
```

### Complete User Journey

```
🚀 ONBOARDING FLOW:
OnboardingActivity → NameActivity

📊 CALIBRATION PHASE:
NameActivity → CalibrationInstructionActivity(RIGHT) → CalibrationTestActivity(RIGHT)
              ↓ [3-frequency MCL measurement, save to CalibrationProfileEntity]
CalibrationTestActivity(RIGHT) → CalibrationInstructionActivity(LEFT) → CalibrationTestActivity(LEFT)
              ↓ [3-frequency MCL measurement, save to CalibrationProfileEntity]

🎧 PURE TONE PHASE:
CalibrationTestActivity(LEFT) → RightEarInstructionActivity → PureToneTestActivity(RIGHT)
              ↓ [8-frequency ANSI testing, save to AudiometryResult]
PureToneTestActivity(RIGHT) → LeftEarInstructionActivity → PureToneTestActivity(LEFT)
              ↓ [8-frequency ANSI testing, save to AudiometryResult]

📋 RESULTS PHASE:
PureToneTestActivity(LEFT) → TestResultsActivity → HomeActivity
```

### Key Data Relationships
```
User 1→* HearingProfile 1→* CalibrationProfileEntity (per ear)
User 1→* HearingProfile 1→* AudiometryResult (per ear, per frequency)

Final Database State:
- CalibrationProfileEntity: 2 records (LEFT + RIGHT ears)
- AudiometryResult: 16 records (8 frequencies × 2 ears)
```

### Error Handling & Safety
- **Null Safety**: All earSide parameters validated with defaults
- **Permission Enforcement**: Audio permissions required before testing
- **Navigation Guards**: Back button prevention during critical flows
- **Database Integrity**: Foreign key constraints with CASCADE delete
- **Fallback Systems**: Legacy audio engine if ANSI fails

This comprehensive flow ensures clinical-grade hearing assessment with proper data persistence and user experience continuity.