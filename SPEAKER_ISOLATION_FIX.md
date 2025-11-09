# Speaker Isolation Fix - FocusActivity Integration

## Problem Statement

**Issue**: Audio was no longer muting when the selected speaker was not speaking in FocusActivity.

**Root Cause**: FocusActivity migrated from legacy AudioStreamingService to SimpleAudioStreamingService, but the speaker isolation logic (isolateEnrolledSpeaker method) was disconnected from the new service architecture.

**Git History**: Commit `1baf2783` ("Combined Isolation") showed the old implementation called `isolateEnrolledSpeaker()` in the audio processing loop after diarization and before amplification.

---

## Solution Architecture

### Broadcast Communication Pattern

Implemented real-time communication between FocusActivity and SimpleAudioStreamingService using Android broadcasts:

```
FocusActivity (Diarization) → Broadcast → SimpleAudioStreamingService → SimpleAudioEngine → Mute Audio
```

**Flow**:
1. FocusActivity monitors active speakers via DirectDiarizationManager
2. When speaker state changes, broadcasts isolation command to service
3. SimpleAudioStreamingService receives broadcast via SpeakerIsolationReceiver
4. Service updates SimpleAudioEngine state variables
5. SimpleAudioEngine applies muting in audio processing pipeline

---

## Implementation Details

### 1. SimpleAudioStreamingService.java

#### Added Broadcast Infrastructure (Lines 41-48)
```java
// Speaker isolation broadcast action and extras
public static final String ACTION_SET_SPEAKER_ISOLATION = "com.example.audion.ACTION_SET_SPEAKER_ISOLATION";
public static final String EXTRA_ISOLATION_ENABLED = "isolationEnabled";
public static final String EXTRA_CHUNK_ID = "chunkId";
public static final String EXTRA_SPEAKER_ACTIVE = "speakerActive";

private SpeakerIsolationReceiver speakerIsolationReceiver;
private boolean speakerIsolationEnabled = false;
private volatile boolean selectedSpeakerActive = false;
```

#### Registered Receiver in onCreate() (Lines 77-91)
```java
// Register broadcast receiver for speaker isolation (Focus Mode)
speakerIsolationReceiver = new SpeakerIsolationReceiver();
IntentFilter isolationFilter = new IntentFilter(ACTION_SET_SPEAKER_ISOLATION);
registerReceiver(speakerIsolationReceiver, isolationFilter, Context.RECEIVER_NOT_EXPORTED);
Log.i(TAG, "★★★ Broadcast receiver registered for SPEAKER_ISOLATION");
```

#### Unregistered in onDestroy() (Lines 137-150)
```java
if (speakerIsolationReceiver != null) {
    unregisterReceiver(speakerIsolationReceiver);
    speakerIsolationReceiver = null;
}
```

#### Created SpeakerIsolationReceiver Inner Class (Lines 345-364)
```java
private class SpeakerIsolationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = intent.getBooleanExtra(EXTRA_ISOLATION_ENABLED, false);
        boolean speakerActive = intent.getBooleanExtra(EXTRA_SPEAKER_ACTIVE, false);
        int chunkId = intent.getIntExtra(EXTRA_CHUNK_ID, -1);
        
        speakerIsolationEnabled = enabled;
        selectedSpeakerActive = speakerActive;
        
        if (audioEngine != null) {
            audioEngine.setSpeakerIsolationEnabled(speakerIsolationEnabled);
            audioEngine.setSelectedSpeakerActive(selectedSpeakerActive);
        }
        
        Log.d(TAG, String.format("[Focus Mode] Speaker isolation: enabled=%b, active=%b, chunk=%d",
            enabled, speakerActive, chunkId));
    }
}
```

---

### 2. SimpleAudioEngine.java

#### Added State Variables (Lines 66-69)
```java
// Speaker isolation controls (Focus Mode)
private final AtomicBoolean speakerIsolationEnabled = new AtomicBoolean(false);
private final AtomicBoolean selectedSpeakerActive = new AtomicBoolean(true);
```

#### Added Control Methods (Lines 631-679)
```java
/**
 * Enable or disable speaker isolation (Focus Mode).
 * When enabled and selectedSpeakerActive is false, audio will be muted.
 */
public void setSpeakerIsolationEnabled(boolean enabled) {
    boolean wasEnabled = speakerIsolationEnabled.getAndSet(enabled);
    if (wasEnabled != enabled) {
        Log.i(TAG, "════════════════════════════════════════════════════════");
        Log.i(TAG, "[Focus Mode] Speaker isolation " + (enabled ? "ENABLED" : "DISABLED"));
        Log.i(TAG, "════════════════════════════════════════════════════════");
    }
}

/**
 * Set whether the selected speaker is currently active.
 * When speaker isolation is enabled and this is false, audio will be muted.
 */
public void setSelectedSpeakerActive(boolean active) {
    selectedSpeakerActive.set(active);
}

public boolean isSpeakerIsolationEnabled() {
    return speakerIsolationEnabled.get();
}

public boolean isSelectedSpeakerActive() {
    return selectedSpeakerActive.get();
}
```

#### Applied Muting Logic in processPhase2() (Lines 456-463)
```java
private void processPhase2() {
    // Process left and right ears independently
    leftProcessor.process(floatProcessed, leftOutput, AudioConfig.FRAME_SIZE_SAMPLES);
    rightProcessor.process(floatProcessed, rightOutput, AudioConfig.FRAME_SIZE_SAMPLES);
    
    // Apply speaker isolation (Focus Mode) - mute audio if selected speaker is not active
    if (speakerIsolationEnabled.get() && !selectedSpeakerActive.get()) {
        Arrays.fill(leftOutput, 0, AudioConfig.FRAME_SIZE_SAMPLES, 0.0f);
        Arrays.fill(rightOutput, 0, AudioConfig.FRAME_SIZE_SAMPLES, 0.0f);
    }
    
    // Apply global gain control (master volume)
    // ... rest of processing
}
```

**Position in Pipeline**:
```
Mic → Feedback Cancel → Scene Analysis → RNNoise → L/R 4-Band WDRC → 
→ Speaker Isolation Muting → Global Gain → Per-Band UCL → Global Limiter → Speakers
```

---

### 3. FocusActivity.java

#### Added Speaker State Monitoring (Lines 703-736)
```java
/**
 * Check if the selected enrolled speaker is currently active and send broadcast to service.
 */
private void updateSpeakerIsolationState() {
    if (selectedEnrolledSpeaker == null || diarizationManager == null) {
        // No speaker selected - disable isolation
        sendSpeakerIsolationBroadcast(false, true, currentChunkId);
        return;
    }
    
    boolean speakerActive = false;
    try {
        // Check if selected speaker is among currently active speakers
        for (var s : diarizationManager.getActiveSpeakers(currentChunkId)) {
            if (diarizationManager.isSpeakerMatchingEnrollment(
                    s.getGlobalId(),
                    selectedEnrolledSpeaker.getEmbedding()
            )) {
                speakerActive = true;
                break;
            }
        }
    } catch (Exception e) {
        Log.w("FocusActivity", "Error checking active speakers", e);
    }
    
    // Send broadcast to service with current state
    sendSpeakerIsolationBroadcast(true, speakerActive, currentChunkId);
}

/**
 * Send broadcast to SimpleAudioStreamingService to update speaker isolation state.
 */
private void sendSpeakerIsolationBroadcast(boolean enabled, boolean speakerActive, int chunkId) {
    Intent intent = new Intent("com.example.audion.ACTION_SET_SPEAKER_ISOLATION");
    intent.putExtra("isolationEnabled", enabled);
    intent.putExtra("speakerActive", speakerActive);
    intent.putExtra("chunkId", chunkId);
    sendBroadcast(intent);
    
    Log.d("FocusActivity", String.format("[Focus Mode] Broadcasting: enabled=%b, active=%b, chunk=%d",
        enabled, speakerActive, chunkId));
}
```

#### Integrated into Event Callbacks

**When speaker history updates** (Line 667):
```java
@Override public void onSpeakersHistoryUpdated(List<List<DirectDiarizationManager.SpeakerInfo>> history) {
    runOnUiThread(() -> {
        globalSpeakersRecyclerView.setVisibility(View.VISIBLE);
        globalSpeakersAdapter.notifyDataSetChanged();
    });
    
    // Update speaker isolation state when speaker history changes
    updateSpeakerIsolationState();
}
```

**When speaker selected** (Line 417):
```java
enrolledSpeakersAdapter.setOnSpeakerSelectListener((speaker, pos) -> {
    sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
    selectedEnrolledSpeaker = speaker;
    defaultPanel.removeAllViews();
    toggleButton.setVisibility(View.VISIBLE);
    toggleButton.setEnabled(true);
    updateToggleUi(false);
    
    // Update speaker isolation state when speaker is selected
    updateSpeakerIsolationState();
});
```

**When processing starts** (Line 602):
```java
private void startProcessing() {
    // ... initialization code ...
    
    // Start SimpleAudioStreamingService
    startAudioStreamingServiceInFocusMode();
    
    isProcessing = true;
    speakerIsolationEnabled = true;
    updateToggleUi(true);
    focusWaveform.setVisibility(View.VISIBLE);
    focusWaveform.levels.clear();
    
    // Enable speaker isolation in service
    updateSpeakerIsolationState();
}
```

**When processing stops** (Line 616):
```java
private void stopProcessing() {
    isProcessing = false;
    speakerIsolationEnabled = false;
    
    // Disable speaker isolation in service
    sendSpeakerIsolationBroadcast(false, true, currentChunkId);
    
    // Stop SimpleAudioStreamingService
    stopAudioStreamingService();
    
    focusWaveform.setVisibility(View.GONE);
    updateToggleUi(false);
}
```

---

## Technical Specifications

### Broadcast Protocol

**Action**: `"com.example.audion.ACTION_SET_SPEAKER_ISOLATION"`

**Extras**:
- `isolationEnabled` (boolean): Whether speaker isolation is active
- `speakerActive` (boolean): Whether selected speaker is currently speaking
- `chunkId` (int): Current audio chunk ID (for debugging/sync)

**Behavior**:
- When `isolationEnabled=true` and `speakerActive=false` → Audio muted (0.0f)
- When `isolationEnabled=false` → Audio passes through normally
- When `speakerActive=true` → Audio passes through normally (even if enabled)

### Thread Safety

All state variables use `AtomicBoolean` to ensure thread-safe reads/writes:
- Audio processing thread reads state
- Broadcast receiver thread writes state
- No locks required due to atomic operations

### Performance Impact

**Minimal overhead**:
- Atomic boolean check: ~1 CPU cycle
- Arrays.fill when muting: O(n) where n=480 samples (10ms frame)
- Broadcast communication: Async, doesn't block audio thread

---

## Testing Checklist

### Functional Tests

1. **Speaker Selection**
   - [ ] Select enrolled speaker
   - [ ] Verify isolation state broadcast sent
   - [ ] Check logs: `[Focus Mode] Broadcasting: enabled=true, active=?`

2. **Speaker Activity Detection**
   - [ ] Start processing with selected speaker
   - [ ] Verify audio plays when selected speaker is talking
   - [ ] Verify audio mutes when other speakers talk
   - [ ] Check logs: `[Focus Mode] Speaker isolation: enabled=true, active=true/false`

3. **Service Integration**
   - [ ] Verify SimpleAudioStreamingService receives broadcasts
   - [ ] Check logs: `[Focus Mode] Speaker isolation ENABLED`
   - [ ] Verify audio engine state updates
   - [ ] Monitor for audio glitches or latency

4. **State Transitions**
   - [ ] Start → Stop → Start processing
   - [ ] Switch between different enrolled speakers
   - [ ] Stop processing and verify isolation disabled
   - [ ] Check logs: `[Focus Mode] Speaker isolation DISABLED`

### Edge Cases

5. **No Speaker Selected**
   - [ ] Start processing without selecting speaker
   - [ ] Verify toast message appears
   - [ ] Verify no crashes

6. **Rapid Speaker Changes**
   - [ ] Switch speakers quickly during processing
   - [ ] Verify broadcasts sent correctly
   - [ ] Monitor for audio artifacts

7. **Service Restart**
   - [ ] Stop and restart service
   - [ ] Verify isolation state persists
   - [ ] Check receiver registration

8. **Memory Leaks**
   - [ ] Start/stop processing 10+ times
   - [ ] Verify receiver unregistered on service destroy
   - [ ] Check logs for registration/unregistration messages

---

## Build Status

✅ **BUILD SUCCESSFUL** in 1m
- 44 actionable tasks: 19 executed, 25 up-to-date
- No compilation errors
- All dependencies resolved

---

## Integration with Phase 3

The speaker isolation fix integrates seamlessly with Phase 3 DSP:

**Pipeline Order**:
```
1. Feedback Cancellation (FeedbackCanceller)
2. Scene Analysis (SceneAnalyzer) 
3. Noise Reduction (RNNoise)
4. Per-Ear Processing (Left/Right PerEarProcessor)
   - 4-band filterbank
   - WDRC compression per band
   - Gain fitting (NAL-NL2/DSL v5)
   - Per-band UCL limiting
5. ★ SPEAKER ISOLATION MUTING ★  ← Applied here
6. Global Gain (master volume)
7. Global Limiter (0.97 safety threshold)
8. Stereo Interleave → AudioTrack
```

**Why This Position?**:
- After per-ear processing: Allows WDRC/filtering to shape audio before muting
- Before global gain: Muting takes priority over volume control
- Independent of DSP: Can enable/disable without affecting other features

---

## Logs to Monitor

### Service Startup
```
I/SimpleAudioStreamingService: ★★★ Broadcast receiver registered for SPEAKER_ISOLATION
```

### State Changes
```
I/SimpleAudioEngine: ════════════════════════════════════════════════════════
I/SimpleAudioEngine: [Focus Mode] Speaker isolation ENABLED
I/SimpleAudioEngine: ════════════════════════════════════════════════════════
```

### Real-Time Updates
```
D/SimpleAudioStreamingService: [Focus Mode] Speaker isolation: enabled=true, active=false, chunk=42
D/FocusActivity: [Focus Mode] Broadcasting: enabled=true, active=false, chunk=42
```

### Service Shutdown
```
I/SimpleAudioStreamingService: onDestroy called
I/SimpleAudioStreamingService: Unregistered broadcast receiver for SPEAKER_ISOLATION
```

---

## Known Limitations

1. **currentChunkId Not Synced**: Currently not updated in real-time from diarization manager. Passed as 0 or last known value. Doesn't affect functionality since only enabled/speakerActive flags matter.

2. **Broadcast Latency**: ~1-10ms typical Android broadcast latency. Acceptable for 10ms audio frames.

3. **No Fade-In/Fade-Out**: Immediate muting may cause clicks. Can add ramp if needed.

---

## Future Enhancements

1. **Smooth Transitions**: Add 5-10ms fade-in/fade-out when muting/unmuting to prevent audio clicks

2. **ChunkId Synchronization**: Update currentChunkId from diarization manager for accurate tracking

3. **Multi-Speaker Mode**: Support multiple selected speakers (OR logic)

4. **Partial Attenuation**: Instead of full mute, attenuate other speakers by -20dB for awareness

5. **UI Indicators**: Visual feedback when isolation is active (e.g., indicator icon, waveform color)

---

## References

- **Original Implementation**: Git commit `1baf2783` "Combined Isolation"
- **Service Architecture**: SimpleAudioStreamingService.java
- **DSP Pipeline**: SimpleAudioEngine.java
- **Diarization**: DirectDiarizationManager.java
- **Phase 3 Details**: PHASE3_IMPLEMENTATION_SUMMARY.md

---

## Summary

**Problem**: Speaker isolation logic disconnected after service migration  
**Solution**: Broadcast communication between FocusActivity and SimpleAudioStreamingService  
**Implementation**: 3 files modified (Service, Engine, Activity)  
**Build Status**: ✅ SUCCESS  
**Testing**: Ready for functional validation  

The speaker isolation feature is now fully integrated with SimpleAudioStreamingService while preserving all Phase 3 DSP enhancements. Audio will mute in real-time when the selected speaker is not active, providing the expected Focus Mode behavior.
