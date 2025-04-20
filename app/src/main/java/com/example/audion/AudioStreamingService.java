package com.example.audion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AudioStreamingService extends Service {

    private static final String TAG = "AudioStreamingService";
    private static final String CHANNEL_ID = "audio_streaming_channel";
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_IN = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    private static final int BUFFER_SIZE_OUT = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

    // SharedPreferences keys.
    public static final String PREFS_NAME = "com.example.audion.PREFERENCES";
    public static final String KEY_NOISE_REMOVAL = "noiseRemoval";
    public static final String KEY_AMPLIFICATION = "amplificationFactor";

    private RNNoise rnnoise;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isProcessing = false;
    private Thread processThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Audio Streaming")
                .setContentText("Audio is running...")
                .setSmallIcon(R.drawable.ic_play) // Use your actual icon here.
                .build();
        startForeground(1, notification);
        startAudioProcessing();
        return START_STICKY;
    }

    /**
     * This helper upsamples the input signal by a factor of 2 using linear interpolation,
     * applies the amplification factor and soft clipping, and then decimates it back to the original size.
     */
    private void processFrameWithOversampling(float[] input, float[] output, float ampFactor) {
        int N = RNNoise.FRAME_SIZE;      // original frame size
        int upLength = 2 * N - 1;          // after linear interpolation
        float[] upsampled = new float[upLength];

        // Upsample: copy each sample to even indices and linearly interpolate between them.
        for (int i = 0; i < N; i++) {
            upsampled[2 * i] = input[i]; // even indices get original sample
            if (i < N - 1) {
                // Interpolate between input[i] and input[i+1]
                upsampled[2 * i + 1] = (input[i] + input[i + 1]) / 2.0f;
            }
        }

        // Apply amplification to the upsampled signal.
        for (int i = 0; i < upLength; i++) {
            upsampled[i] *= ampFactor;
        }

        // Apply soft clipping to each oversampled sample.
        for (int i = 0; i < upLength; i++) {
            upsampled[i] = softClip(upsampled[i]);
        }

        // Low-pass filter and decimate: for each original output sample, average two consecutive oversampled samples.
        for (int j = 0; j < N; j++) {
            if (j < N - 1) {
                output[j] = (upsampled[2 * j] + upsampled[2 * j + 1]) / 2.0f;
            } else {
                output[j] = upsampled[2 * j];
            }
        }
    }

    private void startAudioProcessing() {
        rnnoise = new RNNoise();
        rnnoise.initialize();

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, BUFFER_SIZE_IN);
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
        processThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            short[] audioBuffer = new short[RNNoise.FRAME_SIZE];
            float[] floatBuffer = new float[RNNoise.FRAME_SIZE];
            float[] processedBuffer = new float[RNNoise.FRAME_SIZE];

            audioTrack.play();
            audioRecord.startRecording();

            while (isProcessing) {
                int read = audioRecord.read(audioBuffer, 0, RNNoise.FRAME_SIZE);
                if (read == RNNoise.FRAME_SIZE) {
                    // Convert short PCM to float.
                    for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                        floatBuffer[i] = audioBuffer[i];
                    }

                    // Read preferences for amplification and noise removal.
                    SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    float ampFactor = sp.getFloat(KEY_AMPLIFICATION, 1.0f);
                    boolean noiseRemovalEnabled = sp.getBoolean(KEY_NOISE_REMOVAL, false);

                    // If noise removal is enabled, process the raw buffer.
                    if (noiseRemovalEnabled) {
                        RNNoise.ProcessResult result = rnnoise.processFrame(floatBuffer);
                        // Use the noise-suppressed output as input to our oversampling amplification branch.
                        processFrameWithOversampling(result.audio, processedBuffer, ampFactor);
                    } else {
                        // Without noise removal, apply oversampling amplification directly.
                        processFrameWithOversampling(floatBuffer, processedBuffer, ampFactor);
                    }

                    // Calculate RMS levels for UI update.
                    float inputRMS = calculateRMSLevel(floatBuffer);
                    float outputRMS = calculateRMSLevel(processedBuffer);
                    updateUI(inputRMS, outputRMS);

                    // Convert processed float back to short.
                    for (int i = 0; i < RNNoise.FRAME_SIZE; i++) {
                        audioBuffer[i] = (short) processedBuffer[i];
                    }
                    audioTrack.write(audioBuffer, 0, RNNoise.FRAME_SIZE);
                }
            }
        });
        processThread.start();
    }

    private float calculateRMSLevel(float[] buffer) {
        float sum = 0;
        for (float sample : buffer) {
            sum += sample * sample;
        }
        return (float) Math.sqrt(sum / buffer.length);
    }

    // Soft-clips a sample using tanh (a common soft-clipping function).
    private float softClip(float sample) {
        float normalized = sample / (float) Short.MAX_VALUE;
        float clipped = (float) Math.tanh(normalized);
        return clipped * Short.MAX_VALUE;
    }

    private void updateUI(float inputLevel, float outputLevel) {
        Intent intent = new Intent("com.example.audion.WAVEFORM_UPDATE");
        intent.putExtra("inputLevel", inputLevel);
        intent.putExtra("outputLevel", outputLevel);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        stopAudioProcessing();
        super.onDestroy();
    }

    private void stopAudioProcessing() {
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
        if (rnnoise != null) {
            rnnoise.destroy();
            rnnoise = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Audio Streaming";
            String description = "Channel for streaming audio";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
