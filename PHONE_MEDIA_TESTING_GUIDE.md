# Phone Media Amplification - Testing Guide

## Quick Test Procedure

### Prerequisites
- Android 10+ device
- Audion app installed (with phone media feature)
- Spotify/YouTube app installed
- Headphones connected

---

## Test 1: Basic Mode Switching

**Steps:**
1. Open Audion → Standard Mode
2. Observe audio source toggle (Mic 🎤 | Phone 📱)
3. Tap "Start" button
4. Verify microphone mode active (environment amplified)
5. Tap "Phone Audio" toggle
6. **Expected**: Permission dialog appears
7. Tap "Allow"
8. **Expected**: Toast "Amplifying phone media"
9. **Expected**: Toggle shows Phone selected (white background)
10. Tap "Microphone" toggle
11. **Expected**: Switches back (no permission needed)

**Pass Criteria:**
- ✅ Toggle visual feedback correct
- ✅ Permission dialog shown once
- ✅ Toast messages shown
- ✅ No audio glitches

---

## Test 2: Spotify Amplification

**Steps:**
1. Open Audion → Start Standard Mode
2. Switch to "Phone Audio" mode (grant permission)
3. Open Spotify
4. Play a quiet song (e.g., classical, acoustic)
5. **Expected**: Song amplified through Audion DSP
6. Check gain slider: 0 dB (no amplification)
7. Move slider to 20 dB
8. **Expected**: Song louder, no distortion
9. Move slider to 30 dB (max for media)
10. **Expected**: Song very loud, slight limiting at peaks
11. Play a loud song (e.g., rock, EDM)
12. **Expected**: AGC normalizes loudness automatically

**Pass Criteria:**
- ✅ Spotify audio captured and amplified
- ✅ Gain slider adjusts volume (0-30 dB range)
- ✅ AGC prevents clipping on loud tracks
- ✅ No pumping or breathing artifacts
- ✅ Music quality preserved (no RNNoise artifacts)

---

## Test 3: YouTube Amplification

**Steps:**
1. Audion in Phone Audio mode
2. Open YouTube app
3. Play a video with speech (e.g., podcast, interview)
4. **Expected**: Video audio amplified
5. Set gain slider to 15 dB
6. **Expected**: Clear speech amplification
7. Play a video with music
8. **Expected**: Music amplified, AGC stabilizes loudness
9. Fast-forward through video
10. **Expected**: Audio captures scrubbing sounds

**Pass Criteria:**
- ✅ YouTube audio captured
- ✅ Speech clarity enhanced
- ✅ Music amplified without distortion
- ✅ AGC handles dynamic content

---

## Test 4: Phone Call Amplification

**Setup**: Have someone call your test device

**Steps:**
1. Audion in Phone Audio mode (running in background)
2. Receive incoming call
3. Answer call
4. **Expected**: Caller's voice amplified through Audion
5. Speak to caller
6. **Expected**: Your voice transmitted normally (no echo)
7. Adjust gain slider mid-call
8. **Expected**: Caller volume changes in real-time

**Pass Criteria:**
- ✅ Incoming audio amplified
- ✅ Outgoing audio normal (no feedback)
- ✅ No echo or latency issues
- ✅ Call quality clear

---

## Test 5: AGC Performance

**Steps:**
1. Audion in Phone Audio mode
2. Play a very quiet song (volume 20%)
3. Note: Audion AGC should increase gain
4. **Check logs**: `[Media AGC] RMS=-25 dBFS, AGC gain=+10 dB`
5. Switch to a very loud song (volume 100%)
6. Note: Audion AGC should decrease gain
7. **Check logs**: `[Media AGC] RMS=-5 dBFS, AGC gain=-7 dB`
8. Listen for smooth transitions (no abrupt volume jumps)

**Pass Criteria:**
- ✅ Quiet content boosted
- ✅ Loud content attenuated
- ✅ Target RMS ~-12 dBFS maintained
- ✅ Transitions smooth (1 dB/s max)
- ✅ No pumping artifacts

**AGC Log Example:**
```
[Media AGC] RMS=-18.3 dBFS, AGC gain=5.2 dB, target=-12.0 dBFS
[Media AGC] RMS=-13.1 dBFS, AGC gain=0.8 dB, target=-12.0 dBFS
[Media AGC] RMS=-11.5 dBFS, AGC gain=-0.3 dB, target=-12.0 dBFS
```

---

## Test 6: Mode Switching During Playback

**Steps:**
1. Start Spotify playback
2. Audion in Microphone mode → Tap Start
3. **Expected**: Environment sounds amplified (music not captured)
4. While streaming, switch to "Phone Audio" mode
5. **Expected**: Brief silence (engine restart)
6. **Expected**: Spotify now amplified through Audion
7. Switch back to Microphone mode
8. **Expected**: Music stops being amplified, environment captured
9. Verify no crashes or glitches

**Pass Criteria:**
- ✅ Seamless mode switching
- ✅ Audio source changes correctly
- ✅ Engine restarts <1 second
- ✅ No crashes or hangs

---

## Test 7: Gain Limit Enforcement

**Objective**: Verify 30 dB max for media mode, 40 dB max for mic mode

**Steps:**
1. **Mic Mode Test**:
   - Switch to Microphone mode
   - Move gain slider to 100 (max)
   - **Check logs**: `Applied Gain: 40.0 dB`
   - **Expected**: Environment very loud, feedback possible
   
2. **Media Mode Test**:
   - Switch to Phone Audio mode
   - Move gain slider to 100 (max)
   - **Check logs**: `Applied Gain: 30.0 dB`
   - Play Spotify at normal volume
   - **Expected**: Music loud but controlled, no distortion

**Pass Criteria:**
- ✅ Mic mode caps at 40 dB
- ✅ Media mode caps at 30 dB
- ✅ Logs confirm correct limits
- ✅ No distortion at max gain

---

## Test 8: Permission Edge Cases

**Test 8a: Permission Denial**
1. Clear app data (reset permissions)
2. Tap "Phone Audio" toggle
3. Permission dialog → Tap "Cancel"
4. **Expected**: Toggle stays on Microphone
5. **Expected**: Toast "Permission required for phone audio mode"

**Test 8b: Android Version Check**
1. (If testing on Android 9 or lower)
2. Tap "Phone Audio" toggle
3. **Expected**: Toast "Phone audio mode requires Android 10 or higher"
4. **Expected**: Toggle stays on Microphone

**Pass Criteria:**
- ✅ Permission denial handled gracefully
- ✅ Android version checked
- ✅ User feedback clear

---

## Test 9: No Media Playing

**Steps:**
1. Switch to Phone Audio mode (grant permission)
2. Ensure NO media is playing (Spotify closed, YouTube closed)
3. Tap Start in Audion
4. **Expected**: Silence (no audio captured)
5. Verify logs: RMS should be very low (~-60 dBFS)
6. Start playing Spotify
7. **Expected**: Immediate capture and amplification

**Pass Criteria:**
- ✅ No crashes when no media playing
- ✅ AGC doesn't blow up gain on silence
- ✅ Automatic capture when media starts

---

## Test 10: Background Service Restart

**Steps:**
1. Audion in Phone Audio mode, streaming
2. Spotify playing
3. Open Recent Apps
4. Swipe away Audion app
5. **Expected**: Audio stops (service killed)
6. Reopen Audion
7. **Expected**: Mode remembered (still Phone Audio)
8. BUT: MediaProjection permission lost (Android security)
9. **Expected**: Toggle shows Phone selected but permission dialog on Start
10. Grant permission again
11. **Expected**: Spotify captured again

**Pass Criteria:**
- ✅ Service restarts cleanly
- ✅ Mode preference saved
- ✅ MediaProjection re-requested (Android requirement)

---

## Test 11: Battery & Performance

**Monitoring Tools:**
- Android Battery Usage stats
- `adb shell dumpsys batterystats`
- Logcat for CPU warnings

**Steps:**
1. Full battery charge
2. Run Audion in Media mode for 1 hour
3. Play Spotify continuously
4. Check battery drain
5. Monitor CPU usage in Android Settings

**Pass Criteria:**
- ✅ Battery drain <5% per hour
- ✅ CPU usage <15% average
- ✅ No thermal throttling
- ✅ No "battery optimization" warnings

---

## Test 12: Latency Measurement

**Subjective Test:**
1. Play a video in YouTube
2. Observe lip-sync
3. **Expected**: No noticeable delay (<30ms)

**Objective Test (requires tools):**
1. Use audio latency test app
2. Measure round-trip latency
3. **Expected**: <50ms total (20ms capture + 30ms processing + output)

**Pass Criteria:**
- ✅ Lip-sync good in videos
- ✅ No echo in phone calls
- ✅ Music playback feels real-time

---

## Log Verification

### Expected Log Patterns

**Mode Switch (Mic → Media):**
```
[HomeActivity] Audio mode: Phone media
[SimpleAudioService] [Audio Mode] Switching to: MEDIA
[SimpleAudioEngine] Switching audio mode: MIC → MEDIA
[SimpleAudioEngine] AudioRecord initialized: MEDIA mode (AudioPlaybackCapture)
[GainStagingManager] Max gain updated to: 30.0 dB
[SimpleAudioEngine] Mode switched to MEDIA (max gain: 30.0 dB)
```

**AGC Active:**
```
[SimpleAudioEngine] [Media AGC] RMS=-15.3 dBFS, AGC gain=2.7 dB, target=-12.0 dBFS
[SimpleAudioEngine] [Media AGC] RMS=-13.1 dBFS, AGC gain=0.9 dB, target=-12.0 dBFS
[SimpleAudioEngine] [Media AGC] RMS=-11.8 dBFS, AGC gain=-0.2 dB, target=-12.0 dBFS
```

**DSP Pipeline (Media Mode):**
```
[SimpleAudioEngine] Pipeline: Mic → AGC → GainStaging → WDRC → Limiter → Output
[SimpleAudioEngine] RNNoise: DISABLED (Media mode)
[SimpleAudioEngine] Feedback Canceller: DISABLED (Media mode)
```

---

## Issue Reporting Template

**If you find a bug, report with:**

```
**Issue**: [Brief description]
**Mode**: Mic / Media
**Device**: [Model, Android version]
**Media App**: Spotify / YouTube / Phone Call / etc
**Gain Setting**: [0-100]
**Reproduction Steps**:
1. [Step 1]
2. [Step 2]
3. [Result]

**Expected**: [What should happen]
**Actual**: [What actually happened]

**Logs** (if available):
[Paste relevant logcat lines]

**Screenshots**: [Attach if relevant]
```

---

## Success Criteria Summary

### Must Pass (P0)
- ✅ Mode switching works (Mic ↔ Media)
- ✅ MediaProjection permission flow correct
- ✅ Spotify/YouTube audio captured and amplified
- ✅ Gain limits enforced (40 dB mic, 30 dB media)
- ✅ No crashes or hangs
- ✅ Build successful

### Should Pass (P1)
- ✅ AGC normalizes loudness smoothly
- ✅ Phone calls amplified without echo
- ✅ Battery drain acceptable (<5%/hour)
- ✅ CPU usage <15%
- ✅ Latency <30ms (subjective)

### Nice to Have (P2)
- ⏳ "No media playing" detection and toast
- ⏳ Media EQ profile (frequency-specific boost)
- ⏳ Usage filtering (Music/Calls/All)
- ⏳ Visual indicator (listening to Environment/Media)

---

## Test Sign-Off

**Tester**: ________________  
**Date**: ________________  
**Device**: ________________  
**Android Version**: ________________  

**Overall Result**: PASS / FAIL / NEEDS WORK

**Notes**:
- _______________________________________________________________
- _______________________________________________________________
- _______________________________________________________________

**Recommendation**: 
- [ ] Ready for beta testing
- [ ] Needs bug fixes (see issues above)
- [ ] Needs redesign (major issues)

---

**Document Version**: 1.0  
**Last Updated**: December 2024  
**Status**: Ready for Testing
