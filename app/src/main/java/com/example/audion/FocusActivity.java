// FocusActivity.java
package com.example.audion;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.os.Build;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.example.audion.diarization.DirectDiarizationManager;
import com.example.audion.diarization.SpeakerDiarizationManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FocusActivity extends AppCompatActivity
        implements DirectDiarizationManager.DiarizationListener,
                   GlobalSpeakersAdapter.SpeakerSelectionListener {

    private LocalBroadcastManager lbm;
    private static final String TAG = "FocusActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
    private static final int BUFFER_SIZE_IN = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    private static final int BUFFER_SIZE_OUT = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

    private static final int ENROLLMENT_DURATION_SECONDS = 30;
    private static final int ENROLLMENT_SAMPLE_RATE = 16000;
    private static final int ENROLLMENT_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            ENROLLMENT_SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    private static final int ENROLLMENT_SAMPLE_COUNT =
            ENROLLMENT_SAMPLE_RATE * ENROLLMENT_DURATION_SECONDS;

    // rotating scan messages/images
    private final String[] scanMessages = {
        "Warming up your ears…",
        "Catching every whisper…",
        "Spotlighting the speakers…",
        "Prepping your focus lens…"
    };

    private final Handler scanTextHandler = new Handler(Looper.getMainLooper());
    private Runnable scanTextRunnable;
    private int scanTextIndex = 0;

    private RNNoise rnnoise;
    private DirectDiarizationManager diarizationManager;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isProcessing = false;
    private ProcessThread processThread;

    private boolean speakerIsolationEnabled = false;
    private EnrollmentActivity.EnrolledSpeaker selectedEnrolledSpeaker = null;
    private int currentChunkId = 0;

    private AudioRecord enrollmentRecorder;
    private boolean isEnrolling = false;
    private float[] enrollmentBuffer = new float[ENROLLMENT_SAMPLE_COUNT];
    private int recordedSamples = 0;

    private List<EnrollmentActivity.EnrolledSpeaker> enrolledSpeakers = new ArrayList<>();
    private RecyclerView enrolledSpeakersRecyclerView;
    private EnrolledSpeakersAdapter enrolledSpeakersAdapter;

    private GlobalSpeakersAdapter globalSpeakersAdapter;
    private RecyclerView globalSpeakersRecyclerView;

    private Handler mainHandler;
    private MaterialButton enrollButton;
    private BottomNavigationView bottomNav;


    // inline UI
    private FrameLayout defaultPanel;
    private View defaultView, scanView, diarizationView;
    private ProgressBar scanProgressInline, diarizationProgressInline;
    private TextView scanTextView, diarizationStatusInline;

    // amplification
    private SeekBar amplificationSeekBar;
    private float amplificationFactor = 1.0f;

    // start/stop button
    private MaterialButton toggleButton;

    private WaveformView focusWaveform;
    private final BroadcastReceiver wfReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            float level = i.getFloatExtra("outputLevel", 0f);
            focusWaveform.addLevel(level);
        }
    };

    // TabLayout for Normal/Focus
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        // Make true full-screen (hides status bar)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // If you want behind-the-notch support on P+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_focus);

        // ─── Bind TabLayout at the very top ──────────────────────────────────────
        tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText("Normal"));
        tabLayout.addTab(tabLayout.newTab().setText("Focus"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // “Normal” → go back to HomeActivity
                    Intent intent = new Intent(FocusActivity.this, HomeActivity.class);
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }
                // If “Focus” (position 1), do nothing (we’re already here)
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        // Select “Focus” by default
        tabLayout.selectTab(tabLayout.getTabAt(1));

        focusWaveform = findViewById(R.id.focusWaveform);
        lbm = LocalBroadcastManager.getInstance(this);

        defaultPanel = findViewById(R.id.defaultPanel);
        showDefaultPanel();

        toggleButton = findViewById(R.id.toggleButton);
        if (toggleButton != null) {
            toggleButton.setVisibility(View.INVISIBLE);
            toggleButton.setEnabled(false);
        } else {
            Log.w(TAG, "toggleButton not found yet; skipping initial hide");
        }

        // Loading overlay
        View loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingOverlay.setVisibility(View.VISIBLE);

        // Bind UI
        enrollButton                 = findViewById(R.id.enrollButton);
        enrolledSpeakersRecyclerView = findViewById(R.id.enrolledSpeakersRecyclerView);
        bottomNav                    = findViewById(R.id.bottomNavigationView);
        findViewById(R.id.seekBarContainer).setVisibility(View.GONE);
        findViewById(R.id.enrolledSpeakersRecyclerView).setVisibility(View.GONE);
        findViewById(R.id.noSpeakersText).setVisibility(View.GONE);

        amplificationSeekBar = findViewById(R.id.seekBar);
        amplificationSeekBar.setMax(100);
        amplificationSeekBar.setProgress(50);
        amplificationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                amplificationFactor = p / 50f;
                if (globalSpeakersAdapter != null) {
                    globalSpeakersAdapter.updatePlaybackVolume(amplificationFactor);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        toggleButton.setVisibility(View.INVISIBLE);
        toggleButton.setEnabled(false);
        enrollButton.setEnabled(false);

        mainHandler = new Handler(Looper.getMainLooper());

        // Background init
        new Thread(() -> {
            rnnoise = new RNNoise();
            rnnoise.initialize();

            initializeDiarizationManager();

            runOnUiThread(() -> {
                loadingOverlay.setVisibility(View.GONE);
                toggleButton.setEnabled(true);
                enrollButton.setEnabled(true);
                bindAdaptersAndListeners();
            });
        }).start();

        bottomNav.setSelectedItemId(R.id.navigation_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_frequencies) {
                startActivity(new Intent(this, FrequencyActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.navigation_settings) {
                startActivity(new Intent(this, MusicPlayerActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return true;
        });
    }

    private void bindAdaptersAndListeners() {
        // global speakers
        globalSpeakersAdapter = new GlobalSpeakersAdapter(this, new HashMap<>());
        globalSpeakersAdapter.setSelectionListener(this);
        globalSpeakersAdapter.setDiarizationManager(diarizationManager);
        globalSpeakersRecyclerView = findViewById(R.id.globalSpeakersRecyclerView);
        globalSpeakersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        globalSpeakersRecyclerView.setAdapter(globalSpeakersAdapter);
        globalSpeakersRecyclerView.setVisibility(View.GONE);

        // enrolled speakers
        enrolledSpeakersAdapter = new EnrolledSpeakersAdapter(this, enrolledSpeakers);
        enrolledSpeakersAdapter.setOnSpeakerSelectListener((speaker, pos) -> {
            sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
            selectedEnrolledSpeaker = speaker;
            defaultPanel.removeAllViews();
            toggleButton.setVisibility(View.VISIBLE);
            toggleButton.setEnabled(true);
            updateToggleUi(false);
        });
        enrolledSpeakersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        enrolledSpeakersRecyclerView.setAdapter(enrolledSpeakersAdapter);
        loadEnrolledSpeakers();

        enrolledSpeakersRecyclerView.setVisibility(View.GONE);

        enrollButton.setOnClickListener(v -> {
            if (!isEnrolling) {
                startEnrollment();
            } else {
                // Manual cancel
                isEnrolling = false;
                scanTextHandler.removeCallbacks(scanTextRunnable);
                if (enrollmentRecorder != null) {
                    enrollmentRecorder.stop();
                    enrollmentRecorder.release();
                    enrollmentRecorder = null;
                }
                defaultPanel.removeAllViews();
                showDefaultPanel();
                toggleButton.setVisibility(View.INVISIBLE);

                enrollButton.setText("Scan Environment");
                enrollButton.setBackgroundTintList(
                        ColorStateList.valueOf(Color.parseColor("#0F766E"))
                );
            }
        });

        // start/stop processing
        toggleButton.setOnClickListener(v -> toggleProcessing());



        amplificationSeekBar = findViewById(R.id.seekBar);
        amplificationSeekBar.setMax(100);
        amplificationSeekBar.setProgress(50);
        amplificationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                amplificationFactor = p / 50f;
                if (enrolledSpeakersAdapter != null) {
                    enrolledSpeakersAdapter.updatePlaybackVolume(amplificationFactor);
                }
                if (globalSpeakersAdapter != null) {
                    globalSpeakersAdapter.updatePlaybackVolume(amplificationFactor);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
    }

    /** Called when user taps “Stop Scan” during an in-progress scan */
    private void abortEnrollment() {
        if (!isEnrolling) return;
        isEnrolling = false;
        scanTextHandler.removeCallbacks(scanTextRunnable);
        if (enrollmentRecorder != null) {
            enrollmentRecorder.stop();
            enrollmentRecorder.release();
            enrollmentRecorder = null;
        }
        runOnUiThread(() -> {
            showDefaultPanel();
            toggleButton.setVisibility(View.INVISIBLE);
            toggleButton.setEnabled(false);
            enrollButton.setText("Scan Environment");
            enrollButton.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#0F766E"))
            );
            findViewById(R.id.seekBarContainer).setVisibility(View.GONE);
            findViewById(R.id.enrolledSpeakersRecyclerView).setVisibility(View.GONE);
            findViewById(R.id.noSpeakersText).setVisibility(View.GONE);
        });
    }

    private void showDefaultPanel() {
        defaultPanel.removeAllViews();
        View v = getLayoutInflater()
                .inflate(R.layout.default_inline, defaultPanel, false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        defaultPanel.addView(v, lp);
    }

    private void showScanInline() {
        defaultPanel.removeAllViews();
        View scanV = getLayoutInflater()
                .inflate(R.layout.scan_inline, defaultPanel, false);

        scanTextView       = scanV.findViewById(R.id.scanText);
        scanProgressInline = scanV.findViewById(R.id.scanProgress);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        defaultPanel.addView(scanV, lp);

        scanTextIndex = 0;
        scanTextView.setText(scanMessages[scanTextIndex]);
        scanProgressInline.setProgress(0);

        long interval = ENROLLMENT_DURATION_SECONDS * 1000L / scanMessages.length;
        scanTextRunnable = new Runnable() {
            @Override public void run() {
                scanTextIndex = (scanTextIndex + 1) % scanMessages.length;
                scanTextView.setText(scanMessages[scanTextIndex]);
                scanTextHandler.postDelayed(this, interval);
            }
        };
        scanTextHandler.postDelayed(scanTextRunnable, interval);

        new Thread(() -> {
            while (isEnrolling) {
                runOnUiThread(() ->
                    scanProgressInline.setProgress(
                        Math.min(100,
                          (recordedSamples * 100) / ENROLLMENT_SAMPLE_COUNT))
                );
                try { Thread.sleep(100); }
                catch (InterruptedException ignored) { }
            }
        }).start();
    }

    private void showDiarizationInline() {
        defaultPanel.removeAllViews();
        View diaV = getLayoutInflater()
                .inflate(R.layout.diarization_inline, defaultPanel, false);

        diarizationStatusInline   = diaV.findViewById(R.id.diarizationStatus);
        diarizationProgressInline = diaV.findViewById(R.id.diarizationProgress);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        defaultPanel.addView(diaV, lp);
    }

    private void initializeDiarizationManager() {
        try {
            diarizationManager = new DirectDiarizationManager(this);
            diarizationManager.setListener(this);
            boolean inited = false;
            for (int i = 0; i < 3 && !inited; i++) {
                inited = diarizationManager.initialize();
                if (!inited) Thread.sleep(100);
            }
            if (!inited) Log.w(TAG, "Diarization init failed");
        } catch (Exception e) {
            Log.e(TAG, "Error init diarization", e);
        }
    }

    private void toggleProcessing() {
        if (isProcessing) stopProcessing();
        else {
            if (selectedEnrolledSpeaker == null) {
                Toast.makeText(this, "Select a speaker first",
                               Toast.LENGTH_SHORT).show();
                return;
            }
            startProcessing();
        }
    }

    private void startProcessing() {
        if (!hasPermissions()) { requestPermissions(); return; }
        if (isProcessing) return;

        try {
            if (!diarizationManager.isInitialized())
                diarizationManager.initialize();

            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, BUFFER_SIZE_IN);
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

            audioRecord.startRecording();
            audioTrack.play();
            isProcessing = true;
            speakerIsolationEnabled = true;
            updateToggleUi(true);
            focusWaveform.setVisibility(View.VISIBLE);
            focusWaveform.levels.clear();

            processThread = new ProcessThread();
            processThread.setPriority(Thread.MAX_PRIORITY);
            processThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Error starting processing", e);
            Toast.makeText(this, "Error: " + e.getMessage(),
                           Toast.LENGTH_SHORT).show();
        }
    }

    private void stopProcessing() {
        isProcessing = false;
        speakerIsolationEnabled = false;
        if (processThread != null) {
            try { processThread.join(1000); } catch (Exception ignored) { }
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
        focusWaveform.setVisibility(View.GONE);
        updateToggleUi(false);
    }

    private void updateToggleUi(boolean streaming) {
        if (streaming) {
            toggleButton.setText("Stop");
            toggleButton.setIconResource(R.drawable.ic_stop);
            toggleButton.setBackgroundTintList(
                getResources().getColorStateList(R.color.red_circle)
            );
        } else {
            toggleButton.setText("Start");
            toggleButton.setIconResource(R.drawable.ic_play);
            toggleButton.setBackgroundTintList(
                getResources().getColorStateList(R.color.green_circle)
            );
        }
    }

    @Override protected void onStart() {
        super.onStart();
        lbm.registerReceiver(
                wfReceiver,
                new IntentFilter("com.example.audion.WAVEFORM_UPDATE")
        );
    }
    @Override protected void onStop() {
        super.onStop();
        lbm.unregisterReceiver(wfReceiver);
    }

    @Override public void onSpeakerSelected(int speakerId) {
        sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
    }
    @Override public void onSpeakerDeselected() { }

    @Override public void onSpeakersDetected(List<DirectDiarizationManager.SpeakerInfo> list) {
        runOnUiThread(() -> {
            globalSpeakersRecyclerView.setVisibility(View.VISIBLE);
            globalSpeakersAdapter.notifyDataSetChanged();

            sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
        });

        sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
    }
    @Override public void onSpeakersHistoryUpdated(List<List<DirectDiarizationManager.SpeakerInfo>> history) {
        runOnUiThread(() -> {
            globalSpeakersRecyclerView.setVisibility(View.VISIBLE);
            globalSpeakersAdapter.notifyDataSetChanged();
        });
    }
    @Override public void onBufferFillProgress(float p) { }
    @Override public void onProcessingProgress(float p) {
        if (diarizationProgressInline != null) {
            int pct = Math.round(p * 100);
            runOnUiThread(() -> diarizationProgressInline.setProgress(pct));
        }
    }
    @Override public void onDiarizationError(String m) {
        mainHandler.post(() ->
            Toast.makeText(this, "Diarization error: " + m,
                           Toast.LENGTH_SHORT).show()
        );
    }

    private class ProcessThread extends Thread {
        private static final int FRAME_SIZE = RNNoise.FRAME_SIZE;
        @Override public void run() {
            float[] inBuf  = new float[BUFFER_SIZE_IN/4];
            float[] outBuf = new float[BUFFER_SIZE_IN/4];

            while (isProcessing) {
                int r = audioRecord.read(inBuf, 0, inBuf.length,
                                         AudioRecord.READ_BLOCKING);
                if (r < 0) break;
                System.arraycopy(inBuf,  0, outBuf,  0, r);

                // RNNoise
                for (int i = 0; i + FRAME_SIZE <= r; i += FRAME_SIZE) {
                    float[] fb = new float[FRAME_SIZE];
                    System.arraycopy(outBuf, i, fb, 0, FRAME_SIZE);
                    RNNoise.ProcessResult res = rnnoise.processFrame(fb);
                    System.arraycopy(res.audio, 0, outBuf, i, FRAME_SIZE);
                }

                if (diarizationManager != null) {
                    diarizationManager.processAudioFrame(outBuf);
                    currentChunkId = diarizationManager.getCurrentChunkId();
                }

                if (speakerIsolationEnabled && selectedEnrolledSpeaker != null) {
                    isolateEnrolledSpeaker(outBuf, r);
                }

                for (int i = 0; i < r; i++) {
                    outBuf[i] *= amplificationFactor;
                }
                audioTrack.write(outBuf, 0, r, AudioTrack.WRITE_BLOCKING);
                float sum = 0f;
                for (int j = 0; j < r; j++) sum += outBuf[j] * outBuf[j];
                float rms = (float)Math.sqrt(sum / r);
                lbm.sendBroadcast(new Intent("com.example.audion.WAVEFORM_UPDATE")
                        .putExtra("outputLevel", rms));;
            }
        }
    }

    private void isolateEnrolledSpeaker(float[] buf, int size) {
        try {
            for (var s : diarizationManager.getActiveSpeakers(currentChunkId)) {
                if (diarizationManager.isSpeakerMatchingEnrollment(
                        s.getGlobalId(),
                        selectedEnrolledSpeaker.getEmbedding()
                )) {
                    return;
                }
            }
            for (int i = 0; i < size; i++) buf[i] = 0f;
        } catch (Exception ignored) { }
    }

    private void startEnrollment() {
        if (!hasPermissions()) { requestPermissions(); return; }

        selectedEnrolledSpeaker = null;
        toggleButton.setVisibility(View.INVISIBLE);
        toggleButton.setEnabled(false);
        enrolledSpeakers.clear();
        enrolledSpeakersAdapter.notifyDataSetChanged();

        recordedSamples = 0;
        isEnrolling = true;

        runOnUiThread(() -> {
            findViewById(R.id.seekBarContainer).setVisibility(View.GONE);
            findViewById(R.id.enrolledSpeakersRecyclerView).setVisibility(View.GONE);
            findViewById(R.id.noSpeakersText).setVisibility(View.GONE);

            showScanInline();

            enrollButton.setText("Stop Scan");
            enrollButton.setBackgroundTintList(
                    ColorStateList.valueOf(Color.RED)
            );
        });

        enrollmentRecorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                ENROLLMENT_SAMPLE_RATE, CHANNEL_CONFIG_IN,
                AUDIO_FORMAT, ENROLLMENT_BUFFER_SIZE);
        enrollmentRecorder.startRecording();

        new Thread(() -> {
            float[] buf = new float[ENROLLMENT_BUFFER_SIZE / 4];
            while (isEnrolling && recordedSamples < ENROLLMENT_SAMPLE_COUNT) {
                int r = enrollmentRecorder.read(buf, 0, buf.length, AudioRecord.READ_BLOCKING);
                if (r > 0) {
                    int toCopy = Math.min(r, ENROLLMENT_SAMPLE_COUNT - recordedSamples);
                    System.arraycopy(buf, 0, enrollmentBuffer, recordedSamples, toCopy);
                    recordedSamples += toCopy;
                }
            }

            scanTextHandler.removeCallbacks(scanTextRunnable);

            if (recordedSamples >= ENROLLMENT_SAMPLE_COUNT) {
                runOnUiThread(this::showDiarizationInline);
                stopEnrollment();
            }
        }).start();
    }

    private void stopEnrollment() {
        if (!isEnrolling) return;
        isEnrolling = false;
        scanTextHandler.removeCallbacks(scanTextRunnable);
        if (enrollmentRecorder != null) {
            enrollmentRecorder.stop();
            enrollmentRecorder.release();
            enrollmentRecorder = null;
        }

        runOnUiThread(() -> enrollButton.setText("Stop Scan"));
        new Thread(this::processEnrollmentAudio).start();
    }

    private void processEnrollmentAudio() {
        float[] audio = new float[recordedSamples];
        System.arraycopy(enrollmentBuffer, 0, audio, 0, recordedSamples);

        OfflineSpeakerDiarizationSegment[] segs =
                SpeakerDiarizationManager.processSpeakerDiarization(
                        audio,
                        (proc, tot, unused) -> {
                            int pct = (int)(proc * 100f / tot);
                            runOnUiThread(() -> {
                                if (diarizationProgressInline != null)
                                    diarizationProgressInline.setProgress(pct);
                                if (diarizationStatusInline != null)
                                    diarizationStatusInline.setText(
                                            "Loading Speaker Playbacks… " + pct + "%");
                            });
                            return 0;
                        });

        Map<Integer,List<OfflineSpeakerDiarizationSegment>> bySp = new HashMap<>();
        if (segs != null) {
            for (var s : segs) {
                bySp.computeIfAbsent(s.getSpeakerId(),
                        k -> new ArrayList<>()).add(s);
            }
        }

        List<EnrollmentActivity.EnrolledSpeaker> found = new ArrayList<>();
        for (var e : bySp.entrySet()) {
            float[] clip = extractSpeakerAudio(audio, e.getValue());
            if (clip.length < ENROLLMENT_SAMPLE_RATE) continue;
            float[] emb = SpeakerDiarizationManager.extractSpeakerEmbedding(clip);
            if (emb == null) continue;
            float dur = 0;
            for (var seg : e.getValue())
                dur += seg.getEndTime() - seg.getStartTime();
            found.add(new EnrollmentActivity.EnrolledSpeaker(
                    "Speaker " + (enrolledSpeakers.size() + found.size() + 1),
                    emb, dur, clip));
        }

        runOnUiThread(() -> {
            View seekBarContainer   = findViewById(R.id.seekBarContainer);
            RecyclerView enrolled   = findViewById(R.id.enrolledSpeakersRecyclerView);
            TextView noSpeakersText = findViewById(R.id.noSpeakersText);

            if (found.isEmpty()) {
                showPromptNoSpeakersInline();
                toggleButton.setVisibility(View.INVISIBLE);
                seekBarContainer.setVisibility(View.GONE);
                enrolled.setVisibility(View.GONE);
                noSpeakersText.setVisibility(View.VISIBLE);
            } else {
                toggleButton.setVisibility(View.GONE);
                showPromptSpeakersFoundInline(found.size());

                enrolledSpeakers.clear();
                enrolledSpeakers.addAll(found);
                enrolledSpeakersAdapter.notifyDataSetChanged();

                seekBarContainer.setVisibility(View.VISIBLE);
                enrolled.setVisibility(View.VISIBLE);
                noSpeakersText.setVisibility(View.GONE);
            }

            enrollButton.setText("Scan Again");
            enrollButton.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#0F766E"))
            );
            enrollButton.setVisibility(View.VISIBLE);
        });
    }

    private void showPromptNoSpeakersInline() {
        defaultPanel.removeAllViews();
        View noneV = getLayoutInflater()
                .inflate(R.layout.inline_no_speakers_found, defaultPanel, false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        defaultPanel.addView(noneV, lp);
    }
    private void showPromptSpeakersFoundInline(int count) {
        defaultPanel.removeAllViews();
        View v = getLayoutInflater()
            .inflate(R.layout.inline_speakers_found,
                     defaultPanel, false);
        TextView tv = v.findViewById(R.id.speakersFoundTextInline);

        String message = "Found " + count + " speaker" + (count == 1 ? "" : "s")
                   + "\nPlease select one to focus";
        tv.setText(message);
        defaultPanel.addView(v);
    }

    private void loadEnrolledSpeakers() {
        enrolledSpeakers.clear();
        SharedPreferences prefs =
          getSharedPreferences("SpeakerEnrollment", MODE_PRIVATE);
        int count = prefs.getInt("speaker_count", 0);
        for (int i = 0; i < count; i++) {
            String name   = prefs.getString(
                "speaker_" + i + "_name", "Unknown");
            String embStr = prefs.getString(
                "speaker_" + i + "_embedding", "");
            float dur     = prefs.getFloat(
                "speaker_" + i + "_duration", 0f);
            float[] emb   = stringToFloatArray(embStr);
            if (emb != null && emb.length > 0) {
                enrolledSpeakers.add(
                  new EnrollmentActivity.EnrolledSpeaker(
                    name, emb, dur, null));
            }
        }
        enrolledSpeakersAdapter.notifyDataSetChanged();
    }

    private float[] stringToFloatArray(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split(",");
        float[] res = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { res[i] = Float.parseFloat(parts[i]); }
            catch (Exception e) { return null; }
        }
        return res;
    }

    private float[] extractSpeakerAudio(
        float[] all,
        List<OfflineSpeakerDiarizationSegment> segs) {

        int total = 0;
        for (var s : segs) {
            int st = (int)(s.getStartTime()
                            * ENROLLMENT_SAMPLE_RATE);
            int en = (int)(s.getEndTime()
                            * ENROLLMENT_SAMPLE_RATE);
            total += en - st;
        }

        float[] out = new float[total];
        int ptr = 0;
        for (var s : segs) {
            int st = (int)(s.getStartTime()
                            * ENROLLMENT_SAMPLE_RATE);
            int en = (int)(s.getEndTime()
                            * ENROLLMENT_SAMPLE_RATE);
            int len = en - st;
            System.arraycopy(all, st, out, ptr, len);
            ptr += len;
        }
        if (ptr < total) {
            float[] t = new float[ptr];
            System.arraycopy(out, 0, t, 0, ptr);
            return t;
        }
        return out;
    }

    public float getAmplificationFactor() {
        return amplificationFactor;
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this,
          Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.RECORD_AUDIO},
            PERMISSION_REQUEST_CODE);
    }

    @Override protected void onDestroy() {
        if (isProcessing) stopProcessing();
        if (rnnoise != null) { rnnoise.destroy(); rnnoise = null; }
        if (diarizationManager != null) {
            diarizationManager.release();
            diarizationManager = null;
        }
        super.onDestroy();
    }

    /** Called by adapters for inline status updates */
    public void updateStatus(String message) {
        // runOnUiThread(() -> statusText.setText(message));
    }
}
