# Profile Switching Personalization - Testing Guide

## What We Implemented
When you switch hearing profiles, the audio engine now automatically reloads the personalization data (audiogram and calibration) for the new profile and applies it in real-time.

## Prerequisites
Before testing, you need **at least 2 hearing profiles** with **different hearing test results**:
- Profile A: e.g., "Mild Loss" with some hearing loss at specific frequencies
- Profile B: e.g., "Normal Hearing" or a different hearing loss pattern

## Step-by-Step Testing

### 1. Prepare Test Environment

**Option A: If you already have multiple profiles**
- Make sure each profile has completed hearing tests
- Note the differences between profiles (e.g., one has high-frequency loss, one doesn't)

**Option B: Create test profiles**
1. Open Audion app
2. Create Profile 1: "Test - Mild Loss"
   - Complete hearing test with some thresholds elevated (e.g., 30-40 dB at high frequencies)
3. Create Profile 2: "Test - Normal"
   - Complete hearing test with normal thresholds (e.g., 0-10 dB across all frequencies)

### 2. Start Audio Streaming

1. Connect headphones/earbuds
2. Go to Home screen
3. Start audio streaming (tap play button)
4. Play some audio (music, video, or just talk into the mic)
5. **Important**: Keep audio playing throughout the test

### 3. Enable Logcat Monitoring

**Method 1: Android Studio**
```
1. Open Android Studio
2. Go to View > Tool Windows > Logcat
3. Filter by "SimpleAudioStreamingService" or "HomeActivity"
4. Look for tags: "AudioStream" or "HomeActivity"
```

**Method 2: ADB Command**
```powershell
adb logcat -s AudioStream:I HomeActivity:I
```

**Method 3: Full Log Capture**
```powershell
adb logcat | Select-String "Profile switched|PROFILE CHANGED|Reload|Audio engine updated"
```

### 4. Perform Profile Switch Test

1. **Before Switching**: Note current audio characteristics
   - How loud is the audio?
   - Are high frequencies boosted?
   - Is there any compression effect?

2. **Switch Profile**: 
   - Go to Home screen profile dropdown
   - Select a different profile
   - **Keep listening to the audio**

3. **What Should Happen**:
   - Audio should change **immediately** (within 1 second)
   - You should hear different frequency balance or gain
   - No audio interruption or glitches

### 5. Verify in Logcat

**Look for this exact sequence of log messages:**

```
✅ Step 1: Profile Switch Detected
HomeActivity: Profile switched to: Test - Normal (ID: 2)

✅ Step 2: Service Receives Broadcast
AudioStream: ★★★ PROFILE CHANGED - Reloading personalization for profile ID: 2

✅ Step 3: Database Query
AudioStream: [Reload] Audiogram loaded: Left=8 results, Right=8 results
AudioStream: [Reload] Calibration: Left=✓, Right=✓

✅ Step 4: Engine Update
AudioStream: [Reload] UCL Limit: Safe max gain = 25.0 dB
AudioStream: ★★★ Audio engine updated with new profile personalization

✅ Step 5: Settings Applied
AudioStream: Applying amplification: X dB (safe max: Y dB)
```

### 6. Expected Results by Profile Type

**Profile with Mild Hearing Loss:**
- Audio should be **louder**
- High frequencies (2-8 kHz) should be **boosted**
- Compression should be **active** (dynamic range reduction)
- Logcat shows: `Safe max gain = 25-35 dB`

**Profile with Normal Hearing:**
- Audio should be at **normal volume**
- Frequency response should be **flat** (no boost)
- Minimal compression
- Logcat shows: `Safe max gain = 40+ dB` (less restrictive)

**Profile with Severe Loss:**
- Audio should be **very loud**
- Strong frequency-specific boost
- Heavy compression
- Logcat shows: `Safe max gain = 15-20 dB` (UCL protection)

### 7. Multiple Switch Test

Switch between profiles multiple times rapidly:
```
Profile A → Profile B → Profile A → Profile C → Profile A
```

**Each switch should:**
- ✅ Trigger new log messages
- ✅ Change audio immediately
- ✅ Show different profile ID in logs
- ✅ Show "Audio engine updated" message
- ❌ NOT cause audio to stop
- ❌ NOT cause app to crash
- ❌ NOT show error messages

### 8. Edge Case Testing

**Test 1: Switch to Profile with No Audiogram**
- Create a new profile without completing hearing test
- Switch to it
- **Expected**: Log shows `No audiogram data available for this profile`
- Audio continues but without personalization (flat response)

**Test 2: Switch While Audio is Paused**
- Pause audio streaming
- Switch profiles
- Resume audio
- **Expected**: Reload messages appear, audio uses new profile

**Test 3: Rapid Switching**
- Switch profiles 5 times in 5 seconds
- **Expected**: All switches logged, no crashes, final profile is active

### 9. Audio Difference Verification

**Play a test tone or music with clear frequency content:**

1. Use a tone generator app (500 Hz, 2 kHz, 4 kHz tones)
2. Play tone with Profile A active
3. Note the volume/clarity
4. Switch to Profile B while tone is playing
5. **You should hear immediate change in volume/tone**

**Example Differences:**
- Profile with high-frequency loss → 4 kHz tone becomes louder after switch
- Profile with flat hearing → all tones at similar volume
- Profile with low-frequency loss → 500 Hz tone gets boost

### 10. Troubleshooting

**Problem: No log messages appear**
- Check logcat filter settings
- Verify app is actually built with latest code: `.\gradlew assembleDebug`
- Reinstall app: `adb install -r app\build\outputs\apk\debug\app-debug.apk`

**Problem: Logs appear but no audio change**
- Check if profiles actually have different audiogram data
- Verify both profiles have completed hearing tests
- Check if `AudioEngine.setAudiogramData()` is called (should see in logs)

**Problem: Audio stops when switching**
- Check for error messages in logcat
- Verify service doesn't restart (look for "onCreate" messages)
- Check for crash logs: `adb logcat *:E`

**Problem: "Invalid profile ID received: -1"**
- Profile selection didn't pass ID correctly
- Check HomeActivity logs for "Profile switched to:" message
- Verify currentProfileId is set properly

## Success Criteria

✅ **Implementation is working if:**
1. Switching profiles shows all 5 log message steps above
2. Audio characteristics change immediately when switching
3. No crashes or errors
4. Can switch back and forth multiple times
5. Each profile sounds different (if audiograms are different)

❌ **Implementation has issues if:**
1. No log messages appear when switching
2. Audio doesn't change between profiles
3. Only see "Profile switched to:" but not "PROFILE CHANGED"
4. See "Invalid profile ID" errors
5. Audio stops or glitches during switch

## Quick Verification Command

Run this in PowerShell while testing:
```powershell
adb logcat -c  # Clear log
# Now switch profile in app
adb logcat -d | Select-String "Profile switched|PROFILE CHANGED|Reload|Audio engine updated"
```

You should see output like:
```
HomeActivity: Profile switched to: Test Profile (ID: 3)
AudioStream: ★★★ PROFILE CHANGED - Reloading personalization for profile ID: 3
AudioStream: [Reload] Audiogram loaded: Left=8 results, Right=8 results
AudioStream: ★★★ Audio engine updated with new profile personalization
```

## What Each Component Does

1. **HomeActivity** (profile dropdown):
   - Detects selection change
   - Sends broadcast with profile ID
   - Logs: "Profile switched to: [name] (ID: [id])"

2. **SimpleAudioStreamingService** (ProfileReloadReceiver):
   - Receives broadcast
   - Logs: "★★★ PROFILE CHANGED - Reloading..."
   - Calls reload method

3. **reloadPersonalizationForProfile()** method:
   - Queries database for audiogram by profile ID
   - Separates left/right ear data
   - Loads calibration data
   - Updates audio engine: `audioEngine.setAudiogramData(left, right)`
   - Re-applies settings with new UCL limits

4. **SimpleAudioEngine** (native C++):
   - Receives new audiogram data
   - Recalculates per-band gains (5 bands)
   - Applies NAL-NL2 prescription formula
   - Updates WDRC compression parameters
   - Continues processing with new settings

## Timeline Expectations

- Profile selection → Broadcast sent: **< 50ms**
- Broadcast → Receiver triggered: **< 100ms**
- Database query: **< 200ms**
- Engine update: **< 50ms**
- **Total switch time: < 400ms (less than half a second)**

User should perceive it as instant!
