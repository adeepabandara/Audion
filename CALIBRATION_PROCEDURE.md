# Device Calibration Procedure for Audion Hearing Personalization App

**Document Version**: 1.0  
**Last Updated**: 2024  
**Purpose**: Clinical-grade SPL calibration for hearing safety and accurate audiometry

---

## Overview

This document describes the calibration procedure to establish accurate dBFS ↔ dB SPL conversion factors for the Audion app. Proper calibration ensures:

1. **Hearing Safety**: Output levels stay below personalized UCL limits
2. **Clinical Accuracy**: Audiometry measurements are reliable and reproducible
3. **Gain Prescription**: NAL-NL2 inspired gains are correctly applied
4. **Regulatory Compliance**: Meets IEC 60645-1 (audiometry) and IEC 60118-7 (hearing aids) standards

**CRITICAL**: Calibration must be performed for each unique combination of:
- Device model (e.g., Pixel 6, Samsung S21, iPhone 13)
- Output transducer (headphone model, speaker, bone conductor)
- Android version (audio stack differences)

---

## 1. Required Equipment

### 1.1 Calibrated Reference Microphone
- **Required**: Type 1 measurement microphone per IEC 61672-1
- **Recommended Models**:
  - Brüel & Kjær Type 4192 (reference standard)
  - GRAS 40AE/40AF (½" free-field mic)
  - Earthworks M30 (precision measurement)
- **Specifications**:
  - Frequency response: ±1 dB from 250 Hz to 8 kHz
  - Sensitivity calibration: ±0.5 dB traceable to national standards
  - Self-noise: <20 dB(A) SPL
- **Calibration**: Valid calibration certificate less than 12 months old

### 1.2 Ear Simulator (Acoustic Coupler)
- **Required**: IEC 60318-4 (2mm coupler, formerly IEC 711)
- **Recommended Models**:
  - Brüel & Kjær Type 4157 (occluded ear simulator)
  - GRAS RA0045 (IEC 60318-4 compliant)
  - G.R.A.S. IEC 60318-4 ear simulator
- **Purpose**: Simulates average adult ear canal acoustics for in-ear measurements
- **Alternative**: For headphone calibration, use flat-plate coupler (IEC 60318-1)

### 1.3 Sound Level Meter (SLM)
- **Required**: Class 1 SLM per IEC 61672-1
- **Recommended Models**:
  - Brüel & Kjær 2250/2270
  - Larson Davis 831
  - NTi Audio XL2
- **Settings**:
  - Weighting: C-weighting for pure tones, A-weighting for speech-weighted noise
  - Time weighting: SLOW (1 second integration)
  - Reference: 20 μPa (94 dB SPL = 1 Pa)

### 1.4 Test Device Configuration
- **Test Signal**: Pure tone generator with ≥16-bit resolution
- **Sample Rate**: 48 kHz (must match AudioConfig.SAMPLE_RATE)
- **Bit Depth**: 16-bit signed PCM minimum (24-bit preferred)
- **Audio Output**: System audio routing, no DSP processing
- **Volume Control**: Device volume at 100% (no attenuation)

### 1.5 Environmental Conditions
- **Acoustic Environment**: 
  - Quiet room or sound booth (<40 dB(A) background noise)
  - Minimal reflections (use anechoic chamber if available)
- **Temperature**: 20-25°C (68-77°F)
- **Humidity**: 30-70% RH (non-condensing)
- **Electromagnetic Interference**: Minimal (no nearby motors, transformers)

---

## 2. Pre-Calibration Setup

### 2.1 Device Preparation

1. **Factory Reset Audio Settings**:
   ```bash
   # Clear all audio modifications (requires root or ADB)
   adb shell settings delete global audio_effects_config
   adb shell settings delete global audio_safe_volume_state
   ```

2. **Disable System Audio Processing**:
   - Settings → Sound → Audio Effects → OFF
   - Settings → Sound → Dolby Atmos → OFF
   - Settings → Accessibility → Hearing Enhancements → OFF
   - Developer Options → Disable Absolute Volume → ON (for Bluetooth)

3. **Install Calibration Test App**:
   ```bash
   # Build app in debug mode with calibration test tones enabled
   .\gradlew assembleDebug -PenableCalibrationMode=true
   adb install -r app-debug.apk
   ```

4. **Configure Test Signal Source**:
   - Use `AudioTrack` with `STREAM_MUSIC` or `STREAM_VOICE_CALL`
   - Sample format: `ENCODING_PCM_16BIT` or `ENCODING_PCM_FLOAT`
   - Channel configuration: `CHANNEL_OUT_STEREO`
   - Buffer size: Minimum latency mode disabled (use stable mode)

### 2.2 Microphone Setup

1. **Calibrate Microphone**:
   - Apply calibrator (94 dB or 114 dB reference)
   - Verify SLM reads within ±0.5 dB of reference
   - Record calibration correction factor if needed

2. **Position Microphone in Ear Simulator**:
   - Insert reference microphone into IEC 60318-4 coupler
   - Ensure airtight seal (check for leaks with low-frequency tone)
   - Microphone diaphragm at eardrum reference point (DRP)

3. **Connect Transducer to Ear Simulator**:
   - For in-ear headphones: Insert into coupler with same insertion depth as human ear
   - For over-ear headphones: Use flat-plate coupler with defined force (~10 N)
   - For speakers: Position on-axis at 1 meter distance (free-field measurement)

### 2.3 Signal Generation Configuration

Create test tones in `AudioEngine.java` (calibration mode only):

```java
// CalibrationToneGenerator.java
public class CalibrationToneGenerator {
    private static final int SAMPLE_RATE = 48000;
    
    /**
     * Generate 1 kHz pure tone at 0 dBFS (maximum amplitude)
     * Duration: 5 seconds for stable measurement
     */
    public static short[] generate1kHzTone_0dBFS() {
        int numSamples = SAMPLE_RATE * 5; // 5 seconds
        short[] samples = new short[numSamples * 2]; // Stereo
        
        double frequency = 1000.0; // 1 kHz
        double amplitude = 32767.0; // 0 dBFS = max short value
        
        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * frequency * i / SAMPLE_RATE;
            short value = (short) (amplitude * Math.sin(angle));
            samples[i * 2] = value;     // Left channel
            samples[i * 2 + 1] = value; // Right channel
        }
        
        return samples;
    }
    
    /**
     * Generate speech-weighted noise at 0 dBFS
     * Per ANSI S3.6 for audiometry calibration
     */
    public static short[] generateSpeechWeightedNoise_0dBFS() {
        // Implementation: Pink noise + speech weighting filter
        // See ANSI S3.6 Section 6.4 for exact filter coefficients
        // ... (implementation details omitted for brevity)
    }
}
```

---

## 3. Calibration Measurement Protocol

### 3.1 Pure Tone Calibration (1 kHz Reference)

This is the primary calibration measurement. The 1 kHz tone is used because:
- Microphones have flattest response at mid-frequencies
- Hearing is most sensitive around 1 kHz
- Standard reference for audiometry (ANSI S3.6, ISO 389-1)

**Procedure**:

1. **Generate Test Tone**:
   ```java
   // In AudioStreamingService.java or test harness
   CalibrationToneGenerator generator = new CalibrationToneGenerator();
   short[] testTone = generator.generate1kHzTone_0dBFS();
   
   // Play through AudioTrack (bypass all DSP processing)
   AudioTrack track = new AudioTrack.Builder()
       .setAudioFormat(new AudioFormat.Builder()
           .setSampleRate(48000)
           .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
           .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
           .build())
       .setBufferSizeInBytes(testTone.length * 2)
       .setTransferMode(AudioTrack.MODE_STATIC)
       .build();
   
   track.write(testTone, 0, testTone.length);
   track.setLoopPoints(0, testTone.length / 2, -1); // Loop indefinitely
   track.play();
   ```

2. **Measure Output SPL**:
   - Start SLM measurement (SLOW, C-weighting)
   - Wait 3 seconds for stabilization
   - Record 10-second average SPL reading
   - Repeat measurement 3 times, use median value

3. **Calculate DBFS_TO_DB_SPL Constant**:
   ```
   DBFS_TO_DB_SPL = MeasuredSPL - DigitalLevel
   
   Where:
   - MeasuredSPL = SPL reading from sound level meter (dB SPL)
   - DigitalLevel = 0 dBFS (maximum digital amplitude)
   
   Example:
   If SLM reads 112.3 dB SPL when playing 0 dBFS tone:
   DBFS_TO_DB_SPL_LEFT = 112.3 - 0.0 = 112.3 dB
   ```

4. **Document Results**:
   ```
   Device: Pixel 6 Pro
   Android Version: 13
   Transducer: Sony WH-1000XM4 (wired, ANC OFF)
   Date: 2024-12-15
   Operator: J. Smith
   
   LEFT CHANNEL:
   - Trial 1: 112.1 dB SPL
   - Trial 2: 112.3 dB SPL
   - Trial 3: 112.4 dB SPL
   - Median: 112.3 dB SPL
   - DBFS_TO_DB_SPL_LEFT = 112.3
   
   RIGHT CHANNEL:
   - Trial 1: 111.8 dB SPL
   - Trial 2: 112.0 dB SPL
   - Trial 3: 112.1 dB SPL
   - Median: 112.0 dB SPL
   - DBFS_TO_DB_SPL_RIGHT = 112.0
   ```

### 3.2 Multi-Frequency Verification (Optional but Recommended)

Verify calibration accuracy across audiometric frequencies:

| Frequency | Expected Tolerance | Purpose |
|-----------|-------------------|---------|
| 250 Hz | ±3 dB | Low-frequency response check |
| 500 Hz | ±2 dB | Speech fundamental frequencies |
| 1000 Hz | ±1 dB | **Primary calibration point** |
| 2000 Hz | ±2 dB | Speech consonants |
| 4000 Hz | ±2 dB | Sibilants, fricatives |
| 8000 Hz | ±3 dB | High-frequency extension |

**Procedure** (for each frequency):
1. Generate pure tone at 0 dBFS
2. Measure SPL with SLM
3. Calculate frequency-specific correction factor:
   ```
   Correction(f) = MeasuredSPL(f) - MeasuredSPL(1kHz)
   ```
4. If |Correction(f)| > tolerance, transducer frequency response is non-flat
   - Option A: Use frequency-dependent DBFS_TO_DB_SPL table
   - Option B: Switch to flatter-response transducer

### 3.3 Speech-Weighted Noise Calibration (For Audiometry)

Per ANSI S3.6, audiometry masking noise must be calibrated separately:

1. **Generate Speech-Weighted Noise**:
   - Pink noise shaped by speech spectrum filter
   - RMS level = 0 dBFS (same as pure tone)

2. **Measure Equivalent SPL**:
   - SLM with A-weighting (for speech spectrum)
   - 30-second average measurement
   - Should match 1 kHz pure tone within ±2 dB

3. **Document Noise Calibration**:
   ```
   Speech-Weighted Noise @ 0 dBFS:
   - Measured: 110.5 dB(A) SPL
   - 1 kHz Pure Tone: 112.3 dB(C) SPL
   - Difference: -1.8 dB (acceptable, within ±2 dB tolerance)
   ```

---

## 4. Update Audio Configuration

### 4.1 Edit AudioConfig.java

Located at: `app/src/main/java/com/example/audion/AudioConfig.java`

```java
public class AudioConfig {
    // ... (existing constants)
    
    // === CALIBRATION CONSTANTS ===
    // CRITICAL: These values MUST be measured for each device/transducer combination
    // See CALIBRATION_PROCEDURE.md for measurement protocol
    
    /**
     * Left channel dBFS to dB SPL conversion factor
     * 
     * Measured with: 1 kHz pure tone @ 0 dBFS
     * Device: [YOUR_DEVICE_MODEL]
     * Transducer: [YOUR_HEADPHONE_MODEL]
     * Date: [CALIBRATION_DATE]
     * 
     * Formula: OutputSPL(dB SPL) = RMS_dBFS + DBFS_TO_DB_SPL_LEFT
     * 
     * Example: If RMS = -30 dBFS, OutputSPL = -30 + 112.3 = 82.3 dB SPL
     */
    public static final float DBFS_TO_DB_SPL_LEFT = 112.3f; // UPDATE THIS VALUE
    
    /**
     * Right channel dBFS to dB SPL conversion factor
     * 
     * Measured with: 1 kHz pure tone @ 0 dBFS
     * Device: [YOUR_DEVICE_MODEL]
     * Transducer: [YOUR_HEADPHONE_MODEL]
     * Date: [CALIBRATION_DATE]
     */
    public static final float DBFS_TO_DB_SPL_RIGHT = 112.0f; // UPDATE THIS VALUE
    
    /**
     * Calibration validity indicator
     * Set to true after completing calibration procedure
     * App should warn users if false (uncalibrated device)
     */
    public static final boolean IS_CALIBRATED = false; // SET TO TRUE AFTER CALIBRATION
    
    /**
     * Device and transducer identification (for calibration records)
     */
    public static final String CALIBRATED_DEVICE_MODEL = Build.MODEL; // e.g., "Pixel 6 Pro"
    public static final String CALIBRATED_TRANSDUCER = "REPLACE_WITH_HEADPHONE_MODEL"; // e.g., "Sony WH-1000XM4"
    public static final String CALIBRATION_DATE = "YYYY-MM-DD"; // e.g., "2024-12-15"
    
    // ... (rest of configuration)
}
```

### 4.2 Update LimiterProcessor.java

Located at: `app/src/main/java/com/example/audion/audio/dsp/LimiterProcessor.java`

```java
public class LimiterProcessor {
    // ... (existing code)
    
    // Use calibrated constants from AudioConfig
    private float dbfsToDbSplLeft = AudioConfig.DBFS_TO_DB_SPL_LEFT;
    private float dbfsToDbSplRight = AudioConfig.DBFS_TO_DB_SPL_RIGHT;
    
    /**
     * Set channel-specific calibration (override AudioConfig defaults)
     * Use this if device supports per-channel calibration refinement
     */
    public void setCalibration(float leftChannelDbfsToSpl, float rightChannelDbfsToSpl) {
        this.dbfsToDbSplLeft = leftChannelDbfsToSpl;
        this.dbfsToDbSplRight = rightChannelDbfsToSpl;
        
        // Warn if deviation from AudioConfig is large (indicates calibration problem)
        if (Math.abs(leftChannelDbfsToSpl - AudioConfig.DBFS_TO_DB_SPL_LEFT) > 5.0f ||
            Math.abs(rightChannelDbfsToSpl - AudioConfig.DBFS_TO_DB_SPL_RIGHT) > 5.0f) {
            Log.w(TAG, "WARNING: Large calibration deviation from AudioConfig defaults! " +
                       "Left: " + leftChannelDbfsToSpl + " vs " + AudioConfig.DBFS_TO_DB_SPL_LEFT + ", " +
                       "Right: " + rightChannelDbfsToSpl + " vs " + AudioConfig.DBFS_TO_DB_SPL_RIGHT);
        }
    }
    
    // ... (rest of implementation)
}
```

### 4.3 Rebuild and Verify

```bash
# Clean build to ensure constants are updated
.\gradlew clean
.\gradlew assembleDebug

# Verify calibration values are compiled in
adb shell am start -n com.example.audion/.MainActivity
adb logcat | grep "Calibration"
# Expected output:
# AudioConfig: IS_CALIBRATED = true
# AudioConfig: DBFS_TO_DB_SPL_LEFT = 112.3
# AudioConfig: DBFS_TO_DB_SPL_RIGHT = 112.0
```

---

## 5. Verification and Safety Testing

### 5.1 Functional Verification

Test that SPL monitoring reports accurate levels:

1. **Generate Known Test Level**:
   ```java
   // Generate -20 dBFS tone (20 dB below maximum)
   short[] testTone = CalibrationToneGenerator.generate1kHzTone(-20.0);
   // Play through AudioEngine with DSP bypassed
   ```

2. **Verify SPL Reading**:
   ```
   Expected SPL = -20 dBFS + DBFS_TO_DB_SPL_LEFT
   Example: -20 + 112.3 = 92.3 dB SPL
   
   Measured with SLM: 92.1 dB SPL
   Error: 0.2 dB (acceptable, within ±2 dB tolerance)
   ```

3. **Check App SPL Display**:
   ```java
   // In LimiterProcessor
   float reportedSpl = getCurrentOutputSpl(); // Should show ~92.3 dB SPL
   ```

### 5.2 Safety Limit Testing

**CRITICAL**: Verify UCL-based limiter prevents dangerous output levels.

1. **Test Maximum Output Limit**:
   ```java
   // Configure limiter with conservative UCL (85 dB SPL)
   limiterSettings.setPersonalizedMPO(85.0f);
   limiterSettings.setUclBasedLimit(85.0f);
   
   // Generate very loud input (+10 dBFS clipped signal)
   // Limiter should engage and prevent output > 85 dB SPL
   ```

2. **Measure Actual Output**:
   - SLM should read ≤ 85 dB SPL (limiter working correctly)
   - If > 85 dB SPL: CRITICAL FAILURE - limiter not engaging properly

3. **Sustained Exposure Test**:
   - Play 80 dB SPL tone for 8 hours (NIOSH 85 dB limit)
   - Verify no hearing discomfort (subjective test)
   - Measure total energy exposure: Should be < 100% daily dose

### 5.3 Audiometry Accuracy Verification

Compare app audiometry results with clinical audiometer:

1. **Obtain Clinical Audiogram**:
   - Professional audiometry with calibrated audiometer
   - Per ANSI S3.6, ISO 8253-1 protocols
   - Record thresholds at 250, 500, 1k, 2k, 4k, 8k Hz

2. **Perform In-App Audiometry**:
   - Use same transducer as calibration
   - Quiet environment (<40 dB(A))
   - Modified Hughson-Westlake method (standard clinical protocol)

3. **Compare Results**:
   ```
   Acceptable Agreement: ±5 dB at each frequency
   Good Agreement: ±3 dB at each frequency
   Excellent Agreement: ±2 dB at each frequency
   
   Example:
   Frequency | Clinical | App | Difference
   ----------|----------|-----|------------
   250 Hz    | 25 dB HL | 27 dB HL | +2 dB ✓
   500 Hz    | 20 dB HL | 22 dB HL | +2 dB ✓
   1000 Hz   | 15 dB HL | 14 dB HL | -1 dB ✓
   2000 Hz   | 20 dB HL | 18 dB HL | -2 dB ✓
   4000 Hz   | 35 dB HL | 33 dB HL | -2 dB ✓
   8000 Hz   | 40 dB HL | 42 dB HL | +2 dB ✓
   
   All frequencies within ±3 dB → EXCELLENT AGREEMENT
   ```

4. **Test-Retest Reliability**:
   - Repeat app audiometry 3 times (same day, 1-hour intervals)
   - Standard deviation at each frequency should be ≤3 dB
   - If > 5 dB: Poor reliability, investigate noise floor or test procedure

---

## 6. Tolerance Specifications and Acceptance Criteria

### 6.1 Calibration Accuracy

| Parameter | Tolerance | Clinical Requirement |
|-----------|-----------|----------------------|
| 1 kHz Pure Tone SPL | ±2 dB | IEC 60645-1 (audiometry) |
| Audiometric Frequencies (250-8k Hz) | ±3 dB | ANSI S3.6 |
| Left-Right Channel Balance | ±1 dB | Hearing aid standard IEC 60118-7 |
| Test-Retest Repeatability | ±2 dB | Clinical audiometry practice |
| SPL Linearity (40-100 dB SPL range) | ±3 dB | Full dynamic range verification |

### 6.2 Hearing Safety Limits

| Safety Parameter | Limit | Standard/Rationale |
|------------------|-------|-------------------|
| Maximum Output (MPO) | ≤ 110 dB SPL | IEC 60118-7 hearing aid limit |
| Personalized UCL Limit | User-specific, typically 85-100 dB SPL | Calibration-based individual limit |
| 8-Hour TWA Exposure | < 85 dB(A) | NIOSH/OSHA occupational safety |
| Peak Transient Limit | < 140 dB SPL (unweighted) | Acoustic trauma threshold |
| Limiter Attack Time | < 5 ms | Prevent transient overexposure |

### 6.3 Acceptance Criteria

**PASS** calibration if ALL conditions are met:
1. ✓ 1 kHz pure tone measurement within ±2 dB across 3 trials
2. ✓ Left-right channel balance within ±1 dB
3. ✓ Multi-frequency response within ±3 dB (250-8k Hz)
4. ✓ Limiter prevents output > personalized UCL
5. ✓ App audiometry agrees with clinical audiogram within ±5 dB

**FAIL** calibration if ANY condition fails:
1. ✗ 1 kHz measurement variability > ±2 dB (unstable system)
2. ✗ Left-right imbalance > ±3 dB (asymmetric output)
3. ✗ Any frequency deviation > ±5 dB (poor frequency response)
4. ✗ Limiter allows output > UCL + 3 dB (safety failure)
5. ✗ App audiometry deviates > ±10 dB from clinical (inaccurate thresholds)

---

## 7. Troubleshooting Common Calibration Issues

### 7.1 Unstable SPL Readings (> ±2 dB Variation)

**Symptoms**: SPL meter shows fluctuating values, large trial-to-trial variability

**Possible Causes**:
1. **Background Noise**: 
   - Verify ambient noise < 40 dB(A)
   - Move to quieter location or use sound booth
   
2. **Acoustic Leaks**:
   - Check ear simulator seal (press headphone firmly)
   - Verify coupler gasket is not worn
   
3. **Audio Buffer Underruns**:
   - Check logcat for "AudioTrack: underrun" messages
   - Increase buffer size: `PLAYBACK_BUFFER_FRAMES = 4` instead of 2
   
4. **CPU Throttling**:
   - Device may be reducing performance to save battery
   - Plug into power, disable battery saver mode

### 7.2 Large Left-Right Channel Imbalance (> ±3 dB)

**Symptoms**: DBFS_TO_DB_SPL_LEFT and _RIGHT differ by > 3 dB

**Possible Causes**:
1. **Asymmetric Transducer**:
   - Headphone driver mismatch or damage
   - Try different headphones, if imbalance persists → device issue
   
2. **Non-Centered Ear Simulator Positioning**:
   - Ensure headphone is centered on coupler
   - Rotate headphone 180°, repeat measurement (should flip imbalance)
   
3. **Device Hardware Imbalance**:
   - Some devices have asymmetric DAC outputs
   - Check manufacturer specs or use different device

### 7.3 Frequency Response Non-Flat (> ±5 dB Deviation)

**Symptoms**: Low/high frequency SPL differs significantly from 1 kHz

**Possible Causes**:
1. **Transducer Frequency Response**:
   - Most headphones have ±5 dB variation across frequency
   - Use flat-response headphones (e.g., Etymotic ER-4, Sennheiser HD 600)
   
2. **Ear Simulator Resonances**:
   - IEC 60318-4 coupler has ~2.5 kHz and ~8 kHz resonances
   - This is normal, use frequency-specific correction table
   
3. **Room Acoustics (Speaker Calibration)**:
   - Standing waves cause frequency-dependent SPL variations
   - Use anechoic chamber or absorptive treatment

**Solution**: Implement frequency-dependent calibration table:

```java
// AudioConfig.java
public static final Map<Integer, Float> DBFS_TO_DB_SPL_LEFT_FREQ = new HashMap<Integer, Float>() {{
    put(250,  110.5f); // +/- measured deviations from 1 kHz
    put(500,  111.8f);
    put(1000, 112.3f); // Reference (0 dB deviation)
    put(2000, 113.1f);
    put(4000, 112.0f);
    put(8000, 109.2f);
}};
```

### 7.4 Limiter Not Engaging (Safety Failure)

**Symptoms**: Output exceeds personalized UCL, no limiting observed

**CRITICAL SAFETY ISSUE** - Do not use app until resolved!

**Debugging Steps**:
1. **Check Limiter Threshold**:
   ```java
   // In LimiterProcessor
   Log.d(TAG, "Limiter threshold (dBFS): " + thresholdDbfs);
   Log.d(TAG, "Current input (dBFS): " + inputLevelDbfs);
   ```
   
2. **Verify dBFS → dB SPL Conversion**:
   ```java
   float outputSpl = inputLevelDbfs + AudioConfig.DBFS_TO_DB_SPL_LEFT;
   Log.d(TAG, "Calculated output SPL: " + outputSpl + " dB SPL");
   ```
   
3. **Test Limiter in Isolation**:
   ```java
   // Bypass all other processors, test limiter only
   float[] testSignal = generateClippedSignal(); // +10 dBFS
   limiter.process(testSignal);
   float outputRms = AudioUtils.calculateRMSdBFS(testSignal);
   // Output should be limited to personalized MPO
   ```

4. **Check Attack Time**:
   - Attack time too slow (> 10 ms) may allow transients through
   - Reduce to 1-3 ms for hearing protection

---

## 8. Documentation and Record Keeping

### 8.1 Required Calibration Records

Create a calibration certificate for each device/transducer combination:

```
========================================
AUDION APP - CALIBRATION CERTIFICATE
========================================

Date: 2024-12-15
Operator: J. Smith
Laboratory: University Audiology Research Lab

DEVICE UNDER TEST:
- Manufacturer: Google
- Model: Pixel 6 Pro
- Serial Number: 1234567890ABC
- Android Version: 13 (Build TQ3A.230805.001)

TRANSDUCER:
- Manufacturer: Sony
- Model: WH-1000XM4
- Serial Number: 9876543210XYZ
- Configuration: Wired connection, ANC OFF

MEASUREMENT EQUIPMENT:
- Microphone: B&K Type 4192 (S/N 12345)
  Calibration: 2024-06-01 (Valid until 2025-06-01)
- Ear Simulator: B&K Type 4157 (IEC 60318-4)
- Sound Level Meter: B&K 2270 (S/N 67890)
  Calibration: 2024-08-15 (Valid until 2025-08-15)
- Acoustic Calibrator: B&K 4231 (94 dB @ 1 kHz)

CALIBRATION RESULTS:
- Test Signal: 1 kHz pure tone @ 0 dBFS
- Sample Rate: 48 kHz
- Bit Depth: 16-bit PCM

LEFT CHANNEL:
- Trial 1: 112.1 dB SPL
- Trial 2: 112.3 dB SPL
- Trial 3: 112.4 dB SPL
- Mean: 112.3 dB SPL
- Std Dev: 0.15 dB
- DBFS_TO_DB_SPL_LEFT = 112.3 dB

RIGHT CHANNEL:
- Trial 1: 111.8 dB SPL
- Trial 2: 112.0 dB SPL
- Trial 3: 112.1 dB SPL
- Mean: 112.0 dB SPL
- Std Dev: 0.15 dB
- DBFS_TO_DB_SPL_RIGHT = 112.0 dB

LEFT-RIGHT BALANCE: 0.3 dB (within ±1 dB tolerance) ✓

FREQUENCY RESPONSE VERIFICATION:
- 250 Hz:  110.5 dB SPL (−1.8 dB) ✓
- 500 Hz:  111.8 dB SPL (−0.5 dB) ✓
- 1000 Hz: 112.3 dB SPL (Reference) ✓
- 2000 Hz: 113.1 dB SPL (+0.8 dB) ✓
- 4000 Hz: 112.0 dB SPL (−0.3 dB) ✓
- 8000 Hz: 109.2 dB SPL (−3.1 dB) ✓

All frequencies within ±3 dB tolerance ✓

SAFETY VERIFICATION:
- UCL Limiter Test: 85 dB SPL limit
  Input: +5 dBFS, Output: 84.7 dB SPL ✓
- Maximum Output Test: 110 dB SPL limit
  Input: 0 dBFS, Output: 112.3 dB SPL (limiter disabled) ✓

ACCEPTANCE CRITERIA: PASS ✓
- Repeatability: ✓ (±0.15 dB std dev < ±2 dB)
- Channel balance: ✓ (0.3 dB < ±1 dB)
- Frequency response: ✓ (all within ±3 dB)
- Safety limits: ✓ (limiter functioning correctly)

CALIBRATION VALIDITY:
- Valid From: 2024-12-15
- Valid Until: 2025-12-15 (12 months)
- Recalibration Due: 2025-12-15

SIGNATURES:
Calibration Technician: J. Smith
Reviewing Audiologist: Dr. A. Johnson
Quality Assurance: Dr. M. Lee

========================================
```

### 8.2 Update AudioConfig.java with Calibration Data

```java
// AudioConfig.java
public class AudioConfig {
    // ... existing constants
    
    // === CALIBRATION DATA ===
    // Calibration performed: 2024-12-15
    // Calibration certificate: CAL-2024-1215-001
    // Valid until: 2025-12-15
    
    public static final float DBFS_TO_DB_SPL_LEFT = 112.3f;
    public static final float DBFS_TO_DB_SPL_RIGHT = 112.0f;
    public static final boolean IS_CALIBRATED = true;
    
    public static final String CALIBRATED_DEVICE_MODEL = "Pixel 6 Pro";
    public static final String CALIBRATED_TRANSDUCER = "Sony WH-1000XM4 (wired, ANC OFF)";
    public static final String CALIBRATION_DATE = "2024-12-15";
    public static final String CALIBRATION_CERTIFICATE = "CAL-2024-1215-001";
    public static final String CALIBRATION_VALID_UNTIL = "2025-12-15";
    
    // ... rest of configuration
}
```

### 8.3 In-App Calibration Status Display

Add calibration status to app settings screen:

```java
// SettingsFragment.java or CalibrationStatusActivity.java
public void displayCalibrationStatus() {
    TextView statusText = findViewById(R.id.calibration_status);
    
    if (AudioConfig.IS_CALIBRATED) {
        long daysUntilExpiration = calculateDaysUntil(AudioConfig.CALIBRATION_VALID_UNTIL);
        
        if (daysUntilExpiration > 30) {
            statusText.setText("✓ Device Calibrated\n" +
                              "Valid until: " + AudioConfig.CALIBRATION_VALID_UNTIL + "\n" +
                              "Device: " + AudioConfig.CALIBRATED_DEVICE_MODEL + "\n" +
                              "Transducer: " + AudioConfig.CALIBRATED_TRANSDUCER);
            statusText.setTextColor(Color.GREEN);
        } else if (daysUntilExpiration > 0) {
            statusText.setText("⚠ Calibration Expiring Soon\n" +
                              "Valid until: " + AudioConfig.CALIBRATION_VALID_UNTIL + "\n" +
                              "(" + daysUntilExpiration + " days remaining)\n" +
                              "Please recalibrate device before expiration.");
            statusText.setTextColor(Color.YELLOW);
        } else {
            statusText.setText("✗ Calibration EXPIRED\n" +
                              "Last calibration: " + AudioConfig.CALIBRATION_DATE + "\n" +
                              "Device must be recalibrated for accurate SPL measurements.\n" +
                              "Audiometry results may be unreliable.");
            statusText.setTextColor(Color.RED);
        }
    } else {
        statusText.setText("✗ Device NOT Calibrated\n\n" +
                          "This device has not been calibrated for SPL measurements.\n" +
                          "Audiometry results and hearing safety limits may be inaccurate.\n\n" +
                          "Please perform calibration procedure before clinical use.\n" +
                          "See CALIBRATION_PROCEDURE.md for instructions.");
        statusText.setTextColor(Color.RED);
    }
}
```

---

## 9. Regulatory and Clinical Compliance

### 9.1 Applicable Standards

Audion app calibration procedure follows these international standards:

| Standard | Title | Applicability |
|----------|-------|---------------|
| **IEC 60645-1** | Audiometers - Part 1: Pure-tone audiometers | Audiometry calibration requirements |
| **IEC 60645-3** | Audiometers - Part 3: Auditory test signals of short duration | Test tone specifications |
| **IEC 60318-4** | Electroacoustics - Simulators of human head and ear - Part 4: Occluded-ear simulator | Ear simulator for calibration |
| **IEC 60118-7** | Hearing aids - Part 7: Measurement of performance characteristics | Hearing aid output limits and MPO |
| **IEC 61672-1** | Electroacoustics - Sound level meters - Part 1: Specifications | Measurement microphone requirements |
| **ANSI S3.6** | Specification for Audiometers | US audiometry standard |
| **ISO 389-1** | Acoustics - Reference zero for calibration of audiometric equipment - Part 1: Reference equivalent threshold SPL for pure tones | Audiometry reference levels |
| **ISO 8253-1** | Acoustics - Audiometric test methods - Part 1: Pure-tone audiometry | Clinical audiometry procedures |

### 9.2 Calibration Interval

**Recommended**: Recalibrate every **12 months** or:
- After device software/firmware updates
- After transducer change (different headphones)
- If app audiometry results deviate > ±10 dB from clinical audiogram
- After device physical damage or repair

### 9.3 Clinical Use Disclaimer

**IMPORTANT LEGAL DISCLAIMER**:

```
Audion Hearing Personalization App - Calibration Notice

This calibration procedure enables research and clinical validation of the 
Audion app's audio processing algorithms. It does NOT constitute:

1. Medical device certification (FDA, CE Mark, etc.)
2. Clinical audiometry device approval
3. Hearing aid regulatory classification
4. Professional diagnostic tool authorization

Audion is designed as a personal sound amplification product (PSAP) and 
research tool. It is NOT a substitute for:
- Professional hearing evaluation by licensed audiologist
- Prescription hearing aids
- Medical diagnosis or treatment of hearing loss

Clinical Use Restrictions:
- App-generated audiograms are for research/validation purposes only
- Not approved for clinical diagnostic decision-making
- Users with suspected hearing loss should consult licensed audiologist
- Personalized gain settings do not replace professional hearing aid fitting

By calibrating this device, you acknowledge that the app is intended for:
✓ Research and development
✓ Personal hearing assistance (non-medical)
✓ Algorithm validation and testing
✗ NOT for medical diagnosis
✗ NOT for clinical treatment decisions
✗ NOT as replacement for professional hearing care

For medical advice about hearing loss, consult a licensed audiologist or 
ear, nose, and throat (ENT) physician.
```

---

## 10. Appendix: Calibration Checklist

Use this checklist to ensure all calibration steps are completed:

### Pre-Calibration Setup
- [ ] Device factory reset audio settings
- [ ] Disabled all system audio processing (EQ, effects, etc.)
- [ ] Installed calibration test app build
- [ ] Verified device volume at 100%
- [ ] Calibrated microphone with 94 dB reference
- [ ] Positioned microphone in ear simulator (IEC 60318-4)
- [ ] Connected transducer to ear simulator with proper seal
- [ ] Verified background noise < 40 dB(A)

### 1 kHz Pure Tone Calibration
- [ ] Generated 1 kHz test tone at 0 dBFS
- [ ] Measured LEFT channel SPL (3 trials, recorded median)
- [ ] Measured RIGHT channel SPL (3 trials, recorded median)
- [ ] Calculated DBFS_TO_DB_SPL_LEFT and _RIGHT
- [ ] Verified left-right balance within ±1 dB
- [ ] Verified trial-to-trial repeatability within ±2 dB

### Multi-Frequency Verification (Optional)
- [ ] Measured SPL at 250 Hz
- [ ] Measured SPL at 500 Hz
- [ ] Measured SPL at 2000 Hz
- [ ] Measured SPL at 4000 Hz
- [ ] Measured SPL at 8000 Hz
- [ ] Verified all frequencies within ±3 dB tolerance

### Code Configuration
- [ ] Updated DBFS_TO_DB_SPL_LEFT in AudioConfig.java
- [ ] Updated DBFS_TO_DB_SPL_RIGHT in AudioConfig.java
- [ ] Set IS_CALIBRATED = true
- [ ] Filled in CALIBRATED_DEVICE_MODEL
- [ ] Filled in CALIBRATED_TRANSDUCER
- [ ] Filled in CALIBRATION_DATE
- [ ] Rebuilt app: `.\gradlew clean assembleDebug`
- [ ] Verified constants in logcat output

### Safety Verification
- [ ] Tested UCL limiter at 85 dB SPL limit
- [ ] Verified limiter prevents output > personalized UCL
- [ ] Tested maximum output limit (110 dB SPL)
- [ ] Verified no sustained exposure > 85 dB(A) for 8 hours

### Functional Testing
- [ ] Generated known test level (-20 dBFS)
- [ ] Verified app SPL display matches SLM reading (±2 dB)
- [ ] Performed app audiometry test
- [ ] Compared with clinical audiogram (if available)
- [ ] Verified audiometry agreement within ±5 dB

### Documentation
- [ ] Completed calibration certificate with all fields
- [ ] Recorded serial numbers of device and transducer
- [ ] Documented measurement equipment calibration dates
- [ ] Saved calibration certificate PDF/physical copy
- [ ] Set calibration expiration date (1 year from calibration)
- [ ] Added calibration status display to app settings screen

### Final Review
- [ ] All acceptance criteria met (see Section 6.3)
- [ ] Calibration technician signature
- [ ] Reviewing audiologist signature (if clinical use)
- [ ] Quality assurance review completed
- [ ] App ready for research/validation use

---

## Document Control

**Document Version**: 1.0  
**Effective Date**: 2024-12-15  
**Next Review Date**: 2025-06-15  
**Document Owner**: Audion Development Team  
**Approval**: [Insert Name/Title]

**Revision History**:
| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024-12-15 | AI Assistant | Initial calibration procedure documentation |

---

**END OF CALIBRATION PROCEDURE**
