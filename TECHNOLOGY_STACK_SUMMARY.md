# Audion Mobile Application - Technology Stack Summary

## 📱 **Platform & Core Architecture**
- **Platform**: Android (API 27+ minimum, targeting API 34)
- **Language**: Java (JDK 11) with JNI for native components
- **Build System**: Gradle 8.7.3 with Android Gradle Plugin
- **Native Build**: CMake 3.22.1 for C/C++ compilation
- **Architecture**: Multi-threaded real-time audio processing with DSP pipeline

## 🎵 **Audio Processing & DSP**

### **Core Audio Engine**
- **Primary Engine**: `AudioEngine.java` - Production-grade low-latency audio engine
- **DSP Pipeline**: `DspGraph.java` - 8-stage per-ear processing chain
  - Feedback Cancellation → RNNoise → Scene Classification → Adaptive Policy → Presence Filter → Downward Expander → WDRC → Limiter
- **Processing Modes**: FULL, BYPASS_RNOISE, BYPASS_WDRC, PASSTHROUGH, SAFE, FOCUS
- **Latency**: ≤35ms round-trip for real-time hearing assistance

### **Sound Processing Libraries**
- **RNNoise**: Mozilla's neural network-based noise suppression
  - Native C implementation with JNI wrapper (`RNNoise.java`)
  - Frame size: 480 samples (10ms at 48kHz)
  - Features: VAD (Voice Activity Detection) + noise reduction
  - Location: `app/src/main/cpp/rnnoise/` (complete source integration)

- **AudioTrack/AudioRecord**: Android native audio I/O
  - Sample Rate: 48kHz for processing, 44.1kHz for calibration
  - Format: PCM 16-bit mono/stereo
  - Buffer management with ring buffers for zero-allocation processing

### **Clinical Audio Components**
- **ANSI_AudiometryEngine**: ANSI S3.6 compliant audiometry
  - Hughson-Westlake procedure for threshold detection
  - 11 standard frequencies: 125Hz-8000Hz
  - Clinical-grade pure tone generation and testing
- **CalibrationProfile**: 3-frequency MCL/UCL calibration system (500Hz, 1000Hz, 2000Hz)

## 🗃️ **Data Management & Storage**

### **Local Database**
- **ORM**: Room 2.5.0 (SQLite wrapper)
- **Database**: `AppDatabase.java` with migration support
- **Core Entities**:
  - `CalibrationProfileEntity` - MCL/UCL per ear calibration data
  - `AudiometryResult` - Hearing threshold test results
  - `HearingProfile` - User hearing profiles
  - `User` - User management
- **DAOs**: Type-safe database access with query optimization

### **Data Persistence Patterns**
- **Configuration**: SharedPreferences for feature flags and settings
- **Audio Profiles**: JSON serialization for complex audio settings
- **Real-time Data**: Lock-free ring buffers in memory
- **Clinical Data**: Normalized database schema with foreign key constraints

## 🔧 **External Dependencies & SDKs**

### **Core Android Libraries**
```gradle
// UI & Material Design
implementation 'androidx.appcompat:appcompat:1.7.0'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.constraintlayout:constraintlayout:2.2.0'

// Database
implementation 'androidx.room:room-runtime:2.5.0'
annotationProcessor 'androidx.room:room-compiler:2.5.0'

// Animations & UI
implementation 'com.airbnb.android:lottie:6.0.0'
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
implementation 'com.github.KihonRyuu:TourGuide:v1.0.18-SNAPSHOT'
```

### **Speech Recognition & NLP**
- **Vosk**: Offline speech recognition
  - `implementation "com.alphacephei:vosk-android:0.3.32+"`
  - US English model for mobile applications
  - Real-time transcription capabilities in `CaptionActivity.java`

### **Native Libraries**
- **JNA**: Java Native Access for native library integration
  - `implementation "net.java.dev.jna:jna:5.5.0@aar"`
- **RNNoise**: Custom-compiled Mozilla RNNoise with Android optimizations

## 🎧 **Real-Time Audio Processing**

### **Audio Capture & Playback**
```java
// Audio I/O Configuration
Sample Rate: 48kHz (processing) / 44.1kHz (calibration)
Buffer Size: 480 samples (10ms frames)
Format: AudioFormat.ENCODING_PCM_16BIT
Channels: MONO input, STEREO output
Latency: THREAD_PRIORITY_URGENT_AUDIO threads
```

### **Processing Pipeline**
1. **Capture Thread**: `AudioRecord` → Ring Buffer
2. **DSP Thread**: Ring Buffer → `DspGraph` (per-ear) → Ring Buffer
3. **Playback Thread**: Ring Buffer → `AudioTrack`
4. **Feature Switching**: Runtime pipeline selection via `FeatureFlags.java`

### **Zero-Allocation Design**
- Pre-allocated audio buffers for real-time processing
- Lock-free ring buffer communication
- Thread-safe atomic operations
- Memory pool management for DSP components

## 🧪 **Testing & Quality Assurance**

### **Testing Frameworks**
```gradle
// Unit Testing
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.8.0'
testImplementation 'org.robolectric:robolectric:4.11.1'

// Android Testing
androidTestImplementation 'androidx.test.ext:junit:1.2.1'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
```

### **Validation Components**
- **Safety Validation**: Limiter protection and amplitude monitoring
- **Clinical Compliance**: ANSI S3.6 standard adherence
- **Performance Testing**: Latency measurement and allocation tracking
- **Integration Testing**: End-to-end audio pipeline validation

## 🔒 **Security & Permissions**
- **Audio Permissions**: `RECORD_AUDIO` for microphone access
- **Storage**: Internal app storage for models and profiles
- **Privacy**: All processing performed locally (no cloud dependencies)

## 📊 **Performance Characteristics**
- **Memory**: Zero-allocation audio loops for real-time processing
- **CPU**: Multi-threaded pipeline with per-core optimization
- **Latency**: ≤35ms total system latency
- **Battery**: Optimized for continuous operation with power management
- **Compatibility**: Android 8.1+ (API 27) through Android 14+ (API 34)

## 🎯 **Specialized Features**
- **Personalization**: `PersonalizedGainMapper` with user-specific audio profiles
- **Focus Mode**: Speech-enhancement processing mode
- **Dual-Pipeline**: Legacy fallback system with new DSP pipeline
- **Clinical Integration**: Professional audiometry with threshold detection
- **Real-time Monitoring**: Health monitoring and performance telemetry

---
**Architecture**: Production-grade hearing aid application with clinical-level audio processing, real-time noise suppression, and ANSI-compliant audiometry testing capabilities.