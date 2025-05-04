package com.example.audion;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.audion.diarization.SpeakerDiarizationManager;
import com.example.audion.diarization.WaveFileReader;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;

import java.io.IOException;

/**
 * Example class showing how to use the Speaker Diarization functionality.
 */
public class DiarizationExample {
    private static final String TAG = "DiarizationExample";
    
    private final Context context;
    
    public DiarizationExample(Context context) {
        this.context = context;
    }
    
    /**
     * Initialize the diarization engine.
     * Should be called once when the app starts.
     */
    public void initialize() {
        try {
            // Initialize the diarization engine
            SpeakerDiarizationManager.initialize(context);
            
            // Log the required sample rate
            Log.i(TAG, "Required sample rate: " + SpeakerDiarizationManager.getSampleRate() + " Hz");
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize diarization engine", e);
        }
    }
    
    /**
     * Process a WAV file and perform speaker diarization.
     * 
     * @param uri URI of the WAV file to process
     * @return Array of speaker segments
     */
    public OfflineSpeakerDiarizationSegment[] processWavFile(Uri uri) {
        try {
            // Read the WAV file
            float[] samples = WaveFileReader.readWaveFile(context, uri);
            
            // Process the audio with a progress callback
            OfflineSpeakerDiarizationSegment[] segments = SpeakerDiarizationManager.processSpeakerDiarization(
                    samples,
                    (processed, total, arg) -> {
                        // Report progress (0-100%)
                        int progressPercent = (int) (processed * 100.0 / total);
                        Log.i(TAG, "Processing: " + progressPercent + "%");
                        
                        // Return 0 to continue, non-zero to cancel
                        return 0;
                    }
            );
            
            // Log the results
            Log.i(TAG, "Diarization complete. Found " + segments.length + " segments.");
            for (OfflineSpeakerDiarizationSegment segment : segments) {
                Log.i(TAG, segment.toString());
            }
            
            return segments;
        } catch (IOException e) {
            Log.e(TAG, "Error processing WAV file", e);
            return new OfflineSpeakerDiarizationSegment[0];
        }
    }
    
    /**
     * Process audio samples directly.
     * 
     * @param samples Audio samples (mono, float)
     * @return Array of speaker segments
     */
    public OfflineSpeakerDiarizationSegment[] processAudioSamples(float[] samples) {
        // Process the audio without a callback
        OfflineSpeakerDiarizationSegment[] segments = SpeakerDiarizationManager.processSpeakerDiarization(samples);
        
        // Log the results
        Log.i(TAG, "Diarization complete. Found " + segments.length + " segments.");
        for (OfflineSpeakerDiarizationSegment segment : segments) {
            Log.i(TAG, segment.toString());
        }
        
        return segments;
    }
    
    /**
     * Update the clustering configuration.
     * 
     * @param numClusters Number of clusters (-1 for auto-determine)
     * @param threshold Clustering threshold (0.0-1.0)
     */
    public void updateClusteringConfig(int numClusters, float threshold) {
        SpeakerDiarizationManager.updateClusteringConfig(numClusters, threshold);
    }
    
    /**
     * Release resources.
     * Should be called when the app is shutting down.
     */
    public void release() {
        SpeakerDiarizationManager.release();
    }
} 