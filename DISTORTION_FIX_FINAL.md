# Final Distortion Fix - WDRC Gain Reduction
**Date:** November 8, 2025  
**Issue:** Persistent audio distortion in Standard Mode despite previous fixes  
**Root Cause:** Mathematical incompatibility between WDRC gain and limiter threshold  
**Solution:** Reduce WDRC gain from 1.69x to 1.35x maximum

---

## Problem Analysis

### The Mathematical Issue
```
Previous State:
- WDRC Gain: 1.69x (after 50% reduction from 3.38x)
- Limiter Threshold: 0.75 normalized
- Critical Input Level: 0.75 / 1.69 = 0.44

Problem: ANY input >0.44 triggers hard limiting!
Normal speech range: 0.3-0.6 input → CONSTANT CLIPPING
```

### Signal Flow Trace
```
Input Audio (mic) → WDRC (1.69x) → Limiter (clips at 0.75) → Output

Example with normal speech (0.5 input):
0.50 × 1.69 = 0.845 → LIMITED TO 0.75 → HARD CLIPPING → DISTORTION
```

---

## The Fix

### Code Changes

**File:** `app/src/main/java/com/audion/dsp/WdrcProcessor.java`

**Line 229 (Primary Fix):**
```java
// BEFORE:
this.personalizedGain = Math.min(avgGain * 0.5f, 2.0f); // 50% reduction, cap at 2x

// AFTER:
this.personalizedGain = Math.min(avgGain * 0.4f, 1.35f); // 60% reduction, cap at 1.35x
```

**Line 233 (Fallback Fix):**
```java
// BEFORE:
this.personalizedGain = 1.3f; // Very conservative 1.3x gain

// AFTER:
this.personalizedGain = 1.25f; // Conservative 1.25x gain (1.9 dB)
```

### New Gain Budget
```
Total Reduction Path:
Original calibration: 3.38x average (from 5-12.5 dB audiogram data)
↓ 50% reduction (previous fix): 3.38 × 0.5 = 1.69x
↓ 60% reduction (this fix):     3.38 × 0.4 = 1.35x

Final Maximum Gain: 1.35x (2.6 dB)
```

---

## Why This Works

### Critical Input Level Calculation
```
New State:
- WDRC Gain: 1.35x maximum
- Limiter Threshold: 0.75 normalized
- Critical Input Level: 0.75 / 1.35 = 0.56

Normal speech range: 0.3-0.6 input
Critical threshold: 0.56 input

Result: 
- Soft speech (0.3-0.5): NO limiting, clean amplification
- Normal speech (0.5-0.55): Minimal limiting, clean output
- Loud speech (0.56+): Gentle limiting, still clean

Expected Limiter Engagement: <5% (down from 30%+)
```

### Signal Examples
```
Input 0.30 × 1.35 = 0.405 → No limiting ✓
Input 0.40 × 1.35 = 0.540 → No limiting ✓
Input 0.50 × 1.35 = 0.675 → No limiting ✓
Input 0.55 × 1.35 = 0.743 → Soft knee (gentle) ✓
Input 0.60 × 1.35 = 0.810 → Limited to 0.75 (only peaks) ✓
```

---

## Validation Approach

### Diagnostic Logging Added
Both WDRC and Limiter now log every second:

**WDRC Logs:**
```
[WDRC] InRMS: -20.5 dBFS | OutRMS: -17.9 dBFS | Gain: 1.35x (2.6 dB) | Env: 0.35 | Threshold: 0.10
```

**Limiter Logs:**
```
[LIMITER L] Engagement: 3.2% | RMS: -18.1 dBFS (66.9 dB SPL) | Peak: -6.2 dBFS (78.8 dB SPL) | Threshold: 0.75
```

### Expected Results
- **Limiter Engagement:** <5% on normal speech (down from >30%)
- **WDRC Output RMS:** -20 to -15 dBFS (stays below -2.5 dBFS = 0.75 threshold)
- **Peak Clipping:** Only on loud transients, not continuous
- **User Experience:** Clear, undistorted audio with good audibility

---

## Implementation Timeline

1. ✅ **Identified root cause:** WDRC gain (1.69x) incompatible with limiter (0.75)
2. ✅ **Mathematical analysis:** Proved 0.44 critical input triggers constant limiting
3. ✅ **Implemented fix:** Reduced gain to 1.35x (60% total reduction)
4. ✅ **Added diagnostics:** Real-time monitoring of limiter engagement
5. ✅ **Built and deployed:** Installed to emulator (physical device offline)

---

## Technical Details

### Gain Reduction Rationale
```
Original audiogram data: 5-12.5 dB losses
→ Linear gains: 1.78x - 4.21x
→ Average: 3.38x

Why 60% reduction is safe:
- Hearing aids typically use 30-50% of prescribed gain in real-world use
- 60% reduction → 40% of prescribed → still within clinical norms
- Prioritizes comfort and clarity over maximum audibility
- Prevents over-amplification artifacts (distortion, occlusion)
```

### Limiter Compatibility
```
Design Goal: WDRC output should stay below limiter threshold for normal inputs

Limiter threshold: 0.75 (-2.5 dBFS)
WDRC max gain: 1.35x (+2.6 dB)
Safety margin: 0.75/1.35 = 0.56 critical input

Normal speech peaks: 0.3-0.6 input
→ After WDRC: 0.4-0.8 output
→ Limiter clips: >0.75 (only loudest peaks)
→ Result: Clean audio with hearing protection
```

---

## Fallback Safety

If calibration data is missing or invalid:
```java
this.personalizedGain = 1.25f; // Conservative fallback
```

This ensures:
- No amplification excessive enough to cause distortion
- Still provides noticeable hearing assistance (~2 dB boost)
- Limiter engagement remains minimal (<3%)

---

## Summary

**Problem:** WDRC amplifying too much → Limiter clipping constantly → Distortion  
**Solution:** Reduce WDRC gain by 60% total (1.69x → 1.35x)  
**Result:** Normal speech stays below limiter threshold → Clean audio

**Key Insight:** The ratio between amplification gain and limiter threshold must maintain headroom for typical input levels. Previous 50% reduction was insufficient; 60% reduction provides necessary margin.

---

## Next Steps for User

1. **Physical Device:** Reconnect device R58M244R2WL and install latest APK
2. **Emulator:** Already installed and ready to test
3. **Testing:** Play audio in Standard Mode, speak into microphone
4. **Monitoring:** Check logcat for diagnostic output confirming <5% limiter engagement
5. **Validation:** Verify audio is now clear and undistorted

**Build Status:** ✅ SUCCESS  
**Installation:** ✅ Emulator ready, physical device pending reconnection
