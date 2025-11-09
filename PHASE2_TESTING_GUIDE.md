# Phase 2 Testing Guide

## Overview

This document provides comprehensive testing strategies for the Phase 2 audio enhancement pipeline. It covers unit tests, integration tests, performance validation, and quality assurance procedures.

## Test Structure

### Unit Tests

#### 1. SceneClassifierLiteTest
**Purpose**: Test real-time acoustic scene classification

**Key Test Cases**:
- **Scene Detection Accuracy**: 
  - Clean speech → SPEECH classification
  - Speech in noise → SPEECH_IN_NOISE classification  
  - Steady noise → STEADY_NOISE classification
  - Silence → STEADY_NOISE or UNKNOWN

- **Feature Extraction**:
  - SNR estimation accuracy with known signals
  - Spectral centroid computation stability
  - Modulation energy detection for speech vs noise

- **Performance Requirements**:
  - Processing time ≤0.5ms per frame
  - Memory allocation patterns (zero allocation in hot path)
  - Stability with extreme inputs

- **Transition Behavior**:
  - Smooth scene transitions with temporal filtering
  - Confidence building with consistent input
  - Reset functionality

**Test Signals**:
```java
// Clean speech: f0=200Hz + harmonics + 5Hz modulation
// Noisy speech: speech + white noise at various SNRs
// Steady noise: white/pink noise with consistent characteristics
// Silence: zero samples with comfort noise injection
```

#### 2. RnNoiseControllerTest
**Purpose**: Test adaptive RNNoise wrapper with SNR/VAD control

**Key Test Cases**:
- **Adaptive Control**:
  - High SNR speech → light processing (strength ≤0.4)
  - Low SNR speech → strong processing (strength ≥0.6)
  - VAD probability estimation accuracy
  - SNR estimation with known signals

- **Watchdog Protection**:
  - Timeout detection (>3ms processing)
  - Auto-bypass activation
  - Recovery from bypass mode
  - Graceful degradation

- **Stability**:
  - Parameter smoothing during transitions
  - Consistent output for same input
  - Extreme input handling (±32767)
  - Reset functionality

#### 3. DownwardExpanderTest  
**Purpose**: Test VAD-aware noise gate with comfort noise

**Key Test Cases**:
- **VAD Protection**:
  - Speech signals (VAD ≥0.7) → minimal expansion
  - Noise signals (VAD ≤0.3) → aggressive expansion
  - Smooth VAD transition response

- **Gentle Expansion**:
  - Gradual gain reduction (no hard gating)
  - Comfort noise injection during silence
  - AR(1) noise characteristics validation

- **Threshold Behavior**:
  - Signals above threshold → linear passthrough
  - Signals below threshold → gentle compression
  - Smooth crossover region

#### 4. AdaptiveNoisePolicyTest
**Purpose**: Test scene-to-parameter mapping with smooth transitions

**Key Test Cases**:
- **Scene Mapping**:
  - SPEECH → light RNNoise (≤0.4), moderate presence (2-4dB)
  - SPEECH_IN_NOISE → strong RNNoise (0.4-0.8), high presence (3-6dB)
  - STEADY_NOISE → moderate RNNoise (0.3-0.7), minimal presence (≤2dB)
  - UNKNOWN → conservative defaults

- **Confidence Weighting**:
  - High confidence → full scene-specific parameters
  - Low confidence → blend toward neutral parameters
  - Smooth confidence-based interpolation

- **Parameter Validation**:
  - All parameters within valid bounds
  - Smooth transitions (no sudden jumps)
  - Stability with repeated input

### Integration Tests

#### 5. DspGraphPhase2IntegrationTest
**Purpose**: Test complete processing chain integration

**Key Test Cases**:
- **Processing Chain**:
  - Input → AFC → RNNoise+ → Presence → Expander → WDRC → Limiter → Output
  - Signal flow integrity
  - Component interaction stability

- **Scene Adaptation**:
  - Dynamic parameter updates based on scene classification
  - End-to-end adaptive behavior
  - Performance under scene transitions

- **Performance Requirements**:
  - End-to-end latency ≤35ms
  - Individual component budgets:
    - RNNoise+: ≤3ms
    - AFC: ≤0.6ms  
    - Scene Classifier: ≤0.5ms
    - Other components: ≤0.3ms each

- **Stability Testing**:
  - Long-running operation (5000+ frames)
  - Extreme input scenarios
  - Memory allocation patterns
  - Error recovery

## Performance Validation

### Latency Measurement
```java
@Test
public void testLatencyBudget() {
    // Frame processing time measurement
    long startTime = System.nanoTime();
    dspGraph.processFrame(inputBuffer, outputBuffer);
    long processingTime = (System.nanoTime() - startTime) / 1_000_000; // ms
    
    assertTrue("Frame processing ≤ budget", processingTime < 5.0f); // Test margin
}
```

### CPU Usage Profiling
- Individual component timing
- Hot path optimization validation
- Memory allocation tracking
- Garbage collection impact

### Memory Management
```java
@Test
public void testZeroAllocation() {
    // Warm up to initialize static allocations
    for(int i = 0; i < 100; i++) {
        dspGraph.processFrame(inputBuffer, outputBuffer);
    }
    
    // Main test - should have minimal allocation
    long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    
    for(int i = 0; i < 1000; i++) {
        dspGraph.processFrame(inputBuffer, outputBuffer);
    }
    
    long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long allocatedBytes = finalMemory - initialMemory;
    
    assertTrue("Minimal allocation in hot path", allocatedBytes < 10000); // 10KB margin
}
```

## Test Signal Generation

### Speech Signals
```java
private void generateSpeechSignal(short[] buffer, int amplitude) {
    for (int i = 0; i < buffer.length; i++) {
        float t = i / 48000.0f;
        
        // Varying fundamental frequency (150-250 Hz)
        float f0 = 200 + 50 * (float)Math.sin(2 * Math.PI * 3 * t);
        
        // Harmonic content
        float sample = (float)(
            Math.sin(2 * Math.PI * f0 * t) +           // Fundamental
            0.5 * Math.sin(2 * Math.PI * f0 * 2 * t) + // 2nd harmonic
            0.25 * Math.sin(2 * Math.PI * f0 * 3 * t)  // 3rd harmonic
        );
        
        // Amplitude modulation (speech envelope)
        float envelope = 1.0f + 0.4f * (float)Math.sin(2 * Math.PI * 5 * t);
        sample *= envelope;
        
        buffer[i] = (short)Math.max(-32767, Math.min(32767, sample * amplitude));
    }
}
```

### Noise Signals
```java
private void generateSpeechInNoise(short[] buffer, int speechLevel, int noiseLevel) {
    generateSpeechSignal(buffer, speechLevel);
    
    Random random = new Random(12345); // Reproducible noise
    for (int i = 0; i < buffer.length; i++) {
        float noise = (float)random.nextGaussian() * noiseLevel;
        buffer[i] = (short)Math.max(-32767, Math.min(32767, buffer[i] + noise));
    }
}
```

### Feedback Test Signals
```java
private void generateFeedbackSignal(short[] buffer, int amplitude, float delayMs) {
    generateSpeechSignal(buffer, amplitude);
    
    int delaySamples = (int)(delayMs * 48000 / 1000);
    float feedbackGain = 0.3f;
    
    // Add delayed feedback
    for (int i = delaySamples; i < buffer.length; i++) {
        float feedback = buffer[i - delaySamples] * feedbackGain;
        buffer[i] = (short)Math.max(-32767, Math.min(32767, buffer[i] + feedback));
    }
}
```

## Quality Metrics

### Speech Intelligibility
- **STOI (Short-Time Objective Intelligibility)**:
  - Target: ≥95% of baseline
  - Measurement: Compare processed vs clean speech
  - Frequency: Per major scene type

- **SNR Improvement**:
  - Target: 3-6 dB improvement in noisy conditions
  - Measurement: Segmental SNR analysis
  - Validation: A-weighted and perceptual weighting

### Feedback Suppression
- **Additional Stable Gain (ASG)**:
  - Target: ≥6 dB improvement
  - Measurement: Maximum stable gain before oscillation
  - Test conditions: Multiple feedback path delays (5-25ms)

### Spectral Quality
- **Presence Preservation**:
  - Target: <2 dB variation in 2-8 kHz band
  - Measurement: 1/3 octave band analysis
  - Validation: Before/after spectral comparison

## Continuous Integration Tests

### Automated Test Suite
```bash
# Unit tests (fast, runs on every commit)
./gradlew testDebugUnitTest --tests="*SceneClassifier*"
./gradlew testDebugUnitTest --tests="*RnNoiseController*"  
./gradlew testDebugUnitTest --tests="*DownwardExpander*"
./gradlew testDebugUnitTest --tests="*AdaptiveNoisePolicy*"

# Integration tests (slower, runs on pull requests)
./gradlew testDebugUnitTest --tests="*Phase2Integration*"

# Performance tests (runs nightly)
./gradlew testDebugUnitTest --tests="*Performance*"
```

### Performance Regression Detection
- Automated latency measurement
- Memory usage tracking
- CPU utilization monitoring
- Historical performance comparison

## Manual Testing Scenarios

### Real-World Audio Testing
1. **Office Environment**: 
   - Background chatter, keyboard typing, HVAC noise
   - Expected: SPEECH_IN_NOISE classification, moderate NR

2. **Restaurant Environment**:
   - High background noise, multiple speakers
   - Expected: SPEECH_IN_NOISE classification, aggressive NR

3. **Quiet Room**:
   - Minimal background noise, clear speech
   - Expected: SPEECH classification, light processing

4. **Car Environment**:
   - Road noise, engine noise, wind noise
   - Expected: STEADY_NOISE or SPEECH_IN_NOISE, strong NR

### Subjective Quality Assessment
- **A/B Testing**: Phase 1 vs Phase 2 comparison
- **MOS Scoring**: Mean Opinion Score (1-5 scale)
- **Preference Testing**: Pairwise comparisons
- **Listening Fatigue**: Extended wearing comfort

## Debugging and Instrumentation

### Logging Framework
```java
// Component-level telemetry
logger.debug("Scene: {} Confidence: {:.2f} SNR: {:.1f}dB", 
    scene, confidence, snrDb);

logger.debug("RNNoise: {:.2f} Presence: {:.1f}dB Expander: {:.1f}dB",
    rnnoiseStrength, presenceBoostDb, expanderThresholdDb);

// Performance monitoring  
logger.debug("Processing: {:.2f}ms AFC: {:.2f}ms RNN: {:.2f}ms",
    totalTimeMs, afcTimeMs, rnnoiseTimeMs);
```

### Visual Analysis Tools
- **Spectrograms**: Before/after processing comparison
- **Level Meters**: Real-time RMS and peak monitoring  
- **Parameter Plots**: Scene adaptation behavior over time
- **Latency Histograms**: Processing time distribution

## Acceptance Criteria

### Functional Requirements
- ✅ All unit tests pass (>95% coverage)
- ✅ Integration tests demonstrate end-to-end functionality
- ✅ Scene classification accuracy >80% on test corpus
- ✅ Smooth parameter transitions (<10% change per frame)

### Performance Requirements  
- ✅ End-to-end latency ≤35ms (measured)
- ✅ Individual component budgets met
- ✅ Zero critical allocations in audio thread
- ✅ Stable operation >1 hour continuous use

### Quality Requirements
- ✅ STOI ≥95% of baseline in all scenes
- ✅ Additional stable gain ≥6dB improvement
- ✅ Spectral preservation in 2-8kHz band
- ✅ No audible artifacts in normal operation

### Robustness Requirements
- ✅ Graceful handling of extreme inputs
- ✅ Automatic recovery from overload conditions
- ✅ Stable operation across scene transitions
- ✅ Memory usage bounded and predictable

## Test Data Management

### Test Corpus
- **Clean Speech**: 50 utterances, male/female, 5-15 seconds each
- **Noisy Speech**: Same utterances + various noise types at 0, 5, 10, 15 dB SNR
- **Noise Only**: White, pink, babble, car, restaurant (30 seconds each)
- **Feedback Scenarios**: Synthetic feedback at various gains and delays

### Reproducibility
- Fixed random seeds for noise generation
- Version-controlled test signals
- Documented signal parameters
- Baseline reference measurements

This comprehensive testing approach ensures Phase 2 meets all requirements while maintaining the stability and performance characteristics established in Phase 1.