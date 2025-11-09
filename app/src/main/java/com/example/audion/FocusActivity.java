// FocusActivity.java
package com.example.audion;

import androidx.appcompat.app.AlertDialog;
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

import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

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

    private DirectDiarizationManager diarizationManager;
    private boolean isProcessing = false;

    private boolean speakerIsolationEnabled = false;
    private EnrollmentActivity.EnrolledSpeaker selectedEnrolledSpeaker = null;
    private int currentChunkId = 0;
    
    // Speaker isolation update handler
    private final Handler speakerCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable speakerCheckRunnable;

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


    private boolean allowAbove40 = false;
    private boolean allowAbove70 = false;
    private int     lastProgress = 0;

    private final float maxDb = 100f;

    private SeekBar.OnSeekBarChangeListener gainChangeListener;

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
        gainChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                // compute dB, thresholds
                float curDb = (progress / (float) sb.getMax()) * maxDb;
                int maxProgress = sb.getMax();
                int threshold40 = Math.round((60f / maxDb) * maxProgress);
                int threshold70 = Math.round((90f / maxDb) * maxProgress);

                // grab the three layers of the track
                LayerDrawable ld = (LayerDrawable) sb.getProgressDrawable().mutate();
                Drawable prLayer  = ld.findDrawableByLayerId(android.R.id.progress);
                Drawable secLayer = ld.findDrawableByLayerId(android.R.id.secondaryProgress);
                Drawable bgLayer  = ld.findDrawableByLayerId(android.R.id.background);

                // fetch our colors
                int primaryColor    = ContextCompat.getColor(FocusActivity.this, R.color.primary);
                int bgColor         = ContextCompat.getColor(FocusActivity.this, R.color.background);
                int redAlert        = ContextCompat.getColor(FocusActivity.this, R.color.red_alert);
                int lightRedAlert   = ContextCompat.getColor(FocusActivity.this, R.color.red_alert_light);

                // tint the filled portion: red if ≥60 dB, otherwise primary
                prLayer.setTint(curDb >= 60f ? redAlert : primaryColor);
                // the 0→40dB zone (secondaryProgress) stays background
                secLayer.setTint(bgColor);
                // the rest of the bar is always light‐red
                bgLayer.setTint(lightRedAlert);
                sb.setSecondaryProgress(threshold40);

                // also tint the thumb the same way
                Drawable thumb = sb.getThumb().mutate();
                thumb.setTint(curDb >= 60f ? redAlert : primaryColor);
                sb.setThumb(thumb);

                if (fromUser) {
                    // ── Extreme warning (70 dB) ─────────────────────────
                    if (curDb > 70f && !allowAbove70 && lastProgress <= threshold70) {
                        sb.setEnabled(false);
                        sb.setOnSeekBarChangeListener(null);
                        sb.setProgress(threshold70);
                        lastProgress = threshold70;
                        showThresholdDialog(
                                "Extreme Gain Warning",
                                "You are about to exceed 70 dB of amplification. This can cause severe distortion or hearing damage. Continue?",
                                () -> {
                                    allowAbove70 = true;
                                    sb.setEnabled(true);
                                    sb.setProgress(progress);
                                    lastProgress = progress;
                                    applyGain(progress, maxProgress, maxDb);
                                    sb.setOnSeekBarChangeListener(gainChangeListener);
                                },
                                () -> {
                                    sb.setEnabled(true);
                                    sb.setProgress(threshold70);
                                    lastProgress = threshold70;
                                    sb.setOnSeekBarChangeListener(gainChangeListener);
                                }
                        );
                        return;
                    }
                    // ── High warning (40 dB) ───────────────────────────
                    if (curDb > 40f && curDb <= 70f && !allowAbove40 && lastProgress <= threshold40) {
                        sb.setEnabled(false);
                        sb.setOnSeekBarChangeListener(null);
                        sb.setProgress(threshold40);
                        lastProgress = threshold40;
                        showThresholdDialog(
                                "High Gain Warning",
                                "You are about to exceed 40 dB of amplification. This may cause noticeable distortion. Continue?",
                                () -> {
                                    allowAbove40 = true;
                                    sb.setEnabled(true);
                                    sb.setProgress(progress);
                                    lastProgress = progress;
                                    applyGain(progress, maxProgress, maxDb);
                                    sb.setOnSeekBarChangeListener(gainChangeListener);
                                },
                                () -> {
                                    sb.setEnabled(true);
                                    sb.setProgress(threshold40);
                                    lastProgress = threshold40;
                                    sb.setOnSeekBarChangeListener(gainChangeListener);
                                }
                        );
                        return;
                    }
                    // reset confirmations if moved back down
                    if (curDb <= 40f) allowAbove40 = false;
                    if (curDb <= 70f) allowAbove70 = false;

                    // finally apply the new gain
                    sb.setProgress(progress);
                    applyGain(progress, maxProgress, maxDb);
                    lastProgress = progress;
                } else {
                    // programmatic update: just sync lastProgress
                    lastProgress = progress;
                }
            }

            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb)  { }
        };



// 2) force one initial tint/layout pass immediately:
        amplificationSeekBar.post(() ->
                gainChangeListener.onProgressChanged(
                        amplificationSeekBar,
                        amplificationSeekBar.getProgress(),
                        false
                )
        );;



        toggleButton.setVisibility(View.INVISIBLE);
        toggleButton.setEnabled(false);
        enrollButton.setEnabled(false);

        mainHandler = new Handler(Looper.getMainLooper());

        // Background init
        new Thread(() -> {
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

    private void applyGain(int progress, int maxProgress, float maxDb) {
        float curDb = (progress / (float) maxProgress) * maxDb;
        float ampFactor = (float) Math.pow(10, curDb / 20f);
        // store or use ampFactor however you need…
    }

    private void showThresholdDialog(
            String title, String message,
            Runnable onConfirm, Runnable onCancel
    ) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", (dialog, which) -> {
                    onCancel.run();
                    dialog.dismiss();
                })
                .setPositiveButton("I'm sure", (dialog, which) -> {
                    onConfirm.run();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
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
            
            // Update Focus Mode manager with new speaker
            if (speaker != null) {
                FocusModeManager.getInstance().setSelectedSpeakerEmbedding(speaker.getEmbedding());
            }
            
            // Update speaker isolation state when speaker is selected
            updateSpeakerIsolationState();
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



//––– REPLACE WITH this –––
        amplificationSeekBar = findViewById(R.id.seekBar);
        amplificationSeekBar.setMax(100);
        amplificationSeekBar.setProgress(50);
// attach your threshold-checking listener:
        amplificationSeekBar.setOnSeekBarChangeListener(gainChangeListener);
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

            // Initialize Focus Mode manager with diarization and selected speaker
            FocusModeManager.getInstance().initialize(this, diarizationManager);
            if (selectedEnrolledSpeaker != null) {
                FocusModeManager.getInstance().setSelectedSpeakerEmbedding(
                    selectedEnrolledSpeaker.getEmbedding()
                );
            }

            // Start SimpleAudioStreamingService with Phase 2+3 DSP
            startAudioStreamingServiceInFocusMode();
            
            isProcessing = true;
            speakerIsolationEnabled = true;
            updateToggleUi(true);
            focusWaveform.setVisibility(View.VISIBLE);
            focusWaveform.levels.clear();
            
            // Enable speaker isolation in service
            updateSpeakerIsolationState();
            
            // Start continuous speaker state monitoring (check every 100ms)
            speakerCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isProcessing && speakerIsolationEnabled) {
                        updateSpeakerIsolationState();
                        speakerCheckHandler.postDelayed(this, 100); // Check every 100ms
                    }
                }
            };
            speakerCheckHandler.post(speakerCheckRunnable);

        } catch (Exception e) {
            Log.e(TAG, "Error starting processing", e);
            Toast.makeText(this, "Error: " + e.getMessage(),
                           Toast.LENGTH_SHORT).show();
        }
    }

    private void stopProcessing() {
        isProcessing = false;
        speakerIsolationEnabled = false;
        
        // Stop continuous speaker state monitoring
        if (speakerCheckRunnable != null) {
            speakerCheckHandler.removeCallbacks(speakerCheckRunnable);
            speakerCheckRunnable = null;
        }
        
        // Shutdown Focus Mode manager
        FocusModeManager.getInstance().shutdown();
        
        // Disable speaker isolation in service
        sendSpeakerIsolationBroadcast(false, true, currentChunkId);
        
        // Stop SimpleAudioStreamingService
        stopAudioStreamingService();
        
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
        
        // Update speaker isolation state when speaker history changes
        updateSpeakerIsolationState();
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
    
    /**
     * Check if the selected enrolled speaker is currently active and send broadcast to service.
     * This allows SimpleAudioStreamingService to apply speaker isolation in real-time.
     */
    private void updateSpeakerIsolationState() {
        if (selectedEnrolledSpeaker == null || diarizationManager == null) {
            // No speaker selected or diarization not ready - disable isolation
            sendSpeakerIsolationBroadcast(false, true, currentChunkId);
            return;
        }
        
        boolean speakerActive = false;
        try {
            // Check if selected speaker is among currently active speakers
            for (var s : diarizationManager.getActiveSpeakers(currentChunkId)) {
                if (diarizationManager.isSpeakerMatchingEnrollment(
                        s.getGlobalId(),
                        selectedEnrolledSpeaker.getEmbedding()
                )) {
                    speakerActive = true;
                    break;
                }
            }
        } catch (Exception e) {
            Log.w("FocusActivity", "Error checking active speakers", e);
        }
        
        // Send broadcast to service with current state
        sendSpeakerIsolationBroadcast(true, speakerActive, currentChunkId);
    }
    
    /**
     * Send broadcast to SimpleAudioStreamingService to update speaker isolation state.
     */
    private void sendSpeakerIsolationBroadcast(boolean enabled, boolean speakerActive, int chunkId) {
        Intent intent = new Intent("com.example.audion.ACTION_SET_SPEAKER_ISOLATION");
        intent.putExtra("isolationEnabled", enabled);
        intent.putExtra("speakerActive", speakerActive);
        intent.putExtra("chunkId", chunkId);
        sendBroadcast(intent);
        
        Log.d("FocusActivity", String.format("[Focus Mode] Broadcasting speaker isolation: enabled=%b, active=%b, chunk=%d",
            enabled, speakerActive, chunkId));
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
    
    /**
     * Start SimpleAudioStreamingService with Phase 2+3 DSP (includes RNNoise + clinical processing)
     */
    private void startAudioStreamingServiceInFocusMode() {
        Log.d(TAG, "Starting SimpleAudioStreamingService with Phase 2+3 DSP for Focus Mode");
        
        // SimpleAudioStreamingService doesn't require mode setting - it always uses Phase 2+3 pipeline
        // Focus-specific functionality (speaker isolation, diarization) is handled by FocusActivity itself
        Intent serviceIntent = new Intent(this, SimpleAudioStreamingService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        
        Log.i(TAG, "Focus Mode: Using Phase 2+3 DSP with WDRC, feedback cancellation, and scene analysis");
    }
    
    /**
     * Stop SimpleAudioStreamingService
     */
    private void stopAudioStreamingService() {
        Log.d(TAG, "Stopping SimpleAudioStreamingService");
        stopService(new Intent(this, SimpleAudioStreamingService.class));
    }
}
