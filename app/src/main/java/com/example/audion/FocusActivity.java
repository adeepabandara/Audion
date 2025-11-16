// FocusActivity.java
package com.example.audion;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.example.audion.utils.EarbudsChecker;
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
    private EnrolledSpeakersAdapter enrolledSpeakersAdapter;

    private GlobalSpeakersAdapter globalSpeakersAdapter;

    private Handler mainHandler;
    private MaterialButton enrollButton;
    private MaterialButton scanAgainButton; // Fixed at bottom for "no speakers" case
    private MaterialButton scanAgainButtonInScroll; // Inside ScrollView for "speakers found" case
    private BottomNavigationView bottomNav;

    // Scanning UI elements
    private View scanningContainer;
    private TextView scanStatusText;
    private ProgressBar scanProgress;
    private Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanTextRunnable;
    private int scanMessageIndex = 0;
    
    // Diarization UI elements
    private View diarizationContainer;
    private TextView diarizationStatusText;
    private ProgressBar diarizationProgress;

    // amplification
    private SeekBar amplificationSeekBar;
    private float amplificationFactor = 1.0f;

    // start/stop button
    private MaterialButton toggleButton;

    private WaveformView focusWaveform;
    private int waveformUpdateCount = 0;
    private final BroadcastReceiver wfReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            float level = i.getFloatExtra("outputLevel", 0f);
            if (focusWaveform != null) {
                focusWaveform.addLevel(level);
                waveformUpdateCount++;
                if (waveformUpdateCount % 100 == 0) {
                    android.util.Log.d("FocusActivity", "Waveform received: " + level + " (count=" + waveformUpdateCount + ")");
                }
            }
        }
    };

    // Audio control container (waveform + toggle button + seekbar)
    private androidx.constraintlayout.widget.ConstraintLayout audioControlContainer;

    // Selected speaker card (simple display)
    private View selectedSpeakerCardView;
    private TextView cardSpeakerName;
    private RotatingBorderView rotatingBorder;
    private SpeakerSelectionBottomSheet.DetectedSpeaker currentlySelectedSpeaker = null;

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

        // Initialize audio control container (waveform + toggle + seekbar)
        audioControlContainer = findViewById(R.id.audioControlContainer);
        if (audioControlContainer != null) {
            audioControlContainer.setVisibility(View.GONE);
        }

        // Initialize noise reduction switch
        com.google.android.material.switchmaterial.SwitchMaterial noiseRemovalSwitch = findViewById(R.id.noiseRemovalSwitch);
        TextView noiseStatusText = findViewById(R.id.noiseStatusText);
        
        if (noiseRemovalSwitch != null) {
            // Read current setting from SharedPreferences (default true)
            // Use same prefs name as HomeActivity and SimpleAudioStreamingService
            SharedPreferences prefs = getSharedPreferences("com.example.audion.PREFERENCES", MODE_PRIVATE);
            boolean isOn = prefs.getBoolean("noiseRemoval", true);
            noiseRemovalSwitch.setChecked(isOn);
            
            // Set initial status text
            if (noiseStatusText != null) {
                noiseStatusText.setText(isOn ? "Noise Cancellation ON" : "Noise Cancellation OFF");
            }
            
            // Handle switch changes
            noiseRemovalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Save to SharedPreferences
                prefs.edit().putBoolean("noiseRemoval", isChecked).apply();
                
                // Update status text
                if (noiseStatusText != null) {
                    noiseStatusText.setText(isChecked ? "Noise Cancellation ON" : "Noise Cancellation OFF");
                }
                
                // Notify audio service of preference change
                Intent broadcast = new Intent("com.example.audion.PREFERENCES_CHANGED");
                sendBroadcast(broadcast);
                
                Log.d(TAG, "Noise Reduction " + (isChecked ? "enabled" : "disabled"));
            });
        }

        // Initialize selected speaker card (rotatingBorder is now the container)
        rotatingBorder = findViewById(R.id.rotatingBorder);
        if (rotatingBorder != null) {
            rotatingBorder.setVisibility(View.GONE);
            cardSpeakerName = rotatingBorder.findViewById(R.id.cardSpeakerName);
            selectedSpeakerCardView = rotatingBorder.findViewById(R.id.selectedSpeakerCard);
            
            // Card click opens bottom sheet to change speaker - set on both views for reliability
            rotatingBorder.setOnClickListener(v -> {
                Log.d(TAG, "Speaker card clicked - opening bottom sheet");
                showSpeakerSelectionBottomSheet();
            });
            
            if (selectedSpeakerCardView != null) {
                selectedSpeakerCardView.setOnClickListener(v -> {
                    Log.d(TAG, "Inner card clicked - opening bottom sheet");
                    showSpeakerSelectionBottomSheet();
                });
            }
        }

        toggleButton = findViewById(R.id.toggleButton);
        if (toggleButton != null) {
            toggleButton.setVisibility(View.INVISIBLE);
            toggleButton.setEnabled(false);
        } else {
            Log.w(TAG, "toggleButton not found yet; skipping initial hide");
        }

        // Keep the main content group visible initially (showing image and description)
        View mainContentGroup = findViewById(R.id.mainContentGroup);
        if (mainContentGroup != null) {
            mainContentGroup.setVisibility(View.VISIBLE);
        }




        // Loading overlay
        View loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingOverlay.setVisibility(View.VISIBLE);

        // Initialize scanning UI elements
        scanningContainer = findViewById(R.id.scanningContainer);
        scanStatusText = findViewById(R.id.scanStatusText);
        scanProgress = findViewById(R.id.scanProgress);
        
        // Initialize diarization UI elements
        diarizationContainer = findViewById(R.id.diarizationContainer);
        diarizationStatusText = findViewById(R.id.diarizationStatusText);
        diarizationProgress = findViewById(R.id.diarizationProgress);

        // Bind UI
        enrollButton = findViewById(R.id.enrollButton);
        scanAgainButton = findViewById(R.id.scanAgainButton); // Fixed at bottom
        scanAgainButtonInScroll = findViewById(R.id.scanAgainButtonInScroll); // Inside ScrollView
        if (scanAgainButton == null) {
            Log.e(TAG, "❌ scanAgainButton is NULL after findViewById!");
        } else {
            Log.d(TAG, "✅ scanAgainButton found successfully");
        }
        if (scanAgainButtonInScroll == null) {
            Log.e(TAG, "❌ scanAgainButtonInScroll is NULL after findViewById!");
        } else {
            Log.d(TAG, "✅ scanAgainButtonInScroll found successfully");
        }
        bottomNav = findViewById(R.id.bottomNavigationView);
        findViewById(R.id.seekBarContainer).setVisibility(View.GONE);

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
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return true;
        });
    }

    /**
     * Reset UI to initial state (showing Focus image and description)
     */
    private void resetToInitialState() {
        // Show main content (image + description)
        View mainContentGroup = findViewById(R.id.mainContentGroup);
        if (mainContentGroup != null) {
            mainContentGroup.setVisibility(View.VISIBLE);
        }
        
        // Reset image to default focus image
        ImageView focusModeImage = findViewById(R.id.focusModeImage);
        if (focusModeImage != null) {
            focusModeImage.setImageResource(R.drawable.focus);
        }
        
        // Reset description text
        TextView focusModeDescription = findViewById(R.id.focusModeDescription);
        if (focusModeDescription != null) {
            focusModeDescription.setText("Isolate and amplify the voice you want to hear.\nScan your environment to detect speakers.");
        }
        
        // Hide audio control container
        if (audioControlContainer != null) {
            audioControlContainer.setVisibility(View.GONE);
        }
        
        // Hide scanning container
        if (scanningContainer != null) {
            scanningContainer.setVisibility(View.GONE);
        }
        
        // Hide diarization container
        if (diarizationContainer != null) {
            diarizationContainer.setVisibility(View.GONE);
        }
        
        // Stop and hide animated border container (which contains the speaker card)
        if (rotatingBorder != null) {
            rotatingBorder.stopAnimation();
            rotatingBorder.setVisibility(View.GONE);
        }
        
        // Hide toggle button
        if (toggleButton != null) {
            toggleButton.setVisibility(View.INVISIBLE);
            toggleButton.setEnabled(false);
        }
        
        // Reset button text and visibility
        if (enrollButton != null) {
            enrollButton.setText("Scan Environment");
            enrollButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0F766E));
            enrollButton.setVisibility(View.VISIBLE);
        }
        
        // Hide both scan again buttons
        if (scanAgainButton != null) {
            scanAgainButton.setVisibility(View.GONE);
        }
        if (scanAgainButtonInScroll != null) {
            scanAgainButtonInScroll.setVisibility(View.GONE);
        }
        
        // Clear selected speaker
        currentlySelectedSpeaker = null;
        selectedEnrolledSpeaker = null;
        detectedSpeakers.clear();
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

        // enrolled speakers
        enrolledSpeakersAdapter = new EnrolledSpeakersAdapter(this, enrolledSpeakers);
        enrolledSpeakersAdapter.setOnSpeakerSelectListener((speaker, pos) -> {
            sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
            selectedEnrolledSpeaker = speaker;
            
            // Hide main content group
            View mainContentGroup = findViewById(R.id.mainContentGroup);
            if (mainContentGroup != null) {
                mainContentGroup.setVisibility(View.GONE);
            }
            
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
        loadEnrolledSpeakers();

        enrollButton.setOnClickListener(v -> {
            if (!isEnrolling) {
                // Check for earbuds before starting scan
                if (!EarbudsChecker.areEarbudsConnected(this)) {
                    showEarbudsRequiredSheet();
                    return;
                }
                // Start scanning
                startEnrollment();
            } else {
                // Cancel scan during scanning
                isEnrolling = false;
                if (enrollmentRecorder != null) {
                    enrollmentRecorder.stop();
                    enrollmentRecorder.release();
                    enrollmentRecorder = null;
                }
                resetToInitialState();
            }
        });

        // Scan Again button click listener
        scanAgainButton.setOnClickListener(v -> {
            // Check for earbuds before starting scan
            if (!EarbudsChecker.areEarbudsConnected(this)) {
                showEarbudsRequiredSheet();
                return;
            }
            // Reset to initial state
            resetToInitialState();
            // Then start new scan
            startEnrollment();
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
        
        // Stop scan animation
        if (scanHandler != null && scanTextRunnable != null) {
            scanHandler.removeCallbacks(scanTextRunnable);
        }
        
        if (enrollmentRecorder != null) {
            enrollmentRecorder.stop();
            enrollmentRecorder.release();
            enrollmentRecorder = null;
        }
        runOnUiThread(() -> {
            // Hide scanning and diarization containers
            if (scanningContainer != null) {
                scanningContainer.setVisibility(View.GONE);
            }
            if (diarizationContainer != null) {
                diarizationContainer.setVisibility(View.GONE);
            }
            
            showDefaultPanel();
            toggleButton.setVisibility(View.INVISIBLE);
            toggleButton.setEnabled(false);
            enrollButton.setText("Scan Environment");
            enrollButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0F766E)); // Reset to primary green
            findViewById(R.id.seekBarContainer).setVisibility(View.GONE);
        });
    }

    private void showDefaultPanel() {
        // Hide the static content group
        View mainContentGroup = findViewById(R.id.mainContentGroup);
        if (mainContentGroup != null) {
            mainContentGroup.setVisibility(View.GONE);
        }
    }

    private void showScanInline() {
        // Hide main content group during scanning
        View mainContentGroup = findViewById(R.id.mainContentGroup);
        if (mainContentGroup != null) {
            mainContentGroup.setVisibility(View.GONE);
        }
        
        // Show scanning container with progress animation
        if (scanningContainer != null) {
            scanningContainer.setVisibility(View.VISIBLE);
        }
        
        // Reset progress
        scanMessageIndex = 0;
        if (scanStatusText != null) {
            scanStatusText.setText(scanMessages[scanMessageIndex]);
        }
        if (scanProgress != null) {
            scanProgress.setProgress(0);
        }
        
        // Animate scanning messages
        long interval = ENROLLMENT_DURATION_SECONDS * 1000L / scanMessages.length;
        scanTextRunnable = new Runnable() {
            @Override public void run() {
                scanMessageIndex = (scanMessageIndex + 1) % scanMessages.length;
                if (scanStatusText != null) {
                    scanStatusText.setText(scanMessages[scanMessageIndex]);
                }
                scanHandler.postDelayed(this, interval);
            }
        };
        scanHandler.postDelayed(scanTextRunnable, interval);
        
        // Animate progress bar
        new Thread(() -> {
            while (isEnrolling) {
                int progress = Math.min(100, (recordedSamples * 100) / ENROLLMENT_SAMPLE_COUNT);
                runOnUiThread(() -> {
                    if (scanProgress != null) {
                        scanProgress.setProgress(progress);
                    }
                });
                try { Thread.sleep(100); }
                catch (InterruptedException ignored) { }
            }
        }).start();
    }

    private void showDiarizationInline() {
        // Hide main content group and scanning container
        View mainContentGroup = findViewById(R.id.mainContentGroup);
        if (mainContentGroup != null) {
            mainContentGroup.setVisibility(View.GONE);
        }
        if (scanningContainer != null) {
            scanningContainer.setVisibility(View.GONE);
        }
        
        // Show diarization container with animation
        if (diarizationContainer != null) {
            diarizationContainer.setVisibility(View.VISIBLE);
        }
        if (diarizationStatusText != null) {
            diarizationStatusText.setText("Starting diarization…");
        }
        if (diarizationProgress != null) {
            diarizationProgress.setProgress(0);
        }
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
        if (isProcessing) {
            stopProcessing();
        } else {
            // Check if speaker is selected using either variable
            if (selectedEnrolledSpeaker == null && currentlySelectedSpeaker == null) {
                Toast.makeText(this, "Select a speaker first",
                               Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Check for earbuds before starting processing
            if (!EarbudsChecker.areEarbudsConnected(this)) {
                showEarbudsRequiredSheet();
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
            
            // Set speaker embedding from either selectedEnrolledSpeaker or currentlySelectedSpeaker
            float[] embeddingToUse = null;
            if (selectedEnrolledSpeaker != null) {
                embeddingToUse = selectedEnrolledSpeaker.getEmbedding();
                Log.d(TAG, "Using selectedEnrolledSpeaker embedding: " + selectedEnrolledSpeaker.getName());
            } else if (currentlySelectedSpeaker != null) {
                embeddingToUse = currentlySelectedSpeaker.getEmbedding();
                Log.d(TAG, "Using currentlySelectedSpeaker embedding: " + currentlySelectedSpeaker.getName());
            }
            
            if (embeddingToUse != null) {
                FocusModeManager.getInstance().setSelectedSpeakerEmbedding(embeddingToUse);
                Log.d(TAG, "Speaker embedding set in FocusModeManager (length=" + embeddingToUse.length + ")");
            } else {
                Log.e(TAG, "ERROR: No speaker embedding available!");
            }

            // Start SimpleAudioStreamingService with Phase 2+3 DSP
            startAudioStreamingServiceInFocusMode();
            
            isProcessing = true;
            speakerIsolationEnabled = true;
            updateToggleUi(true);
            // Waveform is now always visible (static section), just clear levels
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
        
        // Waveform stays visible (static section), just clear levels
        focusWaveform.levels.clear();
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
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                wfReceiver,
                new IntentFilter("com.example.audion.WAVEFORM_UPDATE"),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }
    @Override protected void onStop() {
        super.onStop();
        unregisterReceiver(wfReceiver);
    }

    @Override public void onSpeakerSelected(int speakerId) {
        sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
    }
    @Override public void onSpeakerDeselected() { }

    @Override public void onSpeakersDetected(List<DirectDiarizationManager.SpeakerInfo> list) {
        runOnUiThread(() -> {
            globalSpeakersAdapter.notifyDataSetChanged();

            sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
        });

        sendBroadcast(new Intent("com.example.audion.STOP_STREAMING"));
    }
    @Override public void onSpeakersHistoryUpdated(List<List<DirectDiarizationManager.SpeakerInfo>> history) {
        runOnUiThread(() -> {
            globalSpeakersAdapter.notifyDataSetChanged();
        });
        
        // Update speaker isolation state when speaker history changes
        updateSpeakerIsolationState();
    }
    @Override public void onBufferFillProgress(float p) { }
    @Override public void onProcessingProgress(float p) {
        // Update diarization progress
        if (diarizationProgress != null) {
            int pct = Math.round(p * 100);
            runOnUiThread(() -> {
                diarizationProgress.setProgress(pct);
                if (diarizationStatusText != null) {
                    diarizationStatusText.setText("Processing audio… " + pct + "%");
                }
            });
        }
    }

    private void showEarbudsRequiredSheet() {
        EarbudsRequiredBottomSheet bottomSheet = new EarbudsRequiredBottomSheet();
        bottomSheet.setOnEarbudsConnectedListener(() -> {
            // This callback can be empty since user will manually click button again after connecting
            // Or we can automatically trigger the action:
            // For scanning: startEnrollment();
            // For processing: startProcessing();
        });
        bottomSheet.show(getSupportFragmentManager(), "earbuds_required");
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
        // Check for speaker using both variables
        EnrollmentActivity.EnrolledSpeaker speakerToCheck = selectedEnrolledSpeaker;
        float[] embeddingToCheck = null;
        
        if (speakerToCheck != null) {
            embeddingToCheck = speakerToCheck.getEmbedding();
            Log.d(TAG, "updateSpeakerIsolationState: Using selectedEnrolledSpeaker - " + speakerToCheck.getName());
        } else if (currentlySelectedSpeaker != null) {
            embeddingToCheck = currentlySelectedSpeaker.getEmbedding();
            Log.d(TAG, "updateSpeakerIsolationState: Using currentlySelectedSpeaker - " + currentlySelectedSpeaker.getName());
        }
        
        if (embeddingToCheck == null || diarizationManager == null) {
            // No speaker selected or diarization not ready - disable isolation
            Log.d(TAG, "updateSpeakerIsolationState: No speaker or diarization not ready, disabling isolation");
            sendSpeakerIsolationBroadcast(false, true, currentChunkId);
            return;
        }
        
        boolean speakerActive = false;
        try {
            // Check if selected speaker is among currently active speakers
            for (var s : diarizationManager.getActiveSpeakers(currentChunkId)) {
                if (diarizationManager.isSpeakerMatchingEnrollment(
                        s.getGlobalId(),
                        embeddingToCheck
                )) {
                    speakerActive = true;
                    Log.d(TAG, "updateSpeakerIsolationState: Selected speaker IS ACTIVE (globalId=" + s.getGlobalId() + ")");
                    break;
                }
            }
            if (!speakerActive) {
                Log.d(TAG, "updateSpeakerIsolationState: Selected speaker NOT active");
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
            // Hide main content group when scanning starts
            View mainContentGroup = findViewById(R.id.mainContentGroup);
            if (mainContentGroup != null) {
                mainContentGroup.setVisibility(View.GONE);
            }
            
            findViewById(R.id.seekBarContainer).setVisibility(View.GONE);

            showScanInline();

            enrollButton.setText("Stop Scan");
            enrollButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFDC2626)); // Change to red
            enrollButton.setEnabled(true);
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

            if (recordedSamples >= ENROLLMENT_SAMPLE_COUNT) {
                runOnUiThread(this::showDiarizationInline);
                stopEnrollment();
            }
        }).start();
    }

    private void stopEnrollment() {
        if (!isEnrolling) return;
        isEnrolling = false;
        
        // Stop scan animation
        if (scanHandler != null && scanTextRunnable != null) {
            scanHandler.removeCallbacks(scanTextRunnable);
        }
        
        if (enrollmentRecorder != null) {
            enrollmentRecorder.stop();
            enrollmentRecorder.release();
            enrollmentRecorder = null;
        }

        runOnUiThread(() -> {
            // Hide scanning container
            if (scanningContainer != null) {
                scanningContainer.setVisibility(View.GONE);
            }
            
            // Don't change button text here - it will be hidden after processing completes
        });
        new Thread(this::processEnrollmentAudio).start();
    }

    private void processEnrollmentAudio() {
        float[] audio = new float[recordedSamples];
        System.arraycopy(enrollmentBuffer, 0, audio, 0, recordedSamples);

        OfflineSpeakerDiarizationSegment[] segs =
                SpeakerDiarizationManager.processSpeakerDiarization(
                        audio,
                        (proc, tot, unused) -> {
                            // Update diarization progress
                            int pct = (int)(proc * 100f / tot);
                            runOnUiThread(() -> {
                                if (diarizationProgress != null) {
                                    diarizationProgress.setProgress(pct);
                                }
                                if (diarizationStatusText != null) {
                                    diarizationStatusText.setText("Loading Speaker Playbacks… " + pct + "%");
                                }
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
            View seekBarContainer = findViewById(R.id.seekBarContainer);
            View noiseReductionCard = findViewById(R.id.noiseReductionCard);

            if (found.isEmpty()) {
                // No speakers found - show message and Scan Again button
                Log.e(TAG, "══════════════════════════════════════════");
                Log.e(TAG, "NO SPEAKERS DETECTED - Showing error UI");
                Log.e(TAG, "══════════════════════════════════════════");
                
                showPromptNoSpeakersInline();
                toggleButton.setVisibility(View.INVISIBLE);
                seekBarContainer.setVisibility(View.GONE);
                
                // Hide all cards
                if (noiseReductionCard != null) {
                    noiseReductionCard.setVisibility(View.GONE);
                }
                if (rotatingBorder != null) {
                    rotatingBorder.setVisibility(View.GONE);
                }
                
                // Hide main "Scan Environment" button and show "Scan Again" button
                if (enrollButton != null) {
                    enrollButton.setVisibility(View.GONE);
                    Log.d(TAG, "Hidden enrollButton");
                }
                if (scanAgainButton != null) {
                    scanAgainButton.setVisibility(View.VISIBLE);
                    scanAgainButton.requestLayout();
                    scanAgainButton.bringToFront();
                    
                    // Try to scroll to the button
                    View scrollView = findViewById(R.id.contentScrollView);
                    if (scrollView instanceof android.widget.ScrollView) {
                        ((android.widget.ScrollView) scrollView).post(() -> {
                            ((android.widget.ScrollView) scrollView).fullScroll(android.widget.ScrollView.FOCUS_DOWN);
                        });
                    }
                    
                    Log.e(TAG, "✅ scanAgainButton set to VISIBLE with bringToFront()");
                    Log.d(TAG, "Button dimensions: width=" + scanAgainButton.getWidth() + ", height=" + scanAgainButton.getHeight());
                    Log.d(TAG, "Button visibility state: " + scanAgainButton.getVisibility());
                } else {
                    Log.e(TAG, "❌ scanAgainButton is NULL!");
                }
            } else {
                // Convert EnrolledSpeakers to DetectedSpeakers for bottom sheet
                detectedSpeakers.clear();
                for (int i = 0; i < found.size(); i++) {
                    EnrollmentActivity.EnrolledSpeaker es = found.get(i);
                    detectedSpeakers.add(new SpeakerSelectionBottomSheet.DetectedSpeaker(
                            i,
                            es.getName(),
                            es.getDuration(),
                            es.getEmbedding(),
                            es.getAudioSamples()
                    ));
                }
                
                // Store found speakers for later use
                enrolledSpeakers.clear();
                enrolledSpeakers.addAll(found);
                
                // Show speaker selection card with placeholder text
                if (rotatingBorder != null) {
                    rotatingBorder.setVisibility(View.VISIBLE);
                    if (cardSpeakerName != null) {
                        cardSpeakerName.setText("Please select a speaker");
                    }
                    // No animation yet - only start after speaker is selected
                    rotatingBorder.stopAnimation();
                }
                
                // Hide diarization container
                if (diarizationContainer != null) {
                    diarizationContainer.setVisibility(View.GONE);
                }
                
                // Show audio controls (waveform, toggle, seekbar) like normal mode
                if (audioControlContainer != null) {
                    audioControlContainer.setVisibility(View.VISIBLE);
                }
                
                // Show toggle button (but keep disabled until speaker is selected)
                if (toggleButton != null) {
                    toggleButton.setVisibility(View.VISIBLE);
                    toggleButton.setEnabled(false);
                    updateToggleUi(false); // Set proper icon and background
                }
                
                if (seekBarContainer != null) {
                    seekBarContainer.setVisibility(View.VISIBLE);
                }
                
                // Show bottom sheet for speaker selection (will appear on top of main content)
                showPromptSpeakersFoundInline(found.size());
                
                // Hide main "Scan Environment" button and show "Scan Again" button in ScrollView
                if (enrollButton != null) {
                    enrollButton.setVisibility(View.GONE);
                    // Reset button state for future use
                    enrollButton.setText("Scan Environment");
                    enrollButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0F766E));
                }
                // Use the button INSIDE ScrollView for when speakers are found
                if (scanAgainButtonInScroll != null) {
                    scanAgainButtonInScroll.setVisibility(View.VISIBLE);
                }
                // Hide the fixed bottom button
                if (scanAgainButton != null) {
                    scanAgainButton.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showPromptNoSpeakersInline() {
        Log.d(TAG, "showPromptNoSpeakersInline: Showing no speakers found UI");
        
        // Hide loading overlay (in case it's still visible)
        View loadingOverlay = findViewById(R.id.loadingOverlay);
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
            Log.d(TAG, "Loading overlay hidden");
        }
        
        // Hide diarization container (animation and progress)
        if (diarizationContainer != null) {
            diarizationContainer.setVisibility(View.GONE);
        }
        
        // Hide audio control container (waveform, toggle, etc.)
        if (audioControlContainer != null) {
            audioControlContainer.setVisibility(View.GONE);
        }
        
        // Show main content group with "no speakers" message
        View mainContentGroup = findViewById(R.id.mainContentGroup);
        ImageView focusModeImage = findViewById(R.id.focusModeImage);
        TextView focusModeDescription = findViewById(R.id.focusModeDescription);
        
        if (mainContentGroup != null) {
            mainContentGroup.setVisibility(View.VISIBLE);
            Log.d(TAG, "mainContentGroup set to VISIBLE");
        }
        
        if (focusModeImage != null) {
            focusModeImage.setImageResource(R.drawable.ic_not_found);
        }
        
        if (focusModeDescription != null) {
            focusModeDescription.setText("No speakers detected.\nTry scanning again.");
        }
        
        // Ensure ScrollView is visible
        View scrollView = findViewById(R.id.contentScrollView);
        if (scrollView != null) {
            scrollView.setVisibility(View.VISIBLE);
            Log.d(TAG, "ScrollView set to VISIBLE");
        }
    }
    
    private List<SpeakerSelectionBottomSheet.DetectedSpeaker> detectedSpeakers = new ArrayList<>();

    private void showSpeakerSelectionBottomSheet() {
        SpeakerSelectionBottomSheet bottomSheet = SpeakerSelectionBottomSheet.newInstance(detectedSpeakers);
        bottomSheet.setOnSpeakerSelectedListener(speaker -> {
            currentlySelectedSpeaker = speaker;
            onSpeakerSelectedFromBottomSheet(speaker);
        });
        bottomSheet.show(getSupportFragmentManager(), "SpeakerSelectionBottomSheet");
    }

    private void onSpeakerSelectedFromBottomSheet(SpeakerSelectionBottomSheet.DetectedSpeaker speaker) {
        // Find the corresponding EnrolledSpeaker
        EnrollmentActivity.EnrolledSpeaker selectedEnrolled = null;
        for (EnrollmentActivity.EnrolledSpeaker es : enrolledSpeakers) {
            if (es.getName().equals(speaker.getName())) {
                selectedEnrolled = es;
                break;
            }
        }
        
        if (selectedEnrolled == null) return;
        
        selectedEnrolledSpeaker = selectedEnrolled;
        currentlySelectedSpeaker = speaker;
        
        // Hide main content group now that a speaker is selected
        View mainContentGroup = findViewById(R.id.mainContentGroup);
        if (mainContentGroup != null) {
            mainContentGroup.setVisibility(View.GONE);
        }
        
        // Show speaker card with animation
        if (rotatingBorder != null) {
            rotatingBorder.setVisibility(View.VISIBLE);
            
            // Update card info
            if (cardSpeakerName != null) {
                cardSpeakerName.setText(speaker.getName());
            }
            
            // Start animated border
            rotatingBorder.startAnimation();
        }
        
        // Show audio control container (waveform + toggle + seekbar)
        if (audioControlContainer != null) {
            audioControlContainer.setVisibility(View.VISIBLE);
        }
        
        // Show and enable toggle button
        toggleButton.setVisibility(View.VISIBLE);
        toggleButton.setEnabled(true);
        updateToggleUi(false);
        
        // Update Focus Mode manager
        if (selectedEnrolled != null) {
            FocusModeManager.getInstance().setSelectedSpeakerEmbedding(selectedEnrolled.getEmbedding());
        }
        
        // Update speaker isolation state
        updateSpeakerIsolationState();
    }

    private void showPromptSpeakersFoundInline(int count) {
        // Hide main content group (image and description) since we're showing speaker UI now
        View mainContentGroup = findViewById(R.id.mainContentGroup);
        if (mainContentGroup != null) {
            mainContentGroup.setVisibility(View.GONE);
        }
        
        // The speaker selection card and audio controls are already visible from the previous code
        // Show bottom sheet with detected speakers (will appear on top of the speaker selection UI)
        showSpeakerSelectionBottomSheet();
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
