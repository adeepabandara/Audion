# Audion Audio Processing Pipeline - Complete Flow Explanation

**Date:** November 8, 2025  
**Current Implementation:** Legacy Pipeline (Original Audion Architecture)

---

## 🎯 OVERVIEW

The current audio processing flow uses a **real-time, low-latency pipeline** that captures microphone input, processes it through multiple stages, and outputs to speakers/headphones. The pipeline operates at **48 kHz sample rate** with **10ms frames (480 samples)** in **mono**.

---

## 📊 HIGH-LEVEL ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    AudioStreamingService (Foreground)                   │
│                                                                           │
│  Creates → AudioProcessor + RnNoiseProcessor                            │
│  Starts → Dedicated processing thread (THREAD_PRIORITY_URGENT_AUDIO)    │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                         AudioProcessor.start()                          │
│                     (Runs in background thread)                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🔄 COMPLETE SIGNAL FLOW

### **1. INITIALIZATION (One-time setup)**

**Location:** `AudioProcessor.start()` (lines 58-100)

```
AudioRecord Setup
├─ Source: MediaRecorder.AudioSource.DEFAULT (device microphone)
├─ Sample Rate: 48000 Hz
├─ Channel: MONO (single channel)
├─ Format: PCM_FLOAT (32-bit floating point, range -1.0 to +1.0)
└─ Buffer Size: Minimum required for 48kHz mono (typically ~3840 bytes)

AudioTrack Setup
├─ Stream: AudioManager.STREAM_MUSIC
├─ Sample Rate: 48000 Hz
├─ Channel: MONO
├─ Format: PCM_FLOAT (32-bit floating point)
└─ Buffer Size: Minimum required for 48kHz mono
└─ Mode: MODE_STREAM (continuous playback, not one-shot)

4-Band Filterbank Initialization
├─ Band 1: 250 Hz - 750 Hz   (low frequencies, bass)
├─ Band 2: 750 Hz - 1500 Hz  (low-mid frequencies)
├─ Band 3: 1500 Hz - 3000 Hz (mid-high frequencies, speech clarity)
└─ Band 4: 3000 Hz - 6000 Hz (high frequencies, consonants)

Preallocated Buffers (avoid garbage collection during processing)
├─ inputBuf[480]      → Raw microphone input
├─ denoiseBuf[480]    → After RNNoise processing
├─ bandBufs[4][480]   → Per-band filtered outputs
└─ procBuf[480]       → Final processed output
```

**Key Point:** All buffers are allocated once and reused. No memory allocation during real-time processing.

---

### **2. PROCESSING LOOP (Runs continuously)**

**Location:** `AudioProcessor.start()` main loop (lines 113-260)

#### **Step 2.1: Read Preferences (Every Frame)**

**Lines 115-117**

```java
bypassMode = prefs.getBoolean(KEY_BYPASS_MODE, false);
noiseReductionEnabled = prefs.getBoolean(KEY_NOISE_REMOVAL, true);
globalGain = prefs.getFloat(KEY_AMPLIFICATION, 1.0f);
```

**Purpose:** Allow real-time toggle changes without restarting audio
- `bypassMode` = Skip all DSP, just apply gain + limiter
- `noiseReductionEnabled` = Enable/disable RNNoise
- `globalGain` = Master amplification factor (0.5 = half volume, 1.0 = unity, 1.5 = 50% boost)

---

#### **Step 2.2: Capture Audio Frame**

**Lines 120-128**

```
AudioRecord.read(inputBuf, 0, FRAME_SIZE, READ_BLOCKING)
├─ Reads exactly 480 samples (10ms @ 48kHz)
├─ Blocking call: waits until data available
├─ Returns: number of samples read (should be 480)
└─ If partial read: zero-pad the rest

Input Buffer (inputBuf[480])
└─ Float samples in range [-1.0, +1.0]
└─ Example: [0.0012, -0.0034, 0.0089, ...]
```

**Typical Input Levels:**
- Silence: RMS ≈ 0.0001, Peak ≈ 0.001
- Normal speech: RMS ≈ 0.01-0.05, Peak ≈ 0.1-0.3
- Loud speech: RMS ≈ 0.05-0.15, Peak ≈ 0.3-0.7

---

### **3. PROCESSING PATH A: BYPASS MODE**

**Lines 131-160** (When `bypassMode = true`)

```
FOR each sample in inputBuf[480]:
    1. Multiply by globalGain
       sample = inputBuf[i] * globalGain
       
    2. Hard limiting (prevent clipping)
       IF sample > 0.95:  sample = 0.95
       IF sample < -0.95: sample = -0.95
       
    3. Store in procBuf
       procBuf[i] = sample

THEN write procBuf → AudioTrack → Speakers
```

**Bypass Mode Characteristics:**
- ✅ Lowest latency (~20-30ms total)
- ✅ Minimal CPU usage
- ✅ No frequency shaping
- ✅ Good for testing if DSP is causing distortion
- ❌ No noise reduction
- ❌ No frequency-specific amplification

**Log Output Example:**
```
[BYPASS] Frame 0 | Gain=1.00 | Output[RMS=0.0234 Peak=0.4567]
```

---

### **4. PROCESSING PATH B: FULL MODE (4-Band Pipeline)**

**Lines 162-250** (When `bypassMode = false`)

This is the **original Audion hearing aid pipeline**.

---

#### **Stage 1: RNNoise Noise Reduction**

**Lines 182-187**

```
IF noiseReductionEnabled:
    RnNoiseProcessor.process(inputBuf → denoiseBuf)
ELSE:
    Copy inputBuf → denoiseBuf (passthrough)
```

**RNNoise Details (`RnNoiseProcessor.java`):**
- Uses native C++ library (`rnnoise_jni.so`)
- Deep learning-based noise suppression
- Trained on human speech patterns
- Removes stationary noise (fan, A/C, hum)
- Preserves speech intelligibility

**Process:**
1. Check if native library is available (`nativeProbe()`)
2. If available: Call `nativeProcessFrame(in, out, 480)`
3. If not available: Passthrough (copy input to output)

**Effect:**
- Reduces background noise by 10-25 dB
- Typically reduces RMS by 10-30% in noisy environments
- Speech RMS may slightly increase (noise floor lowered)

**Example:**
```
Input:  RMS=0.0345 Peak=0.2134  (speech + background noise)
Output: RMS=0.0289 Peak=0.2089  (noise reduced, speech preserved)
```

---

#### **Stage 2: 4-Band Filterbank**

**Lines 199-201**

```
FOR each band (0 to 3):
    BandPassFilter[band].process(denoiseBuf → bandBufs[band])
```

**Purpose:** Split audio into 4 frequency bands for independent amplification.

**Band Definitions (`AudioProcessor.java` lines 51-54):**

```
Band 0: 250-750 Hz    → Bass, vowel fundamentals
Band 1: 750-1500 Hz   → Low-mid vowels, voice body
Band 2: 1500-3000 Hz  → Speech clarity, formants
Band 3: 3000-6000 Hz  → Consonants, fricatives (s, f, th)
```

**BandPassFilter Implementation (`BandPassFilter.java`):**

**Filter Type:** 2nd-order Butterworth bandpass (RBJ cookbook design)

**Algorithm (Biquad IIR filter):**
```
FOR each sample:
    // Biquad difference equation
    y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
    
    // Update state variables
    x[n-2] = x[n-1]
    x[n-1] = x[n]
    y[n-2] = y[n-1]
    y[n-1] = y[n]
```

**Filter Design Parameters:**
- Center frequency `f0 = (lowHz + highHz) / 2`
- Bandwidth = `highHz - lowHz`
- Q factor = `f0 / bandwidth`
- Coefficients computed using RBJ formulas

**Example for Band 2 (1500-3000 Hz):**
```
f0 = (1500 + 3000) / 2 = 2250 Hz
bandwidth = 3000 - 1500 = 1500 Hz
Q = 2250 / 1500 = 1.5

Coefficients (approximate):
b0 =  0.123
b1 =  0.0
b2 = -0.123
a1 = -1.234
a2 =  0.756
```

**Output:** Each `bandBufs[b][480]` contains only frequencies in that band's range.

---

#### **Stage 3: Per-Band Gain Calculation**

**Lines 203-217**

```
FOR each band (0 to 3):
    Read from SharedPreferences:
    ├─ baseline[b]    = prefs.getFloat("baseline_b" + b, 1.0)
    ├─ userOverride[b] = prefs.getFloat("user_override_b" + b, 1.0)
    
    Calculate final gain:
    bandGain[b] = globalGain × baseline[b] × userOverride[b]
```

**Gain Components:**

1. **`globalGain`** (Master amplification)
   - Set by user via amplification toggle
   - Default: 1.0 (unity)
   - Range: 0.0 to 2.0 typically
   - Example: 1.5 = 50% volume increase

2. **`baseline[b]`** (Calibration-based)
   - Per-band baseline from calibration test
   - Compensates for individual hearing loss
   - Default: 1.0 for each band if no calibration
   - Example: [1.0, 1.2, 1.5, 1.3] = more boost for mid-highs

3. **`userOverride[b]`** (Manual fine-tuning)
   - User can adjust individual bands
   - Default: 1.0 for each band
   - Example: [1.0, 0.9, 1.1, 1.2] = custom EQ

**Final Gain Example:**
```
globalGain = 1.2  (20% overall boost)
baseline = [1.0, 1.1, 1.4, 1.3]  (from calibration)
userOverride = [1.0, 1.0, 1.0, 1.0]  (no manual adjustments)

bandGain[0] = 1.2 × 1.0 × 1.0 = 1.2  (250-750 Hz)
bandGain[1] = 1.2 × 1.1 × 1.0 = 1.32 (750-1500 Hz)
bandGain[2] = 1.2 × 1.4 × 1.0 = 1.68 (1500-3000 Hz)  ← Most boost
bandGain[3] = 1.2 × 1.3 × 1.0 = 1.56 (3000-6000 Hz)
```

---

#### **Stage 4: Band Summation (Multiband Compression)**

**Lines 219-231**

```
FOR each sample (i = 0 to 479):
    accum = 0.0
    
    FOR each band (b = 0 to 3):
        accum += bandBufs[b][i] × bandGain[b]
    
    procBuf[i] = accum
```

**Process:**
1. Take corresponding sample from each band
2. Multiply each by its band-specific gain
3. Sum all 4 bands together
4. Store in procBuf

**Example for sample index i=100:**
```
Band 0: bandBufs[0][100] = 0.012  → × 1.2  = 0.0144
Band 1: bandBufs[1][100] = 0.034  → × 1.32 = 0.0449
Band 2: bandBufs[2][100] = 0.089  → × 1.68 = 0.1495
Band 3: bandBufs[3][100] = 0.023  → × 1.56 = 0.0359
                                    ─────────────────
procBuf[100] = 0.0144 + 0.0449 + 0.1495 + 0.0359 = 0.2447
```

**Typical Levels Pre-Clipping:**
- Normal speech: RMS ≈ 0.05-0.15, Peak ≈ 0.3-0.8
- **If Peak > 1.0:** Clipping will occur (distortion)

---

#### **Stage 5: Soft Clipping (tanh)**

**Lines 233-246**

```
FOR each sample (i = 0 to 479):
    clipped = tanh(procBuf[i])
    
    // Hard clamp to [-1, 1] (safety)
    IF clipped > 1.0:  clipped = 1.0
    IF clipped < -1.0: clipped = -1.0
    
    procBuf[i] = clipped
```

**tanh() Function Characteristics:**

```
tanh(x) behavior:
  Input → Output
  -∞    → -1.0
  -2.0  → -0.964
  -1.0  → -0.762
  -0.5  → -0.462
   0.0  →  0.0
   0.5  →  0.462
   1.0  →  0.762
   2.0  →  0.964
  +∞    → +1.0
```

**Purpose:**
- Prevents hard clipping (which causes harsh distortion)
- Provides smooth compression for loud signals
- Output always stays within [-1.0, +1.0]

**Effect on Signal:**
- Small signals (< 0.5): Nearly linear (minimal distortion)
- Medium signals (0.5-1.0): Gentle compression
- Large signals (> 1.0): Strong compression (asymptotic approach to ±1.0)

**Example:**
```
PreClip:  [-0.123, 0.456, 0.892, 1.234, 1.678]
PostClip: [-0.122, 0.428, 0.713, 0.845, 0.933]
                    ↑      ↑      ↑      ↑
                  Reduced more as signal increases
```

**Problem with Current Implementation:**
- If `procBuf` values are already > 1.5 before tanh, output is compressed to ~0.9-0.95
- This reduces dynamic range significantly
- Can cause "muffled" or "compressed" sound
- **Fix:** Apply tanh AFTER checking if amplification is too high

---

#### **Stage 6: Output to Speakers**

**Lines 253-256**

```
AudioTrack.write(procBuf, 0, FRAME_SIZE, WRITE_BLOCKING)
├─ Writes 480 samples (10ms)
├─ Blocking call: waits if output buffer full
└─ Returns: number of samples written
```

**AudioTrack Playback:**
1. Accepts float samples in range [-1.0, +1.0]
2. Converts to device-specific format internally
3. Sends to Android audio mixer
4. Routes to active output (speakers, headphones, Bluetooth)

---

## 📈 COMPLETE SIGNAL PATH DIAGRAM

```
┌──────────────────────────────────────────────────────────────────────┐
│                         MICROPHONE INPUT                             │
│                    (Android AudioRecord API)                         │
└────────────────────────────────┬─────────────────────────────────────┘
                                 ↓
                        ┌─────────────────┐
                        │  inputBuf[480]  │  Raw PCM float samples
                        │  Range: [-1, 1] │  RMS ≈ 0.01-0.15
                        └────────┬────────┘
                                 ↓
                    ┌────────────────────────┐
                    │  CHECK: bypassMode?    │
                    └──────┬────────────┬────┘
                           │            │
                    YES ◄──┘            └──► NO
                     ↓                       ↓
         ┌───────────────────────┐  ┌────────────────────────┐
         │   BYPASS PROCESSING   │  │   FULL PROCESSING      │
         │                       │  │                        │
         │  × globalGain         │  │  STAGE 1: RNNoise      │
         │  Hard limit @ ±0.95   │  │  ├─ If enabled:        │
         │                       │  │  │  Native denoising   │
         │  → procBuf[480]       │  │  └─ Else: passthrough  │
         └──────────┬────────────┘  │                        │
                    │               │  denoiseBuf[480]       │
                    │               └───────────┬────────────┘
                    │                           ↓
                    │               ┌────────────────────────┐
                    │               │  STAGE 2: Filterbank   │
                    │               │                        │
                    │               │  4× BandPassFilter     │
                    │               │  (2nd-order biquad)    │
                    │               │                        │
                    │               │  ┌─→ bandBufs[0][480]  │ 250-750 Hz
                    │               │  ├─→ bandBufs[1][480]  │ 750-1500 Hz
                    │               │  ├─→ bandBufs[2][480]  │ 1500-3000 Hz
                    │               │  └─→ bandBufs[3][480]  │ 3000-6000 Hz
                    │               └───────────┬────────────┘
                    │                           ↓
                    │               ┌────────────────────────┐
                    │               │  STAGE 3: Gain Calc    │
                    │               │                        │
                    │               │  bandGain[b] =         │
                    │               │    globalGain ×        │
                    │               │    baseline[b] ×       │
                    │               │    userOverride[b]     │
                    │               └───────────┬────────────┘
                    │                           ↓
                    │               ┌────────────────────────┐
                    │               │  STAGE 4: Sum Bands    │
                    │               │                        │
                    │               │  FOR each sample i:    │
                    │               │    sum = Σ(band[b][i]  │
                    │               │          × bandGain[b])│
                    │               │                        │
                    │               │  → procBuf[480]        │
                    │               └───────────┬────────────┘
                    │                           ↓
                    │               ┌────────────────────────┐
                    │               │  STAGE 5: Soft Clip    │
                    │               │                        │
                    │               │  FOR each sample:      │
                    │               │    tanh(procBuf[i])    │
                    │               │    clamp to [-1, 1]    │
                    │               │                        │
                    │               │  → procBuf[480]        │
                    │               └───────────┬────────────┘
                    │                           │
                    └───────────────────────────┘
                                    ↓
                         ┌─────────────────┐
                         │  procBuf[480]   │  Final output
                         │  Range: [-1, 1] │  Ready for playback
                         └────────┬────────┘
                                  ↓
                    ┌─────────────────────────┐
                    │   AudioTrack.write()    │
                    │   (Android Audio Output)│
                    └─────────────┬───────────┘
                                  ↓
                    ┌──────────────────────────┐
                    │   SPEAKERS / HEADPHONES  │
                    └──────────────────────────┘
```

---

## ⏱️ LATENCY BREAKDOWN

**Total End-to-End Latency: ~42-60ms**

```
Microphone → ADC               : ~5ms   (hardware)
AudioRecord buffering          : ~10ms  (FRAME_SIZE buffer)
Processing (read + DSP + write): ~10ms  (480 samples @ 48kHz)
AudioTrack buffering           : ~10ms  (output buffer)
DAC → Speakers                 : ~5ms   (hardware)
                              ─────────
Total (FULL mode)             : ~40ms

Total (BYPASS mode)           : ~30ms  (no DSP overhead)
```

**Acceptable for hearing aids:** < 100ms latency is generally unnoticeable.

---

## 🔧 CURRENT ISSUES AND ROOT CAUSES

### **Issue 1: Distorted/Vibrating Audio**

**Symptoms:**
- Broken, vibrating sound
- Audio seems compressed or muffled
- Toggles don't work

**Likely Root Causes:**

1. **Excessive Gain Before tanh()**
   - If `bandGain` values cause `procBuf` > 2.0
   - tanh(2.0) = 0.964, tanh(3.0) = 0.995
   - All signals compressed to ~0.95-0.99 range
   - Loss of dynamics = muffled sound

   **Fix:** Reduce gain or move tanh earlier

2. **Filterbank Artifacts**
   - Bandpass filters with narrow Q can "ring"
   - Summing 4 overlapping bands can cause phase issues
   - If bands don't sum to unity, volume fluctuates

   **Fix:** Adjust Q factors or use wider bands

3. **RNNoise Artifacts**
   - If native library fails, passthrough is used
   - If native library works but over-processes, speech warping
   
   **Fix:** Test with `noiseReductionEnabled = false`

4. **Buffer Underruns**
   - If processing takes > 10ms per frame
   - AudioTrack buffer empties
   - Causes clicks, pops, or silence

   **Fix:** Optimize DSP, increase buffer size

---

### **Issue 2: Toggles Not Working**

**Current Implementation:**
```java
// Reads preferences EVERY frame (line 115-117)
noiseReductionEnabled = prefs.getBoolean(KEY_NOISE_REMOVAL, true);
globalGain = prefs.getFloat(KEY_AMPLIFICATION, 1.0f);
```

**Why toggles might not work:**

1. **Wrong SharedPreferences Name**
   - AudioProcessor uses: `"com.example.audion.PREFERENCES"`
   - UI might write to: `PreferenceManager.getDefaultSharedPreferences()`
   
   **Fix:** Ensure UI writes to same prefs file

2. **Wrong Key Names**
   - AudioProcessor reads: `"noiseRemoval"`, `"amplificationFactor"`
   - UI might write: `"noise_reduction"`, `"amplification"`
   
   **Fix:** Match key names exactly

3. **UI Not Calling commit()**
   - SharedPreferences changes not persisted
   
   **Fix:** Use `editor.commit()` or `editor.apply()`

---

## 🧪 TESTING RECOMMENDATIONS

### **Test 1: BYPASS Mode (Isolate DSP)**

Enable bypass mode to skip all DSP processing:
```
bypassMode = true  → Only gain + limiter
```

**If audio is clean:** Problem is in DSP chain (RNNoise, filterbank, or tanh)  
**If audio is distorted:** Problem is input gain or hardware

---

### **Test 2: Disable RNNoise**

```
noiseReductionEnabled = false  → Skip RNNoise
```

**If audio improves:** RNNoise is causing artifacts  
**If no change:** Problem is in filterbank or tanh

---

### **Test 3: Reduce Gain**

```
globalGain = 0.5  → Half volume
```

**If audio clears up:** Gain was too high, causing tanh over-compression  
**If no change:** Problem is not gain-related

---

### **Test 4: Monitor Levels**

Check logs for typical signal levels:
```
Input[RMS=0.0234 Peak=0.4567]  ← Should be < 0.5 for speech
PreClip[RMS=0.2345 Peak=1.8923]  ← Problem: Peak > 1.0!
PostClip[RMS=0.1234 Peak=0.9523]  ← tanh compressed it
```

**If PreClip Peak > 1.5:** Reduce gain immediately

---

## 📝 SUMMARY

**Current Architecture:**
- AudioStreamingService starts AudioProcessor in background thread
- AudioProcessor runs continuous loop:
  1. Capture 10ms frame (480 samples)
  2. Optional: RNNoise denoising
  3. Split into 4 frequency bands
  4. Apply per-band gains
  5. Sum bands together
  6. Apply tanh soft clipping
  7. Output to speakers

**Key Parameters:**
- Sample Rate: 48 kHz
- Frame Size: 480 samples (10ms)
- Channels: Mono
- Format: PCM_FLOAT (32-bit)
- Bands: 250-750, 750-1500, 1500-3000, 3000-6000 Hz
- Latency: ~40ms (full mode), ~30ms (bypass mode)

**Next Steps:**
1. Test BYPASS mode to isolate issue
2. Check logs for signal levels (RMS, Peak)
3. Adjust gains if PreClip Peak > 1.0
4. Verify SharedPreferences keys match UI
5. Consider moving tanh before band summation

---

**End of Audio Processing Flow Documentation**
