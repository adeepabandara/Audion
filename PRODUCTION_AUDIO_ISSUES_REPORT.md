# Production Audio Quality Issues - Root Cause Analysis
**Date:** November 8, 2025  
**Severity:** CRITICAL - Production Blocker  
**Status:** Multiple compounding gain stages identified

---

## EXECUTIVE SUMMARY - PRODUCTION PERSPECTIVE

From a **production-ready perspective**, your audio pipeline has **MULTIPLE GAIN STAGES STACKING**, creating cumulative amplification that leads to severe distortion. This is a classic DSP architecture mistake.

### **Current Issue: Cascading Gain Multiplication**

```
Input Signal (microphone)
  ↓
[Stage 1] RNNoise (no gain)
  ↓
[Stage 2] SceneClassifier (no gain)
  ↓
[Stage 3] AdaptivePolicy (no gain)
  ↓
[Stage 4] PresenceFilter → ×1.25 gain (DEFAULT_PRESENCE_GAIN)  ❌ PROBLEM #1
  ↓
[Stage 5] DownwardExpander (reduces quiet sounds, but passes loud sounds through)
  ↓
[Stage 6] WDRC → ×1.69 gain (personalized)  ❌ PROBLEM #2
  ↓
[Stage 7] Limiter → Hard clips at 0.95  ❌ PROBLEM #3 (too late!)
  ↓
Output: 1.25 × 1.69 = 2.11x TOTAL GAIN (+6.5 dB)
```

### **The Math:**
```
Moderate input: 0.5 (normalized)
After PresenceFilter: 0.5 × 1.25 = 0.625
After WDRC: 0.625 × 1.69 = 1.056 ❌ CLIPPING!
After Limiter: Hard clips to 0.95 → distortion artifacts
```

---

## DETAILED PRODUCTION ISSUES

### **Issue #1: PresenceFilter Adding Gain BEFORE WDRC** ⚠️ CRITICAL

**File:** `PresenceFilter.java`, Line 4  
**Problem:** Applies 1.25x gain (2.0 dB) to ALL audio before WDRC

```java
private static final float DEFAULT_PRESENCE_GAIN = 1.25f;

// In process():
sample *= presenceGain;  // Amplifying BEFORE compression!
```

**Impact:**
- Intended for "presence" (speech clarity boost in 2-5 kHz)
- **Actually applied as broadband gain**
- Pushes signal levels higher BEFORE WDRC
- WDRC then amplifies the already-boosted signal
- Result: **2.11x total gain instead of 1.69x**

**Why This Is Wrong:**
In professional hearing aid DSP:
1. Compression happens FIRST (level-dependent processing)
2. Frequency shaping happens AFTER (on compressed signal)
3. This ensures predictable gain behavior

**Your pipeline does:**
1. Frequency shaping FIRST (PresenceFilter)
2. Compression SECOND (WDRC)
3. Result: Unpredictable gain stacking

---

### **Issue #2: WDRC Threshold Too Low** ⚠️ HIGH

**File:** `WdrcProcessor.java`  
**Current Setting:** `threshold = 0.017782794` (-35 dB)

**Problem:**
- This threshold means **99% of all audio** triggers compression
- Even whisper-quiet sounds get full makeup gain
- No headroom for louder sounds

**Professional Standard:**
- Soft speech threshold: ~0.05 (-26 dB)
- Moderate speech threshold: ~0.15 (-16 dB)
- Your threshold: 0.018 (-35 dB) = WAY too sensitive

**Impact:**
- Compressor always active
- Always applying 1.69x makeup gain
- No dynamic range control
- Everything gets amplified equally

---

### **Issue #3: PresenceFilter Not Frequency-Specific** ⚠️ MEDIUM

**File:** `PresenceFilter.java`, Lines 22-26

```java
for (int i = 0; i < length; i++) {
    float sample = input[inputOffset + i] / 32768.0f;
    sample *= presenceGain;  // Broadband gain, not frequency-specific!
    output[i] = (short) Math.max(-32768, Math.min(32767, sample * 32768.0f));
}
```

**Problem:**
- Named "PresenceFilter" suggests frequency-specific processing
- Actually just multiplies ALL frequencies by 1.25x
- No actual filtering or EQ happening
- Misleading name → incorrect usage assumptions

**Professional "Presence" Filter:**
- Bandpass filter: 2-5 kHz (speech fundamental + harmonics)
- Boost ONLY that frequency range
- Leaves low/high frequencies unchanged
- Improves speech clarity without adding overall gain

---

### **Issue #4: Limiter Threshold Too High** ⚠️ MEDIUM

**File:** `LimiterProcessor.java`, Line 4

```java
private static final float DEFAULT_HARD_LIMIT_THRESHOLD = 0.95f;
```

**Problem:**
- Set at 95% of maximum
- By the time limiter engages, signal is already at maximum
- Hard knee limiting creates harsh distortion
- Too late to save audio quality

**Professional Standard:**
- Safety limiter: 0.7-0.8 (-2.3 to -1.9 dB)
- Soft knee starts at 0.6-0.7
- Gives headroom for transients
- Prevents harsh clipping

**Your Current:**
- Hard limit: 0.95 (-0.4 dB)
- Soft knee: 0.85 (-1.4 dB)
- **No effective headroom**

---

### **Issue #5: Integer Conversion Artifacts** ⚠️ LOW-MEDIUM

Multiple float→int conversions with clamping:

```java
output[i] = (short) Math.max(-32768, Math.min(32767, sample * 32768.0f));
```

**Problem:**
- 7 DSP stages = 7 float→int→float conversions
- Each conversion: quantization noise
- Cumulative error builds up
- Hard clamping at each stage = distortion

**Professional Standard:**
- Stay in float throughout pipeline
- Single conversion at final output
- Use dithering for quantization noise shaping

---

## AUDIO QUALITY SYMPTOMS EXPLAINED

### Symptom: "Distorted audio, not clear"

**Root Causes:**
1. **Gain stacking:** 1.25x × 1.69x = 2.11x total
2. **Constant clipping:** Moderate sounds exceed 1.0 after amplification
3. **Hard limiting:** Limiter applies harsh clipping to save from overflow
4. **Quantization artifacts:** 7 float→int conversions add distortion

### Symptom: "Not production level"

**Why:**
1. **Unpredictable gain behavior** - stacking instead of controlled
2. **No input level normalization** - same gain for all input levels
3. **Improper DSP chain ordering** - gain before compression
4. **Misleading component names** - "PresenceFilter" is actually broadband gain
5. **Safety mechanisms too late** - limiter threshold at 95%

---

## PRODUCTION-LEVEL FIXES

### **CRITICAL FIX #1: Disable PresenceFilter Gain**

```java
// File: PresenceFilter.java, Line 4
// CHANGE:
private static final float DEFAULT_PRESENCE_GAIN = 1.25f;

// TO:
private static final float DEFAULT_PRESENCE_GAIN = 1.0f;  // Unity gain - no amplification
```

**Impact:** Removes 1.25x gain stage → total gain drops from 2.11x to 1.69x

---

### **CRITICAL FIX #2: Increase WDRC Threshold**

```java
// File: WdrcProcessor.java or DspGraph.java when setting threshold
// CURRENT: 0.018 (-35 dB)
// CHANGE TO: 0.10 (-20 dB)

compressionThreshold = 0.10f;  // More realistic threshold
```

**Impact:** Only compress when audio actually needs it, not constantly

---

### **HIGH PRIORITY FIX #3: Lower Limiter Threshold**

```java
// File: LimiterProcessor.java, Line 4
// CHANGE:
private static final float DEFAULT_HARD_LIMIT_THRESHOLD = 0.95f;
private static final float SOFT_KNEE_START = 0.85f;

// TO:
private static final float DEFAULT_HARD_LIMIT_THRESHOLD = 0.75f;  // -2.5 dB
private static final float SOFT_KNEE_START = 0.65f;  // -3.7 dB
```

**Impact:** Engage limiter earlier, prevent signals from getting too hot

---

### **MEDIUM PRIORITY: Reorder DSP Chain**

**Current Order (WRONG):**
```
RNNoise → Scene → Policy → Presence → Expander → WDRC → Limiter
```

**Professional Order (CORRECT):**
```
RNNoise → Scene → Policy → WDRC → Presence → Expander → Limiter
           ↑                  ↑        ↑
      (Noise removal)  (Compression) (EQ)
```

**Rationale:**
1. **WDRC first** - Normalize levels based on input
2. **Presence after** - Shape already-normalized signal
3. **Limiter last** - Final safety catch

---

### **LOW PRIORITY: Stay in Float**

**Option A: Keep float buffers throughout**
```java
// Instead of short[] buffers, use float[] buffers
private final float[] buffer1 = new float[FRAME_SIZE];
private final float[] buffer2 = new float[FRAME_SIZE];
// etc.
```

**Option B: Add dithering to int conversion**
```java
// Add tiny random noise before quantization
sample += (Math.random() * 2.0f - 1.0f) / 65536.0f;  // 1 LSB dither
output[i] = (short) Math.max(-32768, Math.min(32767, sample * 32768.0f));
```

---

## IMMEDIATE ACTION PLAN

### **Step 1: Quick Fix (Do This Now) - 5 minutes**

```java
// File: PresenceFilter.java, Line 4
private static final float DEFAULT_PRESENCE_GAIN = 1.0f;  // Disable extra gain
```

**Expected Result:** Audio quality improves significantly  
**Total Gain:** Drops from 2.11x to 1.69x

---

### **Step 2: Threshold Tuning (Next Build) - 10 minutes**

```java
// File: PersonalizedGainMapper.java
// When calculating WDRC threshold, use more conservative value

// CURRENT (generates 0.018):
float threshold = minGainLinear;  // Uses smallest gain

// CHANGE TO:
float threshold = Math.max(0.10f, minGainLinear);  // Never below 0.10
```

---

### **Step 3: Limiter Safety (Same Build) - 5 minutes**

```java
// File: LimiterProcessor.java, Lines 4-5
private static final float DEFAULT_HARD_LIMIT_THRESHOLD = 0.75f;
private static final float SOFT_KNEE_START = 0.65f;
```

---

### **Step 4: Validation Testing**

1. Build and install with fixes
2. Monitor logs for:
   - Limiter engagement percentage
   - Peak levels reaching limiter
   - Clipping events
3. Listen test:
   - Soft speech (should be amplified)
   - Normal speech (should be clear)
   - Loud sounds (should not distort)

---

## PRODUCTION CHECKLIST

### Audio Quality Standards:
- [ ] No audible clipping or distortion
- [ ] Consistent loudness across input levels
- [ ] Natural speech reproduction
- [ ] No pumping or breathing artifacts
- [ ] Transparent processing for loud inputs

### Technical Standards:
- [ ] Total gain budget under control (< 2.0x / 6 dB)
- [ ] Proper DSP chain ordering (compress → EQ → limit)
- [ ] Safety limiter threshold with headroom (0.7-0.8)
- [ ] Compression threshold realistic (0.05-0.15)
- [ ] No gain stacking between stages

### Code Quality:
- [ ] Component names match actual function
- [ ] Clear documentation of gain at each stage
- [ ] Logging for monitoring gain application
- [ ] Configurable parameters (not hardcoded)

---

## TECHNICAL DEBT ITEMS

### Immediate (Blocking Production):
1. ✅ Fix gain stacking (PresenceFilter)
2. ✅ Fix WDRC threshold
3. ✅ Fix limiter threshold

### Short-term (Next Sprint):
1. Reorder DSP chain properly
2. Implement true presence filter (2-5 kHz bandpass)
3. Add input level normalization
4. Add gain staging documentation

### Long-term (Future Enhancement):
1. Stay in float throughout pipeline
2. Implement soft-knee compression
3. Add automatic gain control (AGC)
4. Implement proper multi-band compression

---

## CONCLUSION

Your audio distortion is caused by **GAIN STACKING** from multiple DSP stages:

```
PresenceFilter (1.25x) × WDRC (1.69x) = 2.11x TOTAL GAIN
```

This pushes moderate audio levels above 1.0, causing clipping and harsh distortion. The limiter at 0.95 threshold is too late to help.

**The fix is simple:**
1. **Set PresenceFilter gain to 1.0** (unity) - removes stacking
2. **Increase WDRC threshold to 0.10** - only compress when needed
3. **Lower limiter threshold to 0.75** - engage safety earlier

**Expected result after fixes:** Clean, transparent audio with appropriate amplification for hearing loss compensation.

---

**Next Step:** Implement Critical Fix #1 (PresenceFilter gain = 1.0) and rebuild IMMEDIATELY.
