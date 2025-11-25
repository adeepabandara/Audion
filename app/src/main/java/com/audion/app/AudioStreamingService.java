package com.audion.app;

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

import com.audion.app.data.AppDatabase;
import com.audion.app.data.CalibrationDao;
import com.audion.app.data.CalibrationEntry;
import com.audion.app.data.HearingProfile;
import com.audion.app.data.HearingProfileDao;
import com.audion.app.data.HearingTestResult;
import com.audion.app.data.HearingTestResultDao;
import com.audion.app.R;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class AudioStreamingService extends Service {
    private static final String TAG        = "AudioStreamingService";
    private static final String CHANNEL_ID = "audio_streaming_channel";

    public static final String ACTION_UPDATE_GAIN =
            "com.audion.app.ACTION_UPDATE_GAIN";
    public static final String EXTRA_EAR  = "earSide";
    public static final String EXTRA_FREQ = "frequency";
    public static final String EXTRA_AMPL = "amplitude";

    public static final String ACTION_SET_PROCESSING_MODE = "com.audion.app.ACTION_SET_PROCESSING_MODE";
    public static final String EXTRA_PROCESSING_MODE = "processingMode";
    
    public static final String PREFS_NAME        = "com.audion.app.PREFERENCES";
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
    private long totalFramesWritten = 0;

    // per‐frequency overrides from the UI
    private final Map<String, Map<Integer, Integer>> bandOverrides = new HashMap<>();
    // calibration baseline per ear
    private final Map<String, Integer> baselineMap = new HashMap<>();
    // pure-tone audiogram steps per ear/frequency
    private final Map<String, Map<Integer, Integer>> audiogramMap = new HashMap<>();

    // the four band-pass filters
    private BandPassFilter[] filterbank = new BandPassFilter[4];

    private final BroadcastReceiver stopStreamingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            isProcessing = false;
            stopForeground(true);
            stopSelf();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // handle gain update broadcasts
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

        // normal startup
        ContextCompat.registerReceiver(
                this,
                stopStreamingReceiver,
                new IntentFilter("com.audion.app.STOP_STREAMING"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        createNotificationChannel();
        NotificationCompat.Builder nb =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Audio Streaming")
                        .setContentText("Running…")
                        .setSmallIcon(R.drawable.ic_play);
        startForeground(1, nb.build());

        // load calibration and audiogram baselines
        new Thread(() -> {
            int userId = 1;
            // pick or create a hearing profile
            HearingProfileDao hpDao = AppDatabase
                    .getInstance(this)
                    .hearingProfileDao();
            List<HearingProfile> all = hpDao.getAllProfiles();
            int profileId = all.isEmpty()
                    ? (int) hpDao.insert(new HearingProfile("Default Profile", ""))
                    : all.get(0).getId();

            // calibration entries
            CalibrationDao calDao = AppDatabase
                    .getInstance(this)
                    .calibrationDao();
            List<CalibrationEntry> entries =
                    calDao.getForUserProfile(userId, profileId);
            for (CalibrationEntry c : entries) {
                baselineMap.put(c.getEarSide(), c.getBaselineStep());
                Log.d(TAG, "Loaded calibration " +
                        c.getEarSide() + " → " + c.getBaselineStep());
            }

            // audiogram results
            HearingTestResultDao htrDao = AppDatabase
                    .getInstance(this)
                    .hearingTestResultDao();
            List<HearingTestResult> results =
                    htrDao.getResultsForUserAndProfile(userId, profileId);
            for (HearingTestResult r : results) {
                audiogramMap
                        .computeIfAbsent(r.getEarSide(), k -> new HashMap<>())
                        .put(r.getFrequency(), r.getAmplitudeStep());
                Log.d(TAG, "Loaded audiogram " +
                        r.getEarSide() + " " + r.getFrequency() + "→" + r.getAmplitudeStep());
            }

            // seed global amplification from average calibration
            if (!baselineMap.isEmpty()) {
                int sum = 0;
                for (int v : baselineMap.values()) sum += v;
                float avg = (sum / (float)baselineMap.size()) / 100f;
                SharedPreferences.Editor e =
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                e.putFloat(KEY_AMPLIFICATION, avg).apply();
                Log.d(TAG, "Initialized global amp to " + avg);
            }

            // ─── Initialize our 4-band filterbank here ───────────────────────
            // Bands: 250–750, 750–1500, 1500–3000, 3000–6000 Hz
            filterbank[0] = new BandPassFilter(250, 750, SAMPLE_RATE);
            filterbank[1] = new BandPassFilter(750, 1500, SAMPLE_RATE);
            filterbank[2] = new BandPassFilter(1500, 3000, SAMPLE_RATE);
            filterbank[3] = new BandPassFilter(3000, 6000, SAMPLE_RATE);
        }).start();

        // start audio processing
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
        totalFramesWritten = 0;

        processThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            float[] inBuf   = new float[RNNoise.FRAME_SIZE];
            float[] rnIn    = new float[RNNoise.FRAME_SIZE];
            float[] rnOut   = new float[RNNoise.FRAME_SIZE];
            float[] procBuf = new float[RNNoise.FRAME_SIZE];

            audioTrack.play();
            audioRecord.startRecording();

            while (isProcessing) {
                int r = audioRecord.read(
                        inBuf, 0, RNNoise.FRAME_SIZE,
                        AudioRecord.READ_BLOCKING);
                if (r <= 0) break;

                SharedPreferences sp =
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                float globalAmp = sp.getFloat(KEY_AMPLIFICATION, 1f);
                boolean nr      = sp.getBoolean(KEY_NOISE_REMOVAL, false);

                // RNNoise processing
                for (int i = 0; i < r; i++) rnIn[i] = inBuf[i] * Short.MAX_VALUE;
                if (nr) {
                    rnOut = rnnoise.processFrame(rnIn).audio;
                } else {
                    System.arraycopy(rnIn, 0, rnOut, 0, r);
                }
                for (int i = 0; i < r; i++) rnOut[i] /= Short.MAX_VALUE;

                // ═══ SIMPLIFIED GAIN APPLICATION (No 4-band filterbank, no tanh()) ═══
                // When RNNoise is active, avoid double spectral processing
                // The new SimpleAudioEngine pipeline handles proper DSP chain
                // Legacy AudioStreamingService: Just apply global gain
                for (int i = 0; i < r; i++) {
                    procBuf[i] = rnOut[i] * globalAmp;
                    
                    // Soft clamp to ±0.95 (no tanh waveshaping - prevents harmonic distortion)
                    if (procBuf[i] > 0.95f) procBuf[i] = 0.95f;
                    if (procBuf[i] < -0.95f) procBuf[i] = -0.95f;
                }

                // waveform broadcast remains intact
                float inRms  = calculateRMSFloat(inBuf, r);
                float outRms = calculateRMSFloat(procBuf, r);
                float normIn  = Math.max(0f, Math.min(1f, inRms));
                float normOut = Math.max(0f, Math.min(1f, outRms));
                Intent wave = new Intent("com.audion.app.WAVEFORM_UPDATE")
                        .setPackage(getPackageName())
                        .putExtra("inputLevel",  normIn)
                        .putExtra("outputLevel", normOut);
                sendBroadcast(wave);

                audioTrack.write(procBuf, 0, r, AudioTrack.WRITE_BLOCKING);
                totalFramesWritten += r;
            }
        }, "AudioProc");

        processThread.start();
    }

    private float calculateRMSFloat(float[] buf, int len) {
        float sum = 0; for (int i = 0; i < len; i++) sum += buf[i] * buf[i];
        return (float)Math.sqrt(sum / len);
    }

    @Override public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(stopStreamingReceiver);
        isProcessing = false;
        try { processThread.join(500); } catch (InterruptedException ignored) {}
        if (audioRecord != null) { audioRecord.stop(); audioRecord.release(); }
        if (audioTrack  != null) { audioTrack.stop();  audioTrack.release();  }
        if (rnnoise     != null) { rnnoise.destroy();                     }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Audio Streaming", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    // ─── Simple 2nd‐order Butterworth bandpass helper ─────────────────────────
// ─── Proper 2nd-order Butterworth band-pass filter ─────────────────────
    private static class BandPassFilter {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        /**
         * @param fLow Lower cutoff frequency in Hz
         * @param fHigh Upper cutoff frequency in Hz
         * @param fs Sampling rate in Hz
         */
        public BandPassFilter(double fLow, double fHigh, double fs) {
            // center frequency and Q
            double f0 = Math.sqrt(fLow * fHigh);
            double w0 = 2 * Math.PI * f0 / fs;
            double BW = fHigh - fLow;
            double Q  = f0 / BW;

            double alpha = Math.sin(w0) / (2 * Q);
            double cosw0 = Math.cos(w0);

            // RBJ cookbook coefficients (band-pass)
            double A0 = 1 + alpha;
            double B0 = alpha;
            double B1 = 0;
            double B2 = -alpha;
            double A1 = -2 * cosw0;
            double A2 = 1 - alpha;

            // normalize
            b0 = B0 / A0;
            b1 = B1 / A0;
            b2 = B2 / A0;
            this.a1 = A1 / A0;
            this.a2 = A2 / A0;

            x1 = x2 = y1 = y2 = 0;
        }

        /**
         * Process one block of samples.
         * @param in  input buffer (length >= n)
         * @param out output buffer (length >= n)
         * @param n   number of samples
         */
        public void process(float[] in, float[] out, int n) {
            for (int i = 0; i < n; i++) {
                double x0 = in[i];
                double y0 = b0 * x0
                        + b1 * x1
                        + b2 * x2
                        - a1 * y1
                        - a2 * y2;
                out[i] = (float)y0;
                // shift delays
                x2 = x1; x1 = x0;
                y2 = y1; y1 = y0;
            }
        }


        private double A0(){ return 1 + Math.sin(Math.log(2)/2); } // placeholder
    }
}
