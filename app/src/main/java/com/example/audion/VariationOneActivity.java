package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;
import android.content.Intent;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.button.MaterialButton;

public class VariationOneActivity extends AppCompatActivity {
    private static final String TAG = "VariationOneActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_IN = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    private static final int BUFFER_SIZE_OUT = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

    private RNNoise rnnoise;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isProcessing = false;
    private boolean noiseRemovalEnabled = false;
    private Button toggleButton;
    private SwitchMaterial noiseRemovalSwitch;
    private ProcessThread processThread;
    
    // UI Elements (Waveform views)
    private Handler mainHandler;
    private WaveformView waveformInputView;
    private WaveformView waveformOutputView;
    
    // Amplification Components
    private SeekBar amplificationSeekBar;
    // Changed: Removed invalid declarations. Initialized default values for currentDb and amplificationFactor.
    private float currentDb = 0.0f;           // Default dB (0 dB means no amplification)
    private float amplificationFactor = 1.0f;   // 10^(0/20) = 1 (i.e., unity gain)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_variation_one);
        waveformInputView = findViewById(R.id.waveformInput);
        waveformOutputView = findViewById(R.id.waveformOutput);

        // Initialize RNNoise
        rnnoise = new RNNoise();
        rnnoise.initialize();

        // Initialize UI elements
        toggleButton = findViewById(R.id.toggleButton);
        noiseRemovalSwitch = findViewById(R.id.noiseRemovalSwitch);
        amplificationSeekBar = findViewById(R.id.seekBar);
        // Other UI elements have been commented out
        // inputLevelMeter = findViewById(R.id.inputLevelMeter);
        // outputLevelMeter = findViewById(R.id.outputLevelMeter);
        // vadProbabilityText = findViewById(R.id.vadProbability);
        // noiseReductionText = findViewById(R.id.noiseReduction);

        // Listen for changes on the SeekBar
        amplificationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // ***** LIMIT THE RANGE (NEW ADDITION) *****
                // Define a reduced maximum dB value for more manageable gain (e.g., 40dB)
                float maxDb = 40.0f; // Adjusted maximum dB for a more manageable gain
                
                // Map the progress value to the 0 to maxDb range
                currentDb = (progress / (float) seekBar.getMax()) * maxDb;
                
                // Convert the dB value to a linear amplification factor
                amplificationFactor = (float) Math.pow(10, currentDb / 20.0f);
                
                // Optionally, update your UI or log the current state for debugging
                Log.d("Amplification", "Progress: " + progress + " dB: " + currentDb +
                        " Amplification Factor: " + amplificationFactor);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not used
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not used
            }
        });

        // Set progress bar ranges (using 16-bit audio range) if needed
        // inputLevelMeter.setMax(Short.MAX_VALUE);
        // outputLevelMeter.setMax(Short.MAX_VALUE);

        toggleButton.setOnClickListener(v -> {
            MaterialButton materialButton = (MaterialButton) toggleButton;

            if (materialButton.getText().toString().equals("Start")) {
                materialButton.setText("Stop");
                materialButton.setIconResource(R.drawable.ic_stop);
                materialButton.setBackgroundTintList(getResources().getColorStateList(R.color.red_circle)); // 🔴 Change to red
            } else {
                materialButton.setText("Start");
                materialButton.setIconResource(R.drawable.ic_play);
                materialButton.setBackgroundTintList(getResources().getColorStateList(R.color.green_circle)); // 🟢 Change to green
            }
            toggleProcessing();
        });

        noiseRemovalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            noiseRemovalEnabled = isChecked;
        });
        mainHandler = new Handler(Looper.getMainLooper());

        // Request permissions if needed
        if (!hasPermissions()) {
            requestPermissions();
        }
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Recording permission granted",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Recording permission denied",
                        Toast.LENGTH_SHORT).show();
                toggleButton.setEnabled(false);
            }
        }
    }

    private void toggleProcessing() {
        if (!isProcessing) {
            startProcessing();
        } else {
            stopProcessing();
        }
    }

    private void startProcessing() {
        if (!hasPermissions()) {
            requestPermissions();
            return;
        }

        // Initialize audio input
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                BUFFER_SIZE_IN
        );

        // Initialize audio output
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build())
                .setBufferSizeInBytes(BUFFER_SIZE_OUT)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        // Start processing thread
        isProcessing = true;
        processThread = new ProcessThread();
        processThread.start();

        toggleButton.setText("Stop");
        noiseRemovalSwitch.setEnabled(true);
    }

    private void stopProcessing() {
        isProcessing = false;
        if (processThread != null) {
            try {
                processThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping process thread", e);
            }
            processThread = null;
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }

        toggleButton.setText("Start");
        noiseRemovalSwitch.setEnabled(false);
        noiseRemovalSwitch.setChecked(false);
        noiseRemovalEnabled = false;

        // Reset UI
        updateUI(0, 0, 0, 0);
    }

    private void updateUI(float inputLevel, float outputLevel, float vadProb, float noiseReduction) {
        mainHandler.post(() -> {
            // Update other UI elements if needed
            // inputLevelMeter.setProgress((int) Math.abs(inputLevel));
            // outputLevelMeter.setProgress((int) Math.abs(outputLevel));
            // vadProbabilityText.setText(String.format("%.1f%%", vadProb * 100));
            // noiseReductionText.setText(String.format("%.1f dB", noiseReduction));

            // Update waveform views (ensure these are already initialized)
            waveformInputView.addAmplitude(Math.abs(inputLevel));
            waveformOutputView.addAmplitude(Math.abs(outputLevel));
        });
    }

    private float calculateRMSLevel(float[] buffer) {
        float sum = 0;
        for (float sample : buffer) {
            sum += sample * sample;
        }
        return (float) Math.sqrt(sum / buffer.length);
    }

    private float calculateNoiseReduction(float inputRMS, float outputRMS) {
        if (inputRMS > 0 && outputRMS > 0) {
            return 20 * (float) Math.log10(outputRMS / inputRMS);
        }
        return 0;
    }

    // ***** NEW ADDITION: Soft Clipping / Dynamic Range Compression *****
    /**
     * Applies soft clipping to a given sample using a tanh function.
     * This provides smooth compression, avoiding harsh distortion from hard clipping.
     *
     * @param sample The amplified sample value (can be outside short range)
     * @return The soft-clipped sample scaled back to the short range.
     */
    private float softClip(float sample) {
        // Normalize the sample to [-1, 1] using Short.MAX_VALUE
        float normalized = sample / (float) Short.MAX_VALUE;
        // Apply tanh for smooth compression (soft clipping)
        float clipped = (float) Math.tanh(normalized);
        // Scale back to short's range
        return clipped * Short.MAX_VALUE;
    }

    private class ProcessThread extends Thread {
        @Override
        public void run() {
            // Set high priority for real-time audio processing
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            short[] audioBuffer = new short[RNNoise.FRAME_SIZE];
            float[] floatBuffer = new float[RNNoise.FRAME_SIZE];

            audioTrack.play();
            audioRecord.startRecording();

            while (isProcessing) {
                int read = audioRecord.read(audioBuffer, 0, RNNoise.FRAME_SIZE);
                if (read == RNNoise.FRAME_SIZE) {
                    if (noiseRemovalEnabled) {
                        // Convert to float for RNNoise processing
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            floatBuffer[i] = audioBuffer[i];
                        }

                        float inputRMS = calculateRMSLevel(floatBuffer);

                        // Process through RNNoise
                        RNNoise.ProcessResult result = rnnoise.processFrame(floatBuffer);
                        float outputRMS = calculateRMSLevel(result.audio);

                        // Calculate noise reduction in dB
                        float noiseReduction = calculateNoiseReduction(inputRMS, outputRMS);

                        // Update UI with all metrics
                        updateUI(inputRMS, outputRMS, result.vadProbability, noiseReduction);

                        // Convert processed float data back to short for playback and apply amplification
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            float amplifiedSample = result.audio[i] * amplificationFactor;
                            
                            // ***** NEW ADDITION: Apply soft clipping instead of hard clipping *****
                            amplifiedSample = softClip(amplifiedSample);
                            
                            audioBuffer[i] = (short) amplifiedSample;
                        }
                    } else {
                        // Direct playback without noise removal
                        // Convert short to float
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            floatBuffer[i] = audioBuffer[i];
                        }

                        float inputRMS = calculateRMSLevel(floatBuffer);

                        // Apply amplification and soft clipping
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            float amplifiedSample = floatBuffer[i] * amplificationFactor;
                            // ***** NEW ADDITION: Apply soft clipping instead of hard clipping *****
                            amplifiedSample = softClip(amplifiedSample);
                            audioBuffer[i] = (short) amplifiedSample;
                        }

                        // Update UI: for this branch, output level is the amplified RMS
                        float outputRMS = calculateRMSLevel(floatBuffer) * amplificationFactor;
                        updateUI(inputRMS, outputRMS, 0, 0);
                    }
                    // Write processed (or direct) audio to playback
                    audioTrack.write(audioBuffer, 0, RNNoise.FRAME_SIZE);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProcessing();
        if (rnnoise != null) {
            rnnoise.destroy();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Close current activity and go back
        return true;
    }
}
