# Phase 5 Quick Start Guide

## Overview

Phase 5 adds professional multiband WDRC + look-ahead limiting to the Audion DSP pipeline.

---

## Quick Enable

```java
// In your Activity
SimpleAudioEngine engine = new SimpleAudioEngine(true);  // true = Phase 2+3+4+5
engine.initialize();
engine.start();

// Advanced DSP enabled by default
```

---

## Configure from Audiogram

```java
// Load audiogram from database
List<HearingTestResult> leftEar = hearingTestDao.getLatestResultsForEar(userId, "LEFT");
List<HearingTestResult> rightEar = hearingTestDao.getLatestResultsForEar(userId, "RIGHT");

// Set audiogram data (auto-configures WDRC ratios based on hearing loss)
engine.setAudiogramData(leftEar, rightEar, GainFitting.FittingMode.NAL_NL2);
```

**What happens automatically:**
- Mild loss (0-25 dB HL) → 2:1 ratio, -35 dBFS threshold
- Moderate (25-40 dB) → 2.5:1 ratio, -35 dBFS
- Moderate-Severe (40-55 dB) → 3:1 ratio, -35 dBFS
- Severe (55-70 dB) → 3.5:1 ratio, -30 dBFS
- Profound (>70 dB) → 4:1 ratio, -25 dBFS

---

## Configure UCL Limits

```java
// Load calibration data from database
Map<Integer, Float> leftUCL = new HashMap<>();
leftUCL.put(250, 90.0f);   // dB HL
leftUCL.put(500, 92.0f);
leftUCL.put(1000, 95.0f);
leftUCL.put(2000, 95.0f);
leftUCL.put(4000, 93.0f);
leftUCL.put(6000, 90.0f);
leftUCL.put(8000, 88.0f);

// Set calibration (auto-configures limiter with UCL-5dB)
engine.setCalibrationData(leftMCL, leftUCL, rightMCL, rightUCL);
```

**Result:** Look-ahead limiter prevents output from exceeding UCL-5dB at any frequency.

---

## Monitor Performance

```java
// Get compression statistics
String wdrcStats = engine.getMultibandWDRCStats();
Log.i("DSP", wdrcStats);

// Output:
// ═══════════════════════════════════════
//    MULTIBAND WDRC STATISTICS
// ═══════════════════════════════════════
// 
// LEFT CHANNEL:
//   Band 0 [250-750 Hz]:
//     Max GR: 8.3 dB
//     Active: 67.2%
//   ...
```

```java
// Get limiter statistics
String limiterStats = engine.getLimiterStats();
Log.i("DSP", limiterStats);

// Output:
// ═══════════════════════════════════════
//    LOOK-AHEAD LIMITER STATISTICS
// ═══════════════════════════════════════
// 
// LEFT CHANNEL:
//   Limiting: 12.3% of samples
//   Max Attenuation: -3.2 dB
//   Threshold: -1.0 dBFS
// 
// Total Latency: 10.0 ms
```

---

## Disable Advanced DSP (Fall back to legacy)

```java
// Use legacy PerEarProcessor instead
engine.setAdvancedDspEnabled(false);

// Re-enable
engine.setAdvancedDspEnabled(true);
```

---

## DSP Chain Comparison

### Legacy (Phase 2+3+4)
```
RNNoise → PerEarProcessor (5-band + per-band WDRC) → Global limiter
```

### Advanced (Phase 5)
```
RNNoise → MultibandWDRC (5-band) → LookAheadLimiter (10ms) → Output
```

---

## Performance

- **CPU Usage:** ~45% (well within 10ms frame budget)
- **Memory:** ~62 KB (stereo)
- **Latency:** +10ms (30-35ms total)

---

## Validation Checklist

Before deployment:
- [ ] Load test audiogram → Verify WDRC ratios logged correctly
- [ ] Load test UCL data → Verify limiter threshold set
- [ ] Play loud audio → Check limiter activation in stats
- [ ] Monitor logs → Verify no warnings/errors
- [ ] Test with headphones → Verify no distortion/artifacts

---

## Troubleshooting

### "Advanced DSP not enabled" in stats

**Solution:** Check `phase2Enabled` flag in constructor:
```java
SimpleAudioEngine engine = new SimpleAudioEngine(true);  // Must be true
```

### High CPU usage / Frame time warnings

**Solution:** Disable advanced DSP temporarily:
```java
engine.setAdvancedDspEnabled(false);
```

### No compression observed

**Solution:** Check audiogram loaded:
```java
if (!engine.isPersonalizationAvailable()) {
    engine.setAudiogramData(leftEar, rightEar);
}
```

---

## Files Reference

- **MultibandWDRC.java** - Main compression processor
- **LookAheadLimiter.java** - Final safety limiter
- **BiquadFilter.java** - Filter implementation
- **SimpleAudioEngine.java** - Integration + API

---

## Support

See **PHASE5_IMPLEMENTATION_COMPLETE.md** for full technical details.
