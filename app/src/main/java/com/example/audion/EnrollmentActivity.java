package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.audion.diarization.DirectDiarizationManager;
import com.example.audion.diarization.SpeakerDiarizationManager;
import com.example.audion.diarization.SpeakerEmbeddingMatcher;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnrollmentActivity extends AppCompatActivity implements EnrolledSpeakersAdapter.SpeakerActionListener {
    private static final String TAG = "EnrollmentActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SAMPLE_RATE = 16000; // Match diarization sample rate
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Enrollment duration in seconds
    private static final int ENROLLMENT_DURATION_SECONDS = 30;
    private static final int ENROLLMENT_SAMPLE_COUNT = SAMPLE_RATE * ENROLLMENT_DURATION_SECONDS;
    
    // UI Elements
    private Button recordButton;
    private Button doneButton;
    private Button resetButton;
    private ProgressBar recordingProgress;
    private TextView statusText;
    private RecyclerView enrolledSpeakersRecyclerView;
    
    // Audio recording
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private float[] recordedSamples;
    private int recordedSampleCount = 0;
    
    // Noise reduction
    private RNNoise rnnoise;
    private boolean noiseReductionEnabled = true; // Enable noise reduction by default
    
    // Diarization
    private DirectDiarizationManager diarizationManager;
    private EnrolledSpeakersAdapter speakersAdapter;
    private Handler mainHandler;
    
    // Enrolled speakers storage
    private List<EnrolledSpeaker> enrolledSpeakers = new ArrayList<>();
    private SpeakerEmbeddingMatcher embeddingMatcher = new SpeakerEmbeddingMatcher();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enrollment);
        
        // Initialize UI elements
        recordButton = findViewById(R.id.recordButton);
        doneButton = findViewById(R.id.doneButton);
        resetButton = findViewById(R.id.resetButton);
        recordingProgress = findViewById(R.id.recordingProgress);
        statusText = findViewById(R.id.statusText);
        enrolledSpeakersRecyclerView = findViewById(R.id.enrolledSpeakersRecyclerView);
        
        // Initialize RNNoise for noise reduction
        rnnoise = new RNNoise();
        rnnoise.initialize();
        
        // Set up RecyclerView
        speakersAdapter = new EnrolledSpeakersAdapter(this, enrolledSpeakers);
        speakersAdapter.setSpeakerActionListener(this);
        enrolledSpeakersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        enrolledSpeakersRecyclerView.setAdapter(speakersAdapter);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize diarization manager
        try {
            diarizationManager = new DirectDiarizationManager(this);
            boolean initialized = diarizationManager.initialize();
            if (!initialized) {
                Toast.makeText(this, "Failed to initialize diarization", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing diarization", e);
            Toast.makeText(this, "Diarization error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Allocate memory for recorded samples
        recordedSamples = new float[ENROLLMENT_SAMPLE_COUNT];
        
        // Set up record button
        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
        
        // Done button returns to main activity
        doneButton.setOnClickListener(v -> {
            saveEnrolledSpeakers();
            setResult(RESULT_OK);
            finish();
        });
        
        // Reset button clears all enrolled speakers
        resetButton.setOnClickListener(v -> {
            showResetConfirmationDialog();
        });
        
        // Load any previously enrolled speakers
        loadEnrolledSpeakers();
        
        // Request permissions if needed
        if (!hasPermissions()) {
            requestPermissions();
        }
    }
    
    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Recording permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Recording permission denied", Toast.LENGTH_SHORT).show();
                recordButton.setEnabled(false);
            }
        }
    }
    
    private void startRecording() {
        if (!hasPermissions()) {
            requestPermissions();
            return;
        }
        
        try {
            // Reset sample counter
            recordedSampleCount = 0;
            
            // Initialize AudioRecord
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE);
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "Failed to initialize AudioRecord", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Start recording
            audioRecord.startRecording();
            isRecording = true;
            recordButton.setText("Stop Recording");
            statusText.setText("Recording... Speak naturally for " + ENROLLMENT_DURATION_SECONDS + " seconds");
            
            // Start recording thread
            new Thread(this::recordingThread).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void recordingThread() {
        float[] buffer = new float[BUFFER_SIZE / 4]; // BUFFER_SIZE is in bytes, float is 4 bytes
        final int FRAME_SIZE = RNNoise.FRAME_SIZE;
        
        while (isRecording && recordedSampleCount < ENROLLMENT_SAMPLE_COUNT) {
            int read = audioRecord.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            
            if (read > 0) {
                // Apply noise reduction if enabled
                if (noiseReductionEnabled) {
                    // Process buffer in frame-sized chunks
                    for (int i = 0; i < read; i += FRAME_SIZE) {
                        int framesToProcess = Math.min(FRAME_SIZE, read - i);
                        if (framesToProcess == FRAME_SIZE) {
                            // Create a buffer for this frame
                            float[] frameBuffer = new float[FRAME_SIZE];
                            System.arraycopy(buffer, i, frameBuffer, 0, FRAME_SIZE);
                            
                            // Process with RNNoise
                            RNNoise.ProcessResult result = rnnoise.processFrame(frameBuffer);
                            
                            // Copy processed audio back to the buffer
                            System.arraycopy(result.audio, 0, buffer, i, FRAME_SIZE);
                        }
                    }
                }
                
                // Copy samples to our recording buffer
                int remainingSpace = ENROLLMENT_SAMPLE_COUNT - recordedSampleCount;
                int samplesToAdd = Math.min(read, remainingSpace);
                
                System.arraycopy(buffer, 0, recordedSamples, recordedSampleCount, samplesToAdd);
                recordedSampleCount += samplesToAdd;
                
                // Update progress
                final int progress = recordedSampleCount * 100 / ENROLLMENT_SAMPLE_COUNT;
                mainHandler.post(() -> {
                    recordingProgress.setProgress(progress);
                });
                
                if (recordedSampleCount >= ENROLLMENT_SAMPLE_COUNT) {
                    mainHandler.post(this::stopRecording);
                    break;
                }
            }
        }
    }
    
    private void stopRecording() {
        if (!isRecording) return;
        
        // Stop recording
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        
        recordButton.setText("Record");
        
        // Process the recorded audio
        if (recordedSampleCount > 0) {
            statusText.setText("Processing audio...");
            
            // Process on background thread
            new Thread(this::processRecordedAudio).start();
        } else {
            statusText.setText("No audio recorded");
        }
    }
    
    private void processRecordedAudio() {
        try {
            // Copy only the valid samples
            float[] processableSamples = new float[recordedSampleCount];
            System.arraycopy(recordedSamples, 0, processableSamples, 0, recordedSampleCount);
            
            // Perform diarization
            mainHandler.post(() -> statusText.setText("Running speaker diarization..."));
            
            // Process with progress callback
            OfflineSpeakerDiarizationSegment[] segments = SpeakerDiarizationManager.processSpeakerDiarization(
                    processableSamples,
                    (processed, total, arg) -> {
                        final int progress = (int) (processed * 100.0 / total);
                        mainHandler.post(() -> {
                            recordingProgress.setProgress(progress);
                        });
                        return 0; // Return 0 to continue
                    });
            
            // Extract speaker audio and add to our list
            if (segments != null && segments.length > 0) {
                extractSpeakersFromSegments(processableSamples, segments);
            } else {
                mainHandler.post(() -> {
                    statusText.setText("No speakers detected. Try recording again.");
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing audio", e);
            mainHandler.post(() -> {
                statusText.setText("Error: " + e.getMessage());
            });
        }
    }
    
    private void extractSpeakersFromSegments(float[] audioSamples, OfflineSpeakerDiarizationSegment[] segments) {
        // Group segments by speaker
        Map<Integer, List<OfflineSpeakerDiarizationSegment>> speakerSegments = new HashMap<>();
        
        for (OfflineSpeakerDiarizationSegment segment : segments) {
            int speakerId = segment.getSpeakerId();
            speakerSegments.computeIfAbsent(speakerId, k -> new ArrayList<>()).add(segment);
        }
        
        // For each speaker, extract audio and create embedding
        List<EnrolledSpeaker> newSpeakers = new ArrayList<>();
        for (Map.Entry<Integer, List<OfflineSpeakerDiarizationSegment>> entry : speakerSegments.entrySet()) {
            int speakerId = entry.getKey();
            List<OfflineSpeakerDiarizationSegment> speakerSegmentList = entry.getValue();
            
            // Extract audio for this speaker
            float[] speakerAudio = extractSpeakerAudio(audioSamples, speakerSegmentList);
            
            // If we have enough audio (at least 1 second)
            if (speakerAudio.length >= SAMPLE_RATE) {
                // Extract embedding for the speaker
                float[] embedding = SpeakerDiarizationManager.extractSpeakerEmbedding(speakerAudio);
                
                if (embedding != null) {
                    // Compute total speech duration
                    float duration = 0;
                    for (OfflineSpeakerDiarizationSegment segment : speakerSegmentList) {
                        duration += segment.getEndTime() - segment.getStartTime();
                    }
                    
                    EnrolledSpeaker speaker = new EnrolledSpeaker(
                            "Speaker " + (enrolledSpeakers.size() + newSpeakers.size() + 1),
                            embedding,
                            duration,
                            speakerAudio);
                    
                    newSpeakers.add(speaker);
                }
            }
        }
        
        // Update UI with new speakers
        if (!newSpeakers.isEmpty()) {
            mainHandler.post(() -> {
                enrolledSpeakers.addAll(newSpeakers);
                speakersAdapter.notifyDataSetChanged();
                statusText.setText("Found " + newSpeakers.size() + " new speakers. You can rename them by tapping on their names.");
            });
        } else {
            mainHandler.post(() -> {
                statusText.setText("No speakers with enough speech detected. Try recording again.");
            });
        }
    }
    
    private float[] extractSpeakerAudio(float[] audioSamples, List<OfflineSpeakerDiarizationSegment> segments) {
        // Calculate total number of samples
        int totalSamples = 0;
        for (OfflineSpeakerDiarizationSegment segment : segments) {
            int startSample = (int) (segment.getStartTime() * SAMPLE_RATE);
            int endSample = (int) (segment.getEndTime() * SAMPLE_RATE);
            totalSamples += (endSample - startSample);
        }
        
        // Extract audio from segments
        float[] speakerAudio = new float[totalSamples];
        int currentSample = 0;
        
        for (OfflineSpeakerDiarizationSegment segment : segments) {
            int startSample = (int) (segment.getStartTime() * SAMPLE_RATE);
            int endSample = (int) (segment.getEndTime() * SAMPLE_RATE);
            
            // Check bounds
            if (startSample < 0) startSample = 0;
            if (endSample > audioSamples.length) endSample = audioSamples.length;
            
            int segmentLength = endSample - startSample;
            
            // Copy segment audio to the output array
            if (segmentLength > 0) {
                System.arraycopy(audioSamples, startSample, speakerAudio, currentSample, segmentLength);
                currentSample += segmentLength;
            }
        }
        
        // If we used fewer samples than expected, trim the array
        if (currentSample < totalSamples) {
            float[] trimmedAudio = new float[currentSample];
            System.arraycopy(speakerAudio, 0, trimmedAudio, 0, currentSample);
            return trimmedAudio;
        }
        
        return speakerAudio;
    }
    
    private void showResetConfirmationDialog() {
        if (enrolledSpeakers.isEmpty()) {
            Toast.makeText(this, "No speakers to reset", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Reset All Speakers")
                .setMessage("Are you sure you want to remove all enrolled speakers? This cannot be undone.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    resetAllSpeakers();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void resetAllSpeakers() {
        enrolledSpeakers.clear();
        speakersAdapter.notifyDataSetChanged();
        
        // Clear from shared preferences as well
        SharedPreferences prefs = getSharedPreferences("SpeakerEnrollment", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        statusText.setText("All speakers have been removed");
        Toast.makeText(this, "All enrolled speakers have been removed", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDeleteSpeaker(int position) {
        if (position >= 0 && position < enrolledSpeakers.size()) {
            // Show confirmation dialog
            new AlertDialog.Builder(this)
                    .setTitle("Delete Speaker")
                    .setMessage("Are you sure you want to delete " + enrolledSpeakers.get(position).getName() + "?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        EnrolledSpeaker removed = enrolledSpeakers.remove(position);
                        speakersAdapter.notifyItemRemoved(position);
                        statusText.setText("Removed speaker: " + removed.getName());
                        
                        // Save changes to preferences
                        saveEnrolledSpeakers();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }
    
    private void saveEnrolledSpeakers() {
        try {
            // Save to SharedPreferences
            SharedPreferences prefs = getSharedPreferences("SpeakerEnrollment", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Clear previous entries
            editor.clear();
            
            // Save speaker count
            editor.putInt("speaker_count", enrolledSpeakers.size());
            
            // Save each speaker
            for (int i = 0; i < enrolledSpeakers.size(); i++) {
                EnrolledSpeaker speaker = enrolledSpeakers.get(i);
                editor.putString("speaker_" + i + "_name", speaker.getName());
                
                // Save embedding as string (convert float[] to string)
                String embeddingStr = floatArrayToString(speaker.getEmbedding());
                editor.putString("speaker_" + i + "_embedding", embeddingStr);
                
                // Save duration
                editor.putFloat("speaker_" + i + "_duration", speaker.getDuration());
            }
            
            editor.apply();
            
            Log.i(TAG, "Saved " + enrolledSpeakers.size() + " enrolled speakers");
        } catch (Exception e) {
            Log.e(TAG, "Error saving enrolled speakers", e);
        }
    }
    
    private void loadEnrolledSpeakers() {
        try {
            SharedPreferences prefs = getSharedPreferences("SpeakerEnrollment", MODE_PRIVATE);
            int speakerCount = prefs.getInt("speaker_count", 0);
            
            for (int i = 0; i < speakerCount; i++) {
                String name = prefs.getString("speaker_" + i + "_name", "Unknown Speaker");
                String embeddingStr = prefs.getString("speaker_" + i + "_embedding", "");
                float duration = prefs.getFloat("speaker_" + i + "_duration", 0);
                
                // Convert string to float[]
                float[] embedding = stringToFloatArray(embeddingStr);
                
                if (embedding != null && embedding.length > 0) {
                    EnrolledSpeaker speaker = new EnrolledSpeaker(name, embedding, duration, null);
                    enrolledSpeakers.add(speaker);
                }
            }
            
            Log.i(TAG, "Loaded " + enrolledSpeakers.size() + " enrolled speakers");
            speakersAdapter.notifyDataSetChanged();
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading enrolled speakers", e);
        }
    }
    
    // Helper methods for saving and loading float arrays
    private String floatArrayToString(float[] array) {
        if (array == null) return "";
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
    
    private float[] stringToFloatArray(String str) {
        if (str == null || str.isEmpty()) return null;
        
        String[] parts = str.split(",");
        float[] result = new float[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing float: " + parts[i], e);
                return null;
            }
        }
        
        return result;
    }
    
    @Override
    protected void onDestroy() {
        // Clean up resources
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        
        // if (diarizationManager != null) {
        //     diarizationManager.release();
        // }
        
        if (rnnoise != null) {
            rnnoise.destroy();
        }
        
        super.onDestroy();
    }
    
    // Class to store enrolled speaker information
    public static class EnrolledSpeaker {
        private String name;
        private final float[] embedding;
        private final float duration;
        private final float[] audioSamples;
        
        public EnrolledSpeaker(String name, float[] embedding, float duration, float[] audioSamples) {
            this.name = name;
            this.embedding = embedding;
            this.duration = duration;
            this.audioSamples = audioSamples;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public float[] getEmbedding() {
            return embedding;
        }
        
        public float getDuration() {
            return duration;
        }
        
        public float[] getAudioSamples() {
            return audioSamples;
        }
    }
} 