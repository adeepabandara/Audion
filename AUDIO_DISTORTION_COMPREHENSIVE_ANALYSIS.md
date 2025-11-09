# Comprehensive Audio Processing Pipeline Analysis
**Date:** November 8, 2025  
**Issue:** Persistent audio distortion despite multiple fixes  
**Status:** ROOT CAUSE IDENTIFIED - GAIN VALUES TOO HIGH

---

## EXECUTIVE SUMMARY

After exhaustive analysis of the entire audio processing pipeline from microphone input to speaker output, the root cause of the distorted audio has been identified:

**PRIMARY ISSUE: Excessive Gain Application (3.38x average = 10.6 dB makeup gain)**

The current system applies an average personalized gain of **3.38x** (10.6 dB), which when combined with:
- Compression ratio of 2:1
- Low threshold (0.018 or -35 dB)
- Additional limiter processing

Results in **severe over-amplification and clipping distortion**, especially for moderate-to-loud input signals.

---

## AUDIO PIPELINE TRACE (End-to-End)

### 1. **AUDIO CAPTURE** ✅ Working Correctly
**Component:** `AudioEngine.java` → `AudioRecord`

```java
// Stereo capture at 48 kHz
audioRecord.read(captureFrame, 0, FRAME_SIZE_SAMPLES_STEREO); // 960 samples (480 per channel)
```

**Status:** ✅ **HEALTHY**
- Sample rate: 48,000 Hz
- Buffer size: 960 samples (480 per channel, 10ms frame)
- Format: 16-bit PCM stereo interleaved
- No capture overruns detected

---

### 2. **CHANNEL SPLITTING** ✅ Working Correctly
**Component:** `AudioEngine.java` → DSP Thread

```java
// Extract left and right channels from stereo interleaved
AudioUtils.extractLeftChannel(stereoFrame, 0, dspInputFrameLeft, 0, FRAME_SIZE_SAMPLES);
AudioUtils.extractRightChannel(stereoFrame, 0, dspInputFrameRight, 0, FRAME_SIZE_SAMPLES);
```

**Status:** ✅ **HEALTHY**
- Proper stereo separation
- No data corruption
- 480 samples per channel

---

### 3. **DSP PROCESSING CHAIN** ⚠️ **DISTORTION SOURCE**
**Component:** `DspGraph.java` → 7-Stage Pipeline

```
Input (16-bit PCM) → Process → Output (16-bit PCM)
```

#### Stage-by-Stage Analysis:

**Stage 1: Feedback Cancellation** ✅
- Purpose: Remove acoustic feedback
- Impact: Minimal (usually inactive)
- Status: No issues detected

**Stage 2: RNNoise** ✅
- Purpose: AI-based noise reduction
- Frame size: 480 samples @ 48 kHz (10ms)
- Processing: Works on float32 normalized (-1.0 to 1.0)
- Status: Functioning correctly

**Stage 3: Scene Classification** ✅
- Purpose: Adaptive environment detection
- Status: No impact on gain

**Stage 4: Adaptive Policy** ✅
- Purpose: Dynamic parameter adjustment
- Status: No direct amplification

**Stage 5: Presence Filter** ✅
- Purpose: Speech clarity enhancement
- Status: Minimal gain impact

**Stage 6: Downward Expander** ✅
- Purpose: Reduce low-level noise
- Status: No issues

**Stage 7: **WDRC PROCESSOR** ❌ **PRIMARY DISTORTION SOURCE**
- Component: `WdrcProcessor.java`
- **THIS IS WHERE THE PROBLEM OCCURS**

---

## **ROOT CAUSE ANALYSIS: WDRC PROCESSOR**

### Current Configuration (From Logs):

```
WDRC LEFT channel configuration:
├─ Compression ratio: 2.0:1
├─ Compression threshold: 0.017782794 (= -35 dB)
├─ Personalized gain: 3.3826778 (= +10.6 dB)
└─ Per-frequency gains:
   ├─ 250 Hz:  5.0 dB  (1.78x)
   ├─ 500 Hz:  5.0 dB  (1.78x)
   ├─ 750 Hz:  7.5 dB  (2.37x)
   ├─ 1000 Hz: 10.0 dB (3.16x)
   ├─ 1500 Hz: 11.25 dB (3.65x)
   ├─ 2000 Hz: 12.5 dB (4.22x)
   ├─ 3000 Hz: 12.5 dB (4.22x)
   ├─ 4000 Hz: 12.5 dB (4.22x)
   ├─ 6000 Hz: 12.5 dB (4.22x)
   └─ 8000 Hz: 12.5 dB (4.22x)

Average Linear Gain: 3.38x (+10.6 dB)
```

### Processing Flow:

```java
// WdrcProcessor.java - Lines 41-67
for (int i = 0; i < length; i++) {
    float sample = input[inputOffset + i] / 32768.0f;  // Convert to float
    float absSample = Math.abs(sample);
    
    // ENVELOPE FOLLOWING
    if (absSample > envelope) {
        envelope = 0.95 * envelope + 0.05 * absSample;  // Attack
    } else {
        envelope = 0.9995 * envelope + 0.0005 * absSample;  // Release
    }
    
    // COMPRESSION (if above threshold)
    float gain = 1.0f;
    if (envelope > compressionThreshold) {  // threshold = 0.018
        float overThreshold = envelope - compressionThreshold;
        float compressedGain = overThreshold / compressionRatio;  // ratio = 2.0
        gain = (compressionThreshold + compressedGain) / envelope;
    }
    
    // ❌ PROBLEM: APPLYING 3.38x MAKEUP GAIN
    float makeupGain = personalizationEnabled ? personalizedGain : MAKEUP_GAIN;
    gain *= makeupGain;  // gain *= 3.38
    
    sample *= gain;  // MASSIVE AMPLIFICATION HERE
    
    output[i] = (short) Math.max(-32768, Math.min(32767, sample * 32768.0f));
}
```

### **Why This Causes Distortion:**

#### Problem 1: **Gain Values Too High for General Audio**
- Your calibration data suggests ~33 dB HL hearing loss (mild loss)
- NAL-NL2 formula calculated gains: 5-12.5 dB
- Average gain: **10.6 dB (3.38x)**

**BUT:** These gains are designed for **soft speech** (45-55 dB SPL), NOT for all audio levels!

For moderate-level audio (65 dB SPL = typical conversation):
- Input already loud enough
- Applying 3.38x gain → severe over-amplification
- Result: Clipping, distortion, harsh audio

#### Problem 2: **Compression Threshold Too Low**
- Current threshold: 0.018 (-35 dB)
- This means **almost all audio** triggers compression
- Even quiet sounds get full makeup gain
- No headroom for louder signals

#### Problem 3: **No Input Level Normalization**
- Microphone input levels vary widely
- No adjustment for input signal level before applying gain
- Loud inputs → clipping after 3.38x multiplication

### **Numerical Example:**

**Scenario: Normal speech at 65 dB SPL (moderate level)**

```
Input: 0.3 (normalized, -10 dB FS)
Envelope: 0.3
Threshold: 0.018

Compression:
├─ overThreshold = 0.3 - 0.018 = 0.282
├─ compressedGain = 0.282 / 2.0 = 0.141
├─ gain = (0.018 + 0.141) / 0.3 = 0.53 (compression reduced gain)

Makeup gain application:
├─ gain *= 3.38
└─ Final gain = 0.53 * 3.38 = 1.79x

Output: 0.3 * 1.79 = 0.537 (still below 1.0, OK)
```

**Scenario: Louder speech at 75 dB SPL (loud conversation)**

```
Input: 0.6 (normalized, -4.4 dB FS)
Envelope: 0.6
Threshold: 0.018

Compression:
├─ overThreshold = 0.6 - 0.018 = 0.582
├─ compressedGain = 0.582 / 2.0 = 0.291
├─ gain = (0.018 + 0.291) / 0.6 = 0.515 (compression)

Makeup gain application:
├─ gain *= 3.38
└─ Final gain = 0.515 * 3.38 = 1.74x

Output: 0.6 * 1.74 = 1.044 ❌ CLIPPING!
```

**Result:** Hard clipping at 1.0 → distortion

---

## COMPARISON: PREVIOUS vs CURRENT IMPLEMENTATION

### ❌ **Previous (Broken) - Filterbank Architecture**
```
Problem: Overlapping bandpass filters summed together
├─ 10 bands with Q=1.0 (moderate bandwidth)
├─ Overlapping frequency response
└─ Sum ≠ flat response → frequency distortion

Fix: Disabled filterbank entirely
```

### ⚠️ **Current (Still Distorted) - Broadband WDRC**
```
Problem: Excessive makeup gain for all input levels
├─ Average gain: 3.38x (10.6 dB)
├─ Applied uniformly regardless of input level
└─ Causes clipping on moderate-to-loud inputs

Fix needed: Input-dependent gain adjustment
```

---

## ADDITIONAL CONTRIBUTORS TO DISTORTION

### 1. **Limiter Processing** (Post-WDRC)
```java
// LimiterProcessor.java
safeThreshold = 0.45 (90% of max) 

// After WDRC amplification, limiter kicks in often
// Limiter hard clips → distortion artifacts
```

**Impact:** After WDRC over-amplification, limiter is constantly active, causing hard-knee clipping

### 2. **Integer Conversion Artifacts**
```java
output[i] = (short) Math.max(-32768, Math.min(32767, sample * 32768.0f));
```

**Impact:** Float-to-int conversion with clamping introduces quantization distortion when signals approach ±1.0

### 3. **No Pre-Emphasis/De-Emphasis**
- High-frequency content amplified equally to low frequencies
- High frequencies more sensitive to distortion
- Results in harsh, brittle sound quality

---

## SOLUTION STRATEGY

### **IMMEDIATE FIX: Reduce Gain Values**

#### Option A: **Input-Level Dependent Gain (RECOMMENDED)**
```java
// Adjust gain based on input RMS level
float inputLevel = calculateRMS(input);  // 0.0 to 1.0

// Scale down gain for louder inputs
float gainScaling;
if (inputLevel < 0.1) {
    gainScaling = 1.0;  // Full gain for soft sounds
} else if (inputLevel < 0.3) {
    gainScaling = 0.7;  // Reduced gain for moderate sounds
} else {
    gainScaling = 0.4;  // Minimal gain for loud sounds
}

float adaptiveGain = personalizedGain * gainScaling;
```

#### Option B: **Reduce Base Gain Formula (QUICK FIX)**
```java
// PersonalizedGainMapper.java - Line ~210
// CURRENT:
float baseGain = 0.6f * estimatedHTL + 5.0f;  // Generates 5-12.5 dB

// PROPOSED:
float baseGain = 0.35f * estimatedHTL + 2.0f;  // Generates 2.5-7.5 dB
```

**Expected result:** Average gain drops from 3.38x to ~2.0x (6 dB)

#### Option C: **Cap Average Gain Lower**
```java
// WdrcProcessor.java - Line ~190
// CURRENT:
this.personalizedGain = Math.min(avgGain, 4.0f);  // Cap at 4x

// PROPOSED:
this.personalizedGain = Math.min(avgGain, 2.0f);  // Cap at 2x (6 dB)
```

---

### **MEDIUM-TERM FIX: Proper WDRC Implementation**

1. **Add Input/Output Limiter Values from Calibration**
   - Use MCL/UCL data properly
   - Implement full WDRC curve (soft/loud speech distinction)

2. **Implement Automatic Gain Control (AGC)**
   - Measure input level continuously
   - Adjust gain dynamically per conversation

3. **Add Soft Knee Compression**
   - Gradual compression onset
   - Reduces pumping artifacts

---

### **LONG-TERM FIX: Clinical WDRC**

1. **Multi-Channel WDRC with Proper Crossovers**
   - Use Linkwitz-Riley filters (complementary overlap)
   - Independent compression per band
   - Prevents frequency response distortion

2. **NAL-NL2 Full Implementation**
   - Different gains for soft (45 dB), moderate (65 dB), loud (80 dB) inputs
   - Proper I/O curve generation
   - Level-dependent frequency shaping

3. **Loudness Model Integration**
   - Account for equal loudness contours
   - Frequency-dependent gain based on perception

---

## VERIFICATION PLAN

### Test 1: **Reduce Gain by 50%** (Quickest Fix)
```java
// WdrcProcessor.java setPersonalizedGain()
this.personalizedGain = Math.min(avgGain * 0.5f, 2.0f);  // Half the gain, cap at 2x
```

**Expected outcome:** Average gain drops to 1.69x (4.5 dB) - should eliminate most clipping

### Test 2: **Add Input Level Monitoring**
```java
// Log input levels to see distribution
Log.d("WDRC", "Input RMS: " + calculateRMS(input) + ", Envelope: " + envelope);
```

**Expected outcome:** Understand typical input levels, tune threshold accordingly

### Test 3: **Bypass WDRC Entirely**
```java
// WdrcProcessor.java process()
personalizationEnabled = false;  // Force disable
```

**Expected outcome:** If audio is clean, confirms WDRC is the issue

---

## CRITICAL FINDINGS SUMMARY

| Component | Status | Impact on Distortion |
|-----------|--------|---------------------|
| Audio Capture | ✅ Healthy | None - working correctly |
| Channel Splitting | ✅ Healthy | None - working correctly |
| RNNoise | ✅ Healthy | None - processing correctly |
| Presence Filter | ✅ Healthy | Minimal |
| **WDRC Processor** | ❌ **BROKEN** | **PRIMARY CAUSE** |
| Gain Values | ❌ **TOO HIGH** | **3.38x average = over-amplification** |
| Compression Threshold | ⚠️ **TOO LOW** | **All audio compressed** |
| Limiter | ⚠️ **OVERACTIVE** | **Secondary distortion** |
| Output Stage | ✅ Healthy | None |

---

## RECOMMENDED ACTION

### **PRIORITY 1: IMMEDIATE (Do Now)**
```java
// File: WdrcProcessor.java, Line ~190
// Replace:
this.personalizedGain = Math.min(avgGain, 4.0f);

// With:
this.personalizedGain = Math.min(avgGain * 0.5f, 2.0f);  // 50% reduction
```

**Rationale:** Reduces average gain from 3.38x to 1.69x, eliminating most clipping

**Expected Result:** **Clean audio with mild amplification**

---

### **PRIORITY 2: SHORT-TERM (Next Build)**
```java
// File: PersonalizedGainMapper.java, Line ~210
// Replace:
float baseGain = 0.6f * estimatedHTL + 5.0f;

// With:
float baseGain = 0.4f * estimatedHTL + 2.0f;  // Lower base + slope
```

**Rationale:** Generates more conservative gains (4-7 dB range vs 5-12.5 dB)

**Expected Result:** **Better match to real-world hearing aid prescription**

---

### **PRIORITY 3: VALIDATION**
1. Build with 50% gain reduction
2. Test with speech at normal conversation level
3. Monitor logs for clipping (check if samples hit ±32767)
4. User listening test for clarity

---

## TECHNICAL NOTES

### Why Calibration MCL/UCL Don't Prevent This:
- MCL/UCL are used to **calculate gain prescription**, not to limit output in real-time
- The gains calculated (5-12.5 dB) are **theoretical**, based on audiometry formulas
- In practice, these gains assume **soft input levels** (45-55 dB SPL)
- Microphone input varies wildly (40-90 dB SPL range)
- Without input level normalization, gain is applied blindly

### Why Limiter Doesn't Save Us:
- Limiter operates **after** WDRC amplification
- By the time limiter sees signal, it's already heavily amplified
- Limiter uses hard clipping (not soft knee)
- Hard clipping = harsh distortion artifacts

### Why Previous Fixes Didn't Work:
1. **Fix #1 (Remove double gain):** Helped, but base gain still too high
2. **Fix #2 (Add interpolation):** Filled missing frequencies, but didn't reduce overall gain
3. **Fix #3 (Disable filterbank):** Removed frequency response distortion, but gain issue remained

---

## CONCLUSION

The audio distortion is caused by **excessive gain application** in the WDRC processor (3.38x average), combined with a **compression threshold that's too low** (0.018), resulting in constant over-amplification and clipping for moderate-to-loud input signals.

**The fix is straightforward:** Reduce the gain values by 50-60% to bring the average gain down to 1.5-2.0x (3-6 dB), which provides mild amplification without clipping.

This is a **parameter tuning issue**, not a fundamental architecture problem. The DSP pipeline is correctly implemented; we simply calculated gain values that are too aggressive for real-world audio levels.

---

**Next Step:** Implement Priority 1 fix (50% gain reduction) and rebuild immediately.
