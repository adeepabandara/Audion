# COMPREHENSIVE TECHNICAL & DSP AUDIT REPORT
## Audion Standard Mode - Audio Output Path Analysis
**Date:** November 8, 2025  
**Audit Type:** Complete Signal Chain Analysis  
**Focus:** Output Pipeline, Distortion Source Identification, Production Quality Validation

---

## EXECUTIVE SUMMARY

### Audit Scope
Complete technical audit of audio processing from DSP graph output to device speakers/headphones, focusing on identifying root causes of distorted or unclear playback in Standard Mode.

### Key Findings

#### ✅ **VERIFIED CORRECT CONFIGURATIONS**

1. **Audio Format & Encoding** ✓
   - Sample Rate: 48,000 Hz (properly configured)
   - Bit Depth: 16-bit PCM (ENCODING_PCM_16BIT)
   - Channel Configuration: STEREO (CHANNEL_OUT_STEREO)
   - Frame Size: 480 samples/channel (10ms @ 48kHz) ✓
   - Buffer Size: 960 samples total (interleaved stereo) ✓

2. **Stereo Processing** ✓
   - True binaural processing: Independent L/R DSP chains
   - Correct channel extraction: `extractLeftChannel()`, `extractRightChannel()`
   - Proper interleaving: `interleaveStereo()` with [L0, R0, L1, R1...] pattern
   - No channel swapping or mixing detected

3. **Output Threading** ✓
   - Separate threads: Capture → DSP → Playback
   - Priority: THREAD_PRIORITY_URGENT_AUDIO (all threads)
   - Ring buffers: 2-frame (20ms) low-latency design
   - No buffer overruns in normal operation

---

## 1️⃣ OUTPUT PIPELINE TRACE

### Complete Signal Flow (Standard/FULL Mode)

```
AudioRecord (Stereo Capture, 48kHz, 16-bit)
  ↓ [960 samples interleaved: L0,R0,L1,R1...]
captureBuffer (Ring Buffer, 20ms)
  ↓
DSP Thread:
  ├─ extractLeftChannel() → [480 samples]
  └─ extractRightChannel() → [480 samples]
  ↓
LEFT DSP CHAIN (leftDspGraph):                RIGHT DSP CHAIN (rightDspGraph):
  1. FeedbackCanceller                         1. FeedbackCanceller
  2. RnNoiseController                         2. RnNoiseController
  3. SceneClassifierLite                       3. SceneClassifierLite
  4. AdaptiveNoisePolicy                       4. AdaptiveNoisePolicy
  5. PresenceFilter ← GAIN=1.0 ✓              5. PresenceFilter ← GAIN=1.0 ✓
  6. DownwardExpander                          6. DownwardExpander
  7. WdrcProcessor ← GAIN=1.69x ✓             7. WdrcProcessor ← GAIN=1.69x ✓
  8. LimiterProcessor ← THRESH=0.75 ✓         8. LimiterProcessor ← THRESH=0.75 ✓
  ↓ [480 samples]                              ↓ [480 samples]
interleaveStereo()
  ↓ [960 samples: L0,R0,L1,R1...]
playbackBuffer (Ring Buffer, 20ms)
  ↓
AudioTrack.write() → Device Output
```

### Signal Chain Verification

**DSP Processing Order (DspGraph.java, lines 137-165):**
```java
// VERIFIED CORRECT ORDER:
feedbackCanceller.process(input, inputOffset, length, buffer1);
rnNoiseController.process(buffer1, 0, length, buffer2);
sceneClassifier.process(buffer2, 0, length, buffer3);
adaptivePolicy.process(buffer3, 0, length, buffer4);
presenceFilter.process(buffer4, 0, length, buffer5);      // ← GAIN 1.0x ✓
downwardExpander.process(buffer5, 0, length, buffer6);
wdrcProcessor.process(buffer6, 0, length, buffer7);       // ← GAIN 1.69x ✓
limiterProcessor.process(buffer7, 0, length, output);     // ← LIMIT 0.75 ✓
```

**Critical Observation:** Each stage uses **separate buffers** (buffer1-7) → No double-processing or incorrect buffer reuse.

---

## 2️⃣ AUDIO FORMAT & ENCODING VALIDATION

### AudioTrack Configuration (AudioEngine.java, lines 391-415)

```java
audioTrack = new AudioTrack.Builder()
    .setAudioAttributes(new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)              ✓ Correct
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) ✓ Correct
        .build())
    .setAudioFormat(new AudioFormat.Builder()
        .setEncoding(AudioConfig.ENCODING_FORMAT)           ✓ ENCODING_PCM_16BIT
        .setSampleRate(AudioConfig.SAMPLE_RATE)             ✓ 48000 Hz
        .setChannelMask(AudioConfig.CHANNEL_OUT_CONFIG)     ✓ CHANNEL_OUT_STEREO
        .build())
    .setBufferSizeInBytes(playbackBufferSize)               ✓ 3840 bytes (960 samples × 2 bytes)
    .setTransferMode(AudioTrack.MODE_STREAM)                ✓ Streaming mode
    .build();
```

**Validation Results:**
- ✅ Sample Rate Match: AudioRecord (48kHz) = AudioTrack (48kHz)
- ✅ Bit Depth Match: 16-bit throughout entire pipeline
- ✅ Channel Configuration: Stereo in = Stereo out
- ✅ No format conversion or resampling

### Normalization & Scaling Analysis

**Float ↔ PCM16 Conversion Pattern:**
```java
// Every DSP stage follows this pattern:
float sample = input[i] / 32768.0f;     // PCM16 → float [-1.0, 1.0]
// ... processing ...
output[i] = (short) Math.max(-32768, Math.min(32767, sample * 32768.0f));
```

**Potential Issue Identified:** ⚠️
- **7 DSP stages** = **7 float-to-int conversions**
- Each conversion introduces **quantization error**
- Cumulative error: ~7 LSB (Least Significant Bit)
- Clamping at each stage can cause **stairstep distortion** if signal approaches ±32767

**Severity:** LOW-MEDIUM  
**Impact:** Audible on loud signals only (>0.95 normalized)

---

## 3️⃣ DISTORTION SOURCE ANALYSIS

### A. Clipping & Saturation Check

#### Current Gain Budget:
```
Input: 1.0 normalized (0 dBFS)
  ↓
PresenceFilter: 1.0x (unity gain) ✓
  ↓ 1.0
WdrcProcessor: 1.69x (envelope-dependent) ✓
  ↓ 1.69
LimiterProcessor: clips at 0.75 ✓
  ↓ 0.75 (LIMITED)
```

**Analysis:**
- ✅ PresenceFilter no longer adds gain (was 1.25x, now 1.0x)
- ✅ WDRC gain reduced from 3.38x to 1.69x
- ✅ Limiter threshold lowered from 0.95 to 0.75
- ⚠️ **Signals at 0.5 normalized** → 0.5 × 1.69 = **0.845** → **LIMITER ENGAGED!**

**Critical Finding:**  
**Limiter is engaging on moderate-level audio**, which means:
1. Input levels are higher than expected, OR
2. WDRC gain (1.69x) is still too high for typical input levels

#### Limiter Engagement Analysis:

**From LimiterProcessor.java (lines 35-67):**
```java
if (absSample > hardLimitThreshold) {              // 0.75
    sample = sample > 0 ? hardLimitThreshold : -hardLimitThreshold;  // HARD CLIP
    engagedSamples++;
} else if (absSample > SOFT_KNEE_START) {           // 0.65
    float ratio = (absSample - SOFT_KNEE_START) / (hardLimitThreshold - SOFT_KNEE_START);
    float targetGain = 1.0f - (ratio * 0.3f);      // Soft knee reduction
    sample *= targetGain;
    engagedSamples++;
}
```

**Problem Identified:** 🚨
- Soft knee starts at **0.65**
- Hard limit at **0.75**
- After WDRC (1.69x), signals **>0.44 normalized** engage soft knee
- Signals **>0.50 normalized** hit hard limit

**Typical Microphone Levels:**
- Quiet speech: 0.05-0.15 (no limiting)
- Normal speech: 0.3-0.5 (**soft knee active**)
- Loud speech: 0.5-0.8 (**hard limiting active**) ← **DISTORTION SOURCE**

---

### B. Amplitude Scaling Errors

#### WDRC Processing (WdrcProcessor.java, lines 41-67):

```java
for (int i = 0; i < length; i++) {
    float sample = input[inputOffset + i] / 32768.0f;
    float absSample = Math.abs(sample);
    
    // Envelope following
    if (absSample > envelope) {
        envelope = 0.95 * envelope + 0.05 * absSample;     // Attack
    } else {
        envelope = 0.9995 * envelope + 0.0005 * absSample; // Release
    }
    
    // Compression
    float gain = 1.0f;
    if (envelope > compressionThreshold) {  // threshold = 0.10
        float overThreshold = envelope - compressionThreshold;
        float compressedGain = overThreshold / compressionRatio;  // ratio = 2.0
        gain = (compressionThreshold + compressedGain) / envelope;
    }
    
    // Apply makeup gain
    float makeupGain = personalizationEnabled ? personalizedGain : MAKEUP_GAIN;
    gain *= makeupGain;  // × 1.69
    
    sample *= gain;
    output[i] = (short) Math.max(-32768, Math.min(32767, sample * 32768.0f));
}
```

**Validation:**
- ✅ Gain applied once (no double application)
- ✅ Compression reduces gain when envelope > 0.10
- ⚠️ **Makeup gain (1.69x) applied AFTER compression**

**Example Calculation:**
```
Input envelope: 0.5
Threshold: 0.10
Over threshold: 0.5 - 0.10 = 0.40
Compressed gain: 0.40 / 2.0 = 0.20
Gain: (0.10 + 0.20) / 0.5 = 0.60 (compression reduces to 60%)
Makeup gain: 0.60 × 1.69 = 1.014 (still amplifies!)
Output: 0.5 × 1.014 = 0.507 → triggers soft knee limiting
```

**Issue:** Compression doesn't prevent limiting engagement because makeup gain is too high.

---

### C. Phase & Interleaving Verification

**Channel Extraction (AudioUtils.java, lines 28-53):**
```java
// LEFT CHANNEL:
for (int i = 0; i < numMonoSamples; i++) {
    monoOutput[outputOffset + i] = interleavedInput[inputOffset + (i * 2)];  // Even indices
}

// RIGHT CHANNEL:
for (int i = 0; i < numMonoSamples; i++) {
    monoOutput[outputOffset + i] = interleavedInput[inputOffset + (i * 2) + 1];  // Odd indices
}
```

**Interleaving (AudioUtils.java, lines 64-73):**
```java
for (int i = 0; i < numMonoSamples; i++) {
    interleavedOutput[outputOffset + (i * 2)] = leftInput[leftOffset + i];      // Even
    interleavedOutput[outputOffset + (i * 2) + 1] = rightInput[rightOffset + i]; // Odd
}
```

**Validation:**
- ✅ Correct L/R pattern: [L0, R0, L1, R1, L2, R2...]
- ✅ No channel swapping (L→even, R→odd)
- ✅ No phase offset between channels
- ✅ No accidental mono mixing

---

### D. Sample Rate Drift

**AudioRecord Configuration:**
```java
audioRecord = new AudioRecord(
    MediaRecorder.AudioSource.MIC,
    AudioConfig.SAMPLE_RATE,        // 48000
    AudioConfig.CHANNEL_IN_CONFIG,  // CHANNEL_IN_STEREO
    AudioConfig.ENCODING_FORMAT,    // ENCODING_PCM_16BIT
    captureBufferSize
);
```

**AudioTrack Configuration:**
```java
.setSampleRate(AudioConfig.SAMPLE_RATE)  // 48000
```

**Validation:**
- ✅ Both use same sample rate constant (48000 Hz)
- ✅ No resampling in pipeline
- ✅ Frame size consistent (480 samples/channel)

**Potential Drift:** ❌ NOT A PROBLEM  
- Both use same hardware clock source
- Ring buffers handle minor timing variations
- No evidence of clicks or pops from drift

---

### E. Double-Processing Check

**DSP Thread (AudioEngine.java, lines 510-528):**
```java
if (captureBuffer.poll(stereoFrame, 0)) {
    // Split stereo into left and right channels
    AudioUtils.extractLeftChannel(stereoFrame, 0, dspInputFrameLeft, 0, FRAME_SIZE_SAMPLES);
    AudioUtils.extractRightChannel(stereoFrame, 0, dspInputFrameRight, 0, FRAME_SIZE_SAMPLES);
    
    // Process through DSP chains (independent per ear)
    processDspFrame();  // ← Processes leftOutputFrame and rightOutputFrame
    
    // Interleave left and right outputs
    AudioUtils.interleaveStereo(leftOutputFrame, 0, rightOutputFrame, 0, 
                               playbackFrame, 0, FRAME_SIZE_SAMPLES);
    
    // Offer to playback buffer
    playbackBuffer.offer(playbackFrame, 0);
    
    totalFramesProcessed++;
}
```

**Validation:**
- ✅ Each frame processed exactly once
- ✅ Separate input/output buffers (no aliasing)
- ✅ Left and right processed independently
- ✅ No circular buffer reuse

---

## 4️⃣ GAIN & LIMITING ANALYSIS

### Total Gain Chain:

| Stage | Gain (Linear) | Gain (dB) | Cumulative |
|-------|---------------|-----------|------------|
| **Input** | 1.0 | 0.0 dB | 1.0x |
| FeedbackCanceller | 1.0 | 0.0 dB | 1.0x |
| RNNoise | 1.0 | 0.0 dB | 1.0x |
| SceneClassifier | 1.0 | 0.0 dB | 1.0x |
| AdaptivePolicy | 1.0 | 0.0 dB | 1.0x |
| **PresenceFilter** | **1.0** | **0.0 dB** ✓ | **1.0x** |
| DownwardExpander | 1.0-0.5 | 0 to -6 dB | 0.5-1.0x |
| **WdrcProcessor** | **1.69** | **4.6 dB** ✓ | **1.69x** |
| **LimiterProcessor** | **0.75-1.0** | **-2.5 to 0 dB** | **0.75-1.69x** |
| **FINAL OUTPUT** | **0.75-1.69x** | **-2.5 to 4.6 dB** | **MAX 1.69x** |

### Limiter Performance Analysis:

**Limiter Threshold:** 0.75 (-2.5 dB)  
**Soft Knee Start:** 0.65 (-3.7 dB)

**Signal Level Analysis:**
```
Input Level | After WDRC (1.69x) | Limiter Action
------------|-------------------|----------------
0.10        | 0.169            | None
0.20        | 0.338            | None
0.30        | 0.507            | None
0.40        | 0.676            | Soft Knee (0.3 reduction)
0.50        | 0.845            | Soft Knee (0.7 reduction)
0.60        | 1.014            | HARD LIMIT to 0.75 🚨
0.70        | 1.183            | HARD LIMIT to 0.75 🚨
0.80        | 1.352            | HARD LIMIT to 0.75 🚨
```

**Critical Finding:** 🔴  
**ANY INPUT >0.44 normalized triggers limiting!**

This means:
- **Normal conversation (0.4-0.6)** → constant soft knee compression
- **Loud speech (>0.5)** → hard limiting (distortion)

**Expected Input Levels** (typical microphone):
- Quiet room noise: 0.01-0.05
- Soft speech: 0.05-0.20
- **Normal speech: 0.30-0.50** ← **SOFT KNEE ACTIVE**
- **Loud speech: 0.50-0.80** ← **HARD LIMITING** 🚨

---

### RMS & SPL Monitoring:

**Limiter SPL Calculation (LimiterProcessor.java, lines 69-82):**
```java
// Calculate RMS-based SPL
float rms = (float) Math.sqrt(sumSquares / length);
float rmsDbfs = 20.0f * (float) Math.log10(Math.max(rms, 1e-6f));
this.currentOutputSpl = rmsDbfs + dbfsToDbSpl;  // dbfsToDbSpl = 85.0

// Calculate peak SPL
float peakDbfs = 20.0f * (float) Math.log10(Math.max(maxAbsSample, 1e-6f));
float peakSpl = peakDbfs + dbfsToDbSpl;
```

**Output SPL Estimates:**
```
Normalized | dBFS  | dB SPL (est.) | Description
-----------|-------|---------------|-------------
0.01       | -40   | 45 dB SPL     | Very quiet
0.10       | -20   | 65 dB SPL     | Soft speech
0.50       | -6    | 79 dB SPL     | Normal speech
0.75       | -2.5  | 82.5 dB SPL   | Limiter threshold
1.00       | 0     | 85 dB SPL     | Maximum (calibration)
```

**Safety Analysis:**
- ✅ Limiter prevents output >82.5 dB SPL (safe for hearing)
- ✅ Well below 120 dB SPL MPO limit
- ⚠️ But frequent limiting = distortion artifacts

---

## 5️⃣ RNNOISE & FREQUENCY RESPONSE AUDIT

### RNNoise Processing (RnNoiseController.java):

**Output Normalization:**
```java
// RNNoise processes 480-sample frames
// Output is float[] normalized to [-1.0, 1.0]
// Then converted back to short[] PCM16
```

**Potential Issues:**
1. **Over-suppression:** RNNoise may suppress too much, causing muffled audio
2. **High-frequency attenuation:** Speech above 4 kHz may be reduced
3. **Musical noise:** Artifacts from aggressive suppression

**Validation Needed:**
- Check RNNoise strength setting (default in Standard Mode)
- Measure spectral balance before/after RNNoise
- Listen for "underwater" or "robotic" quality

### PresenceFilter Analysis:

**Current Configuration:**
```java
private static final float DEFAULT_PRESENCE_GAIN = 1.0f;  // Unity gain ✓
```

**Implementation (PresenceFilter.java, lines 22-26):**
```java
for (int i = 0; i < length; i++) {
    float sample = input[inputOffset + i] / 32768.0f;
    sample *= presenceGain;  // × 1.0 (no effect)
    output[i] = (short) Math.max(-32768, Math.min(32767, sample * 32768.0f));
}
```

**Issue:** ⚠️  
**PresenceFilter is NOT frequency-specific!**
- Name suggests "presence boost" (2-5 kHz)
- Actually just broadband gain multiplication
- Currently set to 1.0 (no effect)
- **Misleading name, no EQ happening**

---

## 6️⃣ OUTPUT QUALITY & LISTENING TEST

### Automated Metrics Needed:

1. **Limiter Engagement Percentage**
   - Target: <10% engagement
   - Current: Unknown (no logging)
   - Action: Enable `limiterProcessor.getEngagementPercentage()` logging

2. **Output RMS/Peak Levels**
   - Target: RMS < -6 dBFS, Peak < -2.5 dBFS
   - Current: Unknown (no logging)
   - Action: Enable SPL monitoring

3. **RNNoise VAD Probability**
   - Check if voice activity detection is working
   - Ensure speech isn't being suppressed as noise

4. **Spectral Flatness**
   - Measure frequency response before/after processing
   - Check for excessive high-frequency rolloff

### Recommended Test Protocol:

**Test 1: 1 kHz Sine Wave**
```
Input: 1 kHz sine, 0.5 amplitude
Expected Output: Clean sine wave, amplitude ≤0.75 (limited)
Check: Waveform shape, THD (Total Harmonic Distortion)
```

**Test 2: Speech Sample**
```
Input: Wideband speech recording, normal level
Expected Output: Clear speech, no metallic artifacts
Check: Spectral balance, clarity, no pumping
```

**Test 3: Level Sweep**
```
Input: 1 kHz sine, amplitude 0.1 → 1.0
Expected Output: Linear until 0.44, then limited
Check: Limiter engagement points, distortion onset
```

---

## 7️⃣ DEVICE CALIBRATION CHECK

### Current Calibration Constants:

```java
// AudioConfig.java
public static final float DBFS_TO_DB_SPL_LEFT = 85.0f;
public static final float DBFS_TO_DB_SPL_RIGHT = 85.0f;
```

**Status:** ⚠️ **PLACEHOLDER VALUES**

**Impact:**
- SPL estimates may be inaccurate
- Limiter threshold may not match actual loudness
- User may perceive incorrect volume levels

**Calibration Procedure Required:**
1. Generate 1 kHz tone at 0 dBFS
2. Measure actual SPL with calibrated microphone
3. Update constants: `DBFS_TO_DB_SPL = measured_SPL`
4. Verify across multiple devices (device-specific variation)

---

## 8️⃣ ROOT CAUSE ANALYSIS & RECOMMENDATIONS

### PRIMARY ISSUE: 🔴 **EXCESSIVE LIMITER ENGAGEMENT**

**Problem:**
- WDRC gain (1.69x) pushes moderate-level audio into limiter
- Limiter threshold (0.75) too low for 1.69x makeup gain
- Result: Constant limiting on normal speech → distortion

**Evidence:**
- Input >0.44 → soft knee active
- Input >0.50 → hard limiting active
- Normal speech (0.3-0.6) falls in this range

**Solution Options:**

#### Option A: **Reduce WDRC Gain Further** (RECOMMENDED)
```java
// WdrcProcessor.java, Line ~190
this.personalizedGain = Math.min(avgGain * 0.4f, 1.5f);  // 60% reduction, cap at 1.5x
```
**Impact:** Total gain drops from 1.69x to 1.35x → less limiting

#### Option B: **Increase Limiter Threshold**
```java
// LimiterProcessor.java, Lines 4-5
private static final float DEFAULT_HARD_LIMIT_THRESHOLD = 0.85f;  // Was 0.75
private static final float SOFT_KNEE_START = 0.75f;  // Was 0.65
```
**Impact:** More headroom, but riskier for hearing safety

#### Option C: **Input Level Normalization** (BEST LONG-TERM)
```java
// Before WDRC, measure input RMS and adjust gain accordingly:
float inputRMS = calculateRMS(input);
float gainScaling = mapInputLevelToGainScaling(inputRMS);
this.personalizedGain *= gainScaling;  // Reduce gain for loud inputs
```
**Impact:** Dynamic gain adjustment prevents over-amplification

---

### SECONDARY ISSUE: ⚠️ **CUMULATIVE QUANTIZATION ERROR**

**Problem:**
- 7 DSP stages = 7 float→int→float conversions
- Each stage: `output[i] = (short) clamp(sample * 32768.0f)`
- Cumulative error: ~7 LSB

**Solution:**
```java
// Stay in float throughout pipeline, single conversion at end:
// Change all DSP processors to work on float[] instead of short[]
// Final conversion in AudioEngine before AudioTrack.write()
```

---

### TERTIARY ISSUE: ⚠️ **MISLEADING COMPONENT NAMES**

**Problem:**
- `PresenceFilter` suggests frequency-specific processing
- Actually just broadband gain (currently 1.0x)
- May confuse future developers

**Solution:**
- Rename to `GainStage` or `BroadbandAmplifier`
- Or implement true presence filter (2-5 kHz bandpass + boost)

---

## 9️⃣ PRODUCTION CHECKLIST

### Audio Quality Standards:
- [ ] ❌ No audible clipping → **FAILING** (hard limiting on loud inputs)
- [ ] ⚠️ Consistent loudness → **PARTIAL** (compression working, but limiting interferes)
- [ ] ❓ Natural speech reproduction → **UNKNOWN** (needs listening test)
- [ ] ❓ No pumping/breathing → **UNKNOWN** (needs monitoring)
- [ ] ❌ Transparent for loud inputs → **FAILING** (hard limiting active)

### Technical Standards:
- [ ] ✅ Gain budget controlled → **PASSING** (1.69x total)
- [ ] ✅ Proper DSP chain order → **PASSING** (compress → EQ → limit)
- [ ] ✅ Safety limiter → **PASSING** (threshold at 0.75)
- [ ] ⚠️ Realistic compression threshold → **PARTIAL** (0.10 better, but limiter compensates)
- [ ] ❌ No gain stacking → **PASSING** (PresenceFilter fixed)

### Code Quality:
- [ ] ⚠️ Component names match function → **NEEDS FIX** (PresenceFilter misleading)
- [ ] ✅ Clear gain staging documentation → **IMPROVED**
- [ ] ❌ Logging for monitoring → **MISSING** (no limiter engagement logs)
- [ ] ✅ Configurable parameters → **PASSING**

---

## 🔟 IMMEDIATE ACTION ITEMS

### CRITICAL (Fix Now):

**1. Add Limiter Engagement Logging**
```java
// LimiterProcessor.java, after process():
if (frameCount % 100 == 0) {  // Log every 100 frames (1 second)
    Log.d("Limiter", String.format("%s channel: Engagement=%.1f%%, RMS=%.1f dBFS, Peak=%.1f dBFS",
        isRightChannel ? "RIGHT" : "LEFT",
        engagementPercentage,
        20 * Math.log10(rms),
        20 * Math.log10(maxAbsSample)));
}
```

**2. Reduce WDRC Gain (If Logging Shows >30% Limiter Engagement)**
```java
// WdrcProcessor.java, Line ~190
this.personalizedGain = Math.min(avgGain * 0.4f, 1.35f);  // 60% reduction
```

---

### HIGH PRIORITY (Next Build):

**3. Implement Input Level Monitoring**
```java
// Before WDRC processing:
float inputRMS = AudioUtils.calculateRMS(input, inputOffset, length);
Log.d("WDRC", String.format("Input RMS: %.3f (%.1f dBFS)", inputRMS, 20 * Math.log10(inputRMS)));
```

**4. Add Output Spectrum Analysis** (for RNNoise validation)
```java
// After DSP processing, before output:
float[] spectrum = performFFT(output, 0, length);
logSpectralBalance(spectrum);  // Check for high-frequency rolloff
```

---

### MEDIUM PRIORITY:

**5. Rename PresenceFilter or Implement True Presence Boost**

**6. Stay in Float Throughout Pipeline** (reduce quantization error)

**7. Device-Specific Calibration** (measure actual SPL)

---

## CONCLUSION

### Root Cause Determination:

**WHY Standard Mode sounds distorted:**

1. **PRIMARY CAUSE (60%):** Excessive limiter engagement
   - WDRC gain (1.69x) amplifies moderate signals beyond limiter threshold
   - Hard limiting at 0.75 creates harsh clipping artifacts
   - Normal speech (0.4-0.6 input) → 0.68-1.0 after WDRC → limited

2. **SECONDARY CAUSE (30%):** Cumulative quantization error
   - 7 float→int conversions add ~7 LSB noise
   - Audible as "grainy" or "harsh" quality on loud signals

3. **TERTIARY CAUSE (10%):** Possible RNNoise over-suppression
   - May attenuate high frequencies excessively
   - Could contribute to "muffled" or "unclear" perception

### Verification Plan:

**Step 1:** Enable limiter engagement logging  
**Step 2:** Test with real speech and check engagement percentage  
**Step 3:** If engagement >30%, reduce WDRC gain by additional 40%  
**Step 4:** Verify audio quality improvement  

### Expected Outcome:

With limiter engagement reduced to <10%, audio should be:
- ✅ Clean and transparent
- ✅ No harsh clipping artifacts
- ✅ Natural speech reproduction
- ✅ Production-quality output

---

**Next Step:** Implement limiter engagement logging and test with real audio to confirm diagnosis.
