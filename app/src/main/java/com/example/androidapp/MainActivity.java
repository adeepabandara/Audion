package com.example.androidapp;

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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
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
    
    // UI Elements
    private ProgressBar inputLevelMeter;
    private ProgressBar outputLevelMeter;
    private TextView vadProbabilityText;
    private TextView noiseReductionText;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize RNNoise
        rnnoise = new RNNoise();
        rnnoise.initialize();

        // Initialize UI elements
        toggleButton = findViewById(R.id.toggleButton);
        noiseRemovalSwitch = findViewById(R.id.noiseRemovalSwitch);
        inputLevelMeter = findViewById(R.id.inputLevelMeter);
        outputLevelMeter = findViewById(R.id.outputLevelMeter);
        vadProbabilityText = findViewById(R.id.vadProbability);
        noiseReductionText = findViewById(R.id.noiseReduction);

        // Set progress bar ranges (using 16-bit audio range)
        inputLevelMeter.setMax(Short.MAX_VALUE);
        outputLevelMeter.setMax(Short.MAX_VALUE);

        toggleButton.setOnClickListener(v -> toggleProcessing());
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
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, BUFFER_SIZE_IN);

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
            inputLevelMeter.setProgress((int) Math.abs(inputLevel));
            outputLevelMeter.setProgress((int) Math.abs(outputLevel));
            vadProbabilityText.setText(String.format("%.1f%%", vadProb * 100));
            noiseReductionText.setText(String.format("%.1f dB", noiseReduction));
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

    private class ProcessThread extends Thread {
        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            short[] audioBuffer = new short[RNNoise.FRAME_SIZE];
            float[] floatBuffer = new float[RNNoise.FRAME_SIZE];
            
            audioTrack.play();
            audioRecord.startRecording();

            while (isProcessing) {
                int read = audioRecord.read(audioBuffer, 0, RNNoise.FRAME_SIZE);
                if (read == RNNoise.FRAME_SIZE) {
                    if (noiseRemovalEnabled) {
                        // Convert to float for RNNoise
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

                        // Convert back to shorts for playback
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            audioBuffer[i] = (short) result.audio[i];
                        }
                    } else {
                        // Direct playback without noise removal
                        float inputRMS = calculateRMSLevel(convertToFloat(audioBuffer));
                        updateUI(inputRMS, inputRMS, 0, 0);
                    }

                    // Play processed or direct audio
                    audioTrack.write(audioBuffer, 0, RNNoise.FRAME_SIZE);
                }
            }
        }
    }

    private float[] convertToFloat(short[] input) {
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProcessing();
        if (rnnoise != null) {
            rnnoise.destroy();
        }
    }
}