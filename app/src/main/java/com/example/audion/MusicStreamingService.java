package com.example.audion;

import com.audion.psap.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MusicStreamingService extends Service {

    private static final String TAG = "MusicStreamingService";
    private static final String CHANNEL_ID = "music_streaming_channel";
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // For simplicity, assume BUFFER_SIZE is determined by AudioTrack.
    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

    // SharedPreferences keys.
    public static final String PREFS_NAME = "com.example.audion.PREFERENCES";
    public static final String KEY_NOISE_REMOVAL = "noiseRemoval";
    public static final String KEY_AMPLIFICATION = "amplificationFactor";
    
    private RNNoise rnnoise;
    private AudioTrack audioTrack;
    private boolean isProcessing = false;
    private Thread processThread;
    private String filePath; // Music file path.

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Music Amplifier")
                .setContentText("Amplified Music is playing")
                .setSmallIcon(R.drawable.ic_play) // Replace with your icon.
                .build();
        startForeground(1, notification);
        filePath = intent.getStringExtra("FILE_PATH");
        startAudioProcessing();
        return START_STICKY;
    }
    
    private void startAudioProcessing() {
        rnnoise = new RNNoise();
        rnnoise.initialize();
        
        // For simplicity, assume the music file is a 16-bit PCM WAV file.
        // Skip the 44-byte header.
        isProcessing = true;
        processThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            try (FileInputStream fis = new FileInputStream(filePath)) {
                // Skip WAV header (44 bytes)
                fis.skip(44);
                audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AUDIO_FORMAT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(CHANNEL_CONFIG_OUT)
                                .build())
                        .setBufferSizeInBytes(BUFFER_SIZE)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
                audioTrack.play();
                
                byte[] buffer = new byte[BUFFER_SIZE];
                short[] shortBuffer = new short[BUFFER_SIZE / 2];
                float[] floatBuffer = new float[shortBuffer.length];
                float[] processedBuffer = new float[shortBuffer.length];
                
                SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                while (isProcessing && fis.available() > 0) {
                    int bytesRead = fis.read(buffer);
                    if (bytesRead == -1) break;
                    // Convert byte array (little endian) to short array.
                    ByteBuffer.wrap(buffer, 0, bytesRead)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortBuffer, 0, bytesRead / 2);
                    // Convert shorts to floats.
                    for (int i = 0; i < shortBuffer.length; i++) {
                        floatBuffer[i] = shortBuffer[i];
                    }
                    
                    float ampFactor = sp.getFloat(KEY_AMPLIFICATION, 1.0f);
                    boolean noiseRemovalEnabled = sp.getBoolean(KEY_NOISE_REMOVAL, false);
                    
                    if (noiseRemovalEnabled) {
                        RNNoise.ProcessResult result = rnnoise.processFrame(floatBuffer);
                        for (int i = 0; i < floatBuffer.length; i++) {
                            processedBuffer[i] = result.audio[i];
                        }
                    } else {
                        System.arraycopy(floatBuffer, 0, processedBuffer, 0, floatBuffer.length);
                    }
                    
                    // Apply global amplification.
                    for (int i = 0; i < processedBuffer.length; i++) {
                        processedBuffer[i] *= ampFactor;
                        processedBuffer[i] = softClip(processedBuffer[i]);
                    }
                    
                    // Convert processed float back to short.
                    for (int i = 0; i < processedBuffer.length; i++) {
                        shortBuffer[i] = (short) processedBuffer[i];
                    }
                    audioTrack.write(shortBuffer, 0, shortBuffer.length);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error in music processing", e);
            }
        });
        processThread.start();
    }
    
    private float softClip(float sample) {
        float normalized = sample / (float) Short.MAX_VALUE;
        return (float) Math.tanh(normalized) * Short.MAX_VALUE;
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
            CharSequence name = "Music Streaming";
            String description = "Channel for streaming amplified music";
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
