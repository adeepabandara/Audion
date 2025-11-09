# Phase 6: Quick Start Guide - Continue Implementation

**Last Updated:** 2024  
**Current Status:** Foundation Complete (40%), Build ✅ SUCCESS  
**Next Session:** Complete DSP component integration

---

## Current State

### ✅ What's Working
1. **GainStagingManager** - Fully integrated into SimpleAudioEngine
   - UCL-aware gain calculation active
   - Connected to UI slider via `setAmplificationDb()`
   - Per-band gains calculated (not yet applied)

2. **AdaptiveMultibandWDRC** - Implemented, ready to integrate
   - Adaptive ratios: 1.5:1 → 2.5:1 → 4:1
   - Compiles without errors
   - Waiting to replace old MultibandWDRC

3. **DualStageLimiter** - Implemented, ready to integrate
   - Two-stage limiting: Look-ahead + tanh soft-clip
   - Compiles without errors
   - Waiting to replace old LookAheadLimiter

4. **Build System** - All green
   - No compilation errors
   - 44 tasks, 1m 56s
   - All new classes included in build

### ⏳ What's Pending
1. **SimpleAudioEngine** - Needs DSP component swap
   - Replace MultibandWDRC → AdaptiveMultibandWDRC
   - Replace LookAheadLimiter → DualStageLimiter
   - Update processPhase2() for new pipeline

2. **HomeActivity** - Needs UI text updates
   - Update warning dialog messages
   - Add UCL limiting tooltip
   - No functional changes needed

3. **Testing** - Not started
   - Sinusoid tests (1 kHz @ 60/80/100 dB)
   - Speech intelligibility verification
   - UCL compliance checking

---

## Next Session Tasks (Priority Order)

### Task 1: Replace MultibandWDRC with AdaptiveMultibandWDRC
**Time:** 10 minutes  
**Risk:** Low

**Steps:**
1. Open `SimpleAudioEngine.java`
2. Find line ~57: `private MultibandWDRC leftMultibandWDRC;`
3. Change to: `private AdaptiveMultibandWDRC leftMultibandWDRC;`
4. Repeat for `rightMultibandWDRC`
5. Find line ~229: `leftMultibandWDRC = new MultibandWDRC(...)`
6. Change to: `leftMultibandWDRC = new AdaptiveMultibandWDRC(...)`
7. Repeat for `rightMultibandWDRC`
8. Update log messages to mention "Adaptive WDRC"

**Verification:**
```bash
.\gradlew assembleDebug --no-daemon
```
Expected: ✅ Build success

---

### Task 2: Replace LookAheadLimiter with DualStageLimiter
**Time:** 10 minutes  
**Risk:** Low

**Steps:**
1. Open `SimpleAudioEngine.java`
2. Find line ~59: `private LookAheadLimiter leftLimiter;`
3. Change to: `private DualStageLimiter leftLimiter;`
4. Repeat for `rightLimiter`
5. Find line ~231: `leftLimiter = new LookAheadLimiter(...)`
6. Change to: `leftLimiter = new DualStageLimiter("LEFT", AudioConfig.SAMPLE_RATE);`
7. **Remove** the following lines (DualStageLimiter has fixed thresholds):
   ```java
   leftLimiter.setThreshold(0.9f);
   rightLimiter.setThreshold(0.9f);
   ```
8. Update log messages to mention "Dual-Stage Limiter"

**Verification:**
```bash
.\gradlew assembleDebug --no-daemon
```
Expected: ✅ Build success

---

### Task 3: Update processPhase2() - NEW PIPELINE
**Time:** 30 minutes  
**Risk:** Medium (requires careful testing)

#### Step 3.1: Add New Buffers

**Location:** SimpleAudioEngine.java, top of class (~line 105)

**Add:**
```java
// Phase 6: Per-band gain buffers
private final float[] leftWdrcInput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
private final float[] rightWdrcInput = new float[AudioConfig.FRAME_SIZE_SAMPLES];
```

#### Step 3.2: Modify processPhase2()

**Location:** Line ~545

**Find this block:**
```java
if (advancedDspEnabled && leftMultibandWDRC != null && leftLimiter != null) {
    // Phase 5: Advanced DSP chain with Multiband WDRC + Look-Ahead Limiter
    
    // Step 1: Apply Multiband WDRC (after RNNoise, before limiting)
    leftMultibandWDRC.process(floatProcessed, leftWdrcOutput, AudioConfig.FRAME_SIZE_SAMPLES);
    rightMultibandWDRC.process(floatProcessed, rightWdrcOutput, AudioConfig.FRAME_SIZE_SAMPLES);
    
    // Step 2: Apply Look-Ahead Limiter (final safety stage)
    leftLimiter.process(leftWdrcOutput, leftOutput, AudioConfig.FRAME_SIZE_SAMPLES);
    rightLimiter.process(rightWdrcOutput, rightOutput, AudioConfig.FRAME_SIZE_SAMPLES);
}
```

**Replace with:**
```java
if (advancedDspEnabled && leftMultibandWDRC != null && leftLimiter != null) {
    // Phase 6: Intelligent gain staging → Adaptive WDRC → Dual-Stage Limiter
    
    // Step 1: Get per-band gains from GainStagingManager
    float[] leftBandGains = (leftGainStaging != null) ? 
        leftGainStaging.getLeftBandGains() : new float[]{1,1,1,1,1};
    float[] rightBandGains = (rightGainStaging != null) ? 
        rightGainStaging.getRightBandGains() : new float[]{1,1,1,1,1};
    
    // Calculate average gain (simplified for now - full per-band later)
    float leftAvgGain = (leftBandGains[0] + leftBandGains[1] + leftBandGains[2] + 
                        leftBandGains[3] + leftBandGains[4]) / 5.0f;
    float rightAvgGain = (rightBandGains[0] + rightBandGains[1] + rightBandGains[2] + 
                         rightBandGains[3] + rightBandGains[4]) / 5.0f;
    
    // Apply gains BEFORE WDRC (this is the key change!)
    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
        leftWdrcInput[i] = floatProcessed[i] * leftAvgGain;
        rightWdrcInput[i] = floatProcessed[i] * rightAvgGain;
    }
    
    // Step 2: Adaptive Multiband WDRC
    leftMultibandWDRC.process(leftWdrcInput, leftWdrcOutput, AudioConfig.FRAME_SIZE_SAMPLES);
    rightMultibandWDRC.process(rightWdrcInput, rightWdrcOutput, AudioConfig.FRAME_SIZE_SAMPLES);
    
    // Step 3: Dual-Stage Limiter (final safety)
    leftLimiter.process(leftWdrcOutput, leftOutput, AudioConfig.FRAME_SIZE_SAMPLES);
    rightLimiter.process(rightWdrcOutput, rightOutput, AudioConfig.FRAME_SIZE_SAMPLES);
}
```

#### Step 3.3: Remove Old Global Gain Application

**Find this block** (around line ~600):
```java
// Apply global gain control (master volume)
float currentGain = amplificationGain.get();
if (currentGain != 1.0f) {
    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
        leftOutput[i] *= currentGain;
        rightOutput[i] *= currentGain;
    }
}
```

**DELETE IT** (gain now handled by GainStagingManager)

**OR** wrap in condition:
```java
// Legacy global gain (only for Phase 1 mode - Phase 6 uses GainStagingManager)
if (!advancedDspEnabled) {
    float currentGain = amplificationGain.get();
    if (currentGain != 1.0f) {
        for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
            leftOutput[i] *= currentGain;
            rightOutput[i] *= currentGain;
        }
    }
}
```

#### Step 3.4: Add Adaptive Loudness Tracking

**Add after WDRC processing** (line ~565):
```java
// Step 4: Update adaptive loudness control (every 100ms)
if (framesProcessed % 10 == 0) {
    // Calculate RMS of WDRC output
    float rmsSum = 0.0f;
    for (int i = 0; i < AudioConfig.FRAME_SIZE_SAMPLES; i++) {
        float sample = (leftWdrcOutput[i] + rightWdrcOutput[i]) / 2.0f;  // Average channels
        rmsSum += sample * sample;
    }
    float rms = (float) Math.sqrt(rmsSum / AudioConfig.FRAME_SIZE_SAMPLES);
    float rms_dBFS = 20.0f * (float) Math.log10(Math.max(rms, 1e-6f));
    
    // Update adaptive gain in GainStagingManager
    if (leftGainStaging != null) leftGainStaging.updateAdaptiveLoudness(rms_dBFS);
    if (rightGainStaging != null) rightGainStaging.updateAdaptiveLoudness(rms_dBFS);
}
```

**Verification:**
```bash
.\gradlew assembleDebug --no-daemon
```
Expected: ✅ Build success

**Manual Check:** Review processPhase2() to ensure:
- ✅ Gains applied BEFORE WDRC (not after)
- ✅ Global gain removed or conditionalized
- ✅ Adaptive loudness tracking added
- ✅ Focus Mode and stereo interleaving unchanged

---

### Task 4: Build and Verify
**Time:** 5 minutes  
**Risk:** Low

**Command:**
```bash
cd "c:\Users\adeepa.bandara_rootc\Documents\GitHub\Audion"
.\gradlew assembleDebug --no-daemon
```

**Expected Output:**
```
BUILD SUCCESSFUL in ~2m
44 actionable tasks: X executed, Y up-to-date
```

**If Build Fails:**
1. Check error messages for:
   - Missing method calls (check API matches)
   - Type mismatches (ensure float[] vs float)
   - Missing null checks
2. Review changes against this guide
3. Use `grep_search` to find correct method signatures

---

### Task 5: Update HomeActivity UI (Optional)
**Time:** 15 minutes  
**Risk:** Very low (UI text only)

**Location:** `app\src\main\java\com\example\audion\HomeActivity.java`

#### Change 1: Warning Dialog at 40 dB (line ~301)
**Find:**
```java
"Sound levels above 40 dB can cause hearing damage. Continue?"
```

**Replace with:**
```java
"Desired loudness above 40 dB will be limited by your UCL for safety. Continue?"
```

#### Change 2: Warning Dialog at 70 dB (line ~327)
**Find:**
```java
"WARNING: Sound levels above 70 dB can cause serious hearing damage. Are you sure?"
```

**Replace with:**
```java
"WARNING: High desired loudness will be automatically limited by your UCL. Your hearing is protected. Continue?"
```

#### Change 3: Add Tooltip (Optional)
**Add near amplificationSeekBar setup:**
```java
amplificationSeekBar.setContentDescription(
    "Desired loudness control. Your UCL limits will prevent unsafe levels.");
```

**Verification:** No build needed (UI text only)

---

## Testing Checklist

### Build Verification ✅
- [x] All classes compile without errors
- [x] No lint warnings (except deprecation)
- [x] APK generates successfully

### Functional Testing (On Device) ⏳
- [ ] **Basic Functionality**
  - [ ] App launches without crash
  - [ ] Audio pipeline starts
  - [ ] Slider responds to input
  - [ ] Sound plays in speakers/headphones

- [ ] **Gain Staging**
  - [ ] Test at 20 dB (low gain)
  - [ ] Test at 60 dB (moderate gain)
  - [ ] Test at 80 dB (high gain)
  - [ ] Test at 100 dB (max gain)
  - [ ] Verify no "broken speaker" sound at any level

- [ ] **Compression**
  - [ ] Listen for pumping/breathing artifacts
  - [ ] Verify smooth compression transitions
  - [ ] Check that soft sounds are amplified
  - [ ] Check that loud sounds don't distort

- [ ] **Limiting**
  - [ ] Play loud music (high dynamic range)
  - [ ] Verify no hard clipping
  - [ ] Listen for soft-clipping warmth (should be subtle)
  - [ ] Check that peaks are controlled

- [ ] **UCL Compliance**
  - [ ] Review logs for effective gain values
  - [ ] Verify effective gain ≤ desired gain
  - [ ] Check that UCL-5dB margin is respected
  - [ ] Test with different UCL settings

### Log Verification ⏳
**Connect device and run:**
```bash
adb logcat | grep -E "GainStagingManager|AdaptiveMultibandWDRC|DualStageLimiter"
```

**Look for:**
- ✅ "GainStagingManager initialized"
- ✅ "UCL limits set: LEFT=X dB SPL, RIGHT=Y dB SPL"
- ✅ "Desired gain: X dB → Effective gain: L=Y dB, R=Z dB"
- ✅ Adaptive loudness adjustments (every ~2s)
- ✅ No error messages or warnings

---

## Troubleshooting Guide

### Build Fails with "cannot find symbol"
**Problem:** Missing class or method  
**Solution:**
1. Check spelling of class names
2. Verify package is `com.audion.audio`
3. Ensure all files in same directory
4. Try: `.\gradlew clean assembleDebug`

### Build Fails with "method not applicable"
**Problem:** Wrong parameter types  
**Solution:**
1. Check method signature in target class
2. Verify `float` vs `float[]` types
3. Ensure `AudioConfig.FRAME_SIZE_SAMPLES` is int

### Audio Distorts at High Gain
**Problem:** Clipping somewhere in chain  
**Solution:**
1. Check log for effective gain values
2. Verify per-band gains are < 1.0 for extreme requests
3. Review adaptive loudness adjustments
4. Check limiter engagement statistics

### No Sound Output
**Problem:** Pipeline broken  
**Solution:**
1. Check if old processPhase2() code was deleted
2. Verify new pipeline has no missing steps
3. Check Focus Mode isn't muting audio
4. Review logs for "AudioTrack write incomplete"

### App Crashes on Launch
**Problem:** Null pointer or initialization issue  
**Solution:**
1. Check GainStagingManager initialization
2. Verify null checks in processPhase2()
3. Review logs for stack trace
4. Check if Phase 2 enabled in config

---

## Success Indicators

### Immediate (After Code Changes)
- ✅ Build completes without errors
- ✅ APK generates successfully
- ✅ Logs show new components initializing

### Short-Term (After Device Testing)
- ✅ Audio plays without crashes
- ✅ No "broken speaker" distortion
- ✅ Compression sounds transparent
- ✅ Limiting prevents clipping

### Long-Term (After User Testing)
- ✅ Users report improved clarity at high gains
- ✅ No complaints about distortion
- ✅ UCL compliance maintained
- ✅ Battery life unchanged (efficient processing)

---

## Quick Reference

### Key File Locations
```
SimpleAudioEngine.java:
  Line ~57:   Component declarations
  Line ~229:  Initialization in constructor
  Line ~545:  processPhase2() - MAIN INTEGRATION POINT
  Line ~720:  setAmplificationDb() - UI connection
  Line ~1129: setCalibrationData() - UCL configuration

HomeActivity.java:
  Line ~244:  Slider value calculation
  Line ~301:  Warning dialog (40 dB)
  Line ~327:  Warning dialog (70 dB)
  Line ~460:  applyGain() - Writes to preferences
```

### Log Tags to Monitor
```
GainStagingManager        - Gain calculation and UCL limiting
AdaptiveMultibandWDRC     - Compression ratios and band processing
DualStageLimiter          - Limiter engagement and clipping prevention
SimpleAudioEngine         - Overall pipeline status
```

### ADB Commands
```bash
# View logs
adb logcat -c  # Clear logs
adb logcat | grep -E "GainStaging|WDRC|Limiter"

# Check audio focus
adb shell dumpsys audio

# Monitor CPU usage
adb shell top | grep audion
```

---

## Next Steps After This Session

1. **Immediate Testing**
   - Deploy to device
   - Run basic functional tests
   - Verify no crashes or errors

2. **Iterative Refinement**
   - Fine-tune adaptive ratios if needed
   - Adjust soft-clip drive factor
   - Optimize per-band gain distribution

3. **Safety Monitor** (Optional Enhancement)
   - Create `AudioSafetyMonitor.java`
   - Add UCL watchdog (500ms interval)
   - Track limiter engagement statistics
   - Implement THD estimation

4. **Advanced Features** (Future)
   - Full per-band gain application (not averaged)
   - Audiogram-dependent band weighting
   - Adaptive target RMS based on environment
   - Real-time frequency analyzer

---

## Contact & Support

**Documentation:**
- `PHASE6_GAIN_STAGING_PLAN.md` - Complete implementation guide
- `PHASE6_IMPLEMENTATION_SUMMARY.md` - Current status and achievements
- This file (`PHASE6_QUICK_START.md`) - Step-by-step instructions

**Previous Phases:**
- `PHASE5_IMPLEMENTATION_COMPLETE.md` - Multiband WDRC + Look-Ahead Limiter
- `PHASE5_QUICK_START.md` - Phase 5 usage guide

**Key Concepts:**
- UCL Compliance: Output ≤ UCL-5dB safety margin
- Adaptive Ratios: 1.5:1 → 2.5:1 → 4:1 based on level
- Dual-Stage Limiting: Transparent + musical distortion
- Gain Staging: Intelligent pre-WDRC gain distribution

---

**Last Updated:** 2024  
**Phase 6 Status:** Foundation Complete (40%)  
**Next Milestone:** Complete DSP integration (60% → 80%)  
**Target:** Feature-complete within 1-2 sessions  

**Good luck with the implementation! 🎧**
