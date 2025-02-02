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
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class AudioStreamingActivity extends AppCompatActivity {
    private static final String TAG = "AudioStreamingActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;

    // Audio configs
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_IN =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    private static final int BUFFER_SIZE_OUT =
            AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

    // RNNoise
    private RNNoise rnnoise;

    // Recording / Playback
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private ProcessThread processThread;

    // Flags
    private boolean isProcessing = false;
    private boolean noiseRemovalEnabled = false;

    // UI Elements
    private Button toggleButton;
    private SeekBar amplificationSeekBar;
    private Switch noiseRemovalSwitch;

    // Amplification factor
    private float amplificationFactor = 1.0f; // default no amplification

    // Handler for UI updates from worker thread
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_stream); // <-- Your new layout

        // Initialize RNNoise
        rnnoise = new RNNoise();
        rnnoise.initialize();

        // Find views
        toggleButton = findViewById(R.id.toggleButton);
        noiseRemovalSwitch = findViewById(R.id.noiseRemovalSwitch);
        TextView noiseRemovalLabel = findViewById(R.id.noiseRemovalLabel);
        amplificationSeekBar = findViewById(R.id.amplificationSeekBar);

        mainHandler = new Handler(Looper.getMainLooper());

        // Amplification (default from SeekBar)
        amplificationFactor = amplificationSeekBar.getProgress() / 10.0f;
        amplificationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Map [0..50] -> [0..5.0]
                amplificationFactor = progress / 10.0f;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { /* Not used */ }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { /* Not used */ }
        });

        // Switch for noise removal
        noiseRemovalSwitch.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            noiseRemovalEnabled = isChecked;
        });

        // Toggle button to start/stop
        toggleButton.setOnClickListener(view -> toggleProcessing());

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

        // Create AudioRecord
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                BUFFER_SIZE_IN
        );

        // Create AudioTrack
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

        isProcessing = true;
        processThread = new ProcessThread();
        processThread.start();

        // Update button text and background (GREEN -> RED)
        toggleButton.setText("Stop");
        toggleButton.setBackgroundResource(R.drawable.circle_red);
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

        // Reset button text and background (RED -> GREEN)
        toggleButton.setText("Start");
        toggleButton.setBackgroundResource(R.drawable.circle_green);
    }

    // Worker thread for real-time audio
    private class ProcessThread extends Thread {
        @Override
        public void run() {
            // Priority for real-time audio
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            short[] audioBuffer = new short[RNNoise.FRAME_SIZE];
            float[] floatBuffer = new float[RNNoise.FRAME_SIZE];

            audioTrack.play();
            audioRecord.startRecording();

            while (isProcessing) {
                int read = audioRecord.read(audioBuffer, 0, RNNoise.FRAME_SIZE);
                if (read == RNNoise.FRAME_SIZE) {
                    if (noiseRemovalEnabled) {
                        // Convert short to float
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            floatBuffer[i] = audioBuffer[i];
                        }

                        // RNNoise processing
                        RNNoise.ProcessResult result = rnnoise.processFrame(floatBuffer);

                        // Amplify
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            float amplified = result.audio[i] * amplificationFactor;
                            // Clip to short range
                            if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE;
                            if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE;
                            audioBuffer[i] = (short) amplified;
                        }
                    } else {
                        // Direct audio: no RNNoise
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            floatBuffer[i] = audioBuffer[i];
                        }
                        // Amplify
                        for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                            float amplified = floatBuffer[i] * amplificationFactor;
                            if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE;
                            if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE;
                            audioBuffer[i] = (short) amplified;
                        }
                    }

                    // Write to speaker
                    audioTrack.write(audioBuffer, 0, RNNoise.FRAME_SIZE);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure we stop audio if the activity is destroyed
        stopProcessing();
        if (rnnoise != null) {
            rnnoise.destroy();
        }
    }
}
