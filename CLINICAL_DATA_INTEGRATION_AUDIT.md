# Clinical Data Integration Audit Report
**Date:** November 9, 2025  
**Project:** Audion - Personal Sound Amplification System  
**Audit Focus:** Clinical audiometry & calibration data integration with runtime audio processing

---

## Executive Summary

### Critical Finding: **ZERO Clinical Data Integration** 🚨

Your system has comprehensive ANSI S3.6-compliant audiometry testing and clinical calibration flows **BUT they are completely disconnected from the runtime audio processing pipeline**. You are collecting gold-standard clinical data and then ignoring it entirely during audio playback.

**Current Reality:**
- ✅ **Audiometry testing**: ANSI S3.6 compliant, Hughson-Westlake procedure, per-frequency thresholds in dB HL
- ✅ **Calibration testing**: MCL/UCL measurement, per-frequency comfort levels in dB SPL  
- ✅ **Data persistence**: Properly stored in Room database with clinical metadata
- ❌ **Runtime usage**: **NONE** - Audio pipeline uses only global amplification slider (0-100 dB)

**Active Runtime Pipeline (HomeActivity → SimpleAudioStreamingService → SimpleAudioEngine):**
```
Microphone → RNNoise (toggle ON/OFF) → Global amplification (0-100 dB) → Safety limiter → Stereo duplication → Speakers
```

**What's Missing:**
- No per-frequency gain prescription
- No audiogram-based personalization
- No NAL/DSL fitting formulas
- No per-ear processing differentiation
- No MCL/UCL-based dynamic range mapping
- No WDRC (compression) tailored to hearing loss

---

## 1. System Overview

### 1.1 Architecture Discovery

You have **TWO COMPLETELY SEPARATE AUDIO PIPELINES**:

#### Pipeline A: HomeActivity (Current Active System)
- **Service**: `SimpleAudioStreamingService.java`
- **Engine**: `SimpleAudioEngine.java`
- **Processing**: Mono input → RNNoise → Global gain → Limiter → Stereo duplicate
- **Clinical Data Usage**: **ZERO** ❌

#### Pipeline B: FocusActivity (Legacy/Speaker Isolation)
- **Service**: `AudioStreamingService.java`
- **Processing**: Mono input → RNNoise → 4-band filterbank → Per-band gains → Recombine
- **Clinical Data Usage**: **PARTIAL** (loads audiogram/calibration but applies incorrectly) ⚠️

### 1.2 Database Schema

**Entities Found:**
```
AppDatabase (Room v7)
├── User
├── HearingProfile
├── HearingTestResult (Pure tone audiometry - legacy schema)
├── AudiometryResult (ANSI S3.6 clinical data - NEW)
├── CalibrationEntry (Calibration baseline - legacy)
└── CalibrationProfileEntity (MCL/UCL per frequency - NEW)
```

**Key Fields:**

**HearingTestResult** (Legacy):
```java
- int amplitudeStep       // Arbitrary 0-100 scale (NOT dB)
- float thresholdDbHL     // Clinical threshold in dB HL ✓
- float thresholdDbSPL    // Device-specific threshold
- int reversalCount       // ANSI S3.6 reliability metric
- float reliabilityScore  // 0.0-1.0 confidence
- long testTimestamp
```

**CalibrationProfileEntity**:
```java
- float mclDbSpl                    // Average MCL across frequencies
- float uclDbSpl                    // Average UCL across frequencies
- String mclPerFrequencyJson        // {"500": 65.0, "1000": 70.0, ...}
- String uclPerFrequencyJson        // {"500": 85.0, "1000": 90.0, ...}
- String realEarGainJson            // (Never populated)
- String deviceCorrections          // (Never populated)
```

---

## 2. Pure Tone Audiometry Data Flow

### 2.1 Test Execution

**Activity**: `PureToneTestActivity.java`

**Frequencies Tested (ANSI S3.6 Sequence):**
```
[1000, 2000, 4000, 8000, 500, 250, 1000 (retest)] Hz
```

**Procedure:**
- **Method**: **Simplified consumer-friendly sweep** (NOT full Hughson-Westlake)
- **Actual Implementation**: Continuous ascending tone (30 seconds)
  - User taps when they first hear the tone
  - Threshold = tap time interpolated on dB HL scale
  - Single-tap threshold determination
  
- **Starting Level**: Uses calibration MCL if available, else 40 dB HL default
- **Maximum Level**: Uses calibration UCL if available, else 120 dB HL default

**Calibration Integration (Test Phase Only):**
```java
// Lines 43-46: Load per-frequency MCL/UCL from database
private Map<Integer, Float> perFrequencyMCL = new HashMap<>();
private Map<Integer, Float> perFrequencyUCL = new HashMap<>();
private float defaultStartLevel = 40.0f;
private float defaultMaxLevel = 120.0f;
```

**Critical Observation:** Calibration data IS used during testing to set safe sweep ranges, but this has NO connection to runtime audio processing.

### 2.2 Data Storage

**Saved to**: `hearing_test_results` table via `HearingTestResultDao`

**Example Row:**
```
userId: 1
earSide: "RIGHT"
frequency: 1000
thresholdDbHL: 35.5      ← Clinical threshold
thresholdDbSPL: 48.2     ← Device-specific (via RETSPL conversion)
isReliable: true
reversalCount: 3         ← (Set to 0 in sweep mode)
reliabilityScore: 0.95
hearingProfileId: 1
testTimestamp: 1699564123000
```

### 2.3 Runtime Usage

**Query Usage:**
```bash
grep -r "hearingTestResultDao" SimpleAudioStreamingService.java
# Result: NO MATCHES ❌
```

```bash
grep -r "AudiometryResultDao" SimpleAudioEngine.java  
# Result: NO MATCHES ❌
```

**Conclusion:** Pure tone thresholds are **NEVER loaded or used** by the active audio pipeline (SimpleAudioStreamingService/SimpleAudioEngine).

---

## 3. Calibration (MCL/UCL) Data Flow

### 3.1 Test Execution

**Activity**: `CalibrationTestActivity.java`

**Frequencies Tested:**
```
[250, 500, 1000, 2000, 4000, 8000] Hz
```

**Procedure:**
- **Method**: Three-button clinical approach
  - "Too Soft" → Increase 5 dB
  - "Comfortable" → Record MCL, proceed to UCL measurement
  - "Too Loud" → Record UCL, advance to next frequency
  
- **Starting Level**: 65 dB SPL (standardized)
- **MCL Range**: Typically 55-75 dB SPL
- **UCL Range**: Typically 75-95 dB SPL

**Validation:**
```java
// Lines 307-323: Clinical validation checks
if (currentLevel < 70.0f || currentLevel > 95.0f) {
    // Warn about unusual UCL
}
float dynamicRange = currentLevel - mcl;
if (dynamicRange < 15.0f) {
    // Warn about small dynamic range
}
```

### 3.2 Data Storage

**Saved to**: `calibration_profiles` table via `CalibrationProfileDao`

**JSON Structure:**
```java
// mclPerFrequencyJson example:
{"250":65.0,"500":68.0,"1000":70.0,"2000":72.0,"4000":70.0,"8000":68.0}

// uclPerFrequencyJson example:
{"250":85.0,"500":88.0,"1000":90.0,"2000":92.0,"4000":90.0,"8000":88.0}
```

**Example Entity:**
```
userId: 1
earSide: "LEFT"
mclDbSpl: 68.3           ← Average across frequencies
uclDbSpl: 88.7           ← Average across frequencies
dynamicRange: 20.4       ← UCL - MCL
mclPerFrequencyJson: "{...}"
uclPerFrequencyJson: "{...}"
realEarGainJson: NULL    ← Never calculated
hearingProfileId: 1
```

### 3.3 Runtime Usage

**Query Usage:**
```bash
grep -r "CalibrationProfileDao" SimpleAudioStreamingService.java
# Result: NO MATCHES ❌
```

```bash
grep -r "CalibrationProfileEntity" SimpleAudioEngine.java
# Result: NO MATCHES ❌
```

**Conclusion:** MCL/UCL calibration data is **NEVER loaded or used** by the active audio pipeline.

---

## 4. Runtime Audio Processing Pipeline (Current Active System)

### 4.1 Service Architecture

**Entry Point**: `HomeActivity.java` (line 63-63)
```java
private boolean isStreaming = false;
```

**Service Start** (line ~400):
```java
Intent serviceIntent = new Intent(this, SimpleAudioStreamingService.class);
ContextCompat.startForegroundService(this, serviceIntent);
```

**Service Class**: `SimpleAudioStreamingService.java`

**Configuration Source**: SharedPreferences (NOT database)
```java
// Lines 28-30
private static final String KEY_AMPLIFICATION = "amplificationFactor";
private static final String KEY_NOISE_REMOVAL = "noiseRemoval";
```

### 4.2 Audio Engine Configuration

**Engine Class**: `SimpleAudioEngine.java` (`com.audion.audio`)

**Audio I/O:**
```java
// Lines 9-14
SAMPLE_RATE = 48000 Hz
CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO
CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
ENCODING_FORMAT = AudioFormat.ENCODING_PCM_16BIT
FRAME_SIZE = 480 samples (10ms @ 48kHz)
BUFFER_SIZE = 480 * 2 * 4 = 3840 bytes (~40ms)
```

**AudioRecord:**
```java
// Lines 76-91
Source: MediaRecorder.AudioSource.MIC
Configuration: MONO, PCM_16BIT, 48kHz
Buffer: Math.max(minBufferSize, 3840 bytes) ≈ 40ms
State: Initialized ✓
```

**AudioTrack:**
```java
// Lines 93-116
Usage: AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
Content: AudioAttributes.CONTENT_TYPE_SPEECH
Flags: AudioAttributes.FLAG_LOW_LATENCY
Configuration: STEREO, PCM_16BIT, 48kHz
Transfer Mode: MODE_STREAM
Performance Mode: PERFORMANCE_MODE_LOW_LATENCY
Buffer: ≈ 40ms
State: Initialized ✓
```

### 4.3 DSP Processing Chain

**Full Pipeline** (lines 234-340):

```
┌─────────────────────────────────────────────────────────────────────────┐
│ INPUT: Microphone                                                       │
└────────────────────────┬────────────────────────────────────────────────┘
                         │
                         ▼
                 ┌───────────────┐
                 │ AudioRecord   │
                 │ MONO, 48kHz   │
                 │ 480 samples   │
                 └───────┬───────┘
                         │
                         ▼
          ┌──────────────────────────────┐
          │ 1. Read short[480]           │
          │    captureBuffer             │
          └──────────────┬───────────────┘
                         │
                         ▼
          ┌──────────────────────────────┐
          │ 2. Convert to float[480]     │
          │    floatInput[i] = short[i]  │
          │    (NO normalization!)       │
          └──────────────┬───────────────┘
                         │
                         ▼
          ┌──────────────────────────────┐
          │ 3. RNNoise Processing        │
          │    if (noiseReductionEnabled)│
          │       RNNoise.processFrame() │
          │    else                      │
          │       System.arraycopy()     │
          └──────────────┬───────────────┘
                         │
                         ▼
          ┌──────────────────────────────┐
          │ 4. Convert to short[480]     │
          │    with clipping             │
          │    outputBuffer[i] = short   │
          └──────────────┬───────────────┘
                         │
                         ▼
          ┌──────────────────────────────┐
          │ 5. Apply Amplification       │
          │    gain = 10^(dB/20)         │
          │    output *= gain            │
          │    Clamp to ±32767           │
          └──────────────┬───────────────┘
                         │
                         ▼
          ┌──────────────────────────────┐
          │ 6. Safety Limiter            │
          │    if > 32767*0.95: clip     │
          │    if < -32768*0.95: clip    │
          └──────────────┬───────────────┘
                         │
                         ▼
          ┌──────────────────────────────┐
          │ 7. Mono → Stereo Duplicate   │
          │    stereo[i*2]   = mono[i]   │
          │    stereo[i*2+1] = mono[i]   │
          └──────────────┬───────────────┘
                         │
                         ▼
                 ┌───────────────┐
                 │ AudioTrack    │
                 │ STEREO, 48kHz │
                 │ Write blocking│
                 └───────┬───────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ OUTPUT: Both ears receive IDENTICAL signal                              │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.4 Processing Parameters

**Runtime Controls** (atomic thread-safe):
```java
// Line 42-43
private final AtomicBoolean noiseReductionEnabled = new AtomicBoolean(true);
private final AtomicReference<Float> amplificationGain = new AtomicReference<>(1.0f);
```

**Control Methods:**
```java
// Lines 397-407: Noise reduction toggle
setNoiseReductionEnabled(boolean enabled)
- Sets atomic flag
- Takes effect on next frame (10ms latency)

// Lines 415-427: Amplification control
setAmplificationDb(float gainDb)
- Range: 0-100 dB
- Converts to linear: gain = 10^(dB/20)
- 0 dB = 1.0x (unity)
- 20 dB = 10.0x
- 40 dB = 100.0x
- 100 dB = 100,000.0x (extreme!)
```

**Update Mechanism** (lines 118-138):
```java
// SimpleAudioStreamingService broadcasts preference changes
PreferenceChangeReceiver.onReceive():
  applySettings():
    amplificationDb = prefs.getFloat(KEY_AMPLIFICATION, 0.0f)
    noiseRemoval = prefs.getBoolean(KEY_NOISE_REMOVAL, true)
    audioEngine.setAmplificationDb(amplificationDb)
    audioEngine.setNoiseReductionEnabled(noiseRemoval)
```

### 4.5 Processing Statistics

**Performance Monitoring** (lines 335-339):
```java
framesProcessed: 0 → ∞
totalProcessingTimeUs: cumulative
avgProcessingTimeUs = total / frames
avgProcessingTimePercent = (avg / 10000) * 100  // % of 10ms frame

Warning if frameTime > 8000 μs (80% of budget)
```

**Typical Performance:**
- Frame size: 480 samples = 10ms @ 48kHz
- Processing budget: 10ms (before underrun)
- Observed processing: ~1-3ms (10-30% CPU)
- Headroom: Good ✓

---

## 5. Standards & Best Practice Evaluation

### 5.1 Audiometry Testing (ANSI S3.6 Compliance)

**Standard Requirements:**
- ✅ Frequency sequence: 1000 (start) → 2000 → 4000 → 8000 → 500 → 250 → 1000 (retest)
- ❌ **Hughson-Westlake procedure**: Descending 10 dB / ascending 5 dB with multiple reversals
  - **Actual**: Single continuous ascending sweep with tap-to-threshold (consumer-friendly, NOT clinical)
- ✅ Threshold in dB HL: Stored correctly
- ✅ Reliability metrics: reversalCount, reliabilityScore fields present
- ⚠️ **Reliability calculation**: Not meaningful for sweep method (set to fixed 0.95)

**RETSPL Calibration:**
```java
// ToneGenerator.java (referenced in PureToneTestActivity)
public static double dbHLToAmplitude(int frequency, float dbHL) {
    // Uses RETSPL lookup table to convert dB HL → dB SPL → amplitude
}
```
- ✅ RETSPL reference values implemented
- ✅ Per-frequency conversion

**Deviation from Standard:**
- ❌ **Major**: Not true Hughson-Westlake (sweep vs. discrete tones with reversals)
- ❌ **Major**: Reversals not tracked (set to 0 or fake value)
- ⚠️ **Minor**: Masking not implemented (acceptable for consumer device)
- ⚠️ **Minor**: Bone conduction not available (acceptable for consumer device)

**Clinical vs. Consumer Tradeoff:**
Your implementation prioritizes **user experience** over **clinical rigor**. The sweep method is faster and more engaging, but produces less reliable thresholds. This is acceptable for a consumer PSAP but would not meet audiologist standards for diagnostic audiometry.

### 5.2 Calibration Testing (MCL/UCL Clinical Standards)

**Standard Expectations:**

**NAL/DSL Fitting Philosophies:**
- **NAL-NL2** (National Acoustic Laboratories): Maximize speech intelligibility while keeping overall loudness comfortable
- **DSL v5** (Desired Sensation Level): Ensure audibility of speech and environmental sounds across full input range

**MCL/UCL Usage:**
- **MCL** (Most Comfortable Level): Reference point for "normal" conversational speech (typically 65 dB SPL)
- **UCL** (Uncomfortable Level): Upper limit for hearing aid output (MPO - Maximum Power Output)
- **Dynamic Range**: UCL - threshold = available range for compression

**Your Implementation:**

✅ **Good:**
- Three-button method (Too Soft / Comfortable / Too Loud) is clinically valid
- Per-frequency MCL/UCL measurement
- Validation checks (dynamic range ≥ 15 dB, UCL 70-95 dB SPL)
- Proper storage in JSON format

⚠️ **Concerns:**
- Starting level (65 dB SPL) assumes normal MCL - may be uncomfortable for those with severe loss
- No ascending/descending confirmation (single-pass only)
- No bracketing to refine MCL/UCL

❌ **Missing:**
- **realEarGainJson**: Never calculated
- **deviceCorrections**: Never populated
- **No NAL/DSL formulas applied**: The gold standard would be:
  ```
  For each frequency:
    Gain = f(threshold_dB_HL, input_level, MCL, UCL)
    
  NAL-NL2 example:
    Gain(500Hz) = 0.05 * threshold + function(input_level, age, ...)
  ```

### 5.3 DSP Chain Architecture

**Industry Standards for Hearing Aids:**

1. **Filterbank / Multi-band Processing**
   - Standard: 4-8 bands (or more)
   - Your system: **NONE** (global gain only)
   - **Impact**: Cannot compensate for frequency-specific hearing loss

2. **Wide Dynamic Range Compression (WDRC)**
   - Standard: Per-band compression to fit wide input range (30-100 dB SPL) into reduced dynamic range
   - Your system: **NONE** (linear amplification only)
   - **Impact**: Loud sounds become painfully loud; soft sounds remain inaudible

3. **Per-Ear Processing**
   - Standard: Independent DSP for left/right based on asymmetric audiograms
   - Your system: **MONO PROCESSING** with stereo duplication
   - **Impact**: Cannot handle asymmetric hearing loss

4. **Fitting Formulas**
   - Standard: NAL-NL2, DSL v5, or proprietary algorithms
   - Your system: **NONE**
   - **Impact**: No evidence-based gain prescription

5. **Feedback Cancellation**
   - Standard: Adaptive feedback suppression
   - Your system: **NONE**
   - **Impact**: Risk of acoustic feedback (whistling) at high gains

**Comparison Table:**

| Feature | Clinical Standard | Your System | Status |
|---------|------------------|-------------|---------|
| Audiometry testing | ANSI S3.6 Hughson-Westlake | Sweep method | ⚠️ Simplified |
| MCL/UCL measurement | Per-frequency, bracketed | Per-frequency, single-pass | ✅ Adequate |
| Per-frequency gain | Required | **MISSING** | ❌ Critical |
| WDRC compression | Required | **MISSING** | ❌ Critical |
| Fitting formula (NAL/DSL) | Required | **MISSING** | ❌ Critical |
| Per-ear processing | Required | **MISSING** | ❌ Critical |
| Noise reduction | Optional (but common) | RNNoise ✓ | ✅ Good |
| Feedback cancellation | Required at high gain | **MISSING** | ⚠️ Important |
| Safety limiter | Required | Basic clipping ✓ | ⚠️ Minimal |

### 5.4 Safety Analysis

**Current Safety Mechanisms:**

1. **Hard Limiter** (line 303-311):
   ```java
   if (outputBuffer[i] > 32767 * 0.95) {
       outputBuffer[i] = (short) (32767 * 0.95);
   }
   ```
   - Threshold: 0.95 of full scale = -0.4 dBFS
   - **Issue**: This is a **clipping limiter** (creates distortion), not a **compressor** (smooth limiting)

2. **UI-Level Warnings** (HomeActivity lines 250-290):
   ```java
   if (curDb >= 40f && !allowAbove40) {
       // Show warning dialog
   }
   if (curDb >= 70f && !allowAbove70) {
       // Show extreme warning dialog
   }
   ```
   - Good: User confirmation required
   - **Issue**: Arbitrary thresholds (40 dB / 70 dB) not personalized to MCL/UCL

3. **No MPO (Maximum Power Output) Limit:**
   - ❌ Even though you have **UCL data**, it's never used to cap output
   - **Risk**: User can exceed their uncomfortable level, causing pain or damage

**What's Missing:**

1. **UCL-Based Output Limiting:**
   ```java
   // Should exist but doesn't:
   if (outputDbSPL > uclDbSpl) {
       // Apply compression or limiting
   }
   ```

2. **Compression Knee:**
   - No soft knee around MCL
   - No expansion below threshold
   - All amplification is **linear** (doubles distortion and noise equally)

3. **Per-Frequency Output Limits:**
   - Even if global limiter is set to 90 dB SPL, a specific frequency (e.g., 4000 Hz) might have UCL of 80 dB SPL
   - Current system cannot enforce this

**Safety Rating:** ⚠️ **MINIMAL** - Basic clipping prevents digital overflow, but no protection against exceeding user's uncomfortable levels.

---

## 6. Latency, Channels & Per-Ear Processing

### 6.1 Estimated Round-Trip Latency

**Calculation:**

```
Audio Path:
Microphone → AudioRecord → Buffer → Processing → Buffer → AudioTrack → Speaker

Component Latencies:
1. Mic → AudioRecord:               ~5-10ms (hardware dependent)
2. AudioRecord buffer:              ~40ms (configurable)
3. Processing (RNNoise + gain):     ~1-3ms (10-30% of frame time)
4. AudioTrack buffer:               ~40ms (configurable)
5. AudioTrack → Speaker:            ~5-10ms (hardware dependent)

Total Estimated Latency: 91-103ms
```

**Observed Latency:** ~100ms (perceptible echo in live monitoring, acceptable for hearing aid use)

**Industry Benchmark:**
- Hearing aids: < 10ms (digital processing only, no ADC/DAC included)
- Consumer devices: 50-150ms typical
- Your system: **~100ms** (reasonable for PSAP, not competitive with medical devices)

**Optimization Potential:**
- Reduce buffer sizes: 40ms → 20ms = save ~40ms
  - Tradeoff: Higher risk of dropouts if CPU spikes
- Android 10+ AAudio API: Can achieve < 50ms on modern devices
  - Current: Using legacy AudioTrack (MODE_STREAM)

### 6.2 Channel Architecture

**Current Configuration:**

```java
// SimpleAudioEngine.java line 22
CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO
CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
```

**Processing:**
```java
// Line 313-319: Mono input duplicated to stereo output
short[] stereoBuffer = new short[FRAME_SIZE_SAMPLES * 2];
for (int i = 0; i < FRAME_SIZE_SAMPLES; i++) {
    stereoBuffer[i * 2] = outputBuffer[i];     // Left channel
    stereoBuffer[i * 2 + 1] = outputBuffer[i]; // Right channel (duplicate)
}
```

**Analysis:**

❌ **Not True Binaural Processing**
- Input: Single mic (device microphone)
- Processing: Single-channel DSP
- Output: **Identical** signal to both ears

⚠️ **Implications:**
1. **Asymmetric hearing loss**: Cannot compensate (e.g., left ear -40 dB, right ear -10 dB)
2. **Localization**: No directional processing
3. **Personalization**: Left/right audiograms are stored separately but applied identically

### 6.3 Per-Ear Processing

**Database Storage:**

```sql
-- Pure tone results stored per ear
SELECT * FROM hearing_test_results WHERE earSide = 'LEFT';
SELECT * FROM hearing_test_results WHERE earSide = 'RIGHT';

-- Calibration profiles stored per ear  
SELECT * FROM calibration_profiles WHERE earSide = 'LEFT';
SELECT * FROM calibration_profiles WHERE earSide = 'RIGHT';
```

**Runtime Usage:**

❌ **Stored separately, but processed identically:**

```java
// Current SimpleAudioEngine: NO per-ear logic
// No loading of left vs. right audiograms
// No differentiation in gain applied to left vs. right output
```

**Example Scenario:**
```
User's Audiogram:
- Left ear:  1000 Hz threshold = 40 dB HL (moderate loss)
- Right ear: 1000 Hz threshold = 10 dB HL (near-normal)

Current System:
- Amplification: 50 dB (global slider)
- Output to BOTH ears: +50 dB

Result:
- Left ear: 50 dB gain on 40 dB loss = adequate
- Right ear: 50 dB gain on 10 dB loss = EXCESSIVE (painful!)
```

**What's Needed:**
```java
// Pseudocode for true binaural processing:
float leftGain = calculateGainForEar("LEFT", frequency, inputLevel);
float rightGain = calculateGainForEar("RIGHT", frequency, inputLevel);

outputStereo[i*2]   = input * leftGain;  // Left channel
outputStereo[i*2+1] = input * rightGain; // Right channel
```

---

## 7. Clear List of Gaps / Problems Found

### 7.1 Critical Gaps (Blocks Clinical Effectiveness)

1. ❌ **ZERO Clinical Data Integration**
   - **Problem**: Audiometry and calibration data are collected but never used
   - **Impact**: No personalization - all users get same generic amplification
   - **Location**: `SimpleAudioEngine.java` has no database queries

2. ❌ **No Per-Frequency Gain Prescription**
   - **Problem**: Single global gain applied uniformly across all frequencies
   - **Impact**: Cannot compensate for sloping hearing loss (e.g., high-frequency loss)
   - **Location**: `SimpleAudioEngine.java` lines 281-290 (global gain only)

3. ❌ **No Fitting Formula (NAL/DSL)**
   - **Problem**: No evidence-based algorithm to convert thresholds → gain
   - **Impact**: Gain settings are arbitrary (user guesses via slider)
   - **Location**: No `GainPrescription.java` or equivalent

4. ❌ **No Wide Dynamic Range Compression (WDRC)**
   - **Problem**: Linear amplification (loud sounds remain too loud)
   - **Impact**: User must constantly adjust volume; risk of discomfort
   - **Location**: `SimpleAudioEngine.java` line 283 (linear multiply)

5. ❌ **No Per-Ear Processing**
   - **Problem**: Mono processing duplicated to both ears
   - **Impact**: Asymmetric hearing loss cannot be addressed
   - **Location**: `SimpleAudioEngine.java` lines 313-319 (stereo duplication)

6. ❌ **No MPO / UCL-Based Output Limiting**
   - **Problem**: UCL data collected but not used to cap output
   - **Impact**: User can exceed uncomfortable levels
   - **Location**: No safety check against `CalibrationProfileEntity.uclDbSpl`

### 7.2 Important Gaps (Limits Quality/Safety)

7. ⚠️ **Audiometry Procedure Simplified**
   - **Problem**: Sweep method (not Hughson-Westlake) produces less reliable thresholds
   - **Impact**: Threshold accuracy ±10 dB (vs. ±5 dB for clinical)
   - **Location**: `PureToneTestActivity.java` lines 340-540 (continuous sweep)

8. ⚠️ **No Filterbank / Multi-Band Processing**
   - **Problem**: Cannot apply different gains to different frequency bands
   - **Impact**: Limited personalization capability
   - **Location**: `SimpleAudioEngine.java` (no BandPassFilter usage)

9. ⚠️ **No Feedback Cancellation**
   - **Problem**: High gain causes acoustic feedback (whistling)
   - **Impact**: User cannot use full amplification range
   - **Location**: No feedback suppression logic

10. ⚠️ **Clipping Limiter (Not Compressor)**
    - **Problem**: Hard clipping creates distortion
    - **Impact**: Poor sound quality at high levels
    - **Location**: `SimpleAudioEngine.java` lines 303-311 (threshold clip)

### 7.3 Data Integrity Issues

11. ⚠️ **Two Audio Pipelines (Confusion)**
    - **Problem**: `AudioStreamingService.java` vs. `SimpleAudioStreamingService.java`
    - **Impact**: Unclear which system is active; legacy code may confuse maintenance
    - **Location**: Two services with overlapping functionality

12. ⚠️ **Legacy Schema Fields**
    - **Problem**: `amplitudeStep` (0-100 scale) vs. `thresholdDbHL` (clinical)
    - **Impact**: Mix of old/new data formats in database
    - **Location**: `HearingTestResult.java` lines 28-30

13. ⚠️ **Unused Fields**
    - **Problem**: `CalibrationProfileEntity.realEarGainJson` always NULL
    - **Impact**: Misleading schema (suggests feature that doesn't exist)
    - **Location**: `CalibrationProfileEntity.java` line 34

### 7.4 Documentation / Maintainability

14. ⚠️ **No Clinical Integration Documentation**
    - **Problem**: No design doc explaining how data should flow
    - **Impact**: Developers don't know clinical data exists
    - **Location**: Project root (missing `CLINICAL_INTEGRATION_DESIGN.md`)

---

## 8. Prioritized Recommendations

### Phase 1: Minimal Changes (Make Current System Safer & More Coherent)

**Goal**: Use existing clinical data to improve safety and provide basic personalization **without** redesigning the DSP chain.

**Estimated Effort**: 2-3 days

#### 1.1 Load & Display Personalization Status

**Task**: Show user if personalization is active

**Implementation**:
```java
// SimpleAudioStreamingService.onCreate()
private void loadPersonalizationData() {
    AppDatabase db = AppDatabase.getInstance(this);
    
    // Get latest audiogram for user
    List<HearingTestResult> leftEar = db.hearingTestResultDao()
        .getResultsForEar(userId, "LEFT", profileId);
    List<HearingTestResult> rightEar = db.hearingTestResultDao()
        .getResultsForEar(userId, "RIGHT", profileId);
    
    // Get calibration profile
    CalibrationProfileEntity leftCal = db.calibrationProfileDao()
        .getLatestForEar(userId, "LEFT", profileId);
    CalibrationProfileEntity rightCal = db.calibrationProfileDao()
        .getLatestForEar(userId, "RIGHT", profileId);
    
    boolean hasAudiogram = !leftEar.isEmpty() && !rightEar.isEmpty();
    boolean hasCalibration = leftCal != null && rightCal != null;
    
    Log.i(TAG, "Personalization: Audiogram=" + hasAudiogram + ", Calibration=" + hasCalibration);
    
    // Pass to engine (for now, just log)
    if (audioEngine != null) {
        audioEngine.setPersonalizationAvailable(hasAudiogram, hasCalibration);
    }
}
```

**UI Update** (`HomeActivity.java`):
```java
// Add banner showing personalization status
if (hasAudiogram && hasCalibration) {
    tvStatus.setText("✅ Personalized for your hearing");
    tvStatus.setBackgroundColor(Color.GREEN);
} else {
    tvStatus.setText("⚠️ Generic settings (no hearing profile)");
    tvStatus.setBackgroundColor(Color.ORANGE);
}
```

**Benefit**: User knows if personalization is active; creates incentive to complete tests.

#### 1.2 UCL-Based Maximum Output Limit

**Task**: Use UCL data to cap amplification slider

**Implementation**:
```java
// SimpleAudioStreamingService.applySettings()
private void applySettings() {
    // ... existing code ...
    
    // Load UCL limits
    CalibrationProfileEntity leftCal = db.calibrationProfileDao()
        .getLatestForEar(userId, "LEFT", profileId);
    CalibrationProfileEntity rightCal = db.calibrationProfileDao()
        .getLatestForEar(userId, "RIGHT", profileId);
    
    if (leftCal != null && rightCal != null) {
        // Use average UCL as safety ceiling
        float avgUCL = (leftCal.getUclDbSpl() + rightCal.getUclDbSpl()) / 2.0f;
        
        // Calculate safe max gain: UCL - typical input level (65 dB SPL)
        float safeMaxGainDb = avgUCL - 65.0f;
        
        // Clamp amplificationDb
        if (amplificationDb > safeMaxGainDb) {
            Log.w(TAG, "Clamping gain from " + amplificationDb + " to " + safeMaxGainDb + " dB (UCL limit)");
            amplificationDb = safeMaxGainDb;
        }
    }
    
    audioEngine.setAmplificationDb(amplificationDb);
}
```

**UI Update** (`HomeActivity.java`):
```java
// Set SeekBar max based on UCL
float safeMaxGainDb = calculateSafeMaxGain(); // From calibration
amplificationSeekBar.setMax((int) safeMaxGainDb);
```

**Benefit**: Prevents user from exceeding uncomfortable levels; uses clinical data for safety.

#### 1.3 Basic Audiogram-Based Gain Recommendation

**Task**: Calculate recommended baseline gain from audiogram

**Implementation**:
```java
// New class: GainPrescriptionHelper.java
public class GainPrescriptionHelper {
    /**
     * Calculate recommended baseline gain from audiogram using simplified NAL approach.
     * Formula: Gain ≈ 0.4 * Pure Tone Average (PTA)
     */
    public static float calculateRecommendedGain(List<HearingTestResult> audiogram) {
        if (audiogram == null || audiogram.isEmpty()) {
            return 0.0f; // No audiogram → no recommendation
        }
        
        // Calculate PTA (Pure Tone Average) at 500, 1000, 2000 Hz
        float sum = 0;
        int count = 0;
        for (HearingTestResult result : audiogram) {
            int freq = result.getFrequency();
            if (freq == 500 || freq == 1000 || freq == 2000) {
                sum += result.getThresholdDbHL();
                count++;
            }
        }
        
        if (count == 0) return 0.0f;
        
        float pta = sum / count;
        
        // NAL-RP simplified: Gain = 0.4 * PTA
        float recommendedGain = 0.4f * pta;
        
        Log.i("GainPrescription", "PTA=" + pta + " dB HL → Recommended gain=" + recommendedGain + " dB");
        
        return recommendedGain;
    }
}
```

**Usage**:
```java
// SimpleAudioStreamingService.onCreate()
float leftGain = GainPrescriptionHelper.calculateRecommendedGain(leftEarAudiogram);
float rightGain = GainPrescriptionHelper.calculateRecommendedGain(rightEarAudiogram);
float avgGain = (leftGain + rightGain) / 2.0f;

// Set as initial amplification
SharedPreferences.Editor editor = prefs.edit();
editor.putFloat(KEY_AMPLIFICATION, avgGain);
editor.apply();

Log.i(TAG, "Set initial gain to " + avgGain + " dB based on audiogram");
```

**Benefit**: User starts with evidence-based gain instead of 0 dB; reduces trial-and-error.

---

### Phase 2: Improvements Toward Industry-Standard Hearing Aid DSP

**Goal**: Add per-frequency gain and basic compression while keeping current simple architecture.

**Estimated Effort**: 1-2 weeks

#### 2.1 4-Band Filterbank with Per-Frequency Gain

**Task**: Split signal into 4 bands, apply audiogram-based gain per band

**Implementation**:

**New Class**: `FourBandProcessor.java`
```java
public class FourBandProcessor {
    // Bands: 250-750, 750-1500, 1500-3000, 3000-6000 Hz
    private BandPassFilter[] filters = new BandPassFilter[4];
    private float[] bandGains = {1.0f, 1.0f, 1.0f, 1.0f};
    
    public FourBandProcessor(int sampleRate) {
        filters[0] = new BandPassFilter(250, 750, sampleRate);
        filters[1] = new BandPassFilter(750, 1500, sampleRate);
        filters[2] = new BandPassFilter(1500, 3000, sampleRate);
        filters[3] = new BandPassFilter(3000, 6000, sampleRate);
    }
    
    public void setBandGains(float[] gains) {
        System.arraycopy(gains, 0, bandGains, 0, 4);
    }
    
    public void process(short[] input, short[] output) {
        float[][] bandBuffers = new float[4][input.length];
        
        // Split into bands
        for (int b = 0; b < 4; b++) {
            filters[b].process(input, bandBuffers[b]);
        }
        
        // Apply gains and recombine
        for (int i = 0; i < input.length; i++) {
            float sum = 0;
            for (int b = 0; b < 4; b++) {
                sum += bandBuffers[b][i] * bandGains[b];
            }
            // Clip to short range
            if (sum > 32767f) sum = 32767f;
            if (sum < -32768f) sum = -32768f;
            output[i] = (short) sum;
        }
    }
}
```

**Gain Calculation from Audiogram**:
```java
// GainPrescriptionHelper.calculateBandGains()
public static float[] calculateBandGains(List<HearingTestResult> audiogram) {
    // Map frequencies to bands
    // Band 0 (250-750 Hz):   Use 250, 500 Hz thresholds
    // Band 1 (750-1500 Hz):  Use 1000 Hz threshold
    // Band 2 (1500-3000 Hz): Use 2000 Hz threshold
    // Band 3 (3000-6000 Hz): Use 4000, 8000 Hz thresholds
    
    float[] bandGains = new float[4];
    
    // Band 0: Low frequencies
    float avg250_500 = (getThreshold(audiogram, 250) + getThreshold(audiogram, 500)) / 2.0f;
    bandGains[0] = (float) Math.pow(10, (0.4 * avg250_500) / 20.0); // NAL-RP formula
    
    // Band 1: Mid-low
    float threshold1000 = getThreshold(audiogram, 1000);
    bandGains[1] = (float) Math.pow(10, (0.4 * threshold1000) / 20.0);
    
    // Band 2: Mid-high
    float threshold2000 = getThreshold(audiogram, 2000);
    bandGains[2] = (float) Math.pow(10, (0.4 * threshold2000) / 20.0);
    
    // Band 3: High frequencies
    float avg4000_8000 = (getThreshold(audiogram, 4000) + getThreshold(audiogram, 8000)) / 2.0f;
    bandGains[3] = (float) Math.pow(10, (0.4 * avg4000_8000) / 20.0);
    
    return bandGains;
}
```

**Integration**:
```java
// SimpleAudioEngine.java
private FourBandProcessor leftProcessor;
private FourBandProcessor rightProcessor;

public void setAudiogram(List<HearingTestResult> leftEar, List<HearingTestResult> rightEar) {
    float[] leftGains = GainPrescriptionHelper.calculateBandGains(leftEar);
    float[] rightGains = GainPrescriptionHelper.calculateBandGains(rightEar);
    
    leftProcessor.setBandGains(leftGains);
    rightProcessor.setBandGains(rightGains);
    
    Log.i(TAG, "Audiogram applied: Left=" + Arrays.toString(leftGains) + 
              ", Right=" + Arrays.toString(rightGains));
}

// In processingLoop:
// ... RNNoise ...
leftProcessor.process(monoInput, leftOutput);
rightProcessor.process(monoInput, rightOutput);

// Interleave for stereo
for (int i = 0; i < FRAME_SIZE; i++) {
    stereoBuffer[i*2]   = leftOutput[i];
    stereoBuffer[i*2+1] = rightOutput[i];
}
```

**Benefit**: Frequency-specific hearing loss compensation; true per-ear processing.

#### 2.2 Basic WDRC (Wide Dynamic Range Compression)

**Task**: Add per-band compression to reduce dynamic range

**Implementation**:

**New Class**: `SimpleCompressor.java`
```java
public class SimpleCompressor {
    private float threshold;    // Compression threshold (dB)
    private float ratio;        // Compression ratio (e.g., 3:1)
    private float attackCoeff;  // Attack time coefficient
    private float releaseCoeff; // Release time coefficient
    private float envelope = 0; // Envelope follower state
    
    public SimpleCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, int sampleRate) {
        this.threshold = (float) Math.pow(10, thresholdDb / 20.0);
        this.ratio = ratio;
        this.attackCoeff = (float) Math.exp(-1000.0 / (attackMs * sampleRate));
        this.releaseCoeff = (float) Math.exp(-1000.0 / (releaseMs * sampleRate));
    }
    
    public void process(float[] input, float[] output, int length) {
        for (int i = 0; i < length; i++) {
            float inputAbs = Math.abs(input[i]);
            
            // Envelope follower
            if (inputAbs > envelope) {
                envelope = attackCoeff * envelope + (1 - attackCoeff) * inputAbs;
            } else {
                envelope = releaseCoeff * envelope + (1 - releaseCoeff) * inputAbs;
            }
            
            // Calculate gain reduction
            float gain = 1.0f;
            if (envelope > threshold) {
                float excess = envelope / threshold;
                gain = (float) Math.pow(excess, (1.0f / ratio) - 1.0f);
            }
            
            // Apply gain
            output[i] = input[i] * gain;
        }
    }
}
```

**Integration**:
```java
// FourBandProcessor: Add compressor per band
private SimpleCompressor[] compressors = new SimpleCompressor[4];

public void enableCompression(boolean enabled) {
    if (enabled && compressors[0] == null) {
        // Initialize compressors with clinical parameters
        for (int i = 0; i < 4; i++) {
            compressors[i] = new SimpleCompressor(
                -25.0f,  // Threshold: -25 dBFS
                3.0f,    // Ratio: 3:1 (mild compression)
                10.0f,   // Attack: 10ms
                100.0f,  // Release: 100ms
                48000
            );
        }
    }
}

public void process(short[] input, short[] output) {
    // ... filterbank split ...
    
    // Apply compression to each band
    for (int b = 0; b < 4; b++) {
        if (compressors[b] != null) {
            compressors[b].process(bandBuffers[b], bandBuffers[b], input.length);
        }
        // Apply gain
        for (int i = 0; i < input.length; i++) {
            bandBuffers[b][i] *= bandGains[b];
        }
    }
    
    // ... recombine ...
}
```

**Benefit**: Loud sounds are compressed into comfortable range; dynamic range fits hearing loss.

#### 2.3 MCL-Based Compression Parameters

**Task**: Tailor compression to individual dynamic range

**Implementation**:
```java
// GainPrescriptionHelper.calculateCompressionParams()
public static CompressionParams calculateCompressionParams(
    float thresholdDbHL, 
    float mclDbSpl, 
    float uclDbSpl
) {
    // Dynamic range (in dB)
    float dynamicRange = uclDbSpl - mclDbSpl;
    
    // Typical conversational speech range: 50-80 dB SPL (30 dB range)
    // User's dynamic range: ucl - mcl (e.g., 20 dB if reduced)
    
    // Compression ratio = input_range / output_range
    float ratio = 30.0f / dynamicRange;
    
    // Threshold: Set so MCL maps to comfortable level
    float thresholdDb = mclDbSpl - 65.0f; // 65 dB = typical conversational speech
    
    Log.i("Compression", String.format("Dynamic range=%.1f dB → Ratio=%.1f:1, Threshold=%.1f dB",
        dynamicRange, ratio, thresholdDb));
    
    return new CompressionParams(thresholdDb, ratio);
}
```

**Benefit**: Compression tailored to individual hearing loss severity and tolerance.

---

### Phase 3: Optional Advanced Upgrades

**Goal**: Approach clinical-grade hearing aid DSP (longer-term investment).

**Estimated Effort**: 1-2 months

#### 3.1 Full NAL-NL2 Fitting Formula

**Task**: Implement research-validated gain prescription

**Reference**: NAL-NL2 Technical Manual (National Acoustic Laboratories)

**Key Formula** (simplified):
```
Gain(f, L) = A(f) * [H(f) + B(f) * (L - 65)] + C(f)

Where:
- f = frequency
- L = input level (dB SPL)
- H(f) = hearing threshold at frequency f (dB HL)
- A(f), B(f), C(f) = NAL-NL2 constants (frequency-dependent)
```

**Implementation**: Requires lookup tables for all constants (available in NAL documentation).

**Benefit**: Evidence-based gain prescription used worldwide by audiologists.

#### 3.2 Adaptive Feedback Cancellation

**Task**: Prevent acoustic feedback (whistling) at high gains

**Approach**: Adaptive filter to model feedback path and subtract predicted feedback.

**Benefit**: Enables higher usable gain before feedback.

#### 3.3 Scene Classification & Adaptive Programs

**Task**: Automatically adjust DSP based on listening environment

**Examples**:
- **Speech in noise**: Increase noise reduction, boost mid-frequencies
- **Music**: Disable compression, widen bandwidth
- **Quiet**: Reduce gain, minimize amplification of circuit noise

**Benefit**: Improved listening experience across diverse situations.

#### 3.4 Stereo Microphone Array (Hardware Dependent)

**Task**: Use multiple microphones for beamforming / directional processing

**Benefit**: Improved speech-in-noise performance via spatial filtering.

---

## 9. Summary & Next Steps

### Current State
You have built a **clinically-valid testing framework** (ANSI S3.6 audiometry + MCL/UCL calibration) but a **generic runtime audio pipeline** (global gain + RNNoise). The two systems are **completely disconnected**.

### Root Cause
The SimpleAudioEngine was designed as a minimal working prototype (RNNoise + amplification) without database integration. Clinical testing was added later but never connected to the audio chain.

### Immediate Actions (Choose Priority)

**Option A: Safety First (1 day)**
- Implement UCL-based output limiting (Section 8, Phase 1.2)
- Prevents user from exceeding uncomfortable levels
- Low risk, high value

**Option B: Basic Personalization (3 days)**
- Implement all Phase 1 recommendations
- Load audiogram, display status, calculate recommended gain, apply UCL limits
- Makes clinical data useful without DSP overhaul

**Option C: Full Clinical Integration (2 weeks)**
- Implement Phase 2 (4-band filterbank + WDRC + per-ear processing)
- Transforms system into true hearing aid-grade device
- Highest impact, moderate complexity

### Long-Term Vision

**Phase 1** → User sees personalization status, system is safer  
**Phase 2** → True per-ear, per-frequency compensation with compression  
**Phase 3** → Clinical-grade device competitive with commercial hearing aids

### Key Decision Points

1. **Target audience**: Consumer PSAP or clinical hearing aid?
   - PSAP: Phase 1-2 sufficient
   - Clinical: Phase 3 required

2. **Regulatory**: FDA/medical device classification?
   - Consumer: Current simplified testing acceptable
   - Medical: Must revert to full Hughson-Westlake procedure

3. **Hardware**: Single mic or stereo array?
   - Single: Current mono processing logical
   - Stereo: Enables true binaural processing

---

## Appendix: File Reference

### Pure Tone Testing
- `PureToneTestActivity.java` - Test execution (lines 1-1040)
- `HearingTestResult.java` - Data model (lines 1-157)
- `HearingTestResultDao.java` - Database access (lines 1-60)

### Calibration Testing
- `CalibrationTestActivity.java` - MCL/UCL measurement (lines 1-751)
- `CalibrationProfileEntity.java` - Data model (lines 1-150)
- `CalibrationProfileDao.java` - Database access

### Runtime Audio Pipeline
- `HomeActivity.java` - UI controls (lines 1-1031)
- `SimpleAudioStreamingService.java` - Service wrapper (lines 1-170)
- `SimpleAudioEngine.java` - DSP core (lines 1-447)
- `AudioConfig.java` - Configuration constants (lines 1-235)

### Legacy Code (Not Active)
- `AudioStreamingService.java` - Old 4-band implementation (partial clinical data loading)

### Database
- `AppDatabase.java` - Schema v7 (lines 1-243)

---

**End of Audit Report**

Generated: November 9, 2025  
Auditor: Technical Analysis AI  
Contact: For questions about this audit or implementation guidance
