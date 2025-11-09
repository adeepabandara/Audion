# Phase 2: Quality-Balanced Audio Pipeline

This document describes the Phase 2 enhancement to the Audion hearing-assist audio pipeline, focusing on improved speech clarity and naturalness in noisy environments while maintaining CPU/battery/latency balance.

## Overview

Phase 2 extends the Phase 1 safety-focused pipeline with adaptive quality enhancement features:

- **Scene-driven adaptation** - Real-time classification drives processing parameters
- **RNNoise+ Controller** - SNR/VAD-adaptive noise reduction with watchdog protection
- **Presence Filter** - Speech brightness preservation with minimal coloration
- **Noise Gate + Comfort Noise** - Gentle expansion with dead-silence prevention
- **Adaptive Feedback Cancellation** - Lightweight AFC with speech-aware freeze
- **Balanced Profile** - Optimized defaults for everyday listening scenarios

## Processing Chain Architecture

### Phase 2 Signal Flow

```
Input Audio (48 kHz, 10ms frames)
    │
    ├─→ [Feedback Canceller] ──┐ (Parallel AFC path)
    │                          │
    └─→ [Scene Classifier] ────┴─→ [RNNoise+ Controller]
                                        │
                                        ▼
                                   [Presence Filter]
                                        │
                                        ▼
                                   [Downward Expander]
                                        │
                                        ▼
                                      [WDRC] (from Phase 1)
                                        │
                                        ▼
                                     [Limiter] (from Phase 1)
                                        │
                                        ▼
                                   Output Audio
```

### Component Integration Points

| Component | Input | Output | Control Input |
|-----------|-------|--------|---------------|
| AFC | Input signal | Feedback-cancelled signal | VAD probability |
| Scene Classifier | Input signal | Scene type + confidence | N/A |
| RNNoise+ | AFC output | Noise-reduced signal | Scene policy, VAD |
| Presence Filter | RNNoise+ output | EQ-enhanced signal | Scene policy |
| Downward Expander | Presence output | Gated signal | Scene policy, VAD |
| WDRC | Expander output | Compressed signal | Scene policy (release) |
| Limiter | WDRC output | Safe output | N/A |

## Scene Classification

### Scene Types

The **SceneClassifierLite** identifies three primary acoustic environments:

1. **SPEECH** - Clean speech with minimal background noise
   - RNNoise: Mild suppression (preserve quality)
   - Presence: +2 dB boost (gentle enhancement)
   - Expander: Disabled (no gating needed)
   - WDRC: Normal release (100ms)

2. **SPEECH_IN_NOISE** - Speech with competing background noise
   - RNNoise: Medium suppression (balance clarity/artifacts)  
   - Presence: +4 dB boost (improve clarity)
   - Expander: Disabled (protect speech)
   - WDRC: Slower release +25% (reduce pumping)

3. **STEADY_NOISE** - Consistent background noise without speech
   - RNNoise: Strong suppression (maximize noise reduction)
   - Presence: +3 dB boost (maintain brightness)
   - Expander: Active (gate noise gaps)
   - WDRC: Slower release +40% (stability)

### Classification Features

Low-cost features computed in real-time:

- **Frame RMS** - Overall signal level
- **Short/Long-term SNR** - Signal-to-noise ratio estimation  
- **Modulation Energy** - 4-16 Hz modulation detection
- **Spectral Centroid** - Frequency content characterization
- **Band Energy Ratios** - Spectral shape analysis

Processing budget: ≤0.5ms per frame

## Adaptive Processing Components

### RNNoise+ Controller

Enhanced RNNoise wrapper with intelligent adaptation:

**Adaptive Features:**
- SNR-based suppression strength (mild/medium/strong)
- VAD probability tracking for speech protection
- Watchdog timer (3ms timeout) with auto-bypass
- NaN/INF guard and recovery mechanisms

**Processing Budget:** ≤3ms per frame (p95)

**Degradation Path:** 
- Timeout detection → Bypass mode (500ms recovery)
- Auto-recovery after stable operation

### Presence Filter

Broad shelf EQ around 1-4 kHz for speech brightness:

**Filter Design:**
- Second-order high-frequency shelf
- Center frequency: 2500 Hz
- Q factor: 0.707 (Butterworth response)
- Boost range: 0-5 dB (scene-dependent)

**Smooth Transitions:**
- Parameter smoothing to avoid audible artifacts
- Real-time boost adjustment based on scene classification

### Downward Expander + Comfort Noise

Gentle noise gate with dead-silence prevention:

**Expander Characteristics:**
- Threshold: -45 dBFS (adjustable per scene)
- Ratio: 2:1 (gentle expansion)
- Attack: 5ms, Release: 50ms
- Maximum attenuation: 6 dB

**VAD Protection:**
- Never gates during detected speech
- 50ms speech protection after VAD onset
- Smooth attack/release to avoid artifacts

**Comfort Noise:**
- Level: -50 dBFS baseline
- AR(1) spectral shaping
- Fade in/out with 10ms ramps
- Prevents dead silence artifacts

### Adaptive Feedback Cancellation

Lightweight NLMS-based feedback canceller:

**AFC Parameters:**
- Filter length: 64 taps (8-16ms at 48kHz)
- NLMS step size: 0.001 (normalized)
- Leak factor: 0.9999 (prevents musical noise)
- Processing budget: ≤0.6ms per frame

**Speech Protection:**
- Adaptation freeze when VAD > 0.7
- 10-frame freeze after speech onset
- Stability guard prevents divergence

**Performance Target:** ≥6 dB additional stable gain vs Phase 1

## Performance Specifications

### Latency Requirements

| Component | Processing Time | Cumulative |
|-----------|----------------|------------|
| Scene Classifier | ≤0.5ms | 0.5ms |
| AFC | ≤0.6ms | 1.1ms |
| RNNoise+ | ≤3.0ms | 4.1ms |
| Presence Filter | ≤0.3ms | 4.4ms |
| Downward Expander | ≤0.4ms | 4.8ms |
| WDRC + Limiter | ≤1.0ms | 5.8ms |
| **Total Processing** | **≤5.8ms** | **35ms E2E** |

### CPU Budget Allocation

- **Scene Classification:** 5% of frame budget
- **RNNoise+ Controller:** 30% of frame budget  
- **Other DSP Components:** 15% of frame budget
- **Phase 1 Components:** 40% of frame budget
- **System Overhead:** 10% reserve

### Memory Footprint

- **Additional Buffers:** 4 × 480 samples × 2 bytes = 3.84 KB
- **Filter States:** ~2 KB (AFC + Presence + Expander)
- **Scene Analysis:** ~1 KB (feature history)
- **Total Phase 2 Addition:** ~7 KB per channel

## Quality Metrics & Acceptance Criteria

### Speech Intelligibility
- **STOI Score:** ≥ baseline, never worse than Phase 1
- **Presence Boost:** No sibilant harshness (crest factor ±1 dB)
- **Noise Reduction:** Effective without speech distortion

### Artifact Prevention
- **No Pumping:** WDRC release adaptation prevents artifacts
- **No Speech Cutting:** VAD-guarded expander operation
- **Consistent Noise Floor:** Comfort noise prevents dead silence

### Feedback Margin
- **Stability Improvement:** ≥6 dB additional stable gain
- **AFC Convergence:** Measurable feedback reduction
- **No Divergence:** Stability guard prevents runaway adaptation

### System Stability
- **Watchdog Protection:** RNNoise+ auto-bypass on overload
- **Graceful Degradation:** Automatic mode reduction under stress
- **Recovery Capability:** Auto-recovery after stable operation

## Telemetry & QA Instrumentation

### Real-time Monitoring

**Scene Statistics:**
```
Scene Histogram: [Speech: 45%, Speech-in-Noise: 30%, Steady-Noise: 20%, Unknown: 5%]
Current Scene: SPEECH_IN_NOISE (confidence: 0.85)
Policy Changes: 15 in last 5 minutes
```

**RNNoise+ Status:**
```
Suppression Strength: 0.65 (MEDIUM)
SNR Estimate: 8.2 dB
VAD Probability: 0.42
Bypass Engagements: 0
Timeout Count: 0
```

**AFC Performance:**
```
Convergence Metric: 0.45
Filter Norm: 2.1
Adaptation Frozen: false
Stability Triggers: 0
```

**Processing Performance:**
```
Average Frame Time: 4.2ms (p95: 5.8ms)
Degradation Triggers: 0
Current Mode: FULL
```

### QA CSV Logging

When enabled, logs include:
- Frame timestamp and processing time
- Scene classification (type, confidence, features)
- Adaptive parameters (RNNoise strength, presence boost, WDRC release)
- Component activity (expander engagement %, AFC convergence)
- Performance metrics (CPU usage, buffer health)

## Tuning Guidelines

### Scene Classification Sensitivity

**Conservative (Stable):**
- Increase scene stability counter (10+ frames)
- Raise confidence thresholds (0.5+ required)
- Slower parameter transitions

**Responsive (Adaptive):**
- Reduce scene stability counter (3-5 frames)
- Lower confidence thresholds (0.3+ sufficient)
- Faster parameter transitions

### RNNoise+ Adaptation

**Quality-Focused:**
- Lower SNR thresholds (favor mild suppression)
- Longer VAD lookback (protect speech)
- Conservative watchdog timeout (2ms)

**Noise-Reduction Focused:**
- Higher SNR thresholds (favor strong suppression)
- Shorter VAD lookback (faster adaptation)
- Relaxed watchdog timeout (4ms)

### Presence Enhancement

**Natural Sound:**
- Lower boost levels (1-3 dB max)
- Wider bandwidth (low Q)
- Higher center frequency (3 kHz+)

**Enhanced Clarity:**
- Higher boost levels (3-5 dB max)
- Narrower bandwidth (higher Q)
- Lower center frequency (2 kHz)

### Noise Gate Sensitivity

**Gentle Operation:**
- Lower threshold (-50 dBFS)
- Lower ratio (1.5:1)
- Longer release time (100ms+)

**Aggressive Gating:**
- Higher threshold (-40 dBFS)
- Higher ratio (3:1)
- Shorter release time (30ms)

## Implementation Notes

### Zero-Allocation Requirements

All processing components maintain zero-allocation hot paths:
- Pre-allocated buffers for all intermediate signals
- Atomic parameter updates (volatile variables)
- No object creation during `process()` calls
- Fixed-size circular buffers for history

### Thread Safety

- Parameter updates use volatile variables
- Statistics use atomic operations where needed
- No shared mutable state between channels
- Lock-free ring buffer communication with AudioEngine

### Error Handling Strategy

1. **Component-level:** Individual processors handle errors gracefully
2. **Chain-level:** DspGraph provides fallback processing modes
3. **System-level:** AudioEngine monitors health and triggers degradation
4. **Recovery:** Automatic recovery after stable operation period

### Integration with Phase 1

Phase 2 components integrate seamlessly with Phase 1 infrastructure:
- Uses existing AudioConfig constants and validation
- Maintains HealthMonitor integration for performance tracking
- Leverages QaHooks for testing and instrumentation
- Preserves all Phase 1 safety guarantees (WDRC + MPO limiting)

This Phase 2 implementation provides enhanced speech clarity and naturalness while maintaining the robust safety and performance characteristics established in Phase 1.