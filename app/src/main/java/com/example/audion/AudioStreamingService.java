package com.example.audion;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.audion.data.AppDatabase;
import com.example.audion.data.CalibrationDao;
import com.example.audion.data.CalibrationEntry;
import com.example.audion.data.HearingProfile;
import com.example.audion.data.HearingProfileDao;
import com.example.audion.R;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class AudioStreamingService extends Service {
    private static final String TAG        = "AudioStreamingService";
    private static final String CHANNEL_ID = "audio_streaming_channel";

    public static final String ACTION_UPDATE_GAIN =
            "com.example.audion.ACTION_UPDATE_GAIN";
    public static final String EXTRA_EAR  = "earSide";
    public static final String EXTRA_FREQ = "frequency";
    public static final String EXTRA_AMPL = "amplitude";

    public static final String PREFS_NAME        = "com.example.audion.PREFERENCES";
    public static final String KEY_NOISE_REMOVAL = "noiseRemoval";
    public static final String KEY_AMPLIFICATION = "amplificationFactor";

    private static final int SAMPLE_RATE  = 48000;
    private static final int CHANNEL_IN   = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT  = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
    private static final int BUF_IN  =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT);
    private static final int BUF_OUT =
            AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT);

    private RNNoise rnnoise;
    private AudioRecord audioRecord;
    private AudioTrack  audioTrack;
    private boolean isProcessing;
    private Thread processThread;

    // move totalFramesWritten to a field so it can be mutated in the lambda
    private long totalFramesWritten = 0;

    // per‐frequency overrides from the UI
    private final Map<String, Map<Integer, Integer>> bandOverrides = new HashMap<>();
    // baseline from calibration step
    private final Map<String, Integer> baselineMap = new HashMap<>();

    private final BroadcastReceiver stopStreamingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isProcessing = false;
            stopForeground(true);
            stopSelf();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1) Handle in‐service gain‐update broadcasts immediately on the main thread.
        if (intent != null && ACTION_UPDATE_GAIN.equals(intent.getAction())) {
            String ear = intent.getStringExtra(EXTRA_EAR);
            int freq  = intent.getIntExtra(EXTRA_FREQ, -1);
            int ampl  = intent.getIntExtra(EXTRA_AMPL, -1);
            if (ear != null && freq > 0 && ampl >= 0) {
                bandOverrides
                  .computeIfAbsent(ear, k -> new HashMap<>())
                  .put(freq, ampl);
                Log.d(TAG, "Override " + ear + " " + freq + "→" + ampl);
            }
            return START_STICKY;
        }

        // 2) Normal startup: register stop‐receiver, post foreground notification
        ContextCompat.registerReceiver(
            this,
            stopStreamingReceiver,
            new IntentFilter("com.example.audion.STOP_STREAMING"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        );

        createNotificationChannel();
        NotificationCompat.Builder nb =
            new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Audio Streaming")
                .setContentText("Running…")
                .setSmallIcon(R.drawable.ic_play);
        startForeground(1, nb.build());

        // 3) Load all saved baselines off the UI thread
        new Thread(() -> {
            int userId = 1;

            // a) pick or create a hearing‐profile
            HearingProfileDao hpDao = AppDatabase
                .getInstance(this)
                .hearingProfileDao();
            List<HearingProfile> all = hpDao.getAllProfiles();
            int profileId;
            if (all.isEmpty()) {
                // HearingProfile(String name, String notes)
                profileId = (int) hpDao.insert(new HearingProfile("Default Profile", ""));
            } else {
                profileId = 0;
                for (HearingProfile p : all) {
                    if (p.getId() > profileId) profileId = p.getId();
                }
            }

            // b) fetch all calibration entries for this user/profile
            CalibrationDao calDao = AppDatabase
                .getInstance(this)
                .calibrationDao();
            List<CalibrationEntry> entries =
                calDao.getForUserProfile(userId, profileId);

            // c) populate baselineMap, and also seed the global amplification factor
            int sumSteps = 0, count = 0;
            for (CalibrationEntry c : entries) {
                baselineMap.put(
                    c.getEarSide(),
                    c.getBaselineStep()
                );
                sumSteps += c.getBaselineStep();
                count++;
                Log.d(TAG, "Loaded baseline "
                    + c.getEarSide() + " → " + c.getBaselineStep());
            }
            // d) initialize the app-wide amplification from the average baseline
            if (count > 0) {
                float avgGain = (sumSteps / (float)count) / 100f;
                SharedPreferences.Editor e = 
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                e.putFloat(KEY_AMPLIFICATION, avgGain).apply();
                Log.d(TAG, "Initialized global amp to " + avgGain);
            }
        }).start();

        // 4) finally, kick off your audio‐processing thread
        startAudioProcessing();
        return START_STICKY;
    }

    private void startAudioProcessing() {
        rnnoise = new RNNoise();
        rnnoise.initialize();

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_FORMAT,
                BUF_IN
        );

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build())
                .setBufferSizeInBytes(BUF_OUT)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        isProcessing = true;
        totalFramesWritten = 0;  // reset the field

        processThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            float[] inFloat     = new float[RNNoise.FRAME_SIZE];
            float[] rnInput     = new float[RNNoise.FRAME_SIZE];
            float[] rnOutScaled = new float[RNNoise.FRAME_SIZE];
            float[] rnOut       = new float[RNNoise.FRAME_SIZE];
            float[] processed   = new float[RNNoise.FRAME_SIZE];

            audioTrack.play();
            audioRecord.startRecording();

            while (isProcessing) {
                if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    break;
                }
                int read = audioRecord.read(
                    inFloat, 0, RNNoise.FRAME_SIZE,
                    AudioRecord.READ_BLOCKING);
                if (read <= 0) break;

                SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                float globalAmp = sp.getFloat(KEY_AMPLIFICATION, 1f);
                boolean nr      = sp.getBoolean(KEY_NOISE_REMOVAL, false);

                // RNNoise expects ±32767
                for (int i = 0; i < read; i++) {
                    rnInput[i] = inFloat[i] * Short.MAX_VALUE;
                }
                if (nr) {
                    rnOutScaled = rnnoise.processFrame(rnInput).audio;
                } else {
                    System.arraycopy(rnInput, 0, rnOutScaled, 0, read);
                }
                for (int i = 0; i < read; i++) {
                    rnOut[i] = rnOutScaled[i] / Short.MAX_VALUE;
                }

                // pick first‐available override map
                String ear = bandOverrides.keySet()
                    .stream().findFirst().orElse("LEFT");
                Map<Integer,Integer> ov =
                    bandOverrides.getOrDefault(ear, new HashMap<>());
                float bandGain = 1f;
                if (!ov.isEmpty()) {
                    float sum = 0;
                    for (int v : ov.values()) sum += (v / 100f);
                    bandGain = sum / ov.size();
                }
                int baseStep    = baselineMap.getOrDefault(ear, 100);
                float baseGain  = baseStep / 100f;
                float finalGain = globalAmp * bandGain * baseGain;

                processFrameWithOversamplingFloat(rnOut, processed, finalGain);

                float inRms  = calculateRMSFloat(inFloat, read);
                float outRms = calculateRMSFloat(processed, read);
                float normIn  = Math.max(0f, Math.min(1f, inRms));
                float normOut = Math.max(0f, Math.min(1f, outRms));
                sendBroadcast(new Intent("com.example.audion.WAVEFORM_UPDATE")
                        .setPackage(getPackageName())
                        .putExtra("inputLevel", normIn)
                        .putExtra("outputLevel", normOut));

                audioTrack.write(processed, 0, read, AudioTrack.WRITE_BLOCKING);
                totalFramesWritten += read;  // now mutating the field

                long framesPlayed = audioTrack.getPlaybackHeadPosition();
                double latencyMs  = ((totalFramesWritten - framesPlayed)
                                    / (double) SAMPLE_RATE) * 1000.0;
                sendBroadcast(new Intent("com.example.audion.LATENCY_UPDATE")
                        .putExtra("LATENCY_MS", latencyMs));
            }
        }, "AudioProc");

        processThread.start();
    }

    private float calculateRMSFloat(float[] buf, int length) {
        float sum = 0;
        for (int i = 0; i < length; i++) sum += buf[i] * buf[i];
        return (float) Math.sqrt(sum / length);
    }

    private float softClipFloat(float x) {
        return (float) Math.tanh(x);
    }

    private void processFrameWithOversamplingFloat(
            float[] in, float[] out, float amp) {
        int N  = RNNoise.FRAME_SIZE;
        int up = 2 * N - 1;
        float[] tmp = new float[up];
        for (int i = 0; i < N; i++) {
            tmp[2*i] = in[i];
            if (i < N-1) tmp[2*i+1] = 0.5f*(in[i] + in[i+1]);
        }
        for (int i = 0; i < up; i++) {
            tmp[i] = safeClip(tmp[i] * amp);
        }
        for (int j = 0; j < N; j++) {
            out[j] = (j < N-1)
                   ? 0.5f*(tmp[2*j] + tmp[2*j+1])
                   : tmp[2*j];
        }
    }

    /** Soft-clip helper */
    private float safeClip(float x) {
        return (float) Math.tanh(x);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(stopStreamingReceiver);
        isProcessing = false;
        try { processThread.join(500); } catch (InterruptedException ignored) {}
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
        }
        if (rnnoise != null) rnnoise.destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Audio Streaming", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class)
                .createNotificationChannel(ch);
        }
    }
}
