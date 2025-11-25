# Speaker Diarization Integration

This package provides Java classes for integrating Sherpa-Onnx Speaker Diarization functionality into the AndroidApp project.

## Setup Instructions

### 1. Add Native Libraries

Copy the Sherpa-Onnx JNI libraries to the appropriate architecture-specific directories:
- `app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so`
- `app/src/main/jniLibs/armeabi-v7a/libsherpa-onnx-jni.so`
- `app/src/main/jniLibs/x86/libsherpa-onnx-jni.so`
- `app/src/main/jniLibs/x86_64/libsherpa-onnx-jni.so`

### 2. Add Model Files

Download and add the required model files to the assets directory:

1. **Segmentation Model**:
   - Download from: https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2
   - Extract and rename to `segmentation.onnx`
   - Place in `app/src/main/assets/`

2. **Embedding Model**:
   - Download from: https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx
   - Rename to `embedding.onnx`
   - Place in `app/src/main/assets/`

## Usage

### Initialization

Initialize the diarization engine when your app starts:

```java
// In your Application class or main activity
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // Initialize the speaker diarization engine
    SpeakerDiarizationManager.initialize(getApplicationContext());
}
```

### Process a WAV File

```java
Uri audioUri = Uri.parse("content://path/to/audio.wav");
try {
    // Read the WAV file
    float[] samples = WaveFileReader.readWaveFile(context, audioUri);
    
    // Process the audio
    OfflineSpeakerDiarizationSegment[] segments = 
        SpeakerDiarizationManager.processSpeakerDiarization(samples);
    
    // Use the results
    for (OfflineSpeakerDiarizationSegment segment : segments) {
        Log.i("Diarization", String.format(
            "Speaker %d: %.2f - %.2f seconds", 
            segment.getSpeaker(), 
            segment.getStart(), 
            segment.getEnd()
        ));
    }
} catch (IOException e) {
    Log.e("Diarization", "Error processing WAV file", e);
}
```

### Process with Progress Callback

```java
SpeakerDiarizationManager.processSpeakerDiarization(
    samples,
    (processed, total, arg) -> {
        // Update progress UI
        int progressPercent = (int) (processed * 100.0 / total);
        updateProgressBar(progressPercent);
        
        // Return 0 to continue, non-zero to cancel
        return 0;
    }
);
```

### Customize Clustering

```java
// Change number of speakers (-1 for auto-detection)
SpeakerDiarizationManager.updateClusteringConfig(-1, 0.5f);

// Force specific number of speakers
SpeakerDiarizationManager.updateClusteringConfig(2, 0.5f);
```

### Release Resources

```java
@Override
protected void onDestroy() {
    // Release diarization resources
    SpeakerDiarizationManager.release();
    super.onDestroy();
}
```

## Example Class

See `com.example.androidapp.DiarizationExample` for a complete example of how to use the diarization functionality.

## Troubleshooting

1. **Library not found errors**: Make sure the native libraries are in the correct directories and have the correct names.

2. **Model loading errors**: Verify that the model files are in the assets directory and have the correct names.

3. **Out of memory errors**: Large audio files may require more memory. Consider processing audio in chunks.

4. **Performance issues**: Speaker diarization is CPU-intensive. Consider running it in a background thread or service. 