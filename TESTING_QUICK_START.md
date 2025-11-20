# Quick Testing Guide - DSP Pipeline Refactoring

## 🎯 **Quick A/B Test (5 minutes)**

### **Setup:**
```bash
# 1. Build and install the app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. Start the app and enable audio processing
adb shell am start -n com.example.audion/.HomeActivity

# 3. Start monitoring DSP metrics
adb logcat -c
adb logcat | grep DSP_METRICS
```

### **Test 1: Baseline (No RNNoise)**
```bash
# Expected: Clean, natural sound (confirms pipeline works)
- Toggle Noise Reduction: OFF
- Set Amplification: 30 dB
- Speak into microphone
→ Should sound clear with no artifacts
```

### **Test 2: RNNoise + Low Gain**
```bash
# Expected: Clean with noise reduction
- Toggle Noise Reduction: ON
- Set Amplification: 0-10 dB
- Speak into microphone
→ Should sound natural with background noise reduced
```

### **Test 3: RNNoise + High Gain (THE CRITICAL TEST)**
```bash
# Expected: NO robotic sound (this was broken before)
- Toggle Noise Reduction: ON
- Set Amplification: 30-40 dB
- Speak into microphone
→ Should sound CLEAN and NATURAL (not robotic/metallic)
→ If it sounds robotic, the fix didn't work
```

### **Test 4: Rapid Gain Changes**
```bash
# Expected: Smooth transitions, no clicks
- Toggle Noise Reduction: ON
- Rapidly adjust amplification slider: 0 → 40 → 0 dB
- Repeat 5-10 times quickly
→ Should hear smooth gain changes without clicks or pops
```

---

## 📊 **DSP Metrics to Monitor**

Watch for these values in logcat (`DSP_METRICS` tag):

### **Normal Speech @ 30 dB Amplification:**
```
DSP_METRICS: Frame=100 | UserGain=30.0dB | PreGain=6.0dB | 
             WDRC_env=-18.2dB WDRC_GR=2.3dB | 
             Limiter_GR=0.8dB peak=-5.1dB limiting=8.5% | PreClamp=0.2%
```

**What to check:**
- ✅ **Limiter_GR < 3 dB** → Good (not over-limiting)
- ✅ **peak < -3 dB** → Good (true-peak safe)
- ✅ **limiting < 20%** → Good (not constant limiting)
- ✅ **PreClamp < 5%** → Good (pre-gain not excessive)
- ⚠️ **Limiter_GR > 5 dB** → Warning (too much limiting)
- ❌ **peak > -3 dB** → Error (limiter failed!)

### **Quiet Speech @ 40 dB Amplification:**
```
DSP_METRICS: ... | WDRC_GR=4.2dB | Limiter_GR=1.5dB peak=-4.2dB limiting=15.3%
```
**Expected:** Higher WDRC gain reduction, moderate limiting

### **Loud Music @ 10 dB Amplification:**
```
DSP_METRICS: ... | WDRC_GR=0.5dB | Limiter_GR=0.1dB peak=-8.5dB limiting=0.5%
```
**Expected:** Minimal compression/limiting (signal already loud)

---

## 🔊 **Listening Test Checklist**

### **Speech Quality (Primary Test):**
- [ ] **Intelligibility:** Can you understand all words clearly?
- [ ] **Naturalness:** Does the voice sound human (not robotic)?
- [ ] **Smoothness:** Are there any chirps or clicking sounds?
- [ ] **No pumping:** Volume doesn't "breathe" up and down
- [ ] **No harshness:** No metallic or buzzing quality

### **Music Quality (Secondary Test):**
- [ ] **Bass:** Low frequencies are present and full
- [ ] **Treble:** High frequencies are clear (not dull)
- [ ] **No distortion:** Drums/cymbals sound clean
- [ ] **Dynamics:** Soft and loud passages both sound good

### **Ambient Noise:**
- [ ] **Background reduction:** Noise is attenuated
- [ ] **Speech preserved:** Foreground speech still clear
- [ ] **No artifacts:** No "chirping" or "metallic" sounds

---

## 🐛 **Troubleshooting**

### **Problem: Still sounds robotic**
**Check:**
1. Is RNNoise actually enabled? (Check logs for "RNNoise active")
2. Is amplification > 20 dB? (Lower gains may hide artifacts)
3. Check DSP_METRICS: Is PreGain being applied? (Should be 6.0dB)

**Debug:**
```bash
adb logcat | grep -E "(DSP_METRICS|RNNoise|SimpleAudioEngine)"
```

### **Problem: Too much distortion**
**Check:**
1. DSP_METRICS: Is `Limiter_GR > 5 dB`? (Too much limiting)
2. Is `limiting > 50%`? (Constant limiting = input too loud)
3. Is `PreClamp > 20%`? (Pre-gain too high)

**Fix:**
- Reduce pre-gain: Modify `PreGainStage(6.0f)` → `PreGainStage(3.0f)`
- Or increase limiter ceiling: Modify `LookaheadLimiter(..., -3.0f, ...)` → `..., -1.0f, ...)`

### **Problem: Zipper noise (clicks when adjusting gain)**
**Check:**
1. GainSmoother attack/release times (should be 10ms/80ms)
2. Is user moving slider very quickly? (Should still be smooth)

**Debug:**
```java
// In GainSmoother.java, log transitions:
Log.d("GainSmoother", String.format("Target: %.1f dB, Current: %.1f dB", 
    targetDb, getCurrentDb()));
```

### **Problem: No sound at all**
**Check:**
1. AudioTrack initialized? (Check logs for "AudioTrack initialized")
2. Processing thread running? (Check logs for "Processing thread started")
3. Is amplification = 0? (Set to at least 10-20 dB)

**Debug:**
```bash
adb logcat | grep -E "(AudioTrack|AudioRecord|ProcessThread)"
```

---

## 📈 **Performance Validation**

### **CPU Usage:**
```bash
# Monitor CPU usage during audio processing
adb shell top | grep audion
```

**Expected:**
- CPU usage < 40% on mid-tier device (e.g., Pixel 4a)
- CPU usage < 20% on high-end device (e.g., Pixel 8)

**If too high:**
- Check DSP_METRICS frame time (should be < 4 ms per 10ms frame)
- Profile with Android Profiler in Android Studio

### **Latency:**
```bash
# Measure end-to-end latency (requires test signal)
# Speak "tick tick tick" and listen for echo
# Latency = time between speaking and hearing
```

**Expected:**
- Total latency: 35-55 ms (acceptable for hearing assistance)
- If > 100 ms: Check buffer sizes in AudioConfig

---

## 🎯 **Pass/Fail Criteria**

### **PASS if:**
- ✅ RNNoise ON + 40 dB gain = Natural speech (not robotic)
- ✅ No clicks when rapidly adjusting gain slider
- ✅ DSP_METRICS shows `peak < -3 dB` (no clipping)
- ✅ DSP_METRICS shows `Limiter_GR < 3 dB` on speech
- ✅ CPU usage < 40% of frame budget (< 4 ms per 10ms)

### **FAIL if:**
- ❌ Robotic/metallic sound with RNNoise ON + high gain
- ❌ Clicks or pops when adjusting amplification
- ❌ Peak exceeds -3 dBFS (hard clipping)
- ❌ Limiter constantly active (GR > 5 dB, limiting > 50%)
- ❌ CPU usage > 40% (frame processing > 4 ms)

---

## 📞 **Report Issues**

If tests fail, capture this information:

```bash
# 1. Full logcat during test
adb logcat -d > audion_test_log.txt

# 2. Device info
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

# 3. Test configuration
echo "RNNoise: ON/OFF"
echo "Amplification: XX dB"
echo "Audio input: Microphone / Media"
```

Then describe:
1. Which test failed (Test 1-4)
2. What you heard (robotic / clicks / distortion)
3. What DSP_METRICS showed
4. Device model and Android version

---

## 🚀 **Quick Smoke Test (30 seconds)**

```bash
# Full test in 30 seconds:
1. Open app
2. Enable Noise Reduction (toggle ON)
3. Set Amplification to 30 dB
4. Speak: "The quick brown fox jumps over the lazy dog"
5. Listen: Does it sound natural? ✅ PASS / ❌ FAIL
```

**If PASS:** ✅ Fix works! No robotic sound.  
**If FAIL:** ❌ Check DSP_METRICS logs and debug.

---

## 📚 **Advanced Testing**

### **Frequency Sweep Test:**
```bash
# Generate test tone (requires signal generator app or ADB)
# Play 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz tones
# Listen for consistent quality across frequencies
```

### **Dynamic Range Test:**
```bash
# Test soft speech (-40 dBFS input)
→ Should be audible and clear at 40 dB gain

# Test loud speech (-10 dBFS input)
→ Should not distort or clip
```

### **Latency Measurement (Advanced):**
```bash
# Use Audacity or similar tool:
1. Generate click track
2. Record input + output simultaneously
3. Measure time offset between tracks
→ Should be 35-55 ms total latency
```

---

## ✅ **Final Validation**

Before marking as DONE:
- [ ] All 4 quick tests PASS
- [ ] DSP_METRICS shows expected values
- [ ] No robotic sound with RNNoise ON + 40 dB
- [ ] No clicks when adjusting gain
- [ ] CPU usage < 40%
- [ ] Latency < 100 ms
- [ ] All listening tests PASS

**Status:** 
- [ ] ✅ **READY FOR PRODUCTION**
- [ ] ⚠️ **NEEDS TUNING** (specify issue)
- [ ] ❌ **BLOCKED** (specify blocker)
