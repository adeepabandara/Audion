# Phase 1: Production-Grade Safety Core - Audio Pipeline Architecture

## Overview

This document describes the production-grade, low-latency audio processing pipeline for the Audion hearing-assist application. The architecture implements a comprehensive safety-first approach with per-ear independence, zero-allocation audio loops, and automatic degradation paths.

## System Architecture

### High-Level Pipeline

```
AudioRecord → RingBuffer → DSP Chain (L/R) → RingBuffer → AudioTrack
     ↑            ↑              ↑             ↑           ↑
  Capture      Lock-free       Per-ear       Lock-free   Playback
  Thread      SPSC Queue      Processing    SPSC Queue   Thread
```

### Thread Architecture

**Three dedicated audio threads running at `THREAD_PRIORITY_URGENT_AUDIO`:**

1. **Capture Thread**: AudioRecord → RingBuffer (non-blocking writes)
2. **DSP Thread**: RingBuffer → DspGraph → RingBuffer (frame processing)  
3. **Playback Thread**: RingBuffer → AudioTrack (non-blocking reads)

### Per-Ear DSP Chain

Each ear has an independent processing chain:

```
Input Frame (480 samples @ 48kHz)
    ↓
[RNNoise] (optional, can bypass)
    ↓  
[WDRC] (Wide Dynamic Range Compression)
    ↓
[Limiter] (Look-ahead peak limiter - MPO safety)
    ↓
Output Frame
```

## Core Components

### AudioConfig
- Central configuration for all audio parameters
- Sample rate: 48 kHz, 16-bit PCM, 10ms frames (480 samples)
- Buffer sizes optimized for low latency (8 frames = 80ms total buffer)
- Safety limits and calibration constants

### RingBuffer
- Lock-free SPSC (Single Producer Single Consumer) design
- Power-of-2 sizing for efficient modulo operations
- Atomic indices with memory ordering guarantees
- Built-in overrun/underrun detection and statistics

### AudioEngine
- Main orchestrator managing all threads and components
- Lifecycle management (initialize → start → suspend/resume → stop)
- Processing mode control with runtime switching
- Performance monitoring and health checks

### DspGraph (Per-Ear)
- Modular processing chain with bypass capabilities
- Pre-allocated buffers for zero-allocation operation
- Independent state per channel (L/R)
- Error handling with safe fallback

## Safety Processing

### WDRC (Wide Dynamic Range Compression)
- **Purpose**: Compress dynamic range for hearing loss compensation
- **Parameters**: 
  - Threshold: -25 dBFS (configurable)
  - Ratio: 2.5:1 (soft compression)
  - Attack: 10ms, Release: 100ms
  - Soft knee: 10dB width
- **Calibration**: Uses dBFS→dB SPL conversion per device
- **Safety**: Prevents over-amplification of loud sounds

### Limiter (MPO Protection)
- **Purpose**: Absolute safety ceiling to prevent hearing damage
- **Look-ahead**: 2.5ms (120 samples at 48kHz)
- **Ceiling**: Derived from MPO target (default 120 dB SPL)
- **Overshoot**: <0.5 dB guaranteed
- **Always Active**: Even in degraded modes, limiting remains for safety

### Degradation Path
Automatic performance-based mode switching:

1. **FULL_PROCESSING**: All DSP active
2. **BYPASS_RNNOISE**: Skip noise reduction, keep WDRC + Limiter  
3. **BYPASS_WDRC**: Skip WDRC, keep Limiter only
4. **SAFE_PASSTHROUGH**: Basic limiting only

**Triggers**: CPU overload (>7ms/frame), buffer overruns, processing errors
**Recovery**: Auto-restore when stable for 100 frames (1 second)

## Performance Monitoring

### HealthMonitor
- **Per-stage timing**: Capture, DSP, Playback (average + P95)
- **Buffer health**: Queue depth, overrun/underrun counters
- **CPU budget**: 8ms budget per 10ms frame (80% utilization max)
- **Automatic degradation**: Based on performance thresholds

### Instrumentation
- **Frame-level metrics**: Processing time, buffer utilization
- **Long-term statistics**: Mode changes, error rates, engagement %
- **Watchdog**: Detects stuck threads or excessive processing delays

## Quality Assurance

### QaHooks
- **Tap-test latency**: Cross-correlation based round-trip measurement
- **CSV logging**: RMS, crest factor, limiter engagement, frame drops
- **Device profiling**: Per-device calibration and performance characteristics
- **Production telemetry**: Hidden QA mode for field testing

## Buffer Sizing and Latency

### Target Latency Budget
- **Round-trip target**: ≤35ms on mid-range devices
- **Processing budget**: 8ms per 10ms frame (80% CPU utilization)

### Buffer Configuration
```
Component               Size        Latency Contribution
--------------------------------------------------
AudioRecord buffer      4 frames    40ms
Capture ring buffer     8 frames    80ms (max queue depth)
DSP processing          1 frame     10ms  
Playback ring buffer    8 frames    80ms (max queue depth)
AudioTrack buffer       4 frames    40ms
Look-ahead delay        0.25 frames 2.5ms
--------------------------------------------------
Minimum path:                       ~15ms
Maximum buffered:                   ~50ms
```

## Parameter Defaults

### Audio Format
- **Sample Rate**: 48,000 Hz
- **Bit Depth**: 16-bit signed PCM
- **Channels**: Mono input, dual independent processing
- **Frame Size**: 480 samples (10ms @ 48kHz)

### WDRC Settings
- **Threshold**: -25 dBFS
- **Ratio**: 2.5:1
- **Attack Time**: 10ms
- **Release Time**: 100ms
- **Knee Width**: 10dB

### Limiter Settings  
- **Ceiling**: -1 dBFS (configurable per MPO target)
- **Look-ahead**: 2.5ms (120 samples)
- **Attack**: 0.1ms
- **Release**: 10ms

### Calibration (Device-Specific)
- **Left Channel**: 0 dBFS = 85 dB SPL (example)
- **Right Channel**: 0 dBFS = 85 dB SPL (example)
- **MPO Limit**: 120 dB SPL (adjustable per user)

## QA Testing Instructions

### Latency Verification
1. Enable QA mode in settings
2. Start audio pipeline
3. Execute tap test: `qaHooks.startTapTest()`
4. Generate sharp click/tap near microphone
5. Verify round-trip latency ≤35ms in CSV log

### Dropout Testing
1. Run continuous processing for 10 minutes
2. Monitor health statistics: `healthMonitor.getHealthStats()`
3. Verify 0 underruns/overruns over test period
4. Check CPU utilization stays <80% P95

### Safety Testing
1. Generate 0 dBFS test tone input
2. Verify no digital clipping in output
3. Confirm limiter engagement prevents >MPO output
4. Test overshoot ≤0.5 dB with pink noise bursts

### Stability Testing
1. Perform start/stop cycles 20× times
2. Verify no memory leaks or thread hangs
3. Test suspend/resume during processing
4. Force RNNoise failure → verify graceful bypass

### Per-Ear Independence
1. Process correlated L/R test signals
2. Verify independent limiter statistics per channel
3. Measure crosstalk <-60 dB through entire chain
4. Test asymmetric processing settings

## Error Handling

### Graceful Degradation
- **RNNoise failure**: Bypass with logged warning, continue processing
- **WDRC issues**: Fall back to simple gain, maintain limiter
- **CPU overload**: Automatic mode reduction, user notification
- **Buffer issues**: Soft reset, preserve audio continuity

### Recovery Mechanisms
- **Auto-restart**: Failed processors reinitialize on next frame
- **Health monitoring**: Continuous assessment with recovery thresholds
- **User notification**: Discrete alerts for persistent issues
- **Safe defaults**: Always maintain basic limiter functionality

## Implementation Status

### Core Infrastructure ✅
- [x] AudioConfig with comprehensive constants
- [x] Lock-free RingBuffer implementation
- [x] AudioEngine with thread management
- [x] AudioProcessor interface

### DSP Components ✅
- [x] DspGraph with per-ear chains
- [x] RNNoiseProcessor wrapper
- [x] WdrcProcessor with envelope detection
- [x] LimiterProcessor with look-ahead

### Monitoring & QA ✅
- [x] HealthMonitor with performance tracking
- [x] QaHooks with CSV logging and tap-test
- [x] Automatic degradation state machine
- [x] Comprehensive statistics collection

### Integration Requirements
- [ ] Wire into existing AudioStreamingService
- [ ] Update UI for health monitoring display
- [ ] Add QA mode toggle in settings
- [ ] Device-specific calibration storage
- [ ] Production telemetry endpoints

This architecture provides a robust, safety-first audio processing pipeline suitable for production hearing-assist applications with comprehensive monitoring, automatic adaptation, and quality assurance capabilities.